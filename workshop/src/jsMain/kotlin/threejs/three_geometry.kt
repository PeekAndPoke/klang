@file:Suppress("FunctionName", "unused")

package io.peekandpoke.klang.workshop.threejs

import io.peekandpoke.kraft.addons.threejs.ThreeJsAddon
import io.peekandpoke.kraft.addons.threejs.js.BufferGeometry
import io.peekandpoke.kraft.addons.threejs.js.CylinderGeometry
import io.peekandpoke.kraft.addons.threejs.js.Material
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import io.peekandpoke.kraft.addons.threejs.js.Vector2
import io.peekandpoke.kraft.addons.threejs.js.Vector3

/**
 * Shapes beyond the addon's boxes, spheres and plain cylinders: an outline cut from a slab, a turned profile, part
 * of a cylinder or a cone, one mesh drawn many times over, and a bounding sphere. Types only, as in the addon: an
 * instance is made through [ThreeJsAddon] (the `create...` functions here).
 */

/** A flat outline, drawn as a pen draws: [moveTo] its start, then lines and curves on from there. */
external class Shape {
    /** Starts the outline at [x], [y]. */
    fun moveTo(x: Double, y: Double): Shape

    /** A smooth curve on from where the outline is through [points]. */
    fun splineThru(points: Array<Vector2>): Shape

    /** Closes the outline with a straight line back to its start. */
    fun closePath(): Shape
}

/** How an [ExtrudeGeometry] is cut from a slab. A field left out keeps three.js's default. */
external interface ExtrudeGeometryOptions {
    /** How deep the slab is. */
    var depth: Double

    /** Whether the edges are rounded off. */
    var bevelEnabled: Boolean

    /** How far the rounding reaches into the outline, and how far into the slab's depth. */
    var bevelSize: Double
    var bevelThickness: Double

    /** How many steps the rounding is made of. */
    var bevelSegments: Int

    /** How many points a curve of the outline is made of. */
    var curveSegments: Int
}

/** A [Shape] cut from a slab: the outline is its face, the slab's depth along z. */
external class ExtrudeGeometry : BufferGeometry

/** One mesh drawn [count] times, each with its own place, turn and size: one draw for all of them. */
external class InstancedMesh : Object3D {
    /** How many of them are drawn: at most as many as it was made for. */
    var count: Int

    /** Each one's place, turn and size, as matrices in a row: set [BufferAttribute.needsUpdate] after a change. */
    val instanceMatrix: BufferAttribute

    /** Sets the place, turn and size of the one at [index]. */
    fun setMatrixAt(index: Int, matrix: Matrix4)
}

/** A sphere: what a mesh's points lie in, to tell whether the camera can see any of them. */
external class Sphere {
    val center: Vector3
    var radius: Double
}

/** Whether the node is a mesh drawn many times over. */
val Object3D.isInstancedMesh: Boolean
    get() = asDynamic().isInstancedMesh == true

////  MAKING  /////////////////////////////////////////////////////////////////////////////////////////////////////////

/** An empty outline: start it with [Shape.moveTo]. */
fun ThreeJsAddon.createShape(): Shape = create("Shape")

fun ThreeJsAddon.createExtrudeGeometry(shape: Shape, options: ExtrudeGeometryOptions): ExtrudeGeometry =
    create("ExtrudeGeometry", shape, options)

/**
 * The profile [points] (x out from the axis, y along it) turned about the y axis, in [segments] steps: a bell, a
 * vase.
 */
fun ThreeJsAddon.createLatheGeometry(points: Array<Vector2>, segments: Int): BufferGeometry =
    create("LatheGeometry", points, segments)

/**
 * A cylinder with all of three.js's choices: [heightSegments] rings along it, without its two lids where
 * [openEnded], and only the part of the turn from [thetaStart] that is [thetaLength] wide (in radians, 0 is
 * toward +z).
 */
fun ThreeJsAddon.createCylinderGeometry(
    radiusTop: Double,
    radiusBottom: Double,
    height: Double,
    radialSegments: Int,
    heightSegments: Int,
    openEnded: Boolean,
    thetaStart: Double,
    thetaLength: Double,
): CylinderGeometry = create(
    "CylinderGeometry", radiusTop, radiusBottom, height, radialSegments, heightSegments, openEnded, thetaStart, thetaLength,
)

/** A cone standing on its base, its tip up: [heightSegments] rings along it, without its base where [openEnded]. */
fun ThreeJsAddon.createConeGeometry(
    radius: Double,
    height: Double,
    radialSegments: Int,
    heightSegments: Int,
    openEnded: Boolean,
): BufferGeometry = create("ConeGeometry", radius, height, radialSegments, heightSegments, openEnded)

/** [geometry] drawn with [material] up to [count] times; each one's matrix starts as no move at all. */
fun ThreeJsAddon.createInstancedMesh(geometry: BufferGeometry, material: Material, count: Int): InstancedMesh =
    create("InstancedMesh", geometry, material, count)

/** A sphere about [center], reaching [radius] from it. */
fun ThreeJsAddon.createSphere(center: Vector3, radius: Double): Sphere = create("Sphere", center, radius)
