# Composable Signal Generators (`SignalGen`)

## Problem

Current oscillators use `OscFn` where the caller (SynthVoice) owns phase state and passes pre-computed `phaseInc`. This
works for single oscillators but cannot support:

- **Composition**: `(sine + square).div(2)` — needs two independent phase accumulators
- **Detune**: `sine + sine.detune(7)` — needs different frequencies per sub-oscillator
- **Physical modeling**: Karplus-Strong — doesn't use phase at all, uses delay lines driven by `freqHz`
- **Exciter patterns**: "use noise burst to excite a resonator" — one signal feeds into another

The supersaw already breaks the `OscFn` contract (manages own phases internally, returns `0.0`). This shows the
abstraction is already leaking.

## Design Goal

A single abstraction (`SignalGen`) that unifies:

| Synthesis Method                      | How `freqHz` is used                      | State                   |
|---------------------------------------|-------------------------------------------|-------------------------|
| Phase oscillators (sine, saw, square) | `phaseInc = 2π * freqHz / sampleRate`     | Phase accumulator       |
| Wavetable                             | Phase indexes into table                  | Phase + table reference |
| Karplus-Strong                        | Delay line length = `sampleRate / freqHz` | Delay line buffer       |
| Supersaw / Unison                     | Multiple detuned phases                   | Phase array             |
| Noise                                 | Ignored                                   | RNG state               |
| Composition (`a + b`)                 | Passed through to children                | Children's state        |

All of these produce "a block of samples given a frequency" — that's the abstraction.

---

## Core Interface

```kotlin
fun interface SignalGen {
    fun generate(
        buffer: DoubleArray,
        offset: Int,
        length: Int,
        freqHz: Double,
        sampleRate: Int,
        phaseMod: DoubleArray?,  // per-sample freq multipliers (1.0 = no change)
    )
}
```

**Key design decisions:**

1. **No phase in/out** — each node owns its state internally. This is what supersaw already does.
2. **`freqHz` instead of `phaseInc`** — enables detune wrappers (`freqHz * ratio`), and non-phase synthesis (Karplus
   delay = `sampleRate / freqHz`).
3. **No return value** — state is encapsulated. Simpler interface, no "what does the return mean for composites"
   problem.
4. **`sampleRate` passed per call** — keeps nodes free of system config, though it's constant in practice.

---

## Synthesis Primitives

### Phase-Based Oscillators

Each factory returns a `SignalGen` with captured `var phase`:

```kotlin
object SignalGens {
    fun sine(gain: Double = 1.0): SignalGen {
        var phase = 0.0
        return SignalGen { buffer, offset, length, freqHz, sampleRate, phaseMod ->
            val phaseInc = TWO_PI * freqHz / sampleRate
            // ... same inner loop as current sineFn, using captured `phase` ...
        }
    }

    fun sawtooth(gain: Double = 0.6): SignalGen { /* PolyBLEP, captured phase */
    }
    fun square(gain: Double = 0.5): SignalGen { /* ... */
    }
    fun triangle(gain: Double = 0.7): SignalGen { /* ... */
    }
    fun zawtooth(gain: Double = 1.0): SignalGen { /* ... */
    }
    fun pulze(duty: Double = 0.5, gain: Double = 1.0): SignalGen { /* ... */
    }
    fun impulse(): SignalGen { /* ... */
    }

    // Noise (ignores freqHz)
    fun whiteNoise(rng: Random, gain: Double = 1.0): SignalGen { /* ... */
    }
    fun brownNoise(rng: Random, gain: Double = 1.0): SignalGen { /* ... */
    }
    fun pinkNoise(rng: Random, gain: Double = 1.0): SignalGen { /* ... */
    }

    // Already internally stateful — fits naturally
    fun supersaw(voices: Int, freqSpread: Double, rng: Random, gain: Double = 0.6): SignalGen { /* ... */
    }
}
```

### Physical Modeling: Karplus-Strong

Karplus-Strong is the key test of the architecture. It uses `freqHz` completely differently:

