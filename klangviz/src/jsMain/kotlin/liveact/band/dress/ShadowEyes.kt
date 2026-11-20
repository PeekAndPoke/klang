/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.dress

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.band.figure.Rig
import io.peekandpoke.klang.viz.liveact.lights.FrontFade
import io.peekandpoke.klang.viz.liveact.lights.FrontLight
import io.peekandpoke.klang.viz.liveact.mat4
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.Blending
import io.peekandpoke.klang.workshop.threejs.Bone
import io.peekandpoke.klang.workshop.threejs.Matrix4
import io.peekandpoke.klang.workshop.threejs.ShaderMaterial
import io.peekandpoke.klang.workshop.threejs.ShaderMaterialParameters
import io.peekandpoke.klang.workshop.threejs.SkinnedMesh
import io.peekandpoke.klang.workshop.threejs.applyMatrix4
import io.peekandpoke.klang.workshop.threejs.asSkinnedMesh
import io.peekandpoke.klang.workshop.threejs.createShaderMaterial
import io.peekandpoke.klang.workshop.threejs.emissive
import io.peekandpoke.klang.workshop.threejs.emissiveIntensity
import io.peekandpoke.klang.workshop.threejs.materials
import io.peekandpoke.klang.workshop.threejs.matrixWorld
import io.peekandpoke.klang.workshop.threejs.name
import io.peekandpoke.klang.workshop.threejs.toneMapped
import io.peekandpoke.klang.workshop.threejs.uniform
import io.peekandpoke.klang.workshop.threejs.uniformsOf
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import io.peekandpoke.kraft.utils.jsObject
import kotlinx.browser.window
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Sirene's eyes in her shadow (`?eyes=shadow`, KLANG-00478; the maintainer, 2026-11-19: "when the front light is on
 * Sirene eyes move to the shadow"): while the low front lamp is on and she looks at the camera
 * ([EyesInShadow.facing]), the eyes of [rig] fade out of her face and two glowing eyes fade in on the surface that
 * takes the band's shadows, in the head of her shadow; when the lamp goes off or she turns away they cross back
 * ([EyesInShadow], about a second either way).
 *
 * The eyes on the surface are where the lamp throws her eyes: from the lamp through each eye as it is now onto the
 * surface, so they move with her as her shadow does, at its scale; as she turns her head they come closer together
 * and narrow, as her shadow's would. They glow in her eyes' own colour and light as they are set this frame (her
 * warmth, her flare, her waver), a soft core and a wider glow round it, and add their light to the surface: no
 * sticker on it. They are drawn first of all that is see-through, so the haze behind the fence veils them as it veils
 * her shadow, and the mirror floor mirrors them as it mirrors the rest.
 *
 * In the underworld (`?underworld=on`) her eyes, while they are in her shadow, also shine in her reflection in the
 * mirror floor: while the mirror draws ([inMirror]) her face's eyes have back as much of their light as the
 * underworld shows.
 *
 * One plane of a few pixels in [scene], hidden while the eyes are in her face. Each frame, after the eyes' glow is
 * set and the lamps are: [update]. Not chosen any more: [rest] (the plane hidden, kept for the next time, her face's
 * eyes hers again from the next frame on, as their glow is set anew every frame). [dispose] takes the plane out of the
 * scene and frees its shape and material.
 */
