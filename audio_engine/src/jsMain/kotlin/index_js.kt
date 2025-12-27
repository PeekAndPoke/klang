package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.MessageEvent
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise
import kotlin.js.json

actual fun createDefaultAudioLoop(
    options: KlangPlayer.Options,
): suspend (KlangPlayerState, KlangCommLink.BackendEndpoint) -> Unit = { state, commLink ->

    val ctx = AudioContext()

    // 1. Resume Audio Context (Browser policy usually requires this on interaction)
    if (ctx.state == "suspended") {
        ctx.resume().await()
    }

    lateinit var node: AudioWorkletNode

    try {
        // 2. Load the compiled DSP module
        // This file "dsp.js" must contain the AudioWorkletProcessor registration
        ctx.audioWorklet.addModule("dsp.js").await()

        // 3. Create the Node (this instantiates the Processor in the Audio Thread)
        node = AudioWorkletNode(ctx, "klang-audio-processor")
        node.connect(ctx.destination)

        // 4. Initialize the Processor
        // We send the configuration so the processor knows how to setup the backend
        node.port.postMessage(
            json(
                "type" to "INIT",
                "sampleRate" to options.sampleRate,
                "blockFrames" to options.blockSize
            )
        )

        // 5. Setup Feedback Loop (Worklet -> Frontend)
        // We listen for messages from the worklet (e.g. Sample Requests, Position Updates)
        node.port.onmessage = { event: MessageEvent ->
            val data = event.data

            // Check for internal messages like position updates
            val type = data.asDynamic().type
            if (type == "POSITION") {
                val frame = data.asDynamic().frame as Double
                state.cursorFrame(frame.toLong())
            } else {
                // Otherwise, treat it as a Feedback object for the CommLink
                // Note: In a real app, you might need explicit JSON serialization here
                // if the objects are not simple JS objects.
                @Suppress("UNCHECKED_CAST")
                val feedback = data as? KlangCommLink.Feedback
                if (feedback != null) {
                    commLink.feedback.dispatch(feedback)
                }
            }
        }

        // 6. Setup Command Loop (Frontend -> Worklet)
        // We poll the ring buffer and forward commands to the worklet
        while (currentCoroutineContext().isActive) {
            // Drain the buffer
            while (true) {
                // We are the 'BackendEndpoint' here, so we RECEIVE commands
                val cmd = commLink.control.receive() ?: break

                // Forward to Worklet
                // We wrap it to distinguish from INIT/Control messages if needed,
                // or send directly if the Worklet handles it.
                node.port.postMessage(cmd)
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

// --- Helper extensions ---

suspend fun <T> Promise<T>.await(): T = suspendCancellableCoroutine { cont ->
    then(
        { cont.resume(it) },
        { cont.resumeWithException(it) },
    )
}
