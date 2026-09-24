/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.NativeObjectValue

/** One door parameter: its name, a value unlike the bare stage's own, and that value as script. */
private data class DoorParam(val name: String, val value: Double) {
    val literal: String get() = value.toString()
}

/**
 * One Katalyst stage door. [set] writes one field of the stage by its door-parameter name, so
 * [expect] can build the stage a call SHOULD produce from the bare data class and nothing else,
 * never from the door under test; [kotlin] calls the Kotlin door with only the parameters in its
 * map (a missing key is an omitted parameter, which on these doors is `null`).
 */
private class StageDoor(
    val stage: String,
    val bare: KatalystStageDsl,
    val params: List<DoorParam>,
    val set: (KatalystStageDsl, String, IgnitorDsl) -> KatalystStageDsl,
    val kotlin: (KatalystBuilder, Map<String, Any?>) -> KatalystBuilder,
) {
    fun expect(written: List<DoorParam>): KatalystStageDsl =
        written.fold(bare) { stage, p -> set(stage, p.name, IgnitorDsl.Constant(p.value)) }
}

private fun unknown(stage: String, name: String): Nothing = error("$stage has no door parameter '$name'")

/** The whole family of Katalyst stage doors with parameters: every row loops over this list. */
private val doors: List<StageDoor> = listOf(
    StageDoor(
        stage = "body",
        bare = KatalystStageDsl.Body(),
        params = listOf(DoorParam("wet", 0.7), DoorParam("material", 3.0)),
        set = { s, n, v ->
            val b = s as KatalystStageDsl.Body
            when (n) {
                "wet" -> b.copy(wet = v)
                "material" -> b.copy(material = v)
                else -> unknown("body", n)
            }
        },
        kotlin = { k, a -> k.body(wet = a["wet"], material = a["material"]) },
    ),
    StageDoor(
        stage = "vowel",
        bare = KatalystStageDsl.Vowel(),
        params = listOf(DoorParam("wet", 0.6), DoorParam("vowel", 4.0)),
        set = { s, n, v ->
            val b = s as KatalystStageDsl.Vowel
            when (n) {
                "wet" -> b.copy(wet = v)
                "vowel" -> b.copy(vowel = v)
                else -> unknown("vowel", n)
            }
        },
        kotlin = { k, a -> k.vowel(wet = a["wet"], vowel = a["vowel"]) },
    ),
    StageDoor(
        stage = "delay",
        bare = KatalystStageDsl.Delay(),
        params = listOf(DoorParam("wet", 0.2), DoorParam("time", 0.5), DoorParam("feedback", 0.9)),
        set = { s, n, v ->
            val d = s as KatalystStageDsl.Delay
            when (n) {
                "wet" -> d.copy(wet = v)
                "time" -> d.copy(time = v)
                "feedback" -> d.copy(feedback = v)
                else -> unknown("delay", n)
            }
        },
        kotlin = { k, a -> k.delay(wet = a["wet"], time = a["time"], feedback = a["feedback"]) },
    ),
    StageDoor(
        stage = "reverb",
        bare = KatalystStageDsl.Reverb(),
        params = listOf(DoorParam("wet", 0.3), DoorParam("size", 8.0), DoorParam("lowpass", 6000.0)),
        set = { s, n, v ->
            val r = s as KatalystStageDsl.Reverb
            when (n) {
                "wet" -> r.copy(wet = v)
                "size" -> r.copy(size = v)
                "lowpass" -> r.copy(lowpass = v)
                else -> unknown("reverb", n)
            }
        },
        kotlin = { k, a -> k.reverb(wet = a["wet"], size = a["size"], lowpass = a["lowpass"]) },
    ),
    StageDoor(
        stage = "phaser",
        bare = KatalystStageDsl.Phaser(),
        params = listOf(
            DoorParam("wet", 0.5), DoorParam("rate", 0.3), DoorParam("center", 800.0), DoorParam("sweep", 1200.0),
        ),
        set = { s, n, v ->
            val p = s as KatalystStageDsl.Phaser
            when (n) {
                "wet" -> p.copy(wet = v)
                "rate" -> p.copy(rate = v)
                "center" -> p.copy(center = v)
                "sweep" -> p.copy(sweep = v)
                else -> unknown("phaser", n)
            }
        },
        kotlin = { k, a -> k.phaser(wet = a["wet"], rate = a["rate"], center = a["center"], sweep = a["sweep"]) },
    ),
    StageDoor(
        stage = "compressor",
        bare = KatalystStageDsl.Compressor(),
        params = listOf(
            DoorParam("threshold", -21.0), DoorParam("ratio", 3.0), DoorParam("knee", 5.0),
            DoorParam("attack", 0.005), DoorParam("release", 0.12),
        ),
        set = { s, n, v ->
            val c = s as KatalystStageDsl.Compressor
            when (n) {
                "threshold" -> c.copy(threshold = v)
                "ratio" -> c.copy(ratio = v)
                "knee" -> c.copy(knee = v)
                "attack" -> c.copy(attack = v)
                "release" -> c.copy(release = v)
                else -> unknown("compressor", n)
            }
        },
        kotlin = { k, a ->
            k.compressor(
                threshold = a["threshold"], ratio = a["ratio"], knee = a["knee"],
                attack = a["attack"], release = a["release"],
            )
        },
    ),
    StageDoor(
        stage = "duck",
        bare = KatalystStageDsl.Duck(),
        params = listOf(DoorParam("orbit", 2.0), DoorParam("depth", 0.8), DoorParam("attack", 0.05)),
        set = { s, n, v ->
            val d = s as KatalystStageDsl.Duck
            when (n) {
                "orbit" -> d.copy(orbit = v)
                "depth" -> d.copy(depth = v)
                "attack" -> d.copy(attack = v)
                else -> unknown("duck", n)
            }
        },
        kotlin = { k, a -> k.duck(orbit = a["orbit"], depth = a["depth"], attack = a["attack"]) },
    ),
)

