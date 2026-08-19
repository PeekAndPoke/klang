/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.FreqIgnitor
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.highpass
import io.peekandpoke.klang.audio_be.ignitor.lowpass
import io.peekandpoke.klang.audio_be.ignitor.notch
import io.peekandpoke.klang.audio_be.ignitor.bandpass
import kotlin.random.Random

/**
 * ULP-0 bit-parity guards for [EqCore] against chained per-voice SVF nodes (unified-eq plan,
 * D2a): the fused core must equal the chained `SvfIgnitor`s bit-for-bit — that is the graph
 * optimizer's replacement contract.
 *
 * ORACLE CHOICE: the Ignitor-form chain (`Ignitor.lowpass(...)` etc.) driven by a deterministic
 * [BufferSourceIgnitor] — NOT the class-form `SvfLPF` — because the Ignitor form is literally
 * what the optimizer replaces (the plan's oracle-drift risk item is moot this way).
 *
 * All three LOOP SHAPES are tested (the bake-off toggle); sub-block windows run as the FIRST call on
 * fresh state (a full-block first call would mask window-arithmetic bugs); the denormal-tail
 * case gives `flushDenormal` a discriminating input (tiny impulse decaying through the flush
 * threshold within two blocks).
 */
class EqCoreSpec : StringSpec({

    val blockFrames = 128
    val blocks = 4
    val sr = 44100
    val shapes = listOf(
        "sampleMajor" to EqCore.SHAPE_SAMPLE_MAJOR,
        "sectionMajor" to EqCore.SHAPE_SECTION_MAJOR,
        "sectionMajorLocals" to EqCore.SHAPE_SECTION_MAJOR_LOCALS,
    )

    // Deterministic input: seeded noise + a slow ramp (both polarities, zero crossings,
    // amplitude spread). kotlin.random.Random(seed) is platform-deterministic.
    val input = DoubleArray(blockFrames * (blocks + 1)).also { d ->
        val rng = Random(42)
        for (i in d.indices) {
            d[i] = rng.nextDouble(-1.0, 1.0) * 0.7 + (i % 97) / 97.0 * 0.2 - 0.1
        }
    }

    fun ctx(): IgniteContext = IgniteContext(
        sampleRate = sr,
        voiceDurationFrames = blockFrames * 16,
        gateEndFrame = blockFrames * 16,
        releaseFrames = 0,
        voiceEndFrame = blockFrames * 16,
        scratchBuffers = ScratchBuffers(blockFrames),
    ).apply {
        offset = 0
        length = blockFrames
        voiceElapsedFrames = 0
    }

    /** Section spec: type + freq + q, applied identically to core and oracle chain. */
    data class Section(val type: Int, val freq: Double, val q: Double)

    fun buildCore(sections: List<Section>, shape: Int): EqCore =
        EqCore(sections.size, shape).also { core ->
            sections.forEachIndexed { i, s ->
                core.configureSection(i, s.type, s.freq, s.q, 0.0, 1.0, sr.toDouble())
            }
        }

    fun chainOracle(src: Ignitor, sections: List<Section>): Ignitor =
        sections.fold(src) { acc, s ->
            when (s.type) {
                EqCore.LOWPASS -> acc.lowpass(s.freq, s.q)
                EqCore.HIGHPASS -> acc.highpass(s.freq, s.q)
                EqCore.BANDPASS -> acc.bandpass(s.freq, s.q)
                EqCore.NOTCH -> acc.notch(s.freq, s.q)
                else -> error("EqCoreSpec oracle has no node for type ${s.type}")
            }
        }

    /**
     * Renders [blocks] full blocks through the fused core and the chained oracle from the same
     * deterministic [data] and asserts per-sample bit equality — EXCEPT NaN, which compares as
     * NaN with any payload: the JVM guarantees only "a NaN" (a fresh x86 invalid-op NaN is
     * 0xFFF8..., a propagated input NaN keeps its bits, and JIT operand commutation of
     * commutative ops may select either operand's NaN), so payload bits are outside every
     * layer's contract. A NaN state never returns to finite in an SVF, so the exception can
     * never mask a finite divergence.
     */
    fun assertChainParity(sections: List<Section>, shape: Int, data: DoubleArray = input) {
        val core = buildCore(sections, shape)
        val oracle = chainOracle(BufferSourceIgnitor(data), sections)
        val c = ctx()
        val bufCore = AudioBuffer(blockFrames)
        val bufOracle = AudioBuffer(blockFrames)
        repeat(blocks) { blk ->
            data.copyInto(bufCore, 0, blk * blockFrames, (blk + 1) * blockFrames)
            core.process(bufCore, 0, blockFrames)
            oracle.generate(bufOracle, 220.0, c)
            for (i in 0 until blockFrames) {
                if (!(bufCore[i].isNaN() && bufOracle[i].isNaN())) {
                    bufCore[i].toRawBits() shouldBe bufOracle[i].toRawBits()
                }
            }
            c.voiceElapsedFrames += blockFrames
        }
    }

    val singleSections = listOf(
        "lowpass" to Section(EqCore.LOWPASS, 1234.0, 1.7),
        "highpass" to Section(EqCore.HIGHPASS, 440.0, 0.707),
        "bandpass" to Section(EqCore.BANDPASS, 850.0, 0.9),
        "notch" to Section(EqCore.NOTCH, 210.0, 2.5),
    )

    for ((shapeName, shape) in shapes) {
        for ((secName, section) in singleSections) {
            "single $secName section is bit-equal to the chained node ($shapeName)" {
                assertChainParity(listOf(section), shape)
            }
        }

        "guitar-tail 4-section chain is bit-equal to the chained nodes ($shapeName)" {
            assertChainParity(
                listOf(
                    Section(EqCore.NOTCH, 210.0, 2.5),
                    Section(EqCore.HIGHPASS, 440.0, 0.707),
                    Section(EqCore.LOWPASS, 5300.0, 0.707),
                    Section(EqCore.LOWPASS, 5300.0, 0.707),
                ),
                shape,
            )
        }

        "sub-block FIRST call windows correctly and leaves the rest untouched ($shapeName)" {
            // Parameterized over EVERY section type: the section-major shapes have one
            // hand-written windowed loop PER TYPE (checklist item 4).
            for ((_, single) in singleSections) {
            val sections = listOf(single)
            val offset = 37
            val length = 64
            val sentinel = 123.456

            val core = buildCore(sections, shape)
            val bufCore = AudioBuffer(blockFrames).apply { fill(sentinel) }
            input.copyInto(bufCore, offset, offset, offset + length)
            core.process(bufCore, offset, length)

            val oracle = chainOracle(BufferSourceIgnitor(input, startAt = offset), sections)
            val bufOracle = AudioBuffer(blockFrames).apply { fill(sentinel) }
            val c = ctx().apply {
                this.offset = offset
                this.length = length
                voiceElapsedFrames = -offset // production mid-block-onset shape
            }
            oracle.generate(bufOracle, 220.0, c)

            for (i in 0 until blockFrames) {
                if (i in offset until offset + length) {
                    bufCore[i].toRawBits() shouldBe bufOracle[i].toRawBits()
                } else {
                    bufCore[i] shouldBe sentinel
                    bufOracle[i] shouldBe sentinel
                }
            }
            }
        }

        "per-block cutoff changes stay bit-equal and stable ($shapeName)" {
            // Expression-backed cutoffs recompute per block on the Ignitor side (its cache
            // predicate is Param-only); the core mirrors that by reconfiguring per block.
            // Oracle cutoff = the voice frequency itself (FreqIgnitor), swept per block.
            val freqs = listOf(500.0, 1500.0, 500.0, 3000.0)
            val core = EqCore(1, shape)
            val oracle = BufferSourceIgnitor(input).lowpass(FreqIgnitor)
            val c = ctx()
            val bufCore = AudioBuffer(blockFrames)
            val bufOracle = AudioBuffer(blockFrames)
            freqs.forEachIndexed { blk, f ->
                core.configureSection(0, EqCore.LOWPASS, f, 0.707, 0.0, 1.0, sr.toDouble())
                input.copyInto(bufCore, 0, blk * blockFrames, (blk + 1) * blockFrames)
                core.process(bufCore, 0, blockFrames)
                oracle.generate(bufOracle, f, c)
                for (i in 0 until blockFrames) {
                    bufCore[i].toRawBits() shouldBe bufOracle[i].toRawBits()
                }
                c.voiceElapsedFrames += blockFrames
            }
        }

        "denormal tail flushes bit-identically ($shapeName)" {
            // Tiny impulse then silence: state decays through the flush threshold within the
            // first block — the discriminating input for flushDenormal. Parameterized over
            // EVERY type: the section-major shapes duplicate the state update per type, and
            // the recurrence (where the flush lives) is tap-independent.
            val tiny = DoubleArray(blockFrames * (blocks + 1)).also { it[0] = 1e-14 }
            for ((_, single) in singleSections) {
                assertChainParity(listOf(Section(single.type, 1234.0, 1.7)), shape, data = tiny)
            }
        }

        "extreme freq/q inherit the shared clamps bit-identically ($shapeName)" {
            // The core must never add or remove a clamp vs the Ignitor path — pinned at the
            // values where a well-meaning guard would diverge (NaN -> 1000 Hz fallback,
            // 1e9 -> Nyquist-1, q NaN -> Butterworth, q 1e9 -> 200, q 0 -> 0.1).
            val extremes = listOf(
                Section(EqCore.LOWPASS, Double.NaN, 1.0),
                Section(EqCore.LOWPASS, 1e9, 1.0),
                Section(EqCore.HIGHPASS, 440.0, Double.NaN),
                Section(EqCore.BANDPASS, 850.0, 1e9),
                Section(EqCore.NOTCH, 210.0, 0.0),
            )
            for (section in extremes) {
                assertChainParity(listOf(section), shape)
            }
        }

        "unknown section type is PASSTHROUGH ($shapeName)" {
            // Reserved-but-unimplemented (BELL/RAW_TAP) and garbage types — POSITIVE and
            // NEGATIVE (a one-sided guard notches negatives; -42 is arbitrary garbage, the
            // UNCONFIGURED sentinel has its own dedicated case) — must neither invent gain
            // nor notch: output bit-equal to input. The state half is its own case below.
            for (unknownType in listOf(EqCore.BELL, EqCore.RAW_TAP, 99, -42)) {
                val core = EqCore(1, shape).also {
                    it.configureSection(0, unknownType, 1000.0, 1.0, 6.0, 1.5, sr.toDouble())
                }
                val buf = AudioBuffer(blockFrames)
                input.copyInto(buf, 0, 0, blockFrames)
                core.process(buf, 0, blockFrames)
                for (i in 0 until blockFrames) {
                    buf[i].toRawBits() shouldBe input[i].toRawBits()
                }
            }
        }

        "non-finite and huge input samples propagate bit-identically ($shapeName)" {
            // The signal path is CLAMP-FREE (raw engine — the class KDoc contract): huge
            // finite values, Inf, and NaN must flow through the fused core exactly as through
            // the chained nodes. A well-meant NaN scrub or output coerce on either side
            // reddens here (a scrubbed sample is finite where the oracle's is NaN — the
            // NaN-payload exception in assertChainParity cannot mask it). Each pathology gets
            // its OWN data variant hitting CLEAN state mid-array: SVF state never recovers
            // from Inf/NaN, so a single escalating array masks every pathology after the
            // first (proven live — a NaN-scrub mutation survived because the NaN arrived
            // after an Inf had already NaN'd the state). The pre-split escalating array also
            // caught the NaN-payload issue (section-major shapes propagated the input's
            // 0x7FF8... NaN where oracle + sampleMajor produced x86's fresh 0xFFF8... — both
            // NaN, both correct, JIT-dependent which); the split variants no longer mix
            // payload families, so assertChainParity's NaN exception is a FORWARD guard
            // against platform/JIT variation, not currently exercised — do not "clean it up".
            for (pathology in listOf(1e12, Double.POSITIVE_INFINITY, Double.NaN)) {
                val wild = DoubleArray(blockFrames * (blocks + 1)) { i -> input[i] }.also {
                    it[40] = pathology
                }
                for ((_, single) in singleSections) {
                    assertChainParity(listOf(single), shape, data = wild)
                }
            }
        }

        "disableSection retires a section to PASSTHROUGH and zeroes its state ($shapeName)" {
            // The sanctioned retire call for pooled cores: after disableSection the slot must
            // pass block 2 through bit-untouched (type back to UNCONFIGURED).
            val core = buildCore(listOf(Section(EqCore.LOWPASS, 1234.0, 1.7)), shape)
            val buf = AudioBuffer(blockFrames)
            input.copyInto(buf, 0, 0, blockFrames)
            core.process(buf, 0, blockFrames)

            core.disableSection(0)
            input.copyInto(buf, 0, blockFrames, 2 * blockFrames)
            core.process(buf, 0, blockFrames)
            for (i in 0 until blockFrames) {
                buf[i].toRawBits() shouldBe input[blockFrames + i].toRawBits()
            }

            // Re-enable: state was ZEROED on disable (KDoc — releasing stale pre-disable
            // integrator energy here would thump), so block 3 must equal a FRESH core's
            // first block bit-for-bit.
            core.configureSection(0, EqCore.LOWPASS, 1234.0, 1.7, 0.0, 1.0, sr.toDouble())
            val fresh = buildCore(listOf(Section(EqCore.LOWPASS, 1234.0, 1.7)), shape)
            val bufFresh = AudioBuffer(blockFrames)
            input.copyInto(buf, 0, 2 * blockFrames, 3 * blockFrames)
            input.copyInto(bufFresh, 0, 2 * blockFrames, 3 * blockFrames)
            core.process(buf, 0, blockFrames)
            fresh.process(bufFresh, 0, blockFrames)
            for (i in 0 until blockFrames) {
                buf[i].toRawBits() shouldBe bufFresh[i].toRawBits()
            }
        }

        "out-of-range section indices are ignored ($shapeName)" {
            // configureSection/disableSection run at control rate on the AUDIO thread — they
            // must never throw (house fall-through; JS typed arrays silently drop OOB writes,
            // the guard makes JVM behave the same). The core must act as if the calls never
            // happened.
            val core = buildCore(listOf(Section(EqCore.LOWPASS, 1234.0, 1.7)), shape)
            core.configureSection(1, EqCore.HIGHPASS, 440.0, 0.707, 0.0, 1.0, sr.toDouble())
            core.configureSection(-1, EqCore.HIGHPASS, 440.0, 0.707, 0.0, 1.0, sr.toDouble())
            core.disableSection(1)
            core.disableSection(-1)

            val oracle = chainOracle(
                BufferSourceIgnitor(input),
                listOf(Section(EqCore.LOWPASS, 1234.0, 1.7)),
            )
            val c = ctx()
            val bufCore = AudioBuffer(blockFrames)
            val bufOracle = AudioBuffer(blockFrames)
            input.copyInto(bufCore, 0, 0, blockFrames)
            core.process(bufCore, 0, blockFrames)
            oracle.generate(bufOracle, 220.0, c)
            for (i in 0 until blockFrames) {
                bufCore[i].toRawBits() shouldBe bufOracle[i].toRawBits()
            }
        }

        "unconfigured sections are PASSTHROUGH ($shapeName)" {
            // The surface contract says configure EVERY section before process(); the BREACH
            // degradation is pinned here: types default to UNCONFIGURED (-1), which passes
            // through. A zero-filled type default would render all-zero-coefficient LOWPASS
            // SILENCE on the unconfigured slot and kill the whole chain. The hole sits
            // BEFORE the configured section: "skip this section" and "abort the chain" are
            // only distinguishable when configured work follows the hole.
            val core = EqCore(2, shape).also {
                // section 0 deliberately NOT configured
                it.configureSection(1, EqCore.LOWPASS, 1234.0, 1.7, 0.0, 1.0, sr.toDouble())
            }
            val oracle = chainOracle(
                BufferSourceIgnitor(input),
                listOf(Section(EqCore.LOWPASS, 1234.0, 1.7)),
            )
            val c = ctx()
            val bufCore = AudioBuffer(blockFrames)
            val bufOracle = AudioBuffer(blockFrames)
            repeat(blocks) { blk ->
                input.copyInto(bufCore, 0, blk * blockFrames, (blk + 1) * blockFrames)
                core.process(bufCore, 0, blockFrames)
                oracle.generate(bufOracle, 220.0, c)
                for (i in 0 until blockFrames) {
                    bufCore[i].toRawBits() shouldBe bufOracle[i].toRawBits()
                }
                c.voiceElapsedFrames += blockFrames
            }
        }

        "unknown section mid-chain is transparent to the sections after it ($shapeName)" {
            // With a single section, "skip THIS section" and "skip the REST" are
            // indistinguishable — only a chain with sections AFTER the unknown one can tell
            // them apart: the trailing highpass must still run.
            val lp = Section(EqCore.LOWPASS, 1234.0, 1.7)
            val hp = Section(EqCore.HIGHPASS, 440.0, 0.707)
            val core = EqCore(3, shape).also {
                it.configureSection(0, lp.type, lp.freq, lp.q, 0.0, 1.0, sr.toDouble())
                it.configureSection(1, EqCore.BELL, 1000.0, 1.0, 6.0, 1.5, sr.toDouble())
                it.configureSection(2, hp.type, hp.freq, hp.q, 0.0, 1.0, sr.toDouble())
            }
            val oracle = chainOracle(BufferSourceIgnitor(input), listOf(lp, hp))
            val c = ctx()
            val bufCore = AudioBuffer(blockFrames)
            val bufOracle = AudioBuffer(blockFrames)
            repeat(blocks) { blk ->
                input.copyInto(bufCore, 0, blk * blockFrames, (blk + 1) * blockFrames)
                core.process(bufCore, 0, blockFrames)
                oracle.generate(bufOracle, 220.0, c)
                for (i in 0 until blockFrames) {
                    bufCore[i].toRawBits() shouldBe bufOracle[i].toRawBits()
                }
                c.voiceElapsedFrames += blockFrames
            }
        }

        "unknown section leaves its state untouched ($shapeName)" {
            // Output parity alone can't see state: a mutation that runs the recurrence but
            // still passes the input through stays green above (sample-major's tap `when` has
            // an explicit passthrough else). Reconfiguring the slot to LOWPASS is the
            // observation channel — configureSection keeps state (snap semantics), so a core
            // whose unknown section secretly advanced ic1/ic2 diverges from a fresh core on
            // the very next block. Parameterized over BOTH guard halves: BELL (t > NOTCH)
            // and -42 (t < LOWPASS) — configureSection stores real coefficients regardless
            // of type, so a dropped negative guard half advances state invisibly.
            for (unknownType in listOf(EqCore.BELL, -42)) {
                val core = EqCore(1, shape).also {
                    it.configureSection(0, unknownType, 1234.0, 1.7, 6.0, 1.5, sr.toDouble())
                }
                val warm = AudioBuffer(blockFrames)
                input.copyInto(warm, 0, 0, blockFrames)
                core.process(warm, 0, blockFrames)

                core.configureSection(0, EqCore.LOWPASS, 1234.0, 1.7, 0.0, 1.0, sr.toDouble())
                val fresh = buildCore(listOf(Section(EqCore.LOWPASS, 1234.0, 1.7)), shape)
                val bufCore = AudioBuffer(blockFrames)
                val bufFresh = AudioBuffer(blockFrames)
                input.copyInto(bufCore, 0, blockFrames, 2 * blockFrames)
                input.copyInto(bufFresh, 0, blockFrames, 2 * blockFrames)
                core.process(bufCore, 0, blockFrames)
                fresh.process(bufFresh, 0, blockFrames)
                for (i in 0 until blockFrames) {
                    bufCore[i].toRawBits() shouldBe bufFresh[i].toRawBits()
                }
            }
        }

        "reset zeroes state but keeps the curve ($shapeName)" {
            // Four sections with distinct types: a per-slot reset shortcut (zeroing only
            // slot 0) leaves slots 1..3 ringing and reddens here — the pooling scenario
            // reset() exists for is multi-section.
            val sections = listOf(
                Section(EqCore.NOTCH, 210.0, 2.5),
                Section(EqCore.HIGHPASS, 440.0, 0.707),
                Section(EqCore.BANDPASS, 850.0, 0.9),
                Section(EqCore.LOWPASS, 1234.0, 1.7),
            )
            val fresh = buildCore(sections, shape)
            val recycled = buildCore(sections, shape)

            // Dirty the recycled core's state, then reset — it must match a fresh core
            // bit-for-bit WITHOUT reconfiguration (coefficients persist).
            val warm = AudioBuffer(blockFrames)
            input.copyInto(warm, 0, 0, blockFrames)
            recycled.process(warm, 0, blockFrames)
            recycled.reset()

            val bufFresh = AudioBuffer(blockFrames)
            val bufRecycled = AudioBuffer(blockFrames)
            input.copyInto(bufFresh, 0, 0, blockFrames)
            input.copyInto(bufRecycled, 0, 0, blockFrames)
            fresh.process(bufFresh, 0, blockFrames)
            recycled.process(bufRecycled, 0, blockFrames)
            for (i in 0 until blockFrames) {
                bufRecycled[i].toRawBits() shouldBe bufFresh[i].toRawBits()
            }
        }
    }

    "an unknown loop shape fails at construction, not silently at process" {
        // The shapes are contractually bit-identical, so NO parity row can catch a wrong
        // dispatch — a stale shape constant must fail loudly at construction (never on the
        // render path) instead of silently rerouting the bake-off to a self-comparison.
        shouldThrow<IllegalArgumentException> { EqCore(1, 99) }
        shouldThrow<IllegalArgumentException> { EqCore(1, -1) }
        // The exact boundary: an off-by-one widening of the require range would accept a
        // shape with no dispatch arm — a whole-EQ no-op.
        shouldThrow<IllegalArgumentException> { EqCore(1, EqCore.SHAPE_SECTION_MAJOR_LOCALS + 1) }
    }

    "all three loop shapes are bit-identical to each other" {
        // Transitive from the oracle parities, asserted directly for the bake-off's sake.
        val sections = listOf(
            Section(EqCore.NOTCH, 210.0, 2.5),
            Section(EqCore.HIGHPASS, 440.0, 0.707),
            Section(EqCore.LOWPASS, 5300.0, 0.707),
        )
        val cores = shapes.map { (_, sh) -> buildCore(sections, sh) }
        val bufs = cores.map { AudioBuffer(blockFrames) }
        repeat(blocks) { blk ->
            cores.forEachIndexed { ci, core ->
                input.copyInto(bufs[ci], 0, blk * blockFrames, (blk + 1) * blockFrames)
                core.process(bufs[ci], 0, blockFrames)
            }
            for (i in 0 until blockFrames) {
                for (ci in 1 until bufs.size) {
                    bufs[ci][i].toRawBits() shouldBe bufs[0][i].toRawBits()
                }
            }
        }
    }
})

/**
 * Test helper: plays a fixed array window-by-window — the deterministic input source for the
 * Ignitor-form oracle chains. Each [generate] call copies the next `length` samples into
 * `[offset, offset+length)` and advances the cursor (matching how a real upstream advances
 * per block). [startAt] seats the cursor for sub-block first-call cases.
 */
internal class BufferSourceIgnitor(
    private val data: DoubleArray,
    startAt: Int = 0,
) : Ignitor {
    private var pos = startAt

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val end = ctx.offset + ctx.length
        for (i in ctx.offset until end) {
            buffer[i] = data[pos++]
        }
    }
}
