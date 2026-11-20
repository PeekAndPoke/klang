/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.disposeTree
import io.peekandpoke.klang.viz.liveact.mat4
import io.peekandpoke.klang.viz.liveact.quat
import io.peekandpoke.klang.viz.liveact.toFixed
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.CompiledShader
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
 * Everything it makes is in the scene and is freed with it, or with [dispose] where the fence goes before the scene
 * does (the look turned over to the wall while the show plays).
 */
class Fence(private val scene: Scene) {
    /** The wire mesh, the posts and the surface behind: what [dispose] takes out of the scene and frees. */
    private val parts: List<Object3D>

    /** The surface behind the mesh: black unless the front lamp is on. */
    private val backMaterial: MeshStandardMaterial

    /** The sun's glow as the surface and the wires take it: its colour times its brightness. */
    private val glow = vec()

    init {
        val glowUniform = uniform(glow)

        // the wire mesh: one plane, the wires worked out on it, the gaps between them see-through
        val meshMaterial = Three.addon.createMeshStandardMaterial(jsObject {
            color = 0x5a4430
            roughness = 0.34
            metalness = 0.75
            transparent = true
        })
        meshMaterial.fog = false
        meshMaterial.onBeforeCompile("fence-chain-link") { shader: CompiledShader ->
            shader.uniforms["uSunGlow"] = glowUniform
            shader.vertexShader = shader.vertexShader.withWorld()
            shader.fragmentShader = shader.fragmentShader
                .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vWorld;\nuniform vec3 uSunGlow;\n$SUN_GLOW\n$CHAIN_LINK")
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
        val postMaterial = Three.addon.createMeshStandardMaterial(jsObject {
            color = 0x2a2724
            roughness = 0.45
            metalness = 0.7
        })
        postMaterial.fog = false
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
        val posts = Three.addon.createInstancedMesh(geometry = post, material = postMaterial, count = POSTS)
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
        backMaterial = Three.addon.createMeshStandardMaterial(jsObject {
            color = 0x000000
            roughness = 0.9
        })
        backMaterial.fog = false
        backMaterial.onBeforeCompile("fence-back-stains-sun") { shader: CompiledShader ->
            shader.uniforms["uSunGlow"] = glowUniform
            shader.vertexShader = shader.vertexShader.withWorld()
            shader.fragmentShader = shader.fragmentShader
                .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vWorld;\nuniform vec3 uSunGlow;\n$SUN_GLOW\n$STAINS")
                .replaceFirst(
                    "#include <color_fragment>",
                    "#include <color_fragment>\nfloat stain = stains(vWorld.xy);\ndiffuseColor.rgb *= 0.55 + 0.6 * stain;",
                )
                .replaceFirst(
                    "#include <emissivemap_fragment>",
                    "#include <emissivemap_fragment>\ntotalEmissiveRadiance += uSunGlow * sunGlow(vWorld.xy) * (0.8 + 0.4 * stain);",
                )
        }
        val back = Three.addon.createMesh(Three.addon.createPlaneGeometry(WIDE, HIGH + 2.0), backMaterial)
        back.position.set(0.0, (HIGH + 2.0) / 2, BACK_Z)
        back.receiveShadow = true
        scene.add(back)

        parts = listOf(mesh, posts, back)
    }

    /**
     * Takes the mesh, the posts and the surface behind out of the scene and frees what they hold on the graphics card
     * (their shapes, their materials, the posts' places). The fence is gone after it; call it once.
     */
    fun dispose() {
        for (part in parts) {
            scene.remove(part)
        }

        for (part in parts) {
            part.disposeTree()
        }
    }

    /**
     * Sets the fence for this frame: the front lamp at [front] (0 to 1) lights the surface behind, and the sun glows
     * in [sun] at [level] (from 0).
     */
    fun light(front: Double, sun: Color, level: Double) {
        backMaterial.color.setHex(0x8a8f9c).scale(front)
        glow.set(sun.r, sun.g, sun.b).multiplyScalar(level * GLOW)
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

        /** How bright the sun glows on the surface at its middle, at its full brightness. */
        private const val GLOW = 0.5

        /** The mesh and the surface: as wide as the wall was, and as high. */
        private const val WIDE = 60.0
        private const val HIGH = 16.0

        /** The posts: how many, how far apart, how thick. */
        private const val POSTS = 18
        private const val POST_EVERY = 3.2
        private const val POST_RADIUS = 0.045

        /** A vertex shader that also hands on where each point is in the world (`vWorld`). */
        private fun String.withWorld(): String = this
            .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vWorld;")
            .replaceFirst("#include <begin_vertex>", "#include <begin_vertex>\nvWorld = (modelMatrix * vec4(transformed, 1.0)).xyz;")

        /**
         * The sun on the surface at a place (x, y): a close glow, a wider one and a faint wide one, a little flatter
         * than round, its middle at about 1 at its brightest.
         */
        private val SUN_GLOW = """
float sunGlow(vec2 p) {
  vec2 d = (p - vec2(${SUN_X.toFixed(2)}, ${SUN_Y.toFixed(2)})) * vec2(1.0, 1.1);
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
        private val CHAIN_LINK = """
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
         * The surface's stains, at a place (x, y) in metres: large soft patches and runs down it, about 0 to 1, 0.5 on
         * the mean.
         */
        private val STAINS = """
float stains(vec2 p) {
  float patches = sin(p.x * 1.3 + 2.0 * sin(p.y * 0.7)) * sin(p.y * 1.1 + p.x * 0.4);
  float finer = sin(p.x * 3.7 - p.y * 2.9) * sin(p.y * 4.3 + p.x * 0.8);
  float runs = sin(p.x * 23.0 + 4.0 * sin(p.x * 3.1)) * (0.5 + 0.5 * sin(p.y * 0.9 + p.x * 5.0));
  return clamp(0.5 + 0.25 * patches + 0.15 * finer + 0.08 * runs, 0.0, 1.0);
}"""
    }
}
