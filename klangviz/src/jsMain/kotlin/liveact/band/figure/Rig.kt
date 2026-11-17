/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.figure

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.band.dress.EyeLook
import io.peekandpoke.klang.viz.liveact.quat
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.Bone
import io.peekandpoke.klang.workshop.threejs.Quaternion
import io.peekandpoke.klang.workshop.threejs.SkinnedMesh
import io.peekandpoke.klang.workshop.threejs.addScaledVector
import io.peekandpoke.klang.workshop.threejs.applyQuaternion
import io.peekandpoke.klang.workshop.threejs.asBone
import io.peekandpoke.klang.workshop.threejs.asMeshStandardMaterial
import io.peekandpoke.klang.workshop.threejs.asSkinnedMesh
import io.peekandpoke.klang.workshop.threejs.copied
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.klang.workshop.threejs.createSphere
import io.peekandpoke.klang.workshop.threejs.distanceTo
import io.peekandpoke.klang.workshop.threejs.dot
import io.peekandpoke.klang.workshop.threejs.emissive
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.forceSinglePass
import io.peekandpoke.klang.workshop.threejs.getWorldPosition
import io.peekandpoke.klang.workshop.threejs.getWorldQuaternion
import io.peekandpoke.klang.workshop.threejs.lengthSq
import io.peekandpoke.klang.workshop.threejs.lerp
import io.peekandpoke.klang.workshop.threejs.materials
import io.peekandpoke.klang.workshop.threejs.matrixWorld
import io.peekandpoke.klang.workshop.threejs.name
import io.peekandpoke.klang.workshop.threejs.quaternion
import io.peekandpoke.klang.workshop.threejs.setTo
import io.peekandpoke.klang.workshop.threejs.toneMapped
import io.peekandpoke.klang.workshop.threejs.transformDirection
import io.peekandpoke.klang.workshop.threejs.worldToLocal
import io.peekandpoke.kraft.addons.threejs.js.Color
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A figure's left or right. */
enum class Side(val sign: Double) {
    L(1.0),
    R(-1.0);

    val other: Side get() = if (this == L) R else L
}

/**
 * A figure's rig: its bones by the names of the joints they end at, its measures, and the two-bone reach.
 *
 * Nothing here knows a figure's shape: every length is read from the bones, so a reshaped figure moves the same.
 * A figure's own space has x to its left, y up and z ahead of it, the [root] at the origin.
 */
class Rig(val root: Object3D) {

    /** A bone as it is at rest: its turn and place against its parent, and its place in the world. */
    private class Rest(val q: Quaternion, val p: Vector3, val world: Vector3, val turn: Quaternion)

    /** The bones by name: each is named after the joint it ends at. */
    val bones: Map<String, Bone>

    /** The eyes' material, where the figure has eyes: its glow is the player's to set. */
    val eyes: MeshStandardMaterial?

    /** The eyes' own colour, as the figure was made, and the pale one they have while their player rests. */
    private val eyeColor: Color?
    private val eyePale: Color?

    private val rest: Map<String, Rest>

    /** Where the hips' bone ends at rest, in the figure's space. */
    val pelvis: Vector3

    /** The figure's scale: its leg against the plain one's. Every player's instrument and motion is measured in it. */
    val s: Double

    /** Where each arm starts at rest, in the figure's space (the bone to the elbow starts at the shoulder). */
    val shoulder: Map<Side, Vector3>

    /** From the shoulder to the wrist. */
    val armLength: Double

    /** Where each foot starts at rest, in the figure's space (the bone to the toe starts at the ankle). */
    val ankle: Map<Side, Vector3>

    /** How high the head's bone starts at rest. */
    val height: Double

    /** Which way the face looks, in the head bone's own space. */
    val faceAhead: Vector3

    private companion object {
        const val EYES_OPACITY = 0.95

        /** The figure from its feet to the top of its head, against how high its head's bone starts. */
        const val TALL = 1.2

        /** The sphere a figure's skin is kept within for every pose: so many times the figure's height from its middle. */
        const val REACH = 1.5
    }

