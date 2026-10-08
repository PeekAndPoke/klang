/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import java.lang.management.ManagementFactory
import kotlin.random.Random

/**
 * Tidy-up step 10: what a voice allocated on its FIRST block, and when its voice count changed, moved to the build.
 * A node is built (not measured), then its first block is measured against the blocks after it, by the bytes this
 * thread allocates (`com.sun.management.ThreadMXBean`).
 *
 * **Signals for every knob, a cached box for every count.** A block-constant knob answers
 * `Ignitor.controlRateValueOrNull`, a `Double?` the JVM boxes (24 bytes) wherever the JIT does not inline it away, and
 * how often it does differs from run to run, so a block of a [ConstantIgnitor]-built voice costs 0, 24 or 48 bytes,
 * and its first block, which reads `analog` once more, one box more. Kotlin/JS has no box there. So the nodes here
 * are built from the Kotlin factories with every knob a [Hold] (a constant the build cannot read, which every reader
 * renders) and every count a [Count] (block-constant, so the build sizes the node from it, answering with one box
 * made up front). Nothing is boxed, and a node that allocates nothing after its build takes exactly 0 bytes, its
 * first block included.
 *
 * Not covered, because they still allocate at render (recorded in `docs/tasks/engine-tidy-up.md` step 10): a count
 * signal's, or a count that reads the note's frequency, first rise past what the build sized (the rows with [Steps] start at their maximum, which the first block
 * grows to), the shared drift lane's own `Random` below spread 1, and the phase pool's vocabulary while it grows.
 */
