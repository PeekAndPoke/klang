/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.workshop.threejs.createBufferGeometry
import io.peekandpoke.klang.workshop.threejs.createFloat32Attribute
import io.peekandpoke.klang.workshop.threejs.isInstancedMesh
import io.peekandpoke.klang.workshop.threejs.isMesh
import io.peekandpoke.klang.workshop.threejs.matrixWorld
import io.peekandpoke.klang.workshop.threejs.setAttribute
import io.peekandpoke.kraft.addons.threejs.js.BufferGeometry
import io.peekandpoke.kraft.addons.threejs.js.Material
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get

// Fewer draws for an instrument: the parts that do not move during the show, each kind of surface as one mesh. Every
// mesh is drawn on its own, and again for every lamp that throws a shadow and for the mirror floor (the maintainer,
// 2026-11-27: "Are we able to reduce the number of draw calls?").

/**
 * The [parts] as one geometry: one mesh, drawn at once, where each would be drawn on its own (and again for every
 * shadow and for the mirror floor). Each part is placed already; they are freed. Only where each point is and which
 * way it faces are kept, no picture's places (`uv`): for a surface without a picture. A part may share its points
 * among its triangles (indexed) or not (as three.js cuts an outline from a slab).
 */
internal fun merged(parts: List<BufferGeometry>): BufferGeometry {
    val positions = ArrayList<Double>()
    val normals = ArrayList<Double>()

    for (part in parts) {
        // (three.js warns and hands back the part itself where its triangles share no points already)
        val shared = part.asDynamic().index != null
        val flat = if (shared) part.asDynamic().toNonIndexed().unsafeCast<BufferGeometry>() else part
        val attributes = flat.asDynamic().attributes
        val position = attributes.position.array.unsafeCast<Float32Array>()
        val normal = attributes.normal.array.unsafeCast<Float32Array>()

        for (i in 0 until position.length) {
            positions.add(position[i].toDouble())
            normals.add(normal[i].toDouble())
        }

        if (shared) {
            flat.dispose()
        }

        part.dispose()
    }

    val geometry = Three.addon.createBufferGeometry()

    geometry.setAttribute("position", Three.addon.createFloat32Attribute(positions.toTypedArray(), 3))
    geometry.setAttribute("normal", Three.addon.createFloat32Attribute(normals.toTypedArray(), 3))

    return geometry
}

/** The parts of one look: one [material], and whether they throw a shadow ([casts]) and take one ([takes]). */
private class Look(val material: Material, val casts: Boolean, val takes: Boolean) {
    val parts = mutableListOf<BufferGeometry>()

    fun isOf(mesh: Mesh): Boolean =
        mesh.material === material && mesh.castShadow == casts && mesh.receiveShadow == takes
}

/**
 * The meshes under [still] (its children, and theirs) as one mesh for each look: each material, with its shadow
 * flags, once, in the order they first come. Each part's place, turn and size under [still] are baked into its
 * geometry, so the meshes stand where the parts stood once they are put where [still] would stand. For what does not
 * move during the show: where each part stands is still set as before, part by part, and merged when the instrument
 * is built (another place for a part means building the instrument anew).
 *
 * [still] is a group of its own, in no scene, its own place, turn and size none: its parts are the only thing it is
 * made for. A part's geometry is freed (it is copied). Refused, as they would not look as they did: a mirrored part
 * (its faces would turn inside out), a material with a picture of its own (no picture's places are kept, see
 * [merged]; the room a lacquer mirrors is no such picture), a mesh drawn many times over, a mesh of several materials.
 */
internal fun mergedByLook(still: Object3D): List<Mesh> {
    val looks = mutableListOf<Look>()

    check(still.parent == null) { "The parts to be merged hang in a scene" }

    still.updateWorldMatrix(updateParents = false, updateChildren = true)
    still.traverse { node ->
        if (node.isMesh) {
            val mesh = node.unsafeCast<Mesh>()

            check(!node.isInstancedMesh) { "A part to be merged is drawn many times over" }
            check(js("Array.isArray")(mesh.material) != true) { "A part to be merged has several materials" }
            check(!hasPicture(mesh.material.unsafeCast<Material>())) { "A part to be merged has a picture of its own" }

            val look = looks.firstOrNull { it.isOf(mesh) }
                ?: Look(material = mesh.material.unsafeCast<Material>(), casts = mesh.castShadow, takes = mesh.receiveShadow)
                    .also { looks.add(it) }
            val matrix = mesh.matrixWorld

            check(matrix.asDynamic().determinant().unsafeCast<Double>() > 0.0) { "A part to be merged is mirrored or flat" }

            val placed: dynamic = mesh.geometry.asDynamic().clone()

            placed.applyMatrix4(matrix)
            look.parts.add(placed.unsafeCast<BufferGeometry>())
            mesh.geometry.dispose()
        }
    }

    return looks.map { look ->
        val mesh = Three.addon.createMesh(merged(look.parts), look.material)

        mesh.castShadow = look.casts
        mesh.receiveShadow = look.takes

        mesh
    }
}

/** Whether [material] holds a picture of its own (any texture but the room it mirrors, its `envMap`). */
private fun hasPicture(material: Material): Boolean {
    val m: dynamic = material

    return js("Object.keys")(m).unsafeCast<Array<String>>().any { key ->
        val value: dynamic = m[key]

        key != "envMap" && value != null && value.isTexture == true
    }
}
