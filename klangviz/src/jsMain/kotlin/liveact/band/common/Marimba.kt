/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.common

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.X_AXIS
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.Z_AXIS
import io.peekandpoke.klang.viz.liveact.around
import io.peekandpoke.klang.viz.liveact.band.malletShare
import io.peekandpoke.klang.viz.liveact.beyond
import io.peekandpoke.klang.viz.liveact.lastIndexAt
import io.peekandpoke.klang.viz.liveact.mat4
import io.peekandpoke.klang.viz.liveact.quat
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.song.Note
import io.peekandpoke.klang.viz.liveact.song.Part
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.createInstancedMesh
import io.peekandpoke.klang.workshop.threejs.emissive
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.isMesh
import io.peekandpoke.klang.workshop.threejs.lengthSq
import io.peekandpoke.klang.workshop.threejs.lerp
import io.peekandpoke.klang.workshop.threejs.lerpVectors
import io.peekandpoke.klang.workshop.threejs.quaternion
import io.peekandpoke.kraft.addons.threejs.js.Group
import io.peekandpoke.kraft.addons.threejs.js.MaterialParameters
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import io.peekandpoke.kraft.utils.jsObject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// The marimba player: stands behind a marimba and plays notes with four mallets, two in each hand. Which bar is hit
// follows the note, so the player moves along the instrument with the tune. All places are in the figure's own
// measures.

/**
 * The marimba's materials, made together and in this order the first time a marimba is built (three.js numbers its
 * materials as they are made, and draws in that order where nothing else decides).
 */
private object MarimbaMaterials {
    val wood: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(jsObject<MaterialParameters> {
        color = 0x241710
        roughness = 0.5
    })

    val steel: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(jsObject<MaterialParameters> {
        color = 0x15151a
        roughness = 0.3
        metalness = 0.9
    })

    val felt: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(jsObject<MaterialParameters> {
        color = 0x30262a
        roughness = 0.9
    })
}

/** Which of the twelve notes of an octave are naturals (the bars of the near row), from C. */
private val NATURAL = listOf(true, false, true, false, true, true, false, true, false, true, false, true)

/** One bar of the marimba: its mesh, its glowing material, its height at rest, and where a mallet lands on it. */
private class MarimbaBar(val bar: Mesh, val material: MeshStandardMaterial, val restY: Double, val at: Vector3) {
    /** When it was last hit, within the last moments; -9 where it was not. */
    var hitT: Double = -9.0

    /** How hard it was last hit. It is kept from frame to frame, as the prototype keeps it. */
    var v: Double = 0.0
}

/**
 * One blow of one hand, with its two mallets: when, how hard, and where. [at] is the place between the two heads
 * and [half] the way from there to the outer mallet's head (the inner one's is as far the other way). Where the
 * hand plays one note, its inner mallet lands on the bar and the outer one rides beside it, above the bars.
 */
private class MalletHit(
    override val t: Double,
    override val v: Double,
    override val at: Vector3,
    val half: Vector3,
    val side: Side,
) : Stroke

/**
 * The marimba player of [show]: the figure [rig] behind a marimba, playing [notes] (sorted by time) with four
 * mallets, two in each hand: a single note with a hand's inner mallet, notes that sound together with as many
 * mallets as they need (see [malletShare]). She moves with the music by her [traits], her chance drawn under [seed].
 */
