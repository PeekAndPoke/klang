package io.peekandpoke.klang.audio_be.voices

import de.peekandpoke.ultra.common.maths.Ease
import io.peekandpoke.klang.audio_be.ONE_OVER_TWELVE
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.filters.AudioFilter.Companion.combine
import io.peekandpoke.klang.audio_be.filters.FormantFilter
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.signalgen.*
import io.peekandpoke.klang.audio_be.voices.filter.buildFilterPipeline
import io.peekandpoke.klang.audio_bridge.*
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangMinHeap
import io.peekandpoke.klang.common.math.ValueRamp

class VoiceScheduler(
    val options: Options,
) {
    class Options(
        val commLink: KlangCommLink.BackendEndpoint,
        val sampleRate: Int,
        val blockFrames: Int,
        val signalGenRegistry: SignalGenRegistry,
        val orbits: Orbits,
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
        val soloAmount: Double, // Solo strength: 1.0 = full solo (others silent), 0.0 = no solo
        val sourceId: String?, // Source ID for tracking which pattern/source this voice came from
    )

    // State with active voices
    private val active = ArrayList<ActiveVoice>(64)

    // Smooth gain transition for solo/mute (background voices fade based on max soloAmount)
    private val soloMuteRamp = ValueRamp(initialValue = 1.0, duration = 1.5, ease = Ease.InOut.cubic)

    /**
     * Tracks solo state for source IDs with delayed cleanup.
     * Source IDs remain in the set for the ramp duration after solo is removed,
     * allowing smooth transitions.
     */
    private class SoloSourceTracker(val rampDurationSec: Double, val sampleRate: Int) {
        private data class SourceState(
            val sourceId: String,
            val cleanupFrame: Long?,  // null = active, non-null = cleanup scheduled at this frame
        )

        private val sources = mutableMapOf<String, SourceState>()

        /**
         * Updates the tracker with currently active solo source IDs.
         * Returns the set of source IDs that should be considered solo'd (including those in cleanup).
         */
        fun update(activeSoloSourceIds: Set<String>, currentFrame: Long): Set<String> {
            // Mark existing sources for cleanup if no longer active
            for ((sourceId, state) in sources.toList()) {
                if (sourceId !in activeSoloSourceIds && state.cleanupFrame == null) {
                    // Schedule cleanup after ramp duration
                    val cleanupFrame = currentFrame + (rampDurationSec * sampleRate).toLong()
                    sources[sourceId] = state.copy(cleanupFrame = cleanupFrame)
                }
            }

            // Add new active sources
            for (sourceId in activeSoloSourceIds) {
                if (sourceId !in sources) {
                    sources[sourceId] = SourceState(sourceId, cleanupFrame = null)
                } else {
                    // Re-activate if it was scheduled for cleanup
                    sources[sourceId] = SourceState(sourceId, cleanupFrame = null)
                }
            }

            // Remove sources that have passed their cleanup time
            val iterator = sources.entries.iterator()
            while (iterator.hasNext()) {
                val (_, state) = iterator.next()
                if (state.cleanupFrame != null && currentFrame >= state.cleanupFrame) {
                    iterator.remove()
                }
            }

            // Return all source IDs (both active and in cleanup phase)
            return sources.keys
        }
    }

    private val soloSourceTracker = SoloSourceTracker(
        rampDurationSec = 2.0,
        sampleRate = options.sampleRate
    )

    // Global start time for absolute time to frame conversion
    private var backendStartTimeSec: Double = 0.0

    // Map playbackId -> per-playback context (registry, epoch, ...)
    private val playbackContexts = mutableMapOf<String, PlaybackCtx>()

    // Track the last processed frame (for epoch recording)
    private var lastProcessedFrame: Long = 0

    // Scratch buffers
    private val voiceBuffer = FloatArray(options.blockFrames)
    private val freqModBuffer = DoubleArray(options.blockFrames)

    // Scratch buffers for SignalGen composition operators (plus, times, etc.)
    private val scratchBuffers = ScratchBuffers(options.blockFrames)

    // Context reused per block
    private val ctx = Voice.RenderContext(
        orbits = options.orbits,
        sampleRate = options.sampleRate,
        blockFrames = options.blockFrames,
        voiceBuffer = voiceBuffer,
        freqModBuffer = freqModBuffer,
        scratchBuffers = scratchBuffers,
    )

    // Diagnostics state
    private var lastDiagnosticsTimeMs = 0.0
    private var minHeadroom = 1.0
    private var avgHeadroom = 1.0

    fun VoiceData.isOscillator(): Boolean = options.signalGenRegistry.contains(sound)

    fun VoiceData.isSampleSound() = !isOscillator()

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
                // No acknowledgement needed - frontend completes immediately
            }

            is KlangCommLink.Cmd.Sample.Complete -> {
                samples[req] = SampleEntry.Complete(
                    req = req,
                    note = msg.note,
                    pitchHz = msg.pitchHz,
                    sample = msg.sample,
                )
                // Send acknowledgement to frontend
                options.commLink.feedback.send(
                    KlangCommLink.Feedback.SampleReceived(
                        playbackId = msg.playbackId,
                        req = req,
                    )
                )
            }

            is KlangCommLink.Cmd.Sample.Chunk -> {
                // Only update partials ...
                val existing = samples[req]
                // Already completed?
                if (existing is SampleEntry.Complete) return
                // Use existing entry of create it
                val entry = (existing as? SampleEntry.Partial) ?: SampleEntry.Partial(
                    req = req,
                    note = msg.note,
                    pitchHz = msg.pitchHz,
                    sample = MonoSamplePcm(sampleRate = msg.sampleRate, pcm = FloatArray(msg.totalSize))
                )

                // Write bytes
                msg.data.copyInto(destination = entry.sample.pcm, destinationOffset = msg.chunkOffset)

                // Update entry
                samples[req] = if (!msg.isLastChunk) {
                    entry
                } else {
                    // Promote to Complete
                    val completed = SampleEntry.Complete(
                        req = req,
                        note = entry.note,
                        pitchHz = entry.pitchHz,
                        sample = entry.sample,
                    )
                    // Send acknowledgement when complete
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

    /**
     * Get the complete sample for the given [req] when available
     */
    fun getCompleteSample(req: SampleRequest): SampleEntry.Complete? {
        return samples[req] as? SampleEntry.Complete
    }

    /**
     * Set the backend start time. Called once when the backend initializes.
     */
    fun setBackendStartTime(startTimeSec: Double) {
        backendStartTimeSec = startTimeSec
    }

    /**
     * Returns the number of currently active voices being rendered
     */
    fun getActiveVoiceCount(): Int = active.size

    /**
     * Cleans up state for the given playbackId.
     *
     * - Removes playback epoch (prevents new voices from this playback)
     * - Cancels scheduled voices that haven't started yet
     * - Leaves active voices alone to ring out naturally through their release phase
     */
    fun cleanup(playbackId: String) {
        // Remove playback epoch - prevents new voices from scheduling
        playbackContexts.remove(playbackId)
        // Remove all scheduled voices for this playback (they never started, so no need to ring out)
        // Note: MinHeap doesn't support efficient removal, so we rebuild without matching voices
        clearScheduled(playbackId)

        // DON'T remove active voices - let them ring out naturally!
        // They will finish their release phase and be removed by the render loop when done.
        // This prevents abrupt cuts and allows natural envelope decay.
    }

    /**
     * Clears scheduled voices for a playback but preserves active voices and clears epoch.
     * Used for tempo/pattern changes to drop future events while maintaining timing anchor.
     *
     * Key difference from cleanup(): Clears epoch to force re-anchoring on next voice.
     */
    fun clearScheduled(playbackId: String) {
        scheduled.removeWhen { it.playbackId == playbackId }
    }

    /**
     * Atomically replaces all scheduled voices for a playback with new ones.
     * Used for tempo/pattern changes to ensure gap-free transitions.
     *
     * When [afterTimeSec] is provided, only removes scheduled voices that start at or after
     * the cutoff time, preserving voices about to play (grace window).
     */
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
            // Clear all scheduled voices and epoch
            clearScheduled(playbackId)
        }

        // Schedule all new voices (first one will establish new epoch if needed)
        voices.forEach { voice ->
            scheduleVoice(voice)
        }
    }

    fun scheduleVoice(voice: ScheduledVoice, clearScheduled: Boolean = false) {
        val pid = voice.playbackId
        // Clear scheduled voices if requested (for tempo/pattern changes)
        if (clearScheduled) {
            this.clearScheduled(pid)
        }
        // Make sure we are synced
        ensureEpoch(voice)
        // Move to queue
        scheduled.push(voice)
        // promote scheduled right away ... the new voice might be just due
        promoteScheduled(lastProcessedFrame, lastProcessedFrame + options.blockFrames)
        // Prefetch sound samples
        prefetchSampleSound(voice)
    }

    fun process(cursorFrame: Long) {
        val startMs = options.performanceTimeMs()

        lastProcessedFrame = cursorFrame
        val blockEnd = cursorFrame + options.blockFrames

        // println("active voices: ${active.size}")

        // 1. Promote scheduled to active
        promoteScheduled(cursorFrame, blockEnd)

        // 2. Prepare Context
        ctx.blockStart = cursorFrame

        // 2.5. Calculate solo/mute gain multipliers based on source IDs
        // Collect currently active solo source IDs
        val activeSoloSourceIds = mutableSetOf<String>()
        var maxSoloAmount = 0.0
        for (voice in active) {
            if (voice.soloAmount > 0.0 && voice.sourceId != null) {
                activeSoloSourceIds.add(voice.sourceId)
                maxSoloAmount = maxOf(maxSoloAmount, voice.soloAmount)
            }
        }

        // Update tracker (handles delayed cleanup) and get effective solo sources
        val soloSourceIds = soloSourceTracker.update(activeSoloSourceIds, cursorFrame)
        val hasSoloSources = soloSourceIds.isNotEmpty()

        // Calculate target gain for non-solo voices
        // When no solo -> targetGain = 1.0 (normal)
        // When solo active -> targetGain = 0.05 (heavily muted)
        val targetGain = if (hasSoloSources) 1.0 - (maxSoloAmount * 0.95) else 1.0

        // Step the ramp towards the target (smooth transition)
        val blockDurationSec = options.blockFrames.toDouble() / options.sampleRateDouble
        val currentBackgroundGain = soloMuteRamp.step(targetGain, blockDurationSec)

        // 3. Render Loop
        var i = 0

        while (i < active.size) {
            val activeVoice = active[i]

            // Apply gain multiplier based on source ID membership in solo set
            val isFromSoloSource = activeVoice.sourceId != null && activeVoice.sourceId in soloSourceIds
            if (isFromSoloSource) {
                // This voice's source is solo'd - always full volume
                activeVoice.voice.setGainMultiplier(1.0)
            } else {
                // Non-solo voices use ramped gain (smoothly transitions between states)
                // When no solo: ramps to 1.0
                // When solo active: ramps to ~0.05
                activeVoice.voice.setGainMultiplier(currentBackgroundGain)
            }

            // Delegate logic to the voice itself
            val isAlive = activeVoice.voice.render(ctx)

            if (isAlive) {
                i++
            } else {
                // Swap-remove for performance
                if (i < active.size - 1) {
                    active[i] = active.last()
                }
                active.removeLast()
            }
        }

        // 4. Diagnostics & Headroom
        val endMs = options.performanceTimeMs()
        val durationMs = endMs - startMs
        // Calculate max available time for this block in ms
        val blockDurationMs = (options.blockFrames.toDouble() / options.sampleRateDouble) * 1000.0

        // 1.0 = all time left, 0.0 = no time left, < 0.0 = glitching
        val headroom = 1.0 - (durationMs / blockDurationMs)

        // Track minimum headroom (worst case) since last report to catch glitches
        if (headroom < minHeadroom) {
            minHeadroom = headroom
        }

        // Exponential moving average for smooth reporting (90% old + 10% new)
        avgHeadroom = (avgHeadroom * 9.0 + headroom) / 10.0

        // Send diagnostics approx 20 times per second (every 50ms)
        if (endMs - lastDiagnosticsTimeMs > 20.0) {
            lastDiagnosticsTimeMs = endMs

            val orbitStates = options.orbits.orbits.map { orbit ->
                KlangCommLink.Feedback.Diagnostics.OrbitState(id = orbit.id, active = orbit.isActive)
            }

            options.commLink.feedback.send(
                KlangCommLink.Feedback.Diagnostics(
                    playbackId = KlangCommLink.SYSTEM_PLAYBACK_ID, // System-wide diagnostics
                    sampleRate = options.sampleRate,
                    renderHeadroom = avgHeadroom,
                    activeVoiceCount = active.size,
                    orbits = orbitStates,
                    backendNowMs = endMs,
                )
            )

            // Reset min headroom for next interval
            minHeadroom = 1.0
            // Note: avgHeadroom is NOT reset - it continues smoothing across reports
        }
    }

    /**
     * Updates the relative time epoch for a playback.
     * This is used to ensure voices play at the correct relative time.
     */
    private fun ensureEpoch(voice: ScheduledVoice) {
        val pid = voice.playbackId

        // Auto-register playback context on first voice
        if (pid !in playbackContexts) {
            // Get the current backend time
            val nowSec = backendStartTimeSec + (lastProcessedFrame.toDouble() / options.sampleRate.toDouble())
            // Calculate the frontend latency
            val latency = maxOf(0.0, nowSec - voice.playbackStartTime)
            // Create per-playback context with forked registry
            playbackContexts[pid] = PlaybackCtx(
                playbackId = pid,
                signalGenRegistry = options.signalGenRegistry.fork(),
                epoch = voice.playbackStartTime + latency,
            )
        }
    }

    /**
     * Prefetches sample sounds for a voice.
     * This is used to ensure that sample sounds are available when needed.
     */
    private fun prefetchSampleSound(voice: ScheduledVoice) {
        val pid = voice.playbackId

        if (voice.data.isSampleSound()) {
            val req = voice.data.asSampleRequest()
            if (!samples.containsKey(req)) {
                samples[req] = SampleEntry.Requested(req)
                options.commLink.feedback.send(
                    KlangCommLink.Feedback.RequestSample(
                        playbackId = pid,
                        req = voice.data.asSampleRequest(),
                    )
                )
            }
        }
    }

    private fun promoteScheduled(nowFrame: Long, blockEnd: Long) {
        val blockEndSec = backendStartTimeSec + (blockEnd.toDouble() / options.sampleRate.toDouble())
        val nowSec = backendStartTimeSec + (nowFrame.toDouble() / options.sampleRate.toDouble())
        val blockSizeSec = options.blockFrames.toDouble() / options.sampleRate.toDouble()
        val oldestAllowedSec = nowSec - (5 * blockSizeSec)

        while (true) {
            val head = scheduled.peek() ?: break

            // Look up this playback's context
            val pCtx = playbackContexts[head.playbackId]
            if (pCtx == null) {
                // No context recorded — shouldn't happen, but skip gracefully
                scheduled.pop()
                continue
            }
            val epoch = pCtx.epoch

            // Convert relative time to absolute backend time
            val absoluteStartSec = epoch + head.startTime

            // Early exit: if this event is beyond current block, stop
            if (absoluteStartSec >= blockEndSec) break

            // Remove from heap
            scheduled.pop()

            // Drop if too old
            if (absoluteStartSec < oldestAllowedSec) {
                continue
            }

            // Convert the voice to use absolute times for makeVoice
            val absoluteVoice = head.copy(
                startTime = absoluteStartSec,
                gateEndTime = epoch + head.gateEndTime,
            )

            makeVoice(absoluteVoice, nowFrame)?.let { voice ->
                val soloAmount = head.data.solo ?: 0.0
                val sourceId = head.data.sourceId
                active.add(ActiveVoice(voice, head.playbackId, soloAmount, sourceId))
            }
        }
    }

    private fun FilterDef.toFilter(): AudioFilter = when (this) {
        is FilterDef.LowPass ->
            LowPassHighPassFilters.createLPF(cutoffHz = cutoffHz, q = q, sampleRate = options.sampleRateDouble)

        is FilterDef.HighPass ->
            LowPassHighPassFilters.createHPF(cutoffHz = cutoffHz, q = q, sampleRate = options.sampleRateDouble)

        is FilterDef.BandPass ->
            LowPassHighPassFilters.createBPF(cutoffHz = cutoffHz, q = q, sampleRate = options.sampleRateDouble)

        is FilterDef.Notch ->
            LowPassHighPassFilters.createNotch(cutoffHz = cutoffHz, q = q, sampleRate = options.sampleRateDouble)

        is FilterDef.Formant ->
            FormantFilter(bands = bands, sampleRate = options.sampleRateDouble)
    }

    private fun FilterDef.toModulator(
        filter: AudioFilter,
        sampleRate: Int,
    ): Voice.FilterModulator? {
        // Get the envelope from the FilterDef
        val envData = when (this) {
            is FilterDef.LowPass -> this.envelope
            is FilterDef.HighPass -> this.envelope
            is FilterDef.BandPass -> this.envelope
            is FilterDef.Notch -> this.envelope
            is FilterDef.Formant -> null // Envelope not yet supported for vowel formants
        }

        // No envelope? No modulator needed
        if (envData == null) return null

        // Filter must be tunable
        if (filter !is AudioFilter.Tunable) return null

        // Get base cutoff
        val baseCutoff = when (this) {
            is FilterDef.LowPass -> this.cutoffHz
            is FilterDef.HighPass -> this.cutoffHz
            is FilterDef.BandPass -> this.cutoffHz
            is FilterDef.Notch -> this.cutoffHz
            is FilterDef.Formant -> 0.0 // Not tunable via single cutoff
        }

        // Resolve envelope to concrete values
        val resolved = envData.resolve()

        // Create Voice.Envelope from resolved FilterEnvelope
        val envelope = Voice.Envelope(
            attackFrames = resolved.attack * sampleRate,
            decayFrames = resolved.decay * sampleRate,
            sustainLevel = resolved.sustain,
            releaseFrames = resolved.release * sampleRate
        )

        return Voice.FilterModulator(
            filter = filter,
            envelope = envelope,
            depth = resolved.depth,
            baseCutoff = baseCutoff
        )
    }

    private fun makeVoice(scheduled: ScheduledVoice, nowFrame: Long): Voice? {
        val sampleRate = options.sampleRate
        val data = scheduled.data

        // Convert absolute time to backend-relative time, then to frames
        val relativeStartTime = scheduled.startTime - backendStartTimeSec
        val relativeGateEndTime = scheduled.gateEndTime - backendStartTimeSec

        val startFrame = (relativeStartTime * sampleRate).toLong()
        val gateEndFrameFromTime = (relativeGateEndTime * sampleRate).toLong()

        // Handle legato (clip) logic, if present, it scales the gate duration (note length)
        val clip = data.legato
        val originalGateDuration = gateEndFrameFromTime - startFrame
        val effectiveGateDuration = if (clip != null) (originalGateDuration * clip).toLong() else originalGateDuration
        // Calculate new end frames based on the effective gate duration
        val gateEndFrame = startFrame + effectiveGateDuration

        // Create filters
        val filters = data.filters.filters.map { it.toFilter() }

        // Create modulators for tunable filters (before combining)
        val modulators = data.filters.filters.zip(filters).mapNotNull { (def, filter) ->
            def.toModulator(filter, sampleRate)
        }

        // Combine filters
        val bakedFilters = filters.combine()

        // Routing
        val orbit = data.orbit ?: 0

        // Pitch / Glisando
        val accelerate = Voice.Accelerate(
            amount = data.accelerate ?: 0.0
        )

        // Vibrator
        val vibratoDepth = (data.vibratoMod ?: 0.0) * ONE_OVER_TWELVE
        val vibrato = Voice.Vibrato(
            depth = vibratoDepth,
            rate = if (vibratoDepth > 0.0) data.vibrato ?: 5.0 else 0.0,
        )

        // Pitch Envelope
        val pEnvAmount = data.pEnv ?: 0.0
        val pitchEnvelope = if (pEnvAmount != 0.0) {
            Voice.PitchEnvelope(
                attackFrames = (data.pAttack ?: 0.0) * sampleRate,
                decayFrames = (data.pDecay ?: 0.0) * sampleRate,
                releaseFrames = (data.pRelease ?: 0.0) * sampleRate,
                amount = pEnvAmount,
                curve = data.pCurve ?: 1.0,
                anchor = data.pAnchor ?: 0.0
            )
        } else {
            null
        }

        // ... (Delay, Reverb, Effects setup is same) ...
        // Delay
        val delay = Voice.Delay(
            amount = data.delay ?: 0.0,
            time = data.delayTime ?: 0.0,
            feedback = data.delayFeedback ?: 0.0,
        )

        // Reverb
        val reverb = Voice.Reverb(
            room = data.room ?: 0.0,
            // In Strudel, room size is between [0 and 10], so we need to normalize it
            // See https://strudel.cc/learn/effects/#roomsize
            roomSize = (data.roomSize ?: 0.0) / 10.0,
            roomFade = data.roomFade,
            roomLp = data.roomLp,
            roomDim = data.roomDim,
            iResponse = data.iResponse,
        )

        // Phaser
        val phaser = Voice.Phaser(
            rate = data.phaser ?: 0.0,
            depth = data.phaserDepth ?: 0.0,
            center = data.phaserCenter ?: 1000.0,
            sweep = data.phaserSweep ?: 1000.0,
        )

        // Tremolo
        val tremolo = Voice.Tremolo(
            rate = data.tremoloSync ?: 0.0,
            depth = data.tremoloDepth ?: 0.0,
            skew = data.tremoloSkew ?: 0.0,
            phase = data.tremoloPhase ?: 0.0,
            shape = data.tremoloShape,
            currentPhase = (data.tremoloPhase ?: 0.0) * TWO_PI // Initial phase
        )

        // Ducking / Sidechain
        val duckOrbitParam = data.duckOrbit
        val duckAttackParam = data.duckAttack
        val duckDepthParam = data.duckDepth

        val ducking = if (duckOrbitParam != null && duckDepthParam != null && duckDepthParam > 0.0) {
            Voice.Ducking(
                orbitId = duckOrbitParam,
                attackSeconds = duckAttackParam ?: 0.1,
                depth = duckDepthParam
            )
        } else {
            null
        }

        // Dynamics
        val baseGain = data.gain ?: 1.0
        val velocity = data.velocity ?: 1.0
        val gain = baseGain * velocity
        val postGain = data.postGain ?: 1.0

        // Compressor
        val compressor = Voice.Compressor.fromStringConfig(data.compressor)

        // Effects
        val distort = Voice.Distort(amount = data.distort ?: 0.0, shape = data.distortShape ?: "soft")
        val crush = Voice.Crush(amount = data.crush ?: 0.0)
        val coarse = Voice.Coarse(amount = data.coarse ?: 0.0)

        // FM Synthesis
        val fm = if (data.fmh != null || (data.fmEnv ?: 0.0) != 0.0) {
            val ratio = data.fmh ?: 1.0
            val depth = data.fmEnv ?: 0.0

            // FM Envelope (similar to Filter Modulator envelope logic)
            val fmEnv = Voice.Envelope(
                attackFrames = (data.fmAttack ?: 0.0) * sampleRate,
                decayFrames = (data.fmDecay ?: 0.0) * sampleRate,
                sustainLevel = data.fmSustain ?: 1.0,
                releaseFrames = 0.0 // Simplified for now
            )

            Voice.Fm(ratio, depth, fmEnv)
        } else {
            null
        }

        // Decision
        val freqHz = data.freqHz
        val sound = data.sound
        val isOsci = data.isOscillator() && freqHz != null
        val isSample = data.isSampleSound() && sound != null

        return when {
            // /////////////////////////////////////////////////////////////////////////////////////////////////////////
            isOsci -> {
                // For Synths, use Pattern ADSR or Synth Defaults
                val resolvedAdsr = data.adsr
                    .resolve(AdsrEnvelope.defaultSynth)

                val voiceDurationFrames = (gateEndFrame - startFrame).toInt()

                val signal = options.signalGenRegistry.createSignalGen(sound, data, freqHz)
                    ?: return null

                buildVoice(
                    data = data,
                    resolvedAdsr = resolvedAdsr,
                    startFrame = startFrame,
                    gateEndFrame = gateEndFrame,
                    voiceDurationFrames = voiceDurationFrames,
                    orbit = orbit,
                    gain = gain,
                    postGain = postGain,
                    accelerate = accelerate,
                    vibrato = vibrato,
                    pitchEnvelope = pitchEnvelope,
                    bakedFilters = bakedFilters,
                    modulators = modulators,
                    delay = delay,
                    reverb = reverb,
                    phaser = phaser,
                    tremolo = tremolo,
                    ducking = ducking,
                    compressor = compressor,
                    distort = distort,
                    crush = crush,
                    coarse = coarse,
                    fm = fm,
                    signal = signal,
                    freqHz = freqHz,
                )
            }

            // /////////////////////////////////////////////////////////////////////////////////////////////////////////
            isSample -> {
                // Did we already request this sample?
                val sampleRequest = data.asSampleRequest()

                // Do we have the data for this sample?
                val entry = getCompleteSample(sampleRequest) ?: return null
                val sample = entry.sample

                if (sample.pcm.size <= 1) return null

                // Resolve ADSR: Pattern > Sample Defaults > Synth Defaults
                val resolvedAdsr = data.adsr
                    .mergeWith(sample.meta.adsr)
                    .resolve(AdsrEnvelope.defaultSynth)

                val baseSamplePitchHz = entry.pitchHz
                val targetPitchHz = data.freqHz ?: baseSamplePitchHz
                // We allow 5 octaves up and down pitch 1/32 .. 32
                val pitchRatio = (targetPitchHz / baseSamplePitchHz).coerceIn(1.0 / 32.0, 32.0)

                // Speed modifier
                val loopSpeed = data.speed ?: 1.0
                val rate = (sample.sampleRate.toDouble() / sampleRate.toDouble()) * pitchRatio * loopSpeed

                val pcmSize = sample.pcm.size.toDouble()

                // Begin/End Logic
                val loopBeginRatio = data.begin ?: 0.0
                val startSample = loopBeginRatio * pcmSize

                val loopEndRatio = data.end ?: 1.0
                val endSample = loopEndRatio * pcmSize

                // Loop Logic
                val explicitLoop = data.loop == true
                // If begin/end are set but loop is NOT set, we disable meta-looping to play just the slice.
                // If begin/end are NOT set, we fall back to meta-looping.
                val useMetaLoop = !explicitLoop && data.begin == null && data.end == null

                // Resolve loop points
                val sampleMetaLoop = sample.meta.loop

                val loopStart: Double
                val loopEnd: Double
                val isLooping: Boolean

                if (explicitLoop) {
                    loopStart = startSample
                    loopEnd = endSample
                    isLooping = loopStart >= 0.0 && loopEnd > loopStart
                } else if (useMetaLoop && sampleMetaLoop != null) {
                    loopStart = sampleMetaLoop.start.toDouble()
                    loopEnd = sampleMetaLoop.end.toDouble()
                    isLooping = loopStart >= 0.0 && loopEnd > loopStart
                } else {
                    loopStart = -1.0
                    loopEnd = -1.0
                    isLooping = false
                }

                val cut = data.cut

                // Handle Cut / Choke Groups logic before creating the new voice
                if (cut != null) {
                    val iterator = active.iterator()
                    while (iterator.hasNext()) {
                        val activeVoice = iterator.next()
                        if (activeVoice.voice.cut == cut) {
                            // Kill the active voice in the same cut group
                            // TODO: Use a fade out / release phase instead of hard cut?
                            iterator.remove()
                        }
                    }
                }

                // Start Position Logic:
                // If begin is set, use it.
                // Else, if we use meta loop, start at loop start (for pads).
                // Else start at anchor.
                val playhead0 = if (data.begin != null) {
                    startSample
                } else if (useMetaLoop && sampleMetaLoop != null) {
                    sampleMetaLoop.start.toDouble()
                } else {
                    sample.meta.anchor * sample.sampleRate
                }

                val voiceDurationFrames = (gateEndFrame - nowFrame).toInt()

                val signal = SampleSignalGen(
                    pcm = sample.pcm,
                    rate = rate,
                    playhead = playhead0,
                    loopStart = loopStart,
                    loopEnd = loopEnd,
                    isLooping = isLooping,
                    stopFrame = endSample,
                )

                buildVoice(
                    data = data,
                    resolvedAdsr = resolvedAdsr,
                    startFrame = nowFrame, // Start immediately since we ignore lateFrames
                    gateEndFrame = gateEndFrame,
                    voiceDurationFrames = voiceDurationFrames,
                    orbit = orbit,
                    gain = gain,
                    postGain = postGain,
                    accelerate = accelerate,
                    vibrato = vibrato,
                    pitchEnvelope = pitchEnvelope,
                    bakedFilters = bakedFilters,
                    modulators = modulators,
                    delay = delay,
                    reverb = reverb,
                    phaser = phaser,
                    tremolo = tremolo,
                    ducking = ducking,
                    compressor = compressor,
                    distort = distort,
                    crush = crush,
                    coarse = coarse,
                    fm = fm,
                    signal = signal,
                    freqHz = baseSamplePitchHz,
                    cut = cut,
                )
            }

            else -> null
        }
    }

    private fun buildVoice(
        data: VoiceData,
        resolvedAdsr: AdsrEnvelope.Resolved,
        startFrame: Long,
        gateEndFrame: Long,
        voiceDurationFrames: Int,
        orbit: Int,
        gain: Double,
        postGain: Double,
        accelerate: Voice.Accelerate,
        vibrato: Voice.Vibrato,
        pitchEnvelope: Voice.PitchEnvelope?,
        bakedFilters: AudioFilter,
        modulators: List<Voice.FilterModulator>,
        delay: Voice.Delay,
        reverb: Voice.Reverb,
        phaser: Voice.Phaser,
        tremolo: Voice.Tremolo,
        ducking: Voice.Ducking?,
        compressor: Voice.Compressor?,
        distort: Voice.Distort,
        crush: Voice.Crush,
        coarse: Voice.Coarse,
        fm: Voice.Fm?,
        signal: SignalGen,
        freqHz: Double,
        cut: Int? = null,
    ): VoiceImpl {
        val sampleRate = options.sampleRate
        val envelope = Voice.Envelope.of(resolvedAdsr, sampleRate)
        val endFrame = gateEndFrame + (resolvedAdsr.release * sampleRate).toLong()
        val releaseFrames = (resolvedAdsr.release * sampleRate).toInt()
        val voiceEndFrame = voiceDurationFrames + releaseFrames

        val signalCtx = SignalContext(
            sampleRate = sampleRate,
            voiceDurationFrames = voiceDurationFrames,
            gateEndFrame = voiceDurationFrames,
            releaseFrames = releaseFrames,
            voiceEndFrame = voiceEndFrame,
            scratchBuffers = scratchBuffers,
        )

        // Build filter pipeline: only include active stages
        val filterPipeline = buildFilterPipeline(
            modulators = modulators,
            startFrame = startFrame,
            gateEndFrame = gateEndFrame,
            crush = crush,
            coarse = coarse,
            mainFilter = bakedFilters,
            envelope = envelope,
            distort = distort,
            tremolo = tremolo,
            phaser = phaser,
            sampleRate = sampleRate,
        )

        return VoiceImpl(
            orbitId = orbit,
            startFrame = startFrame,
            endFrame = endFrame,
            gateEndFrame = gateEndFrame,
            gain = gain,
            pan = data.pan ?: 0.5,
            postGain = postGain,
            accelerate = accelerate,
            vibrato = vibrato,
            pitchEnvelope = pitchEnvelope,
            delay = delay,
            reverb = reverb,
            phaser = phaser,
            ducking = ducking,
            compressor = compressor,
            signal = signal,
            signalCtx = signalCtx,
            fm = fm,
            freqHz = freqHz,
            cut = cut,
            filterPipeline = filterPipeline,
        )
    }

}
