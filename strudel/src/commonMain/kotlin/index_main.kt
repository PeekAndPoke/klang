package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.KlangPlayer2
import io.peekandpoke.klang.audio_engine.klangPlayer

fun StrudelPatternEvent.toScheduledVoice(options: KlangPlayer.Options): ScheduledVoice {
    val event = this

    val cps = options.cyclesPerSecond
    val sampleRate = options.sampleRate

    val secPerCycle = 1.0 / cps
    val framesPerCycle = secPerCycle * sampleRate.toDouble()

    val startFrame = (event.begin * framesPerCycle).toLong()
    val durFrames = (event.dur * framesPerCycle).toLong().coerceAtLeast(1L)
    val releaseSec = event.data.release ?: 0.05
    val releaseFrames = (releaseSec * sampleRate).toLong()

    return ScheduledVoice(
        data = event.data,
        startFrame = startFrame,
        endFrame = startFrame + durFrames + releaseFrames,
        gateEndFrame = startFrame + durFrames,
    )
}

fun strudelPlayer(
    pattern: StrudelPattern,
    options: KlangPlayer.Options,
): KlangPlayer2<StrudelPatternEvent> {
    return klangPlayer(
        source = pattern.asEventSource(),
        options = options,
        transform = { event -> event.toScheduledVoice(options) },
    )
}
