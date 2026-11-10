/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

private const val B: Int = PROBE_BLOCK_FRAMES

/** The synthetic notes' attack, release and cut fade, in frames. */
private const val ATTACK: Double = 1000.0
private const val RELEASE: Double = 500.0
private const val CUT_FADE: Double = 200.0

/** The per-block law's step: the gain is 1 on even blocks, 1 + D on odd ones. */
private const val D: Double = 0.01

/** A 220 Hz sine's phase step at the probe's rate. */
private val W: Double = 2.0 * PI * 220.0 / PROBE_SAMPLE_RATE

/** The invariant note's attack at note frame [t]: linear over [ATTACK] frames, 0 before the onset. */
private fun attackAt(t: Int): Double = (t / ATTACK).coerceIn(0.0, 1.0)

/** The note's gate release and cut fade at note frame [j]: linear over [RELEASE] and [CUT_FADE] frames. */
private fun endingAt(j: Int, note: FramingNote): Double {
    var g = 1.0

    if (j >= note.gateFrames) {
        g *= (1.0 - (j - note.gateFrames) / RELEASE).coerceAtLeast(0.0)
    }

    if (note.cutFrame != FRAMING_NONE && j >= note.cutFrame) {
        g *= (1.0 - (j - note.cutFrame) / CUT_FADE).coerceAtLeast(0.0)
    }

    return g
}

/** The invariant note's level at note frame [j]: the attack, the gate's linear release, a cut's linear fade. */
private fun levelAt(j: Int, note: FramingNote): Double = attackAt(j) * endingAt(j, note)

/**
 * A synthetic renderer that runs [fill] once per 128-frame block from frame 0, the way `Voice.render` is driven, into a
 * mono output; [fingerprint] stands in for the voice's rng draw count.
 */
private fun blockRenderer(
    fingerprint: (FramingNote) -> Int? = { 42 },
    fill: (note: FramingNote, blockStart: Int, out: DoubleArray) -> Unit,
): FramingRenderer = FramingRenderer { note ->
    val total = wholeProbeBlocks(note.renderFrames)
    val out = DoubleArray(total)
    var at = 0

    while (at < total) {
        fill(note, at, out)
        at += B
    }

    FramedOutput(left = out, right = null, rngFingerprint = fingerprint(note))
}

/** The first frame of the block at [blockStart] that the note sounds in (its offset, as `Voice.render` computes it). */
private fun windowStart(note: FramingNote, blockStart: Int): Int = maxOf(blockStart, note.onsetFrame)

/** Framing-invariant: every sample a function of its note frame alone. */
private val invariant = blockRenderer { note, bs, out ->
    for (n in windowStart(note, bs) until bs + B) {
        val j = n - note.onsetFrame

        out[n] = levelAt(j, note) * sin(W * j)
    }
}

/** The invariant note that also writes 0.25 one frame BEFORE its onset (an onset placement bug, I1). */
private val leaky = blockRenderer { note, bs, out ->
    if (note.onsetFrame in (bs + 1)..(bs + B)) {
        out[note.onsetFrame - 1] = 0.25
    }

    for (n in windowStart(note, bs) until bs + B) {
        val j = n - note.onsetFrame

        out[n] = levelAt(j, note) * sin(W * j)
    }
}

/**
 * Failure mode (a), KLANG-00156's instance 1: a DC note whose attack clock is read at the BLOCK's start
 * (`blockStart - onset`) but used as the clock at the note's first frame, so the first block's attack runs late by the
 * offset; every later block is right.
 */
private val clockAtBlockStart = blockRenderer { note, bs, out ->
    val offset = maxOf(0, note.onsetFrame - bs)
    val elapsed = bs - note.onsetFrame

    for (n in windowStart(note, bs) until bs + B) {
        val t = elapsed + (n - bs - offset)

        out[n] = attackAt(t) * endingAt(n - note.onsetFrame, note)
    }
}

/**
 * Failure mode (b): a DC note whose gate close (gain 1 to 0.5) and cut (to 0.25) are decided once per block, at the
 * block's start, so each snaps to the first border at or after it.
 */
private val snapsToBorder = blockRenderer { note, bs, out ->
    var gain = if (bs >= note.onsetFrame + note.gateFrames) 0.5 else 1.0

    if (note.cutFrame != FRAMING_NONE && bs >= note.onsetFrame + note.cutFrame) {
        gain = 0.25
    }

    for (n in windowStart(note, bs) until bs + B) {
        out[n] = gain
    }
}

/** The number of block borders strictly inside the note's gate: a draw per border, so the count moves with the onset. */
private fun bordersInGate(note: FramingNote): Int = (note.onsetFrame + note.gateFrames - 1) / B - note.onsetFrame / B

