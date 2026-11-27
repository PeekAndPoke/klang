/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.workshop.threejs.createColor
import io.peekandpoke.kraft.addons.registry.AddonRegistry.Companion.addons
import io.peekandpoke.kraft.addons.registry.addons
import io.peekandpoke.kraft.addons.threejs.ThreeJsAddon
import io.peekandpoke.kraft.addons.threejs.js.BufferGeometry
import io.peekandpoke.kraft.addons.threejs.js.MaterialParameters
import io.peekandpoke.kraft.addons.threejs.js.Mesh
import io.peekandpoke.kraft.addons.threejs.threeJs
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds

private var loaded: ThreeJsAddon? = null

@Suppress("TestFunctionName")
private fun Tag.DrawsThreeCatcher() = comp { DrawsThreeCatcher(it) }

private class DrawsThreeCatcher(ctx: NoProps) : PureComponent(ctx) {
    private val three by subscribingTo(addons.threeJs)

    override fun VDom.render() {
        loaded = three
        div { }
    }
}

private suspend fun withThree(block: suspend (ThreeJsAddon) -> Unit) {
    TestBed.preact(appSetup = { addons { threeJs() } }, view = { DrawsThreeCatcher() }) {
        block(eventually(timeout = 5.seconds) { loaded.shouldNotBeNull() })
    }
}

/** The numbers of [geometry]'s attribute [name], in a row. */
private fun valuesOf(geometry: BufferGeometry, name: String): List<Double> {
    val array: dynamic = geometry.asDynamic().attributes[name].array

    return (0 until array.length.unsafeCast<Int>()).map { array[it].unsafeCast<Double>() }
}

/** The smallest and the largest of every third of [values], from [axis] on: (least, most). */
private fun spanOf(values: List<Double>, axis: Int): Pair<Double, Double> {
    val along = values.filterIndexed { i, _ -> i % 3 == axis }

    return along.min() to along.max()
}

/**
 * Fewer draws for the instruments, on real three.js: the parts that do not move merged into one mesh for each look
 * ([mergedByLook]), and the light each part gives off by itself carried by the geometry ([PartGlow], [glowsByPart]).
 * Not how they look: the test browser has no graphics card, the stills judge that.
 */
