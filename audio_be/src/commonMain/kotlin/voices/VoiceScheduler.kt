package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.ONE_OVER_TWELVE
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.filters.AudioFilter.Companion.combine
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.osci.OscFn
import io.peekandpoke.klang.audio_be.osci.Oscillators
import io.peekandpoke.klang.audio_bridge.*
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangMinHeap

class VoiceScheduler(
    val options: Options,
) {
    class Options(
        val commLink: KlangCommLink.BackendEndpoint,
        val sampleRate: Int,
        val blockFrames: Int,
        val oscillators: Oscillators,
//        val samples: Samples,
        val orbits: Orbits,
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

    private val samples = mutableMapOf<SampleRequest, SampleEntry>()

    private val scheduled = KlangMinHeap<ScheduledVoice> { a, b -> a.startFrame < b.startFrame }
    private val active = ArrayList<Voice>(64)

    // Scratch buffers
    private val voiceBuffer = DoubleArray(options.blockFrames)
    private val freqModBuffer = DoubleArray(options.blockFrames)

    // Context reused per block
    private val ctx = Voice.RenderContext(
        orbits = options.orbits,
        sampleRate = options.sampleRate,
        blockFrames = options.blockFrames,
        voiceBuffer = voiceBuffer,
        freqModBuffer = freqModBuffer
    )

    fun VoiceData.isOscillator() = options.oscillators.isOsc(sound)

    fun VoiceData.isSampleSound() = !isOscillator()

    fun VoiceData.createOscillator(oscillators: Oscillators, freqHz: Double): OscFn {
        val e = this

        return oscillators.get(
            name = e.sound,
            freqHz = freqHz,
            density = e.density,
            unison = e.unison,
            detune = e.detune,
            spread = e.spread,
        )
    }

    fun clear() {
        scheduled.clear()
        active.clear()
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
                    sample = MonoSamplePcm(
                        sampleRate = msg.sampleRate,
                        pcm = msg.pcm,
                    ),
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
                    SampleEntry.Complete(
                        req = req,
                        note = entry.note,
                        pitchHz = entry.pitchHz,
                        sample = entry.sample,
                    )
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

    fun scheduleVoice(voice: ScheduledVoice) {
        scheduled.push(voice)

        // Prefetch sound samples
        if (voice.data.isSampleSound()) {
            val req = voice.data.asSampleRequest()

            if (!samples.containsKey(req)) {
                // make sure we do not request this one again
                samples[req] = SampleEntry.Requested(req)

                // println("VoiceScheduler: requesting sample ${voice.data.asSampleRequest()}")
                options.commLink.feedback.send(
                    KlangCommLink.Feedback.RequestSample(voice.data.asSampleRequest())
                )
            }
        }
    }

    fun process(cursorFrame: Long) {
        val blockEnd = cursorFrame + options.blockFrames

        // 1. Promote scheduled to active
        promoteScheduled(cursorFrame, blockEnd)

        // 2. Prepare Context
        ctx.blockStart = cursorFrame

        // 3. Render Loop
        var i = 0
        while (i < active.size) {
            val voice = active[i]

            // Delegate logic to the voice itself
            val isAlive = voice.render(ctx)

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
    }

    private fun promoteScheduled(nowFrame: Long, blockEnd: Long) {
        while (true) {
            val head = scheduled.peek() ?: break
            if (head.startFrame >= blockEnd) break
            scheduled.pop()

            if (head.endFrame <= nowFrame) continue

            makeVoice(head, nowFrame)?.let { active.add(it) }
        }
    }

    private fun FilterDef.toFilter(): AudioFilter = when (this) {
        is FilterDef.LowPass ->
            LowPassHighPassFilters.createLPF(cutoffHz = cutoffHz, q = q, sampleRate = options.sampleRateDouble)

        is FilterDef.HighPass ->
            LowPassHighPassFilters.createHPF(cutoffHz = cutoffHz, q = q, sampleRate = options.sampleRateDouble)
    }

    private fun makeVoice(scheduled: ScheduledVoice, nowFrame: Long): Voice? {
        val sampleRate = options.sampleRate
        val data = scheduled.data

        // Bake Filters
        val bakedFilters = data.filters.map { it.toFilter() }.combine()

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

        // Envelope
        val envelope = Voice.Envelope(
            attackFrames = (data.attack ?: 0.01) * sampleRate,
            decayFrames = (data.decay ?: 0.0) * sampleRate,
            sustainLevel = data.sustain ?: 1.0,
            releaseFrames = (scheduled.endFrame - scheduled.gateEndFrame).toDouble()
        )

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
            roomSize = (data.roomsize ?: 0.0) / 10.0,
        )

        // Effects
        val distort = Voice.Distort(amount = data.distort ?: 0.0)
        val crush = Voice.Crush(amount = data.crush ?: 0.0)
        val coarse = Voice.Coarse(amount = data.coarse ?: 0.0)

        // Decision
        val freqHz = data.freqHz
        val sound = data.sound
        val isOsci = data.isOscillator() && freqHz != null
        val isSample = data.isSampleSound() && sound != null

        return when {
            // /////////////////////////////////////////////////////////////////////////////////////////////////////////
            isOsci -> {
                val osc = data.createOscillator(oscillators = options.oscillators, freqHz = freqHz)
                val phaseInc = TWO_PI * freqHz / sampleRate.toDouble()

                SynthVoice(
                    orbitId = orbit,
                    startFrame = scheduled.startFrame,
                    endFrame = scheduled.endFrame,
                    gateEndFrame = scheduled.gateEndFrame,
                    gain = data.gain,
                    pan = data.pan ?: 0.0,
                    accelerate = accelerate,
                    vibrato = vibrato,
                    filter = bakedFilters,
                    envelope = envelope,
                    delay = delay,
                    reverb = reverb,
                    distort = distort,
                    crush = crush,
                    coarse = coarse,
                    osc = osc,
                    freqHz = freqHz,
                    phaseInc = phaseInc,
                )
            }

            // /////////////////////////////////////////////////////////////////////////////////////////////////////////
            isSample -> {
                // Did we already request this sample?
                val sampleRequest = data.asSampleRequest()

                // Do we have the data for this sample?
                val entry = getCompleteSample(sampleRequest) ?: return null
                val sample = entry.sample

                val baseSamplePitchHz = entry.pitchHz
                val targetPitchHz = data.freqHz ?: baseSamplePitchHz
                // We allow 5 octaves up and down pitch 1/32 .. 32
                val pitchRatio = (targetPitchHz / baseSamplePitchHz).coerceIn(1.0 / 32.0, 32.0)
                val rate = (sample.sampleRate.toDouble() / sampleRate.toDouble()) * pitchRatio

                val lateFrames = (nowFrame - scheduled.startFrame).coerceAtLeast(0L)
                val playhead0 = lateFrames.toDouble() * rate

                if (sample.pcm.size <= 1 || playhead0 >= sample.pcm.size - 1) return null

                SampleVoice(
                    orbitId = orbit,
                    startFrame = nowFrame,
                    endFrame = scheduled.endFrame,
                    gateEndFrame = scheduled.gateEndFrame,
                    gain = data.gain,
                    pan = data.pan ?: 0.0,
                    filter = bakedFilters,
                    accelerate = accelerate,
                    vibrato = vibrato,
                    envelope = envelope,
                    delay = delay,
                    reverb = reverb,
                    distort = distort,
                    crush = crush,
                    coarse = coarse,
                    sample = sample,
                    rate = rate,
                    playhead = playhead0,
                )
            }

            else -> null
        }
    }
}
