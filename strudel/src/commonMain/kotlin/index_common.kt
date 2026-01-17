package io.peekandpoke.klang.strudel

import io.peekandpoke.klang.audio_engine.KlangPlayback
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.klangPlayer

fun strudelPlayer(
    options: KlangPlayer.Options,
): KlangPlayer {
    return klangPlayer(options = options)
}

fun KlangPlayer.playStrudel(
    pattern: StrudelPattern,
): KlangPlayback {
    return play(
        source = StrudelEventSource(
            pattern = pattern,
            cyclesPerSecond = 0.5, // Default, can be changed via start(options)
            sampleRate = options.sampleRate,
        )
    )
}
