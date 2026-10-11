/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_benchmark

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.BackendClock
import io.peekandpoke.klang.audio_be.PlaybackEngineDispatcher
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinder
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.distort
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.plus
import kotlin.math.abs
import kotlin.random.Random

/**
 * The fixed case list of the allocation check (`KLANG_BENCH_MODE=alloc`, `console/full-check.sh`), the same on
 * both platforms. Each case guards one fix of `docs/tasks/engine-follow-ups.md` against a regression that brings a
 * heap number per sample or per knob read back; the budgets and what each one guards are in
 * `audio_benchmark/alloc-budgets.txt`.
 *
 * Every case renders at 48 kHz in 128-frame blocks at middle C (261.63 Hz, not an integer, so a frequency read that
 * boxes shows). A "voice" case is one voice through [VoiceFactory], offered to its cylinder and committed each
 * block. An "engine" case runs [PlaybackEngineDispatcher], so the master stage (DC blockers, the house limiter, the
 * clip) runs too.
 *
 * The list is fixed and learnable: no random cases. A change to it starts a new scoreboard table.
 */
object AllocationCases {

    /** The sample rate of every case. */
    const val SAMPLE_RATE: Int = 48_000

    /** Middle C, 261.63 Hz, the frequency of every case. */
    const val FREQ_HZ: Double = 261.6255653005986

    /** The block size of every case: the engine's pinned 128 frames. */
    private const val BLOCK_FRAMES: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES

    /** The gate of a sustained note, in seconds: longer than any window. */
    private const val SUSTAIN_SEC: Double = 1.0e9

    /** The name an authored case's tree is registered under. */
    private const val AUTHORED: String = "zzalloc"

    private const val PLAYBACK_ID: String = "alloc"

    /**
     * One case.
     *
     * [sound] is a built-in sound, or [AUTHORED] when [dsl] is set. [params] are the `classic()` slots. The note's
     * gate is [gateSec] seconds. An engine case with [newNoteEveryBlocks] above 0 starts a new note that often
     * instead of one note at the start.
     */
    class Case(
        val name: String,
        val engine: Boolean,
        val sound: String,
        val dsl: IgnitorDsl? = null,
        val params: Map<String, Double>? = null,
        val gateSec: Double = SUSTAIN_SEC,
        val newNoteEveryBlocks: Int = 0,
    )

    private fun c(v: Double) = IgnitorDsl.Constant(v)

    private fun saw() = IgnitorDsl.Saw(analog = c(0.0))

    private val full = mapOf(
        "distort.amount" to 0.5, "lpf.freq" to 1200.0, "lpf.q" to 1.3, "lpf.env" to 12.0, "tremolo.depth" to 0.3,
        "tremolo.rate" to 4.3, "hpf.freq" to 80.0, "analog" to 0.2,
    )

    private val env3 = mapOf(
        "adsr.attack" to 0.0, "adsr.decay" to 1000.0, "adsr.sustain" to 0.5,
        "penv.semitones" to 24.0, "penv.attack" to 0.0, "penv.decay" to 1000.0, "penv.sustain" to 0.0,
        "fm.ratio" to 1.4, "fm.depth" to 300.0, "fm.attack" to 0.0, "fm.decay" to 1000.0, "fm.sustain" to 0.3,
    )

    /** The cases, in scoreboard order. */
    val all: List<Case> = listOf(
        Case(name = "saw", engine = false, sound = AUTHORED, dsl = saw()),
        Case(name = "sawC", engine = false, sound = "saw"),
        Case(name = "C-offrel", engine = false, sound = "saw", params = mapOf("adsr.release" to 1000.0), gateSec = 0.01),
        Case(name = "C-env3", engine = false, sound = "saw", params = env3),
        Case(name = "super7", engine = false, sound = "supersaw", params = mapOf("voices" to 7.0, "spread" to 0.3)),
        Case(
            name = "super7a",
            engine = false,
            sound = "supersaw",
            params = mapOf("voices" to 7.0, "spread" to 0.3, "analog" to 0.3),
        ),
        // Feedback 1: at the default 0.996 the strings are silent long before the window ends, which the check refuses.
        // What the check sees at the end is the strings' DC, which feedback 1 keeps (a constant line is the loop's fixed
        // point); the drift path it guards runs every block whatever the signal.
        Case(
            name = "spluck-a",
            engine = false,
            sound = "superpluck",
            params = mapOf("voices" to 8.0, "analog" to 0.3, "feedback" to 1.0),
        ),
        Case(
            name = "sgpad-vib",
            engine = false,
            sound = "sgpad",
            params = mapOf("vibrato.rate" to 5.0, "vibrato.semitones" to 0.5),
        ),
        Case(name = "whitecolor", engine = false, sound = AUTHORED, dsl = IgnitorDsl.WhiteNoise(color = c(0.3))),
        Case(name = "C-dist", engine = false, sound = "saw", params = mapOf("distort.amount" to 0.5)),
        Case(
            name = "tube4x",
            engine = false,
            sound = AUTHORED,
            dsl = saw().distort(amount = 0.5, shape = "tube", oversample = 4),
        ),
        Case(name = "C-full", engine = false, sound = "saw", params = full),
        Case(name = "E-sawC-sus", engine = true, sound = "saw"),
        Case(name = "E-noisesaw-sus", engine = true, sound = AUTHORED, dsl = saw() + IgnitorDsl.WhiteNoise()),
        Case(
            name = "E-C-rel05-n16",
            engine = true,
            sound = "saw",
            params = mapOf("adsr.release" to 0.5),
            gateSec = 0.01,
            newNoteEveryBlocks = 16,
        ),
    )

