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
import io.peekandpoke.klang.sprudel.lang.addons.nfa
import io.peekandpoke.klang.sprudel.lang.addons.analog
import io.peekandpoke.klang.sprudel.lang.addons.duty
import io.peekandpoke.klang.sprudel.lang.addons.onepole
import io.peekandpoke.klang.sprudel.lang.addons.nfattack
import io.peekandpoke.klang.sprudel.lang.addons.nfd
import io.peekandpoke.klang.sprudel.lang.addons.nfdecay
import io.peekandpoke.klang.sprudel.lang.addons.nfe
import io.peekandpoke.klang.sprudel.lang.addons.nfenv
import io.peekandpoke.klang.sprudel.lang.addons.nfr
import io.peekandpoke.klang.sprudel.lang.addons.nfrelease
import io.peekandpoke.klang.sprudel.lang.addons.nfs
import io.peekandpoke.klang.sprudel.lang.addons.nfsustain
import io.peekandpoke.klang.sprudel.lang.addons.notch
import io.peekandpoke.klang.sprudel.lang.addons.notchf
import io.peekandpoke.klang.sprudel.lang.addons.notchq
import io.peekandpoke.klang.sprudel.lang.addons.nresonance
import io.peekandpoke.klang.sprudel.lang.addons.ntf
import io.peekandpoke.klang.sprudel.lang.addons.ntq

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

    // Batch three: sample, synthesis, vowel, body, tonal, notch and filter envelope fields.
    val mappedBatchThree = listOf(
        row("begin", """s("bd sd").begin(0.25).begin(mul(2))""", { it.begin }, 0.5, s("bd sd").begin(0.25).begin(mul(2))),
        row("end", """s("bd sd").end(0.5).end(mul(2))""", { it.end }, 1.0, s("bd sd").end(0.5).end(mul(2))),
        row("speed", """s("bd sd").speed(1).speed(mul(2))""", { it.speed }, 2.0, s("bd sd").speed(1).speed(mul(2))),
        row("loopBegin", """s("bd sd").loopBegin(0.2).loopBegin(add(0.1))""", { it.loopBegin }, 0.3, s("bd sd").loopBegin(0.2).loopBegin(add(0.1))),
        row("loopEnd", """s("bd sd").loopEnd(0.5).loopEnd(mul(2))""", { it.loopEnd }, 1.0, s("bd sd").loopEnd(0.5).loopEnd(mul(2))),
        row("cut", """s("bd sd").cut(1).cut(add(1))""", { it.cut?.toDouble() }, 2.0, s("bd sd").cut(1).cut(add(1))),
        row("fmh", """s("bd sd").fmh(2).fmh(mul(2))""", { it.fmh }, 4.0, s("bd sd").fmh(2).fmh(mul(2))),
        row("fmattack", """s("bd sd").fmattack(0.1).fmattack(mul(2))""", { it.fmAttack }, 0.2, s("bd sd").fmattack(0.1).fmattack(mul(2))),
        row("fmdecay", """s("bd sd").fmdecay(0.2).fmdecay(mul(2))""", { it.fmDecay }, 0.4, s("bd sd").fmdecay(0.2).fmdecay(mul(2))),
        row("fmsustain", """s("bd sd").fmsustain(0.5).fmsustain(mul(0.5))""", { it.fmSustain }, 0.25, s("bd sd").fmsustain(0.5).fmsustain(mul(0.5))),
        row("vowelWet", """s("bd sd").vowelWet(0.5).vowelWet(mul(0.5))""", { it.vowelMix }, 0.25, s("bd sd").vowelWet(0.5).vowelWet(mul(0.5))),
        row("vowelFloor", """s("bd sd").vowelFloor(0.2).vowelFloor(add(0.3))""", { it.vowelFloor }, 0.5, s("bd sd").vowelFloor(0.2).vowelFloor(add(0.3))),
        row("bodyWet", """s("bd sd").bodyWet(0.4).bodyWet(mul(2))""", { it.bodyMix }, 0.8, s("bd sd").bodyWet(0.4).bodyWet(mul(2))),
        row("bodyFloor", """s("bd sd").bodyFloor(0.2).bodyFloor(add(0.3))""", { it.bodyFloor }, 0.5, s("bd sd").bodyFloor(0.2).bodyFloor(add(0.3))),
        row("legato", """s("bd sd").legato(0.8).legato(mul(2))""", { it.legato }, 1.6, s("bd sd").legato(0.8).legato(mul(2))),
        row("vibrato", """s("bd sd").vibrato(5).vibrato(mul(2))""", { it.vibrato }, 10.0, s("bd sd").vibrato(5).vibrato(mul(2))),
        row("vibratoMod", """s("bd sd").vibratoMod(0.3).vibratoMod(mul(2))""", { it.vibratoMod }, 0.6, s("bd sd").vibratoMod(0.3).vibratoMod(mul(2))),
        row("pattack", """s("bd sd").pattack(0.1).pattack(mul(2))""", { it.pAttack }, 0.2, s("bd sd").pattack(0.1).pattack(mul(2))),
        row("pdecay", """s("bd sd").pdecay(0.2).pdecay(mul(2))""", { it.pDecay }, 0.4, s("bd sd").pdecay(0.2).pdecay(mul(2))),
        row("prelease", """s("bd sd").prelease(0.3).prelease(mul(2))""", { it.pRelease }, 0.6, s("bd sd").prelease(0.3).prelease(mul(2))),
        row("penv", """s("bd sd").penv(12).penv(mul(2))""", { it.pEnv }, 24.0, s("bd sd").penv(12).penv(mul(2))),
        row("pcurve", """s("bd sd").pcurve(1).pcurve(add(1))""", { it.pCurve }, 2.0, s("bd sd").pcurve(1).pcurve(add(1))),
        row("panchor", """s("bd sd").panchor(0.5).panchor(mul(2))""", { it.pAnchor }, 1.0, s("bd sd").panchor(0.5).panchor(mul(2))),
        row("accelerate", """s("bd sd").accelerate(2).accelerate(mul(2))""", { it.accelerate }, 4.0, s("bd sd").accelerate(2).accelerate(mul(2))),
        row("notchf", """s("bd sd").notchf(1000).notchf(mul(2))""", { it.notchf }, 2000.0, s("bd sd").notchf(1000).notchf(mul(2))),
        row("nresonance", """s("bd sd").nresonance(4).nresonance(mul(2))""", { it.nresonance }, 8.0, s("bd sd").nresonance(4).nresonance(mul(2))),
        row("nfattack", """s("bd sd").nfattack(0.1).nfattack(mul(2))""", { it.nfattack }, 0.2, s("bd sd").nfattack(0.1).nfattack(mul(2))),
        row("nfdecay", """s("bd sd").nfdecay(0.2).nfdecay(mul(2))""", { it.nfdecay }, 0.4, s("bd sd").nfdecay(0.2).nfdecay(mul(2))),
        row("nfsustain", """s("bd sd").nfsustain(0.5).nfsustain(mul(0.5))""", { it.nfsustain }, 0.25, s("bd sd").nfsustain(0.5).nfsustain(mul(0.5))),
        row("nfrelease", """s("bd sd").nfrelease(0.3).nfrelease(mul(2))""", { it.nfrelease }, 0.6, s("bd sd").nfrelease(0.3).nfrelease(mul(2))),
        row("nfenv", """s("bd sd").nfenv(12).nfenv(mul(2))""", { it.nfenv }, 24.0, s("bd sd").nfenv(12).nfenv(mul(2))),
        row("lpe", """s("bd sd").lpe(12).lpe(mul(2))""", { it.lpenv }, 24.0, s("bd sd").lpe(12).lpe(mul(2))),
        row("lpx", """s("bd sd").lpx(1).lpx(add(1))""", { it.lpPasses }, 2.0, s("bd sd").lpx(1).lpx(add(1))),
        row("hpe", """s("bd sd").hpe(12).hpe(mul(2))""", { it.hpenv }, 24.0, s("bd sd").hpe(12).hpe(mul(2))),
        row("hpx", """s("bd sd").hpx(1).hpx(add(1))""", { it.hpPasses }, 2.0, s("bd sd").hpx(1).hpx(add(1))),
        row("bpe", """s("bd sd").bpe(12).bpe(mul(2))""", { it.bpenv }, 24.0, s("bd sd").bpe(12).bpe(mul(2))),
    )

    val readBatchThree = listOf(
        row("begin", """s("bd sd").begin(0.25).pan(begin)""", { it.pan }, 0.25, s("bd sd").begin(0.25).pan(begin)),
        row("end", """s("bd sd").end(0.5).pan(end)""", { it.pan }, 0.5, s("bd sd").end(0.5).pan(end)),
        row("speed", """s("bd sd").speed(1).pan(speed)""", { it.pan }, 1.0, s("bd sd").speed(1).pan(speed)),
        row("loopBegin", """s("bd sd").loopBegin(0.2).pan(loopBegin)""", { it.pan }, 0.2, s("bd sd").loopBegin(0.2).pan(loopBegin)),
        row("loopEnd", """s("bd sd").loopEnd(0.5).pan(loopEnd)""", { it.pan }, 0.5, s("bd sd").loopEnd(0.5).pan(loopEnd)),
        row("cut", """s("bd sd").cut(1).pan(cut)""", { it.pan }, 1.0, s("bd sd").cut(1).pan(cut)),
        row("fmh", """s("bd sd").fmh(2).pan(fmh)""", { it.pan }, 2.0, s("bd sd").fmh(2).pan(fmh)),
        row("fmattack", """s("bd sd").fmattack(0.1).pan(fmattack)""", { it.pan }, 0.1, s("bd sd").fmattack(0.1).pan(fmattack)),
        row("fmdecay", """s("bd sd").fmdecay(0.2).pan(fmdecay)""", { it.pan }, 0.2, s("bd sd").fmdecay(0.2).pan(fmdecay)),
        row("fmsustain", """s("bd sd").fmsustain(0.5).pan(fmsustain)""", { it.pan }, 0.5, s("bd sd").fmsustain(0.5).pan(fmsustain)),
        row("vowelWet", """s("bd sd").vowelWet(0.5).pan(vowelWet)""", { it.pan }, 0.5, s("bd sd").vowelWet(0.5).pan(vowelWet)),
        row("vowelFloor", """s("bd sd").vowelFloor(0.2).pan(vowelFloor)""", { it.pan }, 0.2, s("bd sd").vowelFloor(0.2).pan(vowelFloor)),
        row("bodyWet", """s("bd sd").bodyWet(0.4).pan(bodyWet)""", { it.pan }, 0.4, s("bd sd").bodyWet(0.4).pan(bodyWet)),
        row("bodyFloor", """s("bd sd").bodyFloor(0.2).pan(bodyFloor)""", { it.pan }, 0.2, s("bd sd").bodyFloor(0.2).pan(bodyFloor)),
        row("legato", """s("bd sd").legato(0.8).pan(legato)""", { it.pan }, 0.8, s("bd sd").legato(0.8).pan(legato)),
        row("vibrato", """s("bd sd").vibrato(5).pan(vibrato)""", { it.pan }, 5.0, s("bd sd").vibrato(5).pan(vibrato)),
        row("vibratoMod", """s("bd sd").vibratoMod(0.3).pan(vibratoMod)""", { it.pan }, 0.3, s("bd sd").vibratoMod(0.3).pan(vibratoMod)),
        row("pattack", """s("bd sd").pattack(0.1).pan(pattack)""", { it.pan }, 0.1, s("bd sd").pattack(0.1).pan(pattack)),
        row("pdecay", """s("bd sd").pdecay(0.2).pan(pdecay)""", { it.pan }, 0.2, s("bd sd").pdecay(0.2).pan(pdecay)),
        row("prelease", """s("bd sd").prelease(0.3).pan(prelease)""", { it.pan }, 0.3, s("bd sd").prelease(0.3).pan(prelease)),
        row("penv", """s("bd sd").penv(12).pan(penv)""", { it.pan }, 12.0, s("bd sd").penv(12).pan(penv)),
        row("pcurve", """s("bd sd").pcurve(1).pan(pcurve)""", { it.pan }, 1.0, s("bd sd").pcurve(1).pan(pcurve)),
        row("panchor", """s("bd sd").panchor(0.5).pan(panchor)""", { it.pan }, 0.5, s("bd sd").panchor(0.5).pan(panchor)),
        row("accelerate", """s("bd sd").accelerate(2).pan(accelerate)""", { it.pan }, 2.0, s("bd sd").accelerate(2).pan(accelerate)),
        row("notchf", """s("bd sd").notchf(1000).pan(notchf)""", { it.pan }, 1000.0, s("bd sd").notchf(1000).pan(notchf)),
        row("nresonance", """s("bd sd").nresonance(4).pan(nresonance)""", { it.pan }, 4.0, s("bd sd").nresonance(4).pan(nresonance)),
        row("nfattack", """s("bd sd").nfattack(0.1).pan(nfattack)""", { it.pan }, 0.1, s("bd sd").nfattack(0.1).pan(nfattack)),
        row("nfdecay", """s("bd sd").nfdecay(0.2).pan(nfdecay)""", { it.pan }, 0.2, s("bd sd").nfdecay(0.2).pan(nfdecay)),
        row("nfsustain", """s("bd sd").nfsustain(0.5).pan(nfsustain)""", { it.pan }, 0.5, s("bd sd").nfsustain(0.5).pan(nfsustain)),
        row("nfrelease", """s("bd sd").nfrelease(0.3).pan(nfrelease)""", { it.pan }, 0.3, s("bd sd").nfrelease(0.3).pan(nfrelease)),
        row("nfenv", """s("bd sd").nfenv(12).pan(nfenv)""", { it.pan }, 12.0, s("bd sd").nfenv(12).pan(nfenv)),
        row("lpe", """s("bd sd").lpe(12).pan(lpe)""", { it.pan }, 12.0, s("bd sd").lpe(12).pan(lpe)),
        row("lpx", """s("bd sd").lpx(1).pan(lpx)""", { it.pan }, 1.0, s("bd sd").lpx(1).pan(lpx)),
        row("hpe", """s("bd sd").hpe(12).pan(hpe)""", { it.pan }, 12.0, s("bd sd").hpe(12).pan(hpe)),
        row("hpx", """s("bd sd").hpx(1).pan(hpx)""", { it.pan }, 1.0, s("bd sd").hpx(1).pan(hpx)),
        row("bpe", """s("bd sd").bpe(12).pan(bpe)""", { it.pan }, 12.0, s("bd sd").bpe(12).pan(bpe)),
    )

    val aliasSetsBatchThree = listOf(
        row("clip", """s("bd sd").apply(clip(2))""", { it.legato }, 2.0, s("bd sd").apply(clip(2))),
        row("vib", """s("bd sd").apply(vib(2))""", { it.vibrato }, 2.0, s("bd sd").apply(vib(2))),
        row("patt", """s("bd sd").apply(patt(2))""", { it.pAttack }, 2.0, s("bd sd").apply(patt(2))),
        row("pdec", """s("bd sd").apply(pdec(2))""", { it.pDecay }, 2.0, s("bd sd").apply(pdec(2))),
        row("prel", """s("bd sd").apply(prel(2))""", { it.pRelease }, 2.0, s("bd sd").apply(prel(2))),
        row("pamt", """s("bd sd").apply(pamt(2))""", { it.pEnv }, 2.0, s("bd sd").apply(pamt(2))),
        row("pcrv", """s("bd sd").apply(pcrv(2))""", { it.pCurve }, 2.0, s("bd sd").apply(pcrv(2))),
        row("panc", """s("bd sd").apply(panc(2))""", { it.pAnchor }, 2.0, s("bd sd").apply(panc(2))),
        row("loopb", """s("bd sd").apply(loopb(2))""", { it.loopBegin }, 2.0, s("bd sd").apply(loopb(2))),
        row("loope", """s("bd sd").apply(loope(2))""", { it.loopEnd }, 2.0, s("bd sd").apply(loope(2))),
        row("fmatt", """s("bd sd").apply(fmatt(2))""", { it.fmAttack }, 2.0, s("bd sd").apply(fmatt(2))),
        row("fmdec", """s("bd sd").apply(fmdec(2))""", { it.fmDecay }, 2.0, s("bd sd").apply(fmdec(2))),
        row("fmsus", """s("bd sd").apply(fmsus(2))""", { it.fmSustain }, 2.0, s("bd sd").apply(fmsus(2))),
        row("notch", """s("bd sd").apply(notch(2))""", { it.notchf }, 2.0, s("bd sd").apply(notch(2))),
        row("ntf", """s("bd sd").apply(ntf(2))""", { it.notchf }, 2.0, s("bd sd").apply(ntf(2))),
        row("notchq", """s("bd sd").apply(notchq(2))""", { it.nresonance }, 2.0, s("bd sd").apply(notchq(2))),
        row("ntq", """s("bd sd").apply(ntq(2))""", { it.nresonance }, 2.0, s("bd sd").apply(ntq(2))),
        row("nfa", """s("bd sd").apply(nfa(2))""", { it.nfattack }, 2.0, s("bd sd").apply(nfa(2))),
        row("nfd", """s("bd sd").apply(nfd(2))""", { it.nfdecay }, 2.0, s("bd sd").apply(nfd(2))),
        row("nfs", """s("bd sd").apply(nfs(2))""", { it.nfsustain }, 2.0, s("bd sd").apply(nfs(2))),
        row("nfr", """s("bd sd").apply(nfr(2))""", { it.nfrelease }, 2.0, s("bd sd").apply(nfr(2))),
        row("nfe", """s("bd sd").apply(nfe(2))""", { it.nfenv }, 2.0, s("bd sd").apply(nfe(2))),
    )

    val aliasReadsBatchThree = listOf(
        row("clip", """s("bd sd").legato(2).pan(clip)""", { it.pan }, 2.0, s("bd sd").legato(2).pan(clip)),
        row("vib", """s("bd sd").vibrato(2).pan(vib)""", { it.pan }, 2.0, s("bd sd").vibrato(2).pan(vib)),
        row("patt", """s("bd sd").pattack(2).pan(patt)""", { it.pan }, 2.0, s("bd sd").pattack(2).pan(patt)),
        row("pdec", """s("bd sd").pdecay(2).pan(pdec)""", { it.pan }, 2.0, s("bd sd").pdecay(2).pan(pdec)),
        row("prel", """s("bd sd").prelease(2).pan(prel)""", { it.pan }, 2.0, s("bd sd").prelease(2).pan(prel)),
        row("pamt", """s("bd sd").penv(2).pan(pamt)""", { it.pan }, 2.0, s("bd sd").penv(2).pan(pamt)),
        row("pcrv", """s("bd sd").pcurve(2).pan(pcrv)""", { it.pan }, 2.0, s("bd sd").pcurve(2).pan(pcrv)),
        row("panc", """s("bd sd").panchor(2).pan(panc)""", { it.pan }, 2.0, s("bd sd").panchor(2).pan(panc)),
        row("loopb", """s("bd sd").loopBegin(2).pan(loopb)""", { it.pan }, 2.0, s("bd sd").loopBegin(2).pan(loopb)),
        row("loope", """s("bd sd").loopEnd(2).pan(loope)""", { it.pan }, 2.0, s("bd sd").loopEnd(2).pan(loope)),
        row("fmatt", """s("bd sd").fmattack(2).pan(fmatt)""", { it.pan }, 2.0, s("bd sd").fmattack(2).pan(fmatt)),
        row("fmdec", """s("bd sd").fmdecay(2).pan(fmdec)""", { it.pan }, 2.0, s("bd sd").fmdecay(2).pan(fmdec)),
        row("fmsus", """s("bd sd").fmsustain(2).pan(fmsus)""", { it.pan }, 2.0, s("bd sd").fmsustain(2).pan(fmsus)),
        row("notch", """s("bd sd").notchf(2).pan(notch)""", { it.pan }, 2.0, s("bd sd").notchf(2).pan(notch)),
        row("ntf", """s("bd sd").notchf(2).pan(ntf)""", { it.pan }, 2.0, s("bd sd").notchf(2).pan(ntf)),
        row("notchq", """s("bd sd").nresonance(2).pan(notchq)""", { it.pan }, 2.0, s("bd sd").nresonance(2).pan(notchq)),
        row("ntq", """s("bd sd").nresonance(2).pan(ntq)""", { it.pan }, 2.0, s("bd sd").nresonance(2).pan(ntq)),
        row("nfa", """s("bd sd").nfattack(2).pan(nfa)""", { it.pan }, 2.0, s("bd sd").nfattack(2).pan(nfa)),
        row("nfd", """s("bd sd").nfdecay(2).pan(nfd)""", { it.pan }, 2.0, s("bd sd").nfdecay(2).pan(nfd)),
        row("nfs", """s("bd sd").nfsustain(2).pan(nfs)""", { it.pan }, 2.0, s("bd sd").nfsustain(2).pan(nfs)),
        row("nfr", """s("bd sd").nfrelease(2).pan(nfr)""", { it.pan }, 2.0, s("bd sd").nfrelease(2).pan(nfr)),
        row("nfe", """s("bd sd").nfenv(2).pan(nfe)""", { it.pan }, 2.0, s("bd sd").nfenv(2).pan(nfe)),
    )

    // Batch four: the dynamics leftovers, the routing fields, the compressor threshold and fmenv.
    val mappedBatchFour = listOf(
        row("unison", """s("bd sd").unison(3).unison(mul(2))""", { it.oscParams?.get("voices") }, 6.0, s("bd sd").unison(3).unison(mul(2))),
        row("spread", """s("bd sd").spread(0.2).spread(mul(2))""", { it.oscParams?.get("spread") }, 0.4, s("bd sd").spread(0.2).spread(mul(2))),
        row("panSpread", """s("bd sd").panSpread(0.5).panSpread(mul(2))""", { it.oscParams?.get("panSpread") }, 1.0, s("bd sd").panSpread(0.5).panSpread(mul(2))),
        row("density", """s("bd sd").density(0.5).density(mul(2))""", { it.oscParams?.get("density") }, 1.0, s("bd sd").density(0.5).density(mul(2))),
        row("orbit", """s("bd sd").orbit(1).orbit(add(1))""", { it.cylinder?.toDouble() }, 2.0, s("bd sd").orbit(1).orbit(add(1))),
        row("duckorbit", """s("bd sd").duckorbit(1).duckorbit(add(1))""", { it.duckCylinder?.toDouble() }, 2.0, s("bd sd").duckorbit(1).duckorbit(add(1))),
        row("duckattack", """s("bd sd").duckattack(0.05).duckattack(mul(2))""", { it.duckAttack }, 0.1, s("bd sd").duckattack(0.05).duckattack(mul(2))),
        row("duckdepth", """s("bd sd").duckdepth(0.5).duckdepth(mul(2))""", { it.duckDepth }, 1.0, s("bd sd").duckdepth(0.5).duckdepth(mul(2))),
        row("compressor", """s("bd sd").compressor(-12).compressor(add(-6))""", { it.compressorThreshold }, -18.0, s("bd sd").compressor(-12).compressor(add(-6))),
        row("fmenv", """s("bd sd").fmenv(200).fmenv(mul(2))""", { it.fmEnv }, 400.0, s("bd sd").fmenv(200).fmenv(mul(2))),
        row("analog", """s("bd sd").analog(2).analog(mul(2))""", { it.oscParams?.get("analog") }, 4.0, s("bd sd").analog(2).analog(mul(2))),
        row("duty", """s("bd sd").duty(0.25).duty(mul(2))""", { it.oscParams?.get("duty") }, 0.5, s("bd sd").duty(0.25).duty(mul(2))),
        row("onepole", """s("bd sd").onepole(1000).onepole(mul(2))""", { it.oscParams?.get("onepole") }, 2000.0, s("bd sd").onepole(1000).onepole(mul(2))),
    )

    val readBatchFour = listOf(
        row("unison", """s("bd sd").unison(3).pan(unison)""", { it.pan }, 3.0, s("bd sd").unison(3).pan(unison)),
        row("spread", """s("bd sd").spread(0.2).pan(spread)""", { it.pan }, 0.2, s("bd sd").spread(0.2).pan(spread)),
        row("panSpread", """s("bd sd").panSpread(0.5).pan(panSpread)""", { it.pan }, 0.5, s("bd sd").panSpread(0.5).pan(panSpread)),
        row("density", """s("bd sd").density(0.5).pan(density)""", { it.pan }, 0.5, s("bd sd").density(0.5).pan(density)),
        row("orbit", """s("bd sd").orbit(1).pan(orbit)""", { it.pan }, 1.0, s("bd sd").orbit(1).pan(orbit)),
        row("duckorbit", """s("bd sd").duckorbit(1).pan(duckorbit)""", { it.pan }, 1.0, s("bd sd").duckorbit(1).pan(duckorbit)),
        row("duckattack", """s("bd sd").duckattack(0.05).pan(duckattack)""", { it.pan }, 0.05, s("bd sd").duckattack(0.05).pan(duckattack)),
        row("duckdepth", """s("bd sd").duckdepth(0.5).pan(duckdepth)""", { it.pan }, 0.5, s("bd sd").duckdepth(0.5).pan(duckdepth)),
        row("compressor", """s("bd sd").compressor(-12).pan(compressor)""", { it.pan }, -12.0, s("bd sd").compressor(-12).pan(compressor)),
        row("fmenv", """s("bd sd").fmenv(200).pan(fmenv)""", { it.pan }, 200.0, s("bd sd").fmenv(200).pan(fmenv)),
        row("analog", """s("bd sd").analog(2).pan(analog)""", { it.pan }, 2.0, s("bd sd").analog(2).pan(analog)),
        row("duty", """s("bd sd").duty(0.25).pan(duty)""", { it.pan }, 0.25, s("bd sd").duty(0.25).pan(duty)),
        row("onepole", """s("bd sd").onepole(1000).pan(onepole)""", { it.pan }, 1000.0, s("bd sd").onepole(1000).pan(onepole)),
    )

    val aliasSetsBatchFour = listOf(
        row("uni", """s("bd sd").apply(uni(2))""", { it.oscParams?.get("voices") }, 2.0, s("bd sd").apply(uni(2))),
        row("voices", """s("bd sd").apply(voices(2))""", { it.oscParams?.get("voices") }, 2.0, s("bd sd").apply(voices(2))),
        row("d", """s("bd sd").apply(d(2))""", { it.oscParams?.get("density") }, 2.0, s("bd sd").apply(d(2))),
        row("o", """s("bd sd").apply(o(2))""", { it.cylinder?.toDouble() }, 2.0, s("bd sd").apply(o(2))),
        row("duck", """s("bd sd").apply(duck(2))""", { it.duckCylinder?.toDouble() }, 2.0, s("bd sd").apply(duck(2))),
        row("duckatt", """s("bd sd").apply(duckatt(2))""", { it.duckAttack }, 2.0, s("bd sd").apply(duckatt(2))),
        row("comp", """s("bd sd").apply(comp(2))""", { it.compressorThreshold }, 2.0, s("bd sd").apply(comp(2))),
    )

    val aliasReadsBatchFour = listOf(
        row("uni", """s("bd sd").unison(2).pan(uni)""", { it.pan }, 2.0, s("bd sd").unison(2).pan(uni)),
        row("voices", """s("bd sd").unison(2).pan(voices)""", { it.pan }, 2.0, s("bd sd").unison(2).pan(voices)),
        row("d", """s("bd sd").density(2).pan(d)""", { it.pan }, 2.0, s("bd sd").density(2).pan(d)),
        row("o", """s("bd sd").orbit(2).pan(o)""", { it.pan }, 2.0, s("bd sd").orbit(2).pan(o)),
        row("duck", """s("bd sd").duckorbit(2).pan(duck)""", { it.pan }, 2.0, s("bd sd").duckorbit(2).pan(duck)),
        row("duckatt", """s("bd sd").duckattack(2).pan(duckatt)""", { it.pan }, 2.0, s("bd sd").duckattack(2).pan(duckatt)),
        row("comp", """s("bd sd").compressor(2).pan(comp)""", { it.pan }, 2.0, s("bd sd").compressor(2).pan(comp)),
        row("fmmod", """s("bd sd").fmenv(2).pan(fmmod)""", { it.pan }, 2.0, s("bd sd").fmenv(2).pan(fmmod)),
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

    "batch three: a mapper argument applies to the setter's own field, in both doors" {
        check(mappedBatchThree)
    }

    "batch three: the bare accessor reads its field into another setter, in both doors" {
        check(readBatchThree)
    }

    "batch three: every alias constant sets and reads like its canonical object, in both doors" {
        check(aliasSetsBatchThree)
        check(aliasReadsBatchThree)
    }

    "batch four: a mapper argument applies to the setter's own field, in both doors" {
        check(mappedBatchFour)
    }

    "batch four: the bare accessor reads its field into another setter, in both doors" {
        check(readBatchFour)
    }

    "batch four: every alias constant sets and reads like its canonical object, in both doors" {
        check(aliasSetsBatchFour)
        check(aliasReadsBatchFour)
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
        // batch three: the notch door and its alias
        both(s("bd sd").apply(notchf(1000, 5)), """s("bd sd").apply(notchf(1000, 5))""") {
            it.notchf shouldBe 1000.0
            it.nresonance shouldBe 5.0
        }
        both(s("bd sd").notchf(1000).apply(notchf(mul(2), 5)), """s("bd sd").notchf(1000).apply(notchf(mul(2), 5))""") {
            it.notchf shouldBe 2000.0
            it.nresonance shouldBe 5.0
        }
        both(s("bd sd").apply(notch(1000, 5)), """s("bd sd").apply(notch(1000, 5))""") {
            it.notchf shouldBe 1000.0
            it.nresonance shouldBe 5.0
        }
        both(s("bd sd").apply(compressor(-12, 4, 6)), """s("bd sd").apply(compressor(-12, 4, 6))""") {
            it.compressorThreshold shouldBe -12.0
            it.compressorRatio shouldBe 4.0
            it.compressorKnee shouldBe 6.0
        }
        // a named argument that skips the first parameter
        both(note("c e").lpf(700).apply(lpf(q = 6)), """note("c e").lpf(700).apply(lpf(q = 6))""") {
            it.cutoff shouldBe 700.0
            it.resonance shouldBe 6.0
        }
    }

    "fmenv and fmmod, whose call form builds a control pattern, dispatch through invoke in the script door" {
        listOf("fmenv" to fmenv("0.3 0.7"), "fmmod" to fmmod("0.3 0.7")).forEach { (name, kotlin) ->
            withClue(name) {
                val script = SprudelPattern.compile("""$name("0.3 0.7")""").shouldNotBeNull()
                kotlin.cycles().map { events -> events.map { it.whole to it.data.fmEnv } } shouldBe
                        script.cycles().map { events -> events.map { it.whole to it.data.fmEnv } }
                kotlin.cycles().first().map { it.data.fmEnv } shouldBe listOf(0.3, 0.7)
            }
        }
    }

    "adsr: a mapper on one slot leaves the other slots alone, in both doors" {
        listOf(
            "kotlin" to note("c e").adsr(0.01, 0.2, 0.7, 0.5).adsr(attack = mul(10)),
            "script" to SprudelPattern.compile("""note("c e").adsr(0.01, 0.2, 0.7, 0.5).adsr(attack = mul(10))""").shouldNotBeNull(),
        ).forEach { (door, p) ->
            withClue(door) {
                p.cycles().forEach { events ->
                    events shouldHaveSize 2
                    events.forEach {
                        it.data.attack shouldBe (0.1 plusOrMinus 1e-9)
                        it.data.decay shouldBe 0.2
                        it.data.sustain shouldBe 0.7
                        it.data.release shouldBe 0.5
                    }
                }
            }
        }
    }

    "adsr: a mapper on each slot reads that slot's own value, in both doors" {
        class Case(val name: String, val kotlin: SprudelPattern, val script: String, val field: (SprudelVoiceData) -> Double?, val expected: Double)

        listOf(
            Case("decay = add(0.1)", note("c e").adsr(0.1, 0.2, 0.7, 0.5).adsr(decay = add(0.1)),
                """note("c e").adsr(0.1, 0.2, 0.7, 0.5).adsr(decay = add(0.1))""", { it.decay }, 0.3),
            Case("sustain = mul(0.5)", note("c e").adsr(0.1, 0.2, 0.7, 0.5).adsr(sustain = mul(0.5)),
                """note("c e").adsr(0.1, 0.2, 0.7, 0.5).adsr(sustain = mul(0.5))""", { it.sustain }, 0.35),
            Case("release = mul(0.5)", note("c e").adsr(0.1, 0.2, 0.7, 0.5).adsr(release = mul(0.5)),
                """note("c e").adsr(0.1, 0.2, 0.7, 0.5).adsr(release = mul(0.5))""", { it.release }, 0.25),
        ).forEach { c ->
            withClue(c.name) {
                listOf(c.kotlin, SprudelPattern.compile(c.script).shouldNotBeNull()).forEach { p ->
                    p.cycles().forEach { events ->
                        events shouldHaveSize 2
                        events.forEach {
                            c.field(it.data).shouldNotBeNull() shouldBe (c.expected plusOrMinus 1e-9)
                            it.data.attack shouldBe 0.1
                        }
                    }
                }
            }
        }
    }

    "adsr: every slot takes a control pattern and a continuous pattern per event, in both doors" {
        val fields = mapOf<String, (SprudelVoiceData) -> Double?>("attack" to { it.attack }, "decay" to { it.decay }, "sustain" to { it.sustain }, "release" to { it.release })

        fields.forEach { (slot, field) ->
            withClue("$slot control pattern") {
                val script = SprudelPattern.compile("""note("a b").adsr($slot = "0.1 0.5")""").shouldNotBeNull()
                val kotlin = when (slot) {
                    "attack" -> note("a b").adsr(attack = "0.1 0.5")
                    "decay" -> note("a b").adsr(decay = "0.1 0.5")
                    "sustain" -> note("a b").adsr(sustain = "0.1 0.5")
                    else -> note("a b").adsr(release = "0.1 0.5")
                }
                listOf(kotlin, script).forEach { p ->
                    p.cycles().forEach { events -> events.map { field(it.data) } shouldBe listOf(0.1, 0.5) }
                }
            }
        }

        withClue("attack follows a continuous pattern per event") {
            listOf(note("a b c d").adsr(attack = sine), SprudelPattern.compile("""note("a b c d").adsr(attack = sine)""").shouldNotBeNull()).forEach { p ->
                val values = p.queryArc(0.0, 1.0).map { it.data.attack.shouldNotBeNull() }
                values[0] shouldBe (0.5 plusOrMinus 1e-9)
                values[1] shouldBe (1.0 plusOrMinus 1e-9)
                values[2] shouldBe (0.5 plusOrMinus 1e-9)
                values[3] shouldBe (0.0 plusOrMinus 1e-9)
            }
        }
    }

    "adsr: the slots read back as adsr.attack, adsr.decay, adsr.sustain, adsr.release, in both doors" {
        class Case(val name: String, val kotlin: SprudelPattern, val script: String, val field: (SprudelVoiceData) -> Double?, val expected: Double)

        listOf(
            Case("release = adsr.attack", note("c e").adsr(0.3, 0.2, 0.7, 0.5).adsr(release = adsr.attack),
                """note("c e").adsr(0.3, 0.2, 0.7, 0.5).adsr(release = adsr.attack)""", { it.release }, 0.3),
            Case("pan(adsr.decay)", note("c e").adsr(0.3, 0.2, 0.7, 0.5).pan(adsr.decay),
                """note("c e").adsr(0.3, 0.2, 0.7, 0.5).pan(adsr.decay)""", { it.pan }, 0.2),
            Case("gain(adsr.sustain)", note("c e").adsr(0.3, 0.2, 0.7, 0.5).gain(adsr.sustain),
                """note("c e").adsr(0.3, 0.2, 0.7, 0.5).gain(adsr.sustain)""", { it.gain }, 0.7),
            Case("lpf(adsr.release.mul(1000))", note("c e").adsr(0.3, 0.2, 0.7, 0.5).lpf(adsr.release.mul(1000)),
                """note("c e").adsr(0.3, 0.2, 0.7, 0.5).lpf(adsr.release.mul(1000))""", { it.cutoff }, 500.0),
        ).forEach { c ->
            withClue(c.name) {
                val compiled = SprudelPattern.compile(c.script).shouldNotBeNull()
                listOf(c.kotlin, compiled).forEach { p ->
                    p.cycles().forEach { events -> events.forEach { c.field(it.data).shouldNotBeNull() shouldBe (c.expected plusOrMinus 1e-9) } }
                }
                c.kotlin.cycles().map { events -> events.map { it.whole to c.field(it.data) } } shouldBe
                        compiled.cycles().map { events -> events.map { it.whole to c.field(it.data) } }
            }
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
