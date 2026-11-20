/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.dress.jsText
import io.peekandpoke.klang.viz.liveact.toFixed
import io.peekandpoke.klang.workshop.threejs.CompiledShader
import io.peekandpoke.klang.workshop.threejs.onBeforeCompile
import io.peekandpoke.klang.workshop.threejs.set
import io.peekandpoke.klang.workshop.threejs.uniform
import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.disposeTree
import io.peekandpoke.klang.workshop.threejs.fog
import io.peekandpoke.klang.workshop.threejs.scale
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.utils.jsObject
import kotlin.math.min

/**
 * The braid laid flat, for a wall (x across, y up, in metres): the same woven lines between hoops, a pace wide. Not
 * for a body, so not a [ClothPattern].
 */
private const val BRAID_WALL = "1.0 - smoothstep(0.06, 0.16, min(min(abs(sin(p.x * 3.0 + 1.6 * sin(p.y * 4.2))), " +
    "abs(sin(p.x * 3.0 - 1.6 * sin(p.y * 4.2)))), 1.6 * abs(sin(p.y * 2.1))))"

/**
 * The wall behind the stage (a plane, x across and y up in metres) has two faces (the maintainer, 2026-11-16: "first
 * wealthy ... then prison"):
 * - by itself, under the back light: an ornament, a damask of round medallions with a flower in each, ringed, small
 *   studs between them, seen ever so slightly in a faint glow of its own that fades to the sides;
 * - under the front lamp (front 0 to 1, set every frame through the returned function): a wire mesh, the braid
 *   laid flat (the maintainer: "like perfect like wire mesh"), dark on the lit wall. The ornament's glow goes as the
 *   lamp comes.
 *
 * [amount]: how bright the ornament's own glow is; [width]: how far it reaches to the sides before it fades, in
 * metres; [color]: its colour, as red, green and blue.
 */
fun wall(
    material: MeshStandardMaterial,
    amount: Double = 0.017,
    width: Double = 5.5,
    color: Triple<Double, Double, Double> = Triple(0.55, 0.62, 1.0),
): (front: Double) -> Unit {
    val uFront = uniform(0.0)
    val ornament = """
    float u = p.x * 2.4, v = p.y * 2.4;
    float field = cos(u) * cos(v);
    float ring = 1.0 - smoothstep(0.0, 0.07, abs(abs(field) - 0.38));
    float second = 1.0 - smoothstep(0.0, 0.035, abs(abs(field) - 0.62));
    float flower = smoothstep(0.70, 0.86, abs(field)) * (0.45 + 0.55 * cos(6.0 * atan(sin(u), sin(v))));
    float stud = smoothstep(0.90, 0.97, abs(cos(u + 1.5708) * cos(v + 1.5708)));
    float ornament = clamp(max(max(ring, 0.6 * second), max(flower, stud)), 0.0, 1.0);
    // the glow: the same lines again, wide and soft, so the light seems to spill from them onto the wall
    float halo = max(max(exp(-9.0 * abs(abs(field) - 0.38)), 0.6 * exp(-12.0 * abs(abs(field) - 0.62))),
                     max(0.7 * smoothstep(0.45, 0.86, abs(field)), smoothstep(0.60, 0.97, abs(cos(u + 1.5708) * cos(v + 1.5708)))));"""
    // the mesh is the braid laid flat ([BRAID_WALL]): woven wires between hoops, as wire fencing is woven
    val mesh = "float mesh = $BRAID_WALL;"
    val rgb = listOf(color.first, color.second, color.third).joinToString(", ") { it.jsText() }

    material.onBeforeCompile("wall-two-faces-braid-glow") { shader: CompiledShader ->
        shader.uniforms["uFront"] = uFront

        shader.vertexShader = shader.vertexShader
            .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vRest;")
            .replaceFirst("#include <begin_vertex>", "#include <begin_vertex>\nvRest = position;")

        shader.fragmentShader = shader.fragmentShader
            .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vRest;\nuniform float uFront;")
            .replaceFirst(
                "#include <color_fragment>",
                "#include <color_fragment>\n{ vec3 p = vRest; $mesh diffuseColor.rgb *= 1.0 - 0.55 * mesh; }",
            )
            .replaceFirst(
                "#include <emissivemap_fragment>",
                "#include <emissivemap_fragment>\n{ vec3 p = vRest; $ornament totalEmissiveRadiance += vec3($rgb) * " +
                    "${amount.toFixed(4)} * (0.28 + 1.5 * ornament + 0.75 * halo) * " +
                    "exp(-(p.x * p.x) / ${(width * width).toFixed(2)}) * (1.0 - uFront); }",
            )
    }

    return { front -> uFront.value = front }
}

/**
 * The wall behind the stage as it was made ([StageLook.Wall]), built into [scene]: well clear of the riser, it takes
 * the front lamp's light and the band's shadows. It has two faces ([wall]): an ornament in its own faint glow under
 * the back light, a wire mesh under the front lamp. It takes no light from the other lamps (its colour is black
 * unless the front lamp is on), and the fog does not dim it.
 *
 * Each frame: [light]. It is freed with the scene, or with [dispose] where it goes before the scene does (the look
 * turned over to the fence while the show plays).
 */
class BackWall(private val scene: Scene) {
    private val material: MeshStandardMaterial = Three.addon.createMeshStandardMaterial(jsObject {
        color = 0x000000
        roughness = 0.95
    })

    private val mesh: Mesh

    /** Sets the wall's face: the ornament under the back light, the wire mesh under the front lamp (0 to 1). */
    private val face: (Double) -> Unit

    init {
        material.fog = false
        mesh = Three.addon.createMesh(Three.addon.createPlaneGeometry(60.0, 16.0), material)
        mesh.position.set(0.0, 8.0, Z)
        mesh.receiveShadow = true
        face = wall(material, amount = 0.014)
        scene.add(mesh)
    }

    /** The wall for this frame, the front lamp at [front] (0 to 1; as the address gives it, also beyond). */
    fun light(front: Double) {
        material.color.setHex(0x8a8f9c).scale(front)
        face(min(1.0, front))
    }

    /** Takes the wall out of the scene and frees its shape and its material. Call it once. */
    fun dispose() {
        scene.remove(mesh)
        mesh.disposeTree()
    }

    companion object {
        /** Where the wall stands, in metres along z: the front lamp's shadows of the band land here. */
        const val Z = -8.0
    }
}
