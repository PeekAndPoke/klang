/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.marimba

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.band.Place
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.band.figure.Side
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.StringMaterials
import io.peekandpoke.klang.viz.liveact.lastIndexAt
import io.peekandpoke.klang.viz.liveact.mat4
import io.peekandpoke.klang.viz.liveact.quat
import io.peekandpoke.klang.viz.liveact.song.Note
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.ExtrudeGeometryOptions
import io.peekandpoke.klang.workshop.threejs.colorSpace
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.createExtrudeGeometry
import io.peekandpoke.klang.workshop.threejs.createInstancedMesh
import io.peekandpoke.klang.workshop.threejs.emissive
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.createShape
import io.peekandpoke.klang.workshop.threejs.isMesh
import io.peekandpoke.klang.workshop.threejs.map
import io.peekandpoke.klang.workshop.threejs.quaternion
import io.peekandpoke.kraft.addons.threejs.js.Group
import io.peekandpoke.kraft.addons.threejs.js.MaterialParameters
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Texture
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.document
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// The marimba itself: its bars from the lowest note played to the highest, the tubes below them, the frame, and
// the four mallets. It floats above the stage where its player stands. All places are in the figure's own measures.
//
// Its dress (KLANG-00478; the maintainer, 2026-11-22: "currently it looks too plain, too much like pure geometric
// figures and has no ornaments"): bars of rosewood with a line of gold let into each, tubes of brass, and on the
// frame the diamonds of its player's suit in the instruments' old gold: a band of them along the two long sides and
// one large one on each end, which runs down to a point like a keel.

/**
 * The marimba's materials, made together and in this order the first time a marimba is built (three.js numbers its
 * materials as they are made, and draws in that order where nothing else decides).
 */
internal object MarimbaMaterials {
    val wood: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(jsObject<MaterialParameters> {
        color = 0x241710
        roughness = 0.5
    })

    /** The tubes: a dark brass. */
    val brass: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(jsObject<MaterialParameters> {
        color = 0x4a3418
        roughness = 0.3
        metalness = 0.9
    })

    val felt: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(jsObject<MaterialParameters> {
        color = 0x30262a
        roughness = 0.9
    })

    /** A bar's picture, one for all bars of all marimbas: rosewood with a line of gold let into it (see [rosewood]). */
    val bar: Texture = rosewood()
}

/** The gold let into a bar: the instruments' old gold (`ORNAMENT_GOLD_RGB`) as a picture's colour. */
private const val INLAY_GOLD = "#bba062"

/**
 * A bar's picture: rosewood, its grain running along the bar, and a fine line of gold let into its middle with a
 * small diamond at either end. The bar is its upright; the grain is the same on every bar (drawn from fixed numbers).
 */
private fun rosewood(): Texture {
    val wide = 64
    val long = 256
    val canvas = document.createElement("canvas") as HTMLCanvasElement

    canvas.width = wide
    canvas.height = long

    val g = canvas.getContext("2d") as CanvasRenderingContext2D

    g.fillStyle = "#3a1e12"
    g.fillRect(0.0, 0.0, wide.toDouble(), long.toDouble())

    // the grain: streaks along the bar, darker and lighter, from a fixed row of numbers
    var seed = 7.0

    fun next(): Double {
        seed = (seed * 16807.0) % 2147483647.0

        return seed / 2147483647.0
    }

    repeat(46) {
        val x = next() * wide
        val from = next() * long * 0.6
        val length = long * (0.3 + 0.7 * next())

        g.strokeStyle = if (next() < 0.6) "rgba(20,8,4,0.55)" else "rgba(98,52,28,0.45)"
        g.lineWidth = 0.6 + 1.6 * next()
        g.beginPath()
        g.moveTo(x, from)
        g.lineTo(x + (next() - 0.5) * 3.0, from + length)
        g.stroke()
    }

    // the gold: a line down the middle, a diamond at either end
    val middle = wide / 2.0

    g.fillStyle = INLAY_GOLD
    g.fillRect(middle - 1.5, long * 0.12, 3.0, long * 0.76)

    for (y in listOf(long * 0.12, long * 0.88)) {
        g.beginPath()
        g.moveTo(middle, y - 9.0)
        g.lineTo(middle + 7.0, y)
        g.lineTo(middle, y + 9.0)
        g.lineTo(middle - 7.0, y)
        g.closePath()
        g.fill()
    }

    val texture = Three.addon.createCanvasTexture(canvas)

    texture.colorSpace = "srgb"
    texture.anisotropy = 8

    return texture
}

/** Which of the twelve notes of an octave are naturals (the bars of the near row), from C. */
private val NATURAL = listOf(true, false, true, false, true, true, false, true, false, true, false, true)

/**
 * One bar of the marimba: its mesh, its glowing material, its height at rest, and where a mallet lands on it ([place],
 * as plain numbers for the plan of the hands).
 */
internal class MarimbaBar(val bar: Mesh, val material: MeshStandardMaterial, val restY: Double, val place: MarimbaPoint) {
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
class Marimba(rig: Rig, notes: List<Note>, grows: Boolean, home: Place? = null) {
    private val s: Double = rig.s

    /** The marimba. */
    val group: Group = Three.addon.createGroup()

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

