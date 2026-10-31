/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.math.abs

/** [OfflineBackend] and [VoiceWatch] (KLANG-00399, step 2): a backend that renders only when told, and what it played. */
class OfflineBackendSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = AudioBackendContext.RENDER_QUANTUM_FRAMES

    /** One block's length: a voice starts on the first block at or after its second. */
    val block = blockFrames.toDouble() / sampleRate

    /** What the watch saw, one line per voice: "started a 0.5". */
    class Seen : VoiceWatch {
        val lines = mutableListOf<Pair<String, Double>>()

        override fun started(playbackId: String, data: VoiceData, atSec: Double) {
            lines += "started ${data.note}" to atSec
        }

        override fun late(playbackId: String, data: VoiceData, atSec: Double) {
            lines += "late ${data.note}" to atSec
        }

        override fun soundless(playbackId: String, data: VoiceData, atSec: Double) {
            lines += "soundless ${data.note}" to atSec
        }
    }

    class Rig(startTimeSec: Double = 0.0) {
        val link = KlangCommLink(capacity = 1024)
        val seen = Seen()

        val backend = OfflineBackend(
            config = AudioBackend.Config(commLink = link.backend, sampleRate = sampleRate, blockSize = blockFrames),
            startTimeSec = startTimeSec,
            voiceWatch = seen,
        )

        /** A voice of playback "p" named [note], [atSec] after its start, a tenth of a second long. */
        fun send(note: String, atSec: Double, playbackStartTime: Double = 0.0, sound: String = "sine") {
            val voice = ScheduledVoice(
                playbackId = "p",
                data = VoiceData.empty.copy(note = note, sound = sound, freqHz = 440.0),
                startTime = atSec,
                gateEndTime = atSec + 0.1,
                playbackStartTime = playbackStartTime,
            )

            link.frontend.control.send(KlangCommLink.Cmd.ScheduleVoices(playbackId = "p", voices = listOf(voice)))
        }

        fun feedback(): List<KlangCommLink.Feedback> = buildList {
            while (true) {
                add(link.frontend.feedback.receive() ?: break)
            }
        }
    }

    "it is ready at once and renders nothing until told" {
        val rig = Rig()

        rig.send(note = "a", atSec = 0.0)

        rig.feedback().filterIsInstance<KlangCommLink.Feedback.BackendReady>().size shouldBe 1
        rig.backend.cursorFrame shouldBe 0.0
        rig.seen.lines shouldBe emptyList()
    }

    "a voice starts at its second on the timeline, and the block sounds" {
        val rig = Rig()

        rig.send(note = "a", atSec = 0.5)
        rig.backend.renderUntil(0.4)
        rig.seen.lines shouldBe emptyList()

        var peak = 0.0
        rig.backend.renderUntil(0.6) { out -> peak = maxOf(peak, out.left.maxOf { abs(it) }) }

        rig.seen.lines.map { it.first } shouldBe listOf("started a")
        rig.seen.lines.single().second shouldBe (0.5 plusOrMinus 1e-9)
        (peak > 0.0) shouldBe true
    }

    "the timeline starts where it is told, the engine's with it" {
        val rig = Rig(startTimeSec = 1000.0)

        rig.backend.render(blocks = 10)
        rig.backend.nowSec shouldBe (1000.0 + 10 * block plusOrMinus 1e-9)

        // The engine counts from the same second: a playback asking to start at 1000.1 s plays its voice at 1000.6 s
        rig.send(note = "a", atSec = 0.5, playbackStartTime = 1000.1)
        rig.backend.renderUntil(1000.7)

        rig.seen.lines.map { it.first } shouldBe listOf("started a")
        rig.seen.lines.single().second shouldBe (1000.6 plusOrMinus 1e-9)
    }

    "a voice whose start was rendered past is late" {
        val rig = Rig()

        rig.send(note = "a", atSec = 0.1)
        rig.backend.renderUntil(1.0)
        rig.send(note = "b", atSec = 0.5)
        rig.backend.render(blocks = 1)

        rig.seen.lines.map { it.first } shouldBe listOf("started a", "late b")
        rig.seen.lines[1].second shouldBe (0.5 plusOrMinus 1e-9)
    }

    "a sample voice without its sample makes no sound, and the watch says so" {
        val rig = Rig()

        rig.send(note = "a", atSec = 0.1, sound = "no-such-sample")
        rig.backend.renderUntil(0.2)

        rig.seen.lines.map { it.first } shouldBe listOf("soundless a")
    }

    "the backend tells the frontend where it started a playback, once per start" {
        val rig = Rig()

        rig.backend.renderUntil(1.0)
        val now = rig.backend.nowSec
        // Asked for second 0, which has gone by: started at the backend's now
        rig.send(note = "a", atSec = 0.0, playbackStartTime = 0.0)
        rig.send(note = "b", atSec = 0.5, playbackStartTime = 0.0)
        rig.backend.render(blocks = 1)

        val anchors = rig.feedback().filterIsInstance<KlangCommLink.Feedback.PlaybackAnchored>()
        anchors.map { it.playbackId } shouldBe listOf("p")
        anchors.single().epochSec shouldBe (now plusOrMinus 1e-9)

        // Asked for a second still to come: started there
        val later = Rig()
        later.send(note = "a", atSec = 0.0, playbackStartTime = 0.25)
        later.backend.render(blocks = 1)

        later.feedback().filterIsInstance<KlangCommLink.Feedback.PlaybackAnchored>().single().epochSec shouldBe 0.25
    }

    "a playback whose first voice comes late starts at the backend's now" {
        val rig = Rig()

        rig.backend.renderUntil(2.0)
        val now = rig.backend.nowSec
        rig.send(note = "a", atSec = 0.0, playbackStartTime = 0.0)
        rig.backend.render(blocks = 1)

        rig.seen.lines.single().first shouldBe "started a"
        rig.seen.lines.single().second shouldBe (now plusOrMinus 1e-9)
    }
})
