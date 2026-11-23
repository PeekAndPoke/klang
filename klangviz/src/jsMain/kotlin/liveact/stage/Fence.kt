/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.lights.Lamp
import io.peekandpoke.klang.viz.liveact.lights.WorldLight
import io.peekandpoke.klang.viz.liveact.lights.WorldTime
import io.peekandpoke.klang.viz.liveact.mat4
import io.peekandpoke.klang.viz.liveact.quat
import io.peekandpoke.klang.viz.liveact.toFixed
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.CompiledShader
import io.peekandpoke.klang.workshop.threejs.Uniform
import io.peekandpoke.klang.workshop.threejs.createCylinderGeometry
import io.peekandpoke.klang.workshop.threejs.createInstancedMesh
import io.peekandpoke.klang.workshop.threejs.fog
import io.peekandpoke.klang.workshop.threejs.onBeforeCompile
import io.peekandpoke.klang.workshop.threejs.scale
import io.peekandpoke.klang.workshop.threejs.set
import io.peekandpoke.klang.workshop.threejs.uniform
import io.peekandpoke.kraft.addons.threejs.js.Color
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.utils.jsObject
import kotlin.math.PI

/**
 * The back of the stage as a fence (`?stage=mesh`, [StageLook.Mesh]), built into [scene]: in place of the wall, a
 * chain-link wire mesh of dark bronze between dark steel posts, and some 2.5 m behind it a dark surface. Between the
 * two the haze stands ([FogBed.BehindMesh]), and on the surface, low behind the drummer, the sun glows (its colour
 * and brightness each frame: [light]).
 *
 * The front lamp lights both the wires and the surface behind, and the band's big shadows land on both: on the
 * surface, seen through the gaps, as a large dark shape (the alter egos), and on the wires in front of it, a little
 * smaller. The surface is black unless the front lamp is on, as the wall is.
 *
 * The two worlds (`?world=night`, [WorldLook]): the sun's size and height follow the time of day ([light]), and by
 * night and at ember the band's shadows on the surface behind become windows into a cold, moonlit world beyond it
 * ([beyond]): inside each shadow the surface shows a far land of hills under a night sky, seen with depth (it moves
 * against the surface as the camera moves); outside the shadows the surface stays as it is, and by night the lamps'
 * light on it, and half as much on the wires, leans cold and dims ([world], [WallLean]). In the ghosts the shadows are
 * pale figures in the ghosts' colour, there all the time, light standing in the haze with soft edges ([GHOST]), and
 * the surface around them glows in its deep ([world]).
 *
 * Hidden and shown again with [show] (the look turned over to the wall and back while the show plays: kept, so the
 * turn back costs no new materials). Everything it makes is in the scene and is freed with it.
 */
class Fence(scene: Scene) {
    /** The wire mesh, the posts and the surface behind. */
    private val parts: List<Object3D>

    /** The surface behind the mesh: black unless the front lamp is on. */
    private val backMaterial: MeshStandardMaterial

    /** The sun's glow as the surface and the wires take it: its colour times its brightness. */
    private val glow = vec()

    /** Where the sun's middle is on the surface behind (x, y), and how large its glow is against the size as made. */
    private val sunAt = Three.addon.createVector2(x = SUN_X, y = SUN_Y)
    private val sunSize = uniform(1.0)

    // The cold world through the band's shadows (see beyond): the front lamp's map of what it lights, where a place of
    // the world lands on it, where the lamp stands, and how much of the cold world shows (0: none, the surface as made)
    private val frontShadow: Uniform<dynamic> = uniform(null)
    private val frontShadowMatrix: Uniform<dynamic> = uniform(mat4())
    private val frontLamp = vec()
    private val beyondShare = uniform(0.0)

    /**
     * The ghosts ([world], [WorldLight.ghostShare]): how far the band's shadows show the ghosts' pale light ([GHOST])
     * in place of the cold world's windows, that light, and the deep the surface behind sends of its own.
     */
    private val ghostShare = uniform(0.0)
    private val ghostPale = vec()
    private val ghostDepth = vec()

