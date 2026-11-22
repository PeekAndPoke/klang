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
import io.peekandpoke.klang.viz.liveact.lights.FrontLight
import io.peekandpoke.klang.viz.liveact.lights.ShadowQuality
import io.peekandpoke.klang.viz.liveact.stage.AroundLook
import io.peekandpoke.klang.viz.liveact.stage.BackWall
import io.peekandpoke.klang.viz.liveact.stage.Fence
import io.peekandpoke.klang.viz.liveact.stage.FloorLook
import io.peekandpoke.klang.viz.liveact.stage.FogQuality
import io.peekandpoke.klang.viz.liveact.stage.HazeLook
import io.peekandpoke.klang.viz.liveact.stage.LookVariants
import io.peekandpoke.klang.viz.liveact.stage.ReflectionQuality
import io.peekandpoke.klang.viz.liveact.stage.Stage
import io.peekandpoke.klang.viz.liveact.stage.StageEdge
import io.peekandpoke.klang.viz.liveact.stage.StageLook
import io.peekandpoke.klang.viz.liveact.stage.UnderworldLook
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
import kotlinx.coroutines.delay
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

/** The kick's tag, as the stages here are given it (their shows have no song). */
private const val KICK = "kick"

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
                val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = standInRenderer(), kick = KICK)

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
            val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = renderer, kick = KICK)

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
                // floor, wall, riser and its glow on the floor, the lamps with their targets, the crowd's lights and the terraces they stand
                // on, the night sky; and the fog's eight sheets (high)
                scene.children.size shouldBe 18 + 8

                // a frame's light step runs on it, before the song and in it
                stage.light(t = -5.0, house = 0.07, waiting = true)
                stage.light(t = 62.0, house = 1.0, waiting = false)

                // the sky goes with the camera, once the stage is told where the camera stands this frame: its
                // stars, its moon and its shooting star are the one group in the scene that holds stars (the riser is
                // a group of three too, of stone)
                val sky = scene.children.single { it.children.size == 3 && it.children.any { child -> child.asDynamic().isPoints == true } }

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
            val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = renderer, kick = KICK)
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

                sheets.size shouldBe 8
                (picture === drawnInto[0]) shouldBe true
                picture.addEventListener("dispose") { _: dynamic -> freed++ }
            } finally {
                stage.dispose()
            }

            freed shouldBe 1

            drawnInto.clear()

            val show = Show(seed = 1, switches = Switches.parse("?fullfog"))
            val other = Stage(show = show, scene = three.createScene(), camera = camera, renderer = renderer, kick = KICK)

            try {
                other.light(t = 62.0, house = 1.0, waiting = false)
                drawnInto.size shouldBe 0
            } finally {
                other.dispose()
            }

            // a card that cannot hold the maps: every sheet works the fog out for itself, and all eight are there
            r.extensions = js("({ has: function () { return false } })")

            val plain = three.createScene()
            val third = Stage(show = Show(seed = 1), scene = plain, camera = camera, renderer = renderer, kick = KICK)

            try {
                third.light(t = 62.0, house = 1.0, waiting = false)
                drawnInto.size shouldBe 0
                plain.children.count { it.asDynamic().material?.uniforms?.uHeight != null } shouldBe 8
            } finally {
                third.dispose()
            }
        }
    }

    "the fog starts at the address's quality: as many sheets, and none with ?nofog or ?fog=off" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 75.0, aspect = 1.5, near = 1.0, far = 1000.0)

            for ((query, sheets) in listOf("?fog=low" to 4, "?fog=high" to 8, "?fog=xhigh" to 8, "?nofog" to 0, "?fog=off" to 0, "" to 8)) {
                val scene = three.createScene()
                val show = Show(seed = 1, switches = Switches.parse(query))
                val stage = Stage(show = show, scene = scene, camera = camera, renderer = standInRenderer(), kick = KICK)

                try {
                    sheetsOf(scene).size shouldBe sheets
                    scene.children.size shouldBe 18 + sheets
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
            val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = renderer, kick = KICK)

            try {
                val high = sheetsOf(scene)
                val freed = mutableListOf<String>()
                val picture: dynamic = high.first().asDynamic().material.uniforms.uMaps.value.renderTarget

                high.size shouldBe 8

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
                freed.count { it == "material" } shouldBe 8
                freed.count { it == "geometry" } shouldBe 1
                freed.count { it == "maps" } shouldBe 1

                // four sheets, none of the old, reading a picture of 2 by 2 maps
                val low = sheetsOf(scene)
                val maps: dynamic = low.first().asDynamic().material.uniforms.uMaps.value.renderTarget

                low.size shouldBe 4
                low.none { it in high } shouldBe true
                scene.children.size shouldBe 18 + 4
                (maps.width.unsafeCast<Int>() to maps.height.unsafeCast<Int>()) shouldBe (2 * 384 to 2 * 324)
                low.map { it.asDynamic().material.uniforms.uCell.value.x.unsafeCast<Double>() } shouldBe listOf(0.0, 1.0, 0.0, 1.0)
                stage.light(t = 62.0, house = 1.0, waiting = false)

                // off: no sheet at all; and back to a fog
                stage.fog(FogQuality.Off)
                scene.children.size shouldBe 18
                stage.light(t = 62.1, house = 1.0, waiting = false)
                stage.fog(FogQuality.High)
                sheetsOf(scene).size shouldBe 8
            } finally {
                stage.dispose()
            }

            scene.children.size shouldBe 18
        }
    }

    "a fog made anew frees its noise too, where every sheet works the fog out for itself" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 75.0, aspect = 1.5, near = 1.0, far = 1000.0)
            val scene = three.createScene()
            val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = standInRenderer(), kick = KICK)
            var freed = 0

            try {
                val noise: dynamic = sheetsOf(scene).first().asDynamic().material.uniforms.uNoise.value

                noise.addEventListener("dispose") { _: dynamic -> freed++ }
                stage.fog(FogQuality.Low)
                freed shouldBe 1
                sheetsOf(scene).size shouldBe 4
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
            val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = standInRenderer(), kick = KICK)
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

    "the look behind the band turns over while the show plays: the old one is hidden and kept, and freed with the stage" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 75.0, aspect = 1.5, near = 1.0, far = 1000.0)
            val scene = three.createScene()
            val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = standInRenderer(), kick = KICK)
            val freed = mutableListOf<String>()

            try {
                val wall = scene.children.single { it.asDynamic().geometry?.parameters?.width == 60.0 }
                val floorFog = sheetsOf(scene)

                wall.asDynamic().material.addEventListener("dispose") { _: dynamic -> freed.add("wall") }
                floorFog.first().asDynamic().material.addEventListener("dispose") { _: dynamic -> freed.add("fog") }

                // the same look again changes nothing
                stage.look(StageLook.Wall)
                freed shouldBe emptyList()

                // the fence: the wall and the fog on the floor are hidden, the mesh, the posts and the surface behind
                // come, and the haze stands upright behind the mesh
                stage.look(StageLook.Mesh)
                freed shouldBe emptyList()
                wall.visible shouldBe false
                floorFog.none { it.visible } shouldBe true
                scene.children.size shouldBe 18 + 8 + 3 + 8

                val haze = sheetsOf(scene).filter { it !in floorFog }

                haze.size shouldBe 8
                haze.all { it.rotation.x == 0.0 && it.visible } shouldBe true
                stage.light(t = 62.0, house = 1.0, waiting = false)

                val fence = scene.children.filter { it.asDynamic().geometry?.parameters?.width == 60.0 && it !== wall }

                fence.size shouldBe 2

                for (part in fence) {
                    part.asDynamic().material.addEventListener("dispose") { _: dynamic -> freed.add("fence") }
                }

                haze.first().asDynamic().material.addEventListener("dispose") { _: dynamic -> freed.add("haze") }

                // and back to the wall as it was made: the same wall and fog shown again, nothing new made or freed
                stage.look(StageLook.Wall)
                freed shouldBe emptyList()
                wall.visible shouldBe true
                floorFog.all { it.visible } shouldBe true
                fence.none { it.visible } shouldBe true
                haze.none { it.visible } shouldBe true
                scene.children.size shouldBe 18 + 8 + 3 + 8
                stage.light(t = 62.1, house = 1.0, waiting = false)

                // and to the fence again: the one made before
                stage.look(StageLook.Mesh)
                fence.all { it.visible } shouldBe true
                haze.all { it.visible } shouldBe true
                scene.children.size shouldBe 18 + 8 + 3 + 8
            } finally {
                stage.dispose()
            }

            // the stage frees both fogs; the wall and the fence are the scene's to free
            freed.sorted() shouldBe listOf("fog", "haze")
        }
    }

    "the surroundings: none in the look as made; with ?around a group of three, the sides only before the fence; kept while off; freed" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 75.0, aspect = 1.5, near = 1.0, far = 1000.0)
            val plainScene = three.createScene()
            val plain = Stage(show = Show(seed = 1), scene = plainScene, camera = camera, renderer = standInRenderer(), kick = KICK)

            fun groups(scene: Scene): List<Object3D> =
                scene.children.filter { group ->
                    group.children.size == 3 && group.children.all { it.asDynamic().material?.uniforms?.uPick != null }
                }

            try {
                groups(plainScene) shouldBe emptyList()
            } finally {
                plain.dispose()
            }

            val scene = three.createScene()
            val stage = Stage(
                show = Show(seed = 1, switches = Switches.parse("?around=ruins")),
                scene = scene,
                camera = camera,
                renderer = standInRenderer(),
                kick = KICK,
            )
            val freed = mutableListOf<String>()

            try {
                // floor, wall, riser and its glow, the lamps and their targets, the crowd, the terraces, the sky, the fog's
                // eight sheets, and the surroundings as one group
                scene.children.size shouldBe 18 + 8 + 1

                val around = groups(scene).single()
                val (left, right, far) = around.children.toList()

                fun pick(): List<Double> {
                    val p: dynamic = left.asDynamic().material.uniforms.uPick.value

                    return listOf(p.x.unsafeCast<Double>(), p.y.unsafeCast<Double>(), p.z.unsafeCast<Double>())
                }

                fun shown(): Double = left.asDynamic().material.uniforms.uShow.value.unsafeCast<Double>()

                // beside the stage and round the audience: each the near shapes and the ones further back
                val sides: dynamic = left.asDynamic().material.uniforms

                val round: dynamic = far.asDynamic().material.uniforms

                for (picture in listOf(sides.uNear.value, sides.uDeep.value, round.uMap.value, round.uDeep.value, round.uRelief.value)) {
                    picture.addEventListener("dispose") { _: dynamic -> freed.add("picture") }
                }

                // the ruins, held: their channel, there in full; before the wall the sides are hidden, the horizon is not
                stage.light(t = 62.0, house = 1.0, waiting = false)
                pick() shouldBe listOf(0.0, 1.0, 0.0)
                shown() shouldBe 1.0
                listOf(left.visible, right.visible, far.visible) shouldBe listOf(false, false, true)

                // the fence: the sides come
                stage.look(StageLook.Mesh)
                stage.light(t = 62.1, house = 1.0, waiting = false)
                listOf(left.visible, right.visible, far.visible) shouldBe listOf(true, true, true)

                // off: hidden and kept; on again, the stones: the same group, nothing new made
                stage.around(AroundLook.Off)
                around.visible shouldBe false
                stage.around(AroundLook.Stones)
                stage.light(t = 62.2, house = 1.0, waiting = false)
                around.visible shouldBe true
                groups(scene).single() shouldBe around
                pick() shouldBe listOf(0.0, 0.0, 1.0)
                freed shouldBe emptyList()
            } finally {
                stage.dispose()
            }

            // the four pictures freed, and the group out of the scene
            freed shouldBe listOf("picture", "picture", "picture", "picture", "picture")
            groups(scene) shouldBe emptyList()
        }
    }

    "the mirror floor comes where it is chosen and the reflections are on, at every fog; goes off the floor; freed with the stage" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 75.0, aspect = 1.5, near = 1.0, far = 1000.0)
            val scene = three.createScene()
            val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = standInRenderer(), kick = KICK)
            var freed = 0

            try {
                // the floor is the first thing on the stage
                val floor: dynamic = scene.children.first()
                val plain: dynamic = floor.material

                (floor.geometry.parameters.width == 240.0) shouldBe true
                (js("Object.prototype.hasOwnProperty").call(floor, "onBeforeRender") == true) shouldBe false

                stage.floor(FloorLook.Mirror)

                val polished: dynamic = floor.material

                (polished !== plain) shouldBe true
                (js("Object.prototype.hasOwnProperty").call(floor, "onBeforeRender") == true) shouldBe true
                scene.children.size shouldBe 18 + 8
                polished.addEventListener("dispose") { _: dynamic -> freed++ }

                // the fog has no say in it: at low and off the mirror stays on the floor
                for (fog in listOf(FogQuality.Low, FogQuality.Off, FogQuality.High)) {
                    stage.fog(fog)
                    (floor.material === polished) shouldBe true
                    (js("Object.prototype.hasOwnProperty").call(floor, "onBeforeRender") == true) shouldBe true
                }

                // the reflections off take the mirror off the floor, and give the floor its own material back; the
                // mirror's material is kept for the next time
                stage.reflections(ReflectionQuality.Off)
                freed shouldBe 0
                (floor.material === plain) shouldBe true
                (js("Object.prototype.hasOwnProperty").call(floor, "onBeforeRender") == true) shouldBe false

                // on again, the same one comes back; the plain floor chosen takes it off
                stage.reflections(ReflectionQuality.On)
                (floor.material === polished) shouldBe true
                (js("Object.prototype.hasOwnProperty").call(floor, "onBeforeRender") == true) shouldBe true
                stage.floor(FloorLook.Plain)
                (floor.material === plain) shouldBe true
                (js("Object.prototype.hasOwnProperty").call(floor, "onBeforeRender") == true) shouldBe false
                freed shouldBe 0
            } finally {
                stage.dispose()
            }

            freed shouldBe 1
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
            val stage = Stage(show = show, scene = scene, camera = camera, renderer = renderer, kick = KICK)

            try {
                val floor: dynamic = scene.children.first()

                camera.position.set(0.0, 2.5, 12.0)
                camera.lookAt(0.0, 1.0, 0.0)
                camera.asDynamic().updateMatrixWorld()
                floor.onBeforeRender(renderer, scene, camera)

                // the mirror, then the blur's widths from its depth (the next case)
                drawn.size shouldBe 2

                val seen: List<dynamic> = drawn.first().unsafeCast<List<dynamic>>()
                val picture: dynamic = seen[0]
                val height: dynamic = seen[1]
                val floorShown: dynamic = seen[2]
                val fogShown: dynamic = seen[3]
                val shadowsDrawn: dynamic = seen[4]
                val whole: dynamic = seen[5]

                (picture !== "the screen") shouldBe true
                // the screen's own pixels (a canvas laid out nowhere has no page size to cap them by)
                (picture.width.unsafeCast<Int>() to picture.height.unsafeCast<Int>()) shouldBe (1280 to 800)
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
                (drawn[2].unsafeCast<List<dynamic>>()[0] === picture) shouldBe true
                stage.dispose()
                freed shouldBe 1
            } finally {
                stage.dispose()
            }
        }
    }

    "at fog low the mirror is drawn from the start, and hides the fog that lies on the floor then, also one made anew" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 35.0, aspect = 1.6, near = 0.1, far = 110.0)
            val renderer = standInRenderer()
            val r: dynamic = renderer
            var target: dynamic = "the screen"
            val scene = three.createScene()
            // for each drawing of the scene itself: the fog's sheets there, and how many of them were shown
            val fogSeen = mutableListOf<Pair<Int, Int>>()

            r.domElement = js("({ width: 1280, height: 800 })")
            r.autoClear = true
            r.state = js("({ buffers: { depth: { setMask: function () {} } } })")
            r.shadowMap.autoUpdate = true
            r.getRenderTarget = { target }
            r.setRenderTarget = { to: dynamic -> target = to }
            r.render = { what: dynamic, _: dynamic ->
                if (what === scene) {
                    val fog = sheetsOf(scene)

                    fogSeen.add(Pair(fog.size, fog.count { it.visible }))
                }
            }

            val show = Show(seed = 1, switches = Switches.parse("?floor=mirror&fog=low"))
            val stage = Stage(show = show, scene = scene, camera = camera, renderer = renderer, kick = KICK)

            try {
                val floor: dynamic = scene.children.first()

                camera.position.set(0.0, 2.5, 12.0)
                camera.lookAt(0.0, 1.0, 0.0)
                camera.asDynamic().updateMatrixWorld()
                (js("Object.prototype.hasOwnProperty").call(floor, "onBeforeRender") == true) shouldBe true
                floor.onBeforeRender(renderer, scene, camera)

                // the mirror drew the scene with the four sheets of the fog at low hidden, and shows them again after it
                fogSeen shouldBe listOf(Pair(4, 0))
                sheetsOf(scene).count { it.visible } shouldBe 4

                // the fog made anew at high: its eight new sheets are the ones hidden now
                stage.fog(FogQuality.High)
                fogSeen.clear()
                floor.onBeforeRender(renderer, scene, camera)
                fogSeen shouldBe listOf(Pair(8, 0))
                sheetsOf(scene).count { it.visible } shouldBe 8
            } finally {
                stage.dispose()
            }
        }
    }

    "?reflections=off: the mirror floor the look chose is not put on the floor until the reflections are on" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 75.0, aspect = 1.5, near = 1.0, far = 1000.0)
            val scene = three.createScene()
            val show = Show(seed = 1, switches = Switches.parse("?floor=mirror&reflections=off"))
            val stage = Stage(show = show, scene = scene, camera = camera, renderer = standInRenderer(), kick = KICK)

            try {
                val floor: dynamic = scene.children.first()
                val plain: dynamic = floor.material

                (js("Object.prototype.hasOwnProperty").call(floor, "onBeforeRender") == true) shouldBe false

                stage.reflections(ReflectionQuality.On)
                (floor.material !== plain) shouldBe true
                (js("Object.prototype.hasOwnProperty").call(floor, "onBeforeRender") == true) shouldBe true
            } finally {
                stage.dispose()
            }
        }
    }

    "the stage's edge stands where the fence and the wall stand" {
        Fence.MESH_Z shouldBe StageEdge.BACK
        BackWall.Z shouldBe StageEdge.BACK
    }

    "the mirror draws only the box of the stage's floor the camera sees, and nothing where it sees none of it" {
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
            r.getRenderTarget = { target }
            r.setRenderTarget = { to: dynamic -> target = to }
            r.render = { _: dynamic, _: dynamic -> drawn.add(target) }

            val show = Show(seed = 1, switches = Switches.parse("?floor=mirror"))
            val stage = Stage(show = show, scene = scene, camera = camera, renderer = renderer, kick = KICK)
            val floor: dynamic = scene.children.first()

            fun frame(lookAt: Triple<Double, Double, Double>) {
                drawn.clear()
                camera.position.set(0.0, 2.5, 12.0)
                camera.lookAt(lookAt.first, lookAt.second, lookAt.third)
                camera.asDynamic().updateMatrixWorld()
                floor.onBeforeRender(renderer, scene, camera)
            }

            try {
                // the stage before the camera, below the horizon: the mirror and the widths drawn, each only within
                // the box the stage's floor fills on the screen, which leaves out the sky above it
                frame(Triple(0.0, 1.0, 0.0))
                drawn.size shouldBe 2

                val picture: dynamic = drawn[0]
                val cut: dynamic = picture.scissor

                (picture.scissorTest == true) shouldBe true
                (drawn[1].scissorTest == true) shouldBe true
                (cut.y.unsafeCast<Double>() >= 0.0) shouldBe true
                (cut.y.unsafeCast<Double>() + cut.w.unsafeCast<Double>() < 800.0) shouldBe true

                // the camera turned to the audience, the stage behind it: nothing of it seen, nothing drawn
                frame(Triple(0.0, 2.5, 40.0))
                drawn.size shouldBe 0
            } finally {
                stage.dispose()
            }
        }
    }

    "the blur's widths are drawn from the mirror's depth into a picture of their own, freed with the mirror's" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 35.0, aspect = 1.6, near = 0.1, far = 110.0)
            val renderer = standInRenderer()
            val r: dynamic = renderer
            var target: dynamic = "the screen"
            val drawn = mutableListOf<Pair<dynamic, dynamic>>()
            val scene = three.createScene()

            r.domElement = js("({ width: 1280, height: 800 })")
            r.autoClear = true
            r.state = js("({ buffers: { depth: { setMask: function () {} } } })")
            r.getRenderTarget = { target }
            r.setRenderTarget = { to: dynamic -> target = to }
            // (a Pair made by hand: `to` on a dynamic would be looked for on the object)
            r.render = { what: dynamic, _: dynamic -> drawn.add(Pair(target, what)) }

            val show = Show(seed = 1, switches = Switches.parse("?floor=mirror"))
            val stage = Stage(show = show, scene = scene, camera = camera, renderer = renderer, kick = KICK)
            val floor: dynamic = scene.children.first()

            fun frame() {
                drawn.clear()
                floor.onBeforeRender(renderer, scene, camera)
            }

            try {
                camera.position.set(0.0, 2.5, 12.0)
                camera.lookAt(0.0, 1.0, 0.0)
                camera.asDynamic().updateMatrixWorld()
                frame()

                // the mirror first, then the widths: into a picture half the mirror's size each way, without a depth
                // of its own, from a scene of their own that reads the mirror's depth; the screen's again after it
                drawn.size shouldBe 2

                val picture: dynamic = drawn[0].first
                val widths: dynamic = drawn[1].first
                val pass: dynamic = drawn[1].second
                val shape: dynamic = pass.children[0]

                (drawn[0].second === scene) shouldBe true
                (pass !== scene) shouldBe true
                (widths !== picture) shouldBe true
                (widths.width.unsafeCast<Int>() to widths.height.unsafeCast<Int>()) shouldBe (640 to 400)
                (widths.depthTexture == null) shouldBe true
                (shape.material.uniforms.uMirrorDepth.value === picture.depthTexture) shouldBe true
                (target === "the screen") shouldBe true

                // the same size again: the same widths' picture
                frame()
                (drawn[1].first === widths) shouldBe true

                var widthsFreed = 0
                var drawingFreed = 0
                var shapeFreed = 0

                widths.addEventListener("dispose") { _: dynamic -> widthsFreed++ }
                shape.material.addEventListener("dispose") { _: dynamic -> drawingFreed++ }
                shape.geometry.addEventListener("dispose") { _: dynamic -> shapeFreed++ }

                // the fog at low leaves the mirror as it is: the same widths' picture
                stage.fog(FogQuality.Low)
                frame()
                (drawn[1].first === widths) shouldBe true
                widthsFreed shouldBe 0

                // the reflections off take the mirror off: the widths' picture goes with the mirror's, the drawing is kept
                stage.reflections(ReflectionQuality.Off)
                widthsFreed shouldBe 1
                drawingFreed shouldBe 0
                shapeFreed shouldBe 0

                // on again: a new widths' picture, drawn by the same drawing
                stage.reflections(ReflectionQuality.On)
                frame()

                val again: dynamic = drawn[1].first

                (again !== widths) shouldBe true
                (drawn[1].second === pass) shouldBe true
                again.addEventListener("dispose") { _: dynamic -> widthsFreed++ }

                // the stage given up: the picture, the drawing and its shape freed
                stage.dispose()
                widthsFreed shouldBe 2
                drawingFreed shouldBe 1
                shapeFreed shouldBe 1
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
            val stage = Stage(show = show, scene = scene, camera = camera, renderer = standInRenderer(), kick = KICK)

            try {
                val spots = scene.children.filter { it.asDynamic().isSpotLight == true }.map { it.asDynamic() }

                spots.filter { it.castShadow == true }.map { it.shadow.mapSize.x.unsafeCast<Int>() } shouldBe
                    listOf(1024, 1024, 1024)
            } finally {
                stage.dispose()
            }
        }
    }

    "the haze gives way to the band's shadows behind the fence with ?haze=parts, once the lamp has a map; it closes slowly" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 35.0, aspect = 1.6, near = 0.1, far = 110.0)
            val scene = three.createScene()
            val show = Show(seed = 1, switches = Switches.parse("?stage=mesh&haze=parts&front=1"))
            val stage = Stage(show = show, scene = scene, camera = camera, renderer = standInRenderer(), kick = KICK)
            val front: dynamic = scene.children.single { it.asDynamic().isSpotLight == true && it.position.z == 8.6 }

            /** How far each sheet of the haze has drawn back. */
            fun parts(): List<Double> = sheetsOf(scene).map { it.asDynamic().material.uniforms.uParts.value.unsafeCast<Double>() }

            try {
                // the lamp is on, but has no map yet: nothing parts
                stage.light(t = 62.0, house = 1.0, waiting = false)
                parts().size shouldBe 8
                parts().all { it == 0.0 } shouldBe true

                // a map as three.js would have made it: the haze opens with the lamp, reading that map
                val depth: dynamic = js("({})")

                front.shadow.map = js("({})")
                front.shadow.map.depthTexture = depth
                delay(150)
                stage.light(t = 62.1, house = 1.0, waiting = false)

                val opened = parts().first()

                opened shouldBeGreaterThan 0.0
                parts().all { it == opened } shouldBe true
                (sheetsOf(scene).first().asDynamic().material.uniforms.uFrontShadow.value === depth) shouldBe true
                (sheetsOf(scene).first().asDynamic().material.uniforms.uFrontShadowMatrix.value === front.shadow.matrix) shouldBe true

                // turned back (the key h): it closes over a second or two, not in one frame
                stage.haze(HazeLook.Whole)
                delay(30)
                stage.light(t = 62.2, house = 1.0, waiting = false)

                val closing = parts().first()

                closing shouldBeGreaterThan 0.0
                (closing < opened) shouldBe true
            } finally {
                front.shadow.map = null
                stage.dispose()
            }
        }
    }

    "the front lamp's shadow keeps being drawn while the haze's clearing fades after the lamp went dark, and stops after" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 35.0, aspect = 1.6, near = 0.1, far = 110.0)
            val scene = three.createScene()
            val show = Show(seed = 1, switches = Switches.parse("?stage=mesh&haze=parts&front=1"))
            val stage = Stage(show = show, scene = scene, camera = camera, renderer = standInRenderer(), kick = KICK)
            val front: dynamic = scene.children.single { it.asDynamic().isSpotLight == true && it.position.z == 8.6 }

            /** Whether three.js would draw the front lamp's shadow this frame. */
            fun drawn(): Boolean = front.shadow.autoUpdate == true

            try {
                // the lamp on, its shadow made: drawn, and the haze opens
                front.shadow.map = js("({ depthTexture: {} })")
                stage.light(t = 62.0, house = 1.0, waiting = false)
                delay(200)
                stage.light(t = 62.2, house = 1.0, waiting = false)
                drawn() shouldBe true

                // the lamp dark (waiting: the lamp is off): the clearing still fades and reads its shadow, so it is drawn
                stage.light(t = 62.3, house = 1.0, waiting = true)
                (front.intensity.unsafeCast<Double>() == 0.0) shouldBe true
                drawn() shouldBe true

                // the clearing closed (some 1.6 s): a dark lamp's shadow is not drawn any more
                repeat(12) {
                    delay(200)
                    stage.light(t = 62.4, house = 1.0, waiting = true)
                }
                drawn() shouldBe false
            } finally {
                front.shadow.map = null
                stage.dispose()
            }
        }
    }

    "the underworld comes into the mirror over about two seconds and goes over three; without the mirror drawn it never comes" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 35.0, aspect = 1.6, near = 0.1, far = 110.0)
            val mirrored = Stage(
                show = Show(seed = 1, switches = Switches.parse("?floor=mirror&underworld=on")),
                scene = three.createScene(),
                camera = camera,
                renderer = standInRenderer(),
                kick = KICK,
            )
            val plain = Stage(
                show = Show(seed = 1, switches = Switches.parse("?underworld=on")),
                scene = three.createScene(),
                camera = camera,
                renderer = standInRenderer(),
                kick = KICK,
            )
            val unreflected = Stage(
                show = Show(seed = 1, switches = Switches.parse("?floor=mirror&underworld=on&reflections=off")),
                scene = three.createScene(),
                camera = camera,
                renderer = standInRenderer(),
                kick = KICK,
            )

            try {
                mirrored.light(t = 62.0, house = 1.0, waiting = false)
                plain.light(t = 62.0, house = 1.0, waiting = false)
                unreflected.light(t = 62.0, house = 1.0, waiting = false)
                delay(200)
                mirrored.light(t = 62.1, house = 1.0, waiting = false)
                plain.light(t = 62.1, house = 1.0, waiting = false)
                unreflected.light(t = 62.1, house = 1.0, waiting = false)

                val coming = mirrored.underworld

                coming shouldBeGreaterThan 0.0
                (coming < 0.25) shouldBe true
                plain.underworld shouldBe 0.0
                unreflected.underworld shouldBe 0.0

                // turned off (the key v): it goes, more slowly than it came
                mirrored.underworld(UnderworldLook.Off)
                delay(200)
                mirrored.light(t = 62.2, house = 1.0, waiting = false)

                val going = mirrored.underworld

                (going < coming) shouldBe true
                going shouldBeGreaterThan 0.0
            } finally {
                mirrored.dispose()
                plain.dispose()
                unreflected.dispose()
            }
        }
    }

    "the whole look turned at once (the key x): the fence, the mirror and the haze behind it; and back" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 35.0, aspect = 1.6, near = 0.1, far = 110.0)
            val scene = three.createScene()
            val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = standInRenderer(), kick = KICK)

            try {
                val floor: dynamic = scene.children.first()
                val plain: dynamic = floor.material
                val wall = scene.children.single { it.asDynamic().geometry?.parameters?.width == 60.0 }

                stage.turn(LookVariants.NEW)
                stage.shadowsAt shouldBe Fence.BACK_Z
                wall.visible shouldBe false
                (floor.material !== plain) shouldBe true
                stage.light(t = 62.0, house = 1.0, waiting = false)

                stage.turn(LookVariants())
                stage.shadowsAt shouldBe BackWall.Z
                wall.visible shouldBe true
                (floor.material === plain) shouldBe true
                stage.light(t = 62.1, house = 1.0, waiting = false)
            } finally {
                stage.dispose()
            }
        }
    }

    "without ?haze=parts, or before the wall, the haze never reads the lamp's shadows" {
        withThree { three ->
            Three.addon = three

            for (query in listOf("?stage=mesh&front=1", "?haze=parts&front=1")) {
                val camera = three.createPerspectiveCamera(fov = 35.0, aspect = 1.6, near = 0.1, far = 110.0)
                val scene = three.createScene()
                val show = Show(seed = 1, switches = Switches.parse(query))
                val stage = Stage(show = show, scene = scene, camera = camera, renderer = standInRenderer(), kick = KICK)
                val front: dynamic = scene.children.single { it.asDynamic().isSpotLight == true && it.position.z == 8.6 }

                try {
                    front.shadow.map = js("({ depthTexture: {} })")
                    stage.light(t = 62.0, house = 1.0, waiting = false)
                    delay(100)
                    stage.light(t = 62.1, house = 1.0, waiting = false)
                    sheetsOf(scene).size shouldBe 8
                    sheetsOf(scene).all { it.asDynamic().material.uniforms.uParts.value == 0.0 } shouldBe true
                } finally {
                    front.shadow.map = null
                    stage.dispose()
                }
            }
        }
    }

    "behind the fence the haze holds the lamp's map even where it does not part, and lets go of a map the lamp gave up" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 35.0, aspect = 1.6, near = 0.1, far = 110.0)
            val scene = three.createScene()
            val show = Show(seed = 1, switches = Switches.parse("?stage=mesh&front=1"))
            val stage = Stage(show = show, scene = scene, camera = camera, renderer = standInRenderer(), kick = KICK)
            val front: dynamic = scene.children.single { it.asDynamic().isSpotLight == true && it.position.z == 8.6 }

            /** What each sheet of the haze reads as the lamp's map. */
            fun held(): List<dynamic> = sheetsOf(scene).map { it.asDynamic().material.uniforms.uFrontShadow.value }

            try {
                // an empty shadow sampler all the time took the front lamp's light off the fence (the stills of
                // 2026-11-20): the haze reads the lamp's own map as soon as there is one
                val depth: dynamic = js("({})")

                front.shadow.map = js("({})")
                front.shadow.map.depthTexture = depth
                stage.light(t = 62.0, house = 1.0, waiting = false)
                held().all { it === depth } shouldBe true
                sheetsOf(scene).all { it.asDynamic().material.uniforms.uParts.value == 0.0 } shouldBe true

                // the map given up (another shadow quality): not kept, three.js would make a freed one anew
                front.shadow.map = null
                stage.light(t = 62.1, house = 1.0, waiting = false)
                held().all { it == null } shouldBe true
            } finally {
                front.shadow.map = null
                stage.dispose()
            }
        }
    }

    "the front lamp's shadows land on the wall, or behind the fence on the surface there; the stage says which and the lamp" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 35.0, aspect = 1.6, near = 0.1, far = 110.0)
            val scene = three.createScene()
            val show = Show(seed = 1, switches = Switches.parse("?front=1"))
            val stage = Stage(show = show, scene = scene, camera = camera, renderer = standInRenderer(), kick = KICK)

            try {
                stage.shadowsAt shouldBe -8.0
                stage.front shouldBe FrontLight.off
                stage.light(t = 62.0, house = 1.0, waiting = false)
                stage.front shouldBe FrontLight(level = 1.0, strobing = false)
                listOf(stage.frontLamp.x, stage.frontLamp.y, stage.frontLamp.z) shouldBe listOf(0.0, 0.25, 8.6)
                stage.look(StageLook.Mesh)
                stage.shadowsAt shouldBe -10.5
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
