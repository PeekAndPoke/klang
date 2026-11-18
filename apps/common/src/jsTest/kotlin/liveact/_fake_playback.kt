/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common.liveact

import io.peekandpoke.klang.audio_bridge.KlangPattern
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_engine.KlangCycleEvent
import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.Unsubscribe

/**
 * A playback that is told where it is ([heard], seconds; null before it has begun) and what its cycles hold
 * ([events]), at one tempo ([rpm]), and remembers what it was asked.
 */
internal class FakePlayback(
    var rpm: Double = 60.0,
    var events: (cycle: Int) -> List<KlangCycleEvent> = { emptyList() },
) : KlangCyclicPlayback {
    var heard: Double? = null
    var stops = 0

    /** The cycles asked for, in the order they were asked. */
    val asked = mutableListOf<Int>()

    private val source = StreamSource<KlangPlaybackSignal>(KlangPlaybackSignal.Idle)

    override val playbackId: String = "fake"
    private var listening = 0

    /** The stream of its signals, counting who listens. */
    override val signals: Stream<KlangPlaybackSignal> = object : Stream<KlangPlaybackSignal> by source.readonly {
        override fun subscribeToStream(sub: (KlangPlaybackSignal) -> Unit): Unsubscribe {
            val off = source.subscribeToStream(sub)

            listening++

            return {
                listening--
                off()
            }
        }
    }

    /** How many listen to its signals now. */
    fun listeners(): Int = listening

    /** Says that the pattern or the tempo changed, as a playback does once it answers with the new one. */
    fun changed() {
        source(KlangPlaybackSignal.PatternUpdated)
    }

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
    override fun heardCycles(): Double? = heard?.let { it * rpm / 60.0 }
    override fun rpmAt(cycle: Double): Double = rpm

    override fun cycleEvents(cycle: Int): List<KlangCycleEvent> {
        asked += cycle

        return events(cycle)
    }

    override fun setGain(gain: Double, rampSec: Double) = Unit

    override val gain: Double = 1.0
}

/**
 * [perCycle] events of a cycle, evenly spread, as a playback at [rpm] places them: tagged [tag] (none where null),
 * each with [note].
 */
internal fun cycleOf(
    cycle: Int,
    rpm: Double = 60.0,
    perCycle: Int = 4,
    tag: String? = "lead",
    note: String = "c3",
    control: Boolean? = null,
): List<KlangCycleEvent> {
    val cycleSec = 60.0 / rpm

    return List(perCycle) { i ->
        val start = cycle + i.toDouble() / perCycle

        KlangCycleEvent(
            startSec = start * cycleSec,
            endSec = (start + 1.0 / perCycle) * cycleSec,
            startCycles = start,
            durationCycles = 1.0 / perCycle,
            data = VoiceData.empty.copy(note = note, tags = tag?.let { setOf(it) }, control = control),
            sourceLocations = null,
        )
    }
}
