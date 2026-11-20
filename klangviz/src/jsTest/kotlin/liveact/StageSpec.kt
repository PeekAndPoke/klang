/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.lights.ShadowQuality
import io.peekandpoke.klang.viz.liveact.stage.FloorLook
import io.peekandpoke.klang.viz.liveact.stage.FogQuality
import io.peekandpoke.klang.viz.liveact.stage.Stage
import io.peekandpoke.klang.viz.liveact.stage.StageLook
import io.peekandpoke.klang.viz.liveact.theatre.CrowdPlaces
import io.peekandpoke.klang.workshop.threejs.fog
import io.peekandpoke.klang.workshop.threejs.isLight
import io.peekandpoke.kraft.addons.registry.AddonRegistry.Companion.addons
import io.peekandpoke.kraft.addons.registry.addons
import io.peekandpoke.kraft.addons.threejs.ThreeJsAddon
import io.peekandpoke.kraft.addons.threejs.js.Object3D
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.addons.threejs.js.WebGLRenderer
import io.peekandpoke.kraft.addons.threejs.threeJs
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.browser.window
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.time.Duration.Companion.seconds

private var loaded: ThreeJsAddon? = null

@Suppress("TestFunctionName")
private fun Tag.StageThreeCatcher() = comp { StageThreeCatcher(it) }

private class StageThreeCatcher(ctx: NoProps) : PureComponent(ctx) {
    private val three by subscribingTo(addons.threeJs)

    override fun VDom.render() {
        loaded = three
        div { }
    }
}

private suspend fun withThree(block: suspend (ThreeJsAddon) -> Unit) {
    TestBed.preact(appSetup = { addons { threeJs() } }, view = { StageThreeCatcher() }) {
        block(eventually(timeout = 5.seconds) { loaded.shouldNotBeNull() })
    }
}

/**
 * A renderer that draws nothing: the test browser has no graphics card to draw on, and a stage only sets a few
 * things on its renderer. Its card offers no extensions, so the fog is worked out for every pixel.
 */
private fun standInRenderer(): WebGLRenderer {
    val renderer: dynamic = js(
        "({ shadowMap: {}, domElement: { height: 520 }, setPixelRatio: function () {}, getPixelRatio: function () { return 1; }, " +
            "extensions: { has: function () { return false; } } })",
    )

    return renderer.unsafeCast<WebGLRenderer>()
}

/** The listeners for [type] put on the window and not taken off again, while [block] runs. */
private fun listenersLeft(type: String, block: (left: List<dynamic>) -> Unit): List<dynamic> {
    val w: dynamic = window
    val add: dynamic = w.addEventListener
    val remove: dynamic = w.removeEventListener
    val left = mutableListOf<dynamic>()

    w.addEventListener = { t: String, listener: dynamic, options: dynamic ->
        if (t == type) {
            left.add(listener)
        }

        add.call(w, t, listener, options)
    }
    w.removeEventListener = { t: String, listener: dynamic, options: dynamic ->
        if (t == type) {
            left.remove(listener)
        }

        remove.call(w, t, listener, options)
    }

    try {
        block(left)
    } finally {
        // the window's own two again: ours lay over them
        js("delete w.addEventListener")
        js("delete w.removeEventListener")
    }

    return left
}

/**
 * The stage built on real three.js with a stand-in renderer. The whole live act cannot be mounted here: its
 * component needs a WebGL context, which the test browser does not give.
 */
