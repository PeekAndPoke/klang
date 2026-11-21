/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.workshop.threejs.clone
import io.peekandpoke.klang.workshop.threejs.copied
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.emissive
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.setTo
import io.peekandpoke.klang.workshop.threejs.translate
import io.peekandpoke.kraft.addons.threejs.js.BufferGeometry
import io.peekandpoke.kraft.addons.threejs.js.Group
import io.peekandpoke.kraft.addons.threejs.js.MaterialParameters
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.utils.jsObject
import kotlin.math.PI

// A strung instrument, built from what it says of itself (see StringDesign): its body, neck and head, the pickups,
// the bridge, two knobs, the tuners, its gold (see Ornaments), and the strings, which light up when played. All
// places are in the figure's own measures.

/**
 * The instruments' surfaces, shared by every string player: made together when the first instrument is built
 * (three.js must be loaded by then). The lacquer polishes [lacquer], [metal] and [gold] while it is worn (see
 * `Lacquer`); a body with gold of its own follows [lacquer] (see [StrungInstrument]).
 */
internal object StringMaterials {
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

    /** The neck's wood with gold dots and fine frets on the fretboard (see `markNeck`). */
    val markedNeck: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(
        jsObject<MaterialParameters> {
            color = 0x1c140e
            roughness = 0.5
        },
    ).also { markNeck(it) }

    val metal: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(
        jsObject<MaterialParameters> {
            color = 0x1a1a20
            roughness = 0.25
            metalness = 0.95
        },
    )

    /** The gold hardware: the same old, dull gold as the lines (see [ORNAMENT_GOLD_RGB]). */
    val gold: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(
        jsObject<MaterialParameters> {
            roughness = ORNAMENT_GOLD_ROUGH
            metalness = ORNAMENT_GOLD_METAL
        },
    ).also { it.color.setRGB(ORNAMENT_GOLD_RGB.first, ORNAMENT_GOLD_RGB.second, ORNAMENT_GOLD_RGB.third) }
}

/**
 * A strung instrument of the [design], built for the figure [rig] and standing in its scene, with the body [outline]
 * (the design's own unless the player has another; the design's gold lies where its own outline is). Its strings
 * light up as they are played ([light]).
 *
 * Its meshes: the body, the neck and the head (only these three throw a shadow), the hardware (the pickups, the
 * bridge, the knobs and the tuners, one mesh of each metal), and the strings, one each (each lights up on its own).
 */
class StrungInstrument(rig: Rig, design: StringDesign, outline: BodyOutline) {
    private val s = rig.s

    /** Where the strings end at the bridge, along the neck from the instrument's middle, and where at the nut. */
    val bridge = -0.10 * s
    val nut = bridge + design.scale * s

    /** The instrument, in its own space: x along the neck toward the head, y across the strings (up), z out of its face. */
    val group: Group = Three.addon.createGroup()

    /** A string each, the lowest first, lit on its own when it is the one played. */
    private val strings: List<MeshStandardMaterial>

    /**
     * The body's own material where it wears gold (the gold differs from instrument to instrument): it takes the
     * shared lacquer's finish each frame ([follow]), so it is polished with the others while the lacquer is worn.
     */
    private val ownBody: MeshStandardMaterial?