```kotlin
fun karplus(
    exciter: SignalGen,           // What fills the delay line (noise, impulse, custom)
    damping: Double = 0.5,       // Low-pass filter strength (0=bright, 1=dark)
    feedback: Double = 0.998,    // How long the string rings
): SignalGen {
    // State
    var delayLine: DoubleArray? = null
    var writePos = 0
    var excited = false
    var lastOut = 0.0

    return SignalGen { buffer, offset, length, freqHz, sampleRate, phaseMod ->
        val delayLength = (sampleRate.toDouble() / freqHz).toInt().coerceAtLeast(2)

        // Initialize or resize delay line
        if (delayLine == null || delayLine!!.size != delayLength) {
            delayLine = DoubleArray(delayLength)
            writePos = 0
            excited = false
        }

        // Fill delay line with exciter signal on first call
        if (!excited) {
            exciter.generate(delayLine!!, 0, delayLength, freqHz, sampleRate, null)
            excited = true
        }

        // Karplus-Strong loop
        val dl = delayLine!!
        val end = offset + length
        for (i in offset until end) {
            val readPos = writePos
            val nextPos = (writePos + 1) % delayLength

            // Read + average filter (basic Karplus-Strong)
            val current = dl[readPos]
            val next = dl[nextPos]
            val filtered = feedback * (current * (1.0 - damping) + next * damping)

            // One-pole smoothing
            val out = filtered + 0.0 * (lastOut - filtered)
            lastOut = out

            buffer[i] = out

            // Write filtered value back
            dl[writePos] = filtered
            writePos = (writePos + 1) % delayLength
        }
    }
}
```

**Key insight**: The **exciter is itself a `SignalGen`**. This means you can excite with:

- `whiteNoise(rng)` — classic plucked string
- `impulse()` — sharp attack
- `sine()` — bowed string character
- Any composed signal — creative sound design

Usage: `karplus(exciter = whiteNoise(rng), damping = 0.3)`

This is a different kind of composition than arithmetic (`a + b`). It's **structural composition** — one signal
configures/feeds another.

---

## Composition Operators

### Scratch Buffer Management

Binary operators need temp buffers. Stack-based pool, pre-allocated:

```kotlin
class ScratchBuffers(blockFrames: Int, initialCapacity: Int = 4) {
    private val pool = ArrayList<DoubleArray>(initialCapacity)
    private var nextFree = 0

    fun acquire(): DoubleArray {
        if (nextFree >= pool.size) pool.add(DoubleArray(pool[0].size))
        return pool[nextFree++]
    }
    fun release() {
        nextFree--
    }
    fun reset() {
        nextFree = 0
    }
}
```

Max simultaneous buffers = composition tree depth. `(a + b).mul(0.5) + c` needs 2.

### Arithmetic Operators

```kotlin
// Binary (capture ScratchBuffers at construction)
fun SignalGen.plus(other: SignalGen, scratch: ScratchBuffers): SignalGen {
    return SignalGen { buffer, offset, length, freqHz, sampleRate, phaseMod ->
        this.generate(buffer, offset, length, freqHz, sampleRate, phaseMod)
        val tmp = scratch.acquire()
        other.generate(tmp, offset, length, freqHz, sampleRate, phaseMod)
        for (i in offset until offset + length) buffer[i] += tmp[i]
        scratch.release()
    }
}

fun SignalGen.times(other: SignalGen, scratch: ScratchBuffers): SignalGen  // Ring mod

// Unary (no scratch needed)
fun SignalGen.mul(factor: Double): SignalGen   // Scale amplitude
fun SignalGen.div(divisor: Double): SignalGen = mul(1.0 / divisor)
```

### Frequency Operators

```kotlin
fun SignalGen.detune(semitones: Double): SignalGen {
    val ratio = 2.0.pow(semitones / 12.0)
    return SignalGen { buf, off, len, freqHz, sr, pm ->
        this.generate(buf, off, len, freqHz * ratio, sr, pm)
    }
}

fun SignalGen.octaveUp(): SignalGen = detune(12.0)
fun SignalGen.octaveDown(): SignalGen = detune(-12.0)
```

### Post-Processing

```kotlin
fun SignalGen.withWarmth(factor: Double): SignalGen  // One-pole LPF (existing pattern)
fun SignalGen.withGain(gain: Double): SignalGen = mul(gain)
```

### DSL Scope

For clean operator syntax without passing `scratch` everywhere:

