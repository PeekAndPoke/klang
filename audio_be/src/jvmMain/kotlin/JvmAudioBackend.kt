/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

class JvmAudioBackend(
    config: AudioBackend.Config,
) : AudioBackend {
    private val commLink: KlangCommLink.BackendEndpoint = config.commLink
    private val sampleRate: Int = config.sampleRate
    private val blockSize: Int = config.blockSize

    // KlangTime for timing measurements
    private val klangTime = KlangTime.create()

    // 1. DSP graph + Cmd dispatch — shared with the JS worklet backend (see PlaybackEngineDispatcher).
    private val dispatcher = PlaybackEngineDispatcher.create(
        sampleRate = sampleRate,
        blockFrames = blockSize,
        commLink = commLink,
        performanceTimeMs = { klangTime.internalMsNow() },
    )

    override suspend fun run(scope: CoroutineScope) {
        // Set backend start time from KlangTime relative clock
        dispatcher.setBackendStartTime(klangTime.internalMsNow() / 1000.0)

        // Kick off JIT / cache warmup. JVM JIT is warmer than Kotlin/JS but running the same
        // path keeps behavior consistent across targets (and costs 48 to 219 ms of silence at start: 18 blocks, then up
        // to 64 more while the shelves are zeroed, audit L9-12).
        // Warmup voices run through the real scheduler/renderer; the limiter + scheduler are
        // reset at the end of the handshake so nothing leaks into real playback.
        val warmup = WarmupRunner(
            sampleRate = sampleRate,
            dispatcher = dispatcher,
            feedback = commLink,
        )
        warmup.start()

        // Int instead of Long: keeps JVM backend consistent with JS (where Long is boxed).
        // Double, not Int: this is an ABSOLUTE frame on the backend timeline and grows for the
        // life of the backend. As an Int it overflows after ~12.4 h at 48 kHz and audio silently
        // stops. Double is exact to 2^53 (~5,950 years) with zero drift — only +, - and compare are
        // ever applied. See RenderClock.cursorFrame.
        var currentFrame = 0.0

        // Stereo
        val format = AudioFormat(sampleRate.toFloat(), 16, 2, true, false)

        // Audio line
        val line: SourceDataLine = AudioSystem.getSourceDataLine(format)

        // println("AudioSystem SourceDataLine: $line ${line.isActive}")

        // Hardware buffer ≈ 250 ms — matches JS `latencyHint = "playback"` profile
        // (prioritise glitch-free playback over minimum latency). Backed by a single
        // pinned audio thread (see klang/.../index_jvm.kt) and natural pacing via
        // line.write(), so no extra coroutine sleep is needed.
        val bufferMs = 250
        val bufferFrames = (sampleRate * bufferMs / 1000.0).toInt()
        // 4 bytes per frame (Stereo 16-bit)
        val bufferBytes = bufferFrames * 4

        line.open(format, bufferBytes)
        line.start()

        // What the line holds is rendered and not heard yet: the UI's clock takes it off the timeline (audit L8-08).
        // The size the line granted, which may not be the size asked for
        dispatcher.outputLatencyMs = outputLatencyMs(lineBufferBytes = line.bufferSize, sampleRate = sampleRate)

        // Pre-allocate output buffers: the engine's floating-point block, and the line's 16-bit bytes
        val out = StereoBuffer(blockSize)
        val outBytes = ByteArray(blockSize * 4)

        try {
            while (scope.isActive) {
                // Get events
                while (true) {
                    val cmd = commLink.control.receive() ?: break

                    // The last catch: a throw costs this command, never the loop (see [logThrow])
                    try {
                        dispatcher.handle(cmd)
                    } catch (e: Throwable) {
                        logThrow(where = "a command", e = e)
                    }
                }

                // rendering ///////////////////////////////////////////////////////////////////////////////////////
                // Always render — warmup voices live on the real scheduler so this exercises
                // the actual render path for JIT / cache priming.
                // The last catch: a throw costs this block, which goes out silent (see [logThrow]).
                try {
                    dispatcher.renderBlock(cursorFrame = currentFrame, out = out)

                    if (warmup.isWarming) {
                        out.clear()
                        warmup.tick()
                    }
                } catch (e: Throwable) {
                    logThrow(where = "the render", e = e)
                    out.clear()
                }

                // The 16-bit edge: the line takes signed 16-bit little-endian stereo
                writePcm16(source = out, frames = blockSize, bytes = outBytes)

                // Advance Cursor (State Write)
                currentFrame += blockSize

                // 3. Write to Hardware
                // This call blocks if the hardware buffer is full — that IS the loop
                // pacing. No coroutine sleep needed; an explicit delay() would only
                // add scheduler jitter on top of the already-deterministic backpressure
                // and force a per-block thread reschedule. See `audio/ref/performance.md`.
                line.write(outBytes, 0, outBytes.size)
            }
        } finally {
            // Cleanup
            line.drain()
            line.stop()
            line.close()
        }

        println("KlangPlayerBackend stopped")
    }

    /** True once [logThrow] has logged: a throw that comes back every block is logged once. */
    private var hasLoggedThrow = false

    /**
     * The backend loop's last catch, for a throw the dispatcher's own net does not hold (it catches a throw of one
     * playback's engine, `PlaybackEngineDispatcher`, "The net"; this is for the rest, the house stage, the warmup).
     * Without it one throw left the loop, closed the line and ended the audio for the session (audit L8-03, L9-01).
     * `Throwable`, as the dispatcher's net: a stack overflow in a deep tree is an `Error`, and it too must not end
     * the session.
     */
    private fun logThrow(where: String, e: Throwable) {
        if (!hasLoggedThrow) {
            hasLoggedThrow = true
            println("[JvmAudioBackend] $where threw, the backend plays on (logged once): $e")
        }
    }

    internal companion object {
        /**
         * The output latency only the JVM host knows, in ms: the line's buffer, [lineBufferBytes] of 16-bit stereo
         * frames. The engine's own latency (the house limiter's lookahead, a master chain's) the dispatcher adds to it
         * in every report (`PlaybackEngineDispatcher.outputLatencyMs`, KLANG-00431 D3).
         */
        fun outputLatencyMs(lineBufferBytes: Int, sampleRate: Int): Double =
            lineBufferBytes / 4.0 / sampleRate * 1000.0
    }
}
