package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import kotlin.math.tanh

/**
 * Standard renderer that drives the DSP graph (VoiceScheduler -> Orbits -> Stereo Output).
 *
 * This class is stateless regarding the playback cursor. It simply renders "what is requested".
 */
class KlangAudioRenderer(
    private val blockFrames: Int,
    private val voices: VoiceScheduler,
    private val orbits: Orbits,
) {
    private val mix = StereoBuffer(blockFrames)

    /**
     * Renders one block of audio starting at [cursorFrame] into the [out] byte array.
     */
    fun renderBlock(
        cursorFrame: Long,
        out: ByteArray,
    ) {
        // 1. Reset Mix Buffers
        mix.clear()
        orbits.clearAll()

        // 2. Process Voices (Oscillators, Samples, Envelopes)
        voices.process(cursorFrame)

        // 3. Mix Voices into Orbits -> Main Mix
        orbits.processAndMix(mix)

        // 4. Post-Process (Limiter + Interleave to 16-bit PCM)
        val left = mix.left
        val right = mix.right

        for (i in 0 until blockFrames) {
            // Hard Limiter (tanh)
            val l = (tanh(left[i]).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()
            val r = (tanh(right[i]).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()

            // Interleave L/R into Byte Array (Little Endian)
            val idx = i * 4
            out[idx] = (l and 0xff).toByte()
            out[idx + 1] = ((l ushr 8) and 0xff).toByte()
            out[idx + 2] = (r and 0xff).toByte()
            out[idx + 3] = ((r ushr 8) and 0xff).toByte()
        }
    }
}
