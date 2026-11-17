/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.marimba

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.band.figure.Side
import io.peekandpoke.klang.viz.liveact.band.instruments.Equipment
import io.peekandpoke.klang.viz.liveact.band.instruments.Instrument
import io.peekandpoke.klang.viz.liveact.lastIndexAt
import io.peekandpoke.klang.viz.liveact.mat4
import io.peekandpoke.klang.viz.liveact.quat
import io.peekandpoke.klang.viz.liveact.song.Note
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.createInstancedMesh
import io.peekandpoke.klang.workshop.threejs.emissive
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.isMesh
import io.peekandpoke.klang.workshop.threejs.quaternion
import io.peekandpoke.kraft.addons.threejs.js.Group
import io.peekandpoke.kraft.addons.threejs.js.MaterialParameters
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import io.peekandpoke.kraft.utils.jsObject
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// The marimba itself: its bars from the lowest note played to the highest, the tubes below them, the frame, and
// the four mallets. It floats above the stage where its player stands. All places are in the figure's own measures.

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
class MarimbaBar(val bar: Mesh, val material: MeshStandardMaterial, val restY: Double, val at: Vector3) {
    /** When it was last hit, within the last moments; -9 where it was not. */
    var hitT: Double = -9.0

    /** How hard it was last hit. It is kept from frame to frame, as the prototype keeps it. */
    var v: Double = 0.0
}

/**
 * The marimba, built where the figure [rig] stands to play it: its bars from the lowest of [notes] to the highest
 * (sorted by time, as far as the song is known; widened for a song that [grows]), the tubes below them, the frame,
 * and the four mallets. A bar lights up and dips when it is hit (see [answer]); the whole of it hovers.
 */
class Marimba(rig: Rig, notes: List<Note>, grows: Boolean) : Instrument {
    private val s: Double = rig.s

    /** The marimba. */
    val group: Group = Three.addon.createGroup()

    override val equipment: Equipment = EQUIPMENT

    /** The stage's floor where she stands: the marimba hovers above it. */
    private val floor: Double

    /** How high the bars are. */
    val height: Double = 0.86 * s

    /** How far in front of her the near row starts. */
    val near: Double = 0.42 * s

    private val bars = mutableMapOf<Int, MarimbaBar>()

    /** The lowest and the highest bar, and how wide a bar is. */
    private val lowest: Int
    private val highest: Int
    val width: Double

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
        if (grows) {
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
    fun barOf(midi: Int): MarimbaBar {
        var m = midi

        while (m < lowest) {
            m += 12
        }

        while (m > highest) {
            m -= 12
        }

        return bars[m] ?: bars.getValue(lowest)
    }

    /** How far it hovers above the stage at [t] (seconds): a slow breath up and down; the mallets follow it. */
    fun hoverAt(t: Double): Double = 0.012 * s * sin(t * 1.3)

    /** The marimba at [t] (seconds), answering [notes] (sorted by time): the bars just hit glow and fade; it hovers. */
    fun answer(t: Double, notes: List<Note>) {
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

            i--
        }

        for (b in bars.values) {
            val age = t - b.hitT

            b.material.emissiveIntensity = if (b.hitT < 0) 0.0 else 1.0 * max(0.6, b.v) * exp(-age / 0.22)
            b.bar.position.y = b.restY - (if (b.hitT < 0) 0.0 else 0.004 * s * b.v * exp(-age / 0.05))
        }

        // it hovers, a slow breath up and down; the mallets follow it
        group.position.y = floor + hoverAt(t)
    }

    companion object {
        /** What the marimba player holds: four mallets, two in each hand. */
        val EQUIPMENT: Equipment = Equipment.Sticks(perHand = 2)

        /** The highest bar of a marimba whose song still grows: the c three octaves above the middle one. */
        private const val GROWING_TOP = 96

        /** Whether the midi note [m] is a natural; none below midi 0, as in the prototype. */
        private fun isNatural(m: Int): Boolean = NATURAL.getOrElse(m % 12) { false }
    }
}
