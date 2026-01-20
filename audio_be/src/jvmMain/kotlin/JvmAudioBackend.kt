package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.osci.oscillators
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.time.Duration.Companion.milliseconds

class JvmAudioBackend(
    config: AudioBackend.Config,
) : AudioBackend {
    private val commLink: KlangCommLink.BackendEndpoint = config.commLink
    private val sampleRate: Int = config.sampleRate
    private val blockSize: Int = config.blockSize

    // 1. Setup DSP Graph
    val orbits = Orbits(
        maxOrbits = 16,
        blockFrames = blockSize,
        sampleRate = sampleRate
    )

    val voices = VoiceScheduler(
        VoiceScheduler.Options(
            commLink = commLink,
            sampleRate = sampleRate,
            blockFrames = blockSize,
            oscillators = oscillators(sampleRate),
            orbits = orbits,
        )
    )

    // 2. Create Renderer
    val renderer = KlangAudioRenderer(
        blockFrames = blockSize,
        voices = voices,
        orbits = orbits
    )

    override suspend fun run(scope: CoroutineScope) {
        // Set backend start time from KlangTime relative clock
        val klangTime = KlangTime.create()
        voices.setBackendStartTime(klangTime.internalMsNow() / 1000.0)

        var currentFrame = 0L

        // Stereo
        val format = AudioFormat(sampleRate.toFloat(), 16, 2, true, false)

        // Audio line
        val line: SourceDataLine = AudioSystem.getSourceDataLine(format)

        // println("AudioSystem SourceDataLine: $line ${line.isActive}")

        // Buffer size: 500ms should be safe enough for JVM
        val bufferMs = 250
        val bufferFrames = (sampleRate * bufferMs / 1000.0).toInt()
        // 4 bytes per frame (Stereo 16-bit)
        val bufferBytes = bufferFrames * 4

        line.open(format, bufferBytes)
        line.start()

        // Pre-allocate the output buffer for one block
        val out = ByteArray(blockSize * 4)

        try {
            while (scope.isActive) {
                // Get events
                while (true) {
                    val cmd = commLink.control.receive() ?: break

                    when (cmd) {
                        is KlangCommLink.Cmd.ScheduleVoice -> {
                            voices.scheduleVoice(
                                playbackId = cmd.playbackId,
                                voice = cmd.voice,
                            )
                        }

                        is KlangCommLink.Cmd.Sample -> voices.addSample(msg = cmd)
                    }
                }

                // rendering ///////////////////////////////////////////////////////////////////////////////////////
                // Render into buffer (State Read)
                renderer.renderBlock(cursorFrame = currentFrame, out = out)

                // Advance Cursor (State Write)
                currentFrame += blockSize

                // 3. Write to Hardware
                // This call blocks if the hardware buffer is full, pacing the loop
                line.write(out, 0, out.size)

                // 60 FPS
                delay(10.milliseconds)
            }
        } finally {
            // Cleanup
            line.drain()
            line.stop()
            line.close()
        }

        println("KlangPlayerBackend stopped")
    }
}

