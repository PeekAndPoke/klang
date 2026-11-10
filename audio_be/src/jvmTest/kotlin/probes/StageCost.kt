/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

/**
 * One row of the stage cost table (KLANG-00420, probe 7): [stage] at the knob set [knobs], of [family] (`control`,
 * `source`, `ignitor`, `katalyst`, `voice`), rendered by [probe]. [baseline] names the `control` row whose cost is the
 * harness this row runs in (its stage name), subtracted for the row's net cost; null for a control itself.
 */
class StageCostCase(
    val family: String,
    val stage: String,
    val knobs: String,
    val baseline: String?,
    val probe: ProbeStage,
)

/**
 * How [measureCosts] times: every case renders a LONG signal of [blocksLong] blocks and a SHORT one of [blocksShort]
 * blocks per visit; the difference of the two is the steady per-block work, without the build. [warmupRounds] rounds
 * over every case first (for the JIT, nothing recorded), then [rounds] timed rounds, each in its own order ([seed]).
 */
data class CostConfig(
    val blocksLong: Int = 375,
    val blocksShort: Int = 1,
    val warmupRounds: Int = 40,
    val rounds: Int = 41,
    val seed: Int = 0x420,
    val sampleRate: Int = PROBE_SAMPLE_RATE,
    val blockFrames: Int = PROBE_BLOCK_FRAMES,
)

/** The [min], [median] and [max] of one measured quantity over the timed rounds. */
data class CostSpread(
    val min: Double,
    val median: Double,
    val max: Double,
) {
    /** (max - min) / median, NaN when the median is 0. */
    val relativeWidth: Double get() = if (median != 0.0) (max - min) / median else Double.NaN
}

/**
 * What [measureCosts] read for one case: [nsPerSample] the steady cost (long render minus short render, per sample),
 * [buildMicros] the short render (the build and the first block), [allocBytesPerBlock] the bytes the thread allocated
 * per block in steady state (long minus short, per block; NaN where the JVM cannot count them), over [rounds] rounds.
 */
data class CostResult(
    val nsPerSample: CostSpread,
    val buildMicros: CostSpread,
    val allocBytesPerBlock: CostSpread,
    val rounds: Int,
)

/**
 * One row of the finished table: [case], its [result], and what it reads against the others. [netNsPerSample] is
 * the row's median minus its baseline's ([StageCostCase.baseline]; the raw median for a control), [realtimeShare]
 * that net cost over one sample period (1.0 = a whole real-time core for this one voice or bus), [ratioToControl]
 * the raw median over the baseline's raw median, [ratioToSine] the net over the plain sine's net,
 * [netAllocBytesPerBlock] the allocation above the baseline's.
 */
class StageCostRow(
    val case: StageCostCase,
    val result: CostResult,
    val netNsPerSample: Double,
    val realtimeShare: Double,
    val ratioToControl: Double,
    val ratioToSine: Double,
    val netAllocBytesPerBlock: Double,
)
