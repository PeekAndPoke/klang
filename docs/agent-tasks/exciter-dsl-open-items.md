# ExciterDsl — Open Items

## Per-Playback Numerical Attributes (CPS etc.)

Let the frontend push named numerical values (like `cps`) to the audio backend per-playback.
Exciters read these at audio rate for tempo-synced effects.

### Design

```
Frontend                          Bridge                          Backend
─────────                         ──────                          ───────
KlangPlayback.updateAttributes()
  → Cmd.SetAttribute(playbackId, name, value)
                                  → WorkletContract encode/decode
                                                                  → PlaybackCtx.attributes[name] = value
                                                                  → ExciteContext.attributes (ref to PlaybackCtx map)

ExciterDsl.Attribute("cps")       → at render time reads from ctx.attributes["cps"]
```

### Steps

1. `audio_bridge/KlangCommLink.kt` — add `Cmd.SetAttribute(playbackId, name: String, value: Double)`
2. `audio_be/voices/PlaybackCtx.kt` — add `val attributes: MutableMap<String, Double>`
3. `audio_be/exciter/ExciteContext.kt` — add `var attributes: Map<String, Double>`
4. `audio_be/voices/VoiceScheduler.kt` — handle `Cmd.SetAttribute`
5. `audio_bridge/ExciterDsl.kt` — add `ExciterDsl.Attribute(name: String)` node type
6. `audio_be/exciter/ExciterDslRuntime.kt` — runtime exciter reads `ctx.attributes[name]`
7. `klangscript/KlangScriptOsc.kt` — `Osc.cps()` → `ExciterDsl.Attribute("cps")`, `Osc.attr(name)`
8. `klang/KlangPlayback.kt` — `updateAttributes(attrs: Map<String, Double>)`
9. Sprudel integration — `SprudelPlayback` calls `playback.updateAttributes(mapOf("cps" to cps))`

### Usage

```javascript
let pad = Osc.register("syncPad",
    Osc.supersaw().tremolo(Osc.cps(), 0.5).lowpass(2000).adsr(0.01, 0.3, 0.5, 0.5)
)
note("c3 e3 g3").sound(pad)
```

---

## splitAndJoin Operator

Split a signal into parallel branches, process each independently, sum and normalize.

### Design

```javascript
Osc.supersaw().splitAndJoin(
    x => x.octaveUp(),
    x => x.octaveDown(),
    x => x.detune(7)
)
// Desugars to: (branch0 + branch1 + branch2) / 3
```

Each branch gets its own copy of the source tree (independent state).
Future `splitSignalAndJoin()` would share the same source buffer (true signal split).

### Implementation

- KlangScript: vararg arrow functions, call each with self, collect ExciterDsl results
- Register manually (like `Osc.register()`) since it needs to invoke KlangScript lambdas
- Result: `Plus(Plus(b0, b1), b2).div(Constant(numBranches))`

---

## Additional Arithmetic

- `exp()` — `e^self` for exponential curves (dB-to-linear, envelope shaping)
- `abs()` — absolute value
- `neg()` — negate signal (flip polarity)

Each needs: ExciterDsl subtype, runtime Exciter extension, KlangScript extension method, collectParams.

---

## Impulse Response Convolution

### Goal

Enable realistic instrument body modeling, cabinet simulation, and room reverb via impulse response convolution.
Designed for incremental implementation — start with simple FIR, swap in FFT later without changing the API.

### DSL Node

One node for all convolution variants:

```kotlin
data class Convolve(
    val inner: ExciterDsl,
    val ir: String,  // named IR reference (like sound names in ExciterRegistry)
) : ExciterDsl
```

### IR Registry

Mirrors `ExciterRegistry` — named lookup with auto-selected strategy:

