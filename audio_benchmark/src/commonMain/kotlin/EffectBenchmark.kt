package io.peekandpoke.klang.audio_benchmark

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.effects.DelayLine
import io.peekandpoke.klang.audio_be.effects.Ducking
import io.peekandpoke.klang.audio_be.effects.Phaser
import io.peekandpoke.klang.audio_be.effects.Reverb
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.ultra.common.toFixed
import kotlin.math.PI
import kotlin.math.sin
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Multiplatform benchmark suite for individual DSP effects and filters.
 *
 * Each case constructs a single effect/filter instance, pre-fills an input buffer with a
 * stable signal (sine wave), and times repeated `process()` calls. The reported RTF is
 * the per-block CPU time relative to the audio block's wall-clock duration —
 * RTF < 1.0 means the effect alone is faster than real-time on this platform.
 *
 * Useful for comparing JVM vs JS perf at the building-block level: voice rendering,
 * mixer routing, and other voice-pipeline costs are excluded so the numbers reflect
 * the effect's intrinsic cost only.
 */
class EffectBenchmark(
    private val sampleRate: Int = 44100,
    private val blockFrames: Int = 128,
    private val warmupBlocks: Int = 10000,
    private val measureBlocks: Int = 5000,
    private val iterations: Int = 3,
) {

    /**
     * A benchmark case. [factory] builds a "process one block" closure that internally owns
     * the effect instance and any working buffers. The closure is called once per block.
     */
    class Case(
        val name: String,
        val factory: (sampleRate: Int, blockFrames: Int) -> () -> Unit,
    )

    data class Result(
        val name: String,
        val rtf: Double,
        val renderUsPerBlock: Double,
        val audioUsPerBlock: Double,
    )

    fun run(cases: List<Case>): List<Result> =
        cases.map { runCase(it) }.sortedByDescending { it.rtf }

    fun toCsv(results: List<Result>): String = buildString {
        appendLine("name,rtf,render_us_per_block,audio_us_per_block")
        for (r in results) {
            appendLine("${r.name},${fmt(r.rtf, 6)},${fmt(r.renderUsPerBlock)},${fmt(r.audioUsPerBlock)}")
        }
    }

    fun toMarkdown(results: List<Result>, platform: String): String = buildString {
        appendLine("# Effect Benchmark Results")
        appendLine()
        appendLine("- **Platform:** $platform")
        appendLine("- **Sample rate:** $sampleRate Hz")
        appendLine("- **Block size:** $blockFrames frames")
        appendLine("- **Warmup:** $warmupBlocks blocks")
        appendLine("- **Measurement:** $measureBlocks blocks x $iterations iterations (median)")
        appendLine()
        appendLine("Each case runs a single effect/filter `process()` repeatedly on a sine-wave-filled buffer.")
        appendLine("RTF = render time / audio time. Lower is better (RTF < 1.0 = faster than real-time).")
        appendLine()
        appendLine("| Name | RTF | Render µs/block | Audio µs/block |")
        appendLine("|------|----:|----------------:|---------------:|")
        for (r in results) {
            appendLine("| ${r.name} | ${fmt(r.rtf, 6)} | ${fmt(r.renderUsPerBlock)} | ${fmt(r.audioUsPerBlock)} |")
        }
    }

    internal fun fmt(v: Double, decimals: Int = 4): String = v.toFixed(decimals)

    private fun runCase(case: Case): Result {
        val audioUsPerBlock = blockFrames.toDouble() / sampleRate * 1_000_000.0
        val rtfs = DoubleArray(iterations) { runSingleIteration(case, audioUsPerBlock) }
        rtfs.sort()
        val medianRtf = rtfs[iterations / 2]
        return Result(
            name = case.name,
            rtf = medianRtf,
            renderUsPerBlock = medianRtf * audioUsPerBlock,
            audioUsPerBlock = audioUsPerBlock,
        )
    }

    private fun runSingleIteration(case: Case, audioUsPerBlock: Double): Double {
        val processBlock = case.factory(sampleRate, blockFrames)

        repeat(warmupBlocks) { processBlock() }

        val mark = TimeSource.Monotonic.markNow()
        repeat(measureBlocks) { processBlock() }
        val elapsedUs = mark.elapsedNow().toDouble(DurationUnit.MICROSECONDS)

        return elapsedUs / (measureBlocks * audioUsPerBlock)
    }

    companion object {

        /** Sine wave source buffer. Refilled into the working buffer each block. */
        private fun sineSource(freqHz: Double, sampleRate: Int, blockFrames: Int): AudioBuffer {
            val out = AudioBuffer(blockFrames)
            val step = 2.0 * PI * freqHz / sampleRate
            for (i in 0 until blockFrames) {
                out[i] = sin(step * i) * 0.5
            }
            return out
        }

        // ── Filters (mono, in-place) ────────────────────────────────────────────

        private fun monoFilterCase(name: String, build: (sampleRate: Double) -> io.peekandpoke.klang.audio_be.filters.AudioFilter): Case =
            Case(name) { sr, bf ->
                val filter = build(sr.toDouble())
                val src = sineSource(440.0, sr, bf)
                val buf = AudioBuffer(bf)
                val step: () -> Unit = {
                    src.copyInto(buf)
                    filter.process(buf, 0, bf)
                }
                step
            }

        // ── Stereo input + separate output ──────────────────────────────────────

        private fun stereoIoCase(
            name: String,
            build: (sampleRate: Int) -> StereoIoEffect,
        ): Case = Case(name) { sr, bf ->
            val effect = build(sr)
            val srcL = sineSource(440.0, sr, bf)
            val srcR = sineSource(440.0, sr, bf)
            val input = StereoBuffer(bf)
            val output = StereoBuffer(bf)
            val step: () -> Unit = {
                srcL.copyInto(input.left)
                srcR.copyInto(input.right)
                effect.process(input, output, bf)
            }
            step
        }

        // ── Stereo in-place ─────────────────────────────────────────────────────

        private fun stereoInPlaceCase(
            name: String,
            build: (sampleRate: Int) -> StereoInPlaceEffect,
        ): Case = Case(name) { sr, bf ->
            val effect = build(sr)
            val srcL = sineSource(440.0, sr, bf)
            val srcR = sineSource(440.0, sr, bf)
            val buf = StereoBuffer(bf)
            val step: () -> Unit = {
                srcL.copyInto(buf.left)
                srcR.copyInto(buf.right)
                effect.process(buf, bf)
            }
            step
        }

        // ── Stereo dynamics (separate left/right buffers, in-place) ─────────────

        private fun compressorCase(
            name: String,
            build: (sampleRate: Int) -> Compressor,
        ): Case = Case(name) { sr, bf ->
            val comp = build(sr)
            val srcL = sineSource(440.0, sr, bf)
            val srcR = sineSource(440.0, sr, bf)
            val left = AudioBuffer(bf)
            val right = AudioBuffer(bf)
            val step: () -> Unit = {
                srcL.copyInto(left)
                srcR.copyInto(right)
                comp.process(left, right, bf)
            }
            step
        }

        fun defaultCases(): List<Case> = listOf(
            // Filters
            monoFilterCase("OnePoleLPF (1k)") { sr -> LowPassHighPassFilters.OnePoleLPF(1000.0, sr) },
            monoFilterCase("OnePoleHPF (1k)") { sr -> LowPassHighPassFilters.OnePoleHPF(1000.0, sr) },
            monoFilterCase("SvfLPF (1k, q=1)") { sr -> LowPassHighPassFilters.SvfLPF(1000.0, 1.0, sr) },
            monoFilterCase("SvfHPF (1k, q=1)") { sr -> LowPassHighPassFilters.SvfHPF(1000.0, 1.0, sr) },
            monoFilterCase("SvfBPF (1k, q=1)") { sr -> LowPassHighPassFilters.SvfBPF(1000.0, 1.0, sr) },
            monoFilterCase("SvfNotch (1k, q=1)") { sr -> LowPassHighPassFilters.SvfNotch(1000.0, 1.0, sr) },

            // Stereo effects with separate input/output
            stereoIoCase("Reverb (default)") { sr ->
                val r = Reverb(sr).apply {
                    roomSize = 0.5
                    damp = 0.5
                }
                StereoIoEffect { input, output, length -> r.process(input, output, length) }
            },
            stereoIoCase("DelayLine (0.5s, fb=0.3)") { sr ->
                val d = DelayLine(maxDelaySeconds = 1.0, sampleRate = sr).apply {
                    delayTimeSeconds = 0.5
                    feedback = 0.3
                }
                StereoIoEffect { input, output, length -> d.process(input, output, length) }
            },

            // Stereo effects in-place
            stereoInPlaceCase("Phaser (rate=0.5, depth=0.5)") { sr ->
                val p = Phaser(sr).apply {
                    rate = 0.5
                    depth = 0.5
                    centerFreq = 1000.0
                    sweepRange = 1000.0
                    feedback = 0.3
                }
                StereoInPlaceEffect { buf, length -> p.process(buf, length) }
            },

            // Dynamics
            compressorCase("Compressor (default)") { sr ->
                Compressor(
                    sampleRate = sr,
                    thresholdDb = -20.0,
                    ratio = 4.0,
                    kneeDb = 6.0,
                    attackSeconds = 0.003,
                    releaseSeconds = 0.1,
                )
            },
            compressorCase("Compressor (limiter, 20:1)") { sr ->
                // Same parameters as KlangAudioRenderer's master limiter
                Compressor(
                    sampleRate = sr,
                    thresholdDb = -1.0,
                    ratio = 20.0,
                    kneeDb = 0.0,
                    attackSeconds = 0.001,
                    releaseSeconds = 0.1,
                )
            },

            Case("Ducking (mono)") { sr, bf ->
                val ducking = Ducking(sr, attackSeconds = 0.1, depth = 0.5)
                val src = sineSource(440.0, sr, bf)
                val sidechainSrc = sineSource(2.0, sr, bf)
                val input = AudioBuffer(bf)
                val sidechain = AudioBuffer(bf)
                val step: () -> Unit = {
                    src.copyInto(input)
                    sidechainSrc.copyInto(sidechain)
                    ducking.process(input, sidechain, bf)
                }
                step
            },
        )

        private fun interface StereoIoEffect {
            fun process(input: StereoBuffer, output: StereoBuffer, length: Int)
        }

        private fun interface StereoInPlaceEffect {
            fun process(buf: StereoBuffer, length: Int)
        }
    }
}
