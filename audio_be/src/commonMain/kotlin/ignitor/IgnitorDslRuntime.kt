/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.DistortionShape
import io.peekandpoke.klang.audio_be.LfoShape
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.distortionShapeAt
import io.peekandpoke.klang.audio_be.filters.butterworthQLadder
import io.peekandpoke.klang.audio_be.filters.eqSectionSpec
import io.peekandpoke.klang.audio_be.lfoShapeAt
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.LfoShapes
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.childNodes
import io.peekandpoke.klang.audio_bridge.coercePasses
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_DEPTH_SEMITONES
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.MOD_ENV_CURVE
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
    sampleRate: Int = DEFAULT_BUILD_SAMPLE_RATE,
    blockFrames: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES,
): BuiltIgnitor {
    val cache = IgnitorBuildCache(soundIndex, phasePools, orbit, random, freqHz, sampleRate, blockFrames)
    return buildIgnitor(oscParams, cache)
}

/**
 * The sample rate a build assumes when the caller does not say, a test/tool convenience exactly
 * like `random = Random` above. Only ONE build-time consumer reads it: the drift lane of a
 * filter with `humanize = true` derives its time constants from it (`analogDriftStepRate`).
 *
 * Every path that RENDERS A VOICE passes the backend's own rate, through
 * `IgnitorRegistry.createExciter`. Two callers do NOT, and neither renders a voice: [toExciter],
 * the signal-only convenience, does not forward the parameter at all, and `KatalystSlots` calls
 * `buildExciter()` bare to resolve an orbit knob. A filter node CAN appear in a `katp` override
 * (nothing rejects one); what it cannot do there is render, because `KatalystSlots` reads the
 * built graph through `controlRateValueOrNull`, which returns null for a filter and sends the knob
 * to its fallback. So a humanized filter written into an orbit knob would take its four draws off
 * the wrong stream and build a lane at 44100 that nothing ever steps. Absurd rather than
 * dangerous, and named here so the next reader does not have to rediscover it.
 */
