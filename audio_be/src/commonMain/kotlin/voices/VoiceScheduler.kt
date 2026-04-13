package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.common.infra.KlangMinHeap
import io.peekandpoke.klang.common.math.ValueRamp
import io.peekandpoke.ultra.maths.Ease

class VoiceScheduler(
    val options: Options,
) {
    class Options(
        val commLink: KlangCommLink.BackendEndpoint,
        val sampleRate: Int,
        val blockFrames: Int,
        val ignitorRegistry: IgnitorRegistry,
        val cylinders: Cylinders,
        /** Supplier for current backend time in milliseconds (from KlangTime) */
        val performanceTimeMs: () -> Double = { 0.0 },
    ) {
        val sampleRateDouble = sampleRate.toDouble()
    }

    sealed interface SampleEntry {
        data class Requested(
            override val req: SampleRequest,
        ) : SampleEntry

        data class NotFound(
            override val req: SampleRequest,
        ) : SampleEntry

        data class Complete(
            override val req: SampleRequest,
            val note: String?,
            val pitchHz: Double,
            val sample: MonoSamplePcm,
        ) : SampleEntry

        data class Partial(
            override val req: SampleRequest,
            val note: String?,
            val pitchHz: Double,
            val sample: MonoSamplePcm,
        ) : SampleEntry

        val req: SampleRequest
    }

    // The samples uploaded to the backend
    private val samples = mutableMapOf<SampleRequest, SampleEntry>()

    // Heap with scheduled voices
    private val scheduled = KlangMinHeap<ScheduledVoice> { a, b -> a.startTime < b.startTime }

    // Wrapper to track playbackId and solo state alongside Voice
    private data class ActiveVoice(
        val voice: Voice,
        val playbackId: String,
        val soloAmount: Double,
        val sourceId: String?,
    )

    // State with active voices
    private val active = ArrayList<ActiveVoice>(64)

    // Smooth gain transition for solo/mute
    private val soloMuteRamp = ValueRamp(initialValue = 1.0, duration = 1.5, ease = Ease.InOut.cubic)

    /**
     * Tracks solo state for source IDs with delayed cleanup.
     */
    private class SoloSourceTracker(val rampDurationSec: Double, val sampleRate: Int) {
        private data class SourceState(
            val sourceId: String,
            val cleanupFrame: Int?,
        )

        private val sources = mutableMapOf<String, SourceState>()

        // Pre-allocated list for deferred mutations — avoids toList() allocation on the audio thread.
        // Entries are (sourceId, newState) pairs to apply after the read-only iteration.
        private val pendingUpdates = mutableListOf<Pair<String, SourceState>>()

        fun update(activeSoloSourceIds: Set<String>, currentFrame: Int): Set<String> {
            // Pass 1: find sources that need a cleanup frame — collect updates without mutating
            pendingUpdates.clear()
            for ((sourceId, state) in sources) {
                if (sourceId !in activeSoloSourceIds && state.cleanupFrame == null) {
                    val cleanupFrame = currentFrame + (rampDurationSec * sampleRate).toInt()
                    pendingUpdates.add(sourceId to state.copy(cleanupFrame = cleanupFrame))
                }
            }
            // Apply deferred mutations
            for ((sourceId, newState) in pendingUpdates) {
                sources[sourceId] = newState
            }

            for (sourceId in activeSoloSourceIds) {
                sources[sourceId] = SourceState(sourceId, cleanupFrame = null)
            }

            val iterator = sources.entries.iterator()
            while (iterator.hasNext()) {
                val (_, state) = iterator.next()
                if (state.cleanupFrame != null && currentFrame >= state.cleanupFrame) {
                    iterator.remove()
                }
            }

            return sources.keys
        }
    }

    private val soloSourceTracker = SoloSourceTracker(
        rampDurationSec = 2.0,
        sampleRate = options.sampleRate,
    )

    // Global start time for absolute time to frame conversion
    private var backendStartTimeSec: Double = 0.0

    // Map playbackId -> per-playback context (registry, epoch, ...)
    private val playbackContexts = mutableMapOf<String, PlaybackCtx>()

    // Track the last processed frame (for epoch recording)
    private var lastProcessedFrame: Int = 0

    // Scratch buffers — pre-allocated to avoid per-block heap allocation on the audio thread
    private val voiceBuffer = FloatArray(options.blockFrames)
    private val freqModBuffer = DoubleArray(options.blockFrames)
    private val scratchBuffers = ScratchBuffers(options.blockFrames)
    private val activeSoloSourceIds = mutableSetOf<String>()

    // Context reused per block
    private val ctx = Voice.RenderContext(
        cylinders = options.cylinders,
        sampleRate = options.sampleRate,
        blockFrames = options.blockFrames,
        voiceBuffer = voiceBuffer,
        freqModBuffer = freqModBuffer,
        scratchBuffers = scratchBuffers,
    )

    // Voice factory — creates Voice instances from VoiceData
    private val voiceFactory = VoiceFactory(
        sampleRate = options.sampleRate,
        sampleRateDouble = options.sampleRateDouble,
        ignitorRegistry = options.ignitorRegistry,
        cylinders = options.cylinders,
        voiceBuffer = voiceBuffer,
        freqModBuffer = freqModBuffer,
        scratchBuffers = scratchBuffers,
    )

    // Diagnostics state
    private var lastDiagnosticsTimeMs = 0.0
    private var minHeadroom = 1.0
    private var avgHeadroom = 1.0

    fun clear() {
        scheduled.clear()
        active.clear()
        playbackContexts.clear()
    }

    fun addSample(msg: KlangCommLink.Cmd.Sample) {
        val req = msg.req

        when (msg) {
            is KlangCommLink.Cmd.Sample.NotFound -> {
                samples[req] = SampleEntry.NotFound(req)
            }

            is KlangCommLink.Cmd.Sample.Complete -> {
                samples[req] = SampleEntry.Complete(
                    req = req,
                    note = msg.note,
                    pitchHz = msg.pitchHz,
                    sample = msg.sample,
                )
                options.commLink.feedback.send(
                    KlangCommLink.Feedback.SampleReceived(
                        playbackId = msg.playbackId,
                        req = req,
                    )
                )
            }

            is KlangCommLink.Cmd.Sample.Chunk -> {
                val existing = samples[req]
                if (existing is SampleEntry.Complete) return
                val entry = (existing as? SampleEntry.Partial) ?: SampleEntry.Partial(
                    req = req,
                    note = msg.note,
                    pitchHz = msg.pitchHz,
                    sample = MonoSamplePcm(sampleRate = msg.sampleRate, pcm = FloatArray(msg.totalSize)),
                )

                msg.data.copyInto(destination = entry.sample.pcm, destinationOffset = msg.chunkOffset)

                samples[req] = if (!msg.isLastChunk) {
                    entry
                } else {
                    val completed = SampleEntry.Complete(
                        req = req,
                        note = entry.note,
                        pitchHz = entry.pitchHz,
                        sample = entry.sample,
                    )
                    options.commLink.feedback.send(
                        KlangCommLink.Feedback.SampleReceived(
                            playbackId = msg.playbackId,
                            req = req,
                        )
                    )
                    completed
                }
            }
        }
    }

    fun getCompleteSample(req: SampleRequest): SampleEntry.Complete? {
        return samples[req] as? SampleEntry.Complete
    }

    fun setBackendStartTime(startTimeSec: Double) {
        backendStartTimeSec = startTimeSec
    }

    fun getActiveVoiceCount(): Int = active.size

    fun cleanup(playbackId: String) {
        playbackContexts.remove(playbackId)
        clearScheduled(playbackId)
    }

    fun clearScheduled(playbackId: String) {
        scheduled.removeWhen { it.playbackId == playbackId }
    }

    fun replaceVoices(playbackId: String, voices: List<ScheduledVoice>, afterTimeSec: Double? = null) {
        if (afterTimeSec != null) {
            val epoch = playbackContexts[playbackId]?.epoch
            if (epoch != null) {
                val cutoffSec = epoch + afterTimeSec
                scheduled.removeWhen { voice ->
                    voice.playbackId == playbackId &&
                            (playbackContexts[voice.playbackId]?.epoch?.let { it + voice.startTime } ?: 0.0) >= cutoffSec
                }
            }
        } else {
            clearScheduled(playbackId)
        }

        scheduleVoices(voices)
    }

    fun scheduleVoice(voice: ScheduledVoice, clearScheduled: Boolean = false) {
        val pid = voice.playbackId
        if (clearScheduled) {
            this.clearScheduled(pid)
        }
        ensureEpoch(voice)
        scheduled.push(voice)
        promoteScheduled(lastProcessedFrame, lastProcessedFrame + options.blockFrames)
        prefetchSampleSound(voice)
    }

    /**
     * Batched schedule. All voices share a single nowSec snapshot for [ensureEpoch] / [promoteScheduled],
     * which prevents later voices in a tight cluster from sliding into the past while earlier voices
     * are being delivered (the per-voice send path would otherwise interleave with audio blocks).
     */
    fun scheduleVoices(voices: List<ScheduledVoice>) {
        if (voices.isEmpty()) return
        for (voice in voices) {
            ensureEpoch(voice)
            scheduled.push(voice)
            prefetchSampleSound(voice)
        }
        promoteScheduled(lastProcessedFrame, lastProcessedFrame + options.blockFrames)
    }

    fun process(cursorFrame: Int) {
        val startMs = options.performanceTimeMs()

        lastProcessedFrame = cursorFrame
        val blockEnd = cursorFrame + options.blockFrames

        // 1. Promote scheduled to active
        promoteScheduled(cursorFrame, blockEnd)

        // 2. Prepare Context
        ctx.blockStart = cursorFrame

        // 2.5. Calculate solo/mute gain multipliers
        activeSoloSourceIds.clear()
        var maxSoloAmount = 0.0
        for (voice in active) {
            if (voice.soloAmount > 0.0 && voice.sourceId != null) {
                activeSoloSourceIds.add(voice.sourceId)
                maxSoloAmount = maxOf(maxSoloAmount, voice.soloAmount)
            }
        }

        val soloSourceIds = soloSourceTracker.update(activeSoloSourceIds, cursorFrame)
        val hasSoloSources = soloSourceIds.isNotEmpty()

        val targetGain = if (hasSoloSources) 1.0 - (maxSoloAmount * 0.95) else 1.0
        val blockDurationSec = options.blockFrames.toDouble() / options.sampleRateDouble
        val currentBackgroundGain = soloMuteRamp.step(targetGain, blockDurationSec)

        // 3. Render Loop
        var i = 0

        while (i < active.size) {
            val activeVoice = active[i]

            val isFromSoloSource = activeVoice.sourceId != null && activeVoice.sourceId in soloSourceIds
            if (isFromSoloSource) {
                activeVoice.voice.setGainMultiplier(1.0)
            } else {
                activeVoice.voice.setGainMultiplier(currentBackgroundGain)
            }

            val isAlive = activeVoice.voice.render(ctx)

            if (isAlive) {
                i++
            } else {
                if (i < active.size - 1) {
                    active[i] = active.last()
                }
                active.removeLast()
            }
        }

        // 4. Diagnostics & Headroom
        val endMs = options.performanceTimeMs()
        val durationMs = endMs - startMs
        val blockDurationMs = (options.blockFrames.toDouble() / options.sampleRateDouble) * 1000.0
        val headroom = 1.0 - (durationMs / blockDurationMs)

        if (headroom < minHeadroom) {
            minHeadroom = headroom
        }

        avgHeadroom = (avgHeadroom * 9.0 + headroom) / 10.0

        if (endMs - lastDiagnosticsTimeMs > 20.0) {
            lastDiagnosticsTimeMs = endMs

            val cylinderStates = options.cylinders.cylinders.map { cylinder ->
                KlangCommLink.Feedback.Diagnostics.CylinderState(id = cylinder.id, active = cylinder.isActive)
            }

            options.commLink.feedback.send(
                KlangCommLink.Feedback.Diagnostics(
                    playbackId = KlangCommLink.SYSTEM_PLAYBACK_ID,
                    sampleRate = options.sampleRate,
                    renderHeadroom = avgHeadroom,
                    activeVoiceCount = active.size,
                    cylinders = cylinderStates,
                    backendNowMs = endMs,
                )
            )

            minHeadroom = 1.0
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ═════════════════════════════════════════════════════════════════════════════

    private fun ensureEpoch(voice: ScheduledVoice) {
        val pid = voice.playbackId

        if (pid !in playbackContexts) {
            val nowSec = backendStartTimeSec + (lastProcessedFrame.toDouble() / options.sampleRate.toDouble())
            val latency = maxOf(0.0, nowSec - voice.playbackStartTime)
            playbackContexts[pid] = PlaybackCtx(
                playbackId = pid,
                ignitorRegistry = options.ignitorRegistry.fork(),
                epoch = voice.playbackStartTime + latency,
            )
        }
    }

    private fun prefetchSampleSound(voice: ScheduledVoice) {
        val pid = voice.playbackId

        if (!options.ignitorRegistry.contains(voice.data.sound)) {
            val req = voice.data.asSampleRequest()
            if (!samples.containsKey(req)) {
                samples[req] = SampleEntry.Requested(req)
                options.commLink.feedback.send(
                    KlangCommLink.Feedback.RequestSample(
                        playbackId = pid,
                        req = req,
                    )
                )
            }
        }
    }

    private fun promoteScheduled(nowFrame: Int, blockEnd: Int) {
        val blockEndSec = backendStartTimeSec + (blockEnd.toDouble() / options.sampleRate.toDouble())
        val nowSec = backendStartTimeSec + (nowFrame.toDouble() / options.sampleRate.toDouble())
        val blockSizeSec = options.blockFrames.toDouble() / options.sampleRate.toDouble()
        val oldestAllowedSec = nowSec - (5 * blockSizeSec)

        while (true) {
            val head = scheduled.peek() ?: break

            val pCtx = playbackContexts[head.playbackId]
            if (pCtx == null) {
                scheduled.pop()
                continue
            }
            val epoch = pCtx.epoch

            val absoluteStartSec = epoch + head.startTime

            if (absoluteStartSec >= blockEndSec) break

            scheduled.pop()

            if (absoluteStartSec < oldestAllowedSec) {
                println(
                    "VoiceScheduler: dropped voice pid=${head.playbackId} " +
                            "late=${((nowSec - absoluteStartSec) * 1000.0).toInt()}ms"
                )
                continue
            }

            val absoluteVoice = head.copy(
                startTime = absoluteStartSec,
                gateEndTime = epoch + head.gateEndTime,
            )

            // Handle Cut / Choke Groups before creating the new voice
            val cut = head.data.cut
            if (cut != null) {
                val iterator = active.iterator()
                while (iterator.hasNext()) {
                    val activeVoice = iterator.next()
                    if (activeVoice.voice.cut == cut) {
                        // TODO: Use a fade out / release phase instead of hard cut?
                        iterator.remove()
                    }
                }
            }

            voiceFactory.makeVoice(
                scheduled = absoluteVoice,
                nowFrame = nowFrame,
                backendStartTimeSec = backendStartTimeSec,
                playbackCtx = pCtx,
                getSample = ::getCompleteSample,
            )?.let { voice ->
                val soloAmount = head.data.solo ?: 0.0
                val sourceId = head.data.sourceId
                active.add(ActiveVoice(voice, head.playbackId, soloAmount, sourceId))
            }
        }
    }
}
