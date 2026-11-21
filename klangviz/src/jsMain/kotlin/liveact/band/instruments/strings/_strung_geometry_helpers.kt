/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.strings

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.workshop.threejs.ExtrudeGeometryOptions
import io.peekandpoke.klang.workshop.threejs.createBufferGeometry
import io.peekandpoke.klang.workshop.threejs.createExtrudeGeometry
import io.peekandpoke.klang.workshop.threejs.createFloat32Attribute
import io.peekandpoke.klang.workshop.threejs.createShape
import io.peekandpoke.klang.workshop.threejs.setAttribute
import io.peekandpoke.kraft.addons.threejs.js.BufferGeometry
import io.peekandpoke.kraft.utils.jsObject
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.roundToInt
import kotlin.math.sqrt

// The shapes a strung instrument is built of (see StrungInstrument): an outline cut from a slab with rounded edges,
// shaded smooth; a neck's fretboard measured for its frets; several small parts as one mesh.

/**
 * The [outline] cut from a slab [depth] deep, its edges rounded over [round] (as far out from the outline as into
 * the depth) in [roundSteps] steps, with [steps] points on each stretch of the outline (see [BodyOutline.contour]).
 * In the outline's own measure: the back face at z = -[round], the front face at z = [depth] + [round].
 *
 * Shaded smooth round the edge (see [smoothEdges]), so a few points on the outline and a few steps of the rounding
 * look round under the lacquer's long highlights.
 */
internal fun roundedSlab(outline: BodyOutline, steps: Int, depth: Double, round: Double, roundSteps: Int): BufferGeometry {
    val shape = Three.addon.createShape()
    val edge = outline.contour(steps)
    val (x0, y0) = edge[0]

    shape.moveTo(x0, y0)

    for ((x, y) in edge.drop(1)) {
        shape.asDynamic().lineTo(x, y)
    }

    shape.closePath()

    val options = jsObject<ExtrudeGeometryOptions> {
        this.depth = depth
        bevelEnabled = true
        bevelSize = round
        bevelThickness = round
        bevelSegments = roundSteps
        curveSegments = 1
    }
    val geometry = Three.addon.createExtrudeGeometry(shape = shape, options = options)

    smoothEdges(geometry = geometry, front = depth + round, back = -round)

    return geometry
}

/**
 * Smooth shading round the edge of an outline cut from a slab ([geometry], as three.js cuts one: every triangle on its
 * own, each shaded flat): every place on the rounded edge and the side takes the mean of the faces that meet there
 * (each by its angle at that place), and where the edge meets the flat [front] or [back] face (their z) it faces
 * straight out of that face, so the face runs into the rounding without a crease. The two faces stay flat.
 */
