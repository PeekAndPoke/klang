/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.infra

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Pins [KlangMap]'s contract on both platforms, with Kotlin's own `LinkedHashMap` as the reference wherever the
 * contract says "as a `Map` / as `LinkedHashMap`": the same operations on both, the same answers.
 */
class KlangMapSpec : StringSpec({

    /** The same writes on a [KlangMap] and on a `LinkedHashMap`. */
    class Twin {
        val klang = KlangMap<Double>()
        val ref = LinkedHashMap<String, Double>()

        fun set(key: String, value: Double) {
            klang[key] = value
            ref[key] = value
        }
    }

    "an absent key reads null and is not contained" {
        val map = klangMapOf("a" to 1.5)

        map["b"] shouldBe null
        map.containsKey("b") shouldBe false
        map.containsKey("a") shouldBe true
        map.size shouldBe 1
        map.isEmpty() shouldBe false
        KlangMap<Double>().isEmpty() shouldBe true
        KlangMap<Double>().size shouldBe 0
    }

    "a stored value reads back, and an overwrite replaces it" {
        val map = KlangMap<Double>()

        map["a"] = 0.25
        map["a"] shouldBe 0.25

        map["a"] = 0.75
        map["a"] shouldBe 0.75
        map.size shouldBe 1
    }

    "iteration follows insertion order and an overwrite keeps its position, as LinkedHashMap" {
        val both = Twin()

        both.set("delay.wet", 0.3)
        both.set("reverb.size", 6.3)
        both.set("body.wet", 0.4)
        both.set("delay.wet", 0.5) // overwrite: stays first
        both.set("gain.gain", 0.8)

        both.klang.keys.toList() shouldBe listOf("delay.wet", "reverb.size", "body.wet", "gain.gain")
        both.klang.keys.toList() shouldBe both.ref.keys.toList()
        both.klang.values.toList() shouldBe both.ref.values.toList()
        both.klang.entries.map { it.key to it.value } shouldBe both.ref.entries.map { it.key to it.value }
    }

    "a copy is independent in both directions and keeps the order" {
        val original = klangMapOf("a" to 1.0, "b" to 2.0)
        val copy = original.copy()

        copy shouldBe original
        copy.keys.toList() shouldBe listOf("a", "b")

        copy["c"] = 3.0
        copy["a"] = 9.0
        original["b"] = 8.0

        original shouldBe mapOf("a" to 1.0, "b" to 8.0)
        copy shouldBe mapOf("a" to 9.0, "b" to 2.0, "c" to 3.0)
    }

    "equality with a LinkedHashMap and a HashMap holds in both directions, hashCode is Map's" {
        val klang = klangMapOf("delay.wet" to 0.3, "reverb.size" to 6.3, "gain.gain" to 1.0)
        val linked = linkedMapOf("delay.wet" to 0.3, "reverb.size" to 6.3, "gain.gain" to 1.0)
        val hashed = hashMapOf("gain.gain" to 1.0, "delay.wet" to 0.3, "reverb.size" to 6.3)

        (klang == linked) shouldBe true
        (linked == klang) shouldBe true
        (klang == hashed) shouldBe true
        (hashed == klang) shouldBe true
        (klang as Any == klang.copy()) shouldBe true
        klang.hashCode() shouldBe linked.hashCode()
        klang.hashCode() shouldBe hashed.hashCode()
    }

    "a different value, a missing key or an extra key is unequal, as for LinkedHashMap" {
        val klang = klangMapOf("a" to 1.0, "b" to 2.0)

        (klang == mapOf("a" to 1.0, "b" to 2.5)) shouldBe false
        (mapOf("a" to 1.0, "b" to 2.5) == klang) shouldBe false
        (klang == mapOf("a" to 1.0)) shouldBe false
        (klang == mapOf("a" to 1.0, "b" to 2.0, "c" to 3.0)) shouldBe false
        (klang == mapOf("a" to 1.0, "c" to 2.0)) shouldBe false
        (klang as Any == listOf(1.0, 2.0)) shouldBe false
    }

    "NaN and signed zeros compare as in a LinkedHashMap" {
        val klang = klangMapOf("wet" to Double.NaN, "zero" to -0.0)
        val ref = linkedMapOf("wet" to Double.NaN, "zero" to -0.0)
        val positive = linkedMapOf("wet" to Double.NaN, "zero" to 0.0)

        klang["wet"]!!.isNaN() shouldBe true
        (klang == ref) shouldBe (ref == linkedMapOf("wet" to Double.NaN, "zero" to -0.0))
        (klang == positive) shouldBe (ref == positive)
        (positive == klang) shouldBe (positive == ref)
        klang.hashCode() shouldBe ref.hashCode()
    }

    "containsValue answers as LinkedHashMap, NaN included" {
        val klang = klangMapOf("a" to 1.0, "b" to Double.NaN)
        val ref = linkedMapOf("a" to 1.0, "b" to Double.NaN)

        klang.containsValue(1.0) shouldBe true
        klang.containsValue(2.0) shouldBe false
        klang.containsValue(Double.NaN) shouldBe ref.containsValue(Double.NaN)
    }

    "the entries, keys and values views agree with LinkedHashMap's" {
        val klang = klangMapOf("x" to 1.0, "y" to 2.0, "z" to 3.0)
        val ref = linkedMapOf("x" to 1.0, "y" to 2.0, "z" to 3.0)

        klang.entries shouldBe ref.entries
        klang.keys shouldBe ref.keys
        klang.values.toList() shouldBe ref.values.toList()
        klang.entries.size shouldBe 3
        klang.keys.contains("y") shouldBe true
        klang.values.contains(3.0) shouldBe true
        klang.entries.first().hashCode() shouldBe ref.entries.first().hashCode()
        (klang.entries.first() == ref.entries.first()) shouldBe true
        (ref.entries.first() == klang.entries.first()) shouldBe true
        (klang.entries.first() as Any == ref.entries.last()) shouldBe false
        (klang.entries.first() as Any == linkedMapOf("x" to 9.0).entries.first()) shouldBe false
        (klang.entries.first() as Any == linkedMapOf("q" to 1.0).entries.first()) shouldBe false
        klang.entries.contains(ref.entries.last()) shouldBe true
    }

    "no reader can write through it: the map, its views and its entries refuse a write" {
        val map = klangMapOf("a" to 1.0, "b" to 2.0)

        // A cast to a mutable type fails (JS, and the JVM for the map itself) or the write throws (the JVM's views).
        @Suppress("UNCHECKED_CAST")
        val writes = listOf(
            runCatching { (map as MutableMap<String, Double>).remove("a") },
            runCatching { (map.keys as MutableSet<String>).remove("a") },
            runCatching { (map.values as MutableCollection<Double>).remove(1.0) },
            runCatching { (map.entries as MutableSet<Map.Entry<String, Double>>).clear() },
            runCatching { (map.entries.first() as MutableMap.MutableEntry<String, Double>).setValue(9.0) },
        )

        writes.map { it.isFailure } shouldBe listOf(true, true, true, true, true)
        map shouldBe mapOf("a" to 1.0, "b" to 2.0)
    }

    "an exhausted iterator throws NoSuchElementException" {
        val iterator = klangMapOf("a" to 1.0).entries.iterator()

        iterator.next()
        iterator.hasNext() shouldBe false

        val thrown = runCatching { iterator.next() }.exceptionOrNull()

        (thrown is NoSuchElementException) shouldBe true
    }

    "toString prints as LinkedHashMap does" {
        klangMapOf("a" to 1.5, "b" to 2.0).toString() shouldBe linkedMapOf("a" to 1.5, "b" to 2.0).toString()
        KlangMap<Double>().toString() shouldBe linkedMapOf<String, Double>().toString()
    }

    "setAll and klangMapOf keep the source's order and overwrite in place" {
        val map = klangMapOf("b" to 2.0, "a" to 1.0, "b" to 3.0)

        map.keys.toList() shouldBe listOf("b", "a")
        map["b"] shouldBe 3.0

        map.setAll(linkedMapOf("c" to 4.0, "a" to 5.0))

        map.keys.toList() shouldBe listOf("b", "a", "c")
        map shouldBe mapOf("b" to 3.0, "a" to 5.0, "c" to 4.0)
        klangMapOf(linkedMapOf("q" to 1.0, "p" to 2.0)).keys.toList() shouldBe listOf("q", "p")
    }

    "forEachEntry visits every entry once, in insertion order, as the entries view does" {
        val map = klangMapOf("delay.wet" to 0.3, "reverb.size" to 6.3, "gain.gain" to 1.0)

        map["delay.wet"] = 0.5

        val seen = mutableListOf<Pair<String, Double>>()

        map.forEachEntry { key, value -> seen.add(key to value) }

        seen shouldBe listOf("delay.wet" to 0.5, "reverb.size" to 6.3, "gain.gain" to 1.0)
        seen shouldBe map.entries.map { it.key to it.value }

        val none = mutableListOf<String>()

        KlangMap<Double>().forEachEntry { key, _ -> none.add(key) }

        none shouldBe emptyList()
    }

    "setAll from another KlangMap overwrites in place, appends new keys in order, and shares nothing" {
        val target = klangMapOf("b" to 2.0, "a" to 1.0)
        val source = klangMapOf("c" to 4.0, "a" to 5.0)

        target.setAll(source)

        target.keys.toList() shouldBe listOf("b", "a", "c")
        target shouldBe mapOf("b" to 2.0, "a" to 5.0, "c" to 4.0)

        source["d"] = 6.0

        target.containsKey("d") shouldBe false
        source.keys.toList() shouldBe listOf("c", "a", "d")
    }

    "two KlangMaps compare by content, both ways" {
        val a = klangMapOf("x" to 1.0, "y" to 2.0)

        (a == klangMapOf("y" to 2.0, "x" to 1.0)) shouldBe true
        (a == klangMapOf("x" to 1.0, "y" to 2.5)) shouldBe false
        (klangMapOf("x" to 1.0, "y" to 2.5) == a) shouldBe false
        (a == klangMapOf("x" to 1.0)) shouldBe false
        a.hashCode() shouldBe klangMapOf("y" to 2.0, "x" to 1.0).hashCode()
    }

    "a null value is stored, contained and read as null" {
        val map = KlangMap<Double?>()

        map["unset"] = null

        map.containsKey("unset") shouldBe true
        map["unset"] shouldBe null
        map.size shouldBe 1
        (map == mapOf<String, Double?>("unset" to null)) shouldBe true
        (map == mapOf<String, Double?>("other" to null)) shouldBe false
    }
})