class FirstBlockAllocationSpec : StringSpec({

    val mx = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
    val blockFrames = 128
    val scratch = ScratchBuffers(blockFrames = blockFrames, initialCapacity = 16).apply { ensureCapacity(depth = 16, doubleDepth = 8) }

    fun bytes(): Double = mx.currentThreadAllocatedBytes.toDouble()

    /** One voice: one stream for the node and the context, as production wires it. */
    class Voice(val ignitor: Ignitor, random: Random, scratch: ScratchBuffers) {
        val ctx = IgniteContext(sampleRate = 48000, voiceDurationFrames = 1 shl 20, gateEndFrame = 1 shl 20, scratchBuffers = scratch, random = random)
        val buf = AudioBuffer(128)

        fun render() {
            ctx.updateOffsetAndLength(offset = 0, length = 128)
            ignitor.generate(buf, 220.0, ctx)
            ctx.voiceElapsedFrames += 128
        }
    }

    /**
     * [build]'s node over twelve notes: its first block, and the 64 blocks after it. The first six notes warm the JIT
     * and load the classes; over the last six, the fewest bytes any first block took and the fewest any steady block
     * took must both be 0. The fewest, because a one-off (a JIT recompilation in the middle of a note, seen once at
     * 432 bytes) lands in one note, while a node that allocates does so in every note.
     */
    fun allocatesNothingAfterBuild(name: String, build: (random: Random) -> Ignitor) {
        var fewestFirst = Double.MAX_VALUE
        var fewestSteady = Double.MAX_VALUE

        for (note in 0 until 12) {
            val random = Random(100 + note)
            val voice = Voice(ignitor = build(random), random = random, scratch = scratch)
            val b0 = bytes()

            voice.render()

            val b1 = bytes()

            repeat(64) {
                voice.render()
            }

            val b2 = bytes()

            if (note >= 6) {
                fewestFirst = minOf(fewestFirst, b1 - b0)
                fewestSteady = minOf(fewestSteady, b2 - b1)
            }
        }

        withClue("$name: the first block took $fewestFirst bytes, 64 blocks after it $fewestSteady") {
            fewestFirst shouldBe 0.0
            fewestSteady shouldBe 0.0
        }
    }

    /**
     * [build]'s node with a count that changes within its maximum (the first block grows to it, which is not
     * measured): over the last four of eight notes, the fewest bytes the 31 blocks of changes took must be 0. A
     * node that re-allocated its states on a change would allocate in every note.
     */
    fun changesWithinMaxAllocateNothing(name: String, build: (voices: Ignitor, random: Random) -> Ignitor) {
        var fewest = Double.MAX_VALUE

        for (note in 0 until 8) {
            val random = Random(200 + note)
            // 7 first (the maximum), then down and back up within it, every block another count.
            val values = intArrayOf(7, 3, 6, 1, 7, 2, 5, 4)
            val voice = Voice(ignitor = build(Steps(values), random), random = random, scratch = scratch)

            voice.render()

            val b0 = bytes()

            repeat(31) {
                voice.render()
            }

            val b1 = bytes()

            if (note >= 4) {
                fewest = minOf(fewest, b1 - b0)
            }
        }

        withClue("$name: 31 blocks of count changes took $fewest bytes") {
            fewest shouldBe 0.0
        }
    }

    /** The fewest bytes [build] takes over 20 builds, after 20 to warm up: the build's own cost, deterministic. */
    fun buildBytes(build: () -> Any): Double {
        var fewest = Double.MAX_VALUE

        for (i in 0 until 40) {
            val b0 = bytes()
            val built = build()
            val b1 = bytes()

            if (i >= 20) {
                fewest = minOf(fewest, b1 - b0)
            }

            built.hashCode()
        }

        return fewest
    }

    val freq = Hold(220.0)
    val off = Hold(0.0)
    val one = Hold(1.0)
    val drift = Hold(0.7)

    "drift lanes, mono oscillators: the lane is built with the node and seeded at the first block" {
        allocatesNothingAfterBuild("sine") { Ignitors.sine(freq = freq, analog = drift) }
        allocatesNothingAfterBuild("saw") { Ignitors.saw(freq = freq, analog = drift) }
        allocatesNothingAfterBuild("impulse") { Ignitors.impulse(freq = freq, analog = drift) }
        allocatesNothingAfterBuild("pluck") { random ->
            Ignitors.karplusStrong(freq = freq, decay = Hold(0.996), brightness = Hold(0.5), pickPosition = Hold(0.5), stiffness = off, analog = drift, rng = random)
        }
    }

    "the phaser's kernel is built with the node" {
        allocatesNothingAfterBuild("phaser") {
            Ignitors.saw(freq = freq, analog = off).phaser(wet = Hold(0.6), rate = Hold(0.7), center = Hold(1000.0), sweep = Hold(1000.0), floor = off)
        }
    }

    "a memo's cache is built when the memo starts caching" {
        allocatesNothingAfterBuild("a memo with two readers") {
            val shared = MemoizingIgnitor(Ignitors.sine(freq = Hold(3.0), analog = off)).also { it.incConsumers(blockFrames) }

            Ignitors.saw(freq = freq, analog = off) * shared + Ignitors.sine(freq = freq, analog = off) * shared
        }
        allocatesNothingAfterBuild("a memo that caches per block") {
            val mod = MemoizingIgnitor(Ignitors.sine(freq = Hold(5.0), analog = off)).also { it.cachePerBlock(blockFrames) }

            Ignitors.saw(freq = freq, analog = off) * mod
        }
    }

    "the partial bank's arrays and drift lanes are built with the node, and a count change within them allocates nothing" {
        allocatesNothingAfterBuild("sine partials") {
            Ignitors.sinePartials(
                countsAtBuild = true,
                freq = freq, analog = drift, fundamental = one, harmonics = Count(3.0), harmonicsRolloff = one,
                octaves = Count(2.0), octavesRolloff = one, suboctaves = Count(1.0), suboctavesRolloff = one, analogSpread = one,
            )
        }
        allocatesNothingAfterBuild("sine partials, no drift") {
            Ignitors.sinePartials(
                countsAtBuild = true,
                freq = freq, analog = off, fundamental = one, harmonics = Count(4.0), harmonicsRolloff = one,
                octaves = Count(1.0), octavesRolloff = one, suboctaves = Count(2.0), suboctavesRolloff = one, analogSpread = one,
            )
        }
        changesWithinMaxAllocateNothing("sine partials") { count, _ ->
            Ignitors.sinePartials(
                countsAtBuild = true,
                freq = freq, analog = drift, fundamental = one, harmonics = count, harmonicsRolloff = one,
                octaves = off, octavesRolloff = one, suboctaves = off, suboctavesRolloff = one, analogSpread = one,
            )
        }
    }

    "the unison stack's voice states, scratch and drift lanes are built with the node, and a count change within them allocates nothing" {
        allocatesNothingAfterBuild("supersaw") { random ->
            Ignitors.superSaw(countsAtBuild = true, freq = freq, voices = Count(7.0), detune = Hold(0.2), analog = drift, analogSpread = one, rng = random)
        }
        allocatesNothingAfterBuild("supersaw, no drift") { random ->
            Ignitors.superSaw(countsAtBuild = true, freq = freq, voices = Count(7.0), detune = Hold(0.2), analog = off, analogSpread = one, rng = random)
        }
        allocatesNothingAfterBuild("supersaw, stateless banded phases") { random ->
            Ignitors.superSaw(countsAtBuild = true, freq = freq, voices = Count(7.0), detune = Hold(0.2), analog = off, analogSpread = one, rng = random, phasePool = 1.0)
        }
        changesWithinMaxAllocateNothing("supersaw") { voices, random ->
            Ignitors.superSaw(countsAtBuild = true, freq = freq, voices = voices, detune = Hold(0.2), analog = drift, analogSpread = one, rng = random)
        }
    }

    "the superpluck's strings and drift lanes are built with the node, and a count change within them allocates nothing" {
        allocatesNothingAfterBuild("superpluck") { random ->
            Ignitors.superKarplusStrong(
                countsAtBuild = true,
                freq = freq, voices = Count(4.0), detune = Hold(0.2), decay = Hold(0.996), brightness = Hold(0.5), pickPosition = Hold(0.5),
                stiffness = off, analog = drift, analogSpread = one, rng = random,
            )
        }
        allocatesNothingAfterBuild("superpluck, no drift") { random ->
            Ignitors.superKarplusStrong(
                countsAtBuild = true,
                freq = freq, voices = Count(4.0), detune = Hold(0.2), decay = Hold(0.996), brightness = Hold(0.5), pickPosition = Hold(0.5),
                stiffness = off, analog = off, analogSpread = one, rng = random,
            )
        }
        changesWithinMaxAllocateNothing("superpluck") { voices, random ->
            Ignitors.superKarplusStrong(
                countsAtBuild = true,
                freq = freq, voices = voices, detune = Hold(0.2), decay = Hold(0.996), brightness = Hold(0.5), pickPosition = Hold(0.5),
                stiffness = off, analog = drift, analogSpread = one, rng = random,
            )
        }
    }

    "what the build can rule out it does not build: drift at analog 0, the shared lane at spread 1, strings for a count that reads the frequency" {
        // A wave at analog 0 builds no lane (review round 1: it allocated nothing before step 10 either).
        val sawDry = buildBytes { Ignitors.saw(freq = freq, analog = Count(0.0)) }
        val sawDrifting = buildBytes { Ignitors.saw(freq = freq, analog = Count(0.7)) }

        withClue("saw: built at analog 0 $sawDry bytes, at analog 0.7 $sawDrifting") { sawDry shouldBeLessThan sawDrifting }

        // A stack at analog 0 builds no drift container; at spread 1, where the shared walk is never read, no shared lane.
        fun stack(analog: Double, spread: Double) = buildBytes {
            Ignitors.superSaw(countsAtBuild = true, freq = freq, voices = Count(5.0), analog = Count(analog), analogSpread = Count(spread), rng = Random(1))
        }

        val dry = stack(analog = 0.0, spread = 1.0)
        val ownLanes = stack(analog = 0.5, spread = 1.0)
        val withShared = stack(analog = 0.5, spread = 0.5)

        withClue("supersaw: built at analog 0 $dry bytes, at analog 0.5 $ownLanes") { dry shouldBeLessThan ownLanes }
        withClue("supersaw: built at spread 1 $ownLanes bytes, at spread 0.5 $withShared") { ownLanes shouldBeLessThan withShared }

        // A count that reads the frequency is not sized at frequency 0 (review round 1: `30 - 0.05 * freq` built 30
        // strings for a note that plays 8); the build leaves it to the first block. A count that does not is sized.
        fun superpluck(voices: IgnitorDsl) = buildBytes {
            IgnitorDsl.SuperPluck(voices = voices).buildExciter(random = Random(1), freqHz = 440.0, sampleRate = 48000, blockFrames = blockFrames)
        }

        val string = 2500 * 8.0
        val freqFree = superpluck(IgnitorDsl.Constant(4.0))
        val freqReading = superpluck(IgnitorDsl.Minus(IgnitorDsl.Constant(30.0), IgnitorDsl.Times(IgnitorDsl.Freq, IgnitorDsl.Constant(0.05))))

        withClue("superpluck, 4 voices: built $freqFree bytes") { freqFree shouldBeGreaterThan 4 * string }
        withClue("superpluck, 30 - 0.05 * freq voices: built $freqReading bytes") { freqReading shouldBeLessThan string }
    }

    "the phase pool: the selection is parsed at build, and a lookup allocates no key" {
        // A full, frozen pool (warmup fills it at creation, refresh 0, made by the first note), so a note grows nothing.
        val pools = PhasePools(Random(99))

        allocatesNothingAfterBuild("pooled supersaw") { random ->
            Ignitors.superSaw(countsAtBuild = true, 
                freq = freq, voices = Count(7.0), detune = Hold(0.2), analog = off, analogSpread = one, rng = random,
                phasePool = 1.0, poolSize = 4.0, warmup = 4.0, refreshEvery = 0.0, selection = "normal:0.3:0.1", phasePools = pools, orbit = 1,
            )
        }
    }
})

