/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.ignitor.PhasePools
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.builtInSources
import io.peekandpoke.klang.audio_be.ignitor.registerDefaults
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.math.abs
import kotlin.random.Random

/**
 * The oscillator parameter bag has THREE raw reads across two readers, and all of them are guarded
 * here. The rule they follow is the one every wire number follows (`/dsl-design` section 4) and the
 * one the `Param` leaf already applied to every slot (`IgnitorDslRuntime`): a non-finite value was
 * never set, so it reads as unset. The bag is an open `Map<String, Double>` that any frontend may
 * fill, and sprudel alone reaches a non-finite one through a string atom (`"NaN"` and `"Infinity"`
 * both parse) or an overflowing power.
 *
 * **This KDoc is the one home of what each non-finite value used to do.** The lines at the guards
 * themselves say only which tests disagree; everything measured lives here.
 *
 * ## `analog`, read at the top of `VoiceFactory.makeVoice` (the filters)
 *
 * The readers disagree about which test a non-finite value fails.
 * `perVoiceCutoffOffsetMul` tests `analog <= 0.0`, which a NaN FAILS, so the per-voice multiplier
 * came out NaN, every cutoff on the voice came out NaN, and `bilinearK`'s own non-finite guard
 * substituted 1 kHz. `AnalogDrift` and the SVF's saturating branch test `analog > 0.0` instead,
 * which a NaN also fails, so they were NaN-safe by accident and never Infinity-safe.
 *
 * - **NaN.** Every filter on the voice retuned to 1 kHz. On an exciter that never touches the
 *   voice's rng, `saw` here, the poisoned voice rendered a voice whose lowpass really SITS at 1 kHz
 *   sample for sample; that is measured, and it is the strongest statement available. It does NOT
 *   generalise: failing `analog <= 0.0` also consumes one `nextDouble()` PER FILTER off
 *   `voiceRandom` that the `analog = 0` path does not, before the exciter is built off the same
 *   stream. On `supersaw`, `dust`, `whitenoise` or `pluck` the poisoned voice was therefore 1 kHz
 *   AND a shifted jitter or noise stream, so it was not any nameable voice at all. The `supersaw`
 *   row below is that second path.
 * - **`+Infinity`.** Worse than the NaN, and the reason this guard is not cosmetic. It fails
 *   `analog <= 0.0` AND passes `analog > 0.0`, so the SVF took its saturating branch with
 *   `driveScale = Infinity`. At the first sample `ic1eq` is 0.0, so `diodePairResistanceApprox(0.0)`
 *   is exactly 1.0, `tCfb` is exactly 0.0, and `kEff = k + 2.0 * Infinity * 0.0` is NaN. Every
 *   sample of the voice was NaN from frame 0 (measured: all 1024 frames of the render below), and
 *   nothing in `Voice` or `Cylinder` scrubs it, so the NaN reached the ORBIT MIX and stayed in the
 *   orbit's send chain for the rest of the playback:
 *   `note("c3").oscp("analog", "Infinity").lpf(2000)` silenced an orbit. That is exactly the failure
 *   the `gain` guard's comment in `VoiceFactory` describes for its own reader.
 * - **`-Infinity`** passed `<= 0.0` and failed `> 0.0`, so it was already safe. Its row states the
 *   rule rather than closing a defect.
 *
 * ## `analog`, read again in the sample branch (the wow and flutter)
 *
 * The sample branch used to look the bag up a SECOND time, so it had its own defect even while the
 * filters were guarded, and reverting that one read alone still turns the `+Infinity` row below red.
 * `SampleIgnitor` gives its `AnalogDrift` the raw value, and the lane tests `analog > 0.0`:
 *
 * - **`+Infinity`.** The lane is `active` and its block multiplier is non-finite, so the playhead
 *   goes non-finite on the first increment. Measured on a 1024-frame render of a sine PCM: frame 0
 *   is the PCM's own first sample and every frame after it is NaN. Not a silenced drum, a drum
 *   replaced by NaN, which then reaches the orbit mix exactly as the filter case does.
 * - **NaN** fails `analog > 0.0`, so the lane was never active and this read was already NaN-safe.
 *   Its row states the rule; the mutation that reverts this read leaves it green.
 *
 * The branch now takes the guarded local, so the bag is read ONCE per voice and the sample
 * ignitor's drift cannot disagree with the filters'.
 *
 * ## `onepole`, reached through `IgnitorRegistry.createExciter`
 *
 * The gate used to be a bare `oscParams["onepole"] > 0.0` in that function, which a NaN fails but
 * an `+Infinity` passes. So an `+Infinity` built a one-pole lowpass over the whole instrument and
 * `bilinearK` then clamped its cutoff to 1 kHz: a value that named no frequency at all rendered
 * exactly what `onepole(1000)` renders. Measured.
 *
 * Since phase 3 step 2 (2026-09-20) the registry places an `IgnitorDsl.OnePoleLowpass` in the tree
 * instead (around an authored instrument at note-on; on a built-in's source since step 6) and THE gate decides it (`IgnitorDslRuntime`, its `gatedOff` KDoc), with the same off
 * value and the leaf's own unset rule. The rows below are unchanged and still green, which is how
 * the move proved itself; keep them until the door becomes an ordinary instrument slot.
 *
 * The two `analog` reads disappear when those doors become slots in the tree too.
 */
