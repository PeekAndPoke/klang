/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.common

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.Timed
import io.peekandpoke.klang.viz.liveact.X_AXIS
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.Z_AXIS
import io.peekandpoke.klang.viz.liveact.around
import io.peekandpoke.klang.viz.liveact.band.Grip
import io.peekandpoke.klang.viz.liveact.band.Straighten
import io.peekandpoke.klang.viz.liveact.band.Tuning
import io.peekandpoke.klang.viz.liveact.inSight
import io.peekandpoke.klang.viz.liveact.lastIndexAt
import io.peekandpoke.klang.viz.liveact.mat4
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.song.Part
import io.peekandpoke.klang.viz.liveact.song.Strum
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.ExtrudeGeometryOptions
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.angleTo
import io.peekandpoke.klang.workshop.threejs.applyAxisAngle
import io.peekandpoke.klang.workshop.threejs.copied
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.createExtrudeGeometry
import io.peekandpoke.klang.workshop.threejs.createShape
import io.peekandpoke.klang.workshop.threejs.crossVectors
import io.peekandpoke.klang.workshop.threejs.dot
import io.peekandpoke.klang.workshop.threejs.emissive
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.klang.workshop.threejs.quaternion
import io.peekandpoke.klang.workshop.threejs.worldToLocal
import io.peekandpoke.kraft.addons.threejs.js.BufferGeometry
import io.peekandpoke.kraft.addons.threejs.js.Color
import io.peekandpoke.kraft.addons.threejs.js.Group
import io.peekandpoke.kraft.addons.threejs.js.MaterialParameters
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import io.peekandpoke.kraft.utils.jsObject
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

// The string players: a bass or a guitar on a strap. The left hand is where a player's would be for the note (the
// string and the fret of the instrument's real tuning, see Tuning), the right hand plucks (bass) or strums down and
// up (guitar) on every note; the strings light up when played.
// All places are in the figure's own measures; the instrument hangs from the trunk and moves with it.

// how a played string's light falls, in seconds to 1/e: while the note rings, and once it has ended
private const val RING = 0.25
private const val DAMP = 0.06

/** A note this many seconds ahead is settled: the left hand is on its way to it, and it stays held as planned. */
private const val SETTLED = 0.4

/** How far along the neck the fingers reach from the hand's own place, in the figure's measure. */
private const val REACH = 0.04

/**
 * The instruments' surfaces, shared by every string player: made together when the first instrument is built
 * (three.js must be loaded by then).
 */
private object StringMaterials {
    val lacquer: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(
        jsObject<MaterialParameters> {
            color = 0x0a0a0d
            roughness = 0.25
            metalness = 0.3
        },
    )

    val neckWood: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(
        jsObject<MaterialParameters> {
            color = 0x1c140e
            roughness = 0.5
        },
    )

    val metal: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(
        jsObject<MaterialParameters> {
            color = 0x1a1a20
            roughness = 0.25
            metalness = 0.95
        },
    )
}

/**
 * Body outlines, seen from the front with the neck to the right (x along the neck, y across, in the figure's
 * measure): from the neck pocket's lower corner round the body to its upper corner, as points a smooth curve goes
 * through. None is a maker's model: a double cutaway with two horns, a single cutaway, a long-horned bass.
 */
enum class BodyOutline(val points: List<Pair<Double, Double>>) {
    DoubleCutaway(
        listOf(
            0.12 to -0.045, 0.15 to -0.09, 0.185 to -0.125, 0.14 to -0.15, 0.06 to -0.14, -0.02 to -0.12, -0.13 to -0.175,
            -0.225 to -0.105, -0.24 to 0.0, -0.225 to 0.10, -0.13 to 0.17, -0.02 to 0.125, 0.07 to 0.145, 0.16 to 0.15,
            0.215 to 0.115, 0.17 to 0.085, 0.12 to 0.045,
        ),
    ),
    SingleCutaway(
        listOf(
            0.12 to -0.045, 0.165 to -0.075, 0.19 to -0.115, 0.14 to -0.148, 0.05 to -0.135, -0.02 to -0.118,
            -0.13 to -0.172, -0.22 to -0.108, -0.235 to 0.0, -0.22 to 0.108, -0.13 to 0.170, -0.02 to 0.122,
            0.07 to 0.140, 0.13 to 0.112, 0.145 to 0.07, 0.12 to 0.045,
        ),
    ),
    LongHornBass(
        listOf(
            0.15 to -0.05, 0.18 to -0.09, 0.22 to -0.12, 0.16 to -0.15, 0.05 to -0.135, -0.03 to -0.115, -0.16 to -0.18,
            -0.255 to -0.11, -0.275 to 0.0, -0.255 to 0.11, -0.15 to 0.172, -0.02 to 0.125, 0.08 to 0.14, 0.20 to 0.137,
            0.295 to 0.10, 0.225 to 0.075, 0.15 to 0.05,
        ),
    ),
}

