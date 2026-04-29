package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.flushDenormal
import io.peekandpoke.klang.audio_be.resolveDistortionShape
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.adsr
import io.peekandpoke.klang.audio_bridge.bandpass
import io.peekandpoke.klang.audio_bridge.highpass
import io.peekandpoke.klang.audio_bridge.lowpass
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Click hunt for the "guitar" ignitor in TestTextPatterns.kt (rhythm cat() pattern, lines 222–228).
 *
 * Renders the full ignitor + each isolated branch + each chain stage across every distinct
 * MIDI note that the rhythm pattern can produce. Reports click metrics so we can see which
 * sub-tree introduces sample-to-sample discontinuities or transients above the rest.
 *
 * Click metrics:
 * - peakAbs       — max |x|
 * - attackPeak    — max |x| in first 1ms (voice-start transient — should be small)
 * - gateEndDelta  — max sample-to-sample |delta| within ±2ms of gate-end (release-edge click)
 * - tail          — max |x| in last 0.5ms of voice (residual after release — should be ~0)
 * - peakDelta     — max |x[n]-x[n-1]| over the whole render
 * - p99Delta      — 99th percentile |delta| (excludes the periodic-saw-edge baseline)
 * - ratio         — peakDelta / p99Delta. Periodic harshness ≈ 1; real click >> 1.
 */
