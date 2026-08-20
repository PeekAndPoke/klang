/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.seq
import io.peekandpoke.klang.sprudel.lang.stack
import io.peekandpoke.klang.sprudel.lang.superimpose

class LangTagSpec : StringSpec({

    "tag dsl interface" {
        val pat = "a b"

        // The chained rows use the SAME tag twice on purpose: form (d) must chain AND dedup.
        dslInterfaceTests(
            "pattern.tag(name)" to seq(pat).tag("drums"),
            "script pattern.tag(name)" to SprudelPattern.compile("""seq("$pat").tag("drums")"""),
            "string.tag(name)" to pat.tag("drums"),
            "script string.tag(name)" to SprudelPattern.compile(""""$pat".tag("drums")"""),
            "tag(name)" to seq(pat).apply(tag("drums")),
            "script tag(name)" to SprudelPattern.compile("""seq("$pat").apply(tag("drums"))"""),
            "chained tag(name)" to seq(pat).apply(tag("drums").tag("drums")),
            "script chained tag(name)" to SprudelPattern.compile("""seq("$pat").apply(tag("drums").tag("drums"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.forEach { it.data.tags shouldBe setOf("drums") }
        }
    }

    "tag() accumulates across chained calls" {
        val events = seq("a b").tag("first").tag("second").queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events.forEach { it.data.tags shouldBe setOf("first", "second") }
        }
    }

    "tag() accumulates across chained calls in compiled code" {
        val p = SprudelPattern.compile("""seq("a b").tag("first").tag("second")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        assertSoftly {
            events.size shouldBe 2
            events.forEach { it.data.tags shouldBe setOf("first", "second") }
        }
    }

    "tag() deduplicates repeated tags" {
        val events = seq("a").tag("x").tag("x").queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 1
            events[0].data.tags shouldBe setOf("x")
        }
    }

    "tags accumulate across nesting - outer stack tag joins inner tags" {
        val p = stack(
            seq("a").tag("guitar1"),
            seq("b"),
        ).tag("band")

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events.first { it.data.value?.asString == "a" }.data.tags shouldBe setOf("guitar1", "band")
            events.first { it.data.value?.asString == "b" }.data.tags shouldBe setOf("band")
        }
    }

    "superimpose copies inherit tags and can add their own" {
        val p = note("c3").tag("base").superimpose(tag("echo"))

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events.map { it.data.tags } shouldContainExactlyInAnyOrder listOf(
                setOf("base"),
                setOf("base", "echo"),
            )
        }
    }

    "tags union through merge() control overlay" {
        val ctrl = seq("1").tag("fromCtrl")
        val events = seq("a").tag("base").merge(ctrl).queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 1
            events[0].data.tags shouldBe setOf("base", "fromCtrl")
        }
    }

    "tag name is literal - not parsed as mini-notation" {
        val events = seq("a").tag("guitar 1").queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 1
            events[0].data.tags shouldBe setOf("guitar 1")
        }
    }

    "tags reach the engine VoiceData" {
        val events = note("c3").tag("viz").queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 1
            events[0].data.toVoiceData().tags shouldBe setOf("viz")
        }
    }

    "untagged patterns carry null tags - no empty set materialized" {
        val events = note("c3").queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 1
            events[0].data.tags shouldBe null
            events[0].data.toVoiceData().tags shouldBe null
        }
    }
})
