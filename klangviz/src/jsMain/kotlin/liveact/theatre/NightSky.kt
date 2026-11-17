/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.theatre

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.mat4
import io.peekandpoke.klang.viz.liveact.stage.Stage
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.Blending
import io.peekandpoke.klang.workshop.threejs.ShaderMaterialParameters
import io.peekandpoke.klang.workshop.threejs.Uniforms
import io.peekandpoke.klang.workshop.threejs.createBufferGeometry
import io.peekandpoke.klang.workshop.threejs.createFloat32Attribute
import io.peekandpoke.klang.workshop.threejs.createPoints
import io.peekandpoke.klang.workshop.threejs.createShaderMaterial
import io.peekandpoke.klang.workshop.threejs.crossVectors
import io.peekandpoke.klang.workshop.threejs.frustumCulled
import io.peekandpoke.klang.workshop.threejs.lerp
import io.peekandpoke.klang.workshop.threejs.negate
import io.peekandpoke.klang.workshop.threejs.quaternion
import io.peekandpoke.klang.workshop.threejs.setAttribute
import io.peekandpoke.klang.workshop.threejs.uniform
import io.peekandpoke.klang.workshop.threejs.uniformsOf
import io.peekandpoke.kraft.addons.threejs.js.Group
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.PerspectiveCamera
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.addons.threejs.js.Vector3
import io.peekandpoke.kraft.utils.jsObject
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tan

// The night sky over the open-air theatre: stars, a moon low over the audience with thin clouds passing it, and a
// shooting star now and then (where and when: Sky). It gives the dark a place: the theatre stands under a sky.
//
// It is far away, so it goes with the camera and never comes nearer; and it is cheap: the stars are points drawn
// once, the moon and its clouds are worked out only on the small patch of sky round the moon, the shooting star is
// two triangles that are there for a second in a minute and a half. Everything nearer hides it.

/** Adds the night sky to [scene]. Each frame: [update], and [follow] once the camera is placed. */
class NightSky(scene: Scene) {

    private val sky: Group = Three.addon.createGroup()
    private val time = uniform(0.0)
    private val size = uniform(2.0)
    private val glow = uniform(0.0)
    private val streak: Mesh

    init {
        scene.add(sky)

        // the stars
        val stars = Sky.stars
        val geometry = Three.addon.createBufferGeometry()

        geometry.setAttribute(
            "position",
            Three.addon.createFloat32Attribute(stars.flatMap { listOf(it.at.x * FAR, it.at.y * FAR, it.at.z * FAR) }.toTypedArray(), 3),
        )
        geometry.setAttribute("seed", Three.addon.createFloat32Attribute(stars.map { it.seed }.toTypedArray(), 1))

        val points = Three.addon.createPoints(
            geometry,
            material(vertex = STAR_VERTEX, fragment = STAR_FRAGMENT, uniforms = uniformsOf("uTime" to time, "uSize" to size)),
        )

        points.frustumCulled = false
        sky.add(points)

        // the moon and the clouds that pass it: one patch of sky, turned to whoever looks
        val patch = 2 * FAR * tan(PATCH)
        val moon = Three.addon.createMesh(
            Three.addon.createPlaneGeometry(patch, patch),
            material(vertex = PATCH_VERTEX, fragment = MOON_FRAGMENT, uniforms = uniformsOf("uTime" to time)),
        )

        moon.position.set(Sky.MOON.x * FAR, Sky.MOON.y * FAR, Sky.MOON.z * FAR)
        moon.lookAt(0.0, 0.0, 0.0)
        sky.add(moon)

        // the shooting star: a thin streak, set where it is whenever one falls
        streak = Three.addon.createMesh(
            Three.addon.createPlaneGeometry(1.0, 1.0),
            material(vertex = PATCH_VERTEX, fragment = STREAK_FRAGMENT, uniforms = uniformsOf("uGlow" to glow)),
        )
        streak.visible = false
        sky.add(streak)
    }

    /**
     * Puts the sky round [camera]: it goes with whoever looks, a sky is never nearer from anywhere. Call it once the
     * camera stands where this frame is seen from: a sky left where the camera was a frame ago is off at every cut.
     */
    fun follow(camera: PerspectiveCamera) {
        sky.position.copy(camera.position)
    }

    /** The sky at [t] (seconds); [ratio]: how many of the screen's points make one of the page's. */
    fun update(t: Double, ratio: Double) {
        time.value = t
        // a star is a point, not a disc: a good point of the page wide, whatever the picture's size (sized by the
        // picture's height they were circles on a large screen: the maintainer, 2026-11-17)
        size.value = ratio * 1.25

        val falling = Sky.shootingStar(t)

        streak.visible = falling != null

        if (falling != null) {
            val from = vec(falling.from.x, falling.from.y, falling.from.z)
            val to = vec(falling.to.x, falling.to.y, falling.to.z)
            // its head is where it has got to, its tail a stretch behind
            val head = from.clone().lerp(to, falling.share).normalize()
            val tail = from.clone().lerp(to, falling.share - TAIL).normalize()
            val along = head.clone().sub(tail).normalize()
            val toward = head.clone().add(tail).normalize().negate()
            val across = vec().crossVectors(toward, along).normalize()

            streak.position.copy(head).add(tail).multiplyScalar(FAR / 2)
            streak.quaternion.setFromRotationMatrix(mat4().makeBasis(along, across, vec().crossVectors(along, across)))
            streak.scale.set(head.distanceTo(tail) * FAR, THIN * FAR, 1.0)
            // it lights up and burns out
            glow.value = sin(PI * falling.share)
        }
    }

