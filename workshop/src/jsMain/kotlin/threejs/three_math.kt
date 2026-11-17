@file:Suppress("FunctionName", "unused")

package io.peekandpoke.klang.workshop.threejs

import io.peekandpoke.kraft.addons.threejs.ThreeJsAddon
import io.peekandpoke.kraft.addons.threejs.js.Euler
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import io.peekandpoke.kraft.addons.threejs.js.Vector3

/**
 * three.js's arithmetic beyond what Kraft's addon declares: quaternions, matrices, and the rest of a vector's
 * methods. Types only, as in the addon: an instance is made through [ThreeJsAddon] ([createQuaternion],
 * [createMatrix4], the addon's `createVector3`).
 *
 * As in three.js a method changes the object it is called on and returns it, so calls chain; [clone] first where
 * the original must stay. The operators at the end ([plus], [minus], [times]) give a new vector instead.
 */

/** A rotation as a quaternion. */
external class Quaternion {
    var x: Double
    var y: Double
    var z: Double
    var w: Double

    fun set(x: Double, y: Double, z: Double, w: Double): Quaternion
    fun copy(q: Quaternion): Quaternion
    fun clone(): Quaternion

    /** No rotation. */
    fun identity(): Quaternion

    /** The opposite rotation. */
    fun invert(): Quaternion

    /** This rotation after [q]: `this * q`. */
    fun multiply(q: Quaternion): Quaternion

    /** [q] after this rotation: `q * this`. */
    fun premultiply(q: Quaternion): Quaternion

    /** [share] of the way (0 to 1) to [q], by the shorter way round. */
    fun slerp(q: Quaternion, share: Double): Quaternion

    /** The rotation about [axis] (of length 1) by [angle] radians. */
    fun setFromAxisAngle(axis: Vector3, angle: Double): Quaternion

    /** The shortest rotation that turns [from] into [to], both of length 1. */
    fun setFromUnitVectors(from: Vector3, to: Vector3): Quaternion

    /** The rotation of [matrix]'s upper three by three part, which must be a pure rotation. */
    fun setFromRotationMatrix(matrix: Matrix4): Quaternion

    fun setFromEuler(euler: Euler): Quaternion

    /** The angle between this rotation and [q], in radians. */
    fun angleTo(q: Quaternion): Double
}

/** A four by four matrix: a node's place, turn and size in one. */
external class Matrix4 {
    val elements: Array<Double>

    fun copy(m: Matrix4): Matrix4
    fun clone(): Matrix4
    fun identity(): Matrix4
    fun invert(): Matrix4
    fun multiply(m: Matrix4): Matrix4

    /** The rotation whose three axes are [x], [y] and [z]. */
    fun makeBasis(x: Vector3, y: Vector3, z: Vector3): Matrix4

    /** Set to: sized by [scale], then turned by [quaternion], then moved to [position]. */
    fun compose(position: Vector3, quaternion: Quaternion, scale: Vector3): Matrix4
}

fun ThreeJsAddon.createQuaternion(): Quaternion = create("Quaternion")

fun ThreeJsAddon.createMatrix4(): Matrix4 = create("Matrix4")

////  A VECTOR'S OTHER METHODS  ///////////////////////////////////////////////////////////////////////////////////////

fun Vector3.setY(y: Double): Vector3 = also { it.y = y }

/** Adds [v] times [s]. */
fun Vector3.addScaledVector(v: Vector3, s: Double): Vector3 = also { asDynamic().addScaledVector(v, s) }

/** [share] of the way (0 to 1) to [to]. */
fun Vector3.lerp(to: Vector3, share: Double): Vector3 = also { asDynamic().lerp(to, share) }

/** Set to [share] of the way (0 to 1) from [from] to [to]. */
fun Vector3.lerpVectors(from: Vector3, to: Vector3, share: Double): Vector3 = also { asDynamic().lerpVectors(from, to, share) }

/** This vector crossed with [v]. */
fun Vector3.cross(v: Vector3): Vector3 = also { asDynamic().cross(v) }

/** Set to [a] crossed with [b]. */
fun Vector3.crossVectors(a: Vector3, b: Vector3): Vector3 = also { asDynamic().crossVectors(a, b) }

/** Set to [a] minus [b]. */
fun Vector3.subVectors(a: Vector3, b: Vector3): Vector3 = also { asDynamic().subVectors(a, b) }

fun Vector3.negate(): Vector3 = also { asDynamic().negate() }

fun Vector3.lengthSq(): Double = asDynamic().lengthSq().unsafeCast<Double>()

/** The angle to [v], in radians. */
fun Vector3.angleTo(v: Vector3): Double = asDynamic().angleTo(v).unsafeCast<Double>()

/** Turned by [q]. */
fun Vector3.applyQuaternion(q: Quaternion): Vector3 = also { asDynamic().applyQuaternion(q) }

/** Turned about [axis] (of length 1) by [angle] radians. */
fun Vector3.applyAxisAngle(axis: Vector3, angle: Double): Vector3 = also { asDynamic().applyAxisAngle(axis, angle) }

/** As a point, moved by [m]. */
fun Vector3.applyMatrix4(m: Matrix4): Vector3 = also { asDynamic().applyMatrix4(m) }

/** As a direction, turned by [m]'s rotation, and brought to length 1. */
fun Vector3.transformDirection(m: Matrix4): Vector3 = also { asDynamic().transformDirection(m) }

/** A new vector: this plus [v]. */
operator fun Vector3.plus(v: Vector3): Vector3 = clone().add(v)

/** A new vector: this minus [v]. */
operator fun Vector3.minus(v: Vector3): Vector3 = clone().sub(v)

/** A new vector: this times [s]. */
operator fun Vector3.times(s: Double): Vector3 = clone().multiplyScalar(s)

/** A new vector: the opposite one. */
operator fun Vector3.unaryMinus(): Vector3 = clone().multiplyScalar(-1.0)

////  A NODE'S PLACE IN THE WORLD  ////////////////////////////////////////////////////////////////////////////////////

/** The node's turn against its parent, as a quaternion; `rotation` follows it and it follows `rotation`. */
val Object3D.quaternion: Quaternion
    get() = asDynamic().quaternion.unsafeCast<Quaternion>()

/** The node's place, turn and size in the world, as of the last [Object3D.updateWorldMatrix]. */
val Object3D.matrixWorld: Matrix4
    get() = asDynamic().matrixWorld.unsafeCast<Matrix4>()

/** The node's place in the world, written into [into]. It brings the world matrix up to date first. */
fun Object3D.getWorldPosition(into: Vector3): Vector3 = also { asDynamic().getWorldPosition(into) }.let { into }

/** The node's turn in the world, written into [into]. It brings the world matrix up to date first. */
fun Object3D.getWorldQuaternion(into: Quaternion): Quaternion = also { asDynamic().getWorldQuaternion(into) }.let { into }

/** Turns [vector], a point in world coordinates, into this node's coordinates, in place. */
fun Object3D.worldToLocal(vector: Vector3): Vector3 = also { asDynamic().worldToLocal(vector) }.let { vector }
