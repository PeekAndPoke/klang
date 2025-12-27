package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.osci.oscillators
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler
import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import io.peekandpoke.klang.audio_bridge.infra.KlangPlayerState

/**
 * A standard backend implementation that wires together the VoiceScheduler, Orbits, and Renderer.
 * It uses the platform-specific [createAudioLoop] driver to run the audio.
 */
class KlangAudioBackend(
    private val sampleRate: Int,
    private val blockFrames: Int,
) {
    suspend fun run(
        state: KlangPlayerState,
        commLink: KlangCommLink.BackendEndpoint,
    ) {

        // 1. Setup DSP Graph
        val orbits = Orbits(
            maxOrbits = 16,
            blockFrames = blockFrames,
            sampleRate = sampleRate
        )

        val voices = VoiceScheduler(
            VoiceScheduler.Options(
                commLink = commLink,
                sampleRate = sampleRate,
                blockFrames = blockFrames,
                oscillators = oscillators(sampleRate),
                orbits = orbits,
            )
        )

        // 2. Create Renderer
        val renderer = KlangAudioRenderer(
            blockFrames = blockFrames,
            voices = voices,
            orbits = orbits
        )

        // 3. Create Driver
        val driver = createAudioLoop(sampleRate, blockFrames)

        // 4. Run Loop
        driver.runLoop(
            state = state,
            commLink = commLink,
            onCommand = { cmd ->
                when (cmd) {
                    is KlangCommLink.Cmd.ScheduleVoice ->
                        voices.schedule(cmd.voice)

                    is KlangCommLink.Cmd.Sample ->
                        voices.addSample(msg = cmd)
                }
            },
            renderBlock = { out ->
                val currentFrame = state.cursorFrame()

                // Render into buffer (State Read)
                renderer.renderBlock(cursorFrame = currentFrame, out = out)

                // Advance Cursor (State Write)
                state.cursorFrame(currentFrame + blockFrames)
            }
        )
    }
}