class ShadowEyes private constructor(
    private val scene: Scene,
    private val rig: Rig,
    skin: SkinnedMesh,
    headIndex: Int,
) {
    private val head: Bone = rig.bone("head")

    /**
     * Each eye's middle in the head bone's own space, where it is however the head is turned; moved [INSET] of the
     * way in toward the head's axis, so that with her head in profile the eyes lie inside her shadow's head and not on
     * its edge (the lamp throws the face's front onto the outline).
     */
    private val left: Vector3
    private val right: Vector3

    /**
     * The middle between her eyes and the place on the head's axis at their height, in the head bone's own space:
     * from the one to the other is the way her face points.
     */
    private val brow: Vector3
    private val axis: Vector3

    /** Whether she looked at the camera at the last [update] (see [EyesInShadow.facing]). */
    private var facing = false

    /** Half an eye's width and half its height, and how far apart the two eyes are, in the world, in metres. */
    private val halfWide: Double
    private val halfHigh: Double
    private val apart: Double

    /** How far the eyes have crossed into her shadow, 0 (in her face) to 1; anew after each [rest]. */
    private var crossing = FrontFade(rise = CROSSING, fall = CROSSING)

    /**
     * Her face's eyes' brightness as the last [update] found it ([faceFull]) and as it left it ([faceShown]): what
     * [inMirror] gives the reflection and takes back. Not while resting ([resting]).
     */
    private var faceFull = 0.0
    private var faceShown = 0.0
    private var resting = true

    /** When the last [update] ran, in milliseconds of the page; none before the first. */
    private var updatedAt = Double.NaN

    // the eyes now, and where the lamp throws them (made once, set every frame)
    private val eyeL = vec()
    private val eyeR = vec()
    private val onL = vec()
    private val onR = vec()
    private val browNow = vec()
    private val axisNow = vec()

    // What the plane draws: its size, how far each eye's middle is from the plane's, an eye's half width and height
    // there, and the eyes' light (colour times brightness)
    private val size = Three.addon.createVector2()
    private val half = uniform(0.0)
    private val eye = Three.addon.createVector2()
    private val glow = vec()

    private val material: ShaderMaterial
    private val mesh: Mesh

    init {
        // the eyes' points as the figure was made, in the eyes' mesh's own space; split into the two by which side of
        // their middle they lie on (x across the figure)
        val position: dynamic = skin.asDynamic().geometry.attributes.position
        val count = position.count.unsafeCast<Int>()
        var middle = 0.0

        for (i in 0 until count) {
            middle += position.getX(i).unsafeCast<Double>()
        }

        middle /= max(1, count)

        val sides = listOf(EyePoints(), EyePoints())

        for (i in 0 until count) {
            val x = position.getX(i).unsafeCast<Double>()
            val y = position.getY(i).unsafeCast<Double>()
            val z = position.getZ(i).unsafeCast<Double>()

            sides[if (x < middle) 0 else 1].add(x = x, y = y, z = z)
        }

        // from the mesh's own space to the head bone's: the eyes go with the head alone, so this holds in every pose
        // (three.js's skinning: the bone's world times its bind inverse times the bind matrix)
        val skeleton: dynamic = skin.asDynamic().skeleton
        val toHead: Matrix4 = mat4()

        toHead.asDynamic().multiplyMatrices(skeleton.boneInverses[headIndex], skin.asDynamic().bindMatrix)
        val atLeft = sides[0].middle().applyMatrix4(toHead)
        val atRight = sides[1].middle().applyMatrix4(toHead)
        // the head's axis is the bone's own y: inward is toward it, at the eyes' height
        val inward = vec(x = -0.5 * (atLeft.x + atRight.x), y = 0.0, z = -0.5 * (atLeft.z + atRight.z)).multiplyScalar(INSET)

        left = atLeft.clone().add(inward)
        right = atRight.clone().add(inward)
        brow = atLeft.clone().add(atRight).multiplyScalar(0.5)
        axis = vec(x = 0.0, y = brow.y, z = 0.0)

        // the sizes in the world: the eyes' mesh may be scaled against it, as far as their distance says
        head.updateWorldMatrix(updateParents = true, updateChildren = false)

        val inMesh = hypot(hypot(sides[1].x - sides[0].x, sides[1].y - sides[0].y), sides[1].z - sides[0].z)
        val worldL = atLeft.clone().applyMatrix4(head.matrixWorld)
        val worldR = atRight.clone().applyMatrix4(head.matrixWorld)
        val toWorld = if (inMesh > 0.0) worldR.sub(worldL).length() / inMesh else 1.0

        apart = inMesh * toWorld
        halfWide = 0.25 * (sides[0].wide + sides[1].wide) * toWorld
        halfHigh = 0.25 * (sides[0].high + sides[1].high) * toWorld

        material = Three.addon.createShaderMaterial(jsObject<ShaderMaterialParameters> {
            uniforms = uniformsOf(
                "uSize" to uniform(size),
                "uHalf" to half,
                "uEye" to uniform(eye),
                "uGlow" to uniform(glow),
            )
            vertexShader = VERTEX
            fragmentShader = FRAGMENT
            transparent = true
            depthWrite = false
            blending = Blending.AdditiveBlending
        })
        // their light goes to the screen as it is, as her own eyes' does (see Rig)
        material.toneMapped = false
        mesh = Three.addon.createMesh(Three.addon.createPlaneGeometry(1.0, 1.0), material)
        mesh.visible = false
        // first of all that is see-through: everything see-through stands before the surface (the haze, the mesh)
        mesh.renderOrder = ORDER
        scene.add(mesh)
    }

    /**
     * The eyes for this frame: the front lamp at [front], standing at [lamp], throws the band's shadows onto the
     * surface at [planeZ] (along z, in metres); the camera stands at [camera] (none: she counts as looking at it).
     * Call it after her eyes' glow is set for the frame: it takes their light from her face as far as they have
     * crossed.
     */
    fun update(front: FrontLight, lamp: Vector3, planeZ: Double, camera: Vector3?) {
        val eyes = rig.eyes ?: return
        val nowMs = window.performance.now()
        val dt = if (updatedAt.isNaN()) 0.0 else ((nowMs - updatedAt) / 1000).coerceIn(0.0, 0.25)

        updatedAt = nowMs

        // the way her face points and the way to the camera, seen from above
        head.updateWorldMatrix(updateParents = true, updateChildren = false)
        browNow.copy(brow).applyMatrix4(head.matrixWorld)
        axisNow.copy(axis).applyMatrix4(head.matrixWorld)
        facing = camera == null || EyesInShadow.facing(
            was = facing,
            faceX = browNow.x - axisNow.x,
            faceZ = browNow.z - axisNow.z,
            toX = camera.x - axisNow.x,
            toZ = camera.z - axisNow.z,
        )

        val share = crossing.follow(target = if (facing) FrontFade.on(front) else 0.0, dt = dt)
        val onWall = EyesInShadow.wall(share)

        // her eyes' light as it is set this frame, before it leaves her face
        glow.set(eyes.emissive.r, eyes.emissive.g, eyes.emissive.b).multiplyScalar(eyes.emissiveIntensity * onWall * GLOW)
        faceFull = eyes.emissiveIntensity
        eyes.emissiveIntensity *= EyesInShadow.face(share)
        faceShown = eyes.emissiveIntensity
        resting = false

        if (onWall <= 0.0) {
            mesh.visible = false

            return
        }

        eyeL.copy(left).applyMatrix4(head.matrixWorld)
        eyeR.copy(right).applyMatrix4(head.matrixWorld)

        val kL = EyesInShadow.scale(lampZ = lamp.z, eyeZ = eyeL.z, planeZ = planeZ)
        val kR = EyesInShadow.scale(lampZ = lamp.z, eyeZ = eyeR.z, planeZ = planeZ)

        if (kL == null || kR == null) {
            mesh.visible = false

            return
        }

        onL.copy(eyeL).sub(lamp).multiplyScalar(kL).add(lamp)
        onR.copy(eyeR).sub(lamp).multiplyScalar(kR).add(lamp)

        val dx = onR.x - onL.x
        val dy = onR.y - onL.y
        val between = hypot(dx, dy)
        val k = 0.5 * (kL + kR)
        // as she turns her head the eyes come closer on the surface, and each narrows as much
        val squeeze = if (apart > 0.0) (between / (k * apart)).coerceIn(SQUEEZE, 1.0) else 1.0
        val reach = REACH * halfWide * k

        eye.set(halfWide * k * squeeze, halfHigh * k)
        half.value = 0.5 * between
        size.set(between + 2 * reach, 2 * reach)
        // a hair before the surface, toward the lamp (it stands before the stage, at the larger z)
        mesh.position.set(0.5 * (onL.x + onR.x), 0.5 * (onL.y + onR.y), planeZ + LIFT)
        mesh.rotation.z = atan2(dy, dx)
        mesh.scale.set(size.x, size.y, 1.0)
        mesh.visible = true
    }

    /**
     * The mirror floor starts drawing ([drawing]) or is done: while it draws, her face's eyes shine with [share] (0 to
     * 1, how far the underworld shows) of the light they gave up to her shadow, so her reflection has them; done, they
     * are as [update] left them. Nothing while resting.
     */
    fun inMirror(drawing: Boolean, share: Double) {
        val eyes = rig.eyes ?: return

        if (resting) {
            return
        }

        val back = share.coerceIn(0.0, 1.0)

        eyes.emissiveIntensity = if (drawing) faceShown + (faceFull - faceShown) * back else faceShown
    }

    /**
     * The eyes are not chosen any more: the plane hidden and kept, the crossing begun anew the next time; her face's
     * eyes are hers again from the next frame on.
     */
    fun rest() {
        mesh.visible = false
        crossing = FrontFade(rise = CROSSING, fall = CROSSING)
        facing = false
        updatedAt = Double.NaN
        resting = true
    }

    /** Takes the plane out of the scene and frees its shape and its material. Call it once. */
    fun dispose() {
        scene.remove(mesh)
        mesh.asDynamic().geometry.dispose()
        material.dispose()
    }

    /** The points of one eye as they are gathered: their sum, and how far they reach across and up. */
    private class EyePoints {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        private var n = 0
        private var minX = Double.MAX_VALUE
        private var maxX = -Double.MAX_VALUE
        private var minY = Double.MAX_VALUE
        private var maxY = -Double.MAX_VALUE

        /** The eye's width and height, in the mesh's own space. */
        val wide: Double get() = max(0.0, maxX - minX)
        val high: Double get() = max(0.0, maxY - minY)

        fun add(x: Double, y: Double, z: Double) {
            n++
            this.x += (x - this.x) / n
            this.y += (y - this.y) / n
            this.z += (z - this.z) / n
            minX = min(minX, x)
            maxX = max(maxX, x)
            minY = min(minY, y)
            maxY = max(maxY, y)
        }

        /** The eye's middle, as a vector. */
        fun middle(): Vector3 = vec(x, y, z)
    }

    companion object {
        /** How long the eyes take to cross, either way, in seconds. */
        const val CROSSING = 1.0

        /** How far in toward the head's axis the eyes are thrown from, as a share of their way there. */
        private const val INSET = 0.45

        /** How brightly the eyes on the surface glow against her own eyes. */
        private const val GLOW = 1.0

        /** How far the glow reaches round each eye, in its half widths. */
        private const val REACH = 4.0

        /** How narrow an eye gets on the surface as she turns her head, at most: this share of its width. */
        private const val SQUEEZE = 0.3

        /** How far before the surface the eyes are drawn, in metres. */
        private const val LIFT = 0.02

        /** Their place among what is drawn see-through: before the haze behind the fence (from some -102 on). */
        private const val ORDER = -200

        /**
         * Sirene's eyes in her shadow, for her [rig] in [scene]; null where the figure has no eyes of their own or
         * no head bone they hang on (every figure of the band has both).
         */
        fun of(scene: Scene, rig: Rig): ShadowEyes? {
            var skin: SkinnedMesh? = null

            rig.root.traverse { o ->
                o.asSkinnedMesh?.let { mesh ->
                    if (mesh.materials.any { it.name.endsWith("_eyes") }) {
                        skin = mesh
                    }
                }
            }

            val found = skin ?: return null
            val bones = found.asDynamic().skeleton?.bones?.unsafeCast<Array<dynamic>>() ?: return null
            val index = bones.indexOfFirst { it.name == "head" }

            if (index < 0 || rig.eyes == null) {
                return null
            }

            return ShadowEyes(scene = scene, rig = rig, skin = found, headIndex = index)
        }

        private val VERTEX = """
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }"""

        /**
         * Two eyes, `uHalf` metres either side of the plane's middle along its width: each a narrow core, soft at
         * its rim, in a rounder glow that fades over some four of its half widths.
         */
        private val FRAGMENT = """
  varying vec2 vUv;
  uniform vec2 uSize;
  uniform float uHalf;
  uniform vec2 uEye;
  uniform vec3 uGlow;

  float eyeAt(vec2 p) {
    float d = length(p / uEye);
    float core = 1.0 - smoothstep(0.55, 1.05, d);
    float r = length(p / vec2(uEye.x, uEye.x * 0.7));
    return core + 0.28 * exp(-r * r * 0.45);
  }

  void main() {
    vec2 p = (vUv - 0.5) * uSize;
    float light = eyeAt(p - vec2(uHalf, 0.0)) + eyeAt(p + vec2(uHalf, 0.0));
    gl_FragColor = vec4(uGlow * light, 1.0);
    #include <colorspace_fragment>
  }"""
    }
}
