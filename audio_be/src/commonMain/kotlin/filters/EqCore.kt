/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.flushDenormal
import io.peekandpoke.klang.audio_be.safeOut

/**
 * Freq-agnostic serial EQ core — N second-order TPT-SVF sections in one `process()` call.
 *
 * The shared engine of the unified-equalizer work: the PLANNED per-voice `EqIgnitor` adapter
 * will drive it first; the planned `MasterFx.eq()` and Katalyst chains adopt the SAME core
 * later.
 *
 * CONTRACT — the core owns: section state (`ic1`/`ic2`), coefficient storage and computation
 * (via [computeSvfCoeffs], or [computeSvfBellCoeffs] for [BELL] — NaN/Inf-safe through
 * `bilinearK` + the q clamp + the bell's db NaN-guard; the core adds NO clamps of its own:
 * the structural types inherit the Ignitor path's behavior exactly — pinned by parity rows
 * at extreme freq/q — and the bell's single clamp, safeOut on m1, lives in the coefficient
 * HELPER, not here), the process loop, and `flushDenormal` on both integrator states.
 * The SURFACE owns: param resolution (scalars per control tick), WHEN to call
 * [configureSection] (control rate; coefficients take effect immediately — the core is
 * SNAP-only, so any smoothing/ramping policy must be built by the surface BEFORE
 * MasterFx.eq/Katalyst can adopt it: a per-block-swept cutoff snaps here exactly like the
 * per-voice `SvfIgnitor` does, which is parity for the ignitor surface and a click hazard for
 * a bus surface), stereo (the core is MONO — one instance per channel), and the note frequency
 * (the core never sees a voice).
 *
 * EVERY section MUST be configured before the first `process()` — the surface's contract. The
 * BREACH degradation is the house fall-through: types default to [UNCONFIGURED], so an
 * unconfigured section renders as PASSTHROUGH (a zero-filled type array would instead render
 * SILENCE — all-zero LOWPASS coefficients — the one degradation worse than a wrong curve).
 * [reset] keeps types and coefficients by design, so a POOLED core recycled to a new tenant
 * carries the previous tenant's curve until every section is reconfigured — full
 * reconfiguration is the surface's job.
 *
 * Section types are plain Ints ([LOWPASS]..[RAW_TAP]) — NO audio_bridge dependency (Zig-port
 * purity); a sync spec pins them to the wire enum's ordinals when the DSL node lands. The
 * order is APPEND-ONLY (it differs from delivery order: [RAW_TAP] shipped before [BELL] but
 * takes the higher ordinal; both are implemented now). An UNKNOWN type value renders
 * as PASSTHROUGH (the only degradation that can neither invent gain nor gouge a spectral
 * hole — the house fall-through policy); the spec's garbage-type rows include the NEXT
 * append-only ordinal (6), so a newly implemented type fails loudly there and moves the
 * tripwire forward (proven live twice: [RAW_TAP] in D2b, [BELL] in D2c).
 *
 * [BELL] — the parametric peaking section (Simper SVF bell): [configureSection]'s `db` is the
 * gain, `q` the PRE-GAIN bandwidth; math + honest limits in [computeSvfBellCoeffs]. At
 * db == 0.0 (m1 == 0.0) the output is BIT-TRANSPARENT through an EXPLICIT passthrough
 * branch — never the algebraic `v0 + 0·v1`, which flips `-0.0` to `+0.0` and poisons the
 * output on NaN/Inf input — while the recurrence keeps RUNNING at the A=1 coefficients, so
 * an expression-backed db moving off zero mid-note continues from coherent state instead of
 * clicking (at A=1 the state advances exactly like a [BANDPASS] at the same freq/q — same
 * recurrence, different tap — which is the spec's continuity pin). Skipping the state
 * entirely for a db that can never move (Constant/Param-backed = per-voice constant) is the
 * ADAPTER's optimization, not the core's.
 *
 * [RAW_TAP] — the parallel-boost section (the guitar chain's
 * `signal.add(signal.bandpass(f, q).mul(g))`): a tap runs its bandpass on the Eq INPUT (never
 * the running chain value) and ADDS `safeOut(v1 * gain)` onto the chain at its LIST
 * POSITION — the position pin is the DEFINITION, making every wire-supplied section order
 * well-defined. Bit-parity with the legacy graph `Plus(chainSoFar, Times(bandpass(input),
 * gain))`: Plus is a bare add, Times applies safeOut to the per-sample PRODUCT. Tap gain 0 is
 * NOT skipped — legacy adds `safeOut(v1 * 0)`, and `-0.0 + 0.0` flips to `+0.0`, so a skip
 * would break bit-parity. The loop captures the input up front (in-place processing destroys
 * it — see [captureInput]). THREE FUSION PRECONDITIONS (same structural-not-evaluated class
 * as `analog` below): (1) the GAIN operand must be structurally block-constant
 * (`Constant`/`Param`-backed — the optimizer's R2 rule), never by evaluated value — an
 * expression-backed gain (e.g. an LFO) multiplies per SAMPLE in the legacy `Times` node, and
 * snapping it per block is a different sound, not bit-identity; (2) the tap's bandpass SOURCE
 * must be the very node that feeds the Eq's first section, matched by reference IDENTITY
 * (`===`) on the DSL node — NEVER by data-class `==`: runtime sharing is keyed on
 * `IgnitorBuildCache`'s (node identity, accumulated-mod identity) pair, so two structurally
 * equal noise nodes (whiteNoise/dust/crackle draw from the shared global RNG) are two
 * genuinely UNCORRELATED streams — a structural match would fuse them into ONE, turning an
 * uncorrelated ~+3 dB sum into a coherent ~+6 dB resonant peak, and no parity spec can catch
 * it (spec oracles only ever build graphs that already satisfy this rule); (3) taps fuse only from the LEFT-NESTED chain spine — `Plus(chain, tapSubtree)` per
 * fold step: the section list sums `(x + t1) + t2`, while `x.add(t1.add(t2))` sums
 * `x + (t1 + t2)`, and IEEE `+` is NOT associative (x=1.0, t1=t2=2^-53 differ in the last
 * bit), so a spine-flattening matcher breaks bit-parity. Note for direct Kotlin-API users:
 * the `Ignitor.mul(Double)` front door short-circuits at exactly 1.0 WITHOUT safeOut — a
 * unity-gain [RAW_TAP] is bit-parity with the wire's `Times` node (safeOut always applied),
 * not with that shortcut.
 *
 * Per-sample math is copied VERBATIM from `SvfIgnitor`'s linear branches (the bit-identity
 * contract of the graph optimizer: a fused chain must equal the chained nodes bit-for-bit —
 * modulo NaN PAYLOAD bits, which neither IEEE nor the JVM specifies: a NaN matches any NaN,
 * and a NaN state never returns to finite, so no finite divergence can hide behind this).
 * `SvfIgnitor`'s `g` coefficient is deliberately absent — only its saturating (analog > 0)
 * branches read it. UNLIKE env, `analog` IS on the wire and evaluated per block, so "never
 * fuses" is a RULE the optimizer must enforce, not a structural given: a section may fuse
 * ONLY when the node's analog is structurally `Constant(0.0)` (the DSL default) — never by
 * evaluated value, since a Param-backed analog can enable saturation mid-song.
 * (BANDPASS/NOTCH ignore analog outright.) Its per-sample coefficient RAMP (`FilterEnvDef`) is
 * absent too: EqCore does not implement it, which is bit-neutral exactly when env is OFF (the
 * Bresenham steps are then literal `+= 0.0` on strictly positive finite coefficients). An
 * env'd filter must NEVER fuse into a section — the wire DSL cannot express env, so the graph
 * optimizer can never encounter one; the precondition binds direct Kotlin-API users of this
 * class. Sections run serially in list order; each section's `y[n]` depends only on its input
 * at `n` and its own state at `n-1`, so the chained-node oracle (each node renders the whole
 * block before the next node runs) and the fused traversal produce identical IEEE operations
 * on identical operands.
 *
 * LOOP SHAPE — DECIDED 2026-08-19 (docs/benchmarks/2026-08-19_201841 + _202119, both case
 * orders, JVM + Node): section-major with the two state words snapshotted into locals for the
 * duration of ONE section loop and written back after it. On V8 — the deployment platform
 * (browser worklet, the phone) — it beat sample-major at every measured N by 17–38%
 * (array-state section-major, not measured at N=1, lost decisively at 4 and 6); sample-major
 * led only on desktop JVM at N >= 4 (13–34% — offline renders, ample headroom).
 * Deleted candidates, for the Zig port's record: SAMPLE-MAJOR (samples outermost, per-sample
 * small-int type dispatch; the dispatch never amortizes on V8) and ARRAY-STATE SECTION-MAJOR
 * (`buffer`/`ic1`/`ic2` are all DoubleArray, the JIT cannot disambiguate them, every
 * `buffer[i] =` store forced state reloads). The locals snapshot is NOT the pattern
 * `performance.md` bans: that ban targets class-field snapshots across a whole `generate()`
 * body (early-return / `return@use` write-back hazards); this scope is a straight-line loop
 * with a single write-back point.
 */
