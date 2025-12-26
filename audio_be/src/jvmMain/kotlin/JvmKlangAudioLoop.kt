package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.KlangEventReceiver
import io.peekandpoke.klang.audio_bridge.KlangPlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

class JvmKlangAudioLoop<S>(
    private val sampleRate: Int,
    private val blockFrames: Int,
) : KlangAudioLoop<S> {

    override suspend fun runLoop(
        state: KlangPlayerState,
        channel: KlangEventReceiver<S>,
        onSchedule: (S) -> Unit,
        renderBlock: (ByteArray) -> Unit,
    ) {
        // Run on IO dispatcher to avoid blocking the main thread with audio I/O
        withContext(Dispatchers.IO) {
            val format = AudioFormat(sampleRate.toFloat(), 16, 2, true, false)
            val line = AudioSystem.getSourceDataLine(format)

            // Buffer size: 500ms should be safe enough for JVM
            val bufferMs = 500
            val bufferFrames = (sampleRate * bufferMs / 1000.0).toInt()
            // 4 bytes per frame (Stereo 16-bit)
            val bufferBytes = bufferFrames * 4

            line.open(format, bufferBytes)
            line.start()

            // Pre-allocate the output buffer for one block
            val out = ByteArray(blockFrames * 4)

            try {
                while (isActive && state.running()) {
                    // 1. Drain Events from the Receiver
                    while (true) {
                        val evt = channel.receive() ?: break
                        onSchedule(evt)
                    }

                    // 2. Render the Audio Block
                    // This calls back into the engine to mix voices/orbits
                    renderBlock(out)

                    // 3. Write to Hardware
                    // This call blocks if the hardware buffer is full, pacing the loop
                    line.write(out, 0, out.size)
                }
            } finally {
                // Cleanup
                line.drain()
                line.stop()
                line.close()
            }
        }
    }
}
