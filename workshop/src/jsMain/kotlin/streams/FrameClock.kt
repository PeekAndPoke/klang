package io.peekandpoke.klang.workshop.streams

import kotlinx.browser.window

/**
 * The clock a stream's motion runs on: [next] calls back once, on the next frame, with the frame's time in
 * milliseconds, and returns the way to cancel that call. A test drives its own clock frame by frame.
 */
fun interface FrameClock {

    fun next(onFrame: (timeMs: Double) -> Unit): () -> Unit

    companion object {
        /** The browser's animation frames: in step with the screen. */
        val animationFrames: FrameClock = FrameClock { onFrame ->
            val id = window.requestAnimationFrame { onFrame(it) }

            ({ window.cancelAnimationFrame(id) })
        }
    }
}
