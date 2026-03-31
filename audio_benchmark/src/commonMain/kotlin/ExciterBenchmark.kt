package io.peekandpoke.klang.audio_benchmark

import io.peekandpoke.klang.audio_be.KlangAudioRenderer
import io.peekandpoke.klang.audio_be.exciter.ExciterRegistry
import io.peekandpoke.klang.audio_be.exciter.registerDefaults
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.AdsrEnvelope
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

/**
 * Multiplatform benchmark suite for exciter oscillators and DSP compositions.
 *
 * Measures the real-time factor (RTF) for each benchmark case: RTF < 1.0 means the renderer
 * is faster than real-time (good), RTF > 1.0 means it can't keep up (glitches).
 *
 * Usage:
 * ```
 * val bench = ExciterBenchmark()
 * val results = bench.run(ExciterBenchmark.defaultCases())
 * println(bench.toCsv(results))
 * ```
 */
class ExciterBenchmark(
    private val sampleRate: Int = 44100,
    private val blockFrames: Int = 128,
    private val warmupBlocks: Int = 100,
    private val measureBlocks: Int = 500,
    private val iterations: Int = 3,
) {
    /**
     * A single benchmark case — describes what to render and measure.
     */
    data class Case(
        val name: String,
        val voiceCount: Int = 1,
        val voiceData: VoiceData,
    )

    /**
     * Result of a single benchmark case.
     */
    data class Result(
        val name: String,
        val voices: Int,
        val rtf: Double,
        val renderUsPerBlock: Double,
        val audioUsPerBlock: Double,
    )

    /**
     * Runs all benchmark cases and returns results.
     */
    fun run(cases: List<Case>): List<Result> = cases.map { case -> runCase(case) }.sortedByDescending { it.rtf }

    /**
     * Formats results as CSV.
     */
    fun toCsv(results: List<Result>): String = buildString {
        appendLine("name,voices,rtf,render_us_per_block,audio_us_per_block")
        for (r in results) {
            appendLine("${r.name},${r.voices},${fmt(r.rtf)},${fmt(r.renderUsPerBlock)},${fmt(r.audioUsPerBlock)}")
        }
    }

    /**
     * Formats results as a Markdown document with platform header, table, and metadata.
     */
    fun toMarkdown(results: List<Result>, platform: String): String = buildString {
        appendLine("# Audio Benchmark Results")
        appendLine()
        appendLine("- **Platform:** $platform")
        appendLine("- **Sample rate:** $sampleRate Hz")
        appendLine("- **Block size:** $blockFrames frames")
        appendLine("- **Warmup:** $warmupBlocks blocks")
        appendLine("- **Measurement:** $measureBlocks blocks x $iterations iterations (median)")
        appendLine()
        appendLine("| Name | Voices | RTF | Render µs/block | Audio µs/block |")
        appendLine("|------|-------:|----:|----------------:|---------------:|")
        for (r in results) {
            appendLine("| ${r.name} | ${r.voices} | ${fmt(r.rtf)} | ${fmt(r.renderUsPerBlock)} | ${fmt(r.audioUsPerBlock)} |")
        }
    }

    internal fun fmt(v: Double, decimals: Int = 4): String {
        val factor = pow10(decimals)
        val intPart = (v * factor).toLong()
        // Build string manually to avoid Double.toString() scientific notation for small values
        val negative = intPart < 0
        val abs = if (negative) -intPart else intPart
        val whole = abs / factor.toLong()
        val frac = abs % factor.toLong()
        val fracStr = frac.toString().padStart(decimals, '0')
        return (if (negative) "-" else "") + whole.toString() + "." + fracStr
    }

    private fun pow10(n: Int): Double {
        var r = 1.0
        repeat(n) { r *= 10.0 }
        return r
    }

    private fun runCase(case: Case): Result {
        val audioUsPerBlock = blockFrames.toDouble() / sampleRate * 1_000_000.0

        // Run multiple iterations, take median RTF
        val rtfs = DoubleArray(iterations) { runSingleIteration(case, audioUsPerBlock) }
        rtfs.sort()
        val medianRtf = rtfs[iterations / 2]
        val medianRenderUs = medianRtf * audioUsPerBlock

        return Result(
            name = case.name,
            voices = case.voiceCount,
            rtf = medianRtf,
            renderUsPerBlock = medianRenderUs,
            audioUsPerBlock = audioUsPerBlock,
        )
    }

    private fun runSingleIteration(case: Case, audioUsPerBlock: Double): Double {
        // Create fresh infrastructure for each iteration to avoid state leakage
        val commLink = KlangCommLink()
        val orbits = Orbits(blockFrames = blockFrames, sampleRate = sampleRate)
        val exciterRegistry = ExciterRegistry().apply { registerDefaults() }
        val scheduler = VoiceScheduler(
            VoiceScheduler.Options(
                commLink = commLink.backend,
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                exciterRegistry = exciterRegistry,
                orbits = orbits,
            )
        )
        scheduler.setBackendStartTime(0.0)

        val renderer = KlangAudioRenderer(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voices = scheduler,
            orbits = orbits,
        )

        val outBuffer = ShortArray(blockFrames * 2)

        // Schedule voices
        for (i in 0 until case.voiceCount) {
            scheduler.scheduleVoice(
                ScheduledVoice(
                    playbackId = "bench",
                    startTime = 0.0,
                    gateEndTime = 9999.0, // sustain throughout benchmark
                    playbackStartTime = 0.0,
                    data = case.voiceData.copy(
                        freqHz = (case.voiceData.freqHz ?: 440.0) + (i * 2),
                    ),
                ),
                clearScheduled = false,
            )
        }

        // Warmup — prime JIT, allocate lazy buffers
        var frame = 0
        repeat(warmupBlocks) {
            scheduler.process(frame)
            renderer.renderBlock(frame, outBuffer)
            frame += blockFrames
        }

        // Measure
        val mark = TimeSource.Monotonic.markNow()

        repeat(measureBlocks) {
            scheduler.process(frame)
            renderer.renderBlock(frame, outBuffer)
            frame += blockFrames
        }

        val elapsedUs = mark.elapsedNow().toDouble(DurationUnit.MICROSECONDS)
        val totalAudioUs = measureBlocks * audioUsPerBlock

        return elapsedUs / totalAudioUs
    }

    companion object {
        /** Helper to build VoiceData with common defaults for benchmarking. */
        private fun voice(
            sound: String,
            freqHz: Double = 440.0,
            oscParams: Map<String, Double>? = null,
            filters: FilterDefs = FilterDefs.empty,
            adsr: AdsrEnvelope = AdsrEnvelope.defaultSynth,
            room: Double? = null,
            roomSize: Double? = null,
            distort: Double? = null,
            distortShape: String? = null,
            fmh: Double? = null,
            fmEnv: Double? = null,
            vibrato: Double? = null,
            vibratoMod: Double? = null,
            tremoloSync: Double? = null,
            tremoloDepth: Double? = null,
        ): VoiceData = VoiceData.empty.copy(
            sound = sound,
            freqHz = freqHz,
            oscParams = oscParams,
            filters = filters,
            adsr = adsr,
            room = room,
            roomSize = roomSize,
            distort = distort,
            distortShape = distortShape,
            fmh = fmh,
            fmEnv = fmEnv,
            vibrato = vibrato,
            vibratoMod = vibratoMod,
            tremoloSync = tremoloSync,
            tremoloDepth = tremoloDepth,
        )

        /**
         * Standard set of benchmark cases covering individual oscillators, super oscillators,
         * physical models, noise, and common compositions.
         */
        fun defaultCases(): List<Case> {
            val lpf1k = FilterDefs(listOf(FilterDef.LowPass(cutoffHz = 1000.0, q = 1.0)))
            val super8v = mapOf("voices" to 8.0)

            return listOf(
                // ── Individual oscillators ─────────────────────────────────────
                Case("sine", voiceData = voice("sine")),
                Case("sawtooth", voiceData = voice("sawtooth")),
                Case("square", voiceData = voice("square")),
                Case("triangle", voiceData = voice("triangle")),
                Case("ramp", voiceData = voice("ramp")),
                Case("zawtooth", voiceData = voice("zawtooth")),
                Case("impulse", voiceData = voice("impulse")),
                Case("pulze", voiceData = voice("pulze")),

                // ── Super oscillators (8 internal voices) ─────────────────────
                Case("supersaw", voiceData = voice("supersaw", oscParams = super8v)),
                Case("supersine", voiceData = voice("supersine", oscParams = super8v)),
                Case("supersquare", voiceData = voice("supersquare", oscParams = super8v)),
                Case("supertri", voiceData = voice("supertri", oscParams = super8v)),
                Case("superramp", voiceData = voice("superramp", oscParams = super8v)),

                // ── Physical models ───────────────────────────────────────────
                Case("pluck", voiceData = voice("pluck")),
                Case("superpluck", voiceData = voice("superpluck", oscParams = super8v)),

                // ── Noise ─────────────────────────────────────────────────────
                Case("whitenoise", voiceData = voice("whitenoise")),
                Case("pinknoise", voiceData = voice("pinknoise")),
                Case("brownnoise", voiceData = voice("brownnoise")),
                Case("dust", voiceData = voice("dust")),

                // ── Scaling (supersaw at various internal voice counts) ───────
                Case("supersaw_1v", voiceData = voice("supersaw", oscParams = mapOf("voices" to 1.0))),
                Case("supersaw_4v", voiceData = voice("supersaw", oscParams = mapOf("voices" to 4.0))),
                Case("supersaw_8v", voiceData = voice("supersaw", oscParams = mapOf("voices" to 8.0))),
                Case("supersaw_16v", voiceData = voice("supersaw", oscParams = mapOf("voices" to 16.0))),

                // ── Compositions ──────────────────────────────────────────────
                Case("supersaw+lpf+adsr", voiceData = voice("supersaw", oscParams = super8v, filters = lpf1k)),
                Case(
                    "supersaw+lpf+adsr+reverb",
                    voiceData = voice("supersaw", oscParams = super8v, filters = lpf1k, room = 0.5, roomSize = 0.5)
                ),
                Case("pluck+distort", voiceData = voice("pluck", distort = 0.5, distortShape = "soft")),
                Case("square+fm", voiceData = voice("square", fmh = 2.0, fmEnv = 200.0)),
                Case(
                    "sine+vibrato+tremolo",
                    voiceData = voice("sine", vibrato = 6.0, vibratoMod = 0.3, tremoloSync = 4.0, tremoloDepth = 0.5)
                ),
            )
        }
    }
}
