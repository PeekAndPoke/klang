package io.peekandpoke.klang.strudel.lang.addons

import de.peekandpoke.ultra.common.datetime.Kronos
import de.peekandpoke.ultra.common.datetime.MpInstant
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.math.Rational

class LangTimeOfDaySpec : StringSpec({

    /**
     * Helper to create a fixed Kronos time source for testing
     */
    fun fixedKronos(hour: Int, minute: Int = 0, second: Int = 0): Kronos {
        return Kronos.fixed(
            MpInstant.fromEpochMillis(
                // Create a timestamp for the given time (using Jan 1, 2024 as base date)
                1704067200000L + // 2024-01-01 00:00:00 UTC
                        (hour * 3600L + minute * 60L + second) * 1000L
            )
        )
    }

    "timeOfDay at midnight should return 0.0" {
        val ctx = QueryContext {
            set(QueryContext.kronosKey, fixedKronos(0, 0, 0))
        }

        val events = timeOfDay.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe 0.0
    }

    "timeOfDay at noon should return 0.5" {
        val ctx = QueryContext {
            set(QueryContext.kronosKey, fixedKronos(12, 0, 0))
        }

        val events = timeOfDay.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe 0.5
    }

    "timeOfDay at 6 AM should return 0.25" {
        val ctx = QueryContext {
            set(QueryContext.kronosKey, fixedKronos(6, 0, 0))
        }

        val events = timeOfDay.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe 0.25
    }

    "timeOfDay at 6 PM should return 0.75" {
        val ctx = QueryContext {
            set(QueryContext.kronosKey, fixedKronos(18, 0, 0))
        }

        val events = timeOfDay.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe 0.75
    }

    "sinOfDay at midnight should return 0.0" {
        val ctx = QueryContext {
            set(QueryContext.kronosKey, fixedKronos(0, 0, 0))
        }

        val events = sinOfDay.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe (0.0 plusOrMinus 0.01)
    }

    "sinOfDay at noon should return 1.0" {
        val ctx = QueryContext {
            set(QueryContext.kronosKey, fixedKronos(12, 0, 0))
        }

        val events = sinOfDay.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        events.size shouldBe 1
        // sin(0.5 * PI) = 1.0
        events[0].data.value?.asDouble shouldBe (1.0 plusOrMinus 0.01)
    }

    "sinOfDay2 at midnight should return -1.0" {
        val ctx = QueryContext {
            set(QueryContext.kronosKey, fixedKronos(0, 0, 0))
        }

        val events = sinOfDay2.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        events.size shouldBe 1
        // sin(0 * PI) * 2 - 1 = 0 * 2 - 1 = -1
        events[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus 0.01)
    }

    "sinOfDay2 at noon should return 1.0" {
        val ctx = QueryContext {
            set(QueryContext.kronosKey, fixedKronos(12, 0, 0))
        }

        val events = sinOfDay2.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        events.size shouldBe 1
        // sin(0.5 * PI) * 2 - 1 = 1 * 2 - 1 = 1
        events[0].data.value?.asDouble shouldBe (1.0 plusOrMinus 0.01)
    }

    "timeOfNight at midnight should return 1.0" {
        val ctx = QueryContext {
            set(QueryContext.kronosKey, fixedKronos(0, 0, 0))
        }

        val events = timeOfNight.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe 1.0
    }

    "timeOfNight at noon should return 0.5" {
        val ctx = QueryContext {
            set(QueryContext.kronosKey, fixedKronos(12, 0, 0))
        }

        val events = timeOfNight.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe 0.5
    }

    "sinOfNight at midnight should return 1.0" {
        val ctx = QueryContext {
            set(QueryContext.kronosKey, fixedKronos(0, 0, 0))
        }

        val events = sinOfNight.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        events.size shouldBe 1
        // 1.0 - sin(0 * PI) = 1.0 - 0 = 1.0
        events[0].data.value?.asDouble shouldBe (1.0 plusOrMinus 0.01)
    }

    "sinOfNight at noon should return 0.0" {
        val ctx = QueryContext {
            set(QueryContext.kronosKey, fixedKronos(12, 0, 0))
        }

        val events = sinOfNight.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        events.size shouldBe 1
        // 1.0 - sin(0.5 * PI) = 1.0 - 1.0 = 0.0
        events[0].data.value?.asDouble shouldBe (0.0 plusOrMinus 0.01)
    }

    "sinOfNight2 at midnight should return 1.0" {
        val ctx = QueryContext {
            set(QueryContext.kronosKey, fixedKronos(0, 0, 0))
        }

        val events = sinOfNight2.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        events.size shouldBe 1
        // 1.0 - sin(0 * PI) * 2 = 1.0 - 0 * 2 = 1.0
        events[0].data.value?.asDouble shouldBe (1.0 plusOrMinus 0.01)
    }

    "sinOfNight2 at noon should return -1.0" {
        val ctx = QueryContext {
            set(QueryContext.kronosKey, fixedKronos(12, 0, 0))
        }

        val events = sinOfNight2.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        events.size shouldBe 1
        // 1.0 - sin(0.5 * PI) * 2 = 1.0 - 1.0 * 2 = -1.0
        events[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus 0.01)
    }

    "timeOfDay should use system time when no Kronos is set in context" {
        val ctx = QueryContext.empty

        val events = timeOfDay.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        events.size shouldBe 1
        // Should return a value between 0.0 and 1.0 (can't test exact value, just range)
        val value = events[0].data.value?.asDouble!!
        value shouldBeGreaterThanOrEqual 0.0
        value shouldBeLessThanOrEqual 1.0
    }

    "timeOfDay should handle minutes and seconds correctly" {
        val ctx = QueryContext {
            set(QueryContext.kronosKey, fixedKronos(12, 30, 0)) // 12:30:00
        }

        val events = timeOfDay.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        events.size shouldBe 1
        // 12.5 hours / 24 = 0.520833...
        val expected = 12.5 / 24.0
        events[0].data.value?.asDouble shouldBe (expected plusOrMinus 0.01)
    }
})
