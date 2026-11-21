/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.meet

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.smooth
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sin

/**
 * A place on the floor of "Meet the Band": [x] to the right as the camera sees it, [z] toward the camera, in metres,
 * and [facing], the way a member standing there faces, in radians about the vertical (0: toward the camera; a figure
 * turned by it in three.js faces that way).
 */
data class Spot(val x: Double, val z: Double, val facing: Double)

/**
 * The layout of "Meet the Band" in plain numbers: a hexagon of six fence panels round a middle point, and outside it,
 * before the middle of each panel, one member of the band, facing out. The hexagon with its members turns about the
 * vertical (the carousel, [Carousel]), so that one member at a time stands at the front, toward the camera, in the
 * light of the lamp on the floor before them.
 *
 * Angles: a member's place round the hexagon ([angleOf]) is measured in the carousel's own turn, 0 at the front and
 * growing to the right as the camera sees it; the carousel's turn ([turnFor]) adds to it, and where the sum is 0 the
 * member stands at the front. The front member stands at the world's origin, the hexagon's middle [MIDDLE_Z] behind.
 */
object MeetLayout {
    /** The members round the hexagon, from the front to the right: Sirene first. */
    val ORDER: List<Player> = listOf(Player.Sirene, Player.Skalpell, Player.Beben, Player.Wucht, Player.Rune, Player.Klaue)

    /** The hexagon's sides, and the angle from one member to the next, in radians. */
    const val SIDES: Int = 6
    const val STEP: Double = 2 * PI / SIDES

    /** From the middle to the mesh of a panel, at its middle, in metres. */
    const val MESH: Double = 4.6

    /**
     * From the middle to the dark surface behind the mesh, which takes the shadows: 1.25 m behind the mesh, so 1.6 m
     * behind the member. With the lamp 2 m before them ([LAMP]) their shadow on it is 1.8 times their size.
     */
    const val BACK: Double = 3.35

    /** From the middle to where a member stands: 0.35 m before the mesh. */
    const val PLACE: Double = 4.95

    /** From the middle to the lamp fixture before each member's feet: 0.7 m before them. */
    const val FIXTURE: Double = 5.65

    /**
     * From the middle to the lamp that casts the shadows, low before the front place: 2 m before the member. Nearer,
     * the shadow grows and stretches up steeply (a low lamp close by throws what is high far higher); farther, it
     * shrinks. Tuned by the maintainer's eye, 2026-11-21: at 1.6 m, the shadow twice the figure, "the shadows a bit too
     * large, meaning the light is too close. Not the light visible on the floor but the rendering light"; at 2.8 m,
     * a little over one and a half times, "the shadows in the carussel should be bigger ... moving the light closer
     * and a bit higher".
     */
    const val LAMP: Double = 6.95

    /**
     * How high the lamp that casts the shadows shines from, in metres: a little above a fixture's glowing top (it
     * was as low as that, 0.2), so the shadow stretches up less steeply.
     */
    const val LAMP_HIGH: Double = 0.45

    /** Where the hexagon's middle stands along z: the front member at the world's origin. */
    const val MIDDLE_Z: Double = -PLACE

    /**
     * How far from the front a fixture still glows, in radians: it is full at the front, fades as its member turns
     * away, and is dark a little past half the way to the next place ([glow]).
     */
    const val GLOW_REACH: Double = 0.75

    /** How far the lamp dips halfway between two places, as a share of its light ([dip]). */
    const val DIP: Double = 0.3

    /**
     * What the camera frames ([fov]): [FRAME_WIDE] by [FRAME_HIGH] metres, [FRAME_AT] metres before it (the surface
     * behind the front member): the member and the shadow above and round them.
     */
    const val FRAME_WIDE: Double = 3.2
    const val FRAME_HIGH: Double = 6.0
    const val FRAME_AT: Double = 7.45

    /**
     * The camera, fixed: how high it stands, and how high on the surface behind the front member it looks at; it
     * stands [FRAME_AT] before that surface ([CAMERA_Z]), straight before the front member.
     */
    const val CAMERA_HIGH: Double = 1.4
    const val LOOK_HIGH: Double = 1.7
    const val CAMERA_Z: Double = MIDDLE_Z + BACK + FRAME_AT

