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
        row("fmh", """s("bd sd").fm(h = 2).fm(h = mul(2))""", { it.fmh }, 4.0, s("bd sd").fm(h = 2).fm(h = mul(2))),
        row("fmattack", """s("bd sd").fm(attack = 0.1).fm(attack = mul(2))""", { it.fmAttack }, 0.2, s("bd sd").fm(attack = 0.1).fm(attack = mul(2))),
        row("fmdecay", """s("bd sd").fm(decay = 0.2).fm(decay = mul(2))""", { it.fmDecay }, 0.4, s("bd sd").fm(decay = 0.2).fm(decay = mul(2))),
        row("fmsustain", """s("bd sd").fm(sustain = 0.5).fm(sustain = mul(0.5))""", { it.fmSustain }, 0.25, s("bd sd").fm(sustain = 0.5).fm(sustain = mul(0.5))),
        row("vowelWet", """s("bd sd").vowel(wet = 0.5).vowel(wet = mul(0.5))""", { it.vowelMix }, 0.25, s("bd sd").vowel(wet = 0.5).vowel(wet = mul(0.5))),
        row("vowelFloor", """s("bd sd").vowel(floor = 0.2).vowel(floor = add(0.3))""", { it.vowelFloor }, 0.5, s("bd sd").vowel(floor = 0.2).vowel(floor = add(0.3))),
        row("bodyWet", """s("bd sd").body(wet = 0.4).body(wet = mul(2))""", { it.bodyMix }, 0.8, s("bd sd").body(wet = 0.4).body(wet = mul(2))),
        row("bodyFloor", """s("bd sd").body(floor = 0.2).body(floor = add(0.3))""", { it.bodyFloor }, 0.5, s("bd sd").body(floor = 0.2).body(floor = add(0.3))),
        row("legato", """s("bd sd").legato(0.8).legato(mul(2))""", { it.legato }, 1.6, s("bd sd").legato(0.8).legato(mul(2))),
        row("vibrato.rate", """s("bd sd").vibrato(5).vibrato(mul(2))""", { it.vibrato }, 10.0, s("bd sd").vibrato(5).vibrato(mul(2))),
        row("vibratoMod", """s("bd sd").vibrato(depth = 0.3).vibrato(depth = mul(2))""", { it.vibratoMod }, 0.6, s("bd sd").vibrato(depth = 0.3).vibrato(depth = mul(2))),
        row("pattack", """s("bd sd").penv(attack = 0.1).penv(attack = mul(2))""", { it.pAttack }, 0.2, s("bd sd").penv(attack = 0.1).penv(attack = mul(2))),
        row("pdecay", """s("bd sd").penv(decay = 0.2).penv(decay = mul(2))""", { it.pDecay }, 0.4, s("bd sd").penv(decay = 0.2).penv(decay = mul(2))),
        row("prelease", """s("bd sd").penv(release = 0.3).penv(release = mul(2))""", { it.pRelease }, 0.6, s("bd sd").penv(release = 0.3).penv(release = mul(2))),
        row("penv.amount", """s("bd sd").penv(12).penv(mul(2))""", { it.pEnv }, 24.0, s("bd sd").penv(12).penv(mul(2))),
        row("pcurve", """s("bd sd").penv(curve = 1).penv(curve = add(1))""", { it.pCurve }, 2.0, s("bd sd").penv(curve = 1).penv(curve = add(1))),
        row("panchor", """s("bd sd").penv(anchor = 0.5).penv(anchor = mul(2))""", { it.pAnchor }, 1.0, s("bd sd").penv(anchor = 0.5).penv(anchor = mul(2))),
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
        row("fmh", """s("bd sd").fm(h = 2).pan(fm.h)""", { it.pan }, 2.0, s("bd sd").fm(h = 2).pan(fm.h)),
        row("fmattack", """s("bd sd").fm(attack = 0.1).pan(fm.attack)""", { it.pan }, 0.1, s("bd sd").fm(attack = 0.1).pan(fm.attack)),
        row("fmdecay", """s("bd sd").fm(decay = 0.2).pan(fm.decay)""", { it.pan }, 0.2, s("bd sd").fm(decay = 0.2).pan(fm.decay)),
        row("fmsustain", """s("bd sd").fm(sustain = 0.5).pan(fm.sustain)""", { it.pan }, 0.5, s("bd sd").fm(sustain = 0.5).pan(fm.sustain)),
        row("vowelWet", """s("bd sd").vowel(wet = 0.5).pan(vowel.wet)""", { it.pan }, 0.5, s("bd sd").vowel(wet = 0.5).pan(vowel.wet)),
        row("vowelFloor", """s("bd sd").vowel(floor = 0.2).pan(vowel.floor)""", { it.pan }, 0.2, s("bd sd").vowel(floor = 0.2).pan(vowel.floor)),
        row("bodyWet", """s("bd sd").body(wet = 0.4).pan(body.wet)""", { it.pan }, 0.4, s("bd sd").body(wet = 0.4).pan(body.wet)),
        row("bodyFloor", """s("bd sd").body(floor = 0.2).pan(body.floor)""", { it.pan }, 0.2, s("bd sd").body(floor = 0.2).pan(body.floor)),
        row("legato", """s("bd sd").legato(0.8).pan(legato)""", { it.pan }, 0.8, s("bd sd").legato(0.8).pan(legato)),
        row("vibrato.rate", """s("bd sd").vibrato(5).pan(vibrato.rate)""", { it.pan }, 5.0, s("bd sd").vibrato(5).pan(vibrato.rate)),
        row("vibratoMod", """s("bd sd").vibrato(depth = 0.3).pan(vibrato.depth)""", { it.pan }, 0.3, s("bd sd").vibrato(depth = 0.3).pan(vibrato.depth)),
        row("pattack", """s("bd sd").penv(attack = 0.1).pan(penv.attack)""", { it.pan }, 0.1, s("bd sd").penv(attack = 0.1).pan(penv.attack)),
        row("pdecay", """s("bd sd").penv(decay = 0.2).pan(penv.decay)""", { it.pan }, 0.2, s("bd sd").penv(decay = 0.2).pan(penv.decay)),
        row("prelease", """s("bd sd").penv(release = 0.3).pan(penv.release)""", { it.pan }, 0.3, s("bd sd").penv(release = 0.3).pan(penv.release)),
        row("penv.amount", """s("bd sd").penv(12).pan(penv.amount)""", { it.pan }, 12.0, s("bd sd").penv(12).pan(penv.amount)),
        row("pcurve", """s("bd sd").penv(curve = 1).pan(penv.curve)""", { it.pan }, 1.0, s("bd sd").penv(curve = 1).pan(penv.curve)),
        row("panchor", """s("bd sd").penv(anchor = 0.5).pan(penv.anchor)""", { it.pan }, 0.5, s("bd sd").penv(anchor = 0.5).pan(penv.anchor)),
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
        row("pamt", """s("bd sd").apply(pamt(2))""", { it.pEnv }, 2.0, s("bd sd").apply(pamt(2))),
        row("loopb", """s("bd sd").apply(loopb(2))""", { it.loopBegin }, 2.0, s("bd sd").apply(loopb(2))),
        row("loope", """s("bd sd").apply(loope(2))""", { it.loopEnd }, 2.0, s("bd sd").apply(loope(2))),
    )

    val aliasReadsBatchThree = listOf(
        row("clip", """s("bd sd").legato(2).pan(clip)""", { it.pan }, 2.0, s("bd sd").legato(2).pan(clip)),
        row("vib", """s("bd sd").vib(2).pan(vib.rate)""", { it.pan }, 2.0, s("bd sd").vib(2).pan(vib.rate)),
        row("pamt", """s("bd sd").pamt(2).pan(pamt.amount)""", { it.pan }, 2.0, s("bd sd").pamt(2).pan(pamt.amount)),
        row("loopb", """s("bd sd").loopBegin(2).pan(loopb)""", { it.pan }, 2.0, s("bd sd").loopBegin(2).pan(loopb)),
        row("loope", """s("bd sd").loopEnd(2).pan(loope)""", { it.pan }, 2.0, s("bd sd").loopEnd(2).pan(loope)),
    )

    // Batch four: the dynamics leftovers, the routing fields, the compressor threshold and fmenv.
    val mappedBatchFour = listOf(
        row("unison.voices", """s("bd sd").unison(3).unison(mul(2))""", { it.oscParams?.get("voices") }, 6.0, s("bd sd").unison(3).unison(mul(2))),
        row("spread", """s("bd sd").unison(spread = 0.2).unison(spread = mul(2))""", { it.oscParams?.get("spread") }, 0.4, s("bd sd").unison(spread = 0.2).unison(spread = mul(2))),
        row("panSpread", """s("bd sd").unison(pan = 0.5).unison(pan = mul(2))""", { it.oscParams?.get("panSpread") }, 1.0, s("bd sd").unison(pan = 0.5).unison(pan = mul(2))),
        row("density", """s("bd sd").density(0.5).density(mul(2))""", { it.oscParams?.get("density") }, 1.0, s("bd sd").density(0.5).density(mul(2))),
        row("orbit", """s("bd sd").orbit(1).orbit(add(1))""", { it.cylinder?.toDouble() }, 2.0, s("bd sd").orbit(1).orbit(add(1))),
        row("duckorbit", """s("bd sd").duck(1).duck(add(1))""", { it.duckCylinder?.toDouble() }, 2.0, s("bd sd").duck(1).duck(add(1))),
        row("duckattack", """s("bd sd").duck(attack = 0.05).duck(attack = mul(2))""", { it.duckAttack }, 0.1, s("bd sd").duck(attack = 0.05).duck(attack = mul(2))),
        row("duckdepth", """s("bd sd").duck(depth = 0.5).duck(depth = mul(2))""", { it.duckDepth }, 1.0, s("bd sd").duck(depth = 0.5).duck(depth = mul(2))),
        row("compressor.threshold", """s("bd sd").compressor(-12).compressor(add(-6))""", { it.compressorThreshold }, -18.0, s("bd sd").compressor(-12).compressor(add(-6))),
        row("fmenv", """s("bd sd").fm(200).fm(mul(2))""", { it.fmEnv }, 400.0, s("bd sd").fm(200).fm(mul(2))),
        row("analog", """s("bd sd").analog(2).analog(mul(2))""", { it.oscParams?.get("analog") }, 4.0, s("bd sd").analog(2).analog(mul(2))),
        row("duty", """s("bd sd").duty(0.25).duty(mul(2))""", { it.oscParams?.get("duty") }, 0.5, s("bd sd").duty(0.25).duty(mul(2))),
        row("onepole", """s("bd sd").onepole(1000).onepole(mul(2))""", { it.oscParams?.get("onepole") }, 2000.0, s("bd sd").onepole(1000).onepole(mul(2))),
    )

    val readBatchFour = listOf(
        row("unison.voices", """s("bd sd").unison(3).pan(unison.voices)""", { it.pan }, 3.0, s("bd sd").unison(3).pan(unison.voices)),
        row("spread", """s("bd sd").unison(spread = 0.2).pan(unison.spread)""", { it.pan }, 0.2, s("bd sd").unison(spread = 0.2).pan(unison.spread)),
        row("panSpread", """s("bd sd").unison(pan = 0.5).pan(unison.pan)""", { it.pan }, 0.5, s("bd sd").unison(pan = 0.5).pan(unison.pan)),
        row("density", """s("bd sd").density(0.5).pan(density)""", { it.pan }, 0.5, s("bd sd").density(0.5).pan(density)),
        row("orbit", """s("bd sd").orbit(1).pan(orbit)""", { it.pan }, 1.0, s("bd sd").orbit(1).pan(orbit)),
        row("duckorbit", """s("bd sd").duck(1).pan(duck.orbit)""", { it.pan }, 1.0, s("bd sd").duck(1).pan(duck.orbit)),
        row("duckattack", """s("bd sd").duck(attack = 0.05).pan(duck.attack)""", { it.pan }, 0.05, s("bd sd").duck(attack = 0.05).pan(duck.attack)),
        row("duckdepth", """s("bd sd").duck(depth = 0.5).pan(duck.depth)""", { it.pan }, 0.5, s("bd sd").duck(depth = 0.5).pan(duck.depth)),
        row("compressor.threshold", """s("bd sd").compressor(-12).pan(compressor.threshold)""", { it.pan }, -12.0, s("bd sd").compressor(-12).pan(compressor.threshold)),
        row("fmenv", """s("bd sd").fm(200).pan(fm.env)""", { it.pan }, 200.0, s("bd sd").fm(200).pan(fm.env)),
        row("analog", """s("bd sd").analog(2).pan(analog)""", { it.pan }, 2.0, s("bd sd").analog(2).pan(analog)),
        row("duty", """s("bd sd").duty(0.25).pan(duty)""", { it.pan }, 0.25, s("bd sd").duty(0.25).pan(duty)),
        row("onepole", """s("bd sd").onepole(1000).pan(onepole)""", { it.pan }, 1000.0, s("bd sd").onepole(1000).pan(onepole)),
    )

    val aliasSetsBatchFour = listOf(
        row("uni", """s("bd sd").apply(uni(2))""", { it.oscParams?.get("voices") }, 2.0, s("bd sd").apply(uni(2))),
        row("d", """s("bd sd").apply(d(2))""", { it.oscParams?.get("density") }, 2.0, s("bd sd").apply(d(2))),
        row("o", """s("bd sd").apply(o(2))""", { it.cylinder?.toDouble() }, 2.0, s("bd sd").apply(o(2))),
        row("comp", """s("bd sd").apply(comp(2))""", { it.compressorThreshold }, 2.0, s("bd sd").apply(comp(2))),
    )

    val aliasReadsBatchFour = listOf(
        row("uni", """s("bd sd").uni(2).pan(uni.voices)""", { it.pan }, 2.0, s("bd sd").uni(2).pan(uni.voices)),
        row("d", """s("bd sd").density(2).pan(d)""", { it.pan }, 2.0, s("bd sd").density(2).pan(d)),
        row("o", """s("bd sd").orbit(2).pan(o)""", { it.pan }, 2.0, s("bd sd").orbit(2).pan(o)),
        row("comp", """s("bd sd").comp(2).pan(comp.threshold)""", { it.pan }, 2.0, s("bd sd").comp(2).pan(comp.threshold)),
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

    "batch G: a mapper on one slot leaves the other slots alone, in both doors" {
        class Case(val name: String, val kotlin: SprudelPattern, val script: String, val check: (SprudelVoiceData) -> Unit)
        listOf(
            Case("compressor(ratio = mul(2))", note("c e").compressor(-20, 4, 6, 0.003, 0.1).compressor(ratio = mul(2)), """note("c e").compressor(-20, 4, 6, 0.003, 0.1).compressor(ratio = mul(2))""") {
                it.compressorThreshold shouldBe -20.0
                it.compressorRatio shouldBe 8.0
                it.compressorKnee shouldBe 6.0
                it.compressorAttack shouldBe 0.003
                it.compressorRelease shouldBe 0.1
            },
            Case("unison(spread = mul(2))", note("c e").unison(5, 0.3, 0.5).unison(spread = mul(2)), """note("c e").unison(5, 0.3, 0.5).unison(spread = mul(2))""") {
                it.oscParams?.get("voices") shouldBe 5.0
                it.oscParams?.get("spread") shouldBe 0.6
                it.oscParams?.get("panSpread") shouldBe 0.5
            },
            Case("duck(depth = mul(0.5))", note("c e").duck(1, 0.8, 0.2).duck(depth = mul(0.5)), """note("c e").duck(1, 0.8, 0.2).duck(depth = mul(0.5))""") {
                it.duckCylinder shouldBe 1
                it.duckDepth shouldBe 0.4
                it.duckAttack shouldBe 0.2
            },
            Case("vibrato(depth = mul(2))", note("c e").vibrato(5, 0.5).vibrato(depth = mul(2)), """note("c e").vibrato(5, 0.5).vibrato(depth = mul(2))""") {
                it.vibrato shouldBe 5.0
                it.vibratoMod shouldBe 1.0
            },
            Case("penv(curve = mul(2))", note("c e").penv(12, 0.01, 0.2, 0.3, 1.0, 0.5).penv(curve = mul(2)), """note("c e").penv(12, 0.01, 0.2, 0.3, 1.0, 0.5).penv(curve = mul(2))""") {
                it.pEnv shouldBe 12.0
                it.pAttack shouldBe 0.01
                it.pDecay shouldBe 0.2
                it.pRelease shouldBe 0.3
                it.pCurve shouldBe 2.0
                it.pAnchor shouldBe 0.5
            },
            Case("fm(h = mul(2))", note("c e").fm(200, 2, 0.01, 0.3, 0.5).fm(h = mul(2)), """note("c e").fm(200, 2, 0.01, 0.3, 0.5).fm(h = mul(2))""") {
                it.fmEnv shouldBe 200.0
                it.fmh shouldBe 4.0
                it.fmAttack shouldBe 0.01
                it.fmDecay shouldBe 0.3
                it.fmSustain shouldBe 0.5
            },
            Case("vowel(wet = mul(2))", note("c e").vowel("a", 0.4, 0.2).vowel(wet = mul(2)), """note("c e").vowel("a", 0.4, 0.2).vowel(wet = mul(2))""") {
                it.vowel shouldBe "a"
                it.vowelMix shouldBe 0.8
                it.vowelFloor shouldBe 0.2
            },
            Case("body(floor = add(0.1))", note("c e").body("wood", 0.4, 0.2).body(floor = add(0.1)), """note("c e").body("wood", 0.4, 0.2).body(floor = add(0.1))""") {
                it.body shouldBe "wood"
                it.bodyMix shouldBe 0.4
                it.bodyFloor shouldBe (0.3 plusOrMinus 1e-9)
            },
        ).forEach { case ->
            withClue(case.name) {
                listOf("kotlin" to case.kotlin, "script" to SprudelPattern.compile(case.script).shouldNotBeNull()).forEach { (door, p) ->
                    withClue(door) { p.cycles().forEach { events -> events shouldHaveSize 2; events.forEach { case.check(it.data) } } }
                }
            }
        }
    }

    "batch G: every numeric slot takes a control pattern per event, in both doors" {
        class Case(val name: String, val kotlin: SprudelPattern, val script: String, val field: (SprudelVoiceData) -> Double?)
        val cases = mutableListOf<Case>()
        fun add(name: String, kotlin: SprudelPattern, script: String, field: (SprudelVoiceData) -> Double?) { cases.add(Case(name, kotlin, script, field)) }
        add("compressor", s("bd sd").compressor(threshold = "0.1 0.5"), """s("bd sd").compressor(threshold = "0.1 0.5")""") { it.compressorThreshold }
        add("compressor.ratio", s("bd sd").compressor(ratio = "0.1 0.5"), """s("bd sd").compressor(ratio = "0.1 0.5")""") { it.compressorRatio }
        add("compressor.knee", s("bd sd").compressor(knee = "0.1 0.5"), """s("bd sd").compressor(knee = "0.1 0.5")""") { it.compressorKnee }
        add("compressor.attack", s("bd sd").compressor(attack = "0.1 0.5"), """s("bd sd").compressor(attack = "0.1 0.5")""") { it.compressorAttack }
        add("compressor.release", s("bd sd").compressor(release = "0.1 0.5"), """s("bd sd").compressor(release = "0.1 0.5")""") { it.compressorRelease }
        add("unison", s("bd sd").unison(voices = "0.1 0.5"), """s("bd sd").unison(voices = "0.1 0.5")""") { it.oscParams?.get("voices") }
        add("unison", s("bd sd").unison(spread = "0.1 0.5"), """s("bd sd").unison(spread = "0.1 0.5")""") { it.oscParams?.get("spread") }
        add("unison", s("bd sd").unison(pan = "0.1 0.5"), """s("bd sd").unison(pan = "0.1 0.5")""") { it.oscParams?.get("panSpread") }
        add("duck", s("bd sd").duck(depth = "0.1 0.5"), """s("bd sd").duck(depth = "0.1 0.5")""") { it.duckDepth }
        add("duck", s("bd sd").duck(attack = "0.1 0.5"), """s("bd sd").duck(attack = "0.1 0.5")""") { it.duckAttack }
        add("vibrato", s("bd sd").vibrato(rate = "0.1 0.5"), """s("bd sd").vibrato(rate = "0.1 0.5")""") { it.vibrato }
        add("vibrato", s("bd sd").vibrato(depth = "0.1 0.5"), """s("bd sd").vibrato(depth = "0.1 0.5")""") { it.vibratoMod }
        add("penv", s("bd sd").penv(amount = "0.1 0.5"), """s("bd sd").penv(amount = "0.1 0.5")""") { it.pEnv }
        add("penv", s("bd sd").penv(attack = "0.1 0.5"), """s("bd sd").penv(attack = "0.1 0.5")""") { it.pAttack }
        add("penv", s("bd sd").penv(decay = "0.1 0.5"), """s("bd sd").penv(decay = "0.1 0.5")""") { it.pDecay }
        add("penv", s("bd sd").penv(release = "0.1 0.5"), """s("bd sd").penv(release = "0.1 0.5")""") { it.pRelease }
        add("penv", s("bd sd").penv(curve = "0.1 0.5"), """s("bd sd").penv(curve = "0.1 0.5")""") { it.pCurve }
        add("penv", s("bd sd").penv(anchor = "0.1 0.5"), """s("bd sd").penv(anchor = "0.1 0.5")""") { it.pAnchor }
        add("fm", s("bd sd").fm(env = "0.1 0.5"), """s("bd sd").fm(env = "0.1 0.5")""") { it.fmEnv }
        add("fm", s("bd sd").fm(h = "0.1 0.5"), """s("bd sd").fm(h = "0.1 0.5")""") { it.fmh }
        add("fm", s("bd sd").fm(attack = "0.1 0.5"), """s("bd sd").fm(attack = "0.1 0.5")""") { it.fmAttack }
        add("fm", s("bd sd").fm(decay = "0.1 0.5"), """s("bd sd").fm(decay = "0.1 0.5")""") { it.fmDecay }
        add("fm", s("bd sd").fm(sustain = "0.1 0.5"), """s("bd sd").fm(sustain = "0.1 0.5")""") { it.fmSustain }
        add("vowel", s("bd sd").vowel(wet = "0.1 0.5"), """s("bd sd").vowel(wet = "0.1 0.5")""") { it.vowelMix }
        add("vowel", s("bd sd").vowel(floor = "0.1 0.5"), """s("bd sd").vowel(floor = "0.1 0.5")""") { it.vowelFloor }
        add("body", s("bd sd").body(wet = "0.1 0.5"), """s("bd sd").body(wet = "0.1 0.5")""") { it.bodyMix }
        add("body", s("bd sd").body(floor = "0.1 0.5"), """s("bd sd").body(floor = "0.1 0.5")""") { it.bodyFloor }
        cases.forEach { case ->
            withClue(case.name) {
                listOf("kotlin" to case.kotlin, "script" to SprudelPattern.compile(case.script).shouldNotBeNull()).forEach { (door, p) ->
                    withClue(door) { p.queryArc(0.0, 1.0).map { case.field(it.data) } shouldBe listOf(0.1, 0.5) }
                }
            }
        }
        listOf("kotlin" to s("bd sd").duck(orbit = "1 2"), "script" to SprudelPattern.compile("""s("bd sd").duck(orbit = "1 2")""").shouldNotBeNull()).forEach { (door, p) ->
            withClue("duck $door") { p.queryArc(0.0, 1.0).map { it.data.duckCylinder } shouldBe listOf(1, 2) }
        }
    }

    "batch G: a tail-only call does not reinterpret a numeric receiver into the head slot, in both doors" {
        class Case(val name: String, val kotlin: SprudelPattern, val script: String, val head: (SprudelVoiceData) -> Any?, val tail: (SprudelVoiceData) -> Double?)
        listOf(
            Case("compressor(ratio = 4)", seq("3 4").compressor(ratio = 4), """seq("3 4").compressor(ratio = 4)""", { it.compressorThreshold }, { it.compressorRatio }),
            Case("unison(spread = 4)", seq("3 4").unison(spread = 4), """seq("3 4").unison(spread = 4)""", { it.oscParams?.get("voices") }, { it.oscParams?.get("spread") }),
            Case("duck(depth = 4)", seq("3 4").duck(depth = 4), """seq("3 4").duck(depth = 4)""", { it.duckCylinder }, { it.duckDepth }),
            Case("vibrato(depth = 4)", seq("3 4").vibrato(depth = 4), """seq("3 4").vibrato(depth = 4)""", { it.vibrato }, { it.vibratoMod }),
            Case("penv(attack = 4)", seq("3 4").penv(attack = 4), """seq("3 4").penv(attack = 4)""", { it.pEnv }, { it.pAttack }),
            Case("fm(h = 4)", seq("3 4").fm(h = 4), """seq("3 4").fm(h = 4)""", { it.fmEnv }, { it.fmh }),
            Case("vowel(wet = 4)", seq("3 4").vowel(wet = 4), """seq("3 4").vowel(wet = 4)""", { it.vowel }, { it.vowelMix }),
            Case("body(wet = 4)", seq("3 4").body(wet = 4), """seq("3 4").body(wet = 4)""", { it.body }, { it.bodyMix }),
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

    "batch G: a bare call reinterprets the pattern's values as the head slot, in both doors" {
        listOf("kotlin" to seq("3 4").fm(), "script" to SprudelPattern.compile("""seq("3 4").fm()""").shouldNotBeNull()).forEach { (door, p) ->
            withClue("fm $door") { p.queryArc(0.0, 1.0).map { it.data.fmEnv } shouldBe listOf(3.0, 4.0) }
        }
        listOf("kotlin" to seq("1 2").duck(), "script" to SprudelPattern.compile("""seq("1 2").duck()""").shouldNotBeNull()).forEach { (door, p) ->
            withClue("duck $door") { p.queryArc(0.0, 1.0).map { it.data.duckCylinder } shouldBe listOf(1, 2) }
        }
        listOf("kotlin" to seq("wood glass").body(), "script" to SprudelPattern.compile("""seq("wood glass").body()""").shouldNotBeNull()).forEach { (door, p) ->
            withClue("body $door") { p.queryArc(0.0, 1.0).map { it.data.body } shouldBe listOf("wood", "glass") }
        }
    }

    "batch G: every numeric head keeps its value where the control pattern has a gap, in both doors" {
        class Case(val name: String, val kotlin: SprudelPattern, val script: String, val field: (SprudelVoiceData) -> Double?)
        listOf(
            Case("compressor", s("bd sd").compressor(-8).compressor("<-5 ~>"), """s("bd sd").compressor(-8).compressor("<-5 ~>")""") { it.compressorThreshold },
            Case("unison", s("bd sd").unison(8).unison("<5 ~>"), """s("bd sd").unison(8).unison("<5 ~>")""") { it.oscParams?.get("voices") },
            Case("vibrato", s("bd sd").vibrato(8).vibrato("<5 ~>"), """s("bd sd").vibrato(8).vibrato("<5 ~>")""") { it.vibrato },
            Case("penv", s("bd sd").penv(8).penv("<5 ~>"), """s("bd sd").penv(8).penv("<5 ~>")""") { it.pEnv },
            Case("fm", s("bd sd").fm(8).fm("<5 ~>"), """s("bd sd").fm(8).fm("<5 ~>")""") { it.fmEnv },
        ).forEach { case ->
            withClue(case.name) {
                val sign = if (case.name == "compressor") -1.0 else 1.0
                listOf("kotlin" to case.kotlin, "script" to SprudelPattern.compile(case.script).shouldNotBeNull()).forEach { (door, p) ->
                    withClue(door) {
                        p.queryArc(0.0, 1.0).map { case.field(it.data) } shouldBe listOf(5.0 * sign, 5.0 * sign)
                        p.queryArc(1.0, 2.0).map { case.field(it.data) } shouldBe listOf(8.0 * sign, 8.0 * sign)
                    }
                }
            }
        }
        listOf("kotlin" to s("bd sd").duck(2).duck("<1 ~>"), "script" to SprudelPattern.compile("""s("bd sd").duck(2).duck("<1 ~>")""").shouldNotBeNull()).forEach { (door, p) ->
            withClue("duck $door") {
                p.queryArc(0.0, 1.0).map { it.data.duckCylinder } shouldBe listOf(1, 1)
                p.queryArc(1.0, 2.0).map { it.data.duckCylinder } shouldBe listOf(2, 2)
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