class StageSpec : StringSpec({

    "a stage listens for a key on the window while it stands, and no longer once it is given up" {
        withThree { three ->
            Three.addon = three

            val scene = three.createScene()
            val camera = three.createPerspectiveCamera(fov = 75.0, aspect = 1.5, near = 1.0, far = 1000.0)

            val left = listenersLeft("keydown") { left ->
                val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = standInRenderer())

                left.size shouldBe 1
                stage.dispose()
            }

            left shouldBe emptyList()
        }
    }

    "a stage sets the camera's lens, the scene's haze and its six lamps, and its light step runs" {
        withThree { three ->
            Three.addon = three

            val scene = three.createScene()
            val camera = three.createPerspectiveCamera(fov = 75.0, aspect = 1.5, near = 1.0, far = 1000.0)
            val renderer = standInRenderer()
            val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = renderer)

            try {
                camera.fov shouldBe 35.0
                camera.far shouldBe Stage.FAR
                // the audience's last row is seen from wherever the camera goes: up to some 9 m behind the stage's
                // middle or beside it, with room to spare
                Stage.FAR shouldBeGreaterThan CrowdPlaces.LAST + 30.0
                scene.fog.shouldNotBeNull().far shouldBe 22.0
                renderer.asDynamic().shadowMap.enabled.unsafeCast<Boolean>() shouldBe true

                var lamps = 0
                scene.traverse { if (it.isLight) lamps++ }

                // the ambient light, four lamps above and the one low at the front
                lamps shouldBe 6
                // floor, wall, riser, the lamps with their targets, the crowd's lights and the terraces they stand
                // on, the night sky; and the fog's twelve sheets
                scene.children.size shouldBe 17 + 12

                // a frame's light step runs on it, before the song and in it
                stage.light(t = -5.0, house = 0.07, waiting = true)
                stage.light(t = 62.0, house = 1.0, waiting = false)

                // the sky goes with the camera, once the stage is told where the camera stands this frame: its
                // stars, its moon and its shooting star are the one group of three in the scene
                val sky = scene.children.single { it.children.size == 3 && it.children.none { child -> child.isLight } }

                camera.position.set(4.0, 11.0, 46.0)
                (sky.position.x == 4.0) shouldBe false
                stage.follow()
                listOf(sky.position.x, sky.position.y, sky.position.z) shouldBe listOf(4.0, 11.0, 46.0)
            } finally {
                stage.dispose()
            }
        }
    }

    "the fog is worked out as maps once with every light step, where the card can hold them; not with ?fullfog" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 75.0, aspect = 1.5, near = 1.0, far = 1000.0)

            // a renderer that says it can hold the maps, and notes what it is asked to draw into
            val renderer = standInRenderer()
            val r: dynamic = renderer
            val drawnInto = mutableListOf<dynamic>()
            var target: dynamic = "the screen"

            r.extensions = js("({ has: function (name) { return name === 'EXT_color_buffer_float' } })")
            r.getRenderTarget = { target }
            r.setRenderTarget = { to: dynamic -> target = to }
            r.render = { _: dynamic, _: dynamic -> drawnInto.add(target) }

            val scene = three.createScene()
            val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = renderer)
            var freed = 0

            try {
                stage.light(t = 62.0, house = 1.0, waiting = false)

                // one drawing, into a picture of its own, and the renderer draws to where it drew before
                drawnInto.size shouldBe 1
                (drawnInto[0] !== "the screen") shouldBe true
                (target === "the screen") shouldBe true

                stage.light(t = 62.1, house = 1.0, waiting = false)
                drawnInto.size shouldBe 2

                // the picture the sheets read is the one drawn into; it is freed when the stage is given up
                val sheets = scene.children.filter { it.asDynamic().material?.uniforms?.uMaps != null }
                val picture: dynamic = sheets.first().asDynamic().material.uniforms.uMaps.value.renderTarget

                sheets.size shouldBe 12
                (picture === drawnInto[0]) shouldBe true
                picture.addEventListener("dispose") { _: dynamic -> freed++ }
            } finally {
                stage.dispose()
            }

            freed shouldBe 1

            drawnInto.clear()

            val show = Show(seed = 1, switches = Switches.parse("?fullfog"))
            val other = Stage(show = show, scene = three.createScene(), camera = camera, renderer = renderer)

            try {
                other.light(t = 62.0, house = 1.0, waiting = false)
                drawnInto.size shouldBe 0
            } finally {
                other.dispose()
            }

            // a card that cannot hold the maps: every sheet works the fog out for itself, and all twelve are there
            r.extensions = js("({ has: function () { return false } })")

            val plain = three.createScene()
            val third = Stage(show = Show(seed = 1), scene = plain, camera = camera, renderer = renderer)

            try {
                third.light(t = 62.0, house = 1.0, waiting = false)
                drawnInto.size shouldBe 0
                plain.children.count { it.asDynamic().material?.uniforms?.uHeight != null } shouldBe 12
            } finally {
                third.dispose()
            }
        }
    }

    "the fog starts at the address's quality: as many sheets, and none with ?nofog or ?fog=off" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 75.0, aspect = 1.5, near = 1.0, far = 1000.0)

            for ((query, sheets) in listOf("?fog=low" to 4, "?fog=xhigh" to 16, "?nofog" to 0, "?fog=off" to 0, "" to 12)) {
                val scene = three.createScene()
                val show = Show(seed = 1, switches = Switches.parse(query))
                val stage = Stage(show = show, scene = scene, camera = camera, renderer = standInRenderer())

                try {
                    sheetsOf(scene).size shouldBe sheets
                    scene.children.size shouldBe 17 + sheets
                } finally {
                    stage.dispose()
                }
            }
        }
    }

    "the fog at another quality: the old one leaves the scene with all it holds freed, the new one comes" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 75.0, aspect = 1.5, near = 1.0, far = 1000.0)
            val renderer = standInRenderer()
            val r: dynamic = renderer

            // a card that can hold the maps, drawing nothing
            r.extensions = js("({ has: function (name) { return name === 'EXT_color_buffer_float' } })")
            r.getRenderTarget = { null }
            r.setRenderTarget = { _: dynamic -> Unit }
            r.render = { _: dynamic, _: dynamic -> Unit }

            val scene = three.createScene()
            val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = renderer)

            try {
                val high = sheetsOf(scene)
                val freed = mutableListOf<String>()
                val picture: dynamic = high.first().asDynamic().material.uniforms.uMaps.value.renderTarget

                high.size shouldBe 12

                for (sheet in high) {
                    sheet.asDynamic().material.addEventListener("dispose") { _: dynamic -> freed.add("material") }
                }

                high.first().asDynamic().geometry.addEventListener("dispose") { _: dynamic -> freed.add("geometry") }
                picture.addEventListener("dispose") { _: dynamic -> freed.add("maps") }

                // the same quality again changes nothing
                stage.fog(FogQuality.High)
                freed shouldBe emptyList()
                sheetsOf(scene) shouldBe high

                stage.fog(FogQuality.Low)
                freed.count { it == "material" } shouldBe 12
                freed.count { it == "geometry" } shouldBe 1
                freed.count { it == "maps" } shouldBe 1

                // four sheets, none of the old, reading a picture of 2 by 2 maps
                val low = sheetsOf(scene)
                val maps: dynamic = low.first().asDynamic().material.uniforms.uMaps.value.renderTarget

                low.size shouldBe 4
                low.none { it in high } shouldBe true
                scene.children.size shouldBe 17 + 4
                (maps.width.unsafeCast<Int>() to maps.height.unsafeCast<Int>()) shouldBe (2 * 384 to 2 * 324)
                low.map { it.asDynamic().material.uniforms.uCell.value.x.unsafeCast<Double>() } shouldBe listOf(0.0, 1.0, 0.0, 1.0)
                stage.light(t = 62.0, house = 1.0, waiting = false)

                // off: no sheet at all; and back to a fog
                stage.fog(FogQuality.Off)
                scene.children.size shouldBe 17
                stage.light(t = 62.1, house = 1.0, waiting = false)
                stage.fog(FogQuality.Medium)
                sheetsOf(scene).size shouldBe 8
            } finally {
                stage.dispose()
            }

            scene.children.size shouldBe 17
        }
    }

    "a fog made anew frees its noise too, where every sheet works the fog out for itself" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 75.0, aspect = 1.5, near = 1.0, far = 1000.0)
            val scene = three.createScene()
            val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = standInRenderer())
            var freed = 0

            try {
                val noise: dynamic = sheetsOf(scene).first().asDynamic().material.uniforms.uNoise.value

                noise.addEventListener("dispose") { _: dynamic -> freed++ }
                stage.fog(FogQuality.XHigh)
                freed shouldBe 1
                sheetsOf(scene).size shouldBe 16
            } finally {
                stage.dispose()
            }
        }
    }

    "the shadows at low: three lamps cast, in maps of 1024, the old maps freed; at high again four of 2048" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 75.0, aspect = 1.5, near = 1.0, far = 1000.0)
            val scene = three.createScene()
            val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = standInRenderer())
            val spots = scene.children.filter { it.asDynamic().isSpotLight == true }.map { it.asDynamic() }

            /** The lamps that cast, by the size of their maps. */
            fun casting(): List<Int> = spots.filter { it.castShadow == true }.map { it.shadow.mapSize.x.unsafeCast<Int>() }

            var freed = 0

            /** A map as three.js would have made it, that counts when it is freed. */
            fun mapsMade() {
                for (spot in spots.filter { it.castShadow == true }) {
                    val map: dynamic = js("({})")

                    map.dispose = { freed++ }
                    spot.shadow.map = map
                }
            }

            try {
                casting() shouldBe listOf(2048, 2048, 2048, 2048)
                mapsMade()

                // the same quality again changes nothing
                stage.shadows(ShadowQuality.High)
                freed shouldBe 0

                stage.shadows(ShadowQuality.Low)
                freed shouldBe 4
                spots.count { it.shadow.map != null } shouldBe 0
                casting() shouldBe listOf(1024, 1024, 1024)
                // the inner lamp behind casts none, the low front lamp still does
                spots.single { it.position.x == 0.0 && it.position.z == -4.5 }.castShadow.unsafeCast<Boolean>() shouldBe false
                spots.single { it.position.z == 8.6 }.castShadow.unsafeCast<Boolean>() shouldBe true
                stage.light(t = 62.0, house = 1.0, waiting = false)

                mapsMade()
                stage.shadows(ShadowQuality.High)
                freed shouldBe 7
                casting() shouldBe listOf(2048, 2048, 2048, 2048)
            } finally {
                stage.dispose()
            }
        }
    }

    "the look behind the band turns over while the show plays: the old one leaves with all it holds freed" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 75.0, aspect = 1.5, near = 1.0, far = 1000.0)
            val scene = three.createScene()
            val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = standInRenderer())

            try {
                val wall = scene.children.single { it.asDynamic().geometry?.parameters?.width == 60.0 }
                val floorFog = sheetsOf(scene)
                val freed = mutableListOf<String>()

                wall.asDynamic().material.addEventListener("dispose") { _: dynamic -> freed.add("wall") }
                floorFog.first().asDynamic().material.addEventListener("dispose") { _: dynamic -> freed.add("fog") }

                // the same look again changes nothing
                stage.look(StageLook.Wall)
                freed shouldBe emptyList()

                // the fence: the wall and the fog on the floor go, the mesh, the posts and the surface behind come,
                // and the haze stands upright behind the mesh
                stage.look(StageLook.Mesh)
                freed shouldBe listOf("wall", "fog")
                (wall in scene.children) shouldBe false
                scene.children.size shouldBe 17 - 1 + 3 + 12
                sheetsOf(scene).none { it in floorFog } shouldBe true
                sheetsOf(scene).all { it.rotation.x == 0.0 } shouldBe true
                stage.light(t = 62.0, house = 1.0, waiting = false)

                val fence = scene.children.filter { it.asDynamic().geometry?.parameters?.width == 60.0 }

                fence.size shouldBe 2

                for (part in fence) {
                    part.asDynamic().material.addEventListener("dispose") { _: dynamic -> freed.add("fence") }
                }

                // and back to the wall as it was made
                stage.look(StageLook.Wall)
                freed.count { it == "fence" } shouldBe 2
                scene.children.size shouldBe 17 + 12
                sheetsOf(scene).all { it.rotation.x != 0.0 } shouldBe true
                stage.light(t = 62.1, house = 1.0, waiting = false)
            } finally {
                stage.dispose()
            }
        }
    }

    "the mirror floor comes where it is chosen and the fog is medium or finer, and goes with all it holds" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 75.0, aspect = 1.5, near = 1.0, far = 1000.0)
            val scene = three.createScene()
            val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = standInRenderer())

            try {
                // the floor is the first thing on the stage
                val floor: dynamic = scene.children.first()
                val plain: dynamic = floor.material

                (floor.geometry.parameters.width == 240.0) shouldBe true
                (js("Object.prototype.hasOwnProperty").call(floor, "onBeforeRender") == true) shouldBe false

                stage.floor(FloorLook.Mirror)

                val polished: dynamic = floor.material
                var freed = 0

                (polished !== plain) shouldBe true
                (js("Object.prototype.hasOwnProperty").call(floor, "onBeforeRender") == true) shouldBe true
                scene.children.size shouldBe 17 + 12
                polished.addEventListener("dispose") { _: dynamic -> freed++ }

                // the fog at low takes the mirror with it, and gives the floor its own material back
                stage.fog(FogQuality.Low)
                freed shouldBe 1
                (floor.material === plain) shouldBe true
                (js("Object.prototype.hasOwnProperty").call(floor, "onBeforeRender") == true) shouldBe false

                // the fog at high again brings it back; the plain floor chosen takes it away
                stage.fog(FogQuality.High)
                (floor.material !== plain) shouldBe true
                stage.floor(FloorLook.Plain)
                (floor.material === plain) shouldBe true
            } finally {
                stage.dispose()
            }
        }
    }

    "the mirror draws the scene from below the floor into a picture of its own, without the floor, the sky and the fog" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 35.0, aspect = 1.6, near = 0.1, far = 110.0)
            val renderer = standInRenderer()
            val r: dynamic = renderer
            var target: dynamic = "the screen"
            val drawn = mutableListOf<dynamic>()
            val scene = three.createScene()

            r.domElement = js("({ width: 1280, height: 800 })")
            r.autoClear = true
            r.state = js("({ buffers: { depth: { setMask: function () {} } } })")
            r.shadowMap.autoUpdate = true
            r.getRenderTarget = { target }
            r.setRenderTarget = { to: dynamic -> target = to }
            r.render = { what: dynamic, seenBy: dynamic ->
                val floor: dynamic = scene.children.first()
                val fog = sheetsOf(scene)

                drawn.add(
                    listOf(
                        target,
                        seenBy.position.y,
                        floor.visible,
                        fog.any { it.visible },
                        r.shadowMap.autoUpdate,
                        what === scene,
                    )
                )
            }

            val show = Show(seed = 1, switches = Switches.parse("?floor=mirror"))
            val stage = Stage(show = show, scene = scene, camera = camera, renderer = renderer)

            try {
                val floor: dynamic = scene.children.first()

                camera.position.set(0.0, 2.5, 12.0)
                camera.lookAt(0.0, 1.0, 0.0)
                camera.asDynamic().updateMatrixWorld()
                floor.onBeforeRender(renderer, scene, camera)

                drawn.size shouldBe 1

                val seen: List<dynamic> = drawn.single().unsafeCast<List<dynamic>>()
                val picture: dynamic = seen[0]
                val height: dynamic = seen[1]
                val floorShown: dynamic = seen[2]
                val fogShown: dynamic = seen[3]
                val shadowsDrawn: dynamic = seen[4]
                val whole: dynamic = seen[5]

                (picture !== "the screen") shouldBe true
                (picture.width.unsafeCast<Int>() to picture.height.unsafeCast<Int>()) shouldBe (640 to 400)
                (picture.depthTexture != null) shouldBe true
                // the camera mirrored in the floor, the floor and the fog on it hidden, no shadows drawn anew
                height.unsafeCast<Double>() shouldBe (-2.5 plusOrMinus 1e-9)
                floorShown.unsafeCast<Boolean>() shouldBe false
                fogShown.unsafeCast<Boolean>() shouldBe false
                shadowsDrawn.unsafeCast<Boolean>() shouldBe false
                whole.unsafeCast<Boolean>() shouldBe true

                // everything as it was after it
                (target === "the screen") shouldBe true
                (floor.visible == true) shouldBe true
                sheetsOf(scene).all { it.visible } shouldBe true
                (r.shadowMap.autoUpdate == true) shouldBe true

                // the same size again: the same picture; it is freed with the stage
                var freed = 0

                picture.addEventListener("dispose") { _: dynamic -> freed++ }
                floor.onBeforeRender(renderer, scene, camera)
                (drawn[1].unsafeCast<List<dynamic>>()[0] === picture) shouldBe true
                stage.dispose()
                freed shouldBe 1
            } finally {
                stage.dispose()
            }
        }
    }

    "the shadows start at the address's quality" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 75.0, aspect = 1.5, near = 1.0, far = 1000.0)
            val scene = three.createScene()
            val show = Show(seed = 1, switches = Switches.parse("?shadows=low"))
            val stage = Stage(show = show, scene = scene, camera = camera, renderer = standInRenderer())

            try {
                val spots = scene.children.filter { it.asDynamic().isSpotLight == true }.map { it.asDynamic() }

                spots.filter { it.castShadow == true }.map { it.shadow.mapSize.x.unsafeCast<Int>() } shouldBe
                    listOf(1024, 1024, 1024)
            } finally {
                stage.dispose()
            }
        }
    }
})

/** The fog's sheets in [scene]: what reads the fog's maps, or works it out at its own height. */
private fun sheetsOf(scene: Scene): List<Object3D> = scene.children.filter {
    val uniforms: dynamic = it.asDynamic().material?.uniforms

    uniforms?.uMaps != null || uniforms?.uHeight != null
}