```kotlin
interface ConvolveStrategy {
    fun process(input: FloatArray, output: FloatArray, offset: Int, length: Int)
}

class IrRegistry {
    fun register(name: String, samples: FloatArray) {
        val strategy = when {
            samples.size <= 512 -> FirConvolve(samples)       // direct, zero latency
            samples.size <= 8192 -> PartitionedFftConvolve(samples)  // future
            else -> FftConvolve(samples)              // future
        }
        entries[name] = IrEntry(samples, strategy)
    }
}
```

### Implementation steps

**Prerequisite — SampleLibrary cleanup**
The existing SampleLibrary infrastructure will be reused for loading IR WAV files.
Before starting IR work, the SampleLibrary needs cleanup/refactoring to support this use case cleanly.

**Step 1 — FIR convolution + IR loading (the "cheap" start)**
One implementation, two data sources:

- `FirConvolve(ir: FloatArray)` — direct time-domain convolution, ~50 lines of DSP
- Generated IRs: `ir = generateGuitarBodyIr(warmth = 0.5)` — math-based body/cabinet models
- Loaded IRs: `ir = sampleLibrary.load("ir/marshall4x12")` — WAV files via existing sample transport
- Both are short (256-1024 samples), both use the same FIR engine
- Zero latency, simple implementation
- Reuses existing `SampleRequest` / `Cmd.Sample.Complete` / `Cmd.Sample.Chunk` infrastructure
- Naming convention: `ir/` prefix distinguishes IRs from playback samples
- Good for: cabinet IRs, mic IRs, instrument body IRs

**Step 2 — Partitioned FFT (medium IRs, future)**

- `PartitionedFftConvolve` — split IR into chunks, convolve via FFT, overlap-add
- Good for: plate reverb, spring reverb (up to ~8192 samples)
- Requires FFT implementation

**Step 3 — Full FFT (long IRs, future)**

- `FftConvolve` — full frequency-domain convolution
- Good for: room reverbs, cathedral (seconds of tail)
- Bigger project

### KlangScript API — shorthands with parameters

```javascript
// Low level — raw IR reference
Osc.pluck().convolve("guitar-body-dreadnought")

// User-friendly parameterized shorthands
Osc.pluck().guitarBody("dreadnought")
Osc.pluck().guitarBody("classical", 0.7)        // warmth param
Osc.saw().distort(0.5).cabinet("marshall", 0.5)  // mic position
Osc.sine().room("cathedral", 0.9, 0.3)           // size, damping
Osc.pluck().violinBody(0.6)                       // brightness
```

Each shorthand:

1. Selects or generates the right IR based on parameters
2. Calls `.convolve(irName)` internally

### IR sources

**Built-in generated** — mathematical models for common body/cabinet shapes:

```kotlin
fun generateGuitarBodyIr(warmth: Double, size: Double): FloatArray {
    val ir = FloatArray(512)
    // Sum decaying sinusoids at resonant frequencies
    // Guitar body resonances: ~100Hz, ~400Hz, ~800Hz, ~2kHz
    addResonance(ir, freq = 100 * (1 - warmth * 0.3), decay = 0.95, amp = 0.5)
    addResonance(ir, freq = 400 * (1 - warmth * 0.2), decay = 0.90, amp = 0.3)
    addResonance(ir, freq = 800, decay = 0.85, amp = 0.2)
    addResonance(ir, freq = 2000 * (1 + warmth * 0.3), decay = 0.80, amp = 0.1)
    return ir
}
```

**Pre-baked** — named presets for specific gear ("marshall", "fender", "cathedral")

**User-loaded** — WAV file upload (future, needs sample loading infrastructure)

### Files involved

| File                                      | Change                                                           |
|-------------------------------------------|------------------------------------------------------------------|
| `audio_bridge/ExciterDsl.kt`              | Add `Convolve(inner, ir)` node                                   |
| `audio_be/exciter/ExciterDslRuntime.kt`   | Handle `Convolve` → look up IR, create convolver                 |
| `audio_be/exciter/ir/IrRegistry.kt`       | NEW — named IR storage with strategy selection                   |
| `audio_be/exciter/ir/FirConvolve.kt`      | NEW — direct time-domain convolution                             |
| `audio_be/exciter/ir/ConvolveStrategy.kt` | NEW — interface for convolution variants                         |
| `audio_jsworklet/KlangAudioWorklet.kt`    | Initialize IrRegistry alongside ExciterRegistry                  |
| `klangscript/KlangScriptOscExtensions.kt` | Add `.convolve()`, `.guitarBody()`, `.cabinet()`, `.room()` etc. |