class GuitarClickHuntTest : StringSpec({

    val sampleRate = 44100
    val blockFrames = 256

    // Settings from the rhythm-pattern call site (TestTextPatterns.kt:228)
    val drive = 10.0          // .oscparam("drive", drive)  with let drive = 10
    val brightness = 3500.0   // .oscparam("brightness", 3500)
    val spread = 0.02         // .oscp("spread", 0.02)
    val analog = 0.5          // default of the "analog" param in supersaw core

    fun coreSupersaw(): IgnitorDsl = IgnitorDsl.SuperSaw(
        freq = IgnitorDsl.Freq,
        voices = IgnitorDsl.Constant(2.0),
        freqSpread = IgnitorDsl.Param("spread", spread),
        analog = IgnitorDsl.Param("analog", analog),
    ).mulD(0.35)

    fun zawtoothBranch(): IgnitorDsl = IgnitorDsl.Zawtooth().mulD(0.25)

    fun squareBranch(): IgnitorDsl = IgnitorDsl.Square().mulD(0.15)

    fun pickNoiseBranch(): IgnitorDsl =
        IgnitorDsl.PinkNoise()
            .highpass(3000.0)
            .adsr(0.001, 0.025, 0.0, 0.005)
            .mulD(0.15)

    fun lowEndBranch(): IgnitorDsl = IgnitorDsl.SuperSaw(
        freq = IgnitorDsl.Freq,
        voices = IgnitorDsl.Constant(2.0),
        freqSpread = IgnitorDsl.Constant(0.15),
        analog = IgnitorDsl.Constant(0.1),
    ).mulD(0.05).highpass(110.0).lowpass(300.0)

    /** sweep cutoff per the FILE: sin(0.25Hz)*2000 + 2000 + 3000 → range 3000..7000 Hz. */
    fun sweepCutoff(): IgnitorDsl =
        (IgnitorDsl.Sine(freq = IgnitorDsl.Constant(0.25)).plusD(1.0).timesD(2000.0)).plusD(3000.0)

    fun preBandpassMix(): IgnitorDsl =
        coreSupersaw().plusDsl(zawtoothBranch()).plusDsl(squareBranch()).plusDsl(pickNoiseBranch())

    fun preDistortMix(): IgnitorDsl =
        preBandpassMix().bandpass(1000.0, 0.1).plusDsl(lowEndBranch())

    fun preDistortFiltered(): IgnitorDsl =
        preDistortMix().lowpassMod(sweepCutoff(), q = 1.25)

    fun postDistort(): IgnitorDsl = preDistortFiltered().distortChebyshev8(driveAmount = drive)

    fun postBrightnessLp(): IgnitorDsl = postDistort().lowpass(brightness, 0.9)

    fun postHpf(): IgnitorDsl = postBrightnessLp().highpass(100.0)

    fun fullGuitar(): IgnitorDsl =
        postHpf().adsr(0.004, 0.15, 0.8, 0.05)

    // ── Voice rendering ──

    fun renderVoiceFromIgnitor(ig: Ignitor, freqHz: Double, gateMs: Int = 250, releaseMs: Int = 200): AudioBuffer {
        val gateFrames = sampleRate * gateMs / 1000
        val releaseFrames = sampleRate * releaseMs / 1000
        val totalFrames = gateFrames + releaseFrames

        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = gateFrames,
            gateEndFrame = gateFrames,
            releaseFrames = releaseFrames,
            voiceEndFrame = totalFrames,
            scratchBuffers = ScratchBuffers(blockFrames),
        )

        val out = AudioBuffer(totalFrames)
        val tmp = AudioBuffer(blockFrames)
        var pos = 0
        while (pos < totalFrames) {
            val n = minOf(blockFrames, totalFrames - pos)
            ctx.offset = 0
            ctx.length = n
            ctx.voiceElapsedFrames = pos
            ig.generate(tmp, freqHz, ctx)
            for (i in 0 until n) out[pos + i] = tmp[i]
            pos += n
        }
        return out
    }

    fun renderVoice(dsl: IgnitorDsl, freqHz: Double, gateMs: Int = 250, releaseMs: Int = 200): AudioBuffer =
        renderVoiceFromIgnitor(dsl.toExciter(), freqHz, gateMs, releaseMs)

    /**
     * Renders the ignitor through the engine's `IgniteRenderer` wrapper (which hard-clips
     * to ±1 per output sample). Use this to verify the in-engine ±1 invariant.
     */
    fun renderVoiceThroughWrapper(dsl: IgnitorDsl, freqHz: Double, gateMs: Int = 250, releaseMs: Int = 200): AudioBuffer {
        val out = renderVoiceFromIgnitor(dsl.toExciter(), freqHz, gateMs, releaseMs)
        for (i in out.indices) {
            out[i] = out[i].coerceIn(-1.0, 1.0)
        }
        return out
    }

    data class Metrics(
        val peakAbs: Double,
        val attackPeak: Double,
        val gateEndDelta: Double,
        val tail: Double,
        val peakDelta: Double,
        val peakDeltaFrame: Int,
        val peakDeltaWhere: String,
        val p99Delta: Double,
        val nans: Int,
        val infs: Int,
    ) {
        val deltaRatio: Double get() = if (p99Delta > 0.0) peakDelta / p99Delta else 0.0

        fun shortString(): String = buildString {
            append("peak=").append(fmt(peakAbs))
            append(" atk=").append(fmt(attackPeak))
            append(" gateΔ=").append(fmt(gateEndDelta))
            append(" tail=").append(fmt(tail))
            append(" maxΔ=").append(fmt(peakDelta)).append("@").append(peakDeltaWhere)
            append(" p99Δ=").append(fmt(p99Delta))
            append(" ratio=").append(fmt(deltaRatio))
            if (nans > 0) append(" NaN=").append(nans)
            if (infs > 0) append(" Inf=").append(infs)
        }

        private fun fmt(f: Double): String {
            if (f.isNaN()) return "NaN"
            if (f.isInfinite()) return "Inf"
            return ((f * 1000).toInt() / 1000.0).toString()
        }
    }

    fun analyze(samples: AudioBuffer, gateEndFrame: Int): Metrics {
        var peak = 0.0
        var nans = 0
        var infs = 0
        var peakDelta = 0.0
        var peakDeltaIdx = 0
        val deltas = AudioBuffer(samples.size - 1)
        for (i in 1 until samples.size) {
            val v = samples[i]
            if (v.isNaN()) nans++
            if (v.isInfinite()) infs++
            val a = abs(v); if (a > peak) peak = a
            val d = abs(v - samples[i - 1])
            deltas[i - 1] = d
            if (d > peakDelta) {
                peakDelta = d
                peakDeltaIdx = i
            }
        }
        val peakDeltaWhere = when {
            peakDeltaIdx < sampleRate / 1000 -> "ATTACK"
            peakDeltaIdx in (gateEndFrame - sampleRate / 500)..(gateEndFrame + sampleRate / 500) -> "GATE_END"
            peakDeltaIdx > samples.size - sampleRate / 1000 -> "TAIL"
            else -> "MID(${(peakDeltaIdx * 1000 / sampleRate)}ms)"
        }
        val sorted = deltas.copyOf().also { it.sort() }
        val p99 = if (sorted.isEmpty()) 0.0 else sorted[(sorted.size * 0.99).toInt().coerceAtMost(sorted.size - 1)]

        val attackFrames = sampleRate / 1000  // 1ms
        var attackPeak = 0.0
        for (i in 0 until minOf(attackFrames, samples.size)) {
            val a = abs(samples[i]); if (a > attackPeak) attackPeak = a
        }

        val gateWin = sampleRate * 2 / 1000   // ±2ms
        val gateLo = (gateEndFrame - gateWin).coerceAtLeast(1)
        val gateHi = (gateEndFrame + gateWin).coerceAtMost(samples.size - 1)
        var gateDelta = 0.0
        for (i in gateLo..gateHi) {
            val d = abs(samples[i] - samples[i - 1])
            if (d > gateDelta) gateDelta = d
        }

        val tailFrames = sampleRate / 2000    // last 0.5ms
        var tail = 0.0
        for (i in (samples.size - tailFrames).coerceAtLeast(0) until samples.size) {
            val a = abs(samples[i]); if (a > tail) tail = a
        }

        return Metrics(peak, attackPeak, gateDelta, tail, peakDelta, peakDeltaIdx, peakDeltaWhere, p99, nans, infs)
    }

    // Pattern-relative MIDI offsets cap out at +24, descend to −7 → C2:chromatic root MIDI 36.
    val rhythmMidis = (29..60).toList()
    fun midiToHz(m: Int): Double = 440.0 * 2.0.pow((m - 69) / 12.0)

    val variants: List<Pair<String, () -> IgnitorDsl>> = listOf(
        // Sources isolated (no chain)
        "01_supersaw_core_only" to ::coreSupersaw,
        "02_zawtooth_only" to ::zawtoothBranch,
        "03_square_only" to ::squareBranch,
        "04_picknoise_only" to ::pickNoiseBranch,
        "05_lowend_only" to ::lowEndBranch,
        // Chain build-up
        "06_mix_pre_bandpass" to ::preBandpassMix,
        "07_after_bandpass+lowend" to ::preDistortMix,
        "08_after_sweep_lowpass" to ::preDistortFiltered,
        "09_after_chebyshev_distort" to ::postDistort,
        "10_after_post_lowpass" to ::postBrightnessLp,
        "11_after_highpass100" to ::postHpf,
        "12_full_guitar_with_outer_adsr" to ::fullGuitar,
    )

    // ─── Variants designed to attribute the click cleanly ─────────────────────
    // Build the FULL chain on each given source-set so we can answer:
    //   - is the click already present without the chebyshev distort?
    //   - is the click present without one specific source?
    //   - does the chebyshev with shape="soft" still click?

    fun chainWithSources(sourceMix: IgnitorDsl, distortShape: String = "chebyshev"): IgnitorDsl {
        val withBp = sourceMix.bandpass(1000.0, 0.1).plusDsl(lowEndBranch())
        val swept = withBp.lowpassMod(sweepCutoff(), q = 1.25)
        val distorted = IgnitorDsl.Clip(
            inner = IgnitorDsl.Drive(inner = swept, amount = IgnitorDsl.Constant(drive)),
            shape = distortShape, oversample = 8,
        )
        val postLp = distorted.lowpass(brightness, 0.9)
        val hpf = postLp.highpass(100.0)
        return hpf.adsr(0.004, 0.15, 0.8, 0.05)
    }

    // Source sets: full minus one, full, only-one
    fun srcsAll(): IgnitorDsl =
        coreSupersaw().plusDsl(zawtoothBranch()).plusDsl(squareBranch()).plusDsl(pickNoiseBranch())

    fun srcsNoCore(): IgnitorDsl =
        zawtoothBranch().plusDsl(squareBranch()).plusDsl(pickNoiseBranch())

    fun srcsNoZaw(): IgnitorDsl =
        coreSupersaw().plusDsl(squareBranch()).plusDsl(pickNoiseBranch())

    fun srcsNoSquare(): IgnitorDsl =
        coreSupersaw().plusDsl(zawtoothBranch()).plusDsl(pickNoiseBranch())

    fun srcsNoPick(): IgnitorDsl =
        coreSupersaw().plusDsl(zawtoothBranch()).plusDsl(squareBranch())

    fun srcsCoreOnly(): IgnitorDsl = coreSupersaw()
    fun srcsZawOnly(): IgnitorDsl = zawtoothBranch()
    fun srcsSquareOnly(): IgnitorDsl = squareBranch()
    fun srcsPickOnly(): IgnitorDsl = pickNoiseBranch()

    val attributionVariants: List<Pair<String, () -> IgnitorDsl>> = listOf(
        "A1_FULL_chebyshev" to { chainWithSources(srcsAll(), "chebyshev") },
        "A2_FULL_softclip" to { chainWithSources(srcsAll(), "soft") },
        "A3_FULL_hardclip" to { chainWithSources(srcsAll(), "hard") },
        "A4_NO_CORE_chebyshev" to { chainWithSources(srcsNoCore(), "chebyshev") },
        "A5_NO_ZAW_chebyshev" to { chainWithSources(srcsNoZaw(), "chebyshev") },
        "A6_NO_SQUARE_chebyshev" to { chainWithSources(srcsNoSquare(), "chebyshev") },
        "A7_NO_PICK_chebyshev" to { chainWithSources(srcsNoPick(), "chebyshev") },
        "A8_CORE_only_chebyshev" to { chainWithSources(srcsCoreOnly(), "chebyshev") },
        "A9_ZAW_only_chebyshev" to { chainWithSources(srcsZawOnly(), "chebyshev") },
        "A10_SQ_only_chebyshev" to { chainWithSources(srcsSquareOnly(), "chebyshev") },
        "A11_PICK_only_chebyshev" to { chainWithSources(srcsPickOnly(), "chebyshev") },
    )

    "guitar click hunt — sweep all rhythm-pattern notes through every variant" {
        println()
        println("================================================================")
        println("Guitar click hunt — TestTextPatterns.kt rhythm pattern (line 222)")
        println("================================================================")

        for ((name, builder) in variants) {
            val perNote = mutableListOf<Pair<Int, Metrics>>()
            for (m in rhythmMidis) {
                val freq = midiToHz(m)
                val out = renderVoice(builder(), freq, gateMs = 250, releaseMs = 200)
                val gateEndFrame = sampleRate * 250 / 1000
                perNote += m to analyze(out, gateEndFrame)
            }

            val worstPeak = perNote.maxBy { it.second.peakAbs }
            val worstAtk = perNote.maxBy { it.second.attackPeak }
            val worstGate = perNote.maxBy { it.second.gateEndDelta }
            val worstTail = perNote.maxBy { it.second.tail }
            val worstDelta = perNote.maxBy { it.second.peakDelta }
            val worstRatio = perNote.maxBy { it.second.deltaRatio }
            val totalNan = perNote.sumOf { it.second.nans }
            val totalInf = perNote.sumOf { it.second.infs }

            println()
            println("─── $name ───")
            println("  worst peakAbs    @ MIDI ${worstPeak.first}: ${worstPeak.second.shortString()}")
            println("  worst attackPeak @ MIDI ${worstAtk.first}: ${worstAtk.second.shortString()}")
            println("  worst gateΔ      @ MIDI ${worstGate.first}: ${worstGate.second.shortString()}")
            println("  worst tail       @ MIDI ${worstTail.first}: ${worstTail.second.shortString()}")
            println("  worst maxΔ       @ MIDI ${worstDelta.first}: ${worstDelta.second.shortString()}")
            println("  worst Δ-ratio    @ MIDI ${worstRatio.first}: ${worstRatio.second.shortString()}")
            if (totalNan > 0 || totalInf > 0) {
                println("  TOTAL NaN=$totalNan Inf=$totalInf")
            }
        }
        println()
    }

    "guitar click hunt — full guitar through IgniteRenderer wrap (the in-engine path)" {
        println()
        println("================================================================")
        println("In-engine path: full ignitor + IgniteRenderer hard-clip wrapper.")
        println("This is what users actually hear — peak should be ≤ 1.0 (hard cap).")
        println("================================================================")

        val perNote = mutableListOf<Pair<Int, Metrics>>()
        for (m in rhythmMidis) {
            val out = renderVoiceThroughWrapper(fullGuitar(), midiToHz(m), gateMs = 250, releaseMs = 200)
            perNote += m to analyze(out, sampleRate * 250 / 1000)
        }

        val worstPeak = perNote.maxBy { it.second.peakAbs }
        val worstDelta = perNote.maxBy { it.second.peakDelta }
        val worstRatio = perNote.maxBy { it.second.deltaRatio }
        println()
        println("  worst peakAbs @ MIDI ${worstPeak.first}: ${worstPeak.second.shortString()}")
        println("  worst maxΔ    @ MIDI ${worstDelta.first}: ${worstDelta.second.shortString()}")
        println("  worst Δ-ratio @ MIDI ${worstRatio.first}: ${worstRatio.second.shortString()}")
        println()
    }

    "guitar click hunt — sweep ALL 9 distortion shapes at drive=10, oversample=4 (file values)" {
        println()
        println("================================================================")
        println("Shape sweep — file uses 'soft', 4. Test every alternative.")
        println("================================================================")

        val shapes = listOf("soft", "hard", "gentle", "cubic", "diode", "fold", "chebyshev", "rectify", "exp")
        for (shape in shapes) {
            val sources = coreSupersaw().plusDsl(zawtoothBranch()).plusDsl(squareBranch()).plusDsl(pickNoiseBranch())
            val withBp = sources.bandpass(1000.0, 0.1).plusDsl(lowEndBranch())
            val swept = withBp.lowpassMod(sweepCutoff(), q = 1.25)
            val distorted = IgnitorDsl.Clip(
                inner = IgnitorDsl.Drive(inner = swept, amount = IgnitorDsl.Constant(drive)),
                shape = shape, oversample = 4,
            )
            val full = distorted.lowpass(brightness, 0.9).highpass(100.0).adsr(0.004, 0.15, 0.8, 0.05)

            val perNote = mutableListOf<Pair<Int, Metrics>>()
            for (m in rhythmMidis) {
                val out = renderVoice(full, midiToHz(m), gateMs = 250, releaseMs = 200)
                perNote += m to analyze(out, sampleRate * 250 / 1000)
            }
            val worstPeak = perNote.maxBy { it.second.peakAbs }
            val worstDelta = perNote.maxBy { it.second.peakDelta }
            val worstRatio = perNote.maxBy { it.second.deltaRatio }
            println()
            println("─── shape=$shape ───")
            println("  worst peakAbs @ MIDI ${worstPeak.first}: ${worstPeak.second.shortString()}")
            println("  worst maxΔ    @ MIDI ${worstDelta.first}: ${worstDelta.second.shortString()}")
            println("  worst Δ-ratio @ MIDI ${worstRatio.first}: ${worstRatio.second.shortString()}")
        }
        println()
    }

    "guitar click hunt — drive sweep on the full chain (chebyshev/8x)" {
        println()
        println("================================================================")
        println("Drive sweep: how does peakAbs / clicks scale with drive amount?")
        println("================================================================")

        val driveSettings = listOf(0.5, 1.0, 2.0, 3.0, 5.0, 7.0, 10.0)
        for (d in driveSettings) {
            // Full chain matching the user's code, but with a parameterised drive value
            val sources = coreSupersaw().plusDsl(zawtoothBranch()).plusDsl(squareBranch()).plusDsl(pickNoiseBranch())
            val withBp = sources.bandpass(1000.0, 0.1).plusDsl(lowEndBranch())
            val swept = withBp.lowpassMod(sweepCutoff(), q = 1.25)
            val distorted = IgnitorDsl.Clip(
                inner = IgnitorDsl.Drive(inner = swept, amount = IgnitorDsl.Constant(d)),
                shape = "chebyshev", oversample = 8,
            )
            val full = distorted.lowpass(brightness, 0.9).highpass(100.0).adsr(0.004, 0.15, 0.8, 0.05)

            val perNote = mutableListOf<Pair<Int, Metrics>>()
            for (m in rhythmMidis) {
                val out = renderVoice(full, midiToHz(m), gateMs = 250, releaseMs = 200)
                perNote += m to analyze(out, sampleRate * 250 / 1000)
            }
            val worstPeak = perNote.maxBy { it.second.peakAbs }
            val worstDelta = perNote.maxBy { it.second.peakDelta }
            val worstRatio = perNote.maxBy { it.second.deltaRatio }
            println()
            println("─── drive=$d ───")
            println("  worst peakAbs @ MIDI ${worstPeak.first}: ${worstPeak.second.shortString()}")
            println("  worst maxΔ    @ MIDI ${worstDelta.first}: ${worstDelta.second.shortString()}")
            println("  worst Δ-ratio @ MIDI ${worstRatio.first}: ${worstRatio.second.shortString()}")
        }
        println()
    }

    "guitar click hunt — POST_TANH fix across drive sweep (does it kill the saturation tone?)" {
        println()
        println("================================================================")
        println("POST_TANH fix preserved across drive 0.5..10. Should keep saturation")
        println("character (soft-clipping kicks in only when DC-blocker overshoots).")
        println("================================================================")

        for (variant in listOf(DistortVariant.STOCK, DistortVariant.POST_TANH)) {
            println()
            println("=== variant=$variant ===")
            for (d in listOf(0.5, 1.0, 2.0, 3.0, 5.0, 7.0, 10.0)) {
                val sources = coreSupersaw().plusDsl(zawtoothBranch()).plusDsl(squareBranch()).plusDsl(pickNoiseBranch())
                val withBp = sources.bandpass(1000.0, 0.1).plusDsl(lowEndBranch())
                val swept = withBp.lowpassMod(sweepCutoff(), q = 1.25)
                val perNote = mutableListOf<Pair<Int, Metrics>>()
                for (m in rhythmMidis) {
                    val preIg = swept.toExciter()
                    val distortedIg = preIg.distortVariant(
                        amount = d,
                        shape = "soft",
                        oversampleStages = Oversampler.factorToStages(4),
                        variant = variant
                    )
                    val full = distortedIg.lowpass(brightness, 0.9).highpass(100.0).adsr(0.004, 0.15, 0.8, 0.05)
                    val out = renderVoiceFromIgnitor(full, midiToHz(m), gateMs = 250, releaseMs = 200)
                    perNote += m to analyze(out, sampleRate * 250 / 1000)
                }
                val worst = perNote.maxBy { it.second.peakAbs }
                println("  drive=$d → ${worst.second.shortString()}  (MIDI ${worst.first})")
            }
        }
        println()
    }

    "guitar click hunt — DC-blocker fix prototypes (chain matches file 'soft', 4)" {
        println()
        println("================================================================")
        println("DC-blocker prototypes — measure peak/Δ before vs after each fix.")
        println("Chain matches file values: soft, 4x oversample, drive=10.")
        println("================================================================")

        // Build a chain that delegates the distort step to our parametric variant.
        // Everything else is the same as the user's chain via DSL.
        fun fullChainWithVariant(variant: DistortVariant, shape: String, oversample: Int): Ignitor {
            val sources = coreSupersaw().plusDsl(zawtoothBranch()).plusDsl(squareBranch()).plusDsl(pickNoiseBranch())
            val withBp = sources.bandpass(1000.0, 0.1).plusDsl(lowEndBranch())
            val swept = withBp.lowpassMod(sweepCutoff(), q = 1.25)
            val preDistortIg = swept.toExciter()
            val distortedIg = preDistortIg.distortVariant(
                amount = drive,
                shape = shape,
                oversampleStages = Oversampler.factorToStages(oversample),
                variant = variant
            )
            // Post-chain: lowpass(brightness, 0.9), highpass(100), adsr(...)
            return distortedIg
                .lowpass(brightness, 0.9)
                .highpass(100.0)
                .adsr(0.004, 0.15, 0.8, 0.05)
        }

        for (shape in listOf("soft", "chebyshev")) {
            val oversample = if (shape == "soft") 4 else 8
            println()
            println("=== shape=$shape, oversample=$oversample ===")
            for (variant in DistortVariant.values()) {
                val perNote = mutableListOf<Pair<Int, Metrics>>()
                for (m in rhythmMidis) {
                    // IMPORTANT: build a fresh chain per note — each Ignitor has internal state.
                    val ig = fullChainWithVariant(variant, shape, oversample)
                    val out = renderVoiceFromIgnitor(ig, midiToHz(m), gateMs = 250, releaseMs = 200)
                    perNote += m to analyze(out, sampleRate * 250 / 1000)
                }
                val worstPeak = perNote.maxBy { it.second.peakAbs }
                val worstDelta = perNote.maxBy { it.second.peakDelta }
                println("  $variant:")
                println("    worst peakAbs @ MIDI ${worstPeak.first}: ${worstPeak.second.shortString()}")
                println("    worst maxΔ    @ MIDI ${worstDelta.first}: ${worstDelta.second.shortString()}")
            }
        }
        println()
    }

    "guitar click hunt — leave-one-out source attribution + alternative distort shapes" {
        println()
        println("================================================================")
        println("Attribution: which source(s) feed the worst clicks via chebyshev?")
        println("================================================================")

        for ((name, builder) in attributionVariants) {
            val perNote = mutableListOf<Pair<Int, Metrics>>()
            for (m in rhythmMidis) {
                val freq = midiToHz(m)
                val out = renderVoice(builder(), freq, gateMs = 250, releaseMs = 200)
                val gateEndFrame = sampleRate * 250 / 1000
                perNote += m to analyze(out, gateEndFrame)
            }

            val worstPeak = perNote.maxBy { it.second.peakAbs }
            val worstDelta = perNote.maxBy { it.second.peakDelta }
            val worstRatio = perNote.maxBy { it.second.deltaRatio }
            val worstGate = perNote.maxBy { it.second.gateEndDelta }
            val worstAttack = perNote.maxBy { it.second.attackPeak }

            println()
            println("─── $name ───")
            println("  worst peakAbs    @ MIDI ${worstPeak.first}: ${worstPeak.second.shortString()}")
            println("  worst attackPeak @ MIDI ${worstAttack.first}: ${worstAttack.second.shortString()}")
            println("  worst gateΔ      @ MIDI ${worstGate.first}: ${worstGate.second.shortString()}")
            println("  worst maxΔ       @ MIDI ${worstDelta.first}: ${worstDelta.second.shortString()}")
            println("  worst Δ-ratio    @ MIDI ${worstRatio.first}: ${worstRatio.second.shortString()}")
        }
        println()
    }
})

