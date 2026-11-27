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
import io.peekandpoke.klang.viz.liveact.band.figure.strideOf
import io.peekandpoke.klang.viz.liveact.band.figure.strollHips
import io.peekandpoke.klang.viz.liveact.band.figure.walkInHips
import io.peekandpoke.klang.viz.liveact.band.figure.walkPose
import io.peekandpoke.klang.viz.liveact.band.instruments.drums.Drummer
import io.peekandpoke.klang.viz.liveact.band.instruments.marimba.MarimbaPlayer
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.StringPlayer
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.StringStroller
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.TrumpetPlayer
import io.peekandpoke.klang.viz.liveact.band.members.Members
import io.peekandpoke.klang.viz.liveact.band.skills.IDLE_MARGIN
import io.peekandpoke.klang.viz.liveact.band.skills.IdleInUse
import io.peekandpoke.klang.viz.liveact.band.skills.IdleSkill
import io.peekandpoke.klang.viz.liveact.band.skills.Look
import io.peekandpoke.klang.viz.liveact.band.skills.LookPlaces
import io.peekandpoke.klang.viz.liveact.band.skills.crowdSpots
import io.peekandpoke.klang.viz.liveact.band.skills.idlesAt
import io.peekandpoke.klang.viz.liveact.band.skills.keepsIdle
import io.peekandpoke.klang.viz.liveact.band.skills.lookAt
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.band.NoteSeat
import io.peekandpoke.klang.viz.liveact.band.after
import io.peekandpoke.klang.viz.liveact.band.figure.CapturedPose
import io.peekandpoke.klang.viz.liveact.band.figure.WalkIn
import io.peekandpoke.klang.viz.liveact.band.instruments.drums.KitMaterials
import io.peekandpoke.klang.viz.liveact.band.instruments.marimba.MarimbaMaterials
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.StringMaterials
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
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
import io.peekandpoke.klang.workshop.threejs.lerp
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
    cast: Cast,
    plot: StagePlot,
    /** The one who strolls about the stage, the score's (see `Score.strolls`); nobody where null. */
    strolls: Player?,
    /** Every player's rig, in the players' order. */
    val rigs: Map<Player, Rig>,
    /** The figures' dressed parts (the dark skin, the hair), each with its pattern: what the lacquer look re-dresses. */
    val bodies: List<DressedBody>,
    private val viewer: Vector3,
    /** How many of the members' idle moves happen, 0 to 1 (the score's, see `Score.idles`). */
    idles: Double,
) {
    /**
     * Who plays what, where they are and how they get there, and how many idle moves happen: the score's of the song
     * that plays now. They change when the band goes on with another song ([reseat]); the six figures stay.
     */
    private var cast: Cast = cast
    private var plot: StagePlot = plot
    private var strolls: Player? = strolls
    private var idles: Double = idles

    /** The moment on the show's clock at which the song that plays now begins: the plot's walks are told from there. */
    private var startsAt: Double = 0.0

    companion object {
        /** The band's name, as it stands on the bass drum. */
        const val NAME = "MØRTSTØN"

        /** How long the drummer takes to sit down once he is at the stool, in seconds. */
        private const val SIT = 1.6

        /** How long a strung player takes to take up the instrument that appears once they are at their place. */
        private const val TAKE = 1.0

        /** At a change of song: how long an instrument takes to go and to come, and a figure to ease into its new pose. */
        private const val VANISH = 2.2
        private const val APPEAR = 1.8

        /** How high an instrument floats as it goes, and from how high it comes, in metres. */
        private const val FLOAT = 0.9

        /** How long one who arrives with empty hands takes from the walk into the start pose, in seconds. */
        private const val LAND = 0.5
        private const val EASE = 1.6

        /** The head's middle and size, in the body's rest space: a little above and behind where the head's bone starts. */
        private fun hairOf(rig: Rig): HairPart {
            val neck = rig.restStart("head")
            val size = rig.height / 1.60

            return HairPart(centre = vec(neck.x, neck.y + 0.115 * size, neck.z - 0.012 * size), radius = 0.104 * size)
        }

        /**
         * Loads the six figures (from [models], a folder's address ending in a slash) into [scene], each at its
         * place in the stage [plot], and casts the band as [cast] says: every figure gets its instrument and its part
         * of the song. [strolls]: the one who strolls about the stage (see `Score.strolls`).
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
            strolls: Player?,
            scene: Scene,
            models: String,
            version: String,
            viewer: Vector3,
            idles: Double = 1.0,
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

            return Notstrom(
                show = show,
                cast = cast,
                plot = plot,
                strolls = strolls,
                rigs = rigs,
                bodies = bodies,
                viewer = viewer,
                idles = idles,
            )
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
     * When [player] begins a note, sorted, as far as the song is known: what the eyes go by (the camera's choice of a
     * look by [seenAt]). By the tags the cast gives them (see [Cast.tagsOf]).
     */
    fun playsAt(player: Player): List<Double> = show.times(*cast.tagsOf(player).toTypedArray())

    /**
     * When [player] begins a note as the camera's choice of a look goes by it: as [playsAt], but none while they
     * stroll with their instrument, nor a look before that would reach into a stroll (see [StringStroller.seen]): a
     * look on a player goes round with them, and would swing as they walk.
     */
    fun seenAt(player: Player): List<Double> {
        val times = playsAt(player)
        val (who, stroller) = strolling ?: return times

        return if (who == player) stroller.seen(times) else times
    }

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
    var marimba: MarimbaPlayer? = cast.marimba?.let { marimbaPlayerOf(it) }
        private set

    private fun marimbaPlayerOf(seat: NoteSeat): MarimbaPlayer = MarimbaPlayer(
        show = show,
        rig = rig(seat.player),
        part = tunePart(tag = seat.tag, test = ::testTune),
        traits = character(seat.player).traits,
        seed = character(seat.player).seed,
        skills = Members.of(seat.player).skills,
        arrives = startsAt + plot.entrances.getValue(seat.player).end,
        idles = idlesOf(
            player = seat.player,
            part = tunePart(tag = seat.tag, test = ::testTune),
            margin = MarimbaPlayer.idleMargin(bpm = show.bpm, skills = Members.of(seat.player).skills),
        ),
        home = plot.places.getValue(seat.player),
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

    /** The trumpet player, who strolls; null in a song without a trumpet. */
    var frontwoman: TrumpetPlayer? = cast.trumpet?.let { trumpetPlayerOf(it) }
        private set

    private fun trumpetPlayerOf(seat: NoteSeat): TrumpetPlayer = TrumpetPlayer(
        show = show,
        rig = rig(seat.player),
        part = tunePart(tag = seat.tag, test = ::testCall),
        traits = character(seat.player).traits,
        seed = character(seat.player).seed,
        looks = headLooksOf(seat.player),
        gait = character(seat.player).gait,
        others = plot.places.filterKeys { it != seat.player },
        place = plot.places.getValue(seat.player),
    )

    /** The string players, in the order they are moved each frame. */
    var strings: List<Pair<Player, StringPlayer>> = cast.strings.map { seat -> seat.player to stringPlayerOf(seat) }
        private set

    private fun stringPlayerOf(seat: StringSeat): StringPlayer = StringPlayer(
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
        lap = Members.of(seat.player).lap,
    )

    /**
     * The string player who walks about the stage with the instrument while they play (see [StringStroller]), and
     * their strolls: the one who strolls ([strolls]) where they have a string seat; null where nobody does.
     */
    private var strolling: Pair<Player, StringStroller>? = strollingOf()

    private fun strollingOf(): Pair<Player, StringStroller>? {
        val seat = cast.strings.firstOrNull { it.player == strolls } ?: return null

        return seat.player to StringStroller(
            show = show,
            rig = rig(seat.player),
            part = stringPart(seat),
            place = plot.places.getValue(seat.player),
            others = plot.places.filterKeys { it != seat.player },
        )
    }

    /**
     * An instrument that goes at a change of song: [prop] floats up from where it was ([from], its height) and is
     * seen through more and more ([veil], made at its first frame) from the moment [since] on the show's clock; it is
     * taken out of the scene and freed when it is gone.
     */
    private class Leaving(val prop: Object3D, val since: Double, val from: Double) {
        var veil: Veil? = null
    }

    /**
     * An instrument that comes at a change of song: once its [player] has reached the start pose ([reseat]) the
     * [props] come down into place from above, less and less seen through ([veils], made at the first frame they are
     * seen); until then they are not seen. [put]: the height each was last put at here, to tell it from one its
     * player has put anew since.
     */
    private class Arriving(val player: Player, val props: List<Object3D>) {
        var veils: List<Veil>? = null
        val put: MutableList<Double> = props.map { Double.NaN }.toMutableList()
        val base: MutableList<Double> = props.map { 0.0 }.toMutableList()
    }

    private val leaving = mutableListOf<Leaving>()
    private val arriving = mutableListOf<Arriving>()

    /**
     * Whether the last change of song is done with: no instrument goes or comes any more, nobody eases or turns
     * into the next song. Until then the band takes no other change ([reseat] counts on everyone being where the
     * song has them: one who sits really sits).
     */
    val settled: Boolean get() = leaving.isEmpty() && arriving.isEmpty() && eased == null && turned.isEmpty()

    /**
     * The band leaves the scene (the live act is unmounted): the instruments that go or come just now get their
     * own materials back, so that freeing the scene frees those and not only the copies they wear ([Veil]).
     */
    fun dispose() {
        leaving.forEach { it.veil?.lift() }
        arriving.forEach { coming -> coming.veils?.forEach { it.lift() } }
        leaving.clear()
        arriving.clear()
    }

    /**
     * How everyone was held at the last change of song, and when it was (the show's clock): each figure eases from
     * there into what the next song has it do (one who sat gets up, one whose instrument went lets the arms come
     * down), over [EASE] seconds. Null where no change is under way.
     */
    private var eased: Map<Player, CapturedPose>? = null
    private var easedAt: Double = 0.0

    /** How everyone stood turned at the last change of song: they turn from there into their way, over [EASE] seconds. */
    private var turned: Map<Player, Double> = emptyMap()

    /** Who sat in the song before and goes on with the same instrument: they get up from the change on ([easedAt]). */
    private var rising: Set<Player> = emptySet()

    /**
     * The band goes on with another song without leaving the stage (KLANG-00481; the maintainer, 2026-11-23: "let
     * the no longer needed instruments vanish; while they vanish everyone takes the new start pose; only when the
     * start pose is fully reached let the new instruments appear"). [cast], [plot], [strolls] and [idles] are the next
     * song's score's; [startsAt]: the moment on the show's
     * clock at which the song begins; [t]: now on that clock. The [plot] is the song's own: the walks to it are planned
     * here, from where everyone is. Call it once the show has taken the next song
     * (`Show.take`): the parts are read from it.
     *
     * Who keeps their instrument keeps the very player and its instrument, and gets the next song's part: the
     * drummer (both songs have him at the kit), and a strung player who has the same design in both. Every other
     * instrument of the song before goes, and every other one of the next song comes.
     */
    fun reseat(cast: Cast, plot: StagePlot, strolls: Player?, idles: Double, startsAt: Double, t: Double) {
        check(cast.drums.player == this.cast.drums.player) { "A change of the drummer is not built yet" }
        check(settled) { "The band is still in the change of song before" }

        val keeps = this.cast.keeps(cast)
        val beforeStrings = strings
        // the walks begin where everyone is now (she who strolls is not at her place)
        val here = this.plot.copy(
            places = this.plot.places.mapValues { (player, place) ->
                rig(player).root.position.let { place.copy(x = it.x, z = it.z) }
            },
        )

        // how everyone is held now, and who sits: they ease from there
        val held = rigs.mapValues { (_, rig) -> capture(rig) }
        val sat = this.plot.places.filterValues { it.sits }.keys

        easedAt = t
        turned = rigs.mapValues { (_, rig) -> rig.root.rotation.y }

        this.cast = cast
        this.plot = plot.after(before = here, keeps = keeps)
        this.strolls = strolls
        this.idles = idles
        this.startsAt = startsAt

        drummer.take(
            hits = drumPart(),
            idles = idlesOf(
                player = cast.drums.player,
                part = drumPart(),
                arrives = this.plot.entrances.getValue(cast.drums.player).end + SIT,
            ),
        )

        strings = cast.strings.map { seat ->
            val kept = beforeStrings.firstOrNull { it.first == seat.player }?.second?.takeIf { seat.player in keeps }

            if (kept != null) {
                kept.take(part = stringPart(seat), idles = idlesOf(player = seat.player, part = stringPart(seat)))

                seat.player to kept
            } else {
                val made = stringPlayerOf(seat)

                comes(player = seat.player, props = listOf(made.instrument.group))

                seat.player to made
            }
        }

        // the strung instruments nobody plays any more
        for ((player, old) in beforeStrings) {
            if (strings.none { it.first == player && it.second === old }) {
                goes(prop = old.instrument.group, t = t)
            }
        }

        marimba?.let { old ->
            goes(prop = old.marimba.group, t = t)
            old.marimba.mallets.values.flatten().forEach { goes(prop = it, t = t) }
        }
        frontwoman?.let { old -> goes(prop = old.trumpet.group, t = t) }

        marimba = cast.marimba?.let { seat ->
            marimbaPlayerOf(seat).also { made ->
                comes(player = seat.player, props = listOf(made.marimba.group) + made.marimba.mallets.values.flatten())
            }
        }
        frontwoman = cast.trumpet?.let { seat ->
            trumpetPlayerOf(seat).also { made -> comes(player = seat.player, props = listOf(made.trumpet.group)) }
        }
        strolling = strollingOf()

        // who goes on with the same player gets up by that player's own pose, where they sat; who has another one
        // now eases from how they were held into what it has them do
        val same = strings.filter { (player, now) -> beforeStrings.any { it.first == player && it.second === now } }.map { it.first }.toSet()

        rising = sat intersect same
        eased = held.filterKeys { it !in same && it != cast.drums.player }
    }

    /** [prop] goes from [t] on: it shrinks away ([Leaving]). */
    private fun goes(prop: Object3D, t: Double) {
        leaving.add(Leaving(prop = prop, since = t, from = prop.position.y))
    }

    /** [props] come once [player] has reached the start pose: not seen until then ([Arriving]). */
    private fun comes(player: Player, props: List<Object3D>) {
        arriving.add(Arriving(player = player, props = props))
        props.forEach { it.visible = false }
    }

    /**
     * The instruments that go and come at [t] (the show's clock; the maintainer, 2026-11-23: "float up, ease in and
     * get transparent ... and while appearing the other way around"). One that goes floats up [FLOAT] metres over
     * [VANISH] seconds, slowly at first, seen through more and more, and is then taken out and freed. One that comes
     * comes down from that high over [APPEAR] seconds, less and less seen through, from the moment its player has
     * been at their place for [settled] seconds (sat down, where they sit). [walks]: how each one walks now. Call it
     * after everyone is posed: a player puts their instrument each frame.
     */
    private fun swap(t: Double, walks: Map<Player, WalkIn>) {
        val gone = leaving.filter { t - it.since >= VANISH }

        for (left in gone) {
            left.prop.parent?.remove(left.prop)
            left.veil?.lift()
            left.prop.disposeOwn()
        }

        leaving.removeAll(gone)

        for (left in leaving) {
            val u = ((t - left.since) / VANISH).coerceIn(0.0, 1.0)
            val veil = left.veil ?: Veil(left.prop).also { left.veil = it }

            // (slowly at first: it lifts off)
            left.prop.position.y = left.from + FLOAT * u * u
            veil.show(1 - smooth(u))
        }

        val there = mutableListOf<Arriving>()

        for (coming in arriving) {
            val since = walks.getValue(coming.player).since - settled(coming.player)
            val here = smooth(since / APPEAR)

            if (here <= 0) {
                coming.props.forEach { it.visible = false }

                continue
            }

            val veils = coming.veils ?: coming.props.map { Veil(it) }.also { coming.veils = it }

            coming.props.forEachIndexed { index, prop ->
                // where its player has put it anew this frame, that is its place; else the one it had
                if (prop.position.y != coming.put[index]) {
                    coming.base[index] = prop.position.y
                }

                prop.visible = true
                prop.position.y = coming.base[index] + FLOAT * (1 - here) * (1 - here)
                coming.put[index] = prop.position.y
                veils[index].show(here)
            }

            if (here >= 1) {
                veils.forEach { it.lift() }
                there.add(coming)
            }
        }

        arriving.removeAll(there)
    }

    /** How long [player] takes at their place to reach the start pose: the time to sit down, where they sit. */
    private fun settled(player: Player): Double = if (plot.places.getValue(player).sits) SIT else 0.0

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
        // (told from the song's start, as the plot's walks are: on the show's clock it is that much later)
        val from = startsAt + arrives

        if (member.skills.none { it is IdleSkill } || !(idles > 0.0)) {
            return { emptyList() }
        }

        val roll = { index: Double -> show.own(index, member.character.seed) }

        return { t ->
            part.refresh()
            idlesAt(skills = member.skills, t = t, roll = roll, plays = part.events, arrives = from, margin = margin)
                // a quiet song keeps only some of them (the score's)
                .filter { keepsIdle(start = t - it.since, seed = member.character.seed, share = idles) }
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
        // (the plot's walks are told from the song's start)
        val walks = plot.entrances.mapValues { (player, entrance) ->
            enter(rig = rig(player), entrance = entrance, home = plot.places.getValue(player), t = t - startsAt)
        }

        // after a change of song nobody turns into their way at once
        if (turned.isNotEmpty()) {
            val w = smooth((t - easedAt) / EASE)

            if (w >= 1) {
                turned = emptyMap()
            } else {
                for ((player, was) in turned) {
                    val root = rig(player).root
                    val d = root.rotation.y - was

                    root.rotation.y = was + atan2(sin(d), cos(d)) * w
                }
            }
        }


        // the string players and the marimba player walk as they stand, the legs stepping and the hips moving as each
        // one's gait has them on the walk-in; one who strolls with the instrument, once there, as their stroll has them
        // (see [StringStroller]: it puts the figure, and the legs and the hips step by the way it walks)
        for ((player, stringPlayer) in strings) {
            val arrived = walks.getValue(player)
            val stroll = strolling?.takeIf { it.first == player }?.second
            val walk = stroll?.walk(t = t, walkIn = arrived) ?: arrived
            val gait = character(player).gait
            // (on a stroll the hips move as the kind of walk has them, as the trumpet player's do)
            val hips = stroll?.hips?.let { kind ->
                strollHips(rig = rig(player), stride = strideOf(rig(player), walk), going = walk.going, amount = gait.hipsWith(kind))
            } ?: walkInHips(rig = rig(player), walk = walk, gait = gait)
            // one whose place is on the floor sits down over the first SIT seconds after arriving there
            val down = if (plot.places.getValue(player).sits) smooth(walk.since / SIT) else 0.0
            // (and one who sat in the song before gets up first, from the change of song on)
            val up = if (player in rising) 1 - smooth((t - easedAt) / SIT) else 0.0
            val sits = max(down, up)

            stringPlayer.update(t, hips = hips, sits = sits)

            if (!plot.entrances.getValue(player).carries) {
                // coming from the song before with another instrument to play, the hands are empty: a plain walk.
                // At the place the body takes its start pose at once (it sits down, where it sits: nobody waits
                // for the others); the arms hang until that pose is reached, then the instrument comes and they
                // take it
                val takes = smooth((walk.since - settled(player)) / TAKE)

                if (takes < 1) {
                    val playing = capture(rig(player))

                    walkPose(rig = rig(player), walk = walk, gait = character(player).gait)
                    blendApart(rig = rig(player), a = capture(rig(player)), b = playing, body = smooth(walk.since / LAND), arms = takes)
                }
            }

            stepLegs(rig(player), walk)
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

        swap(t = t, walks = walks)

        // after a change of song, who has another instrument now eases from how they were held into this
        eased?.let { from ->
            val w = smooth((t - easedAt) / EASE)

            if (w >= 1) {
                eased = null
                rising = emptySet()
            } else {
                for ((player, pose) in from) {
                    blend(rig = rig(player), a = pose, b = capture(rig(player)), w = w)
                }
            }
        }
    }
}

/** The bones of the arms: the ones named after the joints from the shoulder to the fingers. */
private fun isArm(bone: String): Boolean = ARM_BONES.any { bone.startsWith(it) }

private val ARM_BONES = listOf("shoulder", "elbow", "wrist", "hand", "finger", "thumb")

/**
 * Sets [rig] between the poses [a] and [b] as `blend` does, the arms ([isArm]) [arms] of the way to [b] and the rest
 * of the body [body] of it.
 */
private fun blendApart(rig: Rig, a: CapturedPose, b: CapturedPose, body: Double, arms: Double) {
    for ((name, bone) in rig.bones) {
        bone.quaternion.copy(a.turns.getValue(name)).slerp(b.turns.getValue(name), if (isArm(name)) arms else body)
    }

    rig.bone("hips").position.copy(a.hips).lerp(b.hips, body)
    rig.update()
}

/**
 * Frees what an instrument that left the scene holds of its own: its shapes, and its materials but the ones every
 * instrument of its kind shares (`KitMaterials`, `StringMaterials`, `MarimbaMaterials`: those live as long as the
 * page). A material's pictures are not freed here: the instruments' own materials have none of their own.
 */
private fun Object3D.disposeOwn() {
    val shared: Set<Any> = setOf(
        StringMaterials.lacquer, StringMaterials.neckWood, StringMaterials.markedNeck, StringMaterials.metal, StringMaterials.gold,
        MarimbaMaterials.wood, MarimbaMaterials.brass, MarimbaMaterials.felt,
        KitMaterials.shell, KitMaterials.brass, KitMaterials.steel,
    )

    traverse { node ->
        val o: dynamic = node

        o.geometry?.dispose()

        val materials: Array<dynamic> = when {
            o.material == null -> emptyArray()
            js("Array.isArray")(o.material).unsafeCast<Boolean>() -> o.material.unsafeCast<Array<dynamic>>()
            else -> arrayOf(o.material)
        }

        for (material in materials) {
            if (shared.none { it === material }) {
                material.dispose()
            }
        }
    }
}
