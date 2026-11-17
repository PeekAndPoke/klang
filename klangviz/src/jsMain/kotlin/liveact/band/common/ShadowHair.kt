/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.common

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.band.HairPoint
import io.peekandpoke.klang.viz.liveact.band.MedusaHair
import io.peekandpoke.klang.viz.liveact.band.pulledHead
import io.peekandpoke.klang.viz.liveact.band.sinceLast
import io.peekandpoke.klang.viz.liveact.mat4
import io.peekandpoke.klang.viz.liveact.quat
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.InstancedMesh
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.applyQuaternion
import io.peekandpoke.klang.workshop.threejs.colorWrite
import io.peekandpoke.klang.workshop.threejs.createInstancedMesh
import io.peekandpoke.klang.workshop.threejs.depthWrite
import io.peekandpoke.klang.workshop.threejs.distanceTo
import io.peekandpoke.klang.workshop.threejs.frustumCulled
import io.peekandpoke.klang.workshop.threejs.getWorldQuaternion
import io.peekandpoke.klang.workshop.threejs.lerp
import io.peekandpoke.klang.workshop.threejs.translate
import io.peekandpoke.kraft.addons.threejs.js.MaterialParameters
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import io.peekandpoke.kraft.utils.jsObject

/**
 * Hair that is seen only as a shadow: Medusa's snakes on the head of [rig], writhing (see [MedusaHair]). They are
 * not drawn, but they stand in the lamps' light: so the figure wears its hair as ever, and its shadow on the wall
 * behind the stage, and on the floor, has snakes for hair (the maintainer, 2026-11-17: "moving medusa hair, like
 * snakes, but only in the shadow on the backwall").
 *
 * Each snake's own head does not go with her head: when she nods or turns it stays where it was and finds its place
 * again, and only when its body is stretched straight is it pulled along (the maintainer, 2026-11-17: "imagine
 * every snake head having its own mind of where to move").
 *
 * [shown]: draws them as well, to look at them (`?snakes`).
 *
 * All the snakes are one mesh drawn many times over (a piece of a snake's body each), so they cost one draw for the
 * picture and one for each lamp's shadow.
 */
class ShadowHair(private val rig: Rig, shown: Boolean) {
    private val hair = MedusaHair()

    /** The pieces of one snake: a piece of its body between each two joints, and its head at the tip. */
    private val pieces = hair.joints

    private val mesh: InstancedMesh

    /** How long the head's bone is and how large the head, in the world. */
    private val bone: Double
    private val radius: Double

    /** How the head's bone is turned against the figure at rest. */
    private val headAtRest = rig.restTurn("head")

    /**
     * Where each snake's own head is, in the world, and the moment it was set: the one thing here that goes on
     * from the frame before. A snake's head has a mind of its own (see [pulledHead]); a still, whose time stands,
     * shows it where it wants to be.
     */
    private val tips = arrayOfNulls<HairPoint>(hair.snakes)
    private var last: Double? = null

    private val place = mat4()
    private val turn = quat()
    private val size = vec()

    init {
        val stage = rig.root.parent ?: error("The figure stands on no stage")
        val material = Three.addon.createMeshBasicMaterial(jsObject<MaterialParameters> { color = 0x101014 })

        if (!shown) {
            // nothing of it reaches the picture; the lamps' shadows are drawn from its shape alone
            material.colorWrite = false
            material.depthWrite = false
        }

        // one piece: a round stick one long and one thick, standing on its foot
        val stick = Three.addon.createCylinderGeometry(radiusTop = 0.5, radiusBottom = 0.5, height = 1.0, radialSegments = 6)

        stick.translate(x = 0.0, y = 0.5, z = 0.0)

        mesh = Three.addon.createInstancedMesh(geometry = stick, material = material, count = hair.snakes * pieces)
        mesh.castShadow = true
        mesh.receiveShadow = false
        mesh.frustumCulled = false
        stage.add(mesh)

        val grown = rig.root.scale.y

        bone = rig.restStart("head").distanceTo(rig.restTail("head")) * grown
        radius = HEAD * rig.s
    }

