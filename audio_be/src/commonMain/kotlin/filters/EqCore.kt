/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.flushDenormal

/**
 * Freq-agnostic serial EQ core — N second-order TPT-SVF sections in one `process()` call.
 *
 * The shared engine of the unified-equalizer work: the PLANNED per-voice `EqIgnitor` adapter
 * will drive it first; the planned `MasterFx.eq()` and Katalyst chains adopt the SAME core
 * later.
 *
 * CONTRACT — the core owns: section state (`ic1`/`ic2`), coefficient storage and computation
 * (via [computeSvfCoeffs] — NaN/Inf-safe through `bilinearK` + the q clamp; the core adds NO
 * clamps of its own, so it inherits the Ignitor path's behavior exactly — pinned by parity
 * spec rows at extreme freq/q), the process loop, and `flushDenormal` on both integrator
 * states. The SURFACE owns: param resolution (scalars per control tick), WHEN to call
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
 * order is APPEND-ONLY and reserved IN FULL here — note it differs from delivery order
 * ([RAW_TAP] ships before [BELL] but takes the higher ordinal). An UNKNOWN type value renders
 * as PASSTHROUGH (the only degradation that can neither invent gain nor gouge a spectral
 * hole — the house fall-through policy), so a section type added to one loop shape but not
 * the others fails loudly in the parity specs instead of silently notching.
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
 * block before the next node runs) and every fused traversal shape produce identical IEEE
 * operations on identical operands.
 *
 * LOOP SHAPE — DECISION PENDING (closes at the tap slice; decision-grade numbers need BOTH
 * benchmark orders, per the turbo-decay artifact documented in the plan's D0 record):
 *  - [SHAPE_SAMPLE_MAJOR]: samples outermost, small-int `when` per section; the running sample
 *    rides in a register, and each section's two state words are snapshotted to locals per
 *    sample — they are STORED inside the loop, so array-resident reads would reload after
 *    every store (the aliasing fix at per-sample scope; a bake-off candidate must not be
 *    measured in a handicapped form). `k` is never written and stays an inline read in the
 *    two taps that use it.
 *  - [SHAPE_SECTION_MAJOR]: one specialized in-place loop per section, coefficients hoisted to
 *    locals, state STAYING in the arrays. Measured 2026-08-19 (JVM): loses badly — `buffer`,
 *    `ic1`, `ic2` are all DoubleArray, the JIT cannot disambiguate them, and every
 *    `buffer[i] =` store forces state reloads. Kept on the ballot so the bake-off record shows
 *    WHY, not just THAT.
 *  - [SHAPE_SECTION_MAJOR_LOCALS]: section-major with the two state words snapshotted into
 *    locals for the duration of ONE section loop and written back after it — bit-identical
 *    (same IEEE ops, same operands) and free of the aliasing reloads. NOT the pattern
 *    `performance.md` bans: that ban targets class-field snapshots across a whole `generate()`
 *    body (early-return / `return@use` write-back hazards); this scope is a straight-line
 *    private loop with a single write-back point.
 * The losers are DELETED when the decision closes, not shipped dormant.
 */
