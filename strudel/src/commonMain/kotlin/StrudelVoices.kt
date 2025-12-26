package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_be.ONE_OVER_TWELVE
import io.peekandpoke.klang.audio_be.Oscillators
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.filters.AudioFilter.Companion.combine
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.voices.SampleVoice
import io.peekandpoke.klang.audio_be.voices.SynthVoice
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.audio_fe.tones.Tones
import io.peekandpoke.klang.audio_fe.utils.MinHeap

class StrudelVoices(
    val options: Options,
) {
    class Options(
        val sampleRate: Int,
        val blockFrames: Int,
        val oscillators: Oscillators,
        val samples: Samples,
        val orbits: Orbits,
    ) {
        val sampleRateDouble = sampleRate.toDouble()
    }

    private val scheduled = MinHeap<StrudelScheduledVoice> { a, b -> a.startFrame < b.startFrame }
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

    fun clear() {
        scheduled.clear()
        active.clear()
    }

    fun schedule(voice: StrudelScheduledVoice) {
        scheduled.push(voice)

        // Prefetch sound samples
        if (voice.evt.isSampleSound) {
            options.samples.prefetch(voice.evt.sampleRequest)
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

    private fun StrudelFilterDef.toFilter(): AudioFilter = when (this) {
        is StrudelFilterDef.LowPass ->
            LowPassHighPassFilters.createLPF(cutoffHz = cutoffHz, q = q, sampleRate = options.sampleRateDouble)

        is StrudelFilterDef.HighPass ->
            LowPassHighPassFilters.createHPF(cutoffHz = cutoffHz, q = q, sampleRate = options.sampleRateDouble)
    }

    private fun makeVoice(scheduled: StrudelScheduledVoice, nowFrame: Long): Voice? {
        val sampleRate = options.sampleRate

        // Bake Filters
        val bakedFilters = scheduled.evt.filters.map { it.toFilter() }.combine()

        // Routing
        val orbit = scheduled.evt.orbit ?: 0

        // Envelope
        val envelope = Voice.Envelope(
            attackFrames = (scheduled.evt.attack ?: 0.01) * sampleRate,
            decayFrames = (scheduled.evt.decay ?: 0.0) * sampleRate,
            sustainLevel = scheduled.evt.sustain ?: 1.0,
            releaseFrames = (scheduled.endFrame - scheduled.gateEndFrame).toDouble()
        )

        // Delay
        val delay = Voice.Delay(
            amount = scheduled.evt.delay ?: 0.0,
            time = scheduled.evt.delayTime ?: 0.0,
            feedback = scheduled.evt.delayFeedback ?: 0.0,
        )

        // Reverb
        val reverb = Voice.Reverb(
            room = scheduled.evt.room ?: 0.0,
            // In Strudel, room size is between [0 and 10], so we need to normalize it
            // See https://strudel.cc/learn/effects/#roomsize
            roomSize = (scheduled.evt.roomsize ?: 0.0) / 10.0,
        )

        // Vibrator
        val vibratoDepth = (scheduled.evt.vibratoMod ?: 0.0) * ONE_OVER_TWELVE
        val vibrator = Voice.Vibrator(
            depth = vibratoDepth,
            rate = if (vibratoDepth > 0.0) scheduled.evt.vibrato ?: 5.0 else 0.0,
        )

        // Effects
        val effects = Voice.Effects(
            distort = scheduled.evt.distort ?: 0.0,
        )

        // Decision
        val note = scheduled.evt.note
        val sound = scheduled.evt.sound
        val isOsci = scheduled.evt.isOscillator && note != null
        val isSample = scheduled.evt.isSampleSound && sound != null

        return when {
            // /////////////////////////////////////////////////////////////////////////////////////////////////////////
            isOsci -> {
                val freqHz = Tones.resolveFreq(note, scheduled.evt.scale)
                val osc = scheduled.evt.createOscillator(oscillators = options.oscillators, freqHz = freqHz)
                val phaseInc = TWO_PI * freqHz / sampleRate.toDouble()

                SynthVoice(
                    orbitId = orbit,
                    startFrame = scheduled.startFrame,
                    endFrame = scheduled.endFrame,
                    gateEndFrame = scheduled.gateEndFrame,
                    gain = scheduled.evt.gain,
                    pan = scheduled.evt.pan ?: 0.0,
                    filter = bakedFilters,
                    envelope = envelope,
                    delay = delay,
                    reverb = reverb,
                    vibrator = vibrator,
                    effects = effects,
                    osc = osc,
                    freqHz = freqHz,
                    phaseInc = phaseInc,
                )
            }

            // /////////////////////////////////////////////////////////////////////////////////////////////////////////
            isSample -> {
                val loaded = options.samples.getIfLoaded(scheduled.evt.sampleRequest) ?: return null
                val (sampleId, decoded) = loaded

                val baseSamplePitchHz = sampleId.sample.pitchHz
                val targetHz = scheduled.evt.note?.let { n -> Tones.resolveFreq(n, scheduled.evt.scale) }
                    ?: baseSamplePitchHz
                val pitchRatio = (targetHz / baseSamplePitchHz).coerceIn(0.125, 8.0)
                val rate = (decoded.sampleRate.toDouble() / sampleRate.toDouble()) * pitchRatio

                val lateFrames = (nowFrame - scheduled.startFrame).coerceAtLeast(0L)
                val playhead0 = lateFrames.toDouble() * rate

                if (decoded.pcm.size <= 1 || playhead0 >= decoded.pcm.size - 1) return null

                SampleVoice(
                    orbitId = orbit,
                    startFrame = nowFrame,
                    endFrame = scheduled.endFrame,
                    gateEndFrame = scheduled.gateEndFrame,
                    gain = scheduled.evt.gain,
                    pan = scheduled.evt.pan ?: 0.0,
                    filter = bakedFilters,
                    envelope = envelope,
                    delay = delay,
                    reverb = reverb,
                    vibrator = vibrator,
                    effects = effects,
                    pcm = decoded.pcm,
                    pcmSampleRate = decoded.sampleRate,
                    rate = rate,
                    playhead = playhead0,
                )
            }

            else -> null
        }
    }
}