    init {
        val found = mutableMapOf<String, Bone>()
        var eyesFound: MeshStandardMaterial? = null
        val skins = mutableListOf<SkinnedMesh>()

        root.traverse { o ->
            o.asBone?.let { found[it.name] = it }

            o.asSkinnedMesh?.let { mesh ->
                val eyes = mesh.materials.filter { it.name.endsWith("_eyes") }

                skins.add(mesh)
                // the eyes throw no shadow: it would fall inside the head's
                // (a figure's eyes are a mesh of their own, as the figures are made: one material to a mesh)
                mesh.castShadow = eyes.isEmpty()

                for (material in eyes) {
                    eyesFound = material.asMeshStandardMaterial
                    // the eyes are a little see-through (the maintainer, 2026-11-16: "say 95% opacity")
                    material.transparent = true
                    material.opacity = EYES_OPACITY
                    // drawn in one go: three.js draws a see-through material that shows both its sides twice
                    material.forceSinglePass = true
                    // the eyes' light goes to the screen as it is: the film's curve, which the rest of the picture
                    // goes through, turns a bright colour pale, and their colour is what they are there for
                    material.toneMapped = false
                }
            }
        }

        bones = found
        eyes = eyesFound
        eyeColor = eyesFound?.emissive?.copied()
        // almost white, with a tint of their own colour
        eyePale = eyeColor?.copied()?.lerp(Three.addon.createColor(0xffffff), 0.82)

        root.updateWorldMatrix(updateParents = true, updateChildren = true)

        val upright = root.getWorldQuaternion(quat()).invert()

        rest = bones.mapValues { (_, bone) ->
            Rest(
                q = bone.quaternion.clone(),
                p = bone.position.clone(),
                world = bone.getWorldPosition(vec()),
                turn = upright.clone().multiply(bone.getWorldQuaternion(quat())),
            )
        }

        fun local(name: String) = root.worldToLocal(rest.getValue(name).world.clone())

        pelvis = restTail("hips")
        s = pelvis.y / 0.92
        shoulder = mapOf(Side.L to local("elbowL"), Side.R to local("elbowR"))
        armLength = local("elbowL").distanceTo(local("wristL")) + local("wristL").distanceTo(local("handL"))
        ankle = mapOf(Side.L to local("toeL"), Side.R to local("toeR"))
        height = local("head").y
        faceAhead = dir(0.0, 0.0, 1.0).applyQuaternion(bone("head").getWorldQuaternion(quat()).invert())

        keepInSight(skins)
    }

    /**
     * Gives each of the figure's [skins] a sphere it is kept within, in its own space: round the figure's middle,
     * [REACH] times its height across from there, so the figure fits in it in every pose (arms raised, reaching for
     * an instrument, sitting, a step to the side). three.js works a skinned mesh's sphere out only while it has none,
     * and then from the pose of that moment; a figure that moves would be left out where it still shows. With this
     * one it is left out only where all of the sphere is out of sight.
     */
    private fun keepInSight(skins: List<SkinnedMesh>) {
        val tall = TALL * height

        for (skin in skins) {
            fun inSkin(x: Double, y: Double, z: Double) = skin.worldToLocal(root.localToWorld(vec(x, y, z)))

            val middle = inSkin(0.0, tall / 2, 0.0)
            val away = REACH * tall
            // the farthest of three points that far off in the figure's measure: the skin's own measure may differ
            val radius = listOf(
                inSkin(away, tall / 2, 0.0),
                inSkin(0.0, tall / 2 + away, 0.0),
                inSkin(0.0, tall / 2, away),
            ).maxOf { it.distanceTo(middle) }

            skin.boundingSphere = Three.addon.createSphere(center = middle, radius = radius)
        }
    }