    /** The lamps' light on the surface behind and on the wires at the time of day ([world]). */
    private val backLean = WallLean()
    private val wireLean = WallLean()

    init {
        val glowUniform = uniform(glow)
        val sunAtUniform = uniform(sunAt)

        // the wire mesh: one plane, the wires worked out on it, the gaps between them see-through
        val meshMaterial = wireMaterial()

        meshMaterial.onBeforeCompile("fence-chain-link") { shader: CompiledShader ->
            shader.uniforms["uSunGlow"] = glowUniform
            shader.uniforms["uSunAt"] = sunAtUniform
            shader.uniforms["uSunSize"] = sunSize
            shader.vertexShader = shader.vertexShader.withWorld()
            wireLean.into(shader)
            shader.fragmentShader = shader.fragmentShader
                .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vWorld;\n$SUN_GLOW\n$CHAIN_LINK")
                .replaceFirst(
                    "#include <color_fragment>",
                    "#include <color_fragment>\nfloat wCover, wRim, wShade; vec3 wNormal;\n" +
                        "chainLink(vWorld.xy, wCover, wNormal, wRim, wShade);\n" +
                        "diffuseColor.rgb *= wShade;\ndiffuseColor.a *= wCover;\nif (diffuseColor.a < 0.004) discard;",
                )
                .replaceFirst(
                    "#include <normal_fragment_maps>",
                    "#include <normal_fragment_maps>\nnormal = normalize((viewMatrix * vec4(wNormal, 0.0)).xyz);",
                )
                .replaceFirst(
                    "#include <emissivemap_fragment>",
                    // the glow behind catches the wires' edges, the more the nearer to the sun
                    "#include <emissivemap_fragment>\ntotalEmissiveRadiance += uSunGlow * sunGlow(vWorld.xy) * 0.22 * wRim;",
                )
        }
        val mesh = Three.addon.createMesh(Three.addon.createPlaneGeometry(WIDE, HIGH), meshMaterial)
        mesh.position.set(0.0, HIGH / 2, MESH_Z)
        mesh.receiveShadow = true
        // after the haze behind it, before everything else see-through, which all stands in front of it
        mesh.renderOrder = ORDER
        scene.add(mesh)

        // the posts the mesh hangs on, a little in front of it; the front lamp throws their shadows onto the back
        val steel = postMaterial()
        val post = Three.addon.createCylinderGeometry(
            radiusTop = POST_RADIUS,
            radiusBottom = POST_RADIUS,
            height = HIGH,
            radialSegments = 12,
            heightSegments = 1,
            openEnded = true,
            thetaStart = 0.0,
            thetaLength = 2 * PI,
        )
        val posts = Three.addon.createInstancedMesh(geometry = post, material = steel, count = POSTS)
        val upright = quat()
        val asBuilt = vec(1.0, 1.0, 1.0)

        for (i in 0 until POSTS) {
            // none right behind the drummer: the two nearest stand either side of the sun
            val x = (i - (POSTS - 1) / 2.0) * POST_EVERY

            posts.setMatrixAt(i, mat4().compose(position = vec(x, HIGH / 2, MESH_Z + POST_RADIUS + 0.01), quaternion = upright, scale = asBuilt))
        }

        posts.castShadow = true
        posts.receiveShadow = true
        scene.add(posts)

        // the surface behind: dark, stained, the sun glowing on it low behind the drummer
        backMaterial = surfaceMaterial()
        backMaterial.onBeforeCompile("fence-back-stains-sun-beyond") { shader: CompiledShader ->
            shader.uniforms["uSunGlow"] = glowUniform
            shader.uniforms["uSunAt"] = sunAtUniform
            shader.uniforms["uSunSize"] = sunSize
            shader.uniforms["uFrontShadow"] = frontShadow
            shader.uniforms["uFrontShadowMatrix"] = frontShadowMatrix
            shader.uniforms["uFrontLamp"] = uniform(frontLamp)
            shader.uniforms["uBeyond"] = beyondShare
            shader.uniforms["uGhost"] = ghostShare
            shader.uniforms["uGhostPale"] = uniform(ghostPale)
            shader.uniforms["uGhostDepth"] = uniform(ghostDepth)
            shader.vertexShader = shader.vertexShader.withWorld()
            backLean.into(shader)
            shader.fragmentShader = shader.fragmentShader
                .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vWorld;\n$SUN_GLOW\n$STAINS\n$BEYOND\n$GHOST")
                .replaceFirst(
                    "#include <color_fragment>",
                    "#include <color_fragment>\nfloat stain = stains(vWorld.xy);\ndiffuseColor.rgb *= 0.55 + 0.6 * stain;",
                )
                .replaceFirst(
                    "#include <emissivemap_fragment>",
                    "#include <emissivemap_fragment>\ntotalEmissiveRadiance += uSunGlow * sunGlow(vWorld.xy) * (0.8 + 0.4 * stain);\n" +
                        "totalEmissiveRadiance += uGhostDepth * (0.7 + 0.6 * stain);\n" +
                        "if (uBeyond > 0.0) {\n" +
                        "  if (uGhost < 1.0) {\n" +
                        "    totalEmissiveRadiance += uBeyond * windowAt(vWorld) * beyond(vWorld) * (1.0 - uGhost);\n" +
                        "  }\n" +
                        "  if (uGhost > 0.0) {\n" +
                        "    totalEmissiveRadiance += uBeyond * uGhost * ghostAt(vWorld, stain);\n" +
                        "  }\n" +
                        "}",
                )
        }
        val back = Three.addon.createMesh(Three.addon.createPlaneGeometry(WIDE, HIGH + 2.0), backMaterial)
        back.position.set(0.0, (HIGH + 2.0) / 2, BACK_Z)
        back.receiveShadow = true
        scene.add(back)

        parts = listOf(mesh, posts, back)
    }

