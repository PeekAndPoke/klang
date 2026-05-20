package io.peekandpoke.klang.common.infra

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class KlangSnapshotMapTest : StringSpec({

    "getOrPut allocates on miss and returns the cached value on hit" {
        val map = KlangSnapshotMap<String, Int>()
        var allocations = 0

        val first = map.getOrPut("a") { allocations++; 100 }
        val second = map.getOrPut("a") { allocations++; 200 }

        first shouldBe 100
        second shouldBe 100
        allocations shouldBe 1
    }

    "get returns null when the key is absent and the cached value when present" {
        val map = KlangSnapshotMap<String, Int>()
        map["missing"] shouldBe null
        map.getOrPut("present") { 42 }
        map["present"] shouldBe 42
    }

    "different keys get independent values" {
        val map = KlangSnapshotMap<String, Int>()
        map.getOrPut("a") { 1 } shouldBe 1
        map.getOrPut("b") { 2 } shouldBe 2
        map.getOrPut("c") { 3 } shouldBe 3
        map.size shouldBe 3
    }

    "snapshot() returns a stable copy that doesn't reflect later writes" {
        val map = KlangSnapshotMap<String, Int>()
        map.getOrPut("a") { 1 }
        val snap = map.snapshot()

        map.getOrPut("b") { 2 }
        snap shouldBe mapOf("a" to 1)
        map.size shouldBe 2
    }

    "clear() empties the map" {
        val map = KlangSnapshotMap<String, Int>()
        map.getOrPut("a") { 1 }
        map.getOrPut("b") { 2 }
        map.size shouldBe 2

        map.clear()
        map.size shouldBe 0
        map["a"] shouldBe null
    }

    "snapshot identity changes after writes (publication)" {
        val map = KlangSnapshotMap<String, Int>()
        val empty = map.snapshot()
        map.getOrPut("a") { 1 }
        val afterFirst = map.snapshot()
        map.getOrPut("b") { 2 }
        val afterSecond = map.snapshot()

        empty shouldNotBe afterFirst
        afterFirst shouldNotBe afterSecond
        empty shouldBe emptyMap()
        afterFirst shouldBe mapOf("a" to 1)
        afterSecond shouldBe mapOf("a" to 1, "b" to 2)
    }
})