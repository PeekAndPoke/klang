/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.band.Place
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.fromTyping
import io.peekandpoke.klang.viz.liveact.quat
import io.peekandpoke.klang.viz.liveact.smoother
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.applyQuaternion
import io.peekandpoke.klang.workshop.threejs.asLight
import io.peekandpoke.klang.workshop.threejs.asSkinnedMesh
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.cross
import io.peekandpoke.klang.workshop.threejs.distanceTo
import io.peekandpoke.klang.workshop.threejs.fog
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.klang.workshop.threejs.getWorldQuaternion
import io.peekandpoke.klang.workshop.threejs.isMesh
import io.peekandpoke.klang.workshop.threejs.setY
import io.peekandpoke.kraft.addons.threejs.js.DirectionalLight
import io.peekandpoke.kraft.addons.threejs.js.PerspectiveCamera
import io.peekandpoke.kraft.addons.threejs.js.Scene
import kotlinx.browser.window
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Where the camera is: a fixed look where the page asks for one, else the choreography, else the total.

/**
 * Places [camera] for each frame ([look]): a fixed look where the show's switches ask for one, else the
 * choreography (when the song is there; `?still`: the wide view only), else the total. The choreography goes by
 * where each player stands ([homes], the stage plot's), when they play ([activity]), and who plays seldom and who
 * strolls ([seldom], [strolls], the cast's): see [Choreography].
 *
 * The camera by hand: a key takes it over from the choreography, until 0 or Escape gives it back (where Esc
 * also closes what the live act is shown in, as in the app, only 0 is of use).
 * ```
 *   q the total    w the stadium shot    e from the left    r from above    t from the right    y (or z) from the floor
 *   u the reverse total, from behind the drummer (hovering)
 *   1 to 6 a player (drums, lead guitar, frontwoman, bass, rhythm guitar, marimba); the same key again: their next look
 *   a a flight round the whole band (again: the other way round)    s a slow way round the last player chosen
 *   d a dolly zoom back to the total from wherever the camera is; a plain cut where the camera looks away from the
 *     total (the reverse total, the drummer over his shoulder, behind the band): the dolly would swing it right round
 * ```
 * The keys are heard from the window until [dispose].
 */
