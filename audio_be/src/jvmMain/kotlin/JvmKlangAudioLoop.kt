package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.infra.KlangEventReceiver
import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.time.Duration.Companion.milliseconds

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
        withContext(Dispatchers.IO.limitedParallelism(1)) {
            // Stereo
            val format = AudioFormat(sampleRate.toFloat(), 16, 2, true, false)
            // Audio line
            val line = AudioSystem.getSourceDataLine(format)

            // Buffer size: 500ms should be safe enough for JVM
            val bufferMs = 250
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

                    // Wait a bit
                    delay(10.milliseconds)
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
