/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.soundName

/** `sine` sampled at the four quarters of a cycle: what a raw Double knob carries. */
private val SINE = listOf(0.5, 1.0, 0.5, 0.0)

/**
 * The one home of the sprudel door CALLING FORMS (test consolidation commit 2, 2026-09-27).
 *
 * Each knob of the voice doors listed in [knobs] is one entry: level and routing (`gain`, `pregain`,
 * `pan`, `velocity`, `orbit`, `cylinder`, `density`, `accelerate`), the oscillator knobs (`analog`,
 * `duty`, `onepole`, `ignitorParam`, `ignp`, `sndPluck`, `sndSuperPluck`), the orbit slot setter (`katalystParam`,
 * `katp`), `adsr` and `adsrOn`, `unison`,
 * the bus doors (`compressor`, `duck`, `reverb`, `delay`, `phaser`, `body`, `vowel`), `tremolo`,
 * `distort`, `crush`, `coarse`, the four filters, `fm`, `vibrato` and `penv`, and the aliases' heads.
 * Each row runs the entries through the calling forms the module supports, and asserts the VALUE the
 * form wrote into the knob's field (and, where the door also writes one, its slot), never just "some
 * events came out":
 *
 * | form            | Kotlin                              | KlangScript                                  |
 * |-----------------|-------------------------------------|----------------------------------------------|
 * | pattern method  | `seq("a b").notch(attack = c)`      | `seq("a b").notch(attack = "...")`           |
 * | string receiver | `"a b".notch(attack = c)`           | `"a b".notch(attack = "...")`                |
 * | standalone      | `seq("a b").apply(notch(...))`      | `seq("a b").apply(notch(...))`               |
 * | chained mapper  | `apply(pan(0.25).notch(...))`       | `apply(pan(0.25).notch(...))`                |
 *
 * The chained form starts from ANOTHER door and runs the knob's door twice, a decoy value first:
 * the prefix's own value must survive, and the later call must win.
 *
 * The rows: every knob in every form (the value); every head's bare call (the receiver's numbers
 * reinterpreted); a continuous pattern per knob (sampled per event); every compound door with all
 * its arguments positional, in every form (declaration order).
 *
 * Not in the table (their forms live in their own specs, or they have no knob shape): the sample doors
 * (`begin`, `end`, `speed`, `cut`, `unit`, `loop*`, `slice`, `splice`), `sound`/`s`/`bank`, the curve
 * doors (`adsrCurves`, `lpfCurves`, `hpfCurves`, `bpfCurves`, `notchCurves`, `penvCurves`), `adsrOff`,
 * `cull`/`noCull`, `katalyst`, the `snd*` oscillators other than the two plucks, and the aliases'
 * named tails.
 *
 * What this spec does not own: the mapper argument, the bare accessor read, the gap, the tail-only
 * guard and the per-slot control pattern on both doors (`LangFieldAccessorsSpec`); the compound-door
 * fill rule (`LangKatalystParamSpec`); what each door does beyond writing its knob (the door's own
 * spec).
 */