```kotlin
class SignalGenScope(private val scratch: ScratchBuffers) {
    val sine get() = SignalGens.sine()
    val saw get() = SignalGens.sawtooth()
    val square get() = SignalGens.square()
    val triangle get() = SignalGens.triangle()

    operator fun SignalGen.plus(other: SignalGen) = this.plus(other, scratch)
    operator fun SignalGen.times(other: SignalGen) = this.times(other, scratch)

    fun karplus(exciter: SignalGen, damping: Double = 0.5) =
        SignalGens.karplus(exciter, damping)
}

// Usage:
signalGen(scratch) { (sine + square).div(2) }
signalGen(scratch) { sine.mul(0.7) + saw.mul(0.3) }
signalGen(scratch) { sine + sine.detune(7) }
signalGen(scratch) { karplus(exciter = whiteNoise(rng)) }
```

---

## Backward Compatibility

### OscFn Bridge

```kotlin
fun OscFn.toSignalGen(): SignalGen {
    var phase = 0.0
    return SignalGen { buf, off, len, freqHz, sr, pm ->
        val phaseInc = TWO_PI * freqHz / sr
        phase = this@toSignalGen.process(buf, off, len, phase, phaseInc, pm)
    }
}
```

This allows incremental migration. Existing `OscFn` implementations work immediately.

---

## Integration with Voice Pipeline

### SynthVoice Changes

```kotlin
class SynthVoice(
    // REMOVE: osc: OscFn, phaseInc: Double, phase: Double
    // ADD:
    private val signal: SignalGen,
    private val freqHz: Double,
    // ... rest unchanged ...
) : AbstractVoice(...) {

    override fun getBaseFrequency(): Double = freqHz

    override fun generateSignal(ctx, offset, length, pitchMod) {
        signal.generate(ctx.voiceBuffer, offset, length, freqHz, ctx.sampleRate, pitchMod)
    }
}
```

### Voice.RenderContext Changes

```kotlin
class RenderContext(
    val orbits: Orbits,
    val sampleRate: Int,
    val blockFrames: Int,
    val voiceBuffer: DoubleArray,
    val freqModBuffer: DoubleArray,
    val scratchBuffers: ScratchBuffers,  // NEW
)
```

### VoiceScheduler Changes

```kotlin
// Before:
val osc = data.createOscillator(oscillators, freqHz)
val phaseInc = TWO_PI * freqHz / sampleRate.toDouble()
SynthVoice(osc = osc, freqHz = freqHz, phaseInc = phaseInc)

// After (phase 1 — bridge):
val signal = data.createOscillator(oscillators, freqHz).toSignalGen()
SynthVoice(signal = signal, freqHz = freqHz)

// After (phase 2 — native):
val signal = data.createSignalGen(oscillators, ctx.scratchBuffers)
SynthVoice(signal = signal, freqHz = freqHz)
```

---

## Performance Notes

- **Primitive inner loops**: identical to current `OscFn` — `phaseInc` computed once per block, tight for-loop
- **Binary composition**: one extra 128-sample buffer pass per operator — trivial vs. transcendental functions (sin,
  asin)
- **Detune**: one multiply per block (not per sample)
- **Scratch buffers**: stack allocator, pre-allocated, zero GC
- **Karplus-Strong**: delay line read/write per sample — cheap, no transcendentals

---

## Files to Create/Modify

| File                                    | Change                                                        |
|-----------------------------------------|---------------------------------------------------------------|
| `audio_be/.../osci/SignalGen.kt`        | **NEW** — interface, `ScratchBuffers`, composition extensions |
| `audio_be/.../osci/SignalGens.kt`       | **NEW** — all primitive factories                             |
| `audio_be/.../osci/Oscillators.kt`      | Add `OscFn.toSignalGen()` bridge                              |
| `audio_be/.../voices/Voice.kt`          | Add `scratchBuffers` to `RenderContext`                       |
| `audio_be/.../voices/SynthVoice.kt`     | Replace `osc`/`phase`/`phaseInc` with `signal: SignalGen`     |
| `audio_be/.../voices/VoiceScheduler.kt` | Wire `SignalGen` via bridge, remove `phaseInc` calc           |
| Test helpers + tests                    | Update for new `SynthVoice` signature                         |

## Migration Steps

1. Add `SignalGen`, `ScratchBuffers`, primitives, operators (no existing code touched)
2. Add `OscFn.toSignalGen()` bridge
3. Update `SynthVoice` → `SignalGen`
4. Update `VoiceScheduler` (use bridge initially)
5. Update `RenderContext` + all creation sites
6. Update tests
7. (Later) Port `Oscillators` registry to native `SignalGen`, deprecate `OscFn`

## Resolved Decisions

