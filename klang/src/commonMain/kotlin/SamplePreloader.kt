package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangLock
import io.peekandpoke.klang.audio_bridge.infra.withLock
import io.peekandpoke.klang.audio_fe.samples.Samples
import kotlinx.coroutines.*

/**
 * Centralized sample preloader that caches samples across all playbacks.
 * Avoids redundant loads and backend sends, making repeated sample plays instant.
 *
 * Thread-safe: All operations are guarded by a lock.
 */
class SamplePreloader(
    private val samples: Samples,
    private val sendControl: (KlangCommLink.Cmd) -> Unit,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
) {
    private val klangTime = KlangTime.create()
    private val lock = KlangLock()

    /** Samples that have been successfully loaded and sent to backend */
    private val sent = mutableSetOf<SampleRequest>()

    /** In-flight loading operations (to avoid duplicate loads for concurrent requests) */
    private val inFlight = mutableMapOf<SampleRequest, Deferred<Unit>>()

    /** Pending backend acknowledgements (for ensureLoadedDeferred) */
    private val pendingAcks = mutableMapOf<SampleRequest, CompletableDeferred<Unit>>()

    /**
     * Ensure all requested samples are loaded and sent to backend.
     * Returns a Deferred that completes when backend acknowledges receipt.
     * Completes immediately if all samples are already sent.
     *
     * Use this for initial preload where caller needs to wait for backend confirmation.
     *
     * @param playbackId The playback requesting these samples
     * @param requests Set of sample requests to ensure are loaded
     * @param signals Optional signal bus to emit preloading progress signals
     */
    fun ensureLoadedDeferred(
        playbackId: String,
        requests: Set<SampleRequest>,
        signals: KlangPlaybackSignals? = null,
    ): Deferred<Unit> = scope.async(dispatcher) {
        // Find which samples are not yet sent
        val newRequests = lock.withLock {
            requests.filterNot { it in sent }.toSet()
        }

        // If all samples already loaded, return immediately (instant playback!)
        if (newRequests.isEmpty()) {
            return@async
        }

        // Emit preloading signal
        signals?.emit(
            KlangPlaybackSignal.PreloadingSamples(
                count = newRequests.size,
                samples = newRequests.map { "${it.bank ?: ""}/${it.sound ?: ""}:${it.index ?: 0}" },
            )
        )

        val startTimeMs = klangTime.internalMsNow()

        // Load and send all new samples
        val jobs = newRequests.map { request ->
            getOrStartLoad(playbackId, request)
        }

        // Capture ack deferreds immediately BEFORE awaiting jobs
        // This prevents race where backend acks before we snapshot
        val ackJobs = lock.withLock {
            newRequests.mapNotNull { pendingAcks[it] }
        }

        // Wait for loading to complete
        jobs.forEach { it.await() }

        // Wait for backend acknowledgements
        ackJobs.forEach { it.await() }

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
     * Trigger loading and sending of samples without waiting for backend acknowledgement.
     * Fire-and-forget for lookahead and backend-requested samples.
     *
     * @param playbackId The playback requesting these samples
     * @param requests Set of sample requests to ensure are loaded
     */
    fun ensureLoadedSilently(playbackId: String, requests: Set<SampleRequest>) {
        scope.launch(dispatcher) {
            // Find which samples are not yet sent
            val newRequests = lock.withLock {
                requests.filterNot { it in sent }.toSet()
            }

            // If all samples already loaded, return immediately
            if (newRequests.isEmpty()) {
                return@launch
            }

            // Load and send all new samples (don't wait for acks)
            val jobs = newRequests.map { request ->
                getOrStartLoad(playbackId, request)
            }

            jobs.forEach { it.await() }

            // Don't emit completion signal for async loads (avoids UI noise)
        }
    }

    /**
     * Get existing in-flight load job, or start a new one if needed.
     * Ensures only one load per sample even if multiple playbacks request it concurrently.
     */
    private fun getOrStartLoad(playbackId: String, request: SampleRequest): Deferred<Unit> {
        return lock.withLock {
            // Check if already in flight
            inFlight[request]?.let { return@withLock it }

            // Create pending ack deferred synchronously BEFORE starting job
            // This ensures it exists when ensureLoadedDeferred takes snapshot
            if (request !in pendingAcks) {
                pendingAcks[request] = CompletableDeferred()
            }

            // Start new load job
            val job = scope.async(dispatcher) {
                loadAndSend(playbackId, request)
            }

            inFlight[request] = job

            // Clean up when done
            job.invokeOnCompletion {
                // We do not want to block here!
                scope.launch {
                    lock.withLock {
                        @Suppress("DeferredResultUnused")
                        inFlight.remove(request)
                    }
                }
            }

            job
        }
    }

    /**
     * Load a sample and send it to the backend.
     * Note: pendingAcks entry is created by getOrStartLoad before this runs.
     */
    private suspend fun loadAndSend(playbackId: String, request: SampleRequest) {
        // Load the sample
        val loaded = samples.get(request)
        val loadedSample = loaded?.sample
        val loadedPcm = loaded?.pcm

        // Build command
        val isNotFound = loadedSample == null || loadedPcm == null
        val cmd = if (isNotFound) {
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
        lock.withLock {
            sent.add(request)

            // For NotFound samples, complete immediately (no need to wait for backend ack)
            if (isNotFound) {
                pendingAcks.remove(request)?.complete(Unit)
            }
            // For Complete samples, wait for backend SampleReceived feedback
        }
    }

    /**
     * Handle backend acknowledgement of sample receipt.
     * Called by KlangPlayer when it receives a SampleReceived feedback.
     */
    fun handleSampleReceived(request: SampleRequest) {
        lock.withLock {
            pendingAcks.remove(request)?.complete(Unit)
        }
    }

    /**
     * Check if a sample is already loaded (useful for UI feedback).
     */
    fun isLoaded(request: SampleRequest): Boolean {
        return lock.withLock {
            request in sent
        }
    }

    /**
     * Clear the cache (called on player shutdown).
     */
    fun clear() {
        lock.withLock {
            sent.clear()
            inFlight.clear()
            pendingAcks.clear()
        }
    }
}
