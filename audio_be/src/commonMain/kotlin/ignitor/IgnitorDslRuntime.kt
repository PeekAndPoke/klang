package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.random.Random

/**
 * Walks the DSL tree and produces a runtime [Ignitor] instance.
 *
 * [oscParams] provides runtime overrides from [VoiceData.oscParams][io.peekandpoke.klang.audio_bridge.VoiceData.oscParams].
 * Only [IgnitorDsl.Param] leaf nodes read overrides; the tree structure IS the recipe.
 *
 * **Memoisation contract:**
 *
 * During a single `toExciter` call, every signal-producing DSL node is converted to a runtime
 * Ignitor exactly once, keyed by object identity. Two references to the same DSL node (e.g.
 * `let s = Osc.sine(); s + s`) share one Ignitor wrapped in [MemoizingIgnitor], so both readers
 * see the same block output — phase, noise seed, envelope position all agree.
 *
 * Two structurally-equal-but-distinct DSL instances (e.g. `Osc.sine() + Osc.sine()`) still
 * produce two independent Ignitors, because they are separate tree nodes.
 *
 * Cheap leaves ([IgnitorDsl.Param], [IgnitorDsl.Constant], [IgnitorDsl.Freq]) skip the cache —
 * they produce their output from their own arguments with no per-voice state worth sharing.
 */
fun IgnitorDsl.toExciter(oscParams: Map<String, Double>? = null): Ignitor {
    val cache = IgnitorBuildCache()
    return buildIgnitor(oscParams, cache)
}

/**
 * Identity-based cache for DSL → Ignitor conversion.
 *
 * Kotlin stdlib's `HashMap` uses [equals] which is structural for `data class`es; two
 * structurally-equal DSL nodes would collide. We want *object identity* so that two calls to
 * `Osc.sine()` remain independent. A linear scan with `===` is correct; the tree size is
 * bounded by a voice's DSL depth (tens of nodes) so O(n²) lookup is irrelevant here — and
 * `toExciter` runs once per voice, not per block.
 */
internal class IgnitorBuildCache {
    private val keys = ArrayList<IgnitorDsl>()
    private val values = ArrayList<Ignitor>()

    inline fun getOrPut(key: IgnitorDsl, compute: () -> Ignitor): Ignitor {
        for (i in keys.indices) {
            if (keys[i] === key) return values[i]
        }
        val v = compute()
        keys.add(key)
        values.add(v)
        return v
    }
}

/**
 * Recursive tree walker. Wraps every produced Ignitor in [MemoizingIgnitor] so shared
 * DSL nodes (same object identity, used in multiple positions) yield one evaluation per block
 * with output copied to every reader.
 *
 * Cheap leaves ([IgnitorDsl.Param], [IgnitorDsl.Constant], [IgnitorDsl.Freq]) are built raw —
 * they have no per-voice state and memoising them would cost a buffer copy for no gain.
 */
internal fun IgnitorDsl.buildIgnitor(
    oscParams: Map<String, Double>?,
    cache: IgnitorBuildCache,
): Ignitor {
    // Leaves: no cache, no memoisation wrap.
    when (this) {
        is IgnitorDsl.Param -> return ParamIgnitor(name, oscParams?.get(name) ?: default)
        is IgnitorDsl.Constant -> return ParamIgnitor("", value)
        is IgnitorDsl.Freq -> return FreqIgnitor
        else -> { /* fall through */
        }
    }

    return cache.getOrPut(this) {
        MemoizingIgnitor(buildRaw(oscParams, cache))
    }
}

/**
 * Builds the raw (non-memoised) Ignitor for this DSL node. Sub-nodes go through
 * [buildIgnitor] so they may be memoised and identity-cached.
 */