    /**
     * Colours the eyes by how warm their player is ([warmth], 0 to 1): a pale, almost white light while they rest,
     * their own colour the longer they play (see EyeWarmth). It sets the glow from what the player set this frame,
     * so it runs once a frame, after the players.
     */
    fun warmEyes(warmth: Double) {
        val material = eyes ?: return
        val pale = eyePale ?: return
        val own = eyeColor ?: return
        val look = EyeLook.of(warmth = warmth, set = material.emissiveIntensity)

        material.emissive.setTo(pale).lerp(own, look.own)
        material.emissiveIntensity = look.glow
    }

    /** The bone [name]. It throws where the figure has none of that name: every figure is built on the one skeleton. */
    fun bone(name: String): Bone = bones[name] ?: error("The figure has no bone '$name'")

    /** Where the bone [name] starts just now, in the world. */
    fun at(name: String): Vector3 = bone(name).getWorldPosition(vec())

    /** How the bone [name] is turned against the figure as a whole, at rest. A new one each time. */
    fun restTurn(name: String): Quaternion = rest.getValue(name).turn.clone()

    /** Where the bone [name] starts at rest, in the figure's space. */
    fun restStart(name: String): Vector3 = root.worldToLocal(rest.getValue(name).world.clone())

    /** Where the bone [name] ends at rest, in the figure's space: its first child's start. */
    fun restTail(name: String): Vector3 {
        val child = bone(name).children.firstNotNullOf { it.asBone }

        return root.worldToLocal(rest.getValue(child.name).world.clone())
    }

    /** Back to the rest pose: every frame starts here. */
    fun reset() {
        for ((name, bone) in bones) {
            val r = rest.getValue(name)

            bone.quaternion.copy(r.q)
            bone.position.copy(r.p)
            bone.scale.set(1.0, 1.0, 1.0)
        }
    }

    /** Brings every bone's place in the world up to date. */
    fun update() {
        root.updateWorldMatrix(updateParents = true, updateChildren = true)
    }

    /** Turns the bone [name] by the world rotation [r], where it is. */
    fun turnWorld(name: String, r: Quaternion) {
        val bone = bone(name)
        val now = bone.getWorldQuaternion(quat())
        val parent = bone.parent ?: error("The bone '$name' has no parent")
        val toParent = parent.getWorldQuaternion(quat()).invert()

        bone.quaternion.copy(toParent).multiply(r.clone().multiply(now))
        bone.updateWorldMatrix(updateParents = false, updateChildren = true)
    }

    /** Turns the bone [name] about an axis of the figure (x to its left, y up, z ahead) by [angle]. */
    fun turn(name: String, axis: Vector3, angle: Double) {
        val inWorld = axis.clone().transformDirection(root.matrixWorld)

        turnWorld(name, quat().setFromAxisAngle(inWorld, angle))
    }

    /** Moves the hips by [offset], in the figure's space. */
    fun moveHips(offset: Vector3) {
        val bone = bone("hips")
        val by = offset.clone().transformDirection(root.matrixWorld).multiplyScalar(offset.length())
        val at = bone.getWorldPosition(vec()).add(by)
        val parent = bone.parent ?: error("The hips have no parent")

        parent.worldToLocal(at)
        bone.position.copy(at)
        bone.updateWorldMatrix(updateParents = false, updateChildren = true)
    }

