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
import io.peekandpoke.klang.audio_bridge.mul
import io.peekandpoke.klang.audio_bridge.adsr
import io.peekandpoke.klang.audio_bridge.constants.ENV_DECLICK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.constants.VCA_OFF_TEARDOWN_FADE_SECONDS
import kotlin.math.abs
import kotlin.random.Random

/**
 * **The built-in `saw` on `classic()` against the voice strip, one voice per slot row: STEP 6'S TABLE**
 * (phase 3 steps 5 and 6, `docs/tasks/builtin-instruments.md` section 9). Every row renders the same note
 * three times through the real `VoiceFactory`,
 *
 *  - STRIP: the saw's SOURCE registered as an AUTHORED instrument (`stripsaw`, the strip still runs after
 *    it), the row's settings on the typed `VoiceData` fields sprudel writes today, through the `modern`
 *    pipeline (crush, coarse, distort, the filters, tremolo, the VCA): what `sound("saw")` was before step 6;
 *  - TYPED: the BUILT-IN `saw` (`source.pregain().onepole(slot).classic()`, `IgnitorRegistry.registerBuiltIn`,
 *    the strip off), the SAME typed fields,
 *    which reach its slots through the factory's translation (`classicSlotBag`): the path every song
 *    takes until step 8;
 *  - BAG: the built-in `saw` with the same settings written as SLOTS in the bag and no typed field: the
 *    path the doors take from step 8,
 *
 * and compares the left mix bus in raw bits. The onset is mid-block (frame 37) and the gate ends at a
 * quarter second, so the release and the voice's lifetime are inside the render. Every row runs at
 * 48 kHz AND at 44.1 kHz, because a stage time that is a whole number of frames at one rate is
 * fractional at the other (the envelope law counts attack and decay frames fractionally on both hosts).
 *
 * Every row carries a VERDICT. An IDENTICAL row is pinned bit for bit. A DIVERGENT row is pinned as
 * NOT identical and names, in its title, the decision that explains the difference, so the row turns
 * red the day that decision lands and the table has to be read again, not silently trusted. Every
 * row also checks that its settings are ENGAGED (the strip voice differs from the untouched voice), so
 * no row compares two renders of nothing. The measured maximum difference of each divergent row is
 * recorded in the step 5 report, not here, because a number pinned in a spec invites "tuning" the
 * law to meet it.
 *
 * What is not a slot row here: `onepole` and `pregain`, the two stages the REGISTRY places on a built-in's
 * source (`registerBuiltIn`), not `classic()`. The onepole reaches both sides the same way (the registry
 * wraps the authored `stripsaw` at note-on), and its placement has rows of its own below. The pregain does
 * NOT: the `stripsaw` oracle has no pregain slot and ignores one, while the built-in applies it, so the
 * `[pregain]` tests pin it against scaled oracle registrations (`stripsaw2x`, `stripsaw1p7x`).
 */
