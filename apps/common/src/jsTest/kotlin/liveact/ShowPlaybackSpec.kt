/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_engine.KlangCycleEvent
import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource

/** A playback that is told where it is ([heard]) and remembers what it was asked to do. */
private class FakePlayback : KlangCyclicPlayback {
    var heard: Double? = null
    var stops = 0

    override val playbackId: String = "fake"
    override val signals: Stream<KlangPlaybackSignal> = StreamSource<KlangPlaybackSignal>(KlangPlaybackSignal.Idle).readonly

    override fun handleFeedback(feedback: KlangCommLink.Feedback) = Unit
    override fun start() = Unit
    override fun start(options: KlangCyclicPlayback.Options) = Unit

    override fun stop() {
        stops++
    }

    override fun updatePattern(pattern: KlangPattern) = Unit
    override fun updateRpm(rpm: Double) = Unit
    override fun reemitVoiceSignals() = Unit
    override fun heardSec(): Double? = heard
    override fun heardCycles(): Double? = null
    override fun rpmAt(cycle: Double): Double = 30.0
    override fun cycleEvents(cycle: Int): List<KlangCycleEvent> = emptyList()

    override fun setGain(gain: Double, rampSec: Double) = Unit

    override val gain: Double = 1.0
}

/** A [ShowPlayback] that counts how often it began its playback. */
private class PlaybackBench {
    val playback = FakePlayback()
    var begun = 0

    val transport = ShowPlayback(
        begin = {
            begun++
            playback
        },
    )
}

class ShowPlaybackSpec : StringSpec({

    "before it is played it stands at 0, paused, and nothing is begun" {
        val bench = PlaybackBench()

        bench.transport.time shouldBe 0.0
        bench.transport.paused shouldBe true
        bench.begun shouldBe 0
    }

    "played, it begins the playback once, and its time is what is heard" {
        val bench = PlaybackBench()

        bench.transport.play()
        bench.transport.play()

        bench.begun shouldBe 1
        bench.transport.paused shouldBe false
        bench.transport.time shouldBe 0.0

        bench.playback.heard = 12.5

        bench.transport.time shouldBe 12.5
    }

    "pause and skip leave the song as it is" {
        val bench = PlaybackBench()

        bench.transport.play()
        bench.playback.heard = 20.0
        bench.transport.pause()
        bench.transport.skip(10.0)
        bench.transport.skip(-10.0)

        bench.transport.time shouldBe 20.0
        bench.transport.paused shouldBe false
        bench.playback.stops shouldBe 0
    }

    "released, it stops what plays; released before it began, there is nothing to stop" {
        val before = PlaybackBench()

        before.transport.release()

        before.begun shouldBe 0
        before.playback.stops shouldBe 0

        val bench = PlaybackBench()

        bench.transport.play()
        bench.playback.heard = 30.0
        bench.transport.release()

        bench.playback.stops shouldBe 1
    }
})
