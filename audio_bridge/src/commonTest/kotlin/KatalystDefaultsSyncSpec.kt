/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RATIO
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.COMPRESSOR_THRESHOLD_DB
import io.peekandpoke.klang.audio_bridge.constants.DELAY_CAP
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.DELAY_TIME_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DELAY_WET
import io.peekandpoke.klang.audio_bridge.constants.DUCK_ATTACK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.DUCK_DEPTH
import io.peekandpoke.klang.audio_bridge.constants.PHASER_CENTER_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.PHASER_RATE_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_SWEEP_HZ
import io.peekandpoke.klang.audio_bridge.constants.PHASER_WET
import io.peekandpoke.klang.audio_bridge.constants.REVERB_SIZE
import io.peekandpoke.klang.audio_bridge.constants.REVERB_WET
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET

/**
 * Keeps [KatalystDsl.classic] in sync with what a slot default MEANS: the value the knob has when
 * nobody writes it.
 *
 * Every slot of the classic chain falls into exactly one of FIVE families, and each gets a row
 * (four until 2026-09-19, when the group fader arrived and brought its own):
 *
 *  1. **untouched zero** (0.0): a knob whose engine-untouched value is zero, so seeding it with
 *     the touched constant would switch that effect on for every song that never asked for it:
 *     `delay.wet`, `reverb.wet`, `phaser.wet`, `duck.depth`.
 *  2. **unset** ([SLOT_UNSET]): where "off" is an absence rather than a number, so the wire's
 *     non-finite marker carries it: all five compressor slots (`Voice.Compressor.fromParams`
 *     gates on "any of the five set", so even one finite constant is a compressor already on),
 *     `duck.orbit`, `reverb.lowpass`, and the four name-and-amount slots of the two stages gated
 *     on a NAME, `body.material`, `body.wet`, `vowel.vowel` and `vowel.wet`.
 *  3. **off-value** (0.0): a knob that is not the amount but IS where the engine's gate sits, so
 *     zeroing the wet alone would not be off: `delay.time` and `reverb.size` (the gates are
 *     `KatalystDelayEffect.MIN_ACTIVE_DELAY_SECONDS` and `KatalystReverbEffect.MIN_ACTIVE_SIZE`),
 *     plus `delay.feedback`, which the untouched voice also carries at zero.
 *  4. **constant**: everything else, inert while its gate is off and right the moment it opens.
 *  5. **unity** (1.0): the identity element of a stage, which is none of the four above: the
 *     group fader `gain.gain`, whose stage is bit-transparent at exactly 1.0.
 *
 * Families 1 and 3 together are the untouched voice, and that equality is pinned against the real
 * `VoiceFactory` by `KatalystClassicMatchesUntouchedVoiceSpec` in audio_be, which is the test that
 * can actually see the engine. This file pins the DECLARATION: the slot vocabulary, which family
 * each slot is in, and that no family-4 slot has drifted off its shared constant. Neither file
 * alone proves byte identity; the frozen-song render does that once the cylinder reads the chain in
 * step 2. What they buy is that a wrong value here is loud instead of subtle.
 */