    /** The case named [name], or null. */
    fun byName(name: String): Case? = all.firstOrNull { it.name == name }

    /** Renders a case block by block. */
    abstract class Rig {
        /** Renders one block. */
        abstract fun step()

        /** Renders [blocks] blocks. */
        fun run(blocks: Int) {
            for (b in 0 until blocks) {
                step()
            }
        }

        /**
         * The peak of the last block rendered, both channels: the proof that the case still sounds at the end of its
         * window (a case that went silent would measure nothing and read 0 B). Read after the window, never in it.
         */
        abstract fun peak(): Double
    }

    /** The largest absolute sample of the first [frames] frames of [left] and [right]. */
    private fun peakOf(left: DoubleArray, right: DoubleArray, frames: Int): Double {
        var peak = 0.0

        for (i in 0 until frames) {
            peak = maxOf(peak, abs(left[i]), abs(right[i]))
        }

        return peak
    }

    /** A new rig for [case], built and ready for its first block. */
    fun rig(case: Case): Rig = if (case.engine) EngineRig(case) else VoiceRig(case)

    private fun voiceData(case: Case) = VoiceData.empty.copy(freqHz = FREQ_HZ, sound = case.sound, ignitorParams = case.params)

    /** One voice through [VoiceFactory], offered to its cylinder and committed each block. */
    private class VoiceRig(case: Case) : Rig() {
        private val registry = IgnitorRegistry().apply {
            registerDefaults()

            if (case.dsl != null) {
                register(case.sound, case.dsl)
            }
        }

        private val factory = VoiceFactory(
            sampleRate = SAMPLE_RATE,
            blockFrames = BLOCK_FRAMES,
            voiceBuffer = DoubleArray(BLOCK_FRAMES),
            scratchBuffers = ScratchBuffers(BLOCK_FRAMES),
        )

        private val voice: Voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = PLAYBACK_ID,
                data = voiceData(case),
                startTime = 0.0,
                gateEndTime = case.gateSec,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(PLAYBACK_ID, registry, PhasePools(Random(1))),
        ) { null } ?: error("the voice of case ${case.name} was not built")

        private val ctx = Voice.RenderContext(
            cylinders = Cylinders(blockFrames = BLOCK_FRAMES, sampleRate = SAMPLE_RATE),
            sampleRate = SAMPLE_RATE,
            blockFrames = BLOCK_FRAMES,
            voiceBuffer = DoubleArray(BLOCK_FRAMES),
            scratchBuffers = ScratchBuffers(BLOCK_FRAMES),
        )

        private var block = 0

        /** The cylinder of the last block: its mix is cleared at the start of the next one, so [peak] can read it. */
        private var cylinder: Cylinder? = null

        override fun step() {
            val last = cylinder

            if (last != null) {
                last.mixBuffer.left.fill(0.0)
                last.mixBuffer.right.fill(0.0)
            }

            ctx.blockStart = (block * BLOCK_FRAMES).toDouble()
            voice.render(ctx)

            val next = ctx.cylinders.offer(id = voice.cylinderId, voice = voice, blockStart = 0.0)

            next.commitOwner()
            cylinder = next
            block++
        }

        override fun peak(): Double {
            val mix = cylinder?.mixBuffer ?: return 0.0

            return peakOf(left = mix.left, right = mix.right, frames = BLOCK_FRAMES)
        }
    }

    /** The engine: [PlaybackEngineDispatcher], its master stage included. */
    private class EngineRig(private val case: Case) : Rig() {
        private val clock = BackendClock(SAMPLE_RATE)
        private val commLink = KlangCommLink(capacity = 4096)

        private val context = AudioBackendContext.create(
            sampleRate = SAMPLE_RATE,
            blockFrames = BLOCK_FRAMES,
            commLink = commLink.backend,
            clock = clock,
            performanceTimeMs = { 0.0 },
            // A fixed seed, as the offline renderer has: without it the pools seed from the clock.
            phasePoolSeed = 1,
        )

        private val dispatcher = PlaybackEngineDispatcher(context = context, clock = clock).also {
            it.setBackendStartTime(0.0)
        }

        private val out = StereoBuffer(BLOCK_FRAMES)
        private var block = 0

        init {
            if (case.dsl != null) {
                dispatcher.handle(KlangCommLink.Cmd.RegisterIgnitor(playbackId = PLAYBACK_ID, name = case.sound, dsl = case.dsl))
            }

            if (case.newNoteEveryBlocks == 0) {
                schedule(startSec = 0.0)
            }
        }

        private fun schedule(startSec: Double) {
            dispatcher.handle(
                KlangCommLink.Cmd.ScheduleVoice(
                    playbackId = PLAYBACK_ID,
                    voice = ScheduledVoice(
                        playbackId = PLAYBACK_ID,
                        data = voiceData(case),
                        startTime = startSec,
                        gateEndTime = startSec + case.gateSec,
                        playbackStartTime = 0.0,
                    ),
                )
            )
        }

        override fun step() {
            if (case.newNoteEveryBlocks > 0 && block % case.newNoteEveryBlocks == 0) {
                // Two blocks ahead, so the note is admitted (a start in the past is refused).
                schedule(startSec = (block + 2) * BLOCK_FRAMES / SAMPLE_RATE.toDouble())
            }

            dispatcher.renderBlock(cursorFrame = clock.cursorFrame, out = out)
            block++
        }

        override fun peak(): Double = peakOf(left = out.left, right = out.right, frames = BLOCK_FRAMES)
    }
}