    init {
        val addon = Three.addon
        val k = design
        val g = group
        val gold = k.ornaments

        // the body: its outline cut from a slab, the edges rounded, in the design's own measure (its gold is drawn
        // in that measure); pickups, a bridge and two knobs on its face
        ownBody = if (gold.onBody) {
            StringMaterials.lacquer.clone().also {
                ornamentBody(material = it, ornaments = gold, top = BODY_DEPTH + BODY_ROUND, key = k.name)
            }
        } else {
            null
        }

        val body = addon.createMesh(
            roundedSlab(outline = outline, steps = BODY_STEPS, depth = BODY_DEPTH, round = BODY_ROUND, roundSteps = 2),
            ownBody ?: StringMaterials.lacquer,
        )

        body.scale.set(s, s, s)
        body.position.z = -0.040 * s

        val neckFrom = (k.bodyHalfLength - 0.10) * s
        val neckGeometry = addon.createBoxGeometry(nut - neckFrom, k.neck * s, 0.022 * s)

        if (gold.markers) {
            fretboardUv(geometry = neckGeometry, centre = (nut + neckFrom) / 2, nut = nut, scale = k.scale * s)
        }

        val neck = addon.createMesh(neckGeometry, if (gold.markers) StringMaterials.markedNeck else StringMaterials.neckWood)

        neck.position.set((nut + neckFrom) / 2, 0.0, 0.006 * s)

        val head = addon.createMesh(
            roundedSlab(outline = k.head, steps = HEAD_STEPS, depth = 0.014, round = 0.003, roundSteps = 1),
            StringMaterials.lacquer,
        )

        head.scale.set(s, s, s)
        head.position.set(nut, 0.0, -0.012 * s)
        head.rotation.y = 0.10
        g.add(body)
        g.add(neck)
        g.add(head)

        // the hardware, placed in the instrument's space: the pickups are steel, the rest gold where the design says
        val pickups = k.pickups.map { x ->
            addon.createBoxGeometry(0.022 * s, k.neck * 1.25 * s, 0.008 * s).translate(x * s, 0.0, 0.012 * s)
        }
        val saddle = addon.createBoxGeometry(0.018 * s, k.neck * 1.3 * s, 0.014 * s).translate(bridge, 0.0, 0.014 * s)
        val knobs = k.knobs.map { (x, y) ->
            val knob: BufferGeometry = addon.createCylinderGeometry(0.011 * s, 0.013 * s, 0.014 * s, 12)

            knob.asDynamic().rotateX(PI / 2)
            knob.translate(x * s, y * s, 0.014 * s)
        }
        val tuners = (0 until k.strings).map { i ->
            val along = (i + 0.7) / (k.strings + 0.6)

            addon.createCylinderGeometry(0.006 * s, 0.006 * s, 0.03 * s, 8)
                .translate(nut + (0.03 + 0.14 * along) * s, -0.036 * s, -0.004 * s)
        }
        val fittings = listOf(saddle) + knobs + tuners

        if (gold.goldHardware) {
            g.add(addon.createMesh(merged(pickups), StringMaterials.metal))
            g.add(addon.createMesh(merged(fittings), StringMaterials.gold))
        } else {
            g.add(addon.createMesh(merged(pickups + fittings), StringMaterials.metal))
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

        // only the body, the neck and the head throw a shadow: the hardware's shadow falls inside theirs, a tuner's
        // all but a centimetre of it, and a string's is thinner than a point of the shadow's map
        for (part in listOf(body, neck, head)) {
            part.castShadow = true
        }

        (rig.root.parent ?: error("The figure stands in no scene")).add(g)
    }

    /**
     * Lights each string as [lit] says (0 dark to 1, the lowest string first); a body with gold of its own takes the
     * lacquer's finish ([follow]).
     */
    fun light(lit: DoubleArray) {
        strings.forEachIndexed { index, material -> material.emissiveIntensity = 3.4 * lit[index] }
        ownBody?.let { follow(it) }
    }

    /**
     * The body's own material [own] takes the shared lacquer's finish: its colour, roughness and metalness, and the
     * room it mirrors while the lacquer is worn (see `Lacquer`, which polishes the shared one). Its program is built
     * anew only where a room comes or goes.
     */
    private fun follow(own: MeshStandardMaterial) {
        val from = StringMaterials.lacquer
        val room = from.asDynamic().envMap
        val had = own.asDynamic().envMap

        own.color.setTo(from.color)
        own.roughness = from.roughness
        own.metalness = from.metalness
        own.asDynamic().envMapIntensity = from.asDynamic().envMapIntensity

        if (had !== room) {
            own.asDynamic().envMap = room

            if ((had == null) != (room == null)) {
                own.needsUpdate = true
            }
        }
    }

    private companion object {
        /** The body's slab: how deep, how far its edge is rounded (out and in depth), in the design's measure. */
        const val BODY_DEPTH = 0.036
        const val BODY_ROUND = 0.010

        /** How many points the edge has on each stretch between two of the outline's points: the body's and the head's. */
        const val BODY_STEPS = 3
        const val HEAD_STEPS = 3
    }
}