    /** The snakes at [t] (seconds), on the head as it is held right now: call it once the figure's pose is set. */
    fun update(t: Double) {
        val held = rig.bone("head").getWorldQuaternion(turn)
        // the head's middle: where the head's bone ends
        val middle = rig.at("head").addScaledVector(vec(0.0, 1.0, 0.0).applyQuaternion(held), bone)
        // how the head would be held if it only went with the figure as a whole: a snake's own head keeps to that,
        // and does not nod or turn with the head it grows on
        val calm = rig.root.getWorldQuaternion(quat()).multiply(headAtRest)
        // a moment that does not follow the last one (the first, a jump in the song): every snake is where it wants
        val dt = sinceLast(last = last, t = t, most = MOMENT)
        val anew = dt == null
        val tipJoint = hair.joints - 1

        last = t

        for (snake in 0 until hair.snakes) {
            val shape = (0 until hair.joints).map { joint ->
                val p = hair.at(snake = snake, joint = joint, t = t)

                vec(p.x, p.y, p.z)
            }
            val root = shape[0].clone().applyQuaternion(held).multiplyScalar(radius).add(middle)
            val wants = shape[tipJoint].clone().applyQuaternion(calm).multiplyScalar(radius).add(middle)
            val before = tips[snake]
            val tip = pulledHead(
                head = if (anew || before == null) wants.point() else before,
                wants = wants.point(),
                root = root.point(),
                reach = REACH * hair.length * radius,
                dt = dt ?: 0.0,
            )

            tips[snake] = tip

            // each point of the body: with the head it grows on near the root, with its own head near the tip
            val points = shape.mapIndexed { joint, p ->
                val grown = p.clone().applyQuaternion(held).multiplyScalar(radius).add(middle)
                val own = p.clone().sub(shape[tipJoint]).applyQuaternion(calm).multiplyScalar(radius).add(vec(tip.x, tip.y, tip.z))

                grown.lerp(own, hair.free(joint))
            }

            for (joint in 1 until hair.joints) {
                val from = points[joint - 1]
                val to = points[joint]
                val along = joint.toDouble() / tipJoint
                // thinner toward the tip
                val thick = radius * (THICK - (THICK - THIN) * along)

                set(index = snake * pieces + joint - 1, from = from, to = to, thick = thick)

                // the head: a short thick piece on from the tip
                if (joint == tipJoint) {
                    val on = to.clone().sub(from).normalize()

                    set(
                        index = snake * pieces + joint,
                        from = to.clone().addScaledVector(on, -0.1 * radius),
                        to = to.clone().addScaledVector(on, 0.45 * radius),
                        thick = radius * THIN * 1.6,
                    )
                }
            }
        }

        mesh.instanceMatrix.needsUpdate = true
    }

    private fun Vector3.point() = HairPoint(x = x, y = y, z = z)

    /** The piece [index] reaches from [from] to [to], [thick] across. */
    private fun set(index: Int, from: Vector3, to: Vector3, thick: Double) {
        val way = to.clone().sub(from)
        val long = way.length()
        val q = quat().setFromUnitVectors(Y_AXIS, way.normalize())

        place.compose(position = from, quaternion = q, scale = size.set(thick, long, thick))
        mesh.setMatrixAt(index, place)
    }

    private companion object {
        /** A head's radius, in the figure's measure. */
        const val HEAD = 0.088

        /** How far a snake's head can be from its root, as a share of the snake's length: the body stretched straight. */
        const val REACH = 1.2

        /** Two frames further apart than this many seconds do not follow each other: the snakes are set anew. */
        const val MOMENT = 0.5

        /** How thick a snake is at its root and at its tip, in head radii. */
        const val THICK = 0.62
        const val THIN = 0.34
    }
}
