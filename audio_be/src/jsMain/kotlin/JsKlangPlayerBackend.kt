package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.worklet.WorkletContract
import io.peekandpoke.klang.audio_be.worklet.WorkletContract.sendCmd
import io.peekandpoke.klang.audio_bridge.AudioContext
import io.peekandpoke.klang.audio_bridge.AudioContextOptions
import io.peekandpoke.klang.audio_bridge.AudioWorkletNode
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangRingBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.w3c.dom.MessageEvent
import kotlin.js.json

class JsKlangPlayerBackend(
    private val config: KlangPlayerBackend.Config,
) : KlangPlayerBackend {
    private val commLink: KlangCommLink.BackendEndpoint = config.commLink

    private val cmdBuffer = KlangRingBuffer<KlangCommLink.Cmd>(8192 * 4)

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

            // 5. Stay in the loop and forward all messages from the worklet to the frontend
            while (scope.isActive) {
                // Drain the internal buffer
                while (true) {
                    val cmd = cmdBuffer.receive() ?: break
                    console.log("Forwarded command from internal buffer:", cmd)
                    node.port.sendCmd(cmd)
                    // TODO: use requestAnimation from this buffer ...
                    delay(20)
                }

                // Drain comm link cmd buffer
                while (true) {
                    // We pass all command through to the audio worklet
                    val cmd = commLink.control.receive() ?: break

                    when (cmd) {
                        // Direct forwarding
                        is KlangCommLink.Cmd.ScheduleVoice -> node.port.sendCmd(cmd)

                        is KlangCommLink.Cmd.Sample -> when (cmd) {
                            // Direct forwarding
                            is KlangCommLink.Cmd.Sample.NotFound,
                            is KlangCommLink.Cmd.Sample.Chunk,
                                -> node.port.sendCmd(cmd)

                            is KlangCommLink.Cmd.Sample.Complete -> {
                                // Complete samples will be split and put into the [cmdBuffer]
                                cmd.toChunks(4 * 1024).forEach { chunk -> cmdBuffer.send(chunk) }
                            }
                        }
                    }

                    delay(1)
                    // console.log("Forwarded command to Worklet:", cmd)
                }

                // TODO: we need to keep track of the initial time ... measure how long it took and wait the rest to get to 16.ms
                //  -> write a little helper maybe that can be used in other places as well
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