class View(
    show: Show,
    private val scene: Scene,
    private val camera: PerspectiveCamera,
    private val rigs: Map<Player, Rig>,
    homes: Map<Player, Place>,
    activity: (Player) -> List<Double>,
    seldom: List<Player>,
    strolls: Player,
) {
    private val switches = show.switches

    /** A bar, in seconds. */
    private val bar: Double = show.bar

    /** The looks at each player, clear of the others where they stand. */
    private val looks: Map<Player, List<Placement>> = looksOn(homes)

    /** The camera's choreography, when the song is there (`?still`: the wide view only). */
    private val choreography: Choreography? = if (show.song != null && !switches.has(Switch.Still)) {
        Choreography(
            show = show,
            rigs = rigs,
            looks = looks,
            activity = activity,
            seldom = seldom,
            strolls = strolls,
            force = forcedKind(switches[Switch.Move]),
        )
    } else {
        null
    }

    /** `?view=side`, or stadium, or a player's name: a fixed camera. */
    private val view: String = switches[Switch.View].takeUnless { it.isNullOrEmpty() } ?: "front"

    /**
     * `?head=<player>[&from=side|front|back|threequarter]`: a close look at one head, with a plain inspection light,
     * to judge a head's shape (a still for the maintainer; no part of the show).
     */
    private val inspect: DirectionalLight? = if (switches.has(Switch.Head)) {
        scene.add(Three.addon.createAmbientLight(0xffffff, 0.9))

        val light = Three.addon.createDirectionalLight(0xffffff, 2.2)

        scene.add(light)
        scene.add(light.target)

        // &outline: the bare shape, black on white (pattern and glitter hide a profile)
        if (switches.has(Switch.Outline)) {
            scene.background = Three.addon.createColor(0xffffff)
            scene.fog = null
        }

        light
    } else {
        null
    }

    private var label: String = "fixed"
    private var hand: Hand? = null
    private var shown: Shot? = null
    private var clock: Double = 0.0
    private var player: Player = Player.Skalpell
    private val lookOf: MutableMap<Player, Int> = mutableMapOf()
    private var way: Int = 1

    private val keyListener: ((Event) -> Unit)? = if (choreography != null) ({ event: Event -> onKey(event) }) else null

    init {
        keyListener?.let { window.addEventListener("keydown", it) }
    }

    // The haze that darkens what is far begins at the stage as seen from the start position. From further away it
    // begins that much later, so the stage is as clear from the back of the audience as from its front.
    private val fogNear: Double = scene.fog?.near ?: 0.0
    private val fogDeep: Double = scene.fog?.let { it.far - it.near } ?: 0.0
    private val usual: Double = HOME.pos.distanceTo(HOME.at)

    /** Places the camera for the frame at [t]. Call it after everything that moves a figure. */
    fun look(t: Double) {
        place(t)

        scene.fog?.let { fog ->
            fog.near = fogNear + max(0.0, camera.position.distanceTo(HOME.at) - usual)
            fog.far = fog.near + fogDeep
        }
    }

    /** What the camera is doing just now, in words (for the line at the top). */
    fun what(): String = label

    /** Stops listening to the keys. */
    fun dispose() {
        keyListener?.let { window.removeEventListener("keydown", it) }
    }

    /** The camera by hand: what a key asked for. */
    private sealed class Hand {
        /** When the move began, by the show's clock; null for what stands still. */
        open val from: Double? get() = null

        /** What it shows, in words. */
        abstract val words: String

        data object Home : Hand() {
            override val words: String get() = "total"
        }

        class Fixed(val subject: Subject) : Hand() {
            override val words: String get() = subject.words
        }

        class Circle(override val from: Double, val way: Int) : Hand() {
            override val words: String get() = "circle round the band"
        }

        class Arc(override val from: Double, val way: Int, val player: Player) : Hand() {
            override val words: String get() = "arc round ${player.key}"
        }

        class Back(override val from: Double, val shot: Shot) : Hand() {
            override val words: String get() = "dolly back"
        }
    }

    private fun onKey(event: Event) {
        val e = event as? KeyboardEvent ?: return

        if (e.ctrlKey || e.metaKey || e.altKey || event.fromTyping()) {
            return
        }

        val k = e.key.lowercase()
        val angle = ANGLE_KEYS[k]
        val last = shown

        when {
            k == "0" || k == "escape" -> hand = null

            k == "q" -> hand = Hand.Home

            angle != null -> hand = Hand.Fixed(Subject.Angle(angle))

            k >= "1" && k <= "6" -> {
                val chosen = ORDER[k.first().digitToInt() - 1]
                val theirs = looks.getValue(chosen)
                val before = lookOf[chosen] ?: 0

                lookOf[chosen] = if (chosen == player && hand is Hand.Fixed) (before + 1) % theirs.size else before
                player = chosen
                hand = Hand.Fixed(Subject.OnPlayer(player = chosen, look = theirs[lookOf.getValue(chosen)]))
            }

            k == "a" -> {
                way = -way
                hand = Hand.Circle(from = clock, way = way)
            }

            k == "s" -> {
                way = -way
                hand = Hand.Arc(from = clock, way = way, player = player)
            }

            k == "d" && last != null -> {
                // a camera that looks away from the total would be swung right round on the way: it cuts
                hand = if (choreography != null && !choreography.away(last)) {
                    Hand.Back(from = clock, shot = Shot(pos = last.pos.clone(), at = last.at.clone(), fov = last.fov))
                } else {
                    Hand.Home
                }
            }

            else -> Unit
        }
    }

    /** The camera by hand at [t], or null when the choreography has it. A flight that is over leaves the total. */
    private fun byHand(t: Double, choreography: Choreography): Shot? {
        val now = hand ?: return null
        val home = choreography.home(t)
        val u = now.from?.let { max(0.0, t - it) } ?: 0.0

        label = "by hand: " + now.words

        when (now) {
            is Hand.Fixed -> {
                val subject = now.subject

                // the reverse total hovers here too
                return if (subject is Subject.Angle && subject.angle === REVERSE) choreography.reverse(t) else choreography.shot(subject)
            }

            is Hand.Circle -> if (u < HAND_CIRCLE * bar) {
                return choreography.circleBy(way = now.way, u = smoother(u / (HAND_CIRCLE * bar)), home = home)
            }

            is Hand.Arc -> return choreography.arcBy(player = now.player, way = now.way, u = min(1.0, u / (9 * bar)))

            is Hand.Back -> if (u < 5 * bar) {
                return choreography.back(shot = now.shot, home = home, u = smoother(u / (5 * bar)))
            }

            Hand.Home -> Unit
        }

        hand = Hand.Home

        return home
    }

    private fun place(t: Double) {
        clock = t

        val headRig = if (switches.has(Switch.Head)) Player.of(switches[Switch.Head])?.let { rigs[it] } else null
        val viewRig = Player.of(view)?.let { rigs[it] }
        val choreography = choreography

        when {
            headRig != null -> inspectHead(headRig)

            switches.has(Switch.Cam) -> {
                val c = numbers(switches[Switch.Cam].orEmpty())
                val a = numbers(switches[Switch.At].takeUnless { it.isNullOrEmpty() } ?: "0,1,0")

                camera.position.set(c(0), c(1), c(2))
                camera.lookAt(a(0), a(1), a(2))
            }

            view == "stadium" -> {
                camera.position.set(STADIUM.pos.first, STADIUM.pos.second, STADIUM.pos.third)
                camera.lookAt(STADIUM.at.first, STADIUM.at.second, STADIUM.at.third)

                if (camera.fov != STADIUM.fov) {
                    camera.fov = STADIUM.fov
                    camera.updateProjectionMatrix()
                }
            }

            view == "side" -> {
                camera.position.set(8.5, 2.2, 5.5)
                camera.lookAt(0.0, 1.0, -0.4)
            }

            viewRig != null -> {
                val p = viewRig.root.position

                camera.position.set(p.x + 1.6, 1.5, p.z + 4.2)
                camera.lookAt(p.x, 1.0, p.z)
            }

            choreography != null -> {
                val mine = byHand(t, choreography)
                val shot = mine ?: choreography.at(t)

                if (mine == null) {
                    label = choreography.label
                }

                shown = shot
                camera.position.copy(shot.pos)
                camera.lookAt(shot.at)

                if (abs(camera.fov - shot.fov) > 1e-3) {
                    camera.fov = shot.fov
                    camera.updateProjectionMatrix()
                }
            }

            else -> {
                camera.position.set(0.0, 1.85, 9.4)
                camera.lookAt(0.0, 1.0, 0.0)
            }
        }
    }

    /** The close look at [rig]'s head (`?head=`). */
    private fun inspectHead(rig: Rig) {
        // &down=0.3: look that far below the head (the chest); &far=1.8: from that far off
        val down = switches.double(Switch.Down) ?: 0.0
        val at = rig.bone("head").getWorldPosition(vec()).add(vec(0.0, 0.07 * rig.height / 1.6 - down, 0.0))
        val fromKey = switches[Switch.From].takeUnless { it.isNullOrEmpty() } ?: "side"
        val from = InspectFrom.of(fromKey) ?: error("No such side to look at a head from: '$fromKey'")
        // by where the face looks just now, not the body: a profile stays a profile when the head is turned
        val ahead = rig.faceAhead.clone().applyQuaternion(rig.bone("head").getWorldQuaternion(quat())).setY(0.0).normalize()
        val side = vec(0.0, 1.0, 0.0).cross(ahead)
        val toward = side.multiplyScalar(from.x).addScaledVector(ahead, from.z).add(vec(0.0, from.y, 0.0))

        camera.position.copy(at).addScaledVector(toward.normalize(), switches.double(Switch.Far) ?: 1.2)
        camera.lookAt(at)

        if (camera.fov != 24.0) {
            camera.fov = 24.0
            camera.updateProjectionMatrix()
        }

        if (switches.has(Switch.Outline)) {
            // nothing but this figure: no light, no stage, no instrument, nobody else
            scene.traverse { o ->
                val light = o.asLight

                if (light != null) {
                    light.intensity = 0.0
                } else if (o.isMesh && o.asSkinnedMesh == null) {
                    o.visible = false
                }
            }

            for (other in rigs.values) {
                other.root.visible = other === rig
            }
        }

        inspect?.let { light ->
            light.position.copy(camera.position).add(vec(0.4, 0.8, 0.0))
            light.target.position.copy(at)
        }
    }

    /** Where `&from=` puts the camera, against the face: to its side, ahead, up. */
    private enum class InspectFrom(val key: String, val x: Double, val y: Double, val z: Double) {
        Side("side", x = 1.0, y = 0.05, z = 0.0),
        Front("front", x = 0.0, y = 0.05, z = 1.0),
        Back("back", x = 0.0, y = 0.1, z = -1.0),
        ThreeQuarter("threequarter", x = 0.7, y = 0.1, z = 0.7);

        companion object {
            fun of(key: String): InspectFrom? = entries.firstOrNull { it.key == key }
        }
    }

    private companion object {
        /** How long the circle round the band takes by hand (the key a), in bars: slower than it was (16). */
        const val HAND_CIRCLE = 22

        /** The keys for a fixed angle on the stage. */
        val ANGLE_KEYS: Map<String, Placement> =
            mapOf(
                "e" to ANGLES[0], "r" to ANGLES[1], "t" to ANGLES[2], "y" to ANGLES[3], "z" to ANGLES[3], "w" to STADIUM,
                "u" to REVERSE,
            )

        /**
         * `?move=`: the kind every round is made. The prototype takes any word here: an empty one leaves it to the
         * seed, and one that names no kind gives rounds that run as looks.
         */
        fun forcedKind(move: String?): RoundKind? {
            if (move.isNullOrEmpty()) {
                return null
            }

            return RoundKind.of(move) ?: RoundKind.Looks
        }

        /**
         * Numbers with commas between them (`cam=1,2,3`), read as JavaScript's `Number` reads them in the plain
         * cases (an empty one is 0, one that is no number NaN); one that is missing is NaN too.
         */
        fun numbers(text: String): (Int) -> Double {
            val list = text.split(",").map { part ->
                val trimmed = part.trim()

                if (trimmed.isEmpty()) 0.0 else trimmed.toDoubleOrNull() ?: Double.NaN
            }

            return { index -> list.getOrElse(index) { Double.NaN } }
        }
    }
}
