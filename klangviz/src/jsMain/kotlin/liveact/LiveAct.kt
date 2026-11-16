/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.common.breathing
import io.peekandpoke.klang.viz.liveact.band.common.eyeContact
import io.peekandpoke.klang.viz.liveact.band.common.eyesWaver
import io.peekandpoke.klang.viz.liveact.band.notstrom.Notstrom
import io.peekandpoke.klang.viz.liveact.camera.HOME
import io.peekandpoke.klang.viz.liveact.camera.View
import io.peekandpoke.klang.viz.liveact.stage.Stage
import io.peekandpoke.kraft.addons.registry.AddonRegistry.Companion.addons
import io.peekandpoke.kraft.addons.threejs.ThreeJs
import io.peekandpoke.kraft.addons.threejs.ThreeJsAddon
import io.peekandpoke.kraft.addons.threejs.ThreeJsContext
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
import kotlinx.html.id
import kotlinx.html.style
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.math.sqrt

/**
 * The live act: the band Notstrom on its stage, playing [show]'s song.
 *
 * [models] is the address of the folder that holds the figures (ending in a slash). The music is the show's
 * ([Show.music]).
 */
@Suppress("FunctionName")
fun Tag.LiveAct(show: Show, models: String) = comp(
    LiveAct.Props(show = show, models = models)
) {
    LiveAct(it)
}

/**
 * The live act as a component: it fills its box with the stage, and above it a notice to start and a line that
 * says what the camera does (`?nohud`: none).
 *
 * It puts the pieces together and runs the frames. Set up, in this order (what is made first is drawn first, and
 * the players take their figures' places as home): the stage, the band, the clock, the view. Each frame, in this
 * order: the sound's energy first (the nod reads it); then everyone's place and pose; then what is laid over the
 * poses (eye contact, the eyes' glow, breath, breath last because it widens bones); then the lights; then the
 * camera, which follows the figures; and the viewer's place for the next frame's looks.
 *
 * Two limits, as the prototype it was ported from has them. It takes the page for its own: its keys (space, the
 * arrows, single letters and digits) and a click anywhere are the show's, except while the user types in a field;
 * its notice and its line sit at the window's edges; and there is one of it on a page. And it shows the [Props] it
 * was mounted with: a new show needs a new component (give it a key of the show).
 */
class LiveAct(ctx: Ctx<Props>) : Component<LiveAct.Props>(ctx) {

    data class Props(
        val show: Show,
        val models: String,
    )

    /** Everything a running show holds, once its figures are loaded. */
    private class Running(
        val stage: Stage,
        val band: Notstrom,
        val clock: ShowClock,
        val view: View,
    )

    @Suppress("unused")
    private val threeAddon: ThreeJsAddon? by subscribingTo(addons.threeJs)

    private val scope = MainScope()
    private var running: Running? = null

    /** The stage, from the moment it is built: it listens for a key, so it is given up also where the band never came. */
    private var stage: Stage? = null
    private var scene: Scene? = null
    private var renderer: WebGLRenderer? = null
    private var camera: PerspectiveCamera? = null

    /** Where the camera is: who looks at the viewer looks there. */
    private val viewer by lazy { HOME.pos.clone() }

    /** When the frontwoman plays: she looks at nobody then. */
    private val herNotes: List<Double> get() = props.show.times("trommel")

    // frames a second, counted over each half second, and the longest single frame in it (a stutter shows there)
    private var fps = 0.0
    private var worst = 0.0
    private var worstShown = "-"
    private var counted = 0
    private var since = window.performance.now()
    private var before = window.performance.now()
    private var frames = 0

    private val onResize: (Event) -> Unit = { resize() }

    /**
     * Oversampling: the picture is drawn with more pixels than the screen shows and scaled down, which smooths edges
     * and the glitter. 2x and 4x are the number of pixels drawn (so 1.41 and 2 times as many each way); the cost in
     * frames grows about as much. The key o steps through them; `?oversample=2` starts with one.
     */
    private var over: Int = maxOf(0, SAMPLES.indexOf(props.show.switches["oversample"]?.toIntOrNull() ?: 1))

    private val onKey: (Event) -> Unit = { event ->
        val e = event as? KeyboardEvent

        if (e != null && !event.fromTyping() && e.key.lowercase() == "o" && !e.ctrlKey && !e.metaKey && !e.altKey) {
            over = (over + 1) % SAMPLES.size
            sample()
        }
    }

    init {
        lifecycle {
            onMount {
                window.addEventListener("resize", onResize)
                window.addEventListener("keydown", onKey)
            }

            onUnmount {
                window.removeEventListener("resize", onResize)
                window.removeEventListener("keydown", onKey)
                // the loading too, where it still runs: nothing is built after this
                scope.cancel()
                running?.let {
                    it.view.dispose()
                    it.clock.dispose()
                }
                running = null
                stage?.dispose()
                stage = null
                // what the scene holds on the graphics card (the renderer and its context are Kraft's to give up)
                scene?.disposeTree()
                scene = null
            }
        }
    }