private fun IgnitorDsl.buildRaw(
    oscParams: Map<String, Double>?,
    cache: IgnitorBuildCache,
): Ignitor {
    return when (this) {
        // Leaves already handled in buildIgnitor — safe to throw here.
        is IgnitorDsl.Param, is IgnitorDsl.Constant, is IgnitorDsl.Freq ->
            error("Leaf DSL nodes must be built in buildIgnitor, not buildRaw")

        // Primitives
        is IgnitorDsl.Sine -> Ignitors.sine(
            freq = this.freq.buildIgnitor(oscParams, cache),
            analog = this.analog.buildIgnitor(oscParams, cache)
        )

        is IgnitorDsl.Sawtooth -> Ignitors.sawtooth(
            freq = this.freq.buildIgnitor(oscParams, cache),
            analog = this.analog.buildIgnitor(oscParams, cache)
        )

        is IgnitorDsl.Square -> Ignitors.square(
            freq = this.freq.buildIgnitor(oscParams, cache),
            analog = this.analog.buildIgnitor(oscParams, cache)
        )

        is IgnitorDsl.Triangle -> Ignitors.triangle(
            freq = this.freq.buildIgnitor(oscParams, cache),
            analog = this.analog.buildIgnitor(oscParams, cache)
        )

        is IgnitorDsl.Ramp -> Ignitors.ramp(
            freq = this.freq.buildIgnitor(oscParams, cache),
            analog = this.analog.buildIgnitor(oscParams, cache)
        )

        is IgnitorDsl.Zawtooth -> Ignitors.zawtooth(
            freq = this.freq.buildIgnitor(oscParams, cache),
            analog = this.analog.buildIgnitor(oscParams, cache)
        )
        is IgnitorDsl.Pulze -> Ignitors.pulze(
            freq = this.freq.buildIgnitor(oscParams, cache),
            duty = this.duty.buildIgnitor(oscParams, cache),
            analog = this.analog.buildIgnitor(oscParams, cache),
        )

        is IgnitorDsl.WhiteNoise -> Ignitors.whiteNoise(Random)
        is IgnitorDsl.Impulse -> Ignitors.impulse(
            freq = this.freq.buildIgnitor(oscParams, cache),
            analog = this.analog.buildIgnitor(oscParams, cache)
        )
        is IgnitorDsl.BrownNoise -> Ignitors.brownNoise(Random)
        is IgnitorDsl.PinkNoise -> Ignitors.pinkNoise(Random)

        is IgnitorDsl.PerlinNoise -> Ignitors.perlinNoise(Random, this.rate.buildIgnitor(oscParams, cache))
        is IgnitorDsl.BerlinNoise -> Ignitors.berlinNoise(Random, this.rate.buildIgnitor(oscParams, cache))

        is IgnitorDsl.Dust -> Ignitors.dust(Random, this.density.buildIgnitor(oscParams, cache))
        is IgnitorDsl.Crackle -> Ignitors.crackle(Random, this.density.buildIgnitor(oscParams, cache))

        // Super oscillators
        is IgnitorDsl.SuperSaw -> Ignitors.superSaw(
            freq = this.freq.buildIgnitor(oscParams, cache),
            voices = this.voices.buildIgnitor(oscParams, cache),
            freqSpread = this.freqSpread.buildIgnitor(oscParams, cache),
            analog = this.analog.buildIgnitor(oscParams, cache),
        )

        is IgnitorDsl.SuperSine -> Ignitors.superSine(
            freq = this.freq.buildIgnitor(oscParams, cache),
            voices = this.voices.buildIgnitor(oscParams, cache),
            freqSpread = this.freqSpread.buildIgnitor(oscParams, cache),
            analog = this.analog.buildIgnitor(oscParams, cache),
        )

        is IgnitorDsl.SuperSquare -> Ignitors.superSquare(
            freq = this.freq.buildIgnitor(oscParams, cache),
            voices = this.voices.buildIgnitor(oscParams, cache),
            freqSpread = this.freqSpread.buildIgnitor(oscParams, cache),
            analog = this.analog.buildIgnitor(oscParams, cache),
        )

        is IgnitorDsl.SuperTri -> Ignitors.superTri(
            freq = this.freq.buildIgnitor(oscParams, cache),
            voices = this.voices.buildIgnitor(oscParams, cache),
            freqSpread = this.freqSpread.buildIgnitor(oscParams, cache),
            analog = this.analog.buildIgnitor(oscParams, cache),
        )

        is IgnitorDsl.SuperRamp -> Ignitors.superRamp(
            freq = this.freq.buildIgnitor(oscParams, cache),
            voices = this.voices.buildIgnitor(oscParams, cache),
            freqSpread = this.freqSpread.buildIgnitor(oscParams, cache),
            analog = this.analog.buildIgnitor(oscParams, cache),
        )

        is IgnitorDsl.Silence -> Ignitors.silence()

        // Physical models
        is IgnitorDsl.Pluck -> Ignitors.karplusStrong(
            freq = this.freq.buildIgnitor(oscParams, cache),
            decay = this.decay.buildIgnitor(oscParams, cache),
            brightness = this.brightness.buildIgnitor(oscParams, cache),
            pickPosition = this.pickPosition.buildIgnitor(oscParams, cache),
            stiffness = this.stiffness.buildIgnitor(oscParams, cache),
            analog = this.analog.buildIgnitor(oscParams, cache),
        )

        is IgnitorDsl.SuperPluck -> Ignitors.superKarplusStrong(
            freq = this.freq.buildIgnitor(oscParams, cache),
            voices = this.voices.buildIgnitor(oscParams, cache),
            freqSpread = this.freqSpread.buildIgnitor(oscParams, cache),
            decay = this.decay.buildIgnitor(oscParams, cache),
            brightness = this.brightness.buildIgnitor(oscParams, cache),
            pickPosition = this.pickPosition.buildIgnitor(oscParams, cache),
            stiffness = this.stiffness.buildIgnitor(oscParams, cache),
            analog = this.analog.buildIgnitor(oscParams, cache),
        )

        // Arithmetic
        is IgnitorDsl.Plus -> left.buildIgnitor(oscParams, cache) + right.buildIgnitor(oscParams, cache)
        is IgnitorDsl.Times -> left.buildIgnitor(oscParams, cache) * right.buildIgnitor(oscParams, cache)
        is IgnitorDsl.Mul -> left.buildIgnitor(oscParams, cache).mul(right.buildIgnitor(oscParams, cache))
        is IgnitorDsl.Div -> left.buildIgnitor(oscParams, cache).div(right.buildIgnitor(oscParams, cache))

        // Frequency
        is IgnitorDsl.Detune -> inner.buildIgnitor(oscParams, cache).detune(this.semitones.buildIgnitor(oscParams, cache))

        // Filters
        is IgnitorDsl.Lowpass -> inner.buildIgnitor(oscParams, cache)
            .lowpass(this.cutoffHz.buildIgnitor(oscParams, cache), this.q.buildIgnitor(oscParams, cache))

        is IgnitorDsl.Highpass -> inner.buildIgnitor(oscParams, cache)
            .highpass(this.cutoffHz.buildIgnitor(oscParams, cache), this.q.buildIgnitor(oscParams, cache))

        is IgnitorDsl.OnePoleLowpass -> inner.buildIgnitor(oscParams, cache).onePoleLowpass(this.cutoffHz.buildIgnitor(oscParams, cache))
        is IgnitorDsl.Bandpass -> inner.buildIgnitor(oscParams, cache)
            .bandpass(this.cutoffHz.buildIgnitor(oscParams, cache), this.q.buildIgnitor(oscParams, cache))

        is IgnitorDsl.Notch -> inner.buildIgnitor(oscParams, cache)
            .notch(this.cutoffHz.buildIgnitor(oscParams, cache), this.q.buildIgnitor(oscParams, cache))

        // Envelope
        is IgnitorDsl.Adsr -> inner.buildIgnitor(oscParams, cache).adsr(
            this.attackSec.buildIgnitor(oscParams, cache),
            this.decaySec.buildIgnitor(oscParams, cache),
            this.sustainLevel.buildIgnitor(oscParams, cache),
            this.releaseSec.buildIgnitor(oscParams, cache),
        )

        // FM
        is IgnitorDsl.Fm -> carrier.buildIgnitor(oscParams, cache).fm(
            modulator = modulator.buildIgnitor(oscParams, cache),
            ratio = this.ratio.buildIgnitor(oscParams, cache),
            depth = this.depth.buildIgnitor(oscParams, cache),
            envAttackSec = this.envAttackSec.buildIgnitor(oscParams, cache),
            envDecaySec = this.envDecaySec.buildIgnitor(oscParams, cache),
            envSustainLevel = this.envSustainLevel.buildIgnitor(oscParams, cache),
            envReleaseSec = this.envReleaseSec.buildIgnitor(oscParams, cache),
        )

        // Effects
        is IgnitorDsl.Distort -> inner.buildIgnitor(oscParams, cache)
            .distort(this.amount.buildIgnitor(oscParams, cache), shape, Oversampler.factorToStages(oversample))

        is IgnitorDsl.Drive -> inner.buildIgnitor(oscParams, cache).drive(this.amount.buildIgnitor(oscParams, cache), driveType)
        is IgnitorDsl.Clip -> inner.buildIgnitor(oscParams, cache).clip(shape, Oversampler.factorToStages(oversample))
        is IgnitorDsl.Crush -> inner.buildIgnitor(oscParams, cache).crush(this.amount.buildIgnitor(oscParams, cache))
        is IgnitorDsl.Coarse -> inner.buildIgnitor(oscParams, cache).coarse(this.amount.buildIgnitor(oscParams, cache))
        is IgnitorDsl.Phaser -> inner.buildIgnitor(oscParams, cache).phaser(
            this.rate.buildIgnitor(oscParams, cache),
            this.depth.buildIgnitor(oscParams, cache),
            this.center.buildIgnitor(oscParams, cache),
            this.sweep.buildIgnitor(oscParams, cache),
        )

        is IgnitorDsl.Tremolo -> inner.buildIgnitor(oscParams, cache).tremolo(
            this.rate.buildIgnitor(oscParams, cache),
            this.depth.buildIgnitor(oscParams, cache),
        )

        is IgnitorDsl.Shimmer -> inner.buildIgnitor(oscParams, cache).shimmer(
            this.mix.buildIgnitor(oscParams, cache),
            this.feedback.buildIgnitor(oscParams, cache),
            this.tone.buildIgnitor(oscParams, cache),
        )

        // Pitch modulation
        is IgnitorDsl.Vibrato -> inner.buildIgnitor(oscParams, cache).vibrato(
            this.rate.buildIgnitor(oscParams, cache),
            this.depth.buildIgnitor(oscParams, cache),
        )

        is IgnitorDsl.Accelerate -> inner.buildIgnitor(oscParams, cache).accelerate(this.amount.buildIgnitor(oscParams, cache))
        is IgnitorDsl.PitchEnvelope -> inner.buildIgnitor(oscParams, cache).pitchEnvelope(
            attackSec = this.attackSec.buildIgnitor(oscParams, cache),
            decaySec = this.decaySec.buildIgnitor(oscParams, cache),
            releaseSec = this.releaseSec.buildIgnitor(oscParams, cache),
            amount = this.amount.buildIgnitor(oscParams, cache),
            curve = this.curve.buildIgnitor(oscParams, cache),
            anchor = this.anchor.buildIgnitor(oscParams, cache),
        )
    }
}
