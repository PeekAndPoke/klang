/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * [retainInOrder], the scheduler's one removal law (voice lifecycle step 5): every rejected element leaves, the
 * survivors keep their order, and the predicate runs exactly once per element, in order (the render loop renders
 * each voice inside it).
 */
class RetainInOrderSpec : StringSpec({

    /** Runs [retainInOrder] on 0 until [n], keeping what [keep] accepts; returns the list and the visit order. */
    fun retain(n: Int, keep: (Int) -> Boolean): Pair<List<Int>, List<Int>> {
        val list = MutableList(n) { it }
        val visited = mutableListOf<Int>()

        list.retainInOrder {
            visited.add(it)
            keep(it)
        }

        return list to visited
    }

    val cases = listOf(
        "nothing removed" to { _: Int -> true },
        "everything removed" to { _: Int -> false },
        "only the first removed" to { i: Int -> i != 0 },
        "only the last removed" to { i: Int -> i != 7 },
        "every other element removed" to { i: Int -> i % 2 == 1 },
        "several neighbours removed in a row" to { i: Int -> i !in 2..5 },
        "neighbours at both ends removed" to { i: Int -> i in 2..5 },
    )

    for ((name, keep) in cases) {
        "$name: the survivors keep their order, and every element is visited once, in order" {
            val (list, visited) = retain(n = 8, keep = keep)

            withClue("survivors") { list shouldBe (0 until 8).filter(keep) }
            withClue("visits") { visited shouldBe (0 until 8).toList() }
        }
    }

    "an empty list stays empty and visits nothing" {
        val (list, visited) = retain(n = 0) { true }

        list shouldBe emptyList()
        visited shouldBe emptyList()
    }
})
