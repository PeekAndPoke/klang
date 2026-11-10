/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.SampleIgnitor
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.buildExciter
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.common.infra.toKlangMap
import java.lang.management.ManagementFactory
import kotlin.random.Random
import com.sun.management.ThreadMXBean as HotSpotThreadMXBean

// The stage cost table (KLANG-00420, probe 7): what one source or stage costs alone, in nanoseconds per sample, on the
// JVM. A measurement, never a gate: these functions assert nothing about the numbers, and the timings come from the
// machine they run on. The method (audio/ref/verification.md, "A measurement"): steady input, the pinned block,
// a warm-up, rounds that interleave every case in a fresh order, the median and the spread, a control row.

/** The median of [values] (the mean of the middle two for an even count); NaN for none. [values] is not changed. */
fun median(values: DoubleArray): Double {
    if (values.isEmpty()) {
        return Double.NaN
    }

    val sorted = values.sortedArray()
    val mid = sorted.size / 2

    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
}

/** The min, median and max of [values]; NaN everywhere for none. */
fun spreadOf(values: DoubleArray): CostSpread {
    if (values.isEmpty()) {
        return CostSpread(min = Double.NaN, median = Double.NaN, max = Double.NaN)
    }

    return CostSpread(min = values.min(), median = median(values), max = values.max())
}

/**
 * The visiting order of [count] cases over [rounds] rounds: each round is a permutation of `0 until count`, shuffled
 * from [seed] and the round's index, so no case always runs right after the same neighbour and the same seed gives the
 * same order.
 */
fun interleavedOrder(count: Int, rounds: Int, seed: Int): List<IntArray> {
    val random = Random(seed)

    return List(rounds) {
        val order = IntArray(count) { it }

        // Fisher-Yates.
        for (i in count - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            val t = order[i]
            order[i] = order[j]
            order[j] = t
        }

        order
    }
}

/**
 * The steady input of the cost table: [blocks] blocks of full-scale white noise (uniform -1..1, seeded, a different
 * seed per channel), held: the input never ends inside the render, so an Ignitor stage's gate stays open (a held note).
 */
fun costSignal(blocks: Int, blockFrames: Int = PROBE_BLOCK_FRAMES, seed: Int = 0x5EED): ProbeSignal {
    val frames = blocks * blockFrames
    val rngLeft = Random(seed)
    val rngRight = Random(seed + 1)
    val left = DoubleArray(frames) { rngLeft.nextDouble() * 2.0 - 1.0 }
    val right = DoubleArray(frames) { rngRight.nextDouble() * 2.0 - 1.0 }

    return ProbeSignal(name = "noise-held", left = left, right = right, inputEndFrame = frames)
}

/** The wall clock in nanoseconds, as a Double at once (a Long only at this boundary). */
fun nanoNow(): Double = System.nanoTime().toDouble()

private val hotSpotThreads: HotSpotThreadMXBean? = (ManagementFactory.getThreadMXBean() as? HotSpotThreadMXBean)
    ?.takeIf { it.isThreadAllocatedMemorySupported && it.isThreadAllocatedMemoryEnabled }

/**
 * The bytes this thread has allocated so far (HotSpot's per-thread counter, which counts every TLAB and outside
 * allocation), as a Double at once; NaN on a JVM without the counter.
 */
fun allocatedBytesNow(): Double = hotSpotThreads?.currentThreadAllocatedBytes?.toDouble() ?: Double.NaN

/** Keeps the renders' results alive, so the JIT cannot drop a render whose output nobody reads. */
@Volatile
private var costSink: Double = 0.0

/**
 * Times every stage of [stages] together and hands back one [CostResult] each, in the order given: [config]'s
 * warm-up rounds, then its timed rounds, every round visiting every stage once in its own order
 * ([interleavedOrder]). A visit renders the long signal, then the short one, each between two readings of [clock]
 * (nanoseconds) and [bytes] (the thread's allocated bytes); a fresh stage instance per render (the [ProbeStage]
 * contract), so the short render carries the build. Per round: ns per sample = (long - short) / the frames between
 * them, build = the short render, bytes per block = (long - short) / the blocks between them.
 *
 * [clock] and [bytes] are parameters so a spec can drive the arithmetic with a fake clock; the runner keeps the
 * defaults.
 */