/** A constant per-block law: a DC note whose gain alternates 1 and 1 + [D] with the ABSOLUTE block index (drift's shape). */
private val perBlockLaw = blockRenderer(fingerprint = { bordersInGate(it) }) { note, bs, out ->
    val gain = 1.0 + D * ((bs / B) % 2)

    for (n in windowStart(note, bs) until bs + B) {
        out[n] = gain
    }
}

/** A different summation order: a sine whose phase is reseeded at each block from the note frame, then accumulated. */
private val reseededPhase = blockRenderer { note, bs, out ->
    val start = windowStart(note, bs)
    var phase = W * (start - note.onsetFrame)

    for (n in start until bs + B) {
        out[n] = sin(phase)
        phase += W
    }
}

/**
 * The block-framing checker's MEASURING functions ([measureFraming], [compareFraming], [framingClassOf]) on synthetic
 * renderers driven block by block like `Voice.render`, whose answers are known from their definitions: one that is
 * framing-invariant, one whose clock is read at the block's start (failure mode a), one whose transitions snap to the
 * border (failure mode b), one with a per-block law, one with another summation order. Fast, and asserts nothing about
 * an engine stage.
 */
class FramingCheckerSpec : StringSpec({

    val config = FramingConfig()

    "a framing-invariant note is identical at every offset of every sweep, and a sample before the onset is seen" {
        for (sweep in FramingSweep.entries) {
            val result = measureFraming(renderer = invariant, sweep = sweep, offsets = FRAMING_SHORT_OFFSETS, config = config)

            for (r in result.readings) {
                withClue("$sweep k=${r.offset}") {
                    r.cls shouldBe FramingClass.IDENTICAL
                    r.maxAbsDiff shouldBe 0.0
                    r.firstDiffFrame shouldBe NEVER
                    r.region shouldBe FramingRegion.NONE
                    r.extent shouldBe FramingExtent.NONE
                    r.preOnsetPeak shouldBe 0.0
                    r.rngSame shouldBe true
                    r.referencePeak shouldBeGreaterThan 0.9
                }
            }
        }

        val leak = measureFraming(renderer = leaky, sweep = FramingSweep.ONSET, offsets = listOf(31), config = config).readings.single()

        withClue("leak before the onset") { leak.preOnsetPeak shouldBe 0.25 }
        withClue("leak: the note itself is the same") { leak.cls shouldBe FramingClass.IDENTICAL }
    }

    "a clock read at the block's start: an error of min(k, 127 - k) / attack confined to the first block" {
        val result = measureFraming(renderer = clockAtBlockStart, sweep = FramingSweep.ONSET, offsets = FRAMING_SHORT_OFFSETS, config = config)

        for (r in result.readings) {
            val k = r.offset

            withClue("k=$k") {
                if (k == 0 || k == B - 1) {
                    // At 127 the first block holds note frame 0 alone, where the attack is 0 either way.
                    r.cls shouldBe FramingClass.IDENTICAL
                } else {
                    r.maxAbsDiff shouldBe (minOf(k, B - 1 - k) / ATTACK plusOrMinus 1e-12)
                    r.firstDiffFrame shouldBe 1
                    r.lastDiffFrame shouldBe B - 1 - k
                    r.diffFrames shouldBe B - 1 - k
                    r.region shouldBe FramingRegion.ONSET
                    r.extent shouldBe FramingExtent.CONFINED
                    r.restMaxAbs shouldBe 0.0
                    r.lateMaxAbs shouldBe 0.0
                    r.cls shouldBe FramingClass.AUDIBLE
                }
            }
        }

        // The RMS, from the definitions: note frames 1..30 differ by j / A, 31..96 by 31 / A; the reference is the note.
        val k31 = result.readings.single { it.offset == 31 }
        var diff2 = 0.0
        var ref2 = 0.0
        val note = FramingNote(onsetFrame = 0, gateFrames = config.gateFrames, cutFrame = FRAMING_NONE, renderFrames = 0)

        for (j in 1 until 31) {
            diff2 += (j / ATTACK) * (j / ATTACK)
        }

        diff2 += (B - 1 - 31 - 31 + 1) * (31 / ATTACK) * (31 / ATTACK)

        for (j in 0 until config.spanFrames(FramingSweep.ONSET)) {
            ref2 += levelAt(j, note) * levelAt(j, note)
        }

        withClue("rms re the reference") { k31.rmsRelDb shouldBe (10.0 * log10(diff2 / ref2) plusOrMinus 1e-9) }
    }

    "a transition that snaps to the border: an error confined to the transition block, from the event to the next border" {
        val gate = measureFraming(renderer = snapsToBorder, sweep = FramingSweep.GATE, offsets = FRAMING_SHORT_OFFSETS, config = config)
        val cut = measureFraming(renderer = snapsToBorder, sweep = FramingSweep.CUT, offsets = FRAMING_SHORT_OFFSETS, config = config)

        for ((result, step, base) in listOf(Triple(gate, 0.5, config.gateBaseFrames), Triple(cut, 0.75, config.cutBaseFrames))) {
            for (r in result.readings) {
                val k = r.offset

                withClue("${result.sweep} k=$k") {
                    r.referenceOnset shouldBe (B - k) % B
                    r.eventFrame shouldBe base + k

                    if (k == 0) {
                        r.cls shouldBe FramingClass.IDENTICAL
                    } else {
                        r.maxAbsDiff shouldBe step
                        r.firstDiffFrame shouldBe base + k
                        r.maxDiffFromEvent shouldBe 0
                        r.lastDiffFrame shouldBe base + B - 1
                        r.diffFrames shouldBe B - k
                        r.region shouldBe FramingRegion.TRANSITION
                        r.extent shouldBe FramingExtent.CONFINED
                        r.lateMaxAbs shouldBe 0.0
                        r.cls shouldBe FramingClass.AUDIBLE
                    }
                }
            }
        }
    }

    "a constant per-block law: a difference that persists over the whole note, and draws that move with the onset" {
        val span = config.spanFrames(FramingSweep.ONSET)
        val result = measureFraming(renderer = perBlockLaw, sweep = FramingSweep.ONSET, offsets = FRAMING_SHORT_OFFSETS, config = config)

        for (r in result.readings) {
            val k = r.offset

            withClue("k=$k") {
                if (k == 0) {
                    r.cls shouldBe FramingClass.IDENTICAL
                } else {
                    // Note frame j sits in block j / B of the reference and (j + k) / B of the moved render.
                    val differing = (0 until span).count { j -> ((j + k) / B) % 2 != (j / B) % 2 }

                    r.maxAbsDiff shouldBe (D plusOrMinus 1e-15)
                    r.firstDiffFrame shouldBe B - k
                    r.diffFrames shouldBe differing
                    r.region shouldBe FramingRegion.ONSET
                    r.extent shouldBe FramingExtent.PERSISTS
                    r.persistDb shouldBeGreaterThan -3.0
                    r.lateMaxAbs shouldBe (D plusOrMinus 1e-15)
                    r.cls shouldBe FramingClass.AUDIBLE
                }

                // The gate (70 blocks + 41 frames) holds 70 borders up to k = 87 and 71 from k = 88 on (the border 9088).
                r.rngSame shouldBe (k < 88)
            }
        }
    }

    "another summation order: rounding, never more" {
        val result = measureFraming(renderer = reseededPhase, sweep = FramingSweep.ONSET, offsets = FRAMING_SHORT_OFFSETS, config = config)

        for (r in result.readings) {
            withClue("k=${r.offset}") {
                (r.cls == FramingClass.IDENTICAL || r.cls == FramingClass.ROUNDING) shouldBe true
                r.maxAbsDiff shouldBeLessThan 1e-10
            }
        }

        withClue("the worst offset") {
            result.cls shouldBe FramingClass.ROUNDING
            result.worst.maxAbsDiff shouldBeGreaterThan 0.0
        }
    }

    "the classes' bounds, and a NaN or an infinity on one side only" {
        withClue("identical") { framingClassOf(maxAbsDiff = 0.0, referencePeak = 1.0) shouldBe FramingClass.IDENTICAL }
        withClue("rounding at the bound") { framingClassOf(maxAbsDiff = 2e-10, referencePeak = 2.0) shouldBe FramingClass.ROUNDING }
        withClue("small just past it") { framingClassOf(maxAbsDiff = 2.0001e-10, referencePeak = 2.0) shouldBe FramingClass.SMALL }
        withClue("small at its bound") { framingClassOf(maxAbsDiff = 2e-5, referencePeak = 2.0) shouldBe FramingClass.SMALL }
        withClue("audible past it") { framingClassOf(maxAbsDiff = 2.0001e-5, referencePeak = 2.0) shouldBe FramingClass.AUDIBLE }
        withClue("anything against silence") { framingClassOf(maxAbsDiff = 1e-300, referencePeak = 0.0) shouldBe FramingClass.AUDIBLE }

        val frames = 4 * B
        val reference = FramedOutput(left = DoubleArray(frames) { 0.5 }, right = null, rngFingerprint = null)

        for ((bad, name) in listOf(Double.NaN to "NaN", Double.POSITIVE_INFINITY to "+Inf")) {
            val test = FramedOutput(left = DoubleArray(frames) { if (it == 3) bad else 0.5 }, right = null, rngFingerprint = null)
            val r = compareFraming(reference = reference, referenceOnset = 0, test = test, testOnset = 0, spanFrames = 2 * B, eventFrame = B, offset = 0)

            withClue(name) {
                r.maxAbsDiff shouldBe Double.POSITIVE_INFINITY
                r.firstDiffFrame shouldBe 3
                r.diffFrames shouldBe 1
                r.cls shouldBe FramingClass.AUDIBLE
                r.rngSame shouldBe null
            }
        }
    }
})
