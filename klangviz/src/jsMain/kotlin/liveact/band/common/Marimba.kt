/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.common

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.X_AXIS
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.Z_AXIS
import io.peekandpoke.klang.viz.liveact.around
import io.peekandpoke.klang.viz.liveact.lastIndexAt
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.song.Note
import io.peekandpoke.klang.viz.liveact.song.Part
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.isMesh
import io.peekandpoke.kraft.addons.threejs.js.Color
import io.peekandpoke.kraft.addons.threejs.js.Group
import io.peekandpoke.kraft.addons.threejs.js.MaterialParameters
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import io.peekandpoke.kraft.utils.jsObject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

// The marimba player: stands behind a marimba and plays notes with two mallets. Which bar is hit follows the note,
// so the player moves along the instrument with the tune. All places are in the figure's own measures.

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

// TYPING: belongs in the workshop
private var MaterialParameters.emissive: Color
    get() = asDynamic().emissive.unsafeCast<Color>()
    set(value) {
        asDynamic().emissive = value
    }

// TYPING: belongs in the workshop
private var MaterialParameters.emissiveIntensity: Double
    get() = asDynamic().emissiveIntensity.unsafeCast<Double>()
    set(value) {
        asDynamic().emissiveIntensity = value
    }

// TYPING: belongs in the workshop
private fun Vector3.lerpVectors(from: Vector3, to: Vector3, share: Double): Vector3 =
    also { asDynamic().lerpVectors(from, to, share) }

/** One bar of the marimba: its mesh, its glowing material, its height at rest, and where a mallet lands on it. */
private class MarimbaBar(val bar: Mesh, val material: MeshStandardMaterial, val restY: Double, val at: Vector3) {
    /** When it was last hit, within the last moments; -9 where it was not. */
    var hitT: Double = -9.0

    /** How hard it was last hit. It is kept from frame to frame, as the prototype keeps it. */
    var v: Double = 0.0
}

/** One note as one hand plays it: when, how hard, where the mallet lands, and the note. */
private class MalletHit(override val t: Double, override val v: Double, override val at: Vector3, val midi: Int) : Stroke

/**
 * The marimba player of [show]: the figure [rig] behind a marimba, playing [notes] (sorted by time) with two
 * mallets. She moves with the music by her [traits], her chance drawn under [seed].
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
    private var byHand: Map<Side, List<Stroke>> = emptyMap()

    /** The lowest and the highest bar, and how wide a bar is. */
    private val lowest: Int
    private val highest: Int
    private val width: Double

    /** Where a mallet idles when its hand has nothing to play. */
    private val rests: Map<Side, Vector3> = mapOf(
        Side.L to vec(0.12 * s, height + 0.012 * s, near + 0.08 * s),
        Side.R to vec(-0.12 * s, height + 0.012 * s, near + 0.08 * s),
    )

    /** The mallets' length. */
    val malletLength: Double = 0.40 * s

    /** The mallets, one in each hand: they stand on the stage beside the figure, not in its skeleton. */
    val mallets: Map<Side, Group>

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
            val tube = Three.addon.createMesh(
                Three.addon.createCylinderGeometry(
                    radiusTop = width * 0.36,
                    radiusBottom = width * 0.36,
                    height = (0.55 - 0.38 * share) * s,
                    radialSegments = 10,
                ),
                materials.steel,
            )

            tube.position.set(x, y - 0.03 * s - (0.55 - 0.38 * share) * s / 2, z)
            group.add(tube)

            // a mallet lands near the bar's end that is toward the player
            bars[m] = MarimbaBar(
                bar = bar,
                material = material,
                restY = y,
                at = vec(x, y + 0.012 * s, z - length * (if (natural) 0.15 else 0.30)),
            )
        }

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

        mallets = listOf(Side.L, Side.R).associateWith {
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

            mallet
        }
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

        // which hand plays which note: in turn, but never crossing over the other hand
        val hands = mapOf(Side.L to mutableListOf<Stroke>(), Side.R to mutableListOf<Stroke>())
        var last = Side.R
        val where = mutableMapOf<Side, MalletHit>()

        for (n in notes) {
            val at = barOf(n.midi).at
            var hand = last.other
            val other = hand.other
            val there = where[other]
            val recent = if (there != null && n.t - there.t < 0.6) there.at.x else null

            // the left hand is on the side of the low notes (x > 0): it never plays to the right of the right hand
            if (recent != null && ((hand == Side.L && at.x < recent - width) || (hand == Side.R && at.x > recent + width))) {
                hand = other
            }

            val hit = MalletHit(t = n.t, v = n.v, at = at, midi = n.midi)

            hands.getValue(hand).add(hit)
            where[hand] = hit
            last = hand
        }

        byHand = hands
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
        val left = hands.getValue(Side.L)
        val right = hands.getValue(Side.R)

        // the body and the arms are two things: the arms reach for the notes, and the body stays where it is as long
        // as they can. Only where the playing moves out of easy reach does the body follow, by what is beyond it, and
        // slowly: it goes by where the hands play over a few seconds, not by each note.
        val middle = (left.aim.x + right.aim.x) / 2
        var region = 0.0

        for (dt in listOf(-1.0, -0.5, 0.0, 0.5, 1.0)) {
            for (side in listOf(Side.L, Side.R)) {
                val rest = rests.getValue(side)

                region += strokeAt(list = byHand.getValue(side), t = t + dt, rest = rest, home = rest, height = 0.0).aim.x / 10
            }
        }

        region *= up

        val easy = 0.20 * s
        val beyond = sign(region) * max(0.0, abs(region) - easy)
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

            holdStick(
                rig = rig,
                side = side,
                tip = hand.tip,
                bend = hand.bend,
                length = malletLength,
                grip = 0.80,
                pole = rig.dir(0.6 * side.sign, -1.0, -0.5),
                mesh = mallets.getValue(side),
            )
        }

        val eyes = rig.eyes

        if (eyes != null) {
            eyes.emissiveIntensity = 2 + 12 * min(1.6, energy)
        }
    }

    private companion object {
        /** The highest bar of a marimba whose song still grows: the c three octaves above the middle one. */
        const val GROWING_TOP = 96

        /** Whether the midi note [m] is a natural; none below midi 0, as in the prototype. */
        fun isNatural(m: Int): Boolean = NATURAL.getOrElse(m % 12) { false }
    }
}