    /** Shows the fence ([visible]) or hides it, with all it holds kept: hidden, it is neither drawn nor casts. */
    fun show(visible: Boolean) {
        for (part in parts) {
            part.visible = visible
        }
    }

    /**
     * The lamps' light on the surface behind at the time of day [world] ([WallLean]): by night cold and dimmer; on
     * the wires half as much. In the ghosts ([WorldLight.ghostShare]) the windows show the ghosts' pale light in place
     * of the cold world, and the surface around them glows faintly in the colour's deep, at [level] (0 to 1: the
     * stage's light coming up in a show that opens, from pitch black; 1 otherwise; the windows take it with their share,
     * [beyond]).
     */
    fun world(world: WorldTime, level: Double = 1.0) {
        backLean.world(world)
        wireLean.world(world = world, share = WIRE_LEAN)
        ghostShare.value = WorldLight.ghostShare(world)

        val (pr, pg, pb) = WorldLight.ghostPale(world)
        val (dr, dg, db) = WorldLight.ghostDepth(world)

        ghostPale.set(pr, pg, pb)
        ghostDepth.set(dr * level, dg * level, db * level)
    }

    /**
     * Sets the fence for this frame: the front lamp at [front] (0 to 1) lights the surface behind, and the sun glows
     * in [sun] at [level] (from 0), [size] times as large as made, its middle [height] metres up the surface.
     */
    fun light(front: Double, sun: Color, level: Double, size: Double = 1.0, height: Double = SUN_Y) {
        backMaterial.color.setHex(LIT).scale(front)
        glow.set(sun.r, sun.g, sun.b).multiplyScalar(level * GLOW)
        sunSize.value = size
        sunAt.set(SUN_X, height)
    }

    /**
     * How much of the cold world shows through the band's shadows this frame: [share] from 0 (none, the surface as
     * made) to 1, where the front lamp [front] throws them, by its shadow map as the frame before drew it. None where
     * the lamp has no map yet (it is made at the first frame it casts).
     */
    fun beyond(front: Lamp, share: Double) {
        val shadow: dynamic = front.light.asDynamic().shadow
        val depth: dynamic = shadow?.map?.depthTexture

        if (depth == null) {
            // as the haze does (GroundFog.clearings): no freed map is kept, and the cold world waits for the new one
            frontShadow.value = null
            beyondShare.value = 0.0

            return
        }

        // the lamp's map is handed on whether the cold world shows or not: an empty shadow sampler disturbed the
        // lamp's own map in the haze (GroundFog.clearings), so it is empty for a frame at most here too
        frontShadow.value = depth
        frontShadowMatrix.value = shadow.matrix
        frontLamp.copy(front.light.position)
        beyondShare.value = if (share > 0.0) share else 0.0
    }

