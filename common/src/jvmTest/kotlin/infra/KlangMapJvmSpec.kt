/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.infra

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.InvocationTargetException

/**
 * The JVM half of [KlangMap]'s read-only contract that common code cannot reach: the `java.util.Map` writers the
 * compiler generates for a read-only Kotlin `Map`, called as Java code would call them. Each must throw
 * `UnsupportedOperationException` and leave the map as it was.
 */
class KlangMapJvmSpec : StringSpec({

    /** Calls the `java.util.Map` method [name] on [map] by reflection; the exception it threw, or null. */
    fun javaCall(map: KlangMap<Double>, name: String, types: Array<Class<*>>, args: Array<Any?>): Throwable? =
        runCatching { map.javaClass.getMethod(name, *types).invoke(map, *args) }
            .exceptionOrNull()
            ?.let { if (it is InvocationTargetException) it.targetException else it }

    "the java.util.Map writers throw UnsupportedOperationException and change nothing" {
        val map = klangMapOf("a" to 1.0, "b" to 2.0)
        val anyClass = Any::class.java

        val thrown = listOf(
            javaCall(map, "remove", arrayOf(anyClass), arrayOf("a")),
            javaCall(map, "put", arrayOf(anyClass, anyClass), arrayOf("c", 3.0)),
            javaCall(map, "putAll", arrayOf(Map::class.java), arrayOf(mapOf("d" to 4.0))),
            javaCall(map, "clear", arrayOf(), arrayOf()),
        )

        thrown.map { it is UnsupportedOperationException } shouldBe listOf(true, true, true, true)
        map shouldBe mapOf("a" to 1.0, "b" to 2.0)
    }
})
