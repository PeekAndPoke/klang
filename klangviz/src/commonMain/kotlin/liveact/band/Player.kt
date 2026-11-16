/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band

/**
 * The players of a band, in the order they enter the scene. The order is fixed and matters: what is laid over
 * everyone in turn (breath, the eyes' waver) goes by it, so it must not depend on which figure's file arrived
 * first.
 *
 * [key] is the player's name in a figure's file (`drums.glb`), in a page's switches (`?head=eimer`) and in the
 * prototype this was ported from.
 */
enum class Player(val key: String) {
    Drums("drums"),
    Bass("bass"),
    Guitar1("guitar1"),

    /** The frontwoman: she plays the song's "trommel" on a trumpet. The key is the part's first name. */
    Eimer("eimer"),
    Marimba("marimba"),
    Guitar2("guitar2");

    companion object {
        /** The player with [key]; null where there is none. */
        fun of(key: String?): Player? = entries.firstOrNull { it.key == key }
    }
}
