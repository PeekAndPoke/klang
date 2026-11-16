/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

// The show's clock. With the recording, the audio's time is the stage's time, and before it runs the walk-in:
// the page waits for a click, then counts INTRO seconds up to 0 and starts the audio.

/**
 * The show's clock for [show]. Make it once the band stands: a click starts the walk-in, [intro] seconds long.
 *
 * [audioUrl]: the audio of the same render as the events; null for none (the page's own clock then runs, as with
 * test notes). There is audio only where the show has a song and is not frozen. [notice]: the "click to start"
 * notice; it is filled and shown only where there is audio, and hidden otherwise.
 *
 * Keys, heard from the window until [dispose]: space starts the walk-in or pauses; the right arrow skips the
 * walk-in, or jumps ten seconds ahead; the left arrow ten seconds back.
 */
class ShowClock(
    private val show: Show,
    audioUrl: String?,
    private val notice: HTMLElement?,
    private val intro: Double,
) {
    /** The audio of the same render as the events. */
    private val audio: HTMLAudioElement? =
        if (show.song != null && show.frozen == null && audioUrl != null) audioOf(audioUrl) else null

    /** When the show was started, by the page's clock. */
    private var introAt: Double? = null

    /** The walk-in is over and the audio runs. */
    private var playing: Boolean = false

    /** When the frames began (the clock of the page without a recording). */
    private var started: Double = 0.0

    private val clickListener: ((Event) -> Unit)? = audio?.let { { _: Event -> toggle() } }
    private val keyListener: ((Event) -> Unit)? = audio?.let { { event: Event -> onKey(event) } }

    init {
        if (audio == null) {
            // the prototype makes no notice at all then
            notice?.let { it.style.display = "none" }
        } else {
            notice?.let {
                it.textContent = "click to start"
                it.style.cssText = "position:fixed;left:0;right:0;bottom:18px;text-align:center;color:#9ab;font:14px monospace"
            }

            clickListener?.let { window.addEventListener("click", it) }
            keyListener?.let { window.addEventListener("keydown", it) }
        }
    }

    /** Call right before the first frame. */
    fun start() {
        started = now()
    }

    /**
     * The show's time, in seconds: negative during the walk-in, the song's time after. Starts the audio when the
     * walk-in runs out.
     */
    fun showTime(): Double {
        show.frozen?.let { return it }

        val audio = audio ?: return (now() - started) / 1000 - intro

        if (playing) {
            return audio.currentTime
        }

        val since = introAt ?: return -intro
        val t = (now() - since) / 1000 - intro

        if (t < 0) {
            return t
        }

        playing = true
        audio.play()

        return 0.0
    }

    /** Whether the show still waits for its click or walks in (the audio does not run yet). */
    fun waiting(): Boolean = show.frozen == null && audio != null && !playing

    /**
     * The house lights, 0 to 1: nearly dark while waiting for the click; from the click up within a second, a flash
     * first, like lamps switched on.
     */
    fun houseAt(): Double {
        if (!waiting()) {
            return 1.0
        }

        val since = introAt?.let { (now() - it) / 1000 } ?: -1.0

        return if (since < 0) 0.07 else min(1.0, 0.07 + 0.93 * since / 0.9) + 0.5 * exp(-since / 0.12)
    }

    /** Stops listening to the click and the keys, and stops the audio. */
    fun dispose() {
        clickListener?.let { window.removeEventListener("click", it) }
        keyListener?.let { window.removeEventListener("keydown", it) }
        // stopped and let go: without its source the browser stops fetching it too
        audio?.let {
            it.pause()
            it.removeAttribute("src")
            it.load()
        }
    }

    /**
     * A click or space: the first starts the walk-in; once the audio runs, each pauses or plays it. The notice goes
     * at once, also when the walk-in has started already.
     */
    private fun toggle() {
        val audio = audio ?: return

        if (!playing) {
            if (introAt == null) {
                introAt = now()
            }
        } else if (audio.paused) {
            audio.play()
        } else {
            audio.pause()
        }

        notice?.let { it.style.opacity = "0" }
    }

    private fun onKey(event: Event) {
        val e = event as? KeyboardEvent ?: return
        val audio = audio ?: return

        if (event.fromTyping()) {
            return
        }

        when (e.code) {
            "Space" -> {
                toggle()
                e.preventDefault()
            }

            // right, during the walk-in: skip it (the notice stays, as in the prototype)
            "ArrowRight" -> if (!playing) {
                playing = true
                introAt = introAt ?: 0.0
                audio.play()
            } else {
                audio.currentTime += 10
            }

            "ArrowLeft" -> audio.currentTime = max(0.0, audio.currentTime - 10)

            else -> Unit
        }
    }

    private companion object {
        /** The page's clock, in milliseconds. */
        fun now(): Double = window.performance.now()

        /** An audio element for [url], as `new Audio(url)` makes it: the whole file may be loaded ahead. */
        fun audioOf(url: String): HTMLAudioElement {
            val element = document.createElement("audio").unsafeCast<HTMLAudioElement>()

            element.preload = "auto"
            element.src = url

            return element
        }
    }
}
