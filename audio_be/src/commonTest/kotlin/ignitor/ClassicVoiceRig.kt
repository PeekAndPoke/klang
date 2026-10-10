/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.SampleStore
import io.peekandpoke.klang.audio_be.cylinders.offerAndCommit
import io.peekandpoke.klang.audio_be.voices.PlaybackCtx
import io.peekandpoke.klang.audio_be.voices.VoiceFactory
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.LfoShapes
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.adsr
import io.peekandpoke.klang.audio_bridge.classic
import io.peekandpoke.klang.audio_bridge.constants.ENV_DECLICK_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_ATTACK_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_DECAY_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_SUSTAIN_LEVEL
import io.peekandpoke.klang.audio_bridge.mul
import kotlin.random.Random

/** One row of the `classic()` voice table: its title and its slots. */
class ClassicRow(
    val title: String,
    val bag: Map<String, Double>,
)

/**
 * TEST ONLY. The built-in `saw` on `classic()`, one voice per slot row, through the real `VoiceFactory`: the rig of
 * `ClassicVoiceContractSpec` (both platforms, the hand-built oracles). The render: the onset mid-block (frame 37), the gate a quarter second, so the
 * release and the voice's lifetime are inside the render; the left mix bus of its orbit.
 */
object ClassicVoiceRig {

    val blockFrames = 128
    val blocks = 220
    val frames = blocks * blockFrames
    val gateSec = 0.25

    /** The named curves the curve rows use: every stage its own, so a swapped stage shows. */
    val namedCurves = Triple(AdsrCurve.Linear, AdsrCurve.SCurve, AdsrCurve.InvSquare)

    /** The four filters of the curve rows: the door and its cutoff. */
    val curveFilters = listOf("lpf" to 600.0, "hpf" to 900.0, "bpf" to 800.0, "notch" to 800.0)

    /**
     * The curve rows' oracle: the Ignitor filter NODE, built by hand with the curve rows' stages and its three curves
     * named as the ENUM LITERALS of [namedCurves], in front of `classic()`'s unwritten envelope.
     */
    fun namedCurveNode(door: String, freq: Double): IgnitorDsl {
        val c = { v: Double -> IgnitorDsl.Constant(v) }
        val a = AdsrCurves.knob(namedCurves.first)
        val d = AdsrCurves.knob(namedCurves.second)
        val r = AdsrCurves.knob(namedCurves.third)
        val saw = IgnitorDsl.Saw()
        val an = IgnitorDsl.Slots.analog
        val filter: IgnitorDsl = when (door) {
            "lpf" -> IgnitorDsl.Lowpass(
                inner = saw, freq = c(freq), q = c(0.707), analog = an, env = c(24.0), attack = c(0.01), decay = c(0.15), sustain = c(0.3),
                release = c(0.1), attackCurve = a, decayCurve = d, releaseCurve = r, humanize = true,
            )
            "hpf" -> IgnitorDsl.Highpass(
                inner = saw, freq = c(freq), q = c(0.707), analog = an, env = c(24.0), attack = c(0.01), decay = c(0.15), sustain = c(0.3),
                release = c(0.1), attackCurve = a, decayCurve = d, releaseCurve = r, humanize = true,
            )
            "bpf" -> IgnitorDsl.Bandpass(
                inner = saw, freq = c(freq), q = c(0.707), analog = an, env = c(24.0), attack = c(0.01), decay = c(0.15), sustain = c(0.3),
                release = c(0.1), attackCurve = a, decayCurve = d, releaseCurve = r, humanize = true,
            )
            else -> IgnitorDsl.Notch(
                inner = saw, freq = c(freq), q = c(0.707), analog = an, env = c(24.0), attack = c(0.01), decay = c(0.15), sustain = c(0.3),
                release = c(0.1), attackCurve = a, decayCurve = d, releaseCurve = r, humanize = true,
            )
        }

        return filter.adsr(attack = VOICE_ADSR_ATTACK_SEC, decay = VOICE_ADSR_DECAY_SEC, sustain = VOICE_ADSR_SUSTAIN_LEVEL, release = VOICE_ADSR_RELEASE_SEC, declick = ENV_DECLICK_SECONDS)
    }

    fun render(data: VoiceData, sampleRate: Int): DoubleArray {
        val onsetSec = 37.0 / sampleRate
        val source = builtInSources().getValue("saw")
        val registry = IgnitorRegistry().apply {
            registerDefaults()
            // The pregain oracles, written here: the same source played exactly 2 or 1.7 times as hard, then `classic()`.
            register("saw2x", source.mul(IgnitorDsl.Constant(2.0)).classic())
            register("saw1p7x", source.mul(IgnitorDsl.Constant(1.7)).classic())
            // The other order, for the 1.7 row's anti-vacuous side: the source, a onepole, THEN the gain.
            register("onepolethen1p7x", IgnitorDsl.OnePoleLowpass(inner = source, freq = IgnitorDsl.Constant(900.0)).mul(IgnitorDsl.Constant(1.7)).classic())

            for ((door, freq) in curveFilters) {
                register("curved$door", namedCurveNode(door, freq))
            }
        }
        val factory = VoiceFactory(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = DoubleArray(blockFrames),
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

            val cylinder = ctx.cylinders.offerAndCommit(voice.cylinderId, voice, 0.0)

            cylinder.mixBuffer.left.copyInto(destination = out, destinationOffset = block * blockFrames, startIndex = 0, endIndex = blockFrames)
            cylinder.mixBuffer.left.fill(0.0)
            cylinder.mixBuffer.right.fill(0.0)
        }

        return out
    }

