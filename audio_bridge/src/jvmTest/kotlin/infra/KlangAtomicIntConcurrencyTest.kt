package io.peekandpoke.klang.audio_bridge.infra

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class KlangAtomicIntConcurrencyTest : StringSpec({

    "concurrent increments should be thread-safe" {
        val atomic = KlangAtomicInt(0)
        val threads = 10
        val incrementsPerThread = 1000

        val latch = CountDownLatch(threads)
        val threadList = (1..threads).map {
            thread {
                repeat(incrementsPerThread) {
                    atomic.incrementAndGet()
                }
                latch.countDown()
            }
        }

        latch.await()
        threadList.forEach { it.join() }

        atomic.get() shouldBe (threads * incrementsPerThread)
    }

    "concurrent getAndIncrement should return unique values" {
        val atomic = KlangAtomicInt(0)
        val threads = 10
        val incrementsPerThread = 100

        val results = mutableListOf<MutableList<Int>>()
        repeat(threads) { results.add(mutableListOf()) }

        val latch = CountDownLatch(threads)
        val threadList = (0 until threads).map { threadIdx ->
            thread {
                repeat(incrementsPerThread) {
                    val value = atomic.getAndIncrement()
                    results[threadIdx].add(value)
                }
                latch.countDown()
            }
        }

        latch.await()
        threadList.forEach { it.join() }

        // All results should be unique
        val allValues = results.flatten().sorted()
        allValues shouldBe (0 until (threads * incrementsPerThread)).toList()
    }

    "concurrent mixed operations should be thread-safe" {
        val atomic = KlangAtomicInt(1000)
        val threads = 20
        val operationsPerThread = 500

        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)

        repeat(threads) { threadIdx ->
            executor.submit {
                try {
                    repeat(operationsPerThread) { opIdx ->
                        if ((threadIdx + opIdx) % 2 == 0) {
                            atomic.incrementAndGet()
                        } else {
                            atomic.decrementAndGet()
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executor.shutdown()

        // Final value should be initial + (increments - decrements)
        // Each thread does 250 increments and 250 decrements if evenly distributed
        val finalValue = atomic.get()
        // The exact value depends on threadIdx + opIdx distribution
        // But it should be close to 1000 (initial value)
        println("Final value after mixed operations: $finalValue")
        finalValue shouldBe 1000
    }

    "concurrent set operations should be visible" {
        val atomic = KlangAtomicInt(0)
        val threads = 5
        val latch = CountDownLatch(threads)

        val threadList = (1..threads).map { threadId ->
            thread {
                Thread.sleep(10 * threadId.toLong())
                atomic.set(threadId * 100)
                latch.countDown()
            }
        }

        latch.await()
        threadList.forEach { it.join() }

        // Value should be one of the set values
        val finalValue = atomic.get()
        (finalValue % 100) shouldBe 0
        (finalValue >= 100) shouldBe true
        (finalValue <= 500) shouldBe true
    }
})
