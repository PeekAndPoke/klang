/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.band.instruments.Equipment
import io.peekandpoke.klang.viz.liveact.band.instruments.Instrument
import io.peekandpoke.klang.workshop.threejs.ExtrudeGeometryOptions
import io.peekandpoke.klang.workshop.threejs.copied
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.createExtrudeGeometry
import io.peekandpoke.klang.workshop.threejs.createShape
import io.peekandpoke.klang.workshop.threejs.emissive
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.kraft.addons.threejs.js.BufferGeometry
import io.peekandpoke.kraft.addons.threejs.js.Group
import io.peekandpoke.kraft.addons.threejs.js.MaterialParameters
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.utils.jsObject
import kotlin.math.PI

// A strung instrument, built from what it says of itself (see StringDesign): its body, neck and head, the pickups,
// the bridge, two knobs, the tuners, and the strings, which light up when played. All places are in the figure's own
// measures.

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
 * A strung instrument of the [design], built for the figure [rig] and standing in its scene, with the body [outline]
 * (the design's own unless the player has another). Its strings light up as they are played ([light]).
 */
class StrungInstrument(rig: Rig, design: StringDesign, outline: BodyOutline) : Instrument {
    private val s = rig.s

    /** Where the strings end at the bridge, along the neck from the instrument's middle, and where at the nut. */
    val bridge = -0.10 * s
    val nut = bridge + design.scale * s

    /** The instrument, in its own space: x along the neck toward the head, y across the strings (up), z out of its face. */
    val group: Group = Three.addon.createGroup()

    /** A string each, the lowest first, lit on its own when it is the one played. */
    private val strings: List<MeshStandardMaterial>

    override val equipment: Equipment = design.equipment

    init {
        val addon = Three.addon
        val k = design
        val g = group

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
    }

    /** Lights each string as [lit] says (0 dark to 1, the lowest string first). */
    fun light(lit: DoubleArray) {
        strings.forEachIndexed { index, material -> material.emissiveIntensity = 3.4 * lit[index] }
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
