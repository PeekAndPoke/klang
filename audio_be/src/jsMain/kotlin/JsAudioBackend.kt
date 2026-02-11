package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.worklet.WorkletContract
import io.peekandpoke.klang.audio_be.worklet.WorkletContract.sendCmd
import io.peekandpoke.klang.audio_bridge.*
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangRingBuffer
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.MessageEvent

class JsAudioBackend(
    private val config: AudioBackend.Config,
) : AudioBackend {
    private val commLink: KlangCommLink.BackendEndpoint = config.commLink

    private val sampleUploadBuffer = KlangRingBuffer<KlangCommLink.Cmd.Sample.Chunk>(8192 * 4)

    // AnalyserNode for visualization
    private var analyser: AnalyserNode? = null

    // Implement AudioVisualizer interface with zero-copy methods
    override val visualizer = object : AudioVisualizer {
        override val fftSize: Int = 2048

        override fun getWaveform(out: VisualizerBuffer) {
            // Zero-copy fill on JS (VisualizerBuffer is Float32Array)
            analyser?.getFloatTimeDomainData(out)
        }

        override fun getFft(out: VisualizerBuffer) {
            // Zero-copy fill on JS
            analyser?.getFloatFrequencyData(out)
        }
    }

    override suspend fun run(scope: CoroutineScope) {
        // Init the audio context with the given sample rate
        // latencyHint="playback" prioritizes glitch-free audio with larger buffers
        // This provides more headroom to prevent buffer starvation during CPU spikes or GC pauses
        val contextOpts = jsObject<AudioContextOptions> {
            sampleRate = config.sampleRate
            latencyHint = "playback"  // Prioritize stable, glitch-free playback over minimal latency
        }
        val ctx = AudioContext(contextOpts)

        // 1. Resume Audio Context (Browser policy usually requires this on interaction)
        if (ctx.state == "suspended") {
            ctx.resume().await()
        }

        lateinit var node: AudioWorkletNode

        try {
            // 2. Load the compiled DSP module
            // This file "dsp.js" must contain the AudioWorkletProcessor registration
            ctx.audioWorklet.addModule("klang-worklet.js").await()

            // 2. Create the Node (this instantiates the Processor in the Audio Thread)
            // We need to explicitly request 2 output channels, otherwise it defaults to 1 (Mono)
            val nodeOpts = jsObject<AudioWorkletNodeOptions> {
                outputChannelCount = arrayOf(2)
            }

            node = AudioWorkletNode(ctx, "klang-audio-processor", nodeOpts)

            // Create AnalyserNode for visualization (with fallback if it fails)
            try {
                analyser = ctx.createAnalyser().apply {
                    fftSize = 2048
                    smoothingTimeConstant = 0.8
                }
                console.log("AnalyserNode created successfully for visualization")
            } catch (e: Throwable) {
                console.warn("Failed to create AnalyserNode, visualization disabled:", e)
                analyser = null
            }

            // Connect audio graph: Worklet → Analyser → Destination (or direct if no analyser)
            when (val ana = analyser) {
                null -> {
                    node.connect(ctx.destination)
                }

                else -> {
                    node.connect(ana)
                    ana.connect(ctx.destination)
                }
            }

            // 3. Send Command
            if (ctx.state == "suspended") {
                ctx.resume().await()
            }

            // 4. Setup Feedback Loop (Worklet -> Frontend)
            // We listen for messages from the worklet (e.g. Sample Requests, Position Updates)
            node.port.onmessage = { message: MessageEvent ->
                // We pass all feedback through to the frontend
                val decoded = WorkletContract.decodeFeed(message)

                // Forward
                commLink.feedback.send(decoded)

                // console.log("Forwarded message from Worklet:", message.data)
            }

            fun loop() {
                // Upload the next sample chunk ... we send them one at a time
                sampleUploadBuffer.receive()?.let { cmd ->
                    node.port.sendCmd(cmd)
                }

                // Drain comm link cmd buffer
                while (true) {
                    val cmd = commLink.control.receive() ?: break

                    when (cmd) {
                        // Special handling for Samples ... we split the data for big samples
                        is KlangCommLink.Cmd.Sample -> when (cmd) {
                            // Direct forwarding
                            is KlangCommLink.Cmd.Sample.NotFound,
                            is KlangCommLink.Cmd.Sample.Chunk,
                                -> node.port.sendCmd(cmd)

                            is KlangCommLink.Cmd.Sample.Complete -> {
                                // Complete samples will be split and put into the [cmdBuffer]
                                val chunks = cmd.toChunks(32 * 1024)

                                chunks.forEach { chunk -> sampleUploadBuffer.send(chunk) }
                            }
                        }

                        // Direct forwarding for control commands
                        is KlangCommLink.Cmd.Cleanup,
                        is KlangCommLink.Cmd.ClearScheduled,
                        is KlangCommLink.Cmd.ScheduleVoice,
                            -> node.port.sendCmd(cmd)
                    }
                }

                if (scope.isActive) {
                    window.setTimeout({ loop() }, 10)
                }
            }

            // Start the loop
            loop()

            // Keep the coroutine alive
            suspendCancellableCoroutine { }
        } catch (e: Throwable) {
            console.error("AudioWorklet Error:", e)
            throw e
        } finally {
            // Cleanup when the coroutine is cancelled (Player.stop())
            try {
                node.disconnect()
                ctx.close().await()
            } catch (e: Throwable) {
                console.error("Error closing AudioContext", e)
            }
        }
    }
}