class VoiceBagGuardSpec : StringSpec({

    val sampleRate = 48000
    val blockFrames = 128
    val blocks = 8
    val frames = blocks * blockFrames

    // ── `analog` at the factory: the filters, and the sample branch ───────────────────────────

    fun voiceOf(data: VoiceData, getSample: (SampleRequest) -> SampleStore.SampleEntry.Complete?): Voice {
        // `stripsaw` / `stripsupersaw`: the built-ins' sources as AUTHORED instruments, so the factory's own
        // `analog` read and the voice STRIP's filters are what these rows reach. A built-in runs no strip
        // since phase 3 step 6; its twin row below pins the tree's `Param` leaf instead.
        val registry = IgnitorRegistry().apply {
            registerDefaults()
            register("stripsaw", builtInSources().getValue("saw"))
            register("stripsupersaw", builtInSources().getValue("supersaw"))
        }
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            sampleRateDouble = sampleRate.toDouble(),
            blockFrames = blockFrames,
            ignitorRegistry = registry,
            pipelineRegistry = PipelineRegistry(),
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val scheduled = ScheduledVoice(
            playbackId = "test",
            data = data,
            startTime = 0.0,
            gateEndTime = 1.0,
            playbackStartTime = 0.0,
        )

        return factory.makeVoice(
            scheduled = scheduled,
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(
                playbackId = "test",
                ignitorRegistry = registry,
                phasePools = PhasePools(Random(1)),
            ),
            getSample = getSample,
        ) ?: error("makeVoice returned null")
    }

    /**
     * The left mix bus of a factory-built voice over [blocks] blocks. The buffer is copied out and
     * zeroed per block, because nothing clears a cylinder's mix bus when a voice is rendered on its
     * own; the engine's own block loop does that.
     */
    fun renderVoice(
        data: VoiceData,
        getSample: (SampleRequest) -> SampleStore.SampleEntry.Complete? = { null },
    ): DoubleArray {
        val voice = voiceOf(data, getSample)
        val ctx = createContext(blockStart = 0.0, blockFrames = blockFrames, sampleRate = sampleRate)
        val out = DoubleArray(frames)

        repeat(blocks) { block ->
            ctx.blockStart = (block * blockFrames).toDouble()
            voice.render(ctx)

            val cylinder = ctx.cylinders.getOrInit(voice.cylinderId, voice, 0.0)

            cylinder.mixBuffer.left.copyInto(out, block * blockFrames, 0, blockFrames)
            cylinder.mixBuffer.left.fill(0.0)
            cylinder.mixBuffer.right.fill(0.0)
        }

        return out
    }

    /** An oscillator through one lowpass. [lowpassHz] is where that filter is asked to sit. */
    fun throughLowpass(analog: Double?, sound: String = "stripsaw", lowpassHz: Double = 8000.0): VoiceData =
        VoiceData.empty.copy(
            freqHz = 220.0,
            sound = sound,
            filters = FilterDefs(listOf(FilterDef.LowPass(freq = lowpassHz, q = 1.0))),
            oscParams = analog?.let { mapOf("analog" to it) },
        )

    fun peakOf(buf: DoubleArray): Double = buf.maxOf { abs(it) }

    fun assertSameBits(clue: String, expected: DoubleArray, actual: DoubleArray) {
        for (i in expected.indices) {
            withClue("$clue, frame $i") {
                actual[i].toRawBits() shouldBe expected[i].toRawBits()
            }
        }
    }

    "a finite analog is consumed, so the rows below are not comparing two identical voices" {
        val unset = renderVoice(throughLowpass(null))
        val real = renderVoice(throughLowpass(5.0))

        withClue("not-silence floor") {
            peakOf(unset) shouldBeGreaterThan 0.01
        }

        withClue("engagement: analog 5 must not render what an unset analog renders") {
            real.toList() shouldNotBe unset.toList()
        }
    }

    "a NaN analog reads as unset and no longer retunes the voice's filters to 1 kHz" {
        val unset = renderVoice(throughLowpass(null))
        val poisoned = renderVoice(throughLowpass(Double.NaN))
        val reallyAtOneKilohertz = renderVoice(throughLowpass(null, lowpassHz = 1000.0))

        withClue("not-silence floor") {
            peakOf(unset) shouldBeGreaterThan 0.01
        }

        // `saw` draws nothing from the voice rng, which is what lets this row be this strong: with
        // the guard removed it fails HERE, on the second line, before the bit comparison below.
        withClue("a NaN analog must not render what a voice whose lowpass really sits at 1 kHz renders") {
            reallyAtOneKilohertz.toList() shouldNotBe unset.toList()
            poisoned.toList() shouldNotBe reallyAtOneKilohertz.toList()
        }

        assertSameBits("NaN analog against unset", unset, poisoned)
    }

    "a NaN analog on an exciter that draws from the voice rng reads as unset too" {
        // The second path of the same defect, and the reason the row above does not generalise.
        // Failing `analog <= 0.0` consumes one `nextDouble()` per filter off `voiceRandom` BEFORE
        // the exciter is built off the same stream, so a supersaw's detune jitter moved as well as
        // its cutoff. Nothing but the bit comparison can state that; the poisoned voice was not any
        // nameable voice.
        val unset = renderVoice(throughLowpass(null, sound = "stripsupersaw"))
        val poisoned = renderVoice(throughLowpass(Double.NaN, sound = "stripsupersaw"))

        withClue("not-silence floor") {
            peakOf(unset) shouldBeGreaterThan 0.01
        }

        assertSameBits("NaN analog against unset, supersaw", unset, poisoned)
    }

    "a +Infinity analog reads as unset" {
        val unset = renderVoice(throughLowpass(null))
        val poisoned = renderVoice(throughLowpass(Double.POSITIVE_INFINITY))

        withClue("not-silence floor") {
            peakOf(unset) shouldBeGreaterThan 0.01
        }

        withClue("every sample must be finite: the poisoned voice used to reach the orbit mix as NaN") {
            poisoned.all { it.isFinite() } shouldBe true
        }

        assertSameBits("+Infinity analog against unset", unset, poisoned)
    }

    "a -Infinity analog reads as unset" {
        val unset = renderVoice(throughLowpass(null))
        val poisoned = renderVoice(throughLowpass(Double.NEGATIVE_INFINITY))

        assertSameBits("-Infinity analog against unset", unset, poisoned)
    }

    "an explicit analog of 0 renders what an unset analog renders" {
        val unset = renderVoice(throughLowpass(null))

        assertSameBits("analog 0 against unset", unset, renderVoice(throughLowpass(0.0)))
    }

    "the BUILT-IN twin: a non-finite analog reads as unset in classic()'s filters too (the Param leaf)" {
        // A built-in's filters read `analog` through the `Slots.analog` leaf, which reads a non-finite
        // override as unset: the same rule as the factory's guard, at the one place a slot resolves.
        val unset = renderVoice(throughLowpass(null, sound = "saw"))

        withClue("not-silence floor") {
            peakOf(unset) shouldBeGreaterThan 0.01
        }

        for (poison in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertSameBits("$poison analog against unset, built-in saw", unset, renderVoice(throughLowpass(poison, sound = "saw")))
        }

        withClue("engagement: a finite analog does change the built-in") {
            renderVoice(throughLowpass(5.0, sound = "saw")).toList() shouldNotBe unset.toList()
        }
    }

    // ── `analog` in the sample branch: the wow and flutter of SampleIgnitor ───────────────────

    val sampleSound = "bagguard-sample"

    /**
     * A non-constant PCM on purpose: wow and flutter modulates the playback RATE, and rate
     * modulation is invisible on DC (reading a constant at any speed yields the constant). A sine
     * makes the drift values output-bearing (the `SeededPlaybackReproducibilitySpec` lesson).
     */
    val sampleEntry = SampleStore.SampleEntry.Complete(
        req = SampleRequest(bank = null, sound = sampleSound, index = null, note = null),
        note = null,
        pitchHz = 440.0,
        sample = TestSamples.sine(size = frames * 2, sampleRate = sampleRate),
    )

    fun sampleVoice(analog: Double?): VoiceData = VoiceData.empty.copy(
        freqHz = 440.0,
        sound = sampleSound,
        oscParams = analog?.let { mapOf("analog" to it) },
    )

    fun renderSample(analog: Double?): DoubleArray = renderVoice(sampleVoice(analog)) { sampleEntry }

    "a finite analog reaches the sample ignitor's wow and flutter" {
        val unset = renderSample(null)
        val real = renderSample(3.0)

        withClue("not-silence floor: the sample must actually play") {
            peakOf(unset) shouldBeGreaterThan 0.01
        }

        withClue("engagement: analog 3 must not render what an unset analog renders") {
            real.toList() shouldNotBe unset.toList()
        }
    }

    "a +Infinity analog no longer replaces a sample voice with NaN" {
        // The row that is specific to the THIRD read: reverting the sample branch alone, with the
        // filter read still guarded, turns this row and only this row red.
        val unset = renderSample(null)
        val poisoned = renderSample(Double.POSITIVE_INFINITY)

        withClue("every frame after the first used to be NaN, and it reached the orbit mix") {
            poisoned.all { it.isFinite() } shouldBe true
            peakOf(poisoned) shouldBeGreaterThan 0.01
        }

        assertSameBits("+Infinity analog against unset, sample voice", unset, poisoned)
    }

    "a NaN analog reads as unset on a sample voice, as it always did" {
        // `AnalogDrift` tests `analog > 0.0`, which a NaN fails, so this read was already NaN-safe
        // and this row states the rule rather than closing a defect.
        assertSameBits("NaN analog against unset, sample voice", renderSample(null), renderSample(Double.NaN))
    }

    // ── `onepole`, hung on the tree by IgnitorRegistry.createExciter ──────────────────────────

    fun exciter(onepole: Double?): Ignitor {
        val registry = IgnitorRegistry().apply { registerDefaults() }
        val data = VoiceData.empty.copy(
            freqHz = 220.0,
            sound = "saw",
            oscParams = onepole?.let { mapOf("onepole" to it) },
        )

        return registry.createExciter("saw", data, freqHz = 220.0, random = Random(7))?.ignitor
            ?: error("no exciter")
    }

    fun renderExciter(onepole: Double?): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = frames * 2,
            gateEndFrame = frames * 2,
            releaseFrames = 0,
            scratchBuffers = ScratchBuffers(blockFrames = blockFrames),
            voiceElapsedFrames = 0,
        )
        val chain = exciter(onepole)
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(frames)

        ctx.updateOffsetAndLength(0, blockFrames)

        repeat(blocks) { block ->
            chain.generate(buf, 220.0, ctx)
            buf.copyInto(out, block * blockFrames, 0, blockFrames)
            ctx.voiceElapsedFrames += blockFrames
        }

        return out
    }

    "a finite onepole still builds its filter, so the rows below are not comparing two bare saws" {
        val unset = renderExciter(null)
        val atOneKilohertz = renderExciter(1000.0)

        withClue("not-silence floor") {
            peakOf(unset) shouldBeGreaterThan 0.01
        }

        withClue("engagement: onepole 1000 must not render what an unset onepole renders") {
            atOneKilohertz.toList() shouldNotBe unset.toList()
        }
    }

    "an +Infinity onepole reads as unset and no longer builds a 1 kHz one-pole" {
        val unset = renderExciter(null)
        val poisoned = renderExciter(Double.POSITIVE_INFINITY)

        withClue("THE defect: an +Infinity onepole passed the `> 0.0` gate and became onepole(1000)") {
            poisoned.toList() shouldNotBe renderExciter(1000.0).toList()
        }

        assertSameBits("+Infinity onepole against unset", unset, poisoned)
    }

    "a NaN onepole reads as unset" {
        assertSameBits("NaN onepole against unset", renderExciter(null), renderExciter(Double.NaN))
    }

    "a -Infinity onepole reads as unset" {
        assertSameBits(
            "-Infinity onepole against unset",
            renderExciter(null),
            renderExciter(Double.NEGATIVE_INFINITY),
        )
    }
})
