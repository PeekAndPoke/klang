# Add `ExciterDsl.Freq` to replace magic `Constant(0.0)` convention

## Problem

All oscillators in the ExciterDsl hierarchy default their `freq` parameter to `Constant(0.0)`, where `0` magically
means "use the voice's note frequency." This is confusing API design — a numeric constant shouldn't carry semantic
meaning based on its value.

## Solution

Introduce an explicit `ExciterDsl.Freq` data object that semantically means "use the voice frequency." This makes intent
clear, removes the magic value, and is reusable anywhere in the signal graph (e.g., as a filter cutoff that tracks the
played note).

After this change:

- `Freq` → use the frequency of the voice (e.g., 440 Hz for A4)
- `Constant(n)` → use a fixed constant frequency in Hz
- `Param(name, default)` → param slot with default value, overridable at play time

## Files to modify

| File                                              | Change                                                                                              |
|---------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| `audio_bridge/.../ExciterDsl.kt`                  | Add `Freq` data object; change all `freq` defaults from `Constant(0.0)` to `Freq`; update docblocks |
| `audio_be/.../exciter/FreqExciter.kt`             | **New file** — runtime exciter that fills buffer with voice frequency                               |
| `audio_be/.../exciter/ExciterDslRuntime.kt`       | Map `ExciterDsl.Freq` → `FreqExciter`                                                               |
| `audio_be/.../exciter/Exciters.kt`                | Add `FreqExciter` fast path in `resolveFreq`                                                        |
| `klangscript/.../stdlib/KlangScriptOsc.kt`        | Change factory defaults from `0.0` to `ExciterDsl.Freq`; add `freq()` method; update docs           |
| `audio_bridge/.../ExciterDslSerializationTest.kt` | Add `Freq` round-trip test                                                                          |
| `audio_be/.../exciter/ExciterDslRuntimeTest.kt`   | Add `Freq` runtime test                                                                             |
| `klangscript/.../stdlib/StdLibOscTest.kt`         | Update default-freq test; add `Osc.freq()` test                                                     |

**Not changed:** `ExciterDefaults.kt` — keeps `Param("freq", 0.0)` for sprudel `oscParam()` backward compat.

## Steps

### 1. Add `ExciterDsl.Freq` data object

In `ExciterDsl.kt`, after `Constant`, before the oscillator section:

```kotlin
/** The voice's note frequency (e.g. 440 Hz for A4). Use anywhere a frequency value is needed to track the played note. */
@Serializable
@SerialName("freq")
data object Freq : ExciterDsl {
    override fun collectParams(out: MutableList<Param>) {}
}
```

Update section comment (lines 60-64) to reference `Freq` instead of "0 = voice freq."

Change default `freq` on all 15 oscillator data classes: `Constant(0.0)` → `Freq`
(Sine, Sawtooth, Square, Triangle, Zawtooth, Impulse, Pulze, Ramp, SuperSaw, SuperSine, SuperSquare, SuperTri,
SuperRamp, Pluck, SuperPluck)

### 2. Create `FreqExciter`

New file `audio_be/src/commonMain/kotlin/exciter/FreqExciter.kt`:

```kotlin
/** Runtime exciter that fills buffer with the voice frequency. Runtime representation of [ExciterDsl.Freq]. */
object FreqExciter : Exciter {
    override fun generate(buffer: FloatArray, freqHz: Double, ctx: ExciteContext) {
        buffer.fill(freqHz.toFloat(), ctx.offset, ctx.offset + ctx.length)
    }
}
```

### 3. Wire up runtime

In `ExciterDslRuntime.kt`, add case after `Constant`:

```kotlin
is ExciterDsl.Freq -> FreqExciter
```

### 4. Update `resolveFreq`

In `Exciters.kt`, add `FreqExciter` fast path as first check:

```kotlin
if (freq is FreqExciter) return voiceFreqHz
```

Keep existing `ParamExciter` and `f == 0.0` checks for backward compat.

### 5. Update KlangScript stdlib

In `KlangScriptOsc.kt`:

- All factory methods: `freq: ExciterDslLike = 0.0` → `freq: ExciterDslLike = ExciterDsl.Freq`
- Add `fun freq(): ExciterDsl = ExciterDsl.Freq` factory method
- Update section comments and per-method KDoc

### 6. Update tests

- **Serialization**: Add `Freq` round-trip test
- **Runtime**: Add test that `Freq.toExciter()` fills buffer with voice frequency
- **StdLibOscTest**: Change test at line 46 from checking `Constant(0.0)` to checking `Freq`; add `Osc.freq()` test

### 7. Regenerate KSP + verify

```bash
./gradlew :klangscript:kspCommonMainKotlinMetadata
./gradlew :audio_bridge:allTests :audio_be:allTests :klangscript:allTests
```

## Backward compatibility

- Serialized trees with `{"type":"const","value":0.0}` still deserialize as `Constant(0.0)` and `resolveFreq` still
  handles them via the `ParamExciter`/fallback paths
- `ExciterDefaults.kt` keeps `Param("freq", 0.0)` for sprudel oscParam override support
- No `@SerialName` collision — "freq" is unused in the hierarchy
