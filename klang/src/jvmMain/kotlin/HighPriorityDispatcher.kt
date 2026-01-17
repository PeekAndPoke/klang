package io.peekandpoke.klang.audio_engine

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread factory that creates high-priority threads for real-time audio processing.
 *
 * These threads are given maximum priority to minimize latency and ensure consistent audio playback.
 * The JVM will hint to the OS scheduler to prioritize these threads, though actual behavior depends
 * on the underlying operating system.
 */
private class HighPriorityThreadFactory(
    private val namePrefix: String,
    private val priority: Int = Thread.MAX_PRIORITY,
    private val daemon: Boolean = false,
) : ThreadFactory {
    private val threadNumber = AtomicInteger(1)

    override fun newThread(r: Runnable): Thread {
        return Thread(r, "$namePrefix${threadNumber.getAndIncrement()}").apply {
            priority = this@HighPriorityThreadFactory.priority
            isDaemon = daemon
        }
    }
}

/**
 * Creates a coroutine dispatcher backed by high-priority threads for real-time audio processing.
 *
 * @param threadCount Number of threads in the pool. For real-time audio, a small number (1-2) is
 *                    recommended to avoid context switching overhead.
 * @param namePrefix Prefix for thread names, useful for debugging and profiling.
 * @param priority Thread priority (1-10, where 10 is highest). Defaults to Thread.MAX_PRIORITY.
 * @param daemon Whether threads should be daemon threads. Non-daemon threads keep the JVM alive.
 *
 * @return A CoroutineDispatcher backed by high-priority threads.
 */
fun createHighPriorityDispatcher(
    threadCount: Int = 2,
    namePrefix: String = "klang-audio-",
    priority: Int = Thread.MAX_PRIORITY,
    daemon: Boolean = false,
): CoroutineDispatcher {
    val threadFactory = HighPriorityThreadFactory(
        namePrefix = namePrefix,
        priority = priority,
        daemon = daemon,
    )

    return Executors
        .newFixedThreadPool(threadCount, threadFactory)
        .asCoroutineDispatcher()
}
