package io.peekandpoke.klang.common.infra

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KlangMinHeapSpec : StringSpec({

    fun intHeap() = KlangMinHeap<Int> { a, b -> a < b }

    fun <T> KlangMinHeap<T>.popAll(): List<T> = buildList {
        while (true) {
            add(pop() ?: break)
        }
    }

    "push and pop maintain min-heap order" {
        val heap = intHeap()
        listOf(5, 3, 8, 1, 4, 7, 2, 6).forEach { heap.push(it) }

        heap.popAll() shouldBe listOf(1, 2, 3, 4, 5, 6, 7, 8)
    }

    "pop from empty heap returns null" {
        intHeap().pop() shouldBe null
    }

    "peek returns min without removing" {
        val heap = intHeap()
        heap.push(3)
        heap.push(1)
        heap.push(2)

        heap.peek() shouldBe 1
        heap.size() shouldBe 3
    }

    "removeWhen: remove nothing (predicate always false)" {
        val heap = intHeap()
        listOf(3, 1, 4, 1, 5).forEach { heap.push(it) }

        heap.removeWhen { false }

        heap.size() shouldBe 5
        heap.popAll() shouldBe listOf(1, 1, 3, 4, 5)
    }

    "removeWhen: remove everything (predicate always true)" {
        val heap = intHeap()
        listOf(3, 1, 4, 1, 5).forEach { heap.push(it) }

        heap.removeWhen { true }

        heap.size() shouldBe 0
        heap.pop() shouldBe null
    }

    "removeWhen: remove from middle preserves heap order" {
        val heap = intHeap()
        listOf(1, 2, 3, 4, 5, 6, 7, 8).forEach { heap.push(it) }

        // Remove even numbers
        heap.removeWhen { it % 2 == 0 }

        heap.size() shouldBe 4
        heap.popAll() shouldBe listOf(1, 3, 5, 7)
    }

    "removeWhen: remove first element" {
        val heap = intHeap()
        listOf(1, 5, 3).forEach { heap.push(it) }

        heap.removeWhen { it == 1 }

        heap.size() shouldBe 2
        heap.popAll() shouldBe listOf(3, 5)
    }

    "removeWhen: remove last element" {
        val heap = intHeap()
        listOf(1, 5, 3).forEach { heap.push(it) }

        heap.removeWhen { it == 5 }

        heap.size() shouldBe 2
        heap.popAll() shouldBe listOf(1, 3)
    }

    "removeWhen: single-element heap" {
        val heap = intHeap()
        heap.push(42)

        heap.removeWhen { it == 42 }

        heap.size() shouldBe 0
        heap.pop() shouldBe null
    }

    "removeWhen: single-element heap, predicate false" {
        val heap = intHeap()
        heap.push(42)

        heap.removeWhen { it == 99 }

        heap.size() shouldBe 1
        heap.pop() shouldBe 42
    }

    "removeWhen: empty heap" {
        val heap = intHeap()

        heap.removeWhen { true }

        heap.size() shouldBe 0
    }

    "removeWhen: heap order correct after removing root" {
        val heap = intHeap()
        listOf(10, 20, 30, 40, 50).forEach { heap.push(it) }

        // Remove the root (min element)
        heap.removeWhen { it == 10 }

        heap.peek() shouldBe 20
        heap.popAll() shouldBe listOf(20, 30, 40, 50)
    }
})
