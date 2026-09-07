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
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelVoiceData

/**
 * One row per field accessor, each run through BOTH doors: the Kotlin pattern and the same text
 * compiled as KlangScript must agree with each other and with the expected field value, over 12
 * cycles. Two rows per accessor: a mapper applied to its own field (`gain(mul(0.5))`) and the
 * bare accessor read into another field (`pan(gain)`).
 *
 * `freq` and the mechanism itself are covered by [LangFreqAccessorSpec].
 */
class LangFieldAccessorsSpec : StringSpec({

    class Row(
        val accessor: String,
        val kotlin: SprudelPattern,
        val script: String,
        val field: (SprudelVoiceData) -> Double?,
        val expected: Double,
    )

    fun row(accessor: String, script: String, field: (SprudelVoiceData) -> Double?, expected: Double, kotlin: SprudelPattern) =
        Row(accessor, kotlin, script, field, expected)

    val mapped = listOf(
        row("gain", """note("c e").gain(0.8).gain(mul(0.5))""", { it.gain }, 0.4, note("c e").gain(0.8).gain(mul(0.5))),
        row("velocity", """note("c e").velocity(0.8).velocity(mul(0.5))""", { it.velocity }, 0.4, note("c e").velocity(0.8).velocity(mul(0.5))),
        row("pan", """note("c e").pan(0.3).pan(add(0.2))""", { it.pan }, 0.5, note("c e").pan(0.3).pan(add(0.2))),
        row("postgain", """note("c e").postgain(0.5).postgain(mul(2))""", { it.postGain }, 1.0, note("c e").postgain(0.5).postgain(mul(2))),
        row("lpf", """note("c e").lpf(800).lpf(mul(2))""", { it.cutoff }, 1600.0, note("c e").lpf(800).lpf(mul(2))),
        row("hpf", """note("c e").hpf(200).hpf(add(50))""", { it.hcutoff }, 250.0, note("c e").hpf(200).hpf(add(50))),
        row("bpf", """note("c e").bpf(500).bpf(mul(2))""", { it.bandf }, 1000.0, note("c e").bpf(500).bpf(mul(2))),
        row("lpq", """note("c e").lpq(4).lpq(mul(2))""", { it.resonance }, 8.0, note("c e").lpq(4).lpq(mul(2))),
        row("hpq", """note("c e").hpq(3).hpq(add(1))""", { it.hresonance }, 4.0, note("c e").hpq(3).hpq(add(1))),
        row("bpq", """note("c e").bpq(5).bpq(mul(2))""", { it.bandq }, 10.0, note("c e").bpq(5).bpq(mul(2))),
        row("attack", """note("c e").attack(0.1).attack(mul(2))""", { it.attack }, 0.2, note("c e").attack(0.1).attack(mul(2))),
        row("decay", """note("c e").decay(0.2).decay(add(0.1))""", { it.decay }, 0.3, note("c e").decay(0.2).decay(add(0.1))),
        row("sustain", """note("c e").sustain(0.5).sustain(mul(0.5))""", { it.sustain }, 0.25, note("c e").sustain(0.5).sustain(mul(0.5))),
        row("release", """note("c e").release(0.4).release(mul(0.5))""", { it.release }, 0.2, note("c e").release(0.4).release(mul(0.5))),
    )

    val read = listOf(
        row("gain", """note("c e").gain(0.8).pan(gain)""", { it.pan }, 0.8, note("c e").gain(0.8).pan(gain)),
        row("velocity", """note("c e").velocity(0.7).pan(velocity)""", { it.pan }, 0.7, note("c e").velocity(0.7).pan(velocity)),
        row("pan", """note("c e").pan(0.3).gain(pan)""", { it.gain }, 0.3, note("c e").pan(0.3).gain(pan)),
        row("postgain", """note("c e").postgain(0.6).pan(postgain)""", { it.pan }, 0.6, note("c e").postgain(0.6).pan(postgain)),
        row("lpf", """note("c e").lpf(800).hpf(lpf)""", { it.hcutoff }, 800.0, note("c e").lpf(800).hpf(lpf)),
        row("hpf", """note("c e").hpf(200).lpf(hpf)""", { it.cutoff }, 200.0, note("c e").hpf(200).lpf(hpf)),
        row("bpf", """note("c e").bpf(500).lpf(bpf)""", { it.cutoff }, 500.0, note("c e").bpf(500).lpf(bpf)),
        row("lpq", """note("c e").lpq(4).hpq(lpq)""", { it.hresonance }, 4.0, note("c e").lpq(4).hpq(lpq)),
        row("hpq", """note("c e").hpq(3).bpq(hpq)""", { it.bandq }, 3.0, note("c e").hpq(3).bpq(hpq)),
        row("bpq", """note("c e").bpq(5).lpq(bpq)""", { it.resonance }, 5.0, note("c e").bpq(5).lpq(bpq)),
        row("attack", """note("c e").attack(0.1).decay(attack)""", { it.decay }, 0.1, note("c e").attack(0.1).decay(attack)),
        row("decay", """note("c e").decay(0.2).release(decay)""", { it.release }, 0.2, note("c e").decay(0.2).release(decay)),
        row("sustain", """note("c e").sustain(0.5).pan(sustain)""", { it.pan }, 0.5, note("c e").sustain(0.5).pan(sustain)),
        row("release", """note("c e").release(0.4).attack(release)""", { it.attack }, 0.4, note("c e").release(0.4).attack(release)),
    )

    // Batch two: the effects fields.
    val mappedEffects = listOf(
        row("distort", """s("bd sd").distort(0.4).distort(mul(2))""", { it.distort }, 0.8, s("bd sd").distort(0.4).distort(mul(2))),
        row("distos", """s("bd sd").distort(0.5).distos(2).distos(mul(2))""", { it.distortOversample?.toDouble() }, 4.0, s("bd sd").distort(0.5).distos(2).distos(mul(2))),
        row("crush", """s("bd sd").crush(8).crush(mul(0.5))""", { it.crush }, 4.0, s("bd sd").crush(8).crush(mul(0.5))),
        row("crushos", """s("bd sd").crush(8).crushos(1).crushos(add(1))""", { it.crushOversample?.toDouble() }, 2.0, s("bd sd").crush(8).crushos(1).crushos(add(1))),
        row("coarse", """s("bd sd").coarse(4).coarse(mul(2))""", { it.coarse }, 8.0, s("bd sd").coarse(4).coarse(mul(2))),
        row("coarseos", """s("bd sd").coarse(4).coarseos(1).coarseos(add(1))""", { it.coarseOversample?.toDouble() }, 2.0, s("bd sd").coarse(4).coarseos(1).coarseos(add(1))),
        row("roomWet", """s("bd sd").roomWet(0.3).roomWet(mul(2))""", { it.room }, 0.6, s("bd sd").roomWet(0.3).roomWet(mul(2))),
        row("roomsize", """s("bd sd").roomsize(4).roomsize(mul(2))""", { it.roomSize }, 8.0, s("bd sd").roomsize(4).roomsize(mul(2))),
        row("roomfade", """s("bd sd").roomfade(1).roomfade(mul(2))""", { it.roomFade }, 2.0, s("bd sd").roomfade(1).roomfade(mul(2))),
        row("roomlp", """s("bd sd").roomlp(4000).roomlp(mul(0.5))""", { it.roomLp }, 2000.0, s("bd sd").roomlp(4000).roomlp(mul(0.5))),
        row("roomdim", """s("bd sd").roomdim(3000).roomdim(add(1000))""", { it.roomDim }, 4000.0, s("bd sd").roomdim(3000).roomdim(add(1000))),
        row("delayWet", """s("bd sd").delayWet(0.3).delayWet(mul(2))""", { it.delay }, 0.6, s("bd sd").delayWet(0.3).delayWet(mul(2))),
        row("delaytime", """s("bd sd").delaytime(0.25).delaytime(mul(2))""", { it.delayTime }, 0.5, s("bd sd").delaytime(0.25).delaytime(mul(2))),
        row("delayfeedback", """s("bd sd").delayfeedback(0.4).delayfeedback(add(0.2))""", { it.delayFeedback }, 0.6, s("bd sd").delayfeedback(0.4).delayfeedback(add(0.2))),
        row("phaser", """s("bd sd").phaser(0.5).phaser(mul(4))""", { it.phaserRate }, 2.0, s("bd sd").phaser(0.5).phaser(mul(4))),
        row("phaserWet", """s("bd sd").phaserWet(0.5).phaserWet(mul(0.5))""", { it.phaserDepth }, 0.25, s("bd sd").phaserWet(0.5).phaserWet(mul(0.5))),
        row("phaserFloor", """s("bd sd").phaserFloor(0.2).phaserFloor(add(0.3))""", { it.phaserFloor }, 0.5, s("bd sd").phaserFloor(0.2).phaserFloor(add(0.3))),
        row("phasercenter", """s("bd sd").phasercenter(1000).phasercenter(mul(2))""", { it.phaserCenter }, 2000.0, s("bd sd").phasercenter(1000).phasercenter(mul(2))),
        row("phasersweep", """s("bd sd").phasersweep(2000).phasersweep(mul(0.5))""", { it.phaserSweep }, 1000.0, s("bd sd").phasersweep(2000).phasersweep(mul(0.5))),
        row("tremolosync", """s("bd sd").tremolosync(4).tremolosync(mul(2))""", { it.tremoloSync }, 8.0, s("bd sd").tremolosync(4).tremolosync(mul(2))),
        row("tremolodepth", """s("bd sd").tremolodepth(0.5).tremolodepth(mul(0.5))""", { it.tremoloDepth }, 0.25, s("bd sd").tremolodepth(0.5).tremolodepth(mul(0.5))),
        row("tremoloskew", """s("bd sd").tremoloskew(0.5).tremoloskew(add(0.3))""", { it.tremoloSkew }, 0.8, s("bd sd").tremoloskew(0.5).tremoloskew(add(0.3))),
        row("tremolophase", """s("bd sd").tremolophase(0.25).tremolophase(add(0.5))""", { it.tremoloPhase }, 0.75, s("bd sd").tremolophase(0.25).tremolophase(add(0.5))),
        row("delaycap", """s("bd sd").delaycap(0.5).delaycap(mul(2))""", { it.delayCap }, 1.0, s("bd sd").delaycap(0.5).delaycap(mul(2))),
    )

    val readEffects = listOf(
        row("distort", """s("bd sd").distort(0.4).pan(distort)""", { it.pan }, 0.4, s("bd sd").distort(0.4).pan(distort)),
        row("distos", """s("bd sd").distos(2).crushos(distos)""", { it.crushOversample?.toDouble() }, 2.0, s("bd sd").distos(2).crushos(distos)),
        row("crush", """s("bd sd").crush(8).coarse(crush)""", { it.coarse }, 8.0, s("bd sd").crush(8).coarse(crush)),
        row("crushos", """s("bd sd").crushos(2).coarseos(crushos)""", { it.coarseOversample?.toDouble() }, 2.0, s("bd sd").crushos(2).coarseos(crushos)),
        row("coarse", """s("bd sd").coarse(4).crush(coarse)""", { it.crush }, 4.0, s("bd sd").coarse(4).crush(coarse)),
        row("coarseos", """s("bd sd").coarseos(2).distos(coarseos)""", { it.distortOversample?.toDouble() }, 2.0, s("bd sd").coarseos(2).distos(coarseos)),
        row("roomWet", """s("bd sd").roomWet(0.3).delayWet(roomWet)""", { it.delay }, 0.3, s("bd sd").roomWet(0.3).delayWet(roomWet)),
        row("roomsize", """s("bd sd").roomsize(4).roomfade(roomsize)""", { it.roomFade }, 4.0, s("bd sd").roomsize(4).roomfade(roomsize)),
        row("roomfade", """s("bd sd").roomfade(1).delaytime(roomfade)""", { it.delayTime }, 1.0, s("bd sd").roomfade(1).delaytime(roomfade)),
        row("roomlp", """s("bd sd").roomlp(4000).lpf(roomlp)""", { it.cutoff }, 4000.0, s("bd sd").roomlp(4000).lpf(roomlp)),
        row("roomdim", """s("bd sd").roomdim(3000).roomlp(roomdim)""", { it.roomLp }, 3000.0, s("bd sd").roomdim(3000).roomlp(roomdim)),
        row("delayWet", """s("bd sd").delayWet(0.3).roomWet(delayWet)""", { it.room }, 0.3, s("bd sd").delayWet(0.3).roomWet(delayWet)),
        row("delaytime", """s("bd sd").delaytime(0.25).roomfade(delaytime)""", { it.roomFade }, 0.25, s("bd sd").delaytime(0.25).roomfade(delaytime)),
        row("delayfeedback", """s("bd sd").delayfeedback(0.4).pan(delayfeedback)""", { it.pan }, 0.4, s("bd sd").delayfeedback(0.4).pan(delayfeedback)),
        row("phaser", """s("bd sd").phaser(0.5).tremolosync(phaser)""", { it.tremoloSync }, 0.5, s("bd sd").phaser(0.5).tremolosync(phaser)),
        row("phaserWet", """s("bd sd").phaserWet(0.5).pan(phaserWet)""", { it.pan }, 0.5, s("bd sd").phaserWet(0.5).pan(phaserWet)),
        row("phaserFloor", """s("bd sd").phaserFloor(0.2).pan(phaserFloor)""", { it.pan }, 0.2, s("bd sd").phaserFloor(0.2).pan(phaserFloor)),
        row("phasercenter", """s("bd sd").phasercenter(1000).lpf(phasercenter)""", { it.cutoff }, 1000.0, s("bd sd").phasercenter(1000).lpf(phasercenter)),
        row("phasersweep", """s("bd sd").phasersweep(2000).phasercenter(phasersweep)""", { it.phaserCenter }, 2000.0, s("bd sd").phasersweep(2000).phasercenter(phasersweep)),
        row("tremolosync", """s("bd sd").tremolosync(4).phaser(tremolosync)""", { it.phaserRate }, 4.0, s("bd sd").tremolosync(4).phaser(tremolosync)),
        row("tremolodepth", """s("bd sd").tremolodepth(0.5).pan(tremolodepth)""", { it.pan }, 0.5, s("bd sd").tremolodepth(0.5).pan(tremolodepth)),
        row("tremoloskew", """s("bd sd").tremoloskew(0.5).tremolophase(tremoloskew)""", { it.tremoloPhase }, 0.5, s("bd sd").tremoloskew(0.5).tremolophase(tremoloskew)),
        row("tremolophase", """s("bd sd").tremolophase(0.25).tremoloskew(tremolophase)""", { it.tremoloSkew }, 0.25, s("bd sd").tremolophase(0.25).tremoloskew(tremolophase)),
        row("delaycap", """s("bd sd").delaycap(0.5).pan(delaycap)""", { it.pan }, 0.5, s("bd sd").delaycap(0.5).pan(delaycap)),
    )

    // Every alias is a constant of the canonical object: it sets the canonical field and reads it bare.
    val aliasSets = listOf(
        row("vel", """s("bd sd").apply(vel(2))""", { it.velocity }, 2.0, s("bd sd").apply(vel(2))),
        row("lowpass", """s("bd sd").apply(lowpass(2))""", { it.cutoff }, 2.0, s("bd sd").apply(lowpass(2))),
        row("highpass", """s("bd sd").apply(highpass(2))""", { it.hcutoff }, 2.0, s("bd sd").apply(highpass(2))),
        row("bandpass", """s("bd sd").apply(bandpass(2))""", { it.bandf }, 2.0, s("bd sd").apply(bandpass(2))),
        row("dist", """s("bd sd").apply(dist(2))""", { it.distort }, 2.0, s("bd sd").apply(dist(2))),
        row("distortOversampling", """s("bd sd").apply(distortOversampling(2))""", { it.distortOversample?.toDouble() }, 2.0, s("bd sd").apply(distortOversampling(2))),
        row("crushOversampling", """s("bd sd").apply(crushOversampling(2))""", { it.crushOversample?.toDouble() }, 2.0, s("bd sd").apply(crushOversampling(2))),
        row("coarseOversampling", """s("bd sd").apply(coarseOversampling(2))""", { it.coarseOversample?.toDouble() }, 2.0, s("bd sd").apply(coarseOversampling(2))),
        row("rsize", """s("bd sd").apply(rsize(2))""", { it.roomSize }, 2.0, s("bd sd").apply(rsize(2))),
        row("sz", """s("bd sd").apply(sz(2))""", { it.roomSize }, 2.0, s("bd sd").apply(sz(2))),
        row("size", """s("bd sd").apply(size(2))""", { it.roomSize }, 2.0, s("bd sd").apply(size(2))),
        row("rfade", """s("bd sd").apply(rfade(2))""", { it.roomFade }, 2.0, s("bd sd").apply(rfade(2))),
        row("rlp", """s("bd sd").apply(rlp(2))""", { it.roomLp }, 2.0, s("bd sd").apply(rlp(2))),
        row("rdim", """s("bd sd").apply(rdim(2))""", { it.roomDim }, 2.0, s("bd sd").apply(rdim(2))),
        row("delayfb", """s("bd sd").apply(delayfb(2))""", { it.delayFeedback }, 2.0, s("bd sd").apply(delayfb(2))),
        row("dfb", """s("bd sd").apply(dfb(2))""", { it.delayFeedback }, 2.0, s("bd sd").apply(dfb(2))),
        row("ph", """s("bd sd").apply(ph(2))""", { it.phaserRate }, 2.0, s("bd sd").apply(ph(2))),
        row("phc", """s("bd sd").apply(phc(2))""", { it.phaserCenter }, 2.0, s("bd sd").apply(phc(2))),
        row("phs", """s("bd sd").apply(phs(2))""", { it.phaserSweep }, 2.0, s("bd sd").apply(phs(2))),
        row("tremsync", """s("bd sd").apply(tremsync(2))""", { it.tremoloSync }, 2.0, s("bd sd").apply(tremsync(2))),
        row("tremdepth", """s("bd sd").apply(tremdepth(2))""", { it.tremoloDepth }, 2.0, s("bd sd").apply(tremdepth(2))),
        row("tremskew", """s("bd sd").apply(tremskew(2))""", { it.tremoloSkew }, 2.0, s("bd sd").apply(tremskew(2))),
        row("tremphase", """s("bd sd").apply(tremphase(2))""", { it.tremoloPhase }, 2.0, s("bd sd").apply(tremphase(2))),
        row("dcap", """s("bd sd").apply(dcap(2))""", { it.delayCap }, 2.0, s("bd sd").apply(dcap(2))),
    )

    val aliasReads = listOf(
        row("vel", """s("bd sd").vel(2).pan(vel)""", { it.pan }, 2.0, s("bd sd").vel(2).pan(vel)),
        row("lowpass", """s("bd sd").lowpass(2).pan(lowpass)""", { it.pan }, 2.0, s("bd sd").lowpass(2).pan(lowpass)),
        row("highpass", """s("bd sd").highpass(2).pan(highpass)""", { it.pan }, 2.0, s("bd sd").highpass(2).pan(highpass)),
        row("bandpass", """s("bd sd").bandpass(2).pan(bandpass)""", { it.pan }, 2.0, s("bd sd").bandpass(2).pan(bandpass)),
        row("dist", """s("bd sd").dist(2).pan(dist)""", { it.pan }, 2.0, s("bd sd").dist(2).pan(dist)),
        row("distortOversampling", """s("bd sd").distortOversampling(2).pan(distortOversampling)""", { it.pan }, 2.0, s("bd sd").distortOversampling(2).pan(distortOversampling)),
        row("crushOversampling", """s("bd sd").crushOversampling(2).pan(crushOversampling)""", { it.pan }, 2.0, s("bd sd").crushOversampling(2).pan(crushOversampling)),
        row("coarseOversampling", """s("bd sd").coarseOversampling(2).pan(coarseOversampling)""", { it.pan }, 2.0, s("bd sd").coarseOversampling(2).pan(coarseOversampling)),
        row("rsize", """s("bd sd").rsize(2).pan(rsize)""", { it.pan }, 2.0, s("bd sd").rsize(2).pan(rsize)),
        row("sz", """s("bd sd").sz(2).pan(sz)""", { it.pan }, 2.0, s("bd sd").sz(2).pan(sz)),
        row("size", """s("bd sd").size(2).pan(size)""", { it.pan }, 2.0, s("bd sd").size(2).pan(size)),
        row("rfade", """s("bd sd").rfade(2).pan(rfade)""", { it.pan }, 2.0, s("bd sd").rfade(2).pan(rfade)),
        row("rlp", """s("bd sd").rlp(2).pan(rlp)""", { it.pan }, 2.0, s("bd sd").rlp(2).pan(rlp)),
        row("rdim", """s("bd sd").rdim(2).pan(rdim)""", { it.pan }, 2.0, s("bd sd").rdim(2).pan(rdim)),
        row("delayfb", """s("bd sd").delayfb(2).pan(delayfb)""", { it.pan }, 2.0, s("bd sd").delayfb(2).pan(delayfb)),
        row("dfb", """s("bd sd").dfb(2).pan(dfb)""", { it.pan }, 2.0, s("bd sd").dfb(2).pan(dfb)),
        row("ph", """s("bd sd").ph(2).pan(ph)""", { it.pan }, 2.0, s("bd sd").ph(2).pan(ph)),
        row("phc", """s("bd sd").phc(2).pan(phc)""", { it.pan }, 2.0, s("bd sd").phc(2).pan(phc)),
        row("phs", """s("bd sd").phs(2).pan(phs)""", { it.pan }, 2.0, s("bd sd").phs(2).pan(phs)),
        row("tremsync", """s("bd sd").tremsync(2).pan(tremsync)""", { it.pan }, 2.0, s("bd sd").tremsync(2).pan(tremsync)),
        row("tremdepth", """s("bd sd").tremdepth(2).pan(tremdepth)""", { it.pan }, 2.0, s("bd sd").tremdepth(2).pan(tremdepth)),
        row("tremskew", """s("bd sd").tremskew(2).pan(tremskew)""", { it.pan }, 2.0, s("bd sd").tremskew(2).pan(tremskew)),
        row("tremphase", """s("bd sd").tremphase(2).pan(tremphase)""", { it.pan }, 2.0, s("bd sd").tremphase(2).pan(tremphase)),
        row("dcap", """s("bd sd").dcap(2).pan(dcap)""", { it.pan }, 2.0, s("bd sd").dcap(2).pan(dcap)),
    )

    fun SprudelPattern.cycles() = (0 until 12).map { c -> queryArc(c.toDouble(), c + 1.0) }

    fun check(rows: List<Row>) {
        rows.forEach { r ->
            withClue("${r.accessor} | ${r.script}") {
                val kotlin = r.kotlin.cycles()
                val script = SprudelPattern.compile(r.script).shouldNotBeNull().cycles()

                listOf("kotlin" to kotlin, "script" to script).forEach { (door, cycles) ->
                    withClue(door) {
                        cycles.forEach { events ->
                            events shouldHaveSize 2
                            events.forEach { r.field(it.data).shouldNotBeNull() shouldBe (r.expected plusOrMinus 1e-9) }
                        }
                    }
                }

                // Door parity: same events, same values, in the same places.
                kotlin.map { events -> events.map { it.whole to r.field(it.data) } } shouldBe
                        script.map { events -> events.map { it.whole to r.field(it.data) } }
            }
        }
    }

    "a mapper argument applies to the setter's own field, in both doors" {
        check(mapped)
    }

    "the bare accessor reads its field into another setter, in both doors" {
        check(read)
    }

    "effects: a mapper argument applies to the setter's own field, in both doors" {
        check(mappedEffects)
    }

    "effects: the bare accessor reads its field into another setter, in both doors" {
        check(readEffects)
    }

    "every alias constant sets the canonical field through its call form and reads it bare, in both doors" {
        check(aliasSets)
        check(aliasReads)
    }

    "multi-parameter setters dispatch through the accessor's invoke in both doors" {
        fun both(kotlin: SprudelPattern, script: String, check: (SprudelVoiceData) -> Unit) {
            withClue(script) {
                listOf("kotlin" to kotlin, "script" to SprudelPattern.compile(script).shouldNotBeNull()).forEach { (door, p) ->
                    withClue(door) { p.cycles().forEach { events -> events shouldHaveSize 2; events.forEach { check(it.data) } } }
                }
            }
        }

        both(note("c e").apply(lpf(500, 8, 2)), """note("c e").apply(lpf(500, 8, 2))""") {
            it.cutoff shouldBe 500.0
            it.resonance shouldBe 8.0
            it.lpPasses shouldBe 2.0
        }
        both(note("c e").apply(hpf(300, 4)), """note("c e").apply(hpf(300, 4))""") {
            it.hcutoff shouldBe 300.0
            it.hresonance shouldBe 4.0
        }
        both(note("c e").apply(bpf(1000, 3)), """note("c e").apply(bpf(1000, 3))""") {
            it.bandf shouldBe 1000.0
            it.bandq shouldBe 3.0
        }
        // a mapper in the first slot next to a literal tail: only the first parameter takes a mapper
        both(note("c e").lpf(400).lpf(mul(2), 8), """note("c e").lpf(400).lpf(mul(2), 8)""") {
            it.cutoff shouldBe 800.0
            it.resonance shouldBe 8.0
        }
        both(note("c e").lpf(400).hpf(lpf.div(2), 4), """note("c e").lpf(400).hpf(lpf.div(2), 4)""") {
            it.hcutoff shouldBe 200.0
            it.hresonance shouldBe 4.0
        }
        both(note("c e").bpf(freq.mul(2), 3), """note("c e").bpf(freq.mul(2), 3)""") {
            it.bandf shouldBe (it.freqHz.shouldNotBeNull() * 2 plusOrMinus 1e-9)
            it.bandq shouldBe 3.0
        }
        // batch two: the compound effect doors, positional and named
        both(s("bd sd").apply(roomWet(0.3, 4)), """s("bd sd").apply(roomWet(0.3, 4))""") {
            it.room shouldBe 0.3
            it.roomSize shouldBe 4.0
        }
        both(s("bd sd").apply(roomWet(size = 4)), """s("bd sd").apply(roomWet(size = 4))""") {
            it.roomSize shouldBe 4.0
        }
        both(s("bd sd").apply(delayWet(0.3, 0.25, 0.4)), """s("bd sd").apply(delayWet(0.3, 0.25, 0.4))""") {
            it.delay shouldBe 0.3
            it.delayTime shouldBe 0.25
            it.delayFeedback shouldBe 0.4
        }
        both(s("bd sd").apply(phaser(0.5, 0.6, 1000, 2000)), """s("bd sd").apply(phaser(0.5, 0.6, 1000, 2000))""") {
            it.phaserRate shouldBe 0.5
            it.phaserDepth shouldBe 0.6
            it.phaserCenter shouldBe 1000.0
            it.phaserSweep shouldBe 2000.0
        }
        both(s("bd sd").apply(distort(amount = 0.5, oversample = 2)), """s("bd sd").apply(distort(amount = 0.5, oversample = 2))""") {
            it.distort shouldBe 0.5
            it.distortOversample shouldBe 2
        }
        // a named argument that skips the first parameter
        both(note("c e").lpf(700).apply(lpf(q = 6)), """note("c e").lpf(700).apply(lpf(q = 6))""") {
            it.cutoff shouldBe 700.0
            it.resonance shouldBe 6.0
        }
    }

    "an unset source field leaves the target unchanged | note(\"c\").gain(0.8).gain(velocity)" {
        note("c").gain(0.8).gain(velocity).cycles().forEach { events ->
            events shouldHaveSize 1
            events.single().data.gain shouldBe 0.8
        }
        SprudelPattern.compile("""note("c").gain(0.8).gain(velocity)""").shouldNotBeNull().cycles().forEach { events ->
            events.single().data.gain shouldBe 0.8
        }
    }
})
