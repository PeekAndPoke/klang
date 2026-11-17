/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.notstrom

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.StagePlot
import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.dress.HairPart
import io.peekandpoke.klang.viz.liveact.band.dress.ShadowHair
import io.peekandpoke.klang.viz.liveact.band.dress.pattern
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.band.figure.Side
import io.peekandpoke.klang.viz.liveact.band.figure.blend
import io.peekandpoke.klang.viz.liveact.band.figure.capture
import io.peekandpoke.klang.viz.liveact.band.figure.enter
import io.peekandpoke.klang.viz.liveact.band.figure.stepLegs
import io.peekandpoke.klang.viz.liveact.band.figure.walkPose
import io.peekandpoke.klang.viz.liveact.band.instruments.drums.Drummer
import io.peekandpoke.klang.viz.liveact.band.instruments.marimba.MarimbaPlayer
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.Glance
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.StringPlayer
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.TrumpetPlayer
import io.peekandpoke.klang.viz.liveact.band.members.Extra
import io.peekandpoke.klang.viz.liveact.band.members.Members
import io.peekandpoke.klang.viz.liveact.refactorme.SchmetterlingCast
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.Gltf
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.asMeshStandardMaterial
import io.peekandpoke.klang.workshop.threejs.asSkinnedMesh
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.klang.workshop.threejs.gltfLoader
import io.peekandpoke.klang.workshop.threejs.materials
import io.peekandpoke.klang.workshop.threejs.name
import io.peekandpoke.klang.workshop.threejs.quaternion
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Notstrom, the band: it loads the six figures by their sheets (see [Members]), dresses them, places them by the
 * stage plot ([StagePlot]), and gives each the instrument and the part the cast names ([SchmetterlingCast]); then it
 * moves them, every frame, from the walk-in on.
 */
