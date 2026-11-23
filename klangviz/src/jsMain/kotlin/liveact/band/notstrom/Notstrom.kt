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
import io.peekandpoke.klang.viz.liveact.band.Cast
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.StagePlot
import io.peekandpoke.klang.viz.liveact.band.StringSeat
import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.dress.DressedBody
import io.peekandpoke.klang.viz.liveact.band.dress.HairPart
import io.peekandpoke.klang.viz.liveact.band.dress.ShadowHair
import io.peekandpoke.klang.viz.liveact.band.dress.pattern
import io.peekandpoke.klang.viz.liveact.band.figure.HairTail
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.band.figure.Side
import io.peekandpoke.klang.viz.liveact.band.figure.blend
import io.peekandpoke.klang.viz.liveact.band.figure.capture
import io.peekandpoke.klang.viz.liveact.band.figure.enter
import io.peekandpoke.klang.viz.liveact.band.figure.eyesAt
import io.peekandpoke.klang.viz.liveact.band.figure.stepLegs
import io.peekandpoke.klang.viz.liveact.band.figure.walkInHips
import io.peekandpoke.klang.viz.liveact.band.figure.walkPose
import io.peekandpoke.klang.viz.liveact.band.instruments.drums.Drummer
import io.peekandpoke.klang.viz.liveact.band.instruments.marimba.MarimbaPlayer
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.StringPlayer
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.TrumpetPlayer
import io.peekandpoke.klang.viz.liveact.band.members.Members
import io.peekandpoke.klang.viz.liveact.band.skills.IDLE_MARGIN
import io.peekandpoke.klang.viz.liveact.band.skills.IdleInUse
import io.peekandpoke.klang.viz.liveact.band.skills.IdleSkill
import io.peekandpoke.klang.viz.liveact.band.skills.Look
import io.peekandpoke.klang.viz.liveact.band.skills.LookPlaces
import io.peekandpoke.klang.viz.liveact.band.skills.crowdSpots
import io.peekandpoke.klang.viz.liveact.band.skills.idlesAt
import io.peekandpoke.klang.viz.liveact.band.skills.lookAt
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.song.DrumHit
import io.peekandpoke.klang.viz.liveact.song.Note
import io.peekandpoke.klang.viz.liveact.song.Part
import io.peekandpoke.klang.viz.liveact.song.Strum
import io.peekandpoke.klang.viz.liveact.song.testBeat
import io.peekandpoke.klang.viz.liveact.song.testCall
import io.peekandpoke.klang.viz.liveact.song.testRiff
import io.peekandpoke.klang.viz.liveact.song.testTune
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
 * stage [plot], and gives each the instrument and the part the [cast] names (both the song's score's); then it moves
 * them, every frame, from the walk-in on. Where the show has no song, each plays the test notes in its place
 * (`testBeat` and the others).
 */
