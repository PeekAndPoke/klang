/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.meet

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.mat4
import io.peekandpoke.klang.viz.liveact.quat
import io.peekandpoke.klang.viz.liveact.stage.Fence
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.klang.workshop.threejs.CompiledShader
import io.peekandpoke.klang.workshop.threejs.createCylinderGeometry
import io.peekandpoke.klang.workshop.threejs.createInstancedMesh
import io.peekandpoke.klang.workshop.threejs.onBeforeCompile
import io.peekandpoke.klang.workshop.threejs.set
import io.peekandpoke.klang.workshop.threejs.translate
import io.peekandpoke.klang.workshop.threejs.uniform
import io.peekandpoke.kraft.addons.threejs.js.Group
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * The hexagon of "Meet the Band" ([MeetLayout]), built into [group] (the carousel's turning part): six panels of the
 * show's fence ([Fence]), each a chain-link wire mesh of dark bronze facing out, [MeetLayout.MESH] from the middle,
 * with a dark steel post at each corner, and behind each a dark stained surface, [MeetLayout.BACK] from the middle,
 * that takes the shadows. A faint warm amber glow lies on each surface behind its member, and the wires' edges catch
 * it, as they catch the sun in the show.
 *
 * The show's fence works its wires and stains out from where they are in the world, for its one place; here they are
 * worked out from where they are on their panel (its own x across it and y up from the floor), so six panels turned
 * six ways share one material for the wires and one for the surfaces, and each looks the same. The wires, the posts
 * and the surfaces are the show's materials as made ([Fence.wireMaterial], [Fence.postMaterial],
 * [Fence.surfaceMaterial]) and its shader text ([Fence.CHAIN_LINK], [Fence.STAINS], [Fence.SUN_GLOW]); the cold world
 * beyond, the sun's moves and the time of day are not here.
 *
 * The surfaces are lit as the show's are where its front lamp is on, at once and for good; how they look is the
 * lamp's (`MeetStage`). The glow: [glow]. Everything it makes hangs in [group] and is freed with the scene.
 */
class HexFence(private val group: Group) {

    /** The amber glow on the surfaces and the wires' edges: its colour times its brightness ([glow]). */
    private val amber = vec()

