/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.MemoizingIgnitor
import io.peekandpoke.klang.audio_be.ignitor.buildExciter
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.classic
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * **`classic()`'s FM stage, filled by sprudel's `fm` through the `fm.*` slots (pitch pipeline step 4), against an
 * ORACLE.**
 *
 * The voice strip's FM retired in step 4; sprudel's `fm(depth, ratio, attack, decay, sustain, release)` travels as the
 * `fm.*` slots and fills the Ignitor `fm` node that `classic()` places innermost, over a sine modulator at `analog` 0.
 * Every row renders a real voice through `VoiceFactory` (the sample instrument, so sample voices are covered too) and
 * reads the frequency ratio it applies, frame by frame ([renderPitchRatios], the ramp-sample probe, the sample's
 * recorded pitch 220 Hz), against the law written out here with the library's `sin`, `exp` and `pow`:
 *
 *  - the modulator's phase starts at 0 and advances `2 pi * 220 * ratio / sampleRate` per frame, times the ratio of
 *    the pitch doors above the FM (decision D1: the classic vibrato, accelerate and pitch envelope move the whole
 *    operator), so the modulator follows them;
 *  - the FM ratio is `1 + sin(phase) * depth * level / 220`, and the voice applies the doors' ratio times it;
 *  - the level is the depth envelope PER FRAME (the strip held it per block, ledger E11), on the exponential curve
 *    every modulation envelope has unwritten: attack `g(p)`, decay `s + (1 - s) g(1 - p)`, release from the level at
 *    the gate over `floor(N)` frames, clamped to [0, 1]; with every stage at its default (attack 0, decay 0,
 *    sustain 1, release 0) NO envelope runs and the level is 1 on every frame, the release tail included (the fifth
 *    non-identical cause of step 4: the strip collapsed it to 0 at the gate).
 *
 * The node's law alone is `ModulatorPhaseWrapSpec` and `EnvelopeLawSpec`; the bell's modulator under the stage is
 * `FmModulatorFollowsPitchSpec`.
 */
