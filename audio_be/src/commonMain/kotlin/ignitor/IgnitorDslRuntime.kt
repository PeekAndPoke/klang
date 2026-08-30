/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.filters.EqCore
import io.peekandpoke.klang.audio_be.filters.butterworthQLadder
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.childNodes
import io.peekandpoke.klang.audio_bridge.coercePasses
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
 * Ignitor exactly once, keyed by `(object identity, accumulated pitch mod, detune context)`.
 * Two references to the same DSL node with the same mod chain share one [MemoizingIgnitor].
 * Two references with different mod chains (e.g., `s + s.vibrato(...)`) produce independent
 * Ignitors — each with its own phase accumulator. The DETUNE CONTEXT (ledger D13, redesigned
 * 2026-08-30) makes sharing never cross a `detune` boundary: `s + s.detune(12)` builds two
 * honest instances (the overlay semantics — the instrument transposed), because one shared
 * stateful instance called at two frequencies per window double-advances its state. The
 * identity FOLD is the flip side: a pitch-free subtree (no [IgnitorDsl.Freq] consumer) folds
 * the detune away and STAYS shared — forking noise would decorrelate it (two instances draw
 * different rng), and detune is a pitch op on a signal that has no pitch.
 *
 * **Pitch-mod bubbling:**
 *
 * Pitch-mod DSL nodes ([IgnitorDsl.Vibrato], [IgnitorDsl.Accelerate], [IgnitorDsl.PitchEnvelope],
 * [IgnitorDsl.Fm]) do not become Ignitor wrappers. Instead, they produce a mod Ignitor (ratio-space,
 * 1.0 = no change) that is accumulated and passed down to the source oscillator via
 * [ModApplyingIgnitor]. Insert effects and binary ops pass the mod through transparently.
 */
fun IgnitorDsl.buildExciter(
    oscParams: Map<String, Double>? = null,
    soundIndex: Int = 0,
    phasePools: PhasePools? = null,
    orbit: Int = 0,
    random: Random = Random,
    freqHz: Double = 0.0,
): BuiltIgnitor {
    val cache = IgnitorBuildCache(soundIndex, phasePools, orbit, random, freqHz)
    return buildIgnitor(oscParams, cache)
}

/**
 * Signal-only convenience over [buildExciter], for callers that do not need the build's findings
 * (see [BuiltIgnitor]). The production path goes through [buildExciter], because voice lifetime
 * needs the release tail.
 */
fun IgnitorDsl.toExciter(
    oscParams: Map<String, Double>? = null,
    soundIndex: Int = 0,
    phasePools: PhasePools? = null,
    orbit: Int = 0,
    random: Random = Random,
): Ignitor = buildExciter(oscParams, soundIndex, phasePools, orbit, random).ignitor

/**
 * Identity-based cache for DSL → Ignitor conversion, keyed on `(DSL node identity, mod
 * identity, detune context)`.
 *
 * Two references to the same DSL node with the same accumulated mod AND the same enclosing
 * detune chain share one Ignitor. Different mods, or different detune contexts (ledger D13),
 * produce independent entries — each with its own state.
 *
 * Also carries the per-call [soundIndex] so `IgnitorDsl.Variants` nodes can dispatch
 * without threading the value through every recursive call, and the current [detuneContext]
 * for the same reason (the Detune arm pushes/pops it around its inner build).
 */
