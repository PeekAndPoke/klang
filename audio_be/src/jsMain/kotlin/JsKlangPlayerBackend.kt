package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.worklet.WorkletContract
import io.peekandpoke.klang.audio_be.worklet.WorkletContract.sendCmd
import io.peekandpoke.klang.audio_bridge.AudioContext
import io.peekandpoke.klang.audio_bridge.AudioContextOptions
import io.peekandpoke.klang.audio_bridge.AudioWorkletNode
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangRingBuffer
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.MessageEvent
import kotlin.js.json

class JsKlangPlayerBackend(
    private val config: KlangPlayerBackend.Config,
) : KlangPlayerBackend {
    private val commLink: KlangCommLink.BackendEndpoint = config.commLink

    private val sampleUploadBuffer = KlangRingBuffer<KlangCommLink.Cmd.Sample.Chunk>(8192 * 4)

    override suspend fun run(scope: CoroutineScope) {
        // Init the audio context with the given sample rate
        val options = json("sampleRate" to config.sampleRate).unsafeCast<AudioContextOptions>()
        val ctx = AudioContext(options)

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
            node = AudioWorkletNode(ctx, "klang-audio-processor")
            node.connect(ctx.destination)

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
                println("loop with requestAnimationFrame() 2")

                // Upload the next sample chunk ... we send them one at a time
                sampleUploadBuffer.receive()?.let { cmd ->
                    node.port.sendCmd(cmd)
                }

                // Drain comm link cmd buffer
                when (val cmd = commLink.control.receive()) {
                    null -> Unit
                    // Direct forwarding
                    is KlangCommLink.Cmd.ScheduleVoice -> node.port.sendCmd(cmd)

                    is KlangCommLink.Cmd.Sample -> when (cmd) {
                        // Direct forwarding
                        is KlangCommLink.Cmd.Sample.NotFound,
                        is KlangCommLink.Cmd.Sample.Chunk,
                            -> node.port.sendCmd(cmd)

                        is KlangCommLink.Cmd.Sample.Complete -> {
                            // Complete samples will be split and put into the [cmdBuffer]
                            val chunks = cmd.toChunks(4 * 1024)

                            chunks.forEach { chunk -> sampleUploadBuffer.send(chunk) }
                        }
                    }
                }

                if (scope.isActive) {
                    window.requestAnimationFrame { loop() }
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
