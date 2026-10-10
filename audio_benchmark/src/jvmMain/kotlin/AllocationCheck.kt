/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_benchmark

import com.sun.management.ThreadMXBean
import java.io.File
import java.lang.management.ManagementFactory
import java.time.LocalDate
import java.util.Collections
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The allocation check: `KLANG_BENCH_MODE=alloc ./gradlew :audio_benchmark:jvmRun`, after
 * `./gradlew :audio_benchmark:compileProductionExecutableKotlinJs`. `console/full-check.sh` runs both.
 *
 * In this order:
 * 1. **JVM:** every case of [AllocationCases] in this one process (a mixed profile), 9 rounds of 20,000 warm-up and
 *    16,000 measured blocks, `ThreadMXBean` around the measured window. The median, and the max beside it.
 * 2. **V8:** the production node bundle, once per case, round and block count: node with `--trace-gc-nvp` and a
 *    1 MB semi-space, 20,000 warm-up blocks, then 0 or 200,000 measured blocks. The bytes per block are the
 *    difference of the two processes' summed `allocated=` lines, divided by 200,000. 3 rounds, interleaved, the
 *    median. The node is `KLANG_NODE`, or the system `node`.
 * 3. Both are compared with `audio_benchmark/alloc-budgets.txt`. A case without a budget line, or a line naming an
 *    unknown case, is red.
 *
 * A measurement that saw nothing is red, never 0 B: a node process with no `allocated=` lines, or no `NS` figure; a
 * case whose last block is silent (it no longer exercises its path); a node or a JVM case that times out; a JVM
 * without thread allocation accounting; a bundle older than the sources it is built from (it would measure old code).
 * A true 0 B (the window allocating no more than its base, within the method's noise) is green.
 *
 * 4. Two rows (V8 and JVM) are appended to `docs/benchmarks/alloc-scoreboard.md`.
 * 5. It prints one `OVER` line per breach, one line per other problem, and a `RESULT` line, and returns 1 when red.
 *
 * It measures, it does not locate: after a breach, `audio/ref/performance.md` ("Measuring allocations") has the
 * recipe for finding the box.
 */
object AllocationCheck {

    private const val JVM_ROUNDS = 9
    private const val JVM_WARM = 20_000
    private const val JVM_COUNT = 16_000

    private const val V8_ROUNDS = 3
    private const val V8_WARM = 20_000
    private const val V8_COUNT = 200_000

    /**
     * The longest a node process may run. The slowest case (`E-C-rel05-n16`, about 35 µs per block) takes about 8 s
     * for its 220,000 blocks, so this is generous. A timeout stops the V8 half: a case that hangs must not hold the
     * build lock for long.
     */
    private const val NODE_TIMEOUT_SEC = 120

    /** The longest `node --version` (and `git describe`) may take. */
    private const val COMMAND_TIMEOUT_SEC = 10

    /**
     * The longest one JVM case may take in one round (20,000 warm-up and 16,000 measured blocks; the slowest takes
     * about 1 s). A timeout stops the check: a thread cannot be killed, so nothing else is measured beside it.
     */
    private const val JVM_CASE_TIMEOUT_SEC = 120

    /**
     * The quietest last-block peak a case may have: below it the case is silent, it rendered nothing in its window and
     * guards nothing. The quietest case sounding is far above it (see the task record, review round 2).
     */
    private const val SILENT_PEAK = 1.0e-4

    /** The build command the stale-bundle message names: `--rerun` relinks, so the bundle's time is fresh. */
    private const val BUILD_BUNDLE = "./gradlew :audio_benchmark:compileProductionExecutableKotlinJs --rerun"

    private const val BUNDLE =
        "audio_benchmark/build/compileSync/js/main/productionExecutable/kotlin/klang-engine-audio_benchmark.js"
    private const val BUDGETS = "audio_benchmark/alloc-budgets.txt"

    /**
     * The modules the node bundle is built from: `audio_benchmark` and its `project(...)` dependencies, transitively
     * (read from the build scripts, 2026-10-10). Their `commonMain`, `jsMain` and `main` (the KSP processors) sources
     * are what the bundle is compared with.
     *
     * Kept by hand. It does not see a new `project(...)` dependency or a new source set (a `webMain`), a change to the
     * build (`buildSrc`, `gradle.properties`, a `build.gradle.kts`: the compiler and its flags), or a source whose old
     * time came back (`cp -p`, `touch -r`). `console/full-check.sh` always relinks the bundle right before the check,
     * so the gap matters only for a standalone run.
     */
    private val bundleModules = listOf(
        "audio_benchmark", "audio_be", "audio_bridge", "audio_fe", "audio-wire-codec-ksp", "common", "klang",
        "klangscript", "klangscript-annotations", "klangscript-ksp", "klangscript-libs", "klangui", "sprudel", "tones",
    )
    private val bundleSourceSets = listOf("commonMain", "jsMain", "main")
    private const val SCOREBOARD = "docs/benchmarks/alloc-scoreboard.md"

    private const val V8 = "v8"
    private const val JVM = "jvm"
    private val platforms = listOf(V8, JVM)

    private val allocatedLine = Regex("""allocated=(\d+)""")
    private val nsLine = Regex("""^NS ([0-9.eE+-]+)$""")
    private val peakLine = Regex("""^PEAK (\S+)$""")
    private val errorLike = Regex("""Error|Exception|ERROR""")

    /** Node's "Name: message" line of an uncaught throw; it comes AFTER the throw site's source line. */
    private val errorMessageLike = Regex("""^[A-Za-z_$][\w$]*(Error|Exception)\b: """)

    /** One case on one platform: the median bytes per block, the largest round's, and the median µs per block. */
    private class Measurement(val bytes: Double, val maxBytes: Double, val usPerBlock: Double)

    private class Budget(val bytes: Double, val guards: String)

    /** Runs the check and returns the exit code: 0 green, 1 red. */
    fun run(): Int {
        val root = repoRoot()
        val problems = mutableListOf<String>()
        val budgets = readBudgets(file = File(root, BUDGETS), problems = problems)

        println("=== Allocation check: ${AllocationCases.all.size} cases, bytes per block ===")
        println()
        println("JVM: $JVM_ROUNDS rounds of $JVM_WARM warm-up and $JVM_COUNT measured blocks, in this process")

        val jvmOrNull = measureJvm(problems)
        val jvm = jvmOrNull ?: emptyMap()

        val node = System.getenv("KLANG_NODE")?.trim()?.takeIf { it.isNotEmpty() } ?: "node"
        val nodeVersion = command(listOf(node, "--version"), dir = root)

        println()
        println("V8: $node ${nodeVersion ?: "(no version)"}, $V8_ROUNDS rounds of two processes ($V8_WARM warm-up, then 0 or $V8_COUNT blocks)")

        val v8 = when {
            jvmOrNull == null -> {
                problems.add("FAIL v8: not run, the JVM half did not finish (its thread is still running)")
                emptyMap()
            }

            nodeVersion == null -> {
                problems.add("FAIL v8: not run, `$node --version` gave no answer within $COMMAND_TIMEOUT_SEC s (no node, or it hangs)")
                emptyMap()
            }

            else -> measureV8(node = node, root = root, problems = problems)
        }

        val results = mapOf(V8 to v8, JVM to jvm)
        val over = mutableListOf<String>()
        val breached = mutableSetOf<String>()

        for (case in AllocationCases.all) {
            for (platform in platforms) {
                val budget = budgets[case.name to platform]
                val measured = results.getValue(platform)[case.name]

                if (budget == null) {
                    problems.add("MISSING budget: ${case.name} $platform has no line in $BUDGETS")
                } else if (measured != null && measured.bytes > budget.bytes) {
                    breached.add("${case.name} $platform")
                    over.add(
                        "OVER ${case.name} $platform: ${fmtBytes(measured.bytes)} B per block, " +
                                "budget ${fmtBytes(budget.bytes)} (${budget.guards})"
                    )
                }
            }
        }

        val describe = command(listOf("git", "describe", "--always", "--dirty"), dir = root) ?: "unknown"

        appendScoreboard(
            file = File(root, SCOREBOARD),
            describe = describe,
            rows = listOf(
                Triple(V8, "node ${nodeVersion ?: "unknown"}", v8),
                Triple(JVM, "JVM ${System.getProperty("java.version")}", jvm),
            ),
            breached = breached,
        )

        printTable(v8 = v8, jvm = jvm, budgets = budgets)

        val checked = AllocationCases.all.size * platforms.size

        println()
        over.forEach { println(it) }
        problems.forEach { println(it) }
        println("Scoreboard: $SCOREBOARD ($describe)")

        if (over.isEmpty() && problems.isEmpty()) {
            println("RESULT GREEN: $checked of $checked within budget")

            return 0
        }

        println("RESULT RED: ${over.size} of $checked over budget, other problems: ${problems.size}")

        return 1
    }

    /** What the JVM worker is doing, read by the watching thread. */
    private class JvmProgress {
        @Volatile
        var case: String = ""

        @Volatile
        var startedSec: Double = 0.0
    }

    /**
     * The JVM half, on a worker thread watched for [JVM_CASE_TIMEOUT_SEC] per case and round. Returns the
     * measurements, or null when a case timed out (the problem is added; the worker cannot be stopped).
     */
    private fun measureJvm(problems: MutableList<String>): Map<String, Measurement>? {
        val mx = ManagementFactory.getThreadMXBean() as ThreadMXBean

        if (!mx.isThreadAllocatedMemorySupported || !mx.isThreadAllocatedMemoryEnabled) {
            problems.add(
                "FAIL jvm: this JVM does not count thread allocations (ThreadMXBean supported " +
                        "${mx.isThreadAllocatedMemorySupported}, enabled ${mx.isThreadAllocatedMemoryEnabled}), so it cannot measure"
            )

            return emptyMap()
        }

        val progress = JvmProgress()
        val workerProblems = Collections.synchronizedList(mutableListOf<String>())
        val result = AtomicReference<Map<String, Measurement>>(emptyMap())

        val worker = Thread {
            try {
                result.set(measureJvmRounds(mx = mx, progress = progress, problems = workerProblems))
            } catch (e: Throwable) {
                workerProblems.add("FAIL ${progress.case} jvm: $e")
            }
        }

        worker.isDaemon = true
        progress.startedSec = nowSec()
        worker.start()

        while (worker.isAlive) {
            worker.join(1000)

            if (worker.isAlive && nowSec() - progress.startedSec > JVM_CASE_TIMEOUT_SEC) {
                problems.add(
                    "FAIL ${progress.case} jvm: timeout, one round of it ran longer than $JVM_CASE_TIMEOUT_SEC s; " +
                            "the check stopped there"
                )
                problems.addAll(workerProblems)

                return null
            }
        }

        problems.addAll(workerProblems)

        return result.get()
    }

    private fun measureJvmRounds(mx: ThreadMXBean, progress: JvmProgress, problems: MutableList<String>): Map<String, Measurement> {
        val bytes = HashMap<String, MutableList<Double>>()
        val us = HashMap<String, MutableList<Double>>()
        val silent = mutableSetOf<String>()
        val failed = mutableSetOf<String>()

        for (round in 0 until JVM_ROUNDS) {
            for (case in AllocationCases.all) {
                if (case.name in failed) {
                    continue
                }

                progress.case = case.name
                progress.startedSec = nowSec()

                // One case's exception costs that case only, as on V8.
                val rig = try {
                    AllocationCases.rig(case).also { it.run(JVM_WARM) }
                } catch (e: Exception) {
                    failed.add(case.name)
                    problems.add("FAIL ${case.name} jvm: $e")

                    continue
                }

                val a0 = mx.currentThreadAllocatedBytes.toDouble()
                val t0 = System.nanoTime().toDouble()

                try {
                    rig.run(JVM_COUNT)
                } catch (e: Exception) {
                    failed.add(case.name)
                    problems.add("FAIL ${case.name} jvm: $e")

                    continue
                }

                val t1 = System.nanoTime().toDouble()
                val a1 = mx.currentThreadAllocatedBytes.toDouble()

                if (a0 < 0.0 || a1 < 0.0) {
                    problems.add("FAIL jvm: ThreadMXBean read -1 for ${case.name} (allocation counting is off), so it cannot measure")

                    return emptyMap()
                }

                val peak = rig.peak()

                if (!(peak >= SILENT_PEAK) && case.name !in silent) {
                    silent.add(case.name)
                    problems.add("FAIL ${case.name} jvm: silent, it rendered nothing in its window (last block peak $peak)")
                }

                bytes.getOrPut(case.name) { mutableListOf() }.add((a1 - a0) / JVM_COUNT)
                us.getOrPut(case.name) { mutableListOf() }.add((t1 - t0) / 1000.0 / JVM_COUNT)
            }
        }

        return AllocationCases.all.filter { it.name !in silent && it.name !in failed }.associate { case ->
            val b = bytes.getValue(case.name)

            case.name to Measurement(bytes = median(b), maxBytes = b.max(), usPerBlock = median(us.getValue(case.name)))
        }
    }

    private fun nowSec(): Double = System.nanoTime().toDouble() / 1.0e9

    private fun measureV8(node: String, root: File, problems: MutableList<String>): Map<String, Measurement> {
        val bundle = File(root, BUNDLE)

        if (!bundle.isFile) {
            problems.add("FAIL v8: no bundle at $BUNDLE (build it: $BUILD_BUNDLE)")

            return emptyMap()
        }

        val newest = newestBundleSource(root)

        if (newest != null && newest.lastModified().toDouble() > bundle.lastModified().toDouble()) {
            problems.add(
                "FAIL v8: the bundle is older than ${newest.relativeTo(root).path}, so it would measure old code (build it: $BUILD_BUNDLE)"
            )

            return emptyMap()
        }

        val bytes = HashMap<String, MutableList<Double>>()
        val us = HashMap<String, MutableList<Double>>()
        val failed = mutableSetOf<String>()
        var stopped = false

        // Interleaved: each round visits every case once, so a slow minute on the machine spreads over all of them.
        for (round in 1..V8_ROUNDS) {
            for (case in AllocationCases.all) {
                if (stopped || case.name in failed) {
                    continue
                }

                when (val outcome = measureCase(node = node, bundle = bundle, case = case.name)) {
                    is V8Outcome.Failed -> {
                        failed.add(case.name)
                        problems.add("FAIL ${case.name} v8: ${outcome.message}")

                        // A timeout is never noise, and the first case failing points at a cause every case shares.
                        val first = round == 1 && case == AllocationCases.all.first()

                        if (outcome.timeout || (first && !outcome.silent)) {
                            stopped = true
                            problems.add(
                                "FAIL v8: stopped after ${case.name}'s ${if (outcome.timeout) "timeout" else "failure"}; " +
                                        "the other cases were not run"
                            )
                        }
                    }

                    is V8Outcome.Measured -> {
                        bytes.getOrPut(case.name) { mutableListOf() }.add(outcome.perBlock)
                        us.getOrPut(case.name) { mutableListOf() }.add(outcome.usPerBlock)

                        println(
                            "  round $round/$V8_ROUNDS  ${case.name.padEnd(16)} " +
                                    "${fmt(outcome.perBlock, 1).padStart(9)} B  ${fmt(outcome.usPerBlock, 2).padStart(8)} µs"
                        )
                    }
                }
            }
        }

        return AllocationCases.all.filter { it.name !in failed && bytes.containsKey(it.name) }.associate { case ->
            val b = bytes.getValue(case.name)

            case.name to Measurement(bytes = median(b), maxBytes = b.max(), usPerBlock = median(us.getValue(case.name)))
        }
    }

    /** One case in one round on V8: its bytes and µs per block, or why it could not be measured. */
    private sealed interface V8Outcome {
        class Measured(val perBlock: Double, val usPerBlock: Double) : V8Outcome

        class Failed(val message: String, val timeout: Boolean = false, val silent: Boolean = false) : V8Outcome
    }

    /**
     * The two processes of one case and round. The base runs first; when it fails, the window is not started. A true
     * 0 B (the window's sum at or below the base's, within the method's noise) is a measurement, not a failure.
     */
    private fun measureCase(node: String, bundle: File, case: String): V8Outcome {
        val base = runNode(node = node, bundle = bundle, case = case, count = 0)

        base.failure?.let { return it }

        if (base.gcLines == 0) {
            return V8Outcome.Failed("no allocated= lines in the base process's --trace-gc-nvp output (another node or trace format?)")
        }

        val window = runNode(node = node, bundle = bundle, case = case, count = V8_COUNT)

        window.failure?.let { return it }

        val peak = window.peak

        return when {
            window.gcLines == 0 ->
                V8Outcome.Failed("no allocated= lines in the window's --trace-gc-nvp output (another node or trace format?)")

            !(window.ns > 0.0) -> V8Outcome.Failed("NS ${window.ns}: the window did not run")
            peak == null -> V8Outcome.Failed("no PEAK line: an older bundle without the peak?")
            !(peak >= SILENT_PEAK) ->
                V8Outcome.Failed("silent, it rendered nothing in its window (last block peak $peak)", silent = true)

            else -> V8Outcome.Measured(
                perBlock = (window.allocated - base.allocated) / V8_COUNT,
                usPerBlock = window.ns / 1000.0,
            )
        }
    }

    /**
     * One node process: the summed `allocated=` bytes of its GC lines, how many lines matched, its `NS` and `PEAK`
     * figures, or why it failed (with the first error-like line and the last line of its output).
     */
    private class NodeRun(
        val allocated: Double = 0.0,
        val gcLines: Int = 0,
        val ns: Double = 0.0,
        val peak: Double? = null,
        val failure: V8Outcome.Failed? = null,
    )

    private fun runNode(node: String, bundle: File, case: String, count: Int): NodeRun {
        // To a file, not a pipe: the timeout below must not wait for the end of a stream a hung process never closes.
        val output = File.createTempFile("klang-alloc-", ".log")

        try {
            val process = try {
                ProcessBuilder(
                    node, "--trace-gc-nvp", "--max-semi-space-size=1", "--min-semi-space-size=1", bundle.name,
                ).directory(bundle.parentFile).redirectErrorStream(true).redirectOutput(output).also {
                    it.environment()["KLANG_ALLOC_CASE"] = case
                    it.environment()["KLANG_ALLOC_WARM"] = V8_WARM.toString()
                    it.environment()["KLANG_ALLOC_COUNT"] = count.toString()
                }.start()
            } catch (e: Exception) {
                return NodeRun(failure = V8Outcome.Failed("could not start $node (${e.message})"))
            }

            if (!process.waitFor(NODE_TIMEOUT_SEC.toLong(), TimeUnit.SECONDS)) {
                killTree(process)

                return NodeRun(
                    failure = V8Outcome.Failed(
                        message = "timeout, node ran longer than $NODE_TIMEOUT_SEC s with $count blocks and was killed",
                        timeout = true,
                    )
                )
            }

            var allocated = 0.0
            var gcLines = 0
            var ns: Double? = null
            var peak: Double? = null
            var errorMessage: String? = null
            var firstError: String? = null
            var last: String? = null

            for (line in output.readLines()) {
                val matches = allocatedLine.findAll(line).toList()

                for (match in matches) {
                    allocated += match.groupValues[1].toDouble()
                }

                if (matches.isNotEmpty()) {
                    gcLines++

                    continue
                }

                val text = line.trim()
                val nsMatch = nsLine.find(text)
                val peakMatch = peakLine.find(text)

                if (nsMatch != null) {
                    ns = nsMatch.groupValues[1].toDouble()
                } else if (peakMatch != null) {
                    peak = peakMatch.groupValues[1].toDoubleOrNull() ?: Double.NaN
                } else if (text.isNotEmpty()) {
                    if (errorMessage == null && errorMessageLike.containsMatchIn(text)) {
                        errorMessage = text
                    }

                    if (firstError == null && !text.startsWith("at ") && errorLike.containsMatchIn(text)) {
                        firstError = text
                    }

                    last = text
                }
            }

            val exit = process.exitValue()
            val measuredNs = ns

            if (exit != 0 || measuredNs == null) {
                val shown = errorMessage ?: firstError
                val said = listOfNotNull(shown, last.takeIf { it != shown }).joinToString(" / ").ifEmpty { "no output" }

                return NodeRun(failure = V8Outcome.Failed("node exit $exit, no NS line; it said: $said"))
            }

            return NodeRun(allocated = allocated, gcLines = gcLines, ns = measuredNs, peak = peak)
        } finally {
            output.delete()
        }
    }

    /** Kills [process] and everything it started (a wrapper script's node too). */
    private fun killTree(process: Process) {
        process.descendants().forEach { it.destroyForcibly() }
        process.destroyForcibly()
    }

    /** The newest source file the bundle is built from, or null when none is found. */
    private fun newestBundleSource(root: File): File? = bundleModules
        .flatMap { module -> bundleSourceSets.map { File(root, "$module/src/$it") } }
        .filter { it.isDirectory }
        .flatMap { dir -> dir.walkTopDown().filter { it.isFile }.toList() }
        .maxByOrNull { it.lastModified().toDouble() }

    /**
     * The budget file: `case platform budget guards`, `#` starts a comment. A line naming an unknown case or
     * platform, a budget that is not a finite number at or above 0, or a second line for the same case and platform
     * is a problem.
     */
    private fun readBudgets(file: File, problems: MutableList<String>): Map<Pair<String, String>, Budget> {
        if (!file.isFile) {
            problems.add("MISSING budget file $BUDGETS")

            return emptyMap()
        }

        val known = AllocationCases.all.map { it.name }.toSet()
        val budgets = HashMap<Pair<String, String>, Budget>()

        file.readLines().forEachIndexed { index, raw ->
            val line = raw.substringBefore('#').trim()

            if (line.isEmpty()) {
                return@forEachIndexed
            }

            val parts = line.split(Regex("""\s+"""), limit = 4)
            val where = "$BUDGETS line ${index + 1}"
            val bytes = parts.getOrNull(2)?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }

            if (parts.size < 3 || bytes == null) {
                problems.add("BAD budget line: $where, \"$raw\"")

                return@forEachIndexed
            }

            val case = parts[0]
            val platform = parts[1]

            if (case !in known) {
                problems.add("UNKNOWN case in budget: $where names \"$case\", which is not in AllocationCases")

                return@forEachIndexed
            }

            if (platform !in platforms) {
                problems.add("UNKNOWN platform in budget: $where names \"$platform\" (v8 or jvm)")

                return@forEachIndexed
            }

            if (budgets.containsKey(case to platform)) {
                problems.add("DUPLICATE budget: $where, a second line for $case $platform")

                return@forEachIndexed
            }

            budgets[case to platform] = Budget(bytes = bytes, guards = parts.getOrNull(3)?.trim() ?: "")
        }

        return budgets
    }

    /**
     * Appends one row per platform. A cell is the median bytes and µs per block; a breach carries a `!`. When the
     * last table's header does not match today's case list, a new table starts under a dated heading.
     */
    private fun appendScoreboard(
        file: File,
        describe: String,
        rows: List<Triple<String, String, Map<String, Measurement>>>,
        breached: Set<String>,
    ) {
        val date = LocalDate.now().toString()
        val header = "| date | commit | machine | platform | runtime | " +
                AllocationCases.all.joinToString(" | ") { it.name } + " |"
        val divider = "|" + "---|".repeat(5 + AllocationCases.all.size)
        val text = StringBuilder()

        if (!file.isFile) {
            file.parentFile.mkdirs()
            text.appendLine("# The allocation scoreboard")
            text.appendLine()
            text.appendLine("Two rows per run of the allocation check (`KLANG_BENCH_MODE=alloc ./gradlew :audio_benchmark:jvmRun`,")
            text.appendLine("also the `alloc` step of `console/full-check.sh`): one for V8, one for the JVM. Appended by the driver,")
            text.appendLine("never edited by hand. A cell is the median bytes per block and the median µs per block; a `!` marks a")
            text.appendLine("breach of `audio_benchmark/alloc-budgets.txt`. The V8 µs run with a 1 MB semi-space, so they include")
            text.appendLine("more GC than a worklet: comparable between rows, not with the real-time factor benchmarks.")
            text.appendLine("The cases are `AllocationCases` in `audio_benchmark`, at middle C (261.63 Hz).")
        }

        val lastHeader = if (file.isFile) file.readLines().lastOrNull { it.startsWith("| date |") } else null

        if (lastHeader != header) {
            text.appendLine()
            text.appendLine("## Since $date")
            text.appendLine()
            text.appendLine(header)
            text.appendLine(divider)
        }

        for ((platform, runtime, measured) in rows) {
            val cells = AllocationCases.all.map { case ->
                val m = measured[case.name]

                when {
                    m == null -> "n/a"
                    "${case.name} $platform" in breached -> "${fmtBytes(m.bytes)} B! ${fmt(m.usPerBlock, 2)}"
                    else -> "${fmtBytes(m.bytes)} B ${fmt(m.usPerBlock, 2)}"
                }
            }

            text.appendLine("| $date | $describe | ${cpuModel()} | $platform | $runtime | ${cells.joinToString(" | ")} |")
        }

        file.appendText(text.toString())
    }

    private fun printTable(v8: Map<String, Measurement>, jvm: Map<String, Measurement>, budgets: Map<Pair<String, String>, Budget>) {
        fun budget(case: String, platform: String) = budgets[case to platform]?.let { fmtBytes(it.bytes) } ?: "-"

        fun bytes(m: Measurement?) = m?.let { fmtBytes(it.bytes) } ?: "n/a"

        fun max(m: Measurement?) = m?.let { fmtBytes(it.maxBytes) } ?: "n/a"

        fun us(m: Measurement?) = m?.let { fmt(it.usPerBlock, 2) } ?: "n/a"

        println()
        println(
            "case".padEnd(16) + "v8 B".padStart(8) + "max".padStart(8) + "budget".padStart(8) + "µs".padStart(8) +
                    "   " + "jvm B".padStart(8) + "max".padStart(8) + "budget".padStart(8) + "µs".padStart(8)
        )

        for (case in AllocationCases.all) {
            val a = v8[case.name]
            val b = jvm[case.name]

            println(
                case.name.padEnd(16) + bytes(a).padStart(8) + max(a).padStart(8) + budget(case.name, V8).padStart(8) +
                        us(a).padStart(8) + "   " + bytes(b).padStart(8) + max(b).padStart(8) +
                        budget(case.name, JVM).padStart(8) + us(b).padStart(8)
            )
        }
    }

    private fun median(values: List<Double>): Double = values.sorted()[values.size / 2]

    private fun fmt(v: Double, decimals: Int): String = String.format(Locale.ROOT, "%.${decimals}f", v)

    /** Whole bytes, and no "-0" for a noise reading just under zero. */
    private fun fmtBytes(v: Double): String = fmt(v, 0).let { if (it == "-0") "0" else it }

    private fun cpuModel(): String = runCatching {
        File("/proc/cpuinfo").useLines { lines ->
            lines.firstOrNull { it.startsWith("model name") }?.substringAfter(":")?.trim()
        }
    }.getOrNull() ?: System.getProperty("os.arch")

    /** The first line of a command's output, or null when it fails or runs longer than [COMMAND_TIMEOUT_SEC]. */
    private fun command(cmd: List<String>, dir: File): String? {
        val output = File.createTempFile("klang-alloc-cmd-", ".log")

        try {
            val process = try {
                ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).redirectOutput(output).start()
            } catch (e: Exception) {
                return null
            }

            if (!process.waitFor(COMMAND_TIMEOUT_SEC.toLong(), TimeUnit.SECONDS)) {
                killTree(process)

                return null
            }

            val text = output.readText().trim()

            return if (process.exitValue() == 0 && text.isNotEmpty()) text.lines().first() else null
        } finally {
            output.delete()
        }
    }

    /** `jvmRun` works in `audio_benchmark/`: the repo root is the first folder up with `settings.gradle.kts`. */
    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile

        while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }

        return dir ?: error("no settings.gradle.kts above ${File("").absolutePath}")
    }
}