    private fun material(vertex: String, fragment: String, uniforms: Uniforms) =
        Three.addon.createShaderMaterial(jsObject<ShaderMaterialParameters> {
            this.uniforms = uniforms
            vertexShader = vertex
            fragmentShader = fragment
            transparent = true
            depthWrite = false
            blending = Blending.AdditiveBlending
        })

    private fun Vector3.distanceTo(other: Vector3): Double = clone().sub(other).length()

    private companion object {
        /**
         * How far off the sky is drawn, in metres: within what the camera sees, with room for the moon's patch,
         * whose corners lie a little further; beyond everything else.
         */
        const val FAR = Stage.FAR - 10.0

        /** Half the width of the patch of sky round the moon, in radians: room for its glow and the clouds. */
        const val PATCH = 0.16

        /** How long a shooting star's tail is, as a share of its whole way, and how thin it is against the sky's distance. */
        const val TAIL = 0.35
        const val THIN = 0.0016

        val STAR_VERTEX = """
  attribute float seed;
  uniform float uTime, uSize;
  varying float vGlow;
  varying vec3 vTint;
  void main() {
    // most are faint and small, a few are bright; each twinkles at its own pace, two waves that now and then fall
    // together, so it flickers unevenly as a star does through the air; near the horizon they dim
    float big = pow(fract(seed * 91.0), 3.0);
    float pace = 2.2 + 4.5 * fract(seed * 37.0);
    float twinkle = 0.62 + 0.26 * sin(uTime * pace + seed * 61.0) + 0.12 * sin(uTime * pace * 2.7 + seed * 113.0);
    float haze = smoothstep(0.03, 0.30, normalize(position).y);
    vGlow = (0.42 + 0.58 * big) * twinkle * (0.25 + 0.75 * haze);
    // white, some a little blue, some a little warm
    vTint = mix(vec3(0.80, 0.88, 1.0), vec3(1.0, 0.90, 0.78), fract(seed * 53.0));
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
    gl_PointSize = uSize * (1.0 + 1.3 * big);
  }"""

        val STAR_FRAGMENT = """
  varying float vGlow;
  varying vec3 vTint;
  void main() {
    // a sharp middle that falls off at once: a point of light, not a disc
    float d = length(gl_PointCoord - 0.5) * 2.0;
    float a = 1.0 - smoothstep(0.0, 1.0, d);
    gl_FragColor = vec4(vTint * a * a * vGlow * 2.4, 1.0);
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }"""

        val PATCH_VERTEX = """
  varying vec2 vAt;
  void main() {
    vAt = uv * 2.0 - 1.0;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }"""

        // The moon: a pale disc with faint seas and a soft glow round it. The clouds: thin wisps drifting across the
        // patch, two layers of soft noise; where one passes the moon it dims the disc and takes its light. All of it
        // fades out to the patch's edge, so the patch itself is never seen.
        val MOON_FRAGMENT = """
  uniform float uTime;
  varying vec2 vAt;
  float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
  float soft(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
  }
  void main() {
    float r = length(vAt);
    float disc = 1.0 - smoothstep(0.150, 0.165, r);
    float seas = 0.78 + 0.22 * soft(vAt * 14.0 + 3.0);
    float halo = 0.30 * exp(-r * 7.0) + 0.04 * exp(-r * 2.6);
    // wisps: long across, thin up and down, drifting slowly to one side
    vec2 q = vAt * vec2(1.6, 4.2) + vec2(uTime * 0.020, 0.0);
    float wisp = 0.62 * soft(q) + 0.38 * soft(q * 2.3 + vec2(uTime * 0.013, 4.0));
    float cloud = smoothstep(0.50, 0.82, wisp);
    float edge = 1.0 - smoothstep(0.30, 0.95, r);
    vec3 moon = vec3(0.92, 0.94, 1.0) * (disc * seas * 0.52 * (1.0 - 0.75 * cloud) + halo * (1.0 - 0.45 * cloud));
    vec3 lit = vec3(0.62, 0.68, 0.85) * cloud * (0.035 + 0.30 * exp(-r * 3.2));
    gl_FragColor = vec4((moon + lit) * edge, 1.0);
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }"""

        val STREAK_FRAGMENT = """
  uniform float uGlow;
  varying vec2 vAt;
  void main() {
    // bright at its head (the patch's far end along its way), fading down its tail; thin, soft at its sides
    float along = vAt.x * 0.5 + 0.5;
    float a = along * along * (1.0 - smoothstep(0.0, 1.0, abs(vAt.y))) * uGlow;
    gl_FragColor = vec4(vec3(0.95, 0.97, 1.0) * a * 0.9, 1.0);
    #include <tonemapping_fragment>
    #include <colorspace_fragment>
  }"""
    }
}
