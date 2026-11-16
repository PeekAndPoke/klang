/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.visuals

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

/** A [LatestRead] of texts whose reads end when the spec lets them, and all it tells. */
private class Bench {
    val scope = MainScope()
    val gates = mutableMapOf<String, CompletableDeferred<String?>>()
    val started = mutableListOf<String>()
    val told = mutableListOf<String>()

    val reads = LatestRead<String, String>(
        scope = scope,
        read = { key ->
            started.add(key)
            gates.getOrPut(key) { CompletableDeferred() }.await()
        },
        changed = { before, now -> told.add("changed $before -> $now") },
        done = { key, value -> told.add("done $key = $value") },
        failed = { key, cause -> told.add("failed $key: ${cause.message}") },
    )

    /** Lets the read of [key] end with [value]. */
    fun end(key: String, value: String?) {
        gates.getOrPut(key) { CompletableDeferred() }.complete(value)
    }

    /** Lets what is waiting run. */
    suspend fun settle() {
        repeat(5) { yield() }
        delay(5)
    }
}

class LatestReadSpec : StringSpec({

    "what is wanted is read, and told of when the read ends" {
        val bench = Bench()

        bench.reads.want("a")
        bench.settle()
        bench.started shouldBe listOf("a")
        bench.told shouldBe listOf("changed null -> a")

        bench.end("a", "A")
        bench.settle()
        bench.told shouldBe listOf("changed null -> a", "done a = A")
        bench.scope.cancel()
    }

    "asked for the same again, the read goes on and is not started anew" {
        val bench = Bench()

        bench.reads.want("a")
        bench.settle()
        bench.reads.want("a")
        bench.settle()
        bench.started shouldBe listOf("a")

        bench.end("a", "A")
        bench.settle()
        bench.told shouldBe listOf("changed null -> a", "done a = A")
        bench.scope.cancel()
    }

    "another wanted while one is read: the first read tells nothing, the second is told of" {
        val bench = Bench()

        bench.reads.want("a")
        bench.settle()
        bench.reads.want("b")
        bench.settle()

        // the first read ends late: nobody hears of it
        bench.end("a", "A")
        bench.settle()
        bench.told shouldBe listOf("changed null -> a", "changed a -> b")

        bench.end("b", "B")
        bench.settle()
        bench.told shouldBe listOf("changed null -> a", "changed a -> b", "done b = B")
        bench.scope.cancel()
    }

    "nothing wanted while one is read: the read tells nothing, not as done and not as failed" {
        val bench = Bench()

        bench.reads.want("a")
        bench.settle()
        bench.reads.want(null)
        bench.settle()

        bench.end("a", "A")
        bench.settle()
        bench.told shouldBe listOf("changed null -> a", "changed a -> null")
        bench.scope.cancel()
    }

    "the scope gone while one is read: the read tells nothing" {
        val bench = Bench()

        bench.reads.want("a")
        bench.settle()
        bench.scope.cancel()
        bench.settle()

        bench.told shouldBe listOf("changed null -> a")
    }

    "a read that fails is told of as failed, and as done with nothing" {
        val bench = Bench()

        bench.reads.want("a")
        bench.settle()
        bench.gates.getValue("a").completeExceptionally(IllegalStateException("no song"))
        bench.settle()

        bench.told shouldBe listOf("changed null -> a", "failed a: no song", "done a = null")
        bench.scope.cancel()
    }

    "wanted again after another: read anew" {
        val bench = Bench()

        bench.reads.want("a")
        bench.settle()
        bench.reads.want("b")
        bench.settle()
        bench.gates.remove("a")
        bench.reads.want("a")
        bench.settle()

        bench.started shouldBe listOf("a", "b", "a")

        bench.end("a", "A2")
        bench.settle()
        bench.told.last() shouldBe "done a = A2"
        bench.scope.cancel()
    }
})
