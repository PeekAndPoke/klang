/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.TWO_PI
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.accelerate
import io.peekandpoke.klang.audio_bridge.adsr
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.detune
import io.peekandpoke.klang.audio_bridge.fm
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.pitchMod
import io.peekandpoke.klang.audio_bridge.plus
import io.peekandpoke.klang.audio_bridge.vibrato
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random

/**
 * Pitch pipeline step 3b, decision D1 (maintainer, 2026-10-08): **any pitch modulation that reaches an `fm` node's
 * carrier also reaches its modulator**, so the operator moves as one and the ratio stays exact.
 *
 * **The invariant, read off the oscillators, not by ear.** Every oscillator under test is the [IgnitorDsl.Sample] leaf
 * over one [PitchProbe]: it records, per call, the frequency argument it was driven at and the ratio stream it read
 * (`ctx.phaseMod`, 1.0 where none). A sine's phase increment is `2 pi freq / sampleRate` times that ratio, so the
 * modulator's increment over the carrier's must equal the fm `ratio`. Stronger, and exact: the modulator runs at
 * `carrierHz * ratio` and reads the carrier's ratio stream BIT FOR BIT, because the probe writes 0 on the lanes it
 * checks, which makes the fm's own term exactly 1.0 (`1 + 0 * depth / freq`); the carrier then reads `outer * 1.0`.
 * Lanes are told apart by their frequency argument, so every scenario puts each oscillator at its own frequency, and
 * each lane must be called exactly ONCE per block (a second call is a mod rendered twice, the double-advance hazard).
 *
 * **The other half:** a pitch node that does NOT reach the carrier does not reach the modulator either (inside the
 * modulator only, on a sibling, on the carrier only). **The placement rule** (maintainer, 2026-10-09): a pitch node means what it wraps;
 * above the fm, the whole operator (the note's pitch); on the modulator, the modulator alone; on the carrier, the
 * carrier alone. The three `PLACEMENT` rows pin it.
 *
 * The chains, the nested and summed shapes, parameter positions, shared nodes and the per-block render counts are
 * [FmModulatorTopologySpec].
 *
 * The sprudel rows write the slot bag the sprudel door writes (`vib(6, 0.5)` is `vibrato.rate` 6 and
 * `vibrato.semitones` 0.5, pinned by `ClassicSlotParamsSpec` and the door-parity specs) over a `classic()` fm
 * instrument. Sprudel's own `fm` door is `classic()`'s FM stage since pitch pipeline step 4 (`fm.depth`, `fm.ratio`):
 * innermost, so over the bell it is an fm over an fm's carrier, and the bell's modulator follows it and the doors
 * above it (until step 4 the strip's buffer was emulated here as a root `phaseMod`). The classic FM's own modulator
 * is a plain sine the probe does not track; that it follows the doors is the oracle in `ClassicFmSpec`.
 */
class FmModulatorFollowsPitchSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 40
    val noteHz = 220.0
    val gateFrames = 2000

    fun c(v: Double) = IgnitorDsl.Constant(v)

    /** A plain sine at no drift: a modulator or LFO the probe does not track. */
    fun sine(freq: IgnitorDsl = IgnitorDsl.Freq) = IgnitorDsl.Sine(freq = freq, analog = c(0.0))

    val probed = IgnitorDsl.Sample

    /** One fm over a probed carrier and a probed modulator. */
    fun fmOp(carrier: IgnitorDsl = probed, modulator: IgnitorDsl = probed, ratio: Double, depth: Double) =
        carrier.fm(modulator = modulator, ratio = ratio, depth = depth)

    fun pitchEnvelope(inner: IgnitorDsl) = IgnitorDsl.PitchEnvelope(
        inner = inner, semitones = c(12.0), attack = c(0.01), decay = c(0.05), sustain = c(0.0), release = c(0.1),
    )

    /** One oscillator's record: the frequency it was driven at and the ratio it read, frame by frame. */
    class Lane(val hz: Double, val ratios: DoubleArray)

    /**
     * Records every call of the [IgnitorDsl.Sample] leaves. Writes [emitValue] on the lanes in [emitAt] (an fm whose
     * term must be visible) and 0 elsewhere (an fm whose own term must stay exactly 1.0).
     */
    class PitchProbe(private val emitAt: Set<Double>, private val emitValue: Double) : Ignitor {
        val calls = ArrayList<Triple<Int, Double, DoubleArray>>()

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val pm = ctx.phaseMod
            val window = DoubleArray(ctx.length) { pm?.get(ctx.offset + it) ?: 1.0 }
            val out = if (freqHz in emitAt) emitValue else 0.0

            calls.add(Triple(ctx.voiceElapsedFrames, freqHz, window))

            for (i in ctx.offset until ctx.windowEnd) {
                buffer[i] = out
            }
        }
    }

    /**
     * Renders [dsl] with [bag] and returns one [Lane] per frequency. A lane called twice in one block fails the row.
     * [releaseAt] moves the gate there the way a realtime note-off does, before the block that holds it (a held voice:
     * the gate starts far).
     */
    fun render(
        dsl: IgnitorDsl,
        bag: Map<String, Double> = emptyMap(),
        emitAt: Set<Double> = emptySet(),
        releaseAt: Int? = null,
    ): Map<Double, Lane> {
        val probe = PitchProbe(emitAt = emitAt, emitValue = 0.7)
        val gate = if (releaseAt != null) Int.MAX_VALUE / 2 else gateFrames
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = gate,
            gateEndFrame = gate,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(3),
        )
        val built = dsl.buildExciter(ignitorParams = bag, random = ctx.random, freqHz = noteHz, sampleRate = sampleRate, sampleSource = probe).ignitor
        val buffer = AudioBuffer(blockFrames)

        for (b in 0 until blocks) {
            val start = b * blockFrames

            if (releaseAt != null && releaseAt >= start && releaseAt < start + blockFrames) {
                ctx.gateEndFrame = releaseAt
            }

            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            ctx.voiceElapsedFrames = start

            built.generate(buffer, noteHz, ctx)
        }

        val lanes = LinkedHashMap<Double, Lane>()

        for ((hz, calls) in probe.calls.groupBy { it.second }) {
            withClue("the oscillator at $hz Hz is called once per block (a second call renders its mod twice)") {
                calls.map { it.first } shouldBe (0 until blocks).map { it * blockFrames }
            }

            val ratios = DoubleArray(blocks * blockFrames)

            for ((elapsed, _, window) in calls) {
                window.copyInto(destination = ratios, destinationOffset = elapsed)
            }

            lanes[hz] = Lane(hz = hz, ratios = ratios)
        }

        return lanes
    }

    fun Map<Double, Lane>.lane(hz: Double): Lane = this[hz] ?: error("no oscillator at $hz Hz; lanes: $keys")

    /**
     * THE INVARIANT: the modulator of the fm whose carrier runs at [carrierHz] runs at `carrierHz * ratio` and reads
     * the carrier's ratio stream bit for bit, so its phase increment over the carrier's is [ratio] on every frame.
     * [bent] asks that the carrier's stream is not all 1.0 (the pitch modulation reached it: the positive control).
     */
    fun Map<Double, Lane>.shouldFollow(carrierHz: Double, ratio: Double, bent: Boolean = true) {
        val carrier = lane(carrierHz)
        val modulator = lane(carrierHz * ratio)
        val carrierInc = TWO_PI * carrier.hz / sampleRate
        val modulatorInc = TWO_PI * modulator.hz / sampleRate
        var differing = 0
        var worst = 0.0

        for (i in carrier.ratios.indices) {
            if (modulator.ratios[i] != carrier.ratios[i]) {
                differing++
            }

            worst = maxOf(worst, abs((modulatorInc * modulator.ratios[i]) / (carrierInc * carrier.ratios[i]) / ratio - 1.0))
        }

        println("FM-FOLLOW carrier $carrierHz Hz, ratio $ratio: frames differing $differing of ${carrier.ratios.size}, worst increment ratio error $worst")

        if (bent) {
            withClue("the pitch modulation reaches the carrier at $carrierHz Hz (the positive control)") {
                carrier.ratios.any { it != 1.0 } shouldBe true
            }
        }

        withClue("the modulator at ${modulator.hz} Hz reads the carrier's ratio stream frame for frame") {
            differing shouldBe 0
        }

        // Documentation, kept on purpose: the lane is looked up at `carrierHz * ratio` and its stream is asserted equal
        // above, so this cannot fail on its own (review round 1, A NIT 3); it states the invariant in the plan's units.
        withClue("the modulator's phase increment over the carrier's is the ratio $ratio") {
            (worst <= 1e-12) shouldBe true
        }
    }

    fun Map<Double, Lane>.shouldBeUnbent(hz: Double) {
        withClue("the oscillator at $hz Hz reads no pitch modulation") {
            lane(hz).ratios.count { it != 1.0 } shouldBe 0
        }
    }

    // ── The authored rows: a pitch node ABOVE the fm ────────────────────────────────────────────────────────────

    val op = fmOp(ratio = 3.5, depth = 400.0)

    "PLACEMENT, above the fm: a vibrato above the fm bends the whole operator (the note's pitch)" {
        render(op.vibrato(rate = 6.0, semitones = 0.5)).shouldFollow(carrierHz = noteHz, ratio = 3.5)
    }

    "authored: a pitchMod above the fm (an LFO at a fixed rate) bends the modulator with the carrier" {
        render(op.pitchMod(sine(freq = c(5.0)).mul(0.03))).shouldFollow(carrierHz = noteHz, ratio = 3.5)
    }

    "authored: a pitch envelope above the fm bends the modulator with the carrier" {
        render(pitchEnvelope(op)).shouldFollow(carrierHz = noteHz, ratio = 3.5)
    }

    "authored: an accelerate above the fm bends the modulator with the carrier, through the gate and the hold" {
        render(op.accelerate(semitones = 7.0)).shouldFollow(carrierHz = noteHz, ratio = 3.5)
    }

    "authored: a vibrato inside a pitchMod, both above the fm" {
        render(op.vibrato(rate = 6.0, semitones = 0.5).pitchMod(sine(freq = c(3.0)).mul(0.02))).shouldFollow(carrierHz = noteHz, ratio = 3.5)
    }

    "authored: a pitchMod inside a vibrato, both above the fm" {
        render(op.pitchMod(sine(freq = c(3.0)).mul(0.02)).vibrato(rate = 6.0, semitones = 0.5)).shouldFollow(carrierHz = noteHz, ratio = 3.5)
    }

    "authored: three pitch nodes above the fm, vibrato outermost (step 4's classic head)" {
        render(pitchEnvelope(op).accelerate(semitones = 7.0).vibrato(rate = 6.0, semitones = 0.5)).shouldFollow(carrierHz = noteHz, ratio = 3.5)
    }

    "authored: three pitch nodes above the fm, the pitch envelope outermost" {
        render(pitchEnvelope(op.vibrato(rate = 6.0, semitones = 0.5).accelerate(semitones = -5.0))).shouldFollow(carrierHz = noteHz, ratio = 3.5)
    }

    "authored: a vibrato whose rate reads the note (a freq-keyed mod) bends the modulator with the carrier" {
        val freqRate = IgnitorDsl.Vibrato(inner = op, rate = IgnitorDsl.Freq.mul(0.03), semitones = c(0.5))

        render(freqRate).shouldFollow(carrierHz = noteHz, ratio = 3.5)
    }

    "authored: a pitchMod at audio rate on the note (a freq-keyed mod) bends the modulator with the carrier" {
        render(op.pitchMod(sine().mul(0.01))).shouldFollow(carrierHz = noteHz, ratio = 3.5)
    }

    // ── Stacks ───────────────────────────────────────────────────────────────────────────────────────────────────

    "authored: an fm whose modulator is an fm, under a vibrato: the inner modulator follows too" {
        val stacked = fmOp(modulator = fmOp(ratio = 2.5, depth = 100.0), ratio = 1.5, depth = 300.0)
        val lanes = render(stacked.vibrato(rate = 6.0, semitones = 0.5))

        lanes.shouldFollow(carrierHz = noteHz, ratio = 1.5)
        lanes.shouldFollow(carrierHz = noteHz * 1.5, ratio = 2.5)
    }

    "authored: two fm nodes summed under one vibrato: both follow, each keeps its ratio" {
        val other = fmOp(ratio = 2.5, depth = 200.0).detune(semitones = 7.0)
        val lanes = render((fmOp(ratio = 1.5, depth = 300.0) + other).vibrato(rate = 6.0, semitones = 0.5))
        val otherHz = noteHz * 2.0.pow(7.0 / 12.0)

        lanes.shouldFollow(carrierHz = noteHz, ratio = 1.5)
        lanes.shouldFollow(carrierHz = otherHz, ratio = 2.5)
    }

    "authored: an fm under a detune under a vibrato: the detune moves both, the vibrato bends both" {
        val lanes = render(op.detune(semitones = 7.0).vibrato(rate = 6.0, semitones = 0.5))

        lanes.shouldFollow(carrierHz = noteHz * 2.0.pow(7.0 / 12.0), ratio = 3.5)
    }

    "authored: an fm stacked with its detuned copy (the sgpad shape) under a vibrato: every layer follows" {
        val lanes = render((op + op.detune(semitones = 0.1)).vibrato(rate = 6.0, semitones = 0.5))

        lanes.shouldFollow(carrierHz = noteHz, ratio = 3.5)
        lanes.shouldFollow(carrierHz = noteHz * 2.0.pow(0.1 / 12.0), ratio = 3.5)
    }

    "authored: an fm over an fm's carrier (two modulators on one carrier): the inner modulator follows the outer fm" {
        // The outer modulator is a plain sine: its term must be visible on the carrier, and a second probed leaf
        // under the same mod would share the first one's build (one `IgnitorDsl.Sample` object, one cache entry).
        val inner = fmOp(ratio = 1.5, depth = 300.0)
        val lanes = render(inner.fm(modulator = sine(), ratio = 2.5, depth = 200.0))

        lanes.shouldFollow(carrierHz = noteHz, ratio = 1.5)
    }

    // ── What does NOT reach the carrier does not reach the modulator ────────────────────────────────────────────

    "PLACEMENT, on the modulator: a vibrato on the modulator only bends the modulator alone" {
        val ownOnly = render(fmOp(modulator = probed.vibrato(rate = 6.0, semitones = 0.5), ratio = 3.5, depth = 400.0))
        val own = render(probed.vibrato(rate = 6.0, semitones = 0.5)).lane(noteHz).ratios

        ownOnly.shouldBeUnbent(noteHz)
        ownOnly.lane(noteHz * 3.5).ratios.toList() shouldBe own.toList()
    }

    "authored: a vibrato inside the modulator under an outer vibrato: the modulator reads the outer times its own" {
        val lanes = render(fmOp(modulator = probed.vibrato(rate = 6.0, semitones = 0.5), ratio = 3.5, depth = 400.0).vibrato(rate = 4.0, semitones = 0.3))
        val own = render(probed.vibrato(rate = 6.0, semitones = 0.5)).lane(noteHz).ratios
        val carrier = lanes.lane(noteHz).ratios
        val modulator = lanes.lane(noteHz * 3.5).ratios

        carrier.any { it != 1.0 } shouldBe true
        modulator.indices.count { modulator[it] != carrier[it] * own[it] } shouldBe 0
    }

    "authored: a vibrato on a sibling reaches neither the carrier nor the modulator" {
        // The sibling is a plain sine: a probed leaf would read no Freq, so its detune folds and its lane would sit
        // at the carrier's frequency.
        val lanes = render(op + sine().vibrato(rate = 6.0, semitones = 0.5))

        lanes.shouldBeUnbent(noteHz)
        lanes.shouldBeUnbent(noteHz * 3.5)
    }

    "PLACEMENT, on the carrier: a vibrato on the carrier only bends the carrier alone, the modulator does not follow" {
        val lanes = render(probed.vibrato(rate = 6.0, semitones = 0.5).fm(modulator = probed, ratio = 3.5, depth = 400.0))

        lanes.lane(noteHz).ratios.any { it != 1.0 } shouldBe true
        lanes.shouldBeUnbent(noteHz * 3.5)
    }

    // ── The sprudel doors over a classic() fm instrument (the slot bags the doors write) ────────────────────────

    val vib = mapOf("vibrato.rate" to 6.0, "vibrato.semitones" to 0.5)
    val penv = mapOf("penv.semitones" to 12.0, "penv.attack" to 0.01, "penv.decay" to 0.05, "penv.sustain" to 0.0, "penv.release" to 0.1)
    val accel = mapOf("accelerate" to 7.0)

    /** Sprudel's `fm(300, 1.4)`: `classic()`'s FM stage (pitch pipeline step 4), innermost, over the instrument. */
    val sprudelFm = mapOf("fm.depth" to 300.0, "fm.ratio" to 1.4)

    /** The question's `bell` (`Ign.sine().fm(Ign.sine(), 3.5, 400).adsr(0.001, 1.0, 0.0, 1.0).classic()`), probed. */
    val bell = fmOp(ratio = 3.5, depth = 400.0).adsr(attack = 0.001, decay = 1.0, sustain = 0.0, release = 1.0).classic()

    /** The built-in `sgbell` shape, probed, under `classic()` as the registry serves it. */
    val sgbell = probed.fm(modulator = probed, ratio = 1.4, depth = 300.0, attack = 0.001, decay = 0.5, sustain = 0.0, release = 0.05).classic()

    for ((name, bag) in listOf(
        "vib" to vib,
        "penv" to penv,
        "accelerate" to accel,
        "vib and penv" to vib + penv,
        "vib and accelerate" to vib + accel,
        "vib, penv and accelerate" to vib + penv + accel,
    )) {
        "sprudel: $name over the bell (classic) bends the modulator with the carrier" {
            render(bell, bag = bag).shouldFollow(carrierHz = noteHz, ratio = 3.5)
        }

        "sprudel: $name over sgbell (classic) bends the modulator with the carrier" {
            render(sgbell, bag = bag).shouldFollow(carrierHz = noteHz, ratio = 1.4)
        }

        "sprudel: $name with sprudel's fm (classic()'s FM stage) over the bell: the bell's modulator follows both" {
            render(bell, bag = bag + sprudelFm).shouldFollow(carrierHz = noteHz, ratio = 3.5)
        }
    }

    "sprudel: vib over an fm instrument with its own vibrato inside, above the fm" {
        val ownVib = fmOp(ratio = 3.5, depth = 400.0).vibrato(rate = 5.0, semitones = 0.2).classic()

        render(ownVib, bag = vib).shouldFollow(carrierHz = noteHz, ratio = 3.5)
    }

    "sprudel: vib over an fm instrument with its own pitch envelope inside, above the fm" {
        render(pitchEnvelope(fmOp(ratio = 3.5, depth = 400.0)).classic(), bag = vib).shouldFollow(carrierHz = noteHz, ratio = 3.5)
    }

    "sprudel: a realtime held note with vib and penv, note-off mid-vibrato: the modulator follows through the release" {
        val lanes = render(bell, bag = vib + penv, releaseAt = 2500)

        lanes.shouldFollow(carrierHz = noteHz, ratio = 3.5)
    }

    "sprudel: fm alone over the bell (classic()'s FM stage over an fm's carrier): the bell's modulator follows it" {
        // Decided in step 3b: an fm over an fm's carrier is pitch modulation, so `s("sgbell").fm(...)` bends sgbell's
        // modulator with the carrier (the strip's root buffer did too).
        render(bell, bag = sprudelFm).shouldFollow(carrierHz = noteHz, ratio = 3.5)
        render(sgbell, bag = sprudelFm).shouldFollow(carrierHz = noteHz, ratio = 1.4)
    }

    "sprudel: no pitch door over the bell: nothing bends either oscillator (control)" {
        val lanes = render(bell)

        lanes.shouldBeUnbent(noteHz)
        lanes.shouldBeUnbent(noteHz * 3.5)
        lanes.shouldFollow(carrierHz = noteHz, ratio = 3.5, bent = false)
    }

    "the probe sees the fm's own term when the modulator writes a signal (the probe's positive control)" {
        val lanes = render(op, emitAt = setOf(noteHz * 3.5))

        lanes.lane(noteHz).ratios.any { it != 1.0 } shouldBe true
    }
})
