/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.meet

import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.disposeTree
import io.peekandpoke.klang.workshop.threejs.asPerspectiveCamera
import io.peekandpoke.kraft.addons.registry.AddonRegistry.Companion.addons
import io.peekandpoke.kraft.addons.threejs.ThreeJs
import io.peekandpoke.kraft.addons.threejs.ThreeJsAddon
import io.peekandpoke.kraft.addons.threejs.ThreeJsContext
import io.peekandpoke.kraft.addons.threejs.ThreeJsFrame
import io.peekandpoke.kraft.addons.threejs.js.PerspectiveCamera
import io.peekandpoke.kraft.addons.threejs.js.Scene
import io.peekandpoke.kraft.addons.threejs.js.WebGLRenderer
import io.peekandpoke.kraft.addons.threejs.threeJs
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.style
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import kotlin.math.sqrt

/**
 * "Meet the Band": the six members round a hexagon of fence panels, the [selected] one at the front in the light of
 * a lamp on the floor, their true self in the shadow behind the fence ([MeetTheBand]).
 *
 * [models] is the address of the folder that holds the figures (ending in a slash); [version] the figures' release,
 * asked for with each file (as for the live act). [samples]: the samples drawn for each pixel (1: the screen's own
 * pixels; 2: twice as many, smoother edges; see the live act's oversampling). [onFigures]: told how many of the six
 * figures' files have arrived, as each one does. [onFailed]: told once where the figures could not be loaded (the
 * scene stays dark then; the reason is in the console).
 */
@Suppress("FunctionName")
fun Tag.MeetTheBand(
    models: String,
    version: String,
    selected: Player,
    samples: Double = 1.0,
    onFigures: ((count: Int, of: Int) -> Unit)? = null,
    onFailed: (() -> Unit)? = null,
) = comp(
    MeetTheBand.Props(
        models = models,
        version = version,
        selected = selected,
        samples = samples,
        onFigures = onFigures,
        onFailed = onFailed,
    )
) {
    MeetTheBand(it)
}

/**
 * "Meet the Band" as a component: it fills its box with the room ([MeetStage]) and the band ([MeetBand]), the camera
 * fixed before the front place ([MeetLayout]).
 *
 * Who stands at the front is the app's choice ([Props.selected]): when another member is handed in, the carousel turns
 * to them ([Carousel]), calmly and the shortest way, and a new choice during a turn is taken up from where it is. The
 * component holds no choice of its own and listens for no key: the app has the keys and the buttons.
 *
 * Nothing is shown until the band stands. Everything it makes is freed when it goes: the band's lacquer and Sirene's
 * eyes in her shadow, the mirror's pictures, then the whole scene (shapes, materials, pictures, the lamp's shadow
 * map), and the context; the renderer and its frames are Kraft's to give up ([ThreeJs]).
 */
class MeetTheBand(ctx: Ctx<Props>) : Component<MeetTheBand.Props>(ctx) {

    data class Props(
        val models: String,
        /** The figures' release (see [MeetTheBand]). */
        val version: String,
        /** Who stands at the front, or is on the way there. */
        val selected: Player,
        /** The samples drawn for each pixel: 0.5 to 4. */
        val samples: Double = 1.0,
        /** Told how many of how many figures' files have arrived. Null: nobody is told. */
        val onFigures: ((count: Int, of: Int) -> Unit)? = null,
        /** Told once where the figures could not be loaded. Null: nobody is told. */
        val onFailed: (() -> Unit)? = null,
    )

    @Suppress("unused")
    private val threeAddon: ThreeJsAddon? by subscribingTo(addons.threeJs)

    private val scope = MainScope()

    /** The room, from the moment it is built; the band once its figures are there. */
    private var stage: MeetStage? = null
    private var band: MeetBand? = null
    private var scene: Scene? = null
    private var renderer: WebGLRenderer? = null
    private var camera: PerspectiveCamera? = null

    /** The carousel's turn, from the member handed in first: they stand at the front from the start. */
    private val carousel = Carousel(start = MeetLayout.turnFor(props.selected))

    /** The samples the renderer draws with now ([Props.samples], as [asked] takes them); none before the first. */
    private var samples = 0.0

    private val onResize: (Event) -> Unit = { resize() }

    init {
        lifecycle {
            onMount {
                window.addEventListener("resize", onResize)
            }

            onUnmount {
                window.removeEventListener("resize", onResize)
                // the loading too, where it still runs: nothing is built after this
                scope.cancel()
                // the bodies get their suits back first: the scene frees what they wear
                band?.dispose()
                band = null
                stage?.dispose()
                stage = null
                scene?.disposeTree()
                scene = null
                // and the context itself, with its drawing buffer: Kraft frees the renderer's programs but keeps the
                // context until the canvas is collected, and a browser has only a handful of them (the page is left
                // and entered again at will)
                renderer?.asDynamic()?.forceContextLoss()
                renderer = null
            }
        }
    }

