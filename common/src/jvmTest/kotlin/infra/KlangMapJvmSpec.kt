/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.infra

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.Collection as JavaCollection
import java.util.Map as JavaMap

/**
 * The JVM half of [KlangMap]'s contract that common code cannot reach: it is no `java.util.Map` (since engine
 * follow-up 2, step 5: not a `Map`, so no Java caller can walk or write it through the collections API either), and a
 * negative capacity hint does not reach `LinkedHashMap`, which would throw.
 */
class KlangMapJvmSpec : StringSpec({

    "it is no java.util.Map and no java.util.Collection" {
        val map: Any = klangMapOf("a" to 1.0)

        JavaMap::class.java.isInstance(map) shouldBe false
        JavaCollection::class.java.isInstance(map) shouldBe false
    }

    "a negative capacity hint is coerced, not handed to LinkedHashMap" {
        val map = KlangMap<Double>(-1)

        map["a"] = 1.0

        map["a"] shouldBe 1.0
    }
})
