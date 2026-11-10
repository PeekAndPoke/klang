/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

// The stage cost table on Kotlin/JS (KLANG-00420, probe 7): the JVM runner's catalogue, method and table
// (`StageCostRunner` in `jvmTest`), timed in node on the PRODUCTION test bundle, the shape the worklet ships (Karma's
// development bundle calls accessors where production loads fields, and boxes elsewhere: audio/ref/performance.md).

/** A value of `process.env`, null when unset or outside node. */
private fun envVar(name: String): String? {
    val value: dynamic = js("(typeof process !== 'undefined' && process.env) ? process.env : {}")[name]

    return if (value == null || value == undefined) null else value.unsafeCast<String>()
}

/**
 * V8's new space in use, in bytes (`v8.getHeapSpaceStatistics()`, `new_space`): the allocation counter of the
 * allocation mode. It reads every young object, the probe's own included, and nothing a scavenge took away inside the
 * window, so a window with a collection in it reads wrong (usually negative); the median over the rounds stands.
 */
private fun newSpaceUsedBytes(): Double {
    val spaces: dynamic = js("require('v8')").getHeapSpaceStatistics()
    val count: Int = spaces.length.unsafeCast<Int>()

    for (i in 0 until count) {
        val space: dynamic = spaces[i]

        if (space.space_name == "new_space") {
            return space.space_used_size.unsafeCast<Double>()
        }
    }

    return Double.NaN
}

/**
 * Times every row of [stageCostCases] with [measureCosts] in this process and hands back the text table (the JVM
 * runner's form, its header naming node and V8); with `KLANG_PROBE_OUT` set (a file path) it also writes the table there
 * and the JSON rows beside it ([jsonTwin]'s naming, each row with `"platform": "js"`).
 *
 * The allocation columns are empty (NaN) unless `KLANG_PROBE_JS_ALLOC=1`: then the bytes are V8's new-space growth
 * around each render ([newSpaceUsedBytes], audio/ref/performance.md's window method without its forced scavenge), and
 * the run should give node a large semi-space so few collections fall inside a window. That run is for the bytes only:
 * the readings sit outside the clock's window but change what the process does between renders.
 *
 * Run (one build, then node directly; never Karma's development bundle):
 *
 *     console/with-build-lock.sh ./gradlew :audio_be:compileTestProductionExecutableKotlinJs
 *     cd <build dir>/audio_be/compileSync/js/test/testProductionExecutable/kotlin
 *     KLANG_PROBE_OUT=/abs/stage-cost-js.txt node -e "require('./klang-engine-audio_be-test.js')
 *         .io.peekandpoke.klang.audio_be.probes.runStageCostJs(); process.exit(0)"
 *
 * Loading the test bundle starts kotest's engine, but its specs run only after `require` has returned (the engine
 * goes on asynchronously; checked 2026-11-10: nothing but its `enteredTheMatrix` line runs before), so the table is
 * timed before any spec and the `process.exit(0)` ends the process before the first one.
 */
@JsExport
fun runStageCostJs(): String {
    val allocMode = envVar("KLANG_PROBE_JS_ALLOC") == "1"

    if (allocMode) {
        jsAllocationCounter = ::newSpaceUsedBytes
    }

    val started = nanoNow()
    val config = CostConfig()
    val cases = stageCostCases()
    val results = measureCosts(stages = cases.map { it.probe }, config = config)
    val rows = sortedByCost(deriveCostRows(cases = cases, results = results, sampleRate = config.sampleRate))
    val seconds = (nanoNow() - started) / 1e9
    val node: String = js("process.version").unsafeCast<String>()
    val v8: String = js("process.versions.v8").unsafeCast<String>()
    val arch: String = js("process.arch").unsafeCast<String>()
    val cpus: Int = js("require('os').cpus().length").unsafeCast<Int>()
    val execArgv: String = js("process.execArgv.join(' ')").unsafeCast<String>()
    val report = buildString {
        appendLine("Stage cost table, Kotlin/JS (KLANG-00420, probe 7), ${js("new Date().toISOString()").unsafeCast<String>()}")
        appendLine("node $node, V8 $v8, $arch, $cpus processors, node flags: ${execArgv.ifEmpty { "none" }}; the production test bundle")
        appendLine(
            "sample rate ${config.sampleRate} Hz (one sample = ${formatFixed(x = 1e9 / config.sampleRate, digits = 1)} ns), " +
                "block ${config.blockFrames} frames, every case rendered ${config.blocksLong} blocks and ${config.blocksShort} block per visit, " +
                "ns per sample = (long - short) / the frames between",
        )
        appendLine(
            "${config.warmupRounds} warm-up rounds, then ${config.rounds} timed rounds; every round visits every case once, " +
                "in its own shuffled order (seed ${config.seed}); a fresh stage instance per render; clock performance.now()",
        )
        appendLine(
            "input: full-scale white noise, seeded, held (an Ignitor gate never closes inside the render); sources and voices " +
                "play a held middle C ($COST_NOTE_HZ Hz); the catalogue's Ignitor stages play the stage probe's 220 Hz",
        )
        appendLine(
            "allocation: " + if (allocMode) {
                "V8's new-space growth around each render, (long - short) / the blocks between (KLANG_PROBE_JS_ALLOC=1); " +
                    "typed-array backing stores are outside the V8 heap and do not count, a collection inside a window reads wrong"
            } else {
                "not counted (V8 has no per-thread counter; KLANG_PROBE_JS_ALLOC=1 counts the new space in a run of its own)"
            },
        )
        appendLine()
        appendLine("Columns:")
        STAGE_COST_LEGEND.forEach { appendLine("  $it") }
        appendLine()
        append(formatStageCostTable(rows))
        appendLine()
        appendLine("${rows.size} rows, ${formatFixed(x = seconds, digits = 1)} s")
    }

    println(report)

    val target = envVar("KLANG_PROBE_OUT")

    if (target != null) {
        val fs: dynamic = js("require('fs')")
        val json = if (target.endsWith(".txt")) target.dropLast(4) + ".json" else "$target.json"

        fs.writeFileSync(target, report)
        fs.writeFileSync(json, formatStageCostJson(rows = rows, platform = "js"))
        println("stage cost table written to $target, JSON to $json")
    }

    return report
}
