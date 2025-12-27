package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.worklet.WorkletContract
import io.peekandpoke.klang.audio_be.worklet.WorkletContract.sendCmd
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState
import kotlinx.coroutines.*
import org.w3c.dom.MessageEvent

class JsKlangPlayerBackend(
    config: KlangPlayerBackend.Config,
) : KlangPlayerBackend {
    private val state: KlangPlayerState = config.state
    private val commLink: KlangCommLink.BackendEndpoint = config.commLink
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

            // 3. Create the Node (this instantiates the Processor in the Audio Thread)
            node = AudioWorkletNode(ctx, "klang-audio-processor")
            node.connect(ctx.destination)

            // 4. Send Command
            if (ctx.state == "suspended") {
                ctx.resume().await()
            }

            // 5. Setup Feedback Loop (Worklet -> Frontend)
            // We listen for messages from the worklet (e.g. Sample Requests, Position Updates)
            node.port.onmessage = { message: MessageEvent ->
                console.log("Received message from Worklet:", message.data)

                val decoded = WorkletContract.decodeFeed(message)

                when (decoded) {
                    is KlangCommLink.Feedback.UpdateCursorFrame -> state.cursorFrame(decoded.frame)
                    else -> Unit
                }
            }

            // 6. Setup Command Loop (Frontend -> Worklet)
            // We poll the ring buffer and forward commands to the worklet
            while (currentCoroutineContext().isActive) {
                // Drain the buffer
                while (true) {
                    // We are the 'BackendEndpoint' here, so we RECEIVE commands
                    val cmd = commLink.control.receive() ?: break

                    console.log("Forwarding command to Worklet:", cmd)

                    node.port.sendCmd(cmd)
                }

                // Yield / throttle to ~60fps poll rate
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