class ClassicFmSpec : StringSpec({

    val sampleRate = 48000
    val pitchHz = 220.0

    /** The voice lives a second past its gate: a release tail long enough to show what the depth does there. */
    val longLife = DoorAdsr(release = 1.0)

    fun g(x: Double): Double = (exp(3.0 * x) - 1.0) / (exp(3.0) - 1.0)

    /** The depth envelope's level at frame [p], gate [gate], every time in seconds; the law in the class KDoc. */
    fun level(p: Int, gate: Int, attack: Double, decay: Double, sustain: Double, release: Double): Double {
        if (attack <= 0.0 && decay <= 0.0 && sustain >= 1.0 && release <= 0.0) {
            return 1.0
        }

        val a = attack * sampleRate
        val d = decay * sampleRate
        val r = floor(release * sampleRate)

        fun ads(pos: Int): Double = when {
            pos < a -> g(pos / a)
            pos < a + d -> sustain + (1.0 - sustain) * g(1.0 - (pos - a) / d)
            else -> sustain
        }

        val raw = if (p < gate) {
            ads(p)
        } else {
            val atGate = if (gate <= 0) 0.0 else ads(gate)
            val offset = if (r > 1.0) 0.0 else 1.0
            val denom = if (r > 1.0) r - 1.0 else 1.0

            atGate * g(1.0 - minOf((p - gate + offset) / denom, 1.0))
        }

        return raw.coerceIn(0.0, 1.0)
    }

    /**
     * The ratio the voice applies, frame by frame: the doors' ratio [doors] (1.0 where none) times the FM ratio, the
     * modulator advancing at its own frequency times [doors].
     */
    fun oracle(frames: Int, depth: Double, ratio: Double, level: (Int) -> Double, doors: (Int) -> Double = { 1.0 }): DoubleArray {
        val inc = 2.0 * PI * pitchHz * ratio / sampleRate
        var phase = 0.0

        return DoubleArray(frames) { p ->
            val door = doors(p)
            val fm = 1.0 + sin(phase) * depth * level(p) / pitchHz

            phase += inc * door

            door * fm
        }
    }

    fun firstOff(ratios: DoubleArray, expected: DoubleArray, tolerance: Double = 1e-9): Int? =
        ratios.indices.firstOrNull { abs(ratios[it] - expected[it]) > tolerance }

    class Row(
        val title: String,
        val fm: DoorFm,
        val gate: Int,
        /** Frames rendered past the gate. */
        val tail: Int,
    )

    val rows = listOf(
        Row(title = "envelope-free fm(300, 1.4): the full depth through the release tail", fm = DoorFm(depth = 300.0, ratio = 1.4), gate = 9_000, tail = 12_000),
        Row(
            title = "the bell fm(300, 1.4, 0.001, 0.5, 0): the attack inside the first block",
            fm = DoorFm(depth = 300.0, ratio = 1.4, attack = 0.001, decay = 0.5, sustain = 0.0),
            gate = 30_000,
            tail = 2_000,
        ),
        Row(
            title = "a slow attack, sustain 0.5, release 0.2 (fm.release, decision D3): the depth falls from the gate",
            fm = DoorFm(depth = 200.0, ratio = 2.0, attack = 0.1, decay = 0.05, sustain = 0.5, release = 0.2),
            gate = 9_001,
            tail = 12_000,
        ),
        Row(
            title = "sustain 0.5 and release 0: the depth drops to 0 on the gate frame (ledger E10, 0 means 0)",
            fm = DoorFm(depth = 200.0, ratio = 2.0, sustain = 0.5),
            gate = 9_001,
            tail = 3_000,
        ),
        Row(title = "a negative depth renders, inverted", fm = DoorFm(depth = -150.0, ratio = 3.0), gate = 6_000, tail = 3_000),
    )

    for (row in rows) {
        for (onset in listOf(0, 37)) {
            "the stage renders the FM law per frame: ${row.title} (onset $onset)" {
                val frames = row.gate + row.tail
                val f = row.fm
                val ratios = renderPitchRatios(
                    doors = DoorFields(fm = f, adsr = longLife),
                    frames = frames,
                    gateFrames = row.gate,
                    onsetFrames = onset,
                )
                val expected = oracle(frames = frames, depth = f.depth!!, ratio = f.ratio ?: 1.0, level = { p ->
                    level(p = p, gate = row.gate, attack = f.attack ?: 0.0, decay = f.decay ?: 0.0, sustain = f.sustain ?: 1.0, release = f.release ?: 0.0)
                })

                withClue("first frame off the law") { firstOff(ratios, expected) shouldBe null }
                withClue("engaged: the FM moves the pitch in the first block") { (0 until 128).any { abs(ratios[it] - 1.0) > 1e-3 } shouldBe true }
            }
        }
    }

    for (onset in listOf(0, 37)) {
        "a held realtime voice released at frame 1000 mid-decay: the release starts there (onset $onset)" {
            val f = DoorFm(depth = 300.0, ratio = 1.4, attack = 0.001, decay = 0.5, sustain = 0.2, release = 0.05)
            val frames = 6_000
            val ratios = renderPitchRatios(
                doors = DoorFields(fm = f, adsr = longLife),
                frames = frames,
                gateFrames = 10_000_000,
                onsetFrames = onset,
                releaseAtFrame = 1000,
            )
            val expected = oracle(frames = frames, depth = 300.0, ratio = 1.4, level = { p ->
                level(p = p, gate = 1000, attack = 0.001, decay = 0.5, sustain = 0.2, release = 0.05)
            })

            withClue("first frame off the law released at 1000") { firstOff(ratios, expected) shouldBe null }
            // The probe reads a ratio to about 1e-11, so "back on the note" is a ratio within 1e-9 of 1.0.
            withClue("engaged: the release ends the FM") { (4_000 until frames).all { abs(ratios[it] - 1.0) < 1e-9 } shouldBe true }
        }
    }

    "the modulator follows the classic vibrato above it (decision D1): the operator moves as one" {
        // `vib(6, 0.5)`: the vibrato's ratio written out, `2^(sin(lfo) * 0.5 / 12)`, the LFO from phase 0. The
        // modulator's phase advances by the vibrato's ratio too, so its frequency stays 1.4 times the carrier's.
        val frames = 20_000
        val lfoInc = 2.0 * PI * 6.0 / sampleRate
        val vib = { p: Int -> 2.0.pow(sin(lfoInc * p) * 0.5 / 12.0) }
        val ratios = renderPitchRatios(
            doors = DoorFields(vibratoRate = 6.0, vibratoSemitones = 0.5, fm = DoorFm(depth = 300.0, ratio = 1.4), adsr = longLife),
            frames = frames,
            gateFrames = frames,
        )
        val follows = oracle(frames = frames, depth = 300.0, ratio = 1.4, level = { 1.0 }, doors = vib)
        val unbent = oracle(frames = frames, depth = 300.0, ratio = 1.4, level = { 1.0 })
        val doesNotFollow = DoubleArray(frames) { vib(it) * unbent[it] }

        // 1e-6: the engine's vibrato runs the polynomial sine and `fastExp2` (relative error about 1e-10), and the
        // modulator's phase sums that error over 20,000 frames; the probe adds its own 1e-11. A modulator that ignored
        // the vibrato is off by more than 1e-3 (the control below).
        val worst = ratios.indices.maxOf { abs(ratios[it] - follows[it]) }

        println("CLASSIC-FM follows the vibrato: worst deviation $worst")
        withClue("first frame off the following law") { firstOff(ratios, follows, tolerance = 1e-6) shouldBe null }
        withClue("the positive control: a modulator that ignored the vibrato would be far off") {
            (firstOff(ratios, doesNotFollow, tolerance = 1e-3) != null) shouldBe true
        }
    }

    "depth 0 is the bare voice, and so is an unwritten or a non-finite depth in the bag (it reads as the slot's 0)" {
        // Written raw into the bag, past sprudel's boundary (which drops a non-finite value).
        val frames = 4000
        val key = (IgnitorDsl.Slots.fm.depth as IgnitorDsl.Param).name
        val bare = renderPitchRatios(doors = DoorFields(), frames = frames, gateFrames = 2000)

        withClue("the bare voice is on the note") { bare.all { it == 1.0 } shouldBe true }
        withClue("depth 0") {
            renderPitchRatios(doors = DoorFields(fm = DoorFm(depth = 0.0, ratio = 2.0)), frames = frames, gateFrames = 2000).toList() shouldBe bare.toList()
        }
        withClue("a ratio and an envelope alone switch nothing on") {
            renderPitchRatios(doors = DoorFields(fm = DoorFm(ratio = 2.0, attack = 0.1, release = 0.2)), frames = frames, gateFrames = 2000).toList() shouldBe bare.toList()
        }

        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            withClue("depth $value") {
                renderPitchRatios(
                    doors = DoorFields(),
                    data = VoiceData.empty.copy(ignitorParams = mapOf(key to value)),
                    frames = frames,
                    gateFrames = 2000,
                ).toList() shouldBe bare.toList()
            }
        }
    }

    "a non-finite ratio, attack, decay, sustain or release in the bag reads as the slot's default" {
        // Each knob written raw into the bag next to a depth, against the same render with the knob unwritten.
        val frames = 4000
        val s = IgnitorDsl.Slots.fm
        val depth = (s.depth as IgnitorDsl.Param).name to 300.0
        val reference = renderPitchRatios(
            doors = DoorFields(),
            data = VoiceData.empty.copy(ignitorParams = mapOf(depth)),
            frames = frames,
            gateFrames = 2000,
        )

        withClue("the reference is the envelope-free FM") { reference.any { abs(it - 1.0) > 0.1 } shouldBe true }

        for (knob in listOf(s.ratio, s.attack, s.decay, s.sustain, s.release)) {
            for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
                val name = (knob as IgnitorDsl.Param).name

                withClue("$name $value") {
                    renderPitchRatios(
                        doors = DoorFields(),
                        data = VoiceData.empty.copy(ignitorParams = mapOf(depth, name to value)),
                        frames = frames,
                        gateFrames = 2000,
                    ).toList() shouldBe reference.toList()
                }
            }
        }
    }

    // ── The guard (the pitch stages' shape: an unwritten switch builds nothing) ──────────────────────────────────

    /** The class the root of [dsl]'s build hands the source, built with [bag]: a pitched source under a built pitch stage is a `ModApplyingIgnitor`. */
    fun shapeOf(dsl: IgnitorDsl, bag: Map<String, Double>): Any {
        val built: Ignitor = dsl.buildExciter(ignitorParams = bag, random = Random(7), freqHz = 220.0, sampleRate = sampleRate).ignitor

        return (built as MemoizingIgnitor).inner::class
    }

    "an unwritten fm.depth builds no stage: the tree is the bare source's" {
        // The envelope is switched off so the root IS the source (an off envelope is not built).
        val classic = IgnitorDsl.Sine().classic()
        val off = mapOf("adsr.on" to 0.0)
        val bareShape = shapeOf(IgnitorDsl.Sine(), emptyMap())

        withClue("nothing written") { shapeOf(classic, off) shouldBe bareShape }
        withClue("a ratio alone") { shapeOf(classic, off + ("fm.ratio" to 2.0)) shouldBe bareShape }
        withClue("positive control: a written depth builds the stage") {
            shapeOf(classic, off + ("fm.depth" to 200.0)) shouldNotBe bareShape
        }
    }
})
