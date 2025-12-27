package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.worklet.WorkletContract
import io.peekandpoke.klang.audio_be.worklet.WorkletContract.sendCmd
import io.peekandpoke.klang.audio_bridge.AudioContext
import io.peekandpoke.klang.audio_bridge.AudioWorkletNode
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.w3c.dom.MessageEvent

class JsKlangPlayerBackend(
    config: KlangPlayerBackend.Config,
) : KlangPlayerBackend {
    private val commLink: KlangCommLink.BackendEndpoint = config.commLink

    // TODO: init worklet with sample rate
    private val sampleRate: Int = config.sampleRate
    private val blockSize: Int = config.blockSize

    override suspend fun run(scope: CoroutineScope) {
        val ctx = AudioContext()

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

            // 5. Stay in the loop and forward all messages from the worklet to the frontend
            while (scope.isActive) {
                // Drain the buffer
                while (true) {
                    // We pass all command through to the audio worklet
                    val cmd = commLink.control.receive() ?: break
                    // Forward
                    node.port.sendCmd(cmd)

                    // console.log("Forwarded command to Worklet:", cmd)
                }

                // 60 FPS
                delay(16)
            }
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
