/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.div
import io.peekandpoke.klang.audio_bridge.ign
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.onepole
import io.peekandpoke.klang.common.math.semitones
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * **`s("sgpad").fm(...)` keeps its pitch** (Q31 of KLANG-00305 (fm revisit), fixed 2026-10-18).
 *
 * Since pitch pipeline step 4 sprudel's `fm` is `classic()`'s FM stage, innermost, so it sits above the built-in's
 * source. `sgpad` once built its second saw as `Saw().detune(0.1)`, a FORKING detune under that stage: one modulator
 * served two carrier pitches and was asked twice per block, so it jumped a block of phase at every block boundary
 * (ledger E8: at 128 frames 95 percent of the energy lay off the harmonic grid, and the sound depended on the block
 * size). The second saw now reads its pitch as `Freq * 0.1.semitones()`: the same frequency, but the freq argument
 * stays the note's, so the one modulator runs once per block at the note, the strip's law before step 4.
 *
 * Two checks, each RED on the old shape: the voice is the same at every block size, and it is the sum of its two
 * layers rendered as separate one-pitch voices, each under its own FM at the note (the `onepole`, the divisor, the
 * pregain and the switched-off envelope are linear, so the sum holds to rounding). `s("sgpad")` without fm is pinned
 * bit for bit by `BuiltInVoiceMatrixSpec`'s "sgpad" rows.
 */
class SgpadFmSpec : StringSpec({

    val sampleRate = 48000
    val freqHz = 130.8128
    val gateFrames = 9_613
    val tailFrames = 4_811
    val relSec = (tailFrames + 0.25) / sampleRate
    val fm = DoorFm(depth = 150.0, ratio = 1.5)

    fun layer(saw: IgnitorDsl): IgnitorDsl =
        saw.div(other = IgnitorDsl.Param(name = "divisor", default = 2.0)).onepole(3000.0.ign)

    fun registry(): IgnitorRegistry = IgnitorRegistry().apply {
        registerDefaults()
        register(name = "layer-a", dsl = IgnitorRegistry.builtInVoice(layer(IgnitorDsl.Saw())))
        register(
            name = "layer-b",
            dsl = IgnitorRegistry.builtInVoice(layer(IgnitorDsl.Saw(freq = IgnitorDsl.Freq.mul(0.1.semitones().ign)))),
        )
    }

    /** One voice of [sound], its envelope off, rendered through the real [VoiceFactory] in blocks of [blockFrames]. */
    fun render(sound: String, blockFrames: Int, door: DoorFm?): DoubleArray {
        val pid = "sgpad-fm"
        val voiceBuffer = DoubleArray(blockFrames)
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = voiceBuffer,
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = pid,
                data = VoiceData.empty.copy(freqHz = freqHz, sound = sound)
                    .withClassicSlots(DoorFields(fm = door, adsr = DoorAdsr(release = relSec, on = false))),
                startTime = 0.25 / sampleRate,
                gateEndTime = (gateFrames + 0.25) / sampleRate,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = pid, ignitorRegistry = registry(), phasePools = PhasePools(Random(1))),
            getSample = { null },
        ) ?: error("makeVoice returned null for $sound")

        val rc = Voice.RenderContext(
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = voiceBuffer,
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val total = gateFrames + tailFrames
        val out = DoubleArray(total)
        var written = 0
        var b = 0.0

        while (written < total) {
            voiceBuffer.fill(0.0)
            rc.blockStart = b

            if (!voice.render(rc)) {
                break
            }

            for (v in voiceBuffer) {
                if (written < total) {
                    out[written++] = v
                }
            }

            b += blockFrames
        }

        check(written == total) { "$sound rendered $written frames, need $total" }

        return out
    }

    fun rms(a: DoubleArray): Double {
        var acc = 0.0

        for (v in a) {
            acc += v * v
        }

        return sqrt(acc / a.size)
    }

    fun diffRms(a: DoubleArray, b: DoubleArray): Double = rms(DoubleArray(a.size) { a[it] - b[it] })

    "the fm reaches the pad: with fm the voice differs from the bare pad (engagement control)" {
        val bare = render(sound = "sgpad", blockFrames = 128, door = null)
        val withFm = render(sound = "sgpad", blockFrames = 128, door = fm)

        check(rms(bare) > 1e-3) { "the bare pad is silent: vacuous comparison" }
        (diffRms(withFm, bare) > 0.1 * rms(bare)) shouldBe true
    }

    "sgpad with fm is the same at block sizes 64 and 37 as at the pinned 128 (ledger E8)" {
        val ref = render(sound = "sgpad", blockFrames = 128, door = fm)

        for (bf in listOf(64, 37)) {
            withClue("blockFrames=$bf") {
                val other = render(sound = "sgpad", blockFrames = bf, door = fm)
                var max = 0.0

                for (i in ref.indices) {
                    max = maxOf(max, abs(other[i] - ref[i]))
                }

                max shouldBe 0.0
            }
        }
    }

    "sgpad with fm is the sum of its two layers, each a one-pitch voice under its own fm at the note" {
        val pad = render(sound = "sgpad", blockFrames = 128, door = fm)
        val a = render(sound = "layer-a", blockFrames = 128, door = fm)
        val b = render(sound = "layer-b", blockFrames = 128, door = fm)
        val sum = DoubleArray(pad.size) { a[it] + b[it] }

        check(rms(a) > 1e-3 && rms(b) > 1e-3) { "a layer is silent: vacuous comparison" }
        // Rounding only: the onepole runs on the sum in the pad and on each layer in the reference. The old shape
        // missed by about the pad's own level.
        withClue("diff RMS ${diffRms(pad, sum)} against pad RMS ${rms(pad)}") {
            (diffRms(pad, sum) < 1e-9 * rms(pad)) shouldBe true
        }
    }
})
