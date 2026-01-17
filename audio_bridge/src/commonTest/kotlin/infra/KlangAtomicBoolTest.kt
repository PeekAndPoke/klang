package io.peekandpoke.klang.audio_bridge.infra

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KlangAtomicBoolTest : StringSpec({

    "get and set should work correctly" {
        val atomic = KlangAtomicBool(false)
        atomic.get() shouldBe false

        atomic.set(true)
        atomic.get() shouldBe true

        atomic.set(false)
        atomic.get() shouldBe false
    }

    "compareAndSet should succeed when value matches" {
        val atomic = KlangAtomicBool(false)

        atomic.compareAndSet(expect = false, update = true) shouldBe true
        atomic.get() shouldBe true
    }

    "compareAndSet should fail when value does not match" {
        val atomic = KlangAtomicBool(false)

        atomic.compareAndSet(expect = true, update = false) shouldBe false
        atomic.get() shouldBe false
    }

    "compareAndSet should be atomic" {
        val atomic = KlangAtomicBool(false)

        // First call succeeds
        atomic.compareAndSet(expect = false, update = true) shouldBe true
        atomic.get() shouldBe true

        // Second call with same expect value fails
        atomic.compareAndSet(expect = false, update = true) shouldBe false
        atomic.get() shouldBe true
    }

    "getAndSet should return old value and set new value" {
        val atomic = KlangAtomicBool(false)

        atomic.getAndSet(true) shouldBe false
        atomic.get() shouldBe true

        atomic.getAndSet(false) shouldBe true
        atomic.get() shouldBe false
    }

    "getAndSet with same value" {
        val atomic = KlangAtomicBool(true)

        atomic.getAndSet(true) shouldBe true
        atomic.get() shouldBe true
    }

    "multiple compareAndSet operations" {
        val atomic = KlangAtomicBool(false)

        // Flip from false to true
        atomic.compareAndSet(false, true) shouldBe true
        atomic.get() shouldBe true

        // Try to flip from false to true again (should fail)
        atomic.compareAndSet(false, true) shouldBe false
        atomic.get() shouldBe true

        // Flip from true to false
        atomic.compareAndSet(true, false) shouldBe true
        atomic.get() shouldBe false
    }

    "initialization with true" {
        val atomic = KlangAtomicBool(true)
        atomic.get() shouldBe true

        atomic.compareAndSet(true, false) shouldBe true
        atomic.get() shouldBe false
    }
})