/**
 * A string instrument and how it is played. [scale]: from the nut to the bridge; [bodyHalfLength] and
 * [bodyHalfWidth]: the body's half length and half width; [strings]: how many; [neck]: the neck's width; [tilt]:
 * the neck's tilt; how the right hand plays: [swing] how far it goes through the strings, [tau] how quickly (in
 * seconds), [arm] the share of the swing the arm does (the rest is the wrist); [hang]: how low it hangs from the
 * trunk; [outline]: the body's outline unless the player has another; [pickups]: where the pickups sit along it;
 * [head]: the head's outline, as the body's is drawn; [tuning]: its strings' notes, by which the left hand finds
 * each note's string and fret.
 */
enum class StringKind(
    val scale: Double,
    val bodyHalfLength: Double,
    val bodyHalfWidth: Double,
    val strings: Int,
    val neck: Double,
    val tilt: Double,
    val swing: Double,
    val tau: Double,
    val hang: Double,
    val arm: Double,
    val outline: BodyOutline,
    val pickups: List<Double>,
    val head: List<Pair<Double, Double>>,
    val tuning: Tuning,
) {
    Guitar(
        scale = 0.64, bodyHalfLength = 0.22, bodyHalfWidth = 0.165, strings = 6, neck = 0.044, tilt = 0.50,
        swing = 0.095, tau = 0.028, hang = -0.09, arm = 0.15, outline = BodyOutline.DoubleCutaway,
        pickups = listOf(0.01, 0.075),
        head = listOf(0.0 to -0.022, 0.035 to -0.032, 0.16 to -0.038, 0.19 to -0.012, 0.172 to 0.026, 0.04 to 0.03, 0.0 to 0.022),
        tuning = Tuning.GUITAR,
    ),
    Bass(
        scale = 0.84, bodyHalfLength = 0.25, bodyHalfWidth = 0.17, strings = 4, neck = 0.05, tilt = 0.62,
        swing = 0.040, tau = 0.02, hang = -0.13, arm = 0.35, outline = BodyOutline.LongHornBass,
        pickups = listOf(0.0, 0.07),
        head = listOf(0.0 to -0.025, 0.04 to -0.04, 0.19 to -0.048, 0.23 to -0.015, 0.20 to 0.034, 0.05 to 0.036, 0.0 to 0.025),
        tuning = Tuning.BASS,
    ),
}

/**
 * Looks at the viewer every now and then: a chance every [every] seconds, of which [chance] are taken. [at] is where
 * the camera is: the very vector the show keeps up to date, read whenever the player looks.
 */
class Glance(val at: Vector3, val every: Double, val chance: Double)

/** One pluck or strum, as this player plays it: where the hands go for it and which strings sound. */
private class Pluck(
    override val t: Double,
    val v: Double,
    val d: Double,
    /** The notes that sound, as the song has them, and how the left hand holds them. */
    val pitches: List<Int>,
    val grip: Grip,
    /** The fingers' place along the neck, from the nut: at the frets they press. */
    val along: Double,
    /** The hand's own place along the neck: the middle of the four frets its fingers reach in its position. */
    val hold: Double,
    /** Where the fingers' strings lie across the instrument, drawn two and a half times as far apart as the strings are. */
    val across: Double,
    /** The strings that sound, the lowest is 0. */
    val strings: List<Int>,
    /** Where the right hand plays across the strings: the middle of those that sound. */
    val pick: Double,
    /** How wide the right hand goes through them: 1 for several strings, less for one. */
    val wide: Double,
    /** Which way the right hand goes through the strings: 1 down, -1 up. */
    val way: Double,
) : Timed