- **SignalGen is always per-voice**: Every voice gets a fresh `SignalGen` instance. Oscillators have internal state (
  phase, delay lines, filter memory), so sharing would cause cross-voice interference. Factory functions create fresh
  instances.
- **Language comes later**: A dedicated "osci-lang" will be designed to express custom oscillators. It will compile down
  to instantiated `SignalGen` object trees. Build the runtime layer first, work up to the language from there.
- **Bottom-up approach**: Implement `SignalGen` + primitives + composition operators first. Validate with tests. Then
  integrate into the voice pipeline. Language/syntax is a separate future task.

---

## Six Hats Analysis (2026-03-17)

### Critical Finding: `freqHz` Scalar vs Buffer

Every perspective converged on this as the most consequential design decision. Scalar `freqHz` is clean for 90% of
cases, but:

- Prevents smooth per-sample pitch modulation (staircase artifacts)
- Limits the feedback combinator (which needs per-sample timing precision)
- Would require a signature-breaking change to retrofit later

**Resolution**: Design an extensibility seam from day one. Options:

1. `Param` sealed class: `Param.Const(Double)` / `Param.Buffer(DoubleArray)` — zero-cost abstraction
2. Second `generate` overload accepting `DoubleArray` for freqHz, with scalar version as convenience wrapper
3. Keep scalar but design internal loops so swapping is a one-line change per primitive

### Missing Features (by priority)