class Notstrom private constructor(
    private val show: Show,
    private val cast: Cast,
    private val plot: StagePlot,
    /** Every player's rig, in the players' order. */
    val rigs: Map<Player, Rig>,
    /** The figures' dressed parts (the dark skin, the hair), each with its pattern: what the lacquer look re-dresses. */
    val bodies: List<DressedBody>,
    private val viewer: Vector3,
) {
    companion object {
        /** The band's name, as it stands on the bass drum. */
        const val NAME = "MØRTSTØN"

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
         * place in the stage [plot], and casts the band as [cast] says: every figure gets its instrument and its part
         * of the song.
         *
         * The files are fetched together, but the figures enter the scene in the players' order: what is made first
         * is drawn first. [viewer]: where the camera is (who looks at the viewer looks there); it is read every
         * frame, so the show keeps it up to date. [version]: the figures' release, asked for with each file
         * (`<key>.glb?v=<version>`): a release's figures may be cached for long, and a new release asks anew.
         * [arrived] is told how many of how many files have arrived, as each one does.
         */
        suspend fun load(
            show: Show,
            cast: Cast,
            plot: StagePlot,
            scene: Scene,
            models: String,
            version: String,
            viewer: Vector3,
            arrived: (count: Int, of: Int) -> Unit = { _, _ -> },
        ): Notstrom {
            val loaded = fetch(models = models, version = version, arrived = arrived)
            val rigs = linkedMapOf<Player, Rig>()
            val bodies = mutableListOf<DressedBody>()

            Player.entries.forEachIndexed { index, player ->
                val place = plot.places.getValue(player)
                val figure = loaded[index].scene

                figure.position.set(place.x, place.y, place.z)
                figure.rotation.y = place.turn
                scene.add(figure)

                val rig = Rig(figure)
                rigs[player] = rig

                bodies.addAll(dress(player = player, rig = rig))
            }

            return Notstrom(show = show, cast = cast, plot = plot, rigs = rigs, bodies = bodies, viewer = viewer)
        }

        /**
         * Fetches the six figures' files from [models] (a folder's address ending in a slash), all together, each
         * asked for with the release [version] (`<key>.glb?v=<version>`); gives them in the players' order.
         * [arrived] is told how many of how many files have arrived, as each one does. Also for "Meet the Band".
         */
        internal suspend fun fetch(models: String, version: String, arrived: (count: Int, of: Int) -> Unit): List<Gltf> {
            val loader = Three.addon.gltfLoader()
            val query = "?v=" + js("encodeURIComponent")(version).unsafeCast<String>()
            var count = 0

            arrived(0, Player.entries.size)

            return coroutineScope {
                Player.entries.map { player ->
                    async {
                        loader.load("$models${Members.of(player).figure}$query").also {
                            count++
                            arrived(count, Player.entries.size)
                        }
                    }
                }.awaitAll()
            }
        }

        /**
         * Dresses [player]'s figure, held by [rig], as their sheet says ([Members]): the hair and the dark skin each
         * get their pattern; gives the parts so dressed (what the lacquer re-dresses). Also for "Meet the Band".
         */
        internal fun dress(player: Player, rig: Rig): List<DressedBody> {
            val dress = Members.of(player).dress
            val bodies = mutableListOf<DressedBody>()

            rig.root.traverse { o ->
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

            return bodies
        }
    }

    private fun character(player: Player) = Members.of(player).character

    private fun rig(player: Player) = rigs.getValue(player)

    /**
     * When [player] begins a note, sorted, as far as the song is known: what the camera and the eyes go by. By the tags
     * the cast gives them (see [Cast.tagsOf]).
     */
    fun playsAt(player: Player): List<Double> = show.times(*cast.tagsOf(player).toTypedArray())

    /** The drummer's hits: the song's drums (see `Song.drumHits`), else the test beat. */
    private fun drumPart(): Part<DrumHit> = show.song?.part { drumHits(cast.drums.kit) } ?: Part.fixed(testBeat(64, show.bpm))

    /** The notes of a tune with the song's [tag], else the [test] notes. */
    private fun tunePart(tag: String, test: () -> List<Note>): Part<Note> = show.song?.part { notesOf(tag) } ?: Part.fixed(test())

    /** What the string player in [seat] plays: the song's events with their tags, else their test riff. */
    private fun stringPart(seat: StringSeat): Part<Strum> =
        show.song?.part { strumsOf(seat.tags) } ?: Part.fixed(testRiff(step = seat.riffStep, low = seat.riffLow))

    val drummer = Drummer(
        show = show,
        rig = rig(cast.drums.player),
        hits = drumPart(),
        traits = character(cast.drums.player).traits,
        seed = character(cast.drums.player).seed,
        name = NAME,
        // only once he sits: he sits down over the first SIT seconds after he is at the stool (see [play])
        idles = idlesOf(
            player = cast.drums.player,
            part = drumPart(),
            arrives = plot.entrances.getValue(cast.drums.player).end + SIT,
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

    /**
     * The tails of hair that hang and swing on their own, of every figure whose skeleton has one (see [HairTail]):
     * Rune's. They are set last of all, in [afterPose].
     */
    private val tails: List<HairTail> = Player.entries.mapNotNull { player -> HairTail.of(rig(player)) }

    /** The marimba player; null in a song without a marimba. */
    val marimba: MarimbaPlayer? = cast.marimba?.let { seat ->
        MarimbaPlayer(
            show = show,
            rig = rig(seat.player),
            part = tunePart(tag = seat.tag, test = ::testTune),
            traits = character(seat.player).traits,
            seed = character(seat.player).seed,
            skills = Members.of(seat.player).skills,
            arrives = plot.entrances.getValue(seat.player).end,
            idles = idlesOf(
                player = seat.player,
                part = tunePart(tag = seat.tag, test = ::testTune),
                margin = MarimbaPlayer.idleMargin(bpm = show.bpm, skills = Members.of(seat.player).skills),
            ),
        )
    }

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

    /** The trumpet player, who strolls; null in a song without a trumpet. */
    val frontwoman: TrumpetPlayer? = cast.trumpet?.let { seat ->
        TrumpetPlayer(
            show = show,
            rig = rig(seat.player),
            part = tunePart(tag = seat.tag, test = ::testCall),
            traits = character(seat.player).traits,
            seed = character(seat.player).seed,
            looks = headLooksOf(seat.player),
            gait = character(seat.player).gait,
            others = plot.places.filterKeys { it != seat.player },
        )
    }

    /** The string players, in the order they are moved each frame. */
    val strings: List<Pair<Player, StringPlayer>> = cast.strings.map { seat ->
        seat.player to StringPlayer(
            show = show,
            rig = rig(seat.player),
            design = seat.design,
            part = stringPart(seat),
            traits = character(seat.player).traits,
            seed = character(seat.player).seed,
            bearing = Members.of(seat.player).bearing,
            outline = seat.outline ?: seat.design.outline,
            looks = headLooksOf(seat.player),
            idles = idlesOf(player = seat.player, part = stringPart(seat)),
            skills = Members.of(seat.player).skills,
            hand = Members.of(seat.player).hand,
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
     * [StagePlot.entrances]). None ever where the sheet has no idle skill.
     */
    private fun idlesOf(
        player: Player,
        part: Part<out Timed>,
        arrives: Double = plot.entrances.getValue(player).end,
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
     * `StringPlayer.afterPose` and `Drummer.afterPose`), the snakes on a head, and last a tail of hair, which hangs
     * on the head and the body as they are held at last. Call it once everything that turns a head has done so for
     * this frame (the looks between the players come after [play]).
     */
    fun afterPose(t: Double) {
        for ((_, stringPlayer) in strings) {
            stringPlayer.afterPose()
        }

        drummer.afterPose()

        for (hair in snakes) {
            hair.update(t)
        }

        for (tail in tails) {
            tail.update(t)
        }
    }

    /** The band at [t]: everyone where their walk-in has them, playing; the legs of those who walk step. */
    fun play(t: Double) {
        val walks = plot.entrances.mapValues { (player, entrance) ->
            enter(rig = rig(player), entrance = entrance, home = plot.places.getValue(player), t = t)
        }

        // the string players and the marimba player walk as they stand, the legs stepping and the hips moving as each
        // one's gait has them on the walk-in
        for ((player, stringPlayer) in strings) {
            stringPlayer.update(t, hips = walkInHips(rig = rig(player), walk = walks.getValue(player), gait = character(player).gait))
            stepLegs(rig(player), walks.getValue(player))
        }

        cast.marimba?.player?.let { player ->
            val marimbaRig = rig(player)
            val marimbaWalk = walks.getValue(player)

            marimba?.update(t, hips = walkInHips(rig = marimbaRig, walk = marimbaWalk, gait = character(player).gait))
            stepLegs(marimbaRig, marimbaWalk)
        }

        // the drummer walks in with the sticks in his hands, and sits down once he is at the stool
        drummer.update(t)

        val drumRig = rig(cast.drums.player)
        val drumWalk = walks.getValue(cast.drums.player)
        val sit = smooth(drumWalk.since / SIT)

        if (sit < 1) {
            val seated = capture(drumRig)

            walkPose(rig = drumRig, walk = drumWalk, gait = character(cast.drums.player).gait)
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
        cast.trumpet?.player?.let { player ->
            val herRig = rig(player)
            val herWalk = walks.getValue(player)
            val her = if (herWalk.since < 0) {
                TrumpetPlayer.Walk(at = herRig.root.position.clone(), turn = herRig.root.rotation.y, speed = 0.0, going = 0.0)
            } else {
                null
            }

            frontwoman?.update(
                t,
                put = her,
                hips = her?.let { walkInHips(rig = herRig, walk = herWalk, gait = character(player).gait) },
            )
            stepLegs(herRig, herWalk)
        }
    }
}
