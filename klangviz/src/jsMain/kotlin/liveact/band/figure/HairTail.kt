/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.figure

import io.peekandpoke.klang.viz.liveact.quat
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.Bone
import io.peekandpoke.klang.workshop.threejs.Quaternion
import io.peekandpoke.klang.workshop.threejs.SkinnedMesh
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.applyQuaternion
import io.peekandpoke.klang.workshop.threejs.asBone
import io.peekandpoke.klang.workshop.threejs.asSkinnedMesh
import io.peekandpoke.klang.workshop.threejs.distanceTo
import io.peekandpoke.klang.workshop.threejs.dot
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.klang.workshop.threejs.getWorldQuaternion
import io.peekandpoke.klang.workshop.threejs.materials
import io.peekandpoke.klang.workshop.threejs.name
import io.peekandpoke.klang.workshop.threejs.worldToLocal
import io.peekandpoke.kraft.addons.threejs.js.Vector3

/**
 * A figure's tail of hair, hanging (see [HangingTail]): the joints of its skeleton named `hair1`, `hair2` and on
 * (as many as the figure has, in their order), swung by the tail's own motion rather than turned with the head at
 * once. Made by [of] for a figure that has such joints.
 *
 * Everything it knows of the tail and the body is read from the figure as it is loaded: the tail's joints and their
 * rest directions from the bones, its tip and its thickness from the skin bound to them, and how far the head, the
 * neck, the back and the shoulders reach from the skin round their bones. The skinned mesh, its shadow and its
 * reflection follow the bones by themselves.
 *
 * Its state (where the tail's points are, and the last moment) is its own: it goes with the band that keeps it.
 */