/**
 * The hands' places at one moment: the fingers [along] the neck and [across] it, the left hand's own place along it
 * ([hold]), where the right hand [pick]s and how [wide].
 */
private class Fret(val along: Double, val hold: Double, val across: Double, val pick: Double, val wide: Double)

/**
 * A string player: a bass or a guitar on a strap, played by [rig].
 *
 * [notes]: sorted by time, one per pluck or strum; the nod goes by the [show]'s tempo. [traits]: how the player
 * moves with the music, with the show's chance under [seed] (the player's own; the prototype's fallback is 4, the
 * band gives each player one of their own). [lean]: how the trunk leans (back is
 * negative); [bang]: how much the head goes; [bounce]: how far the hips dip on the beat; [outline]: the body's
 * outline, the [kind]'s own unless given; [glance]: whether and how the player looks at the viewer; [straighten]:
 * when the player straightens up from the instrument (null: never; `?straight=1` holds everyone straight, to look
 * at the pose).
 */
class StringPlayer(
    private val show: Show,
    private val rig: Rig,
    private val kind: StringKind,
    private val part: Part<Strum>,
    traits: Traits,
    private val seed: Double = 4.0,
    private val lean: Double = 0.06,
    private val bang: Double = 0.2,
    private val bounce: Double = 0.03,
    outline: BodyOutline = kind.outline,
    private val glance: Glance? = null,
    private val straighten: Straighten? = Straighten(every = 21.0, chance = 0.55),
) {
    private val mover = Mover(show = show, seed = seed, traits = traits)

    /**
     * When this player's stretches of straightening up begin, in seconds into the song: each player's at another
     * moment, so they do not all rise together, and nobody in the song's first seconds.
     */
    private val straightenFrom: Double = straighten?.let { it.every * (0.3 + 0.7 * show.own(0, seed + 32.5)) } ?: 0.0
    private val s = rig.s
    private val bridge = -0.10 * s
    private val nut = bridge + kind.scale * s

    /** The instrument, in its own space: x along the neck toward the head, y across the strings (up), z out of its face. */
    val instrument: Group = Three.addon.createGroup()

    /** A string each, the lowest first, lit on its own when it is the one played. */
    private val strings: List<MeshStandardMaterial>

    private var notes: List<Pluck> = emptyList()

    /**
     * The last pluck before the first one known, where the song has forgotten its past: the right hand's way
     * through the strings goes on from it, and the plucks begin with it: the hands stay where it left them.
     */
    private var forgotten: Pluck? = null

    init {
        val addon = Three.addon
        val k = kind
        val g = instrument

        // the body: its outline cut from a slab, the edges rounded; pickups, a bridge and two knobs on its face
        val body = addon.createMesh(
            extruded(
                points = outline.points,
                s = s,
                depth = 0.036 * s,
                bevelSize = 0.010 * s,
                bevelThickness = 0.010 * s,
                bevelSegments = 3,
                curveSegments = 14,
            ),
            StringMaterials.lacquer,
        )

        body.position.z = -0.040 * s

        val neckFrom = (k.bodyHalfLength - 0.10) * s
        val neck = addon.createMesh(addon.createBoxGeometry(nut - neckFrom, k.neck * s, 0.022 * s), StringMaterials.neckWood)

        neck.position.set((nut + neckFrom) / 2, 0.0, 0.006 * s)

        val head = addon.createMesh(
            extruded(
                points = k.head,
                s = s,
                depth = 0.014 * s,
                bevelSize = 0.003 * s,
                bevelThickness = 0.003 * s,
                bevelSegments = 2,
                curveSegments = 10,
            ),
            StringMaterials.lacquer,
        )

        head.position.set(nut, 0.0, -0.012 * s)
        head.rotation.y = 0.10
        g.add(body)
        g.add(neck)
        g.add(head)

        for (x in k.pickups) {
            val pickup = addon.createMesh(addon.createBoxGeometry(0.022 * s, k.neck * 1.25 * s, 0.008 * s), StringMaterials.metal)

            pickup.position.set(x * s, 0.0, 0.012 * s)
            g.add(pickup)
        }

        val saddle = addon.createMesh(addon.createBoxGeometry(0.018 * s, k.neck * 1.3 * s, 0.014 * s), StringMaterials.metal)

        saddle.position.set(bridge, 0.0, 0.014 * s)
        g.add(saddle)

        for ((x, y) in listOf(-0.15 to -0.085, -0.19 to -0.05)) {
            val knob = addon.createMesh(addon.createCylinderGeometry(0.011 * s, 0.013 * s, 0.014 * s, 12), StringMaterials.metal)

            knob.rotation.x = PI / 2
            knob.position.set(x * s, y * s, 0.014 * s)
            g.add(knob)
        }

        for (i in 0 until k.strings) {
            val tuner = addon.createMesh(addon.createCylinderGeometry(0.006 * s, 0.006 * s, 0.03 * s, 8), StringMaterials.metal)
            val along = (i + 0.7) / (k.strings + 0.6)

            tuner.position.set(nut + (0.03 + 0.14 * along) * s, -0.036 * s, -0.004 * s)
            g.add(tuner)
        }

        val glow = rig.eyes?.emissive?.copied() ?: addon.createColor(0xffffff)

        strings = (0 until k.strings).map { i ->
            val lit = addon.createMeshStandardMaterial(
                jsObject<MaterialParameters> {
                    color = 0x303038
                    roughness = 0.3
                    metalness = 0.9
                    emissive = glow
                    emissiveIntensity = 0.0
                },
            )
            val y = (0.5 - i.toDouble() / (k.strings - 1)) * k.neck * 0.8 * s         // the lowest string is the upper one
            val string = addon.createMesh(addon.createBoxGeometry(nut - bridge, 0.0025 * s, 0.0025 * s), lit)

            string.position.set((nut + bridge) / 2, y, 0.02 * s)
            g.add(string)

            lit
        }

        // only the body, the neck and the head throw a shadow: the shadows of the pickups, the saddle and the knobs
        // fall inside theirs, a tuner's all but a centimetre of it, and a string's is thinner than a point of the
        // shadow's map
        for (part in listOf(body, neck, head)) {
            part.castShadow = true
        }

        (rig.root.parent ?: error("The figure stands in no scene")).add(g)

        part.refresh()
        notes = fingered(strums = part.events, now = Double.NEGATIVE_INFINITY)
    }

    /**
     * Where the hands are for each of [strums]. The left hand holds each as a player would on the instrument's real
     * strings: the string and the fret of each note, in a position on the neck it leaves as seldom as the passage
     * allows (see [Tuning.grips]); a note too low or too high for the instrument is played octaves nearer.
     *
     * Where the song grows or changes, what was played up to [now] (and the note the hand is already going to) stays
     * as it was held; only what is still to come is worked out anew.
     */
    private fun fingered(strums: List<Strum>, now: Double): List<Pluck> {
        val k = kind
        // what the song has forgotten since the last plan: the hands go on from the last of it
        val from = strums.firstOrNull()?.t ?: Double.POSITIVE_INFINITY

        // (none where the song begins again before it: the plucks stay in the order of their times)
        forgotten = (this.notes.lastOrNull { it.t < from } ?: forgotten)?.takeIf { it.t < from }

        val before = this.notes.associateBy { it.t }
        val kept = mutableListOf<Grip>()

        for (n in strums) {
            val was = before[n.t]

            if (was == null || n.t > now + SETTLED || was.pitches.sorted() != n.all.sorted()) {
                break
            }

            kept.add(was.grip)
        }

        val lead = listOfNotNull(forgotten)
        val grips = k.tuning.grips(
            strums = lead.map { it.pitches } + strums.map { it.all },
            held = lead.map { it.grip } + kept,
        ).drop(lead.size)

        /** How far from the nut a finger lies that presses at [fret]: just behind the fret's wire. */
        fun alongOf(fret: Double): Double = k.scale * s * (1 - 2.0.pow(-max(0.0, fret - 0.4) / 12))

        /** Where the [string] lies across the instrument, drawn two and a half times as far apart as the strings are. */
        fun acrossOf(string: Double): Double = (0.5 - string / (k.strings - 1)) * k.neck * 0.8 * s * 2.5

        // the right hand's way through the strings at each note: down after a pause, else down and up in turn
        var way = forgotten?.way ?: 1.0

        return lead + strums.mapIndexed { i, n ->
            val grip = grips[i]
            // the strings that sound: each note's own
            val sounding = grip.stops.map { it.string }.ifEmpty { listOf(0) }
            // the fingers lie on the strings that are pressed, at the middle of their frets; where every string is
            // open they stay over the neck where the hand is
            val pressed = grip.stops.filter { it.fret > 0 }
            val fret = if (pressed.isEmpty()) grip.position + 1.0 else pressed.sumOf { it.fret }.toDouble() / pressed.size
            val fingers = pressed.map { it.string }.ifEmpty { sounding }
            // the right hand plays at the middle of the strings that sound; one string is picked, several are strummed
            val mid = sounding.sum().toDouble() / sounding.size
            val gap = if (i > 0) n.t - strums[i - 1].t else forgotten?.let { n.t - it.t } ?: 9.0

            way = if (gap > 0.28) 1.0 else -way

            Pluck(
                t = n.t,
                v = n.v,
                d = n.d,
                pitches = n.all,
                grip = grip,
                along = alongOf(fret),
                hold = alongOf(grip.position + Tuning.SPAN / 2.0),
                across = acrossOf(fingers.sum().toDouble() / fingers.size),
                strings = sounding,
                pick = acrossOf(mid),
                wide = if (sounding.size > 1) 1.0 else 0.4,
                way = way,
            )
        }
    }

    /** The left hand's place along the neck at [t]: it stays on a note and changes to the next just before it sounds. */
    private fun fretAt(t: Double): Fret {
        val (before, after) = notes.around(t)
        val from = before ?: after
        val to = after ?: before

        if (from == null || to == null) {
            return Fret(along = 0.1 * s, hold = 0.1 * s, across = 0.0, pick = 0.0, wide = 1.0)
        }

        val gap = max(0.02, to.t - from.t)
        val window = min(gap * 0.9, 0.32)                             // unhurried: the hand glides to the next note
        val move = if (after != null && before != null) smooth(1 - (after.t - t) / window) else 1.0

        fun mix(a: Double, b: Double): Double = a + (b - a) * move

        return Fret(
            along = mix(from.along, to.along),
            hold = mix(from.hold, to.hold),
            across = mix(from.across, to.across),
            pick = mix(from.pick, to.pick),
            wide = mix(from.wide, to.wide),
        )
    }

    /**
     * How much the player looks at the viewer at [t], 0 to 1 (see [Glance]). Chance decides, the same at the same
     * place in the song.
     */
    private fun glanceAt(t: Double): Double {
        val g = glance ?: return 0.0
        val slot = floor(t / g.every)

        fun roll(n: Int): Double = show.own(slot + n, seed)

        if (roll(0) > g.chance) {
            return 0.0
        }

        val start = slot * g.every + 0.8 + roll(1) * (g.every - 6)
        val hold = 2.2 + 2.0 * roll(2)
        val x = t - start

        return smooth(x / 0.7) * smooth((hold + 0.7 - x) / 0.9)
    }

    /** The right hand's place across the strings at [t], -1 (above them) to 1 (below): through them at each note. */
    private fun strumAt(t: Double): Double {
        val (before, after) = notes.around(t)
        val tau = kind.tau

        fun one(n: Pluck): Double = n.way * (0.4 + 0.6 * n.v) * tanh((t - n.t) / tau)

        return when {
            before != null && after != null ->
                one(before) + (one(after) - one(before)) * smooth((t - before.t) / (after.t - before.t))
            before != null -> one(before)
            after != null -> one(after)
            else -> -0.5
        }
    }

    /** The player at [t]: the strings' light, the body with the beat, the instrument on the trunk, both hands. */
    fun update(t: Double) {
        if (part.refresh()) {
            notes = fingered(strums = part.events, now = t)
        }

        val k = kind

        var energy = 0.0
        // a string's light fades as a plucked string's sound does: one exponential fall from the pluck (RING), and a
        // quicker one from where the note ends (the next one at the latest), as the hand damps it (DAMP)
        val lit = DoubleArray(strings.size)
        var i = notes.lastIndexAt(t)

        while (i >= 0 && notes[i].t > t - 4.0) {
            val n = notes[i]
            val age = t - n.t
            val next = notes.getOrNull(i + 1)
            val held = min(n.d, if (next != null) next.t - n.t else 9.0)
            val level = exp(-age / RING - max(0.0, age - held) / DAMP)

            energy += n.v * exp(-age / 0.25)

            for (string in n.strings) {
                lit[string] = max(lit[string], n.v * level)
            }

            i--
        }

        strings.forEachIndexed { index, material -> material.emissiveIntensity = 3.4 * lit[index] }

        val playing = min(1.0, energy)

        // the body: knees giving on the beat, the trunk leaning, the head going with the beat
        val beat = t * show.bpm / 60
        // how much he is playing drives the body, less so for a steady player
        val live = 0.35 + 0.65 * playing
        val drive = live + (1 - live) * mover.traits.steady
        val nod = mover.nod(beat) * drive
        val body = nod + mover.burst()
        val parts = mover.parts
        val sway = sin(PI * beat / 2) * drive * parts.sway

        rig.reset()
        rig.update()
        rig.moveHips(vec(0.03 * sway * s, -(0.04 + bounce * body * parts.knees) * rig.pelvis.y, 0.0))
        // now and then the player straightens up from the instrument: the back comes up (a little past upright, and
        // not forward of where a player who leans back already is), the chest opens, the head lifts and looks out
        val up = show.switches.double("straight")?.coerceIn(0.0, 1.0)
            ?: straighten?.at(t - straightenFrom) { stretch, purpose -> show.own(stretch * 3 + purpose, seed + 31.5) }
            ?: 0.0
        val straight = min(lean, 0.0) - 0.05

        rig.turn("belly", X_AXIS, lean + (straight - lean) * up + 0.04 * body * parts.trunk)
        rig.turn("chest", X_AXIS, -0.06 * up)
        rig.turn("chest", Z_AXIS, -0.05 * sway)
        rig.turn("chest", Y_AXIS, 0.18)

        // a look at the viewer every now and then: the head comes up from the instrument and turns to the camera,
        // holds it a few seconds and goes back
        var look = glanceAt(t)
        var yaw = 0.35
        var pitch = 0.0
        val g = glance

        if (look > 0 && g != null) {
            val eye = rig.root.worldToLocal(g.at.clone())
            val from = rig.height + 0.10 * s
            val toward = atan2(eye.x, eye.z)

            // nobody looks at a camera behind their back: the head would have to turn right round
            look *= inSight(toward)
            yaw = 0.35 + (toward - 0.18 - 0.35) * look     // the chest is turned 0.18 already
            pitch = atan2(eye.y - from, hypot(eye.x, eye.z)) * look
        }

        rig.turn("neck", X_AXIS, bang * 0.4 * nod * parts.head * (1 - 0.7 * look))
        rig.turn("head", X_AXIS, (0.12 - 0.18 * up + bang * nod * parts.head) * (1 - 0.8 * look) - pitch)

        if (look < 0.5) {
            mover.apply(rig, t)
        }

        rig.turn("head", Y_AXIS, yaw)

        for (side in Side.entries) {
            val sign = side.sign
            val foot = rig.ankle.getValue(side).clone()

            foot.x += 0.07 * sign * s
            rig.foot(side, rig.root.localToWorld(foot), rig.dir(0.3 * sign, 0.0, 1.0))
        }

        // the instrument hangs from the trunk: before the right hip, its neck up and to the player's left
        val waist = rig.bone("chest").getWorldPosition(vec())
        val axisX = rig.dir(cos(k.tilt), sin(k.tilt), 0.16).normalize()
        val face = rig.dir(0.0, 0.22, 1.0)
        val axisZ = face.addScaledVector(axisX, -face.dot(axisX)).normalize()
        val axisY = vec().crossVectors(axisZ, axisX)
        val at = waist.clone()
            .addScaledVector(rig.dir(1.0, 0.0, 0.0), -0.13 * s)
            .addScaledVector(rig.dir(0.0, 1.0, 0.0), k.hang * s)
            .addScaledVector(rig.dir(0.0, 0.0, 1.0), 0.17 * s)

        instrument.position.copy(at)
        instrument.quaternion.setFromRotationMatrix(mat4().makeBasis(axisX, axisY, axisZ))

        fun place(x: Double, y: Double, z: Double): Vector3 =
            at.clone().addScaledVector(axisX, x).addScaledVector(axisY, y).addScaledVector(axisZ, z)

        // a hand points toward what it plays, but a wrist only bends so far: never more than [most] radians off the
        // line of its forearm (bent further it looked broken, the maintainer, 2026-11-16)
        fun aim(side: Side, toward: Vector3, most: Double) {
            val hand = rig.bone("hand$side").getWorldPosition(vec())
            val forearm = hand.sub(rig.bone("wrist$side").getWorldPosition(vec())).normalize()
            val want = toward.clone().normalize()
            val angle = forearm.angleTo(want)

            if (angle > most) {
                val axis = vec().crossVectors(forearm, want).normalize()

                want.copy(forearm).applyAxisAngle(axis, most)
            }

            rig.point("hand$side", want)
        }

        // the left hand: under the neck at the note's place, the fingers coming up over the strings
        val fret = fretAt(t)
        // for an upper (lower-sounding) string the fingers reach further round the neck: the wrist comes up and out
        // the left hand goes across the neck by the strings' true distance only, and the wrist hardly at all: on a neck
        // a string is a finger's reach away, not a move of the hand (the right hand's way stays drawn larger)
        val cross = fret.across / 2.5
        val onNeck = place(nut - fret.along, cross, 0.02 * s)
        // the hand stays in its position on the neck while the fingers go from fret to fret: the wrist is at the
        // hand's place, as far from the fingers as a hand allows
        val wristL = place(nut - fret.along - max(-REACH * s, min(REACH * s, fret.hold - fret.along)), cross, 0.02 * s)
            .addScaledVector(axisY, -0.07 * s + 0.10 * cross)
            .addScaledVector(axisZ, -0.03 * s + 0.15 * cross)

        rig.hand(Side.L, wristL, rig.dir(0.4, -1.0, -0.3))
        aim(Side.L, onNeck.clone().sub(wristL), 0.75)

        // the right hand: over the strings between the bridge and the neck, through them on every note
        val strum = strumAt(t)
        val onStrings = place(bridge + 0.13 * s, fret.pick - strum * k.swing * fret.wide * s, 0.03 * s)
        // a guitarist strums from the wrist: the arm goes only a little of the way, the hand turns for the rest
        val wristR = place(bridge + 0.13 * s, fret.pick - strum * k.swing * fret.wide * k.arm * s, 0.03 * s)
            .addScaledVector(axisX, -0.07 * s)
            .addScaledVector(axisY, 0.05 * s)
            .addScaledVector(axisZ, 0.05 * s)

        rig.hand(Side.R, wristR, rig.dir(-0.9, -0.1, -0.7))
        aim(Side.R, onStrings.clone().sub(wristR), 0.80)

        rig.eyes?.let { eyes ->
            eyes.emissiveIntensity = 2 + 13 * min(1.6, energy)
        }
    }
}

/**
 * An outline through [points] (scaled by [s]) cut from a slab [depth] deep, its edges rounded: a smooth curve from
 * the first point through the others, closed.
 */
private fun extruded(
    points: List<Pair<Double, Double>>,
    s: Double,
    depth: Double,
    bevelSize: Double,
    bevelThickness: Double,
    bevelSegments: Int,
    curveSegments: Int,
): BufferGeometry {
    val addon = Three.addon
    val shape = addon.createShape()
    val (x0, y0) = points[0]

    shape.moveTo(x0 * s, y0 * s)
    shape.splineThru(points.drop(1).map { (x, y) -> addon.createVector2(x * s, y * s) }.toTypedArray())
    shape.closePath()

    val options = jsObject<ExtrudeGeometryOptions> {
        this.depth = depth
        bevelEnabled = true
        this.bevelSize = bevelSize
        this.bevelThickness = bevelThickness
        this.bevelSegments = bevelSegments
        this.curveSegments = curveSegments
    }

    return addon.createExtrudeGeometry(shape = shape, options = options)
}
