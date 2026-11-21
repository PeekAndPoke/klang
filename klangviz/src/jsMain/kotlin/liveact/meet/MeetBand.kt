/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.meet

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.dress.DressedBody
import io.peekandpoke.klang.viz.liveact.band.dress.EyeLook
import io.peekandpoke.klang.viz.liveact.band.dress.Lacquer
import io.peekandpoke.klang.viz.liveact.band.dress.ShadowEyes
import io.peekandpoke.klang.viz.liveact.band.dress.ShadowHair
import io.peekandpoke.klang.viz.liveact.band.figure.HairTail
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.band.figure.Side
import io.peekandpoke.klang.viz.liveact.band.figure.breathing
import io.peekandpoke.klang.viz.liveact.band.figure.eyesWaver
import io.peekandpoke.klang.viz.liveact.band.members.Members
import io.peekandpoke.klang.viz.liveact.band.notstrom.Notstrom
import io.peekandpoke.klang.viz.liveact.lights.FrontLight
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import io.peekandpoke.kraft.addons.threejs.js.WebGLRenderer

/**
 * The band in "Meet the Band" ([MeetLayout]): the six figures as the show loads them ([Notstrom.fetch]) and dresses
 * them ([Notstrom.dress]), in black lacquer with gold as the notstrom app shows them (its new look, [Lacquer]), with no
 * instrument. Each stands at its place round the hexagon as the carousel turns ([pose]): at rest, the arms hanging,
 * breathing; the one at the front looks at the camera; everyone's eyes glow as in the show at rest, wavering.
 *
 * The true selves in the shadows come along where they exist ([shadowShapesOf]): Rune's snakes; and Sirene's eyes move
 * from her face into her shadow while she stands at the front ([ShadowEyes]).
 *
 * The figures stand in the scene itself, not in the carousel: the shapes seen only in a shadow are placed in the
 * world, and so are the figures, each frame, by the carousel's turn. [dispose] gives the bodies their suits back and
 * frees the lacquer and Sirene's eyes in her shadow; the figures are in the scene and are freed with it.
 */
