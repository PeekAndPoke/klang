package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.AdsrEnvelope
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata
import io.peekandpoke.klang.audio_bridge.SampleRequest
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink

/**
 * Primes the audio render hot path before the first real voice arrives.
 *
 * Runs a few blocks of rendering through a fully isolated private DSP graph — its own
 * [KlangCommLink], [Cylinders], [VoiceScheduler], and [KlangAudioRenderer]. JIT sees the
 * same classes the real path uses, so it compiles the shared method shapes exactly once.
 * Because the work lives on a throwaway graph, stateful effect tails, limiter gain
 * reduction, and cylinder buffers cannot leak into the real render path when warmup ends
 * — the whole graph is dropped for GC.
 *
 * The caller is responsible for silencing the real output during [isWarming] since the
 * real renderer is not driven at all in this window.
 */
class WarmupRunner(
    private val sampleRate: Int,
    private val blockFrames: Int,
    /** Shared ignitor registry — forked so warmup's synthetic sample doesn't leak into it. */
    sharedIgnitorRegistry: IgnitorRegistry,
    /** Real comm link — only used to emit [KlangCommLink.Feedback.BackendReady] once warmup ends. */
    private val realCommLink: KlangCommLink.BackendEndpoint,
    /** Number of audio blocks to warm up for. 8 blocks ≈ 85 ms at 48 kHz / 512 frames. */
    private val warmupBlocks: Int = 8,
) {
    companion object {
        /** Reserved playback-id — no real song can use this. */
        const val WARMUP_PLAYBACK_ID = "--WARMUP--"

        /** Reserved sample name — no real sample can use this. */
        const val WARMUP_SAMPLE_NAME = "--warmup--"
    }

    private var started = false
    private var finished = false
    private var blocksRun = 0

    /** Shared-registry fork kept until warmup finishes, then released alongside [graph]. */
    private val warmupIgnitorRegistry = sharedIgnitorRegistry.fork()

    /** The isolated warmup DSP graph. Set in [start], nulled in [runBlock] when warmup finishes. */
    private var graph: Graph? = null

    private class Graph(
        val commLink: KlangCommLink,
        val cylinders: Cylinders,
        val voices: VoiceScheduler,
        val renderer: KlangAudioRenderer,
        val outBuffer: ShortArray,
    )

    /** True while the runner is still priming the audio path. */
    val isWarming: Boolean get() = started && !finished

    /** Build the private graph and schedule the warmup voices. Idempotent. */
    fun start() {
        if (started) return
        started = true

        val graphCommLink = KlangCommLink(capacity = 64)
        val graphCylinders = Cylinders(blockFrames = blockFrames, sampleRate = sampleRate)
        val graphVoices = VoiceScheduler(
            VoiceScheduler.Options(
                commLink = graphCommLink.backend,
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                ignitorRegistry = warmupIgnitorRegistry,
                cylinders = graphCylinders,
            )
        )
        graphVoices.setBackendStartTime(0.0)
        val graphRenderer = KlangAudioRenderer(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voices = graphVoices,
            cylinders = graphCylinders,
        )

        // Pre-register the synthetic sample so the sample voice has something to read.
        graphVoices.addSample(
            KlangCommLink.Cmd.Sample.Complete(
                req = SampleRequest(bank = null, sound = WARMUP_SAMPLE_NAME, index = null, note = null),
                note = null,
                pitchHz = 440.0,
                sample = MonoSamplePcm(
                    sampleRate = sampleRate,
                    pcm = FloatArray(64),
                    meta = SampleMetadata.default,
                ),
            )
        )

        // Schedule warmup voices. Full gain is safe — output goes into the isolated graph only.
        graphVoices.scheduleVoices(
            listOf(
                // Synth path — Voice.render + IgnitorSine + filter + cylinder mix + limiter.
                ScheduledVoice(
                    playbackId = WARMUP_PLAYBACK_ID,
                    data = VoiceData.empty.copy(
                        sound = "sine",
                        freqHz = 440.0,
                        adsr = AdsrEnvelope(attack = 0.005, decay = 0.05, sustain = 0.0, release = 0.05),
                    ),
                    startTime = 0.0,
                    gateEndTime = 0.1,
                    playbackStartTime = 0.0,
                ),
                // Sample path — SampleIgnitor lookup + playback.
                ScheduledVoice(
                    playbackId = WARMUP_PLAYBACK_ID,
                    data = VoiceData.empty.copy(
                        sound = WARMUP_SAMPLE_NAME,
                        adsr = AdsrEnvelope(attack = 0.001, decay = 0.05, sustain = 0.0, release = 0.05),
                    ),
                    startTime = 0.0,
                    gateEndTime = 0.1,
                    playbackStartTime = 0.0,
                ),
            )
        )

        graph = Graph(
            commLink = graphCommLink,
            cylinders = graphCylinders,
            voices = graphVoices,
            renderer = graphRenderer,
            outBuffer = ShortArray(blockFrames * 2),
        )
    }

    /**
     * Runs one warmup block on the isolated graph. Returns true while still warming, false once
     * the handshake has been emitted and the graph released.
     */
    fun runBlock(): Boolean {
        val g = graph ?: return false

        g.renderer.renderBlock(cursorFrame = blocksRun * blockFrames, out = g.outBuffer)
        blocksRun++

        if (blocksRun >= warmupBlocks) {
            finished = true
            graph = null // Release the whole graph for GC — nothing from warmup survives.
            realCommLink.feedback.send(KlangCommLink.Feedback.BackendReady())
            return false
        }

        return true
    }
}
