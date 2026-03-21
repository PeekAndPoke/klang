package io.peekandpoke.klang.sprudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.SprudelVoiceData
import io.peekandpoke.klang.sprudel.TimeSpan
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.pattern.ContextModifierPattern.Companion.withContext

class ContextModifierPatternSpec : StringSpec({

    val testKey = QueryContext.Key<String>("test_key")

    "ContextModifierPattern: Direct Instantiation" {
        // A pattern that returns its context's 'test_key' value as the note name
        val contextAwarePattern = object : SprudelPattern.FixedWeight {
            override val numSteps: Rational = Rational.ONE

            override fun queryArcContextual(
                from: Rational,
                to: Rational,
                ctx: QueryContext,
            ): List<SprudelPatternEvent> {
                val testVal = ctx.getOrNull(testKey) ?: "none"
                return listOf(
                    SprudelPatternEvent(
                        part = TimeSpan(from, to),
                        whole = TimeSpan(from, to),
                        data = SprudelVoiceData.empty.copy(note = testVal)
                    )
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

        val inspector = object : SprudelPattern.FixedWeight {
            override val numSteps: Rational = Rational.ONE

            override fun queryArcContextual(
                from: Rational,
                to: Rational,
                ctx: QueryContext,
            ): List<SprudelPatternEvent> {
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
