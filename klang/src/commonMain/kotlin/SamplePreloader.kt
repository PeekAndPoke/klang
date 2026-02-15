package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_fe.samples.Samples
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Centralized sample preloader that caches samples across all playbacks.
 * Avoids redundant loads and backend sends, making repeated sample plays instant.
 *
 * Thread-safe: All operations are guarded by a mutex.
 */
class SamplePreloader(
    private val samples: Samples,
    private val sendControl: (KlangCommLink.Cmd) -> Unit,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    private val klangTime = KlangTime.create()
    private val mutex = Mutex()

    /** Samples that have been successfully loaded and sent to backend */
    private val sent = mutableSetOf<SampleRequest>()

    /** In-flight loading operations (to avoid duplicate loads for concurrent requests) */
    private val inFlight = mutableMapOf<SampleRequest, Deferred<Unit>>()

    /**
     * Ensure all requested samples are loaded and sent to backend.
     * Returns immediately if all samples are already loaded.
     *
     * @param playbackId The playback requesting these samples
     * @param requests Set of sample requests to ensure are loaded
     * @param signals Optional signal bus to emit preloading progress signals
     */
    suspend fun ensureLoaded(
        playbackId: String,
        requests: Set<SampleRequest>,
        signals: KlangPlaybackSignals? = null,
    ) {
        // Find which samples are not yet sent
        val newRequests = mutex.withLock {
            requests.filterNot { it in sent }.toSet()
        }

        // If all samples already loaded, return immediately (instant playback!)
        if (newRequests.isEmpty()) {
            return
        }

        // Emit preloading signal
        signals?.emit(
            KlangPlaybackSignal.PreloadingSamples(
                count = newRequests.size,
                samples = newRequests.map { "${it.bank ?: ""}/${it.sound ?: ""}:${it.index ?: 0}" },
            )
        )

        val startTimeMs = klangTime.internalMsNow()

        // Load all new samples
        val jobs = newRequests.map { request ->
            getOrStartLoad(playbackId, request)
        }

        // Wait for all to complete
        jobs.forEach { it.await() }

        val durationMs = (klangTime.internalMsNow() - startTimeMs).toLong()

        // Emit completion signal
        signals?.emit(
            KlangPlaybackSignal.SamplesPreloaded(
                count = newRequests.size,
                durationMs = durationMs,
            )
        )
    }

    /**
     * Get existing in-flight load job, or start a new one if needed.
     * Ensures only one load per sample even if multiple playbacks request it concurrently.
     */
    private suspend fun getOrStartLoad(playbackId: String, request: SampleRequest): Deferred<Unit> {
        return mutex.withLock {
            // Check if already in flight
            inFlight[request]?.let { return@withLock it }

            // Start new load job
            val job = scope.async(dispatcher) {
                loadAndSend(playbackId, request)
            }

            inFlight[request] = job

            // Clean up when done
            job.invokeOnCompletion {
                scope.async {
                    mutex.withLock {
                        inFlight.remove(request)
                    }
                }
            }

            job
        }
    }

    /**
     * Load a sample and send it to the backend.
     */
    private suspend fun loadAndSend(playbackId: String, request: SampleRequest) {
        // Load the sample
        val loaded = samples.get(request)
        val loadedSample = loaded?.sample
        val loadedPcm = loaded?.pcm

        // Build command
        val cmd = if (loadedSample == null || loadedPcm == null) {
            KlangCommLink.Cmd.Sample.NotFound(playbackId = playbackId, req = request)
        } else {
            KlangCommLink.Cmd.Sample.Complete(
                playbackId = playbackId,
                req = request,
                note = loadedSample.note,
                pitchHz = loadedSample.pitchHz,
                sample = loadedPcm,
            )
        }

        // Send to backend
        sendControl(cmd)

        // Mark as sent
        mutex.withLock {
            sent.add(request)
        }
    }

    /**
     * Check if a sample is already loaded (useful for UI feedback).
     */
    suspend fun isLoaded(request: SampleRequest): Boolean {
        return mutex.withLock {
            request in sent
        }
    }

    /**
     * Clear the cache (called on player shutdown).
     */
    fun clear() {
        scope.async {
            mutex.withLock {
                sent.clear()
                inFlight.clear()
            }
        }
    }
}