class EqCore(
    val sectionCount: Int,
) {
    companion object {
        // Section types — APPEND-ONLY order (see class KDoc). UNCONFIGURED is the
        // construction default: negative, so an unconfigured section renders as PASSTHROUGH.
        const val UNCONFIGURED = -1
        const val LOWPASS = 0
        const val HIGHPASS = 1
        const val BANDPASS = 2
        const val NOTCH = 3
        const val BELL = 4
        const val RAW_TAP = 5
    }

    // Parallel primitive arrays: one hidden class for the whole core; DoubleArray is a
    // Float64Array on JS (monomorphic access, no per-section object headers). All allocated at
    // construction — nothing allocates in process() at steady state.
    private val type = IntArray(sectionCount) { UNCONFIGURED }
    private val a1 = DoubleArray(sectionCount)
    private val a2 = DoubleArray(sectionCount)
    private val a3 = DoubleArray(sectionCount)
    private val k = DoubleArray(sectionCount)
    private val gain = DoubleArray(sectionCount)
    private val m1 = DoubleArray(sectionCount)
    private val ic1 = DoubleArray(sectionCount)
    private val ic2 = DoubleArray(sectionCount)

    // RAW_TAP support: the flag is RECOMPUTED by configureSection/disableSection over the type
    // array (never latched at construction — types arrive per configure call); the input copy
    // is grown on demand (see captureInput). `internal` so the spec can pin the flag's
    // LIFECYCLE directly: a stuck-true flag is output-invisible (the copy is redundant, not
    // wrong) but silently taxes every serial-only core — benchmark integrity, not parity.
    internal var hasRawTap = false
        private set

    private var inputCopy = AudioBuffer(0)

    // Capacity accessor for the spec: the quantum-grain round-up in captureInput is
    // output-invisible (allocation count only — the exact class of thing the hasRawTap
    // lifecycle row pins), so it is pinned directly too.
    internal val inputCopyCapacity: Int get() = inputCopy.size

    // Out-param holder for the coefficient helpers (reused per configure call, never per sample).
    private val coefs = SvfCoeffs()

    /**
     * Sets section [index]'s type and coefficients from control-rate scalars. Takes effect
     * immediately (snap — see the class KDoc's smoothing note). [gain] is the [RAW_TAP] mix
     * gain, stored RAW — safeOut is applied to the per-sample PRODUCT, matching the legacy
     * Times node, never to the stored gain. [db] is the [BELL] gain (coefficient-bearing —
     * see [computeSvfBellCoeffs]; its safeOut lands on the m1 COEFFICIENT at configure time).
     * The four structural types ignore both. Freq/q travel through the same clamps as every
     * SVF in the engine ([computeSvfCoeffs] / `bilinearK`) — the core must never add or
     * remove a clamp here (parity contract).
     *
     * An out-of-range [index] is IGNORED (house fall-through): configure calls run at control
     * rate ON the audio thread — never throw there — and JS typed arrays silently drop OOB
     * writes anyway; the guard makes JVM behave like JS instead of throwing.
     */
    fun configureSection(
        index: Int,
        type: Int,
        freqHz: Double,
        q: Double,
        db: Double,
        gain: Double,
        sampleRate: Double,
    ) {
        if (index < 0 || index >= sectionCount) {
            return // out-of-range: ignore (KDoc)
        }

        this.type[index] = type
        this.gain[index] = gain

        if (type == BELL) {
            computeSvfBellCoeffs(freqHz, q, db, sampleRate, coefs)
        } else {
            computeSvfCoeffs(freqHz, q, sampleRate, coefs)
        }

        a1[index] = coefs.a1
        a2[index] = coefs.a2
        a3[index] = coefs.a3
        k[index] = coefs.k
        m1[index] = coefs.m1
        recomputeTapFlag()
    }

    /**
     * Retires section [index] to [UNCONFIGURED] — it renders as PASSTHROUGH until configured
     * again. The sanctioned way for a pooled core's new tenant to drop leftover sections.
     * State is ZEROED, deliberately unlike [configureSection] (which keeps state so a live
     * curve tweak never clicks): a disabled span makes state STALE, and re-enabling a high-Q
     * section seconds later would release the frozen integrator energy as a thump — a
     * re-enabled section starts clean instead, like a fresh core. Out-of-range [index] is
     * ignored (see [configureSection]).
     */
    fun disableSection(index: Int) {
        if (index < 0 || index >= sectionCount) {
            return // out-of-range: ignore (see configureSection)
        }

        type[index] = UNCONFIGURED
        ic1[index] = 0.0
        ic2[index] = 0.0
        recomputeTapFlag()
    }

    private fun recomputeTapFlag() {
        var found = false

        for (i in 0 until sectionCount) {
            if (type[i] == RAW_TAP) {
                found = true
                break
            }
        }

        hasRawTap = found
    }

    /**
     * Captures the raw Eq input: the loop processes in place, so by the time a [RAW_TAP]
     * section runs, earlier sections have already overwritten `buffer` — the tap must read
     * the ORIGINAL input. Taken ONLY when a tap is present (a serial-only core pays no copy).
     * Reads go through RELATIVE indexing (`inputCopy[i - offset]`): the copy runs from
     * index 0, so an absolute-indexed read on a mid-block voice start reads the WRONG samples
     * (shifted by `offset`, zero-fill past `length`) — SILENTLY, on both platforms (the
     * capacity round-up means no bounds error saves you); only value comparison catches it.
     * Growth rounds capacity up to the next [AudioBackendContext.RENDER_QUANTUM_FRAMES]
     * multiple: a mid-block voice onset (short window first, then full blocks — the NORMAL
     * production sequence) then allocates ONCE, not twice (performance.md Rule 2
     * grow-on-shape-change; never allocates at steady state).
     */
    private fun captureInput(buffer: AudioBuffer, offset: Int, length: Int) {
        if (inputCopy.size < length) {
            val grain = AudioBackendContext.RENDER_QUANTUM_FRAMES
            inputCopy = AudioBuffer(((length + grain - 1) / grain) * grain)
        }

        buffer.copyInto(inputCopy, 0, offset, offset + length)
    }

    /**
     * Zeroes all section STATE; types and coefficients persist ("clear the delay lines, keep
     * the curve" — the standard EQ reset semantic, for bus adopters and pooling).
     */
    fun reset() {
        ic1.fill(0.0)
        ic2.fill(0.0)
    }

    /**
     * Runs all sections serially, in place, over `[offset, offset+length)`. One specialized
     * loop per section with coefficients AND the two state words in locals, written back once
     * after the section loop (the decided shape — see the class KDoc's LOOP SHAPE record).
     *
     * An INSANE window (negative offset, non-positive length, or reaching past the buffer)
     * is IGNORED —
     * the same house fall-through as the index guards: on a broken surface, the serial loops
     * would throw on JVM and NaN silently on JS, and [captureInput]'s `copyInto` would throw
     * on BOTH platforms — on the JS worklet an escaped exception kills the whole processor,
     * not one voice. One branch per block buys the never-throw property back.
     */
    fun process(buffer: AudioBuffer, offset: Int, length: Int) {
        // Overflow-proof form: with offset >= 0 established, `length > size - offset` cannot
        // wrap, while `offset + length` would for astronomical offsets (e.g. an absolute
        // frame cursor passed by mistake) — and would then throw inside captureInput.
        if (offset < 0 || length <= 0 || length > buffer.size - offset) {
            return // insane window: ignore (KDoc)
        }

        if (hasRawTap) {
            captureInput(buffer, offset, length)
        }

        val n = sectionCount
        val end = offset + length
        for (s in 0 until n) {
            val ca1 = a1[s]
            val ca2 = a2[s]
            val ca3 = a3[s]
            val ck = k[s]
            var s1 = ic1[s]
            var s2 = ic2[s]
            when (type[s]) {
                LOWPASS -> {
                    for (i in offset until end) {
                        val v0 = buffer[i]
                        val v3 = v0 - s2
                        val v1 = ca1 * s1 + ca2 * v3
                        val v2 = s2 + ca2 * s1 + ca3 * v3
                        s1 = (2.0 * v1 - s1).flushDenormal()
                        s2 = (2.0 * v2 - s2).flushDenormal()
                        buffer[i] = v2
                    }
                }

                HIGHPASS -> {
                    for (i in offset until end) {
                        val v0 = buffer[i]
                        val v3 = v0 - s2
                        val v1 = ca1 * s1 + ca2 * v3
                        val v2 = s2 + ca2 * s1 + ca3 * v3
                        s1 = (2.0 * v1 - s1).flushDenormal()
                        s2 = (2.0 * v2 - s2).flushDenormal()
                        buffer[i] = v0 - ck * v1 - v2
                    }
                }

                BANDPASS -> {
                    for (i in offset until end) {
                        val v0 = buffer[i]
                        val v3 = v0 - s2
                        val v1 = ca1 * s1 + ca2 * v3
                        val v2 = s2 + ca2 * s1 + ca3 * v3
                        s1 = (2.0 * v1 - s1).flushDenormal()
                        s2 = (2.0 * v2 - s2).flushDenormal()
                        buffer[i] = v1
                    }
                }

                NOTCH -> {
                    for (i in offset until end) {
                        val v0 = buffer[i]
                        val v3 = v0 - s2
                        val v1 = ca1 * s1 + ca2 * v3
                        val v2 = s2 + ca2 * s1 + ca3 * v3
                        s1 = (2.0 * v1 - s1).flushDenormal()
                        s2 = (2.0 * v2 - s2).flushDenormal()
                        buffer[i] = v0 - ck * v1
                    }
                }

                RAW_TAP -> {
                    // A tap filters the Eq INPUT (inputCopy — earlier sections already
                    // overwrote buffer in place) and ADDS its band onto the chain at this
                    // list position; bare add + safeOut on the product = legacy Plus/Times
                    // per-sample math. Relative indexing — see captureInput. The copy rides
                    // in a local like everything else this loop touches per sample (the
                    // field-load discipline the shape was measured with).
                    val cg = gain[s]
                    val src = inputCopy
                    for (i in offset until end) {
                        val v0 = src[i - offset]
                        val v3 = v0 - s2
                        val v1 = ca1 * s1 + ca2 * v3
                        val v2 = s2 + ca2 * s1 + ca3 * v3
                        s1 = (2.0 * v1 - s1).flushDenormal()
                        s2 = (2.0 * v2 - s2).flushDenormal()
                        buffer[i] += safeOut(v1 * cg)
                    }
                }

                BELL -> {
                    val cm1 = m1[s]

                    if (cm1 == 0.0) {
                        // 0 dB: BIT-TRANSPARENT via the EXPLICIT branch — the algebraic
                        // `v0 + 0·v1` flips -0.0 to +0.0 and poisons the output on NaN/Inf
                        // input. State keeps RUNNING at the A=1 coefficients so a db moving
                        // off zero continues click-free (class KDoc).
                        for (i in offset until end) {
                            val v0 = buffer[i]
                            val v3 = v0 - s2
                            val v1 = ca1 * s1 + ca2 * v3
                            val v2 = s2 + ca2 * s1 + ca3 * v3
                            s1 = (2.0 * v1 - s1).flushDenormal()
                            s2 = (2.0 * v2 - s2).flushDenormal()
                            buffer[i] = v0
                        }
                    } else {
                        // Simper bell tap: v0 + m1·v1, bare — m1 was safeOut-capped at
                        // configure time (computeSvfBellCoeffs), the per-sample path adds
                        // no clamp (raw engine).
                        for (i in offset until end) {
                            val v0 = buffer[i]
                            val v3 = v0 - s2
                            val v1 = ca1 * s1 + ca2 * v3
                            val v2 = s2 + ca2 * s1 + ca3 * v3
                            s1 = (2.0 * v1 - s1).flushDenormal()
                            s2 = (2.0 * v2 - s2).flushDenormal()
                            buffer[i] = v0 + cm1 * v1
                        }
                    }
                }

                else -> {
                    // Unknown/unconfigured types: PASSTHROUGH, state untouched (class KDoc;
                    // the write-back below stores the unread snapshot).
                }
            }
            ic1[s] = s1
            ic2[s] = s2
        }
    }
}