internal fun smoothEdges(geometry: BufferGeometry, front: Double, back: Double) {
    val attributes = geometry.asDynamic().attributes
    val position = attributes.position.array.unsafeCast<Float32Array>()
    val normal = attributes.normal.array.unsafeCast<Float32Array>()
    val count: Int = attributes.position.count.unsafeCast<Int>()
    val sums = HashMap<String, DoubleArray>()

    fun at(i: Int, axis: Int): Double = position[3 * i + axis].toDouble()

    fun placeOf(i: Int): String =
        "${(at(i, 0) * 1e5).roundToInt()},${(at(i, 1) * 1e5).roundToInt()},${(at(i, 2) * 1e5).roundToInt()}"

    /** The flat face normal of the triangle from [a] on, or null where it is one of the two faces. */
    fun faceOf(a: Int): DoubleArray? {
        val ux = at(a + 1, 0) - at(a, 0)
        val uy = at(a + 1, 1) - at(a, 1)
        val uz = at(a + 1, 2) - at(a, 2)
        val vx = at(a + 2, 0) - at(a, 0)
        val vy = at(a + 2, 1) - at(a, 1)
        val vz = at(a + 2, 2) - at(a, 2)
        val n = doubleArrayOf(uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx)
        val length = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2])

        if (!(length > 1e-12)) {
            return null
        }

        for (k in 0..2) {
            n[k] /= length
        }

        return if (abs(n[2]) > 0.9999) null else n
    }

    /** The angle at [i] between its two neighbours [j] and [k] in a triangle. */
    fun angleAt(i: Int, j: Int, k: Int): Double {
        val ux = at(j, 0) - at(i, 0)
        val uy = at(j, 1) - at(i, 1)
        val uz = at(j, 2) - at(i, 2)
        val vx = at(k, 0) - at(i, 0)
        val vy = at(k, 1) - at(i, 1)
        val vz = at(k, 2) - at(i, 2)
        val lengths = sqrt((ux * ux + uy * uy + uz * uz) * (vx * vx + vy * vy + vz * vz))

        return if (lengths > 1e-18) acos(((ux * vx + uy * vy + uz * vz) / lengths).coerceIn(-1.0, 1.0)) else 0.0
    }

    val faces = (0 until count / 3).map { faceOf(3 * it) }

    faces.forEachIndexed { t, n ->
        if (n != null) {
            val a = 3 * t

            for ((i, j, k) in listOf(Triple(a, a + 1, a + 2), Triple(a + 1, a + 2, a), Triple(a + 2, a, a + 1))) {
                val sum = sums.getOrPut(placeOf(i)) { DoubleArray(3) }
                val w = angleAt(i, j, k)

                for (axis in 0..2) {
                    sum[axis] += n[axis] * w
                }
            }
        }
    }

    for (i in 0 until count) {
        if (faces[i / 3] == null) {
            continue
        }

        val z = at(i, 2)
        val sum = sums.getValue(placeOf(i))
        val length = sqrt(sum[0] * sum[0] + sum[1] * sum[1] + sum[2] * sum[2])
        val n = when {
            abs(z - front) < 1e-5 -> doubleArrayOf(0.0, 0.0, 1.0)
            abs(z - back) < 1e-5 -> doubleArrayOf(0.0, 0.0, -1.0)
            length > 1e-12 -> doubleArrayOf(sum[0] / length, sum[1] / length, sum[2] / length)
            else -> continue
        }

        for (axis in 0..2) {
            normal[3 * i + axis] = n[axis].toFloat()
        }
    }

    attributes.normal.needsUpdate = true
}

/**
 * Measures a neck's fretboard for its frets (see `markNeck`): every place of the neck's box [geometry] gets as `uv`
 * how far it is from the [nut] toward the body and from the middle across, in [scale]s (the nut to the bridge, in the
 * instrument's measure); a place on any side but the fretboard is put far out across, where nothing is drawn. [centre]:
 * where along the neck the box's middle is.
 */
internal fun fretboardUv(geometry: BufferGeometry, centre: Double, nut: Double, scale: Double) {
    val attributes = geometry.asDynamic().attributes
    val position = attributes.position.array.unsafeCast<Float32Array>()
    val normal = attributes.normal.array.unsafeCast<Float32Array>()
    val uv = attributes.uv.array.unsafeCast<Float32Array>()
    val count: Int = attributes.position.count.unsafeCast<Int>()

    for (i in 0 until count) {
        val onBoard = normal[3 * i + 2] > 0.5f

        uv[2 * i] = ((nut - (position[3 * i] + centre)) / scale).toFloat()
        uv[2 * i + 1] = if (onBoard) (position[3 * i + 1] / scale).toFloat() else FAR_OFF_BOARD
    }

    attributes.uv.needsUpdate = true
}

/** Across the neck, far beyond any dot or fret: where its other sides are measured. */
private const val FAR_OFF_BOARD = 9.0f

/**
 * The [parts] as one geometry: one mesh, drawn at once, where each would be drawn on its own (and again for every
 * shadow and for the mirror floor). Each part is placed already; they are freed.
 */
internal fun merged(parts: List<BufferGeometry>): BufferGeometry {
    val positions = ArrayList<Double>()
    val normals = ArrayList<Double>()

    for (part in parts) {
        val flat = part.asDynamic().toNonIndexed().unsafeCast<BufferGeometry>()
        val attributes = flat.asDynamic().attributes
        val position = attributes.position.array.unsafeCast<Float32Array>()
        val normal = attributes.normal.array.unsafeCast<Float32Array>()

        for (i in 0 until position.length) {
            positions.add(position[i].toDouble())
            normals.add(normal[i].toDouble())
        }

        flat.dispose()
        part.dispose()
    }

    val geometry = Three.addon.createBufferGeometry()

    geometry.setAttribute("position", Three.addon.createFloat32Attribute(positions.toTypedArray(), 3))
    geometry.setAttribute("normal", Three.addon.createFloat32Attribute(normals.toTypedArray(), 3))

    return geometry
}