    init {
        val glowUniform = uniform(amber)
        // the glow's middle on each panel, as the show's sun has it: across, up from the floor; and its size
        val glowAt = uniform(Three.addon.createVector2(x = 0.0, y = GLOW_HIGH))
        val glowSize = uniform(GLOW_SIZE)

        val wires = Fence.wireMaterial()

        wires.onBeforeCompile("meet-chain-link") { shader: CompiledShader ->
            shader.uniforms["uSunGlow"] = glowUniform
            shader.uniforms["uSunAt"] = glowAt
            shader.uniforms["uSunSize"] = glowSize
            shader.vertexShader = shader.vertexShader.onPanel()
            shader.fragmentShader = shader.fragmentShader
                .replaceFirst("#include <common>", "#include <common>\n$PANEL_VARYINGS\n${Fence.SUN_GLOW}\n${Fence.CHAIN_LINK}")
                .replaceFirst(
                    "#include <color_fragment>",
                    "#include <color_fragment>\nfloat wCover, wRim, wShade; vec3 wNormal;\n" +
                        "chainLink(vPanel, wCover, wNormal, wRim, wShade);\n" +
                        "diffuseColor.rgb *= wShade;\ndiffuseColor.a *= wCover;\nif (diffuseColor.a < 0.004) discard;",
                )
                .replaceFirst(
                    "#include <normal_fragment_maps>",
                    // the wire's normal is worked out on the panel: across it, up, and out of it, turned into the world
                    "#include <normal_fragment_maps>\n" +
                        "vec3 wWorld = wNormal.x * vAcross + wNormal.y * vec3(0.0, 1.0, 0.0) + wNormal.z * vOut;\n" +
                        "normal = normalize((viewMatrix * vec4(wWorld, 0.0)).xyz);",
                )
                .replaceFirst(
                    "#include <emissivemap_fragment>",
                    "#include <emissivemap_fragment>\ntotalEmissiveRadiance += uSunGlow * sunGlow(vPanel) * 0.22 * wRim;",
                )
        }

        val surfaces = Fence.surfaceMaterial()

        surfaces.color.setHex(Fence.LIT)
        surfaces.onBeforeCompile("meet-back-stains-glow") { shader: CompiledShader ->
            shader.uniforms["uSunGlow"] = glowUniform
            shader.uniforms["uSunAt"] = glowAt
            shader.uniforms["uSunSize"] = glowSize
            shader.vertexShader = shader.vertexShader.onPanel()
            shader.fragmentShader = shader.fragmentShader
                .replaceFirst("#include <common>", "#include <common>\n$PANEL_VARYINGS\n${Fence.SUN_GLOW}\n${Fence.STAINS}")
                .replaceFirst(
                    "#include <color_fragment>",
                    "#include <color_fragment>\nfloat stain = stains(vPanel);\ndiffuseColor.rgb *= 0.55 + 0.6 * stain;",
                )
                .replaceFirst(
                    "#include <emissivemap_fragment>",
                    "#include <emissivemap_fragment>\ntotalEmissiveRadiance += uSunGlow * sunGlow(vPanel) * (0.8 + 0.4 * stain);",
                )
        }

        // one shape for all six panels and one for all six surfaces: standing on the floor, their middle at x = 0
        val mesh = Three.addon.createPlaneGeometry(MESH_WIDE, MESH_HIGH)
        val back = Three.addon.createPlaneGeometry(BACK_WIDE, BACK_HIGH)

        mesh.translate(x = 0.0, y = MESH_HIGH / 2, z = 0.0)
        back.translate(x = 0.0, y = BACK_HIGH / 2, z = 0.0)

        for (side in 0 until MeetLayout.SIDES) {
            val angle = side * MeetLayout.STEP
            val panel = Three.addon.createMesh(mesh, wires)

            panel.position.set(MeetLayout.MESH * sin(angle), 0.0, MeetLayout.MESH * cos(angle))
            panel.rotation.y = angle
            panel.receiveShadow = true
            // after what is drawn see-through on the surface behind (Sirene's eyes), as the show's mesh
            panel.renderOrder = Fence.ORDER
            group.add(panel)

            val surface = Three.addon.createMesh(back, surfaces)

            surface.position.set(MeetLayout.BACK * sin(angle), 0.0, MeetLayout.BACK * cos(angle))
            surface.rotation.y = angle
            surface.receiveShadow = true
            group.add(surface)
        }

        // a post at each corner of the mesh, a little out from it
        val post = Three.addon.createCylinderGeometry(
            radiusTop = Fence.POST_RADIUS,
            radiusBottom = Fence.POST_RADIUS,
            height = MESH_HIGH,
            radialSegments = 12,
            heightSegments = 1,
            openEnded = true,
            thetaStart = 0.0,
            thetaLength = 2 * PI,
        )
        val posts = Three.addon.createInstancedMesh(geometry = post, material = Fence.postMaterial(), count = MeetLayout.SIDES)
        val corner = MeetLayout.MESH / cos(MeetLayout.STEP / 2) + Fence.POST_RADIUS + 0.01
        val upright = quat()
        val asBuilt = vec(1.0, 1.0, 1.0)

        for (side in 0 until MeetLayout.SIDES) {
            val angle = (side + 0.5) * MeetLayout.STEP
            val at = vec(corner * sin(angle), MESH_HIGH / 2, corner * cos(angle))

            posts.setMatrixAt(side, mat4().compose(position = at, quaternion = upright, scale = asBuilt))
        }

        posts.castShadow = true
        posts.receiveShadow = true
        group.add(posts)
    }

    /** The amber glow on the surfaces behind, at [level] (0: none; 1: as bright as the show's sun at its middle). */
    fun glow(level: Double) {
        amber.set(AMBER_R, AMBER_G, AMBER_B).multiplyScalar(level)
    }

    private companion object {
        /** A panel of the mesh and a surface behind: as wide as their side of the hexagon, and high. */
        val MESH_WIDE = 2 * MeetLayout.MESH * tan(MeetLayout.STEP / 2)
        val BACK_WIDE = 2 * MeetLayout.BACK * tan(MeetLayout.STEP / 2)
        const val MESH_HIGH = 6.0
        const val BACK_HIGH = 7.0

        /** The amber glow: where its middle is up the surface, how large it is against the show's sun, its colour. */
        const val GLOW_HIGH = 2.0
        const val GLOW_SIZE = 2.2
        const val AMBER_R = 1.0
        const val AMBER_G = 0.55
        const val AMBER_B = 0.2

        /** What the panels' shaders hand on: where a point is on its panel, and the panel's across and out in the world. */
        const val PANEL_VARYINGS = "varying vec2 vPanel;\nvarying vec3 vAcross;\nvarying vec3 vOut;"

        /** A vertex shader that also hands on where each point is on its panel and how the panel stands in the world. */
        fun String.onPanel(): String = this
            .replaceFirst("#include <common>", "#include <common>\n$PANEL_VARYINGS")
            .replaceFirst(
                "#include <begin_vertex>",
                "#include <begin_vertex>\nvPanel = transformed.xy;\n" +
                    "vAcross = normalize((modelMatrix * vec4(1.0, 0.0, 0.0, 0.0)).xyz);\n" +
                    "vOut = normalize((modelMatrix * vec4(0.0, 0.0, 1.0, 0.0)).xyz);",
            )
    }
}
