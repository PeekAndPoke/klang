package io.peekandpoke.klang.audio_be.worklet

import io.peekandpoke.klang.audio_be.KlangAudioRenderer
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.osci.oscillators
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_be.worklet.WorkletContract.sendFeed
import io.peekandpoke.klang.audio_bridge.AudioWorkletProcessor
import io.peekandpoke.klang.audio_bridge.KlangTime
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import org.khronos.webgl.Float32Array
import org.khronos.webgl.set
import kotlin.js.Date

@JsName("KlangAudioWorklet")
class KlangAudioWorklet : AudioWorkletProcessor {

    class Ctx(
        val sampleRate: Int,
        val blockFrames: Int,
    ) {
        init {
            console.log("[WORKLET] Initialized. Sample rate: $sampleRate, block frames: $blockFrames")
        }

        val commLink = KlangCommLink()
        val klangTime = KlangTime.create()  // Creates AudioWorklet-specific time source

        // Core DSP components
        val orbits = Orbits(
            blockFrames = blockFrames,
            sampleRate = sampleRate
        )

        val voices = VoiceScheduler(
            VoiceScheduler.Options(
                commLink = commLink.backend,
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                oscillators = oscillators(sampleRate = sampleRate),
                orbits = orbits,
                // Used for performance measurement only
                performanceTimeMs = { Date.now() },
            )
        )

        val renderer = KlangAudioRenderer(
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voices = voices,
            orbits = orbits
        )

        // Buffers
        val renderBuffer = ShortArray(blockFrames * 2) // 16-bit Stereo PCM (2 shorts per frame)
        var cursorFrame = 0L

        var isPlaying = true
    }

    private var ctx: Ctx? = null

    @JsName("init")
    private fun init(outputs: Array<Array<Float32Array>>, block: Ctx.() -> Boolean): Boolean {

        fun makeContext(): Ctx {
            // Dynamic detection of environment parameters
            val sampleRate = (js("sampleRate") as Number).toInt()

            // Detect block size from the first output channel
            val output = outputs[0]
            val numChannels = output.size
            // Fallback to 128 if no channels (unlikely)
            val blockFrames = if (numChannels > 0) output[0].length else 128

            val ctx = Ctx(sampleRate, blockFrames)

            // Initialize KlangTime with current frame
            ctx.klangTime.updateCurrentFrame(ctx.cursorFrame)

            // Set backend start time
            ctx.voices.setBackendStartTime(ctx.klangTime.internalMsNow() / 1000.0)

            return ctx
        }

        return (ctx ?: makeContext()).let { ctx ->
            this.ctx = ctx

            // Listening (Receiving from Main Thread)
            port.onmessage = { message ->
                WorkletContract.decodeCmd(message).also { cmd ->
                    // console.log("[WORKLET] decoded cmd", cmd::class.simpleName, cmd)

                    when (cmd) {
                        is KlangCommLink.Cmd.ScheduleVoice -> {
                            ctx.voices.scheduleVoice(voice = cmd.voice, clearScheduled = cmd.clearScheduled)
                        }

                        is KlangCommLink.Cmd.ReplaceVoices -> {
                            ctx.voices.replaceVoices(cmd.playbackId, cmd.voices)
                        }

                        is KlangCommLink.Cmd.Cleanup -> {
                            ctx.voices.cleanup(cmd.playbackId)
                        }

                        is KlangCommLink.Cmd.ClearScheduled -> {
                            ctx.voices.clearScheduled(cmd.playbackId)
                        }

                        is KlangCommLink.Cmd.Sample -> ctx.voices.addSample(msg = cmd)
                    }
                }
            }

            block(ctx)
        }
    }

    override fun process(
        inputs: Array<Array<Float32Array>>,
        outputs: Array<Array<Float32Array>>,
        parameters: dynamic,
    ): Boolean = init(outputs) {
        if (!isPlaying) return@init true

        // Update KlangTime with current frame for accurate timing
        klangTime.updateCurrentFrame(cursorFrame)

        // Port 0
        val output = outputs[0]
        val numChannels = output.size
        if (numChannels == 0) return@init true

        // 1. Render the block into our intermediate ShortArray
        renderer.renderBlock(cursorFrame, renderBuffer)

        // 2. Convert PCM 16-bit back to Float32 for Web Audio
        // renderer.renderBlock interleaves L/R: [L, R, L, R, ...]
        for (i in 0 until blockFrames) {
            val idx = i * 2

            // Read Short and normalize to -1.0..1.0
            val lSample = renderBuffer[idx].toFloat() / Short.MAX_VALUE
            val rSample = renderBuffer[idx + 1].toFloat() / Short.MAX_VALUE

            // Write to output channels
            output[0][i] = lSample
            if (numChannels > 1) {
                output[1][i] = rSample
            }
        }

        cursorFrame += blockFrames

        // Forward all feedback messages
        while (true) {
            val feed = commLink.frontend.feedback.receive() ?: break
            port.sendFeed(feed)

            // console.log("[WORKLET] Sending feedback to frontend:", feed)
        }

        true
    }
}
