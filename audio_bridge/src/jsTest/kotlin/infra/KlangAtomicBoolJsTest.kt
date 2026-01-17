package io.peekandpoke.klang.audio_bridge.infra

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KlangAtomicBoolJsTest : StringSpec({

    "sequential compareAndSet operations" {
        val atomic = KlangAtomicBool(false)

        // Simulate state machine transitions
        repeat(100) {
            atomic.compareAndSet(false, true) shouldBe true
            atomic.get() shouldBe true

            atomic.compareAndSet(true, false) shouldBe true
            atomic.get() shouldBe false
        }
    }

    "rapid toggle using getAndSet" {
        val atomic = KlangAtomicBool(false)

        repeat(1000) { i ->
            val expected = i % 2 == 0
            atomic.getAndSet(!expected)
            atomic.get() shouldBe !expected
        }
    }

    "state machine simulation" {
        val atomic = KlangAtomicBool(false)
        var transitionCount = 0

        repeat(500) {
            // Try to transition from stopped to running
            if (atomic.compareAndSet(false, true)) {
                transitionCount++
            }

            // Try to transition from running to stopped
            if (atomic.compareAndSet(true, false)) {
                transitionCount++
            }
        }

        // Should have 1000 successful transitions (500 * 2)
        transitionCount shouldBe 1000
        atomic.get() shouldBe false
    }

    "compareAndSet with repeated attempts" {
        val atomic = KlangAtomicBool(false)

        // First attempt succeeds
        atomic.compareAndSet(false, true) shouldBe true

        // Repeated attempts with wrong expect value fail
        repeat(100) {
            atomic.compareAndSet(false, true) shouldBe false
            atomic.get() shouldBe true
        }

        // Correct expect value succeeds
        atomic.compareAndSet(true, false) shouldBe true
        atomic.get() shouldBe false
    }

    "mixed operations sequence" {
        val atomic = KlangAtomicBool(false)

        atomic.set(true)
        atomic.get() shouldBe true

        atomic.getAndSet(false) shouldBe true
        atomic.get() shouldBe false

        atomic.compareAndSet(false, true) shouldBe true
        atomic.get() shouldBe true

        atomic.compareAndSet(false, false) shouldBe false
        atomic.get() shouldBe true
    }

    "idempotent operations" {
        val atomic = KlangAtomicBool(false)

        // Setting to the same value multiple times
        repeat(100) {
            atomic.set(false)
            atomic.get() shouldBe false
        }

        repeat(100) {
            atomic.set(true)
            atomic.get() shouldBe true
        }
    }
})
