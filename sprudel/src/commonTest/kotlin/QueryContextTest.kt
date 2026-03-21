package io.peekandpoke.klang.strudel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class QueryContextTest : StringSpec({

    "empty context has no keys" {
        val ctx = StrudelPattern.QueryContext()
        val key = StrudelPattern.QueryContext.Key<String>("test")

        ctx.has(key) shouldBe false
        ctx.getOrNull(key) shouldBe null
    }

    "can store and retrieve values by key" {
        val key1 = StrudelPattern.QueryContext.Key<String>("key1")
        val key2 = StrudelPattern.QueryContext.Key<Int>("key2")

        val ctx = StrudelPattern.QueryContext()
            .update { set(key1, "value1") }
            .update { set(key2, 42) }

        ctx.has(key1) shouldBe true
        ctx.has(key2) shouldBe true
        ctx.getOrNull(key1) shouldBe "value1"
        ctx.getOrNull(key2) shouldBe 42
    }

    "get throws error when key not found" {
        val ctx = StrudelPattern.QueryContext()
        val key = StrudelPattern.QueryContext.Key<String>("missing")

        val result = runCatching { ctx.get(key) }
        result.isFailure shouldBe true
    }

    "getOrDefault returns default when key not found" {
        val ctx = StrudelPattern.QueryContext()
        val key = StrudelPattern.QueryContext.Key<String>("missing")

        ctx.getOrDefault(key, "default") shouldBe "default"
    }

    "getOrDefault returns value when key exists" {
        val key = StrudelPattern.QueryContext.Key<String>("key")
        val ctx = StrudelPattern.QueryContext()
            .update { set(key, "value") }

        ctx.getOrDefault(key, "default") shouldBe "value"
    }

    "update with set creates new context" {
        val key = StrudelPattern.QueryContext.Key<String>("key")
        val original = StrudelPattern.QueryContext()
        val updated = original.update { set(key, "value") }

        original shouldNotBe updated
        original.has(key) shouldBe false
        updated.has(key) shouldBe true
    }

    "update without changes returns same context" {
        val key = StrudelPattern.QueryContext.Key<String>("key")
        val original = StrudelPattern.QueryContext()
            .update { set(key, "value") }

        val updated = original.update { set(key, "value") }

        original shouldBe updated
    }

    "update with same value returns same context (copy-on-write optimization)" {
        val key = StrudelPattern.QueryContext.Key<Int>("key")
        val original = StrudelPattern.QueryContext()
            .update { set(key, 42) }

        val updated = original.update { set(key, 42) }

        original shouldBe updated
    }

    "update with different value creates new context" {
        val key = StrudelPattern.QueryContext.Key<Int>("key")
        val original = StrudelPattern.QueryContext()
            .update { set(key, 42) }

        val updated = original.update { set(key, 100) }

        original shouldNotBe updated
        original.getOrNull(key) shouldBe 42
        updated.getOrNull(key) shouldBe 100
    }

    "setIfAbsent sets value when key is absent" {
        val key = StrudelPattern.QueryContext.Key<String>("key")
        val ctx = StrudelPattern.QueryContext()
            .update { setIfAbsent(key, "value") }

        ctx.getOrNull(key) shouldBe "value"
    }

    "setIfAbsent does not set value when key exists" {
        val key = StrudelPattern.QueryContext.Key<String>("key")
        val ctx = StrudelPattern.QueryContext()
            .update { set(key, "first") }
            .update { setIfAbsent(key, "second") }

        ctx.getOrNull(key) shouldBe "first"
    }

    "setIfAbsent returns same context when key exists (copy-on-write)" {
        val key = StrudelPattern.QueryContext.Key<String>("key")
        val original = StrudelPattern.QueryContext()
            .update { set(key, "value") }

        val updated = original.update { setIfAbsent(key, "other") }

        original shouldBe updated
    }

    "setWhen sets value when condition is true" {
        val key = StrudelPattern.QueryContext.Key<String>("key")
        val ctx = StrudelPattern.QueryContext()
            .update { setWhen(key, "value") { true } }

        ctx.getOrNull(key) shouldBe "value"
    }

    "setWhen does not set value when condition is false" {
        val key = StrudelPattern.QueryContext.Key<String>("key")
        val ctx = StrudelPattern.QueryContext()
            .update { setWhen(key, "value") { false } }

        ctx.has(key) shouldBe false
    }

    "setWhen returns same context when condition is false (copy-on-write)" {
        val key = StrudelPattern.QueryContext.Key<String>("key")
        val original = StrudelPattern.QueryContext()

        val updated = original.update { setWhen(key, "value") { false } }

        original shouldBe updated
    }

    "setWhen can access current context in condition" {
        val key1 = StrudelPattern.QueryContext.Key<Int>("key1")
        val key2 = StrudelPattern.QueryContext.Key<String>("key2")

        val ctx = StrudelPattern.QueryContext()
            .update { set(key1, 42) }
            .update {
                setWhen(key2, "set") { it.getOrNull(key1) == 42 }
            }

        ctx.getOrNull(key2) shouldBe "set"
    }

    "remove removes the key" {
        val key = StrudelPattern.QueryContext.Key<String>("key")
        val ctx = StrudelPattern.QueryContext()
            .update { set(key, "value") }
            .update { remove(key) }

        ctx.has(key) shouldBe false
        ctx.getOrNull(key) shouldBe null
    }

    "remove creates new context when key exists" {
        val key = StrudelPattern.QueryContext.Key<String>("key")
        val original = StrudelPattern.QueryContext()
            .update { set(key, "value") }

        val updated = original.update { remove(key) }

        original shouldNotBe updated
        original.has(key) shouldBe true
        updated.has(key) shouldBe false
    }

    "remove returns same context when key does not exist (copy-on-write)" {
        val key = StrudelPattern.QueryContext.Key<String>("key")
        val original = StrudelPattern.QueryContext()

        val updated = original.update { remove(key) }

        original shouldBe updated
    }

    "multiple updates in single block - only one copy when needed" {
        val key1 = StrudelPattern.QueryContext.Key<String>("key1")
        val key2 = StrudelPattern.QueryContext.Key<String>("key2")
        val original = StrudelPattern.QueryContext()

        val updated = original.update {
            set(key1, "value1")
            set(key2, "value2")
        }

        original shouldNotBe updated
        updated.getOrNull(key1) shouldBe "value1"
        updated.getOrNull(key2) shouldBe "value2"
    }

    "multiple updates with no actual changes returns same context" {
        val key1 = StrudelPattern.QueryContext.Key<String>("key1")
        val key2 = StrudelPattern.QueryContext.Key<String>("key2")
        val original = StrudelPattern.QueryContext()
            .update {
                set(key1, "value1")
                set(key2, "value2")
            }

        val updated = original.update {
            set(key1, "value1")
            set(key2, "value2")
        }

        original shouldBe updated
    }

    "mixed updates: some change, some don't - creates new context only once" {
        val key1 = StrudelPattern.QueryContext.Key<String>("key1")
        val key2 = StrudelPattern.QueryContext.Key<String>("key2")
        val key3 = StrudelPattern.QueryContext.Key<String>("key3")

        val original = StrudelPattern.QueryContext()
            .update {
                set(key1, "v1")
                set(key2, "v2")
            }

        val updated = original.update {
            set(key1, "v1") // no change
            set(key2, "v2_new") // change
            set(key3, "v3") // new key
        }

        original shouldNotBe updated
        original.getOrNull(key2) shouldBe "v2"
        updated.getOrNull(key2) shouldBe "v2_new"
        updated.getOrNull(key3) shouldBe "v3"
    }

    "context initialized with data" {
        val key1 = StrudelPattern.QueryContext.Key<String>("key1")
        val key2 = StrudelPattern.QueryContext.Key<Int>("key2")

        val ctx = StrudelPattern.QueryContext(
            mapOf(
                key1 to "value",
                key2 to 42
            )
        )

        ctx.getOrNull(key1) shouldBe "value"
        ctx.getOrNull(key2) shouldBe 42
    }

    "different key instances with same name are different keys" {
        val key1a = StrudelPattern.QueryContext.Key<String>("key")
        val key1b = StrudelPattern.QueryContext.Key<String>("key")

        val ctx = StrudelPattern.QueryContext()
            .update { set(key1a, "value1") }
            .update { set(key1b, "value2") }

        // Keys are equal by data class equality
        key1a shouldBe key1b
        ctx.getOrNull(key1a) shouldBe "value2"
        ctx.getOrNull(key1b) shouldBe "value2"
    }

    "can store null values" {
        val key = StrudelPattern.QueryContext.Key<String?>("key")
        val ctx = StrudelPattern.QueryContext()
            .update { set(key, null) }

        ctx.has(key) shouldBe true
        ctx.getOrNull(key) shouldBe null
    }

    "setting null value is different from not having key" {
        val key = StrudelPattern.QueryContext.Key<String?>("key")
        val original = StrudelPattern.QueryContext()

        val withNull = original.update { set(key, null) }

        original shouldNotBe withNull
        original.has(key) shouldBe false
        withNull.has(key) shouldBe true
    }

    "changing from null to value creates new context" {
        val key = StrudelPattern.QueryContext.Key<String?>("key")
        val original = StrudelPattern.QueryContext()
            .update { set(key, null) }

        val updated = original.update { set(key, "value") }

        original shouldNotBe updated
        original.getOrNull(key) shouldBe null
        updated.getOrNull(key) shouldBe "value"
    }

    "changing from value to null creates new context" {
        val key = StrudelPattern.QueryContext.Key<String?>("key")
        val original = StrudelPattern.QueryContext()
            .update { set(key, "value") }

        val updated = original.update { set(key, null) }

        original shouldNotBe updated
        original.getOrNull(key) shouldBe "value"
        updated.getOrNull(key) shouldBe null
    }

    "complex scenario: chaining multiple updates" {
        val depth = StrudelPattern.QueryContext.Key<Int>("depth")
        val name = StrudelPattern.QueryContext.Key<String>("name")
        val flag = StrudelPattern.QueryContext.Key<Boolean>("flag")

        val ctx0 = StrudelPattern.QueryContext()
        val ctx1 = ctx0.update { set(depth, 0) }
        val ctx2 = ctx1.update { set(depth, 1) }
        val ctx3 = ctx2.update {
            set(depth, 2)
            setIfAbsent(name, "pattern")
        }
        val ctx4 = ctx3.update {
            setWhen(flag, true) { it.getOrNull(depth)!! > 1 }
        }

        ctx0.has(depth) shouldBe false
        ctx1.getOrNull(depth) shouldBe 0
        ctx2.getOrNull(depth) shouldBe 1
        ctx3.getOrNull(depth) shouldBe 2
        ctx3.getOrNull(name) shouldBe "pattern"
        ctx4.getOrNull(flag) shouldBe true
    }
})