class KatalystDefaultsSyncSpec : StringSpec({

    /** Every knob of every stage, flattened to `slot name to default`, in declaration order. */
    fun slots(stage: KatalystStageDsl): List<Pair<String, Double>> {
        val knobs: List<IgnitorDsl?> = when (stage) {
            is KatalystStageDsl.Body -> listOf(stage.material, stage.wet, stage.floor)
            is KatalystStageDsl.Vowel -> listOf(stage.vowel, stage.wet, stage.floor)
            is KatalystStageDsl.Delay -> listOf(stage.wet, stage.time, stage.feedback, stage.cap)
            is KatalystStageDsl.Reverb -> listOf(stage.wet, stage.size, stage.lowpass)
            is KatalystStageDsl.Phaser -> listOf(stage.rate, stage.wet, stage.center, stage.sweep, stage.floor)
            is KatalystStageDsl.Compressor ->
                listOf(stage.threshold, stage.ratio, stage.knee, stage.attack, stage.release)

            is KatalystStageDsl.Duck -> listOf(stage.orbit, stage.depth, stage.attack)
            is KatalystStageDsl.Eq -> emptyList()
            is KatalystStageDsl.Gain -> listOf(stage.gain)
        }

        return knobs.filterIsInstance<IgnitorDsl.Param>().map { it.name to it.default }
    }

    val classicSlots: Map<String, Double> = KatalystDsl.classic.stages.flatMap { slots(it) }.toMap()

    "every knob of the classic chain is a slot named <stage>.<knob>" {
        KatalystDsl.classic.stages.flatMap { slots(it) }.map { it.first } shouldBe listOf(
            "body.material", "body.wet", "body.floor",
            "vowel.vowel", "vowel.wet", "vowel.floor",
            "delay.wet", "delay.time", "delay.feedback", "delay.cap",
            "reverb.wet", "reverb.size", "reverb.lowpass",
            "phaser.rate", "phaser.wet", "phaser.center", "phaser.sweep", "phaser.floor",
            "compressor.threshold", "compressor.ratio", "compressor.knee", "compressor.attack",
            "compressor.release",
            "gain.gain",
            "duck.orbit", "duck.depth", "duck.attack",
        )
    }

    "family 1, the untouched zeros: every knob here is 0.0, never its touched constant" {
        // A slot's default is its value when nobody writes it, so seeding an amount with the
        // touched constant would put REVERB_WET of room on every orbit of every song that never
        // wrote `reverb(...)`. The criterion for THIS family is exactly that: a knob whose
        // engine-UNTOUCHED value is zero belongs here, because seeding it with the touched constant
        // would switch that effect on everywhere. It is not about where the engine's gate sits (the
        // delay gates on `time`, the reverb on `size`, the duck on `orbit`, and `duck.orbit` is in
        // family 2 while `phaser.wet` is here). Family 3 holds the two gate knobs whose untouched
        // value happens to be zero, `delay.time` and `reverb.size`, for this same reason.
        listOf("delay.wet", "reverb.wet", "phaser.wet", "duck.depth")
            .forEach { name ->
                withClue(name) { classicSlots.getValue(name) shouldBe 0.0 }
            }

        // ...and none of them is the touched default, which is the mistake this row exists for.
        // `phaser.wet` and `duck.depth` are absent here on purpose: their touched constants are
        // 0.0 as well (PHASER_WET, DUCK_DEPTH), so for those two the gate and the constant say the
        // same thing and there is nothing to tell apart.
        listOf(
            "delay.wet" to DELAY_WET,
            "reverb.wet" to REVERB_WET,
        ).forEach { (name, touched) ->
            withClue(name) { classicSlots.getValue(name) shouldNotBe touched }
        }

        // `body.wet` and `vowel.wet` are NOT here, and that is the fix of round 1: a 0.0 amount is
        // a SET amount, so the engine never substituted its constant and a material-only
        // `body("wood")` played dry on a declared chain. They are in family 2 below.
        listOf("body.wet", "vowel.wet").forEach { name ->
            withClue(name) { classicSlots.getValue(name).isFinite() shouldBe false }
        }
    }

    "family 2, the unset slots: off is an absence, carried by the non-finite marker" {
        // ALL FIVE compressor slots, not just the threshold. `Voice.Compressor.fromParams` gates on
        // "any of the five set", so a single finite constant among them is a compressor that is
        // already on for every song that never wrote `compressor(...)`.
        //
        // `body.material` and `vowel.vowel` joined them on 2026-09-18 (Katalyst step 5a-2): the
        // index of a name in a closed catalogue, where 0 IS a legitimate entry (`none`), so the off
        // state has to be the absence marker and not a zero. Index 0 resolving to off as well is
        // belt to that braces, not the mechanism.
        //
        // `body.wet` and `vowel.wet` joined them in round 1 of that step's review, for the
        // compressor's reason and not the index's: `KatalystSlots.bodyDef` substitutes BODY_WET for
        // an UNSET mix, and a 0.0 default is set, so a material-only `body("wood")` ran the bank
        // fully dry on a declared chain. Safe here and nowhere else in this family, because these
        // two stages are gated on their NAME: no material, no stage, whatever the amount says.
        listOf(
            "compressor.threshold", "compressor.ratio", "compressor.knee", "compressor.attack",
            "compressor.release", "duck.orbit", "reverb.lowpass", "body.material", "vowel.vowel",
            "body.wet", "vowel.wet",
        ).forEach { name ->
            withClue(name) { classicSlots.getValue(name).isFinite() shouldBe false }
        }

        // ...and none of the seven numeric ones is its touched constant, which is the mistake this
        // row exists for: the point of "unset" is that the ENGINE substitutes, so a chain that
        // wrote the constant out would look identical and stop being a declaration of "untouched".
        listOf(
            "compressor.threshold" to COMPRESSOR_THRESHOLD_DB,
            "compressor.ratio" to COMPRESSOR_RATIO,
            "compressor.knee" to COMPRESSOR_KNEE_DB,
            "compressor.attack" to COMPRESSOR_ATTACK_SECONDS,
            "compressor.release" to COMPRESSOR_RELEASE_SECONDS,
            "body.wet" to BODY_WET,
            "vowel.wet" to VOWEL_WET,
        ).forEach { (name, touched) ->
            // The raw `==` and not `shouldNotBe`, deliberately, and this is the one exception
            // code-style rule 23 names: every slot here is NaN, so `shouldNotBe` would pass for the
            // wrong reason (NaN is not equal to itself) and say nothing about whether the slot is
            // still unset. The raw comparison is the same verdict for the right reason.
            val isTouchedDefault = classicSlots.getValue(name) == touched

            withClue(name) { isTouchedDefault shouldBe false }
        }
    }

    "family 3, the off-value slots: where the engine's gate sits, not on the wet" {
        // This is the family that was wrong in review round 1. The delay engages on
        // `time >= MIN_ACTIVE_DELAY_SECONDS` and the reverb on `size >= MIN_ACTIVE_SIZE`, so a
        // chain with `wet = 0` but `time = DELAY_TIME_SECONDS` declares a RUNNING delay line that
        // happens to be fed nothing, which is not what an untouched orbit is.
        listOf("delay.time", "delay.feedback", "reverb.size").forEach { name ->
            withClue(name) { classicSlots.getValue(name) shouldBe 0.0 }
        }

        listOf(
            "delay.time" to DELAY_TIME_SECONDS,
            "delay.feedback" to DELAY_FEEDBACK,
            "reverb.size" to REVERB_SIZE,
        ).forEach { (name, touched) ->
            withClue(name) { classicSlots.getValue(name) shouldNotBe touched }
        }
    }

    "family 4, the constant slots: inert while the gate is off, right when it opens" {
        // These must not drift from the constants the sprudel doors, the master stages and the
        // engine fallbacks all read. `delay.cap` belongs here and not in family 3: the untouched
        // voice carries DELAY_CAP too, because a cap is a soft-clip shape, not an amount.
        listOf(
            "delay.cap" to DELAY_CAP,
            "body.floor" to BODY_FLOOR,
            "vowel.floor" to VOWEL_FLOOR,
            "phaser.rate" to PHASER_RATE_HZ,
            "phaser.center" to PHASER_CENTER_HZ,
            "phaser.sweep" to PHASER_SWEEP_HZ,
            "phaser.floor" to PHASER_FLOOR,
            "duck.attack" to DUCK_ATTACK_SECONDS,
        ).forEach { (name, constant) ->
            withClue(name) { classicSlots.getValue(name) shouldBe constant }
        }
    }

    "family 5, the unity slot: the identity element of a stage, not an off state" {
        // The group fader, added 2026-09-19 (the signal-flow plan, section 6, spot C). It is its
        // own family because none of the other four fits and saying so is the point: it is not an
        // off value (a fader at 0 is silence, not transparency), not an absence (unity IS a
        // number the stage uses) and not a shared constant from `constants/` (an identity element
        // is not a taste decision anybody retunes, which is why `MasterStageDsl.Gain` writes it
        // out too). Exactly 1.0, because the stage's bit-transparency is a `== 1.0` branch in
        // `KatalystGainEffect.process`: a 0.9999999999 here would multiply every orbit of every
        // song by something.
        classicSlots.getValue("gain.gain") shouldBe 1.0
    }

    "the five families together cover every slot, with no slot in two of them" {
        // The rows above are lists, and a slot quietly added to the chain would be in none of them.
        val gateOff = listOf("delay.wet", "reverb.wet", "phaser.wet", "duck.depth")
        val unset = listOf(
            "compressor.threshold", "compressor.ratio", "compressor.knee", "compressor.attack",
            "compressor.release", "duck.orbit", "reverb.lowpass", "body.material", "vowel.vowel",
            "body.wet", "vowel.wet",
        )
        val offValue = listOf("delay.time", "delay.feedback", "reverb.size")
        val constant = listOf(
            "delay.cap", "body.floor", "vowel.floor", "phaser.rate", "phaser.center", "phaser.sweep",
            "phaser.floor", "duck.attack",
        )
        val unity = listOf("gain.gain")
        val all = gateOff + unset + offValue + constant + unity

        all.size shouldBe all.toSet().size
        all.toSet() shouldBe classicSlots.keys
    }

    "a BARE stage carries the TOUCHED constant: reaching for an effect is not leaving it alone" {
        // The other half of the family rule. `Katalyst(k => k.reverb())` means "I want a reverb",
        // so the bare stage seeds the shared defaults; only `classic` seeds the untouched state. If
        // these two ever collapse into one, one of the two meanings is gone.
        KatalystStageDsl.Reverb().wet shouldBe IgnitorDsl.Constant(REVERB_WET)
        KatalystStageDsl.Reverb().size shouldBe IgnitorDsl.Constant(REVERB_SIZE)
        KatalystStageDsl.Delay().wet shouldBe IgnitorDsl.Constant(DELAY_WET)
        KatalystStageDsl.Delay().time shouldBe IgnitorDsl.Constant(DELAY_TIME_SECONDS)
        KatalystStageDsl.Delay().feedback shouldBe IgnitorDsl.Constant(DELAY_FEEDBACK)
        KatalystStageDsl.Delay().cap shouldBe IgnitorDsl.Constant(DELAY_CAP)
        KatalystStageDsl.Compressor().threshold shouldBe IgnitorDsl.Constant(COMPRESSOR_THRESHOLD_DB)
        KatalystStageDsl.Body().wet shouldBe IgnitorDsl.Constant(BODY_WET)
        KatalystStageDsl.Body().floor shouldBe IgnitorDsl.Constant(BODY_FLOOR)
        KatalystStageDsl.Vowel().wet shouldBe IgnitorDsl.Constant(VOWEL_WET)
        KatalystStageDsl.Vowel().floor shouldBe IgnitorDsl.Constant(VOWEL_FLOOR)
        KatalystStageDsl.Phaser().center shouldBe IgnitorDsl.Constant(PHASER_CENTER_HZ)
        KatalystStageDsl.Phaser().sweep shouldBe IgnitorDsl.Constant(PHASER_SWEEP_HZ)
        KatalystStageDsl.Phaser().floor shouldBe IgnitorDsl.Constant(PHASER_FLOOR)
    }

    "the phaser, the duck, the body and the vowel stay OFF bare: one knob has to name something" {
        // "Touched" is not "audible" for these four, and that is not an oversight: it matches the
        // sprudel doors today. The phaser is gated on its depth (`Phaser.MIN_ACTIVE_DEPTH`), so
        // `phaser()` with no wet is a chain entry that does nothing; the duck needs a source orbit
        // before it can duck anything; and the body and the vowel need a catalogue INDEX.
        val bareMaterial = (KatalystStageDsl.Body().material as IgnitorDsl.Constant).value
        val bareVowel = (KatalystStageDsl.Vowel().vowel as IgnitorDsl.Constant).value

        withClue("a bare body names no material") { bareMaterial.isFinite() shouldBe false }
        withClue("a bare vowel names none either") { bareVowel.isFinite() shouldBe false }
        // ...and not a zero, for the same reason `duck.orbit` is not: 0 is `none`, a real entry in
        // the catalogue, so "never set" has to be the absence marker to stay distinguishable.
        IgnitorDsl.Constant(bareMaterial) shouldBe IgnitorDsl.Constant(SLOT_UNSET)
        IgnitorDsl.Constant(bareVowel) shouldBe IgnitorDsl.Constant(SLOT_UNSET)

        KatalystStageDsl.Phaser().wet shouldBe IgnitorDsl.Constant(PHASER_WET)
        (KatalystStageDsl.Phaser().wet as IgnitorDsl.Constant).value shouldBe 0.0
        KatalystStageDsl.Phaser().rate shouldBe IgnitorDsl.Constant(PHASER_RATE_HZ)
        (KatalystStageDsl.Phaser().rate as IgnitorDsl.Constant).value shouldBe 0.0

        KatalystStageDsl.Duck().depth shouldBe IgnitorDsl.Constant(DUCK_DEPTH)
        (KatalystStageDsl.Duck().depth as IgnitorDsl.Constant).value shouldBe 0.0
        KatalystStageDsl.Duck().attack shouldBe IgnitorDsl.Constant(DUCK_ATTACK_SECONDS)

        // `orbit` is a member of the UNSET family, not a plain zero: 0 is a legitimate orbit, so
        // the off state has to be the absence marker. A `-1` is a finite orbit request too.
        // And it is asserted the way the rule says to: `orbit shouldBe SLOT_UNSET` FAILS here,
        // because kotest compares primitive Doubles with IEEE `==` and NaN is not equal to itself.
        // That is the "test isFinite(), never compare" rule biting in its own spec.
        val orbit = (KatalystStageDsl.Duck().orbit as IgnitorDsl.Constant).value

        orbit.isFinite() shouldBe false
        IgnitorDsl.Constant(orbit) shouldBe IgnitorDsl.Constant(SLOT_UNSET)
    }

    "the classic body and vowel carry their NAME as an unset index slot, like every other knob" {
        // Katalyst step 5a-2 (2026-09-18): a name travels as the INDEX of a name in a closed
        // catalogue, so the two stages that used to be the exception are slots like the rest, and
        // `body("wood")` on a pattern reaches a declared classic chain through `katp`.
        val body = KatalystDsl.classic.stages.filterIsInstance<KatalystStageDsl.Body>().single()
        val vowel = KatalystDsl.classic.stages.filterIsInstance<KatalystStageDsl.Vowel>().single()

        val material = body.material.shouldBeInstanceOf<IgnitorDsl.Param>()
        val theVowel = vowel.vowel.shouldBeInstanceOf<IgnitorDsl.Param>()

        material.name shouldBe "body.material"
        theVowel.name shouldBe "vowel.vowel"

        // Unset, not 0: 0 is `none`, a real catalogue entry (see family 2).
        material.default.isFinite() shouldBe false
        theVowel.default.isFinite() shouldBe false

        // And so is the AMOUNT of both stages, which is the other half of "a material-only call
        // works on a declared chain": the engine substitutes BODY_WET / VOWEL_WET for an unset mix,
        // so the door has nothing to invent (round 1, 2026-09-18).
        body.wet.shouldBeInstanceOf<IgnitorDsl.Param>().default.isFinite() shouldBe false
        vowel.wet.shouldBeInstanceOf<IgnitorDsl.Param>().default.isFinite() shouldBe false
    }

    "a classic chain rebuilt from FRESH instances gets the same name, NaN slots and all" {
        // The identity map is content-keyed, so it hashes the chain. `classic` carries NaN on eleven
        // slots, and NaN is the one value where hashCode and equals can disagree: IEEE says NaN is
        // not equal to itself, Kotlin's data-class equals says it is, and a hash that took the IEEE
        // view would put a rebuilt chain in a different bucket and mint it a second name. Then one
        // chain would be announced twice, registered twice and crossfaded against itself.
        //
        // Every part is rebuilt: fresh `Param` objects, fresh stage data classes, a fresh list. An
        // earlier version of this row passed `classic.stages` straight back in, which shares the
        // list AND the `Param` instances, so equals and hashCode short-circuited on `===` and the
        // NaN path never ran (review round 3).
        //
        // Deliberately in commonTest: JS and JVM represent and hash doubles differently, so this is
        // a per-platform property and a jvmTest row would only prove half of it.
        fun freshParam(p: IgnitorDsl.Param) = IgnitorDsl.Param(
            name = p.name.toList().joinToString(""),
            default = if (p.default.isFinite()) p.default else Double.NaN,
            description = p.description,
        )

        fun freshKnob(knob: IgnitorDsl): IgnitorDsl = when (knob) {
            is IgnitorDsl.Param -> freshParam(knob)
            else -> knob
        }

        val rebuilt = KatalystDsl(
            KatalystDsl.classic.stages.map { stage ->
                when (stage) {
                    is KatalystStageDsl.Body -> KatalystStageDsl.Body(
                        freshKnob(stage.material), freshKnob(stage.wet), freshKnob(stage.floor),
                    )

                    is KatalystStageDsl.Vowel -> KatalystStageDsl.Vowel(
                        freshKnob(stage.vowel), freshKnob(stage.wet), freshKnob(stage.floor),
                    )

                    is KatalystStageDsl.Delay -> KatalystStageDsl.Delay(
                        freshKnob(stage.wet), freshKnob(stage.time), freshKnob(stage.feedback), freshKnob(stage.cap),
                    )

                    is KatalystStageDsl.Reverb -> KatalystStageDsl.Reverb(
                        freshKnob(stage.wet), freshKnob(stage.size), stage.lowpass?.let { freshKnob(it) },
                    )

                    is KatalystStageDsl.Phaser -> KatalystStageDsl.Phaser(
                        freshKnob(stage.rate), freshKnob(stage.wet), freshKnob(stage.center),
                        freshKnob(stage.sweep), freshKnob(stage.floor),
                    )

                    is KatalystStageDsl.Compressor -> KatalystStageDsl.Compressor(
                        freshKnob(stage.threshold), freshKnob(stage.ratio), freshKnob(stage.knee),
                        freshKnob(stage.attack), freshKnob(stage.release),
                    )

                    is KatalystStageDsl.Duck -> KatalystStageDsl.Duck(
                        freshKnob(stage.orbit), freshKnob(stage.depth), freshKnob(stage.attack),
                    )

                    is KatalystStageDsl.Eq -> KatalystStageDsl.Eq(stage.sections.toList())
                    is KatalystStageDsl.Gain -> KatalystStageDsl.Gain(freshKnob(stage.gain))
                }
            }
        )

        // Nothing is shared: not the list, not the stages, not the Params that carry the NaNs.
        (rebuilt.stages === KatalystDsl.classic.stages) shouldBe false

        val rebuiltCompressor = rebuilt.stages.filterIsInstance<KatalystStageDsl.Compressor>().single()
        val classicCompressor = KatalystDsl.classic.stages.filterIsInstance<KatalystStageDsl.Compressor>().single()

        (rebuiltCompressor === classicCompressor) shouldBe false
        (rebuiltCompressor.threshold === classicCompressor.threshold) shouldBe false
        (rebuiltCompressor.threshold as IgnitorDsl.Param).default.isFinite() shouldBe false

        rebuilt shouldBe KatalystDsl.classic
        rebuilt.uniqueId() shouldBe KatalystDsl.classic.uniqueId()
    }

    "the unset marker is non-finite, so a consumer must test isFinite and never compare" {
        SLOT_UNSET.isFinite() shouldBe false

        // On statically-typed Doubles Kotlin's `==` is IEEE, so a `x == SLOT_UNSET` guard is dead
        // code: it can never be true. And an infinity is unset too, which no equality would catch.
        // Both halves of "test isFinite(), never compare", pinned.
        val unset: Double = SLOT_UNSET

        (unset == SLOT_UNSET) shouldBe false
        Double.POSITIVE_INFINITY.isFinite() shouldBe false

        // The data-class `equals` the wire round trip relies on is the OTHER one (total order), so
        // a decoded `Param(name, NaN)` does compare equal to its original. Not a contradiction:
        // different operator, and `WireCodecRoundTripSpec` depends on this one holding.
        IgnitorDsl.Param("x", SLOT_UNSET) shouldBe IgnitorDsl.Param("x", SLOT_UNSET)
    }
})
