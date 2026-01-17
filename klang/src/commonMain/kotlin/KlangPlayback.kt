package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.infra.KlangAtomicBool
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangLock
import io.peekandpoke.klang.audio_bridge.infra.withLock
import io.peekandpoke.klang.audio_fe.CpsAwareEventSource
import io.peekandpoke.klang.audio_fe.KlangEventFetcher
import io.peekandpoke.klang.audio_fe.KlangEventSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.ceil

class KlangPlayback internal constructor(
    /** Unique identifier for this playback */
    private val playbackId: String,
    /** Sound event source */
    private val source: KlangEventSource,
    /** The player config */
    private val playerOptions: KlangPlayer.Options,
    /** Shared communication link to the backend */
    private val commLink: KlangCommLink,
    /** The coroutines scope on which the player runs */
    private val scope: CoroutineScope,
    /** The dispatcher used for the event fetcher */
    private val fetcherDispatcher: CoroutineDispatcher,
    /** Callback invoked when this playback is stopped */
    private val onStopped: (KlangPlayback) -> Unit = {},
) {
    data class Options(
        /** Amount of time to look ahead in the [KlangEventSource] */
        val lookaheadSec: Double = 1.0,
        // TODO: use BPM instead and let strudel do the conversion to CPS
        val cyclesPerSecond: Double = 0.5,
        /** Initial cycles prefetch, so that the audio starts flawlessly. Auto-calculated if null. */
        val prefetchCycles: Int? = null,
    )

    // Thread-safe state management
    private val running = KlangAtomicBool(false)
    private val jobLock = KlangLock()
    private var fetcherJob: Job? = null

    fun start(options: Options = Options()) {
        // Atomically transition from stopped to running
        if (!running.compareAndSet(expect = false, update = true)) {
            // Already running
            return
        }

        // Update the source's CPS if it supports it
        if (source is CpsAwareEventSource) {
            source.cyclesPerSecond = options.cyclesPerSecond
        }

        // Tell backend to start this playback
        commLink.frontend.control.send(
            KlangCommLink.Cmd.StartPlayback(playbackId = playbackId)
        )

        // Calculate prefetchCycles if not specified
        val prefetchCycles = options.prefetchCycles
            ?: ceil(maxOf(2.0, options.cyclesPerSecond * 2)).toInt()

        // Start the fetcher job
        jobLock.withLock {
            fetcherJob = scope.launch(fetcherDispatcher.limitedParallelism(1)) {
                val fetcher = KlangEventFetcher(
                    config = KlangEventFetcher.Config(
                        playbackId = playbackId,
                        samples = playerOptions.samples,
                        source = source,
                        commLink = commLink.frontend,
                        sampleRate = playerOptions.sampleRate,
                        cps = options.cyclesPerSecond,
                        lookaheadSec = options.lookaheadSec,
                        prefetchCycles = prefetchCycles.toDouble()
                    ),
                )

                fetcher.run(this)
            }
        }
    }

    fun stop() {
        // Atomically transition from running to stopped
        if (!running.compareAndSet(expect = true, update = false)) {
            // Already stopped
            return
        }

        // Cancel the fetcher job
        jobLock.withLock {
            fetcherJob?.cancel()
            fetcherJob = null
        }

        // Tell backend to stop this playback
        commLink.frontend.control.send(
            KlangCommLink.Cmd.StopPlayback(playbackId = playbackId)
        )

        // Notify the player that this playback has stopped (outside lock to avoid deadlock)
        onStopped(this)
    }
}
