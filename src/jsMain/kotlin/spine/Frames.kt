/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.ops.FrameClock
import io.peekandpoke.ultra.streams.ops.TickerFrame
import io.peekandpoke.ultra.streams.ops.animationFrames
import io.peekandpoke.ultra.streams.ops.cutoffWhen
import io.peekandpoke.ultra.streams.ops.ticker

/**
 * The animation frames, one handler for everyone (the maintainer, 2026-10-29: "spine exposes a single animationFrame
 * stream and components subscribe to it and call their step function(). Then we have the subscription and unsub in one
 * place and maybe also only one animation frame handler"; KLANG-00388). A component subscribes with Kraft's
 * `stream.subscribe { step() }`, which ends with the component and draws nothing again by itself; nobody listening, no
 * frame is asked for.
 */
object Frames {

    /** Every animation frame, with its time and the time since the last. */
    val all: Stream<TickerFrame> = ticker(FrameClock.animationFrames)

    /** The frames for work that only shows things: none while it rests ([UiWork]), so nothing need keep track of it. */
    val ui: Stream<TickerFrame> = all.cutoffWhen(UiWork.resting)
}
