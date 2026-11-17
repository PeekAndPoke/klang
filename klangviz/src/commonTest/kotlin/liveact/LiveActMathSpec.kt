/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe

private data class At(override val t: Double) : Timed

class LiveActMathSpec : StringSpec({

    "smooth is 0 below 0, 1 above 1, a half in the middle, and flat at both ends" {
        smooth(-3.0) shouldBe 0.0
        smooth(4.0) shouldBe 1.0
        smooth(0.5) shouldBe 0.5
        smooth(0.25) shouldBe (0.15625 plusOrMinus 1e-12)
        smooth(0.01) shouldBe (0.000298 plusOrMinus 1e-9)
    }

    "smoother is flatter at its ends than smooth, and the same in the middle" {
        smoother(0.5) shouldBe 0.5
        smoother(0.25) shouldBe (0.103515625 plusOrMinus 1e-12)
        smoother(-1.0) shouldBe 0.0
        smoother(2.0) shouldBe 1.0
    }

    "valueNoise gives the value at a whole number and eases between two" {
        val at = { i: Int -> if (i == 3) 1.0 else 0.0 }

        valueNoise(3.0, at) shouldBe 1.0
        valueNoise(2.5, at) shouldBe 0.5
        valueNoise(2.25, at) shouldBe (0.15625 plusOrMinus 1e-12)
        valueNoise(-0.5) { i -> if (i == -1) 1.0 else 0.0 } shouldBe 0.5
    }

    "around gives the one at or before a time and the one after it" {
        val list = listOf(At(1.0), At(2.0), At(3.0))

        list.around(0.5) shouldBe (null to At(1.0))
        list.around(2.0) shouldBe (At(2.0) to At(3.0))
        list.around(2.5) shouldBe (At(2.0) to At(3.0))
        list.around(9.0) shouldBe (At(3.0) to null)
        emptyList<At>().around(1.0) shouldBe (null to null)
    }

    "lastIndexAt is the index of the last one at or before a time, the later of two at the same time" {
        val list = listOf(At(1.0), At(2.0), At(2.0), At(3.0))

        list.lastIndexAt(0.5) shouldBe -1
        list.lastIndexAt(2.0) shouldBe 2
        list.lastIndexAt(2.9) shouldBe 2
        list.lastIndexAt(3.0) shouldBe 3
    }

    "the switches of a query string: flags, values, numbers and triples" {
        val switches = Switches.parse("?seed=7&still&t=62.1&cam=1,2.5,-3&at=1,2")

        switches.has("still") shouldBe true
        switches.has("nohud") shouldBe false
        switches["seed"] shouldBe "7"
        switches["still"] shouldBe ""
        switches.double("t") shouldBe 62.1
        switches.double("still") shouldBe null
        switches.triple("cam") shouldBe Triple(1.0, 2.5, -3.0)
        switches.triple("at") shouldBe null
        Switches.parse("").has("seed") shouldBe false
    }

    "a show's chance is the same under the same seed and another under another" {
        val a = Show(seed = 3)
        val b = Show(seed = 3)
        val c = Show(seed = 4)

        a.own(5, 11.0) shouldBe b.own(5, 11.0)
        (a.own(5, 11.0) == c.own(5, 11.0)) shouldBe false
        a.own(5, 11.0) shouldBe a.own(5.0, 11.0)
        a.bpm shouldBe 120.0
        a.bar shouldBe 2.0
    }

    "a recording as music: its loudness where it was measured, an even level else, a transport of its own for every listener" {
        val loudness = Loudness(
            step = 1.0, offset = 0.0,
            level = listOf(0.3), slow = listOf(0.25), low = listOf(0.1), mid = listOf(0.2), high = listOf(0.4),
        )
        var made = 0
        val transport = object : Transport {
            override val time = 12.5
            override val paused = true
            override val controls: TransportControls? = null
            override fun release() = Unit
        }
        val music = RecordedMusic(song = null, loudness = loudness) {
            made++
            transport
        }

        music.energyAt(0.0).slow shouldBe 0.25
        RecordedMusic(song = null, loudness = null).energyAt(3.0) shouldBe Energy.even
        // a show built on it does not listen yet
        Show(seed = 1, music = music).energyAt(0.0).high shouldBe 0.4
        made shouldBe 0
        music.listen()?.time shouldBe 12.5
        made shouldBe 1
        music.listen()?.time shouldBe 12.5
        made shouldBe 2
        Music.none.listen() shouldBe null
        Music.none.source shouldBe "test notes"
        music.source shouldBe "test notes"
    }

    "live loudness: a level follows a rise at once and falls slowly, the slow level lags, a burst shows a sudden rise" {
        val live = LiveLoudness()

        live.current().level shouldBe 0.0

        // an even sound for three seconds, at sixty frames a second
        var t = 0.0
        var energy = live.current()

        repeat(180) {
            t += 1.0 / 60
            energy = live.feed(now = t, total = 0.5, low = 0.2, mid = 0.1, high = 0.05)
        }

        // each measure is set against its own loudest: an even sound reads full, 1 / HEADROOM, in every band
        energy.level shouldBe (1 / LiveLoudness.HEADROOM plusOrMinus 1e-9)
        energy.low shouldBe (1 / LiveLoudness.HEADROOM plusOrMinus 1e-9)
        energy.slow shouldBe (energy.level plusOrMinus 0.03)
        energy.burst shouldBe (0.0 plusOrMinus 0.03)

        // it drops to a fifth: the level falls slowly, a meter's fall
        t += 1.0 / 60
        val fallen = live.feed(now = t, total = 0.1, low = 0.04, mid = 0.02, high = 0.01)
        (fallen.level > 0.9 * energy.level) shouldBe true

        repeat(120) {
            t += 1.0 / 60
            energy = live.feed(now = t, total = 0.1, low = 0.04, mid = 0.02, high = 0.01)
        }

        // two seconds on it stands at a fifth of full, the loudest heard hardly forgotten
        energy.level shouldBe (0.2 / LiveLoudness.HEADROOM plusOrMinus 0.03)

        // a sudden loud moment: the level is up at once, the slow level is not, and the burst shows it
        t += 1.0 / 60
        val hit = live.feed(now = t, total = 0.5, low = 0.2, mid = 0.1, high = 0.05)

        (hit.level > 1.0) shouldBe true
        (hit.slow < 0.4) shouldBe true
        (hit.burst > 0.1) shouldBe true
    }

    "live loudness takes what is no number for silence, and a long rest for one frame" {
        val live = LiveLoudness()

        live.feed(now = 1.0, total = Double.NaN, low = -1.0, mid = Double.POSITIVE_INFINITY, high = 0.0) shouldBe
            Energy(slow = 0.0, burst = 0.0, level = 0.0, low = 0.0, mid = 0.0, high = 0.0)

        live.feed(now = 2.0, total = 0.5, low = 0.5, mid = 0.5, high = 0.5)
        // an hour later the level has not fallen by an hour: the rest counts as one frame
        val later = live.feed(now = 3602.0, total = 0.0, low = 0.0, mid = 0.0, high = 0.0)

        (later.level > 1.0) shouldBe true
    }

    "the loudness at a time: the nearest measured value, the ends held, the burst over the last six" {
        val loudness = Loudness(
            step = 0.5, offset = 0.0,
            level = listOf(0.2, 0.8, 0.4), slow = listOf(0.2, 0.2, 0.4),
            low = listOf(0.1, 0.2, 0.3), mid = listOf(0.4, 0.5, 0.6), high = listOf(0.7, 0.8, 0.9),
        )

        loudness.at(-5.0).level shouldBe 0.2
        loudness.at(99.0).level shouldBe 0.4
        loudness.at(0.5).low shouldBe 0.2
        loudness.at(0.5).slow shouldBe 0.2
        // at index 1 the level is 0.6 above the slow one; the five before it (all index 0) are not above
        loudness.at(0.5).burst shouldBe (0.1 plusOrMinus 1e-12)
        loudness.at(1.0).burst shouldBe (0.1 plusOrMinus 1e-12)
    }

    "a head turns fully to what is before or beside someone, and not at all to what is behind them" {
        inSight(0.0) shouldBe 1.0
        inSight(1.2) shouldBe 1.0
        inSight(-1.2) shouldBe 1.0

        // between 70 and 95 degrees to the side it fades, the same on both sides and never back up
        val fade = (0..50).map { inSight(1.22 + it * 0.009) }

        fade[25] shouldBeGreaterThan 0.0
        fade[25] shouldBeLessThan 1.0
        fade.zipWithNext().all { (a, b) -> b <= a } shouldBe true
        inSight(-1.4835) shouldBe inSight(1.4835)
        inSight(1.7) shouldBe 0.0
        inSight(-2.55) shouldBe 0.0
        inSight(3.14159) shouldBe 0.0
    }
})
