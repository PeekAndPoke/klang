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
 * Each has a name of their own (the maintainer, 2026-11-17). [key] is that name in a figure's file (`wucht.glb`),
 * in the script that builds the figure (`klangviz/figures/`) and in a page's switches (`?head=sirene`). Which part
 * of the song each one plays, and on which instrument, is the cast's (`SchmetterlingCast`); what is each one's own
 * is on their sheet (`Members`).
 */
enum class Player(val key: String) {
    /** The drummer: the muscular one. */
    Wucht("wucht"),

    /** The bass player: tall and thin. */
    Beben("beben"),

    /** The lead guitarist: slim, long hair, the virtuoso. */
    Skalpell("skalpell"),

    /** The frontwoman: she plays the song's "trommel" on a trumpet. */
    Sirene("sirene"),

    /** The marimba player: the riddle of the band. */
    Rune("rune"),

    /** The rhythm guitarist: the heavier one with the cap, less fine than [Skalpell] and with the power. */
    Klaue("klaue");

    companion object {
        /** The player with [key]; null where there is none. */
        fun of(key: String?): Player? = entries.firstOrNull { it.key == key }
    }
}
