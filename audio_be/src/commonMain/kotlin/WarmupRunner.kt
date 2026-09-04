/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * Primes the audio render hot path before the first real voice arrives.
 *
 * Scheduling work runs on the **real** [PlaybackEngineDispatcher] engine so that V8 inline caches,
 * hidden-class bindings, and any lazy allocations inside those instances are warm when the user's
 * first kick hits. The caller silences the output buffer for the duration of the warmup, so users
 * do not hear the synthetic voices.
 *
 * At the end of the warmup window the runner hard-removes its own voices and resets the master
 * post chain (limiter envelope etc.) so no warmup residue leaks into real playback.
 */
class WarmupRunner(
    private val sampleRate: Int,
    /** Real backend host — warmup voices run through its engine; its post-chain is reset at the end. */
    private val dispatcher: PlaybackEngineDispatcher,
    /** Comm link used only to emit [KlangCommLink.Feedback.BackendReady]. */
    private val feedback: KlangCommLink.BackendEndpoint,
    /**
     * Number of audio blocks the warmup voices run for: one per warmed orbit ([WARMUP_ORBITS]), plus
     * [TAIL_BLOCKS] so the last orbit's voice has rendered through its effects too. After that the
     * warmup engine is disposed and the runner keeps ticking until the warehouse has zeroed what came
     * back (a bounded slice per block), and only then sends `BackendReady`.
     *
     * A block count, not a duration — deliberately. What is being primed is per-block work (JIT of
     * the render path, lazy allocations, inline caches), so the useful unit is *renders performed*.
     * It used to be ~85 ms on the JVM only because that backend ran a 4× larger block; that
     * divergence is gone.
     */
    private val warmupBlocks: Int = WARMUP_ORBITS + TAIL_BLOCKS,
    /**
     * Blocks after disposal the runner waits for the warehouse to be clean before sending
     * `BackendReady` regardless. The clean shelf is an optimisation, not a correctness condition
     * (`rent` zeroes a dirty buffer itself), and the warehouse is shared state other playbacks
     * return into — an unbounded wait could hold the output silenced forever while the frontend
     * gives up and starts cold (review round 4). Sixteen rings need sixteen blocks; four times that.
     */
    private val maxCleanWaitBlocks: Int = 4 * WARMUP_ORBITS,
) {
    companion object {
        /** Reserved playback-id — no real song can use this. */
        const val WARMUP_PLAYBACK_ID = "--WARMUP--"

        /** Reserved sample name — no real sample can use this. */
        const val WARMUP_SAMPLE_NAME = "--warmup--"

        /**
         * Orbits the warmup builds — one cylinder, one delay ring and one reverb network each, all of
         * which go to the warehouse's shelves when the warmup engine is disposed, so the first song
         * takes them from there instead of building them in its first frame (maintainer, 2026-09-04,
         * after the Fairphone measurement: that first frame killed the playback). 16 covers every
         * builtin song (the widest uses 8) twice over.
         */
        const val WARMUP_ORBITS: Int = 16

        /**
         * Blocks the last orbit's voice gets to render through its effects before the warmup engine
         * is disposed. Two, not more: from the moment the sixteenth orbit has rented, all sixteen
         * cylinders run their delay and reverb every block (they cannot deactivate inside the
         * window — the tail check is round-robin, one cylinder per block), which is four times the
         * widest builtin song's reverb load, on the phone this exists for (review round 3).
         *
         * The warmup is BUCKETED: orbit k's voice starts in block k, so each block builds ONE cylinder,
         * rents one ring and one network. Building all sixteen in the first render frame would be the
         * very stall the warmup exists to prevent — inaudible (output is silenced), but on a phone
         * long enough to have the worklet dropped for overrunning. The teardown is bucketed too, by
         * the warehouse itself: the units come back dirty and are zeroed a slice per block.
         */
        const val TAIL_BLOCKS: Int = 2

        /**
         * Sounds the warmed voices rotate through. Each is a different ignitor graph, and the first
         * note of each JITs it: a song's first supersaw used to compile in a live frame. The
         * warmup's own all-zeros sample exercises the sample path.
         */
        val WARMUP_SOUNDS: List<String> = listOf("sine", "saw", "supersaw", "square", "triangle", WARMUP_SAMPLE_NAME)

        /**
         * Orbit-level effects the warmed voices rotate through on top of delay + room + filter, so
         * their constructors and first blocks run here and not in a song's first frame: a phaser,
         * a compressor, a body resonator, a vowel bank (review round 3). Ducking is left out — it
         * needs a second orbit as sidechain and is unused by the corpus (`docs/tasks/future/ducking-unfinished.md`).
         */
        private const val EXTRA_EFFECT_KINDS = 4
    }

    private var started = false
    private var finished = false
    private var blocksRun = 0

    /** True while the runner is still priming the audio path. Callers should zero output in this window. */
    val isWarming: Boolean get() = started && !finished

    /**
     * Register the synthetic warmup sample on the real scheduler and schedule the warmup voices.
     * Idempotent.
     */
    fun start() {
        if (started) return
        started = true

        // Pre-register the all-zeros warmup sample (SYSTEM-wide sample cache). Reserved name —
        // no real song references it, so the entry is harmless left in the cache.
        dispatcher.handle(
            KlangCommLink.Cmd.Sample.Complete(
                req = SampleRequest(bank = null, sound = WARMUP_SAMPLE_NAME, index = null, note = null),
                note = null,
                pitchHz = 440.0,
                sample = MonoSamplePcm(
                    sampleRate = sampleRate,
                    pcm = DoubleArray(256),
                    meta = SampleMetadata.default,
                ),
            )
        )

        // One WET voice per warmed orbit on a dedicated warmup engine, each starting one block
        // after the previous (bucketed, see TAIL_BLOCKS): every block builds one cylinder and rents
        // one ring + one network, and the voice renders through delay, reverb, filter and its
        // ignitor graph — Voice.render → ignitor → filter → cylinder mix → delay → reverb → limiter.
        // Explicit and synthetic, not a builtin song (maintainer). Output is silenced by the host.
        // The HOST's block size, not the constant: the worklet detects its quantum and the JVM's is
        // an option; bucketing on the wrong size either stacks orbits into one block or starts the
        // last ones after the window (review round 3).
        val blockSec = dispatcher.blockFrames.toDouble() / sampleRate
        dispatcher.handle(
            KlangCommLink.Cmd.ScheduleVoices(
                playbackId = WARMUP_PLAYBACK_ID,
                voices = List(WARMUP_ORBITS) { orbit ->
                    // Mid-block, not on the boundary: block-framing B2 drops a voice whose start is
                    // behind the block it is promoted for, and `k * blockSec` can land an ulp before
                    // the clock's own `k * frames / sampleRate`.
                    val start = (orbit + 0.5) * blockSec
                    val base = VoiceData.empty.copy(
                        sound = WARMUP_SOUNDS[orbit % WARMUP_SOUNDS.size],
                        freqHz = 220.0 + 20.0 * orbit,
                        cylinder = orbit,
                        adsr = AdsrDef.Std(attack = 0.001, decay = 0.05, sustain = 0.0, release = 0.05),
                        cutoff = 2000.0,
                        resonance = 0.3,
                        delay = 0.5,
                        delayTime = 0.3,
                        delayFeedback = 0.2,
                        room = 0.5,
                        roomSize = 0.6,
                    )
                    val data = when (orbit % EXTRA_EFFECT_KINDS) {
                        0 -> base.copy(phaser = 0.5, phaserDepth = 0.5)
                        1 -> base.copy(compressorThreshold = -18.0, compressorRatio = 4.0, compressorKnee = 6.0, compressorAttack = 0.01, compressorRelease = 0.2)
                        2 -> base.copy(filters = FilterDefs(listOf(FilterDef.Body(bands = listOf(FilterDef.Body.Mode(freq = 220.0, db = 6.0, q = 8.0), FilterDef.Body.Mode(freq = 440.0, db = 3.0, q = 6.0)), mix = 0.5))))
                        else -> base.copy(filters = FilterDefs(listOf(FilterDef.Formant(bands = listOf(FilterDef.Formant.Band(freq = 700.0, db = 0.0, q = 8.0), FilterDef.Formant.Band(freq = 1200.0, db = -6.0, q = 10.0)), mix = 0.5))))
                    }
                    ScheduledVoice(
                        playbackId = WARMUP_PLAYBACK_ID,
                        data = data,
                        startTime = start,
                        gateEndTime = start + 2 * blockSec,
                        playbackStartTime = 0.0,
                    )
                },
            )
        )
    }

    /** The warmup engine has been disposed; the runner is waiting for the warehouse to zero what came back. */
    private var disposed = false

    /** True if `BackendReady` went out with the shelves still dirty — the wait hit [maxCleanWaitBlocks]. */
    var readyWhileDirty: Boolean = false
        private set

    /**
     * Should be called once per audio block while warming. Counts progress toward [warmupBlocks];
     * on that tick the warmup engine is disposed (its cylinders, rings and networks go to the
     * warehouse's shelves, dirty) and the limiter reset; on the first tick after that on which the
     * warehouse reports every shelved unit zeroed, [KlangCommLink.Feedback.BackendReady] goes out.
     * Returns true while still warming, false once done.
     */
    fun tick(): Boolean {
        if (!started || finished) return false

        blocksRun++

        if (!disposed && blocksRun >= warmupBlocks) {
            disposed = true
            // Dispose the warmup engine entirely: the return path (2f) stocks the shelves for the
            // first real playback. Nothing is zeroed here — the dispatcher's per-block housekeeping
            // does that, a slice at a time (review round 3).
            dispatcher.cleanupHard(WARMUP_PLAYBACK_ID)
            // Wipe limiter state so the first real kick sees unity gain.
            dispatcher.resetPostChain()
        }

        if (disposed) {
            val clean = dispatcher.isWarehouseClean
            if (clean || blocksRun >= warmupBlocks + maxCleanWaitBlocks) {
                finished = true
                readyWhileDirty = !clean
                feedback.feedback.send(KlangCommLink.Feedback.BackendReady())
                return false
            }
        }

        return true
    }
}