// ── Prototype distort() variants — drop-in replacements for testing fixes ──
//
// Stock applies a one-pole DC blocker AFTER drive→shape, then DC-blocks. The blocker is
// `y[n] = x[n] - x[n-1] + α·y[n-1]` with α=0.995 — its impulse response is essentially
// a differential filter, so a square-wave input edge of magnitude 2 produces an output
// peak of ±2 (the audible click on the rhythm pattern). This is documented as the
// 2026-04-27 trade-off (see audio/MEMORY.md). The fixes here keep DC-blocking but
// remove or bound the 2x edge response.

internal enum class DistortVariant {
    /** Current behaviour. Reference baseline. */
    STOCK,

    /** Stock + tanh-saturate the DC-blocker output to ±~1.05. Bounded peaks, near-zero added cost. */
    POST_TANH,

    /** Replace the one-pole DC blocker with a biquad HPF at 5Hz Q=0.707 — no edge overshoot. */
    BIQUAD_HPF,

    /** DC-block BEFORE drive/shape (input is normally well-behaved); skip the post DC blocker. */
    PRE_BLOCK,
}

/**
 * Re-implementation of `Ignitor.distort` with a selectable DC-blocker strategy so we can
 * compare without touching the production code yet.
 */
internal fun Ignitor.distortVariant(
    amount: Double,
    shape: String,
    oversampleStages: Int,
    variant: DistortVariant,
): Ignitor {
    val resolved = resolveDistortionShape(shape)
    val waveshaper = resolved.fn
    val outputGain = resolved.outputGain
    val oversampler = if (oversampleStages > 0) Oversampler(oversampleStages) else null

    // One-pole state (used by STOCK + PRE_BLOCK + POST_TANH for the post path).
    val a = 0.995
    var x1 = 0.0
    var y1 = 0.0
    // Pre-block state (used by PRE_BLOCK).
    var px1 = 0.0
    var py1 = 0.0

    // Biquad HPF state (used by BIQUAD_HPF). Coeffs computed lazily for sample rate.
    var b0 = 0.0;
    var b1 = 0.0;
    var b2 = 0.0
    var ar1 = 0.0;
    var ar2 = 0.0
    var bx1 = 0.0;
    var bx2 = 0.0;
    var by1 = 0.0;
    var by2 = 0.0
    var biquadInit = false

    return Ignitor { output, freqHz, ctx ->
        ctx.scratchBuffers.use { work ->
            this.generate(work, freqHz, ctx)

            if (amount <= 0.0) {
                val end = ctx.offset + ctx.length
                for (i in ctx.offset until end) output[i] = work[i]
                return@use
            }

            val driveGain = 10.0.pow(amount * 1.2)
            val end = ctx.offset + ctx.length

            // Pre-shape DC block (PRE_BLOCK only).
            if (variant == DistortVariant.PRE_BLOCK) {
                for (i in ctx.offset until end) {
                    val xi = work[i].toDouble()
                    val po = xi - px1 + a * py1
                    px1 = xi
                    py1 = flushDenormal(po)
                    work[i] = po
                }
            }

            val os = oversampler
            if (os != null) {
                os.process(work, ctx.offset, ctx.length, ctx.scratchBuffers) { sample ->
                    (waveshaper(sample.toDouble() * driveGain) * outputGain)
                }
            } else {
                for (i in ctx.offset until end) {
                    work[i] = (waveshaper(work[i].toDouble() * driveGain) * outputGain)
                }
            }

            when (variant) {
                DistortVariant.STOCK -> {
                    for (i in ctx.offset until end) {
                        val y = work[i].toDouble()
                        val out = y - x1 + a * y1
                        x1 = y; y1 = flushDenormal(out)
                        output[i] = out
                    }
                }

                DistortVariant.POST_TANH -> {
                    for (i in ctx.offset until end) {
                        val y = work[i].toDouble()
                        val out = y - x1 + a * y1
                        x1 = y; y1 = flushDenormal(out)
                        // Soft-cap at ±1: tanh of the DC-blocked signal. Bounds the
                        // 2× overshoot back to ~1, smooth knee → no aliasing introduced.
                        output[i] = tanh(out)
                    }
                }

                DistortVariant.BIQUAD_HPF -> {
                    if (!biquadInit) {
                        // 2nd-order Butterworth HPF, fc=5Hz, Q=0.707.
                        val fc = 5.0
                        val w0 = 2.0 * PI * fc / ctx.sampleRateD
                        val cosw = kotlin.math.cos(w0)
                        val sinw = kotlin.math.sin(w0)
                        val q = 0.707
                        val alpha = sinw / (2.0 * q)
                        val a0 = 1.0 + alpha
                        b0 = ((1.0 + cosw) / 2.0) / a0
                        b1 = -(1.0 + cosw) / a0
                        b2 = ((1.0 + cosw) / 2.0) / a0
                        ar1 = (-2.0 * cosw) / a0
                        ar2 = (1.0 - alpha) / a0
                        biquadInit = true
                    }
                    for (i in ctx.offset until end) {
                        val xi = work[i].toDouble()
                        val out = b0 * xi + b1 * bx1 + b2 * bx2 - ar1 * by1 - ar2 * by2
                        bx2 = bx1; bx1 = xi
                        by2 = by1; by1 = flushDenormal(out)
                        output[i] = out
                    }
                }

                DistortVariant.PRE_BLOCK -> {
                    for (i in ctx.offset until end) {
                        output[i] = work[i]
                    }
                }
            }
        }
    }
}

