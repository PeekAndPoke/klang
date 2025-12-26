package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.orbits.Orbits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.tanh

class StrudelAudioLoop(
    private val options: StrudelPlayer.Options,
    private val state: StrudelPlayerState,
    private val eventChannel: ReceiveChannel<StrudelScheduledVoice>,
    private val voices: StrudelVoices,
    private val orbits: Orbits,
) {
    private val masterMix = StereoBuffer(options.blockFrames)

    fun runLoop(scope: CoroutineScope) {
        val format = AudioFormat(options.sampleRate.toFloat(), 16, 2, true, false)
        val line = AudioSystem.getSourceDataLine(format)

        val bufferMs = 500
        val bufferFrames = (options.sampleRate * bufferMs / 1000.0).toInt()
        line.open(format, bufferFrames * 4)
        line.start()

        val out = ByteArray(options.blockFrames * 4)

        try {
            while (scope.isActive) {
                // Drain events
                while (true) {
                    val evt = eventChannel.tryReceive().getOrNull() ?: break
                    voices.schedule(evt)
                }

                renderBlockInto(out)
                line.write(out, 0, out.size)
            }
        } finally {
            line.drain()
            line.stop()
            line.close()
        }
    }

    private fun renderBlockInto(out: ByteArray) {
        val blockStart = state.cursorFrame.value
        masterMix.clear()
        orbits.clearAll()

        voices.process(blockStart)
        orbits.processAndMix(masterMix)

        val masterMixL = masterMix.left
        val masterMixR = masterMix.right

        for (i in 0 until options.blockFrames) {
            val pcmL = (tanh(masterMixL[i]).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()
            val pcmR = (tanh(masterMixR[i]).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()

            val baseIdx = i * 4
            out[baseIdx] = (pcmL and 0xff).toByte()
            out[baseIdx + 1] = ((pcmL ushr 8) and 0xff).toByte()
            out[baseIdx + 2] = (pcmR and 0xff).toByte()
            out[baseIdx + 3] = ((pcmR ushr 8) and 0xff).toByte()
        }

        state.cursorFrame.value = blockStart + options.blockFrames
    }
}
