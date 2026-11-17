/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.stage.CrowdPlaces
import io.peekandpoke.klang.viz.liveact.stage.Stage
import io.peekandpoke.klang.workshop.threejs.fog
import io.peekandpoke.klang.workshop.threejs.isLight
import io.peekandpoke.kraft.addons.registry.AddonRegistry.Companion.addons
import io.peekandpoke.kraft.addons.registry.addons
import io.peekandpoke.kraft.addons.threejs.ThreeJsAddon
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
})
