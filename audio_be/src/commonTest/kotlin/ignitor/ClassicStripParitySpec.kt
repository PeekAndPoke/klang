/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.engines.PipelineRegistry
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.FilterEnvDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.LfoShapes
import io.peekandpoke.klang.audio_bridge.PipelineDsl
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.lowpass
import io.peekandpoke.klang.audio_bridge.adsr
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.constants.ENV_DECLICK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.VCA_OFF_TEARDOWN_FADE_SECONDS
import kotlin.math.abs
import kotlin.random.Random

/**
 * **`classic()` against today's voice strip, one voice per slot row: THE TABLE STEP 6 IS JUDGED
 * AGAINST** (phase 3 step 5, `docs/tasks/builtin-instruments.md` section 9). A measurement, not an
 * identity claim: every row renders the same note twice through the real `VoiceFactory`,
 *
 *  - STRIP: the bare built-in `saw`, the row's settings on the typed `VoiceData` fields sprudel writes
 *    today, through the `modern` pipeline (crush, coarse, distort, the filters, tremolo, the VCA);
 *  - CLASSIC: `Sawtooth().classic()` registered as an instrument, the SAME settings as slots in the
 *    bag, on an EMPTY pipeline, so the strip adds nothing and the tree is the whole voice,
 *
 * and compares the left mix bus in raw bits. The onset is mid-block (frame 37) and the gate ends at a
 * quarter second, so the release and the voice's lifetime are inside the render. Every row runs at
 * 48 kHz AND at 44.1 kHz, because a stage time that is a whole number of frames at one rate is
 * fractional at the other, which is where the frame-count minor shows.
 *
 * Every row carries a VERDICT. An IDENTICAL row is pinned bit for bit. A DIVERGENT row is pinned as
 * NOT identical and names, in its title, the decision that explains the difference, so the row turns
 * red the day that decision lands and the table has to be read again, not silently trusted. Every
 * row also checks that its settings are ENGAGED (the strip voice differs from the untouched voice), so
 * no row compares two renders of nothing. The measured maximum difference of each divergent row is
 * recorded in the step 5 report, not here, because a number pinned in a spec invites "tuning" the
 * law to meet it.
 *
 * What is not a slot row here: `onepole` (the registry's tail, outside every instrument) and `pregain`
 * (not in `classic()`); both are identical by construction because both voices get them the same way.
 */