class MarimbaPlayer(
    private val show: Show,
    private val rig: Rig,
    private val part: Part<Note>,
    traits: Traits,
    seed: Double = 2.0,
) {
    private val mover = Mover(show = show, seed = seed, traits = traits)
    private val s: Double = rig.s

    /** The marimba. */
    val group: Group = Three.addon.createGroup()

    /**
     * Her hair as only her shadow has it: Medusa's snakes (see [ShadowHair]). None with `?nosnakes`; drawn as well,
     * to look at them, with `?snakes`.
     */
    private val snakes: ShadowHair? = when {
        show.switches.has(Switch.NoSnakes) -> null
        else -> ShadowHair(rig = rig, shown = show.switches.has(Switch.Snakes))
    }

    /** The stage's floor where she stands: the marimba hovers above it. */
    private val floor: Double

    /** How high the bars are. */
    private val height: Double = 0.86 * s

    /** How far in front of her the near row starts. */
    private val near: Double = 0.42 * s

    private val bars = mutableMapOf<Int, MarimbaBar>()

    /** Her notes, sorted by time, as far as the song is known. */
    private var notes: List<Note> = emptyList()

    /** Which hand plays which note. */
    private var byHand: Map<Side, List<MalletHit>> = emptyMap()

    /** The notes known as they are played, in their order. */
    private var played: List<MalletHit> = emptyList()

    /**
     * Where the hands were before the first note known, where the song has forgotten its past: the hand that played
     * last, and each hand's last hit. The hands go on from there, so no note that is still known changes hands, and
     * each hand's list begins with its last hit forgotten: the mallet stays where that hit left it.
     */
    private var lastBefore: Side = Side.R
    private val hitsBefore = mutableMapOf<Side, MalletHit>()

    /** The lowest and the highest bar, and how wide a bar is. */
    private val lowest: Int
    private val highest: Int
    private val width: Double

    /** Where a hand's mallets idle when it has nothing to play: the place between their two heads. */
    private val rests: Map<Side, Vector3> = mapOf(
        Side.L to vec(0.14 * s, height + 0.012 * s, near + 0.08 * s),
        Side.R to vec(-0.14 * s, height + 0.012 * s, near + 0.08 * s),
    )

    /** From there to the outer mallet's head, as the hand holds the two when both idle. */
    private val restHalves: Map<Side, Vector3> = Side.entries.associateWith { side -> vec(side.sign * APART / 2 * s, 0.0, 0.0) }

    /** The mallets' length. */
    val malletLength: Double = 0.40 * s

    /**
     * The mallets, two in each hand, the outer one first: they stand on the stage beside the figure, not in its
     * skeleton.
     */
    val mallets: Map<Side, List<Group>>

    init {
        val stage = rig.root.parent ?: error("The marimba player's figure stands on no stage")
        // the marimba's materials are made before the bars' own, as in the prototype
        val materials = MarimbaMaterials

        // the marimba stands on the stage where she plays, whether she is there yet or not
        stage.add(group)
        group.position.copy(rig.root.position)
        group.rotation.y = rig.root.rotation.y
        floor = rig.root.position.y

        part.refresh()
        notes = part.events

        // the bars: from the lowest note played to the highest, widened to whole naturals; low notes to the player's
        // left
        var lo = min(notes.minOfOrNull { it.midi } ?: 60, 60)
        var hi = max(notes.maxOfOrNull { it.midi } ?: 72, 72)

        if (notes.isEmpty()) {
            lo = 60
            hi = 84
        }

        // a song that grows has not said its highest note yet: three octaves from the middle c, as a marimba has
        // them. A note beyond the bars is played octaves nearer (see barOf)
        if (part.grows) {
            hi = max(hi, GROWING_TOP)
        }

        while (!NATURAL[((lo % 12) + 12) % 12]) {
            lo--
        }

        while (!NATURAL[((hi % 12) + 12) % 12]) {
            hi++
        }

        val naturals = (lo..hi).filter { isNatural(it) }
        val width = min(0.058 * s, 1.25 * s / naturals.size)

        lowest = lo
        highest = hi
        this.width = width
        val span = width * (naturals.size - 1)
        // (kept under full strength: brighter, the colour washes out to white)
        // a hit bar glows purple, the two rows in slightly different tones: the near row bluer, the far row redder
        val nearGlow = Three.addon.createColor(0x8a3cff)
        val farGlow = Three.addon.createColor(0xc23cf0)
        // the tubes below the bars: where each one's middle is, and how long it is
        val tubes = mutableListOf<Pair<Vector3, Double>>()

        for (m in lo..hi) {
            val natural = isNatural(m)
            val index = if (natural) naturals.indexOf(m).toDouble() else naturals.indexOf(m - 1) + 0.5
            val share = if (naturals.size > 1) index / (naturals.size - 1) else 0.0
            val x = span / 2 - index * width
            val length = (0.24 - 0.08 * share) * s
            val z = if (natural) near + length / 2 else near + 0.25 * s + length / 2
            val y = height + (if (natural) 0.0 else 0.025 * s)
            val material = Three.addon.createMeshStandardMaterial(jsObject<MaterialParameters> {
                color = 0x2a1a10
                roughness = 0.45
                emissive = if (natural) nearGlow else farGlow
                emissiveIntensity = 0.0
            })
            val bar = Three.addon.createMesh(
                Three.addon.createBoxGeometry(width = width * 0.86, height = 0.02 * s, depth = length),
                material,
            )

            bar.position.set(x, y, z)
            group.add(bar)

            // the tube below it: longer for a low note
            val tubeLength = (0.55 - 0.38 * share) * s

            tubes.add(vec(x, y - 0.03 * s - tubeLength / 2, z) to tubeLength)

            // a mallet lands near the bar's end that is toward the player
            bars[m] = MarimbaBar(
                bar = bar,
                material = material,
                restY = y,
                at = vec(x, y + 0.012 * s, z - length * (if (natural) 0.15 else 0.30)),
            )
        }

        // the tubes: one cylinder, one long, drawn once for each tube and stretched to its length (one draw for all)
        val tubeMesh = Three.addon.createInstancedMesh(
            geometry = Three.addon.createCylinderGeometry(
                radiusTop = width * 0.36,
                radiusBottom = width * 0.36,
                height = 1.0,
                radialSegments = 10,
            ),
            material = materials.steel,
            count = tubes.size,
        )
        val upright = quat()

        tubes.forEachIndexed { index, (middle, tubeLength) ->
            tubeMesh.setMatrixAt(index, mat4().compose(position = middle, quaternion = upright, scale = vec(1.0, tubeLength, 1.0)))
        }

        group.add(tubeMesh)

        // the frame
        val far = near + 0.50 * s

        for (z in listOf(near - 0.02 * s, near + 0.245 * s, far)) {
            val rail = Three.addon.createMesh(
                Three.addon.createBoxGeometry(width = span + width * 2, height = 0.03 * s, depth = 0.03 * s),
                materials.wood,
            )

            rail.position.set(0.0, height - 0.03 * s, z)
            group.add(rail)
        }

        // no legs: the marimba floats (the maintainer, 2026-11-15: "make the marimba fly"); short ends close the frame
        for (x in listOf(-span / 2 - width, span / 2 + width)) {
            val end = Three.addon.createMesh(
                Three.addon.createBoxGeometry(width = 0.03 * s, height = 0.05 * s, depth = far - near + 0.05 * s),
                materials.wood,
            )

            end.position.set(x, height - 0.035 * s, (near + far) / 2)
            group.add(end)
        }

        group.traverse { o ->
            if (o.isMesh) {
                o.castShadow = true
            }
        }

        plan()

        fun mallet(): Group {
            val mallet = Three.addon.createGroup()

            mallet.add(
                Three.addon.createMesh(
                    Three.addon.createCylinderGeometry(
                        radiusTop = 0.005 * s, radiusBottom = 0.006 * s, height = malletLength, radialSegments = 6,
                    ),
                    materials.wood,
                )
            )

            val head = Three.addon.createMesh(
                Three.addon.createSphereGeometry(radius = 0.022 * s, widthSegments = 12, heightSegments = 8),
                materials.felt,
            )

            head.position.y = malletLength / 2
            mallet.add(head)
            stage.add(mallet)

            return mallet
        }

        mallets = listOf(Side.L, Side.R).associateWith { listOf(mallet(), mallet()) }
    }

    /** The bar of the note [midi]: its own, or the same note as many octaves nearer as it takes where it lies beyond the bars. */
    private fun barOf(midi: Int): MarimbaBar {
        var m = midi

        while (m < lowest) {
            m += 12
        }

        while (m > highest) {
            m -= 12
        }

        return bars[m] ?: bars.getValue(lowest)
    }

    /** Which hand plays which note, from the notes as far as they are known. */
    private fun plan() {
        notes = part.events

        // what the song has forgotten since the last plan: the hands go on from the last of it
        val from = notes.firstOrNull()?.t ?: Double.POSITIVE_INFINITY
        val gone = played.filter { it.t < from }

        gone.lastOrNull()?.let { lastBefore = it.side }

        for (side in Side.entries) {
            gone.lastOrNull { it.side == side }?.let { hitsBefore[side] = it }
        }

        val hands = Side.entries.associateWith { side -> listOfNotNull(hitsBefore[side]).toMutableList() }
        val all = mutableListOf<MalletHit>()
        var last = lastBefore
        val where = hitsBefore.toMutableMap()

        /** The [hand] plays the [struck] bars (one or two) at [t], as hard as [v]. */
        fun blow(hand: Side, struck: List<MarimbaBar>, t: Double, v: Double) {
            val half = when (struck.size) {
                // one note: the inner mallet plays it, the outer one rides beside it, further out and above the bars
                1 -> vec(hand.sign * APART / 2 * s, RIDE / 2 * s, 0.0)
                // two: the outer mallet takes the bar further out (the left hand's lower one, the right hand's higher)
                else -> {
                    val (inner, outer) = struck.sortedBy { it.at.x * hand.sign }

                    vec().addScaledVector(outer.at, 0.5).addScaledVector(inner.at, -0.5)
                }
            }
            val inner = struck.minBy { it.at.x * hand.sign }
            val hit = MalletHit(t = t, v = v, at = inner.at.clone().add(half), half = half, side = hand)

            all.add(hit)
            hands.getValue(hand).add(hit)
            where[hand] = hit
            last = hand
        }

        var i = 0

        while (i < notes.size) {
            // what sounds together is one moment
            var end = i + 1

            while (end < notes.size && notes[end].t - notes[i].t < TOGETHER) {
                end++
            }

            val together = notes.subList(i, end)
            val t = together.first().t

            i = end

            // (notes that lie on one bar, as a note doubled an octave beyond the bars does, are one note)
            val struck = together.map { barOf(it.midi) }.distinct()
            val v = together.maxOf { it.v }

            if (struck.size == 1) {
                // one note: the hands play in turn, but never crossing over each other
                val at = struck.single().at
                var hand = last.other
                val other = hand.other
                val there = where[other]
                // where the other hand's inner mallet is, the one nearest this hand (its blow's middle less the way
                // to its outer one)
                val recent = if (there != null && t - there.t < 0.6) there.at.x - there.half.x else null
                val slack = width / 2

                // the left hand is on the side of the low notes (x > 0): it never plays to the right of the right
                // hand's mallets, nor between them
                if (recent != null && ((hand == Side.L && at.x < recent - slack) || (hand == Side.R && at.x > recent + slack))) {
                    hand = other
                }

                blow(hand = hand, struck = struck, t = t, v = v)

                continue
            }

            // several: shared between the hands, the low ones for the left (the low notes lie at x > 0)
            val share = malletShare(places = struck.map { -it.at.x }, reach = REACH * s)

            for ((hand, mine) in listOf(Side.L to share.low, Side.R to share.high)) {
                if (mine.isNotEmpty()) {
                    blow(hand = hand, struck = mine.map { struck[it] }, t = t, v = v)
                }
            }
        }

        byHand = hands
        played = all
    }

    /**
     * Half the way between the two mallet heads of [side]'s hand at [t]: as its blow before asks, on the way to what
     * the next one asks, as the hand itself travels (see [strokeAt]).
     */
    private fun halfAt(side: Side, t: Double): Vector3 {
        val (before, after) = byHand.getValue(side).around(t)
        val from = before?.half ?: restHalves.getValue(side)
        val to = after?.half ?: from
        val gap = if (before != null && after != null) after.t - before.t else 1.0
        val until = if (after != null) after.t - t else 9.0

        return from.clone().lerp(to, smooth((gap - until) / max(0.05, gap * 0.8)))
    }

    /** The marimba player and her marimba at [t] (seconds). */
    fun update(t: Double) {
        if (part.refresh()) {
            plan()
        }

        // the bars just hit glow and fade; how much is played feeds the eyes
        var energy = 0.0
        val (last, coming) = notes.around(t)

        for (b in bars.values) {
            b.hitT = -9.0
        }

        var i = notes.lastIndexAt(t)

        while (i >= 0 && notes[i].t > t - 1.2) {
            val n = notes[i]
            val b = barOf(n.midi)

            if (b.hitT < 0) {
                b.hitT = n.t
                b.v = n.v
            }

            energy += n.v * exp(-(t - n.t) / 0.3)
            i--
        }

        for (b in bars.values) {
            val age = t - b.hitT

            b.material.emissiveIntensity = if (b.hitT < 0) 0.0 else 1.0 * max(0.6, b.v) * exp(-age / 0.22)
            b.bar.position.y = b.restY - (if (b.hitT < 0) 0.0 else 0.004 * s * b.v * exp(-age / 0.05))
        }

        // it hovers, a slow breath up and down; the mallets follow it
        val hover = 0.012 * s * sin(t * 1.3)
        group.position.y = floor + hover

        // is she playing: up from well before a phrase to a little after it; between phrases she relaxes, the
        // arms hanging with the mallets down beside her, standing upright
        val until = if (coming != null) coming.t - t else 99.0
        val since = if (last != null) t - last.t else 99.0
        // into position a few cycles ahead: the arms start to rise three cycles before her first note and she stands
        // ready, mallets over the bars, a good cycle before it
        val cycle = 240 / show.bpm
        val up = max(smooth(1 - (until - 1.2 * cycle) / (1.8 * cycle)), 1 - smooth((since - 1.4) / 1.2))

        val hands = listOf(Side.L, Side.R).associateWith { side ->
            val shoulder = rig.shoulder.getValue(side)
            val pose = strokeAt(
                list = byHand.getValue(side),
                t = t,
                rest = rests.getValue(side),
                home = vec(shoulder.x, 0.0, 0.22 * s),
                height = 0.20 * s,
                windup = 0.26,
                hover = 0.25,
            )

            pose.tip.y += hover

            val down = vec(shoulder.x + 0.05 * side.sign * s, rig.pelvis.y - 0.34 * s, 0.14 * s)

            pose.tip.lerpVectors(down, pose.tip, up)
            pose.aim.x *= up

            StickPose(tip = pose.tip, bend = pose.bend * up, lift = pose.lift, aim = pose.aim)
        }
        // each hand's two mallets: wide as its blows ask while she plays, nearly closed while the arms hang
        val halves = listOf(Side.L, Side.R).associateWith { side ->
            val half = halfAt(side, t)

            half.set(half.x * (0.3 + 0.7 * up), half.y * up, half.z * up)
        }
        val left = hands.getValue(Side.L)
        val right = hands.getValue(Side.R)

        // the body and the arms are two things: the arms reach for the notes, and the body stays where it is as long
        // as they can. Only where the playing moves out of easy reach does the body follow, by what is beyond it, and
        // slowly: it goes by where the hands play over some five seconds, the nearer moments counting more, and not
        // by each note; and it starts and ends softly. (Over two seconds, and from a hard edge, the weight went from
        // leg to leg too hectically: the maintainer, 2026-11-17.)
        val middle = (left.aim.x + right.aim.x) / 2
        var region = 0.0
        var counted = 0.0

        for (step in -WEIGH_STEPS..WEIGH_STEPS) {
            val counts = 0.5 + 0.5 * cos(PI * step / (WEIGH_STEPS + 1))

            for (side in listOf(Side.L, Side.R)) {
                val rest = rests.getValue(side)

                region += counts * strokeAt(list = byHand.getValue(side), t = t + step * WEIGH_STEP, rest = rest, home = rest, height = 0.0).aim.x
                counted += counts
            }
        }

        region *= up / counted

        val beyond = beyond(x = region, easy = 0.20 * s, soft = 0.08 * s)
        val stance = (0.07 + 0.10 * up) * s
        val reach = stance + rig.ankle.getValue(Side.L).x
        val shift = max(-1.0, min(1.0, beyond / reach)) * reach
        // -1 on the right leg, 1 on the left
        val weight = shift / (stance + rig.ankle.getValue(Side.L).x)
        val nod = mover.nod(t * show.bpm / 60)
        val body = nod + mover.burst()

        rig.reset()
        rig.update()
        rig.moveHips(vec(shift, -(0.015 + 0.03 * up + 0.010 * body * mover.parts.knees - 0.02 * abs(weight)) * rig.pelvis.y, 0.0))
        rig.turn("hips", Z_AXIS, -0.05 * weight)
        rig.turn("belly", X_AXIS, 0.02 + 0.11 * up + 0.02 * body * mover.parts.trunk)
        rig.turn("belly", Z_AXIS, 0.04 * weight)
        rig.turn("chest", Y_AXIS, 0.22 * (middle - shift) / s)
        rig.turn("head", X_AXIS, 0.04 + 0.18 * up + 0.08 * nod * mover.parts.head)
        mover.apply(rig, t)
        rig.turn("head", Y_AXIS, 0.5 * (middle - shift) / s)

        for (side in listOf(Side.L, Side.R)) {
            val foot = rig.ankle.getValue(side).clone()

            foot.x += stance * side.sign
            // the leg without the weight rests on its toes a little
            foot.y += 0.025 * s * max(0.0, -weight * side.sign)
            rig.foot(side, rig.root.localToWorld(foot), rig.dir(0.25 * side.sign, 0.0, 1.0))
        }

        for (side in listOf(Side.L, Side.R)) {
            val hand = hands.getValue(side)
            val half = halves.getValue(side)
            val held = malletLength * GRIP
            // the hand holds both: it reaches for the place between the two heads, each of them about a mallet's hold
            // away. Each mallet is then set from its head, so the head is where the blow asks whatever the angle
            // between the two; only where the hand holds the shaft gives a little
            val grip = holdStick(
                rig = rig,
                side = side,
                tip = hand.tip,
                bend = hand.bend,
                length = malletLength,
                pole = rig.dir(0.6 * side.sign, -1.0, -0.5),
                mesh = null,
                reach = sqrt(max(0.25 * held * held, held * held - half.lengthSq())),
            )

            for ((mallet, way) in mallets.getValue(side).zip(listOf(1.0, -1.0))) {
                val head = rig.root.localToWorld(hand.tip.clone().addScaledVector(half, way))
                val along = head.clone().sub(grip).normalize()

                mallet.position.copy(head).addScaledVector(along, -malletLength / 2)
                mallet.quaternion.setFromUnitVectors(Y_AXIS, along)
            }
        }

        val eyes = rig.eyes

        if (eyes != null) {
            eyes.emissiveIntensity = 2 + 12 * min(1.6, energy)
        }
    }

    /**
     * What goes with her as she is posed at last: the snakes on her head. Call it once everything that turns her
     * head has done so for this frame (the looks between the players come after [update]).
     */
    fun afterPose(t: Double) {
        snakes?.update(t)
    }

    private companion object {
        /** The highest bar of a marimba whose song still grows: the c three octaves above the middle one. */
        const val GROWING_TOP = 96

        /** Notes that begin within this many seconds of each other sound together: one blow. */
        const val TOGETHER = 0.03

        /** How far apart a hand's two mallet heads are when it plays one note or none, in the figure's measure: two bars or so. */
        const val APART = 0.11

        /** How high above the bars the mallet rides that does not play. */
        const val RIDE = 0.045

        /** How wide a hand's two mallets open at most: an octave in the middle of the instrument. */
        const val REACH = 0.36

        /** Where along a mallet the hand holds it, from its head, as a share of its length. */
        const val GRIP = 0.80

        /** How the body weighs where the hands play: this many moments either side of now, this many seconds apart. */
        const val WEIGH_STEPS = 6
        const val WEIGH_STEP = 0.45

        /** Whether the midi note [m] is a natural; none below midi 0, as in the prototype. */
        fun isNatural(m: Int): Boolean = NATURAL.getOrElse(m % 12) { false }
    }
}