/** A name knob (an index slot): the door, its parameter, a known name, and its catalogue. */
private class NameKnob(
    val stage: String,
    val param: String,
    val name: String,
    val indexOf: (String) -> Double,
    val stageWith: (IgnitorDsl) -> KatalystStageDsl,
    val kotlin: (KatalystBuilder, Any) -> KatalystBuilder,
)

private val nameKnobs: List<NameKnob> = listOf(
    NameKnob(
        stage = "body", param = "material", name = "glass", indexOf = BodyMaterials::indexOf,
        stageWith = { KatalystStageDsl.Body(material = it) },
        kotlin = { k, v -> k.body(material = v) },
    ),
    NameKnob(
        stage = "vowel", param = "vowel", name = "bass:a", indexOf = VowelBands::indexOf,
        stageWith = { KatalystStageDsl.Vowel(vowel = it) },
        kotlin = { k, v -> k.vowel(vowel = v) },
    ),
)

/**
 * Dual-surface rule: every stage and every knob of an orbit chain must be reachable from
 * KlangScript AND from Kotlin, with the same name meaning the same thing. This spec compares the
 * two doors stage for stage and knob for knob.
 *
 * Both doors are compared against the STAGE DATA CLASSES written out by hand (never against each
 * other alone), so a knob wired to the wrong field (`sweep` writing `center`) cannot look identical
 * on both sides. The Kotlin door is the same builder function the script door registers, called
 * from Kotlin; the door-shape rows call it for every stage of the family.
 */
