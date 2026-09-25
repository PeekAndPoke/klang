/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.constants.MOD_ENV_CURVE
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import kotlin.random.Random

/**
 * The envelope curves as INDEX KNOBS (phase 3 step 3c): the chain `adsr`, the four filters' cutoff
 * envelope and the pitch envelope each read their three curve knobs ONCE at build, leaf-only,
 * through `AdsrCurves.curveAt`, and fall back to THEIR OWN default: exponential on the chain,
 * `MOD_ENV_CURVE` (linear) on the modulation envelopes.
 *
 * The chain rows compare against an INDEPENDENT oracle, the hand-authoring runtime API
 * `Ignitor.adsr(..., attackCurve = AdsrCurve.X)`, which takes the enum directly and never sees a
 * knob; so a knob read that resolves to the wrong curve is red even though both sides share the
 * curve math.
 */
class EnvelopeCurveKnobSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 24
    val frames = blockFrames * blocks
    val gateEnd = frames / 2
    val freqHz = 220.0

    fun render(ignitor: Ignitor): List<Long> {
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = frames,
            gateEndFrame = gateEnd,
            releaseFrames = frames - gateEnd,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(3),
        ).apply {
            updateOffsetAndLength(0, blockFrames)
            voiceElapsedFrames = 0
        }
        val out = ArrayList<Long>(frames)
        val buffer = AudioBuffer(blockFrames)

        for (b in 0 until blocks) {
            ignitor.generate(buffer, freqHz, ctx)

            for (i in 0 until blockFrames) {
                out.add(buffer[i].toRawBits())
            }

            ctx.voiceElapsedFrames += blockFrames
        }

        return out
    }

    fun renderDsl(dsl: IgnitorDsl, params: Map<String, Double>? = null): List<Long> =
        render(dsl.buildExciter(oscParams = params, random = Random(3), freqHz = freqHz).ignitor)

    // ── The chain `adsr` ──────────────────────────────────────────────────────────────────────

    // Short stages inside the render: attack 10 ms, decay 20 ms to 0.4, release 20 ms from the gate.
    fun chain(attack: IgnitorDsl? = null, decay: IgnitorDsl? = null, release: IgnitorDsl? = null): IgnitorDsl.Adsr {
        val base = IgnitorDsl.Adsr(
            inner = IgnitorDsl.Constant(1.0),
            attackSec = IgnitorDsl.Constant(0.01),
            decaySec = IgnitorDsl.Constant(0.02),
            sustainLevel = IgnitorDsl.Constant(0.4),
            releaseSec = IgnitorDsl.Constant(0.02),
        )

        return base.copy(
            attackCurve = attack ?: base.attackCurve,
            decayCurve = decay ?: base.decayCurve,
            releaseCurve = release ?: base.releaseCurve,
        )
    }

    fun oracle(a: AdsrCurve, d: AdsrCurve, r: AdsrCurve): List<Long> =
        render(ConstantIgnitor(1.0).adsr(0.01, 0.02, 0.4, 0.02, attackCurve = a, decayCurve = d, releaseCurve = r))

    val exp = AdsrCurve.Exponential
    val chainDefault = oracle(exp, exp, exp)

    "the chain: every curve on every stage renders what the runtime renders for that enum" {
        for (curve in AdsrCurve.entries) {
            val knob = AdsrCurves.knob(curve)

            withClue("attack $curve") { renderDsl(chain(attack = knob)) shouldBe oracle(curve, exp, exp) }
            withClue("decay $curve") { renderDsl(chain(decay = knob)) shouldBe oracle(exp, curve, exp) }
            withClue("release $curve") { renderDsl(chain(release = knob)) shouldBe oracle(exp, exp, curve) }

            if (curve != exp) {
                // Anti-vacuous, per stage: the curve really moves that stage in this fixture.
                withClue("$curve is audible on every stage") {
                    oracle(curve, exp, exp) shouldNotBe chainDefault
                    oracle(exp, curve, exp) shouldNotBe chainDefault
                    oracle(exp, exp, curve) shouldNotBe chainDefault
                }
            }
        }

        withClue("the node's default knobs are exponential") { renderDsl(chain()) shouldBe chainDefault }
    }

    "the chain: an unreadable curve is EXPONENTIAL, the chain's own default, never the modulation one" {
        // A non-finite, negative and past-the-end index each on its own stage, and a non-leaf
        // expression (it would evaluate to 1.0, square, if it were read) on all three.
        val bad = listOf(
            "unset" to IgnitorDsl.Constant(SLOT_UNSET),
            "negative" to IgnitorDsl.Constant(-1.0),
            "past the end" to IgnitorDsl.Constant(AdsrCurves.names.size.toDouble()),
            "non-leaf" to IgnitorDsl.Plus(IgnitorDsl.Constant(0.5), IgnitorDsl.Constant(0.5)),
        )

        for ((name, knob) in bad) {
            withClue("$name on the attack") { renderDsl(chain(attack = knob)) shouldBe chainDefault }
            withClue("$name on the decay") { renderDsl(chain(decay = knob)) shouldBe chainDefault }
            withClue("$name on the release") { renderDsl(chain(release = knob)) shouldBe chainDefault }
        }

        // The fallback is OBSERVABLE: the modulation default renders differently here.
        oracle(MOD_ENV_CURVE, MOD_ENV_CURVE, MOD_ENV_CURVE) shouldNotBe chainDefault
    }

    "the chain: a curve SLOT reads the pattern's value from the voice's bag" {
        val slotted = chain(decay = IgnitorDsl.Param("adsr.decayCurve", AdsrCurves.indexOf(exp)))

        renderDsl(slotted, emptyMap()) shouldBe chainDefault
        renderDsl(slotted, mapOf("adsr.decayCurve" to AdsrCurves.indexOf(AdsrCurve.Square))) shouldBe
                oracle(exp, AdsrCurve.Square, exp)
    }

    // ── The modulation envelopes: the four filters and the pitch envelope ────────────────────

    val saw = IgnitorDsl.Sawtooth(freq = IgnitorDsl.Freq)

    // Each envelope with ONE curve knob set (on its decay, the stage that runs longest here), so a
    // curve read on the wrong envelope or the wrong stage cannot hide behind the others.
    val cutoff = IgnitorDsl.Constant(400.0)
    val sweep = IgnitorDsl.Constant(24.0)
    val decay = IgnitorDsl.Constant(0.03)
    val sustain = IgnitorDsl.Constant(0.2)

    // `null` = the node's own default knob, which IS `AdsrCurves.knob(MOD_ENV_CURVE)`; the first
    // assertion of the fallback row pins that equality by render.
    fun IgnitorDsl?.orNodeDefault(): IgnitorDsl = this ?: IgnitorDsl.PitchEnvelope(inner = saw).decayCurve

    val modulationEnvelopes: List<Pair<String, (IgnitorDsl?) -> IgnitorDsl>> = listOf(
        "lowpass" to { k ->
            IgnitorDsl.Lowpass(saw, cutoff, env = sweep, decaySec = decay, sustainLevel = sustain, decayCurve = k.orNodeDefault())
        },
        "highpass" to { k ->
            IgnitorDsl.Highpass(saw, cutoff, env = sweep, decaySec = decay, sustainLevel = sustain, decayCurve = k.orNodeDefault())
        },
        "bandpass" to { k ->
            IgnitorDsl.Bandpass(saw, cutoff, env = sweep, decaySec = decay, sustainLevel = sustain, decayCurve = k.orNodeDefault())
        },
        "notch" to { k ->
            IgnitorDsl.Notch(saw, cutoff, env = sweep, decaySec = decay, sustainLevel = sustain, decayCurve = k.orNodeDefault())
        },
        "pitch" to { k ->
            IgnitorDsl.PitchEnvelope(saw, IgnitorDsl.Constant(12.0), decaySec = decay, sustainLevel = sustain, decayCurve = k.orNodeDefault())
        },
    )

    "the modulation envelopes: an unreadable curve is MOD_ENV_CURVE, their own default, never exponential" {
        for ((name, envelope) in modulationEnvelopes) {
            val atDefault = renderDsl(envelope(null))

            withClue("$name: the default knob is MOD_ENV_CURVE") {
                renderDsl(envelope(AdsrCurves.knob(MOD_ENV_CURVE))) shouldBe atDefault
            }

            for (knob in listOf(IgnitorDsl.Constant(SLOT_UNSET), IgnitorDsl.Constant(-1.0), IgnitorDsl.Constant(99.0))) {
                withClue("$name: $knob falls back to MOD_ENV_CURVE") { renderDsl(envelope(knob)) shouldBe atDefault }
            }

            withClue("$name: a non-leaf falls back to MOD_ENV_CURVE") {
                renderDsl(envelope(IgnitorDsl.Plus(IgnitorDsl.Constant(2.5), IgnitorDsl.Constant(2.5)))) shouldBe atDefault
            }

            // The fallback is OBSERVABLE (exponential, the chain's default, sounds different) and a
            // written curve is READ (square is not the default either).
            withClue("$name: exponential and square are audible") {
                renderDsl(envelope(AdsrCurves.knob(AdsrCurve.Exponential))) shouldNotBe atDefault
                renderDsl(envelope(AdsrCurves.knob(AdsrCurve.Square))) shouldNotBe atDefault
            }
        }
    }

    "the modulation envelopes: a curve SLOT reads the pattern's value from the voice's bag" {
        // Square, not Cube: exponential and square are the two curves the fallback row proves AUDIBLE
        // in this fixture, so a slot that was never read cannot pass by rendering the same thing.
        for ((name, envelope) in modulationEnvelopes) {
            val slotted = envelope(IgnitorDsl.Param("curve", AdsrCurves.indexOf(MOD_ENV_CURVE)))

            withClue(name) {
                renderDsl(slotted, emptyMap()) shouldBe renderDsl(envelope(null))
                renderDsl(slotted, mapOf("curve" to AdsrCurves.indexOf(AdsrCurve.Square))) shouldBe
                        renderDsl(envelope(AdsrCurves.knob(AdsrCurve.Square)))
            }
        }
    }

    "every modulation node's CONSTRUCTOR default curve is MOD_ENV_CURVE, on all three stages" {
        // The rows above hand every envelope an explicit knob and the Kotlin doors fill their own, so
        // only this row reaches the data classes' defaults: the ones `classic()` (step 5) will build
        // on. Pinned twice: the knob itself, and the render against an explicit MOD_ENV_CURVE knob.
        val mod = AdsrCurves.knob(MOD_ENV_CURVE)
        val bare: List<Pair<String, IgnitorDsl>> = listOf(
            "lowpass" to IgnitorDsl.Lowpass(saw, cutoff, env = sweep, decaySec = decay, sustainLevel = sustain),
            "highpass" to IgnitorDsl.Highpass(saw, cutoff, env = sweep, decaySec = decay, sustainLevel = sustain),
            "bandpass" to IgnitorDsl.Bandpass(saw, cutoff, env = sweep, decaySec = decay, sustainLevel = sustain),
            "notch" to IgnitorDsl.Notch(saw, cutoff, env = sweep, decaySec = decay, sustainLevel = sustain),
            "pitch" to IgnitorDsl.PitchEnvelope(saw, IgnitorDsl.Constant(12.0), decaySec = decay, sustainLevel = sustain),
        )

        for ((name, node) in bare) {
            val (curves, explicit) = when (node) {
                is IgnitorDsl.Lowpass -> listOf(node.attackCurve, node.decayCurve, node.releaseCurve) to
                        node.copy(attackCurve = mod, decayCurve = mod, releaseCurve = mod)
                is IgnitorDsl.Highpass -> listOf(node.attackCurve, node.decayCurve, node.releaseCurve) to
                        node.copy(attackCurve = mod, decayCurve = mod, releaseCurve = mod)
                is IgnitorDsl.Bandpass -> listOf(node.attackCurve, node.decayCurve, node.releaseCurve) to
                        node.copy(attackCurve = mod, decayCurve = mod, releaseCurve = mod)
                is IgnitorDsl.Notch -> listOf(node.attackCurve, node.decayCurve, node.releaseCurve) to
                        node.copy(attackCurve = mod, decayCurve = mod, releaseCurve = mod)
                is IgnitorDsl.PitchEnvelope -> listOf(node.attackCurve, node.decayCurve, node.releaseCurve) to
                        node.copy(attackCurve = mod, decayCurve = mod, releaseCurve = mod)
                else -> error(name)
            }

            withClue("$name: the three default knobs") { curves shouldBe listOf(mod, mod, mod) }
            withClue("$name: renders as MOD_ENV_CURVE") { renderDsl(node) shouldBe renderDsl(explicit) }
        }
    }
})
