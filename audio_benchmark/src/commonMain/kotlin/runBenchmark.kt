package io.peekandpoke.klang.audio_benchmark

/**
 * Runs the full benchmark suite (ignitors + effects) and prints results as table + CSV + Markdown.
 * Called from platform-specific main() functions.
 *
 * Output is structured so the trailing markdown section (starting at the first `# Audio Benchmark`)
 * contains both the ignitor and effect markdown reports, contiguous, with nothing else after them —
 * the `console/run-dsp-benchmarks.sh` script captures from there to end.
 */
fun runBenchmark() {
    val platform = platformInfo()

    println("Platform: $platform")
    println()

    val ignitorMd = runIgnitorBenchmarks(platform)
    println()
    val effectMd = runEffectBenchmarks(platform)

    // Trailing markdown section — captured by the wrapper script.
    println()
    println(ignitorMd)
    println()
    println(effectMd)
}

private fun runIgnitorBenchmarks(platform: String): String {
    val bench = IgnitorBenchmark()
    val cases = IgnitorBenchmark.defaultCases()

    println("=== Ignitor benchmarks ===")
    println("Running ${cases.size} cases (3x each, median)...")
    println()

    val results = bench.run(cases)

    val nameWidth = results.maxOf { it.name.length } + 2

    fun fmt(v: Double, decimals: Int): String = bench.fmt(v, decimals)

    println(
        "${"Name".padEnd(nameWidth)} ${"Voices".padStart(6)} ${"RTF".padStart(10)} " +
                "${"Render µs".padStart(12)} ${"Audio µs".padStart(12)}"
    )
    println("-".repeat(nameWidth + 6 + 10 + 12 + 12 + 4))

    for (r in results) {
        println(
            "${r.name.padEnd(nameWidth)} ${r.voices.toString().padStart(6)} ${fmt(r.rtf, 6).padStart(10)} " +
                    "${fmt(r.renderUsPerBlock, 2).padStart(12)} ${fmt(r.audioUsPerBlock, 2).padStart(12)}"
        )
    }

    println()
    println("=== CSV (ignitors) ===")
    println()
    println(bench.toCsv(results))

    return bench.toMarkdown(results, platform)
}

private fun runEffectBenchmarks(platform: String): String {
    val bench = EffectBenchmark()
    val cases = EffectBenchmark.defaultCases()

    println("=== Effect benchmarks ===")
    println("Running ${cases.size} cases (3x each, median)...")
    println()

    val results = bench.run(cases)

    val nameWidth = results.maxOf { it.name.length } + 2

    fun fmt(v: Double, decimals: Int): String = bench.fmt(v, decimals)

    println(
        "${"Name".padEnd(nameWidth)} ${"RTF".padStart(10)} " +
                "${"Render µs".padStart(12)} ${"Audio µs".padStart(12)}"
    )
    println("-".repeat(nameWidth + 10 + 12 + 12 + 3))

    for (r in results) {
        println(
            "${r.name.padEnd(nameWidth)} ${fmt(r.rtf, 6).padStart(10)} " +
                    "${fmt(r.renderUsPerBlock, 2).padStart(12)} ${fmt(r.audioUsPerBlock, 2).padStart(12)}"
        )
    }

    println()
    println("=== CSV (effects) ===")
    println()
    println(bench.toCsv(results))

    return bench.toMarkdown(results, platform)
}
