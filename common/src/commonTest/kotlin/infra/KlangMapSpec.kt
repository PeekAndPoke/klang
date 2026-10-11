/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.infra

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Pins [KlangMap]'s contract on both platforms. Where the contract says "as Kotlin's own maps do" (order, overwrite,
 * `hashCode`, `toString`), Kotlin's `LinkedHashMap` is the reference: the same writes on both, the same answers.
 */
class KlangMapSpec : StringSpec({

    /** The entries in iteration order, as pairs. */
    fun <V> KlangMap<V>.pairs(): List<Pair<String, V>> = buildList { forEachEntry { key, value -> add(key to value) } }

    "an absent key reads null and is not contained; size and isEmpty count the keys" {
        val map = klangMapOf("a" to 1.5)

        map["b"] shouldBe null
        ("b" in map) shouldBe false
        ("a" in map) shouldBe true
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

    "iteration follows insertion order, an overwrite keeps its position, a deleted and re-set key goes last, as LinkedHashMap" {
        val klang = KlangMap<Double>()
        val ref = LinkedHashMap<String, Double>()

        fun set(key: String, value: Double) {
            klang[key] = value
            ref[key] = value
        }

        set("delay.wet", 0.3)
        set("reverb.size", 6.3)
        set("body.wet", 0.4)
        set("delay.wet", 0.5) // overwrite: stays first
        klang.delete("reverb.size")
        ref.remove("reverb.size")
        set("reverb.size", 7.0) // re-set: goes last
        set("gain.gain", 0.8)

        klang.pairs() shouldBe listOf("delay.wet" to 0.5, "body.wet" to 0.4, "reverb.size" to 7.0, "gain.gain" to 0.8)
        klang.pairs() shouldBe ref.toList()
    }

    "delete answers the old value, or null for an absent key, and the key is gone" {
        val map = klangMapOf("a" to 1.0, "b" to 2.5)

        map.delete("a") shouldBe 1.0
        map.delete("zz") shouldBe null
        ("a" in map) shouldBe false
        map["a"] shouldBe null
        map.size shouldBe 1
        map shouldBe klangMapOf("b" to 2.5)
    }

    "a copy is independent in both directions and keeps the order" {
        val original = klangMapOf("a" to 1.0, "b" to 2.0)
        val copy = original.copy()

        copy shouldBe original
        copy.pairs() shouldBe listOf("a" to 1.0, "b" to 2.0)

        copy["c"] = 3.0
        copy["a"] = 9.0
        original["b"] = 8.0

        original shouldBe klangMapOf("a" to 1.0, "b" to 8.0)
        copy shouldBe klangMapOf("a" to 9.0, "b" to 2.0, "c" to 3.0)
    }

    "two KlangMaps compare by content in any order, both ways; hashCode is a Map's for the same content" {
        val a = klangMapOf("delay.wet" to 0.3, "reverb.size" to 6.3, "gain.gain" to 1.0)
        val b = klangMapOf("gain.gain" to 1.0, "delay.wet" to 0.3, "reverb.size" to 6.3)

        (a == b) shouldBe true
        (b == a) shouldBe true
        a.hashCode() shouldBe b.hashCode()
        a.hashCode() shouldBe linkedMapOf("delay.wet" to 0.3, "reverb.size" to 6.3, "gain.gain" to 1.0).hashCode()
    }

    "a different value, a missing key or an extra key is unequal, both ways" {
        val map = klangMapOf("a" to 1.0, "b" to 2.0)

        (map == klangMapOf("a" to 1.0, "b" to 2.5)) shouldBe false
        (klangMapOf("a" to 1.0, "b" to 2.5) == map) shouldBe false
        (map == klangMapOf("a" to 1.0)) shouldBe false
        (klangMapOf("a" to 1.0) == map) shouldBe false
        (map == klangMapOf("a" to 1.0, "b" to 2.0, "c" to 3.0)) shouldBe false
        (map == klangMapOf("a" to 1.0, "c" to 2.0)) shouldBe false
    }

    "it is not a Map: it never equals a stdlib map, even one with the same entries" {
        val map = klangMapOf("a" to 1.0)

        ((map as Any) is Map<*, *>) shouldBe false
        ((map as Any) == linkedMapOf("a" to 1.0)) shouldBe false
        ((linkedMapOf("a" to 1.0) as Any) == map) shouldBe false
    }

    "NaN equals NaN and -0.0 differs from 0.0, as in a LinkedHashMap" {
        val map = klangMapOf("wet" to Double.NaN, "zero" to -0.0)

        map["wet"]!!.isNaN() shouldBe true
        (map == klangMapOf("wet" to Double.NaN, "zero" to -0.0)) shouldBe
            (linkedMapOf("wet" to Double.NaN, "zero" to -0.0) == linkedMapOf("wet" to Double.NaN, "zero" to -0.0))
        (map == klangMapOf("wet" to Double.NaN, "zero" to 0.0)) shouldBe
            (linkedMapOf("wet" to Double.NaN, "zero" to -0.0) == linkedMapOf("wet" to Double.NaN, "zero" to 0.0))
        map.hashCode() shouldBe linkedMapOf("wet" to Double.NaN, "zero" to -0.0).hashCode()
    }

    "forEachEntry visits every entry once, in insertion order" {
        val map = klangMapOf("delay.wet" to 0.3, "reverb.size" to 6.3, "gain.gain" to 1.0)

        map["delay.wet"] = 0.5

        map.pairs() shouldBe listOf("delay.wet" to 0.5, "reverb.size" to 6.3, "gain.gain" to 1.0)
        KlangMap<Double>().pairs() shouldBe emptyList()
    }

    "setAll overwrites in place, appends new keys in order, and shares nothing with its source" {
        val target = klangMapOf("b" to 2.0, "a" to 1.0)
        val source = klangMapOf("c" to 4.0, "a" to 5.0)

        target.setAll(source)

        target.pairs() shouldBe listOf("b" to 2.0, "a" to 5.0, "c" to 4.0)

        source["d"] = 6.0

        ("d" in target) shouldBe false
        source.pairs() shouldBe listOf("c" to 4.0, "a" to 5.0, "d" to 6.0)
    }

    "toString prints as Kotlin's own map of the same entries does" {
        klangMapOf("a" to 1.5, "b" to 2.0).toString() shouldBe linkedMapOf("a" to 1.5, "b" to 2.0).toString()
        KlangMap<Double>().toString() shouldBe linkedMapOf<String, Double>().toString()
    }

    "klangMapOf keeps the order and a later duplicate overwrites in place" {
        val map = klangMapOf("b" to 2.0, "a" to 1.0, "b" to 3.0)

        map.pairs() shouldBe listOf("b" to 3.0, "a" to 1.0)
    }

    "the interop extensions copy in order, both ways, and share nothing" {
        val klang = klangMapOf("q" to 1.0, "p" to 2.0)
        val plain = klang.toMap()

        plain shouldBe linkedMapOf("q" to 1.0, "p" to 2.0)
        plain.keys.toList() shouldBe listOf("q", "p")

        val back = linkedMapOf("y" to 3.0, "x" to 4.0).toKlangMap()

        back.pairs() shouldBe listOf("y" to 3.0, "x" to 4.0)

        klang["r"] = 5.0

        plain.containsKey("r") shouldBe false
    }

    "a null value is stored, contained and read as null" {
        val map = KlangMap<Double?>()

        map["unset"] = null

        ("unset" in map) shouldBe true
        map["unset"] shouldBe null
        map.size shouldBe 1
        (map == klangMapOf<Double?>("unset" to null)) shouldBe true
        (map == klangMapOf<Double?>("other" to null)) shouldBe false
    }

    "a capacity hint gives an empty map that works as any other, a negative hint included" {
        val map = KlangMap<Double>(32)

        map.isEmpty() shouldBe true
        map["a"] = 1.0
        map shouldBe klangMapOf("a" to 1.0)
        KlangMap<Double>(-5).also { it["b"] = 2.0 } shouldBe klangMapOf("b" to 2.0)
    }
})
