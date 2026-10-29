/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.playOnce
import io.peekandpoke.klang.sprudel.SprudelPattern

/** This jingle's code as a pattern; null, and said in the console, when it does not compile. */
fun Jingle.compile(): SprudelPattern? = try {
    SprudelPattern.compile(Player.createEngine(), code)
} catch (e: Throwable) {
    console.error("Jingle $id does not compile", e)
    null
}

/**
 * Plays [pattern], [jingle] compiled, as the start page does: its cycles at its speed, then it stops by itself. The
 * playback comes back, to stop it sooner.
 */
fun KlangPlayer.playJingle(jingle: Jingle, pattern: SprudelPattern): KlangCyclicPlayback =
    playOnce(pattern = pattern, cycles = jingle.cycles).also {
        it.start(KlangCyclicPlayback.Options(rpm = jingle.rpm))
    }
