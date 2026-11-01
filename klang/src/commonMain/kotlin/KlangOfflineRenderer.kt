/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_be.AudioBackend
import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.OfflineBackend
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_fe.samples.NullAudioDecoder
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.utils.AssetLoader
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Renders a pattern offline, at full CPU speed, through the same path a live playback takes (KLANG-00399, step 4):
 * the [KlangPatternScheduler] sends its voices over the wire to an [OfflineBackend], the live dispatcher, in lockstep
 * on one thread. Before each block the scheduler is asked with the backend's exact position ([KlangPatternScheduler.tick]),
 * then the block renders. Nothing waits on a timer, so a render runs as fast as the engine.
 *
 * What it adds to the live path, so a render repeats: a fixed playback id ("offline", it seeds the voices' random
 * streams), a fixed seed for the unison phase pools, every sample loaded and handed to the backend before the first
 * block. Platform-independent: produces audio blocks via a callback.
 */
class KlangOfflineRenderer(
    private val sampleRate: Int = 48_000,
    /**
     * Must stay at [AudioBackendContext.RENDER_QUANTUM_FRAMES] for the render to match live
     * playback — block size drives the analog-drift rate and the filter smoothing granularity,
     * so a "faster" larger block is a different sound, not just a faster render.
     */
    private val blockFrames: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES,
) {
    companion object {
        /** The playback id of every render: it seeds the voices' random streams, so a render repeats. */
        const val PLAYBACK_ID = "offline"
    }

    data class Result(
        val durationSec: Double,
        val totalFrames: Int,
        val elapsedMs: Double,
    )

    /**
     * Render a pattern to audio.
     *
     * @param pattern the compiled pattern to render
     * @param cycles number of cycles to render
     * @param cyclesPerSecond tempo (cps = rpm / 60.0)
     * @param tailSec extra seconds after last note for reverb/delay tails
     * @param onBlock called for each rendered block with the engine's clipped floating-point stereo
     *   output and its frame count. The buffer is reused for the next block: copy what you keep. A
     *   16-bit consumer converts with `writePcm16` (audio_be), the one home of that conversion.
     */
    suspend fun render(
        pattern: KlangPattern,
        cycles: Int,
        cyclesPerSecond: Double,
        tailSec: Double = 2.0,
        customIgnitors: List<Pair<String, IgnitorDsl>> = emptyList(),
        samples: Samples? = null,
        onBlock: (out: StereoBuffer, frames: Int) -> Unit,
    ): Result {
        val klangTime = KlangTime.create()
        val startMs = klangTime.internalMsNow()

        // The tempo the playback plays at, its floor included: the render's length and its samples are counted on it
        val cps = cyclesPerSecond.coerceAtLeast(KlangPatternScheduler.MIN_RPM / 60.0)

        val commLink = KlangCommLink()
        val backend = OfflineBackend(
            config = AudioBackend.Config(commLink = commLink.backend, sampleRate = sampleRate, blockSize = blockFrames),
            // Fixed seed: offline unison phase pools reproduce across renders (doc §5 rng row).
            phasePoolSeed = 1,
        )

        // This render's custom ignitors on top of the built-in defaults, before the playback's engine forks them
        for ((name, dsl) in customIgnitors) {
            if (backend.ignitorRegistry.contains(name)) {
                println("[KlangOfflineRenderer] Custom ignitor '$name' overrides built-in sound")
            }

            backend.ignitorRegistry.register(name, dsl)
        }

        if (samples != null) {
            loadSamples(
                pattern = pattern,
                cycles = cycles,
                cyclesPerSecond = cps,
                samples = samples,
                commLink = commLink,
                backend = backend,
            )
        }

        // Nothing here waits: signals go out inline, the preloader has nothing to load (the samples are in)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val nowMs = { backend.nowSec * 1000.0 }
        val sendControl: (KlangCommLink.Cmd) -> Unit = { commLink.frontend.control.send(it) }
        val noSamples = Samples(
            index = Samples.Index(banks = emptyList()),
            loader = object : AssetLoader {
                override suspend fun download(uri: String): ByteArray? = null
            },
            decoder = NullAudioDecoder(),
        )

        val context = KlangPlaybackContext(
            playerOptions = KlangPlayer.Options(samples = noSamples),
            samplePreloader = SamplePreloader(
                samples = noSamples,
                sendControl = {},
                scope = scope,
                dispatcher = Dispatchers.Unconfined,
            ),
            sendControl = sendControl,
            scope = scope,
            fetcherDispatcher = Dispatchers.Unconfined,
            callbackDispatcher = Dispatchers.Unconfined,
            backendReady = CompletableDeferred(Unit),
            // No report needed: its clock before the first report is the backend's position itself
            clock = PlaybackClock(localMs = nowMs),
            diagnostics = KlangDiagnostics(
                stream = StreamSource<KlangCommLink.Feedback.Diagnostics?>(null),
                ticks = StreamSource(Unit),
                capacity = 1,
                nowMs = nowMs,
            ),
        )

        val scheduler = KlangPatternScheduler(
            playbackId = PLAYBACK_ID,
            pattern = CycleLimitedPattern(inner = pattern, maxCycles = cycles),
            context = context,
            signals = StreamSource<KlangPlaybackSignal>(KlangPlaybackSignal.Idle),
            registrar = context.registrarFor(PLAYBACK_ID),
            uiMs = nowMs,
        )

        // Render past the end by the engine's own output latency, otherwise the last samples are still sitting in a
        // delay ring (the house limiter's lookahead, a master chain's lookahead or oversampler) when the loop stops.
        // Matters at tailSec = 0.0, where the truncated tail is the actual music rather than a reverb tail. Read again
        // after every block: a master event later in the song swaps the chain in the middle of the render.
        val totalDurationSec = cycles.toDouble() / cps + tailSec
        val musicalAndTailFrames = (totalDurationSec * sampleRate).toInt()
        var totalFrames = musicalAndTailFrames + backend.latencyFrames

        try {
            scheduler.startDriven(cyclesPerSecond = cps)

            while (backend.cursorFrame < totalFrames) {
                // The playback started at the backend's second 0: its time is the backend's position
                scheduler.tick(nowSec = backend.nowSec - backend.startTimeSec)
                backend.render(blocks = 1) { out -> onBlock(out, blockFrames) }
                deliverFeedback(commLink = commLink, scheduler = scheduler)
                totalFrames = musicalAndTailFrames + backend.latencyFrames
            }
        } finally {
            scheduler.stop()
            scope.cancel()
        }

        return Result(
            durationSec = totalDurationSec,
            totalFrames = totalFrames,
            elapsedMs = klangTime.internalMsNow() - startMs,
        )
    }

    /**
     * Every sample the render plays, loaded and handed to the backend before the first block: offline, nothing may wait
     * for one. A sound the backend plays itself (a built-in or custom ignitor) is not a sample; an inline one's synthetic
     * name is asked for and not found, which costs a lookup.
     */
    private suspend fun loadSamples(
        pattern: KlangPattern,
        cycles: Int,
        cyclesPerSecond: Double,
        samples: Samples,
        commLink: KlangCommLink,
        backend: OfflineBackend,
    ) {
        // Cycle by cycle, as the scheduler asks: a pattern may answer a longer span otherwise (KLANG-00401)
        val requests = (0 until cycles)
            .flatMap { cycle -> pattern.queryEvents(fromCycles = cycle.toDouble(), toCycles = cycle + 1.0, cps = cyclesPerSecond) }
            .map { it.toVoiceData().asSampleRequest() }
            .filter { !backend.ignitorRegistry.contains(it.sound ?: "") }
            .toSet()

        for (req in requests) {
            try {
                val loaded = samples.get(req) ?: continue
                val pcm = loaded.pcm ?: continue

                commLink.frontend.control.send(
                    KlangCommLink.Cmd.Sample.Complete(
                        req = req,
                        note = loaded.sample.note,
                        pitchHz = loaded.sample.pitchHz,
                        sample = pcm,
                    )
                )
                // One at a time: the link's ring is bounded, a song may have more samples than it holds
                backend.drain()
            } catch (e: Exception) {
                println("[KlangOfflineRenderer] Failed to load sample ${req.sound}: ${e.message}")
            }
        }
    }

    /** The backend's feedback after a block: where it started the playback reaches the scheduler, the rest is not needed. */
    private fun deliverFeedback(commLink: KlangCommLink, scheduler: KlangPatternScheduler) {
        while (true) {
            when (val feedback = commLink.frontend.feedback.receive() ?: break) {
                is KlangCommLink.Feedback.PlaybackAnchored -> scheduler.handleFeedback(feedback)

                is KlangCommLink.Feedback.BackendReady,
                is KlangCommLink.Feedback.Diagnostics,
                is KlangCommLink.Feedback.RequestSample,
                is KlangCommLink.Feedback.SampleReceived,
                -> {}
            }
        }
    }
}
