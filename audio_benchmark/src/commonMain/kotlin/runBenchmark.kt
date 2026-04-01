package io.peekandpoke.klang.audio_benchmark

/**
 * Runs the full benchmark suite and prints results as table + CSV + Markdown.
 * Called from platform-specific main() functions.
 */
fun runBenchmark() {
    val bench = IgnitorBenchmark()
    val cases = IgnitorBenchmark.defaultCases()
    val platform = platformInfo()

    println("Platform: $platform")
    println("Running ${cases.size} benchmark cases (3x each, median)...")
    println()

    val results = bench.run(cases)

    // Print aligned table
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
    println("=== CSV ===")
    println()
    println(bench.toCsv(results))

    println("=== Markdown ===")
    println()
    println(bench.toMarkdown(results, platform))
}