internal class IgnitorBuildCache(
    val soundIndex: Int = 0,
    /** Per-playback unison phase pools; null → stateless banded fallback. Carried here (like
     *  [soundIndex]) so the value reaches the super-oscillator branches without threading a
     *  parameter through every recursive call. */
    val phasePools: PhasePools? = null,
    /** The voice's orbit ([VoiceData.cylinder]) — half of the pool key. */
    val orbit: Int = 0,
    /** The voice's random stream (see [IgniteContext.random]) — build-time consumers (noise,
     *  supersaw jitter) capture it here; generate-time constructions (drift) read the SAME
     *  instance from the context. Carried like [soundIndex] to reach the source branches
     *  without threading a parameter through every recursive call. */
    val random: Random = Random,
    /** The note's base frequency. Build-time control-rate reads need it ([FreqIgnitor] answers with
     *  it), which is how a pitch-relative release such as `Osc.freq().recip().mul(200)` resolves for
     *  voice lifetime. Carried here like [soundIndex] rather than threaded through every arm. */
    val freqHz: Double = 0.0,
) {
    /** The detune scope at the current recursion point — null at the root.
     *  Identity-compared as part of the cache key (pushed/popped by the Detune build arm). */
    var detuneContext: DetuneContext? = null

    private val dslKeys = ArrayList<IgnitorDsl>()
    private val modKeys = ArrayList<Ignitor?>()
    private val ctxKeys = ArrayList<DetuneContext?>()
    private val values = ArrayList<BuiltIgnitor>()

    inline fun getOrPut(key: IgnitorDsl, mod: Ignitor?, compute: () -> BuiltIgnitor): BuiltIgnitor {
        // Snapshot: the entry must be FILED under the context it was looked up in, even if
        // compute() misbehaves with the mutable field (review round 1 hardening).
        val ctx = detuneContext

        for (i in dslKeys.indices) {
            if (dslKeys[i] === key && modKeys[i] === mod && ctxKeys[i] === ctx) {
                val existing = values[i]
                val ignitor = existing.ignitor
                if (ignitor is MemoizingIgnitor) ignitor.incConsumers()
                // The tail rides along with the cached value on purpose — see [BuiltIgnitor].
                return existing
            }
        }
        val v = compute()
        dslKeys.add(key)
        modKeys.add(mod)
        ctxKeys.add(ctx)
        values.add(v)
        return v
    }

    // ── The D13 fold predicate, identity-memoized per build ─────────────────────────────────

    private val freqWalkKeys = ArrayList<IgnitorDsl>()
    private val freqWalkAnswers = ArrayList<Boolean>()

    /**
     * True when the subtree contains a consumer of the MUSICAL frequency — the freq ARGUMENT
     * every ignitor receives. Two consumer shapes exist: the [IgnitorDsl.Freq] leaf (every
     * note-pitched oscillator's freq param DEFAULTS to that leaf, so `Osc.sine()` is caught
     * while `Osc.sine(5)`'s `Constant(5.0)` is not), and [IgnitorDsl.Fm], whose runtime
     * normalizes the FM index by the freq argument (`depth / freqHz`) and drives the modulator
     * at `freqHz x ratio` with no leaf in the tree (review round 1 — the one such node,
     * verified by sweeping every freqHz-argument consumer in the ignitor package).
     *
     * [IgnitorDsl.Variants] resolves through the SAME pick as the build, so the decision
     * matches the subtree actually built. A nested [IgnitorDsl.Detune] answers with its INNER
     * only: if that folds, the nested semitones is never built and its Freq could never be
     * consumed; if it forks, the answer is true regardless.
     *
     * Answers are memoized by node identity for the build's lifetime — a shared-`let` diamond
     * would otherwise be walked exponentially (the optimizer guards the same hazard with a
     * seen-set; the build itself is immune via the identity cache).
     *
     * Known-conservative direction (recorded, accepted): wave/super oscillators re-anchor
     * their OWN param reads to `actualFreq`, so a `Freq` leaf inside e.g. an absolute-freq
     * sine's analog slot resolves to the constant, not the note — the predicate still answers
     * true and forks a subtree the detune provably cannot reach. A detune is never LOST in
     * that direction. The reverse direction is the dangerous one: a NEW node whose runtime
     * consumes the freq ARGUMENT without a Freq leaf must be added to the if-chain above, or
     * its detune folds away silently (the round-1 Fm bug; the consumer sweep and the pointer
     * in `childNodes`' KDoc are the tripwires, and `DetuneForkSpec`'s Fm row pins the known
     * set).
     */
    fun usesMusicalFreq(node: IgnitorDsl): Boolean {
        if (node is IgnitorDsl.Freq) {
            return true
        }

        if (node is IgnitorDsl.Fm) {
            return true
        }

        if (node is IgnitorDsl.Detune) {
            return usesMusicalFreq(node.inner)
        }

        if (node is IgnitorDsl.Variants) {
            return usesMusicalFreq(node.pick(soundIndex))
        }

        for (i in freqWalkKeys.indices) {
            if (freqWalkKeys[i] === node) {
                return freqWalkAnswers[i]
            }
        }

        val answer = node.childNodes().any { usesMusicalFreq(it) }
        freqWalkKeys.add(node)
        freqWalkAnswers.add(answer)
        return answer
    }
}

