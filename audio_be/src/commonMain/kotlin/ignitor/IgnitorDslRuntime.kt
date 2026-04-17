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
 * Ignitor exactly once, keyed by `(object identity, accumulated pitch mod)`. Two references to
 * the same DSL node with the same mod chain share one [MemoizingIgnitor]. Two references with
 * different mod chains (e.g., `s + s.vibrato(...)`) produce independent Ignitors — each with
 * its own phase accumulator.
 *
 * **Pitch-mod bubbling:**
 *
 * Pitch-mod DSL nodes ([IgnitorDsl.Vibrato], [IgnitorDsl.Accelerate], [IgnitorDsl.PitchEnvelope],
 * [IgnitorDsl.Fm]) do not become Ignitor wrappers. Instead, they produce a mod Ignitor (ratio-space,
 * 1.0 = no change) that is accumulated and passed down to the source oscillator via
 * [ModApplyingIgnitor]. Insert effects and binary ops pass the mod through transparently.
 */
fun IgnitorDsl.toExciter(oscParams: Map<String, Double>? = null): Ignitor {
    val cache = IgnitorBuildCache()
    return buildIgnitor(oscParams, cache)
}

/**
 * Identity-based cache for DSL → Ignitor conversion, keyed on `(DSL node identity, mod identity)`.
 *
 * Two references to the same DSL node with the same accumulated mod share one Ignitor.
 * Two references with different mods (or one with mod, one without) produce independent entries.
 */
internal class IgnitorBuildCache {
    private val dslKeys = ArrayList<IgnitorDsl>()
    private val modKeys = ArrayList<Ignitor?>()
    private val values = ArrayList<Ignitor>()

    inline fun getOrPut(key: IgnitorDsl, mod: Ignitor?, compute: () -> Ignitor): Ignitor {
        for (i in dslKeys.indices) {
            if (dslKeys[i] === key && modKeys[i] === mod) return values[i]
        }
        val v = compute()
        dslKeys.add(key)
        modKeys.add(mod)
        values.add(v)
        return v
    }
}

/**
 * Recursive tree walker.
 *
 * @param accumulatedMod ratio-space mod Ignitor accumulated from outer pitch-mod wrappers (null = no mod).
 */
internal fun IgnitorDsl.buildIgnitor(
    oscParams: Map<String, Double>?,
    cache: IgnitorBuildCache,
    accumulatedMod: Ignitor? = null,
): Ignitor {
    // ── Leaves: direct return, no cache. ──
    when (this) {
        is IgnitorDsl.Param -> return ParamIgnitor(name, oscParams?.get(name) ?: default)
        is IgnitorDsl.Constant -> return ParamIgnitor("", value)
        is IgnitorDsl.Freq -> return FreqIgnitor
        else -> { /* fall through */
        }
    }

    // ── Pitch-mod nodes: absorb into mod, descend. No cache/Memoized for this node itself. ──
    when (this) {
        is IgnitorDsl.Vibrato -> {
            val vibMod = vibratoModIgnitor(
                rate = this.rate.buildIgnitor(oscParams, cache),
                depth = this.depth.buildIgnitor(oscParams, cache),
            )
            return inner.buildIgnitor(oscParams, cache, combineMods(accumulatedMod, vibMod))
        }

        is IgnitorDsl.Accelerate -> {
            val accelMod = accelerateModIgnitor(this.amount.buildIgnitor(oscParams, cache))
            return inner.buildIgnitor(oscParams, cache, combineMods(accumulatedMod, accelMod))
        }

        is IgnitorDsl.PitchEnvelope -> {
            val peMod = pitchEnvelopeModIgnitor(
                attackSec = this.attackSec.buildIgnitor(oscParams, cache),
                decaySec = this.decaySec.buildIgnitor(oscParams, cache),
                releaseSec = this.releaseSec.buildIgnitor(oscParams, cache),
                amount = this.amount.buildIgnitor(oscParams, cache),
                curve = this.curve.buildIgnitor(oscParams, cache),
                anchor = this.anchor.buildIgnitor(oscParams, cache),
            )
            return inner.buildIgnitor(oscParams, cache, combineMods(accumulatedMod, peMod))
        }

        is IgnitorDsl.PitchMod -> {
            val userMod = this.mod.buildIgnitor(oscParams, cache)
            val ratioMod = deviationToRatioIgnitor(userMod)
            return inner.buildIgnitor(oscParams, cache, combineMods(accumulatedMod, ratioMod))
        }

        is IgnitorDsl.Fm -> {
            val modulatorIgnitor = modulator.buildIgnitor(oscParams, cache)
            val fmMod = fmModIgnitor(
                modulator = modulatorIgnitor,
                ratio = this.ratio.buildIgnitor(oscParams, cache),
                depth = this.depth.buildIgnitor(oscParams, cache),
                envAttackSec = this.envAttackSec.buildIgnitor(oscParams, cache),
                envDecaySec = this.envDecaySec.buildIgnitor(oscParams, cache),
                envSustainLevel = this.envSustainLevel.buildIgnitor(oscParams, cache),
                envReleaseSec = this.envReleaseSec.buildIgnitor(oscParams, cache),
            )
            return carrier.buildIgnitor(oscParams, cache, combineMods(accumulatedMod, fmMod))
        }

        else -> { /* fall through to cache+memoize path */
        }
    }

    // ── Everything else: identity-cache + MemoizingIgnitor wrap. ──
    return cache.getOrPut(this, accumulatedMod) {
        MemoizingIgnitor(buildRaw(oscParams, cache, accumulatedMod))
    }
}