class ClassicStripParitySpec : StringSpec({

    val blockFrames = 128
    val blocks = 220
    val frames = blocks * blockFrames
    val gateSec = 0.25

    /**
     * What the D3 fill row is compared against: the DOOR's filter with the same one stage named (its
     * compound fill supplies the depth), in front of the envelope `classic()` builds when nothing else
     * is written, so the only thing that can differ is the filter.
     */
    val doorFilledLowpass: IgnitorDsl = IgnitorDsl.Sawtooth()
        .lowpass(IgnitorDsl.Constant(600.0), analog = IgnitorDsl.Slots.analog, attackSec = IgnitorDsl.Constant(0.05), humanize = true)
        .adsr(VOICE_ADSR_ATTACK_SEC, VOICE_ADSR_DECAY_SEC, VOICE_ADSR_SUSTAIN_LEVEL, VOICE_ADSR_RELEASE_SEC, declickSeconds = ENV_DECLICK_SECONDS)

    /**
     * What the default-curve row is compared against: the door's lowpass with `env = 24` and its three curves
     * NAMED (Exponential, and Linear for the anti-vacuous side), in front of `classic()`'s unwritten envelope.
     */
    fun namedCurveLowpass(curve: AdsrCurve): IgnitorDsl = IgnitorDsl.Sawtooth()
        .lowpass(
            IgnitorDsl.Constant(600.0), analog = IgnitorDsl.Slots.analog, env = IgnitorDsl.Constant(24.0),
            attackCurve = AdsrCurves.knob(curve), decayCurve = AdsrCurves.knob(curve), releaseCurve = AdsrCurves.knob(curve),
            humanize = true,
        )
        .adsr(VOICE_ADSR_ATTACK_SEC, VOICE_ADSR_DECAY_SEC, VOICE_ADSR_SUSTAIN_LEVEL, VOICE_ADSR_RELEASE_SEC, declickSeconds = ENV_DECLICK_SECONDS)

    /** The named curves the curve rows use: every stage its own, so a swapped stage shows. */
    // The release is InvSquare, about 4.2 st from the default Exponential on this envelope (Square is within
    // about 0.85 st of it, too close to tell a release that silently kept the default).
    val namedCurves = Triple(AdsrCurve.Linear, AdsrCurve.SCurve, AdsrCurve.InvSquare)

    /** The four filters of the curve rows: the door, its cutoff, and the strip definition with [namedCurves] or none. */
    val curveFilters = listOf("lpf" to 600.0, "hpf" to 900.0, "bpf" to 800.0, "notch" to 800.0)

    /** The curve rows' envelope on the strip: every stage curved and inside the render (the gate at 0.25 s). */
    fun curveEnv(named: Boolean): FilterEnvDef = FilterEnvDef(
        attack = 0.01, decay = 0.15, sustain = 0.3, release = 0.1, depth = 24.0,
        attackCurve = if (named) namedCurves.first else null,
        decayCurve = if (named) namedCurves.second else null,
        releaseCurve = if (named) namedCurves.third else null,
    )

    fun curveFilterDef(door: String, freq: Double, named: Boolean): FilterDef = when (door) {
        "lpf" -> FilterDef.LowPass(freq, 0.707, envelope = curveEnv(named))
        "hpf" -> FilterDef.HighPass(freq, 0.707, envelope = curveEnv(named))
        "bpf" -> FilterDef.BandPass(freq, 0.707, envelope = curveEnv(named))
        else -> FilterDef.Notch(freq, 0.707, envelope = curveEnv(named))
    }

    /**
     * The strip-host oracle of the curve rows: the Ignitor filter NODE, built by hand with the same stages and its
     * three curves named as the ENUM LITERALS of [namedCurves], in front of `classic()`'s unwritten envelope. A named
     * curve that never reaches the strip (or reaches the wrong stage) renders the strip unlike this node.
     */
    fun namedCurveNode(door: String, freq: Double): IgnitorDsl {
        val c = { v: Double -> IgnitorDsl.Constant(v) }
        val a = AdsrCurves.knob(namedCurves.first)
        val d = AdsrCurves.knob(namedCurves.second)
        val r = AdsrCurves.knob(namedCurves.third)
        val saw = IgnitorDsl.Sawtooth()
        val an = IgnitorDsl.Slots.analog
        val filter: IgnitorDsl = when (door) {
            "lpf" -> IgnitorDsl.Lowpass(
                saw, c(freq), c(0.707), an, env = c(24.0), attackSec = c(0.01), decaySec = c(0.15), sustainLevel = c(0.3),
                releaseSec = c(0.1), attackCurve = a, decayCurve = d, releaseCurve = r, humanize = true,
            )
            "hpf" -> IgnitorDsl.Highpass(
                saw, c(freq), c(0.707), an, env = c(24.0), attackSec = c(0.01), decaySec = c(0.15), sustainLevel = c(0.3),
                releaseSec = c(0.1), attackCurve = a, decayCurve = d, releaseCurve = r, humanize = true,
            )
            "bpf" -> IgnitorDsl.Bandpass(
                saw, c(freq), c(0.707), an, env = c(24.0), attackSec = c(0.01), decaySec = c(0.15), sustainLevel = c(0.3),
                releaseSec = c(0.1), attackCurve = a, decayCurve = d, releaseCurve = r, humanize = true,
            )
            else -> IgnitorDsl.Notch(
                saw, c(freq), c(0.707), an, env = c(24.0), attackSec = c(0.01), decaySec = c(0.15), sustainLevel = c(0.3),
                releaseSec = c(0.1), attackCurve = a, decayCurve = d, releaseCurve = r, humanize = true,
            )
        }

        return filter.adsr(VOICE_ADSR_ATTACK_SEC, VOICE_ADSR_DECAY_SEC, VOICE_ADSR_SUSTAIN_LEVEL, VOICE_ADSR_RELEASE_SEC, declickSeconds = ENV_DECLICK_SECONDS)
    }

    fun render(data: VoiceData, sampleRate: Int): DoubleArray {
        val onsetSec = 37.0 / sampleRate
        val registry = IgnitorRegistry().apply {
            registerDefaults()
            // The built-in saw's own source, AUTHORED: the voice strip runs after it, as it did after
            // every built-in before step 6.
            register("stripsaw", builtInSources().getValue("saw"))
            // The pregain oracle, written here: the same source played exactly twice as hard, AUTHORED, so the
            // voice strip (and the registry's onepole around it) runs after the doubled source.
            register("stripsaw2x", builtInSources().getValue("saw").mul(IgnitorDsl.Constant(2.0)))
            register("stripsaw1p7x", builtInSources().getValue("saw").mul(IgnitorDsl.Constant(1.7)))
            // The other order, for the 1.7 row's anti-vacuous side: the source, the onepole, THEN the gain
            // (the registry adds no second onepole, because the bag this voice carries writes none).
            register(
                "onepolethen1p7x",
                IgnitorDsl.OnePoleLowpass(builtInSources().getValue("saw"), IgnitorDsl.Constant(900.0)).mul(IgnitorDsl.Constant(1.7)),
            )
            register("doorfill", doorFilledLowpass)
            register("expfilter", namedCurveLowpass(AdsrCurve.Exponential))
            register("linfilter", namedCurveLowpass(AdsrCurve.Linear))

            for ((door, freq) in curveFilters) {
                register("curved$door", namedCurveNode(door, freq))
            }
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

    /** The bag keys that are not `classic()` slots: the voice's own, which every column carries. */
    val ownKeys = setOf("analog", "onepole")

    fun own(bag: Map<String, Double>): Map<String, Double>? = bag.filterKeys { it in ownKeys }.takeIf { it.isNotEmpty() }

    fun strip(
        settings: VoiceData.() -> VoiceData,
        own: Map<String, Double>?,
        sampleRate: Int,
        voice: VoiceData.() -> VoiceData = { this },
    ): DoubleArray = render(base.copy(sound = "stripsaw", oscParams = own).settings().voice(), sampleRate)

    fun typed(
        settings: VoiceData.() -> VoiceData,
        own: Map<String, Double>?,
        sampleRate: Int,
        voice: VoiceData.() -> VoiceData = { this },
    ): DoubleArray = render(base.copy(sound = "saw", oscParams = own).settings().voice(), sampleRate)

    /** The built-in `saw` (or an authored helper [sound] on the EMPTY pipeline) with [bag] as its slots. */
    fun classic(
        bag: Map<String, Double>,
        sampleRate: Int,
        sound: String = "saw",
        voice: VoiceData.() -> VoiceData = { this },
    ): DoubleArray = render(base.copy(sound = sound, pipeline = "bare", oscParams = bag).voice(), sampleRate)

    fun maxDiff(a: DoubleArray, b: DoubleArray): Double = a.indices.maxOf { abs(a[it] - b[it]) }

    fun firstMismatch(a: DoubleArray, b: DoubleArray): Int = a.indices.firstOrNull { a[it].toRawBits() != b[it].toRawBits() } ?: -1

    val rates = listOf(48000, 44100)

    val untouchedStrip: Map<Int, DoubleArray> by lazy { rates.associateWith { strip({ this }, null, it) } }
    val untouchedClassic: Map<Int, DoubleArray> by lazy { rates.associateWith { classic(emptyMap(), it) } }

    /**
     * A row: its bag, the same settings on the strip's typed fields, the voice-level fields every column
     * carries ([voice]: the pitch pipeline, which is not a `classic()` stage), and whether the classic
     * columns are identical to the strip at both rates.
     */
    class Row(
        val title: String,
        val identical: Boolean,
        val bag: Map<String, Double>,
        val voice: VoiceData.() -> VoiceData = { this },
        val strip: VoiceData.() -> VoiceData,
    )

    fun filters(vararg defs: FilterDef): FilterDefs = FilterDefs(defs.toList())

    val rows = listOf(
        Row("untouched: the envelope alone, at the voice envelope's defaults", true, emptyMap()) { this },

        // ── crush: one law since step 4 (D1, floor, CrushCore) ──
        Row("crush 4", true, mapOf("crush.amount" to 4.0)) { copy(crush = 4.0) },
        Row("crush 8", true, mapOf("crush.amount" to 8.0)) { copy(crush = 8.0) },
        Row("crush 1 (the quantizer runs)", true, mapOf("crush.amount" to 1.0)) { copy(crush = 1.0) },

        // ── coarse ──
        Row("coarse 3", true, mapOf("coarse.amount" to 3.0)) { copy(coarse = 3.0) },
        Row("coarse 7.5", true, mapOf("coarse.amount" to 7.5)) { copy(coarse = 7.5) },

        // ── distort: one law since step 4 (D2 option A, DistortionCore) ──
        *listOf(
            Triple(0.3, "soft", 0), Triple(0.5, "soft", 0), Triple(1.0, "soft", 0), Triple(2.0, "soft", 0),
            Triple(0.5, "tube", 0), Triple(0.5, "gentle", 0), Triple(1.0, "hard", 0), Triple(1.0, "fold", 0),
            Triple(0.5, "rectify", 0), Triple(0.5, "soft", 2), Triple(0.5, "tube", 4),
        ).map { (amount, shape, os) ->
            Row(
                "distort $amount $shape x$os",
                true,
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
        Row("lpf env 24: one default curve since D3 (b)", true, mapOf("lpf.freq" to 600.0, "lpf.env" to 24.0)) {
            copy(filters = filters(FilterDef.LowPass(600.0, 0.707, envelope = FilterEnvDef(depth = 24.0))))
        },
        Row("lpf attack only (the fill itself is proven by the door-sweep row)", true, mapOf("lpf.freq" to 600.0, "lpf.attack" to 0.05)) {
            copy(filters = filters(FilterDef.LowPass(600.0, 0.707, envelope = FilterEnvDef(attack = 0.05))))
        },
        Row(
            "lpf pluck env 24 decay 0.2 sustain 0.1",
            true,
            mapOf("lpf.freq" to 500.0, "lpf.env" to 24.0, "lpf.attack" to 0.002, "lpf.decay" to 0.2, "lpf.sustain" to 0.1, "lpf.release" to 0.1),
        ) {
            copy(filters = filters(FilterDef.LowPass(500.0, 0.707, envelope = FilterEnvDef(0.002, 0.2, 0.1, 0.1, 24.0))))
        },
        Row("lpf explicit env 0 with a decay: static on both", true, mapOf("lpf.freq" to 600.0, "lpf.env" to 0.0, "lpf.decay" to 0.3)) {
            copy(filters = filters(FilterDef.LowPass(600.0, 0.707, envelope = FilterEnvDef(decay = 0.3, depth = 0.0))))
        },
        Row("hpf env -12 decay 0.2 sustain 0.3", true, mapOf("hpf.freq" to 800.0, "hpf.env" to -12.0, "hpf.decay" to 0.2, "hpf.sustain" to 0.3)) {
            copy(filters = filters(FilterDef.HighPass(800.0, 0.707, envelope = FilterEnvDef(decay = 0.2, sustain = 0.3, depth = -12.0))))
        },
        Row("bpf env 12", true, mapOf("bpf.freq" to 700.0, "bpf.env" to 12.0)) {
            copy(filters = filters(FilterDef.BandPass(700.0, 0.707, envelope = FilterEnvDef(depth = 12.0))))
        },
        Row("notch env 12", true, mapOf("notch.freq" to 700.0, "notch.env" to 12.0)) {
            copy(filters = filters(FilterDef.Notch(700.0, 0.707, envelope = FilterEnvDef(depth = 12.0))))
        },
        // ── the named filter curves, step 5b (c2): `<door>Curves` reaches classic() exactly as it reaches the strip ──
        *curveFilters.map { (f, freq) ->
            Row(
                "${f}Curves linear, scurve, invsquare (env 24, every stage curved)",
                true,
                mapOf(
                    "$f.freq" to freq, "$f.env" to 24.0, "$f.attack" to 0.01, "$f.decay" to 0.15, "$f.sustain" to 0.3,
                    "$f.release" to 0.1,
                    "${f}Curves.attack" to AdsrCurves.indexOf(namedCurves.first),
                    "${f}Curves.decay" to AdsrCurves.indexOf(namedCurves.second),
                    "${f}Curves.release" to AdsrCurves.indexOf(namedCurves.third),
                ),
            ) {
                copy(filters = filters(curveFilterDef(f, freq, named = true)))
            }
        }.toTypedArray(),
        // A STEP envelope (attack 0, decay 0, release 0) has no curved stage, so the default-curve half of
        // D3 cannot reach it: these rows see the sampling alone (the sweep at the gate block) and the
        // sweep's step placement in each strip loop, the saturated ones with the drift lane.
        *listOf("lpf" to 600.0, "hpf" to 900.0, "bpf" to 800.0, "notch" to 800.0).map { (f, freq) ->
            Row(
                "$f env 24, a step envelope (attack 0, decay 0, sustain 0.4, release 0): the sampling alone",
                true,
                mapOf("$f.freq" to freq, "$f.env" to 24.0, "$f.attack" to 0.0, "$f.decay" to 0.0, "$f.sustain" to 0.4, "$f.release" to 0.0),
            ) {
                val env = FilterEnvDef(attack = 0.0, decay = 0.0, sustain = 0.4, release = 0.0, depth = 24.0)
                val def = when (f) {
                    "lpf" -> FilterDef.LowPass(freq, 0.707, envelope = env)
                    "hpf" -> FilterDef.HighPass(freq, 0.707, envelope = env)
                    "bpf" -> FilterDef.BandPass(freq, 0.707, envelope = env)
                    else -> FilterDef.Notch(freq, 0.707, envelope = env)
                }

                copy(filters = filters(def))
            }
        }.toTypedArray(),
        Row(
            "lpf env 24 q 3 passes 3, a step envelope: the sweep forwarded to every stage of the cascade",
            true,
            mapOf(
                "lpf.freq" to 600.0, "lpf.q" to 3.0, "lpf.passes" to 3.0, "lpf.env" to 24.0,
                "lpf.attack" to 0.0, "lpf.decay" to 0.0, "lpf.sustain" to 0.4, "lpf.release" to 0.0,
            ),
        ) {
            val env = FilterEnvDef(attack = 0.0, decay = 0.0, sustain = 0.4, release = 0.0, depth = 24.0)

            copy(filters = filters(FilterDef.LowPass(600.0, 3.0, envelope = env, passes = 3)))
        },
        *listOf("lpf" to 600.0, "hpf" to 900.0).map { (f, freq) ->
            Row(
                "analog 2, $f env 24 q 3, a step envelope: the sweep through the saturated branch with the drift",
                true,
                mapOf(
                    "analog" to 2.0, "$f.freq" to freq, "$f.q" to 3.0, "$f.env" to 24.0,
                    "$f.attack" to 0.0, "$f.decay" to 0.0, "$f.sustain" to 0.4, "$f.release" to 0.0,
                ),
            ) {
                val env = FilterEnvDef(attack = 0.0, decay = 0.0, sustain = 0.4, release = 0.0, depth = 24.0)
                val def = if (f == "lpf") FilterDef.LowPass(freq, 3.0, envelope = env) else FilterDef.HighPass(freq, 3.0, envelope = env)

                copy(filters = filters(def))
            }
        }.toTypedArray(),

        // ── analog: the humanize lane ──
        Row("analog 2, no filter: the oscillator drift alone", true, mapOf("analog" to 2.0)) { this },
        Row("analog 2, lpf 1200: one filter, the drift lane (one sampling since D3 a2)", true, mapOf("analog" to 2.0, "lpf.freq" to 1200.0)) {
            copy(filters = filters(FilterDef.LowPass(1200.0, 0.707)))
        },
        Row("analog 2, hpf and lpf: the draw order (section 8)", false, mapOf("analog" to 2.0, "hpf.freq" to 300.0, "lpf.freq" to 1200.0)) {
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
            "adsr 0.005 / 0.2 / 0.5 / 0.2 (whole frame counts at 48 kHz, 220.5 attack frames at 44.1 kHz)",
            true,
            mapOf("adsr.attack" to 0.005, "adsr.decay" to 0.2, "adsr.sustain" to 0.5, "adsr.release" to 0.2),
        ) { copy(adsr = AdsrDef.Std(attack = 0.005, decay = 0.2, sustain = 0.5, release = 0.2)) },
        Row(
            "adsr at fractional frame counts (one envelope law: fractional attack and decay on both hosts)",
            true,
            mapOf("adsr.attack" to 0.00501, "adsr.decay" to 0.20001, "adsr.sustain" to 0.5, "adsr.release" to 0.20001),
        ) { copy(adsr = AdsrDef.Std(attack = 0.00501, decay = 0.20001, sustain = 0.5, release = 0.20001)) },
        Row(
            "adsr release 0.02: a built-in lives as long as its tree says, no floor from the voice envelope's 0.05 (step 6)",
            true,
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
        Row(
            "adsr release -0.1: a zero-length release stage, and the voice still lives to its gate (the lifetime floored at 0)",
            true,
            mapOf("adsr.release" to -0.1),
        ) { copy(adsr = AdsrDef.Std(release = -0.1)) },
        Row("adsrOff: the teardown fade, one law on both hosts (step 6)", true, mapOf("adsr.on" to 0.0)) { copy(adsr = AdsrDef.Std(on = false)) },
        Row("adsrOff with release 0.2: the fade over a longer tail", true, mapOf("adsr.on" to 0.0, "adsr.release" to 0.2)) {
            copy(adsr = AdsrDef.Std(on = false, release = 0.2))
        },

        // ── the registry's onepole: on the built-in's SOURCE, in front of every stage (step 6) ──
        Row("onepole 900 with crush 5: in front of the quantizer", true, mapOf("onepole" to 900.0, "crush.amount" to 5.0)) {
            copy(crush = 5.0)
        },
        Row("onepole 900 with distort 0.8: in front of the shaper", true, mapOf("onepole" to 900.0, "distort.amount" to 0.8)) {
            copy(distort = 0.8)
        },

        // ── names the catalogues do not know: both hosts fall back through the same `indexOf` ──
        Row(
            "unknown tremolo and distort shape names",
            true,
            mapOf(
                "tremolo.depth" to 0.6, "tremolo.sync" to 3.0, "tremolo.shape" to LfoShapes.indexOf("wobble"),
                "distort.amount" to 0.5, "distort.shape" to DistortionShapes.indexOf("nope"),
            ),
        ) { copy(tremoloDepth = 0.6, tremoloSync = 3.0, tremoloShape = "wobble", distort = 0.5, distortShape = "nope") },

        // ── the voice's pitch pipeline under a filtered built-in: it stays on the voice ──
        Row(
            "vibrato and FM from the voice's pitch pipeline, under lpf env and hpf",
            true,
            mapOf("lpf.freq" to 900.0, "lpf.env" to 12.0, "hpf.freq" to 120.0),
            voice = { copy(vibrato = 5.0, vibratoMod = 0.4, fmh = 2.0, fmEnv = 150.0) },
        ) {
            copy(filters = filters(FilterDef.HighPass(120.0, 0.707), FilterDef.LowPass(900.0, 0.707, envelope = FilterEnvDef(depth = 12.0))))
        },

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
            "crush and distort in the chain",
            true,
            mapOf("crush.amount" to 5.0, "distort.amount" to 0.4, "lpf.freq" to 3000.0),
        ) { copy(crush = 5.0, distort = 0.4, filters = filters(FilterDef.LowPass(3000.0, 0.707))) },
    )

    "[crush oversample] DIVERGENT: classic()'s crush has no oversampler (docs/tasks/oversampling-regions.md)" {
        for (rate in rates) {
            val plain = strip({ copy(crush = 4.0) }, null, rate)

            withClue("$rate Hz: engaged, the oversampled strip crush is not the plain one") {
                firstMismatch(strip({ copy(crush = 4.0, crushOversample = 2) }, null, rate), plain) shouldNotBe -1
            }
            withClue("$rate Hz: the built-in renders the plain crush") {
                firstMismatch(typed({ copy(crush = 4.0, crushOversample = 2) }, null, rate), plain) shouldBe -1
            }
        }
    }

    // ── pregain: placed on every built-in's source (phase 3 step 6, commit 2) ──

    "[pregain] a built-in's pregain 2 doubles every sample exactly: nothing nonlinear is written" {
        for (rate in rates) {
            val unity = classic(emptyMap(), rate)
            val doubled = classic(mapOf("pregain" to 2.0), rate)

            withClue("$rate Hz: engaged, the unity voice sounds") { unity.any { it != 0.0 } shouldBe true }
            withClue("$rate Hz: first frame that is not exactly twice the unity voice") {
                (unity.indices.firstOrNull { doubled[it].toRawBits() != (2.0 * unity[it]).toRawBits() } ?: -1) shouldBe -1
            }
        }
    }

    "[pregain] a built-in's pregain sits IN FRONT of its nonlinear stages: distort and crush see the doubled source" {
        for (rate in rates) {
            for ((label, settings) in listOf<Pair<String, VoiceData.() -> VoiceData>>(
                "distort 0.5" to { copy(distort = 0.5) },
                "crush 5 with onepole 900" to { copy(crush = 5.0, oscParams = mapOf("onepole" to 900.0)) },
            )) {
                val builtIn = render(base.copy(sound = "saw").settings().let { it.copy(oscParams = (it.oscParams ?: emptyMap()) + ("pregain" to 2.0)) }, rate)
                val oracle = render(base.copy(sound = "stripsaw2x").settings(), rate)
                val afterTheStage = typed(settings, null, rate).map { 2.0 * it }

                withClue("$rate Hz, $label: first mismatch against the doubled source through the strip") {
                    firstMismatch(oracle, builtIn) shouldBe -1
                }
                withClue("$rate Hz, $label: anti-vacuous, doubling AFTER the stage is a different signal") {
                    firstMismatch(afterTheStage.toDoubleArray(), builtIn) shouldNotBe -1
                }
            }
        }
    }

    "[pregain] ...and in front of the registry's onepole: the source, then pregain, then onepole (a 1.7 gain, so the order shows in the bits)" {
        // Scaling by 2 commutes with the linear one-pole bit for bit; a gain of 1.7 rounds differently on
        // either side of it, so this row is what tells `source.pregain().onepole()` from `source.onepole().pregain()`.
        for (rate in rates) {
            val bag = mapOf("onepole" to 900.0, "pregain" to 1.7)
            val builtIn = classic(bag, rate)
            val oracle = render(base.copy(sound = "stripsaw1p7x", oscParams = mapOf("onepole" to 900.0)), rate)

            withClue("$rate Hz: first mismatch against the 1.7x source through the registry's onepole") {
                firstMismatch(oracle, builtIn) shouldBe -1
            }
            withClue("$rate Hz: anti-vacuous, the onepole-then-gain order is a different signal") {
                firstMismatch(render(base.copy(sound = "onepolethen1p7x"), rate), builtIn) shouldNotBe -1
            }
        }
    }

    "the harness sees sound: the untouched voice is not silence" {
        for (rate in rates) {
            withClue("$rate Hz") { untouchedStrip.getValue(rate).maxOf { abs(it) } shouldBeGreaterThan 0.1 }
        }
    }

    for (rate in rates) {
        // The teardown window the adsrOff row differs in, for readers of the printed table.
        val teardownFrames = (VCA_OFF_TEARDOWN_FADE_SECONDS * rate).toInt()

        for (row in rows) {
            val identical = row.identical

            "[$rate Hz] ${if (identical) "IDENTICAL" else "DIVERGENT"}: ${row.title}" {
                val own = own(row.bag)
                val s = strip(row.strip, own, rate, row.voice)
                val columns = listOf(
                    "TYPED" to typed(row.strip, own, rate, row.voice),
                    "BAG" to classic(row.bag, rate, voice = row.voice),
                )

                if (row.bag.isNotEmpty()) {
                    withClue("engagement: the strip renders something other than the untouched voice") {
                        s.toList() shouldNotBe untouchedStrip.getValue(rate).toList()
                    }
                }

                for ((column, c) in columns) {
                    val mismatch = firstMismatch(s, c)

                    println(
                        "CLASSIC-TABLE | $rate | $column | ${row.title} | first mismatch $mismatch | max diff ${maxDiff(s, c)} | teardown window $teardownFrames",
                    )

                    if (identical) {
                        withClue("$column: first mismatching frame") { mismatch shouldBe -1 }
                    } else {
                        withClue("$column: recorded as divergent, and it still is") { mismatch shouldNotBe -1 }
                        // ...and the CLASSIC side is not its unwritten tail. For a single-knob row that makes the
                        // stated cause true; a multi-knob row leans on its single-knob siblings for each cause.
                        withClue("$column engagement: classic() renders something other than its unwritten tail") {
                            c.toList() shouldNotBe untouchedClassic.getValue(rate).toList()
                        }
                    }
                }
            }
        }

        "[$rate Hz] the strip's unwritten filter curve IS Exponential (D3 b): `lpf env 24` renders the door's lowpass with its curves named Exponential" {
            // The rows above prove the strip and `classic()` AGREE; this one pins WHAT they agree on, against
            // the curve named as an enum literal, so a default moved back to linear on both hosts is red here.
            val s = strip({ copy(filters = filters(FilterDef.LowPass(600.0, 0.707, envelope = FilterEnvDef(depth = 24.0)))) }, null, rate)

            withClue("first mismatching frame against the Exponential-named lowpass") {
                firstMismatch(classic(emptyMap(), rate, sound = "expfilter"), s) shouldBe -1
            }
            withClue("anti-vacuous: the Linear-named lowpass is not the strip") {
                firstMismatch(classic(emptyMap(), rate, sound = "linfilter"), s) shouldNotBe -1
            }
        }

        for ((door, freq) in curveFilters) {
            "[$rate Hz] the strip's named $door curves ARE the node's curves named as enum literals, and they move the sweep" {
                val named = strip({ copy(filters = filters(curveFilterDef(door, freq, named = true))) }, null, rate)
                val unnamed = strip({ copy(filters = filters(curveFilterDef(door, freq, named = false))) }, null, rate)

                withClue("first mismatching frame against the node with Linear, SCurve, InvSquare named") {
                    firstMismatch(classic(emptyMap(), rate, sound = "curved$door"), named) shouldBe -1
                }
                withClue("anti-vacuous: the named curves change the strip's sweep") {
                    firstMismatch(unnamed, named) shouldNotBe -1
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