    override fun VDom.render() {
        div {
            style = "position: relative; width: 100%; height: 100%; overflow: hidden; background: #020308;"

            ThreeJs(
                onReady = { ctx -> build(ctx) },
                onFrame = { frame -> step(frame) },
                createCamera = { aspect ->
                    Three.addon = threeAddon ?: error("three.js is not loaded")

                    Three.addon.createPerspectiveCamera(fov = MeetLayout.fov(aspect), aspect = aspect, near = 0.1, far = FAR)
                },
            )
        }
    }

    /** Builds the room at once, and the band once the figures are there. */
    private fun build(ctx: ThreeJsContext) {
        val lens = ctx.camera.asPerspectiveCamera ?: error("The camera of Meet the Band is not a perspective camera")

        // kept before the room is built: whatever fails on the way, the unmount frees the scene and the context
        renderer = ctx.renderer
        camera = lens
        scene = ctx.scene

        val room = MeetStage(scene = ctx.scene, renderer = ctx.renderer)

        stage = room
        // where it looks follows the picture's shape (resize)
        lens.position.set(0.0, MeetLayout.CAMERA_HIGH, MeetLayout.CAMERA_Z)
        // nothing is shown until the band stands: the room alone is no picture of them
        ctx.scene.visible = false
        sample()

        scope.launch {
            band = try {
                MeetBand.load(
                    scene = ctx.scene,
                    renderer = ctx.renderer,
                    models = props.models,
                    version = props.version,
                    turn = carousel.target,
                    arrived = { count, of -> props.onFigures?.invoke(count, of) },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // without its figures there is nobody to meet: the room stays dark, and the reason is said where a
                // developer looks
                console.error("Meet the Band could not make the band (its figures are looked for in '${props.models}')", e)
                props.onFailed?.invoke()

                return@launch
            }

            ctx.scene.visible = true
        }
    }

    /** One frame: the carousel toward the member chosen, the room and the band at its turn. */
    private fun step(frame: ThreeJsFrame) {
        val t = frame.elapsedMs / 1000
        val room = stage ?: return
        val lens = camera ?: return

        if (asked(props.samples) != samples) {
            sample()
        }

        carousel.choose(turn = MeetLayout.turnFor(props.selected), now = t)

        val turn = carousel.at(t)

        room.turn(turn)
        band?.pose(
            turn = turn,
            t = t,
            camera = lens.position,
            lamp = room.lampAt,
            surfaceZ = room.surfaceZ,
            settled = !carousel.turning(t),
        )
    }

    /** The renderer's pixels: the screen's (at most 2) times the samples asked for, each way. */
    private fun sample() {
        samples = asked(props.samples)
        renderer?.setPixelRatio(minOf(window.devicePixelRatio, 2.0) * sqrt(samples))
        resize()
    }

    /**
     * The picture follows the box's size; the lens fits the frame by the narrower side ([MeetLayout.fov]), and on a
     * tall picture the camera looks lower ([MeetLayout.lookHigh]).
     */
    private fun resize() {
        val box = dom?.unsafeCast<HTMLElement>() ?: return
        val width = box.clientWidth
        val height = box.clientHeight

        if (width <= 0 || height <= 0) {
            return
        }

        renderer?.setSize(width, height)
        camera?.let {
            it.aspect = width.toDouble() / height.toDouble()
            it.fov = MeetLayout.fov(it.aspect)
            it.lookAt(0.0, MeetLayout.lookHigh(it.aspect), stage?.surfaceZ ?: (MeetLayout.MIDDLE_Z + MeetLayout.BACK))
            it.updateProjectionMatrix()
        }
    }

    private companion object {
        /** How far the camera sees, in metres: past the room. */
        const val FAR = 60.0

        /** The fewest and the most samples drawn for each pixel. */
        const val FEWEST_SAMPLES = 0.5
        const val MOST_SAMPLES = 4.0

        /** The samples [samples] asks for, held to [FEWEST_SAMPLES] to [MOST_SAMPLES]; no number is 1. */
        fun asked(samples: Double): Double {
            // NaN-guard: samples of no number are the screen's own pixels
            if (samples != samples) {
                return 1.0
            }

            return samples.coerceIn(FEWEST_SAMPLES, MOST_SAMPLES)
        }
    }
}
