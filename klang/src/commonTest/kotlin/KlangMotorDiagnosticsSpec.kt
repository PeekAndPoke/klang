/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.ultra.streams.StreamSource

/** The Motor's diagnostics, kept by the player: the latest, and a history of a reading per tick, a gap where no fresh diagnostics came, the last n. */
class KlangMotorDiagnosticsSpec : StringSpec({

    fun diag(headroom: Double, voices: Int, active: Int) = KlangCommLink.Feedback.Diagnostics(
        playbackId = KlangCommLink.SYSTEM_PLAYBACK_ID,
        sampleRate = 48_000,
        renderHeadroom = headroom,
        activeVoiceCount = voices,
        cylinders = List(4) { KlangCommLink.Feedback.Diagnostics.CylinderState(id = it, active = it < active) },
        backendNowMs = 0.0,
        warehouse = KlangCommLink.Feedback.Diagnostics.WarehouseStats.empty,
    )

    "a reading per tick, from the latest diagnostics; a tick without fresh ones is a gap; the last n kept" {
        val diagnostics = StreamSource<KlangCommLink.Feedback.Diagnostics?>(null)
        val ticks = StreamSource(0)
        val motor = KlangMotorDiagnostics(stream = diagnostics, ticks = ticks, capacity = 3)

        motor.start()

        diagnostics(diag(headroom = 0.75, voices = 5, active = 2))
        ticks(1)
        diagnostics(diag(headroom = 0.5, voices = 9, active = 3))
        ticks(2)
        // Nothing new: the Motor stalled
        ticks(3)
        diagnostics(diag(headroom = 1.0, voices = 0, active = 0))
        ticks(4)

        val snapshot = motor.history()

        snapshot.capacity shouldBe 3
        snapshot.heat shouldContainExactly listOf(0.5, null, 0.0)
        snapshot.voices shouldContainExactly listOf(9, null, 0)
        snapshot.cylinders shouldContainExactly listOf(3, null, 0)
        snapshot.latest shouldBe KlangMotorDiagnostics.Reading(heat = 0.0, cylinders = 0, voices = 0)

        motor.stop()
        diagnostics(diag(headroom = 0.0, voices = 1, active = 1))
        ticks(5)

        // Stopped, it keeps nothing more
        motor.history().heat shouldContainExactly listOf(0.5, null, 0.0)
    }
})