    /** Where [player] stands round the hexagon, in the carousel's own turn (see [MeetLayout]): 0 for Sirene. */
    fun angleOf(player: Player): Double = ORDER.indexOf(player) * STEP

    /**
     * The member after [player] round the hexagon ([ORDER], after the last the first): the one standing to the right
     * of the front, who comes in from the right as the carousel turns to them.
     */
    fun next(player: Player): Player = ORDER[(ORDER.indexOf(player) + 1) % ORDER.size]

    /** The member before [player] round the hexagon ([ORDER], before the first the last): the one to the left. */
    fun previous(player: Player): Player = ORDER[(ORDER.indexOf(player) + ORDER.size - 1) % ORDER.size]

    /** The carousel's turn that brings [player] to the front. */
    fun turnFor(player: Player): Double = -angleOf(player)

    /** Which way [player] faces with the carousel at [turn], in the world: 0 at the front. */
    fun facing(player: Player, turn: Double): Double = angleOf(player) + turn

    /** How far [angle] (radians, any number of rounds) lies from the front, either way: 0 to pi. */
    fun offFront(angle: Double): Double = abs(Carousel.wrap(angle))

    /** The place [radius] metres out from the hexagon's middle in the world direction [angle] (0: toward the camera). */
    fun at(radius: Double, angle: Double): Spot = Spot(x = radius * sin(angle), z = MIDDLE_Z + radius * cos(angle), facing = angle)

    /** Where [player] stands with the carousel at [turn], facing out. */
    fun spotOf(player: Player, turn: Double): Spot = at(radius = PLACE, angle = facing(player = player, turn = turn))

    /**
     * How brightly a fixture glows whose member faces [angle] (radians from the front, either way): 1 at the front,
     * easing down to 0 at [GLOW_REACH] and beyond.
     */
    fun glow(angle: Double): Double = 1.0 - smooth(offFront(angle) / GLOW_REACH)

    /**
     * How bright the lamp at the front is with the carousel at [turn]: full where a member stands at the front, and
     * dipping by [DIP] halfway between two places, so the light seems to look for the next one.
     */
    fun dip(turn: Double): Double {
        val steps = turn / STEP
        val between = abs(steps - round(steps)) / 0.5

        return 1.0 - DIP * smooth(between)
    }

    /**
     * The camera's vertical field of view, in degrees, for a picture [aspect] wide to its height (width over height):
     * so that the frame ([FRAME_WIDE] by [FRAME_HIGH] at [FRAME_AT]) fits by the narrower side. A wide picture shows
     * the frame's height and more to the sides (the neighbouring panels); a tall one its width and more above and
     * below. An aspect of no number or not above 0 counts as square.
     */
    fun fov(aspect: Double): Double {
        // NaN-guard: an aspect of no number fails the comparison and counts as square
        val wide = if (aspect > 0.0) aspect else 1.0
        val tall = atan(FRAME_HIGH / 2 / FRAME_AT)
        val across = atan(FRAME_WIDE / 2 / FRAME_AT / wide)

        return 2 * max(tall, across) * 180.0 / PI
    }

    /**
     * How high on the surface behind the front member the camera looks, for a picture [aspect] wide to its height: at
     * [LOOK_HIGH] where the picture is wide enough to show the frame's height ([fov]); on a taller one the frame's top
     * stays where it is on a wide one, and all the height the picture shows more goes below, to the floor (where an
     * app's words can stand under the lamp). An aspect of no number or not above 0 counts as square.
     */
    fun lookHigh(aspect: Double): Double {
        // NaN-guard: an aspect of no number fails the comparison and counts as square
        val wide = if (aspect > 0.0) aspect else 1.0
        val shown = max(FRAME_HIGH, FRAME_WIDE / wide)

        return LOOK_HIGH + (FRAME_HIGH - shown) / 2
    }

    /** How brightly the fixture before [player]'s feet glows with the carousel at [turn] ([glow]). */
    fun glowOf(player: Player, turn: Double): Double = glow(facing(player = player, turn = turn))
}