fun measureCosts(
    stages: List<ProbeStage>,
    config: CostConfig = CostConfig(),
    clock: () -> Double = ::nanoNow,
    bytes: () -> Double = ::allocatedBytesNow,
): List<CostResult> {
    require(config.blocksLong > config.blocksShort) { "the long render must be longer than the short one" }

    val long = costSignal(blocks = config.blocksLong, blockFrames = config.blockFrames)
    val short = costSignal(blocks = config.blocksShort, blockFrames = config.blockFrames)
    val framesBetween = ((config.blocksLong - config.blocksShort) * config.blockFrames).toDouble()
    val blocksBetween = (config.blocksLong - config.blocksShort).toDouble()
    val nsPerSample = List(stages.size) { DoubleArray(config.rounds) }
    val buildMicros = List(stages.size) { DoubleArray(config.rounds) }
    val allocPerBlock = List(stages.size) { DoubleArray(config.rounds) }
    val order = interleavedOrder(count = stages.size, rounds = config.warmupRounds + config.rounds, seed = config.seed)

    for ((round, visits) in order.withIndex()) {
        val timed = round - config.warmupRounds

        for (index in visits) {
            val longRun = timedRender(stage = stages[index], signal = long, clock = clock, bytes = bytes)
            val shortRun = timedRender(stage = stages[index], signal = short, clock = clock, bytes = bytes)

            if (timed >= 0) {
                nsPerSample[index][timed] = (longRun.nanos - shortRun.nanos) / framesBetween
                buildMicros[index][timed] = shortRun.nanos / 1000.0
                allocPerBlock[index][timed] = (longRun.bytes - shortRun.bytes) / blocksBetween
            }
        }
    }

    return stages.indices.map { index ->
        CostResult(
            nsPerSample = spreadOf(nsPerSample[index]),
            buildMicros = spreadOf(buildMicros[index]),
            allocBytesPerBlock = spreadOf(allocPerBlock[index]),
            rounds = config.rounds,
        )
    }
}

/** One stage alone through [measureCosts]: the function a guard spec of one stage calls. */
fun measureCost(stage: ProbeStage, config: CostConfig = CostConfig()): CostResult = measureCosts(stages = listOf(stage), config = config).single()

/** One timed render: its wall time in [nanos] and what the thread allocated meanwhile in [bytes]. */
private class TimedRender(
    val nanos: Double,
    val bytes: Double,
)

/**
 * Renders [signal] through [stage] between two readings of [clock] and two of [bytes], the readings of [bytes] outside
 * the clock's. The result object is made after the last reading, so neither window counts it.
 */
private fun timedRender(stage: ProbeStage, signal: ProbeSignal, clock: () -> Double, bytes: () -> Double): TimedRender {
    val bytesBefore = bytes()
    val start = clock()

    sink(stage.render(signal))

    val end = clock()
    val bytesAfter = bytes()

    return TimedRender(nanos = end - start, bytes = bytesAfter - bytesBefore)
}

private fun sink(output: ProbeOutput) {
    val left = output.left

    if (left.isNotEmpty()) {
        costSink += left[left.size - 1]
    }
}

/**
 * The cost table's finished rows, in [cases] order: each case with its [results] entry (the same index) and what it
 * reads against its baseline and against the plain sine, the case of family [COST_SOURCE] and stage [COST_SINE].
 * A control's net cost is its raw cost; a row whose baseline is missing reads NaN for every comparison.
 */
