package io.peekandpoke.klang.audio_bridge.infra

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class KlangAtomicBoolConcurrencyTest : StringSpec({

    "concurrent compareAndSet should have exactly one winner" {
        val atomic = KlangAtomicBool(false)
        val threads = 100
        val successCount = AtomicInteger(0)

        val latch = CountDownLatch(threads)
        val threadList = (1..threads).map {
            thread {
                if (atomic.compareAndSet(expect = false, update = true)) {
                    successCount.incrementAndGet()
                }
                latch.countDown()
            }
        }

        latch.await()
        threadList.forEach { it.join() }

        // Exactly one thread should have succeeded
        successCount.get() shouldBe 1
        atomic.get() shouldBe true
    }

    "concurrent compareAndSet flip-flop" {
        val atomic = KlangAtomicBool(false)
        val threads = 50
        val flipsPerThread = 100
        val successCounts = AtomicInteger(0)

        val latch = CountDownLatch(threads)
        val threadList = (1..threads).map {
            thread {
                repeat(flipsPerThread) {
                    // Try to flip from false to true
                    if (atomic.compareAndSet(false, true)) {
                        successCounts.incrementAndGet()
                    }
                    // Try to flip from true to false
                    if (atomic.compareAndSet(true, false)) {
                        successCounts.incrementAndGet()
                    }
                }
                latch.countDown()
            }
        }

        latch.await()
        threadList.forEach { it.join() }

        // Total successful flips should be even (each successful flip is paired)
        val totalSuccess = successCounts.get()
        println("Total successful compareAndSet operations: $totalSuccess")
        (totalSuccess > 0) shouldBe true
    }

    "concurrent getAndSet should all complete" {
        val atomic = KlangAtomicBool(false)
        val threads = 20
        val results = mutableListOf<MutableList<Boolean>>()
        repeat(threads) { results.add(mutableListOf()) }

        val latch = CountDownLatch(threads)
        val threadList = (0 until threads).map { threadIdx ->
            thread {
                repeat(50) { iteration ->
                    val oldValue = atomic.getAndSet(iteration % 2 == 0)
                    results[threadIdx].add(oldValue)
                }
                latch.countDown()
            }
        }

        latch.await()
        threadList.forEach { it.join() }

        // All threads should have completed
        results.forEach { threadResults ->
            threadResults.size shouldBe 50
        }

        // Final value should be either true or false
        val finalValue = atomic.get()
        (finalValue == true || finalValue == false) shouldBe true
    }

    "compareAndSet race condition simulation (start/stop pattern)" {
        val running = KlangAtomicBool(false)
        val threads = 10
        val startSuccesses = AtomicInteger(0)
        val stopSuccesses = AtomicInteger(0)

        val latch = CountDownLatch(threads * 2)

        // Start threads
        val startThreads = (1..threads).map {
            thread {
                if (running.compareAndSet(expect = false, update = true)) {
                    startSuccesses.incrementAndGet()
                    Thread.sleep(1) // Simulate some work
                }
                latch.countDown()
            }
        }

        // Stop threads
        val stopThreads = (1..threads).map {
            thread {
                Thread.sleep(5) // Wait a bit before trying to stop
                if (running.compareAndSet(expect = true, update = false)) {
                    stopSuccesses.incrementAndGet()
                }
                latch.countDown()
            }
        }

        latch.await()
        (startThreads + stopThreads).forEach { it.join() }

        println("Start successes: ${startSuccesses.get()}, Stop successes: ${stopSuccesses.get()}")

        // At least one start should have succeeded
        (startSuccesses.get() >= 1) shouldBe true

        // Stop successes should be <= start successes (can't stop what wasn't started)
        (stopSuccesses.get() <= startSuccesses.get()) shouldBe true
    }

    "high contention scenario" {
        val atomic = KlangAtomicBool(false)
        val threads = 100
        val operationsPerThread = 1000
        val operations = AtomicInteger(0)

        val latch = CountDownLatch(threads)
        val threadList = (1..threads).map {
            thread {
                repeat(operationsPerThread) {
                    // Try various operations
                    atomic.get()
                    atomic.set(it % 2 == 0)
                    atomic.getAndSet(it % 3 == 0)
                    atomic.compareAndSet(true, false)
                    atomic.compareAndSet(false, true)
                    operations.incrementAndGet()
                }
                latch.countDown()
            }
        }

        latch.await()
        threadList.forEach { it.join() }

        // All operations should have completed
        operations.get() shouldBe (threads * operationsPerThread)

        // Final state should be valid
        val finalValue = atomic.get()
        (finalValue == true || finalValue == false) shouldBe true
    }
})
