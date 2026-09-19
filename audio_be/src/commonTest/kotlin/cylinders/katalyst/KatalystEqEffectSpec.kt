/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.filters.EqCore
import io.peekandpoke.klang.audio_be.filters.eqSectionSpec
import io.peekandpoke.klang.audio_be.ignitor.ConstantIgnitor
import io.peekandpoke.klang.audio_be.ignitor.EqIgnitor
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The orbit's mix EQ ([KatalystEqEffect]): the same curve as on a voice, in one place instead of
 * N, and click-free when it moves.
 *
 * The claim the whole stage rests on is the motivation section's case 1: a static linear filter
 * moves from the voice to the bus LOSSLESSLY and gets N times cheaper. Two rows measure that
 * claim on the real signal path (one voice, then two), and the per-variant rows pin every section
 * kind against the per-voice adapter that drives the same [EqCore].
 */
class KatalystEqEffectSpec : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100
    val blocks = 12
    val orbit = 1

    fun c(value: Double): IgnitorDsl = IgnitorDsl.Constant(value)

    fun ctx(): KatalystContext = KatalystContext(
        blockFrames = blockFrames,
        mixBuffer = StereoBuffer(blockFrames),
    )

    /** A declared chain, slot-driven, as the cylinder builds one for a `katalyst(...)` name. */
    fun chainOf(vararg stages: KatalystStageDsl): KatalystChain = KatalystChainBuilder.build(
        dsl = KatalystDsl.of(*stages),
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = SizedBuffers.forRings(sampleRate),
        reverbs = ReverbUnits(sampleRate),
    )

    /**
     * A deterministic broadband probe: an LCG, which excites every band the sections below touch.
     * Silence or DC would pass a section whose coefficients never arrived.
     */
    fun noise(length: Int): DoubleArray {
        var x = 0x5EED
        return DoubleArray(length) {
            x = x * 1103515245 + 12345
            ((x ushr 8) and 0xFFFF) / 65535.0 - 0.5
        }
    }

    /** A sine at [freq], so a resonant section has something to ring on. */
    fun sine(length: Int, freq: Double): DoubleArray =
        DoubleArray(length) { sin(2.0 * PI * freq * it / sampleRate) * 0.5 }

    /** Plays [data] into the voice buffer, one window at a time: the per-voice upstream. */
    class ArraySource(private val data: DoubleArray) : Ignitor {
        private var cursor = 0

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in ctx.offset until ctx.windowEnd) {
                buffer[i] = if (cursor < data.size) data[cursor] else 0.0
                cursor++
            }
        }
    }

    /** The largest sample-to-sample step in [samples], the zipper measure. */
    fun maxStep(samples: DoubleArray): Double {
        var worst = 0.0

        for (i in 1 until samples.size) {
            val step = abs(samples[i] - samples[i - 1])

            if (step > worst) {
                worst = step
            }
        }

        return worst
    }

    fun rms(samples: DoubleArray): Double {
        var sum = 0.0

        for (sample in samples) {
            sum += sample * sample
        }

        return sqrt(sum / samples.size)
    }

    fun maxAbsDiff(a: DoubleArray, b: DoubleArray): Double {
        var worst = 0.0

        for (i in a.indices) {
            val diff = abs(a[i] - b[i])

            if (diff > worst) {
                worst = diff
            }
        }

        return worst
    }

    /** [data] through the ORBIT stage: the mix buffer, block by block, left channel out. */
    fun throughOrbit(chain: KatalystChain, data: DoubleArray): DoubleArray {
        chain.applyParams(null)

        val out = DoubleArray(data.size)
        val ctx = ctx()

        for (b in 0 until data.size / blockFrames) {
            val base = b * blockFrames

            for (i in 0 until blockFrames) {
                ctx.mixBuffer.left[i] = data[base + i]
                ctx.mixBuffer.right[i] = data[base + i]
            }

            chain.process(ctx)
            ctx.mixBuffer.left.copyInto(out, base, 0, blockFrames)
        }

        return out
    }

    /**
     * [data] through the PER-VOICE adapter, the oracle: the same [EqCore], the same section type
     * (from the same shared mapping), the same scalars, resolved once because every param is a
     * constant.
     */
    fun throughVoiceAdapter(section: IgnitorDsl.EqSection, values: DoubleArray, data: DoubleArray): DoubleArray {
        val spec = eqSectionSpec(section)
        val eq = EqIgnitor(
            upstream = ArraySource(data),
            sections = listOf(
                EqIgnitor.Section(
                    type = spec.type,
                    freq = ConstantIgnitor(values[KatalystEqEffect.KNOB_FREQ]),
                    q = ConstantIgnitor(values[KatalystEqEffect.KNOB_Q]),
                    db = spec.db?.let { ConstantIgnitor(values[KatalystEqEffect.KNOB_DB]) },
                    gain = spec.gain?.let { ConstantIgnitor(values[KatalystEqEffect.KNOB_GAIN]) },
                )
            ),
        )

        val igniteCtx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = data.size,
            gateEndFrame = data.size,
            releaseFrames = 0,
            scratchBuffers = ScratchBuffers(blockFrames),
        ).apply {
            updateOffsetAndLength(0, blockFrames)
            voiceElapsedFrames = 0
        }

        val out = DoubleArray(data.size)
        val buffer = AudioBuffer(blockFrames)

        for (b in 0 until data.size / blockFrames) {
            eq.generate(buffer, 220.0, igniteCtx)
            buffer.copyInto(out, b * blockFrames, 0, blockFrames)
            igniteCtx.voiceElapsedFrames += blockFrames
        }

        return out
    }

    /**
     * One rendered orbit: real voices, a real cylinder, the chain installed by name. The left
     * channel of the fusion mix, block seams included.
     *
     * This is the path the case-1 claim is about. Everything else in the file is measured on the
     * stage alone, which cannot see whether the orbit runs the EQ in the right place at all.
     */
    fun renderOrbit(signals: List<Ignitor>, chain: KatalystDsl): DoubleArray {
        val registry = KatalystRegistry()
        val cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate, katalysts = registry)

        registry.register("probe", chain)
        cylinders.requestChain(orbit, "probe")

        val renderCtx = Voice.RenderContext(
            cylinders = cylinders,
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = AudioBuffer(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )

        val voices = signals.map { signal ->
            VoiceTestHelpers.createSynthVoice(
                endFrame = (blocks * blockFrames).toDouble(),
                gateEndFrame = (blocks * blockFrames).toDouble(),
                cylinderId = orbit,
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                signal = signal,
            )
        }

        val out = DoubleArray(blocks * blockFrames)
        val fusion = StereoBuffer(blockFrames)

        for (b in 0 until blocks) {
            renderCtx.blockStart = (b * blockFrames).toDouble()
            cylinders.clearAll()
            fusion.clear()

            for (voice in voices) {
                voice.render(renderCtx)
            }

            cylinders.processAndMix(fusion)
            fusion.left.copyInto(out, b * blockFrames, 0, blockFrames)
        }

        return out
    }

    // ── Case 1: the filter moves to the bus losslessly ───────────────────────────────────────────

    "ONE voice: the same band on the voice and on its orbit render the same orbit output" {
        val data = noise(blocks * blockFrames)
        val band = IgnitorDsl.EqSection.Bell(freq = c(300.0), q = c(0.8), db = c(6.0))

        // The voice carries the EQ inside its ignitor; its orbit declares an empty chain, which is
        // bit-transparent by construction (no stage at all).
        val onVoice = renderOrbit(
            signals = listOf(
                EqIgnitor(
                    upstream = ArraySource(data),
                    sections = listOf(
                        EqIgnitor.Section(
                            type = eqSectionSpec(band).type,
                            freq = ConstantIgnitor(300.0),
                            q = ConstantIgnitor(0.8),
                            db = ConstantIgnitor(6.0),
                        )
                    ),
                )
            ),
            chain = KatalystDsl(emptyList()),
        )

        // The voice is plain; the orbit's chain carries the band.
        val onOrbit = renderOrbit(
            signals = listOf(ArraySource(data)),
            chain = KatalystDsl.of(KatalystStageDsl.Eq(sections = listOf(band))),
        )

        withClue("the band is doing something: the EQ'd render is not the dry one") {
            rms(onOrbit) shouldBeGreaterThan rms(data) * 0.5
            maxAbsDiff(onOrbit, DoubleArray(onOrbit.size)) shouldBeGreaterThan 0.1
        }

        // NOT bit-identical, and the reason is not the EQ: the voice path filters the mono signal
        // BEFORE the voice's gain and pan multiply it, the bus path filters the product, and IEEE
        // multiplication does not commute with the recurrence bit for bit. Superposition holds to
        // the last few bits, which is what "moves losslessly" means in floating point.
        maxAbsDiff(onVoice, onOrbit) shouldBeLessThan 1e-12
    }

    "TWO voices: the orbit's EQ is the EQ of the sum, which is the sum of the per-voice EQs" {
        val a = sine(blocks * blockFrames, 220.0)
        val b = sine(blocks * blockFrames, 990.0)
        val band = IgnitorDsl.EqSection.Bell(freq = c(300.0), q = c(0.8), db = c(6.0))

        fun voiceEq(data: DoubleArray) = EqIgnitor(
            upstream = ArraySource(data),
            sections = listOf(
                EqIgnitor.Section(
                    type = eqSectionSpec(band).type,
                    freq = ConstantIgnitor(300.0),
                    q = ConstantIgnitor(0.8),
                    db = ConstantIgnitor(6.0),
                )
            ),
        )

        val perVoice = renderOrbit(
            signals = listOf(voiceEq(a), voiceEq(b)),
            chain = KatalystDsl(emptyList()),
        )

        val onBus = renderOrbit(
            signals = listOf(ArraySource(a), ArraySource(b)),
            chain = KatalystDsl.of(KatalystStageDsl.Eq(sections = listOf(band))),
        )

        // Linearity is the whole license for moving the filter: two voices, one filter, and the
        // difference is float noise. A nonlinear stage on a bus would fail this row by design,
        // which is why the motivation section forbids moving one.
        maxAbsDiff(perVoice, onBus) shouldBeLessThan 1e-12
    }

    // ── Every section kind, against the per-voice adapter ────────────────────────────────────────

    // The scalars each row expects to reach the core, in the stage's own layout, plus the EqCore
    // type the shared mapping must produce for that variant. The type is asserted rather than
    // taken from the mapping, so a swapped arm reddens here as well as in `EqIgnitorSpec`.
    val variants = listOf(
        Triple("lowpass", IgnitorDsl.EqSection.Lowpass(freq = c(800.0), q = c(0.9)), EqCore.LOWPASS)
            to doubleArrayOf(800.0, 0.9, 0.0, 0.0),
        Triple("highpass", IgnitorDsl.EqSection.Highpass(freq = c(400.0), q = c(1.2)), EqCore.HIGHPASS)
            to doubleArrayOf(400.0, 1.2, 0.0, 0.0),
        Triple("bandpass", IgnitorDsl.EqSection.Bandpass(freq = c(600.0), q = c(2.0)), EqCore.BANDPASS)
            to doubleArrayOf(600.0, 2.0, 0.0, 0.0),
        Triple("notch", IgnitorDsl.EqSection.Notch(freq = c(1200.0), q = c(3.0)), EqCore.NOTCH)
            to doubleArrayOf(1200.0, 3.0, 0.0, 0.0),
        Triple("bell", IgnitorDsl.EqSection.Bell(freq = c(300.0), q = c(0.8), db = c(2.0)), EqCore.BELL)
            to doubleArrayOf(300.0, 0.8, 2.0, 0.0),
        Triple("tap", IgnitorDsl.EqSection.RawTap(freq = c(2700.0), q = c(2.0), gain = c(1.7)), EqCore.RAW_TAP)
            to doubleArrayOf(2700.0, 2.0, 0.0, 1.7),
    )

    for ((head, values) in variants) {
        val (name, section, type) = head

        "the $name section builds, processes, and is BIT-identical to the per-voice adapter" {
            eqSectionSpec(section).type shouldBe type

            val data = noise(blocks * blockFrames)
            val onOrbit = throughOrbit(chainOf(KatalystStageDsl.Eq(sections = listOf(section))), data)
            val onVoice = throughVoiceAdapter(section, values, data)

            withClue("the section is audible at all: it changed the probe") {
                maxAbsDiff(onOrbit, data) shouldBeGreaterThan 1e-3
            }

            // Bit-identical here, unlike the two rows above: the same core gets the same input and
            // the same coefficients, with no gain or pan multiply between the two paths.
            for (i in data.indices) {
                withClue("frame $i") {
                    onOrbit[i] shouldBe onVoice[i]
                }
            }
        }
    }

    "a bell at 0 dB is bit-transparent, through the core's explicit passthrough" {
        val data = noise(4 * blockFrames)
        val chain = chainOf(
            KatalystStageDsl.Eq(sections = listOf(IgnitorDsl.EqSection.Bell(freq = c(300.0), q = c(0.8), db = c(0.0))))
        )

        val out = throughOrbit(chain, data)

        for (i in data.indices) {
            withClue("frame $i") {
                // Raw bits: the explicit branch is what keeps a -0.0 sample negative, where the
                // algebraic `v0 + 0 * v1` would flip it.
                out[i].toRawBits() shouldBe data[i].toRawBits()
            }
        }
    }

    // ── A curve change does not click ────────────────────────────────────────────────────────────

    "a re-resolve that moves a coefficient crossfades: no step anywhere in the window" {
        // A resonant boost, then the same band as a deep cut: the two curves differ by ~24 dB at
        // the probe's frequency, so a bare instance swap would drop the output by most of the ring.
        val chain = chainOf(
            KatalystStageDsl.Eq(
                sections = listOf(
                    IgnitorDsl.EqSection.Bell(freq = c(300.0), q = c(4.0), db = IgnitorDsl.Param("eq.db", 12.0))
                )
            )
        )

        val data = sine(24 * blockFrames, 300.0)
        val out = DoubleArray(data.size)
        val ctx = ctx()
        // 12 ms at 44100 Hz is 529 frames, 4.1 blocks: the fade ENDS inside the window below, so
        // the seam where the old bank is dropped is measured too (the failure `KatalystFilterSwap`
        // would show if its ramp never advanced).
        val changeBlock = 12

        for (b in 0 until data.size / blockFrames) {
            if (b == changeBlock) {
                chain.applyParams(mapOf("eq.db" to -12.0))
            } else if (b == 0) {
                chain.applyParams(null)
            }

            val base = b * blockFrames

            for (i in 0 until blockFrames) {
                ctx.mixBuffer.left[i] = data[base + i]
                ctx.mixBuffer.right[i] = data[base + i]
            }

            chain.process(ctx)
            ctx.mixBuffer.left.copyInto(out, base, 0, blockFrames)
        }

        val settled = out.copyOfRange((changeBlock - 4) * blockFrames, changeBlock * blockFrames)
        val across = out.copyOfRange((changeBlock - 1) * blockFrames, (changeBlock + 8) * blockFrames)
        val after = out.copyOfRange((changeBlock + 8) * blockFrames, data.size)

        withClue("the change really happened: the boost is gone") {
            rms(settled) / rms(after) shouldBeGreaterThan 3.0
        }

        // The measure `KatalystBodyEffectSpec` uses for the same mechanism, widened to the whole
        // window: on a sine probe a per-sample step is phase-dependent, so the threshold is the
        // probe's OWN largest step, which the crossfade may not exceed by much. A bare swap steps
        // by the difference of the two rings, an order of magnitude more.
        maxStep(across) shouldBeLessThan maxStep(settled) * 1.5
    }

    "after the fade the old bank is gone: a transparent curve leaves the probe bit-identical" {
        val chain = chainOf(
            KatalystStageDsl.Eq(
                sections = listOf(
                    IgnitorDsl.EqSection.Bell(freq = c(300.0), q = c(4.0), db = IgnitorDsl.Param("eq.db", 12.0))
                )
            )
        )

        val data = sine(24 * blockFrames, 300.0)
        val ctx = ctx()
        val out = DoubleArray(data.size)

        chain.applyParams(null)

        for (b in 0 until data.size / blockFrames) {
            if (b == 12) {
                // 0 dB is the bit-transparent curve, so once the fade has run out, ANY residue of
                // the previous bank shows up as a difference from the input: its 24 dB ring, a
                // stale integrator, a fade that never finished.
                chain.applyParams(mapOf("eq.db" to 0.0))
            }

            val base = b * blockFrames

            for (i in 0 until blockFrames) {
                ctx.mixBuffer.left[i] = data[base + i]
                ctx.mixBuffer.right[i] = data[base + i]
            }

            chain.process(ctx)
            ctx.mixBuffer.left.copyInto(out, base, 0, blockFrames)
        }

        for (i in 17 * blockFrames until data.size) {
            withClue("frame $i") {
                out[i].toRawBits() shouldBe data[i].toRawBits()
            }
        }
    }

    "re-applying the same curve installs ONE bank, not one per block" {
        val fx = KatalystEqEffect(
            sampleRate = sampleRate.toDouble(),
            types = intArrayOf(EqCore.BELL),
        )

        val values = doubleArrayOf(300.0, 0.8, 2.0, 0.0)

        repeat(20) {
            fx.configure(values)
        }

        // The work pin: every install recomputes every section's coefficients on both channels and
        // starts a 12 ms crossfade. A stage that installed per block would sound almost the same
        // and never stop paying for it.
        fx.installs shouldBe 1
        fx.isEngaged shouldBe true
    }

    "an array of the wrong shape is ignored, and the installed curve stands" {
        // The never-throw policy of a control-rate door on the audio thread ([EqCore]'s own index
        // and window guards). Without the length check this call throws inside the compare pass or
        // `copyInto`, and on the JS worklet an escaped exception takes the whole processor.
        val fx = KatalystEqEffect(sampleRate = sampleRate.toDouble(), types = intArrayOf(EqCore.BELL))

        fx.configure(doubleArrayOf(300.0, 0.8, 2.0, 0.0))
        fx.configure(doubleArrayOf(1.0, 2.0))
        fx.configure(DoubleArray(0))
        fx.configure(doubleArrayOf(300.0, 0.8, 2.0, 0.0, 99.0))

        fx.installs shouldBe 1
        fx.isEngaged shouldBe true
    }

    "a knob that reads as UNSET installs once too, instead of once per block" {
        // The NaN guard in the change test: an unset slot is a non-finite number, and `NaN != NaN`
        // would make an unchanged curve look different on every single block.
        val chain = chainOf(
            KatalystStageDsl.Eq(
                sections = listOf(IgnitorDsl.EqSection.Lowpass(freq = IgnitorDsl.Param("eq.freq", SLOT_UNSET)))
            )
        )

        val fx = chain.pipeline[0] as KatalystEqEffect

        repeat(20) {
            chain.applyParams(null)
        }

        fx.installs shouldBe 1
    }

    // ── The two chain kinds, and the lifecycle ───────────────────────────────────────────────────

    "an eq appended to the classic block engages from the orbit's param state like any stage" {
        // `eq` is the one classic-block neighbour that never had a voice field, and it was
        // slot-driven one step before the rest of them (step 4). The row stays because it is the
        // cheapest place the classic-plus-eq shape is exercised end to end.
        val chain = KatalystChainBuilder.build(
            dsl = KatalystDsl(
                KatalystDsl.classic.stages +
                    KatalystStageDsl.Eq(sections = listOf(IgnitorDsl.EqSection.Bell(freq = c(300.0), db = c(6.0)))),
            ),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = ReverbUnits(sampleRate),
        )

        val fx = chain.pipeline.filterIsInstance<KatalystEqEffect>().single()

        fx.isEngaged shouldBe false

        chain.applyParams(null)

        fx.isEngaged shouldBe true
    }

    "retire clears the banks: a shelved stage rings nothing into its next tenant" {
        val chain = chainOf(
            KatalystStageDsl.Eq(sections = listOf(IgnitorDsl.EqSection.Bell(freq = c(300.0), q = c(8.0), db = c(18.0))))
        )

        val fx = chain.pipeline[0] as KatalystEqEffect
        val data = sine(8 * blockFrames, 300.0)
        val ctx = ctx()

        chain.applyParams(null)

        for (b in 0 until 8) {
            val base = b * blockFrames

            for (i in 0 until blockFrames) {
                ctx.mixBuffer.left[i] = data[base + i]
                ctx.mixBuffer.right[i] = data[base + i]
            }

            chain.process(ctx)
        }

        chain.retire()

        fx.isEngaged shouldBe false

        // The bank was ringing at q 8 and 18 dB when the cylinder went to the shelf. A retire that
        // left it installed would pour that ring into the next silent block.
        for (b in 0 until 4) {
            ctx.mixBuffer.clear()
            chain.process(ctx)

            for (i in 0 until blockFrames) {
                withClue("block $b frame $i") {
                    ctx.mixBuffer.left[i] shouldBe 0.0
                    ctx.mixBuffer.right[i] shouldBe 0.0
                }
            }
        }
    }

    // ── Stereo, and the lifecycle ────────────────────────────────────────────────────────────────

    "left and right are independent cores: a signal on one channel stays on it" {
        val chain = chainOf(
            KatalystStageDsl.Eq(sections = listOf(IgnitorDsl.EqSection.Bell(freq = c(300.0), q = c(4.0), db = c(12.0))))
        )

        chain.applyParams(null)

        val data = sine(8 * blockFrames, 300.0)
        val ctx = ctx()
        var leftEnergy = 0.0
        var rightEnergy = 0.0

        for (b in 0 until 8) {
            val base = b * blockFrames

            for (i in 0 until blockFrames) {
                ctx.mixBuffer.left[i] = data[base + i]
                ctx.mixBuffer.right[i] = 0.0
            }

            chain.process(ctx)

            for (i in 0 until blockFrames) {
                leftEnergy += abs(ctx.mixBuffer.left[i])
                rightEnergy += abs(ctx.mixBuffer.right[i])
            }
        }

        withClue("the left channel was filtered, so the boost is there") {
            leftEnergy shouldBeGreaterThan 0.1
        }

        withClue("and nothing crossed over: one core per channel, not one shared") {
            rightEnergy shouldBe 0.0
        }
    }

    "reset turns the stage off, and the bank that comes back from the ping-pong is silent on silence" {
        // The flush has one place, `EqBank.install`, and this is the row that fails without it.
        // Reaching a DIRTY bank takes three installs: the first takes bank 0, the second takes
        // bank 1 and parks bank 0 mid-ring, and the third takes bank 0 again. A `reset` in
        // between is what an orbit does when it deactivates, and it must not leave the bank that
        // comes back holding the previous tenant's resonance.
        val chain = chainOf(
            KatalystStageDsl.Eq(
                sections = listOf(
                    IgnitorDsl.EqSection.Bell(freq = c(300.0), q = c(8.0), db = IgnitorDsl.Param("eq.db", 18.0))
                )
            )
        )

        val fx = chain.pipeline[0] as KatalystEqEffect
        val data = sine(12 * blockFrames, 300.0)
        val ctx = ctx()

        fun loud(block: Int) {
            val base = block * blockFrames

            for (i in 0 until blockFrames) {
                ctx.mixBuffer.left[i] = data[base + i]
                ctx.mixBuffer.right[i] = data[base + i]
            }

            chain.process(ctx)
        }

        chain.applyParams(null) // install 1, bank 0

        for (b in 0 until 8) {
            loud(b)
        }

        chain.applyParams(mapOf("eq.db" to 12.0)) // install 2, bank 1; bank 0 is parked, mid-ring

        loud(8)
        loud(9)

        chain.reset()

        fx.isEngaged shouldBe false
        fx.installs shouldBe 2

        chain.applyParams(null) // install 3, bank 0 again

        fx.installs shouldBe 3

        for (b in 0 until 8) {
            ctx.mixBuffer.clear()
            chain.process(ctx)

            for (i in 0 until blockFrames) {
                withClue("block $b frame $i") {
                    ctx.mixBuffer.left[i] shouldBe 0.0
                    ctx.mixBuffer.right[i] shouldBe 0.0
                }
            }
        }
    }
})