// ── DSL helpers (private, only used by this test) ──

private fun IgnitorDsl.mulD(c: Double): IgnitorDsl = IgnitorDsl.Times(this, IgnitorDsl.Constant(c))

private fun IgnitorDsl.timesD(c: Double): IgnitorDsl = IgnitorDsl.Times(this, IgnitorDsl.Constant(c))

private fun IgnitorDsl.plusD(c: Double): IgnitorDsl = IgnitorDsl.Plus(this, IgnitorDsl.Constant(c))

private fun IgnitorDsl.plusDsl(other: IgnitorDsl): IgnitorDsl = IgnitorDsl.Plus(this, other)

private fun IgnitorDsl.lowpassMod(cutoff: IgnitorDsl, q: Double): IgnitorDsl =
    IgnitorDsl.Lowpass(inner = this, cutoffHz = cutoff, q = IgnitorDsl.Constant(q))

/** Mirrors `Osc.distort(amount, "chebyshev", 8)` — `factorToStages(8) = 3` (8x oversample). */
private fun IgnitorDsl.distortChebyshev8(driveAmount: Double): IgnitorDsl =
    IgnitorDsl.Clip(
        inner = IgnitorDsl.Drive(inner = this, amount = IgnitorDsl.Constant(driveAmount)),
        shape = "chebyshev",
        oversample = 8,
    )
