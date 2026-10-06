/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.tremolo
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * The oscillators' `phase` knob and the tremolo's `range(from, to)` on both doors (`docs/tasks-archive/2026-10/20261006-oscillator-phase-knob.md`,
 * decisions 1 and 2 of 2026-10-06): the script door (`Ignitor.sine(4, x => x.phase(0.25))`, positional and named) and
 * the Kotlin door (`KlangScriptIgnitor.sine(4) { it.phase(0.25) }`) build the same node, and that node is the
 * default node with exactly its `phase` field written (the expectation never goes through a builder). Every periodic
 * oscillator door is a row. The pluck doors and the noise doors have no `phase`.
 *
 * The tremolo: `range(from, to)` on the script door's builder; the flat Kotlin door in `audio_bridge` takes `rangeFrom`
 * and `rangeTo` (the recorded flat-door asymmetry, the filter doors' precedent).
 */
class KlangScriptIgnitorPhaseDoorParitySpec : StringSpec({

    fun ks(code: String): IgnitorDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        return engine.execute(code).toObjectOrNull<IgnitorDsl>()!!
    }

    fun c(v: Double) = IgnitorDsl.Constant(v)

    /** A door: its script name, its Kotlin door with the knob, and the default node with a phase written by `copy`. */
    class PhaseDoor(
        val name: String,
        val kotlin: (Any) -> IgnitorDsl,
        val expected: (IgnitorDsl) -> IgnitorDsl,
    )

    val doors = listOf(
        PhaseDoor("sine", { p -> KlangScriptIgnitor.sine(4.0) { it.phase(p) } }, { p -> IgnitorDsl.Sine(freq = c(4.0), phase = p) }),
        PhaseDoor("saw", { p -> KlangScriptIgnitor.saw(4.0) { it.phase(p) } }, { p -> IgnitorDsl.Saw(freq = c(4.0), phase = p) }),
        PhaseDoor("ramp", { p -> KlangScriptIgnitor.ramp(4.0) { it.phase(p) } }, { p -> IgnitorDsl.Ramp(freq = c(4.0), phase = p) }),
        PhaseDoor("square", { p -> KlangScriptIgnitor.square(4.0) { it.phase(p) } }, { p -> IgnitorDsl.Pulze(freq = c(4.0), phase = p) }),
        PhaseDoor("pulze", { p -> KlangScriptIgnitor.pulze(4.0) { it.phase(p) } }, { p -> IgnitorDsl.RawPulze(freq = c(4.0), phase = p) }),
        PhaseDoor("tri", { p -> KlangScriptIgnitor.tri(4.0) { it.phase(p) } }, { p -> IgnitorDsl.Tri(freq = c(4.0), phase = p) }),
        PhaseDoor("zawtooth", { p -> KlangScriptIgnitor.zawtooth(4.0) { it.phase(p) } }, { p -> IgnitorDsl.Zawtooth(freq = c(4.0), phase = p) }),
        PhaseDoor("zamp", { p -> KlangScriptIgnitor.zamp(4.0) { it.phase(p) } }, { p -> IgnitorDsl.Zamp(freq = c(4.0), phase = p) }),
        PhaseDoor("impulse", { p -> KlangScriptIgnitor.impulse(4.0) { it.phase(p) } }, { p -> IgnitorDsl.Impulse(freq = c(4.0), phase = p) }),
        PhaseDoor("supersaw", { p -> KlangScriptIgnitor.supersaw(4.0) { it.phase(p) } }, { p -> IgnitorDsl.SuperSaw(freq = c(4.0), phase = p) }),
        PhaseDoor("supersine", { p -> KlangScriptIgnitor.supersine(4.0) { it.phase(p) } }, { p -> IgnitorDsl.SuperSine(freq = c(4.0), phase = p) }),
        PhaseDoor("supersquare", { p -> KlangScriptIgnitor.supersquare(4.0) { it.phase(p) } }, { p -> IgnitorDsl.SuperSquare(freq = c(4.0), phase = p) }),
        PhaseDoor("supertri", { p -> KlangScriptIgnitor.supertri(4.0) { it.phase(p) } }, { p -> IgnitorDsl.SuperTri(freq = c(4.0), phase = p) }),
        PhaseDoor("superramp", { p -> KlangScriptIgnitor.superramp(4.0) { it.phase(p) } }, { p -> IgnitorDsl.SuperRamp(freq = c(4.0), phase = p) }),
    )

    "every periodic oscillator door: phase(x) positional, named, on Ign and on the Kotlin door is the node with its phase written" {
        for (door in doors) {
            withClue(door.name) {
                val expected = door.expected(c(0.25))

                ks("Ignitor.${door.name}(4, x => x.phase(0.25))") shouldBe expected
                ks("Ignitor.${door.name}(4, x => x.phase(phase = 0.25))") shouldBe expected
                ks("Ign.${door.name}(4, x => x.phase(0.25))") shouldBe expected
                door.kotlin(0.25) shouldBe expected

                // not vacuous: the knob wrote a field the default does not have
                expected shouldNotBe ks("Ignitor.${door.name}(4)")
            }
        }
    }

    "the default phase is 0 on both doors, and the value is not clamped or wrapped at the door (the engine wraps)" {
        for (door in doors) {
            withClue(door.name) {
                ks("Ignitor.${door.name}(4)") shouldBe door.expected(c(0.0))
                ks("Ignitor.${door.name}(4, x => x.phase(1.25))") shouldBe door.expected(c(1.25))
                ks("Ignitor.${door.name}(4, x => x.phase(-0.25))") shouldBe door.expected(c(-0.25))
                door.kotlin(-0.25) shouldBe door.expected(c(-0.25))
            }
        }
    }

    "the phase takes a signal on both doors: phase modulation" {
        val lfo = ks("Ign.sine(3).mul(0.1)")

        for (door in doors) {
            withClue(door.name) {
                ks("Ignitor.${door.name}(4, x => x.phase(Ign.sine(3).mul(0.1)))") shouldBe door.expected(lfo)
                door.kotlin(lfo) shouldBe door.expected(lfo)
            }
        }
    }

    // ── the tremolo's range ──────────────────────────────────────────────────────────────────────────────

    "tremolo range(from, to): positional, named in either order, and both Kotlin doors build the same node" {
        val saw = IgnitorDsl.Saw()
        val expected = IgnitorDsl.Tremolo(inner = saw, rate = c(4.0), depth = c(0.3), rangeFrom = c(0.0), rangeTo = c(1.0))

        ks("Ignitor.saw().tremolo(4, 0.3, x => x.range(0, 1))") shouldBe expected
        ks("Ignitor.saw().tremolo(4, 0.3, x => x.range(from = 0, to = 1))") shouldBe expected
        ks("Ignitor.saw().tremolo(4, 0.3, x => x.range(to = 1, from = 0))") shouldBe expected
        KlangScriptIgnitorExtensions.tremolo(saw, 4.0, 0.3) { it.range(0.0, 1.0) } shouldBe expected
        saw.tremolo(4.0, 0.3, rangeFrom = 0.0, rangeTo = 1.0) shouldBe expected

        // with the shape on the same builder
        ks("""Ignitor.saw().tremolo(4, 0.3, x => x.shape("square").range(-1, 1))""") shouldBe
            saw.tremolo(4.0, 0.3, shape = "square", rangeFrom = -1.0, rangeTo = 1.0)
    }

    "the tremolo's default range is (-1, 0), the classic dip, on every door" {
        val saw = IgnitorDsl.Saw()
        val classic = ks("Ignitor.saw().tremolo(4, 0.3)") as IgnitorDsl.Tremolo

        classic.rangeFrom shouldBe c(-1.0)
        classic.rangeTo shouldBe c(0.0)
        saw.tremolo(4.0, 0.3) shouldBe classic
        ks("Ignitor.saw().tremolo(4, 0.3, x => x.range(-1, 0))") shouldBe classic
        ks("Ignitor.saw().tremolo(4, 0.3, x => x.range(0, 1))") shouldNotBe classic
    }

    "the tremolo's range takes signals" {
        val saw = IgnitorDsl.Saw()
        val lfo = ks("Ign.sine(0.5).range(1, 2)")

        ks("Ignitor.saw().tremolo(4, 0.3, x => x.range(0, Ign.sine(0.5).range(1, 2)))") shouldBe
            IgnitorDsl.Tremolo(inner = saw, rate = c(4.0), depth = c(0.3), rangeFrom = c(0.0), rangeTo = lfo)
    }
})
