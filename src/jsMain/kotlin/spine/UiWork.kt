/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.kraft.utils.WindowController
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.ops.FrameClock
import io.peekandpoke.ultra.streams.ops.TickerFrame
import io.peekandpoke.ultra.streams.ops.animationFrames
import io.peekandpoke.ultra.streams.ops.cutoffWhen
import io.peekandpoke.ultra.streams.ops.distinct
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.ticker

/**
 * The work that only shows things, and the frames it runs on: the scopes, the spectrums, the gauges' needles, the
 * Motor's graphs, the lamp over the plate, the voices' rain. The music plays on.
 *
 * - **When it rests** ([resting]): while the window has no attention (the maintainer, 2026-10-29: "The spine should
 *   also react to the window losing focus and pause all ui work and resume when the window/document is focused
 *   again"): a window left for another app gets animation frames from the browser and draws for no one. The default
 *   pause of every part that has one; a part under a visual rests for that too (the workbench's Motor).
 * - **Its frames** ([frames], [allFrames]): one animation frame stream for everyone (the maintainer, 2026-10-29: "spine
 *   exposes a single animationFrame stream and components subscribe to it and call their step function(). Then we
 *   have the subscription and unsub in one place and maybe also only one animation frame handler"; KLANG-00388). A
 *   component subscribes with Kraft's `stream.subscribe { step() }`, which ends with the component and draws nothing
 *   again by itself; nobody listening, no frame is asked for.
 *
 * One object, the focus taken from Kraft's [WindowController] (the maintainer, 2026-10-29: "I think we can merge Frame
 * and UiWork into one object? Also there already is WindowController in Kraft for detect focus"). Frames that rest on
 * a stream are asked of Kraft itself (ULTRA-00284); until then these three lines do it.
 */
object UiWork {

    /** The page's window controller, Kraft's one for every app, so the app and the UI's rest follow the same focus. */
    val window: WindowController = WindowController.default

    /** True while showing things is pointless: the window has no attention (hidden or not focused). */
    val resting: Stream<Boolean> = window.hasFocus.map { !it }.distinct()

    /** Every animation frame, with its time and the time since the last; for a part with a pause of its own. */
    val allFrames: Stream<TickerFrame> = ticker(FrameClock.animationFrames)

    /** The frames for work that only shows things: none while it rests, so nothing need keep track of it. */
    val frames: Stream<TickerFrame> = allFrames.cutoffWhen(resting)
}