/**
 * A block-constant count the build can read, which answers [controlRateValueOrNull] with ONE box made here: a read
 * returns that box and allocates nothing (a [ConstantIgnitor] boxes per read on the JVM, see the class KDoc).
 */
private class Count(value: Double) : Ignitor {
    private val boxed: Double? = value

    override val isBlockConstant: Boolean = true

    override fun controlRateValueOrNull(freqHz: Double): Double? = boxed

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val v = boxed ?: 0.0

        for (i in ctx.offset until ctx.windowEnd) {
            buffer[i] = v
        }
    }
}

/** A constant the build cannot read: not block-constant, so every reader renders it, and nothing is boxed. */
private class Hold(private val value: Double) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        for (i in ctx.offset until ctx.windowEnd) {
            buffer[i] = value
        }
    }
}

/**
 * A count that steps through [values], one value per block, cycling. Not block-constant, so the build cannot read it
 * (the node sizes nothing for it) and every reader renders it (no box).
 */
private class Steps(private val values: IntArray) : Ignitor {
    private var block = 0
    private var lastElapsed = -1

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        if (ctx.voiceElapsedFrames != lastElapsed) {
            lastElapsed = ctx.voiceElapsedFrames
            block++
        }

        val v = values[(block - 1) % values.size].toDouble()

        for (i in ctx.offset until ctx.windowEnd) {
            buffer[i] = v
        }
    }
}