/**
 * Identity token for one detune scope (ledger D13). The Detune build arm mints one per visit
 * and [IgnitorBuildCache.getOrPut] compares it by IDENTITY as part of the key; the chain
 * structure is implicit in the minting discipline (a nested detune mints inside its parent's
 * scope), nothing ever walks it, so the token carries no fields. A re-visit of the same
 * Detune node mints a fresh token only when the cache let the visit through: same
 * (node, mod, outer context) re-visits are stopped there, while a different MOD chain
 * legitimately mints a second scope (`d + d.vibrato(...)` was two instances before D13 too,
 * keyed by mod identity).
 */
internal class DetuneContext

/** The one Variants pick rule, shared by the build dispatch and the D13 fold predicate so the
 *  two can never judge different subtrees (review round 1). */
private fun IgnitorDsl.Variants.pick(soundIndex: Int): IgnitorDsl {
    require(children.isNotEmpty()) { "Osc.variants(...) must have at least one child" }
    return children[soundIndex.mod(children.size)]
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
): BuiltIgnitor {
    // ── Leaves: direct return, no cache. Note the oscParams lookup: an `.oscp(...)` override is
    //    folded into the leaf HERE, so any later read of this node (including the build-time
    //    release read in the Adsr arm) sees the overridden value with no second lookup rule. ──
    when (this) {
        is IgnitorDsl.Param -> return BuiltIgnitor(ParamIgnitor(name, oscParams?.get(name) ?: default))
        is IgnitorDsl.Constant -> return BuiltIgnitor(ConstantIgnitor(value))
        is IgnitorDsl.Freq -> return BuiltIgnitor(FreqIgnitor)
        else -> { /* fall through */
        }
    }

    // ── Optimizer marker: a registration-time hint, invisible at render. Dissolve it. ──
    if (this is IgnitorDsl.OptimizerHint) {
        return inner.buildIgnitor(oscParams, cache, accumulatedMod)
    }

    // ── Variants: dispatch on cache.soundIndex, no cache entry for this node itself. ──
    if (this is IgnitorDsl.Variants) {
        return pick(cache.soundIndex).buildIgnitor(oscParams, cache, accumulatedMod)
    }

    // ── Pitch-mod nodes: absorb into mod, descend. No cache/Memoized for this node itself. ──
    when (this) {
        is IgnitorDsl.Vibrato -> {
            val vibMod = vibratoModIgnitor(
                rate = this.rate.buildIgnitor(oscParams, cache).ignitor,
                semitones = this.semitones.buildIgnitor(oscParams, cache).ignitor,
            )
            return inner.buildIgnitor(oscParams, cache, combineMods(accumulatedMod, vibMod))
        }

        is IgnitorDsl.Accelerate -> {
            val accelMod = accelerateModIgnitor(this.semitones.buildIgnitor(oscParams, cache).ignitor)
            return inner.buildIgnitor(oscParams, cache, combineMods(accumulatedMod, accelMod))
        }

        is IgnitorDsl.PitchEnvelope -> {
            val peMod = pitchEnvelopeModIgnitor(
                attackSec = this.attackSec.buildIgnitor(oscParams, cache).ignitor,
                decaySec = this.decaySec.buildIgnitor(oscParams, cache).ignitor,
                releaseSec = this.releaseSec.buildIgnitor(oscParams, cache).ignitor,
                semitones = this.semitones.buildIgnitor(oscParams, cache).ignitor,
                curve = this.curve.buildIgnitor(oscParams, cache).ignitor,
                anchor = this.anchor.buildIgnitor(oscParams, cache).ignitor,
            )
            return inner.buildIgnitor(oscParams, cache, combineMods(accumulatedMod, peMod))
        }

        is IgnitorDsl.PitchMod -> {
            val userMod = this.mod.buildIgnitor(oscParams, cache).ignitor
            val ratioMod = deviationToRatioIgnitor(userMod)
            return inner.buildIgnitor(oscParams, cache, combineMods(accumulatedMod, ratioMod))
        }

        is IgnitorDsl.Fm -> {
            val modulatorBuilt = modulator.buildIgnitor(oscParams, cache)
            val fmMod = fmModIgnitor(
                modulator = modulatorBuilt.ignitor,
                ratio = this.ratio.buildIgnitor(oscParams, cache).ignitor,
                depth = this.depth.buildIgnitor(oscParams, cache).ignitor,
                envAttackSec = this.envAttackSec.buildIgnitor(oscParams, cache).ignitor,
                envDecaySec = this.envDecaySec.buildIgnitor(oscParams, cache).ignitor,
                envSustainLevel = this.envSustainLevel.buildIgnitor(oscParams, cache).ignitor,
                envReleaseSec = this.envReleaseSec.buildIgnitor(oscParams, cache).ignitor,
            )
            val carrierBuilt = carrier.buildIgnitor(oscParams, cache, combineMods(accumulatedMod, fmMod))
            // The modulator is not on the amplitude spine, but the old `maxReleaseSec` counted it
            // (`maxOf(carrier, modulator)`). Keep counting it: over-counting only over-allocates
            // lifetime, whereas dropping it would silently shorten voices that render fine today.
            return carrierBuilt.copy(
                releaseTailSec = maxTail(carrierBuilt.releaseTailSec, modulatorBuilt.releaseTailSec),
            )
        }

        else -> { /* fall through to cache+memoize path */
        }
    }

    // ── Everything else: identity-cache + MemoizingIgnitor wrap. ──
    return cache.getOrPut(this, accumulatedMod) {
        val raw = buildRaw(oscParams, cache, accumulatedMod)
        val ignitor = raw.ignitor
        // A FOLDED Detune hands back its child's already-memoized ignitor — wrapping it again
        // would stack a second per-block cache on the same node (double delegation, one more
        // allocation per note-on). Aliasing the child's memo is correct because it behaves
        // exactly like sharing the child directly: a later reference to either node
        // incConsumers() the ONE memo, and if a caller ever presents a different freq (the fm
        // MODULATOR door can — E8), the memo's freq key keeps it honest, same as any share.
        raw.copy(ignitor = if (ignitor is MemoizingIgnitor) ignitor else MemoizingIgnitor(ignitor))
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
 *
 * Also reports the subtree's release tail (see [BuiltIgnitor]), absorbed from signal-path children
 * only. The absorb happens inside the local `withMod` helper, not per arm.
 */
private fun IgnitorDsl.buildRaw(
    oscParams: Map<String, Double>?,
    cache: IgnitorBuildCache,
    accumulatedMod: Ignitor?,
): BuiltIgnitor {
    // Release tails are absorbed from the SIGNAL SPINE only, and the split already exists in every
    // arm below: `withMod()` marks a signal-carrying edge (pitch mod propagates along it), `noMod()`
    // a parameter position. Doing the absorb HERE, once, means no arm has to remember and a new node
    // type cannot forget. Accepted rough edge: seven arms treat a parameter operand as signal for
    // pitch-mod reasons (Pow.exp, Div/Mod.right, Clamp/Range bounds, Lerp.t, Select.cond), so their
    // tails are absorbed too. That over-counts, which only over-allocates lifetime — harmless, and
    // the opposite direction (under-counting) is what truncates.
    var spineTail: Double? = null

    fun IgnitorDsl.withMod(mod: Ignitor? = accumulatedMod): Ignitor {
        val built = buildIgnitor(oscParams, cache, mod)
        spineTail = maxTail(spineTail, built.releaseTailSec)
        return built.ignitor
    }

    fun IgnitorDsl.noMod(): Ignitor = buildIgnitor(oscParams, cache).ignitor

    val ignitor = when (this) {
        is IgnitorDsl.Param, is IgnitorDsl.Constant, is IgnitorDsl.Freq ->
            error("Leaf DSL nodes must be built in buildIgnitor, not buildRaw")

        is IgnitorDsl.Vibrato, is IgnitorDsl.Accelerate, is IgnitorDsl.PitchEnvelope, is IgnitorDsl.Fm, is IgnitorDsl.PitchMod ->
            error("Pitch-mod DSL nodes must be absorbed in buildIgnitor, not buildRaw")

        is IgnitorDsl.Variants ->
            error("Variants DSL nodes must be absorbed in buildIgnitor, not buildRaw")

        is IgnitorDsl.OptimizerHint ->
            error("OptimizerHint DSL nodes must be absorbed in buildIgnitor, not buildRaw")

        // ── Sources: apply accumulated mod ──

        is IgnitorDsl.Sine -> applyMod(Ignitors.sine(freq.noMod(), analog.noMod()), accumulatedMod)
        is IgnitorDsl.Sawtooth -> applyMod(
            Ignitors.sawtooth(freq.noMod(), analog.noMod(), resetSamples = resetSamples, shapeMax = shapeMax),
            accumulatedMod
        )
        is IgnitorDsl.Square -> applyMod(Ignitors.square(freq.noMod(), analog.noMod()), accumulatedMod)
        is IgnitorDsl.Triangle -> applyMod(Ignitors.triangle(freq.noMod(), analog.noMod()), accumulatedMod)
        is IgnitorDsl.Ramp -> applyMod(
            Ignitors.ramp(freq.noMod(), analog.noMod(), resetSamples = resetSamples, shapeMax = shapeMax),
            accumulatedMod
        )
        is IgnitorDsl.Zawtooth -> applyMod(Ignitors.zawtooth(freq.noMod(), analog.noMod()), accumulatedMod)
        is IgnitorDsl.Zamp -> applyMod(Ignitors.zamp(freq.noMod(), analog.noMod()), accumulatedMod)
        is IgnitorDsl.Pulze -> applyMod(
            Ignitors.pulze(
                freq.noMod(), duty.noMod(), analog.noMod(),
                flankSamples = flankSamples, riseFlank = riseFlank, fallFlank = fallFlank,
            ),
            accumulatedMod
        )
        is IgnitorDsl.RawPulze -> applyMod(Ignitors.rawPulze(freq.noMod(), duty.noMod(), analog.noMod()), accumulatedMod)
        is IgnitorDsl.Impulse -> applyMod(Ignitors.impulse(freq.noMod(), analog.noMod()), accumulatedMod)
        is IgnitorDsl.Silence -> applyMod(Ignitors.silence(), accumulatedMod)

        // Noise sources ignore phaseMod — skip ModApplyingIgnitor to avoid wasting cycles.
        is IgnitorDsl.WhiteNoise -> Ignitors.whiteNoise(cache.random, color.noMod())
        is IgnitorDsl.BrownNoise -> Ignitors.brownNoise(cache.random, depth.noMod())
        is IgnitorDsl.PinkNoise -> Ignitors.pinkNoise(cache.random)
        is IgnitorDsl.PerlinNoise -> Ignitors.perlinNoise(cache.random, rate.noMod(), octaves.noMod(), persistence.noMod())
        is IgnitorDsl.BerlinNoise -> Ignitors.berlinNoise(cache.random, rate.noMod(), octaves.noMod(), persistence.noMod())
        is IgnitorDsl.Dust -> Ignitors.dust(cache.random, density.noMod(), tail.noMod(), bipolar.noMod())
        is IgnitorDsl.Crackle -> Ignitors.crackle(cache.random, chaos.noMod())

        is IgnitorDsl.SuperSaw -> applyMod(
            Ignitors.superSaw(
                freq.noMod(), voices.noMod(), spread.noMod(), analog.noMod(), rng = cache.random,
                sideAtten = sideAtten, gainJitter = gainJitter, spreadPower = spreadPower,
                centerJitterScale = centerJitterScale,
                phasePool = phasePool, drawTries = drawTries, kMin = kMin, kMax = kMax,
                poolSize = poolSize, refreshEvery = refreshEvery, selection = selection, warmup = warmup,
                phasePools = cache.phasePools, orbit = cache.orbit,
            ),
            accumulatedMod
        )

        is IgnitorDsl.SuperSine -> applyMod(
            Ignitors.superSine(
                freq.noMod(), voices.noMod(), spread.noMod(), analog.noMod(), rng = cache.random,
                sideAtten = sideAtten, gainJitter = gainJitter, spreadPower = spreadPower,
                centerJitterScale = centerJitterScale,
                phasePool = phasePool, drawTries = drawTries, kMin = kMin, kMax = kMax,
                poolSize = poolSize, refreshEvery = refreshEvery, selection = selection, warmup = warmup,
                phasePools = cache.phasePools, orbit = cache.orbit,
            ),
            accumulatedMod
        )

        is IgnitorDsl.SuperSquare -> applyMod(
            Ignitors.superSquare(
                freq.noMod(), voices.noMod(), spread.noMod(), analog.noMod(), rng = cache.random,
                sideAtten = sideAtten, gainJitter = gainJitter, spreadPower = spreadPower,
                centerJitterScale = centerJitterScale,
                phasePool = phasePool, drawTries = drawTries, kMin = kMin, kMax = kMax,
                poolSize = poolSize, refreshEvery = refreshEvery, selection = selection, warmup = warmup,
                phasePools = cache.phasePools, orbit = cache.orbit,
            ),
            accumulatedMod
        )

        is IgnitorDsl.SuperTri -> applyMod(
            Ignitors.superTri(
                freq.noMod(), voices.noMod(), spread.noMod(), analog.noMod(), rng = cache.random,
                sideAtten = sideAtten, gainJitter = gainJitter, spreadPower = spreadPower,
                centerJitterScale = centerJitterScale,
                phasePool = phasePool, drawTries = drawTries, kMin = kMin, kMax = kMax,
                poolSize = poolSize, refreshEvery = refreshEvery, selection = selection, warmup = warmup,
                phasePools = cache.phasePools, orbit = cache.orbit,
            ),
            accumulatedMod
        )

        is IgnitorDsl.SuperRamp -> applyMod(
            Ignitors.superRamp(
                freq.noMod(), voices.noMod(), spread.noMod(), analog.noMod(), rng = cache.random,
                sideAtten = sideAtten, gainJitter = gainJitter, spreadPower = spreadPower,
                centerJitterScale = centerJitterScale,
                phasePool = phasePool, drawTries = drawTries, kMin = kMin, kMax = kMax,
                poolSize = poolSize, refreshEvery = refreshEvery, selection = selection, warmup = warmup,
                phasePools = cache.phasePools, orbit = cache.orbit,
            ),
            accumulatedMod
        )

        is IgnitorDsl.Pluck -> applyMod(
            Ignitors.karplusStrong(
                freq.noMod(),
                decay.noMod(),
                brightness.noMod(),
                pickPosition.noMod(),
                stiffness.noMod(),
                analog.noMod(),
                rng = cache.random,
            ),
            accumulatedMod,
        )

        is IgnitorDsl.SuperPluck -> applyMod(
            Ignitors.superKarplusStrong(
                freq.noMod(),
                voices.noMod(),
                spread.noMod(),
                decay.noMod(),
                brightness.noMod(),
                pickPosition.noMod(),
                stiffness.noMod(),
                analog.noMod(),
                rng = cache.random,
            ),
            accumulatedMod,
        )

        // ── Arithmetic: pass mod to both children ──

        is IgnitorDsl.Plus -> left.withMod() + right.withMod()
        is IgnitorDsl.Times -> left.withMod() * right.withMod()
        is IgnitorDsl.Div -> left.withMod().div(right.withMod())
        is IgnitorDsl.Minus -> left.withMod().minus(right.withMod())
        is IgnitorDsl.Neg -> inner.withMod().neg()
        is IgnitorDsl.Abs -> inner.withMod().abs()
        is IgnitorDsl.Pow -> base.withMod().pow(exp.withMod())
        is IgnitorDsl.Min -> left.withMod().min(right.withMod())
        is IgnitorDsl.Max -> left.withMod().max(right.withMod())
        is IgnitorDsl.Clamp -> inner.withMod().clamp(lo.withMod(), hi.withMod())
        is IgnitorDsl.Exp -> inner.withMod().exp()
        is IgnitorDsl.Log -> inner.withMod().log()
        is IgnitorDsl.Sqrt -> inner.withMod().sqrt()
        is IgnitorDsl.Sign -> inner.withMod().sign()
        is IgnitorDsl.Tanh -> inner.withMod().tanh()
        is IgnitorDsl.Lerp -> left.withMod().lerp(right.withMod(), t.withMod())
        is IgnitorDsl.Range -> inner.withMod().range(lo.withMod(), hi.withMod())
        is IgnitorDsl.Bipolar -> inner.withMod().bipolar()
        is IgnitorDsl.Unipolar -> inner.withMod().unipolar()
        is IgnitorDsl.Floor -> inner.withMod().floor()
        is IgnitorDsl.Ceil -> inner.withMod().ceil()
        is IgnitorDsl.Round -> inner.withMod().round()
        is IgnitorDsl.Frac -> inner.withMod().frac()
        is IgnitorDsl.Mod -> left.withMod().mod(right.withMod())
        is IgnitorDsl.Recip -> inner.withMod().recip()
        is IgnitorDsl.Sq -> inner.withMod().sq()
        is IgnitorDsl.Select -> cond.withMod().select(whenTrue.withMod(), whenFalse.withMod())

        // ── Frequency: pass mod through ──

        is IgnitorDsl.Detune -> {
            // Ledger D13 (redesigned 2026-08-30): sharing never crosses a detune boundary — the
            // inner subtree builds under a CHILD detune context, so `s + s.detune(12)` becomes
            // two honest instances (the overlay: the instrument transposed) instead of one
            // instance double-advanced at two freqs per window. The runtime wrapper is
            // unchanged: multiplying the freq ARGUMENT is what scopes detune to musical
            // frequencies (Freq-derived pitches move; an `Osc.sine(5)` LFO ignores the
            // argument and stays put).
            if (cache.usesMusicalFreq(inner)) {
                val outer = cache.detuneContext
                cache.detuneContext = DetuneContext()
                val forked = inner.withMod()
                cache.detuneContext = outer

                // Semitones is built OUTSIDE the child context — it controls the detune, it is
                // not detuned by it.
                forked.detune(semitones.noMod())
            } else {
                // The identity fold — semantically load-bearing, not perf: this subtree has no
                // musical-frequency consumer, so a pitch shift of it IS it, and forking would
                // DECORRELATE noise (a second instance draws different rng). Build shared, in
                // the unchanged context, no DetuneIgnitor — the wrap site below reuses the
                // child's own memo instead of stacking a second one.
                inner.withMod()
            }
        }

        // ── Filters: pass mod through to inner ──

        is IgnitorDsl.Lowpass -> {
            // C5: `passes` cascades the stage with the Butterworth q ladder (relative
            // factors — the modulated q scales every stage coherently). passes = 1 is the
            // untouched single-stage path, bit-identical. `analog` is handed to EVERY stage,
            // so its drive character compounds with the slope (documented, not a bug).
            val n = coercePasses(passes)
            if (n == 1) {
                inner.withMod().lowpass(freq.noMod(), q.noMod(), analog = analog.noMod())
            } else {
                val rel = butterworthQLadder(n, 1.0)
                var chain = inner.withMod()
                for (k in 0 until n) {
                    chain = chain.lowpass(freq.noMod(), q.noMod().scaledBy(rel[k]), analog = analog.noMod())
                }
                chain
            }
        }

        is IgnitorDsl.Highpass -> {
            // See Lowpass above: same ladder, same analog-compounding note.
            val n = coercePasses(passes)
            if (n == 1) {
                inner.withMod().highpass(freq.noMod(), q.noMod(), analog = analog.noMod())
            } else {
                val rel = butterworthQLadder(n, 1.0)
                var chain = inner.withMod()
                for (k in 0 until n) {
                    chain = chain.highpass(freq.noMod(), q.noMod().scaledBy(rel[k]), analog = analog.noMod())
                }
                chain
            }
        }
        is IgnitorDsl.OnePoleLowpass -> inner.withMod().onePoleLowpass(freq.noMod())
        is IgnitorDsl.Bandpass -> inner.withMod().bandpass(freq.noMod(), q.noMod(), analog = analog.noMod())
        is IgnitorDsl.Notch -> inner.withMod().notch(freq.noMod(), q.noMod(), analog = analog.noMod())

        // Eq: withMod ONLY on inner; noMod on all section params — mirrors the filter arms
        // above (a withMod param subtree would change the freqHz the params see and break
        // tracking-HP parity). The exhaustive `when` below IS the wire→EqCore type mapping:
        // a new EqSection variant without an arm fails compilation.
        is IgnitorDsl.Eq -> EqIgnitor(
            upstream = inner.withMod(),
            sections = sections.map { s ->
                when (s) {
                    is IgnitorDsl.EqSection.Lowpass ->
                        EqIgnitor.Section(EqCore.LOWPASS, s.freq.noMod(), s.q.noMod())
                    is IgnitorDsl.EqSection.Highpass ->
                        EqIgnitor.Section(EqCore.HIGHPASS, s.freq.noMod(), s.q.noMod())
                    is IgnitorDsl.EqSection.Bandpass ->
                        EqIgnitor.Section(EqCore.BANDPASS, s.freq.noMod(), s.q.noMod())
                    is IgnitorDsl.EqSection.Notch ->
                        EqIgnitor.Section(EqCore.NOTCH, s.freq.noMod(), s.q.noMod())
                    is IgnitorDsl.EqSection.Bell ->
                        EqIgnitor.Section(EqCore.BELL, s.freq.noMod(), s.q.noMod(), db = s.db.noMod())
                    is IgnitorDsl.EqSection.RawTap ->
                        EqIgnitor.Section(EqCore.RAW_TAP, s.freq.noMod(), s.q.noMod(), gain = s.gain.noMod())
                }
            },
        )

        // ── Envelope: pass mod through ──

        is IgnitorDsl.Adsr -> {
            // Order matters and is unchanged: build order IS rng draw order (IgniteContext.random),
            // so inner / attack / decay / sustain / release / declick / expK stay in sequence.
            val innerIgnitor = inner.withMod()
            val attack = attackSec.noMod()
            val decay = decaySec.noMod()
            val sustain = sustainLevel.noMod()
            val release = releaseSec.noMod()

            // This node's own tail. controlRateValueOrNull folds Constant/Param leaves AND pointwise
            // expressions over them, so `pRel.mul(2)` and `Osc.freq().recip().mul(200)` resolve
            // exactly, and an `.oscp("release", ...)` override is already baked into the ParamIgnitor
            // (see the Param leaf in buildIgnitor). null = the release time is itself modulated, so
            // no static answer exists: contribute nothing rather than guess.
            spineTail = maxTail(spineTail, release.controlRateValueOrNull(cache.freqHz))

            innerIgnitor.adsr(
                attack, decay, sustain, release,
                // Unset curve = "exp" on EVERY stage and EVERY door (maintainer decision,
                // 2026-08-24) — the strip path's AdsrDef.Resolved already defaults Exponential.
                attackCurve ?: AdsrCurve.Default,
                decayCurve ?: AdsrCurve.Default,
                releaseCurve ?: AdsrCurve.Default,
                declickSeconds = declickSeconds.noMod(),
                expK = expK.noMod(),
            )
        }

        // ── Effects: pass mod through to inner ──

        is IgnitorDsl.Distort -> inner.withMod().distort(amount.noMod(), shape, Oversampler.factorToStages(oversample))
        is IgnitorDsl.Drive -> inner.withMod().drive(amount.noMod(), driveType)
        is IgnitorDsl.Shape -> inner.withMod().shape(shape, Oversampler.factorToStages(oversample))
        is IgnitorDsl.Crush -> inner.withMod().crush(amount.noMod())
        is IgnitorDsl.Coarse -> inner.withMod().coarse(amount.noMod())
        is IgnitorDsl.Phaser -> inner.withMod().phaser(rate.noMod(), wet.noMod(), center.noMod(), sweep.noMod(), dryFloor.noMod())
        is IgnitorDsl.Tremolo -> inner.withMod().tremolo(rate.noMod(), depth.noMod())
        is IgnitorDsl.Shimmer -> inner.withMod().shimmer(wet.noMod(), feedback.noMod(), tone.noMod(), pitches, dryFloor.noMod())
    }

    return BuiltIgnitor(ignitor, spineTail)
}
