/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.storage.SeedStorage
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.ops.TickerFrame
import io.peekandpoke.ultra.streams.ops.map

/**
 * The visuals' state, the first part of the Spine (KLANG-00389: the app's data and flows in one layer under the UI;
 * the UI sends to it and receives from it). It hosts the [lamp] over the Motor's plate: the start page and the visuals
 * send it their commands, and the plate and everything that reflects the lamp receive its [light] (the maintainer,
 * 2026-10-29: "something like VisualState owning the lamp and the MotorBackground would get it from there and all
 * others too").
 */
object VisualState {

    /** A song open on a page, to be played from a visual the page's own way ([play]): its code as it stands there. */
    data class OpenSong(val play: () -> Unit)

    /** The songs offered by the pages mounted now, the latest last ([offerSong]). */
    private val offered = mutableListOf<OpenSong>()

    private val openSongSource = StreamSource<OpenSong?>(null)

    /**
     * The song open on the page under the visuals, the latest offered and not withdrawn; null when no page offers one.
     * The Motor's plate plays it (the maintainer, 2026-10-29: "the first visual has to play the song the user had
     * open... so pass in a song when possible").
     */
    val openSong: Stream<OpenSong?> = openSongSource

    /**
     * A page offers its [song] while it is mounted; the way to withdraw it comes back. Offers, not one slot: the page's
     * router may hold a page twice while it changes, and the copy that leaves must not take the other's song along.
     */
    fun offerSong(song: OpenSong): () -> Unit {
        offered.add(song)
        openSongSource(offered.lastOrNull())

        return {
            offered.remove(song)
            openSongSource(offered.lastOrNull())
        }
    }

    /** The one lamp over every plate: the start page's and the visuals'. */
    val lamp = SwingingLamp(seed = SeedStorage.metal)

    /** The lamp's light, worked out on [frames] while anyone listens: nobody listens, no frames, no work. */
    fun lightOf(lamp: SwingingLamp, frames: Stream<TickerFrame>): Stream<SwingingLamp.Light> =
        frames.map { frame -> lamp.advanceTo(frame.currentTime) }

    /**
     * The [lamp]'s light, a frame at a time on the UI's frames ([Frames.ui]), only while someone listens and the UI does
     * not rest: the lamp waits where it was (the maintainer, 2026-10-29: "Ideally we stop the calculations in
     * SwingingLamp when there is no subscriber").
     */
    val light: Stream<SwingingLamp.Light> = lightOf(lamp = lamp, frames = Frames.ui)
}