class HairTail private constructor(
    private val rig: Rig,
    /** The tail's bones, from the root: each reaches from one of its points to the next. */
    private val bones: List<String>,
    /** The bone the tail hangs on (the head). */
    private val parent: String,
    /** The body's capsules: each a bone, from its start to its end (where its first child bone starts). */
    private val sticks: List<String>,
    shape: TailShape,
) {
    private val chain = HangingTail(shape)
    private val hold = TailHold(sticks.size)

    /** How the parent is turned against the figure at rest, taken back: what turns the rest shape with it. */
    private val parentAtRest: Quaternion = rig.restTurn(parent).invert()

    /** The bones the capsules end at. */
    private val stickEnds: List<Bone> = sticks.map { firstChild(rig.bone(it)) ?: error("The bone '$it' has no child") }

    /** The tail at [t] (seconds), on the figure as it is posed at last: call it once nothing turns the head any more. */
    fun update(t: Double) {
        val pin = rig.at(bones[0])
        val turn = rig.bone(parent).getWorldQuaternion(quat()).multiply(parentAtRest)

        hold.pin[0] = pin.x
        hold.pin[1] = pin.y
        hold.pin[2] = pin.z
        hold.turn[0] = turn.x
        hold.turn[1] = turn.y
        hold.turn[2] = turn.z
        hold.turn[3] = turn.w

        for ((c, name) in sticks.withIndex()) {
            val a = rig.at(name)
            val b = stickEnds[c].getWorldPosition(vec())

            hold.body[6 * c] = a.x
            hold.body[6 * c + 1] = a.y
            hold.body[6 * c + 2] = a.z
            hold.body[6 * c + 3] = b.x
            hold.body[6 * c + 4] = b.y
            hold.body[6 * c + 5] = b.z
        }

        chain.follow(t, hold)

        // each bone aims at the next point, from where its parent has put it: the shortest turn, so the tail does
        // not twist
        for ((i, name) in bones.withIndex()) {
            val from = rig.at(name)

            rig.point(name, vec(chain.x(i + 1) - from.x, chain.y(i + 1) - from.y, chain.z(i + 1) - from.z))
        }
    }

    companion object {
        /** A joint of a tail of hair, by its number. */
        private val HAIR = Regex("hair(\\d+)")

        /** The bones the body round the tail is measured on, where the figure has them. */
        private val BODY = listOf("hips", "belly", "chest", "neck", "shoulderL", "shoulderR")

        /** How far the head and the body reach round their bones: the distance this share of the skin is within. */
        private const val GIRTH = 0.75

        /** How thick the tail is round its bones: the distance this share of its skin is within. */
        private const val THICK = 0.5

        /** A point of the skin belongs to the tail where the tail's bones carry this much of it or more. */
        private const val TAILS = 0.5

        private fun firstChild(bone: Bone): Bone? = bone.children.firstNotNullOfOrNull { it.asBone }

        /** The hanging tail of [rig], or null where the figure has no joints of a tail of hair. */
        fun of(rig: Rig): HairTail? {
            val numbered = rig.bones.keys
                .mapNotNull { name -> HAIR.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()?.let { it to name } }
                .sortedBy { it.first }
                .map { it.second }

            if (numbered.isEmpty()) {
                return null
            }

            // the chain as the bones hang on each other, from the first: a joint that does not hang on the one
            // before ends it
            val bones = mutableListOf(numbered[0])

            for (name in numbered.drop(1)) {
                if (rig.bone(name).parent != rig.bone(bones.last())) {
                    break
                }

                bones.add(name)
            }

            val parent = rig.bone(bones[0]).parent?.asBone?.name ?: return null

            if ("head" !in rig.bones) {
                return null
            }

            rig.update()

            val skin = Skin.of(rig = rig, tail = bones.toSet())
            // the world's measure against the figure's
            val grown = rig.root.localToWorld(vec(0.0, 0.0, 0.0)).distanceTo(rig.root.localToWorld(vec(1.0, 0.0, 0.0)))

            // the tail's points: where each of its bones starts, and the tip, where the last one's skin ends
            val starts = bones.map { rig.restStart(it) }
            val last = bones.last()
            val lastAxis = vec(0.0, 1.0, 0.0).applyQuaternion(rig.restTurn(last))
            val lastStart = starts.last()
            val reach = skin.of(last).maxOfOrNull { it.clone().sub(lastStart).dot(lastAxis) }
            val tipLength = reach?.takeIf { it > 0 }
                ?: if (starts.size >= 2) starts[starts.size - 2].distanceTo(lastStart) else 0.1 * rig.height
            val points = starts + lastStart.clone().addScaledVector(lastAxis, tipLength)

            // how thick the tail is at each point: round the bone that starts there (the tip: round the last bone)
            val thick = points.indices.map { i ->
                val bone = bones[minOf(i, bones.size - 1)]
                val a = points[minOf(i, bones.size - 1)]
                val b = points[minOf(i, bones.size - 1) + 1]

                reachRound(
                    points = skin.flat(skin.of(bone)),
                    ax = a.x, ay = a.y, az = a.z,
                    bx = b.x, by = b.y, bz = b.z,
                    share = THICK,
                )
            }

            // the body's bones as sticks: each skin point is measured on the one it is nearest to
            val all = rig.bones.keys.filter { it !in bones && firstChild(rig.bone(it)) != null }
            val ends = all.associateWith { rig.restTail(it) }
            val flatSticks = DoubleArray(6 * all.size)

            for ((s, name) in all.withIndex()) {
                val a = rig.restStart(name)
                val b = ends.getValue(name)

                doubleArrayOf(a.x, a.y, a.z, b.x, b.y, b.z).copyInto(destination = flatSticks, destinationOffset = 6 * s)
            }

            val body = skin.flat(skin.body)
            val nearest = nearestSticks(points = body, sticks = flatSticks)

            fun nearTo(name: String): DoubleArray {
                val s = all.indexOf(name)
                val picked = nearest.indices.filter { nearest[it] == s }

                return DoubleArray(3 * picked.size) { k -> body[3 * picked[k / 3] + k % 3] }
            }

            // the head: a ball round where its bone ends
            val headMiddle = ends["head"] ?: rig.restStart("head")
            val headStart = rig.restStart("head")
            val headRadius = reachRound(
                points = nearTo("head"),
                ax = headMiddle.x, ay = headMiddle.y, az = headMiddle.z,
                bx = headMiddle.x, by = headMiddle.y, bz = headMiddle.z,
                share = GIRTH,
            ).takeIf { it > 0 } ?: headStart.distanceTo(headMiddle)

            val sticks = BODY.filter { it in all }
            val capsules = DoubleArray(7 * sticks.size)

            for ((c, name) in sticks.withIndex()) {
                val a = rig.restStart(name)
                val b = ends.getValue(name)
                val r = reachRound(points = nearTo(name), ax = a.x, ay = a.y, az = a.z, bx = b.x, by = b.y, bz = b.z, share = GIRTH)

                doubleArrayOf(a.x, a.y, a.z, b.x, b.y, b.z, r).copyInto(destination = capsules, destinationOffset = 7 * c)
            }

            val shape = TailShape(
                points = DoubleArray(3 * points.size) { k -> grown * points[k / 3].component(k % 3) },
                thick = DoubleArray(points.size) { grown * thick[it] },
                head = doubleArrayOf(grown * headMiddle.x, grown * headMiddle.y, grown * headMiddle.z, grown * headRadius),
                body = DoubleArray(capsules.size) { grown * capsules[it] },
            )

            return HairTail(rig = rig, bones = bones, parent = parent, sticks = sticks, shape = shape)
        }

        private fun Vector3.component(k: Int): Double = when (k) {
            0 -> x
            1 -> y
            else -> z
        }
    }

    /**
     * The figure's skin as it was made, in the figure's space: the points the tail's bones carry, by the bone that
     * carries most of each ([of]), and the rest, the [body]. The eyes are left out: they are not the head's shape.
     */
    private class Skin(private val tail: Map<String, List<Vector3>>, val body: List<Vector3>) {

        /** The tail's points that [bone] carries most of. */
        fun of(bone: String): List<Vector3> = tail[bone] ?: emptyList()

        /** The [points] as three numbers each. */
        fun flat(points: List<Vector3>): DoubleArray = DoubleArray(3 * points.size) { k ->
            val p = points[k / 3]

            when (k % 3) {
                0 -> p.x
                1 -> p.y
                else -> p.z
            }
        }

        companion object {
            fun of(rig: Rig, tail: Set<String>): Skin {
                val byBone = mutableMapOf<String, MutableList<Vector3>>()
                val body = mutableListOf<Vector3>()
                val skins = mutableListOf<SkinnedMesh>()

                rig.root.traverse { o ->
                    val mesh = o.asSkinnedMesh ?: return@traverse

                    if (mesh.materials.none { it.name.endsWith("_eyes") }) {
                        skins.add(mesh)
                    }
                }

                for (mesh in skins) {
                    val geometry: dynamic = mesh.asDynamic().geometry
                    val position: dynamic = geometry.attributes.position
                    val index: dynamic = geometry.attributes.skinIndex
                    val weight: dynamic = geometry.attributes.skinWeight
                    val skeleton: dynamic = mesh.asDynamic().skeleton

                    if (index == null || weight == null || skeleton == null) {
                        continue
                    }

                    val count = position.count.unsafeCast<Int>()

                    for (i in 0 until count) {
                        // the point as the figure was made: the bones at rest, the skin where it was bound
                        val p = rig.root.worldToLocal(
                            mesh.localToWorld(
                                vec(
                                    position.getX(i).unsafeCast<Double>(),
                                    position.getY(i).unsafeCast<Double>(),
                                    position.getZ(i).unsafeCast<Double>(),
                                ),
                            ),
                        )
                        var onTail = 0.0
                        var most = -1.0
                        var carrier = ""

                        for (k in 0 until 4) {
                            val w = weight.getComponent(i, k).unsafeCast<Double>()
                            val bone = skeleton.bones[index.getComponent(i, k).unsafeCast<Int>()]
                            val name = (bone?.name as? String) ?: continue

                            if (name in tail) {
                                onTail += w
                            }

                            if (w > most) {
                                most = w
                                carrier = name
                            }
                        }

                        if (onTail >= TAILS && carrier in tail) {
                            byBone.getOrPut(carrier) { mutableListOf() }.add(p)
                        } else if (onTail < TAILS) {
                            body.add(p)
                        }
                    }
                }

                return Skin(tail = byBone, body = body)
            }
        }
    }
}
