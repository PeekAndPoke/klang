/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.figure.Traits
import io.peekandpoke.klang.viz.liveact.band.skills.Skill

// A member's sheet, and what it is made of: the dress, the character, the bearing, the skills.

/**
 * A member's sheet: only what is the character's own (the maintainer, 2026-11-17: "name, figure, character,
 * extras ... so basically only things intrinsic to the character"). Which instrument a member plays and which part
 * of a song is the cast's; where they stand and how they walk in is the stage plot's.
 *
 * [player]: who it is in the band (the camera, the eyes and the page's switches go by it). [dress]: what the figure
 * wears (and has: the snakes in a shadow are dress). [character]: how they move with the music. [bearing]: how they
 * carry themselves over an instrument on a strap; null where they have no bearing of their own. [skills]: what they
 * can do on their own, beyond playing and moving with the beat, in the order of rank (see [Skill]); one without a
 * skill simply does not do it.
 */
data class Member(
    val player: Player,
    val dress: Dress,
    val character: Character,
    val bearing: Bearing?,
    val skills: List<Skill>,
) {
    /** Their name, as the band calls them: the player's. */
    val name: String get() = player.name

    /** The figure's file, as the figure script (`klangviz/figures/`) writes it: `<key>.glb`. */
    val figure: String get() = "${player.key}.glb"
}

/**
 * What a member wears. [cloth]: the pattern, seen only in how the body throws back the light. [glitter]: the share of
 * spangles in the skin (everyone glitters; the two women a little more). [combedBack]: the hair is worn as a pattern
 * in the light, combed straight back. [shadowSnakes]: hair seen only as a shadow, Medusa's snakes on the head (see
 * `ShadowHair`). The band makes them and sets them every frame, once the head is held at last. None with
 * `?nosnakes`; drawn as well, to look at them, with `?snakes`.
 */
data class Dress(
    val cloth: ClothPattern,
    val glitter: Double,
    val combedBack: Boolean = false,
    val shadowSnakes: Boolean = false,
)

/**
 * A member's character: how they move with the music apart from playing (see `Mover`), the [traits], with the [seed]
 * of their own chance.
 */
data class Character(val seed: Double, val traits: Traits)

/**
 * How a member carries themselves over an instrument on a strap: [lean], how the trunk leans (back is negative);
 * [bang], how much the head goes; [bounce], how far the hips dip on the beat; [step], how much they dance from one
 * leg to the other, two beats on each (0: the feet stay planted; 1: the weight goes right over each leg in turn).
 */
data class Bearing(val lean: Double, val bang: Double, val bounce: Double, val step: Double = 0.0)
