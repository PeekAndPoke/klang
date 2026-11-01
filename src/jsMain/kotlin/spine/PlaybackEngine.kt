/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.script.KlangScriptEngine
import io.peekandpoke.ultra.streams.Stream

/**
 * What a code playback needs of the engine (KLANG-00389): the [Player] in the app; in the specs a player without a
 * backend, so a playback's rules run without audio.
 */
interface PlaybackEngine {
    /** Whether the engine is loading, ready or failed. */
    val status: Stream<Player.Status>

    /** The player, once it is there. */
    suspend fun readyPlayer(): KlangPlayer

    /** A script engine to compile a playback's code with. */
    fun scriptEngine(): KlangScriptEngine
}
