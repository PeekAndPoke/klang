package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer

/**
 * Ignitor that plays back pre-recorded PCM audio samples.
 *
 * Wraps sample playback (interpolation, looping, stop frame) as a composable
 * [Ignitor] source, enabling samples to flow through the same signal chain
 * as synthesized oscillators.
 *
 * When [analog] > 0, applies Perlin noise drift to the playhead advance rate,
 * simulating wow & flutter from vinyl records and tape machines.
 *
 * Pitch is controlled via [rate] and
 * [IgniteContext.phaseMod], consistent with how noise generators ignore freqHz.
 */
class SampleIgnitor(
    private val pcm: DoubleArray,
    private val rate: Double,
    private var playhead: Double,
    private val loopStart: Double,
    private val loopEnd: Double,
    private val isLooping: Boolean,
    private val stopFrame: Double,
    analog: Double = 0.0,
) : Ignitor {

    private val drift = AnalogDrift(analog)
    private val loopLength = if (isLooping && loopEnd > loopStart) loopEnd - loopStart else 0.0

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val pcmMax = pcm.size - 1
        val phaseMod = ctx.phaseMod

        var ph = playhead

        if (drift.active) {
            // Analog drift path: wow & flutter on playback rate
            for (i in 0 until ctx.length) {
                val idxOut = ctx.offset + i

                if (isLooping && loopLength > 0.0 && ph >= loopEnd) {
                    val offset = (ph - loopStart) % loopLength
                    ph = loopStart + if (offset < 0.0) offset + loopLength else offset
                }

                if (ph < 0.0 || ph >= stopFrame) {
                    buffer[idxOut] = 0.0
                } else {
                    val base = ph.toInt()
                    if (base >= pcmMax) {
                        buffer[idxOut] = 0.0
                    } else {
                        val frac = ph - base.toDouble()
                        val a = pcm[base]
                        val b = pcm[base + 1]
                        buffer[idxOut] = (a + (b - a) * frac)
                    }
                }

                val driftMult = drift.nextMultiplier()
                ph += if (phaseMod != null) rate * phaseMod[idxOut] * driftMult else rate * driftMult
            }
        } else {
            // Clean digital path (unchanged)
            for (i in 0 until ctx.length) {
                val idxOut = ctx.offset + i

                if (isLooping && loopLength > 0.0 && ph >= loopEnd) {
                    val offset = (ph - loopStart) % loopLength
                    ph = loopStart + if (offset < 0.0) offset + loopLength else offset
                }

                if (ph < 0.0 || ph >= stopFrame) {
                    buffer[idxOut] = 0.0
                } else {
                    val base = ph.toInt()
                    if (base >= pcmMax) {
                        buffer[idxOut] = 0.0
                    } else {
                        val frac = ph - base.toDouble()
                        val a = pcm[base]
                        val b = pcm[base + 1]
                        buffer[idxOut] = (a + (b - a) * frac)
                    }
                }

                ph += if (phaseMod != null) rate * phaseMod[idxOut] else rate
            }
        }

        playhead = ph
    }
}
