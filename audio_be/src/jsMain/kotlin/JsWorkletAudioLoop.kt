package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.time.Duration.Companion.milliseconds

/**
 * Creates the passive driver for the JS AudioWorklet environment.
 */
actual fun createAudioLoop(sampleRate: Int, blockFrames: Int): KlangAudioLoop =
    JsWorkletAudioLoop(sampleRate, blockFrames)

/**
 * A passive audio loop that waits for [process] calls from the browser's AudioWorkletProcessor.
 */
class JsWorkletAudioLoop(
    val sampleRate: Int,
    val blockFrames: Int,
) : KlangAudioLoop {

    // Callbacks provided by the engine (KlangAudioBackend)
    private var renderBlockCallback: ((ByteArray) -> Unit)? = null
    private var onCommandCallback: ((KlangCommLink.Cmd) -> Unit)? = null

    /**
     * Called by KlangAudioBackend.run().
     * Instead of looping, we just register the callbacks and suspend forever.
     */
    override suspend fun runLoop(
        state: KlangPlayerState,
        commLink: KlangCommLink.BackendEndpoint,
        onCommand: (KlangCommLink.Cmd) -> Unit,
        renderBlock: (ByteArray) -> Unit,
    ) {
        this.renderBlockCallback = renderBlock
        this.onCommandCallback = onCommand

        while (state.running()) {
            delay(10.milliseconds)
        }

        // Keep the coroutine alive so the backend doesn't exit.
        // Replaces awaitCancellation()
        suspendCancellableCoroutine<Unit> { }
    }

    /**
     * To be called by the AudioWorkletProcessor inside process().
     * Triggers the engine to render one block.
     */
    fun process(outBuffer: ByteArray) {
        renderBlockCallback?.invoke(outBuffer)
    }

    /**
     * To be called by the AudioWorkletProcessor when a message arrives.
     */
    fun dispatchCommand(cmd: KlangCommLink.Cmd) {
        onCommandCallback?.invoke(cmd)
    }
}