class KlangScriptKatalystDoorParitySpec : StringSpec({

    fun ks(code: String): KatalystDsl {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")
        val result = engine.execute(code)
        result.shouldBeInstanceOf<NativeObjectValue<*>>()
        return result.value.shouldBeInstanceOf<KatalystDsl>()
    }

    fun c(value: Double): IgnitorDsl = IgnitorDsl.Constant(value)

    "Katalyst() is the empty chain, and Katalyst(k => ...) == Katalyst.build(k => ...)" {
        ks("Katalyst()") shouldBe KatalystDsl(emptyList())

        val code = "k => k.reverb(0.2).gain(1.4)"
        ks("Katalyst($code)") shouldBe ks("Katalyst.build($code)")
    }

    "Katalyst.classic() == KatalystDsl.classic, and classic() inside a chain appends its stages" {
        ks("Katalyst.classic()") shouldBe KatalystDsl.classic

        ks("Katalyst(k => k.classic().eq(e => e.band(freq = 160, q = 1.0, db = -3.0)))") shouldBe
                KatalystDsl(
                    KatalystDsl.classic.stages + KatalystStageDsl.Eq(
                        sections = listOf(
                            IgnitorDsl.EqSection.Bell(freq = c(160.0), q = c(1.0), db = c(-3.0))
                        )
                    )
                )
    }

    "k.classic().gain(0.8) is classic's unity slot followed by the author's own fader" {
        // The classic block has ended in a `Gain` at unity since 2026-09-19 (the signal-flow
        // plan's spot C), so an author appending their own fader gets TWO gain stages, in written
        // order, and the engine multiplies them. Nothing collapses them: the first is a SLOT a
        // pattern can move with `katp("gain.gain", x)` and the second is the author's constant.
        // What the two then do to the mix is `KatalystClassicGainStageSpec`'s.
        val chain = ks("Katalyst(k => k.classic().gain(0.8))")

        chain shouldBe KatalystDsl(KatalystDsl.classic.stages + KatalystStageDsl.Gain(c(0.8)))

        val faders = chain.stages.filterIsInstance<KatalystStageDsl.Gain>()

        faders.size shouldBe 2
        (faders[0].gain as IgnitorDsl.Param).name shouldBe "gain.gain"
        faders[1].gain shouldBe c(0.8)
    }

    "classic() lands its block at most ONCE per builder, on both doors" {
        // Decided with the maintainer, 2026-09-18. Its duplicated stages read the SAME slot
        // names, so a doubled classic ran reverb into reverb and two compressors in series off one
        // `reverb(0.3)`. The guard is a contiguous-sublist check in the builder, so it holds
        // however the author gets there, and it held when the block grew from seven stages to
        // eight (2026-09-19).
        ks("Katalyst(k => k.classic().classic())") shouldBe KatalystDsl.classic
        ks("Katalyst(k => k.classic().classic().classic())") shouldBe KatalystDsl.classic

        // ...and it is the BLOCK that is dropped, not the call: a classic after something else
        // still finds its own stages and adds nothing, and the stage in between stays where it is.
        val eq = KatalystStageDsl.Eq(
            sections = listOf(IgnitorDsl.EqSection.Bell(freq = c(300.0), q = c(0.8), db = c(2.0)))
        )

        ks("Katalyst(k => k.classic().eq(e => e.band(freq = 300, q = 0.8, db = 2.0)).classic())") shouldBe
                KatalystDsl(KatalystDsl.classic.stages + eq)

        // The Kotlin door is the same builder, so it says the same thing.
        KlangScriptKatalyst.build { it.classic().classic() } shouldBe KatalystDsl.classic
    }

    "an EXPLICIT stage written twice still stacks: that is an author asking for two" {
        // The other half of the idempotency rule, and the reason it is scoped to `classic()`: two
        // rooms in series is a legitimate mix, and nothing here may collapse it.
        ks("Katalyst(k => k.reverb(size = 2).reverb(size = 8))") shouldBe KatalystDsl.of(
            KatalystStageDsl.Reverb(size = c(2.0)),
            KatalystStageDsl.Reverb(size = c(8.0)),
        )

        ks("Katalyst(k => k.gain(2).gain(3))").stages.size shouldBe 2
    }

    "stages append in written order, script == Kotlin data classes" {
        ks("Katalyst(k => k.reverb(0.05, 9).gain(2.5).compressor())") shouldBe KatalystDsl.of(
            KatalystStageDsl.Reverb(wet = c(0.05), size = c(9.0)),
            KatalystStageDsl.Gain(gain = c(2.5)),
            KatalystStageDsl.Compressor(),
        )
    }

    "a bare stage from the script door is the bare data class, the eq and the gain included" {
        ks("Katalyst(k => k.body().vowel().delay().reverb().phaser().compressor().duck().eq().gain())") shouldBe
                KatalystDsl.of(
                    KatalystStageDsl.Body(),
                    KatalystStageDsl.Vowel(),
                    KatalystStageDsl.Delay(),
                    KatalystStageDsl.Reverb(),
                    KatalystStageDsl.Phaser(),
                    KatalystStageDsl.Compressor(),
                    KatalystStageDsl.Duck(),
                    KatalystStageDsl.Eq(),
                    KatalystStageDsl.Gain(),
                )
    }

    "the eq and the gain keep their shapes" {
        ks("Katalyst(k => k.eq(e => e.band(freq = 300, q = 0.8, db = 2.0).tap(850, 0.707, 1.7)).gain(1.45))") shouldBe
                KatalystDsl.of(
                    KatalystStageDsl.Eq(
                        sections = listOf(
                            IgnitorDsl.EqSection.Bell(freq = c(300.0), q = c(0.8), db = c(2.0)),
                            IgnitorDsl.EqSection.RawTap(freq = c(850.0), q = c(0.707), gain = c(1.7)),
                        )
                    ),
                    KatalystStageDsl.Gain(gain = c(1.45)),
                )
    }

    // ── The door shapes (phase 3 step 3d(ii), `docs/tasks/builtin-instruments.md` section 3b) ──
    //
    // Every row below LOOPS over [doors], the whole family, and compares THREE forms: the script
    // door, the Kotlin door (the same builder function called from Kotlin) and the stage data class
    // written out by hand. A door that stops handling one parameter goes red on that stage's name.

    "every door parameter reaches its own field, positionally, on both doors" {
        doors.forEach { door ->
            val args = door.params.joinToString(", ") { it.literal }
            val expected = door.expect(door.params)

            withClue("${door.stage}: script, positional") {
                ks("Katalyst(k => k.${door.stage}($args))") shouldBe KatalystDsl.of(expected)
            }
            withClue("${door.stage}: Kotlin") {
                KlangScriptKatalyst.build { door.kotlin(it, door.params.associate { p -> p.name to p.value }) } shouldBe
                        KatalystDsl.of(expected)
            }
        }
    }

    "an omitted door parameter is exactly what the bare stage carries, on both doors" {
        // The identity rule of step 3d(ii): leaving a parameter out must mean what leaving the
        // builder knob out meant before, which is the bare data class's own value (a touched
        // constant, or the "never set" marker on a name knob). Each parameter is left out ONCE,
        // with every other one written, so a door that invents its own default for any single
        // parameter goes red on that parameter's name.
        doors.forEach { door ->
            door.params.forEach { omitted ->
                val written = door.params - omitted
                val args = written.joinToString(", ") { "${it.name} = ${it.literal}" }
                val expected = door.expect(written)

                withClue("${door.stage} without ${omitted.name}: script") {
                    ks("Katalyst(k => k.${door.stage}($args))") shouldBe KatalystDsl.of(expected)
                }
                withClue("${door.stage} without ${omitted.name}: Kotlin") {
                    KlangScriptKatalyst.build { door.kotlin(it, written.associate { p -> p.name to p.value }) } shouldBe
                            KatalystDsl.of(expected)
                }
            }

            withClue("${door.stage}: nothing written is the bare stage") {
                ks("Katalyst(k => k.${door.stage}())") shouldBe KatalystDsl.of(door.bare)
                KlangScriptKatalyst.build { door.kotlin(it, emptyMap()) } shouldBe KatalystDsl.of(door.bare)
            }
        }
    }

    "a Katalyst.param slot is accepted on every door parameter, on both doors" {
        doors.forEach { door ->
            door.params.forEach { param ->
                val slot = IgnitorDsl.Param("s", param.value)
                val expected = door.set(door.bare, param.name, slot)

                withClue("${door.stage}(${param.name} = Katalyst.param(...)): script") {
                    ks("""Katalyst(k => k.${door.stage}(${param.name} = Katalyst.param("s", ${param.literal})))""") shouldBe
                            KatalystDsl.of(expected)
                }
                withClue("${door.stage}(${param.name} = Katalyst.param(...)): Kotlin") {
                    KlangScriptKatalyst.build {
                        door.kotlin(it, mapOf(param.name to KlangScriptKatalyst.param("s", param.value)))
                    } shouldBe KatalystDsl.of(expected)
                }
            }
        }
    }

    "the builder knobs reach their own field, and only the builder carries them" {
        listOf(
            "Katalyst(k => k.body(configure = b => b.floor(0.3)))" to KatalystStageDsl.Body(floor = c(0.3)),
            "Katalyst(k => k.vowel(configure = v => v.floor(0.1)))" to KatalystStageDsl.Vowel(floor = c(0.1)),
            "Katalyst(k => k.delay(configure = d => d.cap(3.0)))" to KatalystStageDsl.Delay(cap = c(3.0)),
            "Katalyst(k => k.phaser(configure = p => p.floor(0.2)))" to KatalystStageDsl.Phaser(floor = c(0.2)),
            // A trailing lambda floats past the unset door parameters to `configure`.
            "Katalyst(k => k.phaser(0.5, p => p.floor(0.2)))" to KatalystStageDsl.Phaser(wet = c(0.5), floor = c(0.2)),
        ).forEach { (script, stage) ->
            withClue(script) { ks(script) shouldBe KatalystDsl.of(stage) }
        }

        KlangScriptKatalyst.build { it.delay(0.2, configure = { d -> d.cap(3.0) }) } shouldBe
                KatalystDsl.of(KatalystStageDsl.Delay(wet = c(0.2), cap = c(3.0)))
    }

    "the name knobs take a NAME through the catalogue, a bare number, or a slot, on both doors" {
        // Katalyst step 5a-2 (maintainer, 2026-09-18): the names are INDEX slots. The knob moved
        // from the builder to the door in 3d(ii), and its reach did not shrink: a name still
        // resolves through the one shared `indexOf`, and a number or a `Katalyst.param` is the
        // index itself, which is what lets a chain move its material with `katp`.
        nameKnobs.forEach { knob ->
            withClue("${knob.stage}: a name") {
                ks("""Katalyst(k => k.${knob.stage}(${knob.param} = "${knob.name}"))""") shouldBe
                        KatalystDsl.of(knob.stageWith(c(knob.indexOf(knob.name))))
                KlangScriptKatalyst.build { knob.kotlin(it, knob.name) } shouldBe
                        KatalystDsl.of(knob.stageWith(c(knob.indexOf(knob.name))))
            }
            withClue("${knob.stage}: a bare number") {
                ks("""Katalyst(k => k.${knob.stage}(${knob.param} = 2))""") shouldBe KatalystDsl.of(knob.stageWith(c(2.0)))
                KlangScriptKatalyst.build { knob.kotlin(it, 2.0) } shouldBe KatalystDsl.of(knob.stageWith(c(2.0)))
            }
            withClue("${knob.stage}: a slot") {
                ks("""Katalyst(k => k.${knob.stage}(${knob.param} = Katalyst.param("idx", 3)))""") shouldBe
                        KatalystDsl.of(knob.stageWith(IgnitorDsl.Param("idx", 3.0)))
                KlangScriptKatalyst.build { knob.kotlin(it, KlangScriptKatalyst.param("idx", 3.0)) } shouldBe
                        KatalystDsl.of(knob.stageWith(IgnitorDsl.Param("idx", 3.0)))
            }
            withClue("${knob.stage}: an unknown name is index 0, the stage off, never a throw") {
                ks("""Katalyst(k => k.${knob.stage}(${knob.param} = "unobtainium"))""") shouldBe
                        KatalystDsl.of(knob.stageWith(c(0.0)))
            }
        }

        // And `none` is the same index, spelled by an author who means it.
        ks("""Katalyst(k => k.body(material = "none"))""") shouldBe ks("""Katalyst(k => k.body(material = "unobtainium"))""")
    }

    "the retired forms fail loudly instead of meaning something else" {
        // Nothing here may be silently reinterpreted. A positional NAME now lands on `wet`, which
        // takes a number or a sound, so it is a type error; an old stage lambda either lands on a
        // flat door's first parameter (a type error) or floats to `configure`, where the knob it
        // calls no longer exists.
        val notANumber = "expected a sound or a number"
        listOf(
            """Katalyst(k => k.body("wood"))""" to notANumber,
            """Katalyst(k => k.vowel("a"))""" to notANumber,
            "Katalyst(k => k.body(b => b.material(2)))" to "has no method 'material'",
            "Katalyst(k => k.vowel(v => v.vowel(2)))" to "has no method 'vowel'",
            "Katalyst(k => k.body(b => b.wet(0.3)))" to "has no method 'wet'",
            "Katalyst(k => k.delay(d => d.wet(0.2)))" to "has no method 'wet'",
            "Katalyst(k => k.phaser(p => p.rate(0.3)))" to "has no method 'rate'",
            "Katalyst(k => k.reverb(r => r.wet(0.2)))" to notANumber,
            "Katalyst(k => k.compressor(c => c.ratio(3)))" to notANumber,
            "Katalyst(k => k.duck(d => d.orbit(1)))" to notANumber,
        ).forEach { (script, reason) ->
            withClue(script) { shouldThrowAny { ks(script) }.message shouldContain reason }
        }
    }

    "a knob takes an Osc.param slot as readily as a number" {
        ks("""Katalyst(k => k.reverb(wet = Osc.param("room", 0.2)))""") shouldBe
                KatalystDsl.of(KatalystStageDsl.Reverb(wet = IgnitorDsl.Param("room", 0.2)))
    }

    "Katalyst.param is the chain's own slot door, on both doors, with the description" {
        ks("""Katalyst(k => k.reverb(size = Katalyst.param("room", 5.0)))""") shouldBe
                KatalystDsl.of(KatalystStageDsl.Reverb(size = IgnitorDsl.Param("room", 5.0)))

        ks("""Katalyst(k => k.reverb(size = Katalyst.param("room", 5.0, "the tail")))""") shouldBe
                KlangScriptKatalyst.build {
                    it.reverb(size = KlangScriptKatalyst.param("room", 5.0, "the tail"))
                }

        // Same node type as `Osc.param`, and that is the point: one slot vocabulary, two
        // namespaces that never cross (`oscp` fills the voice's, `katp` the orbit's).
        KlangScriptKatalyst.param("room", 5.0) shouldBe KlangScriptOsc.param("room", 5.0)
    }

    "the Kotlin door takes the same lambda" {
        ks("Katalyst(k => k.reverb(0.2).gain(1.4))") shouldBe
                KlangScriptKatalyst.build { it.reverb(0.2).gain(1.4) }
    }

    "Katalyst is still a value: stored, then called" {
        ks("let K = Katalyst\nK(k => k.gain(2))") shouldBe KatalystDsl.of(KatalystStageDsl.Gain(c(2.0)))
    }

    "a lambda that returns nothing is a script-level type error naming the door" {
        val err = shouldThrow<KlangScriptTypeError> { ks("Katalyst(k => { k.gain(2) })") }
        err.message shouldBe
                "the configure lambda of Katalyst returned nothing; return the builder it received (`x => x.analog(3)`)"
    }

    "a stage lambda that returns nothing names its stage" {
        val err = shouldThrow<KlangScriptTypeError> { ks("Katalyst(k => k.delay(configure = d => { d.cap(2) }))") }
        err.message shouldBe
                "the configure lambda of Katalyst delay returned nothing; return the builder it received (`x => x.analog(3)`)"
    }
})
