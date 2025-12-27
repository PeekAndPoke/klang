package io.peekandpoke.klang.audio_be.worklet

import io.peekandpoke.klang.audio_be.AudioWorkletProcessor
import io.peekandpoke.klang.audio_be.KlangAudioRenderer
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.osci.oscillators
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_be.worklet.WorkletContract.sendFeed
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import org.khronos.webgl.Float32Array
import org.khronos.webgl.set

@JsName("KlangAudioWorklet")
class KlangAudioWorklet : AudioWorkletProcessor {

    class Ctx {
        val commLink = KlangCommLink()

        val sampleRate = 44100
        val blockFrames = 128 // Standard AudioWorklet block size

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
            )
        )
        val renderer = KlangAudioRenderer(blockFrames, voices, orbits)

        // Buffers
        val renderBuffer = ByteArray(blockFrames * 4) // 16-bit Stereo PCM (4 bytes per frame)
        var cursorFrame = 0L

        var isPlaying = true
    }

    private var ctx: Ctx? = null

    @JsName("init")
    private fun init(block: Ctx.() -> Boolean): Boolean {
        return (ctx ?: Ctx()).let { ctx ->
            this.ctx = ctx

            // Listening (Receiving from Main Thread)
            port.onmessage = { message ->
                val cmd = WorkletContract.decodeCmd(message)

                console.log("[WORKLET] decoded cmd", cmd::class.simpleName, cmd)

                when (cmd) {
                    is KlangCommLink.Cmd.ScheduleVoice -> ctx.voices.schedule(cmd.voice)
                    else -> Unit
                }

//                console.log("KlangAudioProcessor onmessage", e)
//
//                val msg = e.data.toString()
//                if (msg == "play") ctx.isPlaying = true
//                if (msg == "stop") ctx.isPlaying = false
            }

            block(ctx)
        }
    }

    override fun process(
        inputs: Array<Array<Float32Array>>,
        outputs: Array<Array<Float32Array>>,
        parameters: dynamic,
    ): Boolean = init {
        if (!isPlaying) return@init true

        // Port 0
        val output = outputs[0]
        val numChannels = output.size
        if (numChannels == 0) return@init true

        // 1. Render the block into our intermediate ByteArray (PCM 16-bit)
        renderer.renderBlock(cursorFrame, renderBuffer)

        // 2. Convert PCM 16-bit back to Float32 for Web Audio
        // renderer.renderBlock interleaves L/R: [L_low, L_high, R_low, R_high, ...]
        for (i in 0 until blockFrames) {
            val idx = i * 4

            // Read Little Endian Int16 and normalize to -1.0..1.0
            val lInt = (renderBuffer[idx].toInt() and 0xFF) or (renderBuffer[idx + 1].toInt() shl 8)
            val rInt = (renderBuffer[idx + 2].toInt() and 0xFF) or (renderBuffer[idx + 3].toInt() shl 8)

            // Convert to signed short and normalize
            val lSample = lInt.toShort().toFloat() / Short.MAX_VALUE
            val rSample = rInt.toShort().toFloat() / Short.MAX_VALUE

            // Write to output channels
            output[0][i] = lSample
            if (numChannels > 1) {
                output[1][i] = rSample
            }
        }

        cursorFrame += blockFrames

        // Tell the frontend about the cursor update
        port.sendFeed(KlangCommLink.Feedback.UpdateCursorFrame(frame = cursorFrame))

        // Notify main thread of current position (optional but helpful for UI)
        // port.postMessage(js("{type: 'POSITION', frame: ctx.cursorFrame}"))

        true
    }
}
