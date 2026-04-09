# Distortion Oversampling

Created: 2026-04-09

## Problem

Heavy distortion generates harmonics above Nyquist, causing audible aliasing. The ADSR-before-distortion
pipeline order also causes clicks: heavy drive squashes the envelope ramp into a near-rectangle
(even a 2% envelope level gets driven past saturation with drive=251x).

Oversampling the distortion stage is the standard fix: upsample, distort at higher rate, anti-alias
lowpass, decimate. This also smooths the saturation boundary, reducing click artifacts from
ADSR-distortion interaction.

## Design Decisions

| Decision            | Choice                                                                                 | Rationale                                                                                      |
|---------------------|----------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| Oversampling factor | Direct factor, floored to power of 2                                                   | `distos(2)`=2x, `distos(4)`=4x, `distos(8)`=8x. Non-powers rounded down. ≤1 or negative = off. |
| Anti-alias lowpass  | Fixed 15-tap half-band FIR, not user-configurable                                      | Technical purpose only; `lpf()` already handles creative filtering                             |
| Buffer strategy     | `ScratchBuffers.oversample(factor)` — cached factory returning a larger ScratchBuffers | Minimal change (one method), shared across voices, reuses existing pool logic entirely         |
| Scope               | Distortion only                                                                        | crush/coarse are less prone to aliasing; can extend later                                      |
| Signal order        | Unchanged (ADSR before distortion)                                                     | Intentional for dynamics-responsive distortion                                                 |

## Parameter Spec

User passes the oversampling **factor** directly. Non-power-of-2 values are floored to the
previous power of 2. Values ≤ 1 or negative = off.

| `distos()`      | Effective factor | Internal stages |
|-----------------|------------------|-----------------|
| `distos(0)`     | 1x (off)         | 0               |
| `distos(1)`     | 1x (off)         | 0               |
| `distos(2)`     | 2x               | 1               |
| `distos(3)`     | 2x (floored)     | 1               |
| `distos(4)`     | 4x               | 2               |
| `distos(5)`–`7` | 4x (floored)     | 2               |
| `distos(8)`     | 8x               | 3               |

Colon syntax: `distort("0.8:exp:4")` — 3rd segment is the factor (same rules).

Internal conversion:

```kotlin
fun factorToStages(factor: Int): Int {
    if (factor <= 1) return 0
    var s = factor;
    var stages = 0
    while (s > 1) {
        s = s shr 1; stages++
    }
    return stages
}
```

**Future:** Negative values reserved for sample-rate reduction (undersampling = intentional
lo-fi aliasing). Deferred — currently treated as off.

## Implementation

### Step 1: Oversampler DSP class

**New file:** `audio_be/src/commonMain/kotlin/Oversampler.kt`

```kotlin
class Oversampler(val stages: Int) {
    val factor: Int = 1 shl stages  // 2^stages

    // Persistent state (small, per-voice)
    private val decimators = Array(stages) { HalfBandState() }
    private var lastSample: Double = 0.0      // block boundary continuity

    // Working buffer borrowed from ScratchBuffers.oversample(factor)
    fun process(
        buffer: FloatArray, offset: Int, length: Int,
        scratchBuffers: ScratchBuffers, transform: (Float) -> Float
    ) {
        scratchBuffers.oversample(factor).use { work ->
            upsample(buffer, offset, length, work)
            for (i in 0 until length * factor) {
                work[i] = transform(work[i])
            }
            cascadeDecimate(work, length * factor)
            work.copyInto(buffer, offset, 0, length)
        }
    }
}
```

**Upsampling:** Direct linear interpolation to target rate (one pass). `lastSample` persists across
blocks for boundary continuity. No cascading needed for interpolation.

**Processing:** User-provided `(Float) -> Float` transform runs at oversampled rate on working buffer.

**Downsampling:** Cascaded 2x half-band FIR stages, in-place. Each stage reads `work[0..currentLen)`,
applies FIR, writes every 2nd output to `work[0..currentLen/2)`. Read index always leads write index.

**Half-band FIR (15-tap, ~60dB stopband rejection):**

```kotlin
// Non-zero taps (one side, symmetric). Offsets +/-1, +/-3, +/-5, +/-7 from center.
private val KERNEL = doubleArrayOf(
    0.29028467725446233,   // +/-1
    -0.10082903357273678,   // +/-3
    0.04020109949498927,   // +/-5
    -0.01147518582890975,   // +/-7
)
private const val CENTER_TAP = 0.5
private const val TAPS = 15
```

Half-band property: even-offset coefficients (except center) are zero, so only 4 symmetric
multiply-accumulates + center tap per output sample (5 MACs vs 15 for naive FIR).

**HalfBandState:** Circular delay buffer of 15 doubles + write position. Persists across blocks
(no reset between `process()` calls) for filter continuity.

**Buffer management — ScratchBuffers extensions:**

Two additions to ScratchBuffers. Existing FloatArray pool logic is untouched.

**1. `oversample(factor)` — cached factory for oversampled buffers:**

```kotlin
private val oversampleCache = mutableMapOf<Int, ScratchBuffers>()

fun oversample(factor: Int): ScratchBuffers {
    if (factor <= 1) return this
    return oversampleCache.getOrPut(factor) { ScratchBuffers(blockFrames * factor) }
}
```

**2. `useDouble()` — DoubleArray pool (same stack discipline as `use()`):**

```kotlin
private val doublePool = ArrayList<DoubleArray>(2)
private var doubleNextFree = 0

inline fun <R> useDouble(block: (DoubleArray) -> R): R {
    val buf = acquireDouble()
    try {
        return block(buf)
    } finally {
        releaseDouble()
    }
}
```

This replaces the lazy `var modBuf: DoubleArray? = null` pattern in 5 places:

| File                 | Line | Current                      | Migrates to                       |
|----------------------|------|------------------------------|-----------------------------------|
| `IgnitorPitchMod.kt` | 33   | vibrato `DoubleArray?`       | `ctx.scratchBuffers.useDouble {}` |
| `IgnitorPitchMod.kt` | 96   | accelerate `DoubleArray?`    | `ctx.scratchBuffers.useDouble {}` |
| `IgnitorPitchMod.kt` | 174  | pitchEnvelope `DoubleArray?` | `ctx.scratchBuffers.useDouble {}` |
| `IgnitorFm.kt`       | 27   | FM mod `FloatArray?`         | `ctx.scratchBuffers.use {}`       |
| `IgnitorFm.kt`       | 28   | FM phaseMod `DoubleArray?`   | `ctx.scratchBuffers.useDouble {}` |

Usage in Oversampler:

```kotlin
val osScratch = scratchBuffers.oversample(factor)
osScratch.use { work ->
    upsample(buffer, offset, length, work)
    for (i in 0 until length * factor) {
        work[i] = transform(work[i])
    }
    cascadeDecimate(work, length * factor)
    work.copyInto(buffer, offset, 0, length)
}
```

Each factor creates one cached ScratchBuffers on first use; all voices share it via the
standard `use {}` acquire/release pattern. Zero changes to existing ScratchBuffers logic.

### Step 2: DistortionRenderer

**File:** `audio_be/src/commonMain/kotlin/voices/strip/filter/DistortionRenderer.kt`

Add `oversampleStages: Int = 0` constructor param. When > 0, create `Oversampler` and wrap
waveshaper in `oversampler.process()`. DC blocker runs post-decimation at original rate.

```kotlin
class DistortionRenderer(
    private val amount: Double,
    shape: String = "soft",
    private val oversampleStages: Int = 0,
) : BlockRenderer {
    private val oversampler: Oversampler? =
        if (oversampleStages > 0) Oversampler(oversampleStages) else null
    // ... existing fields ...

    override fun render(ctx: BlockContext) {
        if (amount <= 0.0) return
        if (oversampler != null) renderOversampled(ctx) else renderDirect(ctx)
    }
}
```

### Step 3: Ignitor distort/clip

**File:** `audio_be/src/commonMain/kotlin/ignitor/IgnitorEffects.kt`

Add `oversampleStages: Int = 0` to:

- `Ignitor.distort(amount: Ignitor, shape, oversampleStages)` — Oversampler in closure
- `Ignitor.distort(amount: Double, shape, oversampleStages)` — convenience overload
- `Ignitor.clip(shape, oversampleStages)` — same pattern without drive

### Step 4: Data model plumbing

**audio_bridge/VoiceData.kt** — add field:

```kotlin
val distortOversample: Int?,
```

**audio_bridge/IgnitorDsl.kt** — add `oversample: Int = 0` to:

- `IgnitorDsl.Distort` data class
- `IgnitorDsl.Clip` data class
- Builder functions `IgnitorDsl.distort()` and `IgnitorDsl.clip()`

**audio_be/Voice.kt** — update:

```kotlin
class Distort(val amount: Double, val shape: String = "soft", val oversample: Int = 0)
```

**audio_be/VoiceFactory.kt** — wire:

```kotlin
val distort = Voice.Distort(
    amount = data.distort ?: 0.0,
    shape = data.distortShape ?: "soft",
    oversample = data.distortOversample ?: 0,
)
```

**audio_be/FilterPipelineBuilder.kt** — pass through:

```kotlin
if (distort.amount > 0.0) {
    add(DistortionRenderer(distort.amount, distort.shape, distort.oversample))
}
```

**audio_be/IgnitorDslRuntime.kt** — wire both Distort and Clip cases to pass `oversample`.

### Step 5: Sprudel DSL

**sprudel/SprudelVoiceData.kt** — add `distortOversample: Int?`, update `mergeWith()` and
`toVoiceData()`.

**sprudel/lang/lang_effects.kt** — two changes:

1. Extend `distortMutation` to parse 3rd colon segment:
   `"0.8:exp:2"` -> `distort=0.8, distortShape=exp, distortOversample=2`

2. Add new `distos()` function (alias: `distortOversampling`) — voiceModifier + full set of
   DSL overloads (SprudelPattern, String, PatternMapperFn, standalone). Maps to
   `distortOversample` field.

## Testing

```bash
./gradlew :audio_be:jvmTest         # Oversampler + DistortionRenderer
./gradlew :audio_bridge:jvmTest     # Serialization with new fields
./gradlew :sprudel:jvmTest          # DSL parsing of distos() and colon syntax
```

**New tests: OversamplerSpec**

- Passthrough: stages=0, output = transform(input)
- Identity: sine through identity transform at 2x → output matches input
- Anti-alias: high-freq sine through hard clipper, measure above-Nyquist content
- Block boundary: two consecutive blocks → no discontinuity

**Manual listening test:** DISCO FOREVER lead — change `distort("0.8:exp")` to
`distort("0.8:exp:2")` or `.distort("0.8:exp").distos(2)` and compare.

### Migrating existing lazy buffers (Step 6)

Before changing FM/pitch mod to use ScratchBuffers, pin down existing behavior with tests
(if not already covered). Then migrate:

- `IgnitorPitchMod.kt` — vibrato, accelerate, pitchEnvelope: replace `var modBuf: DoubleArray?`
  with `ctx.scratchBuffers.useDouble {}`
- `IgnitorFm.kt` — FM: replace `var modBuf: FloatArray?` with `ctx.scratchBuffers.use {}`,
  replace `var phaseModBuf: DoubleArray?` with `ctx.scratchBuffers.useDouble {}`