class EqCore(
    val sectionCount: Int,
    private val shape: Int,
) {
    companion object {
        // Section types — APPEND-ONLY reserved order (see class KDoc; BELL/RAW_TAP arrive with
        // their slices but their ordinals are fixed NOW). UNCONFIGURED is the construction
        // default: negative, so an unconfigured section renders as PASSTHROUGH in every shape.
        const val UNCONFIGURED = -1
        const val LOWPASS = 0
        const val HIGHPASS = 1
        const val BANDPASS = 2
        const val NOTCH = 3
        const val BELL = 4
        const val RAW_TAP = 5

        // Loop shapes (bake-off ballot; losers are removed when the decision closes).
        const val SHAPE_SAMPLE_MAJOR = 0
        const val SHAPE_SECTION_MAJOR = 1
        const val SHAPE_SECTION_MAJOR_LOCALS = 2
    }

    // Parallel primitive arrays: one hidden class for the whole core; DoubleArray is a
    // Float64Array on JS (monomorphic access, no per-section object headers). All allocated at
    // construction — nothing allocates in process().
    private val type = IntArray(sectionCount) { UNCONFIGURED }
    private val a1 = DoubleArray(sectionCount)
    private val a2 = DoubleArray(sectionCount)
    private val a3 = DoubleArray(sectionCount)
    private val k = DoubleArray(sectionCount)
    private val ic1 = DoubleArray(sectionCount)
    private val ic2 = DoubleArray(sectionCount)

    // Out-param holder for the coefficient helpers (reused per configure call, never per sample).
    private val coefs = SvfCoeffs()

    init {
        // Internal invariant, checked at CONSTRUCTION (never the render path): the shapes are
        // contractually bit-identical, so no parity spec can ever catch a wrong dispatch — a
        // stale shape constant would silently poison the bake-off with a self-comparison.
        require(shape in SHAPE_SAMPLE_MAJOR..SHAPE_SECTION_MAJOR_LOCALS) {
            "unknown EqCore loop shape: $shape"
        }
    }

    /**
     * Sets section [index]'s type and coefficients from control-rate scalars. Takes effect
     * immediately (snap — see the class KDoc's smoothing note). [db] and [gain] are reserved
     * for the [BELL] and [RAW_TAP] section types; the four structural types ignore them.
     * Freq/q travel through the same clamps as every SVF in the engine ([computeSvfCoeffs] /
     * `bilinearK`) — the core must never add or remove a clamp here (parity contract).
     *
     * An out-of-range [index] is IGNORED (house fall-through): configure calls run at control
     * rate ON the audio thread — never throw there (unlike the construction-time shape
     * `require`, which runs off it) — and JS typed arrays silently drop OOB writes anyway;
     * the guard makes JVM behave like JS instead of throwing.
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
        computeSvfCoeffs(freqHz, q, sampleRate, coefs)
        a1[index] = coefs.a1
        a2[index] = coefs.a2
        a3[index] = coefs.a3
        k[index] = coefs.k
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
    }

    /**
     * Zeroes all section STATE; types and coefficients persist ("clear the delay lines, keep
     * the curve" — the standard EQ reset semantic, for bus adopters and pooling).
     */
    fun reset() {
        ic1.fill(0.0)
        ic2.fill(0.0)
    }

    /** Runs all sections serially, in place, over `[offset, offset+length)`. */
    fun process(buffer: AudioBuffer, offset: Int, length: Int) {
        when (shape) {
            SHAPE_SAMPLE_MAJOR -> processSampleMajor(buffer, offset, length)
            SHAPE_SECTION_MAJOR -> processSectionMajor(buffer, offset, length)
            SHAPE_SECTION_MAJOR_LOCALS -> processSectionMajorLocals(buffer, offset, length)
            else -> Unit // unreachable: shape validated at construction
        }
    }

    /** Shape: samples outermost, small-int type dispatch per section per sample. */
    private fun processSampleMajor(buffer: AudioBuffer, offset: Int, length: Int) {
        val n = sectionCount
        val end = offset + length
        for (i in offset until end) {
            var acc = buffer[i]
            for (s in 0 until n) {
                // Shared TPT-SVF recurrence (operand-identical to SvfIgnitor's linear
                // branches — state snapshotted to locals, see class KDoc); only the
                // output tap differs per type.
                val t = type[s]

                if (t < LOWPASS || t > NOTCH) {
                    // Unknown/unconfigured/not-yet-implemented type: PASSTHROUGH, state
                    // untouched (class KDoc). The range test assumes the IMPLEMENTED types
                    // stay contiguous — true today (LOWPASS..NOTCH); when RAW_TAP ships
                    // before BELL it must become an enumeration, and the spec's reserved-type
                    // passthrough rows go red if this guard lags behind.
                    continue
                }

                val i1 = ic1[s]
                val i2 = ic2[s]
                val v0 = acc
                val v3 = v0 - i2
                val v1 = a1[s] * i1 + a2[s] * v3
                val v2 = i2 + a2[s] * i1 + a3[s] * v3
                ic1[s] = (2.0 * v1 - i1).flushDenormal()
                ic2[s] = (2.0 * v2 - i2).flushDenormal()

                // k is never written in process(), so the taps read it inline: one load in
                // exactly the arms that use it — hoisting it above would add a dead load for
                // every LOWPASS/BANDPASS section-sample.
                acc = when (t) {
                    LOWPASS -> v2
                    HIGHPASS -> v0 - k[s] * v1 - v2
                    BANDPASS -> v1
                    NOTCH -> v0 - k[s] * v1
                    else -> acc // unreachable under the guard; passthrough, NEVER a tap
                }
            }
            buffer[i] = acc
        }
    }

    /**
     * Shape: one specialized in-place loop per section, coefficients hoisted to locals,
     * state STAYING in the arrays (see the class KDoc's aliasing note — kept on the ballot
     * for the record).
     */
    private fun processSectionMajor(buffer: AudioBuffer, offset: Int, length: Int) {
        val n = sectionCount
        val end = offset + length
        for (s in 0 until n) {
            val ca1 = a1[s]
            val ca2 = a2[s]
            val ca3 = a3[s]
            val ck = k[s]
            when (type[s]) {
                LOWPASS -> {
                    for (i in offset until end) {
                        val v0 = buffer[i]
                        val v3 = v0 - ic2[s]
                        val v1 = ca1 * ic1[s] + ca2 * v3
                        val v2 = ic2[s] + ca2 * ic1[s] + ca3 * v3
                        ic1[s] = (2.0 * v1 - ic1[s]).flushDenormal()
                        ic2[s] = (2.0 * v2 - ic2[s]).flushDenormal()
                        buffer[i] = v2
                    }
                }

                HIGHPASS -> {
                    for (i in offset until end) {
                        val v0 = buffer[i]
                        val v3 = v0 - ic2[s]
                        val v1 = ca1 * ic1[s] + ca2 * v3
                        val v2 = ic2[s] + ca2 * ic1[s] + ca3 * v3
                        ic1[s] = (2.0 * v1 - ic1[s]).flushDenormal()
                        ic2[s] = (2.0 * v2 - ic2[s]).flushDenormal()
                        buffer[i] = v0 - ck * v1 - v2
                    }
                }

                BANDPASS -> {
                    for (i in offset until end) {
                        val v0 = buffer[i]
                        val v3 = v0 - ic2[s]
                        val v1 = ca1 * ic1[s] + ca2 * v3
                        val v2 = ic2[s] + ca2 * ic1[s] + ca3 * v3
                        ic1[s] = (2.0 * v1 - ic1[s]).flushDenormal()
                        ic2[s] = (2.0 * v2 - ic2[s]).flushDenormal()
                        buffer[i] = v1
                    }
                }

                NOTCH -> {
                    for (i in offset until end) {
                        val v0 = buffer[i]
                        val v3 = v0 - ic2[s]
                        val v1 = ca1 * ic1[s] + ca2 * v3
                        val v2 = ic2[s] + ca2 * ic1[s] + ca3 * v3
                        ic1[s] = (2.0 * v1 - ic1[s]).flushDenormal()
                        ic2[s] = (2.0 * v2 - ic2[s]).flushDenormal()
                        buffer[i] = v0 - ck * v1
                    }
                }

                else -> {
                    // unknown/not-yet-implemented type: PASSTHROUGH (class KDoc)
                }
            }
        }
    }

    /**
     * Shape: section-major with state snapshotted into locals per section loop (bit-identical;
     * dodges the DoubleArray aliasing reloads — see the class KDoc's shape notes).
     */
    private fun processSectionMajorLocals(buffer: AudioBuffer, offset: Int, length: Int) {
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

                else -> {
                    // unknown/not-yet-implemented type: PASSTHROUGH (class KDoc)
                }
            }
            ic1[s] = s1
            ic2[s] = s2
        }
    }
}
