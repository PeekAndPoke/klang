/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystParam
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.stdlib.KlangScriptIgnitor
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData

/**
 * The two slot setters under their four names, `ignitorParam` / `ignp` and `katalystParam` / `katp` (the
 * Ignitor/Katalyst naming, plan sections 4.5 and 4.7), BY TABLE: every name, every kind of slot argument, both doors,
 * all four calling forms (pattern, string receiver, standalone mapper, chained mapper).
 *
 * - **Parity**: a slot NAME, the slot OBJECT (`Ign.slot.*` / `Kat.slot.*`, the Kotlin `IgnitorDsl.Slots.*` /
 *   `KatalystDsl.Slots.*`) and a param OBJECT of the same name write the same bag entry, and nothing else.
 * - **Errors**: the wrong param kind, a number (decision Q8), a sound, an expression over a param, a chain, null and an
 *   object literal are each a script error raised when the door is CALLED, whose message names the door and the fix. In commonTest, so the
 *   rows run on JS too (`/dsl-design` checklist 13: a type check can compile away there).
 */
class LangSlotSetterSpec : StringSpec({

    /** One setter name: its four Kotlin forms, its twin on the other host, and the bag it writes. */
    class Setter(
        val name: String,
        val twin: String,
        /** What the error says a right param is: "an Ignitor param" / "a Katalyst param". */
        val paramKind: String,
        val bag: (SprudelVoiceData) -> Map<String, Double>?,
        val pattern: (SprudelPattern, Any?, Any) -> SprudelPattern,
        val string: (String, Any?, Any) -> SprudelPattern,
        val mapper: (Any?, Any) -> PatternMapperFn,
        val chained: (PatternMapperFn, Any?, Any) -> PatternMapperFn,
    ) {
        /** The four Kotlin forms, each producing a pattern from one slot argument and the value 0.25. */
        fun kotlinForms(slot: Any?): List<Pair<String, () -> SprudelPattern>> = listOf(
            "pattern" to { pattern(note("c3"), slot, 0.25) },
            "string" to { string("c3", slot, 0.25) },
            "mapper" to { note("c3").apply(mapper(slot, 0.25)) },
            "chained" to { note("c3").apply(chained(gain(0.5), slot, 0.25)) },
        )

        /** The four script forms of the same call, with [slot] as script source. */
        fun scriptForms(slot: String): List<String> = listOf(
            """note("c3").$name($slot, 0.25)""",
            """"c3".$name($slot, 0.25)""",
            """note("c3").apply($name($slot, 0.25))""",
            """note("c3").apply(gain(0.5).$name($slot, 0.25))""",
        )
    }

    val ignitorBag: (SprudelVoiceData) -> Map<String, Double>? = { it.ignitorParams?.toMap() }
    val katalystBag: (SprudelVoiceData) -> Map<String, Double>? = { it.katalystParams?.toMap() }

    val ignitorSetters = listOf(
        Setter(
            "ignitorParam", "katalystParam", "an Ignitor param", ignitorBag,
            { p, s, v -> p.ignitorParam(s, v) }, { p, s, v -> p.ignitorParam(s, v) }, { s, v -> ignitorParam(s, v) },
            { m, s, v -> m.ignitorParam(s, v) },
        ),
        Setter(
            "ignp", "katp", "an Ignitor param", ignitorBag,
            { p, s, v -> p.ignp(s, v) }, { p, s, v -> p.ignp(s, v) }, { s, v -> ignp(s, v) }, { m, s, v -> m.ignp(s, v) },
        ),
    )

    val katalystSetters = listOf(
        Setter(
            "katalystParam", "ignitorParam", "a Katalyst param", katalystBag,
            { p, s, v -> p.katalystParam(s, v) }, { p, s, v -> p.katalystParam(s, v) }, { s, v -> katalystParam(s, v) },
            { m, s, v -> m.katalystParam(s, v) },
        ),
        Setter(
            "katp", "ignp", "a Katalyst param", katalystBag,
            { p, s, v -> p.katp(s, v) }, { p, s, v -> p.katp(s, v) }, { s, v -> katp(s, v) }, { m, s, v -> m.katp(s, v) },
        ),
    )

    fun SprudelPattern.firstData(): SprudelVoiceData = queryArc(0.0, 1.0).first().data

    fun compile(code: String): SprudelPattern = SprudelPattern.compile(code).shouldNotBeNull()

    // ── Parity ───────────────────────────────────────────────────────────────────────────────────

    fun parityRows(setters: List<Setter>, slotName: String, scriptSlots: List<String>, kotlinSlots: List<Any>) {
        setters.forEach { setter ->
            val expected = mapOf(slotName to 0.25)

            scriptSlots.forEach { slot ->
                setter.scriptForms(slot).forEach { code ->
                    withClue("script: $code") { setter.bag(compile(code).firstData()) shouldBe expected }
                }
            }

            kotlinSlots.forEach { slot ->
                setter.kotlinForms(slot).forEach { (form, build) ->
                    withClue("Kotlin: ${setter.name}, $form form, slot $slot") { setter.bag(build().firstData()) shouldBe expected }
                }
            }
        }
    }

    "ignitorParam and ignp: a name, the slot object and a param object write the same entry, both doors, every form" {
        parityRows(
            setters = ignitorSetters,
            slotName = "lpf.freq",
            scriptSlots = listOf(
                "\"lpf.freq\"", "Ign.slot.lpf.freq", "Ignitor.slot.lpf.freq", "Ign.param(\"lpf.freq\", 99)",
            ),
            kotlinSlots = listOf("lpf.freq", IgnitorDsl.Slots.lpf.freq, IgnitorDsl.Param("lpf.freq", 99.0)),
        )
    }

    "katalystParam and katp: a name, the slot object and a param object write the same entry, both doors, every form" {
        parityRows(
            setters = katalystSetters,
            slotName = "reverb.wet",
            scriptSlots = listOf(
                "\"reverb.wet\"", "Kat.slot.reverb.wet", "Katalyst.slot.reverb.wet", "Kat.param(\"reverb.wet\", 9)",
            ),
            kotlinSlots = listOf("reverb.wet", KatalystDsl.Slots.reverb.wet, KatalystParam(IgnitorDsl.Param("reverb.wet", 9.0))),
        )
    }

    "a param held in a variable is written by its name, its default stays the instrument's" {
        val voice = compile(
            """
            let cutoff = Ign.param("cutoff", 800)
            note("c3").ignp(cutoff, 1200)
            """.trimIndent()
        ).firstData()
        voice.ignitorParams?.toMap() shouldBe mapOf("cutoff" to 1200.0)

        val orbit = compile(
            """
            let room = Kat.param("room", 2)
            note("c3").katp(room, 9)
            """.trimIndent()
        ).firstData()
        orbit.katalystParams?.toMap() shouldBe mapOf("room" to 9.0)
    }

    // ── Errors ───────────────────────────────────────────────────────────────────────────────────

    /** One wrong slot argument: as script, as a Kotlin value, and the end of the message it must produce. */
    data class Wrong(val label: String, val script: String, val kotlin: Any?, val message: (Setter) -> String)

    fun wrongKinds(otherParamScript: String, otherParam: Any, otherSlotScript: String, otherSlot: Any): List<Wrong> {
        val expects: (Setter, String) -> String = { s, got -> "${s.name} expects a slot name or ${s.paramKind}, got $got" }
        val wrongHost: (Setter) -> String = { s ->
            val other = if (s.paramKind == "an Ignitor param") "a Katalyst param" else "an Ignitor param"
            "$other passed to ${s.name}; use ${s.twin}"
        }

        return listOf(
            Wrong("the other host's param", otherParamScript, otherParam, wrongHost),
            Wrong("the other host's slot object", otherSlotScript, otherSlot, wrongHost),
            Wrong("a number (Q8)", "42", 42) { expects(it, "a number") },
            Wrong("a sound", "Ign.sine()", KlangScriptIgnitor.sine()) { expects(it, "a sound") },
            Wrong(
                "an expression over a param", "Ign.param(\"x\", 1).mul(2)",
                IgnitorDsl.Param("x", 1.0).mul(IgnitorDsl.Constant(2.0)),
            ) { expects(it, "a sound") },
            Wrong("a chain", "Kat()", KatalystDsl(emptyList())) { expects(it, "a Katalyst chain") },
            // A `let` never assigned: the slot parameter is nullable, so null reaches the door's own message instead of
            // the interpreter's "Cannot convert NullValue to Any".
            Wrong("null", "null", null) { expects(it, "null") },
            Wrong("an object literal", "{a: 1}", mapOf("a" to 1)) { expects(it, "an object") },
        )
    }

    fun errorRows(setters: List<Setter>, wrongs: List<Wrong>) {
        setters.forEach { setter ->
            wrongs.forEach { wrong ->
                val message = wrong.message(setter)

                setter.scriptForms(wrong.script).forEach { code ->
                    withClue("script, ${wrong.label}: $code") {
                        shouldThrow<KlangScriptTypeError> { SprudelPattern.compile(code) }.message shouldBe message
                    }
                }

                setter.kotlinForms(wrong.kotlin).forEach { (form, build) ->
                    withClue("Kotlin, ${wrong.label}: ${setter.name}, $form form") {
                        // Raised at the CALL: building is enough, nothing is queried.
                        shouldThrow<KlangScriptTypeError> { build() }.message shouldBe message
                    }
                }
            }
        }
    }

    "ignitorParam and ignp: a wrong slot argument is a script error at the call, naming the door and the fix" {
        errorRows(
            setters = ignitorSetters,
            wrongs = wrongKinds(
                otherParamScript = "Kat.param(\"room\", 5)", otherParam = KatalystParam(IgnitorDsl.Param("room", 5.0)),
                otherSlotScript = "Kat.slot.reverb.wet", otherSlot = KatalystDsl.Slots.reverb.wet,
            ),
        )
    }

    "katalystParam and katp: a wrong slot argument is a script error at the call, naming the door and the fix" {
        errorRows(
            setters = katalystSetters,
            wrongs = wrongKinds(
                otherParamScript = "Ign.param(\"cutoff\", 800)", otherParam = IgnitorDsl.Param("cutoff", 800.0),
                otherSlotScript = "Ign.slot.lpf.freq", otherSlot = IgnitorDsl.Slots.lpf.freq,
            ),
        )
    }

    "the script error carries the call's location, so the editor marks the call" {
        for (code in listOf("""note("c3").ignp(Kat.slot.reverb.wet, 1)""", """note("c3").katp(Ign.slot.lpf.freq, 1)""")) {
            withClue(code) {
                val location = shouldThrow<KlangScriptTypeError> { SprudelPattern.compile(code) }.location.shouldNotBeNull()
                location.startLine shouldBe 1
            }
        }
    }
})
