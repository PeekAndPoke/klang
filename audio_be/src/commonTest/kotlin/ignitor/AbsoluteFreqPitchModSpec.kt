/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import kotlin.math.abs
import kotlin.random.Random

/**
 * Ledger W13, "musical vs absolute frequency, part 2": a pitch modulation moves Freq-derived
 * pitches and leaves absolute ones alone — on BOTH doors.
 *
 * This is the phaseMod counterpart of [DetuneForkSpec]. Detune was always immune here by
 * construction (it multiplies the freq ARGUMENT, which an absolute-freq oscillator ignores);
 * phaseMod was not, because an oscillator scales its phase increment by `ctx.phaseMod` whatever
 * its frequency came from. So a fixed-pitch body resonance on the spine of a vibrato'd patch was
 * bent along with the note — live in `DialogueWithTheStars`, whose 180 Hz soundbox thump is
 * commented "fixed-pitch" and was being pitch-enveloped by 0.4 semitones.
 *
 * The two doors are genuinely different mechanisms and each gets its own rows:
 *  - the IGNITOR door hands modulation down through `ModApplyingIgnitor`;
 *  - the STRIP door (sprudel `.vibrato()` / `.pitchEnvelope()` / `.accelerate()`) writes
 *    `ctx.phaseMod` once for the whole graph in `IgniteRenderer`, with no wrapper involved.
 */
class AbsoluteFreqPitchModSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val blocks = 6

    /** Renders [dsl]; [phaseMod] is the STRIP door — a ratio array set straight onto the ctx. */
    fun render(dsl: IgnitorDsl, freqHz: Double = 220.0, phaseMod: Double? = null): DoubleArray {
        val rng = Random(7)
        val ignitor = dsl.buildExciter(soundIndex = 0, random = rng, freqHz = freqHz).ignitor
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = blockFrames * (blocks + 2),
            gateEndFrame = blockFrames * (blocks + 2),
            releaseFrames = 0,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = rng,
        )
        val out = DoubleArray(blocks * blockFrames)
        val buf = AudioBuffer(blockFrames)
        val mod = if (phaseMod != null) DoubleArray(blockFrames) { phaseMod } else null

        repeat(blocks) { b ->
            ctx.voiceElapsedFrames = b * blockFrames
            ctx.updateOffsetAndLength(0, blockFrames)
            ctx.phaseMod = mod
            buf.fill(0.0)
            ignitor.generate(buf, freqHz, ctx)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buf[i]
            }
        }

        return out
    }

    fun maxDiff(a: DoubleArray, b: DoubleArray): Double {
        var m = 0.0
        for (i in a.indices) {
            m = maxOf(m, abs(a[i] - b[i]))
        }
        return m
    }

    val absolute = IgnitorDsl.Sine(freq = IgnitorDsl.Constant(180.0))
    val musical = IgnitorDsl.Sine(freq = IgnitorDsl.Freq)

    // ── The strip door: sprudel's vibrato / pitchEnvelope / accelerate ───────────────────────

    "an absolute-freq oscillator ignores a strip-level phaseMod" {
        // DialogueWithTheStars' soundbox thump, reduced: 180 Hz, fixed by authoring, sitting in
        // a patch whose pitch envelope writes ctx.phaseMod for the whole graph.
        maxDiff(render(absolute, phaseMod = 1.05), render(absolute)) shouldBe 0.0
    }

    "a musical oscillator still follows a strip-level phaseMod" {
        // The control. Without it the row above passes for a renderer that ignores phaseMod
        // altogether, which would break every vibrato in the engine.
        (maxDiff(render(musical, phaseMod = 1.05), render(musical)) > 0.1) shouldBe true
    }

    // ── The ignitor door: .vibrato() on the DSL ──────────────────────────────────────────────

    "an absolute-freq oscillator on a vibrato'd spine is not bent" {
        val plain = render(absolute)
        val vibratoed = render(
            IgnitorDsl.Vibrato(inner = absolute, rate = IgnitorDsl.Constant(5.0), semitones = IgnitorDsl.Constant(2.0))
        )

        maxDiff(vibratoed, plain) shouldBe 0.0
    }

    "a musical oscillator on the same spine IS bent" {
        val plain = render(musical)
        val vibratoed = render(
            IgnitorDsl.Vibrato(inner = musical, rate = IgnitorDsl.Constant(5.0), semitones = IgnitorDsl.Constant(2.0))
        )

        (maxDiff(vibratoed, plain) > 0.1) shouldBe true
    }

    "a mixed spine bends only its musical half" {
        // The shape that actually ships: note-pitched partials summed with a fixed-pitch body.
        // The sum must move (the musical half is bent) while the absolute half stays put, which
        // is what makes this different from "the whole subtree is exempt".
        val mixed = IgnitorDsl.Plus(musical, absolute)
        val plain = render(mixed)
        val vibratoed = render(
            IgnitorDsl.Vibrato(inner = mixed, rate = IgnitorDsl.Constant(5.0), semitones = IgnitorDsl.Constant(2.0))
        )

        (maxDiff(vibratoed, plain) > 0.1) shouldBe true

        // and the absolute partial alone is untouched under the same modulation
        val absoluteAlone = render(
            IgnitorDsl.Vibrato(inner = absolute, rate = IgnitorDsl.Constant(5.0), semitones = IgnitorDsl.Constant(2.0))
        )
        maxDiff(absoluteAlone, render(absolute)) shouldBe 0.0
    }

    "the shield restores the context for whatever renders after it" {
        // TWO things have to line up for a missing restore to be visible, and the campaign had
        // to find both. (1) ORDER: the absolute half must render FIRST, so there is still a
        // sibling left to damage — the mixed row above renders the musical half first and is
        // blind. (2) DOOR: it must be the STRIP door. On the ignitor door `ctx.phaseMod` is
        // null on entry (only ModApplyingIgnitor sets it, and only around its own inner call),
        // so the barrier saves null and restores null and dropping the restore changes
        // nothing. Only an upstream-set phaseMod makes the restore observable.
        val absoluteFirst = IgnitorDsl.Plus(absolute, musical)

        val modulated = render(absoluteFirst, phaseMod = 1.05)
        val plain = render(absoluteFirst)

        // The musical half must still be modulated after the absolute half has been shielded.
        // Drop `ctx.phaseMod = saved` and it is not, both renders collapse together, and this
        // difference goes to zero.
        (maxDiff(modulated, plain) > 0.1) shouldBe true
    }

    "a keytracked freq expression still counts as musical" {
        // `Osc.freq().mul(2)` has a Freq leaf, so it is a pitch and must follow the vibrato —
        // the predicate is "does this freq slot derive from Freq", not "is it a bare Freq".
        val keytracked = IgnitorDsl.Sine(freq = IgnitorDsl.Times(IgnitorDsl.Freq, IgnitorDsl.Constant(2.0)))
        val plain = render(keytracked)
        val vibratoed = render(
            IgnitorDsl.Vibrato(inner = keytracked, rate = IgnitorDsl.Constant(5.0), semitones = IgnitorDsl.Constant(2.0))
        )

        (maxDiff(vibratoed, plain) > 0.1) shouldBe true
    }
})
