package io.peekandpoke.klang.workshop.streams

import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSourceImpl
import io.peekandpoke.ultra.streams.Unsubscribe
import kotlin.math.abs
import kotlin.math.pow

/**
 * Smooths a stream of numbers: a new value is approached step by step, a step each frame of [clock], until the
 * stream is there, also while the source holds still (KLANG-00345). Each step keeps [sharePer10Ms] of the way left
 * per 10 ms, sized by the time since the last frame, so the pace is the same at any frame rate.
 *
 * - The first value after someone starts listening is taken as it is: a needle shown anew starts at its reading.
 * - `null` (no value, offline), a NaN and an infinity pass at once as `null` and stop the motion.
 * - After a `null` the motion starts from [rest] (a needle rises from its rest when it comes online).
 * - A [sharePer10Ms] of 0 or less (or a NaN) passes every value at once; one of 1 or more counts as 0.999, the
 *   slowest there is, as a share of 1 would never move and one above it would move away.
 * - The motion stops within [near] of the value, in the value's units, and lands exactly on it.
 *
 * The source is listened to only while someone listens here, and no frame is asked for while nothing moves.
 */
fun Stream<Double?>.smooth(
    sharePer10Ms: Double,
    rest: Double = 0.0,
    near: Double = 0.001,
    clock: FrameClock = FrameClock.animationFrames,
): Stream<Double?> = StreamSmoothOperator(source = this, sharePer10Ms = sharePer10Ms, rest = rest, near = near, clock = clock)

private class StreamSmoothOperator(
    private val source: Stream<Double?>,
    private val sharePer10Ms: Double,
    private val rest: Double,
    private val near: Double,
    private val clock: FrameClock,
) : StreamSourceImpl<Double?>(source().finiteOrNull()) {

    private companion object {
        /** The time [sharePer10Ms] is the share kept for. */
        const val STEP_MS = 10.0

        /** A usual frame, for the first step of a motion and for a clock that stands or goes backwards. */
        const val FRAME_MS = 1000.0 / 60.0

        fun Double?.finiteOrNull(): Double? = this?.takeIf { it.isFinite() }
    }

    private var unsubscribe: Unsubscribe? = null

    /** Someone listens: the source is subscribed, frames may be asked for. */
    private var listening = false

    /** The first value of this listening has come; until then a value is taken as it is. */
    private var settledIn = false

    /** Where the stream heads: the source's latest value. */
    private var target: Double? = current

    /** The pending frame's cancel, while the stream moves. */
    private var cancelFrame: (() -> Unit)? = null

    /** When the last step was taken, from the clock; null before the first step of a motion. */
    private var lastFrameAt: Double? = null

    /** Nobody listening: the source's value as it is now, as Ultra's own operators read through then. */
    override fun invoke(): Double? = if (listening) current else source().finiteOrNull()

    override fun onSub() {
        if (!listening) {
            listening = true
            settledIn = false
            unsubscribe = source.subscribeToStream { incoming(it) }
        }
    }

    override fun onUnSub() {
        if (subscriptions.isEmpty()) {
            listening = false
            unsubscribe?.invoke()
            unsubscribe = null
            stop()
        }
    }

    private fun incoming(value: Double?) {
        val next = value.finiteOrNull()
        // NaN-guard: a share that is not a number counts as none
        val smoothing = sharePer10Ms == sharePer10Ms && sharePer10Ms > 0.0

        target = next

        when {
            // The first value of a listening, nothing (offline), or no smoothing: at once
            !settledIn || next == null || !smoothing -> {
                settledIn = true
                stop()
                emit(next)
            }

            else -> {
                if (current == null) {
                    emit(rest)
                }

                if (current != next) {
                    start()
                }
            }
        }
    }

    private fun start() {
        if (cancelFrame != null || !listening) {
            return
        }

        lastFrameAt = null
        cancelFrame = clock.next { at -> frame(at) }
    }

    private fun stop() {
        cancelFrame?.invoke()
        cancelFrame = null
        lastFrameAt = null
    }

    private fun frame(at: Double) {
        cancelFrame = null

        val goal = target ?: return
        val from = current ?: return
        // NaN-guard: the first step, or a clock that stands or goes backwards, counts as one usual frame
        val elapsed = lastFrameAt?.let { at - it }?.takeIf { it > 0.0 } ?: FRAME_MS
        val keep = sharePer10Ms.coerceAtMost(0.999).pow(elapsed / STEP_MS)
        val next = from * keep + goal * (1.0 - keep)
        // There: within [near], a step that moves nothing any more, or a NaN; it lands exactly on the value
        val done = !(abs(next - goal) > near) || next == from

        lastFrameAt = at
        emit(if (done) goal else next)

        // The last listener may have left during the emit: then no more frames; and a listener that wrote a new value
        // to the source during it may have started a frame already: then not a second one
        if (done || !listening) {
            lastFrameAt = null
        } else if (cancelFrame == null) {
            cancelFrame = clock.next { nextAt -> frame(nextAt) }
        }
    }

    private fun emit(value: Double?) {
        invoke(value)
    }
}
