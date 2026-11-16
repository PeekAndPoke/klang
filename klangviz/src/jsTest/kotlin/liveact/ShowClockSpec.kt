/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.song.RecordedEvent
import io.peekandpoke.klang.viz.liveact.song.Recording
import io.peekandpoke.klang.viz.liveact.song.Song
import kotlinx.browser.window
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/** A transport that writes down what is asked of it. */
private class Asked : Transport {
    val calls = mutableListOf<String>()
    val skips = mutableListOf<Double>()
    var standing = true

    override var time: Double = 0.0
    override val paused: Boolean get() = standing

    override fun play() {
        calls.add("play")
        standing = false
    }

    override fun pause() {
        calls.add("pause")
        standing = true
    }

    override fun skip(seconds: Double) {
        calls.add("skip")
        skips.add(seconds)
    }

    override fun release() {
        calls.add("release")
    }
}

private val oneKick = Song(Recording(rpm = 30.0, events = listOf(RecordedEvent(t = 1.0, d = 0.5, tags = listOf("kick")))))

/** Music that counts who listens; with a song unless [song] is null. */
private class Counted(val asked: Asked, override val song: Song? = oneKick) : Music {
    var listeners = 0

    override fun energyAt(t: Double) = Energy.even

    override fun listen(): Transport {
        listeners++

        return asked
    }
}

private fun press(code: String) {
    window.dispatchEvent(KeyboardEvent("keydown", KeyboardEventInit(code = code, key = code, bubbles = true)))
}

class ShowClockSpec : StringSpec({

    "a frozen show and a show without a song do not listen to the music at all" {
        val music = Counted(Asked())

        ShowClock(show = Show(seed = 1, music = music, switches = Switches.parse("t=62.1")), notice = null, intro = 15.0).dispose()
        music.listeners shouldBe 0

        // music that could be listened to, but has no song
        val songless = Counted(Asked(), song = null)
        val silent = ShowClock(show = Show(seed = 1, music = songless), notice = null, intro = 15.0)

        silent.waiting() shouldBe false
        silent.dispose()
        songless.listeners shouldBe 0
    }

    "a click starts the walk-in, and when it has run out the music plays from its start" {
        val asked = Asked()
        // no walk-in at all: it has run out the moment it starts
        val clock = ShowClock(show = Show(seed = 1, music = Counted(asked)), notice = null, intro = 0.0)

        try {
            clock.waiting() shouldBe true
            asked.calls shouldBe emptyList()

            window.dispatchEvent(Event("click"))
            asked.calls shouldBe emptyList()

            clock.showTime() shouldBe 0.0
            asked.calls shouldBe listOf("play")
            clock.waiting() shouldBe false

            // the next click pauses, the one after plays again
            window.dispatchEvent(Event("click"))
            window.dispatchEvent(Event("click"))
            asked.calls shouldBe listOf("play", "pause", "play")
        } finally {
            clock.dispose()
        }
    }

    "a frozen show's time is the frozen one" {
        val clock = ShowClock(show = Show(seed = 1, music = Counted(Asked()), switches = Switches.parse("t=62.1")), notice = null, intro = 15.0)

        try {
            clock.showTime() shouldBe 62.1
            clock.waiting() shouldBe false
            clock.houseAt() shouldBe 1.0
        } finally {
            clock.dispose()
        }
    }

    "the keys: the right arrow skips the walk-in and then jumps ahead, the left one back, space pauses and plays" {
        val asked = Asked()
        val music = Counted(asked)
        val clock = ShowClock(show = Show(seed = 1, music = music), notice = null, intro = 15.0)

        try {
            music.listeners shouldBe 1
            clock.waiting() shouldBe true
            clock.showTime() shouldBe -15.0
            clock.houseAt() shouldBe 0.07

            press("ArrowRight")
            asked.calls shouldBe listOf("play")
            clock.waiting() shouldBe false

            // the music's time is the stage's time now
            asked.time = 33.5
            clock.showTime() shouldBe 33.5

            press("ArrowRight")
            press("ArrowLeft")
            asked.calls shouldBe listOf("play", "skip", "skip")
            asked.skips shouldBe listOf(10.0, -10.0)

            press("Space")
            press("Space")
            asked.calls.takeLast(2) shouldBe listOf("pause", "play")
        } finally {
            clock.dispose()
        }
    }

    "a clock given up says it is done with the music, and hears no key and no click after" {
        val asked = Asked()
        val clock = ShowClock(show = Show(seed = 1, music = Counted(asked)), notice = null, intro = 15.0)

        clock.dispose()
        asked.calls shouldBe listOf("release")

        press("ArrowRight")
        press("Space")
        window.dispatchEvent(Event("click"))
        asked.calls shouldBe listOf("release")
        // the click started no walk-in: the house is as dark as before it
        clock.houseAt() shouldBe 0.07
    }

    "every clock listens for itself" {
        val music = Counted(Asked())

        ShowClock(show = Show(seed = 1, music = music), notice = null, intro = 15.0).dispose()
        ShowClock(show = Show(seed = 1, music = music), notice = null, intro = 15.0).dispose()

        music.listeners shouldBe 2
    }
})
