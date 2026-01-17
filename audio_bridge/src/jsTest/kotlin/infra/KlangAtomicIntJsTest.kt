package io.peekandpoke.klang.audio_bridge.infra

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KlangAtomicIntJsTest : StringSpec({

    "sequential operations should work correctly in JS" {
        val atomic = KlangAtomicInt(0)

        // Simulate rapid sequential operations
        repeat(1000) {
            atomic.incrementAndGet()
        }

        atomic.get() shouldBe 1000
    }

    "rapid set operations" {
        val atomic = KlangAtomicInt(0)

        repeat(100) { i ->
            atomic.set(i)
            atomic.get() shouldBe i
        }
    }

    "interleaved increment and decrement operations" {
        val atomic = KlangAtomicInt(0)

        repeat(500) {
            atomic.incrementAndGet()
            atomic.decrementAndGet()
        }

        atomic.get() shouldBe 0
    }

    "large number of operations" {
        val atomic = KlangAtomicInt(0)
        val operations = 10000

        repeat(operations / 2) {
            atomic.getAndIncrement()
        }
        repeat(operations / 2) {
            atomic.incrementAndGet()
        }

        atomic.get() shouldBe operations
    }

    "boundary values" {
        val atomic = KlangAtomicInt(Int.MAX_VALUE - 1)

        atomic.incrementAndGet() shouldBe Int.MAX_VALUE
        atomic.getAndIncrement() shouldBe Int.MAX_VALUE
        // Note: Overflow behavior is platform-dependent
    }
})