class Notstrom private constructor(
    private val show: Show,
    /** Every player's rig, in the players' order. */
    val rigs: Map<Player, Rig>,
    private val viewer: Vector3,
) {
    companion object {
        /** The band's name, as it stands on the bass drum. */
        const val NAME = "NOTSTROM"

        /** The head's middle and size, in the body's rest space: a little above and behind where the head's bone starts. */
        private fun hairOf(rig: Rig): HairPart {
            val neck = rig.restStart("head")
            val size = rig.height / 1.60

            return HairPart(centre = vec(neck.x, neck.y + 0.115 * size, neck.z - 0.012 * size), radius = 0.104 * size)
        }

        /**
         * Loads the six figures (from [models], a folder's address ending in a slash) into [scene], each at its
         * place, and casts the band: every figure gets its instrument and its part of the song.
         *
         * The files are fetched together, but the figures enter the scene in the players' order: what is made first
         * is drawn first. [viewer]: where the camera is (who looks at the viewer looks there); it is read every
         * frame, so the show keeps it up to date.
         */
        suspend fun load(show: Show, scene: Scene, models: String, viewer: Vector3): Notstrom {
            val loader = Three.addon.gltfLoader()

            val loaded: List<Gltf> = coroutineScope {
                Player.entries.map { player -> async { loader.load("$models${Members.of(player).figure}") } }.awaitAll()
            }

            val rigs = linkedMapOf<Player, Rig>()

            Player.entries.forEachIndexed { index, player ->
                val place = StagePlot.PLACES.getValue(player)
                val figure = loaded[index].scene

                figure.position.set(place.x, place.y, place.z)
                figure.rotation.y = place.turn
                scene.add(figure)

                val rig = Rig(figure)
                val dress = Members.of(player).dress
                rigs[player] = rig

                figure.traverse { o ->
                    o.asSkinnedMesh?.materials?.forEach { m ->
                        // a pattern goes into a standard material's shader: every material of the figures is one
                        val material = m.asMeshStandardMaterial ?: return@forEach

                        if (m.name.endsWith("_hair")) {
                            pattern(material = material, kind = ClothPattern.Strands, gloss = 0.07, matt = 0.62, hair = null, glitter = 0.05)
                        }

                        if (m.name.endsWith("_dark")) {
                            pattern(
                                material = material,
                                kind = dress.cloth,
                                gloss = 0.12,
                                matt = 0.85,
                                hair = if (dress.combedBack) hairOf(rig) else null,
                                glitter = dress.glitter,
                            )
                        }
                    }
                }
            }

            return Notstrom(show = show, rigs = rigs, viewer = viewer)
        }
    }

    private fun character(player: Player) = Members.of(player).character

    private fun rig(player: Player) = rigs.getValue(player)

    /** When [player] begins a note, sorted, as far as the song is known: what the camera and the eyes go by. */
    fun playsAt(player: Player): List<Double> = SchmetterlingCast.playsAt(show = show, player = player)

    val drummer = Drummer(
        show = show,
        rig = rig(SchmetterlingCast.DRUMS),
        hits = SchmetterlingCast.drumHits(show),
        traits = character(SchmetterlingCast.DRUMS).traits,
        seed = character(SchmetterlingCast.DRUMS).seed,
        name = NAME,
    )

    /**
     * Medusa's snakes, in the shadow of whoever's sheet names them (see [Extra.ShadowSnakes]). They are made here,
     * after the drummer and before the marimba, as when the marimba player made them: three.js numbers what is made
     * in order, and the picture depends on it.
     */
    private val snakes: List<ShadowHair> = when {
        show.switches.has(Switch.NoSnakes) -> emptyList()
        else -> Player.entries
            .filter { player -> Extra.ShadowSnakes in Members.of(player).extras }
            .map { player -> ShadowHair(rig = rig(player), shown = show.switches.has(Switch.Snakes)) }
    }

    val marimba = MarimbaPlayer(
        show = show,
        rig = rig(SchmetterlingCast.MARIMBA),
        part = SchmetterlingCast.marimbaNotes(show),
        traits = character(SchmetterlingCast.MARIMBA).traits,
        seed = character(SchmetterlingCast.MARIMBA).seed,
    )

    val frontwoman = TrumpetPlayer(
        show = show,
        rig = rig(SchmetterlingCast.TRUMPET),
        part = SchmetterlingCast.trumpetNotes(show),
        traits = character(SchmetterlingCast.TRUMPET).traits,
        seed = character(SchmetterlingCast.TRUMPET).seed,
    )

    /** The string players, in the order they are moved each frame. */
    val strings: List<Pair<Player, StringPlayer>> = SchmetterlingCast.STRINGS.map { cast ->
        cast.player to StringPlayer(
            show = show,
            rig = rig(cast.player),
            design = cast.design,
            part = cast.part(show),
            traits = character(cast.player).traits,
            seed = character(cast.player).seed,
            bearing = Members.of(cast.player).bearing,
            outline = cast.outline ?: cast.design.outline,
            glance = glanceOf(cast.player),
        )
    }

    /** How [player] glances at the camera, where their sheet says they do (see [Extra.GlanceAtCamera]). */
    private fun glanceOf(player: Player): Glance? = Members.of(player).extras
        .filterIsInstance<Extra.GlanceAtCamera>()
        .firstOrNull()
        ?.let { glance -> Glance(at = viewer, every = glance.every, chance = glance.chance) }

    /**
     * What goes with the figures as they are posed at last: the snakes on a head. Call it once everything that turns
     * a head has done so for this frame (the looks between the players come after [play]).
     */
    fun afterPose(t: Double) {
        for (hair in snakes) {
            hair.update(t)
        }
    }

    /** The band at [t]: everyone where their walk-in has them, playing; the legs of those who walk step. */
    fun play(t: Double) {
        val walks = StagePlot.ENTRANCES.mapValues { (player, entrance) ->
            enter(rig = rig(player), entrance = entrance, home = StagePlot.PLACES.getValue(player), t = t)
        }

        // the string players and the marimba player walk as they stand, the legs stepping
        for ((player, stringPlayer) in strings) {
            stringPlayer.update(t)
            stepLegs(rig(player), walks.getValue(player))
        }

        marimba.update(t)
        stepLegs(rig(SchmetterlingCast.MARIMBA), walks.getValue(SchmetterlingCast.MARIMBA))

        // the frontwoman: put where her walk-in has her; once there she is her own (her place, her strolls)
        val herRig = rig(SchmetterlingCast.TRUMPET)
        val herWalk = walks.getValue(SchmetterlingCast.TRUMPET)
        val her = if (herWalk.since < 0) {
            TrumpetPlayer.Walk(at = herRig.root.position.clone(), turn = herRig.root.rotation.y, speed = 0.0, going = 0.0)
        } else {
            null
        }

        frontwoman.update(t, her)
        stepLegs(herRig, herWalk)

        // the drummer walks in with the sticks in his hands, and sits down once he is at the stool
        drummer.update(t)

        val drumRig = rig(SchmetterlingCast.DRUMS)
        val drumWalk = walks.getValue(SchmetterlingCast.DRUMS)
        val sit = smooth(drumWalk.since / 1.6)

        if (sit < 1) {
            val seated = capture(drumRig)

            walkPose(drumRig, drumWalk)
            blend(rig = drumRig, a = capture(drumRig), b = seated, w = sit)

            for (side in Side.entries) {
                val d = drumRig.axisOf("hand$side")
                val stick = drummer.kit.sticks.getValue(side)

                stick.position.copy(drumRig.bone("hand$side").getWorldPosition(vec())).addScaledVector(d, drummer.kit.stickLength * 0.22)
                stick.quaternion.setFromUnitVectors(Y_AXIS, d)
            }
        }
    }
}