class InstrumentDrawsSpec : StringSpec({

    "the parts are merged into one mesh for each material and shadow flags, in the order they first come" {
        withThree { three ->
            Three.addon = three

            val steel = three.createMeshStandardMaterial(js("({})").unsafeCast<MaterialParameters>())
            val brass = three.createMeshStandardMaterial(js("({})").unsafeCast<MaterialParameters>())
            val still = three.createGroup()

            fun part(material: dynamic, casts: Boolean, takes: Boolean = false): Mesh {
                val mesh = three.createMesh(three.createBoxGeometry(1.0, 1.0, 1.0), material)

                mesh.castShadow = casts
                mesh.receiveShadow = takes
                still.add(mesh)

                return mesh
            }

            part(steel, casts = true)
            part(brass, casts = false)
            part(steel, casts = true)
            part(steel, casts = false)
            part(steel, casts = true, takes = true)

            val merged = mergedByLook(still)
            // a box is twelve triangles: 36 points once they are not shared
            val points = merged.map { it.geometry.asDynamic().attributes.position.count.unsafeCast<Int>() }

            merged.map { it.material === steel } shouldBe listOf(true, false, true, true)
            merged.map { it.castShadow } shouldBe listOf(true, false, false, true)
            merged.map { it.receiveShadow } shouldBe listOf(false, false, false, true)
            points shouldBe listOf(72, 36, 36, 36)
        }
    }

    "each part's place, size and turn are baked into the merged geometry, through every group it hangs in" {
        withThree { three ->
            Three.addon = three

            val steel = three.createMeshStandardMaterial(js("({})").unsafeCast<MaterialParameters>())
            val still = three.createGroup()
            val stretched = three.createMesh(three.createBoxGeometry(1.0, 1.0, 1.0), steel)
            val stand = three.createGroup()
            val held = three.createMesh(three.createBoxGeometry(1.0, 1.0, 1.0), steel)

            // one stretched upright and moved along x; one in a group raised and turned a quarter about z
            stretched.scale.set(1.0, 3.0, 1.0)
            stretched.position.set(10.0, 0.0, 0.0)
            stand.position.set(0.0, 5.0, 0.0)
            stand.rotation.z = PI / 2
            held.position.set(2.0, 0.0, 0.0)
            held.scale.set(4.0, 1.0, 1.0)
            still.add(stretched)
            stand.add(held)
            still.add(stand)

            val geometry = mergedByLook(still).single().geometry
            val position = valuesOf(geometry, "position")
            val first = position.take(36 * 3)
            val second = position.drop(36 * 3)

            spanOf(first, 0) shouldBe (9.5 to 10.5)
            spanOf(first, 1) shouldBe (-1.5 to 1.5)
            // turned a quarter: its x is now up, its 4 long along y, standing 2 above the group's 5
            spanOf(second, 0).first shouldBe (-0.5 plusOrMinus 1e-6)
            spanOf(second, 0).second shouldBe (0.5 plusOrMinus 1e-6)
            spanOf(second, 1).first shouldBe (5.0 plusOrMinus 1e-6)
            spanOf(second, 1).second shouldBe (9.0 plusOrMinus 1e-6)

            // the faces still face out, a unit long: the turned box's end that faced +x faces +y
            val normal = valuesOf(geometry, "normal")
            val lengths = normal.chunked(3).map { (x, y, z) -> sqrt(x * x + y * y + z * z) }
            val endsUp = normal.drop(36 * 3).chunked(3).count { (x, y, z) -> y > 0.999 && x * x + z * z < 1e-9 }

            lengths.all { it > 0.9999 && it < 1.0001 } shouldBe true
            endsUp shouldBe 6
        }
    }

    "the parts' own geometries are freed, and a mirrored part is refused (its faces would turn inside out)" {
        withThree { three ->
            Three.addon = three

            val steel = three.createMeshStandardMaterial(js("({})").unsafeCast<MaterialParameters>())
            val still = three.createGroup()
            val box = three.createBoxGeometry(1.0, 1.0, 1.0)
            val freed = mutableListOf<Boolean>()

            box.asDynamic().addEventListener("dispose") { freed.add(true) }
            still.add(three.createMesh(box, steel))
            mergedByLook(still)

            freed shouldBe listOf(true)

            val mirrored = three.createGroup()
            val part = three.createMesh(three.createBoxGeometry(1.0, 1.0, 1.0), steel)

            part.scale.set(1.0, -1.0, 1.0)
            mirrored.add(part)

            shouldThrow<IllegalStateException> { mergedByLook(mirrored) }
        }
    }

    "a part whose triangles share no points (as three.js cuts an outline from a slab) is merged as it is, once freed" {
        withThree { three ->
            Three.addon = three

            val indexed = three.createBoxGeometry(1.0, 1.0, 1.0)
            val flat = indexed.asDynamic().toNonIndexed().unsafeCast<BufferGeometry>()
            val expected = valuesOf(flat, "position")
            val freed = mutableListOf<Boolean>()
            val warnings = mutableListOf<String>()
            val console: dynamic = js("console")
            val warn: dynamic = console.warn

            flat.asDynamic().index.unsafeCast<Any?>() shouldBe null
            flat.asDynamic().addEventListener("dispose") { freed.add(true) }
            console.warn = { message: dynamic -> warnings.add(message.toString()) }

            val geometry = try {
                merged(listOf(flat))
            } finally {
                console.warn = warn
            }

            warnings shouldBe emptyList<String>()
            freed.size shouldBe 1
            valuesOf(geometry, "position") shouldBe expected
        }
    }

    "a part with a picture of its own is refused (its picture would be lost); the room a lacquer mirrors is no picture" {
        withThree { three ->
            Three.addon = three

            fun stillWith(material: dynamic): dynamic {
                val still = three.createGroup()

                still.add(three.createMesh(three.createBoxGeometry(1.0, 1.0, 1.0), material))

                return still
            }

            val pictured = three.createMeshStandardMaterial(js("({})").unsafeCast<MaterialParameters>())
            val mirroring = three.createMeshStandardMaterial(js("({})").unsafeCast<MaterialParameters>())

            pictured.asDynamic().map = three.create<dynamic>("Texture")
            mirroring.asDynamic().envMap = three.create<dynamic>("Texture")

            shouldThrow<IllegalStateException> { mergedByLook(stillWith(pictured)) }.message shouldBe
                "A part to be merged has a picture of its own"
            mergedByLook(stillWith(mirroring)).size shouldBe 1
        }
    }

    "refused as well: parts that hang in a scene, a mesh drawn many times over, a mesh of several materials" {
        withThree { three ->
            Three.addon = three

            val steel = three.createMeshStandardMaterial(js("({})").unsafeCast<MaterialParameters>())
            val scene = three.createGroup()
            val hanging = three.createGroup()
            val many = three.createGroup()
            val mixed = three.createGroup()

            hanging.add(three.createMesh(three.createBoxGeometry(1.0, 1.0, 1.0), steel))
            scene.add(hanging)
            many.add(three.create<dynamic>("InstancedMesh", three.createBoxGeometry(1.0, 1.0, 1.0), steel, 2))
            mixed.add(three.createMesh(three.createBoxGeometry(1.0, 1.0, 1.0), arrayOf(steel, steel)))

            shouldThrow<IllegalStateException> { mergedByLook(hanging) }.message shouldBe "The parts to be merged hang in a scene"
            shouldThrow<IllegalStateException> { mergedByLook(many) }.message shouldBe "A part to be merged is drawn many times over"
            shouldThrow<IllegalStateException> { mergedByLook(mixed) }.message shouldBe "A part to be merged has several materials"
        }
    }

    "a material whose shader is changed already cannot glow part by part (the one change would replace the other)" {
        withThree { three ->
            Three.addon = three

            val once = three.createMeshStandardMaterial(js("({})").unsafeCast<MaterialParameters>())
            val named = three.createMeshStandardMaterial(js("({})").unsafeCast<MaterialParameters>())

            glowsByPart(once)
            named.asDynamic().customProgramCacheKey = { "own" }

            shouldThrow<IllegalStateException> { glowsByPart(once) }.message shouldBe "The material's shader is changed already"
            shouldThrow<IllegalStateException> { glowsByPart(named) }.message shouldBe
                "The material's program has a name of its own already"
        }
    }

    "a mesh drawn many times over: each time its own light, the colour times its brightness, the others untouched" {
        withThree { three ->
            Three.addon = three

            val geometry = three.createBoxGeometry(1.0, 1.0, 1.0)
            val glows = PartGlow.instanced(geometry = geometry, count = 3)
            val attribute: dynamic = geometry.asDynamic().attributes[PART_GLOW]
            val before = attribute.version.unsafeCast<Int>()
            val color = three.createColor(0x000000)

            color.r = 0.5
            color.g = 0.25
            color.b = 1.0
            glows.set(part = 1, color = color, intensity = 2.0)

            attribute.isInstancedBufferAttribute.unsafeCast<Boolean>() shouldBe true
            glows.parts shouldBe 3
            valuesOf(geometry, PART_GLOW) shouldBe listOf(0.0, 0.0, 0.0, 1.0, 0.5, 2.0, 0.0, 0.0, 0.0)
            // three.js uploads it again
            (attribute.version.unsafeCast<Int>() > before) shouldBe true
        }
    }

    "parts merged into one geometry: a part's light on every one of its points, and on none of the others" {
        withThree { three ->
            Three.addon = three

            val geometry = merged((0 until 3).map { three.createBoxGeometry(1.0, 1.0, 1.0) })
            val glows = PartGlow.perPoint(geometry = geometry, points = 36)
            val color = three.createColor(0xffffff)

            glows.set(part = 2, color = color, intensity = 0.5)
            glows.set(part = 0, color = color, intensity = 0.25)

            val values = valuesOf(geometry, PART_GLOW)

            glows.parts shouldBe 3
            values.size shouldBe 3 * 36 * 3
            values.take(36 * 3).all { it == 0.25 } shouldBe true
            values.drop(36 * 3).take(36 * 3).all { it == 0.0 } shouldBe true
            values.drop(2 * 36 * 3).all { it == 0.5 } shouldBe true
        }
    }

    "a part's light is passed from the vertex to the fragment and added to the material's own, after its picture" {
        withThree { three ->
            Three.addon = three

            val material = three.createMeshStandardMaterial(js("({})").unsafeCast<MaterialParameters>())
            val lib: dynamic = three.raw.ShaderLib.standard
            val shader: dynamic = js("({})")

            glowsByPart(material)
            shader.vertexShader = lib.vertexShader
            shader.fragmentShader = lib.fragmentShader
            shader.uniforms = js("({})")
            material.asDynamic().onBeforeCompile(shader)

            val vertex = shader.vertexShader.unsafeCast<String>()
            val fragment = shader.fragmentShader.unsafeCast<String>()

            vertex shouldContain "#include <common>\nattribute vec3 klPartGlow;\nvarying vec3 vKlPartGlow;"
            vertex shouldContain "#include <begin_vertex>\nvKlPartGlow = klPartGlow;"
            fragment shouldContain "#include <common>\nvarying vec3 vKlPartGlow;"
            fragment shouldContain "#include <emissivemap_fragment>\ntotalEmissiveRadiance += vKlPartGlow;"
            // after the material's own light is set, before the lamps add theirs
            (fragment.indexOf("vec3 totalEmissiveRadiance = emissive;") < fragment.indexOf("+= vKlPartGlow")) shouldBe true
            (fragment.indexOf("+= vKlPartGlow") < fragment.indexOf("#include <lights_fragment_begin>")) shouldBe true
            material.asDynamic().customProgramCacheKey().unsafeCast<String>() shouldBe "instrument-part-glow"
        }
    }
})
