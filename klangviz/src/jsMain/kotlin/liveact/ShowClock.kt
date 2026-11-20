/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.peekandpoke.klang.viz.liveact.lights.LightsUp
import io.peekandpoke.klang.viz.liveact.sound.Transport
import io.peekandpoke.klang.viz.liveact.sound.TransportControls
import io.peekandpoke.klang.viz.liveact.stage.WorldLook
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.math.exp
import kotlin.math.min

// The show's clock. With music that is listened to, its time is the stage's time, and before it runs the walk-in:
// the page waits for a click, then counts INTRO seconds up to 0 and starts the music.

/**
 * The show's clock for [show]. Make it once the band stands: a click starts the walk-in, [intro] seconds long.
 *
 * The music is the show's ([Show.music]): its transport is what is started, paused and asked for the time. It is
 * taken only where the show has a song and is not frozen; without one the page's own clock runs, as with test
 * notes. [notice]: the "click to start" notice; it is filled and shown only where there is a transport, and hidden
 * otherwise.
 *
 * Keys, heard from the window until [dispose]: space starts the walk-in or pauses; the right arrow skips the
 * walk-in, or jumps ten seconds ahead; the left arrow ten seconds back.
 *
 * [begun]: the walk-in begins at [start], without waiting for a click, and no notice asks for one. For an app whose
 * own start screen took the click already (notstrom's, KLANG-00465: its click unlocked the sound, and the show was
 * made after it). Clicks and keys after it do what they do once the walk-in runs.
 *
 * [opens]: the show opens (`?opening=ember`, [WorldLook.opens]): its light comes up from pitch black as the band walks
 * in ([houseAt], [LightsUp]).
 */
class ShowClock(
    private val show: Show,
    private val notice: HTMLElement?,
    private val intro: Double,
    private val begun: Boolean = false,
    private val opens: Boolean = false,
) {
    /**
     * What plays the music, listened to from here until [dispose]: the stage's time is its time once it runs. A
     * frozen show and one without a song do not listen at all.
     */
    private val transport: Transport? = if (show.song != null && show.frozen == null) show.music.listen() else null

    /**
     * What moves the music, where it is this clock's to move. Null with a transport means the music is someone
     * else's: the clock follows it. There is no walk-in then and no notice, no click and no key: the stage's time is
     * the music's, from the moment the show is there.
     */
    private val controls: TransportControls? = transport?.controls

    /** When the show was started, by the page's clock. */
    private var introAt: Double? = null

    /** The walk-in is over and the music runs. */
    private var playing: Boolean = false

    /** When the frames began (the page's own clock, where there is no music to listen to). */
    private var started: Double = 0.0

    /** In a show that opens: the seconds since the walk-in began, counted frame by frame ([LightsUp.advance]). */
    private var risen: Double = 0.0

    /** In a show that opens: the page's clock at the last [houseAt] since the walk-in began; null before it. */
    private var risenAt: Double? = null

    private val clickListener: ((Event) -> Unit)? = controls?.let { { _: Event -> toggle() } }
    private val keyListener: ((Event) -> Unit)? = controls?.let { { event: Event -> onKey(event) } }

    init {
        if (controls == null || begun) {
            // the prototype makes no notice at all where there is nothing to start; and where the click was taken
            // already, nothing asks for one
            notice?.let { it.style.display = "none" }
        }

        if (controls != null) {
            if (!begun) {
                notice?.let {
                    it.textContent = "click to start"
                    it.style.cssText = "position:fixed;left:0;right:0;bottom:18px;text-align:center;color:#9ab;font:14px monospace"
                }
            }

            clickListener?.let { window.addEventListener("click", it) }
            keyListener?.let { window.addEventListener("keydown", it) }
        }
    }

    /** Call right before the first frame. With [begun], the walk-in begins here. */
    fun start() {
        started = now()

        if (begun && controls != null && introAt == null) {
            introAt = started
        }
    }

    /**
     * The show's time, in seconds: negative during the walk-in, the song's time after. Starts the music when the
     * walk-in runs out.
     */
    fun showTime(): Double {
        show.frozen?.let { return it }

        val transport = transport ?: return (now() - started) / 1000 - intro

        val controls = controls

        if (playing || controls == null) {
            return transport.time
        }

        val since = introAt ?: return -intro
        val t = (now() - since) / 1000 - intro

        if (t < 0) {
            return t
        }

        playing = true
        controls.play()

        return 0.0
    }

    /** Whether the show still waits for its click or walks in (the music does not run yet). */
    fun waiting(): Boolean = show.frozen == null && controls != null && !playing

    /**
     * The house lights, 0 to 1: nearly dark while waiting for the click; from the click up within a second, a flash
     * first, like lamps switched on. In a show that opens ([opens]): pitch black while waiting, and from the walk-in's
     * start up over [LightsUp.SECONDS], eased, without the flash; also when the song begins before it is up. A frozen
     * show there stands at the level of its time ([Show.frozen]: at `?t=-15`, the walk-in's start, pitch black). Call
     * it each frame: it counts the frames.
     */
    fun houseAt(): Double {
        if (opens) {
            return risingHouse()
        }

        if (!waiting()) {
            return 1.0
        }

        val since = introAt?.let { (now() - it) / 1000 } ?: -1.0

        return if (since < 0) 0.07 else min(1.0, 0.07 + 0.93 * since / 0.9) + 0.5 * exp(-since / 0.12)
    }

    /**
     * The house level in a show that opens ([houseAt]): frozen, the level of its time since the walk-in's start; where
     * the clock runs no walk-in (no music of its own to move), up at once, as without an opening; else 0 until the
     * walk-in begins, then rising by the frames counted since.
     */
    private fun risingHouse(): Double {
        show.frozen?.let { return LightsUp.level(since = it + intro) }

        if (controls == null) {
            return 1.0
        }

        val began = introAt ?: return 0.0
        val nowMs = now()
        val last = risenAt ?: began

        risen = LightsUp.advance(since = risen, frame = (nowMs - last) / 1000)
        risenAt = nowMs

        return LightsUp.level(since = risen)
    }

    /** Stops listening to the click and the keys, and says it is done with the music. Call it once. */
    fun dispose() {
        clickListener?.let { window.removeEventListener("click", it) }
        keyListener?.let { window.removeEventListener("keydown", it) }
        transport?.release()
    }

    /**
     * A click or space: the first starts the walk-in; once the music runs, each pauses or plays it. The notice goes
     * at once, also when the walk-in has started already.
     */
    private fun toggle() {
        val transport = transport ?: return
        val controls = controls ?: return

        if (!playing) {
            if (introAt == null) {
                introAt = now()
            }
        } else if (transport.paused) {
            controls.play()
        } else {
            controls.pause()
        }

        notice?.let { it.style.opacity = "0" }
    }

    private fun onKey(event: Event) {
        val e = event as? KeyboardEvent ?: return
        val controls = controls ?: return

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
                controls.play()
            } else {
                controls.skip(10.0)
            }

            "ArrowLeft" -> controls.skip(-10.0)

            else -> Unit
        }
    }

    private companion object {
        /** The page's clock, in milliseconds. */
        fun now(): Double = window.performance.now()
    }
}