1. **Feedback combinator** (from Faust's `~` operator) — highest-leverage gap
    - Pattern: `A.feedback(delaySamples) { transform }`
    - Collapses Karplus-Strong, comb filters, FM feedback (DX7 op6→6), waveguide into composition algebra
    - Eliminates need for Karplus-Strong as a special-cased class
    - Example: `sine.feedback(1) { it.times(feedbackAmount) }` = FM self-modulation
    - Example: `noise.feedback(pitchSamples) { it.lowpass(damping) }` = Karplus-Strong

2. **Buffer-rate frequency** — see above. At minimum, design the seam.

3. **LFO / control-rate signals** — inspired by SuperCollider's `.ar` / `.kr` distinction
    - Some nodes (LFOs, envelopes) compute once per block, not per sample
    - Could be a `Rate` enum on the node, or just convention

4. **State inspection / reset** — owned state is opaque
    - No hard sync between oscillators
    - No preset snapshot/restore
    - No debugging deep compositions
    - Consider optional `Inspectable` interface for development

5. **Wavetable synthesis** — mentioned in design goal table but no primitive yet

6. **Waveshaping combinator** — transfer function applied to signal
    - `signal.shape { tanh(it * drive) }`
    - Generalizes distortion, saturation, wavefolding

### Future-Proofness Assessment

The architecture IS fundamentally extensible:

- New primitives just implement `SignalGen`
- New operators are extension functions
- Structural composition (exciter→resonator) already demonstrated by Karplus

Two risks:

- The `generate` signature: if `freqHz` must become a buffer, every node touches → mitigate with seam
- Feedback: if not designed as a combinator, calcifies into special cases that resist composition

### Inspirations from Other Software

| Tool              | Key Idea                                                      | Relevance                                                       |
|-------------------|---------------------------------------------------------------|-----------------------------------------------------------------|
| **Faust**         | Block diagram algebra: `:` serial, `,` parallel, `~` feedback | The `~` feedback operator is the most relevant missing piece    |
| **SuperCollider** | Multi-rate UGens (`.ar` audio-rate, `.kr` control-rate)       | Informs freqHz scalar/buffer decision and LFO integration       |
| **Reaktor**       | Modular "macro" patches as reusable presets                   | Composition graphs as serializable "timbre snapshots"           |
| **Max/MSP**       | Visual patching with explicit signal flow                     | Feedback = `[tapin~]`/`[tapout~]` pattern in functional form    |
| **Sonic Pi**      | Time-aware, beat-synced execution                             | Beat-phase in `generate()` for rhythm-synced timbral modulation |

### Recommended Pre-Implementation Actions

1. **Design the `freqHz` seam** before writing primitives
2. **Design feedback combinator shape on paper** — even if post-MVP, ensure core interface doesn't preclude it
3. **Build one beautiful-sounding composition end-to-end** as the MVP validation milestone
4. **Add scratch buffer polyphony load test** (16+ voices, composition depth 3+)

---

## Instrument Modeling Analysis (2026-03-17)

### New Combinators Needed (Priority Order)

1. **CombFilter(delay, feedback)** — highest leverage. Turns noise→pitched percussion, adds body to KS plucks, enables
   flute-like tones. Should be Tier 0 infrastructure.
2. **FeedInject(source, delay, mix)** — continuous signal injection into a delay line. Unlocks bowed string textures,
   drones (didgeridoo), feedback guitar. Key insight: "commuted synthesis" — model the output characteristics of
   excitation, not the physics.
3. **Waveshaper(curve)** — transfer function on signal. Generalizes distortion, tube warmth, wavefolding, harmonic
   enrichment.
4. **MultiTap(delays, gains)** — approximates sympathetic resonance. Sitar shimmer, piano-ish body, complex metallic
   decays.

### Instrument Roadmap

**Milestone 1 — Hero Instruments (current architecture):**

| Instrument           | Technique        | Expression Sketch                                 | Realism |
|----------------------|------------------|---------------------------------------------------|---------|
| Acoustic guitar      | KS               | `karplus(noise, damping=0.4)`                     | 70-80%  |
| E-guitar (clean)     | KS + warmth      | `karplus(noise, damping=0.3).withWarmth(0.2)`     | 70-75%  |
| E-guitar (distorted) | Saw + distortion | `saw.withWarmth(0.3)` → pipeline distortion       | 65-75%  |
| Bass guitar          | KS               | `karplus(noise.withWarmth(0.5), damping=0.6)`     | 70-80%  |
| Rhodes / E-piano     | FM               | `sine.fm(sine, ratio=1.0, depth=...)`             | 65-75%  |
| FM Bell              | FM inharmonic    | `sine.fm(sine, ratio=1.4, depth=...)`             | 70-80%  |
| Organ                | Additive         | `sine(f) + sine(2f).mul(0.7) + sine(3f).mul(0.3)` | 75-85%  |
| Synth pad            | Detuned saw      | `(saw + saw.detune(0.1)).div(2).withWarmth(0.4)`  | 80-90%  |
| Hi-hat               | Filtered noise   | `noise` → HP filter, fast ADSR                    | 60-70%  |
| Kick drum            | Sine + pitch env | `sine` with pitch envelope sweep                  | 60-70%  |

**Milestone 2 — With CombFilter:**

| Instrument | What CombFilter Adds                          | Realism |
|------------|-----------------------------------------------|---------|
| Banjo      | Brighter exciter + short comb = twangy body   | 60-70%  |
| Harp       | Soft exciter + long decay + comb body         | 55-65%  |
| Kalimba    | Impulse exciter + comb = metallic tine        | 65-75%  |
| Marimba    | Noise exciter + tuned comb = wooden resonance | 60-70%  |
| Steel drum | FM metallic + comb body                       | 60-70%  |
| Snare drum | KS(noise) membrane + noise rattle + comb      | 55-65%  |
| Flute-like | FM + breath noise + tuned comb                | 50-60%  |

**Milestone 3 — With FeedInject + Waveshaper:**

| Instrument              | Technique                                   | Realism |
|-------------------------|---------------------------------------------|---------|
| Bowed string (stylized) | KS + continuous filtered noise injection    | 40-55%  |
| Didgeridoo              | Buzz exciter + long FeedInject + LFO filter | 55-65%  |
| Sitar (with MultiTap)   | Sympathetic string resonance                | 50-60%  |
| Feedback guitar         | Self-feeding KS with high gain              | 60-70%  |
| Distortion bass         | Saw + waveshaper tube harmonics             | 65-75%  |

**Not Feasible Without Major New Primitives:**

| Instrument          | What's Missing                                                          |
|---------------------|-------------------------------------------------------------------------|
| Piano (realistic)   | Multi-segment envelope, per-partial inharmonicity, coupled string model |
| Violin (realistic)  | Nonlinear bow-string friction, resonant body impulse response           |
| Clarinet / Oboe     | Reed-bore waveguide coupling, register-dependent formants               |
| Saxophone           | Embouchure dynamics, formant structure, breath pressure curves          |
| Trumpet (realistic) | Lip-reed coupling, bell radiation model                                 |
| Human voice         | Formant synthesis engine, glottal pulse model                           |

### Design Principle

Name instruments by **musical role** (pluck, bass, keys, bell, pad, perc), not by imitation target. In a live-coding
context, an expressive synthesizer timbre named "pluck" is more honest and more useful than a mediocre imitation named "
guitar." Curate ruthlessly — ship only what sounds intentionally good.

---

## Complete DSP Building Block Inventory (2026-03-17)

### Safety & Infrastructure

| Block                            | Description                                                                             | Effort | Impact   | Notes                                                                 |
|----------------------------------|-----------------------------------------------------------------------------------------|--------|----------|-----------------------------------------------------------------------|
| **DC Blocker**                   | One-pole highpass ~5Hz. Prevents DC offset accumulation in feedback/waveshaping chains. | L      | Critical | 10 lines. MUST come before feedback loops. Speaker damage without it. |
| **Anti-aliasing (square/pulze)** | Add PolyBLEP to square and pulze oscillators (currently naive discontinuities).         | L      | H        | Saw already has PolyBLEP. Same technique.                             |
| **Soft Clipper / Saturation**    | Smooth limiting (tanh). Prevents harsh digital clipping in composition chains.          | L      | M        | Different from distortion — this is safety/warmth, not effect.        |

### Generators (new SignalGen primitives)

| Block                    | Description                                                                            | Effort | Impact | Notes                                                                              |
|--------------------------|----------------------------------------------------------------------------------------|--------|--------|------------------------------------------------------------------------------------|
| **Wavetable Oscillator** | Single-cycle table with interpolated phase lookup. Follows same `generate()` contract. | M      | H      | Opens entire wavetable synthesis paradigm. Morphing between tables.                |
| **TransientClick**       | Shaped noise burst on note-on (1-20ms). Params: color, punch, duration.                | L      | H      | The first 5-20ms defines instrument identity. Hammer, pick, tongue, pluck.         |
| **ImpulseColor**         | 8-tap FIR with material-derived coefficients (wood, metal, skin, glass).               | L      | H      | Cheap body/cabinet character. Replaces expensive MicroConvolver for per-voice use. |

### Filters as SignalGen Combinators

| Block                          | Description                                                | Effort | Impact   | Notes                                                                               |
|--------------------------------|------------------------------------------------------------|--------|----------|-------------------------------------------------------------------------------------|
| **CombFilter**                 | Tuned delay + feedback. Pitched resonance from noise.      | L      | H        | Tier 0 infrastructure. Percussion, body, flute-like tones.                          |
| **Allpass Filter**             | Phase-shifting without amplitude change.                   | L      | M        | Exists inlined in reverb — extract as standalone. Phaser, diffusion, custom reverb. |
| **Bandpass (resonant)**        | SVF bandpass at SignalGen level (not just voice pipeline). | M      | H        | Needed for formant sweeps, wah, filter FM inside compositions.                      |
| **DC Blocker (as combinator)** | Insertable in composition chains, not just pipeline.       | L      | Critical | Mandatory after waveshaper, before feedback. Graph-enforced ordering.               |

### Modulators as SignalGen

| Block                        | Description                                                                                     | Effort | Impact | Notes                                                                                  |
|------------------------------|-------------------------------------------------------------------------------------------------|--------|--------|----------------------------------------------------------------------------------------|
| **LFOGen**                   | Multi-shape modulation source. Ignores note freqHz, uses own rate. Output: control signal.      | L      | H      | Enables vibrato, tremolo, filter sweeps at composition level (not locked in Voice).    |
| **DriftCloud**               | Correlated micro-randomness via Perlin-style noise. Per-partial wandering. Param: `life` (0-1). | L      | H      | Analog warmth. Must use correlated noise (not independent per-voice) to avoid beating. |
| **SampleAndHold**            | Samples source when trigger crosses zero. Classic stepped modulation.                           | L      | M      | Retro sci-fi, random sequencing, quantized modulation.                                 |
| **AttackPhaseModulator**     | Decaying FM index during attack only. Fast-decaying modulation envelope.                        | L      | H      | DX7 electric piano pluck trick. Momentary harmonic splash that vanishes.               |
| **Envelope (multi-segment)** | Beyond ADSR: DAHDSR, AR, ASR, function-based, looping envelopes.                                | M      | M      | Per-voice temporal shaping. Percussion needs exponential AR; pads need multi-stage.    |
| **Slew Rate Limiter**        | Exponential follower for smoothing parameter changes.                                           | L      | M      | Prevents zipper noise. Portamento. Envelope following.                                 |

### Structural Combinators

| Block                 | Description                                                      | Effort | Impact | Notes                                                                         |
|-----------------------|------------------------------------------------------------------|--------|--------|-------------------------------------------------------------------------------|
| **Feedback**          | `A.feedback(delaySamples) { transform }` — Faust's `~` operator. | M      | H      | Gates all recursive topologies: KS, comb, FM feedback, waveguide.             |
| **FeedInject**        | Continuous signal injection into active delay line.              | M      | H      | Bowed strings, drones, blown tubes. Commuted synthesis approach.              |
| **Crossfade / Morph** | Smooth interpolation between two SignalGens.                     | L      | M      | Timbral animation, vector synthesis, wavetable morphing.                      |
| **Waveguide**         | Bidirectional coupled delay lines with reflection filters.       | H      | M      | Tubes, strings, membranes. Needs mandatory loss filter (energy conservation). |

### Effects / Post-Processing

| Block                 | Description                                                      | Effort | Impact | Notes                                                                                           |
|-----------------------|------------------------------------------------------------------|--------|--------|-------------------------------------------------------------------------------------------------|
| **Chorus / Ensemble** | LFO-modulated DelayLine. Already have all pieces, just wiring.   | L      | H      | Instant "more than one thing vibrating." Free from existing infrastructure.                     |
| **FormantBank**       | 3-5 parallel bandpass filters at formant frequencies. Morphable. | M      | M      | Vowel character (ah-ee-oo). Crude but sufficient for live-coding.                               |
| **MicroConvolver**    | Brute-force FIR, 32-128 taps. Shared bus, not per-voice.         | H      | H      | Full cabinet/room/body IR. Too expensive per-voice (~30-45% JS budget at 128 taps × 16 voices). |
| **MultiTap Delay**    | Multiple read points with independent gains.                     | M      | M      | Sympathetic resonance, sitar shimmer, complex metallic decays.                                  |

### Implementation Phases

**Phase 1 — Foundation (8 blocks, mostly low effort):**

1. DC Blocker
2. Soft Clipper
3. Anti-aliasing for square/pulze
4. LFOGen
5. DriftCloud
6. TransientClick
7. ImpulseColor (8-tap FIR)
8. Chorus

**Phase 2 — Expression (7 blocks):**

9. CombFilter (with mandatory loss filter)
10. Allpass (extract from reverb)
11. Bandpass at SignalGen level
12. SampleAndHold
13. AttackPhaseModulator
14. Waveshaper (polynomial/tanh/fold)
15. Crossfade/Morph

**Phase 3 — Advanced (5 blocks):**

16. Wavetable Oscillator
17. FeedInject
18. FormantBank
19. Waveguide (bidirectional, with energy conservation)
20. MicroConvolver (shared bus, polyphony-budgeted)

### Architectural Concerns

- **Signal ordering is load-bearing**: Waveshaper → DC Blocker → Feedback is mandatory. DC in a feedback loop = speaker
  damage. Should be graph-enforced, not advisory.
- **State lifetime management**: Every DSP block has mutable state (phase, filter history, delay lines). Must be
  allocated per-voice, preserved across blocks, deterministically released on voice-end, and reset on voice-steal.
- **Performance budget**: JS AudioWorklet ~200-300M ops/sec total. MicroConvolver at 128 taps per-voice is infeasible.
  ImpulseColor (8-tap) is the per-voice alternative. Full convolution must be shared post-mixdown.
- **Drift must use correlated noise**: Independent random pitch drift per voice causes beating. DriftCloud uses
  Perlin-style correlated noise so partials wander coherently.

### Synthesis Families Enabled

With the full 20-block toolkit:

| Family                   | Blocks Required                                | Status      |
|--------------------------|------------------------------------------------|-------------|
| Subtractive              | Oscillators + filters + ADSR                   | **Have it** |
| FM                       | Sine + FM in voice pipeline                    | **Have it** |
| Karplus-Strong / plucked | KS + exciter-as-SignalGen                      | **Have it** |
| Additive                 | sine + plus + mul (composition)                | **Have it** |
| Wavetable                | Wavetable oscillator + LFO morphing            | Phase 3     |
| Physical modeling        | Feedback + CombFilter + FeedInject + Waveguide | Phase 2-3   |
| Formant / vocal          | FormantBank + noise + envelopes                | Phase 3     |
| Modular / patch-style    | LFO + S&H + mod routing                        | Phase 1-2   |

Estimated capability: ~70% of commonly-used SuperCollider synthesis power, ~60% of Faust, ~50% of Reaktor. The remaining
30% is specialist territory (granular, spectral, advanced physical modeling) that can be added incrementally.
