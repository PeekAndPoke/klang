package io.peekandpoke.klang.audio_bridge.infra

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KlangAtomicIntTest : StringSpec({

    "get and set should work correctly" {
        val atomic = KlangAtomicInt(42)
        atomic.get() shouldBe 42

        atomic.set(100)
        atomic.get() shouldBe 100
    }

    "incrementAndGet should increment and return new value" {
        val atomic = KlangAtomicInt(10)

        atomic.incrementAndGet() shouldBe 11
        atomic.get() shouldBe 11

        atomic.incrementAndGet() shouldBe 12
        atomic.get() shouldBe 12
    }

    "decrementAndGet should decrement and return new value" {
        val atomic = KlangAtomicInt(10)

        atomic.decrementAndGet() shouldBe 9
        atomic.get() shouldBe 9

        atomic.decrementAndGet() shouldBe 8
        atomic.get() shouldBe 8
    }

    "getAndIncrement should return old value then increment" {
        val atomic = KlangAtomicInt(10)

        atomic.getAndIncrement() shouldBe 10
        atomic.get() shouldBe 11

        atomic.getAndIncrement() shouldBe 11
        atomic.get() shouldBe 12
    }

    "getAndDecrement should return old value then decrement" {
        val atomic = KlangAtomicInt(10)

        atomic.getAndDecrement() shouldBe 10
        atomic.get() shouldBe 9

        atomic.getAndDecrement() shouldBe 9
        atomic.get() shouldBe 8
    }

    "should handle negative values" {
        val atomic = KlangAtomicInt(-5)

        atomic.get() shouldBe -5
        atomic.incrementAndGet() shouldBe -4
        atomic.decrementAndGet() shouldBe -5
    }

    "should handle zero" {
        val atomic = KlangAtomicInt(0)

        atomic.incrementAndGet() shouldBe 1
        atomic.decrementAndGet() shouldBe 0
        atomic.decrementAndGet() shouldBe -1
    }

    "multiple operations in sequence" {
        val atomic = KlangAtomicInt(0)

        atomic.getAndIncrement() shouldBe 0
        atomic.getAndIncrement() shouldBe 1
        atomic.getAndIncrement() shouldBe 2
        atomic.get() shouldBe 3

        atomic.decrementAndGet() shouldBe 2
        atomic.decrementAndGet() shouldBe 1
        atomic.get() shouldBe 1
    }
})
