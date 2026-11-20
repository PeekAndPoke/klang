/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.song.Note
import io.peekandpoke.klang.workshop.threejs.createCircleGeometry
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.createLatheGeometry
import io.peekandpoke.klang.workshop.threejs.emissive
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.isMesh
import io.peekandpoke.kraft.addons.threejs.js.Group
import io.peekandpoke.kraft.addons.threejs.js.MaterialParameters
import io.peekandpoke.kraft.addons.threejs.js.MaterialSide
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Vector2
import io.peekandpoke.kraft.utils.jsObject
import kotlin.math.PI
import kotlin.math.pow

// The trumpet itself: the tubes, the bell, the loop and the three valves; the bell lights up at each note and fades.
// All places are in the figure's own measures.

/** Which of the three valves are down, by the note: not a real fingering, but it changes as one does. */
private val FINGERS: List<List<Int>> = listOf(
    listOf(0, 0, 0), listOf(0, 1, 0), listOf(1, 0, 0), listOf(1, 1, 0), listOf(0, 1, 1), listOf(1, 0, 1), listOf(1, 1, 1),
)

/**
 * The trumpet, built for the figure [rig] and standing in its scene: in its own space x runs from the mouthpiece to
 * the bell, y up. Its valves go down with a note's fingering ([press]), its bell glows ([glow]).
 */
class Trumpet(private val rig: Rig) {
    private val s: Double = rig.s

    /** The trumpet's brass; the lacquer look (`?skin=lacquer`) polishes it while the band wears it. */
    internal val brass: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(
        jsObject<MaterialParameters> {
            color = 0x3a2c12
            roughness = 0.28
            metalness = 0.95
            side = MaterialSide.DoubleSide
        }
    )

    private val valves = mutableListOf<Mesh>()

    // the bell's light has its own colour, a turquoise (kept under full strength: brighter, it washes out to white)
    private val mouth: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(
        jsObject<MaterialParameters> {
            color = 0x000000
            emissive = Three.addon.createColor(0x30ffd0)
            emissiveIntensity = 0.0
            side = MaterialSide.DoubleSide
        }
    )

    /** The trumpet, in its own space: x from the mouthpiece to the bell, y up. It stands in the figure's scene. */
    val group: Group = buildTrumpet()

    private fun buildTrumpet(): Group {
        val g = Three.addon.createGroup()

        fun tube(from: Double, to: Double, r: Double): Mesh {
            val m = Three.addon.createMesh(Three.addon.createCylinderGeometry(r * s, r * s, (to - from) * s, 10), brass)

            m.rotation.z = -PI / 2
            m.position.x = (from + to) / 2 * s

            return m
        }

        g.add(tube(from = 0.0, to = 0.05, r = 0.010))
        g.add(tube(from = 0.03, to = 0.38, r = 0.0065))

        val profile = mutableListOf<Vector2>()

        for (i in 0..10) {
            val u = i / 10.0

            profile.add(Three.addon.createVector2((0.007 + 0.058 * u.pow(2.6)) * s, u * 0.14 * s))
        }

        val lathe = Three.addon.createLatheGeometry(points = profile.toTypedArray(), segments = 24)
        val bell = Three.addon.createMesh(lathe, brass)

        bell.rotation.z = -PI / 2
        bell.position.x = 0.36 * s
        g.add(bell)

        // the loop under the tube, and the three valves
        val loop = Three.addon.createMesh(Three.addon.createTorusGeometry(0.035 * s, 0.006 * s, 8, 20), brass)

        loop.scale.set(2.6, 1.0, 1.0)
        loop.position.set(0.20 * s, -0.04 * s, 0.0)
        g.add(loop)

        for (i in 0 until 3) {
            val casing = Three.addon.createMesh(Three.addon.createCylinderGeometry(0.011 * s, 0.011 * s, 0.085 * s, 10), brass)

            casing.position.set((0.165 + i * 0.027) * s, -0.02 * s, 0.0)

            val cap = Three.addon.createMesh(Three.addon.createCylinderGeometry(0.008 * s, 0.008 * s, 0.03 * s, 8), brass)

            cap.position.set((0.165 + i * 0.027) * s, 0.035 * s, 0.0)
            g.add(casing)
            g.add(cap)
            valves.add(cap)
        }

        val disc = Three.addon.createMesh(Three.addon.createCircleGeometry(0.034 * s, 24), mouth)

        disc.rotation.y = PI / 2
        disc.position.x = 0.465 * s
        g.add(disc)

        g.traverse { o ->
            if (o.isMesh) {
                o.castShadow = true
            }
        }

        val scene = rig.root.parent ?: error("The figure must stand in a scene before it is given its trumpet")

        scene.add(g)

        return g
    }

    /** The valves down as the fingering of [note] has them while it is [sounding] (0 to 1); all up without a note. */
    fun press(note: Note?, sounding: Double) {
        val fingers = if (note != null && sounding > 0.05) FINGERS[((note.midi % 7) + 7) % 7] else listOf(0, 0, 0)

        valves.forEachIndexed { i, cap -> cap.position.y = (0.035 - 0.014 * fingers[i]) * s }
    }

    /** The bell's light, [bell] 0 (dark) to 1 (a note just begun, blown full). */
    fun glow(bell: Double) {
        mouth.emissiveIntensity = 1.6 * bell
    }
}
