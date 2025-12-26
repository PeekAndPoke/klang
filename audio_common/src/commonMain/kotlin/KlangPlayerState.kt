package io.peekandpoke.klang.audio_common

import kotlinx.atomicfu.atomic

/** Shared state between fetcher and renderer */
class KlangPlayerState {
    val running = atomic(false)
    val cursorFrame = atomic(0L)
}
