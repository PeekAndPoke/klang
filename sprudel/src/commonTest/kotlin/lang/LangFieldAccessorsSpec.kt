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
import io.peekandpoke.klang.sprudel.lang.addons.analog
import io.peekandpoke.klang.sprudel.lang.addons.duty
import io.peekandpoke.klang.sprudel.lang.addons.onepole
import io.peekandpoke.klang.sprudel.lang.addons.notch

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
        row("lpf.freq", """note("c e").lpf(800).lpf(mul(2))""", { it.cutoff }, 1600.0, note("c e").lpf(800).lpf(mul(2))),
        row("hpf.freq", """note("c e").hpf(200).hpf(add(50))""", { it.hcutoff }, 250.0, note("c e").hpf(200).hpf(add(50))),
        row("bpf.freq", """note("c e").bpf(500).bpf(mul(2))""", { it.bandf }, 1000.0, note("c e").bpf(500).bpf(mul(2))),
        row("lpf.q", """note("c e").lpf(q = 4).lpf(q = mul(2))""", { it.resonance }, 8.0, note("c e").lpf(q = 4).lpf(q = mul(2))),
        row("hpf.q", """note("c e").hpf(q = 3).hpf(q = add(1))""", { it.hresonance }, 4.0, note("c e").hpf(q = 3).hpf(q = add(1))),
        row("bpf.q", """note("c e").bpf(q = 5).bpf(q = mul(2))""", { it.bandq }, 10.0, note("c e").bpf(q = 5).bpf(q = mul(2))),
    )

    val read = listOf(
        row("gain", """note("c e").gain(0.8).pan(gain)""", { it.pan }, 0.8, note("c e").gain(0.8).pan(gain)),
        row("velocity", """note("c e").velocity(0.7).pan(velocity)""", { it.pan }, 0.7, note("c e").velocity(0.7).pan(velocity)),
        row("pan", """note("c e").pan(0.3).gain(pan)""", { it.gain }, 0.3, note("c e").pan(0.3).gain(pan)),
        row("postgain", """note("c e").postgain(0.6).pan(postgain)""", { it.pan }, 0.6, note("c e").postgain(0.6).pan(postgain)),
        row("lpf.freq", """note("c e").lpf(800).hpf(lpf.freq)""", { it.hcutoff }, 800.0, note("c e").lpf(800).hpf(lpf.freq)),
        row("hpf.freq", """note("c e").hpf(200).lpf(hpf.freq)""", { it.cutoff }, 200.0, note("c e").hpf(200).lpf(hpf.freq)),
        row("bpf.freq", """note("c e").bpf(500).lpf(bpf.freq)""", { it.cutoff }, 500.0, note("c e").bpf(500).lpf(bpf.freq)),
        row("lpf.q", """note("c e").lpf(q = 4).hpf(q = lpf.q)""", { it.hresonance }, 4.0, note("c e").lpf(q = 4).hpf(q = lpf.q)),
        row("hpf.q", """note("c e").hpf(q = 3).bpf(q = hpf.q)""", { it.bandq }, 3.0, note("c e").hpf(q = 3).bpf(q = hpf.q)),
        row("bpf.q", """note("c e").bpf(q = 5).lpf(q = bpf.q)""", { it.resonance }, 5.0, note("c e").bpf(q = 5).lpf(q = bpf.q)),
    )

    // Effects: one row per slot of the seven compound objects (distort, crush, coarse, room, delay, phaser, tremolo).
    val mappedEffects = listOf(
        row("distort.amount", """s("bd sd").distort(0.4).distort(mul(0.5))""", { it.distort }, 0.2, s("bd sd").distort(0.4).distort(mul(0.5))),
        row("distort.oversample", """s("bd sd").distort(oversample = 2).distort(oversample = mul(2))""", { it.distortOversample?.toDouble() }, 4.0, s("bd sd").distort(oversample = 2).distort(oversample = mul(2))),
        row("crush.amount", """s("bd sd").crush(8).crush(div(2))""", { it.crush }, 4.0, s("bd sd").crush(8).crush(div(2))),
        row("crush.oversample", """s("bd sd").crush(oversample = 2).crush(oversample = mul(2))""", { it.crushOversample?.toDouble() }, 4.0, s("bd sd").crush(oversample = 2).crush(oversample = mul(2))),
        row("coarse.amount", """s("bd sd").coarse(4).coarse(mul(2))""", { it.coarse }, 8.0, s("bd sd").coarse(4).coarse(mul(2))),
        row("coarse.oversample", """s("bd sd").coarse(oversample = 2).coarse(oversample = mul(2))""", { it.coarseOversample?.toDouble() }, 4.0, s("bd sd").coarse(oversample = 2).coarse(oversample = mul(2))),
        row("room.wet", """s("bd sd").room(0.3).room(add(0.2))""", { it.room }, 0.5, s("bd sd").room(0.3).room(add(0.2))),
        row("room.size", """s("bd sd").room(size = 4).room(size = mul(2))""", { it.roomSize }, 8.0, s("bd sd").room(size = 4).room(size = mul(2))),
        row("room.fade", """s("bd sd").room(fade = 1).room(fade = mul(2))""", { it.roomFade }, 2.0, s("bd sd").room(fade = 1).room(fade = mul(2))),
        row("room.lowpass", """s("bd sd").room(lowpass = 4000).room(lowpass = div(2))""", { it.roomLp }, 2000.0, s("bd sd").room(lowpass = 4000).room(lowpass = div(2))),
        row("room.dim", """s("bd sd").room(dim = 3000).room(dim = sub(1000))""", { it.roomDim }, 2000.0, s("bd sd").room(dim = 3000).room(dim = sub(1000))),
        row("delay.wet", """s("bd sd").delay(0.3).delay(add(0.2))""", { it.delay }, 0.5, s("bd sd").delay(0.3).delay(add(0.2))),
        row("delay.time", """s("bd sd").delay(time = 0.25).delay(time = mul(2))""", { it.delayTime }, 0.5, s("bd sd").delay(time = 0.25).delay(time = mul(2))),
        row("delay.feedback", """s("bd sd").delay(feedback = 0.4).delay(feedback = mul(0.5))""", { it.delayFeedback }, 0.2, s("bd sd").delay(feedback = 0.4).delay(feedback = mul(0.5))),
        row("delay.cap", """s("bd sd").delay(cap = 0.5).delay(cap = mul(2))""", { it.delayCap }, 1.0, s("bd sd").delay(cap = 0.5).delay(cap = mul(2))),
        row("phaser.rate", """s("bd sd").phaser(0.5).phaser(mul(4))""", { it.phaserRate }, 2.0, s("bd sd").phaser(0.5).phaser(mul(4))),
        row("phaser.wet", """s("bd sd").phaser(wet = 0.5).phaser(wet = mul(0.5))""", { it.phaserDepth }, 0.25, s("bd sd").phaser(wet = 0.5).phaser(wet = mul(0.5))),
        row("phaser.center", """s("bd sd").phaser(center = 1000).phaser(center = mul(2))""", { it.phaserCenter }, 2000.0, s("bd sd").phaser(center = 1000).phaser(center = mul(2))),
        row("phaser.sweep", """s("bd sd").phaser(sweep = 2000).phaser(sweep = mul(0.5))""", { it.phaserSweep }, 1000.0, s("bd sd").phaser(sweep = 2000).phaser(sweep = mul(0.5))),
        row("phaser.floor", """s("bd sd").phaser(floor = 0.2).phaser(floor = add(0.3))""", { it.phaserFloor }, 0.5, s("bd sd").phaser(floor = 0.2).phaser(floor = add(0.3))),
        row("tremolo.depth", """s("bd sd").tremolo(0.5).tremolo(mul(0.5))""", { it.tremoloDepth }, 0.25, s("bd sd").tremolo(0.5).tremolo(mul(0.5))),
        row("tremolo.sync", """s("bd sd").tremolo(sync = 4).tremolo(sync = mul(2))""", { it.tremoloSync }, 8.0, s("bd sd").tremolo(sync = 4).tremolo(sync = mul(2))),
        row("tremolo.skew", """s("bd sd").tremolo(skew = 0.5).tremolo(skew = add(0.3))""", { it.tremoloSkew }, 0.8, s("bd sd").tremolo(skew = 0.5).tremolo(skew = add(0.3))),
        row("tremolo.phase", """s("bd sd").tremolo(phase = 0.25).tremolo(phase = add(0.5))""", { it.tremoloPhase }, 0.75, s("bd sd").tremolo(phase = 0.25).tremolo(phase = add(0.5))),
    )

    // The children of a compound read their slot bare. Slots apply in declaration order within one
    // call, so a child read of a LATER slot in the same call sees the old value; chain instead.
    val readEffects = listOf(
        row("distort.amount", """s("bd sd").distort(0.4).pan(distort.amount)""", { it.pan }, 0.4, s("bd sd").distort(0.4).pan(distort.amount)),
        row("distort.oversample", """s("bd sd").distort(oversample = 2).crush(oversample = distort.oversample)""", { it.crushOversample?.toDouble() }, 2.0, s("bd sd").distort(oversample = 2).crush(oversample = distort.oversample)),
        row("crush.amount", """s("bd sd").crush(8).coarse(crush.amount)""", { it.coarse }, 8.0, s("bd sd").crush(8).coarse(crush.amount)),
        row("crush.oversample", """s("bd sd").crush(oversample = 2).coarse(oversample = crush.oversample)""", { it.coarseOversample?.toDouble() }, 2.0, s("bd sd").crush(oversample = 2).coarse(oversample = crush.oversample)),
        row("coarse.amount", """s("bd sd").coarse(4).crush(coarse.amount)""", { it.crush }, 4.0, s("bd sd").coarse(4).crush(coarse.amount)),
        row("coarse.oversample", """s("bd sd").coarse(oversample = 2).distort(oversample = coarse.oversample)""", { it.distortOversample?.toDouble() }, 2.0, s("bd sd").coarse(oversample = 2).distort(oversample = coarse.oversample)),
        row("room.wet", """s("bd sd").room(0.3).delay(room.wet)""", { it.delay }, 0.3, s("bd sd").room(0.3).delay(room.wet)),
        row("room.size", """s("bd sd").room(size = 4, fade = room.size)""", { it.roomFade }, 4.0, s("bd sd").room(size = 4, fade = room.size)),
        row("room.fade", """s("bd sd").room(fade = 1).delay(time = room.fade)""", { it.delayTime }, 1.0, s("bd sd").room(fade = 1).delay(time = room.fade)),
        row("room.lowpass", """s("bd sd").room(lowpass = 4000).lpf(room.lowpass)""", { it.cutoff }, 4000.0, s("bd sd").room(lowpass = 4000).lpf(room.lowpass)),
        row("room.dim", """s("bd sd").room(dim = 3000).room(lowpass = room.dim)""", { it.roomLp }, 3000.0, s("bd sd").room(dim = 3000).room(lowpass = room.dim)),
        row("delay.wet", """s("bd sd").delay(0.3).room(delay.wet)""", { it.room }, 0.3, s("bd sd").delay(0.3).room(delay.wet)),
        row("delay.time", """s("bd sd").delay(time = 0.25).room(fade = delay.time)""", { it.roomFade }, 0.25, s("bd sd").delay(time = 0.25).room(fade = delay.time)),
        row("delay.feedback", """s("bd sd").delay(feedback = 0.4).pan(delay.feedback)""", { it.pan }, 0.4, s("bd sd").delay(feedback = 0.4).pan(delay.feedback)),
        row("delay.cap", """s("bd sd").delay(cap = 0.5).pan(delay.cap)""", { it.pan }, 0.5, s("bd sd").delay(cap = 0.5).pan(delay.cap)),
        row("phaser.rate", """s("bd sd").phaser(0.5).tremolo(sync = phaser.rate)""", { it.tremoloSync }, 0.5, s("bd sd").phaser(0.5).tremolo(sync = phaser.rate)),
        row("phaser.wet", """s("bd sd").phaser(wet = 0.5).pan(phaser.wet)""", { it.pan }, 0.5, s("bd sd").phaser(wet = 0.5).pan(phaser.wet)),
        row("phaser.center", """s("bd sd").phaser(center = 1000).lpf(phaser.center)""", { it.cutoff }, 1000.0, s("bd sd").phaser(center = 1000).lpf(phaser.center)),
        row("phaser.sweep", """s("bd sd").phaser(sweep = 2000).phaser(center = phaser.sweep)""", { it.phaserCenter }, 2000.0, s("bd sd").phaser(sweep = 2000).phaser(center = phaser.sweep)),
        row("phaser.floor", """s("bd sd").phaser(floor = 0.2).pan(phaser.floor)""", { it.pan }, 0.2, s("bd sd").phaser(floor = 0.2).pan(phaser.floor)),
        row("tremolo.depth", """s("bd sd").tremolo(0.5).pan(tremolo.depth)""", { it.pan }, 0.5, s("bd sd").tremolo(0.5).pan(tremolo.depth)),
        row("tremolo.sync", """s("bd sd").tremolo(sync = 4).phaser(tremolo.sync)""", { it.phaserRate }, 4.0, s("bd sd").tremolo(sync = 4).phaser(tremolo.sync)),
        row("tremolo.skew", """s("bd sd").tremolo(skew = 0.5, phase = tremolo.skew)""", { it.tremoloPhase }, 0.5, s("bd sd").tremolo(skew = 0.5, phase = tremolo.skew)),
        row("tremolo.phase", """s("bd sd").tremolo(phase = 0.25).tremolo(skew = tremolo.phase)""", { it.tremoloSkew }, 0.25, s("bd sd").tremolo(phase = 0.25).tremolo(skew = tremolo.phase)),
    )

    // Every alias is a constant of the canonical object: it sets the canonical field and reads it bare.
    val aliasSets = listOf(
        row("vel", """s("bd sd").apply(vel(2))""", { it.velocity }, 2.0, s("bd sd").apply(vel(2))),
        row("lowpass", """s("bd sd").apply(lowpass(2))""", { it.cutoff }, 2.0, s("bd sd").apply(lowpass(2))),
        row("highpass", """s("bd sd").apply(highpass(2))""", { it.hcutoff }, 2.0, s("bd sd").apply(highpass(2))),
        row("bandpass", """s("bd sd").apply(bandpass(2))""", { it.bandf }, 2.0, s("bd sd").apply(bandpass(2))),
    )

    val aliasReads = listOf(
        row("vel", """s("bd sd").vel(2).pan(vel)""", { it.pan }, 2.0, s("bd sd").vel(2).pan(vel)),
        row("lowpass", """s("bd sd").lowpass(2).pan(lowpass.freq)""", { it.pan }, 2.0, s("bd sd").lowpass(2).pan(lowpass.freq)),
        row("highpass", """s("bd sd").highpass(2).pan(highpass.freq)""", { it.pan }, 2.0, s("bd sd").highpass(2).pan(highpass.freq)),
        row("bandpass", """s("bd sd").bandpass(2).pan(bandpass.freq)""", { it.pan }, 2.0, s("bd sd").bandpass(2).pan(bandpass.freq)),
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
        row("notch.freq", """s("bd sd").notch(1000).notch(mul(2))""", { it.notchf }, 2000.0, s("bd sd").notch(1000).notch(mul(2))),
        row("notch.q", """s("bd sd").notch(q = 4).notch(q = mul(2))""", { it.nresonance }, 8.0, s("bd sd").notch(q = 4).notch(q = mul(2))),
        row("notch.attack", """s("bd sd").notch(attack = 0.1).notch(attack = mul(2))""", { it.nfattack }, 0.2, s("bd sd").notch(attack = 0.1).notch(attack = mul(2))),
        row("notch.decay", """s("bd sd").notch(decay = 0.2).notch(decay = mul(2))""", { it.nfdecay }, 0.4, s("bd sd").notch(decay = 0.2).notch(decay = mul(2))),
        row("notch.sustain", """s("bd sd").notch(sustain = 0.5).notch(sustain = mul(0.5))""", { it.nfsustain }, 0.25, s("bd sd").notch(sustain = 0.5).notch(sustain = mul(0.5))),
        row("notch.release", """s("bd sd").notch(release = 0.3).notch(release = mul(2))""", { it.nfrelease }, 0.6, s("bd sd").notch(release = 0.3).notch(release = mul(2))),
        row("notch.env", """s("bd sd").notch(env = 12).notch(env = mul(2))""", { it.nfenv }, 24.0, s("bd sd").notch(env = 12).notch(env = mul(2))),
        row("lpf.attack", """s("bd sd").lpf(attack = 0.1).lpf(attack = mul(2))""", { it.lpattack }, 0.2, s("bd sd").lpf(attack = 0.1).lpf(attack = mul(2))),
        row("lpf.decay", """s("bd sd").lpf(decay = 0.2).lpf(decay = mul(2))""", { it.lpdecay }, 0.4, s("bd sd").lpf(decay = 0.2).lpf(decay = mul(2))),
        row("lpf.sustain", """s("bd sd").lpf(sustain = 0.5).lpf(sustain = mul(0.5))""", { it.lpsustain }, 0.25, s("bd sd").lpf(sustain = 0.5).lpf(sustain = mul(0.5))),
        row("lpf.release", """s("bd sd").lpf(release = 0.3).lpf(release = mul(2))""", { it.lprelease }, 0.6, s("bd sd").lpf(release = 0.3).lpf(release = mul(2))),
        row("hpf.attack", """s("bd sd").hpf(attack = 0.1).hpf(attack = mul(2))""", { it.hpattack }, 0.2, s("bd sd").hpf(attack = 0.1).hpf(attack = mul(2))),
        row("hpf.decay", """s("bd sd").hpf(decay = 0.2).hpf(decay = mul(2))""", { it.hpdecay }, 0.4, s("bd sd").hpf(decay = 0.2).hpf(decay = mul(2))),
        row("hpf.sustain", """s("bd sd").hpf(sustain = 0.5).hpf(sustain = mul(0.5))""", { it.hpsustain }, 0.25, s("bd sd").hpf(sustain = 0.5).hpf(sustain = mul(0.5))),
        row("hpf.release", """s("bd sd").hpf(release = 0.3).hpf(release = mul(2))""", { it.hprelease }, 0.6, s("bd sd").hpf(release = 0.3).hpf(release = mul(2))),
        row("bpf.attack", """s("bd sd").bpf(attack = 0.1).bpf(attack = mul(2))""", { it.bpattack }, 0.2, s("bd sd").bpf(attack = 0.1).bpf(attack = mul(2))),
        row("bpf.decay", """s("bd sd").bpf(decay = 0.2).bpf(decay = mul(2))""", { it.bpdecay }, 0.4, s("bd sd").bpf(decay = 0.2).bpf(decay = mul(2))),
        row("bpf.sustain", """s("bd sd").bpf(sustain = 0.5).bpf(sustain = mul(0.5))""", { it.bpsustain }, 0.25, s("bd sd").bpf(sustain = 0.5).bpf(sustain = mul(0.5))),
        row("bpf.release", """s("bd sd").bpf(release = 0.3).bpf(release = mul(2))""", { it.bprelease }, 0.6, s("bd sd").bpf(release = 0.3).bpf(release = mul(2))),
        row("lpf.env", """s("bd sd").lpf(env = 12).lpf(env = mul(2))""", { it.lpenv }, 24.0, s("bd sd").lpf(env = 12).lpf(env = mul(2))),
        row("lpf.passes", """s("bd sd").lpf(passes = 1).lpf(passes = add(1))""", { it.lpPasses }, 2.0, s("bd sd").lpf(passes = 1).lpf(passes = add(1))),
        row("hpf.env", """s("bd sd").hpf(env = 12).hpf(env = mul(2))""", { it.hpenv }, 24.0, s("bd sd").hpf(env = 12).hpf(env = mul(2))),
        row("hpf.passes", """s("bd sd").hpf(passes = 1).hpf(passes = add(1))""", { it.hpPasses }, 2.0, s("bd sd").hpf(passes = 1).hpf(passes = add(1))),
        row("bpf.env", """s("bd sd").bpf(env = 12).bpf(env = mul(2))""", { it.bpenv }, 24.0, s("bd sd").bpf(env = 12).bpf(env = mul(2))),
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
        row("notch.freq", """s("bd sd").notch(1000).pan(notch.freq)""", { it.pan }, 1000.0, s("bd sd").notch(1000).pan(notch.freq)),
        row("notch.q", """s("bd sd").notch(q = 4).pan(notch.q)""", { it.pan }, 4.0, s("bd sd").notch(q = 4).pan(notch.q)),
        row("notch.attack", """s("bd sd").notch(attack = 0.1).pan(notch.attack)""", { it.pan }, 0.1, s("bd sd").notch(attack = 0.1).pan(notch.attack)),
        row("notch.decay", """s("bd sd").notch(decay = 0.2).pan(notch.decay)""", { it.pan }, 0.2, s("bd sd").notch(decay = 0.2).pan(notch.decay)),
        row("notch.sustain", """s("bd sd").notch(sustain = 0.5).pan(notch.sustain)""", { it.pan }, 0.5, s("bd sd").notch(sustain = 0.5).pan(notch.sustain)),
        row("notch.release", """s("bd sd").notch(release = 0.3).pan(notch.release)""", { it.pan }, 0.3, s("bd sd").notch(release = 0.3).pan(notch.release)),
        row("notch.env", """s("bd sd").notch(env = 12).pan(notch.env)""", { it.pan }, 12.0, s("bd sd").notch(env = 12).pan(notch.env)),
        row("lpf.attack", """s("bd sd").lpf(attack = 0.1).pan(lpf.attack)""", { it.pan }, 0.1, s("bd sd").lpf(attack = 0.1).pan(lpf.attack)),
        row("lpf.decay", """s("bd sd").lpf(decay = 0.2).pan(lpf.decay)""", { it.pan }, 0.2, s("bd sd").lpf(decay = 0.2).pan(lpf.decay)),
        row("lpf.sustain", """s("bd sd").lpf(sustain = 0.5).pan(lpf.sustain)""", { it.pan }, 0.5, s("bd sd").lpf(sustain = 0.5).pan(lpf.sustain)),
        row("lpf.release", """s("bd sd").lpf(release = 0.3).pan(lpf.release)""", { it.pan }, 0.3, s("bd sd").lpf(release = 0.3).pan(lpf.release)),
        row("hpf.attack", """s("bd sd").hpf(attack = 0.1).pan(hpf.attack)""", { it.pan }, 0.1, s("bd sd").hpf(attack = 0.1).pan(hpf.attack)),
        row("hpf.decay", """s("bd sd").hpf(decay = 0.2).pan(hpf.decay)""", { it.pan }, 0.2, s("bd sd").hpf(decay = 0.2).pan(hpf.decay)),
        row("hpf.sustain", """s("bd sd").hpf(sustain = 0.5).pan(hpf.sustain)""", { it.pan }, 0.5, s("bd sd").hpf(sustain = 0.5).pan(hpf.sustain)),
        row("hpf.release", """s("bd sd").hpf(release = 0.3).pan(hpf.release)""", { it.pan }, 0.3, s("bd sd").hpf(release = 0.3).pan(hpf.release)),
        row("bpf.attack", """s("bd sd").bpf(attack = 0.1).pan(bpf.attack)""", { it.pan }, 0.1, s("bd sd").bpf(attack = 0.1).pan(bpf.attack)),
        row("bpf.decay", """s("bd sd").bpf(decay = 0.2).pan(bpf.decay)""", { it.pan }, 0.2, s("bd sd").bpf(decay = 0.2).pan(bpf.decay)),
        row("bpf.sustain", """s("bd sd").bpf(sustain = 0.5).pan(bpf.sustain)""", { it.pan }, 0.5, s("bd sd").bpf(sustain = 0.5).pan(bpf.sustain)),
        row("bpf.release", """s("bd sd").bpf(release = 0.3).pan(bpf.release)""", { it.pan }, 0.3, s("bd sd").bpf(release = 0.3).pan(bpf.release)),
        row("lpf.env", """s("bd sd").lpf(env = 12).pan(lpf.env)""", { it.pan }, 12.0, s("bd sd").lpf(env = 12).pan(lpf.env)),
        row("lpf.passes", """s("bd sd").lpf(passes = 1).pan(lpf.passes)""", { it.pan }, 1.0, s("bd sd").lpf(passes = 1).pan(lpf.passes)),
        row("hpf.env", """s("bd sd").hpf(env = 12).pan(hpf.env)""", { it.pan }, 12.0, s("bd sd").hpf(env = 12).pan(hpf.env)),
        row("hpf.passes", """s("bd sd").hpf(passes = 1).pan(hpf.passes)""", { it.pan }, 1.0, s("bd sd").hpf(passes = 1).pan(hpf.passes)),
        row("bpf.env", """s("bd sd").bpf(env = 12).pan(bpf.env)""", { it.pan }, 12.0, s("bd sd").bpf(env = 12).pan(bpf.env)),
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
        both(note("c e").lpf(400).hpf(lpf.freq.div(2), 4), """note("c e").lpf(400).hpf(lpf.freq.div(2), 4)""") {
            it.hcutoff shouldBe 200.0
            it.hresonance shouldBe 4.0
        }
        both(note("c e").bpf(freq.mul(2), 3), """note("c e").bpf(freq.mul(2), 3)""") {
            it.bandf shouldBe (it.freqHz.shouldNotBeNull() * 2 plusOrMinus 1e-9)
            it.bandq shouldBe 3.0
        }
        // batch two: the compound effect doors, positional and named
        both(s("bd sd").apply(room(0.3, 4)), """s("bd sd").apply(room(0.3, 4))""") {
            it.room shouldBe 0.3
            it.roomSize shouldBe 4.0
        }
        both(s("bd sd").apply(room(size = 4)), """s("bd sd").apply(room(size = 4))""") {
            it.roomSize shouldBe 4.0
        }
        both(s("bd sd").apply(delay(0.3, 0.25, 0.4)), """s("bd sd").apply(delay(0.3, 0.25, 0.4))""") {
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
        // batch three: the notch door
        both(s("bd sd").apply(notch(1000, 5)), """s("bd sd").apply(notch(1000, 5))""") {
            it.notchf shouldBe 1000.0
            it.nresonance shouldBe 5.0
        }
        both(s("bd sd").notch(1000).apply(notch(mul(2), 5)), """s("bd sd").notch(1000).apply(notch(mul(2), 5))""") {
            it.notchf shouldBe 2000.0
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

    "effects: a mapper on one slot leaves the other slots alone, in both doors" {
        class Case(val name: String, val kotlin: SprudelPattern, val script: String, val check: (SprudelVoiceData) -> Unit)
        listOf(
            Case("room(size = mul(2))", s("bd sd").room(0.3, 4, 1.5).room(size = mul(2)), """s("bd sd").room(0.3, 4, 1.5).room(size = mul(2))""") {
                it.room shouldBe 0.3
                it.roomSize shouldBe 8.0
                it.roomFade shouldBe 1.5
            },
            Case("delay(feedback = mul(2))", s("bd sd").delay(0.3, 0.25, 0.2).delay(feedback = mul(2)), """s("bd sd").delay(0.3, 0.25, 0.2).delay(feedback = mul(2))""") {
                it.delay shouldBe 0.3
                it.delayTime shouldBe 0.25
                it.delayFeedback shouldBe 0.4
            },
            Case("phaser(center = mul(2))", s("bd sd").phaser(0.5, 0.6, 1000, 2000).phaser(center = mul(2)), """s("bd sd").phaser(0.5, 0.6, 1000, 2000).phaser(center = mul(2))""") {
                it.phaserRate shouldBe 0.5
                it.phaserDepth shouldBe 0.6
                it.phaserCenter shouldBe 2000.0
                it.phaserSweep shouldBe 2000.0
            },
            Case("tremolo(skew = add(0.2))", s("bd sd").tremolo(0.5, 4, "sine", 0.3).tremolo(skew = add(0.2)), """s("bd sd").tremolo(0.5, 4, "sine", 0.3).tremolo(skew = add(0.2))""") {
                it.tremoloDepth shouldBe 0.5
                it.tremoloSync shouldBe 4.0
                it.tremoloShape shouldBe "sine"
                it.tremoloSkew shouldBe 0.5
            },
            Case("distort(oversample = mul(2))", s("bd sd").distort(0.5, "soft", 2).distort(oversample = mul(2)), """s("bd sd").distort(0.5, "soft", 2).distort(oversample = mul(2))""") {
                it.distort shouldBe 0.5
                it.distortShape shouldBe "soft"
                it.distortOversample shouldBe 4
            },
            Case("crush(amount = div(2))", s("bd sd").crush(8, 2).crush(amount = div(2)), """s("bd sd").crush(8, 2).crush(amount = div(2))""") {
                it.crush shouldBe 4.0
                it.crushOversample shouldBe 2
            },
            Case("coarse(oversample = mul(2))", s("bd sd").coarse(4, 2).coarse(oversample = mul(2)), """s("bd sd").coarse(4, 2).coarse(oversample = mul(2))""") {
                it.coarse shouldBe 4.0
                it.coarseOversample shouldBe 4
            },
        ).forEach { case ->
            withClue(case.name) {
                listOf("kotlin" to case.kotlin, "script" to SprudelPattern.compile(case.script).shouldNotBeNull()).forEach { (door, p) ->
                    withClue(door) { p.cycles().forEach { events -> events shouldHaveSize 2; events.forEach { case.check(it.data) } } }
                }
            }
        }
    }

    "effects: every compound head keeps its value where the control pattern has a gap, in both doors" {
        class Case(val name: String, val kotlin: SprudelPattern, val script: String, val field: (SprudelVoiceData) -> Double?)
        listOf(
            Case("room", s("bd sd").room(0.8).room("<0.5 ~>"), """s("bd sd").room(0.8).room("<0.5 ~>")""") { it.room },
            Case("delay", s("bd sd").delay(0.8).delay("<0.5 ~>"), """s("bd sd").delay(0.8).delay("<0.5 ~>")""") { it.delay },
            Case("phaser", s("bd sd").phaser(0.8).phaser("<0.5 ~>"), """s("bd sd").phaser(0.8).phaser("<0.5 ~>")""") { it.phaserRate },
            Case("tremolo", s("bd sd").tremolo(0.8).tremolo("<0.5 ~>"), """s("bd sd").tremolo(0.8).tremolo("<0.5 ~>")""") { it.tremoloDepth },
            Case("distort", s("bd sd").distort(0.8).distort("<0.5 ~>"), """s("bd sd").distort(0.8).distort("<0.5 ~>")""") { it.distort },
            Case("crush", s("bd sd").crush(0.8).crush("<0.5 ~>"), """s("bd sd").crush(0.8).crush("<0.5 ~>")""") { it.crush },
            Case("coarse", s("bd sd").coarse(0.8).coarse("<0.5 ~>"), """s("bd sd").coarse(0.8).coarse("<0.5 ~>")""") { it.coarse },
        ).forEach { case ->
            withClue(case.name) {
                listOf("kotlin" to case.kotlin, "script" to SprudelPattern.compile(case.script).shouldNotBeNull()).forEach { (door, p) ->
                    withClue(door) {
                        p.queryArc(0.0, 1.0).map { case.field(it.data) } shouldBe listOf(0.5, 0.5)
                        p.queryArc(1.0, 2.0).map { case.field(it.data) } shouldBe listOf(0.8, 0.8)
                    }
                }
            }
        }
    }

    "filters: a mapper on one slot leaves the other slots alone, in both doors" {
        class Case(val name: String, val kotlin: SprudelPattern, val script: String, val check: (SprudelVoiceData) -> Unit)
        listOf(
            Case("lpf(env = mul(2))", note("c e").lpf(500, 8, 2, 12, 0.1, 0.2, 0.5, 0.3).lpf(env = mul(2)), """note("c e").lpf(500, 8, 2, 12, 0.1, 0.2, 0.5, 0.3).lpf(env = mul(2))""") {
                it.cutoff shouldBe 500.0
                it.resonance shouldBe 8.0
                it.lpPasses shouldBe 2.0
                it.lpenv shouldBe 24.0
                it.lpattack shouldBe 0.1
                it.lpdecay shouldBe 0.2
                it.lpsustain shouldBe 0.5
                it.lprelease shouldBe 0.3
            },
            Case("hpf(attack = mul(2))", note("c e").hpf(300, 4, 1, 12, 0.1, 0.2, 0.5, 0.3).hpf(attack = mul(2)), """note("c e").hpf(300, 4, 1, 12, 0.1, 0.2, 0.5, 0.3).hpf(attack = mul(2))""") {
                it.hcutoff shouldBe 300.0
                it.hresonance shouldBe 4.0
                it.hpPasses shouldBe 1.0
                it.hpenv shouldBe 12.0
                it.hpattack shouldBe 0.2
                it.hpdecay shouldBe 0.2
                it.hpsustain shouldBe 0.5
                it.hprelease shouldBe 0.3
            },
            Case("bpf(q = mul(2))", note("c e").bpf(1000, 3, 12, 0.1, 0.2, 0.5, 0.3).bpf(q = mul(2)), """note("c e").bpf(1000, 3, 12, 0.1, 0.2, 0.5, 0.3).bpf(q = mul(2))""") {
                it.bandf shouldBe 1000.0
                it.bandq shouldBe 6.0
                it.bpenv shouldBe 12.0
                it.bpattack shouldBe 0.1
                it.bpdecay shouldBe 0.2
                it.bpsustain shouldBe 0.5
                it.bprelease shouldBe 0.3
            },
            Case("notch(release = mul(2))", note("c e").notch(1000, 5, 12, 0.1, 0.2, 0.5, 0.3).notch(release = mul(2)), """note("c e").notch(1000, 5, 12, 0.1, 0.2, 0.5, 0.3).notch(release = mul(2))""") {
                it.notchf shouldBe 1000.0
                it.nresonance shouldBe 5.0
                it.nfenv shouldBe 12.0
                it.nfattack shouldBe 0.1
                it.nfdecay shouldBe 0.2
                it.nfsustain shouldBe 0.5
                it.nfrelease shouldBe 0.6
            },
        ).forEach { case ->
            withClue(case.name) {
                listOf("kotlin" to case.kotlin, "script" to SprudelPattern.compile(case.script).shouldNotBeNull()).forEach { (door, p) ->
                    withClue(door) { p.cycles().forEach { events -> events shouldHaveSize 2; events.forEach { case.check(it.data) } } }
                }
            }
        }
    }

    "filters: every slot takes a control pattern per event, in both doors" {
        class Case(val name: String, val kotlin: SprudelPattern, val script: String, val field: (SprudelVoiceData) -> Double?)
        val cases = mutableListOf<Case>()
        fun add(name: String, kotlin: SprudelPattern, script: String, field: (SprudelVoiceData) -> Double?) { cases.add(Case(name, kotlin, script, field)) }
        add("lpf.freq", s("bd sd").lpf(freq = "0.1 0.5"), """s("bd sd").lpf(freq = "0.1 0.5")""") { it.cutoff }
        add("lpf.q", s("bd sd").lpf(q = "0.1 0.5"), """s("bd sd").lpf(q = "0.1 0.5")""") { it.resonance }
        add("lpf.passes", s("bd sd").lpf(passes = "0.1 0.5"), """s("bd sd").lpf(passes = "0.1 0.5")""") { it.lpPasses }
        add("lpf.env", s("bd sd").lpf(env = "0.1 0.5"), """s("bd sd").lpf(env = "0.1 0.5")""") { it.lpenv }
        add("lpf.attack", s("bd sd").lpf(attack = "0.1 0.5"), """s("bd sd").lpf(attack = "0.1 0.5")""") { it.lpattack }
        add("lpf.decay", s("bd sd").lpf(decay = "0.1 0.5"), """s("bd sd").lpf(decay = "0.1 0.5")""") { it.lpdecay }
        add("lpf.sustain", s("bd sd").lpf(sustain = "0.1 0.5"), """s("bd sd").lpf(sustain = "0.1 0.5")""") { it.lpsustain }
        add("lpf.release", s("bd sd").lpf(release = "0.1 0.5"), """s("bd sd").lpf(release = "0.1 0.5")""") { it.lprelease }
        add("hpf.freq", s("bd sd").hpf(freq = "0.1 0.5"), """s("bd sd").hpf(freq = "0.1 0.5")""") { it.hcutoff }
        add("hpf.q", s("bd sd").hpf(q = "0.1 0.5"), """s("bd sd").hpf(q = "0.1 0.5")""") { it.hresonance }
        add("hpf.passes", s("bd sd").hpf(passes = "0.1 0.5"), """s("bd sd").hpf(passes = "0.1 0.5")""") { it.hpPasses }
        add("hpf.env", s("bd sd").hpf(env = "0.1 0.5"), """s("bd sd").hpf(env = "0.1 0.5")""") { it.hpenv }
        add("hpf.attack", s("bd sd").hpf(attack = "0.1 0.5"), """s("bd sd").hpf(attack = "0.1 0.5")""") { it.hpattack }
        add("hpf.decay", s("bd sd").hpf(decay = "0.1 0.5"), """s("bd sd").hpf(decay = "0.1 0.5")""") { it.hpdecay }
        add("hpf.sustain", s("bd sd").hpf(sustain = "0.1 0.5"), """s("bd sd").hpf(sustain = "0.1 0.5")""") { it.hpsustain }
        add("hpf.release", s("bd sd").hpf(release = "0.1 0.5"), """s("bd sd").hpf(release = "0.1 0.5")""") { it.hprelease }
        add("bpf.freq", s("bd sd").bpf(freq = "0.1 0.5"), """s("bd sd").bpf(freq = "0.1 0.5")""") { it.bandf }
        add("bpf.q", s("bd sd").bpf(q = "0.1 0.5"), """s("bd sd").bpf(q = "0.1 0.5")""") { it.bandq }
        add("bpf.env", s("bd sd").bpf(env = "0.1 0.5"), """s("bd sd").bpf(env = "0.1 0.5")""") { it.bpenv }
        add("bpf.attack", s("bd sd").bpf(attack = "0.1 0.5"), """s("bd sd").bpf(attack = "0.1 0.5")""") { it.bpattack }
        add("bpf.decay", s("bd sd").bpf(decay = "0.1 0.5"), """s("bd sd").bpf(decay = "0.1 0.5")""") { it.bpdecay }
        add("bpf.sustain", s("bd sd").bpf(sustain = "0.1 0.5"), """s("bd sd").bpf(sustain = "0.1 0.5")""") { it.bpsustain }
        add("bpf.release", s("bd sd").bpf(release = "0.1 0.5"), """s("bd sd").bpf(release = "0.1 0.5")""") { it.bprelease }
        add("notch.freq", s("bd sd").notch(freq = "0.1 0.5"), """s("bd sd").notch(freq = "0.1 0.5")""") { it.notchf }
        add("notch.q", s("bd sd").notch(q = "0.1 0.5"), """s("bd sd").notch(q = "0.1 0.5")""") { it.nresonance }
        add("notch.env", s("bd sd").notch(env = "0.1 0.5"), """s("bd sd").notch(env = "0.1 0.5")""") { it.nfenv }
        add("notch.attack", s("bd sd").notch(attack = "0.1 0.5"), """s("bd sd").notch(attack = "0.1 0.5")""") { it.nfattack }
        add("notch.decay", s("bd sd").notch(decay = "0.1 0.5"), """s("bd sd").notch(decay = "0.1 0.5")""") { it.nfdecay }
        add("notch.sustain", s("bd sd").notch(sustain = "0.1 0.5"), """s("bd sd").notch(sustain = "0.1 0.5")""") { it.nfsustain }
        add("notch.release", s("bd sd").notch(release = "0.1 0.5"), """s("bd sd").notch(release = "0.1 0.5")""") { it.nfrelease }
        cases.forEach { case ->
            withClue(case.name) {
                listOf("kotlin" to case.kotlin, "script" to SprudelPattern.compile(case.script).shouldNotBeNull()).forEach { (door, p) ->
                    withClue(door) { p.queryArc(0.0, 1.0).map { case.field(it.data) } shouldBe listOf(0.1, 0.5) }
                }
            }
        }
    }

    "filters: a tail-only call does not reinterpret a numeric receiver into the head slot, in both doors" {
        class Case(val name: String, val kotlin: SprudelPattern, val script: String, val head: (SprudelVoiceData) -> Double?, val tail: (SprudelVoiceData) -> Double?)
        listOf(
            Case("lpf(q = 4)", seq("3 4").lpf(q = 4), """seq("3 4").lpf(q = 4)""", { it.cutoff }, { it.resonance }),
            Case("hpf(env = 4)", seq("3 4").hpf(env = 4), """seq("3 4").hpf(env = 4)""", { it.hcutoff }, { it.hpenv }),
            Case("bpf(q = 4)", seq("3 4").bpf(q = 4), """seq("3 4").bpf(q = 4)""", { it.bandf }, { it.bandq }),
            Case("notch(attack = 4)", seq("3 4").notch(attack = 4), """seq("3 4").notch(attack = 4)""", { it.notchf }, { it.nfattack }),
        ).forEach { case ->
            withClue(case.name) {
                listOf("kotlin" to case.kotlin, "script" to SprudelPattern.compile(case.script).shouldNotBeNull()).forEach { (door, p) ->
                    withClue(door) {
                        val events = p.queryArc(0.0, 1.0)
                        events shouldHaveSize 2
                        events.map { case.head(it.data) } shouldBe listOf(null, null)
                        events.map { case.tail(it.data) } shouldBe listOf(4.0, 4.0)
                    }
                }
            }
        }
    }

    "filters: every filter head keeps its value where the control pattern has a gap, in both doors" {
        class Case(val name: String, val kotlin: SprudelPattern, val script: String, val field: (SprudelVoiceData) -> Double?)
        listOf(
            Case("lpf", s("bd sd").lpf(800).lpf("<500 ~>"), """s("bd sd").lpf(800).lpf("<500 ~>")""") { it.cutoff },
            Case("hpf", s("bd sd").hpf(800).hpf("<500 ~>"), """s("bd sd").hpf(800).hpf("<500 ~>")""") { it.hcutoff },
            Case("bpf", s("bd sd").bpf(800).bpf("<500 ~>"), """s("bd sd").bpf(800).bpf("<500 ~>")""") { it.bandf },
            Case("notch", s("bd sd").notch(800).notch("<500 ~>"), """s("bd sd").notch(800).notch("<500 ~>")""") { it.notchf },
        ).forEach { case ->
            withClue(case.name) {
                listOf("kotlin" to case.kotlin, "script" to SprudelPattern.compile(case.script).shouldNotBeNull()).forEach { (door, p) ->
                    withClue(door) {
                        p.queryArc(0.0, 1.0).map { case.field(it.data) } shouldBe listOf(500.0, 500.0)
                        p.queryArc(1.0, 2.0).map { case.field(it.data) } shouldBe listOf(800.0, 800.0)
                    }
                }
            }
        }
    }

    "effects: every numeric slot takes a control pattern per event, in both doors" {
        class Case(val name: String, val kotlin: SprudelPattern, val script: String, val field: (SprudelVoiceData) -> Double?)
        listOf(
            Case("room.wet", s("bd sd").room(wet = "0.1 0.5"), """s("bd sd").room(wet = "0.1 0.5")""") { it.room },
            Case("room.size", s("bd sd").room(size = "0.1 0.5"), """s("bd sd").room(size = "0.1 0.5")""") { it.roomSize },
            Case("room.fade", s("bd sd").room(fade = "0.1 0.5"), """s("bd sd").room(fade = "0.1 0.5")""") { it.roomFade },
            Case("room.lowpass", s("bd sd").room(lowpass = "0.1 0.5"), """s("bd sd").room(lowpass = "0.1 0.5")""") { it.roomLp },
            Case("room.dim", s("bd sd").room(dim = "0.1 0.5"), """s("bd sd").room(dim = "0.1 0.5")""") { it.roomDim },
            Case("delay.wet", s("bd sd").delay(wet = "0.1 0.5"), """s("bd sd").delay(wet = "0.1 0.5")""") { it.delay },
            Case("delay.time", s("bd sd").delay(time = "0.1 0.5"), """s("bd sd").delay(time = "0.1 0.5")""") { it.delayTime },
            Case("delay.feedback", s("bd sd").delay(feedback = "0.1 0.5"), """s("bd sd").delay(feedback = "0.1 0.5")""") { it.delayFeedback },
            Case("delay.cap", s("bd sd").delay(cap = "0.1 0.5"), """s("bd sd").delay(cap = "0.1 0.5")""") { it.delayCap },
            Case("phaser.rate", s("bd sd").phaser(rate = "0.1 0.5"), """s("bd sd").phaser(rate = "0.1 0.5")""") { it.phaserRate },
            Case("phaser.wet", s("bd sd").phaser(wet = "0.1 0.5"), """s("bd sd").phaser(wet = "0.1 0.5")""") { it.phaserDepth },
            Case("phaser.center", s("bd sd").phaser(center = "0.1 0.5"), """s("bd sd").phaser(center = "0.1 0.5")""") { it.phaserCenter },
            Case("phaser.sweep", s("bd sd").phaser(sweep = "0.1 0.5"), """s("bd sd").phaser(sweep = "0.1 0.5")""") { it.phaserSweep },
            Case("phaser.floor", s("bd sd").phaser(floor = "0.1 0.5"), """s("bd sd").phaser(floor = "0.1 0.5")""") { it.phaserFloor },
            Case("tremolo.depth", s("bd sd").tremolo(depth = "0.1 0.5"), """s("bd sd").tremolo(depth = "0.1 0.5")""") { it.tremoloDepth },
            Case("tremolo.sync", s("bd sd").tremolo(sync = "0.1 0.5"), """s("bd sd").tremolo(sync = "0.1 0.5")""") { it.tremoloSync },
            Case("tremolo.skew", s("bd sd").tremolo(skew = "0.1 0.5"), """s("bd sd").tremolo(skew = "0.1 0.5")""") { it.tremoloSkew },
            Case("tremolo.phase", s("bd sd").tremolo(phase = "0.1 0.5"), """s("bd sd").tremolo(phase = "0.1 0.5")""") { it.tremoloPhase },
            Case("distort.amount", s("bd sd").distort(amount = "0.1 0.5"), """s("bd sd").distort(amount = "0.1 0.5")""") { it.distort },
            Case("crush.amount", s("bd sd").crush(amount = "0.1 0.5"), """s("bd sd").crush(amount = "0.1 0.5")""") { it.crush },
            Case("coarse.amount", s("bd sd").coarse(amount = "0.1 0.5"), """s("bd sd").coarse(amount = "0.1 0.5")""") { it.coarse },
        ).forEach { case ->
            withClue(case.name) {
                listOf("kotlin" to case.kotlin, "script" to SprudelPattern.compile(case.script).shouldNotBeNull()).forEach { (door, p) ->
                    withClue(door) {
                        val events = p.queryArc(0.0, 1.0)
                        events.map { case.field(it.data) } shouldBe listOf(0.1, 0.5)
                    }
                }
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
