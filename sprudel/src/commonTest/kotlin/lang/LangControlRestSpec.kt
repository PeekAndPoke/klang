/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * A rest in a setter's control pattern leaves the field untouched (decided 2026-09-16): nothing is
 * written on that event, so a value an earlier call set survives. One test per function that sets a
 * field through a control pattern, and one per slot of every compound, aliases included.
 *
 * Each row calls the setter twice on the same notes: first with a plain value, then with a control
 * pattern `"<B ~>"`. In cycle 0 the control writes B, so the events must differ from the first call
 * alone (the row really reaches the field). In cycle 1 the control rests, so the events must equal
 * the first call alone, field for field. A row marked `fresh` has no first call: its value does not
 * reach the voice data (the `snd...(params)` doors set the sound only), so the rest cycle must equal
 * the notes before the call.
 *
 * `hurry` is not here: it also runs `fast` with the same control pattern, and a rest there drops the
 * notes, which is the structural family (`_lift` / `_liftData` joins) left for a later decision.
 * String slots carry their plain value quoted (`"wood"`); inside the control pattern it is an atom.
 */
class LangControlRestSpec : StringSpec({

    class Row(val call: String, val slot: String, val a: String, val b: String, val fresh: Boolean = false)

    fun single(call: String, slot: String, a: String, b: String) = Row(call, slot, a, b)

    fun compound(call: String, vararg slots: Triple<String, String, String>) =
        slots.map { (slot, a, b) -> Row(call, slot, a, b) }

    fun t(slot: String, a: String, b: String) = Triple(slot, a, b)

    fun filterSlots(passes: Boolean) = listOfNotNull(
        t("freq", "800", "1600"),
        t("q", "1", "2"),
        if (passes) t("passes", "2", "3") else null,
        t("env", "12", "24"),
        t("attack", "0.01", "0.02"),
        t("decay", "0.1", "0.2"),
        t("sustain", "0.3", "0.6"),
        t("release", "0.1", "0.2"),
    ).toTypedArray()

    val compressorSlots = arrayOf(
        t("threshold", "-12", "-24"), t("ratio", "2", "4"), t("knee", "3", "6"),
        t("attack", "0.01", "0.02"), t("release", "0.1", "0.2"),
    )
    val unisonSlots = arrayOf(t("voices", "3", "5"), t("spread", "0.1", "0.2"), t("pan", "0.3", "0.6"))
    val penvSlots = arrayOf(
        t("amount", "12", "24"), t("attack", "0.01", "0.02"), t("decay", "0.1", "0.2"),
        t("sustain", "0.25", "0.5"), t("release", "0.1", "0.2"),
    )
    val vibratoSlots = arrayOf(t("rate", "4", "6"), t("depth", "0.3", "0.6"))
    val superSlots = arrayOf(t("voices", "3", "5"), t("spread", "0.1", "0.2"))
    val pluckSlots = arrayOf(
        t("decay", "0.9", "0.95"), t("brightness", "0.3", "0.6"),
        t("pickPosition", "0.3", "0.6"), t("stiffness", "0.3", "0.6"),
    )

    val rows: List<Row> = listOf(
        // Level
        single("gain", "amount", "0.3", "0.6"),
        single("pregain", "amount", "0.3", "0.6"),
        single("pan", "amount", "0.3", "0.6"),
        single("velocity", "amount", "0.3", "0.6"),
        single("vel", "amount", "0.3", "0.6"),
        // Routing and dynamics
        single("orbit", "index", "1", "2"),
        single("o", "index", "1", "2"),
        single("cylinder", "index", "1", "2"),
        single("density", "amount", "2", "3"),
        single("d", "amount", "2", "3"),
        single("cull", "seconds", "0.5", "1"),
    ) + listOf(
        compound("adsr", t("attack", "0.01", "0.02"), t("decay", "0.1", "0.2"), t("sustain", "0.3", "0.6"), t("release", "0.1", "0.2")),
        compound("duck", t("orbit", "1", "2"), t("depth", "0.3", "0.6"), t("attack", "0.01", "0.02")),
        compound("unison", *unisonSlots),
        compound("uni", *unisonSlots),
        compound("compressor", *compressorSlots),
        compound("comp", *compressorSlots),
        // Effects
        compound("body", t("wet", "0.3", "0.6"), t("material", "\"wood\"", "metal"), t("floor", "0.3", "0.6")),
        compound("vowel", t("wet", "0.3", "0.6"), t("vowel", "\"a\"", "o"), t("floor", "0.3", "0.6")),
        compound("delay", t("wet", "0.3", "0.6"), t("time", "0.25", "0.5"), t("feedback", "0.4", "0.7"), t("cap", "1", "2")),
        compound("reverb", t("size", "3", "6"), t("lowpass", "1000", "2000")),
        compound("distort", t("amount", "0.3", "0.6"), t("oversample", "2", "4")),
        compound("crush", t("amount", "4", "8"), t("oversample", "2", "4")),
        compound("coarse", t("amount", "2", "4"), t("oversample", "2", "4")),
        compound(
            "phaser", t("wet", "0.3", "0.6"), t("rate", "0.5", "1"), t("center", "1000", "2000"),
            t("sweep", "500", "1000"), t("floor", "0.3", "0.6"),
        ),
        compound("tremolo", t("depth", "0.3", "0.6"), t("sync", "2", "4"), t("skew", "0.2", "0.4"), t("phase", "0.25", "0.5")),
        // Filters
        compound("lpf", *filterSlots(passes = true)),
        compound("lowpass", *filterSlots(passes = true)),
        compound("hpf", *filterSlots(passes = true)),
        compound("highpass", *filterSlots(passes = true)),
        compound("bpf", *filterSlots(passes = false)),
        compound("bandpass", *filterSlots(passes = false)),
        compound("notch", *filterSlots(passes = false)),
        // Samples
        listOf(
            single("begin", "pos", "0.1", "0.2"),
            single("end", "pos", "0.8", "0.9"),
            single("loopBegin", "pos", "0.1", "0.2"),
            single("loopb", "pos", "0.1", "0.2"),
            single("loopEnd", "pos", "0.8", "0.9"),
            single("loope", "pos", "0.8", "0.9"),
            single("cut", "group", "1", "2"),
            single("speed", "rate", "1.5", "2"),
        ),
        // Synthesis
        compound("fm", t("env", "2", "4"), t("h", "1", "2"), t("attack", "0.01", "0.02"), t("decay", "0.1", "0.2"), t("sustain", "0.3", "0.6")),
        listOf(
            single("analog", "amount", "1", "2"),
            single("duty", "amount", "0.3", "0.6"),
            single("onepole", "freq", "1000", "2000"),
            Row("oscparam(key = \"analog\", ", "value", "1", "2"),
            Row("oscp(key = \"analog\", ", "value", "1", "2"),
        ),
        listOf("sndPink", "sndRamp", "sndSaw", "sndSine", "sndSquare", "sndTriangle", "sndZamp").map {
            Row(it, "params", "1", "1", fresh = true)
        },
        compound("sndNoise", t("color", "0.3", "0.6")),
        compound("sndBrown", t("depth", "0.3", "0.6")),
        compound("sndPulze", t("duty", "0.3", "0.6")),
        compound("sndDust", t("density", "1", "2"), t("tail", "0.3", "0.6")),
        compound("sndCrackle", t("chaos", "0.3", "0.6")),
        compound("sndPluck", *pluckSlots),
        compound("sndSuperPluck", *superSlots, *pluckSlots),
        compound("sndSuperSaw", *superSlots),
        compound("sndSuperSine", *superSlots),
        compound("sndSuperSquare", *superSlots),
        compound("sndSuperTri", *superSlots),
        compound("sndSuperRamp", *superSlots),
        // Tonal
        listOf(
            single("freq", "hz", "220", "440"),
            single("legato", "amount", "0.5", "0.8"),
            single("clip", "amount", "0.5", "0.8"),
            single("accelerate", "semitones", "1", "2"),
            single("scale", "name", "\"c:major\"", "d:minor"),
            single("bank", "name", "\"RolandTR808\"", "RolandTR909"),
        ),
        compound("penv", *penvSlots),
        compound("pamt", *penvSlots),
        compound("vibrato", *vibratoSlots),
        compound("vib", *vibratoSlots),
    ).flatten()

    fun call(row: Row, value: String): String =
        if (row.call.endsWith(", ")) "${row.call}${row.slot} = $value)" else "${row.call}(${row.slot} = $value)"

    fun compile(code: String): SprudelPattern = withClue(code) { SprudelPattern.compile(code).shouldNotBeNull() }

    fun dataOf(p: SprudelPattern, cycle: Int) = p.queryArc(cycle.toDouble(), cycle + 1.0).map { it.data.copy(patternId = null) }

    rows.forEach { row ->
        val label = if (row.call.endsWith(", ")) "${row.call.substringBefore("(")}(${row.slot})" else "${row.call}(${row.slot})"

        "$label: a rest in the control pattern leaves the field untouched" {
            val base = """note("c3 e3")"""
            val reference = if (row.fresh) base else "$base.${call(row, row.a)}"
            val withRest = "$reference.${call(row, "\"<${row.b} ~>\"")}"

            val before = compile(reference)
            val after = compile(withRest)

            withClue("cycle 0: the control writes ${row.b}, so the call must reach the voice data ($withRest)") {
                dataOf(after, 0) shouldNotBe dataOf(before, 0)
            }
            withClue("cycle 1: the control rests, so nothing may change ($withRest)") {
                dataOf(after, 1) shouldBe dataOf(before, 1)
            }
        }
    }
})
