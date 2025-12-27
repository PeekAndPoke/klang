package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.createDefaultAudioLoop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

fun strudelPlayer(
    pattern: StrudelPattern,
    options: KlangPlayer.Options,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
): KlangPlayer<StrudelPatternEvent> {
    val cps = options.cyclesPerSecond
    val sampleRate = options.sampleRate

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
        // Now using the generic factory from audio_engine
        audioLoop = createDefaultAudioLoop(options),
        scope = scope
    )
}
