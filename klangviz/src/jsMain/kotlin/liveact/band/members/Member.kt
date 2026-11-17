/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.figure.Traits

// A member's sheet, and what it is made of: the dress, the character, the bearing, the extras.

/**
 * A member's sheet: only what is the character's own (the maintainer, 2026-11-17: "name, figure, character,
 * extras ... so basically only things intrinsic to the character"). Which instrument a member plays and which part
 * of a song is the cast's; where they stand and how they walk in is the stage plot's.
 *
 * [name]: as the band calls them. [player]: who it is in the band (the camera, the eyes and the page's switches go by
 * it). [dress]: what the figure wears. [character]: how they move with the music. [bearing]: how they carry
 * themselves over an instrument on a strap; null where they have no bearing of their own. [extras]: what only this
 * member has.
 */
abstract class Member(
    val name: String,
    val player: Player,
    val dress: Dress,
    val character: Character,
    val bearing: Bearing?,
    val extras: List<Extra> = emptyList(),
) {
    /** The figure's file, as the figure script (`klangviz/figures/`) writes it: `<key>.glb`. */
    val figure: String get() = "${player.key}.glb"
}

/**
 * What a member wears. [cloth]: the pattern, seen only in how the body throws back the light. [glitter]: the share of
 * spangles in the skin (everyone glitters; the two women a little more). [combedBack]: the hair is worn as a pattern
 * in the light, combed straight back.
 */
data class Dress(val cloth: ClothPattern, val glitter: Double, val combedBack: Boolean = false)

/**
 * A member's character: how they move with the music apart from playing (see `Mover`), the [traits], with the [seed]
 * of their own chance.
 */
data class Character(val seed: Double, val traits: Traits)

/**
 * How a member carries themselves over an instrument on a strap: [lean], how the trunk leans (back is negative);
 * [bang], how much the head goes; [bounce], how far the hips dip on the beat.
 */
data class Bearing(val lean: Double, val bang: Double, val bounce: Double)

/** What only one member has, beyond what every figure has. The band applies each (see `Notstrom`). */
sealed interface Extra {
    /**
     * Hair seen only as a shadow: Medusa's snakes on the head (see `ShadowHair`). The band makes them and sets them
     * every frame, once the head is held at last. None with `?nosnakes`; drawn as well, to look at them, with
     * `?snakes`.
     */
    data object ShadowSnakes : Extra

    /**
     * Looks at the camera every now and then: a chance every [every] seconds, of which [chance] are taken (see
     * `Glance`). Whoever plays a strung instrument performs it.
     */
    data class GlanceAtCamera(val every: Double, val chance: Double) : Extra
}