private fun combineMods(existing: Ignitor?, newMod: Ignitor): Ignitor =
    if (existing != null) existing * newMod else newMod

private fun applyMod(source: Ignitor, mod: Ignitor?): Ignitor =
    if (mod != null) ModApplyingIgnitor(source, mod) else source

/**
 * Builds the raw (non-memoised) Ignitor for a non-pitch-mod, non-leaf DSL node.
 *
 * Source nodes apply [accumulatedMod] via [ModApplyingIgnitor].
 * Insert effects, binary ops, and other wrappers pass [accumulatedMod] through to their children.
 */
private fun IgnitorDsl.buildRaw(
    oscParams: Map<String, Double>?,
    cache: IgnitorBuildCache,
    accumulatedMod: Ignitor?,
): Ignitor {
    fun IgnitorDsl.withMod(mod: Ignitor? = accumulatedMod): Ignitor = buildIgnitor(oscParams, cache, mod)
    fun IgnitorDsl.noMod(): Ignitor = buildIgnitor(oscParams, cache)

    return when (this) {
        is IgnitorDsl.Param, is IgnitorDsl.Constant, is IgnitorDsl.Freq ->
            error("Leaf DSL nodes must be built in buildIgnitor, not buildRaw")

        is IgnitorDsl.Vibrato, is IgnitorDsl.Accelerate, is IgnitorDsl.PitchEnvelope, is IgnitorDsl.Fm, is IgnitorDsl.PitchMod ->
            error("Pitch-mod DSL nodes must be absorbed in buildIgnitor, not buildRaw")

        // ── Sources: apply accumulated mod ──

        is IgnitorDsl.Sine -> applyMod(Ignitors.sine(freq.noMod(), analog.noMod()), accumulatedMod)
        is IgnitorDsl.Sawtooth -> applyMod(Ignitors.sawtooth(freq.noMod(), analog.noMod()), accumulatedMod)
        is IgnitorDsl.Square -> applyMod(Ignitors.square(freq.noMod(), analog.noMod()), accumulatedMod)
        is IgnitorDsl.Triangle -> applyMod(Ignitors.triangle(freq.noMod(), analog.noMod()), accumulatedMod)
        is IgnitorDsl.Ramp -> applyMod(Ignitors.ramp(freq.noMod(), analog.noMod()), accumulatedMod)
        is IgnitorDsl.Zawtooth -> applyMod(Ignitors.zawtooth(freq.noMod(), analog.noMod()), accumulatedMod)
        is IgnitorDsl.Pulze -> applyMod(Ignitors.pulze(freq.noMod(), duty.noMod(), analog.noMod()), accumulatedMod)
        is IgnitorDsl.Impulse -> applyMod(Ignitors.impulse(freq.noMod(), analog.noMod()), accumulatedMod)
        is IgnitorDsl.Silence -> applyMod(Ignitors.silence(), accumulatedMod)

        // Noise sources ignore phaseMod — skip ModApplyingIgnitor to avoid wasting cycles.
        is IgnitorDsl.WhiteNoise -> Ignitors.whiteNoise(Random)
        is IgnitorDsl.BrownNoise -> Ignitors.brownNoise(Random)
        is IgnitorDsl.PinkNoise -> Ignitors.pinkNoise(Random)
        is IgnitorDsl.PerlinNoise -> Ignitors.perlinNoise(Random, rate.noMod())
        is IgnitorDsl.BerlinNoise -> Ignitors.berlinNoise(Random, rate.noMod())
        is IgnitorDsl.Dust -> Ignitors.dust(Random, density.noMod())
        is IgnitorDsl.Crackle -> Ignitors.crackle(Random, density.noMod())

        is IgnitorDsl.SuperSaw -> applyMod(
            Ignitors.superSaw(freq.noMod(), voices.noMod(), freqSpread.noMod(), analog.noMod()),
            accumulatedMod
        )

        is IgnitorDsl.SuperSine -> applyMod(
            Ignitors.superSine(freq.noMod(), voices.noMod(), freqSpread.noMod(), analog.noMod()),
            accumulatedMod
        )

        is IgnitorDsl.SuperSquare -> applyMod(
            Ignitors.superSquare(freq.noMod(), voices.noMod(), freqSpread.noMod(), analog.noMod()),
            accumulatedMod
        )

        is IgnitorDsl.SuperTri -> applyMod(
            Ignitors.superTri(freq.noMod(), voices.noMod(), freqSpread.noMod(), analog.noMod()),
            accumulatedMod
        )

        is IgnitorDsl.SuperRamp -> applyMod(
            Ignitors.superRamp(freq.noMod(), voices.noMod(), freqSpread.noMod(), analog.noMod()),
            accumulatedMod
        )

        is IgnitorDsl.Pluck -> applyMod(
            Ignitors.karplusStrong(
                freq.noMod(),
                decay.noMod(),
                brightness.noMod(),
                pickPosition.noMod(),
                stiffness.noMod(),
                analog.noMod()
            ),
            accumulatedMod,
        )

        is IgnitorDsl.SuperPluck -> applyMod(
            Ignitors.superKarplusStrong(
                freq.noMod(),
                voices.noMod(),
                freqSpread.noMod(),
                decay.noMod(),
                brightness.noMod(),
                pickPosition.noMod(),
                stiffness.noMod(),
                analog.noMod()
            ),
            accumulatedMod,
        )

        // ── Arithmetic: pass mod to both children ──

        is IgnitorDsl.Plus -> left.withMod() + right.withMod()
        is IgnitorDsl.Times -> left.withMod() * right.withMod()
        is IgnitorDsl.Mul -> left.withMod().mul(right.withMod())
        is IgnitorDsl.Div -> left.withMod().div(right.withMod())

        // ── Frequency: pass mod through ──

        is IgnitorDsl.Detune -> inner.withMod().detune(semitones.noMod())

        // ── Filters: pass mod through to inner ──

        is IgnitorDsl.Lowpass -> inner.withMod().lowpass(cutoffHz.noMod(), q.noMod())
        is IgnitorDsl.Highpass -> inner.withMod().highpass(cutoffHz.noMod(), q.noMod())
        is IgnitorDsl.OnePoleLowpass -> inner.withMod().onePoleLowpass(cutoffHz.noMod())
        is IgnitorDsl.Bandpass -> inner.withMod().bandpass(cutoffHz.noMod(), q.noMod())
        is IgnitorDsl.Notch -> inner.withMod().notch(cutoffHz.noMod(), q.noMod())

        // ── Envelope: pass mod through ──

        is IgnitorDsl.Adsr -> inner.withMod().adsr(attackSec.noMod(), decaySec.noMod(), sustainLevel.noMod(), releaseSec.noMod())

        // ── Effects: pass mod through to inner ──

        is IgnitorDsl.Distort -> inner.withMod().distort(amount.noMod(), shape, Oversampler.factorToStages(oversample))
        is IgnitorDsl.Drive -> inner.withMod().drive(amount.noMod(), driveType)
        is IgnitorDsl.Clip -> inner.withMod().clip(shape, Oversampler.factorToStages(oversample))
        is IgnitorDsl.Crush -> inner.withMod().crush(amount.noMod())
        is IgnitorDsl.Coarse -> inner.withMod().coarse(amount.noMod())
        is IgnitorDsl.Phaser -> inner.withMod().phaser(rate.noMod(), mix.noMod(), center.noMod(), sweep.noMod())
        is IgnitorDsl.Tremolo -> inner.withMod().tremolo(rate.noMod(), depth.noMod())
        is IgnitorDsl.Shimmer -> inner.withMod().shimmer(mix.noMod(), feedback.noMod(), tone.noMod())
    }
}