    /**
     * Two bones reach for [target] (world): [upper] starts at the first joint (shoulder, hip), [lower] at the middle
     * one (elbow, knee), [end] at the last (wrist, ankle). [pole] (a world direction) is where the middle joint
     * bends to.
     */
    fun reach(upper: String, lower: String, end: String, target: Vector3, pole: Vector3) {
        val a = at(upper)
        val b = at(lower)
        val c = at(end)
        val lab = a.distanceTo(b)
        val lbc = b.distanceTo(c)
        val dir = target.clone().sub(a)
        val lat = min(max(dir.length(), abs(lab - lbc) + 1e-4), (lab + lbc) * 0.999)

        dir.normalize()

        val cosA = (lab * lab + lat * lat - lbc * lbc) / (2 * lab * lat)
        val sinA = sqrt(max(0.0, 1 - cosA * cosA))
        val perp = pole.clone().addScaledVector(dir, -pole.dot(dir))

        if (perp.lengthSq() < 1e-8) {
            perp.set(0.0, -1.0, 0.0).addScaledVector(dir, dir.y)
        }

        perp.normalize()

        val elbow = a.clone().addScaledVector(dir, lab * cosA).addScaledVector(perp, lab * sinA)
        val reached = a.clone().addScaledVector(dir, lat)

        turnWorld(upper, quat().setFromUnitVectors(b.clone().sub(a).normalize(), elbow.clone().sub(a).normalize()))

        val b2 = at(lower)
        val c2 = at(end)

        turnWorld(lower, quat().setFromUnitVectors(c2.sub(b2).normalize(), reached.sub(b2).normalize()))
    }

    /**
     * Turns the face toward [target] (world), [w] of the way (0 to 1): the neck a little, the head the rest. Never
     * further than a head can go: about 70 degrees to a side, 30 up or down from straight ahead.
     */
    fun faceTo(target: Vector3, w: Double) {
        if (w <= 0) {
            return
        }

        val head = bone("head")
        val from = head.getWorldPosition(vec())
        val local = root.worldToLocal(target.clone()).sub(root.worldToLocal(from.clone()))
        val yaw = max(-1.25, min(1.25, atan2(local.x, local.z)))
        val pitch = max(-0.5, min(0.5, atan2(local.y, hypot(local.x, local.z))))
        val want = dir(sin(yaw) * cos(pitch), sin(pitch), cos(yaw) * cos(pitch))

        for ((name, share) in listOf("neck" to 0.3 * w, "head" to w)) {
            val now = faceAhead.clone().applyQuaternion(head.getWorldQuaternion(quat()))
            val turn = quat().setFromUnitVectors(now, want)

            turnWorld(name, quat().slerp(turn, share))
        }
    }

    /**
     * Breathing: the belly goes out and in, the chest a little with it ([breath] 0 breathed out to 1 breathed in,
     * [depth] how far the belly goes, as a share of its girth). A bone is made wider across (its own y runs along
     * it), and the bones that hang on it are made narrower by as much, so only this part of the body swells. Call
     * it last in a frame: the other moves read the bones' turns, which a width would blur.
     */
    fun breathe(breath: Double, depth: Double) {
        val belly = 1 + depth * breath
        val chest = 1 + 0.4 * depth * breath

        bone("belly").scale.set(belly, 1.0, belly)
        bone("chest").scale.set(chest / belly, 1.0, chest / belly)

        for (child in bone("chest").children) {
            child.asBone?.scale?.set(1 / chest, 1.0, 1 / chest)
        }
    }

    /** Which way the bone [name] points (its own y axis), in the world. */
    fun axisOf(name: String): Vector3 = vec(0.0, 1.0, 0.0).applyQuaternion(bone(name).getWorldQuaternion(quat()))

    /** Points the bone [name] along [d] (world). */
    fun point(name: String, d: Vector3) {
        turnWorld(name, quat().setFromUnitVectors(axisOf(name), d.clone().normalize()))
    }

    /** A direction of the figure's space in the world, of length 1. */
    fun dir(x: Double, y: Double, z: Double): Vector3 = vec(x, y, z).transformDirection(root.matrixWorld)

    /** The arm [side] reaches for [target] with its wrist; the elbow bends toward [pole]. */
    fun hand(side: Side, target: Vector3, pole: Vector3) {
        reach(upper = "elbow$side", lower = "wrist$side", end = "hand$side", target = target, pole = pole)
    }

    /** The leg [side] reaches for [target] with its ankle; the knee bends toward [pole]. */
    fun foot(side: Side, target: Vector3, pole: Vector3) {
        reach(upper = "knee$side", lower = "ankle$side", end = "toe$side", target = target, pole = pole)
    }
}
