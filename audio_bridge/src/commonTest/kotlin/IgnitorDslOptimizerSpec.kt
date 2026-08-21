/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Tree-in / tree-out contract for [optimize]. The RENDERED bit-identity promise lives in
 * `audio_be`'s `IgnitorDslOptimizerRenderSpec`, which is where a real engine exists; this spec
 * pins the shapes, the guards and the sharing behaviour.
 */
class IgnitorDslOptimizerSpec : StringSpec({

    val c = { v: Double -> IgnitorDsl.Constant(v) }

    // ── R1: what fuses ────────────────────────────────────────────────────────

    "a run of adjacent filters collapses into ONE Eq, in written order" {
        val dsl = IgnitorDsl.Sawtooth()
            .notch(210.0, 2.5)
            .highpass(440.0, 0.707)
            .lowpass(5300.0, 0.707)
            .optimize()

        val eq = dsl.shouldBeInstanceOf<IgnitorDsl.Eq>()
        eq.inner.shouldBeInstanceOf<IgnitorDsl.Sawtooth>()
        eq.sections.size shouldBe 3
        eq.sections[0].shouldBeInstanceOf<IgnitorDsl.EqSection.Notch>()
        eq.sections[1].shouldBeInstanceOf<IgnitorDsl.EqSection.Highpass>()
        eq.sections[2].shouldBeInstanceOf<IgnitorDsl.EqSection.Lowpass>()
    }

    "a lone filter converts to a one-section Eq" {
        // Standalone conversion is ON by measurement, not assumption: Node 0.44 vs 0.74 us/block
        // for a one-section core versus a single Ignitor filter node, JVM a wash.
        val eq = IgnitorDsl.Sine().lowpass(2000.0).optimize().shouldBeInstanceOf<IgnitorDsl.Eq>()
        eq.sections.size shouldBe 1
    }

    "a filter on top of an existing authored Eq appends into it" {
        val eq = IgnitorDsl.Sawtooth().eq().band(1200.0, 0.9, 6.0)
            .lowpass(5000.0)
            .optimize()
            .shouldBeInstanceOf<IgnitorDsl.Eq>()

        eq.sections.size shouldBe 2
        eq.sections[0].shouldBeInstanceOf<IgnitorDsl.EqSection.Bell>()
        eq.sections[1].shouldBeInstanceOf<IgnitorDsl.EqSection.Lowpass>()
    }

    "section params are carried across unchanged" {
        val eq = IgnitorDsl.Sine().lowpass(5300.0, 0.9).optimize() as IgnitorDsl.Eq
        val lp = eq.sections.single() as IgnitorDsl.EqSection.Lowpass
        (lp.freqHz as IgnitorDsl.Constant).value shouldBe 5300.0
        (lp.q as IgnitorDsl.Constant).value shouldBe 0.9
    }

    "Der Schmetterling's guitar tail collapses to ONE Eq node" {
        // The shape Der Schmetterling's guitar is being migrated to: two authored taps, then
        // four chained filters. Without this rule that is 5 nodes; the point of D4 is that it
        // becomes 1. (The song file itself is the maintainer's to commit, so the repo copy may
        // still carry the older hand-built parallel form.)
        val authored = IgnitorDsl.Sawtooth()
            .eq()
            .tap(850.0, 0.707, 1.7)
            .tap(2500.0, 0.7, 5.0)
            .notch(210.0, 2.5)
            .let {
                IgnitorDsl.Highpass(
                    inner = it,
                    cutoffHz = IgnitorDsl.Times(IgnitorDsl.Freq, c(1.0)),
                    q = c(0.707),
                )
            }
            .lowpass(5250.0, 0.707)
            .lowpass(5250.0, 0.707)

        val eq = authored.optimize().shouldBeInstanceOf<IgnitorDsl.Eq>()
        eq.inner.shouldBeInstanceOf<IgnitorDsl.Sawtooth>()
        eq.sections.map { it::class.simpleName } shouldBe listOf(
            "RawTap", "RawTap", "Notch", "Highpass", "Lowpass", "Lowpass",
        )
    }

    // ── R1: what must NOT fuse ────────────────────────────────────────────────

    "a nonlinear node between two filters blocks the fusion" {
        // The maintainer's example. Nothing may move across the distort, so this is two
        // independent one-section Eqs, never one two-section Eq.
        val dsl = IgnitorDsl.Sine().bandpass(1000.0).distort(0.5).lowpass(4000.0).optimize()

        val outer = dsl.shouldBeInstanceOf<IgnitorDsl.Eq>()
        outer.sections.size shouldBe 1
        outer.sections.single().shouldBeInstanceOf<IgnitorDsl.EqSection.Lowpass>()

        val clip = outer.inner.shouldBeInstanceOf<IgnitorDsl.Clip>()
        val drive = clip.inner.shouldBeInstanceOf<IgnitorDsl.Drive>()
        val inner = drive.inner.shouldBeInstanceOf<IgnitorDsl.Eq>()
        inner.sections.single().shouldBeInstanceOf<IgnitorDsl.EqSection.Bandpass>()
    }

    "a gain multiply between two filters blocks the fusion" {
        // Mathematically a scalar commutes with a linear filter; in floating point it does NOT
        // give the same bits, and bit-identity is the promise. So a Times is a wall too.
        val dsl = IgnitorDsl.Sine().lowpass(2000.0).mul(c(0.5)).lowpass(3000.0).optimize()

        val outer = dsl.shouldBeInstanceOf<IgnitorDsl.Eq>()
        outer.sections.size shouldBe 1
        outer.inner.shouldBeInstanceOf<IgnitorDsl.Times>()
    }

    "analog > 0 never fuses (the saturating branch is deliberate character)" {
        val dsl = IgnitorDsl.Lowpass(
            inner = IgnitorDsl.Sine(),
            cutoffHz = c(2000.0),
            q = c(0.707),
            analog = c(2.0),
        ).optimize()

        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
    }

    "a Param-backed analog never fuses even when it defaults to zero" {
        // oscparam("analog", 3) could switch saturation on per note; the decision is made once,
        // here, so only a structural literal zero is safe.
        val dsl = IgnitorDsl.Lowpass(
            inner = IgnitorDsl.Sine(),
            cutoffHz = c(2000.0),
            q = c(0.707),
            analog = IgnitorDsl.Param("analog", 0.0),
        ).optimize()

        dsl.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
    }

    "an unfusable FILTER between two fusible ones splits them, and both sides still fuse" {
        // A rendered parity row cannot see this: a regression that makes the optimizer MORE
        // conservative below a wall is bit-identical. Only a shape assertion catches it.
        val dsl = IgnitorDsl.Sawtooth()
            .lowpass(2000.0, 0.707)
            .onePoleLowpass(800.0)
            .lowpass(4000.0, 0.707)
            .optimize()

        val outer = dsl.shouldBeInstanceOf<IgnitorDsl.Eq>()
        outer.sections.size shouldBe 1
        val wall = outer.inner.shouldBeInstanceOf<IgnitorDsl.OnePoleLowpass>()
        wall.inner.shouldBeInstanceOf<IgnitorDsl.Eq>().sections.size shouldBe 1
    }

    "an analog filter between two fusible ones splits them the same way" {
        val dsl = IgnitorDsl.Lowpass(
            inner = IgnitorDsl.Sawtooth().lowpass(2000.0, 0.707),
            cutoffHz = IgnitorDsl.Constant(3000.0),
            q = IgnitorDsl.Constant(0.707),
            analog = IgnitorDsl.Constant(2.0),
        ).lowpass(4000.0, 0.707).optimize()

        val outer = dsl.shouldBeInstanceOf<IgnitorDsl.Eq>()
        outer.sections.size shouldBe 1
        val wall = outer.inner.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        wall.inner.shouldBeInstanceOf<IgnitorDsl.Eq>().sections.size shouldBe 1
    }

    "warmth / one-pole is left alone (no one-pole section type exists)" {
        IgnitorDsl.Sine().onePoleLowpass(800.0).optimize()
            .shouldBeInstanceOf<IgnitorDsl.OnePoleLowpass>()
    }

    // ── Sharing ───────────────────────────────────────────────────────────────

    "a SHARED intermediate is never forked" {
        // let t = sig.notch(...); t.lowpass(a).add(t.lowpass(b))
        // Absorbing the shared notch into either branch would compute it twice — a silent CPU
        // regression, which is the opposite of the point.
        val shared = IgnitorDsl.Sawtooth().notch(210.0, 2.5)
        val dsl = IgnitorDsl.Plus(shared.lowpass(3000.0), shared.lowpass(6000.0)).optimize()

        val plus = dsl.shouldBeInstanceOf<IgnitorDsl.Plus>()
        val left = plus.left.shouldBeInstanceOf<IgnitorDsl.Eq>()
        val right = plus.right.shouldBeInstanceOf<IgnitorDsl.Eq>()

        // Each branch keeps ONE section of its own; the shared notch stays a separate node.
        left.sections.size shouldBe 1
        right.sections.size shouldBe 1
        // ...and it is still literally the same instance on both sides.
        (left.inner === right.inner) shouldBe true
    }

    "a dissolved optimizer(1) does not hide a SHARED node from the guard" {
        // A hint is always refcount-1, so once it dissolves the guard could mistake it for
        // the real inner and append into a shared Eq — forking it and computing the lowpass
        // twice per block. Audio would be unaffected, which is what makes it dangerous: it
        // is a silent CPU regression landing exactly in the .optimizer(0) -> (1) A/B flow.
        val shared = IgnitorDsl.Sawtooth().lowpass(1000.0)
        val dsl = IgnitorDsl.Plus(
            shared.optimizer(on = 1).notch(210.0, 2.5),
            shared.highpass(300.0),
        ).optimize()

        val plus = dsl.shouldBeInstanceOf<IgnitorDsl.Plus>()
        val left = plus.left.shouldBeInstanceOf<IgnitorDsl.Eq>()
        val right = plus.right.shouldBeInstanceOf<IgnitorDsl.Eq>()

        // Neither branch may have absorbed the shared lowpass: each carries ONE section of
        // its own, and both sit on the very same rewritten Eq instance.
        left.sections.size shouldBe 1
        right.sections.size shouldBe 1
        (left.inner === right.inner) shouldBe true
    }

    "a shared subtree is rewritten once and stays shared" {
        val shared = IgnitorDsl.Sawtooth().lowpass(1000.0)
        val dsl = IgnitorDsl.Plus(shared, shared).optimize()

        val plus = dsl.shouldBeInstanceOf<IgnitorDsl.Plus>()
        (plus.left === plus.right) shouldBe true
    }

    // ── Purity / stability ────────────────────────────────────────────────────

    "optimize is idempotent" {
        val once = IgnitorDsl.Sawtooth().notch(210.0, 2.5).lowpass(5300.0).optimize()
        once.optimize() shouldBe once
    }

    "a tree with nothing to fuse returns the SAME instance" {
        // Identity, not just equality: MemoizingIgnitor caches on node identity downstream.
        val dsl = IgnitorDsl.Sine().distort(0.5)
        (dsl.optimize() === dsl) shouldBe true
    }

    "param names and their first-occurrence order survive" {
        val chained = IgnitorDsl.Notch(
            inner = IgnitorDsl.Lowpass(
                inner = IgnitorDsl.Sawtooth(freq = IgnitorDsl.Param("f", 220.0)),
                cutoffHz = IgnitorDsl.Param("lf", 5300.0),
                q = c(0.707),
            ),
            cutoffHz = IgnitorDsl.Param("nf", 210.0),
            q = c(2.5),
        )

        val before = mutableListOf<IgnitorDsl.Param>().also { chained.collectParams(it) }
        val after = mutableListOf<IgnitorDsl.Param>().also { chained.optimize().collectParams(it) }

        after.map { it.name }.toSet() shouldBe before.map { it.name }.toSet()
        after.map { it.name }.distinct() shouldBe before.map { it.name }.distinct()
    }

    "maxReleaseSec is unchanged by optimization" {
        val chained = IgnitorDsl.Sawtooth()
            .adsr(0.01, 0.2, 0.5, 1.5)
            .lowpass(5300.0)
            .notch(210.0, 2.5)

        chained.optimize().maxReleaseSec() shouldBe chained.maxReleaseSec()

        // With the kill switch on the tree too: VoiceFactory reads the AUTHORED tree, which
        // always still contains the hint, so a wrong passthrough arm truncates voice lifetime
        // and cuts the release tail of any sound carrying .optimizer(...) — for BOTH on values.
        val marked = chained.optimizer(on = 0)
        marked.maxReleaseSec() shouldBe chained.maxReleaseSec()
        marked.optimize().maxReleaseSec() shouldBe chained.maxReleaseSec()
    }

    // ── Kill switch ───────────────────────────────────────────────────────────

    "optimizer(0) disables fusion for the WHOLE definition" {
        val dsl = IgnitorDsl.Sawtooth().notch(210.0, 2.5).lowpass(5300.0).optimizer(on = 0)
        (dsl.optimize() === dsl) shouldBe true
    }

    "optimizer(0) anywhere in the tree disables it, not just below the marker" {
        // The marker is a definition-level switch, so burying it in a param subtree still counts.
        val dsl = IgnitorDsl.Sawtooth()
            .let {
                IgnitorDsl.Lowpass(
                    inner = it,
                    cutoffHz = IgnitorDsl.Sine(freq = c(2.0)).optimizer(on = 0),
                    q = c(0.707),
                )
            }
            .notch(210.0, 2.5)

        (dsl.optimize() === dsl) shouldBe true
    }

    "optimizer(1) leaves fusion ON and dissolves the marker" {
        val marked = IgnitorDsl.Sawtooth().notch(210.0, 2.5).lowpass(5300.0).optimizer(on = 1)
        val eq = marked.optimize().shouldBeInstanceOf<IgnitorDsl.Eq>()
        eq.sections.size shouldBe 2
    }

    "a mid-chain optimizer(1) is NOT a fusion wall" {
        // The A/B hatch must compare the authored tree against the tree production really
        // renders. If an on=1 marker survived the rewrite it would split the chain in two,
        // so flipping 0 -> 1 would show a THIRD sound that ships nowhere.
        val marked = IgnitorDsl.Sawtooth()
            .lowpass(5000.0)
            .optimizer(on = 1)
            .notch(210.0, 2.5)

        val eq = marked.optimize().shouldBeInstanceOf<IgnitorDsl.Eq>()
        eq.inner.shouldBeInstanceOf<IgnitorDsl.Sawtooth>()
        eq.sections.size shouldBe 2
    }
})
