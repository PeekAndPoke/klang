/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
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

    "C5/D6: passes = N expands into N staggered sections — never one section that loses slope" {
        val eq = IgnitorDsl.Sine().lowpass(2000.0, 1.0, passes = 2).optimize()
            .shouldBeInstanceOf<IgnitorDsl.Eq>()
        eq.sections.size shouldBe 2
        val q0 = (eq.sections[0] as IgnitorDsl.EqSection.Lowpass).q
            .shouldBeInstanceOf<IgnitorDsl.Constant>()
        val q1 = (eq.sections[1] as IgnitorDsl.EqSection.Lowpass).q
            .shouldBeInstanceOf<IgnitorDsl.Constant>()
        // the 4th-order Butterworth ladder, scaled by userQ/0.7071 (userQ = 1.0)
        q0.value shouldBe (0.7654 plusOrMinus 0.001)
        q1.value shouldBe (1.8478 plusOrMinus 0.002)
    }

    "C5: a MODULATED q still expands — per-stage Times wrappers keep the sweep coherent" {
        val lfoQ = IgnitorDsl.Sine(freq = IgnitorDsl.Constant(0.5))
        val node = IgnitorDsl.Lowpass(
            inner = IgnitorDsl.Sine(),
            freq = IgnitorDsl.Constant(2000.0),
            q = lfoQ,
            passes = 2,
        )
        val eq = node.optimize().shouldBeInstanceOf<IgnitorDsl.Eq>()
        eq.sections.size shouldBe 2
        (eq.sections[0] as IgnitorDsl.EqSection.Lowpass).q.shouldBeInstanceOf<IgnitorDsl.Times>()
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
        (lp.freq as IgnitorDsl.Constant).value shouldBe 5300.0
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
                    freq = IgnitorDsl.Times(IgnitorDsl.Freq, c(1.0)),
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

        val clip = outer.inner.shouldBeInstanceOf<IgnitorDsl.Shape>()
        val drive = clip.inner.shouldBeInstanceOf<IgnitorDsl.Drive>()
        val inner = drive.inner.shouldBeInstanceOf<IgnitorDsl.Eq>()
        inner.sections.single().shouldBeInstanceOf<IgnitorDsl.EqSection.Bandpass>()
    }

    "a gain multiply between two filters blocks the fusion" {
        // A scalar commutes with a linear filter on paper, not in floating point, and the serial
        // rule keeps its no-reorder shape: the gain (folded into an Affine since R2) is a wall
        // between the two Eqs until step 3 folds it into a neighbouring Eq's gain.
        val dsl = IgnitorDsl.Sine().lowpass(2000.0).mul(c(0.5)).lowpass(3000.0).optimize()

        val outer = dsl.shouldBeInstanceOf<IgnitorDsl.Eq>()
        outer.sections.size shouldBe 1
        outer.inner.shouldBeInstanceOf<IgnitorDsl.Affine>()
    }

    "analog > 0 never fuses (the saturating branch is deliberate character)" {
        val dsl = IgnitorDsl.Lowpass(
            inner = IgnitorDsl.Sine(),
            freq = c(2000.0),
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
            freq = c(2000.0),
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
            .onepole(800.0)
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
            freq = IgnitorDsl.Constant(3000.0),
            q = IgnitorDsl.Constant(0.707),
            analog = IgnitorDsl.Constant(2.0),
        ).lowpass(4000.0, 0.707).optimize()

        val outer = dsl.shouldBeInstanceOf<IgnitorDsl.Eq>()
        outer.sections.size shouldBe 1
        val wall = outer.inner.shouldBeInstanceOf<IgnitorDsl.Lowpass>()
        wall.inner.shouldBeInstanceOf<IgnitorDsl.Eq>().sections.size shouldBe 1
    }

    "onepole (the one-pole lowpass) is left alone (no one-pole section type exists)" {
        IgnitorDsl.Sine().onepole(800.0).optimize()
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
                freq = IgnitorDsl.Param("lf", 5300.0),
                q = c(0.707),
            ),
            freq = IgnitorDsl.Param("nf", 210.0),
            q = c(2.5),
        )

        val before = mutableListOf<IgnitorDsl.Param>().also { chained.collectParams(it) }
        val after = mutableListOf<IgnitorDsl.Param>().also { chained.optimize().collectParams(it) }

        after.map { it.name }.toSet() shouldBe before.map { it.name }.toSet()
        after.map { it.name }.distinct() shouldBe before.map { it.name }.distinct()
    }

    // NOTE: "maxReleaseSec is unchanged by optimization" lived here until 2026-08-27. It existed
    // only because ONE question (how long is the release tail?) was answered on TWO structures that
    // could drift: VoiceFactory analysed the AUTHORED tree while voices rendered the OPTIMIZED one.
    // The tail now falls out of the build of the tree that actually renders, so there is one source
    // and the drift is unrepresentable — the guard has no equivalent. See
    // `docs/tasks-archive/2026-08/20260831-ignitor-envelope-ownership.md`.

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
                    freq = IgnitorDsl.Sine(freq = c(2.0)).optimizer(on = 0),
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

    // ═════════════════════════════════════════════════════════════════════════════
    // R2 — the arithmetic fold (step 2 of the arithmetic folds, 2026-09-15)
    // ═════════════════════════════════════════════════════════════════════════════
    //
    // One Affine covers exactly the authored shape `x [.add(p)] .mul(m) [.add(a)]`; an absent
    // pre-add or add is the node's default, `Constant(-0.0)`. The negatives are the contract:
    // no fold across an addition, no fold without a multiply, no fold of a modulated operand,
    // no fold into a shared node, a literal run composed only while the chain's clamp cannot
    // differ from the fold's.

    val none = IgnitorDsl.Constant(-0.0)

    "R2: a multiply then an add is one Affine, the absent pre-add the -0.0 default" {
        IgnitorDsl.Sawtooth().mul(c(2.0)).plus(c(10.0)).optimize() shouldBe
            IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = none, mul = c(2.0), add = c(10.0))
    }

    "R2: an add then a multiply is one Affine with the pre-add (exact at the zero crossings)" {
        IgnitorDsl.Sawtooth().plus(c(10.0)).mul(c(2.0)).optimize() shouldBe
            IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = c(10.0), mul = c(2.0), add = none)
    }

    "R2: a lone multiply folds, with both identities; a constant on the left folds the same" {
        IgnitorDsl.Sawtooth().mul(c(2.0)).optimize() shouldBe
            IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = none, mul = c(2.0), add = none)
        c(2.0).mul(IgnitorDsl.Sawtooth()).optimize() shouldBe
            IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = none, mul = c(2.0), add = none)
    }

    "R2: a subtract before the multiply is the negated pre-add; a leading constant plus is the pre-add" {
        IgnitorDsl.Sawtooth().minus(c(3.0)).mul(c(2.0)).optimize() shouldBe
            IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = c(-3.0), mul = c(2.0), add = none)
        c(10.0).plus(IgnitorDsl.Sawtooth()).mul(c(2.0)).optimize() shouldBe
            IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = c(10.0), mul = c(2.0), add = none)
    }

    "R2: the full shape, add then multiply then add, is one node" {
        IgnitorDsl.Sawtooth().plus(c(1.0)).mul(c(2.0)).plus(c(3.0)).optimize() shouldBe
            IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = c(1.0), mul = c(2.0), add = c(3.0))
    }

    "R2: a growing run of literal multiplies composes into one coefficient" {
        // |m| >= 1 throughout: the chain's per-op clamp and the fold's single clamp saturate alike
        IgnitorDsl.Sawtooth().mul(c(2.0)).mul(c(2.0)).plus(c(10.0)).optimize() shouldBe
            IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = none, mul = c(4.0), add = c(10.0))
    }

    "R2: a mixed run (up then down) does not compose: the chain's intermediate clamp could differ" {
        IgnitorDsl.Sawtooth().mul(c(100.0)).mul(c(0.01)).optimize() shouldBe
            IgnitorDsl.Affine(
                IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = none, mul = c(100.0), add = none),
                pre = none, mul = c(0.01), add = none,
            )
    }

    "R2: an attenuating run composes only over a clamped input (an Affine output is one; a bare source is not)" {
        // the source is not provably below SAFE_MAX (a Constant(1e300) source is legal): two nodes
        IgnitorDsl.Sawtooth().mul(c(0.5)).mul(c(0.25)).optimize() shouldBe
            IgnitorDsl.Affine(
                IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = none, mul = c(0.5), add = none),
                pre = none, mul = c(0.25), add = none,
            )
        // through a clamping node first (mul(2) is one), the attenuating pair composes
        IgnitorDsl.Sawtooth().mul(c(2.0)).mul(c(0.5)).mul(c(0.25)).optimize() shouldBe
            IgnitorDsl.Affine(
                IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = none, mul = c(2.0), add = none),
                pre = none, mul = c(0.125), add = none,
            )
    }

    "R2: an Affine that carries an add is NOT a clamped input: its add sits outside the clamp" {
        // 2x + 8e15 is unbounded; composing 0.5 · 0.5 over it would clamp once where the chain
        // clamps twice (1e15 versus 5e14, half the value)
        IgnitorDsl.Sawtooth().mul(c(2.0)).plus(c(8e15)).mul(c(0.5)).mul(c(0.5)).optimize() shouldBe
            IgnitorDsl.Affine(
                IgnitorDsl.Affine(
                    IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = none, mul = c(2.0), add = c(8e15)),
                    pre = none, mul = c(0.5), add = none,
                ),
                pre = none, mul = c(0.5), add = none,
            )
    }

    "R2: a run whose product overflows, or underflows to a subnormal, is not composed" {
        IgnitorDsl.Sawtooth().mul(c(1e300)).mul(c(1e300)).optimize() shouldBe
            IgnitorDsl.Affine(
                IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = none, mul = c(1e300), add = none),
                pre = none, mul = c(1e300), add = none,
            )
        // 1e-12 · 1e-300 is subnormal: the constant alone would carry a rounding error over the margin
        IgnitorDsl.Sawtooth().mul(c(2.0)).mul(c(1e-12)).mul(c(1e-300)).optimize() shouldBe
            IgnitorDsl.Affine(
                IgnitorDsl.Affine(
                    IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = none, mul = c(2.0), add = none),
                    pre = none, mul = c(1e-12), add = none,
                ),
                pre = none, mul = c(1e-300), add = none,
            )
    }

    "R2: a shared Affine is not filled by an add either: the Plus stays, the sharing survives" {
        val shared = IgnitorDsl.Sawtooth().mul(c(2.0))
        val dsl = shared.plus(c(1.0)).plus(shared).optimize()

        val outer = dsl.shouldBeInstanceOf<IgnitorDsl.Plus>()
        val inner = outer.left.shouldBeInstanceOf<IgnitorDsl.Plus>()

        inner.right shouldBe c(1.0)
        (inner.left === outer.right) shouldBe true
        inner.left shouldBe IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = none, mul = c(2.0), add = none)
    }

    "R2: a Param multiply folds alone, never composed with a literal" {
        val level = IgnitorDsl.Param("level", 0.5)

        IgnitorDsl.Sawtooth().mul(level).mul(c(2.0)).optimize() shouldBe
            IgnitorDsl.Affine(
                IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = none, mul = level, add = none),
                pre = none, mul = c(2.0), add = none,
            )
    }

    "R2: never across an addition: mul, add, mul is two nodes; mul, add, add keeps a Plus" {
        IgnitorDsl.Sawtooth().mul(c(2.0)).plus(c(1.0)).mul(c(3.0)).optimize() shouldBe
            IgnitorDsl.Affine(
                IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = none, mul = c(2.0), add = c(1.0)),
                pre = none, mul = c(3.0), add = none,
            )
        IgnitorDsl.Sawtooth().mul(c(2.0)).plus(c(1.0)).plus(c(2.0)).optimize() shouldBe
            IgnitorDsl.Plus(IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = none, mul = c(2.0), add = c(1.0)), c(2.0))
    }

    "R2: no multiply, no Affine: adds and subtracts stay what they are" {
        val adds = IgnitorDsl.Sawtooth().plus(c(1.0)).plus(c(2.0))
        val sub = IgnitorDsl.Sawtooth().minus(c(1.0))

        adds.optimize() shouldBe adds
        sub.optimize() shouldBe sub
    }

    "R2: a modulated operand is not a coefficient: mul(lfo) stays Times, add(signal) stays Plus" {
        val lfo = IgnitorDsl.Sine(freq = c(3.0))
        val ring = IgnitorDsl.Sawtooth().mul(lfo)
        val mix = IgnitorDsl.Sawtooth().plus(IgnitorDsl.Sine()).mul(c(0.5))

        ring.optimize() shouldBe ring
        mix.optimize() shouldBe IgnitorDsl.Affine(IgnitorDsl.Sawtooth().plus(IgnitorDsl.Sine()), pre = none, mul = c(0.5), add = none)
    }

    "R2: a block-constant coefficient EXPRESSION folds as the coefficient, unevaluated" {
        val tracking = IgnitorDsl.Freq.mul(c(2.0)).plus(c(10.0))

        IgnitorDsl.Sawtooth().mul(tracking).optimize() shouldBe
            IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = none, mul = tracking, add = none)
    }

    "R2: a shared node is never absorbed: the fold wraps it, the sharing survives" {
        val shared = IgnitorDsl.Sawtooth().mul(c(2.0))
        val dsl = shared.mul(c(3.0)).plus(shared).optimize()

        val plus = dsl.shouldBeInstanceOf<IgnitorDsl.Plus>()
        val outer = plus.left.shouldBeInstanceOf<IgnitorDsl.Affine>()

        outer.mul shouldBe c(3.0)
        // the shared multiply was folded ONCE and both consumers hold the same instance
        (outer.inner === plus.right) shouldBe true
        outer.inner shouldBe IgnitorDsl.Affine(IgnitorDsl.Sawtooth(), pre = none, mul = c(2.0), add = none)
    }

    "R2: a Param on the LEFT of a multiply or an add stays where it was written (param order is a contract)" {
        // An Affine lists its signal's params before its coefficients'; folding a left-hand Param
        // would move it behind the signal's in collectParams, which the UI keys on.
        val level = IgnitorDsl.Param("level", 0.5)
        val leftMul = level.mul(IgnitorDsl.Sine())
        val leftAdd = level.plus(IgnitorDsl.Sine()).mul(c(2.0))

        leftMul.optimize() shouldBe leftMul
        leftAdd.optimize() shouldBe IgnitorDsl.Affine(level.plus(IgnitorDsl.Sine()), pre = none, mul = c(2.0), add = none)

        val params = mutableListOf<IgnitorDsl.Param>().also { leftAdd.optimize().collectParams(it) }.map { it.name }

        params shouldBe listOf("level", "analog")
    }

    "R2: a scalar expression with no signal is left alone (a parameter's own arithmetic)" {
        val scalar = IgnitorDsl.Freq.mul(c(2.0)).plus(c(10.0))

        scalar.optimize() shouldBe scalar
    }

    "R2: optimizer(0) leaves every arithmetic node untouched" {
        val dsl = IgnitorDsl.Sawtooth().mul(c(2.0)).plus(c(10.0)).optimizer(on = 0)

        dsl.optimize() shouldBe dsl
    }
})
