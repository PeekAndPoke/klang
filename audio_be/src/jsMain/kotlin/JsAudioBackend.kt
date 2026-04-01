package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.WorkletContract.sendCmd
import io.peekandpoke.klang.audio_bridge.AnalyserNode
import io.peekandpoke.klang.audio_bridge.AudioContext
import io.peekandpoke.klang.audio_bridge.AudioContextOptions
import io.peekandpoke.klang.audio_bridge.AudioWorkletNode
import io.peekandpoke.klang.audio_bridge.AudioWorkletNodeOptions
import io.peekandpoke.klang.audio_bridge.guessDeviceLatencyMs
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.common.infra.KlangRingBuffer
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.MessageEvent
import org.w3c.dom.get

class JsAudioBackend(
    private val config: AudioBackend.Config,
) : AudioBackend {
    private val commLink: KlangCommLink.BackendEndpoint = config.commLink

    private val sampleUploadBuffer = KlangRingBuffer<KlangCommLink.Cmd.Sample.Chunk>(8192 * 4)

    // AnalyserNode for visualization
    private var analyserNode: AnalyserNode? = null

    // Implement AudioVisualizer interface with zero-copy methods
    override val analyzer = JsAudioAnalyzer { analyserNode }

    override suspend fun run(scope: CoroutineScope) {
        console.log("JsAudioBackend starting")

        // Init the audio context with the given sample rate
        // latencyHint="playback" prioritizes glitch-free audio with larger buffers
        // This provides more headroom to prevent buffer starvation during CPU spikes or GC pauses
        val contextOpts = jsObject<AudioContextOptions> {
            sampleRate = config.sampleRate
            latencyHint = "playback"  // Prioritize stable, glitch-free playback over minimal latency
        }
        val ctx = AudioContext(contextOpts)

        console.log("JsAudioBackend AudioContext created successfully", ctx)

        // 1. Audio Context State
        // Note: We intentionally do NOT call ctx.resume() here because:
        // - Safari's resume() can hang indefinitely without user gesture
        // - Modern browsers auto-resume AudioContext on first audio playback
        // - This allows the app to initialize without waiting for user interaction
        console.log("JsAudioBackend AudioContext state: ${ctx.state}")

        if (ctx.state == "suspended") {
            console.log("AudioContext is suspended - this is normal before user interaction")
            console.log("It will auto-resume when audio playback starts")
        }

        lateinit var node: AudioWorkletNode

        try {
            console.log("JsAudioBackend loading worklet")

            // 2. Load the audio worklet module with cache busting
            val cacheBusterHash = getCacheBusterHash()
            val uri = when {
                cacheBusterHash.isBlank() -> "/klang-worklet.js"
                else -> "/klang-worklet.js?c=$cacheBusterHash"
            }
            ctx.audioWorklet.addModule(uri).await()

            // 2. Create the Node (this instantiates the Processor in the Audio Thread)
            // We need to explicitly request 2 output channels, otherwise it defaults to 1 (Mono)
            val nodeOpts = jsObject<AudioWorkletNodeOptions> {
                outputChannelCount = arrayOf(2)
            }

            node = AudioWorkletNode(ctx, "klang-audio-processor", nodeOpts)

            console.log("JsAudioBackend AudioWorkletNode created successfully", node)

            // Create AnalyserNode for visualization (with fallback if it fails)
            try {
                console.log("Creating AnalyserNode")
                analyserNode = ctx.createAnalyser().apply {
                    fftSize = 2048
                    smoothingTimeConstant = 0.8
                }
                console.log("AnalyserNode created successfully for visualization")
            } catch (e: Throwable) {
                console.warn("Failed to create AnalyserNode, visualization disabled:", e)
                analyserNode = null
            }

            // Connect audio graph: Worklet → Analyser → Destination (or direct if no analyser)
            when (val ana = analyserNode) {
                null -> {
                    node.connect(ctx.destination)
                }

                else -> {
                    node.connect(ana)
                    ana.connect(ctx.destination)
                }
            }

            // 3. Setup Feedback Loop (Worklet -> Frontend)
            // We listen for messages from the worklet (e.g. Sample Requests, Position Updates)
            node.port.onmessage = { message: MessageEvent ->
                // We pass all feedback through to the frontend
                val decoded = WorkletContract.decodeFeed(message)

                // Augment Diagnostics with real output latency from the actual AudioContext.
                // The worklet has no access to AudioContext properties, so we enrich here.
                val enriched = if (decoded is KlangCommLink.Feedback.Diagnostics) {
                    val baseLat = ctx.baseLatency * 1000.0
                    val deviceLat = guessDeviceLatencyMs(ctx.outputLatency * 1000.0)
                    decoded.copy(
                        baseLatencyMs = baseLat,
                        outputDeviceLatencyMs = deviceLat,
                        outputLatencyMs = baseLat + deviceLat,
                    )
                } else {
                    decoded
                }

                // Forward
                commLink.feedback.send(enriched)

                // console.log("JsAudioBackend received message from Worklet:", decoded::class.simpleName)
            }

            // CRITICAL: Start the MessagePort (required for Safari)
            // Safari requires explicit port.start() to activate MessagePort communication
            console.log("JsAudioBackend starting MessagePort")
            node.port.start()
            console.log("JsAudioBackend MessagePort started")

            fun loop() {
                // console.log("JsAudioBackend running feedback loop")

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
                        is KlangCommLink.Cmd.RegisterIgnitor,
                        is KlangCommLink.Cmd.ReplaceVoices,
                        is KlangCommLink.Cmd.ScheduleVoice,
                            -> node.port.sendCmd(cmd)
                    }
                }

                if (scope.isActive) {
                    window.setTimeout({ loop() }, 10)
                }
            }

            console.log("JsAudioBackend starting feedback loop")

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

    /**
     * Calculates the cache buster hash for the current script.
     */
    private fun getCacheBusterHash(): String {
        var hash = ""
        val scripts = window.document.scripts
        for (i in 0 until scripts.length) {
            val src = (scripts[i] as? org.w3c.dom.HTMLScriptElement)?.src ?: continue
            // Look for a typical bundler hash: a dot, followed by at least 8 hex characters, ending in .js
            val match = Regex("""\.([a-fA-F0-9]{8,})\.js$""").find(src)
            if (match != null) {
                hash = match.groupValues[1]
                break
            }
        }

        return hash
    }
}