class MeetBand private constructor(
    private val rigs: Map<Player, Rig>,
    private val lacquer: Lacquer,
    /** The shapes seen only in a member's shadow, each set for a time once its figure is posed. */
    private val shadowShapes: List<(Double) -> Unit>,
    private val tails: List<HairTail>,
    private val sirenesEyes: ShadowEyes?,
) {
    companion object {
        /** How far the front member's face turns to the camera: most of the way, as a person looks. */
        private const val LOOK = 0.8

        /** How deep they breathe: as the band does at a quiet moment of the show. */
        private const val QUIET = 0.3

        /** How bright the lacquer mirrors its room: a little darker than the show's stage with the house up. */
        private const val ROOM = 0.7

        /**
         * Loads the six figures (from [models], a folder's address ending in a slash, each asked for with the release
         * [version]) into [scene], each at its place for the carousel at [turn], dresses them and puts the lacquer on
         * them (its room drawn with [renderer]). [arrived] is told how many of how many files have arrived.
         */
        suspend fun load(
            scene: Scene,
            renderer: WebGLRenderer,
            models: String,
            version: String,
            turn: Double,
            arrived: (count: Int, of: Int) -> Unit,
        ): MeetBand {
            val loaded = Notstrom.fetch(models = models, version = version, arrived = arrived)
            val rigs = linkedMapOf<Player, Rig>()
            val bodies = mutableListOf<DressedBody>()

            // in the players' order, as the show makes them: what is made first is drawn first
            Player.entries.forEachIndexed { index, player ->
                val figure = loaded[index].scene
                val spot = MeetLayout.spotOf(player = player, turn = turn)

                figure.position.set(spot.x, 0.0, spot.z)
                figure.rotation.y = spot.facing
                scene.add(figure)

                val rig = Rig(figure)

                rigs[player] = rig
                bodies.addAll(Notstrom.dress(player = player, rig = rig))
            }

            val lacquer = Lacquer.bodiesOnly(renderer = renderer, bodies = bodies)

            lacquer.light(house = ROOM)

            return MeetBand(
                rigs = rigs,
                lacquer = lacquer,
                shadowShapes = Player.entries.flatMap { player -> shadowShapesOf(player = player, rig = rigs.getValue(player)) },
                tails = Player.entries.mapNotNull { player -> HairTail.of(rigs.getValue(player)) },
                sirenesEyes = ShadowEyes.of(scene = scene, rig = rigs.getValue(Player.Sirene)),
            )
        }

        /**
         * The true self in [player]'s shadow: the shapes that are seen only in the shadow on the surface behind the
         * fence, made for [rig], each to be set for a time once the figure is posed. Today Rune's snakes, as her sheet's
         * dress has them ([ShadowHair]). The others' true selves (Sirene's wings, Wucht's horns, Klaue's wolf, a sword,
         * a heron's neck) are to be made here as the snakes are: shapes that cast a shadow and are not drawn.
         */
        private fun shadowShapesOf(player: Player, rig: Rig): List<(Double) -> Unit> {
            val shapes = mutableListOf<(Double) -> Unit>()

            if (Members.of(player).dress.shadowSnakes) {
                shapes.add(ShadowHair(rig = rig, shown = false)::update)
            }

            return shapes
        }

        /**
         * [rig] standing at rest: the arms hanging by the sides, as the show's walk has them without the swing
         * (`walkPose`).
         */
        private fun stand(rig: Rig) {
            val s = rig.s

            rig.reset()
            rig.update()

            for (side in Side.entries) {
                val sign = side.sign
                val shoulder = rig.shoulder.getValue(side)
                val hand = vec(shoulder.x + 0.05 * sign * s, shoulder.y - 0.94 * rig.armLength, 0.05 * s)

                rig.hand(side, rig.root.localToWorld(hand), rig.dir(0.3 * sign, -0.3, -1.0))
            }
        }
    }

    /**
     * Everyone for the carousel at [turn] and the time [t] (seconds): at their place, standing and breathing, the front
     * member looking at the [camera]; the eyes' glow; the true selves in the shadows; Sirene's eyes in her shadow while
     * she stands at the front and the carousel is [settled] (no turn under way), thrown from [lamp] onto the surface at
     * [surfaceZ] (along z) behind her. They are thrown onto that plane alone, the front surface's as it stands at
     * rest: so they cross back into her face as soon as a turn begins, and out again once she has arrived.
     */
    fun pose(turn: Double, t: Double, camera: Vector3, lamp: Vector3, surfaceZ: Double, settled: Boolean) {
        for ((player, rig) in rigs) {
            val spot = MeetLayout.spotOf(player = player, turn = turn)
            val front = MeetLayout.glowOf(player = player, turn = turn)

            rig.root.position.set(spot.x, 0.0, spot.z)
            rig.root.rotation.y = spot.facing
            stand(rig)

            if (front > 0.0) {
                rig.update()
                rig.faceTo(camera, LOOK * front)
            }

            // the eyes as a player's at rest sets them, at rest's warmth
            rig.eyes?.emissiveIntensity = EyeLook.LEAST
            rig.warmEyes(0.0)
        }

        eyesWaver(seed = 0, rigs = rigs, t = t)

        for (shapes in shadowShapes) {
            shapes(t)
        }

        for (tail in tails) {
            tail.update(t)
        }

        breathing(seed = 0, rigs = rigs, t = t, slow = QUIET)

        sirenesEyes?.update(
            front = FrontLight(level = if (settled) MeetLayout.glowOf(player = Player.Sirene, turn = turn) else 0.0, strobing = false),
            lamp = lamp,
            planeZ = surfaceZ,
        )
    }

    /** Gives the bodies their suits back and frees the lacquer and Sirene's eyes in her shadow. Call it once. */
    fun dispose() {
        sirenesEyes?.dispose()
        lacquer.dispose()
    }
}
