package io.peekandpoke.klang.strudel

import kotlinx.atomicfu.atomic

class StrudelPlayerState {
    val running = atomic(false)
    val cursorFrame = atomic(0L)
}
