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
    0.33261825699561426,   // +/-1
    -0.11553340575436945,  // +/-3
    0.046063814906802995,  // +/-5
    -0.013148666148047813, // +/-7
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

## Code Review Findings (2026-04-09)

Post-implementation review by software engineer + audio/DSP engineer.

### CRITICAL — DSP Quality

| #  | Finding                                                                                                                                                                                                                                                           | Impact                                                                               |
|----|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| A1 | **Half-band FIR stopband rejection is ~20 dB, not ~60 dB.** 15-tap with 4 free parameters can't exceed ~30-35 dB equiripple. Current coefficients do worse.                                                                                                       | Aliased images only suppressed ~20 dB in worst case — audible as metallic harshness. |
| A2 | **Linear interpolation images intermodulate through the nonlinearity.** At 20 kHz the image is only -11 dB down. Waveshaper creates in-band intermod products (e.g., 8 kHz difference tone from 20k + 28k image) that cannot be removed by the decimation filter. | In-band artifacts on bright material.                                                |
| A5 | **No anti-imaging filter between upsample and nonlinearity.** Half-band FIR only runs on decimation side. Images from upsampling pass through the waveshaper unfiltered.                                                                                          | Compounds with A2 — the root cause of image intermodulation.                         |

**Recommended fix for A1+A2+A5:** Replace linear interpolation with **zero-stuffing + half-band FIR** (reuse same filter
for both upsample and downsample paths). Upgrade to **31-43 tap** half-band for genuine 50-60 dB rejection (~8-11
symmetric MACs per output sample vs current 5).

### MEDIUM — Software Engineering

| #  | Finding                                                                                                                                                                                 | File                      | Impact                                                                                            |
|----|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------|---------------------------------------------------------------------------------------------------|
| S1 | `upsample()` never writes `curr` itself — systematic half-sample bias. Writes `prev + step*j` for `j in 0..f-1`, so the current sample value is missing from the output.                | `Oversampler.kt:66-73`    | Slight lowpass + phase shift beyond what the FIR introduces. Moot if we switch to zero-stuff+FIR. |
| S2 | `ScratchBuffers.reset()` doesn't reset `doubleNextFree` or `oversampleCache` sub-pools.                                                                                                 | `ScratchBuffers.kt:37-39` | Asymmetry could cause stale pointers if reset() usage patterns change.                            |
| S3 | `Oversampler.process()` crashes on `length == 0` — reads `buffer[offset - 1]` in `upsample()`.                                                                                          | `Oversampler.kt:76`       | ArrayIndexOutOfBounds on zero-length blocks.                                                      |
| S4 | `oversampleCache` uses `mutableMapOf<Int, ScratchBuffers>()` — `getOrPut()` boxes Int key in Kotlin/JS on every call.                                                                   | `ScratchBuffers.kt:69`    | Hot path boxing overhead.                                                                         |
| S5 | Shared `Oversampler` state in Ignitor closures — if same Ignitor tree shared across voices, filter state corrupts. Matches existing DC blocker pattern but oversampler state is larger. | `IgnitorEffects.kt:31-84` | Needs verification: are Ignitor closures per-voice or shared?                                     |

### MEDIUM — Audio Quality

| #  | Finding                                                                                                                                           | Impact                                                                |
|----|---------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------|
| A3 | +0.8 dB passband ripple at 16 kHz. Compounds with chained oversampled effects.                                                                    | Bright/harsh coloration in upper midrange.                            |
| A4 | No latency compensation for FIR group delay (3.5 samples at 2x). If mixed with dry path → comb filter null at ~6.9 kHz.                           | Relevant for dry/wet mix scenarios.                                   |
| A6 | `sineFold` with high drive generates harmonics at every integer multiple — even 4x oversampling can't suppress them all with this filter quality. | Fold shape benefits most from oversampling but also demands the most. |

### LOW / NIT

| #   | Finding                                                                                               | File                      |
|-----|-------------------------------------------------------------------------------------------------------|---------------------------|
| S6  | `Voice.Distort.oversample` stores stages but name implies factor. Should be `oversampleStages`.       | `Voice.kt:199`            |
| S7  | No `reset()` on Oversampler for voice recycling — stale filter state may bleed into new note attack.  | `Oversampler.kt`          |
| S8  | Transform lambda `(Float) -> Float` boxes per sample in Kotlin/JS. `inline` on `process()` would fix. | `Oversampler.kt:31-57`    |
| S9  | No validation on `stages` param — negative values cause `1 shl stages` → 0 or undefined.              | `Oversampler.kt:16`       |
| S10 | DC blocker logic duplicated in 4 places.                                                              | Multiple files            |
| S11 | Test constant `HALF_LEN_WARMUP = 15` disconnected from `Oversampler.TAPS`.                            | `OversamplerSpec.kt:168`  |
| A7  | No denormal flush on FIR output path. Fine for JS (hardware FTZ), JVM risk.                           | `Oversampler.kt:110-124`  |
| A8  | Oversample buffer pools never shrink — slow memory creep in long sessions.                            | `ScratchBuffers.kt:67-78` |
| A9  | DC blocker coefficient `0.995` hardcoded, not sample-rate-adaptive.                                   | Multiple files            |