### Electric guitar signal chain — multiple IRs

A realistic electric guitar needs two IRs in series:

```
String vibration
  → Guitar body IR (wood resonance, pickup position)     ~256-512 samples
    → Amp (preamp gain, EQ, distortion — NOT an IR)
      → Cabinet IR (speaker + mic response)               ~512-1024 samples
        → Room IR (optional — the space the cab is in)     ~4800-144000 samples
```

In our DSL:

```javascript
Osc.pluck()
    .guitarBody("stratocaster")     // IR 1: guitar body
    .distort(0.6)                   // amp (just processing, not IR)
    .lowpass(4000)                  // amp tone stack
    .cabinet("marshall4x12")        // IR 2: speaker cabinet + mic
```

The cabinet IR is the most impactful — 80% of the "amp sound" comes from the speaker, not the preamp.
Guitar body IR matters less for electric (pickups bypass acoustic resonance) but adds character.

### How IRs are captured (theory)

**Method 1 — Sine sweep (professional standard):**

1. Attach transducer to the instrument/speaker
2. Play logarithmic sine sweep (20Hz → 20kHz over ~10s)
3. Record the output
4. Deconvolve: `IR = IFFT(FFT(recorded) / FFT(original_sweep))`

**Method 2 — Direct impulse (simple):**
Tap the bridge/speaker cone, record the response. Quick but noisy.

**Method 3 — MLS/white noise:**
Play white noise, cross-correlate input and output. Fast, lower quality.

### What an IR contains (guitar body example)

- **~0-1ms:** Initial transient (wood "click")
- **~1-5ms:** Body resonances ringing (peaks at ~100Hz, ~400Hz, ~800Hz, ~2kHz)
- **~5-10ms:** Decay tail — wood absorbing energy
- Frequency peaks define the character: dreadnought vs classical vs violin body

### Free & open-source IR resources

**Large free collections:**

- [Overdriven.fr](https://overdriven.fr/overdriven/index.php/irdownloads/) — extensive free guitar cabinet IR library
- [Djammincabs](https://zystrix.com/djammincabs.htm) — 200 free guitar cab IRs + 200 free bass cab IRs
- [Origin Effects IR Cab Library](https://origineffects.com/product/ir-cab-library/) — free vintage cab collection
- [PreSonus 25 Analog Cab IRs](https://www.presonus.com/blogs/home/free-download-25-analog-cab-irs) — 25 free analog
  cabinet IRs

**Curated lists:**

- [Neural DSP community list](https://unity.neuraldsp.com/t/list-of-ir-sites-paid-or-free/10016) — comprehensive list of
  free and paid IR sources
- [Produce Like A Pro — Best Guitar IRs 2025](https://producelikeapro.com/blog/best-guitar-impulse-responses/) — top 20
  rated
- [Line 6 community IR links](https://line6.com/support/topic/17076-links-for-free-impulse-responses-ir-here/) —
  community-collected

**Specialty:**

- [Worship Tutorials Acoustic IR Pack](https://worshiptutorials.com/product/acoustic-ir-sample-pack/) — free acoustic
  guitar body IRs (rare)

**Format:** Typically WAV, 44.1/48kHz, mono. Cabinet IRs are 512-4096 samples (perfect for our FIR step 1).

---

## Minor Items

- KSP: nested type alias resolution only one level deep (latent — no chained aliases exist today)
- `Osc.register()` casts without validation — wrong arg types produce ClassCastException instead of user-friendly error
