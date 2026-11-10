/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.common.infra.klangMapOf
import kotlin.random.Random

/**
 * Pins the emergent property the oscillator audit named O8: **a zero-length render window can only
 * ever be a voice's TERMINAL block.** It follows from three independent facts — `blockStart` is
 * integral (all drivers advance by whole blocks), `startFrame` is integral (`VoiceFactory` floors
 * it), and only `endFrame` is fractional — and it is what makes E5's deterministic 0.0 and every
 * lazy note-on init in `Ignitors.kt` safe.
 *
 * It is NOT a law of `Voice.render` itself: with a FRACTIONAL start the property fails (the third
 * test constructs the violation), so if the B1 startup redesign ever makes `startFrame` fractional,
 * the flooring pin below goes red — that is the tripwire, and the moment the six lazy-init latch
 * sites need re-auditing (ledger O8).
 */
class ZeroLengthWindowSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 100

    "VoiceFactory floors startFrame to a whole sample — the tripwire (ledger O8)" {
        val registry = IgnitorRegistry().apply { registerDefaults() }
        val factory = VoiceFactory(
            sampleRate = sampleRate, blockFrames = blockFrames,
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "z",
                data = VoiceData.empty.copy(freqHz = 220.0, sound = "sine"),
                startTime = 1000.6 / sampleRate,   // fractional on purpose
                gateEndTime = 5000.0 / sampleRate,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "z", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = { null },
        ) ?: error("null voice")
        voice.startFrame shouldBe 1000.0
    }

    /** Records every (elapsed, length) window the strip hands the exciter. */
    class RecordingIgnitor : Ignitor {
        val windows = mutableListOf<Pair<Int, Int>>()
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            windows.add(ctx.voiceElapsedFrames to ctx.length)
        }
    }

    fun renderAll(startFrame: Double, endFrame: Double): List<Pair<Int, Int>> {
        val rec = RecordingIgnitor()
        val voice = VoiceTestHelpers.createSynthVoice(
            startFrame = startFrame, endFrame = endFrame, gateEndFrame = endFrame,
            sampleRate = sampleRate, blockFrames = blockFrames, signal = rec,
        )
        val rc = Voice.RenderContext(
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            sampleRate = sampleRate, blockFrames = blockFrames, voiceBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        var b = 0.0
        while (b < 10_000) {
            rc.blockStart = b
            if (!voice.render(rc)) break
            b += blockFrames
        }
        return rec.windows
    }

    "with an INTEGRAL start, a zero-length window only ever appears as the LAST window" {
        for (endFrame in listOf(400.5, 437.25, 499.9, 500.75, 623.0001)) {
            val windows = renderAll(startFrame = 0.0, endFrame = endFrame)
            windows.forEachIndexed { i, (_, len) ->
                if (len == 0) {
                    (i == windows.lastIndex) shouldBe true
                }
            }
        }
    }

    // ── A voice that ends BEFORE its onset (audit L2-04) ──────────────────────────────────────────────────────────

    "a voice whose end lies before its onset renders a zero-length window, never a negative one, and ends" {
        // Onset 50 frames into block 4 (blocks of 100), end 24 frames before it: the onset block's window was -24
        val windows = renderAll(startFrame = 450.0, endFrame = 426.0)

        withClue("the tree is called once, in the onset block, on a zero-length window") {
            windows.map { it.second } shouldBe listOf(0)
        }
    }

    "through the factory: a gate that ends before the onset with no release renders nothing and throws nothing" {
        // The audit's measured crash (`note("c3*8").s("silence").legato(-0.001).adsr(release = 0)` and the like):
        // `DoubleArray.fill(x, offset, windowEnd)` with windowEnd < offset threw IllegalArgumentException out of the
        // render. One tree per kind of node that fills its window: the built-in `silence`, a `Progress` (the note
        // clock at a gate of 0 frames or less), a constant and the `Freq` leaf; and two ordinary built-ins.
        val registry = IgnitorRegistry().apply {
            registerDefaults()
            register("zlw_progress", IgnitorRegistry.builtInVoice(IgnitorDsl.Times(IgnitorDsl.Sine(), IgnitorDsl.Progress)))
            register("zlw_constant", IgnitorRegistry.builtInVoice(IgnitorDsl.Constant(0.25)))
            register("zlw_freq", IgnitorRegistry.builtInVoice(IgnitorDsl.Freq))
        }
        val sr = 48000
        val bf = 128
        val factory = VoiceFactory(sampleRate = sr, blockFrames = bf, scratchBuffers = ScratchBuffers(bf))
        val playback = PlaybackCtx(playbackId = "z", ignitorRegistry = registry, phasePools = PhasePools(Random(1)))
        val rc = VoiceTestHelpers.createContext(blockStart = 0.0, blockFrames = bf, sampleRate = sr)
        val onset = 10 * bf + 100

        // `saw` at `pregain` 0: the measured end-to-end crash, an `Affine` whose block-constant multiplier is 0 fills
        val cases = listOf("silence", "zlw_progress", "zlw_constant", "zlw_freq", "sine", "supersaw").map { it to 1.0 } + ("saw" to 0.0)

        for ((sound, pregain) in cases) {
            fun voiceWithGate(gateFrame: Int) = factory.makeVoice(
                scheduled = ScheduledVoice(
                    playbackId = "z",
                    data = VoiceData.empty.copy(
                        sound = sound,
                        freqHz = 220.0,
                        ignitorParams = klangMapOf("adsr.release" to 0.0, "pregain" to pregain),
                    ),
                    startTime = (onset + 0.25) / sr,
                    gateEndTime = (gateFrame + 0.25) / sr,
                    playbackStartTime = 0.0,
                ),
                backendStartTimeSec = 0.0,
                playbackCtx = playback,
                getSample = { null },
            ) ?: error("null voice for $sound")

            // The voice's lifetime past its gate: a release of 0 still lives the envelope de-click's settle (audit
            // L2-01, 1729 frames for classic()'s), so the gate is placed that far further back, to end the voice 24
            // frames before its onset.
            val tailFrames = (voiceWithGate(gateFrame = onset).endFrame - onset).toInt()
            val voice = voiceWithGate(gateFrame = onset - 24 - tailFrames)

            withClue("$sound: the voice ends before its onset, so the onset block's window was negative") {
                (voice.endFrame < voice.startFrame) shouldBe true
            }

            val alive = ArrayList<Boolean>()

            for (b in 10 until 13) {
                rc.blockStart = (b * bf).toDouble()
                rc.cylinders.clearAll()
                alive.add(voice.render(rc))

                withClue("$sound, block $b: nothing reaches the cylinder") {
                    rc.cylinders.cylinders.all { c -> c.mixBuffer.left.all { it == 0.0 } && c.mixBuffer.right.all { it == 0.0 } } shouldBe true
                }
            }

            withClue("$sound: it renders its onset block and is Done at the next") {
                alive shouldBe listOf(true, false, false)
            }
        }
    }

    "with a FRACTIONAL start the property FAILS — why the flooring above is load-bearing" {
        // startFrame 399.5: the first block [300,400) renders a zero-length window, then rendering
        // continues — a zero-length FIRST block. Every lazy note-on init would latch there.
        val windows = renderAll(startFrame = 399.5, endFrame = 475.0)
        val zeroAt = windows.indexOfFirst { it.second == 0 }
        (zeroAt >= 0 && zeroAt < windows.lastIndex) shouldBe true
    }
})