    override fun VDom.render() {
        div {
            style = "position: relative; width: 100%; height: 100%; overflow: hidden; background: #020308;"

            ThreeJs(
                onReady = { ctx -> build(ctx) },
                onFrame = { frame() },
                createCamera = { aspect ->
                    Three.addon = threeAddon ?: error("three.js is not loaded")

                    Three.addon.createPerspectiveCamera(fov = 35.0, aspect = aspect, near = 0.1, far = 60.0)
                },
            )

            div {
                id = NOTICE_ID
            }

            div {
                id = HUD_ID
                style = "position: fixed; left: 12px; top: 8px; font: 12px monospace; color: #8a93a8; " +
                    "pointer-events: none; white-space: pre;" + (if (props.show.switches.has("nohud")) " display: none;" else "")
            }
        }
    }

    private fun element(id: String): HTMLElement? = window.document.getElementById(id) as? HTMLElement

    /** Builds the stage at once, and the rest once the figures are there. */
    private fun build(ctx: ThreeJsContext) {
        val show = props.show
        val lens = ctx.camera.unsafeCast<PerspectiveCamera>()

        renderer = ctx.renderer
        camera = lens

        val stage = Stage(show = show, scene = ctx.scene, camera = lens, renderer = ctx.renderer)

        this.stage = stage
        scene = ctx.scene
        // Nothing is shown until the band stands: Kraft draws every frame from now on, and the stage alone, fully
        // lit, seen from where the camera happens to be, is no picture of the show
        ctx.scene.visible = false

        resize()

        if (over > 0) {
            sample()
        }

        scope.launch {
            val band = try {
                Notstrom.load(show = show, scene = ctx.scene, models = props.models, viewer = viewer)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // without its figures there is no show: the stage stays dark, and the reason is said where a
                // developer looks
                console.error("The live act's band could not be made (its figures are looked for in '${props.models}')", e)

                return@launch
            }

            stage.shade(band.drummer.kit)

            val view = View(show = show, scene = ctx.scene, camera = lens, rigs = band.rigs)
            // the clock last: it starts to listen to the music and to the page's keys, and from here on nothing can
            // fail before it is kept (and with it given up on unmount)
            val clock = ShowClock(show = show, notice = element(NOTICE_ID), intro = Notstrom.INTRO)

            clock.start()

            // the frames are counted from here, not from the loading
            since = window.performance.now()
            before = since
            running = Running(stage = stage, band = band, clock = clock, view = view)
            ctx.scene.visible = true
        }
    }

    /** One frame. The order matters: see the class. */
    private fun frame() {
        val now = running ?: return
        val show = props.show
        val t = now.clock.showTime()
        val energy = show.energyAt(t)

        show.energy = energy
        now.band.play(t)
        eyeContact(show = show, rigs = now.band.rigs, herNotes = herNotes, t = t)
        eyesWaver(show = show, rigs = now.band.rigs, t = t)
        breathing(show = show, rigs = now.band.rigs, t = t, slow = energy.slow)
        now.stage.light(t = t, house = now.clock.houseAt(), waiting = now.clock.waiting())
        now.view.look(t)
        camera?.let { viewer.copy(it.position) }

        val nowMs = window.performance.now()

        worst = maxOf(worst, nowMs - before)
        before = nowMs
        counted++

        if (nowMs - since >= 500) {
            fps = counted * 1000 / (nowMs - since)
            worstShown = worst.asDynamic().toFixed(0).unsafeCast<String>()
            counted = 0
            worst = 0.0
            since = nowMs
        }

        element(HUD_ID)?.textContent = listOf(
            "music: ${show.music.source}",
            "camera: ${now.view.what()}",
            "seed ${show.seed}",
            "oversampling ${SAMPLES[over]}x (key o)",
            "${fixed(fps, 0)} fps",
            "slowest frame $worstShown ms",
            "t ${fixed(t, 2)} s",
            "beat ${fixed(t * show.bpm / 60, 2)}",
        ).joinToString("  |  ")

        frames++

        if (frames > 2) {
            // for whoever takes a picture of the page: the show has drawn
            window.asDynamic().__ready = true
        }
    }

    /** Sets the renderer's pixels by the oversampling chosen, on top of the stage's own ratio (the screen's, at most 2). */
    private fun sample() {
        renderer?.setPixelRatio(minOf(window.devicePixelRatio, 2.0) * sqrt(SAMPLES[over].toDouble()))
        resize()
    }

    /** The picture follows the box's size. */
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
            it.updateProjectionMatrix()
        }
    }

    private fun fixed(value: Double, digits: Int): String = value.asDynamic().toFixed(digits).unsafeCast<String>()

    private companion object {
        const val NOTICE_ID = "liveact-notice"
        const val HUD_ID = "liveact-hud"

        /** How many times the screen's pixels are drawn. */
        val SAMPLES = listOf(1, 2, 4)
    }
}
