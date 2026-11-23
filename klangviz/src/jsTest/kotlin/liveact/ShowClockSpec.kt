/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.song.RecordedEvent
import io.peekandpoke.klang.viz.liveact.song.Recording
import io.peekandpoke.klang.viz.liveact.song.Song
import io.peekandpoke.klang.viz.liveact.sound.Energy
import io.peekandpoke.klang.viz.liveact.sound.Music
import io.peekandpoke.klang.viz.liveact.sound.Transport
import io.peekandpoke.klang.viz.liveact.sound.TransportControls
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.KeyboardEventInit

/** A transport that writes down what is asked of it. */
private class Asked(private val own: Boolean = true) : Transport, TransportControls {
    val calls = mutableListOf<String>()
    val skips = mutableListOf<Double>()
    var standing = true

    override var time: Double = 0.0
    override val paused: Boolean get() = standing
    override val controls: TransportControls? get() = if (own) this else null

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

    override val source = "counted"

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

    "begun: the walk-in begins at start without a click, no notice asks for one, and the music plays when it runs out" {
        val asked = Asked()
        val notice = document.createElement("div").unsafeCast<HTMLElement>()
        val clock = ShowClock(show = Show(seed = 1, music = Counted(asked)), notice = notice, intro = 15.0, begun = true)

        try {
            notice.style.display shouldBe "none"
            notice.textContent shouldBe ""
            // not started yet: the house is dark
            clock.houseAt() shouldBe 0.07

            clock.start()

            // the lamps come on with the start, no click needed
            clock.waiting() shouldBe true
            (clock.houseAt() > 0.07) shouldBe true
            asked.calls shouldBe emptyList()
        } finally {
            clock.dispose()
        }

        // no walk-in at all: it has run out the moment it starts, and the music plays at the first frame
        val now = Asked()
        val at = ShowClock(show = Show(seed = 1, music = Counted(now)), notice = null, intro = 0.0, begun = true)

        try {
            at.start()
            at.showTime() shouldBe 0.0
            now.calls shouldBe listOf("play")
            at.waiting() shouldBe false
        } finally {
            at.dispose()
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
        val notice = document.createElement("div").unsafeCast<HTMLElement>()
        val clock = ShowClock(show = Show(seed = 1, music = music), notice = notice, intro = 15.0)

        try {
            music.listeners shouldBe 1
            notice.textContent shouldBe "click to start"
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

    "music that is someone else's is followed: its time at once, no walk-in, no key, no click, nothing started" {
        val asked = Asked(own = false)
        val notice = document.createElement("div").unsafeCast<HTMLElement>()
        val clock = ShowClock(show = Show(seed = 1, music = Counted(asked)), notice = notice, intro = 15.0)

        try {
            clock.waiting() shouldBe false
            clock.houseAt() shouldBe 1.0
            // no "click to start": there is nothing to start
            notice.style.display shouldBe "none"
            notice.textContent shouldBe ""

            asked.time = 41.25
            clock.showTime() shouldBe 41.25

            press("ArrowRight")
            press("Space")
            window.dispatchEvent(Event("click"))
            asked.calls shouldBe emptyList()
            clock.showTime() shouldBe 41.25
        } finally {
            clock.dispose()
        }

        asked.calls shouldBe listOf("release")
    }

    "every clock listens for itself" {
        val music = Counted(Asked())

        ShowClock(show = Show(seed = 1, music = music), notice = null, intro = 15.0).dispose()
        ShowClock(show = Show(seed = 1, music = music), notice = null, intro = 15.0).dispose()

        music.listeners shouldBe 2
    }

    "a show that opens: pitch black until the walk-in begins, then the light comes up from nothing, without a flash" {
        val notice = document.createElement("div").unsafeCast<HTMLElement>()
        val show = Show(seed = 1, music = Counted(Asked()), switches = Switches.parse("opening=ember"))
        val clock = ShowClock(show = show, notice = notice, intro = 15.0, begun = true, opens = true)

        try {
            clock.houseAt() shouldBe 0.0

            clock.start()

            // the walk-in has just begun: hardly any light yet, and no flash of lamps switched on
            (clock.houseAt() < 0.01) shouldBe true
        } finally {
            clock.dispose()
        }
    }

    "a frozen show that opens stands at the light of its time: pitch black at the walk-in's start, up ten seconds on" {
        fun frozenAt(t: Double): Double {
            val show = Show(seed = 1, music = Counted(Asked()), switches = Switches.parse("t=$t&opening=ember"))
            val clock = ShowClock(show = show, notice = null, intro = 15.0, opens = true)

            return clock.houseAt().also { clock.dispose() }
        }

        frozenAt(-15.0) shouldBe 0.0
        frozenAt(-10.0) shouldBe (0.5 plusOrMinus 1e-12)
        frozenAt(-5.0) shouldBe 1.0
        frozenAt(62.1) shouldBe 1.0
    }

    "a show that opens without a song walks in on the page's clock: pitch black before the first frame, then rising" {
        val show = Show(seed = 1, music = Counted(Asked(), song = null), switches = Switches.parse("opening=ember"))
        val clock = ShowClock(show = show, notice = null, intro = 15.0, opens = true)

        try {
            clock.houseAt() shouldBe 0.0

            clock.start()

            // the walk-in has just begun: hardly any light yet
            (clock.houseAt() < 0.01) shouldBe true
        } finally {
            clock.dispose()
        }
    }

    "a show that opens but follows music it does not move has no walk-in: its light is up at once" {
        val show = Show(seed = 1, music = Counted(Asked(own = false)), switches = Switches.parse("opening=ember"))
        val clock = ShowClock(show = show, notice = null, intro = 15.0, opens = true)

        try {
            clock.start()
            clock.houseAt() shouldBe 1.0
        } finally {
            clock.dispose()
        }
    }

    "a change of song: the music is let go, the show's time runs on, and the next song's music plays from its moment" {
        val first = Asked()
        val next = Asked()
        val clock = ShowClock(show = Show(seed = 1, music = Counted(first)), notice = null, intro = 0.0)

        try {
            window.dispatchEvent(Event("click"))
            clock.showTime() shouldBe 0.0
            first.time = 30.0
            clock.showTime() shouldBe 30.0

            // let go: no jump back, no standstill, and the show does not wait for a click
            clock.hush()
            first.calls shouldBe listOf("play", "release")

            val after = clock.showTime()

            (after >= 30.0 && after < 31.0) shouldBe true
            clock.waiting() shouldBe false

            // the next song begins at a moment that has come already: it plays at once, from that moment
            clock.changeTo(music = Counted(next), at = after)
            next.calls shouldBe emptyList()
            clock.waiting() shouldBe false
            clock.showTime() shouldBe after
            next.calls shouldBe listOf("play")

            // and from then the show's time is that moment and the music's time on top
            next.time = 5.0
            clock.showTime() shouldBe after + 5.0
        } finally {
            clock.dispose()
        }

        next.calls shouldBe listOf("play", "release")
    }

    "a song that begins later is waited for on the show's running time, and a frozen show changes nothing" {
        val first = Asked()
        val next = Asked()
        val clock = ShowClock(show = Show(seed = 1, music = Counted(first)), notice = null, intro = 0.0)

        try {
            window.dispatchEvent(Event("click"))
            clock.showTime() shouldBe 0.0
            first.time = 10.0
            clock.changeTo(music = Counted(next), at = 10.0 + 3600.0)

            val waited = clock.showTime()

            (waited >= 10.0 && waited < 11.0) shouldBe true
            next.calls shouldBe emptyList()
        } finally {
            clock.dispose()
        }

        val frozenMusic = Counted(Asked())
        val frozen = ShowClock(show = Show(seed = 1, music = frozenMusic, switches = Switches.parse("t=62.1")), notice = null, intro = 15.0)

        frozen.changeTo(music = Counted(Asked()), at = 100.0)
        frozen.showTime() shouldBe 62.1
        frozen.dispose()
    }

    "between two songs no click and no key starts the next one before its moment" {
        val first = Asked()
        val next = Asked()
        val clock = ShowClock(show = Show(seed = 1, music = Counted(first)), notice = null, intro = 0.0)

        try {
            window.dispatchEvent(Event("click"))
            clock.showTime() shouldBe 0.0
            clock.changeTo(music = Counted(next), at = 3600.0)

            press("ArrowRight")
            press("ArrowLeft")
            press("Space")
            window.dispatchEvent(Event("click"))

            next.calls shouldBe emptyList()
            (clock.showTime() < 1.0) shouldBe true
            next.calls shouldBe emptyList()
        } finally {
            clock.dispose()
        }
    }
})
