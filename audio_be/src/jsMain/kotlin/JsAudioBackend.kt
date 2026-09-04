/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.WorkletContract.sendCmd
import io.peekandpoke.klang.audio_bridge.AnalyserNode
import io.peekandpoke.klang.audio_bridge.AudioContext
import io.peekandpoke.klang.audio_bridge.AudioContextOptions
import io.peekandpoke.klang.audio_bridge.AudioWorkletNode
import io.peekandpoke.klang.audio_bridge.AudioWorkletNodeOptions
import io.peekandpoke.klang.audio_bridge.guessDeviceLatencyMs
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.common.infra.KlangRingBuffer
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.await
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.w3c.dom.MessageEvent
import org.w3c.dom.get

class JsAudioBackend(
    private val config: AudioBackend.Config,
) : AudioBackend {
    private val commLink: KlangCommLink.BackendEndpoint = config.commLink

    private val sampleUploadBuffer = KlangRingBuffer<KlangCommLink.Cmd.Sample.Chunk>(8192 * 4)

    // AnalyserNode for visualization
    private var analyserNode: AnalyserNode? = null

    // The worklet node, held as a field (not just a local in [run]) so [pump] can reach the port
    // from outside the render loop. Null before the worklet is created and after teardown.
    private var workletNode: AudioWorkletNode? = null

    // Implement AudioVisualizer interface with zero-copy methods
    override val analyzer = JsAudioAnalyzer { analyserNode }

    override suspend fun run(scope: CoroutineScope) {
        console.log("JsAudioBackend starting")

        // Init the audio context with the given sample rate
        // latencyHint="playback" prioritizes glitch-free audio with larger buffers
        // This provides more headroom to prevent buffer starvation during CPU spikes or GC pauses
        val contextOpts = jsObject<AudioContextOptions> {
            sampleRate = config.sampleRate
            latencyHint = "playback"  // Prioritize stable, glitch-free playback over minimal latency
//            latencyHint = "interactive"  // Prioritize stable, glitch-free playback over minimal latency
        }
        val ctx = AudioContext(contextOpts)

        console.log("JsAudioBackend AudioContext created successfully", ctx)

        // 1. Audio Context State
        // Note: We intentionally do NOT call ctx.resume() here because:
        // - Safari's resume() can hang indefinitely without user gesture
        // - Modern browsers auto-resume AudioContext on first audio playback
        // - This allows the app to initialize without waiting for user interaction
        console.log("JsAudioBackend AudioContext state: ${ctx.state}")

        if (ctx.state == "suspended") {
            console.log("AudioContext is suspended - this is normal before user interaction")
            console.log("It will auto-resume when audio playback starts")
        }

        try {
            console.log("JsAudioBackend loading worklet")

            // 2. Load the audio worklet module with cache busting
            val cacheBusterHash = getCacheBusterHash()
            val uri = when {
                cacheBusterHash.isBlank() -> "/klang-worklet.js"
                else -> "/klang-worklet.js?c=$cacheBusterHash"
            }
            ctx.audioWorklet.addModule(uri).await()

            // 2. Create the Node (this instantiates the Processor in the Audio Thread)
            // We need to explicitly request 2 output channels, otherwise it defaults to 1 (Mono)
            val nodeOpts = jsObject<AudioWorkletNodeOptions> {
                outputChannelCount = arrayOf(2)
            }

            val node = AudioWorkletNode(ctx, "klang-audio-processor", nodeOpts)
            workletNode = node

            console.log("JsAudioBackend AudioWorkletNode created successfully", node)

            // Create AnalyserNode for visualization (with fallback if it fails)
            try {
                console.log("Creating AnalyserNode")
                analyserNode = ctx.createAnalyser().apply {
                    fftSize = 2048
                    smoothingTimeConstant = 0.8
                }
                console.log("AnalyserNode created successfully for visualization")
            } catch (e: Throwable) {
                console.warn("Failed to create AnalyserNode, visualization disabled:", e)
                analyserNode = null
            }

            // Connect audio graph: Worklet → Analyser → Destination (or direct if no analyser)
            when (val ana = analyserNode) {
                null -> {
                    node.connect(ctx.destination)
                }

                else -> {
                    node.connect(ana)
                    ana.connect(ctx.destination)
                }
            }

            // 3. Setup Feedback Loop (Worklet -> Frontend)
            // We listen for messages from the worklet (e.g. Sample Requests, Position Updates)
            node.port.onmessage = { message: MessageEvent ->
                // We pass all feedback through to the frontend
                val decoded = WorkletContract.decodeFeed(message)

                // Augment Diagnostics with real output latency from the actual AudioContext.
                // The worklet has no access to AudioContext properties, so we enrich here.
                val enriched = if (decoded is KlangCommLink.Feedback.Diagnostics) {
                    val baseLat = ctx.baseLatency * 1000.0
                    val deviceLat = guessDeviceLatencyMs(ctx.outputLatency * 1000.0)
                    // The master limiter's lookahead delay happens INSIDE the worklet, downstream of
                    // the clock — `AudioContext.outputLatency` cannot see it. Without adding it here
                    // every visual that aligns to audio (code highlight, block highlight) fires
                    // early by exactly that much.
                    val masterLat = MasterStage.HOUSE_LIMITER_LOOKAHEAD_SECONDS * 1000.0
                    decoded.copy(
                        baseLatencyMs = baseLat,
                        outputDeviceLatencyMs = deviceLat,
                        outputLatencyMs = baseLat + deviceLat + masterLat,
                    )
                } else {
                    decoded
                }

                // Forward
                commLink.feedback.send(enriched)

                // console.log("JsAudioBackend received message from Worklet:", decoded::class.simpleName)
            }

            // CRITICAL: Start the MessagePort (required for Safari)
            // Safari requires explicit port.start() to activate MessagePort communication
            console.log("JsAudioBackend starting MessagePort")
            node.port.start()
            console.log("JsAudioBackend MessagePort started")

            fun pumpTimerTick() {
                // Upload the next sample chunk ... we send them one at a time.
                //
                // This drip is why the timer still exists. One chunk is ~512 KB (`toChunks`
                // counts DoubleArray elements, so 64 * 1024 frames is 512 KB), i.e. ~51 MB/s of
                // structured clone onto the same MessagePort the audio thread services between
                // render callbacks. Draining the whole upload buffer here would hand the audio
                // thread an unbounded burst mid-load. Control commands do not go through this
                // valve, see [pump].
                //
                // Read through the FIELD, not the captured local: a timer already scheduled when
                // teardown runs fires once more afterwards, and the field is null by then. Check
                // before receiving, so a chunk is never popped and then dropped.
                workletNode?.let { live ->
                    sampleUploadBuffer.receive()?.let { cmd -> live.port.sendCmd(cmd) }
                }

                // Kept for commands queued before the worklet exists: [pump] is inert while
                // [workletNode] is null. NOT a fallback for a second writer - `sendControl` is the
                // only writer of this ring in the tree.
                //
                // Nothing reaches that window today, because `klangPlayer()` awaits BackendReady
                // before handing the player out. It is one gating change away from live though
                // (drop that await, give it a timeout, or hand a player out earlier), and the
                // failure would be a self-deadlock rather than late delivery: `preloadSamples`
                // blocks on the sample ack before the first ScheduleVoices, so the only code that
                // could trigger the next pump() is the code waiting on this drain.
                drainControl()
            }

            fun loop() {
                // Guarded, and the reschedule is deliberately OUTSIDE the guard: it is the only
                // thing keeping this chain alive, so an escaping throw kills the timer forever.
                //
                // That used to be loud - it killed control delivery too, so all audio stopped at
                // once. Now pump() keeps control flowing, so an unguarded throw here would leave
                // the music playing while sample uploads silently stall: every later
                // Sample.Complete still lands in [sampleUploadBuffer] and is never sent, so
                // SampleStore never acks, SamplePreloader never completes, and Play on any
                // sample-based pattern hangs with no error anywhere.
                try {
                    pumpTimerTick()
                } catch (t: Throwable) {
                    console.error("JsAudioBackend timer tick failed, chain continues", t)
                }

                if (scope.isActive) {
                    window.setTimeout({ loop() }, 10)
                }
            }

            console.log("JsAudioBackend starting feedback loop")

            // Start the loop
            loop()

            // Keep the coroutine alive
            suspendCancellableCoroutine { }
        } catch (e: Throwable) {
            console.error("AudioWorklet Error:", e)
            throw e
        } finally {
            // Cleanup when the coroutine is cancelled (Player.stop()). Drop the node reference
            // FIRST: BOTH post paths (drainControl and the timer's chunk drip) read the field, so
            // after this nothing can post to a port that is about to be disconnected.
            val liveNode = workletNode
            workletNode = null

            try {
                liveNode?.disconnect()
            } catch (e: Throwable) {
                console.error("Error disconnecting AudioWorkletNode", e)
            }

            // Separate try, deliberately: a failed disconnect must never skip the context close.
            // This used to be one block over a `lateinit` local, so when addModule() rejected (a
            // stale cache-buster against a redeployed worklet) the disconnect threw
            // UninitializedPropertyAccessException and close() was never reached - every failed
            // start leaked a live AudioContext until the browser's budget ran out and the player
            // could not start again without a page reload.
            // NonCancellable because this runs in a `finally` during cancellation:
            // `suspendCancellableCoroutine` has already made the job cancelled, so a bare
            // `await()` throws CancellationException immediately. `ctx.close()` itself would
            // still fire (the receiver evaluates first), but every ordinary Player.stop() logged
            // an "Error closing AudioContext" that was only the cancellation, and any cleanup
            // added below this line would be skipped every time.
            try {
                withContext(NonCancellable) { ctx.close().await() }
            } catch (e: Throwable) {
                console.error("Error closing AudioContext", e)
            }
        }
    }

    /**
     * Forward everything queued on the control link to the worklet, right now.
     *
     * The frontend and this backend both live on the main thread, so a command can be handed to
     * the audio thread in the same task that produced it instead of waiting for the next timer
     * tick. That wait used to be up to 10 ms (`setTimeout`, longer under main-thread load) and it
     * was pure latency on the realtime path.
     *
     * The whole ring is drained in order, so CONTROL commands cannot overtake each other: a
     * `StartRealtimeVoice` still arrives after the `RegisterIgnitor` that names its sound. Sample
     * PCM is a different story and always was - `Cmd.Sample.Complete` leaves here into
     * [sampleUploadBuffer] and is dripped out by the timer, so a voice queued behind a sample
     * still reaches the engine long before that sample's bytes do.
     *
     * Must not throw; see [AudioBackend.pump]. Re-entrancy is not a concern (`sendCmd` is an async
     * postMessage and nothing here calls back into the link), and a second call finds the ring
     * empty.
     */
    override fun pump() {
        drainControl()
    }

    /** Drain the control ring into the worklet port. No-op before the worklet exists. */
    private fun drainControl() {
        val node = workletNode ?: return

        while (true) {
            val cmd = commLink.control.receive() ?: break

            when (cmd) {
                // Special handling for Samples ... we split the data for big samples
                is KlangCommLink.Cmd.Sample -> when (cmd) {
                    // Direct forwarding
                    is KlangCommLink.Cmd.Sample.NotFound,
                    is KlangCommLink.Cmd.Sample.Chunk,
                        -> node.port.sendCmd(cmd)

                    is KlangCommLink.Cmd.Sample.Complete -> {
                        // Complete samples will be split and put into the [cmdBuffer]
                        val chunks = cmd.toChunks(128 * 1024)

                        chunks.forEach { chunk -> sampleUploadBuffer.send(chunk) }
                    }
                }

                // Direct forwarding for control commands
                is KlangCommLink.Cmd.Cleanup,
                is KlangCommLink.Cmd.ClearScheduled,
                is KlangCommLink.Cmd.RegisterIgnitor,
                is KlangCommLink.Cmd.RegisterPipeline,
                is KlangCommLink.Cmd.RegisterMaster,
                is KlangCommLink.Cmd.ReplaceVoices,
                is KlangCommLink.Cmd.ScheduleVoice,
                is KlangCommLink.Cmd.ScheduleVoices,
                is KlangCommLink.Cmd.StartRealtimeVoice,
                is KlangCommLink.Cmd.StopRealtimeVoice,
                    -> node.port.sendCmd(cmd)
            }
        }
    }

    /**
     * Calculates the cache buster hash for the current script.
     */
    private fun getCacheBusterHash(): String {
        var hash = ""
        val scripts = window.document.scripts
        for (i in 0 until scripts.length) {
            val src = (scripts[i] as? org.w3c.dom.HTMLScriptElement)?.src ?: continue
            // Look for a typical bundler hash: a dot, followed by at least 8 hex characters, ending in .js
            val match = Regex("""\.([a-fA-F0-9]{8,})\.js$""").find(src)
            if (match != null) {
                hash = match.groupValues[1]
                break
            }
        }

        return hash
    }
}
