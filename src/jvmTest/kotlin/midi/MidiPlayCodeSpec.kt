/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.midi

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.midi.MidiPlayCode.Sound
import io.peekandpoke.klang.storage.MidiInstrument

/** The MIDI play code's text work (KLANG-00402, D7): the names it binds, the sound a key plays, another sound there, old saves. */
class MidiPlayCodeSpec : StringSpec({

    val code = """
        |let lead = Ign.saw()
        |const pad = Ign.supersaw()
        |
        |x => x.sound(lead).reverb(wet = 0.2).sound("sine")
        |""".trimMargin()

    "the names a play code binds at its top level, in order; none where it does not parse" {
        MidiPlayCode.namesOf(code) shouldBe listOf("lead", "pad")
        MidiPlayCode.namesOf("let = (") shouldBe emptyList()
        withClue("a let ended by ';' before a function written (x) =>, as New instrument writes it") {
            val code = "let instrument1 = Ign.saw();\n\n(x) => x.sound(lead)"

            MidiPlayCode.namesOf(code) shouldBe listOf("instrument1")
            MidiPlayCode.soundOf(code) shouldBe Sound.Own("lead")
        }
    }

    "the sound a key plays: the last of the function's chain, a let by its name, a built-in in quotes; none for an expression" {
        MidiPlayCode.soundOf(code) shouldBe Sound.BuiltIn("sine")
        MidiPlayCode.soundOf("let x = Ign.saw()\n\nx => x.sound(lead)") shouldBe Sound.Own("lead")
        withClue("sprudel's s, sound's alias, is a sound too") {
            MidiPlayCode.soundOf("x => x.sound(lead).s(\"pluck\")") shouldBe Sound.BuiltIn("pluck")
        }
        withClue("a sound inside an argument is not the chain's") {
            MidiPlayCode.soundOf("x => x.sound(lead).superimpose(y => y.sound(bell))") shouldBe Sound.Own("lead")
        }
        withClue("a sound in a let before the function plays no key") {
            MidiPlayCode.soundOf("let pat = note(\"c\").sound(\"bd\")\n\nx => x.sound(lead)") shouldBe Sound.Own("lead")
        }
        MidiPlayCode.soundOf("""x => x.sound("supersaw")""") shouldBe Sound.BuiltIn("supersaw")
        MidiPlayCode.soundOf("""x => x.sound(pick(1))""") shouldBe null
        MidiPlayCode.soundOf("x => x.reverb(wet = 0.2)") shouldBe null
    }

    "another sound in the place of the one a key plays, the rest of the code as it was; none to change, no code" {
        withClue("a let for a built-in, quotes and all, in the chain's last sound") {
            MidiPlayCode.withSound(code, Sound.Own("pad")) shouldBe code.replace(".sound(\"sine\")", ".sound(pad)")
        }
        withClue("a built-in in quotes for a let") {
            MidiPlayCode.withSound("x => x.sound(lead)", Sound.BuiltIn("pluck")) shouldBe "x => x.sound(\"pluck\")"
        }
        withClue("a let for a built-in, quotes and all") {
            MidiPlayCode.withSound("""x => x.sound("sine").gain(0.5)""", Sound.Own("lead")) shouldBe "x => x.sound(lead).gain(0.5)"
        }
        withClue("an expression there is the user's") {
            MidiPlayCode.withSound("""x => x.sound(pick(1))""", Sound.Own("lead")) shouldBe null
        }
    }

    "the play code a first visit starts with plays its own warm saw" {
        MidiPlayCode.namesOf(MidiPlayCode.DEFAULT) shouldBe listOf("warmSaw")
        MidiPlayCode.soundOf(MidiPlayCode.DEFAULT) shouldBe Sound.Own("warmSaw")
    }

    "an old save as one play code: each Ignitor a let, the draft's last and played, its mapper chained on" {
        val saved = listOf(MidiInstrument(name = "Warm saw", ignitorCode = "Ign.saw()", mapperCode = "reverb(wet = 0.2)"))
        val draft = MidiInstrument(name = "2 bright!", ignitorCode = "Ign.square()", mapperCode = "x => x.reverb(wet = 0.4)")

        val converted = MidiPlayCode.fromOld(draft = draft, saved = saved)

        withClue("the names: one per instrument, a word that begins with a letter") {
            MidiPlayCode.namesOf(converted) shouldBe listOf("warmSaw", "instrument2Bright")
        }
        withClue("the draft's instrument played, its arrow mapper's body after the sound") {
            MidiPlayCode.soundOf(converted) shouldBe Sound.Own("instrument2Bright")
            converted.trim().lines().last() shouldBe "x => x.sound(instrument2Bright).reverb(wet = 0.4)"
        }
        withClue("a mapper value chained on; no Ignitor in the draft: a built-in") {
            MidiPlayCode.fromOld(draft = MidiInstrument(name = "x", ignitorCode = " ", mapperCode = "reverb(wet = 0.2)"), saved = emptyList())
                .trim() shouldBe "x => x.sound(\"supersaw\").reverb(wet = 0.2)"
        }
        withClue("two of one name: the second numbered") {
            MidiPlayCode.namesOf(
                MidiPlayCode.fromOld(
                    draft = null,
                    saved = listOf(
                        MidiInstrument(name = "Lead", ignitorCode = "Ign.saw()", mapperCode = ""),
                        MidiInstrument(name = "lead", ignitorCode = "Ign.sine()", mapperCode = ""),
                    ),
                )
            ) shouldBe listOf("lead", "lead2")
        }
    }

    "an old save always reads: other arrow forms kept, a mapper or an Ignitor that would not read kept as a comment" {
        fun converted(ignitorCode: String = "Ign.saw()", mapperCode: String, name: String = "Lead") =
            MidiPlayCode.fromOld(draft = MidiInstrument(name = name, ignitorCode = ignitorCode, mapperCode = mapperCode), saved = emptyList())

        fun function(code: String) = code.trim().substringAfterLast("\n\n")

        withClue("(x) => and the dot on the next line") {
            function(converted(mapperCode = "(x) => x\n  .reverb(wet = 0.2)")) shouldBe "x => x.sound(lead).reverb(wet = 0.2)"
        }
        withClue("its own parameter's name, used again in the body") {
            function(converted(mapperCode = "p => p.stack(p.add(7))")) shouldBe "p => p.sound(lead).stack(p.add(7))"
        }
        withClue("a mapper that does not read so: the plain sound, the old one a comment above it") {
            val code = converted(mapperCode = "x => stack(x, x.add(7))")

            function(code) shouldBe "x => x.sound(lead)"
            code.contains("// x => stack(x, x.add(7))") shouldBe true
            MidiPlayCode.soundOf(code) shouldBe Sound.Own("lead")
        }
        withClue("an Ignitor of two statements: a comment, a built-in plays") {
            val code = converted(ignitorCode = "Ign.saw()\nlet b = 1", mapperCode = "")

            code.contains("// Ign.saw()") shouldBe true
            MidiPlayCode.soundOf(code) shouldBe Sound.BuiltIn(MidiPlayCode.FALLBACK_SOUND)
        }
        withClue("a saved instrument's own mapper: a comment under its let") {
            val code = MidiPlayCode.fromOld(
                draft = null,
                saved = listOf(MidiInstrument(name = "Pad", ignitorCode = "Ign.supersaw()", mapperCode = "x => x.room(0.8)")),
            )

            MidiPlayCode.namesOf(code) shouldBe listOf("pad")
            code.contains("// x => x.room(0.8)") shouldBe true
        }
        withClue("a name that is the function's parameter: another, so the sound plays the instrument") {
            val code = converted(mapperCode = "p => p.reverb(wet = 0.2)", name = "P")

            MidiPlayCode.namesOf(code) shouldBe listOf("p2")
            function(code) shouldBe "p => p.sound(p2).reverb(wet = 0.2)"
        }
        withClue("a name that is a word of the language or a sound's: an instrument's") {
            MidiPlayCode.namesOf(converted(mapperCode = "", name = "If")) shouldBe listOf("ifInstrument")
            MidiPlayCode.namesOf(converted(mapperCode = "", name = "Sine")) shouldBe listOf("sineInstrument")
        }
    }
})
