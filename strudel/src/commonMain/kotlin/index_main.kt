package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.createAudioLoop
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.osci.oscillators
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_engine.KlangPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.math.tanh

fun strudelPlayer(
    pattern: StrudelPattern,
    options: KlangPlayer.Options,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
): KlangPlayer<StrudelPatternEvent> {
    val cps = options.cps
    val sampleRate = options.sampleRate
    val blockFrames = options.blockSize
    val samples = options.samples

    return KlangPlayer(
        source = pattern.asEventSource(),
        options = options,
        transform = { event ->
            val secPerCycle = 1.0 / cps
            val framesPerCycle = secPerCycle * sampleRate.toDouble()

            val startFrame = (event.begin * framesPerCycle).toLong()
            val durFrames = (event.dur * framesPerCycle).toLong().coerceAtLeast(1L)
            val releaseSec = event.data.release ?: 0.05
            val releaseFrames = (releaseSec * sampleRate).toLong()

            ScheduledVoice(
                data = event.data,
                startFrame = startFrame,
                endFrame = startFrame + durFrames + releaseFrames,
                gateEndFrame = startFrame + durFrames,
            )
        },
        audioLoop = { state, commLink ->
            // TODO: this is wrong here ...
            //  - The audio loop creates the orbits and everything by itself

            val orbits = Orbits(maxOrbits = 16, blockFrames = blockFrames, sampleRate = sampleRate)
            val voices = StrudelVoices(
                StrudelVoices.Options(
                    sampleRate = sampleRate,
                    blockFrames = blockFrames,
                    oscillators = oscillators(sampleRate),
                    samples = samples,
                    orbits = orbits,
                )
            )

            val mix = StereoBuffer(blockFrames)
            val loop = createAudioLoop(sampleRate, blockFrames)

            loop.runLoop(
                state = state,
                commLink = commLink,
                onSchedule = { evt -> voices.schedule(evt) },
                renderBlock = { out ->
                    val blockStart = state.cursorFrame()

                    mix.clear()
                    orbits.clearAll()

                    voices.process(blockStart)
                    orbits.processAndMix(mix)

                    val left = mix.left
                    val right = mix.right

                    for (i in 0 until blockFrames) {
                        val l = (tanh(left[i]).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()
                        val r = (tanh(right[i]).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()

                        val idx = i * 4
                        out[idx] = (l and 0xff).toByte()
                        out[idx + 1] = ((l ushr 8) and 0xff).toByte()
                        out[idx + 2] = (r and 0xff).toByte()
                        out[idx + 3] = ((r ushr 8) and 0xff).toByte()
                    }

                    state.cursorFrame(blockStart + blockFrames)
                }
            )
        },
        scope = scope
    )
}
