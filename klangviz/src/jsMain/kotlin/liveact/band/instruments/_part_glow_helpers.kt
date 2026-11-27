/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.workshop.threejs.BufferAttribute
import io.peekandpoke.klang.workshop.threejs.CompiledShader
import io.peekandpoke.klang.workshop.threejs.onBeforeCompile
import io.peekandpoke.klang.workshop.threejs.setAttribute
import io.peekandpoke.kraft.addons.threejs.js.BufferGeometry
import io.peekandpoke.kraft.addons.threejs.js.Color
import io.peekandpoke.kraft.addons.threejs.js.MeshStandardMaterial
import org.khronos.webgl.Float32Array
import org.khronos.webgl.set

// Parts that light up each on its own (a marimba's bars, an instrument's strings) in one mesh, with one material:
// the light each part gives off by itself rides on the geometry, not on a material of its own. One mesh is one draw,
// where a material for each part was a draw for each.

/** The geometry's attribute with the light each part gives off by itself (see [glowsByPart]). */
internal const val PART_GLOW = "klPartGlow"

/**
 * Lets [material] give off light part by part: what a point gives off by itself is the material's own light (its
 * `emissive` times its `emissiveIntensity`, as three.js has it) and its part's ([PART_GLOW], a colour already times
 * its brightness), the two added. Give the material no light of its own (black) where the parts alone glow: then a
 * part glows exactly as a material of its own with that colour and that brightness would.
 *
 * The material must have no change of its shader yet (its `onBeforeCompile` and `customProgramCacheKey` as three.js
 * made them): this one would take that one's place, and three.js would share one program between the two.
 */
internal fun glowsByPart(material: MeshStandardMaterial) {
    val plain: dynamic = Three.addon.raw.Material.prototype

    check(material.asDynamic().onBeforeCompile === plain.onBeforeCompile) { "The material's shader is changed already" }
    check(material.asDynamic().customProgramCacheKey === plain.customProgramCacheKey) {
        "The material's program has a name of its own already"
    }

    material.onBeforeCompile("instrument-part-glow") { shader: CompiledShader ->
        shader.vertexShader = shader.vertexShader
            .replaceFirst("#include <common>", "#include <common>\nattribute vec3 $PART_GLOW;\nvarying vec3 vKlPartGlow;")
            .replaceFirst("#include <begin_vertex>", "#include <begin_vertex>\nvKlPartGlow = $PART_GLOW;")
        shader.fragmentShader = shader.fragmentShader
            .replaceFirst("#include <common>", "#include <common>\nvarying vec3 vKlPartGlow;")
            .replaceFirst(
                "#include <emissivemap_fragment>",
                "#include <emissivemap_fragment>\ntotalEmissiveRadiance += vKlPartGlow;",
            )
    }
}

/**
 * The light each part of a mesh gives off by itself, as [glowsByPart] reads it: the parts one after the other,
 * [points] places of the attribute for each (one for a mesh drawn many times over, where each time is a part; a
 * part's points where the parts are merged into one geometry). Made into a geometry by [instanced] or [perPoint];
 * every part is dark at first.
 */
internal class PartGlow private constructor(private val attribute: BufferAttribute, private val points: Int) {
    private val array: Float32Array = attribute.asDynamic().array.unsafeCast<Float32Array>()

    /** How many parts there are. */
    val parts: Int = attribute.count / points

    /** The part [part] gives off [color] at [intensity], as a material's `emissive` and `emissiveIntensity` would. */
    fun set(part: Int, color: Color, intensity: Double) {
        val r = color.r * intensity
        val g = color.g * intensity
        val b = color.b * intensity

        for (i in part * points until (part + 1) * points) {
            array[3 * i] = r.toFloat()
            array[3 * i + 1] = g.toFloat()
            array[3 * i + 2] = b.toFloat()
        }

        attribute.needsUpdate = true
    }

    companion object {
        /** For a mesh drawn [count] times over ([geometry] is its shape): each time it is drawn is a part. */
        fun instanced(geometry: BufferGeometry, count: Int): PartGlow {
            val attribute = Three.addon.create<BufferAttribute>("InstancedBufferAttribute", Float32Array(3 * count), 3)

            geometry.setAttribute(PART_GLOW, attribute)

            return PartGlow(attribute = attribute, points = 1)
        }

        /** For [geometry] made of parts of [points] points each, one after the other (as [merged] puts them). */
        fun perPoint(geometry: BufferGeometry, points: Int): PartGlow {
            val count = geometry.asDynamic().attributes.position.count.unsafeCast<Int>()
            val attribute = Three.addon.create<BufferAttribute>("BufferAttribute", Float32Array(3 * count), 3)

            geometry.setAttribute(PART_GLOW, attribute)

            return PartGlow(attribute = attribute, points = points)
        }
    }
}
