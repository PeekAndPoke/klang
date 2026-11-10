/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File

/**
 * The stage cost table's own logic (KLANG-00420, probe 7), never its wall-clock numbers: the median and spread
 * helpers on fixed arrays, the interleaving order, [measureCosts]' arithmetic and visiting order under a FAKE clock
 * and allocation counter, the comparisons [deriveCostRows] draws, the sort, and the hand-written JSON (a NaN, the
 * infinities, a quote in a name), for the cost rows and the stage probe's rows alike. Fast: nothing renders audio.
 */
class StageCostSpec : StringSpec({

    "median: the middle value of an odd count, the mean of the middle two of an even count, NaN for none; the input is not sorted in place" {
        val odd = doubleArrayOf(3.0, 1.0, 2.0)
        val even = doubleArrayOf(4.0, 1.0, 3.0, 2.0)

        median(odd) shouldBe 2.0
        median(even) shouldBe 2.5
        median(DoubleArray(0)).isNaN() shouldBe true
        odd.toList() shouldContainExactly listOf(3.0, 1.0, 2.0)
    }

    "spreadOf: min, median and max, and the relative width (max - min) / median" {
        val spread = spreadOf(doubleArrayOf(5.0, 1.0, 9.0, 3.0))

        spread shouldBe CostSpread(min = 1.0, median = 4.0, max = 9.0)
        spread.relativeWidth shouldBe 2.0
    }

    "interleavedOrder: every round visits every case once, the rounds are not all the same order, one seed gives one order" {
        val order = interleavedOrder(count = 7, rounds = 6, seed = 42)

        order.size shouldBe 6

        for (round in order) {
            round.sorted() shouldContainExactly (0 until 7).toList()
        }

        order.map { it.toList() }.toSet().size shouldNotBe 1
        interleavedOrder(count = 7, rounds = 6, seed = 42).map { it.toList() } shouldBe order.map { it.toList() }
    }

    "measureCosts under a fake clock: exact ns per sample, build and bytes per block; each round visits each stage once, long render then short" {
        var now = 0.0
        var allocated = 0.0
        val visits = ArrayList<Pair<Int, Int>>()

        // Stage i costs (i + 1) ns per frame plus a build of 500 * (i + 1) ns, and allocates 8 bytes per frame plus 100.
        fun fake(i: Int): ProbeStage = ProbeStage { signal ->
            visits.add(i to signal.frames)
            now += (i + 1) * signal.frames + 500.0 * (i + 1)
            allocated += 8.0 * signal.frames + 100.0
            ProbeOutput(left = DoubleArray(1), right = null)
        }

        val config = CostConfig(blocksLong = 5, blocksShort = 1, warmupRounds = 2, rounds = 3, seed = 7)
        val results = measureCosts(stages = List(3) { fake(it) }, config = config, clock = { now }, bytes = { allocated })

        for (i in 0 until 3) {
            results[i].nsPerSample shouldBe CostSpread(min = i + 1.0, median = i + 1.0, max = i + 1.0)
            results[i].buildMicros.median shouldBe ((i + 1) * 128.0 + 500.0 * (i + 1)) / 1000.0
            results[i].allocBytesPerBlock.median shouldBe 8.0 * 128.0
            results[i].rounds shouldBe 3
        }

        val long = 5 * 128
        val short = 128
        val expected = interleavedOrder(count = 3, rounds = 5, seed = 7).flatMap { round -> round.flatMap { i -> listOf(i to long, i to short) } }

        visits shouldContainExactly expected
    }

    "deriveCostRows: net = row minus its baseline control, ratios to the control and to the sine's net, the real-time share, the net bytes" {
        fun result(ns: Double, bytes: Double): CostResult = CostResult(
            nsPerSample = CostSpread(min = ns, median = ns, max = ns),
            buildMicros = CostSpread(min = 1.0, median = 1.0, max = 1.0),
            allocBytesPerBlock = CostSpread(min = bytes, median = bytes, max = bytes),
            rounds = 1,
        )

        fun case(family: String, stage: String, baseline: String?): StageCostCase =
            StageCostCase(family = family, stage = stage, knobs = "-", baseline = baseline, probe = ProbeStage { ProbeOutput(left = DoubleArray(0), right = null) })

        val cases = listOf(
            case(family = "control", stage = "a", baseline = null),
            case(family = "control", stage = "b", baseline = null),
            case(family = COST_SOURCE, stage = COST_SINE, baseline = "a"),
            case(family = "katalyst", stage = "x", baseline = "b"),
            case(family = "katalyst", stage = "orphan", baseline = "missing"),
        )
        val results = listOf(
            result(ns = 10.0, bytes = 1024.0),
            result(ns = 20.0, bytes = 2048.0),
            result(ns = 14.0, bytes = 1024.0),
            result(ns = 28.0, bytes = 2100.0),
            result(ns = 30.0, bytes = 0.0),
        )
        val rows = deriveCostRows(cases = cases, results = results, sampleRate = 48000)

        rows[0].netNsPerSample shouldBe 10.0
        rows[0].ratioToControl.isNaN() shouldBe true
        rows[0].netAllocBytesPerBlock shouldBe 1024.0
        rows[2].netNsPerSample shouldBe 4.0
        rows[2].ratioToSine shouldBe 1.0
        rows[3].netNsPerSample shouldBe 8.0
        rows[3].ratioToControl shouldBe 1.4
        rows[3].ratioToSine shouldBe 2.0
        rows[3].realtimeShare shouldBe (8.0 / (1e9 / 48000) plusOrMinus 1e-15)
        rows[3].netAllocBytesPerBlock shouldBe 52.0
        rows[4].netNsPerSample.isNaN() shouldBe true
        rows[4].netAllocBytesPerBlock.isNaN() shouldBe true
    }

    "sortedByCost: the most expensive first, a NaN cost last" {
        fun row(stage: String, net: Double): StageCostRow = StageCostRow(
            case = StageCostCase(family = "f", stage = stage, knobs = "-", baseline = null, probe = ProbeStage { ProbeOutput(left = DoubleArray(0), right = null) }),
            result = CostResult(
                nsPerSample = CostSpread(min = net, median = net, max = net),
                buildMicros = CostSpread(min = 0.0, median = 0.0, max = 0.0),
                allocBytesPerBlock = CostSpread(min = 0.0, median = 0.0, max = 0.0),
                rounds = 1,
            ),
            netNsPerSample = net,
            realtimeShare = 0.0,
            ratioToControl = 1.0,
            ratioToSine = 1.0,
            netAllocBytesPerBlock = 0.0,
        )

        val sorted = sortedByCost(listOf(row("cheap", 1.0), row("unknown", Double.NaN), row("dear", 9.0), row("middle", 5.0)))

        sorted.map { it.case.stage } shouldContainExactly listOf("dear", "middle", "cheap", "unknown")
    }

    "JSON writer: quotes, backslashes and control characters escaped, NaN and the infinities as strings, maps in their own order" {
        val value = linkedMapOf(
            "name" to "a \"quoted\" \\ name\n\u0001",
            "nan" to Double.NaN,
            "inf" to Double.POSITIVE_INFINITY,
            "minusInf" to Double.NEGATIVE_INFINITY,
            "x" to 1.5,
            "small" to 1e-5,
            "count" to 3,
            "flag" to true,
            "none" to null,
            "list" to listOf(1, "s"),
        )

        jsonValue(value) shouldBe
            """{"name": "a \"quoted\" \\ name\n\u0001", "nan": "NaN", "inf": "Infinity", "minusInf": "-Infinity", "x": 1.5, """ +
            """"small": 1.0E-5, "count": 3, "flag": true, "none": null, "list": [1, "s"]}"""
        jsonRows(listOf(mapOf("a" to 1), mapOf("a" to 2))) shouldBe "[\n  {\"a\": 1},\n  {\"a\": 2}\n]\n"
        jsonRows(emptyList()) shouldBe "[]\n"
    }

    "stage probe JSON row: the case and signal names, every OutputFacts field by name, a NaN and an infinity as strings, crossed null for a mono stage" {
        val facts = OutputFacts(
            frames = 128, peak = 0.5, inputPeak = 1.0, dcInput = Double.NaN, dcEnd = 0.0, nonFiniteCount = 0, firstNonFiniteFrame = NEVER,
            lastNonFiniteFrame = NEVER, finiteAgainFrames = 0, tailFrames = 3, zeroFrames = NEVER, subnormalCount = 0, firstSubnormalFrame = NEVER,
            minTailAbs = Double.POSITIVE_INFINITY,
        )
        val case = StageProbeCase(family = "ignitor", stage = "say \"hi\"", knobs = "k=1", probe = ProbeStage { ProbeOutput(left = DoubleArray(0), right = null) })
        val signal = ProbeSignal(name = "impulse", left = DoubleArray(128), right = DoubleArray(128), inputEndFrame = 1)
        val row = stageProbeJsonRow(StageProbeRow(case = case, signal = signal, facts = StageFacts(left = facts, right = null)))

        row.keys.toList() shouldContainExactly listOf(
            "family", "stage", "knobs", "signal", "frames", "peak", "inputPeak", "dcInput", "dcEnd", "nonFiniteCount", "firstNonFiniteFrame",
            "lastNonFiniteFrame", "finiteAgainFrames", "tailFrames", "zeroFrames", "subnormalCount", "firstSubnormalFrame", "minTailAbs", "gainDb",
            "allFinite", "crossed",
        )
        jsonValue(row) shouldBe
            """{"family": "ignitor", "stage": "say \"hi\"", "knobs": "k=1", "signal": "impulse", "frames": 128, "peak": 0.5, "inputPeak": 1.0, """ +
            """"dcInput": "NaN", "dcEnd": 0.0, "nonFiniteCount": 0, "firstNonFiniteFrame": -1, "lastNonFiniteFrame": -1, "finiteAgainFrames": 0, """ +
            """"tailFrames": 3, "zeroFrames": -1, "subnormalCount": 0, "firstSubnormalFrame": -1, "minTailAbs": "Infinity", "gainDb": """ +
            """${facts.gainDb}, "allFinite": true, "crossed": null}"""

        val poisoned = facts.copy(nonFiniteCount = 2)
        val stereo = stageProbeJsonRow(StageProbeRow(case = case, signal = signal, facts = StageFacts(left = facts, right = poisoned)))

        stereo["crossed"] shouldBe true
        stereo["nonFiniteCount"] shouldBe 2
    }

    "jsonTwin: the JSON file beside the text table" {
        jsonTwin(File("/x/probes/stage-cost.txt")) shouldBe File("/x/probes/stage-cost.json")
        jsonTwin(File("/x/out")) shouldBe File("/x/out.json")
    }
})
