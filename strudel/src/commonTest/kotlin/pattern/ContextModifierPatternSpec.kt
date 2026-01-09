package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.StrudelPatternEvent
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.pattern.ContextModifierPattern.Companion.withContext

class ContextModifierPatternSpec : StringSpec({

    val testKey = QueryContext.Key<String>("test_key")

    "ContextModifierPattern: Direct Instantiation" {
        // A pattern that returns its context's 'test_key' value as the note name
        val contextAwarePattern = object : StrudelPattern.FixedWeight {
            override fun queryArcContextual(
                from: Rational,
                to: Rational,
                ctx: QueryContext,
            ): List<StrudelPatternEvent> {
                val testVal = ctx.getOrNull(testKey) ?: "none"
                return listOf(
                    StrudelPatternEvent(from, to, to - from, VoiceData.empty.copy(note = testVal))
                )
            }
        }

        val pattern = ContextModifierPattern(contextAwarePattern) {
            set(testKey, "updated_value")
        }

        val events = pattern.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBe "updated_value"
    }

    "ContextModifierPattern: Kotlin DSL (withContext)" {
        val base = note("a")
        val flagKey = QueryContext.Key<String>("my_flag")

        var capturedValue: String? = null

        val inspector = object : StrudelPattern.FixedWeight {
            override fun queryArcContextual(
                from: Rational,
                to: Rational,
                ctx: QueryContext,
            ): List<StrudelPatternEvent> {
                capturedValue = ctx.getOrNull(flagKey)
                return base.queryArcContextual(from, to, ctx)
            }
        }

        val pattern = inspector.withContext {
            set(flagKey, "active")
        }

        pattern.queryArc(0.0, 1.0)

        capturedValue shouldBe "active"
    }

    "ContextModifierPattern: Weight is preserved" {
        val base = note("a")
        val pattern = ContextModifierPattern(base) {
            set(testKey, "bar")
        }

        pattern.weight shouldBe base.weight
    }
})