class LangDoorFormsSpec : StringSpec({

    class Knob(
        /** The knob, as its accessor reads it: `notch.attack`, `gain`. */
        val name: String,
        /** The KlangScript call with `%s` where the control goes: `notch(attack = %s)`. */
        val call: String,
        /** The control, two steps of mini-notation. */
        val ctrl: String,
        /** What the two events carry in [field] (and [slot]) after the call. */
        val expected: List<Any?>,
        val field: (SprudelVoiceData) -> Any?,
        /** The slot the door writes next to [field], if it writes one. */
        val slot: ((SprudelVoiceData) -> Double?)?,
        /** What [slot] carries, where it is not [expected] (a name knob's slot is the name's catalogue index). */
        val slotExpected: List<Any?>,
        /** True for the door's head: a bare call reinterprets the receiver's numbers into it. */
        val head: Boolean,
        /** What `sine` sampled at the four quarters lands as in [field], or null where it does not apply. */
        val continuous: List<Any?>?,
        val pattern: (SprudelPattern, PatternLike?) -> SprudelPattern,
        val string: (String, PatternLike?) -> SprudelPattern,
        val mapper: (PatternLike?) -> PatternMapperFn,
        /** Null where the door has no chained overload (the `o` alias is a plain value). */
        val chained: ((PatternMapperFn, PatternLike?) -> PatternMapperFn)?,
    )

    fun katSlot(key: String): (SprudelVoiceData) -> Double? = { it.katalystParams?.get(key) }
    fun ignitorSlot(key: String): (SprudelVoiceData) -> Double? = { it.ignitorParams?.get(key) }

    fun k(
        name: String,
        call: String,
        field: (SprudelVoiceData) -> Any?,
        pattern: (SprudelPattern, PatternLike?) -> SprudelPattern,
        string: (String, PatternLike?) -> SprudelPattern,
        mapper: (PatternLike?) -> PatternMapperFn,
        chained: ((PatternMapperFn, PatternLike?) -> PatternMapperFn)?,
        ctrl: String = "0.5 1",
        expected: List<Any?> = listOf(0.5, 1.0),
        slot: ((SprudelVoiceData) -> Double?)? = null,
        slotExpected: List<Any?> = expected,
        head: Boolean = false,
        continuous: List<Any?>? = SINE,
    ) = Knob(name, call, ctrl, expected, field, slot, slotExpected, head, continuous, pattern, string, mapper, chained)

    val knobs = listOf(
        // -- level, routing, voice ------------------------------------------------------------------------------------
        k("gain", "gain(%s)", { it.gain }, { p, c -> p.gain(c) }, { s, c -> s.gain(c) }, { c -> gain(c) }, { m, c -> m.gain(c) }, head = true),
        k("pregain", "pregain(%s)", ignitorSlot("pregain"), { p, c -> p.pregain(c) }, { s, c -> s.pregain(c) }, { c -> pregain(c) }, { m, c -> m.pregain(c) }, head = true),
        k("pan", "pan(%s)", { it.pan }, { p, c -> p.pan(c) }, { s, c -> s.pan(c) }, { c -> pan(c) }, { m, c -> m.pan(c) },
            ctrl = "-0.5 1", expected = listOf(-0.5, 1.0), head = true),
        k("velocity", "velocity(%s)", { it.velocity }, { p, c -> p.velocity(c) }, { s, c -> s.velocity(c) }, { c -> velocity(c) }, { m, c -> m.velocity(c) }, head = true),
        k("vel", "vel(%s)", { it.velocity }, { p, c -> p.vel(c) }, { s, c -> s.vel(c) }, { c -> vel(c) }, { m, c -> m.vel(c) }, head = true),
        k("orbit", "orbit(%s)", { it.cylinder }, { p, c -> p.orbit(c) }, { s, c -> s.orbit(c) }, { c -> orbit(c) }, { m, c -> m.orbit(c) },
            ctrl = "1 2", expected = listOf(1, 2), head = true, continuous = listOf(0, 1, 0, 0)),
        k("o", "o(%s)", { it.cylinder }, { p, c -> p.o(c) }, { s, c -> s.o(c) }, { c -> o(c) }, null,
            ctrl = "1 2", expected = listOf(1, 2), head = true, continuous = listOf(0, 1, 0, 0)),
        k("cylinder", "cylinder(%s)", { it.cylinder }, { p, c -> p.cylinder(c) }, { s, c -> s.cylinder(c) }, { c -> cylinder(c) }, { m, c -> m.cylinder(c) },
            ctrl = "1 2", expected = listOf(1, 2), head = true, continuous = listOf(0, 1, 0, 0)),
        k("density", "density(%s)", ignitorSlot("density"), { p, c -> p.density(c) }, { s, c -> s.density(c) }, { c -> density(c) }, { m, c -> m.density(c) }, head = true),
        k("d", "d(%s)", ignitorSlot("density"), { p, c -> p.d(c) }, { s, c -> s.d(c) }, { c -> d(c) }, { m, c -> m.d(c) }, head = true),
        k("accelerate", "accelerate(%s)", { it.accelerate }, { p, c -> p.accelerate(c) }, { s, c -> s.accelerate(c) }, { c -> accelerate(c) }, { m, c -> m.accelerate(c) },
            ctrl = "-0.5 0.75", expected = listOf(-0.5, 0.75), head = true),
        k("ignitorParam", "ignitorParam(\"mykey\", %s)", ignitorSlot("mykey"), { p, c -> p.ignitorParam("mykey", c!!) }, { s, c -> s.ignitorParam("mykey", c!!) }, { c -> ignitorParam("mykey", c!!) }, { m, c -> m.ignitorParam("mykey", c!!) }),
        k("ignp", "ignp(\"mykey\", %s)", ignitorSlot("mykey"), { p, c -> p.ignp("mykey", c!!) }, { s, c -> s.ignp("mykey", c!!) }, { c -> ignp("mykey", c!!) }, { m, c -> m.ignp("mykey", c!!) }),
        k("katalystParam", "katalystParam(\"mykey\", %s)", katSlot("mykey"), { p, c -> p.katalystParam("mykey", c!!) }, { s, c -> s.katalystParam("mykey", c!!) }, { c -> katalystParam("mykey", c!!) }, { m, c -> m.katalystParam("mykey", c!!) }),
        k("katp", "katp(\"mykey\", %s)", katSlot("mykey"), { p, c -> p.katp("mykey", c!!) }, { s, c -> s.katp("mykey", c!!) }, { c -> katp("mykey", c!!) }, { m, c -> m.katp("mykey", c!!) }),
        k("analog", "analog(%s)", ignitorSlot("analog"), { p, c -> p.analog(c) }, { s, c -> s.analog(c) }, { c -> analog(c) }, { m, c -> m.analog(c) }, head = true),
        k("duty", "duty(%s)", ignitorSlot("duty"), { p, c -> p.duty(c) }, { s, c -> s.duty(c) }, { c -> duty(c) }, { m, c -> m.duty(c) }, head = true),
        k("onepole", "onepole(%s)", ignitorSlot("onepole"), { p, c -> p.onepole(c) }, { s, c -> s.onepole(c) }, { c -> onepole(c) }, { m, c -> m.onepole(c) }, head = true),

        // -- adsr -----------------------------------------------------------------------------------------------------
        k("adsr.attack", "adsr(attack = %s)", { it.attack }, { p, c -> p.adsr(attack = c) }, { s, c -> s.adsr(attack = c) }, { c -> adsr(attack = c) }, { m, c -> m.adsr(attack = c) }),
        k("adsr.decay", "adsr(decay = %s)", { it.decay }, { p, c -> p.adsr(decay = c) }, { s, c -> s.adsr(decay = c) }, { c -> adsr(decay = c) }, { m, c -> m.adsr(decay = c) }),
        k("adsr.sustain", "adsr(sustain = %s)", { it.sustain }, { p, c -> p.adsr(sustain = c) }, { s, c -> s.adsr(sustain = c) }, { c -> adsr(sustain = c) }, { m, c -> m.adsr(sustain = c) }),
        k("adsr.release", "adsr(release = %s)", { it.release }, { p, c -> p.adsr(release = c) }, { s, c -> s.adsr(release = c) }, { c -> adsr(release = c) }, { m, c -> m.adsr(release = c) }),
        k("adsrOn", "adsrOn(%s)", { it.adsrOn }, { p, c -> p.adsrOn(c!!) }, { s, c -> s.adsrOn(c!!) }, { c -> adsrOn(c!!) }, { m, c -> m.adsrOn(c!!) },
            ctrl = "1 0", expected = listOf(true, false), continuous = null),

        // -- unison ---------------------------------------------------------------------------------------------------
        k("unison.voices", "unison(%s)", ignitorSlot("voices"), { p, c -> p.unison(c) }, { s, c -> s.unison(c) }, { c -> unison(c) }, { m, c -> m.unison(c) },
            ctrl = "3 5", expected = listOf(3.0, 5.0), head = true),
        k("uni", "uni(%s)", ignitorSlot("voices"), { p, c -> p.uni(c) }, { s, c -> s.uni(c) }, { c -> uni(c) }, { m, c -> m.uni(c) },
            ctrl = "3 5", expected = listOf(3.0, 5.0), head = true),
        k("unison.spread", "unison(spread = %s)", ignitorSlot("spread"), { p, c -> p.unison(spread = c) }, { s, c -> s.unison(spread = c) }, { c -> unison(spread = c) }, { m, c -> m.unison(spread = c) }),
        k("unison.pan", "unison(pan = %s)", ignitorSlot("panSpread"), { p, c -> p.unison(pan = c) }, { s, c -> s.unison(pan = c) }, { c -> unison(pan = c) }, { m, c -> m.unison(pan = c) }),

        // -- compressor and duck (Katalyst slots) ----------------------------------------------------------------------
        k("compressor.threshold", "compressor(%s)", katSlot("compressor.threshold"), { p, c -> p.compressor(c) }, { s, c -> s.compressor(c) }, { c -> compressor(c) }, { m, c -> m.compressor(c) },
            ctrl = "-10 -30", expected = listOf(-10.0, -30.0), head = true),
        k("comp", "comp(%s)", katSlot("compressor.threshold"), { p, c -> p.comp(c) }, { s, c -> s.comp(c) }, { c -> comp(c) }, { m, c -> m.comp(c) },
            ctrl = "-10 -30", expected = listOf(-10.0, -30.0), head = true),
        k("compressor.ratio", "compressor(ratio = %s)", katSlot("compressor.ratio"), { p, c -> p.compressor(ratio = c) }, { s, c -> s.compressor(ratio = c) }, { c -> compressor(ratio = c) }, { m, c -> m.compressor(ratio = c) }),
        k("compressor.knee", "compressor(knee = %s)", katSlot("compressor.knee"), { p, c -> p.compressor(knee = c) }, { s, c -> s.compressor(knee = c) }, { c -> compressor(knee = c) }, { m, c -> m.compressor(knee = c) }),
        k("compressor.attack", "compressor(attack = %s)", katSlot("compressor.attack"), { p, c -> p.compressor(attack = c) }, { s, c -> s.compressor(attack = c) }, { c -> compressor(attack = c) }, { m, c -> m.compressor(attack = c) }),
        k("compressor.release", "compressor(release = %s)", katSlot("compressor.release"), { p, c -> p.compressor(release = c) }, { s, c -> s.compressor(release = c) }, { c -> compressor(release = c) }, { m, c -> m.compressor(release = c) }),
        k("duck.orbit", "duck(%s)", katSlot("duck.orbit"), { p, c -> p.duck(c) }, { s, c -> s.duck(c) }, { c -> duck(c) }, { m, c -> m.duck(c) },
            ctrl = "1 2", expected = listOf(1.0, 2.0), head = true, continuous = listOf(0.0, 1.0, 0.0, 0.0)),
        k("duck.depth", "duck(depth = %s)", katSlot("duck.depth"), { p, c -> p.duck(depth = c) }, { s, c -> s.duck(depth = c) }, { c -> duck(depth = c) }, { m, c -> m.duck(depth = c) }),
        k("duck.attack", "duck(attack = %s)", katSlot("duck.attack"), { p, c -> p.duck(attack = c) }, { s, c -> s.duck(attack = c) }, { c -> duck(attack = c) }, { m, c -> m.duck(attack = c) }),

        // -- reverb and delay (Katalyst slots) -------------------------------------------------------------------------
        k("reverb.wet", "reverb(%s)", katSlot("reverb.wet"), { p, c -> p.reverb(c) }, { s, c -> s.reverb(c) }, { c -> reverb(c) }, { m, c -> m.reverb(c) }, head = true),
        k("reverb.size", "reverb(size = %s)", katSlot("reverb.size"), { p, c -> p.reverb(size = c) }, { s, c -> s.reverb(size = c) }, { c -> reverb(size = c) }, { m, c -> m.reverb(size = c) }),
        k("reverb.lowpass", "reverb(lowpass = %s)", katSlot("reverb.lowpass"), { p, c -> p.reverb(lowpass = c) }, { s, c -> s.reverb(lowpass = c) }, { c -> reverb(lowpass = c) }, { m, c -> m.reverb(lowpass = c) }),
        k("delay.wet", "delay(%s)", katSlot("delay.wet"), { p, c -> p.delay(c) }, { s, c -> s.delay(c) }, { c -> delay(c) }, { m, c -> m.delay(c) }, head = true),
        k("delay.time", "delay(time = %s)", katSlot("delay.time"), { p, c -> p.delay(time = c) }, { s, c -> s.delay(time = c) }, { c -> delay(time = c) }, { m, c -> m.delay(time = c) }),
        k("delay.feedback", "delay(feedback = %s)", katSlot("delay.feedback"), { p, c -> p.delay(feedback = c) }, { s, c -> s.delay(feedback = c) }, { c -> delay(feedback = c) }, { m, c -> m.delay(feedback = c) }),
        k("delay.cap", "delay(cap = %s)", katSlot("delay.cap"), { p, c -> p.delay(cap = c) }, { s, c -> s.delay(cap = c) }, { c -> delay(cap = c) }, { m, c -> m.delay(cap = c) },
            ctrl = "0.5 3", expected = listOf(0.5, 3.0)),

        // -- phaser, body, vowel (field AND Katalyst slot) --------------------------------------------------------------
        k("phaser.wet", "phaser(%s)", { it.phaserDepth }, { p, c -> p.phaser(c) }, { s, c -> s.phaser(c) }, { c -> phaser(c) }, { m, c -> m.phaser(c) },
            slot = katSlot("phaser.wet"), head = true),
        k("phaser.rate", "phaser(rate = %s)", { it.phaserRate }, { p, c -> p.phaser(rate = c) }, { s, c -> s.phaser(rate = c) }, { c -> phaser(rate = c) }, { m, c -> m.phaser(rate = c) },
            slot = katSlot("phaser.rate")),
        k("phaser.center", "phaser(center = %s)", { it.phaserCenter }, { p, c -> p.phaser(center = c) }, { s, c -> s.phaser(center = c) }, { c -> phaser(center = c) }, { m, c -> m.phaser(center = c) },
            slot = katSlot("phaser.center")),
        k("phaser.sweep", "phaser(sweep = %s)", { it.phaserSweep }, { p, c -> p.phaser(sweep = c) }, { s, c -> s.phaser(sweep = c) }, { c -> phaser(sweep = c) }, { m, c -> m.phaser(sweep = c) },
            slot = katSlot("phaser.sweep")),
        k("phaser.floor", "phaser(floor = %s)", { it.phaserFloor }, { p, c -> p.phaser(floor = c) }, { s, c -> s.phaser(floor = c) }, { c -> phaser(floor = c) }, { m, c -> m.phaser(floor = c) },
            slot = katSlot("phaser.floor")),
        k("body.wet", "body(%s)", { it.bodyMix }, { p, c -> p.body(c) }, { s, c -> s.body(c) }, { c -> body(c) }, { m, c -> m.body(c) },
            slot = katSlot("body.wet"), head = true),
        k("body.material", "body(material = %s)", { it.body }, { p, c -> p.body(material = c) }, { s, c -> s.body(material = c) }, { c -> body(material = c) }, { m, c -> m.body(material = c) },
            ctrl = "wood glass", expected = listOf("wood", "glass"), continuous = null,
            slot = katSlot("body.material"), slotExpected = listOf(BodyMaterials.indexOf("wood"), BodyMaterials.indexOf("glass"))),
        k("body.floor", "body(floor = %s)", { it.bodyFloor }, { p, c -> p.body(floor = c) }, { s, c -> s.body(floor = c) }, { c -> body(floor = c) }, { m, c -> m.body(floor = c) },
            slot = katSlot("body.floor")),
        k("vowel.wet", "vowel(%s)", { it.vowelMix }, { p, c -> p.vowel(c) }, { s, c -> s.vowel(c) }, { c -> vowel(c) }, { m, c -> m.vowel(c) },
            slot = katSlot("vowel.wet"), head = true),
        k("vowel.vowel", "vowel(vowel = %s)", { it.vowel }, { p, c -> p.vowel(vowel = c) }, { s, c -> s.vowel(vowel = c) }, { c -> vowel(vowel = c) }, { m, c -> m.vowel(vowel = c) },
            ctrl = "a e", expected = listOf("a", "e"), continuous = null,
            slot = katSlot("vowel.vowel"), slotExpected = listOf(VowelBands.indexOf("a"), VowelBands.indexOf("e"))),
        k("vowel.floor", "vowel(floor = %s)", { it.vowelFloor }, { p, c -> p.vowel(floor = c) }, { s, c -> s.vowel(floor = c) }, { c -> vowel(floor = c) }, { m, c -> m.vowel(floor = c) },
            slot = katSlot("vowel.floor")),

        // -- tremolo ----------------------------------------------------------------------------------------------------
        k("tremolo.depth", "tremolo(%s)", { it.tremoloDepth }, { p, c -> p.tremolo(c) }, { s, c -> s.tremolo(c) }, { c -> tremolo(c) }, { m, c -> m.tremolo(c) }, head = true),
        k("tremolo.rate", "tremolo(rate = %s)", { it.tremoloRate }, { p, c -> p.tremolo(rate = c) }, { s, c -> s.tremolo(rate = c) }, { c -> tremolo(rate = c) }, { m, c -> m.tremolo(rate = c) }),
        k("tremolo.shape", "tremolo(shape = %s)", { it.tremoloShape }, { p, c -> p.tremolo(shape = c) }, { s, c -> s.tremolo(shape = c) }, { c -> tremolo(shape = c) }, { m, c -> m.tremolo(shape = c) },
            ctrl = "sine square", expected = listOf("sine", "square"), continuous = null),

        // -- distort, crush, coarse -------------------------------------------------------------------------------------
        k("distort.amount", "distort(%s)", { it.distort }, { p, c -> p.distort(c) }, { s, c -> s.distort(c) }, { c -> distort(c) }, { m, c -> m.distort(c) },
            ctrl = "0.5 10", expected = listOf(0.5, 10.0), head = true),
        k("distort.shape", "distort(shape = %s)", { it.distortShape }, { p, c -> p.distort(shape = c) }, { s, c -> s.distort(shape = c) }, { c -> distort(shape = c) }, { m, c -> m.distort(shape = c) },
            ctrl = "soft hard", expected = listOf("soft", "hard"), continuous = null),
        k("distort.oversample", "distort(oversample = %s)", { it.distortOversample }, { p, c -> p.distort(oversample = c) }, { s, c -> s.distort(oversample = c) }, { c -> distort(oversample = c) }, { m, c -> m.distort(oversample = c) },
            ctrl = "2 4", expected = listOf(2, 4), continuous = null),
        k("crush.bits", "crush(%s)", { it.crush }, { p, c -> p.crush(c) }, { s, c -> s.crush(c) }, { c -> crush(c) }, { m, c -> m.crush(c) }, head = true),
        k("crush.oversample", "crush(oversample = %s)", { it.crushOversample }, { p, c -> p.crush(oversample = c) }, { s, c -> s.crush(oversample = c) }, { c -> crush(oversample = c) }, { m, c -> m.crush(oversample = c) },
            ctrl = "2 4", expected = listOf(2, 4), continuous = null),
        k("coarse.factor", "coarse(%s)", { it.coarse }, { p, c -> p.coarse(c) }, { s, c -> s.coarse(c) }, { c -> coarse(c) }, { m, c -> m.coarse(c) }, head = true),
        k("coarse.oversample", "coarse(oversample = %s)", { it.coarseOversample }, { p, c -> p.coarse(oversample = c) }, { s, c -> s.coarse(oversample = c) }, { c -> coarse(oversample = c) }, { m, c -> m.coarse(oversample = c) },
            ctrl = "2 4", expected = listOf(2, 4), continuous = null),

        // -- lpf and its alias ------------------------------------------------------------------------------------------
        k("lpf.freq", "lpf(%s)", { it.cutoff }, { p, c -> p.lpf(c) }, { s, c -> s.lpf(c) }, { c -> lpf(c) }, { m, c -> m.lpf(c) }, head = true),
        k("lowpass", "lowpass(%s)", { it.cutoff }, { p, c -> p.lowpass(c) }, { s, c -> s.lowpass(c) }, { c -> lowpass(c) }, { m, c -> m.lowpass(c) }, head = true),
        k("lpf.q", "lpf(q = %s)", { it.resonance }, { p, c -> p.lpf(q = c) }, { s, c -> s.lpf(q = c) }, { c -> lpf(q = c) }, { m, c -> m.lpf(q = c) }),
        k("lpf.passes", "lpf(passes = %s)", { it.lpPasses }, { p, c -> p.lpf(passes = c) }, { s, c -> s.lpf(passes = c) }, { c -> lpf(passes = c) }, { m, c -> m.lpf(passes = c) },
            ctrl = "1 2", expected = listOf(1.0, 2.0)),
        k("lpf.env", "lpf(env = %s)", { it.lpenv }, { p, c -> p.lpf(env = c) }, { s, c -> s.lpf(env = c) }, { c -> lpf(env = c) }, { m, c -> m.lpf(env = c) }),
        k("lpf.attack", "lpf(attack = %s)", { it.lpattack }, { p, c -> p.lpf(attack = c) }, { s, c -> s.lpf(attack = c) }, { c -> lpf(attack = c) }, { m, c -> m.lpf(attack = c) }),
        k("lpf.decay", "lpf(decay = %s)", { it.lpdecay }, { p, c -> p.lpf(decay = c) }, { s, c -> s.lpf(decay = c) }, { c -> lpf(decay = c) }, { m, c -> m.lpf(decay = c) }),
        k("lpf.sustain", "lpf(sustain = %s)", { it.lpsustain }, { p, c -> p.lpf(sustain = c) }, { s, c -> s.lpf(sustain = c) }, { c -> lpf(sustain = c) }, { m, c -> m.lpf(sustain = c) }),
        k("lpf.release", "lpf(release = %s)", { it.lprelease }, { p, c -> p.lpf(release = c) }, { s, c -> s.lpf(release = c) }, { c -> lpf(release = c) }, { m, c -> m.lpf(release = c) }),

        // -- hpf and its alias ------------------------------------------------------------------------------------------
        k("hpf.freq", "hpf(%s)", { it.hcutoff }, { p, c -> p.hpf(c) }, { s, c -> s.hpf(c) }, { c -> hpf(c) }, { m, c -> m.hpf(c) }, head = true),
        k("highpass", "highpass(%s)", { it.hcutoff }, { p, c -> p.highpass(c) }, { s, c -> s.highpass(c) }, { c -> highpass(c) }, { m, c -> m.highpass(c) }, head = true),
        k("hpf.q", "hpf(q = %s)", { it.hresonance }, { p, c -> p.hpf(q = c) }, { s, c -> s.hpf(q = c) }, { c -> hpf(q = c) }, { m, c -> m.hpf(q = c) }),
        k("hpf.passes", "hpf(passes = %s)", { it.hpPasses }, { p, c -> p.hpf(passes = c) }, { s, c -> s.hpf(passes = c) }, { c -> hpf(passes = c) }, { m, c -> m.hpf(passes = c) },
            ctrl = "1 2", expected = listOf(1.0, 2.0)),
        k("hpf.env", "hpf(env = %s)", { it.hpenv }, { p, c -> p.hpf(env = c) }, { s, c -> s.hpf(env = c) }, { c -> hpf(env = c) }, { m, c -> m.hpf(env = c) }),
        k("hpf.attack", "hpf(attack = %s)", { it.hpattack }, { p, c -> p.hpf(attack = c) }, { s, c -> s.hpf(attack = c) }, { c -> hpf(attack = c) }, { m, c -> m.hpf(attack = c) }),
        k("hpf.decay", "hpf(decay = %s)", { it.hpdecay }, { p, c -> p.hpf(decay = c) }, { s, c -> s.hpf(decay = c) }, { c -> hpf(decay = c) }, { m, c -> m.hpf(decay = c) }),
        k("hpf.sustain", "hpf(sustain = %s)", { it.hpsustain }, { p, c -> p.hpf(sustain = c) }, { s, c -> s.hpf(sustain = c) }, { c -> hpf(sustain = c) }, { m, c -> m.hpf(sustain = c) }),
        k("hpf.release", "hpf(release = %s)", { it.hprelease }, { p, c -> p.hpf(release = c) }, { s, c -> s.hpf(release = c) }, { c -> hpf(release = c) }, { m, c -> m.hpf(release = c) }),

        // -- bpf and its alias ------------------------------------------------------------------------------------------
        k("bpf.freq", "bpf(%s)", { it.bandf }, { p, c -> p.bpf(c) }, { s, c -> s.bpf(c) }, { c -> bpf(c) }, { m, c -> m.bpf(c) }, head = true),
        k("bandpass", "bandpass(%s)", { it.bandf }, { p, c -> p.bandpass(c) }, { s, c -> s.bandpass(c) }, { c -> bandpass(c) }, { m, c -> m.bandpass(c) }, head = true),
        k("bpf.q", "bpf(q = %s)", { it.bandq }, { p, c -> p.bpf(q = c) }, { s, c -> s.bpf(q = c) }, { c -> bpf(q = c) }, { m, c -> m.bpf(q = c) }),
        k("bpf.env", "bpf(env = %s)", { it.bpenv }, { p, c -> p.bpf(env = c) }, { s, c -> s.bpf(env = c) }, { c -> bpf(env = c) }, { m, c -> m.bpf(env = c) }),
        k("bpf.attack", "bpf(attack = %s)", { it.bpattack }, { p, c -> p.bpf(attack = c) }, { s, c -> s.bpf(attack = c) }, { c -> bpf(attack = c) }, { m, c -> m.bpf(attack = c) }),
        k("bpf.decay", "bpf(decay = %s)", { it.bpdecay }, { p, c -> p.bpf(decay = c) }, { s, c -> s.bpf(decay = c) }, { c -> bpf(decay = c) }, { m, c -> m.bpf(decay = c) }),
        k("bpf.sustain", "bpf(sustain = %s)", { it.bpsustain }, { p, c -> p.bpf(sustain = c) }, { s, c -> s.bpf(sustain = c) }, { c -> bpf(sustain = c) }, { m, c -> m.bpf(sustain = c) }),
        k("bpf.release", "bpf(release = %s)", { it.bprelease }, { p, c -> p.bpf(release = c) }, { s, c -> s.bpf(release = c) }, { c -> bpf(release = c) }, { m, c -> m.bpf(release = c) }),

        // -- notch ------------------------------------------------------------------------------------------------------
        k("notch.freq", "notch(%s)", { it.notchf }, { p, c -> p.notch(c) }, { s, c -> s.notch(c) }, { c -> notch(c) }, { m, c -> m.notch(c) }, head = true),
        k("notch.q", "notch(q = %s)", { it.nresonance }, { p, c -> p.notch(q = c) }, { s, c -> s.notch(q = c) }, { c -> notch(q = c) }, { m, c -> m.notch(q = c) }),
        k("notch.env", "notch(env = %s)", { it.nfenv }, { p, c -> p.notch(env = c) }, { s, c -> s.notch(env = c) }, { c -> notch(env = c) }, { m, c -> m.notch(env = c) }),
        k("notch.attack", "notch(attack = %s)", { it.nfattack }, { p, c -> p.notch(attack = c) }, { s, c -> s.notch(attack = c) }, { c -> notch(attack = c) }, { m, c -> m.notch(attack = c) }),
        k("notch.decay", "notch(decay = %s)", { it.nfdecay }, { p, c -> p.notch(decay = c) }, { s, c -> s.notch(decay = c) }, { c -> notch(decay = c) }, { m, c -> m.notch(decay = c) }),
        k("notch.sustain", "notch(sustain = %s)", { it.nfsustain }, { p, c -> p.notch(sustain = c) }, { s, c -> s.notch(sustain = c) }, { c -> notch(sustain = c) }, { m, c -> m.notch(sustain = c) }),
        k("notch.release", "notch(release = %s)", { it.nfrelease }, { p, c -> p.notch(release = c) }, { s, c -> s.notch(release = c) }, { c -> notch(release = c) }, { m, c -> m.notch(release = c) }),

        // -- fm -----------------------------------------------------------------------------------------------------------
        k("fm.env", "fm(%s)", { it.fmEnv }, { p, c -> p.fm(c) }, { s, c -> s.fm(c) }, { c -> fm(c) }, { m, c -> m.fm(c) },
            ctrl = "100 500", expected = listOf(100.0, 500.0), head = true),
        k("fm.h", "fm(h = %s)", { it.fmh }, { p, c -> p.fm(h = c) }, { s, c -> s.fm(h = c) }, { c -> fm(h = c) }, { m, c -> m.fm(h = c) }),
        k("fm.attack", "fm(attack = %s)", { it.fmAttack }, { p, c -> p.fm(attack = c) }, { s, c -> s.fm(attack = c) }, { c -> fm(attack = c) }, { m, c -> m.fm(attack = c) }),
        k("fm.decay", "fm(decay = %s)", { it.fmDecay }, { p, c -> p.fm(decay = c) }, { s, c -> s.fm(decay = c) }, { c -> fm(decay = c) }, { m, c -> m.fm(decay = c) }),
        k("fm.sustain", "fm(sustain = %s)", { it.fmSustain }, { p, c -> p.fm(sustain = c) }, { s, c -> s.fm(sustain = c) }, { c -> fm(sustain = c) }, { m, c -> m.fm(sustain = c) }),

        // -- vibrato and the pitch envelope, with their aliases ---------------------------------------------------------
        k("vibrato.rate", "vibrato(%s)", { it.vibrato }, { p, c -> p.vibrato(c) }, { s, c -> s.vibrato(c) }, { c -> vibrato(c) }, { m, c -> m.vibrato(c) }, head = true),
        k("vib", "vib(%s)", { it.vibrato }, { p, c -> p.vib(c) }, { s, c -> s.vib(c) }, { c -> vib(c) }, { m, c -> m.vib(c) }, head = true),
        k("vibrato.semitones", "vibrato(semitones = %s)", { it.vibratoMod }, { p, c -> p.vibrato(semitones = c) }, { s, c -> s.vibrato(semitones = c) }, { c -> vibrato(semitones = c) }, { m, c -> m.vibrato(semitones = c) }),
        k("penv.semitones", "penv(%s)", { it.pEnv }, { p, c -> p.penv(c) }, { s, c -> s.penv(c) }, { c -> penv(c) }, { m, c -> m.penv(c) }, head = true),
        k("penv.attack", "penv(attack = %s)", { it.pAttack }, { p, c -> p.penv(attack = c) }, { s, c -> s.penv(attack = c) }, { c -> penv(attack = c) }, { m, c -> m.penv(attack = c) }),
        k("penv.decay", "penv(decay = %s)", { it.pDecay }, { p, c -> p.penv(decay = c) }, { s, c -> s.penv(decay = c) }, { c -> penv(decay = c) }, { m, c -> m.penv(decay = c) }),
        k("penv.sustain", "penv(sustain = %s)", { it.pSustain }, { p, c -> p.penv(sustain = c) }, { s, c -> s.penv(sustain = c) }, { c -> penv(sustain = c) }, { m, c -> m.penv(sustain = c) }),
        k("penv.release", "penv(release = %s)", { it.pRelease }, { p, c -> p.penv(release = c) }, { s, c -> s.penv(release = c) }, { c -> penv(release = c) }, { m, c -> m.penv(release = c) }),

        // -- the pluck oscillators' knobs (ignitorParams) --------------------------------------------------------------------
        k("sndPluck.feedback", "sndPluck(feedback = %s)", { it.soundName to it.ignitorParams?.get("feedback") }, { p, c -> p.sndPluck(feedback = c) }, { s, c -> s.sndPluck(feedback = c) }, { c -> sndPluck(feedback = c) }, { m, c -> m.sndPluck(feedback = c) },
            expected = listOf("pluck" to 0.5, "pluck" to 1.0), continuous = null),
        k("sndPluck.brightness", "sndPluck(brightness = %s)", { it.soundName to it.ignitorParams?.get("brightness") }, { p, c -> p.sndPluck(brightness = c) }, { s, c -> s.sndPluck(brightness = c) }, { c -> sndPluck(brightness = c) }, { m, c -> m.sndPluck(brightness = c) },
            expected = listOf("pluck" to 0.5, "pluck" to 1.0), continuous = null),
        k("sndPluck.pickPosition", "sndPluck(pickPosition = %s)", { it.soundName to it.ignitorParams?.get("pickPosition") }, { p, c -> p.sndPluck(pickPosition = c) }, { s, c -> s.sndPluck(pickPosition = c) }, { c -> sndPluck(pickPosition = c) }, { m, c -> m.sndPluck(pickPosition = c) },
            expected = listOf("pluck" to 0.5, "pluck" to 1.0), continuous = null),
        k("sndPluck.stiffness", "sndPluck(stiffness = %s)", { it.soundName to it.ignitorParams?.get("stiffness") }, { p, c -> p.sndPluck(stiffness = c) }, { s, c -> s.sndPluck(stiffness = c) }, { c -> sndPluck(stiffness = c) }, { m, c -> m.sndPluck(stiffness = c) },
            expected = listOf("pluck" to 0.5, "pluck" to 1.0), continuous = null),
        k("sndSuperPluck.voices", "sndSuperPluck(voices = %s)", { it.soundName to it.ignitorParams?.get("voices") }, { p, c -> p.sndSuperPluck(voices = c) }, { s, c -> s.sndSuperPluck(voices = c) }, { c -> sndSuperPluck(voices = c) }, { m, c -> m.sndSuperPluck(voices = c) },
            ctrl = "3 5", expected = listOf("superpluck" to 3.0, "superpluck" to 5.0), continuous = null),
        k("sndSuperPluck.spread", "sndSuperPluck(spread = %s)", { it.soundName to it.ignitorParams?.get("spread") }, { p, c -> p.sndSuperPluck(spread = c) }, { s, c -> s.sndSuperPluck(spread = c) }, { c -> sndSuperPluck(spread = c) }, { m, c -> m.sndSuperPluck(spread = c) },
            expected = listOf("superpluck" to 0.5, "superpluck" to 1.0), continuous = null),
        k("sndSuperPluck.feedback", "sndSuperPluck(feedback = %s)", { it.soundName to it.ignitorParams?.get("feedback") }, { p, c -> p.sndSuperPluck(feedback = c) }, { s, c -> s.sndSuperPluck(feedback = c) }, { c -> sndSuperPluck(feedback = c) }, { m, c -> m.sndSuperPluck(feedback = c) },
            expected = listOf("superpluck" to 0.5, "superpluck" to 1.0), continuous = null),
        k("sndSuperPluck.brightness", "sndSuperPluck(brightness = %s)", { it.soundName to it.ignitorParams?.get("brightness") }, { p, c -> p.sndSuperPluck(brightness = c) }, { s, c -> s.sndSuperPluck(brightness = c) }, { c -> sndSuperPluck(brightness = c) }, { m, c -> m.sndSuperPluck(brightness = c) },
            expected = listOf("superpluck" to 0.5, "superpluck" to 1.0), continuous = null),
        k("sndSuperPluck.pickPosition", "sndSuperPluck(pickPosition = %s)", { it.soundName to it.ignitorParams?.get("pickPosition") }, { p, c -> p.sndSuperPluck(pickPosition = c) }, { s, c -> s.sndSuperPluck(pickPosition = c) }, { c -> sndSuperPluck(pickPosition = c) }, { m, c -> m.sndSuperPluck(pickPosition = c) },
            expected = listOf("superpluck" to 0.5, "superpluck" to 1.0), continuous = null),
        k("sndSuperPluck.stiffness", "sndSuperPluck(stiffness = %s)", { it.soundName to it.ignitorParams?.get("stiffness") }, { p, c -> p.sndSuperPluck(stiffness = c) }, { s, c -> s.sndSuperPluck(stiffness = c) }, { c -> sndSuperPluck(stiffness = c) }, { m, c -> m.sndSuperPluck(stiffness = c) },
            expected = listOf("superpluck" to 0.5, "superpluck" to 1.0), continuous = null),
    )

    val cycles = 12

    /** The script call with its control filled in as a string literal, or bare when [ctrl] is null. */
    fun Knob.script(ctrl: String?): String = call.replace("%s", ctrl?.let { "\"$it\"" } ?: "")

    /**
     * Asserts [expected] per event in [field], and [slotExpected] in [slot], over [cycles] cycles of two events each.
     * [values] are the receiver's own values, which a string receiver keeps; [extra] checks what the
     * chained form owes its prefix.
     */
    fun Knob.check(
        form: String,
        p: SprudelPattern?,
        values: List<String>? = null,
        extra: (SprudelVoiceData) -> Unit = {},
    ) {
        withClue("$name | $form") {
            val pattern = p.shouldNotBeNull()

            (0 until cycles).forEach { c ->
                withClue("cycle $c") {
                    val events = pattern.queryArc(c.toDouble(), c + 1.0)

                    events shouldHaveSize expected.size
                    events.map { field(it.data) } shouldBe expected
                    slot?.let { s -> events.map { s(it.data) } shouldBe slotExpected }
                    values?.let { v -> events.map { it.data.value?.asString } shouldBe v }
                    events.forEach { extra(it.data) }
                }
            }
        }
    }

    // A prefix door for the chained form: `pan`, or `gain` for the knobs that write `pan`.
    fun Knob.prefixIsPan() = name != "pan"

    "every knob, every calling form, both doors: the form writes the knob's value" {
        val pat = "a b"

        knobs.forEach { knob ->
            val c = knob.ctrl
            val call = knob.script(c)
            // The chained form runs the door twice: a decoy (the control reversed), then the control,
            // which must win.
            val decoy = c.split(" ").reversed().joinToString(" ")
            val prefixKotlin: PatternMapperFn = if (knob.prefixIsPan()) pan(0.25) else gain(0.25)
            val prefixScript = if (knob.prefixIsPan()) "pan(0.25)" else "gain(0.25)"
            val prefixField: (SprudelVoiceData) -> Double? = if (knob.prefixIsPan()) ({ it.pan }) else ({ it.gain })
            val keepsPrefix: (SprudelVoiceData) -> Unit = { prefixField(it) shouldBe 0.25 }

            knob.check("kotlin pattern", knob.pattern(seq(pat), c))
            knob.check("script pattern", SprudelPattern.compile("""seq("$pat").$call"""))
            knob.check("kotlin string", knob.string(pat, c), values = listOf("a", "b"))
            knob.check("script string", SprudelPattern.compile(""""$pat".$call"""), values = listOf("a", "b"))
            knob.check("kotlin mapper", seq(pat).apply(knob.mapper(c)))
            knob.check("script mapper", SprudelPattern.compile("""seq("$pat").apply($call)"""))

            knob.chained?.let { chained ->
                knob.check("kotlin chained", seq(pat).apply(chained(chained(prefixKotlin, decoy), c)), extra = keepsPrefix)
                knob.check(
                    "script chained",
                    SprudelPattern.compile("""seq("$pat").apply($prefixScript.${knob.script(decoy)}.$call)"""),
                    extra = keepsPrefix,
                )
            }
        }
    }

    "every head, every calling form, both doors: a bare call reinterprets the receiver's numbers as the head" {
        knobs.filter { it.head }.forEach { knob ->
            val nums = knob.ctrl
            val bare = knob.script(null)

            knob.check("kotlin pattern", knob.pattern(seq(nums), null))
            knob.check("script pattern", SprudelPattern.compile("""seq("$nums").$bare"""))
            knob.check("kotlin string", knob.string(nums, null))
            knob.check("script string", SprudelPattern.compile(""""$nums".$bare"""))
            knob.check("kotlin mapper", seq(nums).apply(knob.mapper(null)))
            knob.check("script mapper", SprudelPattern.compile("""seq("$nums").apply($bare)"""))
        }
    }

    "every numeric knob, both doors: a continuous pattern is sampled per event" {
        // sine is 0.5 at t = 0, 1.0 at t = 0.25, 0.5 at t = 0.5 and 0.0 at t = 0.75; an Int knob truncates
        knobs.forEach { knob ->
            val expected = knob.continuous ?: return@forEach

            listOf(
                "kotlin" to knob.pattern(note("a b c d"), sine),
                "script" to SprudelPattern.compile("""note("a b c d").${knob.call.replace("%s", "sine")}""").shouldNotBeNull(),
            ).forEach { (door, p) ->
                withClue("${knob.name} | $door") {
                    val events = p.queryArc(0.0, 1.0)

                    events shouldHaveSize 4
                    events.forEachIndexed { i, e ->
                        when (val want = expected[i]) {
                            is Double -> {
                                knob.field(e.data).shouldBeInstanceOf<Double>() shouldBe (want plusOrMinus 1e-9)
                                knob.slot?.let { s -> s(e.data).shouldNotBeNull() shouldBe (want plusOrMinus 1e-9) }
                            }

                            else -> knob.field(e.data) shouldBe want
                        }
                    }
                }
            }
        }
    }

    "every compound door in the table, every calling form, both doors: positional arguments land in declaration order" {
        class Door(
            val name: String,
            /** The positional arguments, as KlangScript source (the Kotlin lambdas pass the same). */
            val args: String,
            /** The door's knobs, in declaration order. */
            val fields: (SprudelVoiceData) -> List<Any?>,
            val expected: List<Any?>,
            val pattern: (SprudelPattern) -> SprudelPattern,
            val string: (String) -> SprudelPattern,
            val mapper: () -> PatternMapperFn,
            val chained: (PatternMapperFn) -> PatternMapperFn,
        )

        // Every argument a distinct value, so two swapped positions cannot agree by accident.
        val doors = listOf(
            Door(
                "adsr", """0.1, 0.2, 0.7, 0.4""", { listOf(it.attack, it.decay, it.sustain, it.release) }, listOf(0.1, 0.2, 0.7, 0.4),
                { it.adsr(0.1, 0.2, 0.7, 0.4) }, { it.adsr(0.1, 0.2, 0.7, 0.4) }, { adsr(0.1, 0.2, 0.7, 0.4) }, { it.adsr(0.1, 0.2, 0.7, 0.4) },
            ),
            Door(
                "compressor", """-20, 4, 6, 0.003, 0.1""", { listOf("threshold", "ratio", "knee", "attack", "release").map { k -> it.katalystParams?.get("compressor.$k") } }, listOf(-20.0, 4.0, 6.0, 0.003, 0.1),
                { it.compressor(-20, 4, 6, 0.003, 0.1) }, { it.compressor(-20, 4, 6, 0.003, 0.1) }, { compressor(-20, 4, 6, 0.003, 0.1) }, { it.compressor(-20, 4, 6, 0.003, 0.1) },
            ),
            Door(
                "comp", """-20, 4, 6, 0.003, 0.1""", { listOf("threshold", "ratio", "knee", "attack", "release").map { k -> it.katalystParams?.get("compressor.$k") } }, listOf(-20.0, 4.0, 6.0, 0.003, 0.1),
                { it.comp(-20, 4, 6, 0.003, 0.1) }, { it.comp(-20, 4, 6, 0.003, 0.1) }, { comp(-20, 4, 6, 0.003, 0.1) }, { it.comp(-20, 4, 6, 0.003, 0.1) },
            ),
            Door(
                "unison", """5, 0.3, 0.6""", { listOf("voices", "spread", "panSpread").map { k -> it.ignitorParams?.get(k) } }, listOf(5.0, 0.3, 0.6),
                { it.unison(5, 0.3, 0.6) }, { it.unison(5, 0.3, 0.6) }, { unison(5, 0.3, 0.6) }, { it.unison(5, 0.3, 0.6) },
            ),
            Door(
                "uni", """5, 0.3, 0.6""", { listOf("voices", "spread", "panSpread").map { k -> it.ignitorParams?.get(k) } }, listOf(5.0, 0.3, 0.6),
                { it.uni(5, 0.3, 0.6) }, { it.uni(5, 0.3, 0.6) }, { uni(5, 0.3, 0.6) }, { it.uni(5, 0.3, 0.6) },
            ),
            Door(
                "duck", """1, 0.8, 0.2""", { listOf("orbit", "depth", "attack").map { k -> it.katalystParams?.get("duck.$k") } }, listOf(1.0, 0.8, 0.2),
                { it.duck(1, 0.8, 0.2) }, { it.duck(1, 0.8, 0.2) }, { duck(1, 0.8, 0.2) }, { it.duck(1, 0.8, 0.2) },
            ),
            Door(
                "reverb", """0.3, 4, 1500""", { listOf("wet", "size", "lowpass").map { k -> it.katalystParams?.get("reverb.$k") } }, listOf(0.3, 4.0, 1500.0),
                { it.reverb(0.3, 4, 1500) }, { it.reverb(0.3, 4, 1500) }, { reverb(0.3, 4, 1500) }, { it.reverb(0.3, 4, 1500) },
            ),
            Door(
                "delay", """0.3, 0.25, 0.4, 0.9""", { listOf("wet", "time", "feedback", "cap").map { k -> it.katalystParams?.get("delay.$k") } }, listOf(0.3, 0.25, 0.4, 0.9),
                { it.delay(0.3, 0.25, 0.4, 0.9) }, { it.delay(0.3, 0.25, 0.4, 0.9) }, { delay(0.3, 0.25, 0.4, 0.9) }, { it.delay(0.3, 0.25, 0.4, 0.9) },
            ),
            Door(
                "phaser", """0.6, 0.5, 1000, 2000, 0.2""", { listOf(it.phaserDepth, it.phaserRate, it.phaserCenter, it.phaserSweep, it.phaserFloor) }, listOf(0.6, 0.5, 1000.0, 2000.0, 0.2),
                { it.phaser(0.6, 0.5, 1000, 2000, 0.2) }, { it.phaser(0.6, 0.5, 1000, 2000, 0.2) }, { phaser(0.6, 0.5, 1000, 2000, 0.2) }, { it.phaser(0.6, 0.5, 1000, 2000, 0.2) },
            ),
            Door(
                "body", """0.4, "wood", 0.2""", { listOf(it.bodyMix, it.body, it.bodyFloor) }, listOf(0.4, "wood", 0.2),
                { it.body(0.4, "wood", 0.2) }, { it.body(0.4, "wood", 0.2) }, { body(0.4, "wood", 0.2) }, { it.body(0.4, "wood", 0.2) },
            ),
            Door(
                "vowel", """0.4, "a", 0.2""", { listOf(it.vowelMix, it.vowel, it.vowelFloor) }, listOf(0.4, "a", 0.2),
                { it.vowel(0.4, "a", 0.2) }, { it.vowel(0.4, "a", 0.2) }, { vowel(0.4, "a", 0.2) }, { it.vowel(0.4, "a", 0.2) },
            ),
            Door(
                "tremolo", """0.5, 4, "sine"""", { listOf(it.tremoloDepth, it.tremoloRate, it.tremoloShape) }, listOf(0.5, 4.0, "sine"),
                { it.tremolo(0.5, 4, "sine") }, { it.tremolo(0.5, 4, "sine") }, { tremolo(0.5, 4, "sine") }, { it.tremolo(0.5, 4, "sine") },
            ),
            Door(
                "distort", """0.5, "soft", 2""", { listOf(it.distort, it.distortShape, it.distortOversample) }, listOf(0.5, "soft", 2),
                { it.distort(0.5, "soft", 2) }, { it.distort(0.5, "soft", 2) }, { distort(0.5, "soft", 2) }, { it.distort(0.5, "soft", 2) },
            ),
            Door(
                "crush", """8, 2""", { listOf(it.crush, it.crushOversample) }, listOf(8.0, 2),
                { it.crush(8, 2) }, { it.crush(8, 2) }, { crush(8, 2) }, { it.crush(8, 2) },
            ),
            Door(
                "coarse", """4, 2""", { listOf(it.coarse, it.coarseOversample) }, listOf(4.0, 2),
                { it.coarse(4, 2) }, { it.coarse(4, 2) }, { coarse(4, 2) }, { it.coarse(4, 2) },
            ),
            Door(
                "lpf", """800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3""", { listOf(it.cutoff, it.resonance, it.lpPasses, it.lpenv, it.lpattack, it.lpdecay, it.lpsustain, it.lprelease) }, listOf(800.0, 2.0, 3.0, 12.0, 0.1, 0.2, 0.5, 0.3),
                { it.lpf(800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3) }, { it.lpf(800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3) }, { lpf(800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3) }, { it.lpf(800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3) },
            ),
            Door(
                "lowpass", """800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3""", { listOf(it.cutoff, it.resonance, it.lpPasses, it.lpenv, it.lpattack, it.lpdecay, it.lpsustain, it.lprelease) }, listOf(800.0, 2.0, 3.0, 12.0, 0.1, 0.2, 0.5, 0.3),
                { it.lowpass(800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3) }, { it.lowpass(800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3) }, { lowpass(800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3) }, { it.lowpass(800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3) },
            ),
            Door(
                "hpf", """800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3""", { listOf(it.hcutoff, it.hresonance, it.hpPasses, it.hpenv, it.hpattack, it.hpdecay, it.hpsustain, it.hprelease) }, listOf(800.0, 2.0, 3.0, 12.0, 0.1, 0.2, 0.5, 0.3),
                { it.hpf(800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3) }, { it.hpf(800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3) }, { hpf(800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3) }, { it.hpf(800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3) },
            ),
            Door(
                "highpass", """800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3""", { listOf(it.hcutoff, it.hresonance, it.hpPasses, it.hpenv, it.hpattack, it.hpdecay, it.hpsustain, it.hprelease) }, listOf(800.0, 2.0, 3.0, 12.0, 0.1, 0.2, 0.5, 0.3),
                { it.highpass(800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3) }, { it.highpass(800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3) }, { highpass(800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3) }, { it.highpass(800, 2, 3, 12, 0.1, 0.2, 0.5, 0.3) },
            ),
            Door(
                "bpf", """800, 2, 12, 0.1, 0.2, 0.5, 0.3""", { listOf(it.bandf, it.bandq, it.bpenv, it.bpattack, it.bpdecay, it.bpsustain, it.bprelease) }, listOf(800.0, 2.0, 12.0, 0.1, 0.2, 0.5, 0.3),
                { it.bpf(800, 2, 12, 0.1, 0.2, 0.5, 0.3) }, { it.bpf(800, 2, 12, 0.1, 0.2, 0.5, 0.3) }, { bpf(800, 2, 12, 0.1, 0.2, 0.5, 0.3) }, { it.bpf(800, 2, 12, 0.1, 0.2, 0.5, 0.3) },
            ),
            Door(
                "bandpass", """800, 2, 12, 0.1, 0.2, 0.5, 0.3""", { listOf(it.bandf, it.bandq, it.bpenv, it.bpattack, it.bpdecay, it.bpsustain, it.bprelease) }, listOf(800.0, 2.0, 12.0, 0.1, 0.2, 0.5, 0.3),
                { it.bandpass(800, 2, 12, 0.1, 0.2, 0.5, 0.3) }, { it.bandpass(800, 2, 12, 0.1, 0.2, 0.5, 0.3) }, { bandpass(800, 2, 12, 0.1, 0.2, 0.5, 0.3) }, { it.bandpass(800, 2, 12, 0.1, 0.2, 0.5, 0.3) },
            ),
            Door(
                "notch", """800, 2, 12, 0.1, 0.2, 0.5, 0.3""", { listOf(it.notchf, it.nresonance, it.nfenv, it.nfattack, it.nfdecay, it.nfsustain, it.nfrelease) }, listOf(800.0, 2.0, 12.0, 0.1, 0.2, 0.5, 0.3),
                { it.notch(800, 2, 12, 0.1, 0.2, 0.5, 0.3) }, { it.notch(800, 2, 12, 0.1, 0.2, 0.5, 0.3) }, { notch(800, 2, 12, 0.1, 0.2, 0.5, 0.3) }, { it.notch(800, 2, 12, 0.1, 0.2, 0.5, 0.3) },
            ),
            Door(
                "fm", """200, 2, 0.01, 0.3, 0.5""", { listOf(it.fmEnv, it.fmh, it.fmAttack, it.fmDecay, it.fmSustain) }, listOf(200.0, 2.0, 0.01, 0.3, 0.5),
                { it.fm(200, 2, 0.01, 0.3, 0.5) }, { it.fm(200, 2, 0.01, 0.3, 0.5) }, { fm(200, 2, 0.01, 0.3, 0.5) }, { it.fm(200, 2, 0.01, 0.3, 0.5) },
            ),
            Door(
                "vibrato", """5, 0.5""", { listOf(it.vibrato, it.vibratoMod) }, listOf(5.0, 0.5),
                { it.vibrato(5, 0.5) }, { it.vibrato(5, 0.5) }, { vibrato(5, 0.5) }, { it.vibrato(5, 0.5) },
            ),
            Door(
                "vib", """5, 0.5""", { listOf(it.vibrato, it.vibratoMod) }, listOf(5.0, 0.5),
                { it.vib(5, 0.5) }, { it.vib(5, 0.5) }, { vib(5, 0.5) }, { it.vib(5, 0.5) },
            ),
            Door(
                "penv", """12, 0.01, 0.2, 0.25, 0.3""", { listOf(it.pEnv, it.pAttack, it.pDecay, it.pSustain, it.pRelease) }, listOf(12.0, 0.01, 0.2, 0.25, 0.3),
                { it.penv(12, 0.01, 0.2, 0.25, 0.3) }, { it.penv(12, 0.01, 0.2, 0.25, 0.3) }, { penv(12, 0.01, 0.2, 0.25, 0.3) }, { it.penv(12, 0.01, 0.2, 0.25, 0.3) },
            ),
            Door(
                "sndPluck", """0.99, 0.8, 0.2, 0.3""", { listOf(it.soundName) + listOf("feedback", "brightness", "pickPosition", "stiffness").map { k -> it.ignitorParams?.get(k) } }, listOf("pluck", 0.99, 0.8, 0.2, 0.3),
                { it.sndPluck(0.99, 0.8, 0.2, 0.3) }, { it.sndPluck(0.99, 0.8, 0.2, 0.3) }, { sndPluck(0.99, 0.8, 0.2, 0.3) }, { it.sndPluck(0.99, 0.8, 0.2, 0.3) },
            ),
            Door(
                "sndSuperPluck", """7, 0.3, 0.99, 0.8, 0.2, 0.1""", { listOf(it.soundName) + listOf("voices", "spread", "feedback", "brightness", "pickPosition", "stiffness").map { k -> it.ignitorParams?.get(k) } }, listOf("superpluck", 7.0, 0.3, 0.99, 0.8, 0.2, 0.1),
                { it.sndSuperPluck(7, 0.3, 0.99, 0.8, 0.2, 0.1) }, { it.sndSuperPluck(7, 0.3, 0.99, 0.8, 0.2, 0.1) }, { sndSuperPluck(7, 0.3, 0.99, 0.8, 0.2, 0.1) }, { it.sndSuperPluck(7, 0.3, 0.99, 0.8, 0.2, 0.1) },
            ),
        )

        doors.forEach { door ->
            val call = "${door.name}(${door.args})"

            listOf(
                "kotlin pattern" to door.pattern(seq("a b")),
                "script pattern" to SprudelPattern.compile("""seq("a b").$call"""),
                "kotlin string" to door.string("a b"),
                "script string" to SprudelPattern.compile(""""a b".$call"""),
                "kotlin mapper" to seq("a b").apply(door.mapper()),
                "script mapper" to SprudelPattern.compile("""seq("a b").apply($call)"""),
                "kotlin chained" to seq("a b").apply(door.chained(gain(0.25))),
                "script chained" to SprudelPattern.compile("""seq("a b").apply(gain(0.25).$call)"""),
            ).forEach { (form, p) ->
                withClue("${door.name} | $form") {
                    val pattern = p.shouldNotBeNull()

                    (0 until cycles).forEach { c ->
                        withClue("cycle $c") {
                            val events = pattern.queryArc(c.toDouble(), c + 1.0)

                            events shouldHaveSize 2
                            events.forEach { door.fields(it.data) shouldBe door.expected }

                            if (form.endsWith("chained")) {
                                events.forEach { it.data.gain shouldBe 0.25 }
                            }
                        }
                    }
                }
            }
        }
    }
})
