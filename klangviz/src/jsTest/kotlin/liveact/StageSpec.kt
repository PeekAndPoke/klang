/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
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
 * things on its renderer.
 */
private fun standInRenderer(): WebGLRenderer {
    val renderer: dynamic = js("({ shadowMap: {}, domElement: { height: 520 }, setPixelRatio: function () {} })")

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
                camera.far shouldBe 60.0
                scene.fog.shouldNotBeNull().far shouldBe 22.0
                renderer.asDynamic().shadowMap.enabled.unsafeCast<Boolean>() shouldBe true

                var lamps = 0
                scene.traverse { if (it.isLight) lamps++ }

                // the ambient light, four lamps above and the one low at the front
                lamps shouldBe 6
                // floor, wall, riser, the lamps with their targets, the crowd; the fog's sheets are not among them
                scene.children.size shouldBe 15

                // a frame's light step runs on it, before the song and in it
                stage.light(t = -5.0, house = 0.07, waiting = true)
                stage.light(t = 62.0, house = 1.0, waiting = false)
            } finally {
                stage.dispose()
            }
        }
    }

    "the fog draws apart, after the scene, until the stage is given up; with ?fullfog its sheets are in the scene" {
        withThree { three ->
            Three.addon = three

            val camera = three.createPerspectiveCamera(fov = 75.0, aspect = 1.5, near = 1.0, far = 1000.0)
            val plain: dynamic = three.createScene().asDynamic().onAfterRender

            val scene = three.createScene()
            val stage = Stage(show = Show(seed = 1), scene = scene, camera = camera, renderer = standInRenderer())

            (scene.asDynamic().onAfterRender !== plain) shouldBe true
            stage.dispose()
            // given up: nothing of the stage is called after the scene is drawn
            (scene.asDynamic().onAfterRender === plain) shouldBe true

            val full = three.createScene()
            val show = Show(seed = 1, switches = Switches.parse("?fullfog"))
            val other = Stage(show = show, scene = full, camera = camera, renderer = standInRenderer())

            try {
                (full.asDynamic().onAfterRender === plain) shouldBe true
                // the twelve sheets
                full.children.size shouldBe 15 + 12
            } finally {
                other.dispose()
            }
        }
    }
})
