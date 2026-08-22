/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptError
import io.peekandpoke.klang.sprudel.SprudelDiagnostic
import io.peekandpoke.klang.sprudel.SprudelDiagnostics
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.sprudelLib

/**
 * The swallowed-error channel.
 *
 * A shape function that throws is caught on purpose (a bad edit mid-performance must not kill the
 * audio), which used to make the mistake invisible: `.ocsp` for `.oscp` discarded an entire shape
 * and left only a console stack trace. These rows pin that the error is now CAPTURED with its
 * source location intact, and that the audio still survives.
 */
class SprudelDiagnosticsTest : StringSpec({

    fun engine() = klangScript {
        registerLibrary(sprudelLib)
        registerBuiltInSongsAsModules()
    }

    fun compileAndQuery(src: String, into: MutableList<SprudelDiagnostic>): Int =
        SprudelDiagnostics.collectingInto(into) {
            val p = SprudelPattern.compile(engine(), src)!!
            p.queryArc(0.0, 1.0).size
        }

    val broken = """
        import * from "stdlib"
        import * from "sprudel"
        let shape = x => x.gain(0.25).sound("saw").ocsp("midsHz", 1500)
        export song = n("c3").apply(shape)
    """.trimIndent()

    val good = """
        import * from "stdlib"
        import * from "sprudel"
        let shape = x => x.gain(0.25).sound("saw").oscp("midsHz", 1500)
        export song = n("c3").apply(shape)
    """.trimIndent()

    "a typo in a shape is CAPTURED instead of vanishing into the console" {
        val found = mutableListOf<SprudelDiagnostic>()
        compileAndQuery(broken, found)

        found.size shouldBe 1
        found.single().error.message.shouldNotBeNull() shouldContain "has no method 'ocsp'"
    }

    "the captured error still carries its SOURCE LOCATION" {
        // This is the whole point: the location is what the editor turns into a clickable
        // marker, and flattening the error to a string is exactly what used to lose it.
        val found = mutableListOf<SprudelDiagnostic>()
        compileAndQuery(broken, found)

        val loc = (found.single().error as KlangScriptError).location.shouldNotBeNull()
        loc.startLine shouldBe 3
    }

    "the suggestion survives into the diagnostic" {
        val found = mutableListOf<SprudelDiagnostic>()
        compileAndQuery(broken, found)

        found.single().error.message.shouldNotBeNull() shouldContain "Did you mean 'oscp'?"
    }

    "the audio survives — the pattern still produces events" {
        // The catch exists for live-coding resilience. Capturing must not change that.
        val found = mutableListOf<SprudelDiagnostic>()
        val events = compileAndQuery(broken, found)

        events shouldBe 1
    }

    "for a build-time transform, wrapping ONLY compile is enough" {
        // The editor wiring wraps SprudelPattern.compile and nothing else. For `.apply(shape)`
        // the transform runs at BUILD time, so that is sufficient. It is NOT sufficient in
        // general — see the row below, which pins the known gap.
        val atCompile = mutableListOf<SprudelDiagnostic>()
        val pattern = SprudelDiagnostics.collectingInto(atCompile) {
            SprudelPattern.compile(engine(), broken)!!
        }
        atCompile.size shouldBe 1

        // ...and querying afterwards, OUTSIDE any collector, adds nothing new.
        val atQuery = mutableListOf<SprudelDiagnostic>()
        SprudelDiagnostics.collectingInto(atQuery) {
            repeat(5) { pattern.queryArc(it.toDouble(), it + 1.0) }
        }
        atQuery.shouldBeEmpty()
    }

    "KNOWN GAP: a transform applied at QUERY time is not captured by the compile wrapper" {
        // sometimesBy routes through _innerJoin -> BindPattern, whose lambda runs per QUERY, so
        // the swallow happens after compile has returned. The frontend therefore shows no
        // marker for this shape and the user still only gets a console trace — the original bug,
        // unfixed, for this family of transforms.
        //
        // Pinned deliberately rather than left as a comment, but be precise about WHAT it
        // pins: that sometimesBy's mapper resolves per QUERY, not at build. It is NOT an
        // acceptance criterion for the fix — both options in the task doc (a warm-up query in
        // KlangCodePlaybackCtrl, or a collector around the player's query batches) live
        // outside SprudelPattern.compile, so implementing either leaves this row exactly as
        // it is, green. Verifying the fix needs a test against the frontend wiring.
        val src = """
            import * from "stdlib"
            import * from "sprudel"
            export song = s("hh*4").sometimesBy(0.9, x => x.ocsp("midsHz", 1500))
        """.trimIndent()

        val atCompile = mutableListOf<SprudelDiagnostic>()
        val pattern = SprudelDiagnostics.collectingInto(atCompile) {
            SprudelPattern.compile(engine(), src)!!
        }
        // Nothing at compile time...
        atCompile.shouldBeEmpty()

        // ...but the failure is real, and only visible if the QUERY is wrapped too.
        val atQuery = mutableListOf<SprudelDiagnostic>()
        SprudelDiagnostics.collectingInto(atQuery) {
            pattern.queryArc(0.0, 1.0)
        }
        atQuery.size shouldBe 1
    }

    "a correct script produces NO diagnostics" {
        val found = mutableListOf<SprudelDiagnostic>()
        compileAndQuery(good, found)

        found.shouldBeEmpty()
    }

    "repeated identical failures are de-duplicated" {
        val found = mutableListOf<SprudelDiagnostic>()
        SprudelDiagnostics.collectingInto(found) {
            repeat(20) {
                val p = SprudelPattern.compile(engine(), broken)!!
                p.queryArc(0.0, 1.0)
            }
        }

        found.size shouldBe 1
    }

    "the collector is restored after the block, so nothing leaks between collections" {
        val first = mutableListOf<SprudelDiagnostic>()
        compileAndQuery(broken, first)

        val second = mutableListOf<SprudelDiagnostic>()
        // A collection that runs CLEAN code must not pick up the previous run's diagnostics.
        compileAndQuery(good, second)

        first.size shouldBe 1
        second.shouldBeEmpty()
    }

    "the collector is restored even when the block throws" {
        val found = mutableListOf<SprudelDiagnostic>()
        runCatching {
            SprudelDiagnostics.collectingInto(found) { error("boom") }
        }

        // With no collector installed, reporting must fall back to printing rather than
        // appending to a list that is no longer being collected into.
        SprudelDiagnostics.report("after", IllegalStateException("stray"))
        found.shouldBeEmpty()
    }
})