class ClassicStripParitySpec : StringSpec({

    val blockFrames = 128
    val blocks = 220
    val frames = blocks * blockFrames
    val gateSec = 0.25

    val classicSaw: IgnitorDsl = IgnitorDsl.Sawtooth().classic()

    /**
     * What the D3 fill row is compared against: the DOOR's filter with the same one stage named (its
     * compound fill supplies the depth), in front of the envelope `classic()` builds when nothing else
     * is written, so the only thing that can differ is the filter.
     */
    val doorFilledLowpass: IgnitorDsl = IgnitorDsl.Sawtooth()
        .lowpass(IgnitorDsl.Constant(600.0), analog = IgnitorDsl.Slots.analog, attackSec = IgnitorDsl.Constant(0.05), humanize = true)
        .adsr(VOICE_ADSR_ATTACK_SEC, VOICE_ADSR_DECAY_SEC, VOICE_ADSR_SUSTAIN_LEVEL, VOICE_ADSR_RELEASE_SEC, declickSeconds = ENV_DECLICK_SECONDS)

    fun render(data: VoiceData, sampleRate: Int): DoubleArray {
        val onsetSec = 37.0 / sampleRate
        val registry = IgnitorRegistry().apply {
            registerDefaults()
            register("classicsaw", classicSaw)
            register("doorfill", doorFilledLowpass)
        }
        val pipelines = PipelineRegistry().apply { register("bare", PipelineDsl(emptyList())) }
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            sampleRateDouble = sampleRate.toDouble(),
            blockFrames = blockFrames,
            ignitorRegistry = registry,
            pipelineRegistry = pipelines,
            cylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate),
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames),
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val noSamples: (SampleRequest) -> SampleStore.SampleEntry.Complete? = { null }
        val voice = factory.makeVoice(
            scheduled = ScheduledVoice(
                playbackId = "test",
                data = data,
                startTime = onsetSec,
                gateEndTime = onsetSec + gateSec,
                playbackStartTime = 0.0,
            ),
            backendStartTimeSec = 0.0,
            playbackCtx = PlaybackCtx(playbackId = "test", ignitorRegistry = registry, phasePools = PhasePools(Random(1))),
            getSample = noSamples,
        ) ?: error("makeVoice returned null")

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

    val base = VoiceData.empty.copy(freqHz = 220.0)

    fun strip(settings: VoiceData.() -> VoiceData, analog: Double?, sampleRate: Int): DoubleArray =
        render(base.copy(sound = "saw", oscParams = analog?.let { mapOf("analog" to it) }).settings(), sampleRate)

    fun classic(bag: Map<String, Double>, sampleRate: Int, sound: String = "classicsaw"): DoubleArray =
        render(base.copy(sound = sound, pipeline = "bare", oscParams = bag), sampleRate)

    fun maxDiff(a: DoubleArray, b: DoubleArray): Double = a.indices.maxOf { abs(a[it] - b[it]) }

    fun firstMismatch(a: DoubleArray, b: DoubleArray): Int = a.indices.firstOrNull { a[it].toRawBits() != b[it].toRawBits() } ?: -1

    val rates = listOf(48000, 44100)

    val untouchedStrip: Map<Int, DoubleArray> by lazy { rates.associateWith { strip({ this }, null, it) } }
    val untouchedClassic: Map<Int, DoubleArray> by lazy { rates.associateWith { classic(emptyMap(), it) } }

    /**
     * A row: its bag, the same settings on the strip's fields, and whether the two are identical at
     * 48 kHz ([identical]) and at 44.1 kHz ([identical44], the same unless the row says otherwise; then
     * [cause44] names why the lower rate differs).
     */
    class Row(
        val title: String,
        val identical: Boolean,
        val bag: Map<String, Double>,
        val identical44: Boolean = identical,
        val cause44: String? = null,
        val strip: VoiceData.() -> VoiceData,
    )

    fun filters(vararg defs: FilterDef): FilterDefs = FilterDefs(defs.toList())

    val rows = listOf(
        Row("untouched: the envelope alone, at the voice envelope's defaults", true, emptyMap()) { this },

        // ── crush, D1 ──
        Row("crush 4: D1 decided, floor, lands in step 4", false, mapOf("crush.amount" to 4.0)) { copy(crush = 4.0) },
        Row("crush 8: D1 decided, floor, lands in step 4", false, mapOf("crush.amount" to 8.0)) { copy(crush = 8.0) },
        Row("crush 1 (the quantizer runs): D1 decided, floor, lands in step 4", false, mapOf("crush.amount" to 1.0)) { copy(crush = 1.0) },

        // ── coarse ──
        Row("coarse 3", true, mapOf("coarse.amount" to 3.0)) { copy(coarse = 3.0) },
        Row("coarse 7.5", true, mapOf("coarse.amount" to 7.5)) { copy(coarse = 7.5) },

        // ── distort, D2 ──
        *listOf(
            Triple(0.3, "soft", 0), Triple(0.5, "soft", 0), Triple(1.0, "soft", 0), Triple(2.0, "soft", 0),
            Triple(0.5, "tube", 0), Triple(0.5, "gentle", 0), Triple(1.0, "hard", 0), Triple(1.0, "fold", 0),
            Triple(0.5, "rectify", 0), Triple(0.5, "soft", 2), Triple(0.5, "tube", 4),
        ).map { (amount, shape, os) ->
            Row(
                "distort $amount $shape x$os: D2 decided, A, the strip's law lands in step 4",
                false,
                mapOf("distort.amount" to amount, "distort.shape" to DistortionShapes.indexOf(shape), "distort.oversample" to os.toDouble()),
            ) { copy(distort = amount, distortShape = shape, distortOversample = os) }
        }.toTypedArray(),

        // ── the four filters, static ──
        Row("hpf 400", true, mapOf("hpf.freq" to 400.0)) { copy(filters = filters(FilterDef.HighPass(400.0, 0.707))) },
        Row("hpf 400 q 3 passes 2", true, mapOf("hpf.freq" to 400.0, "hpf.q" to 3.0, "hpf.passes" to 2.0)) {
            copy(filters = filters(FilterDef.HighPass(400.0, 3.0, passes = 2)))
        },
        Row("bpf 1000 q 2", true, mapOf("bpf.freq" to 1000.0, "bpf.q" to 2.0)) { copy(filters = filters(FilterDef.BandPass(1000.0, 2.0))) },
        Row("notch 1000 q 4", true, mapOf("notch.freq" to 1000.0, "notch.q" to 4.0)) { copy(filters = filters(FilterDef.Notch(1000.0, 4.0))) },
        Row("lpf 1200", true, mapOf("lpf.freq" to 1200.0)) { copy(filters = filters(FilterDef.LowPass(1200.0, 0.707))) },
        Row("lpf 1200 q 3 passes 3", true, mapOf("lpf.freq" to 1200.0, "lpf.q" to 3.0, "lpf.passes" to 3.0)) {
            copy(filters = filters(FilterDef.LowPass(1200.0, 3.0, passes = 3)))
        },
        Row(
            "all four filters",
            true,
            mapOf("hpf.freq" to 200.0, "bpf.freq" to 900.0, "bpf.q" to 0.5, "notch.freq" to 3000.0, "lpf.freq" to 5000.0, "lpf.q" to 1.5),
        ) {
            copy(
                filters = filters(
                    FilterDef.HighPass(200.0, 0.707), FilterDef.BandPass(900.0, 0.5),
                    FilterDef.Notch(3000.0, 0.707), FilterDef.LowPass(5000.0, 1.5),
                ),
            )
        },

        // ── the cutoff envelope, D3 ──
        Row("lpf env 24: D3, the law (linear against exp) and the sampling", false, mapOf("lpf.freq" to 600.0, "lpf.env" to 24.0)) {
            copy(filters = filters(FilterDef.LowPass(600.0, 0.707, envelope = FilterEnvDef(depth = 24.0))))
        },
        Row("lpf attack only (the fill itself is proven by the door-sweep row): D3", false, mapOf("lpf.freq" to 600.0, "lpf.attack" to 0.05)) {
            copy(filters = filters(FilterDef.LowPass(600.0, 0.707, envelope = FilterEnvDef(attack = 0.05))))
        },
        Row(
            "lpf pluck env 24 decay 0.2 sustain 0.1: D3",
            false,
            mapOf("lpf.freq" to 500.0, "lpf.env" to 24.0, "lpf.attack" to 0.002, "lpf.decay" to 0.2, "lpf.sustain" to 0.1, "lpf.release" to 0.1),
        ) {
            copy(filters = filters(FilterDef.LowPass(500.0, 0.707, envelope = FilterEnvDef(0.002, 0.2, 0.1, 0.1, 24.0))))
        },
        Row("lpf explicit env 0 with a decay: static on both", true, mapOf("lpf.freq" to 600.0, "lpf.env" to 0.0, "lpf.decay" to 0.3)) {
            copy(filters = filters(FilterDef.LowPass(600.0, 0.707, envelope = FilterEnvDef(decay = 0.3, depth = 0.0))))
        },
        Row("hpf env -12 decay 0.2 sustain 0.3: D3", false, mapOf("hpf.freq" to 800.0, "hpf.env" to -12.0, "hpf.decay" to 0.2, "hpf.sustain" to 0.3)) {
            copy(filters = filters(FilterDef.HighPass(800.0, 0.707, envelope = FilterEnvDef(decay = 0.2, sustain = 0.3, depth = -12.0))))
        },
        Row("bpf env 12: D3", false, mapOf("bpf.freq" to 700.0, "bpf.env" to 12.0)) {
            copy(filters = filters(FilterDef.BandPass(700.0, 0.707, envelope = FilterEnvDef(depth = 12.0))))
        },
        Row("notch env 12: D3", false, mapOf("notch.freq" to 700.0, "notch.env" to 12.0)) {
            copy(filters = filters(FilterDef.Notch(700.0, 0.707, envelope = FilterEnvDef(depth = 12.0))))
        },

        // ── analog: the humanize lane ──
        Row("analog 2, no filter: the oscillator drift alone", true, mapOf("analog" to 2.0)) { this },
        Row("analog 2, lpf 1200: one filter, the drift sampling (D3)", false, mapOf("analog" to 2.0, "lpf.freq" to 1200.0)) {
            copy(filters = filters(FilterDef.LowPass(1200.0, 0.707)))
        },
        Row("analog 2, hpf and lpf: the draw order (section 8) and the drift sampling (D3)", false, mapOf("analog" to 2.0, "hpf.freq" to 300.0, "lpf.freq" to 1200.0)) {
            copy(filters = filters(FilterDef.HighPass(300.0, 0.707), FilterDef.LowPass(1200.0, 0.707)))
        },

        // ── tremolo ──
        Row("tremolo depth 0.5 sync 4", true, mapOf("tremolo.depth" to 0.5, "tremolo.sync" to 4.0)) { copy(tremoloDepth = 0.5, tremoloSync = 4.0) },
        Row("tremolo depth only: rate 0, a static gain", true, mapOf("tremolo.depth" to 0.5)) { copy(tremoloDepth = 0.5) },
        Row(
            "tremolo square, skew 0.3, phase 0.25",
            true,
            mapOf(
                "tremolo.depth" to 1.0, "tremolo.sync" to 3.3, "tremolo.shape" to LfoShapes.indexOf("square"),
                "tremolo.skew" to 0.3, "tremolo.phase" to 0.25,
            ),
        ) { copy(tremoloDepth = 1.0, tremoloSync = 3.3, tremoloShape = "square", tremoloSkew = 0.3, tremoloPhase = 0.25) },

        // ── the envelope ──
        Row(
            "adsr 0.005 / 0.2 / 0.5 / 0.2, whole frame counts at 48 kHz",
            true,
            mapOf("adsr.attack" to 0.005, "adsr.decay" to 0.2, "adsr.sustain" to 0.5, "adsr.release" to 0.2),
            identical44 = false,
            cause44 = "0.005 s is 220.5 frames, the frame-count minor (Int against Double)",
        ) { copy(adsr = AdsrDef.Std(attack = 0.005, decay = 0.2, sustain = 0.5, release = 0.2)) },
        Row(
            "adsr at fractional frame counts: the frame-count minor (Int against Double)",
            false,
            mapOf("adsr.attack" to 0.00501, "adsr.decay" to 0.20001, "adsr.sustain" to 0.5, "adsr.release" to 0.20001),
        ) { copy(adsr = AdsrDef.Std(attack = 0.00501, decay = 0.20001, sustain = 0.5, release = 0.20001)) },
        Row(
            "adsr release 0.02: the factory's lifetime floor (the voice envelope's 0.05) keeps the de-click tail, step 6",
            false,
            mapOf("adsr.release" to 0.02),
        ) { copy(adsr = AdsrDef.Std(release = 0.02)) },
        Row(
            "adsrCurves linear, scurve, square",
            true,
            mapOf(
                "adsr.sustain" to 0.4,
                "adsrCurves.attack" to AdsrCurves.indexOf(AdsrCurve.Linear),
                "adsrCurves.decay" to AdsrCurves.indexOf(AdsrCurve.SCurve),
                "adsrCurves.release" to AdsrCurves.indexOf(AdsrCurve.Square),
            ),
        ) {
            copy(adsr = AdsrDef.Std(sustain = 0.4, attackCurve = AdsrCurve.Linear, decayCurve = AdsrCurve.SCurve, releaseCurve = AdsrCurve.Square))
        },
        Row("adsrOff: the strip's teardown fade, step 6", false, mapOf("adsr.on" to 0.0)) { copy(adsr = AdsrDef.Std(on = false)) },

        // ── in combination ──
        Row(
            "coarse, hpf, lpf, tremolo and the envelope together",
            true,
            mapOf(
                "coarse.amount" to 2.0, "hpf.freq" to 150.0, "lpf.freq" to 2500.0, "lpf.q" to 2.0,
                "tremolo.depth" to 0.4, "tremolo.sync" to 6.0, "adsr.attack" to 0.02, "adsr.sustain" to 0.6, "adsr.release" to 0.1,
            ),
        ) {
            copy(
                coarse = 2.0,
                filters = filters(FilterDef.HighPass(150.0, 0.707), FilterDef.LowPass(2500.0, 2.0)),
                tremoloDepth = 0.4, tremoloSync = 6.0,
                adsr = AdsrDef.Std(attack = 0.02, sustain = 0.6, release = 0.1),
            )
        },
        Row(
            "crush and distort in the chain: D1 and D2 (each cause pinned by its own single-stage rows)",
            false,
            mapOf("crush.amount" to 5.0, "distort.amount" to 0.4, "lpf.freq" to 3000.0),
        ) { copy(crush = 5.0, distort = 0.4, filters = filters(FilterDef.LowPass(3000.0, 0.707))) },
    )

    "the harness sees sound: the untouched voice is not silence" {
        for (rate in rates) {
            withClue("$rate Hz") { untouchedStrip.getValue(rate).maxOf { abs(it) } shouldBeGreaterThan 0.1 }
        }
    }

    for (rate in rates) {
        // The teardown window the adsrOff row differs in, for readers of the printed table.
        val teardownFrames = (VCA_OFF_TEARDOWN_FADE_SECONDS * rate).toInt()

        for (row in rows) {
            val identical = if (rate == 48000) row.identical else row.identical44
            val title = if (rate != 48000 && row.cause44 != null) "${row.title}; at $rate Hz: ${row.cause44}" else row.title

            "[$rate Hz] ${if (identical) "IDENTICAL" else "DIVERGENT"}: $title" {
                val analog = row.bag["analog"]
                val s = strip(row.strip, analog, rate)
                val c = classic(row.bag, rate)
                val mismatch = firstMismatch(s, c)

                println(
                    "CLASSIC-TABLE | $rate | ${row.title} | first mismatch $mismatch | max diff ${maxDiff(s, c)} | teardown window $teardownFrames",
                )

                if (row.bag.isNotEmpty()) {
                    withClue("engagement: the strip renders something other than the untouched voice") {
                        s.toList() shouldNotBe untouchedStrip.getValue(rate).toList()
                    }
                }

                if (identical) {
                    withClue("first mismatching frame") { mismatch shouldBe -1 }
                } else {
                    withClue("recorded as divergent, and it still is") { mismatch shouldNotBe -1 }
                    // ...and the CLASSIC side is not its unwritten tail. For a single-knob row that makes the
                    // stated cause true; a multi-knob row leans on its single-knob siblings for each cause.
                    withClue("engagement: classic() renders something other than its unwritten tail") {
                        c.toList() shouldNotBe untouchedClassic.getValue(rate).toList()
                    }
                }
            }
        }

        "[$rate Hz] the D3 fill row's classic side IS the door's sweep: `lpf.attack` alone renders `lowpass(600, attackSec = 0.05)`" {
            val c = classic(mapOf("lpf.freq" to 600.0, "lpf.attack" to 0.05), rate)
            val door = classic(emptyMap(), rate, sound = "doorfill")

            withClue("first mismatching frame against the door-built filter") { firstMismatch(door, c) shouldBe -1 }
            withClue("and that is not the static filter") {
                firstMismatch(classic(mapOf("lpf.freq" to 600.0), rate), c) shouldNotBe -1
            }
        }
    }
})