const val DEFAULT_BUILD_SAMPLE_RATE: Int = 44100

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
    /** The backend's sample rate; read only by a `humanize` filter's drift lane. See
     *  [DEFAULT_BUILD_SAMPLE_RATE] for what the default means. */
    val sampleRate: Int = DEFAULT_BUILD_SAMPLE_RATE,
    /** The backend's block size; read only by a `humanize` filter's drift lane, which steps
     *  once per block. Pinned to 128 everywhere (it is a tone parameter, see
     *  [AudioBackendContext.RENDER_QUANTUM_FRAMES]). */
    val blockFrames: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES,
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
     * True when the subtree contains a consumer of the MUSICAL frequency — the [IgnitorDsl.Freq]
     * leaf. Every note-pitched consumer defaults its freq param to that leaf (`Osc.sine()` is
     * caught while `Osc.sine(5)`'s `Constant(5.0)` is not — and since `Fm.freq` joined that
     * convention there is NO special case left: the house rule is that runtime code never
     * consumes the freq argument except to forward it, so freq-dependence is always visible
     * structurally; see `IgnitorDslWalk`'s KDoc).
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
     * Known-conservative direction (recorded, accepted): wave/super oscillators — and since
     * the freq-param change, `Fm` — re-anchor their OWN param reads to the RESOLVED frequency,
     * so a `Freq` leaf inside e.g. an absolute-freq sine's analog slot (or an absolute-freq
     * fm's depth) resolves to the constant, not the note — the predicate still answers
     * true and forks a subtree the detune provably cannot reach. A detune is never LOST in
     * that direction. The reverse direction is the dangerous one: a NEW node whose runtime
     * consumed the freq ARGUMENT without a Freq leaf would fold its detune away silently — the
     * round-1 Fm bug, retired by giving Fm a `freq = Freq` param and stating the convention in
     * `childNodes`' KDoc; `DetuneForkSpec`'s FM rows pin it.
     */
    fun usesMusicalFreq(node: IgnitorDsl): Boolean {
        if (node is IgnitorDsl.Freq) {
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
    //
    //    A NON-FINITE override reads as UNSET and takes the slot's authored default. That is the
    //    general rule for every wire number (`/dsl-design` section 4), applied once, at the one
    //    place a slot resolves, rather than per slot name. Two reasons it is general: the bag is
    //    an open `Map<String, Double>` that any frontend may fill, so no name is safer than
    //    another; and a NaN that gets in multiplies through the rest of the tree and the voice
    //    never recovers. Sprudel alone can deliver one through a string atom (`"NaN"` and
    //    `"Infinity"` both parse) or an overflowing power. The Katalyst's own `SLOT_UNSET` slots
    //    do NOT pass through here: they resolve in `KatalystSlots` / `KatalystKnob` off
    //    `katalystParams`, where non-finite is the DECLARED off state and stays readable as such.
    when (this) {
        is IgnitorDsl.Param -> {
            val override = oscParams?.get(name)
            // NaN-guard on a value the author can write: a non-finite slot was never set.
            val value = if (override != null && override.isFinite()) override else default

            return BuiltIgnitor(ParamIgnitor(name, value))
        }

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
            // Build order IS rng draw order: attack, decay, release, semitones as before, then the
            // sustain in the slot the anchor had (the dropped `curve` was a leaf and drew nothing).
            // The three curves are read leaf-only (`adsrCurveKnob`) and build nothing.
            // The release is NOT reported as a tail: a pitch release never extends the voice.
            val peMod = pitchEnvelopeModIgnitor(
                attackSec = this.attackSec.buildIgnitor(oscParams, cache).ignitor,
                decaySec = this.decaySec.buildIgnitor(oscParams, cache).ignitor,
                releaseSec = this.releaseSec.buildIgnitor(oscParams, cache).ignitor,
                semitones = this.semitones.buildIgnitor(oscParams, cache).ignitor,
                sustainLevel = this.sustainLevel.buildIgnitor(oscParams, cache).ignitor,
                attackCurve = this.attackCurve.adsrCurveKnob(oscParams, cache, MOD_ENV_CURVE),
                decayCurve = this.decayCurve.adsrCurveKnob(oscParams, cache, MOD_ENV_CURVE),
                releaseCurve = this.releaseCurve.adsrCurveKnob(oscParams, cache, MOD_ENV_CURVE),
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
                freq = this.freq.buildIgnitor(oscParams, cache).ignitor,
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

// ═════════════════════════════════════════════════════════════════════════════════════════════
// The build-time GATE (`docs/plans/signal-flow-redesign.md` section 5, phase 3 step 2)
// ═════════════════════════════════════════════════════════════════════════════════════════════

/**
 * **THE RULE.** At voice build, a stage whose gating knob is a build-time constant and resolves
 * to the UNSET sentinel or to that stage's OFF value is NOT BUILT: the arm returns the inner.
 * A knob that is not a build-time constant (an LFO on the amount) stays unconditional, because it
 * can move within a note and no single build-time answer is correct for the whole note.
 *
 * Why it exists: a slotted tail (phase 3's `classic()`) declares every stage of today's voice
 * strip, and a pattern writes two of them. Without the gate the other seven each pay a scratch
 * render and a buffer copy per block per voice at their off value, and a nine-stage tail with
 * nothing written costs more than ten times a bare saw. With the gate a plain `sound("saw")` IS a
 * bare saw. The measurements have ONE home, `audio/MEMORY.md`'s entry for this rule, so that a
 * re-measurement never has to be chased through comments.
 *
 * **It is also a NaN GUARDRAIL, and that is the half a reviewer must not weaken.** `SLOT_UNSET`
 * is `Double.NaN`, and the [IgnitorDsl.Param] leaf above reads a NON-FINITE OVERRIDE as unset and
 * hands back the slot's DEFAULT, so a slot whose default IS the sentinel resolves to NaN at the
 * leaf, and nothing downstream of it is a second line of defence (a NaN cutoff is substituted by
 * `bilinearK`'s guard, a NaN amount multiplies straight through). The `!isFinite()` arm below is
 * what keeps that NaN out of the DSP, for EVERY gated stage and without a per-stage rule, which
 * is why it sits here and not in each arm's own comparison. `IgnitorGateSpec` pins it.
 *
 * **Restricted to the two LEAVES on purpose.** The query has to be answered BEFORE the stage is
 * built, and building a knob subtree in order to ask it would move that subtree's BUILD-TIME rng
 * draws in front of the inner's. Several nodes take theirs in a property initialiser, which is
 * construction: `crackle` seeds its chaotic map with two, `perlin` and `berlin` a permutation
 * table and a start position, and the sample ignitor's `AnalogDrift` lane three. Moving those is
 * the same class of hazard step 1 found in `perVoiceCutoffOffsetMul`, and it is silent (the other
 * sources capture the stream and draw at GENERATE time instead, where build order cannot reach
 * them). A [IgnitorDsl.Param] or [IgnitorDsl.Constant] leaf provably draws nothing, needs no cache
 * entry and is the exact shape the plan's rule names. `controlRateValueOrNull` would fold
 * pointwise EXPRESSIONS over those leaves too, which is strictly stronger, but telling a draw-free
 * expression from a drawing one needs a new walker, so that strength stays unused here.
 *
 * **What the leaf restriction does NOT cover, stated because it is a real consequence and it was
 * chosen.** A gated-off stage is not built at all, so its SIBLING knobs are not built either: a
 * `perlin` in a gated filter's `q`, or in a gated tremolo's `rate`, no longer takes its build-time
 * draws, and every drawing node after it in the build order shifts. That is not the query's doing,
 * it is what "the stage does not exist" means. `IgnitorGateSpec` pins the chosen behaviour so
 * nobody "fixes" it by accident, and no tree in the corpus has a drawing sibling under a gated
 * stage (the whole-corpus render of 2026-09-20).
 *
 * Three options were on the table, not two, and this is the one taken:
 *  1. **Do not gate when a sibling could draw.** Rejected, and it is the worst of the three: a
 *     filter whose cutoff is UNSET would then be BUILT because its `q` happens to draw, and
 *     `bilinearK`'s guard would silently substitute 1 kHz. That is the step-1 defect on the stage
 *     the gate exists for.
 *  2. **Gate, but still build the knob subtrees in declaration order and discard them.** This
 *     reproduces the rng stream exactly, since the draws sit in property initialisers, and costs
 *     nothing per block. NOT taken, and not because it is free: `buildIgnitor` CACHES every
 *     non-leaf build, so a discarded knob subtree that is also referenced on the live spine (a
 *     `let`-bound LFO, which is how people write them) is reached a second time through
 *     `IgnitorBuildCache.getOrPut`, which calls `incConsumers()` and flips that node's
 *     [MemoizingIgnitor] from pure delegation to a per-block cache plus a buffer copy, for every
 *     block of the voice. It also reintroduces per-note-on allocation on the OFF path, which is
 *     the path the gate exists to make cheap. Worth revisiting in step 3, when every filter cutoff
 *     becomes an unset-default slot and the case stops being hypothetical.
 *  3. **Gate, and let the siblings go with the stage.** Taken. Simple, and the consequence is
 *     visible in a spec row rather than lurking.
 *
 * Cost: the ON path builds the knob leaf twice (once to ask, once in the arm) and the query boxes
 * one `Double` on JVM. Both are note-on, both are one small object, and the OFF path removes far
 * more than that (the stage node, its `BuiltIgnitor` and its [MemoizingIgnitor]). Nothing here
 * runs per block. The ON path's build cost is not measured by any benchmark here.
 *
 * @param isOff the stage's own off test, applied to a FINITE value only. The off values are one
 *   table and it lives in `docs/tasks/builtin-instruments.md`, section 5b; each call site below
 *   carries its row.
 */
private inline fun IgnitorDsl.gatedOff(
    oscParams: Map<String, Double>?,
    cache: IgnitorBuildCache,
    isOff: (Double) -> Boolean,
): Boolean {
    val value = buildTimeKnobValue(oscParams, cache) ?: return false

    return !value.isFinite() || isOff(value)
}

/**
 * The knob's build-time value, or `null` when it is not a build-time constant and the stage must
 * therefore be built unconditionally. The leaf restriction and its reason live in [gatedOff].
 */
private fun IgnitorDsl.buildTimeKnobValue(oscParams: Map<String, Double>?, cache: IgnitorBuildCache): Double? {
    if (this !is IgnitorDsl.Param && this !is IgnitorDsl.Constant) {
        return null
    }

    // The leaf resolves the wire bag and the unset rule in ONE place (the Param arm of
    // buildIgnitor); asking the built leaf keeps this function from owning a second copy of it.
    return buildIgnitor(oscParams, cache).ignitor.controlRateValueOrNull(cache.freqHz)
}

/**
 * The `mul` row of the gate: a factor of EXACTLY 1.0 is not built.
 *
 * **Unset is NOT off here, and that is the one deliberate asymmetry in the table.** Every other
 * gated knob steers a stage that would take a NaN into the DSP unguarded, which is why [gatedOff]
 * switches those off on any non-finite value. A multiply is different in both directions: its own
 * `safeOut` already turns a non-finite factor into an exact zero rather than into NaN, so there is
 * nothing to guard; and a multiply is ARITHMETIC, not a stage, so it also sits in parameter
 * positions where a non-finite coefficient is a value the graph is meant to sanitise, not an
 * absence (`AffineIgnitorSpec`'s non-finite rows and the C5 non-finite-q row pin exactly that).
 * The consequence for the slot side: a `mul` slot must default to a safe literal such as `1.0`,
 * never to `SLOT_UNSET`, or an unwritten one silences the voice. `Slots.pregain` does.
 *
 * The caller must also check [survivesUnityFold] on the side that would survive. See there.
 */
private fun IgnitorDsl.gatedOffAtUnity(oscParams: Map<String, Double>?, cache: IgnitorBuildCache): Boolean {
    val value = buildTimeKnobValue(oscParams, cache) ?: return false

    return value == 1.0
}

/**
 * May this built side survive a unity fold alone, with its multiply removed?
 *
 * Only if it is a SIGNAL. What the fold drops is the multiply's `safeOut`, and whether that scrub
 * was doing work depends entirely on where the multiply sits:
 *
 *  - in a PARAMETER position the survivor is a control-rate expression, and the scrub is the thing
 *    that keeps a non-finite coefficient inside the engine's range. `q = param("res", +Inf).mul(1)`
 *    resolved to `SAFE_MAX` and then to the filter's q ceiling; folded, it stays `+Inf` and lands
 *    on the q fallback instead. Do NOT fold.
 *  - on the AUDIO spine the survivor is an oscillator, a filter or an envelope, and the scrub only
 *    fires on a sample that is already NaN or past `SAFE_MAX`. Fold.
 *
 * **The second bullet is a claim about the ENGINE, not a hope, and it had to be made true.** Round
 * 2 of this step's review found the counter-example inside the same change: `AdsrIgnitor` is not
 * block-constant, so it folds, and a non-finite `sustainLevel` or `expK` (a knob removed in step
 * 3c) used to multiply NaN into every sample. Before the fold `TimesIgnitor`'s scrub turned that into silence; after it the NaN
 * would travel, and "a later stage guards it" is false for a unity `mul` placed after an envelope
 * at the END of a tail (an authored `adsr(...).mul(slot)`; `classic()` itself places no pregain, and
 * the built-ins place theirs at the SOURCE, in front of every stage). The fix is at the source, in `AdsrIgnitor`'s own read (see
 * its `finiteOr`), not a clamp bolted back onto the multiply.
 *
 * **A rule for NEW nodes, not an audited invariant, and here is the known exception.** A node that
 * can emit a non-finite sample from finite input owes a substitution at its own read, the way the
 * envelope now does. The FEEDBACK sources do not obey it and must not be made to: `Pluck` and
 * `SuperPluck` write `delayLine[writePos] = filtered * decayVal` with `decay` read raw off
 * `Slots.decay`, so `oscp("decay", 10)` diverges geometrically to an infinity and the fractional
 * read turns it into NaN. That divergence is authored character and the Motor stays raw, so the
 * rule is "a new node owes it", not "every node has it". `note("c3").sound("pluck").oscp("decay", 10)`
 * is unguarded with or without this fold; what the fold contributes is that the built-ins' unity
 * `pregain` (placed on every built-in's source since phase 3 step 6) does not MASK it by scrubbing
 * the NaN to silence on its way out.
 *
 * `isBlockConstant` is exactly the parameter/signal distinction, structural and computed once at
 * construction: it is true for the leaves and for pointwise combinators over them, false for
 * everything that makes a signal. The pregain case (a saw, an adsr) folds; a coefficient never
 * does.
 *
 * The two doors differ here and it is deliberate: the hand-authoring `Ignitor.mul(Double)`
 * short-circuits at unity unconditionally, control-rate survivor included, because a Kotlin caller
 * writing a literal `1.0` has said "no multiply" rather than "a coefficient of one". The DSL door
 * cannot read intent that way, because the 1.0 it sees may be a slot nobody wrote.
 */
private fun Ignitor.survivesUnityFold(): Boolean = !isBlockConstant

/**
 * The gate for a stage whose only off state is an ABSENCE: the four filters. A lowpass at 20 kHz
 * is not "off", which is why the rule is "unset" and not a number. There is no numeric off short
 * of Nyquist, and picking one would silently delete a filter somebody meant.
 *
 * "Unset" here is ANY non-finite value, not only the `SLOT_UNSET` spelling: that is the house rule
 * for every wire number (`/dsl-design` section 4), and `SLOT_UNSET` is simply the value we WRITE.
 */
private fun IgnitorDsl.gatedOffWhenUnset(oscParams: Map<String, Double>?, cache: IgnitorBuildCache): Boolean =
    gatedOff(oscParams, cache) { false }


/**
 * The five cutoff-envelope knobs of a filter node, resolved at BUILD into the runtime's
 * [FilterEnvDef].
 *
 * **Why build time and not per block.** The runtime's envelope is a per-voice constant on the
 * strip too (`FilterDef.envelope` is resolved once, at note-on, in `VoiceFactory.toModulator`),
 * so a per-block read would be a new capability, not parity. It is also what lets the depth stay
 * the envelope's SWITCH: `SvfIgnitor` decides `hasEnv` at construction and a whole branch of its
 * block loop with it.
 *
 * **Leaf-only, through [buildTimeKnobValue], and that is load-bearing for the rng.** A
 * [IgnitorDsl.Param] or [IgnitorDsl.Constant] leaf provably draws nothing, so asking these five
 * moves no draw. Anything else has no build-time answer and the knob's own default stands, which
 * is the same answer the house gives a non-finite value (`/dsl-design` section 4: a non-finite
 * wire number reads as unset). The fallbacks are the shared constants, the same ones the door
 * fills with and the same ones `FilterEnvDef.resolve()` uses on the strip.
 *
 * **The DEPTH's fallback is 0, and that is a sharper edge than the other four.** A stage knob
 * that cannot be read falls back to a usable time; a depth that cannot be read falls back to the
 * OFF value, so `lowpass(800, x => x.env(Osc.param("e", 24).max(36)))` renders a static filter with no
 * warning at all. It is the honest answer here (an unreadable depth is not a depth) and the
 * alternative, substituting 7 semitones for an expression the author wrote, would invent a sweep
 * nobody asked for. Both filter-node KDocs say it out loud; the editor diagnostic of step 11 is
 * where it should become visible instead of silent.
 */
private fun filterEnvDef(
    env: IgnitorDsl,
    attackSec: IgnitorDsl,
    decaySec: IgnitorDsl,
    sustainLevel: IgnitorDsl,
    releaseSec: IgnitorDsl,
    attackCurve: IgnitorDsl,
    decayCurve: IgnitorDsl,
    releaseCurve: IgnitorDsl,
    oscParams: Map<String, Double>?,
    cache: IgnitorBuildCache,
): FilterEnvDef {
    val authoredDepth = env.filterEnvKnob(oscParams, cache, 0.0)

    // THE SLOT-LAYER FILL (phase 3 step 5, maintainer 2026-09-25): an UNSET depth slot the bag did not
    // write takes the shared depth when any of the four STAGE knobs is written, the strip's `depth ?: 7`.
    // See `writtenIn` for what "written" means, and `slotLayerDepth` for the whole rule.
    val depth = slotLayerDepth(authoredDepth, env, attackSec, decaySec, sustainLevel, releaseSec, oscParams)

    // The depth is the switch: with no sweep the four stage knobs are inert, and not reading
    // them keeps a filter without an envelope exactly as cheap to build as it was.
    if (depth == 0.0) {
        return FilterEnvDef.NONE
    }

    return FilterEnvDef(
        depth = depth,
        attackSec = attackSec.filterEnvKnob(oscParams, cache, FILTER_ENV_ATTACK_SEC),
        decaySec = decaySec.filterEnvKnob(oscParams, cache, FILTER_ENV_DECAY_SEC),
        sustainLevel = sustainLevel.filterEnvKnob(oscParams, cache, FILTER_ENV_SUSTAIN_LEVEL),
        releaseSec = releaseSec.filterEnvKnob(oscParams, cache, FILTER_ENV_RELEASE_SEC),
        // Index knobs since step 3c, read the same leaf-only way: no build, no draw.
        attackCurve = attackCurve.adsrCurveKnob(oscParams, cache, MOD_ENV_CURVE),
        decayCurve = decayCurve.adsrCurveKnob(oscParams, cache, MOD_ENV_CURVE),
        releaseCurve = releaseCurve.adsrCurveKnob(oscParams, cache, MOD_ENV_CURVE),
    )
}

/**
 * **The compound fill for the filter envelope, answered at the SLOT layer** (phase 3 step 5; decided by
 * the maintainer 2026-09-25, `docs/tasks/builtin-instruments.md` section 5b's neighbourhood). The rule's
 * text has one home, `/dsl-design` section 4; this is what a SLOTTED filter does under it.
 *
 * A slotted filter (`classic()`'s) never calls the door per note, so the door's fill, which reads
 * "named against null" at call time, never happens. The same question is asked here one layer down,
 * against the note's bag: when the depth knob is an UNSET slot (a [IgnitorDsl.Param] whose DEFAULT is
 * non-finite, the `SLOT_UNSET` sentinel `classic()` places, and which the bag did NOT write) and ANY of
 * the four stage knobs is a `Param` the bag DID write, the depth is [FILTER_ENV_DEPTH_SEMITONES]
 * instead of "no envelope". That is the strip's `FilterEnvDef.resolve`, `depth ?: 7`, reached from a
 * pattern that writes only `lpf.attack`.
 *
 * What does NOT switch it on, each pinned by a row of `FilterSlotLayerFillSpec`:
 *  - a WRITTEN depth, an explicit 0 included: an explicit value is never overwritten by a fill (the
 *    strip keeps `lpf(env = 0)` static too);
 *  - an AUTHORED depth default, 0 included: `Osc.param("e", 0)` is the author saying "no sweep", and an
 *    authored default is never filled (round 1 of step 5's review found the first cut filling it);
 *  - an authored stage default alone: a stage `Param` whose default is a real number but that the bag
 *    did not write (`Osc.param("fa", 0.02)`) is not "written";
 *  - a non-finite value in the bag: it reads as unset, as it does at the `Param` leaf;
 *  - a CONSTANT depth: a door-filled or an authored constant is an explicit value, never a question.
 *
 * Five map lookups per filter per note-on at most, and nothing per block.
 */
private fun slotLayerDepth(
    authoredDepth: Double,
    env: IgnitorDsl,
    attackSec: IgnitorDsl,
    decaySec: IgnitorDsl,
    sustainLevel: IgnitorDsl,
    releaseSec: IgnitorDsl,
    oscParams: Map<String, Double>?,
): Double {
    // Only an UNSET depth slot is a question: a `Param` whose DEFAULT is the non-finite sentinel,
    // as `classic()` places it, and which the bag did not write. An authored default (0 or any other
    // number) and a written value are answers already. (An unset, unwritten slot always resolves to
    // 0 here, `filterEnvKnob`'s fallback, so no separate "depth is 0" test is needed.)
    if (env !is IgnitorDsl.Param || env.default.isFinite() || env.writtenIn(oscParams)) {
        return authoredDepth
    }

    val aStageIsWritten = attackSec.writtenIn(oscParams) || decaySec.writtenIn(oscParams) ||
        sustainLevel.writtenIn(oscParams) || releaseSec.writtenIn(oscParams)

    return if (aStageIsWritten) FILTER_ENV_DEPTH_SEMITONES else authoredDepth
}

/**
 * Is this knob WRITTEN by the note: a [IgnitorDsl.Param] whose name the bag holds a FINITE value for.
 * Finite-in-the-bag only (maintainer, 2026-09-25): an authored default never counts, and a non-finite
 * value is unset, the rule the `Param` leaf applies to every slot.
 */
private fun IgnitorDsl.writtenIn(oscParams: Map<String, Double>?): Boolean =
    this is IgnitorDsl.Param && oscParams?.get(name)?.isFinite() == true

/** One cutoff-envelope knob: its build-time value, or [fallback] when it has none. See [filterEnvDef]. */
private fun IgnitorDsl.filterEnvKnob(
    oscParams: Map<String, Double>?,
    cache: IgnitorBuildCache,
    fallback: Double,
): Double = buildTimeKnobValue(oscParams, cache)?.takeIf { it.isFinite() } ?: fallback

// ── The knobs read ONCE at voice build (phase 3 step 3b, 2026-09-25) ──
//
// A waveshaper's shape, its oversampling factor, and a tremolo's shape and start phase are chosen
// once per note, as they always were on both hosts: the shaper and the oversampler are built with
// the stage, and the start phase SEEDS the LFO's clock. They are knobs so that `classic()` can fill
// them from slots. Each is read the leaf-only way [filterEnvDef] reads its knobs: a `Param` or
// `Constant` leaf gives its value, and anything else has no build-time answer, takes the knob's
// default and is NOT BUILT, so asking moves no rng draw. None of them is a gate: they choose HOW a
// stage renders, never WHETHER it exists.

/**
 * The waveshaper a `shape` knob selects: `DistortionShapes.indexAt`'s rule, so a non-finite,
 * negative or past-the-end index is `soft`, as an unknown name is. A non-leaf is `soft`.
 */
private fun IgnitorDsl.distortionShapeKnob(oscParams: Map<String, Double>?, cache: IgnitorBuildCache): DistortionShape =
    distortionShapeAt(buildTimeKnobValue(oscParams, cache) ?: DistortionShapes.SOFT_INDEX.toDouble())

/**
 * The oversampler STAGES an `oversample` factor knob asks for, through the one conversion
 * ([Oversampler.factorOf], then [Oversampler.factorToStages]): a factor of 1 or less, a non-finite
 * one and a non-leaf are all 0 stages, today's plain path. Decision D7's stopgap.
 */
private fun IgnitorDsl.oversampleStagesKnob(oscParams: Map<String, Double>?, cache: IgnitorBuildCache): Int =
    Oversampler.factorToStages(Oversampler.factorOf(buildTimeKnobValue(oscParams, cache) ?: 0.0))

/**
 * The cascade count a filter's `passes` knob asks for (phase 3 step 5, the `lpf.passes` / `hpf.passes`
 * slots of `classic()`): through [coercePasses], the one coercion, so it ROUNDS, bounds to
 * 1..`FILTER_MAX_PASSES` and reads a non-finite value as one pass, exactly as the strip reads sprudel's
 * `lpf(passes = ...)`. A non-leaf has no build-time answer and is one pass.
 */
private fun IgnitorDsl.passesKnob(oscParams: Map<String, Double>?, cache: IgnitorBuildCache): Int =
    coercePasses(buildTimeKnobValue(oscParams, cache) ?: 1.0)

/** The LFO waveform a tremolo `shape` knob selects, `LfoShapes.indexAt`'s rule; a non-leaf is `sine`. */
private fun IgnitorDsl.lfoShapeKnob(oscParams: Map<String, Double>?, cache: IgnitorBuildCache): LfoShape =
    lfoShapeAt(buildTimeKnobValue(oscParams, cache) ?: LfoShapes.SINE_INDEX.toDouble())

/**
 * A tremolo's start phase in cycles; a non-leaf is 0. A non-finite value passes through on purpose:
 * `TremoloCore` folds the seed with `wrapPhase`, which turns it into 0, the strip's own rule.
 */
private fun IgnitorDsl.startPhaseKnob(oscParams: Map<String, Double>?, cache: IgnitorBuildCache): Double =
    buildTimeKnobValue(oscParams, cache) ?: 0.0

/**
 * The curve an envelope's curve knob selects (phase 3 step 3c): `AdsrCurves.curveAt`'s rule, so a
 * non-finite, negative or past-the-end index is [fallback], as a non-leaf is. The fallback is the
 * reading envelope's OWN default: [AdsrCurve.Default] on the chain `adsr`, `MOD_ENV_CURVE` on the
 * filter and pitch envelopes.
 */
private fun IgnitorDsl.adsrCurveKnob(
    oscParams: Map<String, Double>?,
    cache: IgnitorBuildCache,
    fallback: AdsrCurve,
): AdsrCurve = buildTimeKnobValue(oscParams, cache)?.let { AdsrCurves.curveAt(it, fallback) } ?: fallback

/**
 * The envelope row of the gate: the ADSR's `on` switch is OFF only when it is a build-time leaf at
 * exactly `0.0` (either sign). **Unset is NOT off**, the second asymmetry in the table after `mul`:
 * a non-finite value, a non-zero value and a non-leaf all leave the envelope ON, because the classic
 * envelope is built by default and only an explicit `adsrOff` switches it off. Exactly `0.0` rather
 * than `<= 0.0` because this is a FLAG, not an amount: the house flag rule is "non-zero is on"
 * (`coerceFlag` on the script door, `isTruthy` in sprudel), so a negative value is on there too.
 */
private fun IgnitorDsl.switchedOff(oscParams: Map<String, Double>?, cache: IgnitorBuildCache): Boolean {
    val value = buildTimeKnobValue(oscParams, cache) ?: return false

    return value == 0.0
}

/**
 * The release tail a switched-OFF envelope still reports, so the voice keeps the lifetime the
 * envelope would have given it (as the strip's `adsrOff` does).
 *
 * Leaf-only, the build-time knobs' rule, and it differs from the ON path in one case on purpose.
 * The ON path asks the BUILT release (`controlRateValueOrNull`), which also folds a pointwise
 * expression over leaves (`pRel.mul(2)`). An OFF envelope builds no knob subtree, and building the
 * release only to ask it would take that subtree's rng draws and register it in the build cache
 * (the gate's recorded hazard, `gatedOff`'s KDoc), so a NON-LEAF release on an off envelope reports
 * no tail: the voice then lives as long as the rest of the voice says. `classic()` places a slot, a
 * leaf, so the case that matters for step 6 is covered. A non-finite release reports none either,
 * the ON path's rule.
 */
private fun IgnitorDsl.offEnvelopeTail(oscParams: Map<String, Double>?, cache: IgnitorBuildCache): Double? =
    buildTimeKnobValue(oscParams, cache)?.takeIf { it.isFinite() }

/**
 * The filter's per-voice humanization, or null when the node does not carry it.
 *
 * The DRAW ORDER lives in [buildFilterHumanization]; this function only resolves the `analog`
 * amount that decides whether anything is drawn at all, and it resolves it the same leaf-only
 * way [filterEnvDef] resolves its knobs, so asking the question moves no draw either. A
 * modulated `analog` therefore humanizes nothing: there is no build-time answer, and the strip
 * has no such case to match (its `analog` is one number off the voice's bag).
 *
 * A GATED-OFF filter never reaches here, which is the rule "the stage does not exist" applied to
 * its draws as well: the gate's arm returns the inner before this runs.
 */
private fun IgnitorDsl.filterHumanization(
    humanize: Boolean,
    oscParams: Map<String, Double>?,
    cache: IgnitorBuildCache,
): FilterHumanization? {
    if (!humanize) {
        return null
    }

    val analogValue = buildTimeKnobValue(oscParams, cache) ?: 0.0

    return buildFilterHumanization(analogValue, cache.sampleRate, cache.blockFrames, cache.random)
}

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

    // Does this subtree gate its own OUTPUT (see [BuiltIgnitor.gatesOutput])? Absorbed along the same
    // signal edges as the tail, for the same reason: a tremolo in a PARAMETER position (an LFO on a
    // cutoff) silences nothing. The seven over-counting arms named above over-count here too, which
    // only switches the silence culler off for a voice that did not need it: harmless.
    var spineGatesOutput = false

    // Does THIS node end the voice in a built amplitude envelope (see [BuiltIgnitor.endsInEnvelope])?
    // Set by the `Adsr` arm when it builds, and by a stage that is NOT built (a gate row) from its
    // inner, because such a node IS its inner. Never absorbed from a child of a built stage: an
    // envelope under a later stage does not end the voice.
    var builtEnvelope = false

    // The last child `withMod` built, so a pass-through can hand its answer on.
    var lastChildEndsInEnvelope = false

    fun IgnitorDsl.withMod(mod: Ignitor? = accumulatedMod): Ignitor {
        val built = buildIgnitor(oscParams, cache, mod)
        spineTail = maxTail(spineTail, built.releaseTailSec)
        spineGatesOutput = spineGatesOutput || built.gatesOutput
        lastChildEndsInEnvelope = built.endsInEnvelope
        return built.ignitor
    }

    /** A gated-off stage: the node is not built and IS its inner, [BuiltIgnitor.endsInEnvelope] included. */
    fun IgnitorDsl.passThrough(): Ignitor {
        val ignitor = withMod()
        builtEnvelope = lastChildEndsInEnvelope

        return ignitor
    }

    fun IgnitorDsl.noMod(): Ignitor = buildIgnitor(oscParams, cache).ignitor

    /**
     * Builds a PITCHED source: applies [accumulatedMod] as before when the source's own `freq`
     * slot is musically derived, and otherwise shields it from pitch modulation entirely
     * ([ModBlockingIgnitor]).
     *
     * Ledger W13. `usesMusicalFreq` is the same predicate D13's detune fold uses, but asked of the
     * oscillator's OWN freq slot rather than of a whole subtree — that per-source question is
     * exactly what W13 flagged as missing. A detuned arm still answers yes (its freq is the `Freq`
     * leaf, forked under a detune context), so vibrato keeps moving detuned voices; a hand-rolled
     * `Osc.sine(5)` LFO answers no, and so does a fixed-pitch body resonance.
     *
     * Every pitched source arm below goes through here. A new oscillator that calls the bare
     * [applyMod] instead silently reopens W13 — [ModBlockingIgnitor]'s KDoc is the forcing note.
     */
    fun pitchedSource(freq: IgnitorDsl, source: Ignitor): Ignitor {
        if (!cache.usesMusicalFreq(freq)) {
            return ModBlockingIgnitor(source)
        }

        return applyMod(source, accumulatedMod)
    }

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

        // Literal defaults build the plain sine, bit-identical to before the partial banks existed;
        // any bank knob set builds the bank (a Param included: its value is only known at voice build).
        is IgnitorDsl.Sine -> if (isPlainSine()) {
            pitchedSource(freq, Ignitors.sine(freq.noMod(), analog.noMod()))
        } else {
            pitchedSource(
                freq,
                Ignitors.sinePartials(
                    freq.noMod(), analog.noMod(), fundamental.noMod(),
                    harmonics.noMod(), harmonicsRolloff.noMod(), octaves.noMod(), octavesRolloff.noMod(),
                    suboctaves.noMod(), suboctavesRolloff.noMod(), analogSpread.noMod(),
                ),
            )
        }
        is IgnitorDsl.Sawtooth -> pitchedSource(
            freq,
            Ignitors.sawtooth(freq.noMod(), analog.noMod(), resetSamples = resetSamples, shapeMax = shapeMax),
        )
        is IgnitorDsl.Square -> pitchedSource(freq, Ignitors.square(freq.noMod(), analog.noMod()))
        is IgnitorDsl.Triangle -> pitchedSource(freq, Ignitors.triangle(freq.noMod(), analog.noMod()))
        is IgnitorDsl.Ramp -> pitchedSource(
            freq,
            Ignitors.ramp(freq.noMod(), analog.noMod(), resetSamples = resetSamples, shapeMax = shapeMax),
        )
        is IgnitorDsl.Zawtooth -> pitchedSource(freq, Ignitors.zawtooth(freq.noMod(), analog.noMod()))
        is IgnitorDsl.Zamp -> pitchedSource(freq, Ignitors.zamp(freq.noMod(), analog.noMod()))
        is IgnitorDsl.Pulze -> pitchedSource(
            freq,
            Ignitors.pulze(
                freq.noMod(), duty.noMod(), analog.noMod(),
                flankSamples = flankSamples, riseFlank = riseFlank, fallFlank = fallFlank,
            ),
        )
        is IgnitorDsl.RawPulze -> pitchedSource(freq, Ignitors.rawPulze(freq.noMod(), duty.noMod(), analog.noMod()))
        is IgnitorDsl.Impulse -> pitchedSource(freq, Ignitors.impulse(freq.noMod(), analog.noMod()))
        is IgnitorDsl.Silence -> applyMod(Ignitors.silence(), accumulatedMod)

        // Noise sources ignore phaseMod — skip ModApplyingIgnitor to avoid wasting cycles.
        is IgnitorDsl.WhiteNoise -> Ignitors.whiteNoise(cache.random, color.noMod())
        is IgnitorDsl.BrownNoise -> Ignitors.brownNoise(cache.random, depth.noMod())
        is IgnitorDsl.PinkNoise -> Ignitors.pinkNoise(cache.random)
        is IgnitorDsl.PerlinNoise -> Ignitors.perlinNoise(cache.random, rate.noMod(), octaves.noMod(), persistence.noMod())
        is IgnitorDsl.BerlinNoise -> Ignitors.berlinNoise(cache.random, rate.noMod(), octaves.noMod(), persistence.noMod())
        is IgnitorDsl.Dust -> Ignitors.dust(cache.random, density.noMod(), tail.noMod(), bipolar.noMod())
        is IgnitorDsl.Crackle -> Ignitors.crackle(cache.random, chaos.noMod())

        is IgnitorDsl.SuperSaw -> pitchedSource(
            freq,
            Ignitors.superSaw(
                freq.noMod(), voices.noMod(), spread.noMod(), analog.noMod(), analogSpread.noMod(),
                rng = cache.random,
                sideAtten = sideAtten, gainJitter = gainJitter, spreadPower = spreadPower,
                centerJitterScale = centerJitterScale,
                phasePool = phasePool, drawTries = drawTries, kMin = kMin, kMax = kMax,
                poolSize = poolSize, refreshEvery = refreshEvery, selection = selection, warmup = warmup,
                phasePools = cache.phasePools, orbit = cache.orbit,
            ),
        )

        is IgnitorDsl.SuperSine -> pitchedSource(
            freq,
            Ignitors.superSine(
                freq.noMod(), voices.noMod(), spread.noMod(), analog.noMod(), analogSpread.noMod(),
                rng = cache.random,
                sideAtten = sideAtten, gainJitter = gainJitter, spreadPower = spreadPower,
                centerJitterScale = centerJitterScale,
                phasePool = phasePool, drawTries = drawTries, kMin = kMin, kMax = kMax,
                poolSize = poolSize, refreshEvery = refreshEvery, selection = selection, warmup = warmup,
                phasePools = cache.phasePools, orbit = cache.orbit,
            ),
        )

        is IgnitorDsl.SuperSquare -> pitchedSource(
            freq,
            Ignitors.superSquare(
                freq.noMod(), voices.noMod(), spread.noMod(), analog.noMod(), analogSpread.noMod(),
                rng = cache.random,
                sideAtten = sideAtten, gainJitter = gainJitter, spreadPower = spreadPower,
                centerJitterScale = centerJitterScale,
                phasePool = phasePool, drawTries = drawTries, kMin = kMin, kMax = kMax,
                poolSize = poolSize, refreshEvery = refreshEvery, selection = selection, warmup = warmup,
                phasePools = cache.phasePools, orbit = cache.orbit,
            ),
        )

        is IgnitorDsl.SuperTri -> pitchedSource(
            freq,
            Ignitors.superTri(
                freq.noMod(), voices.noMod(), spread.noMod(), analog.noMod(), analogSpread.noMod(),
                rng = cache.random,
                sideAtten = sideAtten, gainJitter = gainJitter, spreadPower = spreadPower,
                centerJitterScale = centerJitterScale,
                phasePool = phasePool, drawTries = drawTries, kMin = kMin, kMax = kMax,
                poolSize = poolSize, refreshEvery = refreshEvery, selection = selection, warmup = warmup,
                phasePools = cache.phasePools, orbit = cache.orbit,
            ),
        )

        is IgnitorDsl.SuperRamp -> pitchedSource(
            freq,
            Ignitors.superRamp(
                freq.noMod(), voices.noMod(), spread.noMod(), analog.noMod(), analogSpread.noMod(),
                rng = cache.random,
                sideAtten = sideAtten, gainJitter = gainJitter, spreadPower = spreadPower,
                centerJitterScale = centerJitterScale,
                phasePool = phasePool, drawTries = drawTries, kMin = kMin, kMax = kMax,
                poolSize = poolSize, refreshEvery = refreshEvery, selection = selection, warmup = warmup,
                phasePools = cache.phasePools, orbit = cache.orbit,
            ),
        )

        is IgnitorDsl.Pluck -> pitchedSource(
            freq,
            Ignitors.karplusStrong(
                freq.noMod(),
                decay.noMod(),
                brightness.noMod(),
                pickPosition.noMod(),
                stiffness.noMod(),
                analog.noMod(),
                rng = cache.random,
            ),
        )

        is IgnitorDsl.SuperPluck -> pitchedSource(
            freq,
            Ignitors.superKarplusStrong(
                freq.noMod(),
                voices.noMod(),
                spread.noMod(),
                decay.noMod(),
                brightness.noMod(),
                pickPosition.noMod(),
                stiffness.noMod(),
                analog.noMod(),
                analogSpread.noMod(),
                rng = cache.random,
            ),
        )

        // ── Arithmetic: pass mod to both children ──

        is IgnitorDsl.Plus -> left.withMod() + right.withMod()

        // GATE ROW `mul`: a factor of EXACTLY 1.0 over a SIGNAL is not built, and the signal is
        // returned (unset is deliberately NOT off here, and a control-rate survivor never folds;
        // see `gatedOffAtUnity` and `survivesUnityFold`). This is the row that folds a placed
        // `.mul(OscSlot.pregain)` away at unity, which identity demands rather than merely allows:
        // the built-ins carried no pregain until phase 3 step 6 placed it on their sources, so
        // KEEPING a unity multiply would be the change, not removing it. (A registered tree renders
        // optimized, where a bare `x.mul(k)` is the `Affine` arm below.) The right side is asked
        // first, because `x.mul(k)` is where a knob is written.
        is IgnitorDsl.Times -> when {
            right.gatedOffAtUnity(oscParams, cache) -> {
                val survivor = left.withMod()
                val survivorEnds = lastChildEndsInEnvelope

                if (survivor.survivesUnityFold()) {
                    builtEnvelope = survivorEnds
                    survivor
                } else {
                    survivor * right.withMod()
                }
            }

            left.gatedOffAtUnity(oscParams, cache) -> {
                // The gated side is a leaf, so building it after the survivor draws nothing and
                // the authored left-then-right order is unobservable.
                val survivor = right.withMod()
                val survivorEnds = lastChildEndsInEnvelope

                if (survivor.survivesUnityFold()) {
                    builtEnvelope = survivorEnds
                    survivor
                } else {
                    left.withMod() * survivor
                }
            }

            else -> left.withMod() * right.withMod()
        }

        // The same `mul` row, on the node the OPTIMIZER folds a bare `.mul(k)` into: every
        // registered tree renders optimized, so `Times` alone would leave the row unable to fire
        // on the shipping path. Only a BARE multiply qualifies (no pre-add, no add), and then the
        // whole node is the identity `safeOut(1.0 * (x + -0.0)) + -0.0`. The absent-addend test
        // has one home, on the node whose encoding it is.
        is IgnitorDsl.Affine -> {
            val bareUnity = IgnitorDsl.Affine.isAbsentAddend(pre) &&
                    IgnitorDsl.Affine.isAbsentAddend(add) &&
                    mul.gatedOffAtUnity(oscParams, cache)
            val survivor = inner.withMod()
            val survivorEnds = lastChildEndsInEnvelope

            if (bareUnity && survivor.survivesUnityFold()) {
                builtEnvelope = survivorEnds
                survivor
            } else {
                survivor.affine(pre.withMod(), mul.withMod(), add.withMod())
            }
        }

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
                // A detune scales the freq argument, not the amplitude: it hands on its inner's
                // `endsInEnvelope` (`passThrough`), in both branches.
                val forked = inner.passThrough()
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
                inner.passThrough()
            }
        }

        // ── Filters: pass mod through to inner ──

        is IgnitorDsl.Lowpass -> if (freq.gatedOffWhenUnset(oscParams, cache)) {
            // GATE ROW `a filter`: unset cutoff only. See `gatedOffWhenUnset`.
            inner.passThrough()
        } else {
            // C5: `passes` cascades the stage with the Butterworth q ladder (relative
            // factors — the modulated q scales every stage coherently). passes = 1 is the
            // untouched single-stage path, bit-identical. `analog` is handed to EVERY stage,
            // so its drive character compounds with the slope (documented, not a bug).
            // Build order IS rng draw order, and this arm's is: the INNER first (the house
            // convention, every other arm does it), then the envelope knobs, which are leaves and
            // draw nothing, then the humanization's four draws, then freq / q / analog exactly
            // where they were. A CASCADE draws ONCE: every stage shares the one tolerance and the
            // one drift lane, the way the strip's single `AudioFilter` with N stages does.
            // `q.noMod()` deliberately stays INSIDE the loop: the build cache counts consumers,
            // and hoisting it would change the memo's shape for the whole voice.
            val built = inner.withMod()
            val envDef = filterEnvDef(
                env, attackSec, decaySec, sustainLevel, releaseSec, attackCurve, decayCurve, releaseCurve, oscParams, cache,
            )
            val hum = analog.filterHumanization(humanize, oscParams, cache)
            val n = passes.passesKnob(oscParams, cache)

            if (n == 1) {
                built.lowpass(freq.noMod(), q.noMod(), envDef, analog.noMod(), hum)
            } else {
                val rel = butterworthQLadder(n, 1.0)
                var chain = built
                for (k in 0 until n) {
                    chain = chain.lowpass(freq.noMod(), q.noMod().scaledBy(rel[k]), envDef, analog.noMod(), hum)
                }
                chain
            }
        }

        is IgnitorDsl.Highpass -> if (freq.gatedOffWhenUnset(oscParams, cache)) {
            // GATE ROW `a filter`: unset cutoff only. See `gatedOffWhenUnset`.
            inner.passThrough()
        } else {
            // See Lowpass above: same ladder, same analog-compounding note, same build/draw
            // order and the same one shared lane for the whole cascade.
            val built = inner.withMod()
            val envDef = filterEnvDef(
                env, attackSec, decaySec, sustainLevel, releaseSec, attackCurve, decayCurve, releaseCurve, oscParams, cache,
            )
            val hum = analog.filterHumanization(humanize, oscParams, cache)
            val n = passes.passesKnob(oscParams, cache)

            if (n == 1) {
                built.highpass(freq.noMod(), q.noMod(), envDef, analog.noMod(), hum)
            } else {
                val rel = butterworthQLadder(n, 1.0)
                var chain = built
                for (k in 0 until n) {
                    chain = chain.highpass(freq.noMod(), q.noMod().scaledBy(rel[k]), envDef, analog.noMod(), hum)
                }
                chain
            }
        }
        // GATE ROW `onepole`: at or below 0.0, or unset. Unlike the four SVF filters this one HAS
        // a numeric off state and always had: until 2026-09-20 the test lived in
        // `IgnitorRegistry.createExciter`, OUTSIDE the tree, as the only gate in the engine that
        // was not a door's own. It is here now so the rule has one home, and the registry places
        // the `onepole` slot in the tree instead of reading the bag itself (around an authored
        // instrument at note-on, on a built-in's source since phase 3 step 6).
        is IgnitorDsl.OnePoleLowpass -> if (freq.gatedOff(oscParams, cache) { it <= 0.0 }) {
            inner.passThrough()
        } else {
            inner.withMod().onePoleLowpass(freq.noMod())
        }

        // GATE ROW `a filter`: unset cutoff only (see `gatedOffWhenUnset`).
        is IgnitorDsl.Bandpass -> if (freq.gatedOffWhenUnset(oscParams, cache)) {
            inner.passThrough()
        } else {
            // Same build/draw order as Lowpass above.
            val built = inner.withMod()
            val envDef = filterEnvDef(
                env, attackSec, decaySec, sustainLevel, releaseSec, attackCurve, decayCurve, releaseCurve, oscParams, cache,
            )
            val hum = analog.filterHumanization(humanize, oscParams, cache)
            built.bandpass(freq.noMod(), q.noMod(), envDef, analog.noMod(), hum)
        }

        is IgnitorDsl.Notch -> if (freq.gatedOffWhenUnset(oscParams, cache)) {
            inner.passThrough()
        } else {
            // Same build/draw order as Lowpass above.
            val built = inner.withMod()
            val envDef = filterEnvDef(
                env, attackSec, decaySec, sustainLevel, releaseSec, attackCurve, decayCurve, releaseCurve, oscParams, cache,
            )
            val hum = analog.filterHumanization(humanize, oscParams, cache)
            built.notch(freq.noMod(), q.noMod(), envDef, analog.noMod(), hum)
        }

        // Eq: withMod ONLY on inner; noMod on all section params — mirrors the filter arms
        // above (a withMod param subtree would change the freqHz the params see and break
        // tracking-HP parity). The wire-to-EqCore type mapping is `eqSectionSpec`, shared with
        // the orbit stage since Katalyst step 4; the build order per section stays freq, q, then
        // the type's own param, because build order is rng draw order.
        is IgnitorDsl.Eq -> EqIgnitor(
            upstream = inner.withMod(),
            sections = sections.map { s ->
                val spec = eqSectionSpec(s)

                EqIgnitor.Section(
                    type = spec.type,
                    freq = spec.freq.noMod(),
                    q = spec.q.noMod(),
                    db = spec.db?.noMod(),
                    gain = spec.gain?.noMod(),
                )
            },
        )

        // ── Envelope: pass mod through ──

        // GATE ROW `the envelope`: OFF only at an explicit `on` of exactly 0.0; UNSET IS ON. Inverted
        // from the plan's sketch on purpose (the spike corrected it, `docs/tasks/builtin-instruments.md`
        // section 5): the voice strip's VCA ran on EVERY voice with `AdsrDef.defaultSynth` when the
        // pattern set nothing, and since step 6 a built-in's classic ADSR is that VCA, so it has to be
        // built BY DEFAULT, and only an explicit `adsrOff` (filled into `on` by `classic()`) switches it
        // off. See `switchedOff`.
        //
        // OFF keeps the voice's LIFETIME: the envelope would have released over `releaseSec`, the
        // strip's `adsrOff` keeps that lifetime (its `renderGate` fades over the last frames of it),
        // and step 6's identity depends on the node doing the same. So the signal skips the stage
        // but the tail is still reported, read the build-time way (see `offEnvelopeTail`). The
        // teardown fade is the VOICE's: it reads `BuiltIgnitor.endsInEnvelope`, which this switched-off
        // node hands on from its inner (`passThrough`), so a `classic()` tree reports false here.
        is IgnitorDsl.Adsr -> if (on.switchedOff(oscParams, cache)) {
            val signal = inner.passThrough()
            spineTail = maxTail(spineTail, releaseSec.offEnvelopeTail(oscParams, cache))
            signal
        } else {
            // Order matters and is unchanged: build order IS rng draw order (IgniteContext.random),
            // so inner / attack / decay / sustain / release / declick stay in sequence. The switch
            // and the three curves are read leaf-only and build nothing, so they draw nothing.
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
            //
            // A NON-FINITE release is the same "no static answer", and it has to say so HERE.
            // `maxTail` is `if (a >= b) a else b`, and a NaN loses every comparison, so it wins
            // only as the SECOND argument: `maxTail(NaN, 2.0)` discards the NaN and returns 2.0,
            // while `maxTail(2.0, NaN)` returns the NaN. `VoiceFactory` then tests
            // `ignitorTailSec > resolvedAdsr.release`, which is false for a NaN, so the voice is
            // cut to the strip's release. The two shapes that reach it, both with the non-finite
            // release on the RIGHT of the accumulation:
            //   `s.adsr(release = 2.0) + s.adsr(release = NaN)`, because `buildRaw` accumulates
            //   the left operand first; and the commoner one, a CHAIN such as
            //   `s.adsr(release = 2.0).lowpass(...).adsr(release = NaN)`, because this arm builds
            //   its inner before it reaches the line below.
            // The samples of a NaN-released envelope are fine (`Double.toInt()` of a NaN is 0
            // frames); its TAIL is not.
            spineTail = maxTail(spineTail, release.controlRateValueOrNull(cache.freqHz)?.takeIf { it.isFinite() })
            builtEnvelope = true

            innerIgnitor.adsr(
                attack, decay, sustain, release,
                // Unset curve = "exp" on every stage of every AMPLITUDE envelope, on every door
                // (maintainer decision, 2026-08-24); the strip path's AdsrDef.Resolved already
                // defaults Exponential. The modulation envelopes (filter cutoff, pitch) fall back to
                // `MOD_ENV_CURVE` instead, which decision D3 sets.
                attackCurve.adsrCurveKnob(oscParams, cache, AdsrCurve.Default),
                decayCurve.adsrCurveKnob(oscParams, cache, AdsrCurve.Default),
                releaseCurve.adsrCurveKnob(oscParams, cache, AdsrCurve.Default),
                declickSeconds = declickSeconds.noMod(),
            )
        }

        // ── Effects: pass mod through to inner ──

        // GATE ROW `distort`: at or below 0.0, or unset. Neither authoring door builds this node
        // (both spell `distort` as `Shape(Drive(...))`, the Kotlin one at `IgnitorDsl.kt` and the
        // script one at `KlangScriptOscExtensions`); `classic()` does (its distort stage, phase 3
        // step 5, the one node that switches drive AND shape off as a unit), and `WarmupVocabulary`
        // at 0.3. Gating it aligns that node with the `Drive` row below and with the strip, which
        // adds no distort stage for an amount at or below 0.
        //
        // Since phase 3 step 4 (decision D2, option A) the node renders the VOICE STRIP's law through
        // `DistortionCore` (`fusedDistort`): the drive inside the oversampler, the DC blocker, no soft
        // cap; `ClassicStripParitySpec` proves it bit-identical to the strip. The gate is its ONLY
        // bypass: a modulated amount at or below 0 is not a leaf, is never gated, and runs at unity
        // drive with its state contiguous (ledger W5's hazard, see `fusedDistort`).
        //
        // Gating was a BEHAVIOUR CHANGE on that node when it landed (step 2), and the magnitude was
        // shape-dependent: the node was then `drive(amount).shape(shape)` and only the DRIVE half
        // bypassed at 0, so the tree's chosen shaper stayed on the signal at unity gain (modelled on a
        // 220 Hz sine: "soft" -1.77 dB, "tube" -6.24 dB, "zerosquare" up to +16.83 dB, "rectify" an
        // octave up). Now it does nothing, as on the strip.
        //
        // The shape and the oversampling factor are knobs read once, here (see `distortionShapeKnob`).
        is IgnitorDsl.Distort -> if (amount.gatedOff(oscParams, cache) { it <= 0.0 }) {
            inner.passThrough()
        } else {
            inner.withMod().fusedDistort(
                amount.noMod(),
                shape.distortionShapeKnob(oscParams, cache),
                oversample.oversampleStagesKnob(oscParams, cache),
            )
        }

        // GATE ROW `drive`: at or below 0.0, or unset. THE row the authoring doors reach, because
        // both build `Shape(Drive(...))`. Two different things at once:
        //
        //  - at or below 0 it is a true FOLD: `DriveIgnitor` already copies its input through
        //    unchanged there, bit for bit, so the only thing removed is a buffer pass;
        //  - at a NON-FINITE amount it CLOSES A HOLE. `amt <= 0.0` is false for a NaN, so the gain
        //    became `10^(NaN * 1.2)` and every sample of the voice came out NaN, with nothing
        //    between it and the orbit mix. That is the defect class of step 1, and step 3 would
        //    have walked straight into it: a `classic()` distort slot defaulting to `SLOT_UNSET`,
        //    wired to this node, would have made every voice of that instrument all-NaN.
        //
        // `Shape` is NOT gated and cannot be: it has no amount knob, only a transfer function, so
        // there is nothing to read an off value from. That is why `classic()`'s distort stage is the
        // fused `Distort` node above, not this pair; D2 also gave that node the strip's law.
        is IgnitorDsl.Drive -> if (amount.gatedOff(oscParams, cache) { it <= 0.0 }) {
            inner.passThrough()
        } else {
            inner.withMod().drive(amount.noMod())
        }

        // NOT gated (no amount knob, see the `drive` row). Its shape and oversampling factor are knobs
        // read once, here, at voice build (phase 3 step 3b; the factor is decision D7's stopgap).
        is IgnitorDsl.Shape -> inner.withMod().shape(
            shape.distortionShapeKnob(oscParams, cache),
            oversample.oversampleStagesKnob(oscParams, cache),
        )

        // GATE ROW `crush`: BELOW 1.0, or unset, and NOT 0. `CrushIgnitor` itself bypasses below two
        // levels (`CrushCore.halfLevels`, amount below 1), and `Ignitor.crush(Double)` returns the
        // inner below 1.0, so the whole range (0, 1) is already an exact bypass at render time
        // and gating it is the fold of a bypass, not a change.
        is IgnitorDsl.Crush -> if (amount.gatedOff(oscParams, cache) { it < 1.0 }) {
            inner.passThrough()
        } else {
            inner.withMod().crush(amount.noMod())
        }

        // GATE ROW `coarse`: at or below 1.0, or unset. Both paths already agree on that value
        // (`Ignitor.coarse(Double)` returns the inner, `FilterPipelineBuilder` adds no stage).
        // At or below 0 and at a non-finite amount the render already takes a bit-exact bypass, so
        // there the gate folds a bypass. In (0, 1] the engaged loop takes every sample (ledger W3)
        // but latches it through `nanGuard()`, so a NON-FINITE UPSTREAM SAMPLE used to come out as
        // 0.0 and now passes through: a change, on a sample a gated-off stage's upstream has no
        // business producing, and one every later clamping stage still guards.
        is IgnitorDsl.Coarse -> if (amount.gatedOff(oscParams, cache) { it <= 1.0 }) {
            inner.passThrough()
        } else {
            inner.withMod().coarse(amount.noMod())
        }

        // NOT gated: no per-voice phaser is reachable from a built-in tail today, the stage
        // retires with `PipelineDsl`, and the wet/dry law makes its off value a second question
        // (`floor`) that no caller needs answered yet.
        // Named, in the historical order: each `noMod()` BUILDS, and build order is rng draw order.
        is IgnitorDsl.Phaser -> inner.withMod().phaser(
            rate = rate.noMod(), wet = wet.noMod(), center = center.noMod(), sweep = sweep.noMod(), floor = floor.noMod(),
        )

        // GATE ROW `tremolo`: DEPTH at or below 0.0, or unset. The rate is not a gating knob: a
        // tremolo at rate 0 is a static gain, not an absence. Nor are shape, skew and phase.
        //
        // Build order is rng draw order: inner, rate, depth as before, then the skew (read per
        // block, so built); the shape and the start phase are read once, leaf-only, and build
        // nothing. A BUILT tremolo reports that it gates its own output (see `BuiltIgnitor`).
        is IgnitorDsl.Tremolo -> if (depth.gatedOff(oscParams, cache) { it <= 0.0 }) {
            inner.passThrough()
        } else {
            spineGatesOutput = true

            inner.withMod().tremolo(
                rate = rate.noMod(),
                depth = depth.noMod(),
                skew = skew.noMod(),
                shape = shape.lfoShapeKnob(oscParams, cache),
                startPhase = phase.startPhaseKnob(oscParams, cache),
            )
        }
        is IgnitorDsl.Shimmer -> inner.withMod().shimmer(wet.noMod(), feedback.noMod(), tone.noMod(), pitches, floor.noMod())
    }

    return BuiltIgnitor(ignitor, spineTail, spineGatesOutput, builtEnvelope)
}
