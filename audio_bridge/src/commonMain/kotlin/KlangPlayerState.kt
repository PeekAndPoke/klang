package io.peekandpoke.klang.audio_bridge

/** Shared state between fetcher and renderer */
class KlangPlayerState {
    private var cursorFrame: Long = 0L
    private var cursorFrameMutex = KlangLock()

    private var running: Boolean = false
    private var runningMutex = KlangLock()

    fun cursorFrame(): Long = cursorFrameMutex.withLock { cursorFrame }
    fun cursorFrame(frame: Long): Unit = cursorFrameMutex.withLock { cursorFrame = frame }

    fun running(): Boolean = runningMutex.withLock { running }
    fun running(running: Boolean): Unit = runningMutex.withLock { this.running = running }
    fun running(expect: Boolean, update: Boolean): Boolean = runningMutex.withLock {
        if (running == expect) {
            running = update
            return@withLock update
        }
        return@withLock false
    }
}
