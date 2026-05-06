package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

class JvmAudioBackend(
    config: AudioBackend.Config,
) : AudioBackend {
    private val commLink: KlangCommLink.BackendEndpoint = config.commLink
    private val sampleRate: Int = config.sampleRate
    private val blockSize: Int = config.blockSize

    // KlangTime for timing measurements
    private val klangTime = KlangTime.create()

    // 1. Setup DSP Graph
    val cylinders = Cylinders(blockFrames = blockSize, sampleRate = sampleRate)

    val ignitorRegistry = IgnitorRegistry().apply {
        registerDefaults()
    }

    val voices = VoiceScheduler(
        VoiceScheduler.Options(
            commLink = commLink,
            sampleRate = sampleRate,
            blockFrames = blockSize,
            ignitorRegistry = ignitorRegistry,
            cylinders = cylinders,
            performanceTimeMs = { klangTime.internalMsNow() },
        )
    )

    // 2. Create Renderer
    val renderer = KlangAudioRenderer(
        sampleRate = sampleRate,
        blockFrames = blockSize,
        voices = voices,
        cylinders = cylinders
    )

    override suspend fun run(scope: CoroutineScope) {
        // Set backend start time from KlangTime relative clock
        voices.setBackendStartTime(klangTime.internalMsNow() / 1000.0)

        // Kick off JIT / cache warmup. JVM JIT is warmer than Kotlin/JS but running the same
        // path keeps behavior consistent across targets (and costs ~85 ms of silence at start).
        // Warmup voices run through the real scheduler/renderer; the limiter + scheduler are
        // reset at the end of the handshake so nothing leaks into real playback.
        val warmup = WarmupRunner(
            sampleRate = sampleRate,
            voices = voices,
            renderer = renderer,
            feedback = commLink,
        )
        warmup.start()

        // Int instead of Long: keeps JVM backend consistent with JS (where Long is boxed).
        // At 48kHz, Int overflows after ~12.4 hours — sufficient for any session.
        var currentFrame = 0

        // Stereo
        val format = AudioFormat(sampleRate.toFloat(), 16, 2, true, false)

        // Audio line
        val line: SourceDataLine = AudioSystem.getSourceDataLine(format)

        // println("AudioSystem SourceDataLine: $line ${line.isActive}")

        // Hardware buffer ≈ 250 ms — matches JS `latencyHint = "playback"` profile
        // (prioritise glitch-free playback over minimum latency). Backed by a single
        // pinned audio thread (see klang/.../index_jvm.kt) and natural pacing via
        // line.write(), so no extra coroutine sleep is needed.
        val bufferMs = 250
        val bufferFrames = (sampleRate * bufferMs / 1000.0).toInt()
        // 4 bytes per frame (Stereo 16-bit)
        val bufferBytes = bufferFrames * 4

        line.open(format, bufferBytes)
        line.start()

        // Pre-allocate output buffers
        val outShorts = ShortArray(blockSize * 2)
        val outBytes = ByteArray(blockSize * 4)
        val byteBuffer = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)
        val shortBuffer = byteBuffer.asShortBuffer()

        try {
            while (scope.isActive) {
                // Get events
                while (true) {
                    val cmd = commLink.control.receive() ?: break

                    when (cmd) {
                        is KlangCommLink.Cmd.ScheduleVoice -> {
                            voices.scheduleVoice(voice = cmd.voice, clearScheduled = cmd.clearScheduled)
                        }

                        is KlangCommLink.Cmd.ScheduleVoices -> {
                            voices.scheduleVoices(cmd.voices)
                        }

                        is KlangCommLink.Cmd.ReplaceVoices -> {
                            voices.replaceVoices(cmd.playbackId, cmd.voices, cmd.afterTimeSec)
                        }

                        is KlangCommLink.Cmd.Cleanup -> {
                            voices.cleanup(cmd.playbackId)
                        }

                        is KlangCommLink.Cmd.ClearScheduled -> {
                            voices.clearScheduled(cmd.playbackId)
                        }

                        is KlangCommLink.Cmd.Sample -> voices.addSample(msg = cmd)

                        is KlangCommLink.Cmd.RegisterIgnitor -> {
                            ignitorRegistry.register(cmd.name, cmd.dsl)
                        }
                    }
                }

                // rendering ///////////////////////////////////////////////////////////////////////////////////////
                // Always render — warmup voices live on the real scheduler so this exercises
                // the actual render path for JIT / cache priming.
                renderer.renderBlock(cursorFrame = currentFrame, out = outShorts)

                if (warmup.isWarming) {
                    outShorts.fill(0)
                    warmup.tick()
                }

                // Convert ShortArray to ByteArray efficiently via ByteBuffer
                shortBuffer.clear()
                shortBuffer.put(outShorts)

                // Advance Cursor (State Write)
                currentFrame += blockSize

                // 3. Write to Hardware
                // This call blocks if the hardware buffer is full — that IS the loop
                // pacing. No coroutine sleep needed; an explicit delay() would only
                // add scheduler jitter on top of the already-deterministic backpressure
                // and force a per-block thread reschedule. See `audio/ref/performance.md`.
                line.write(outBytes, 0, outBytes.size)
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