    /** The mallets' length, as the marimba player's equipment says ([MARIMBA_EQUIPMENT]). */
    val malletLength: Double = MARIMBA_EQUIPMENT.length * s

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
        // (at her place in the song, where it is told: at a change of song she is not there yet)
        if (home != null) {
            group.position.set(home.x, home.y, home.z)
            group.rotation.y = home.turn
        } else {
            group.position.copy(rig.root.position)
            group.rotation.y = rig.root.rotation.y
        }

        floor = group.position.y

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
                map = materials.bar
                roughness = 0.62
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
                place = MarimbaPoint(x = x, y = y + 0.012 * s, z = z - length * (if (natural) 0.15 else 0.30)),
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
            material = materials.brass,
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

        // no legs: the marimba floats (the maintainer, 2026-11-15: "make the marimba fly"); an end closes the frame
        // on either side, running down to a point below its middle like a keel
        val endThick = 0.03 * s
        val endFrom = near - 0.045 * s
        val endTo = far + 0.045 * s
        val endMiddle = (endFrom + endTo) / 2
        val endTop = height - 0.008 * s
        val endShoulder = height - 0.075 * s
        val endKeel = height - 0.16 * s
        val ends = listOf(-span / 2 - width, span / 2 + width)

        for (x in ends) {
            // drawn along z and up, then turned so that its depth lies along x
            val outline = Three.addon.createShape()

            outline.moveTo(endFrom, endTop)

            for ((z, y) in listOf(endTo to endTop, endTo to endShoulder, endMiddle to endKeel, endFrom to endShoulder)) {
                outline.asDynamic().lineTo(z, y)
            }

            outline.closePath()

            val end = Three.addon.createMesh(
                Three.addon.createExtrudeGeometry(
                    shape = outline,
                    options = jsObject<ExtrudeGeometryOptions> {
                        depth = endThick
                        bevelEnabled = false
                    },
                ),
                materials.wood,
            )

            end.rotation.y = -PI / 2
            end.position.set(x + endThick / 2, 0.0, 0.0)
            group.add(end)
        }

        group.traverse { o ->
            if (o.isMesh) {
                o.castShadow = true
            }
        }

        // the gold (it throws no shadow of its own: it lies flat on the frame). On each end one large diamond, a
        // smaller dark one within it, so that it is a diamond's outline
        val proud = 0.002 * s
        val aboutX = quat().setFromAxisAngle(vec(1.0, 0.0, 0.0), PI / 4)
        val aboutZ = quat().setFromAxisAngle(vec(0.0, 0.0, 1.0), PI / 4)
        val emblemAt = (endTop + endKeel) / 2 + 0.012 * s

        for (x in ends) {
            val out = if (x < 0) -1.0 else 1.0

            for ((size, material, lift) in listOf(
                Triple(0.062 * s, StringMaterials.gold, proud),
                Triple(0.044 * s, materials.wood, proud * 2),
            )) {
                val diamond = Three.addon.createMesh(
                    Three.addon.createBoxGeometry(width = proud * 2, height = size, depth = size),
                    material,
                )

                diamond.position.set(x + out * (endThick / 2 + lift - proud), emblemAt, endMiddle)
                diamond.quaternion.copy(aboutX)
                group.add(diamond)
            }
        }

        // along the two long sides a band of small diamonds, tip to tip, between two fine lines
        val railHigh = 0.03 * s
        val railY = height - 0.03 * s
        val across = 0.017 * s
        val long = span + width * 2
        val count = (long / across).toInt()
        val sides = listOf(near - 0.02 * s to -1.0, far to 1.0)
        val band = Three.addon.createInstancedMesh(
            geometry = Three.addon.createBoxGeometry(width = across / SQRT_2, height = across / SQRT_2, depth = proud * 2),
            material = StringMaterials.gold,
            count = count * sides.size,
        )
        val whole = vec(1.0, 1.0, 1.0)

        sides.forEachIndexed { row, (z, out) ->
            val face = z + out * (0.015 * s + proud)

            for (i in 0 until count) {
                val x = (i - (count - 1) / 2.0) * across

                band.setMatrixAt(row * count + i, mat4().compose(position = vec(x, railY, face), quaternion = aboutZ, scale = whole))
            }

            for (y in listOf(railY - railHigh / 2 + proud, railY + railHigh / 2 - proud)) {
                val line = Three.addon.createMesh(
                    Three.addon.createBoxGeometry(width = long, height = proud * 1.5, depth = proud * 2),
                    StringMaterials.gold,
                )

                line.position.set(0.0, y, face)
                group.add(line)
            }
        }

        group.add(band)

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
            // a mallet stands on the stage, not in the marimba's group: it casts its shadow by its own flag
            mallet.traverse { o ->
                if (o.isMesh) {
                    o.castShadow = true
                }
            }
            stage.add(mallet)

            return mallet
        }

        mallets = listOf(Side.L, Side.R).associateWith { listOf(mallet(), mallet()) }
    }

    /** The bar of the note [midi]: its own, or the same note as many octaves nearer as it takes where it lies beyond the bars. */
    internal fun barOf(midi: Int): MarimbaBar {
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
        /** The highest bar of a marimba whose song still grows: the c three octaves above the middle one. */
        private const val GROWING_TOP = 96

        /** The square root of two: a square's diagonal over its side. */
        private const val SQRT_2 = 1.4142135623730951

        /** Whether the midi note [m] is a natural; none below midi 0, as in the prototype. */
        private fun isNatural(m: Int): Boolean = NATURAL.getOrElse(m % 12) { false }
    }
}
