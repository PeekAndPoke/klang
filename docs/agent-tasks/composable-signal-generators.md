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

    private fun acquire(): DoubleArray {
        if (nextFree >= pool.size) pool.add(DoubleArray(pool[0].size))
        return pool[nextFree++]
    }

    private fun release() {
        nextFree--
    }

    /** Scoped access — guarantees release even on exceptions. Never leak a buffer. */
    inline fun <R> use(block: (DoubleArray) -> R): R {
        val buf = acquire()
        try {
            return block(buf)
        } finally {
            release()
        }
    }

    fun reset() {
        nextFree = 0
    }
}
```

`acquire()` and `release()` are private — all external access goes through `use { }`, which guarantees the buffer is
returned. Max simultaneous buffers = composition tree depth. `(a + b).mul(0.5) + c` needs 2.

### Arithmetic Operators

```kotlin
// Binary (capture ScratchBuffers at construction)
fun SignalGen.plus(other: SignalGen, scratch: ScratchBuffers): SignalGen {
    return SignalGen { buffer, offset, length, freqHz, sampleRate, phaseMod ->
        this.generate(buffer, offset, length, freqHz, sampleRate, phaseMod)
        scratch.use { tmp ->
            other.generate(tmp, offset, length, freqHz, sampleRate, phaseMod)
            for (i in offset until offset + length) buffer[i] += tmp[i]
        }
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
| **Oversample**        | Run wrapped SignalGen at Nx rate, decimate with anti-alias LPF.  | M      | H      | Essential for waveshaper/distortion. 2x-8x. Per-node, not global.             |
| **ControlRate**       | Compute once per block, broadcast to buffer. For modulators.     | L      | M      | LFOs, envelopes, drift don't need audio-rate. Saves significant CPU.          |
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
- **Buffer type (DoubleArray vs FloatArray)**: The entire existing audio_be codebase uses `DoubleArray` (48+ occurrences
  across 23 files). `FloatArray` would be faster (half the memory, better cache locality, maps to JS `Float32Array`
  natively), but switching requires conversion at every voice pipeline boundary. Decision: start with `DoubleArray` for
  compatibility, but use `typealias SignalBuffer = DoubleArray` so a future swap is mechanical.
- **Scratch buffer safety**: `ScratchBuffers.acquire()`/`release()` are private. All access through scoped
  `use { buf -> ... }` which guarantees release even on exceptions. Prevents resource leaks in composition chains.
- **Oversampling**: Nonlinear operations (waveshaping, distortion, wavefolding, soft clipping) generate aliasing
  artifacts at high drive values. Solution: `signal.oversample(factor)` combinator that runs the wrapped SignalGen at
  2x/4x/8x sample rate internally, then decimates with an anti-alias lowpass filter. Cost: Nx compute for that node +
  upsample/downsample filters. Per-node, not global — only the nonlinear stage needs it. SuperCollider and Faust both
  support per-UGen oversampling.
- **Undersampling / control rate**: Modulation sources (LFOGen, DriftCloud, Envelope, SampleAndHold) don't need
  audio-rate computation. Computing once per block and filling the buffer with a constant value saves significant CPU.
  SuperCollider's `.kr` runs at `blockSize/sampleRate`. Could be implicit for modulator nodes, or explicit via a
  `signal.controlRate()` wrapper that computes one sample per block and broadcasts.

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

---

## Professional Audio Engineer Review (2026-03-17)

### Correctness Issues Found

**Karplus-Strong — missing critical details:**

- **Fractional delay interpolation is not optional.** Delay length `sampleRate / freqHz` is rarely integer. At 48kHz,
  A4 (440Hz) = 109.09 samples. Without interpolation, high notes are audibly out of tune. Use allpass interpolation
  (first-order allpass at delay line read point) for best spectral preservation, or at minimum linear interpolation.
- **Separate loss factor needed** in feedback loop (typically 0.990-0.999), independent of damping filter. Otherwise
  timbre and sustain are undesirably coupled.
- **DC blocker inside the loop** needed for long-sustain patches, not just after output.

**PolyBLEP for square/pulze — partially correct:**

- PolyBLEP works for fixed-duty square. For variable pulse width (PWM), PolyBLEP must be applied at *both* transitions
  independently, with positions that change with pulse width. More involved implementation.
- Adequate up to ~80% of Nyquist. Above that, MinBLEP (minimum-phase BLEP tables) needed for better rejection.
- **Triangle waves** need **PolyBLAMP** (bandlimited ramp), not PolyBLEP. If generating from phase directly (not by
  integrating a square), PolyBLAMP at the corners is required.

**8-tap ImpulseColor — too short for body simulation:**

- 8 taps at 48kHz = 0.17ms. Useful body resonances start at ~1ms and extend to 20ms+.
- 8 taps can shape spectral slope but cannot create resonant peaks/notches that distinguish materials.
- **Replace with ModalResonator**: bank of 3-8 second-order resonators (biquads) with preset mode frequencies and decay
  rates per material. 5 modes = 5 biquads = similar cost to a 10-tap FIR but far more realistic resonance.
  Example guitar body modes: ~100Hz, ~200Hz, ~400Hz.

**tanh soft clipping — correct but basic:**

- `tanh` produces symmetric odd harmonics. Fine as default.
- For **tube character**: needs asymmetric function (different coefficients for +/- half-cycles) to produce even
  harmonics.
- For **transistor character**: `atan(k*x)/atan(k)` with high k gives sharper knee.
- **Critical missing piece**: Pre-emphasis / de-emphasis filtering (boost highs before clipping, cut after). Without
  this, no waveshaper sounds like an amp. Real amp circuits do this with tone stacks.
- **ADAA (Antiderivative Anti-Aliasing)** should replace oversampling for waveshapers. For `tanh`, the antiderivative
  is `log(cosh(x))`, compute `(F(x1) - F(x0)) / (x1 - x0)`. Eliminates aliasing at zero oversampling cost.
  Published by Parker et al. (2016), now industry standard. Strongly recommended over per-node oversampling.

**DC blocker cutoff**: 5Hz may be too low for feedback paths where DC accumulates fast. Use 20Hz in feedback loops
(still inaudible on any real speaker), keep 5Hz for master output.

### Realism Estimates — Corrected

| Instrument           | Document claim | Engineer estimate | Key gap                                                        |
|----------------------|----------------|-------------------|----------------------------------------------------------------|
| Acoustic guitar (KS) | 70-80%         | 55-70%            | Missing body resonance, pick position, sympathetic resonance   |
| Bass guitar (KS)     | 70-80%         | 65-75%            | More achievable (less complex body, listener less sensitive)   |
| E-guitar (clean)     | 70-75%         | 45-55%            | **Most inflated.** Missing pickup sim + cabinet IR             |
| E-guitar (distorted) | 65-75%         | 40-55%            | Saw + distortion ≠ guitar. Needs pickup, cab, multi-stage gain |
| Rhodes (FM)          | 65-75%         | 55-65%            | Velocity-dependent FM index envelope is critical               |
| Organ (additive)     | 75-85%         | 70-80%            | Achievable with key click + scanner vibrato                    |
| Synth pad            | 80-90%         | 80-90%            | Correct — detuned saws IS what pads are                        |
| Bowed string         | 40-55%         | 25-40%            | Noise injection into KS = "scratchy pad," not "violin"         |

### What's Missing for +10-20% Realism Per Family

**Plucked strings:**

1. Fractional delay interpolation (allpass/Thiran): +5%, essential for pitch accuracy
2. **Pick position modeling**: comb filter in excitation path. `exciter[n] - exciter[n-pickDelay]` where
   `pickDelay = delayLength * pickPosition`. Pick near bridge = bright, near neck = warm. Cheap (one subtraction +
   delay read). +5-10%
3. **Body resonance via ModalResonator**: 3-5 second-order resonators tuned to body modes. +5-10%
4. **Velocity-dependent excitation**: harder picks = brighter noise (lowpass cutoff scales with velocity). +3-5%
5. **String stiffness**: second-order allpass in feedback loop for inharmonic partials. Critical for bass. +5%

**Electric guitar:**

1. **Pickup simulation**: single biquad peak filter at 2-5kHz (single-coil ~4-5kHz, humbucker ~2-3kHz). +10-15%
2. **Cabinet simulation**: the single most important element. Severe bandpass 80Hz-5kHz with peak ~2kHz. Short IR
   (64-128 taps) or 8-band EQ. +15-20%
3. **Pre-distortion tone stack**: treble/mid/bass EQ *before* distortion (3 biquads). +5-10%
4. **Multi-stage gain**: `gain → EQ → tanh → gain → EQ → tanh` (2-3 stages). +5-10%
5. **Sag / dynamic response**: envelope follower controlling gain before clipper. +3-5%

**Rhodes FM — parameters that work:**

- Carrier: sine at fundamental. Modulator 1: 1x freq, index 1.5-3.0 (tine/tone bar).
  Modulator 2: ~14x freq, index 0.3-0.8, fast decay (tine attack "bell").
- **Critical**: FM index must decay with envelope. High at attack (bell-like) → low sustained (warm).
- Velocity mapping: low velocity = low index (warm), high velocity = high index (bright, barky).
- Tremolo: stereo panning effect (L/R anti-phase triangle LFO 3-7Hz), not simple AM.

**Organ — convincing additive:**

- 9 drawbar harmonics: 16', 5⅓', 8', 4', 2⅔', 2', 1⅗', 1⅓', 1' (note non-octave quint stops)
- **Key click**: 2-5ms broadband noise at note-on AND note-off. +10%
- **Scanner vibrato/chorus**: NOT simple pitch vibrato. Multi-tap delay scanned by LFO. Simplified: 3-stage phaser
  with LFO. +10-15%
- **Tonewheel imperfections**: ±1-2% random detuning and level variation per harmonic. +3-5%
- **Percussion**: decaying click on 2nd or 3rd harmonic, single-trigger. +5%
- **Leslie speaker**: dual LFOs with frequency-dependent AM/FM (horn/drum at different speeds, crossover ~800Hz). +15%

**Percussion recipes:**

- **Kick**: sine 150-300Hz → sweep to 40-60Hz over 30-80ms + noise burst 2-5ms (beater) + 2x harmonic sine (punch).
  75-85% realism.
- **Snare**: body sine/tri 150-250Hz (slight pitch sweep) + bandpass noise 1-5kHz (snare wires, longer decay) + comb
  filter on noise for buzz character. 65-75%.
- **Hi-hat**: 6 detuned square waves at inharmonic ratios (1.0, 1.4471, 1.6170, 1.9265, 2.5028, 2.6637 — TR-808
  ratios) + bandpass 7-10kHz. Open/closed = envelope length only. 60-70%.
- **Cymbals**: 15-20 resonant modes with inharmonic ratios, frequency-dependent damping. Very hard. 40-50%.

**Bowed strings — minimum viable:**

- Waveguide (bidirectional delay) + nonlinear friction element at one point:
  `F_friction = f(v_bow - v_string)` with stick/slip regions
- Bow pressure + velocity as continuous controls (not note-on params)
- Body resonance critical: violin modes at ~280Hz (A0), 460Hz (B1-), 530Hz (B1+)
- Simplified friction + waveguide + 3-mode ModalResonator = 50-60%

**Wind — minimum viable:**

- Flute: noise into tuned comb filter with continuous injection. 40-50%
- Clarinet: reed model `y = max(0, 1 - pressure_diff) * pressure_diff` (produces odd harmonics) + waveguide. 45-55%
- Brass: out of scope (lip-bore coupling too complex)

### Overlooked DSP Fundamentals

**Filters:**

- **Ladder filter (Moog-style)**: Four cascaded one-pole filters with feedback. Most requested synth filter topology.
  Huovilainen model is the standard reference. Cheap (4 one-pole sections + feedback). If you implement one filter
  besides SVF, make it this.
- **Filter self-oscillation**: When resonance > 1.0, filter should produce a sine at cutoff frequency. Fundamental
  synthesis tool, not just artifact. Both SVF and ladder support this.
- **Filter FM / cutoff modulation at audio rate**: Essential for acid bass (TB-303 style). Filter must accept
  modulation signal, not just static cutoff.
- **Cytomic SVF**: Andrew Simper's SVF implementation is the industry standard for virtual analog. Better behavior at
  high frequencies than Chamberlin SVF. Consider this over the basic SVF.

**Envelopes — curve shapes matter:**

- **Exponential curves** are essential. Linear envelopes sound "digital." Use
  `y = target + (start - target) * exp(-t / tau)` or cheap digital: `y = y + (target - y) * coeff`.
- Attack: concave exponential (slow start, fast finish). Decay: convex exponential (fast start, slow finish).
- **Curve parameter** per segment (-1 to +1, linear at 0) gives full control.
- **Retriggering behavior**: restart from zero, restart from current value (most common/musical), or legato.

**Missing core synth features:**

1. **Portamento / Glide**: one-pole lowpass on freqHz. `freq += (target - freq) * coeff`. SignalGen wrapper that
   smooths incoming freqHz.
2. **Hard sync**: oscillator A resets B's phase each cycle. Requires inter-SignalGen communication (phase reset
   callback). Current interface doesn't support this.
3. **Phase reset on note-on**: many synths reset phase to 0 on each note for consistent timbre. Need a reset mechanism.
4. **Unison generalized**: `unison(count, detuneCents, stereoSpread)` combinator for any SignalGen, not just supersaw.
5. **Sub-oscillator**: trivially `sine.detune(-12)` but should be documented as a pattern.
6. **Sample playback as SignalGen**: one-shot or looped sample player. Turns any recording into an oscillator.
7. **Chebyshev polynomial waveshaping**: T2(x) adds exact 2nd harmonic, T3(x) adds 3rd, etc. Cheap, musically
   precise.
8. **Additional noise colors**: blue noise (HF emphasis, dithering), velvet noise (sparse impulses, efficient reverb),
   crackle (already have this one).

### Performance Reality Check

**DoubleArray vs FloatArray — engineer's recommendation:**

- On JS: `Float64Array` vs `Float32Array` arithmetic speed is similar (JS uses f64 internally). But **memory bandwidth
  and cache utilization** matter: `Float32Array` = half the memory = 2x cache fit = **15-30% throughput improvement**.
- On WASM: more pronounced. SIMD processes 4x `f32` vs 2x `f64` per instruction.
- **Recommendation**: Use `FloatArray` (32-bit) for signal buffers. 32-bit gives ~150dB dynamic range (human hearing
  ~120dB). Use `Double` only for **phase accumulators** (to avoid pitch drift) and **feedback delay indices**.

**Oversampling — prefer ADAA instead:**

- Per-node oversampling needs upsample filter + Nx processing + downsample filter. At 2x with 23-tap half-band FIR:
  ~4.4M MACs/sec per node. Expensive.
- **ADAA (Antiderivative Anti-Aliasing)** eliminates aliasing from waveshapers at zero oversampling cost. Use for
  tanh, polynomial, Chebyshev. Keep oversampling only for waveshapers without known antiderivatives.
- Budget: at most 1-2 oversampled nodes per voice in JS AudioWorklet.

**Realistic voice budgets (JS AudioWorklet, 48kHz, 128-sample blocks, 50% CPU headroom):**

- Simple voice (2 oscs + filter + env): ~1.5M MACs/sec → 50-80 simultaneous
- Complex voice (supersaw + filter + distortion + 3 envs + mod): ~5-8M MACs/sec → 15-25 simultaneous
- Physical model (KS + body + effects): ~3-5M MACs/sec → 20-35 simultaneous
- **Realistic targets with JS overhead (GC, function calls): 8-16 complex voices, 16-32 simple voices.**

### Recommended Priority Changes

**Drop or defer:**

- Oversample combinator → use ADAA instead (cheaper, equally effective)
- ImpulseColor (8-tap) → replace with ModalResonator (biquad bank, far more realistic at similar cost)
- MicroConvolver per-voice → keep only as shared post-mix bus effect

**Add to plan:**

- **ModalResonator** (bank of 3-8 second-order resonators): body simulation, material character
- **Ladder Filter** (Moog-style, Huovilainen model): the most requested synth filter
- **ADAA Waveshaper**: anti-aliased distortion without oversampling cost
- **Fractional Delay Line** (allpass interpolation): essential for KS pitch accuracy
- **Portamento / Glide**: frequency smoothing wrapper
- **Pick Position**: comb filter in KS excitation path
- **Cabinet IR**: shared bus, post-mix (essential for electric guitar)
