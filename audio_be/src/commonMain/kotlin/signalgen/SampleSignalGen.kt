package io.peekandpoke.klang.audio_be.signalgen

/**
 * SignalGen that plays back pre-recorded PCM audio samples.
 *
 * Wraps sample playback (interpolation, looping, stop frame) as a composable
 * [SignalGen] source, enabling samples to flow through the same signal chain
 * as synthesized oscillators.
 *
 * The [freqHz] parameter is ignored — pitch is controlled via [rate] and
 * [SignalContext.phaseMod], consistent with how noise generators ignore freqHz.
 */
class SampleSignalGen(
    private val pcm: FloatArray,
    private val rate: Double,
    private var playhead: Double,
    private val loopStart: Double,
    private val loopEnd: Double,
    private val isLooping: Boolean,
    private val stopFrame: Double,
) : SignalGen {

    private val loopLength = if (isLooping && loopEnd > loopStart) loopEnd - loopStart else 0.0

    override fun generate(buffer: FloatArray, freqHz: Double, ctx: SignalContext) {
        val pcmMax = pcm.size - 1
        val phaseMod = ctx.phaseMod

        var ph = playhead

        for (i in 0 until ctx.length) {
            val idxOut = ctx.offset + i

            // Loop wrap — handles overshoots larger than one loop length
            if (isLooping && loopLength > 0.0 && ph >= loopEnd) {
                val offset = (ph - loopStart) % loopLength
                ph = loopStart + if (offset < 0.0) offset + loopLength else offset
            }

            // Check strict end
            if (ph < 0.0 || ph >= stopFrame) {
                buffer[idxOut] = 0.0f
            } else {
                // Read sample with linear interpolation
                val base = ph.toInt()

                if (base >= pcmMax) {
                    buffer[idxOut] = 0.0f
                } else {
                    val frac = ph - base.toDouble()
                    val a = pcm[base]
                    val b = pcm[base + 1]
                    buffer[idxOut] = (a + (b - a) * frac).toFloat()
                }
            }

            // Advance playhead with pitch modulation
            ph += if (phaseMod != null) rate * phaseMod[idxOut] else rate
        }

        playhead = ph
    }
}
