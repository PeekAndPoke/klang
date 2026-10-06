/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The names the Ignitor/Katalyst naming retired (2026-10-04, `docs/plans/ignitor-katalyst-naming.md`) stay
 * retired: no live source, test, song or document spells the old script object, its slot object, its setter, the
 * old wire field or the old Kotlin types. A replaced surface is removed, not deprecated (`/dsl-design` section 5),
 * so a re-added old name is a regression this row names with its file and line.
 *
 * The same holds for the Ignitor's triangle door, `tri` since 2026-10-06
 * (`docs/tasks-archive/2026-10/20261006-oscillator-names-across-dsls.md`): [retiredTri] guards the old door
 * `Ign.triangle` / `Ignitor.triangle`, its builder, its wire node and its engine factory. The sound name and the LFO
 * shape `triangle` stay, so the pattern names only the code spellings, never the bare word.
 *
 * History keeps its words (the rules register): the [historyPaths] are records, and inside [lineScopedFiles] only
 * the named section may spell an old name. A new allowlist entry is a maintainer decision.
 */
class RetiredIgnitorNamesSpec : StringSpec({

    val retired = Regex(
        """\bOsc\b(?!\*Builder)|OscSlot|Oscp\b|KlangScriptOsc|(?<![A-Za-z0-9])oscp\b|(?<![A-Za-z0-9])oscparams?\b|""" +
            """(?<![A-Za-z0-9])oscParam|OscParam|Oscparam|StdLibOscTest|\boscSlot\(|\bosc-params?\b|SoundValue\.Osc"""
    )

    /** The triangle door's old code names (2026-10-06); the sound and LFO shape `"triangle"` are not among them. */
    val retiredTri = Regex(
        """\bIgn(?:itor)?\.triangle\b|KlangScriptIgnitor\.triangle\b|Ignitors\.triangle\b|OscTriangleBuilder|""" +
            """IgnitorDsl\.Triangle\b"""
    )

    val extensions = setOf("kt", "kts", "md", "MD", "html", "xml", "py", "sh", "ipynb", "txt")
    /** Generated or local directories, skipped at any depth. */
    val skippedDirs = setOf("build", ".git", ".gradle", "kotlin-js-store", "node_modules", ".kotlin")

    /** Local directories skipped only at these paths: the sample cache and scratch, agent worktrees, IDE state. */
    val skippedPaths = setOf("cache", "tmp", ".claude/worktrees", ".idea/artifacts")

    /** Records that keep the words of their day, each with its reason. */
    val historyPaths: List<Pair<String, String>> = listOf(
        "docs/tasks-archive/" to "archived task records",
        "docs/history/" to "dated history",
        "docs/funding/evidence/" to "verbatim maintainer quotes and answers",
        "docs/funding/scripts/build_quotes.py" to "builds the verbatim quotes",
        "docs/strategy/" to "dated strategist records",
        "docs/blog/2026-08-12-one-annotation-six-artifacts/" to "a post: history, quotes code of its day (Q5)",
        "docs/blog/2026-08-12-the-fundamental-lottery/" to "a post: history, quotes code of its day (Q5)",
        "docs/blog/2026-09-07-loop-shape-beats-pass-count/" to "a post: history, quotes code of its day (Q5)",
        "src/jsMain/resources/blog/one-annotation-six-artifacts/" to "the built copy of that post",
        "src/jsMain/resources/blog/the-fundamental-lottery/" to "the built copy of that post",
        "src/jsMain/resources/blog/loop-shape-beats-pass-count/" to "the built copy of that post",
        "src/jsMain/resources/klang-mission-log.html" to "a generated dated snapshot",
        "src/jsMain/resources/klang-topic-map.html" to "a generated dated snapshot",
        "DEV-DIARY.MD" to "the diary",
        ".claude/build-lock-log.md" to "a dated log",
        "docs/plans/ignitor-katalyst-naming.md" to "the plan of this rename names what it renamed",
        "src/jvmTest/kotlin/RetiredIgnitorNamesSpec.kt" to "this guard spells the names it guards",
    )

    fun isHistory(path: String): Boolean =
        path.endsWith("/ref/memory-history.md") || historyPaths.any { (prefix, _) -> path == prefix || path.startsWith(prefix) }

    /** Files where only the lines of one section may spell an old name: path to the section's heading. */
    val lineScopedFiles: Map<String, String> = mapOf(
        "CLAUDE.md" to "### Retired, do not restore or cite",
    )

    /** Dated rows inside a live file that keep their words: path to the rows' pattern. */
    val datedRows: Map<String, Regex> = mapOf(
        "docs/funding/design-decisions-skeleton.md" to Regex("""^\| R10 \| 2026-09-18 \|"""), // decision Q11
        ".claude/skills/review-loop/escape-ledger.md" to Regex("""^\| 2026-"""), // dated ledger rows
        ".claude/skills/agent-fleet/defect-density-ledger.md" to Regex("""^\| """), // ledger rows
        "docs/tasks/silent-shape-discard-on-error.md" to Regex("""^shape = x => """), // a measured probe transcript
    )

    fun allowedLines(path: String, lines: List<String>): Set<Int> {
        datedRows[path]?.let { row -> return lines.indices.filter { row.containsMatchIn(lines[it]) }.toSet() }
        val heading = lineScopedFiles[path] ?: return emptySet()
        val start = lines.indexOfFirst { it.trim() == heading }
        if (start < 0) {
            return emptySet()
        }
        val end = (start + 1 until lines.size).firstOrNull { lines[it].startsWith("## ") || lines[it].startsWith("### ") }
            ?: lines.size
        return (start until end).toSet()
    }

    /** Walks the live files once; the hits come back per pattern, in the order of [patterns]. */
    fun scan(patterns: List<Regex>): Pair<Set<String>, List<List<String>>> {
        val root = File(".").canonicalFile
        val seen = mutableSetOf<String>()
        val hits = patterns.map { mutableListOf<String>() }

        root.walkTopDown()
            .onEnter { dir ->
                dir == root || (dir.name !in skippedDirs && dir.relativeTo(root).invariantSeparatorsPath !in skippedPaths)
            }
            .filter { it.isFile && it.extension in extensions && it.name != "workspace.xml" }
            .forEach { file ->
                val path = file.relativeTo(root).invariantSeparatorsPath
                if (isHistory(path)) {
                    return@forEach
                }
                seen += path
                val lines = file.readLines()
                val allowed = allowedLines(path, lines)
                lines.forEachIndexed { i, line ->
                    if (i in allowed) {
                        return@forEachIndexed
                    }

                    patterns.forEachIndexed { p, pattern ->
                        if (pattern.containsMatchIn(line)) {
                            hits[p] += "$path:${i + 1}: ${line.trim().take(160)}"
                        }
                    }
                }
            }

        return seen to hits
    }

    val scanned by lazy { scan(listOf(retired, retiredTri)) }

    "no live file spells a name the Ignitor/Katalyst naming retired" {
        val (seen, perPattern) = scanned
        val hits = perPattern[0]

        withClue("the walk must actually see the repository (saw ${seen.size} live files; 1,921 on 2026-10-04)") {
            seen.size shouldBeGreaterThan 1800
        }
        // sentinels: a song, the whitepaper, a live task, a skill reference; a walk that lost a whole tree fails here
        listOf(
            "src/commonMain/kotlin/builtinsongs/DerSchmetterling.kt",
            "src/jsMain/resources/klang-whitepaper.html",
            "docs/tasks/_priorities.md",
            ".claude/skills/klang-music-writing/ref/ignitor-reference.md",
        ).forEach { sentinel -> withClue("the walk must see $sentinel") { (sentinel in seen) shouldBe true } }
        withClue("old names in live files; rename them (docs/plans/ignitor-katalyst-naming.md, section 2):\n" + hits.joinToString("\n")) {
            hits.shouldBeEmpty()
        }
    }

    "no live file spells the retired triangle door, builder, wire node or engine factory" {
        val (seen, perPattern) = scanned
        val hits = perPattern[1]

        withClue("the walk must actually see the repository (saw ${seen.size} live files)") {
            seen.size shouldBeGreaterThan 1800
        }
        withClue("the triangle door is `tri` since 2026-10-06; rename these:\n" + hits.joinToString("\n")) {
            hits.shouldBeEmpty()
        }
    }

    "the triangle pattern recognises the old door and leaves the sound name and the LFO shape alone" {
        val known = listOf(
            "Ign.triangle()", "Ignitor.triangle(x => x.analog(3))", "the `Ignitor.triangle` door", "OscTriangleBuilder",
            "IgnitorDsl.Triangle(phase = p)", "is IgnitorDsl.Triangle -> analog",
            "Ignitors.triangle(rate, analog)", "KlangScriptIgnitor.triangle(4.0)",
        )
        known.forEach { sample -> withClue(sample) { retiredTri.containsMatchIn(sample) shouldBe true } }

        val clean = listOf(
            "Ign.tri()", "Ignitor.tri(x => x.analog(3))", "OscTriBuilder", "IgnitorDsl.Tri(phase = p)", "Ignitors.tri(rate)",
            "@WireName(\"tri\")", "note(\"c3\").s(\"triangle\")", ".sound(\"triangle\")", "put(\"triangle\", tri)",
            "x.shape(\"triangle\")", "\"triangle\" -> Ignitors.tri(rate, analog)", "a triangle wave", "Ignitor.supertri()",
            "IgnitorDsl.SuperTri()", "sndTriangle()", "Triangle wave oscillator",
        )
        clean.forEach { sample -> withClue(sample) { retiredTri.containsMatchIn(sample) shouldBe false } }

        // the history allowlist is not dead for this pattern either: the archived task record spells the old door
        File("docs/tasks-archive/2026-10/20261006-oscillator-names-across-dsls.md").readText()
            .let { retiredTri.containsMatchIn(it) } shouldBe true
    }

    "the pattern still recognises the retired names it guards (a guard for the guard)" {
        val known = listOf(
            "Osc.sine()", "OscSlot.lpf.freq", "x.oscp(\"a\", 1)", "x.oscparam(\"a\", 1)", "data.oscParams",
            "x.oscParamsOrNew()", "maps these onto oscparams", "the **Osc** object", "withOscParams(...)",
            "Nfenv/Oscparam specs", "lang_synthesis_oscparam.kt", "an osc-param", "x._oscp(1)",
            "applyOscp(x)", "the OscSlots", "toOscSlot()",
        )
        known.forEach { sample -> withClue(sample) { retired.containsMatchIn(sample) shouldBe true } }

        // and it does not fire on real oscillators or on the new names
        val clean = listOf(
            "OscSineBuilder", "the Osc*Builder types", "oscillator", "OscillatorTuning", "Ign.sine()", "ignitorParams",
            "x.ignp(\"a\", 1)", "StdLibIgnitorTest", "oscA",
        )
        clean.forEach { sample -> withClue(sample) { retired.containsMatchIn(sample) shouldBe false } }

        // the history allowlist is not dead: the plan of this rename really spells the old names
        File("docs/plans/ignitor-katalyst-naming.md").readText().let { retired.containsMatchIn(it) } shouldBe true

        // the skip mechanism skips history and nothing else
        isHistory("docs/tasks-archive/2026-10/anything.md") shouldBe true
        isHistory("audio/ref/memory-history.md") shouldBe true
        isHistory("docs/tasks/anything.md") shouldBe false
        isHistory("docs/blog/howto-write-a-post.md") shouldBe false
        isHistory("docs/funding/gaps.md") shouldBe false
    }

    "the CLAUDE.md allowance covers the Retired section and ends at the next heading" {
        val lines = File("CLAUDE.md").readLines()
        val allowed = allowedLines("CLAUDE.md", lines)
        val start = lines.indexOfFirst { it.trim() == "### Retired, do not restore or cite" }
        val next = (start + 1 until lines.size).first { lines[it].startsWith("## ") || lines[it].startsWith("### ") }

        withClue("the heading exists") { (start >= 0) shouldBe true }
        withClue("the section is allowed") { allowed.contains(start + 1) shouldBe true }
        withClue("the allowance stops before the next heading") { allowed.contains(next) shouldBe false }
        withClue("and covers nothing after it") { allowed.none { it >= next } shouldBe true }
    }

    "the funding skeleton allows its dated row R10 and nothing else" {
        val path = "docs/funding/design-decisions-skeleton.md"
        val lines = File(path).readLines()
        val allowed = allowedLines(path, lines)

        allowed.size shouldBe 1
        lines[allowed.single()].startsWith("| R10 |") shouldBe true
    }
})
