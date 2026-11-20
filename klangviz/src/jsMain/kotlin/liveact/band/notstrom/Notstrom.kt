/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.notstrom

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.Timed
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.StagePlot
import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.dress.DressedBody
import io.peekandpoke.klang.viz.liveact.band.dress.HairPart
import io.peekandpoke.klang.viz.liveact.band.dress.ShadowHair
import io.peekandpoke.klang.viz.liveact.band.dress.pattern
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.band.figure.Side
import io.peekandpoke.klang.viz.liveact.band.figure.blend
import io.peekandpoke.klang.viz.liveact.band.figure.capture
import io.peekandpoke.klang.viz.liveact.band.figure.enter
import io.peekandpoke.klang.viz.liveact.band.figure.eyesAt
import io.peekandpoke.klang.viz.liveact.band.figure.stepLegs
import io.peekandpoke.klang.viz.liveact.band.figure.walkPose
import io.peekandpoke.klang.viz.liveact.band.instruments.drums.Drummer
import io.peekandpoke.klang.viz.liveact.band.instruments.marimba.MarimbaPlayer
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.StringPlayer
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.TrumpetPlayer
import io.peekandpoke.klang.viz.liveact.band.members.Members
import io.peekandpoke.klang.viz.liveact.band.skills.CountIn
import io.peekandpoke.klang.viz.liveact.band.skills.CrossedMallets
import io.peekandpoke.klang.viz.liveact.band.skills.IDLE_MARGIN
import io.peekandpoke.klang.viz.liveact.band.skills.IdleInUse
import io.peekandpoke.klang.viz.liveact.band.skills.IdleSkill
import io.peekandpoke.klang.viz.liveact.band.skills.Look
import io.peekandpoke.klang.viz.liveact.band.skills.LookPlaces
import io.peekandpoke.klang.viz.liveact.band.skills.crowdSpots
import io.peekandpoke.klang.viz.liveact.band.skills.idlesAt
import io.peekandpoke.klang.viz.liveact.band.skills.lookAt
import io.peekandpoke.klang.viz.liveact.refactorme.SchmetterlingCast
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.song.Part
import io.peekandpoke.klang.viz.liveact.theatre.CrowdPlaces
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
    /** The figures' dressed parts (the dark skin, the hair), each with its pattern: what the lacquer look re-dresses. */
    val bodies: List<DressedBody>,
    private val viewer: Vector3,
) {
    companion object {
        /** The band's name, as it stands on the bass drum. */
        const val NAME = "NOTSTROM"

        /** How long the drummer takes to sit down once he is at the stool, in seconds. */
        private const val SIT = 1.6

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
         * frame, so the show keeps it up to date. [version]: the figures' release, asked for with each file
         * (`<key>.glb?v=<version>`): a release's figures may be cached for long, and a new release asks anew.
         * [arrived] is told how many of how many files have arrived, as each one does.
         */
        suspend fun load(
            show: Show,
            scene: Scene,
            models: String,
            version: String,
            viewer: Vector3,
            arrived: (count: Int, of: Int) -> Unit = { _, _ -> },
        ): Notstrom {
            val loader = Three.addon.gltfLoader()
            val query = "?v=" + js("encodeURIComponent")(version).unsafeCast<String>()
            var count = 0

            arrived(0, Player.entries.size)

            val loaded: List<Gltf> = coroutineScope {
                Player.entries.map { player ->
                    async {
                        loader.load("$models${Members.of(player).figure}$query").also {
                            count++
                            arrived(count, Player.entries.size)
                        }
                    }
                }.awaitAll()
            }

            val rigs = linkedMapOf<Player, Rig>()
            val bodies = mutableListOf<DressedBody>()

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
                    val mesh = o.asSkinnedMesh ?: return@traverse

                    mesh.materials.forEach { m ->
                        // a pattern goes into a standard material's shader: every material of the figures is one
                        val material = m.asMeshStandardMaterial ?: return@forEach

                        if (m.name.endsWith("_hair")) {
                            pattern(material = material, kind = ClothPattern.Strands, gloss = 0.07, matt = 0.62, hair = null, glitter = 0.05)
                            bodies.add(DressedBody(mesh = mesh, material = material, cloth = ClothPattern.Strands, hair = null))
                        }

                        if (m.name.endsWith("_dark")) {
                            val hair = if (dress.combedBack) hairOf(rig) else null

                            pattern(
                                material = material,
                                kind = dress.cloth,
                                gloss = 0.12,
                                matt = 0.85,
                                hair = hair,
                                glitter = dress.glitter,
                            )
                            bodies.add(DressedBody(mesh = mesh, material = material, cloth = dress.cloth, hair = hair))
                        }
                    }
                }
            }

            return Notstrom(show = show, rigs = rigs, bodies = bodies, viewer = viewer)
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
        // only once he sits: he sits down over the first SIT seconds after he is at the stool (see [play])
        idles = idlesOf(
            player = SchmetterlingCast.DRUMS,
            part = SchmetterlingCast.drumHits(show),
            arrives = StagePlot.ENTRANCES.getValue(SchmetterlingCast.DRUMS).end + SIT,
        ),
    )

    /**
     * Medusa's snakes, in the shadow of whoever's dress has them (see `Dress.shadowSnakes`). They are made here,
     * after the drummer and before the marimba's materials: three.js numbers what is made in order, and the picture
     * depends on it.
     */
    private val snakes: List<ShadowHair> = when {
        show.switches.has(Switch.NoSnakes) -> emptyList()
        else -> Player.entries
            .filter { player -> Members.of(player).dress.shadowSnakes }
            .map { player -> ShadowHair(rig = rig(player), shown = show.switches.has(Switch.Snakes)) }
    }

    /** The marimba player's crossed mallets, where her sheet has them. */
    private val runeCrossing: CrossedMallets? =
        Members.of(SchmetterlingCast.MARIMBA).skills.filterIsInstance<CrossedMallets>().firstOrNull()

    val marimba = MarimbaPlayer(
        show = show,
        rig = rig(SchmetterlingCast.MARIMBA),
        part = SchmetterlingCast.marimbaNotes(show),
        traits = character(SchmetterlingCast.MARIMBA).traits,
        seed = character(SchmetterlingCast.MARIMBA).seed,
        crossing = runeCrossing,
        arrives = StagePlot.ENTRANCES.getValue(SchmetterlingCast.MARIMBA).end,
        counting = Members.of(SchmetterlingCast.MARIMBA).skills.filterIsInstance<CountIn>().firstOrNull(),
        idles = idlesOf(
            player = SchmetterlingCast.MARIMBA,
            part = SchmetterlingCast.marimbaNotes(show),
            margin = MarimbaPlayer.idleMargin(bpm = show.bpm, crossing = runeCrossing),
        ),
    )

    /**
     * Where a look can go: the camera (the viewer, as the show keeps it), the members' eyes as they are posed now,
     * the crowd's spots, and nowhere: a place low before the member and a little aside, by where the figure stands
     * and faces (not by its head, which the look itself turns).
     */
    private val places = LookPlaces(
        camera = viewer,
        eyes = { player -> eyesAt(rig(player)) },
        crowd = crowdSpots(CrowdPlaces.all),
        nowhere = { player -> rig(player).let { it.root.localToWorld(vec(0.45 * it.height, 0.4 * it.height, 1.5 * it.height)) } },
    )

    val frontwoman = TrumpetPlayer(
        show = show,
        rig = rig(SchmetterlingCast.TRUMPET),
        part = SchmetterlingCast.trumpetNotes(show),
        traits = character(SchmetterlingCast.TRUMPET).traits,
        seed = character(SchmetterlingCast.TRUMPET).seed,
        looks = headLooksOf(SchmetterlingCast.TRUMPET),
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
            looks = headLooksOf(cast.player),
            idles = idlesOf(player = cast.player, part = cast.part(show)),
        )
    }

    /**
     * Where [player]'s skills ask the head to look at a time (see [lookAt]): by the skills on their sheet, with their
     * own chance (the show's under their character's seed), toward the [places] a look can go to.
     */
    private fun headLooksOf(player: Player): (Double) -> List<Look> {
        val member = Members.of(player)
        val roll = { index: Double -> show.own(index, member.character.seed) }

        return { t -> lookAt(who = player, skills = member.skills, t = t, roll = roll, places = places) }
    }

    /**
     * Which of [player]'s idle skills are in use at a time (see [idlesAt]): by the skills on their sheet, with their own
     * chance (as for [headLooksOf]), within the pauses of [part] (their part of the song, read on its own: it is
     * refreshed here, apart from the one they play from), keeping [margin] seconds from a note (see [idlesAt]), and
     * only from [arrives] on (by default once they are at their place, not on their walk-in: see
     * [StagePlot.ENTRANCES]). None ever where the sheet has no idle skill.
     */
    private fun idlesOf(
        player: Player,
        part: Part<out Timed>,
        arrives: Double = StagePlot.ENTRANCES.getValue(player).end,
        margin: Double = IDLE_MARGIN,
    ): (Double) -> List<IdleInUse> {
        val member = Members.of(player)

        if (member.skills.none { it is IdleSkill }) {
            return { emptyList() }
        }

        val roll = { index: Double -> show.own(index, member.character.seed) }

        return { t ->
            part.refresh()
            idlesAt(skills = member.skills, t = t, roll = roll, plays = part.events, arrives = arrives, margin = margin)
        }
    }

    /**
     * What goes with the figures as they are posed at last: a hand at the head or the neck (an idle skill, see
     * `StringPlayer.afterPose` and `Drummer.afterPose`), and the snakes on a head. Call it once everything that turns
     * a head has done so for this frame (the looks between the players come after [play]).
     */
    fun afterPose(t: Double) {
        for ((_, stringPlayer) in strings) {
            stringPlayer.afterPose()
        }

        drummer.afterPose()

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

        // the drummer walks in with the sticks in his hands, and sits down once he is at the stool
        drummer.update(t)

        val drumRig = rig(SchmetterlingCast.DRUMS)
        val drumWalk = walks.getValue(SchmetterlingCast.DRUMS)
        val sit = smooth(drumWalk.since / SIT)

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

        // the frontwoman, last: her looks may go to the others' eyes, as they are posed in this frame. Put where her
        // walk-in has her; once there she is her own (her place, her strolls)
        val herRig = rig(SchmetterlingCast.TRUMPET)
        val herWalk = walks.getValue(SchmetterlingCast.TRUMPET)
        val her = if (herWalk.since < 0) {
            TrumpetPlayer.Walk(at = herRig.root.position.clone(), turn = herRig.root.rotation.y, speed = 0.0, going = 0.0)
        } else {
            null
        }

        frontwoman.update(t, her)
        stepLegs(herRig, herWalk)
    }
}