fun deriveCostRows(cases: List<StageCostCase>, results: List<CostResult>, sampleRate: Int = PROBE_SAMPLE_RATE): List<StageCostRow> {
    require(cases.size == results.size) { "one result per case" }

    val byControl = cases.indices
        .filter { cases[it].baseline == null }
        .associate { cases[it].stage to results[it] }
    val samplePeriodNs = 1e9 / sampleRate

    fun net(index: Int): Double {
        val baseline = cases[index].baseline ?: return results[index].nsPerSample.median
        val control = byControl[baseline] ?: return Double.NaN

        return results[index].nsPerSample.median - control.nsPerSample.median
    }

    val sineIndex = cases.indexOfFirst { it.family == COST_SOURCE && it.stage == COST_SINE }
    val sineNet = if (sineIndex >= 0) net(sineIndex) else Double.NaN

    return cases.indices.map { index ->
        val case = cases[index]
        val result = results[index]
        val control = case.baseline?.let { byControl[it] }
        val rowNet = net(index)

        StageCostRow(
            case = case,
            result = result,
            netNsPerSample = rowNet,
            realtimeShare = rowNet / samplePeriodNs,
            ratioToControl = if (control != null) result.nsPerSample.median / control.nsPerSample.median else Double.NaN,
            ratioToSine = rowNet / sineNet,
            netAllocBytesPerBlock = if (case.baseline == null) {
                result.allocBytesPerBlock.median
            } else {
                result.allocBytesPerBlock.median - (control?.allocBytesPerBlock?.median ?: Double.NaN)
            },
        )
    }
}

/**
 * The control without an engine: the signal copied block by block into fresh output arrays, as the Katalyst adapter
 * copies it in and out ([katalystProbeStage]), with nothing in between. The floor every other row stands on.
 */
fun copyProbeStage(blockFrames: Int = PROBE_BLOCK_FRAMES): ProbeStage = ProbeStage { signal ->
    val mixLeft = DoubleArray(blockFrames)
    val mixRight = DoubleArray(blockFrames)
    val left = DoubleArray(signal.frames)
    val right = DoubleArray(signal.frames)
    var at = 0

    while (at < signal.frames) {
        signal.left.copyInto(destination = mixLeft, destinationOffset = 0, startIndex = at, endIndex = at + blockFrames)
        signal.right.copyInto(destination = mixRight, destinationOffset = 0, startIndex = at, endIndex = at + blockFrames)
        mixLeft.copyInto(destination = left, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
        mixRight.copyInto(destination = right, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
        at += blockFrames
    }

    ProbeOutput(left = left, right = right)
}

/**
 * An Ignitor tree over the engine's own sample playhead ([SampleIgnitor], as `VoiceFactory` builds it for a sample
 * voice), mono. Built and driven exactly as [ignitorProbeStage] drives its tree; the one difference is the
 * `sampleSource`: [ignitorProbeStage] plays the probe signal through a plain array reader, which has no interpolation,
 * so a sample voice's cost needs this adapter. [pcm] is the sample (played from frame 0, not looped, up to its end)
 * at playback [rate] (1.0 = its own pitch; any other rate interpolates); the probe signal only sets the gate.
 */
fun sampleVoiceProbeStage(
    tree: IgnitorDsl,
    pcm: DoubleArray,
    rate: Double,
    params: Map<String, Double> = emptyMap(),
    freqHz: Double = COST_NOTE_HZ,
    sampleRate: Int = PROBE_SAMPLE_RATE,
    blockFrames: Int = PROBE_BLOCK_FRAMES,
): ProbeStage = ProbeStage { signal ->
    val playhead = SampleIgnitor(
        pcm = pcm,
        rate = rate,
        playhead = 0.0,
        loopStart = -1.0,
        loopEnd = -1.0,
        isLooping = false,
        stopFrame = pcm.size.toDouble(),
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rng = Random(7),
    )
    val built = tree.buildExciter(
        ignitorParams = params.toKlangMap(),
        random = Random(7),
        freqHz = freqHz,
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        sampleSource = playhead,
    )
    val gate = maxOf(1, signal.inputEndFrame)
    val ctx = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = gate,
        gateEndFrame = gate,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = Random(7),
    )
    val buffer = AudioBuffer(blockFrames)
    val left = DoubleArray(signal.frames)
    var at = 0

    while (at < signal.frames) {
        ctx.voiceElapsedFrames = at
        ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
        built.ignitor.generate(buffer, freqHz, ctx)
        buffer.copyInto(destination = left, destinationOffset = at, startIndex = 0, endIndex = blockFrames)
        at += blockFrames
    }

    ProbeOutput(left = left, right = null)
}
