/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.WorkletContract.sendCmd
import io.peekandpoke.klang.audio_be.WorkletContract.sendSample
import io.peekandpoke.klang.audio_be.utils.jsObject
import io.peekandpoke.klang.audio_bridge.AnalyserNode
import io.peekandpoke.klang.audio_bridge.AudioContext
import io.peekandpoke.klang.audio_bridge.AudioContextOptions
import io.peekandpoke.klang.audio_bridge.AudioWorkletNode
import io.peekandpoke.klang.audio_bridge.AudioWorkletNodeOptions
import io.peekandpoke.klang.audio_bridge.GainNode
import io.peekandpoke.klang.audio_bridge.analyzer.SPECTRUM_FFT_SIZE
import io.peekandpoke.klang.audio_bridge.guessDeviceLatencyMs
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.w3c.dom.MessageEvent
import org.w3c.dom.get

/** How fast the master volume follows the knob: the time constant of its glide, short enough to feel direct. */
private const val VOLUME_GLIDE_SECONDS = 0.015

class JsAudioBackend(
    private val config: AudioBackend.Config,
) : AudioBackend {
    private val commLink: KlangCommLink.BackendEndpoint = config.commLink

    // Samples whose copy to transfer could not be allocated on this thread (see [drainControl]); added to the
    // warehouse's `sampleAllocationFailures` in every diagnostics forwarded. Main thread only.
    private var uploadAllocationFailures = 0

    // The analysers for the visualisations, one on each side of the master volume (KLANG-00346, maintainer,
    // 2026-10-26: the volume "should only influence the osci but not the spectrum"): the spectrum reads the song as it
    // is made, the scope what the speakers get
    private var spectrumAnalyser: AnalyserNode? = null
    private var scopeAnalyser: AnalyserNode? = null

    // The master volume (KLANG-00330): a gain between the worklet and the speakers, with the scope's analyser after it
    // and the spectrum's before it. Null before the graph is built and after teardown.
    private var volumeNode: GainNode? = null
    private var audioContext: AudioContext? = null

    // The volume asked for, also before the node exists
    private var outputVolume: Double = 1.0

    // The worklet node, held as a field (not just a local in [run]) so [pump] can reach the port
    // from outside the render loop. Null before the worklet is created and after teardown.
    private var workletNode: AudioWorkletNode? = null

    // Implement AudioVisualizer interface with zero-copy methods
    override val analyzer = JsAudioAnalyzer(waveformNode = { scopeAnalyser }, fftNode = { spectrumAnalyser })

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

        // Back to running when the browser stopped it (KLANG-00399, problem 9)
        val stopKeepingRunning = keepRunning(
            target = document,
            visible = { document.asDynamic().visibilityState == "visible" },
            state = { ctx.state },
            resume = { ctx.resume() },
        )

        ctx.onstatechange = { console.log("JsAudioBackend AudioContext state: ${ctx.state}") }

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

            // The processor is dead for good once it throws out of `process()` (the Web Audio rule). The worklet catches
            // every throw itself (`KlangAudioWorklet`), so this should never fire; if it does, the log says why the
            // sound stopped instead of nothing at all (audit L8-03, L9-01).
            node.onprocessorerror = { event ->
                console.error("JsAudioBackend: the audio worklet's processor stopped after an error, audio is off until a reload", event)
                commLink.feedback.send(
                    KlangCommLink.Feedback.PlaybackFailed(
                        playbackId = KlangCommLink.SYSTEM_PLAYBACK_ID,
                        reason = "the audio worklet's processor stopped after an error",
                    )
                )
            }

            // The analysers for the visualisations (with a fallback if one cannot be made: that view stays dark)
            spectrumAnalyser = createAnalyserOrNull(ctx = ctx, name = "spectrum", fftSize = SPECTRUM_FFT_SIZE)
            scopeAnalyser = createAnalyserOrNull(ctx = ctx, name = "scope", fftSize = 2048)

            val volume = ctx.createGain().apply { gain.value = outputVolume }

            volumeNode = volume
            audioContext = ctx

            // Connect audio graph: worklet, spectrum analyser, volume, scope analyser, destination; an analyser that could
            // not be made is left out, the sound takes the same way without it
            val spectrumTap = spectrumAnalyser
            val scopeTap = scopeAnalyser

            if (spectrumTap != null) {
                node.connect(spectrumTap)
                spectrumTap.connect(volume)
            } else {
                node.connect(volume)
            }

            if (scopeTap != null) {
                volume.connect(scopeTap)
                scopeTap.connect(ctx.destination)
            } else {
                volume.connect(ctx.destination)
            }

            // 3. Setup Feedback Loop (Worklet -> Frontend)
            // We listen for messages from the worklet (e.g. Sample Requests, Position Updates)
            node.port.onmessage = { message: MessageEvent ->
                // We pass all feedback through to the frontend
                val decoded = WorkletContract.decodeFeed(message)

                // What only the main thread knows joins the worklet's report: the context's latency figures, the
                // refusals of the ring this forwards into, the sample copies that could not be made here.
                val enriched = if (decoded is KlangCommLink.Feedback.Diagnostics) {
                    withMainThreadFigures(
                        diagnostics = decoded,
                        baseLatencyMs = ctx.baseLatency * 1000.0,
                        deviceLatencyMs = guessDeviceLatencyMs(ctx.outputLatency * 1000.0),
                        refusedFeedbackSends = commLink.refusedFeedbackSends,
                        uploadAllocationFailures = uploadAllocationFailures,
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
                // Kept for commands queued before the worklet exists: [pump] is inert while
                // [workletNode] is null. NOT a fallback for a second writer - `sendControl` is the
                // only writer of this ring in the tree. (It also dripped the sample chunks out, one per
                // tick, until each sample became one transferred buffer, KLANG-00431 D5.)
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
                // once. Now pump() keeps control flowing, but a dead timer would leave a command queued
                // before the worklet existed undelivered for good.
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
            stopKeepingRunning()
            ctx.onstatechange = null

            // Cleanup when the coroutine is cancelled (Player.stop()). Drop the node reference
            // FIRST: the post path (drainControl, from pump() and the timer) reads the field, so
            // after this nothing can post to a port that is about to be disconnected.
            val liveNode = workletNode
            workletNode = null
            volumeNode = null
            audioContext = null

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

    /** An analyser for one visualisation, or null when the browser cannot make one (that view then stays dark). */
    private fun createAnalyserOrNull(ctx: AudioContext, name: String, fftSize: Int): AnalyserNode? = try {
        ctx.createAnalyser().apply {
            this.fftSize = fftSize
            smoothingTimeConstant = 0.8
        }
    } catch (e: Throwable) {
        console.warn("Failed to create the $name AnalyserNode, its visualization disabled:", e)

        null
    }

    /** The master volume on the gain between the spectrum's analyser and the scope's; see [AudioBackend.setOutputVolume]. */
    override fun setOutputVolume(gain: Double) {
        // NaN-guard: an unreadable volume is silence; the scale already keeps the knob in 0.0 to 1.0
        val safe = if (gain != gain) 0.0 else gain.coerceIn(0.0, 1.0)

        outputVolume = safe

        val node = volumeNode ?: return
        val ctx = audioContext ?: return

        try {
            // A short glide instead of a jump, so turning the knob does not click. The cancel drops a glide already
            // set for this same instant (several moves in one render quantum, or a suspended context whose clock
            // stands still); a glide under way is kept, and the new one takes over from its current value
            node.gain.cancelScheduledValues(ctx.currentTime)
            node.gain.setTargetAtTime(target = safe, startTime = ctx.currentTime, timeConstant = VOLUME_GLIDE_SECONDS)
        } catch (e: Throwable) {
            console.warn("JsAudioBackend: could not set the volume", e)
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
     * The whole ring is drained in order, so commands cannot overtake each other: a
     * `StartRealtimeVoice` still arrives after the `RegisterIgnitor` that names its sound, and a
     * sample (one `Cmd.Sample.Complete`, its PCM a transferred buffer, KLANG-00431 D5) arrives in
     * its place among them. Until 2026-11-11 a sample left in 1 MB chunks dripped out by the timer.
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
                // A whole sample: one message, its PCM a copy made here and transferred (`WorkletContract.sendSample`)
                is KlangCommLink.Cmd.Sample.Complete -> if (!node.port.sendSample(cmd)) {
                    uploadAllocationFailed(cmd)
                }

                // Direct forwarding for everything else
                is KlangCommLink.Cmd.Sample.NotFound,
                is KlangCommLink.Cmd.Cleanup,
                is KlangCommLink.Cmd.RegisterIgnitor,
                is KlangCommLink.Cmd.RegisterKatalyst,
                is KlangCommLink.Cmd.ReplaceVoices,
                is KlangCommLink.Cmd.ScheduleVoices,
                is KlangCommLink.Cmd.SetGain,
                is KlangCommLink.Cmd.StartRealtimeVoice,
                is KlangCommLink.Cmd.StopRealtimeVoice,
                    -> node.port.sendCmd(cmd)
            }
        }
    }

    /**
     * The copy of [cmd]'s PCM could not be allocated on this thread (KLANG-00431 D5; the store's own allocation
     * failure before, resource warehouse 2g): the sample is silent, as a missing one is. The worklet is told it is not
     * found, so a voice that asks for it is counted soundless and it is not asked for again; the frontend's preloader
     * gets the ack a success sends, as it awaits that with no timeout; the diagnostics count it.
     */
    private fun uploadAllocationFailed(cmd: KlangCommLink.Cmd.Sample.Complete) {
        uploadAllocationFailures++
        console.error("JsAudioBackend: no memory for the sample ${cmd.req.sound}, it stays silent")
        workletNode?.port?.sendCmd(KlangCommLink.Cmd.Sample.NotFound(req = cmd.req))
        commLink.feedback.send(KlangCommLink.Feedback.SampleReceived(playbackId = cmd.playbackId, req = cmd.req))
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

/**
 * The worklet's [diagnostics] with what only the main thread knows: the audio context's latency figures (in ms) added
 * to the engine's own latency the worklet reports (KLANG-00431 D3: the house limiter's lookahead and the longest
 * master chain's, which the context's figures cannot see), the refusals of the main thread's feedback ring added to
 * the worklet's (D10), and the sample copies the main thread could not make added to the warehouse's allocation
 * failures (D5). Reporting only: nothing compensates the audio.
 */
internal fun withMainThreadFigures(
    diagnostics: KlangCommLink.Feedback.Diagnostics,
    baseLatencyMs: Double,
    deviceLatencyMs: Double,
    refusedFeedbackSends: Double,
    uploadAllocationFailures: Int,
): KlangCommLink.Feedback.Diagnostics {
    val warehouse = diagnostics.warehouse

    return diagnostics.copy(
        baseLatencyMs = baseLatencyMs,
        outputDeviceLatencyMs = deviceLatencyMs,
        outputLatencyMs = diagnostics.outputLatencyMs + baseLatencyMs + deviceLatencyMs,
        refusedFeedbackSends = diagnostics.refusedFeedbackSends + refusedFeedbackSends,
        warehouse = if (uploadAllocationFailures == 0) {
            warehouse
        } else {
            warehouse.copy(sampleAllocationFailures = warehouse.sampleAllocationFailures + uploadAllocationFailures)
        },
    )
}
