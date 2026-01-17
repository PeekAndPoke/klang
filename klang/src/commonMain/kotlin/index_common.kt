package io.peekandpoke.klang.audio_engine

import io.peekandpoke.klang.audio_engine.KlangPlayer.Options

/**
 * Creates a platform-specific KlangPlayer instance
 */
expect fun klangPlayer(
    options: Options,
): KlangPlayer
