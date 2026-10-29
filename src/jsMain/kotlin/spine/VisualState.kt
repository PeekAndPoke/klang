/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.storage.SeedStorage
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.ops.FrameClock
import io.peekandpoke.ultra.streams.ops.animationFrames
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.ticker

/**
 * The visuals' state, the first part of the Spine (KLANG-00389: the app's data and flows in one layer under the UI;
 * the UI sends to it and receives from it). It hosts the [lamp] over the Motor's plate: the start page and the visuals
 * send it their commands, and the plate and everything that reflects the lamp receive its [light] (the maintainer,
 * 2026-10-29: "something like VisualState owning the lamp and the MotorBackground would get it from there and all
 * others too").
 */
object VisualState {

    /** The one lamp over every plate: the start page's and the visuals'. */
    val lamp = SwingingLamp(seed = SeedStorage.metal)

    /** The lamp's light, worked out on [clock]'s frames while anyone listens: nobody listens, no frames, no work. */
    fun lightOf(lamp: SwingingLamp, clock: FrameClock): Stream<SwingingLamp.Light> =
        ticker(clock).map { frame -> lamp.advanceTo(frame.currentTime) }

    /**
     * The [lamp]'s light, a frame at a time on the browser's animation frames, only while someone listens (the
     * maintainer, 2026-10-29: "Ideally we stop the calculations in SwingingLamp when there is no subscriber").
     */
    val light: Stream<SwingingLamp.Light> = lightOf(lamp = lamp, clock = FrameClock.animationFrames)
}