    companion object {
        /** Where the mesh hangs (the wall's place) and where the surface behind it stands, in metres along z. */
        const val MESH_Z = -8.0
        const val BACK_Z = -10.5

        /** The sun's middle on the surface behind: behind the drummer, low (his head is at some 1.9 m). */
        const val SUN_X = 0.0
        const val SUN_Y = 1.3

        /** The mesh's place among what is drawn see-through: after the haze, before all the rest. */
        const val ORDER = -50

        /** How much of the night's lean the wires take ([world]). */
        private const val WIRE_LEAN = 0.5

        /** How bright the sun glows on the surface at its middle, at its full brightness. */
        private const val GLOW = 0.5

        /**
         * How far toward the front lamp a place of the surface behind is tested for the band's shadow, in metres: past
         * the fence's posts (some 2.5 m before the surface), short of the riser's back and the band (7 m and more).
         */
        private const val BAND_GAP = 4.0

        /** How far around that place the band's shadow is read as well, in metres: the window's soft edge. */
        private const val WINDOW_EDGE = 0.05

        /**
         * How many readings of the front lamp's map each ring of a ghost takes ([GHOST]): its body and its aura, each
         * this many.
         */
        private const val GHOST_TAPS = 16

        /**
         * How far around the place the ghost's body is read, in metres at the place the map is read ([BAND_GAP] toward
         * the lamp; on the surface some 1.3 times as far): its edge, soft over a few centimetres.
         */
        private const val GHOST_BODY = 0.035

        /**
         * How far around the place the ghost's aura is read, in metres as [GHOST_BODY]: how far inside a ghost is
         * brightest (its thick parts more than its thin arms) and how far its glow reaches out into the haze.
         */
        private const val GHOST_AURA = 0.32

        /** The mesh and the surface: as wide as the wall was, and as high. */
        private const val WIDE = 60.0
        private const val HIGH = 16.0

        /** The posts: how many, how far apart, how thick. */
        // (twice as wide as they were, 3.2 m: Sirene's eyes in her ghost stood right behind a post, at her place in
        // Der Schmetterling and where she sits in Kokon. Now the two posts nearest the middle stand 3.2 m to either
        // side of it, and her ghost's head stays between them all through a visit, as the total sees it; the
        // maintainer, 2026-11-23: "we need to make the fence segments wider")
        private const val POSTS = 10
        internal const val POST_EVERY = 6.4

        /** How much wider the front lamp's cone covers the surface behind when it has crept for the ghosts, in metres. */
        internal const val GHOST_WIDER = 3.2
        internal const val POST_RADIUS = 0.045

        /** The surface behind's colour where a lamp is on in full ([light]): the lamp's light makes it warm or cold. */
        internal const val LIT = 0x8a8f9c

        /**
         * The wires' material before the chain link goes into its shader ([CHAIN_LINK]): dark bronze, polished metal,
         * see-through where the wires leave gaps; no distance fog. Also the panels of "Meet the Band" (`HexFence`).
         */
        internal fun wireMaterial(): MeshStandardMaterial {
            val material = Three.addon.createMeshStandardMaterial(jsObject {
                color = 0x5a4430
                roughness = 0.34
                metalness = 0.75
                transparent = true
            })

            material.fog = false

            return material
        }

        /** The posts' material: dark steel, no distance fog. */
        internal fun postMaterial(): MeshStandardMaterial {
            val material = Three.addon.createMeshStandardMaterial(jsObject {
                color = 0x2a2724
                roughness = 0.45
                metalness = 0.7
            })

            material.fog = false

            return material
        }

        /** The surface behind's material: black until a lamp's light is set on it ([LIT]), rough; no distance fog. */
        internal fun surfaceMaterial(): MeshStandardMaterial {
            val material = Three.addon.createMeshStandardMaterial(jsObject {
                color = 0x000000
                roughness = 0.9
            })

            material.fog = false

            return material
        }

        /** A vertex shader that also hands on where each point is in the world (`vWorld`). */
        private fun String.withWorld(): String = this
            .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vWorld;")
            .replaceFirst("#include <begin_vertex>", "#include <begin_vertex>\nvWorld = (modelMatrix * vec4(transformed, 1.0)).xyz;")

        /**
         * The sun on the surface at a place (x, y): a close glow, a wider one and a faint wide one, a little flatter
         * than round, its middle at about 1 at its brightest; its middle at `uSunAt`, `uSunSize` times as large as made.
         */
        internal val SUN_GLOW = """
uniform vec3 uSunGlow;
uniform vec2 uSunAt;
uniform float uSunSize;
float sunGlow(vec2 p) {
  vec2 d = (p - uSunAt) * vec2(1.0, 1.1) / uSunSize;
  float r2 = dot(d, d);
  return 0.75 * exp(-r2 / 1.0) + 0.22 * exp(-r2 / 5.0) + 0.04 * exp(-r2 / 30.0);
}"""

        /**
         * The chain link, at a place (x, y) on the mesh in metres: two families of round wires, diagonal, crossing in
         * diamonds 22 cm wide and 26 cm high, 1.5 cm thick. Gives how much of the pixel the wires cover (thin wires
         * spread over a pixel, and far off, where the diamonds come down to a few pixels, the mean of them, so they
         * do not shimmer), the wire's normal in the world (round across it; along it, each piece between two
         * crossings bows a little out and in, as the woven wire does), how near its edge the pixel is, and a dirt of
         * its own for each diamond.
         */
        internal val CHAIN_LINK = """
const vec2 CELL = vec2(0.22, 0.26);
const float WIRE = 0.0075;
void chainLink(vec2 p, out float cover, out vec3 n, out float rim, out float shade) {
  vec2 a = p / CELL;
  float u = a.x + a.y, v = a.x - a.y;
  // how far apart two wires of a family are, crosswise, is 1 / g metres
  float g = length(1.0 / CELL);
  float du = (u - floor(u + 0.5)) / g, dv = (v - floor(v + 0.5)) / g;
  float px = max(length(fwidth(p)), 1e-5);
  float w = 2.0 * WIRE, wide = max(w, px);
  float cu = clamp((0.5 * wide - abs(du)) / px + 0.5, 0.0, 1.0) * (w / wide);
  float cv = clamp((0.5 * wide - abs(dv)) / px + 0.5, 0.0, 1.0) * (w / wide);
  float near = 1.0 - (1.0 - cu) * (1.0 - cv);
  float mean = 1.0 - (1.0 - w * g) * (1.0 - w * g);
  cover = mix(near, mean, smoothstep(0.12, 0.35, px * g));
  // the nearer wire: across it and along it, in the world
  bool first = abs(du) < abs(dv);
  float s = clamp((first ? du : dv) / WIRE, -1.0, 1.0);
  vec3 across = normalize(vec3(1.0 / CELL.x, first ? 1.0 / CELL.y : -1.0 / CELL.y, 0.0));
  vec3 along = vec3(-across.y, across.x, 0.0);
  float runs = first ? v : u;
  float side = mod(floor(runs) + floor((first ? u : v) + 0.5), 2.0) * 2.0 - 1.0;
  float bow = 0.9 * (fract(runs) - 0.5) * side;
  n = normalize(vec3(0.0, 0.0, 1.0) * sqrt(1.0 - s * s) + across * s + along * bow);
  rim = s * s;
  // each diamond a little dirtier or cleaner than the next
  shade = 0.7 + 0.6 * fract(sin(dot(floor(vec2(u, v)), vec2(12.9898, 78.233))) * 43758.5453);
}"""

        /**
         * The cold world beyond the surface, for a place of it (in the world): `windowAt` is how much the place lies in
         * the band's shadow from the front lamp, 0 to 1, read from the lamp's map [BAND_GAP] m toward the lamp (so the
         * fence's posts open no window) and [WINDOW_EDGE] m around (a soft edge of a few millimetres on the surface);
         * six readings of the map, only where the cold world shows at all (the ghosts read it their own way, [GHOST]).
         * `beyond` is what the window shows, the light it sends: a night sky, silver blue at the horizon and deep blue higher up, with the glow of a moon high
         * to the left; far hills, 60 m behind the surface, dark blue, their ridges caught by the moon, mist lying in
         * the valley before them; nearer hills with a few bare trees, 18 m behind, nearly black. Where the ray from the camera through the place
         * meets each layer gives the depth: the land moves against the surface as the camera moves.
         */
        private val BEYOND = """
uniform highp sampler2DShadow uFrontShadow;
uniform mat4 uFrontShadowMatrix;
uniform vec3 uFrontLamp;
uniform float uBeyond;
uniform float uGhost;
uniform vec3 uGhostPale;
uniform vec3 uGhostDepth;

float lampHeldBack(vec3 at) {
  vec4 c = uFrontShadowMatrix * vec4(at, 1.0);
  if (c.w <= 0.0) return 0.0;
  c.xyz /= c.w;
  if (c.x < 0.0 || c.x > 1.0 || c.y < 0.0 || c.y > 1.0 || c.z > 1.0) return 0.0;
  return 1.0 - texture(uFrontShadow, c.xyz);
}

float windowAt(vec3 world) {
  vec3 toLamp = normalize(uFrontLamp - world);
  vec3 near = world + toLamp * ${BAND_GAP.toFixed(3)};
  vec3 across = normalize(cross(toLamp, vec3(0.0, 1.0, 0.0))) * ${WINDOW_EDGE.toFixed(3)};
  vec3 up = normalize(cross(across, toLamp)) * ${WINDOW_EDGE.toFixed(3)};
  float held = 2.0 * lampHeldBack(near) + lampHeldBack(near + across) + lampHeldBack(near - across) +
    lampHeldBack(near + up) + lampHeldBack(near - up);
  return smoothstep(0.15, 0.85, held / 6.0);
}

// a ridge of hills along x: its height at x, in metres
float ridgeAt(float x, float seed) {
  return 1.8 * sin(x * 0.09 + seed) + 1.0 * sin(x * 0.27 + 2.0 * seed) + 0.35 * sin(x * 0.83 + 3.0 * seed);
}

// how much of a layer of land, [dist] metres behind the surface, covers the ray, with how far above its ridge the ray is
float landAt(vec3 world, vec3 dir, float dist, float base, float seed, float trees, out float above) {
  vec2 at = world.xy + dir.xy * (dist / -dir.z);
  float ridge = base + ridgeAt(at.x, seed);
  // a few bare trees on the ridge: thin spikes
  ridge += trees * pow(max(0.0, sin(at.x * 5.3 + seed) * sin(at.x * 1.7 + 4.0 * seed)), 14.0);
  above = at.y - ridge;
  float edge = fwidth(above) + 1e-4;
  return clamp(0.5 - above / edge, 0.0, 1.0);
}

vec3 beyond(vec3 world) {
  vec3 dir = normalize(world - cameraPosition);
  dir.z = min(dir.z, -0.05);
  // the sky: silver blue at the horizon, where the moonlit mist lies, deep blue higher up
  float rise = dir.y / -dir.z;
  vec3 light = mix(vec3(0.13, 0.16, 0.22), vec3(0.005, 0.009, 0.026), smoothstep(-0.02, 0.16, rise));
  // the moon's glow, high to the left
  float m = max(dot(normalize(dir), normalize(vec3(-0.5, 0.3, -1.0))), 0.0);
  light += vec3(0.55, 0.62, 0.8) * (0.3 * pow(m, 300.0) + 0.06 * pow(m, 24.0));
  float above;
  // the far hills, dark blue in the mist, their ridges caught by the moon
  float far = landAt(world, dir, 60.0, 4.2, 1.3, 0.0, above);
  light = mix(light, vec3(0.01, 0.015, 0.03) + vec3(0.08, 0.09, 0.11) * exp(min(above, 0.0) * 6.0), far);
  // mist lying in the valley before them
  light += vec3(0.04, 0.05, 0.07) * exp(-abs(above + 1.6) * 1.2) * far;
  // the near hills, nearly black, with trees, their ridges caught faintly
  float near = landAt(world, dir, 18.0, 1.3, 4.1, 1.4, above);
  light = mix(light, vec3(0.002, 0.003, 0.007) + vec3(0.04, 0.05, 0.07) * exp(min(above, 0.0) * 10.0), near);
  return 1.3 * light;
}"""

        /**
         * The ghosts ([WorldLight.ghostShare]), for a place of the surface behind and its stain: `ghostAt` is the light
         * the band's shadow sends there, as a figure of pale light standing in the haze. It reads the front lamp's map
         * as [BEYOND] does ([BAND_GAP] m toward the lamp), but in two discs of [GHOST_TAPS] readings each, spread evenly
         * (each turned on from the one before by the golden angle) and turned as a whole by a turn of its own for each
         * pixel: the body, [GHOST_BODY] m around, gives the figure and its soft edge; the aura, [GHOST_AURA] m around,
         * makes the thick parts brighter than the thin ones and glows out a little beyond the edge. Lower down the
         * figure dims into the haze on the floor, and the surface's stains show through it.
         *
         * Why so many readings (the maintainer, 2026-11-21, "heavy aliasing around the bodies"): the windows' five
         * readings in a cross, made wider for the ghosts, laid five copies of each figure over one another, a short
         * way apart; each copy's edge was the map's own, hard at its points, and the light rose in steps of a sixth
         * from one copy to the next. Sixteen readings over a disc rise in sixteenths, and the turn for each pixel breaks
         * what is left of the copies into a fine grain.
         */
        private val GHOST = """
vec3 ghostAt(vec3 world, float stain) {
  vec3 toLamp = normalize(uFrontLamp - world);
  vec3 near = world + toLamp * ${BAND_GAP.toFixed(3)};
  vec3 across = normalize(cross(toLamp, vec3(0.0, 1.0, 0.0)));
  vec3 up = normalize(cross(across, toLamp));
  // the turn for this pixel (interleaved gradient noise), and the golden angle the readings turn on by
  float turn = 6.2831853 * fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
  vec2 dir = vec2(cos(turn), sin(turn));
  mat2 golden = mat2(-0.7373688, 0.6754903, -0.6754903, -0.7373688);
  float body = 0.0;
  float aura = 0.0;
  for (int i = 0; i < $GHOST_TAPS; i++) {
    float r = sqrt((float(i) + 0.5) / ${GHOST_TAPS}.0);
    body += lampHeldBack(near + (across * dir.x + up * dir.y) * (r * ${GHOST_BODY.toFixed(3)}));
    aura += lampHeldBack(near + (across * dir.y - up * dir.x) * (r * ${GHOST_AURA.toFixed(3)}));
    dir = golden * dir;
  }
  body /= ${GHOST_TAPS}.0;
  aura /= ${GHOST_TAPS}.0;
  float shape = smoothstep(0.1, 0.9, body);
  float inside = smoothstep(0.2, 0.9, aura);
  float rise = 0.5 + 0.5 * smoothstep(0.2, 3.0, world.y);
  float light = shape * (0.45 + 0.55 * inside) * rise * (0.6 + 0.6 * stain) + 0.3 * aura * aura;
  return uGhostPale * light;
}"""

        /**
         * The surface's stains, at a place (x, y) in metres: large soft patches and runs down it, about 0 to 1, 0.5 on
         * the mean.
         */
        internal val STAINS = """
float stains(vec2 p) {
  float patches = sin(p.x * 1.3 + 2.0 * sin(p.y * 0.7)) * sin(p.y * 1.1 + p.x * 0.4);
  float finer = sin(p.x * 3.7 - p.y * 2.9) * sin(p.y * 4.3 + p.x * 0.8);
  float runs = sin(p.x * 23.0 + 4.0 * sin(p.x * 3.1)) * (0.5 + 0.5 * sin(p.y * 0.9 + p.x * 5.0));
  return clamp(0.5 + 0.25 * patches + 0.15 * finer + 0.08 * runs, 0.0, 1.0);
}"""
    }
}