    val base = VoiceData.empty.copy(freqHz = 220.0)

    /** The built-in `saw` (or a helper [sound] registered above) with [bag] as its slots. */
    fun classic(
        bag: Map<String, Double>,
        sampleRate: Int,
        sound: String = "saw",
    ): DoubleArray = render(base.copy(sound = sound, ignitorParams = bag.takeIf { it.isNotEmpty() }), sampleRate)

    fun firstMismatch(a: DoubleArray, b: DoubleArray): Int = a.indices.firstOrNull { a[it].toRawBits() != b[it].toRawBits() } ?: -1

    val rates = listOf(48000, 44100)

    /**
     * The baseline's rows, trimmed by the test consolidation (2026-09-28) to one row per stage and mode: every stage
     * kind, the filter envelope's shapes on `lpf` and `hpf`, the analog lane and its draw order, the envelope and
     * lifetime rows (the 44.1 kHz fractional frame counts among them), and the combinations. Each stage LAW is its
     * own oracle spec's: since test consolidation commit 7 (2026-09-28) that includes coarse (`CoarseLawSpec`, the
     * fractional amount among them), the waveshaper curves (`DoorDistortionLawSpec`, every shape) and the SVF node's
     * per-sample cutoff sweep in each of its six loops (`SvfNodeLawSpec`, against a swept oracle; the saturated loops'
     * diode curve at literal values and their state scale and drive as literals in the oracle). The rows
     * `coarse 7.5`, one per distort shape and one swept row per SVF loop stay until the phase 3 end checkpoint retires
     * or regenerates the baseline. These rows pin the exact bits of the whole voice.
     */
    val rows = listOf(
        ClassicRow("untouched: the envelope alone, at the voice envelope's defaults", emptyMap()),

        // ── crush (D1, floor, CrushCore) ──
        ClassicRow("crush 4", mapOf("crush.bits" to 4.0)),

        // ── coarse ──
        ClassicRow("coarse 3", mapOf("coarse.factor" to 3.0)),
        ClassicRow("coarse 7.5", mapOf("coarse.factor" to 7.5)),

        // ── distort (D2 option A, DistortionCore) ──
        *listOf(
            Triple(0.5, "soft", 0), Triple(0.5, "gentle", 0), Triple(1.0, "hard", 0), Triple(1.0, "fold", 0),
            Triple(0.5, "rectify", 0), Triple(0.5, "tube", 4),
        ).map { (amount, shape, os) ->
            ClassicRow(
                "distort $amount $shape x$os",
                mapOf("distort.amount" to amount, "distort.shape" to DistortionShapes.indexOf(shape), "distort.oversample" to os.toDouble()),
            )
        }.toTypedArray(),

        // ── the four filters, static ──
        ClassicRow("hpf 400 q 3 passes 2", mapOf("hpf.freq" to 400.0, "hpf.q" to 3.0, "hpf.passes" to 2.0)),
        ClassicRow("bpf 1000 q 2", mapOf("bpf.freq" to 1000.0, "bpf.q" to 2.0)),
        ClassicRow("notch 1000 q 4", mapOf("notch.freq" to 1000.0, "notch.q" to 4.0)),
        ClassicRow("lpf 1200 q 3 passes 3", mapOf("lpf.freq" to 1200.0, "lpf.q" to 3.0, "lpf.passes" to 3.0)),

        // ── the cutoff envelope, D3 ──
        ClassicRow("lpf attack only: the slot-layer fill (its law is FilterSlotLayerFillSpec's)", mapOf("lpf.freq" to 600.0, "lpf.attack" to 0.05)),
        ClassicRow(
            "lpf pluck env 24 decay 0.2 sustain 0.1",
            mapOf("lpf.freq" to 500.0, "lpf.env" to 24.0, "lpf.attack" to 0.002, "lpf.decay" to 0.2, "lpf.sustain" to 0.1, "lpf.release" to 0.1),
        ),
        ClassicRow("hpf env -12 decay 0.2 sustain 0.3", mapOf("hpf.freq" to 800.0, "hpf.env" to -12.0, "hpf.decay" to 0.2, "hpf.sustain" to 0.3)),
        ClassicRow("bpf env 12", mapOf("bpf.freq" to 700.0, "bpf.env" to 12.0)),
        ClassicRow("notch env 12", mapOf("notch.freq" to 700.0, "notch.env" to 12.0)),
        ClassicRow(
            "lpfCurves linear, scurve, invsquare (env 24, every stage curved)",
            mapOf(
                "lpf.freq" to 600.0, "lpf.env" to 24.0, "lpf.attack" to 0.01, "lpf.decay" to 0.15, "lpf.sustain" to 0.3,
                "lpf.release" to 0.1,
                "lpfCurves.attack" to AdsrCurves.indexOf(namedCurves.first),
                "lpfCurves.decay" to AdsrCurves.indexOf(namedCurves.second),
                "lpfCurves.release" to AdsrCurves.indexOf(namedCurves.third),
            ),
        ),
        ClassicRow(
            "lpf env 24 q 3 passes 3, a step envelope: the sweep forwarded to every stage of the cascade",
            mapOf(
                "lpf.freq" to 600.0, "lpf.q" to 3.0, "lpf.passes" to 3.0, "lpf.env" to 24.0,
                "lpf.attack" to 0.0, "lpf.decay" to 0.0, "lpf.sustain" to 0.4, "lpf.release" to 0.0,
            ),
        ),
        *listOf("lpf" to 600.0, "hpf" to 900.0).map { (f, freq) ->
            ClassicRow(
                "analog 2, $f env 24 q 3, a step envelope: the sweep through the saturated branch with the drift",
                mapOf(
                    "analog" to 2.0, "$f.freq" to freq, "$f.q" to 3.0, "$f.env" to 24.0,
                    "$f.attack" to 0.0, "$f.decay" to 0.0, "$f.sustain" to 0.4, "$f.release" to 0.0,
                ),
            )
        }.toTypedArray(),

        // ── analog: the humanize lane ──
        ClassicRow("analog 2, no filter: the oscillator drift alone", mapOf("analog" to 2.0)),
        ClassicRow("analog 2, hpf and lpf: the draw order (section 8)", mapOf("analog" to 2.0, "hpf.freq" to 300.0, "lpf.freq" to 1200.0)),

        // ── tremolo ──
        ClassicRow("tremolo depth 0.5 rate 4", mapOf("tremolo.depth" to 0.5, "tremolo.rate" to 4.0)),
        ClassicRow(
            "tremolo square",
            mapOf("tremolo.depth" to 1.0, "tremolo.rate" to 3.3, "tremolo.shape" to LfoShapes.indexOf("square")),
        ),

        // ── the envelope ──
        ClassicRow(
            "adsr 0.005 / 0.2 / 0.5 / 0.2 (whole frame counts at 48 kHz, 220.5 attack frames at 44.1 kHz)",
            mapOf("adsr.attack" to 0.005, "adsr.decay" to 0.2, "adsr.sustain" to 0.5, "adsr.release" to 0.2),
        ),
        ClassicRow(
            "adsr at fractional frame counts (one envelope law: fractional attack and decay on both hosts)",
            mapOf("adsr.attack" to 0.00501, "adsr.decay" to 0.20001, "adsr.sustain" to 0.5, "adsr.release" to 0.20001),
        ),
        ClassicRow(
            "adsrCurves linear, scurve, square",
            mapOf(
                "adsr.sustain" to 0.4,
                "adsrCurves.attack" to AdsrCurves.indexOf(AdsrCurve.Linear),
                "adsrCurves.decay" to AdsrCurves.indexOf(AdsrCurve.SCurve),
                "adsrCurves.release" to AdsrCurves.indexOf(AdsrCurve.Square),
            ),
        ),
        ClassicRow(
            "adsr release -0.1: a zero-length release stage, and the voice still lives to its gate (the lifetime floored at 0)",
            mapOf("adsr.release" to -0.1),
        ),
        ClassicRow("adsrOff: the teardown fade, one law on both hosts (step 6)", mapOf("adsr.on" to 0.0)),
        ClassicRow("adsrOff with release 0.2: the fade over a longer tail", mapOf("adsr.on" to 0.0, "adsr.release" to 0.2)),

        // ── the onepole: `classic()`'s first stage, in front of every other ──
        ClassicRow("onepole 900 with crush 5: in front of the quantizer", mapOf("onepole" to 900.0, "crush.bits" to 5.0)),

        // ── pitch doors under a filtered built-in: the vibrato and the FM as classic() slots ──
        ClassicRow(
            "the vibrato and FM stages (classic slots), under lpf env and hpf",
            mapOf(
                "lpf.freq" to 900.0, "lpf.env" to 12.0, "hpf.freq" to 120.0, "vibrato.rate" to 5.0, "vibrato.semitones" to 0.4,
                "fm.ratio" to 2.0, "fm.depth" to 150.0,
            ),
        ),

        // ── in combination ──
        ClassicRow(
            "coarse, hpf, lpf, tremolo and the envelope together",
            mapOf(
                "coarse.factor" to 2.0, "hpf.freq" to 150.0, "lpf.freq" to 2500.0, "lpf.q" to 2.0,
                "tremolo.depth" to 0.4, "tremolo.rate" to 6.0, "adsr.attack" to 0.02, "adsr.sustain" to 0.6, "adsr.release" to 0.1,
            ),
        ),
        ClassicRow("crush and distort in the chain", mapOf("crush.bits" to 5.0, "distort.amount" to 0.4, "lpf.freq" to 3000.0)),
    )
}
