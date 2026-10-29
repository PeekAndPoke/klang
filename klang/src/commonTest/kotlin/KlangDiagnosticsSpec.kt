/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_engine

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.ultra.streams.StreamSource

/**
 * The diagnostics' metrics (KLANG-00385): any owner registers, posts and closes; a clock reads them all, a gap where a
 * metric went quiet, a closed metric's line until it scrolls out, a late one starting with gaps.
 */
class KlangDiagnosticsSpec : StringSpec({

    class Rig {
        var now = 0.0
        val ticks = StreamSource(0)
        val diagnostics = KlangDiagnostics(
            stream = StreamSource<KlangCommLink.Feedback.Diagnostics?>(null),
            ticks = ticks,
            capacity = 4,
            nowMs = { now },
        )

        /** Time passes by [ms], then the clock ticks. */
        fun tick(ms: Double = 250.0) {
            now += ms
            ticks(ticks() + 1)
        }

        fun values(metric: KlangDiagnostics.Metric): List<Double?> =
            diagnostics.history().series.single { it.metric === metric }.values
    }

    "a metric's readings are its latest posts, one per tick, the last n kept" {
        val rig = Rig().apply { diagnostics.start() }
        val heat = rig.diagnostics.metric(id = "h", name = "Heat", category = "CPU", unit = "%", min = 0.0, max = 100.0)

        for (value in listOf(10.0, 20.0, 30.0, 40.0, 50.0)) {
            heat.post(value)
            rig.tick()
        }

        rig.values(heat) shouldContainExactly listOf(20.0, 30.0, 40.0, 50.0)
        rig.diagnostics.history().series.single().latest shouldBe 50.0
    }

    "a metric that went quiet for more than a second is a gap, and back when it posts again" {
        val rig = Rig().apply { diagnostics.start() }
        val heat = rig.diagnostics.metric(id = "h", name = "Heat", category = "CPU", unit = "%", min = 0.0, max = 100.0)

        heat.post(10.0)
        rig.tick(ms = 500.0)
        rig.tick(ms = 600.0)
        heat.post(30.0)
        rig.tick(ms = 250.0)

        // The first reading is the one taken when the clock was first heard, before any post
        rig.values(heat) shouldContainExactly listOf(null, 10.0, null, 30.0)
    }

    "a late metric starts with gaps; a closed one keeps its line until it scrolls out; the registry ends empty" {
        val rig = Rig().apply { diagnostics.start() }
        val heat = rig.diagnostics.metric(id = "h", name = "Heat", category = "CPU", unit = "%", min = 0.0, max = 100.0)

        heat.post(10.0)
        rig.tick()

        val sprudel = rig.diagnostics.metric(id = "s", name = "Sprudel", category = "CPU", unit = "%", min = 0.0, max = 100.0)
        heat.post(11.0)
        sprudel.post(5.0)
        rig.tick()

        rig.values(sprudel) shouldContainExactly listOf(null, null, 5.0)
        rig.diagnostics.history().byCategory.keys shouldContainExactly listOf("CPU")

        sprudel.close()
        heat.post(12.0)
        rig.tick()

        rig.values(sprudel) shouldContainExactly listOf(null, null, 5.0, null)

        repeat(3) {
            heat.post(13.0)
            rig.tick()
        }

        rig.diagnostics.history().series.map { it.metric.name } shouldContainExactly listOf("Heat")

        heat.close()
        rig.diagnostics.metrics.shouldBeEmpty()
    }

    "a metric registered again under an id goes on in the same line, after a gap, named by the newest" {
        // The same song played, stopped and played again from the same place (the maintainer, 2026-10-29)
        val rig = Rig().apply { diagnostics.start() }
        val first = rig.diagnostics.metric(id = "s", name = "Sprudel: A", category = "CPU", unit = "%", min = 0.0, max = 100.0)

        first.post(5.0)
        rig.tick()
        first.close()
        rig.tick()

        val again = rig.diagnostics.metric(id = "s", name = "Sprudel: B", category = "CPU", unit = "%", min = 0.0, max = 100.0)

        again.post(7.0)
        rig.tick()

        val line = rig.diagnostics.history().series.single()

        line.values shouldContainExactly listOf(null, 5.0, null, 7.0)
        line.metric.name shouldBe "Sprudel: B"
    }
})
