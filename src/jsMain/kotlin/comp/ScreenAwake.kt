/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.spine.Player
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.utils.WakeLockComponent
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.streams.Stream
import kotlinx.css.Display
import kotlinx.css.display
import kotlinx.html.Tag
import kotlinx.html.div

@Suppress("FunctionName")
fun Tag.ScreenAwake(
    playing: Stream<Boolean> = Player.playing,
) = comp(
    ScreenAwake.Props(playing = playing)
) {
    ScreenAwake(it)
}

/**
 * Keeps the screen awake while something plays (the maintainer, 2026-10-31: whenever a song is playing, on every page,
 * not only in the visuals): Kraft's [WakeLockComponent] is mounted while [Props.playing] says so. A phone that locks
 * its screen also stops the sound. Mounted once, at the app's root.
 */
class ScreenAwake(ctx: Ctx<Props>) : Component<ScreenAwake.Props>(ctx) {

    //  PROPS  //////////////////////////////////////////////////////////////////////////////////////////////////

    data class Props(
        val playing: Stream<Boolean>,
    )

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val playing by subscribingTo(props.playing)

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        div(classes = "screen-awake") {
            css { display = Display.none }

            if (playing) {
                WakeLockComponent()
            }
        }
    }
}
