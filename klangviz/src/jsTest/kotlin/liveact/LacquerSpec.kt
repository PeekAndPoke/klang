/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.dress.HairPart
import io.peekandpoke.klang.viz.liveact.band.dress.INLAY_GLSL
import io.peekandpoke.klang.viz.liveact.band.dress.Lacquer
import io.peekandpoke.klang.viz.liveact.band.dress.pattern
import io.peekandpoke.kraft.addons.registry.AddonRegistry.Companion.addons
import io.peekandpoke.kraft.addons.registry.addons
import io.peekandpoke.kraft.addons.threejs.ThreeJsAddon
import io.peekandpoke.kraft.addons.threejs.js.Material
import io.peekandpoke.kraft.addons.threejs.js.MaterialParameters
import io.peekandpoke.kraft.addons.threejs.threeJs
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.time.Duration.Companion.seconds

private var loaded: ThreeJsAddon? = null

@Suppress("TestFunctionName")
private fun Tag.LacquerThreeCatcher() = comp { LacquerThreeCatcher(it) }

private class LacquerThreeCatcher(ctx: NoProps) : PureComponent(ctx) {
    private val three by subscribingTo(addons.threeJs)

    override fun VDom.render() {
        loaded = three
        div { }
    }
}

private suspend fun withThree(block: suspend (ThreeJsAddon) -> Unit) {
    TestBed.preact(appSetup = { addons { threeJs() } }, view = { LacquerThreeCatcher() }) {
        block(eventually(timeout = 5.seconds) { loaded.shouldNotBeNull() })
    }
}

/** The shader sources [material]'s change makes of three.js's own physical shader: (vertex, fragment). */
private fun compiled(three: ThreeJsAddon, material: Material): Pair<String, String> {
    val lib: dynamic = three.raw.ShaderLib.physical
    val shader: dynamic = js("({})")

    shader.vertexShader = lib.vertexShader
    shader.fragmentShader = lib.fragmentShader
    shader.uniforms = js("({})")
    material.asDynamic().onBeforeCompile(shader)

    return shader.vertexShader.unsafeCast<String>() to shader.fragmentShader.unsafeCast<String>()
}

/**
 * The lacquer's material (`?skin=lacquer`), on real three.js: what it splices into three.js's physical shader. Not
 * how it looks: the test browser has no graphics card, the stills judge that.
 */
class LacquerSpec : StringSpec({

    "every pattern's gold lines go into a physical material with a clear coat, as metal in the pattern's lines" {
        withThree { three ->
            Three.addon = three

            for (cloth in ClothPattern.entries) {
                val material = Lacquer.lacquerFor(name = "sirene_dark", cloth = cloth, hair = null, room = null)
                val (vertex, fragment) = compiled(three, material)

                material.asDynamic().isMeshPhysicalMaterial.unsafeCast<Boolean>() shouldBe true
                material.asDynamic().clearcoat.unsafeCast<Double>() shouldBe 1.0
                material.asDynamic().name.unsafeCast<String>() shouldBe "sirene_dark_lacquer"
                vertex shouldContain "vRest = position;"
                fragment shouldContain INLAY_GLSL
                fragment shouldContain "klGold = clamp(${cloth.inlay}, 0.0, 1.0);"
                fragment shouldContain "diffuseColor.rgb = mix(diffuseColor.rgb, vec3(0.9, 0.64, 0.26), klGold);"
                fragment shouldContain "roughnessFactor = mix(roughnessFactor, 0.180, klGold);"
                fragment shouldContain "metalnessFactor = mix(metalnessFactor, 1.0, klGold);"
                fragment shouldContain "material.clearcoat *= mix(1.0, 0.250, klGold);"
                // no glitter in the lacquer
                fragment shouldNotContain "spangle"
            }
        }
    }

    "the gold never glows: the lacquer gives off no light of its own, and nothing it adds touches the emission" {
        withThree { three ->
            Three.addon = three

            val material = Lacquer.lacquerFor(name = "rune_dark", cloth = ClothPattern.Diamonds, hair = null, room = null)
            val emissive: dynamic = material.asDynamic().emissive
            val original = three.raw.ShaderLib.physical.fragmentShader.unsafeCast<String>().lines().toSet()
            val added = compiled(three, material).second.lines().filter { it !in original }

            listOf(emissive.r, emissive.g, emissive.b).map { it.unsafeCast<Double>() } shouldBe listOf(0.0, 0.0, 0.0)
            added.filter { it.contains("klGold") }.size shouldBe 5
            added.filter { it.contains("missive", ignoreCase = true) } shouldBe emptyList()
        }
    }

    "a head with combed-back hair gets lacquered strands, and its own program" {
        withThree { three ->
            Three.addon = three

            val hair = HairPart(centre = vec(0.0, 1.6, 0.0), radius = 0.1)
            val combed = Lacquer.lacquerFor(name = "skalpell_dark", cloth = ClothPattern.Spiral, hair = hair, room = null)
            val plain = Lacquer.lacquerFor(name = "beben_dark", cloth = ClothPattern.Spiral, hair = null, room = null)

            compiled(three, combed).second shouldContain "mix(0.30, 0.06, shine)"
            compiled(three, plain).second shouldNotContain "in_hair"
            combed.asDynamic().customProgramCacheKey().unsafeCast<String>() shouldNotBe
                plain.asDynamic().customProgramCacheKey().unsafeCast<String>()
        }
    }

    "the suit's combed-back hair is the same as before the lacquer shared it" {
        withThree { three ->
            Three.addon = three

            val material = three.createMeshStandardMaterial(js("({})").unsafeCast<MaterialParameters>())

            pattern(material = material, kind = ClothPattern.Spiral, hair = HairPart(centre = vec(0.0, 1.6, 0.0), radius = 0.1))

            val fragment = compiled(three, material).second

            fragment shouldContain "roughnessFactor = mix(roughnessFactor, mix(0.62, 0.07, shine), in_hair); }"
            fragment shouldContain "vec3(0.0000, 1.6000, 0.0000)) / 0.1000;"
        }
    }
})
