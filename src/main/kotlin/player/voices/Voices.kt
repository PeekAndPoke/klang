package io.peekandpoke.player.voices

import io.peekandpoke.dsp.AudioFilter
import io.peekandpoke.dsp.ChainAudioFilter
import io.peekandpoke.dsp.NoOpAudioFilter
import io.peekandpoke.player.StrudelPlayer
import io.peekandpoke.player.orbits.Orbits
import io.peekandpoke.tones.StrudelTones
import io.peekandpoke.utils.MinHeap
import io.peekandpoke.utils.Numbers
import io.peekandpoke.utils.Numbers.ONE_OVER_TWELVE

class Voices(
    val options: StrudelPlayer.Options,
    val orbits: Orbits,
) {
    private val scheduled = MinHeap<ScheduledVoice> { a, b -> a.startFrame < b.startFrame }
    private val active = ArrayList<Voice>(64)

    // Scratch buffers
    private val voiceBuffer = DoubleArray(options.blockFrames)
    private val freqModBuffer = DoubleArray(options.blockFrames)

    // Context reused per block
    private val ctx = Voice.RenderContext(
        orbits = orbits,
        sampleRate = options.sampleRate,
        blockFrames = options.blockFrames,
        voiceBuffer = voiceBuffer,
        freqModBuffer = freqModBuffer
    )

    fun clear() {
        scheduled.clear()
        active.clear()
    }

    fun schedule(voice: ScheduledVoice) {
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

    private fun makeVoice(scheduled: ScheduledVoice, nowFrame: Long): Voice? {
        val sampleRate = options.sampleRate

        // Bake Filters
        val bakedFilters = combineFilters(scheduled.evt.filters)

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

        // Decision
        val note = scheduled.evt.note
        val sound = scheduled.evt.sound
        val isOsci = scheduled.evt.isOscillator && note != null
        val isSample = scheduled.evt.isSampleSound && sound != null

        return when {
            // /////////////////////////////////////////////////////////////////////////////////////////////////////////
            isOsci -> {
                val freqHz = StrudelTones.resolveFreq(note, scheduled.evt.scale)
                val osc = options.oscillators.get(e = scheduled.evt, freqHz = freqHz)
                val phaseInc = Numbers.TWO_PI * freqHz / sampleRate.toDouble()

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
                val targetHz = scheduled.evt.note?.let { n -> StrudelTones.resolveFreq(n, scheduled.evt.scale) }
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
                    pcm = decoded.pcm,
                    pcmSampleRate = decoded.sampleRate,
                    rate = rate,
                    playhead = playhead0,
                )
            }

            else -> null
        }
    }

    private fun combineFilters(filters: List<AudioFilter>): AudioFilter {
        if (filters.isEmpty()) return NoOpAudioFilter
        if (filters.size == 1) return filters[0]

        return ChainAudioFilter(filters)
    }
}
