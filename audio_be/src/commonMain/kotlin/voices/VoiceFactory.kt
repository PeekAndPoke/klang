package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.ONE_OVER_TWELVE
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.exciter.*
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.filters.AudioFilter.Companion.combine
import io.peekandpoke.klang.audio_be.filters.FormantFilter
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.voices.strip.BlockContext
import io.peekandpoke.klang.audio_be.voices.strip.excite.ExciteRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.buildFilterPipeline
import io.peekandpoke.klang.audio_be.voices.strip.pitch.buildPitchPipeline
import io.peekandpoke.klang.audio_bridge.*

/**
 * Creates [Voice] instances from [ScheduledVoice] data.
 *
 * Extracted from [VoiceScheduler] to separate voice construction (parameter mapping,
 * pipeline building, filter creation) from scheduling concerns (timing, promotion, lifecycle).
 */
class VoiceFactory(
    private val sampleRate: Int,
    private val sampleRateDouble: Double,
    private val exciterRegistry: ExciterRegistry,
    private val orbits: Orbits,
    private val voiceBuffer: FloatArray,
    private val freqModBuffer: DoubleArray,
    private val scratchBuffers: ScratchBuffers,
) {

    /**
     * Creates a voice from a scheduled voice with absolute timing and resolved sample data.
     *
     * For oscillator voices, [nowFrame] is not used (startFrame comes from the schedule).
     * For sample voices, [nowFrame] is used as the start frame to avoid late-start artifacts.
     *
     * Returns null if the voice cannot be created (unknown sound, missing sample, etc.).
     */
    fun makeVoice(
        scheduled: ScheduledVoice,
        nowFrame: Long,
        backendStartTimeSec: Double,
        playbackCtx: PlaybackCtx,
        getSample: (SampleRequest) -> VoiceScheduler.SampleEntry.Complete?,
    ): Voice? {
        val data = scheduled.data

        // Convert absolute time to backend-relative time, then to frames
        val relativeStartTime = scheduled.startTime - backendStartTimeSec
        val relativeGateEndTime = scheduled.gateEndTime - backendStartTimeSec

        val startFrame = (relativeStartTime * sampleRate).toLong()
        val gateEndFrameFromTime = (relativeGateEndTime * sampleRate).toLong()

        // Handle legato (clip) logic
        val clip = data.legato
        val originalGateDuration = gateEndFrameFromTime - startFrame
        val effectiveGateDuration = if (clip != null) (originalGateDuration * clip).toLong() else originalGateDuration
        val gateEndFrame = startFrame + effectiveGateDuration

        // Create filters
        val filters = data.filters.filters.map { it.toFilter() }
        val modulators = data.filters.filters.zip(filters).mapNotNull { (def, filter) ->
            def.toModulator(filter, sampleRate)
        }
        val bakedFilters = filters.combine()

        // Routing
        val orbit = data.orbit ?: 0

        // Pitch / Glissando
        val accelerate = Voice.Accelerate(amount = data.accelerate ?: 0.0)

        // Vibrato
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
                anchor = data.pAnchor ?: 0.0,
            )
        } else {
            null
        }

        // Delay
        val delay = Voice.Delay(
            amount = data.delay ?: 0.0,
            time = data.delayTime ?: 0.0,
            feedback = data.delayFeedback ?: 0.0,
        )

        // Reverb
        val reverb = Voice.Reverb(
            room = data.room ?: 0.0,
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
            currentPhase = (data.tremoloPhase ?: 0.0) * TWO_PI,
        )

        // Ducking / Sidechain
        val duckOrbitParam = data.duckOrbit
        val duckDepthParam = data.duckDepth
        val ducking = if (duckOrbitParam != null && duckDepthParam != null && duckDepthParam > 0.0) {
            Voice.Ducking(
                orbitId = duckOrbitParam,
                attackSeconds = data.duckAttack ?: 0.1,
                depth = duckDepthParam,
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
            val fmEnv = Voice.Envelope(
                attackFrames = (data.fmAttack ?: 0.0) * sampleRate,
                decayFrames = (data.fmDecay ?: 0.0) * sampleRate,
                sustainLevel = data.fmSustain ?: 1.0,
                releaseFrames = 0.0,
            )
            Voice.Fm(ratio, depth, fmEnv)
        } else {
            null
        }

        // Decision: oscillator vs sample
        val freqHz = data.freqHz
        val sound = data.sound
        val isOsci = exciterRegistry.contains(sound)
        val isSample = !exciterRegistry.contains(sound) && sound != null

        return when {
            isOsci -> {
                val resolvedAdsr = data.adsr.resolve(AdsrEnvelope.defaultSynth)
                val voiceDurationFrames = (gateEndFrame - startFrame).toInt()
                val signal = playbackCtx.exciterRegistry.createExciter(sound, data, freqHz ?: 0.0)
                    ?: return null

                buildVoice(
                    data, resolvedAdsr, startFrame, gateEndFrame, voiceDurationFrames, orbit,
                    gain, postGain, accelerate, vibrato, pitchEnvelope, bakedFilters, modulators,
                    delay, reverb, phaser, tremolo, ducking, compressor, distort, crush, coarse,
                    fm, signal, freqHz ?: 0.0,
                )
            }

            isSample -> {
                val sampleRequest = data.asSampleRequest()
                val entry = getSample(sampleRequest) ?: return null
                val sample = entry.sample
                if (sample.pcm.size <= 1) return null

                val resolvedAdsr = data.adsr
                    .mergeWith(sample.meta.adsr)
                    .resolve(AdsrEnvelope.defaultSynth)

                val baseSamplePitchHz = entry.pitchHz
                val targetPitchHz = data.freqHz ?: baseSamplePitchHz
                val pitchRatio = (targetPitchHz / baseSamplePitchHz).coerceIn(1.0 / 32.0, 32.0)
                val loopSpeed = data.speed ?: 1.0
                val rate = (sample.sampleRate.toDouble() / sampleRate.toDouble()) * pitchRatio * loopSpeed
                val pcmSize = sample.pcm.size.toDouble()

                val loopBeginRatio = data.begin ?: 0.0
                val startSample = loopBeginRatio * pcmSize
                val loopEndRatio = data.end ?: 1.0
                val endSample = loopEndRatio * pcmSize

                val explicitLoop = data.loop == true
                val useMetaLoop = !explicitLoop && data.begin == null && data.end == null
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

                val playhead0 = if (data.begin != null) {
                    startSample
                } else if (useMetaLoop && sampleMetaLoop != null) {
                    sampleMetaLoop.start.toDouble()
                } else {
                    sample.meta.anchor * sample.sampleRate
                }

                val voiceDurationFrames = (gateEndFrame - nowFrame).toInt()

                val signal = SampleExciter(
                    pcm = sample.pcm,
                    rate = rate,
                    playhead = playhead0,
                    loopStart = loopStart,
                    loopEnd = loopEnd,
                    isLooping = isLooping,
                    stopFrame = endSample,
                    analog = data.oscParams?.get("analog") ?: 0.0,
                )

                buildVoice(
                    data, resolvedAdsr, nowFrame, gateEndFrame, voiceDurationFrames, orbit,
                    gain, postGain, accelerate, vibrato, pitchEnvelope, bakedFilters, modulators,
                    delay, reverb, phaser, tremolo, ducking, compressor, distort, crush, coarse,
                    fm, signal, baseSamplePitchHz, data.cut,
                )
            }

            else -> null
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ═════════════════════════════════════════════════════════════════════════════

    private fun FilterDef.toFilter(): AudioFilter = when (this) {
        is FilterDef.LowPass -> LowPassHighPassFilters.createLPF(cutoffHz, q, sampleRateDouble)
        is FilterDef.HighPass -> LowPassHighPassFilters.createHPF(cutoffHz, q, sampleRateDouble)
        is FilterDef.BandPass -> LowPassHighPassFilters.createBPF(cutoffHz, q, sampleRateDouble)
        is FilterDef.Notch -> LowPassHighPassFilters.createNotch(cutoffHz, q, sampleRateDouble)
        is FilterDef.Formant -> FormantFilter(bands = bands, sampleRate = sampleRateDouble)
    }

    private fun FilterDef.toModulator(
        filter: AudioFilter,
        sampleRate: Int,
    ): Voice.FilterModulator? {
        val envData = when (this) {
            is FilterDef.LowPass -> this.envelope
            is FilterDef.HighPass -> this.envelope
            is FilterDef.BandPass -> this.envelope
            is FilterDef.Notch -> this.envelope
            is FilterDef.Formant -> null
        } ?: return null

        if (filter !is AudioFilter.Tunable) return null

        val baseCutoff = when (this) {
            is FilterDef.LowPass -> this.cutoffHz
            is FilterDef.HighPass -> this.cutoffHz
            is FilterDef.BandPass -> this.cutoffHz
            is FilterDef.Notch -> this.cutoffHz
            is FilterDef.Formant -> 0.0
        }

        val resolved = envData.resolve()
        val envelope = Voice.Envelope(
            attackFrames = resolved.attack * sampleRate,
            decayFrames = resolved.decay * sampleRate,
            sustainLevel = resolved.sustain,
            releaseFrames = resolved.release * sampleRate,
        )

        return Voice.FilterModulator(
            filter = filter,
            envelope = envelope,
            depth = resolved.depth,
            baseCutoff = baseCutoff,
        )
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
        signal: Exciter,
        freqHz: Double,
        cut: Int? = null,
    ): Voice {
        val envelope = Voice.Envelope.of(resolvedAdsr, sampleRate)
        val endFrame = gateEndFrame + (resolvedAdsr.release * sampleRate).toLong()
        val releaseFrames = (resolvedAdsr.release * sampleRate).toInt()
        val voiceEndFrame = voiceDurationFrames + releaseFrames

        val signalCtx = ExciteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = voiceDurationFrames,
            gateEndFrame = voiceDurationFrames,
            releaseFrames = releaseFrames,
            voiceEndFrame = voiceEndFrame,
            scratchBuffers = scratchBuffers,
        )

        val pipeline = buildPitchPipeline(
            vibrato = vibrato,
            accelerate = accelerate,
            pitchEnvelope = pitchEnvelope,
            fm = fm,
            freqHz = freqHz,
            sampleRate = sampleRate,
            startFrame = startFrame,
            endFrame = endFrame,
            gateEndFrame = gateEndFrame,
        ) + ExciteRenderer(
            signal = signal,
            signalCtx = signalCtx,
            freqHz = freqHz,
            startFrame = startFrame,
        ) + buildFilterPipeline(
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

        val blockCtx = BlockContext(
            audioBuffer = voiceBuffer,
            freqModBuffer = freqModBuffer,
            scratchBuffers = scratchBuffers,
            sampleRate = sampleRate,
            startFrame = startFrame,
            endFrame = endFrame,
            gateEndFrame = gateEndFrame,
            freqHz = freqHz,
            signal = signal,
            signalCtx = signalCtx,
            orbits = orbits,
        )

        return Voice(
            orbitId = orbit,
            startFrame = startFrame,
            endFrame = endFrame,
            gateEndFrame = gateEndFrame,
            gain = gain,
            pan = data.pan ?: 0.5,
            postGain = postGain,
            delay = delay,
            reverb = reverb,
            phaser = phaser,
            ducking = ducking,
            compressor = compressor,
            cut = cut,
            pipeline = pipeline,
            blockCtx = blockCtx,
        )
    }
}
