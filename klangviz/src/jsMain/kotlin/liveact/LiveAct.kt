/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.peekandpoke.klang.viz.liveact.band.StagePlot
import io.peekandpoke.klang.viz.liveact.band.dress.EyeWarmth
import io.peekandpoke.klang.viz.liveact.band.figure.breathing
import io.peekandpoke.klang.viz.liveact.band.figure.eyeContact
import io.peekandpoke.klang.viz.liveact.band.figure.eyesWarm
import io.peekandpoke.klang.viz.liveact.band.figure.eyesWaver
import io.peekandpoke.klang.viz.liveact.band.notstrom.Notstrom
import io.peekandpoke.klang.viz.liveact.camera.HOME
import io.peekandpoke.klang.viz.liveact.camera.View
import io.peekandpoke.klang.viz.liveact.refactorme.SchmetterlingCast
import io.peekandpoke.klang.viz.liveact.sound.Sharpness
import io.peekandpoke.klang.viz.liveact.stage.Stage
import io.peekandpoke.klang.workshop.threejs.asPerspectiveCamera
import io.peekandpoke.klang.workshop.threejs.info
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
 * poses (eye contact, the eyes' glow, what goes with a head, breath; breath last because it widens bones); then the
 * lights; then the camera, which follows the figures; and the viewer's place for the next frame's looks.
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

    /** How the players' eyes take colour as they play. */
    private val eyeWarmth = EyeWarmth()

    /** The stage, from the moment it is built: it listens for a key, so it is given up also where the band never came. */
    private var stage: Stage? = null
    private var scene: Scene? = null
    private var renderer: WebGLRenderer? = null
    private var camera: PerspectiveCamera? = null

    /** Where the camera is: who looks at the viewer looks there. */
    private val viewer by lazy { HOME.pos.clone() }

    // frames a second, counted over each half second, and the longest single frame in it (a stutter shows there)
    private var fps = 0.0
    private var worst = 0.0
    private var worstShown = "-"
    private var counted = 0
    private var since = window.performance.now()
    private var before = window.performance.now()
    private var frames = 0

    /** The page is seen again, or no more: the frames are counted anew, so the time it was away is not a slow frame. */
    private val onVisibility: (Event) -> Unit = { recount(window.performance.now()) }

    private val onResize: (Event) -> Unit = {
        // another size is another number of pixels: the picture starts fine again and is judged anew
        if (sharpness.reset(full = screenRatio())) {
            sample()
        } else {
            resize()
        }
    }

    /**
     * Oversampling: the picture is drawn with more pixels than the screen shows and scaled down, which smooths edges
     * and the glitter. 2x and 4x are the number of pixels drawn (so 1.41 and 2 times as many each way); the cost in
     * frames grows about as much. The key o steps through them; `?oversample=2` starts with one.
     */
    private var over: Int = maxOf(0, SAMPLES.indexOf(props.show.switches[Switch.Oversample]?.toIntOrNull() ?: 1))

    /**
     * How finely the picture is drawn: it follows the frame rate (see [Sharpness]). Not where the picture is frozen
     * (a still is the same on every machine), not with oversampling (that asks for more pixels), and not with
     * `?sharp`, which keeps the screen's own pixels whatever the frames do.
     */
    private val sharpness = Sharpness(full = screenRatio())

    private val adapts: Boolean get() = over == 0 && props.show.frozen == null && !props.show.switches.has(Switch.Sharp)

    /** The line that says what the camera does, once it was looked up, and when it was last written. */
    private var hud: HTMLElement? = null
    private var hudAt = 0.0

    private val onKey: (Event) -> Unit = { event ->
        val e = event as? KeyboardEvent

        if (e != null && !event.fromTyping() && e.key.lowercase() == "o" && !e.ctrlKey && !e.metaKey && !e.altKey) {
            over = (over + 1) % SAMPLES.size
            sharpness.reset(full = screenRatio())
            sample()
        }
    }

    init {
        lifecycle {
            onMount {
                window.addEventListener("resize", onResize)
                window.document.addEventListener("visibilitychange", onVisibility)
                window.addEventListener("keydown", onKey)
            }

            onUnmount {
                window.removeEventListener("resize", onResize)
                window.document.removeEventListener("visibilitychange", onVisibility)
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

                    Three.addon.createPerspectiveCamera(fov = 35.0, aspect = aspect, near = 0.1, far = Stage.FAR)
                },
            )

            div {
                id = NOTICE_ID
            }

            div {
                id = HUD_ID
                style = "position: fixed; left: 12px; top: 8px; font: 12px monospace; color: #8a93a8; " +
                    "pointer-events: none; white-space: pre;" + (if (props.show.switches.has(Switch.NoHud)) " display: none;" else "")
            }
        }
    }

    private fun element(id: String): HTMLElement? = window.document.getElementById(id) as? HTMLElement

    /** Builds the stage at once, and the rest once the figures are there. */
    private fun build(ctx: ThreeJsContext) {
        val show = props.show
        val lens = ctx.camera.asPerspectiveCamera ?: error("The live act's camera is not a perspective camera")

        renderer = ctx.renderer
        camera = lens
        // a frame is drawn in several goes (the fog's maps have their own): what was drawn is counted over all of them, and
        // set back to nothing in frame(), with every frame that is counted (after loading or a hidden page the
        // line's first number is too high, for a tenth of a second)
        ctx.renderer.info.autoReset = false

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

            stage.shade(band.drummer.kit.group)

            val view = View(
                show = show,
                scene = ctx.scene,
                camera = lens,
                rigs = band.rigs,
                homes = StagePlot.PLACES,
                activity = band::playsAt,
            )
            // the clock last: it starts to listen to the music and to the page's keys, and from here on nothing can
            // fail before it is kept (and with it given up on unmount)
            val clock = ShowClock(show = show, notice = element(NOTICE_ID), intro = StagePlot.INTRO)

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
        // (into refactorme: her notes are the trumpet player's, and who that is, the cast says)
        eyeContact(show = show, rigs = now.band.rigs, herNotes = now.band.playsAt(SchmetterlingCast.TRUMPET), t = t)
        // (`?warm=1` holds everyone's eyes at their full colour, `?warm=0` at rest: to look at them)
        val held = show.switches.double(Switch.Warm)?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)

        eyesWarm(rigs = now.band.rigs) { player -> held ?: eyeWarmth.at(notes = now.band.playsAt(player), t = t) }
        eyesWaver(show = show, rigs = now.band.rigs, t = t)
        // what goes with a head as it is held at last: after every turn of it above, and before the breath, which
        // widens bones and would blur how the head is read
        now.band.afterPose(t)
        breathing(show = show, rigs = now.band.rigs, t = t, slow = energy.slow)
        now.stage.light(t = t, house = now.clock.houseAt(), waiting = now.clock.waiting())
        now.view.look(t)
        now.stage.follow()
        camera?.let { viewer.copy(it.position) }

        val nowMs = window.performance.now()

        if (window.document.asDynamic().hidden == true) {
            // nobody sees the page: some browsers go on with a frame a second, which says nothing about the
            // machine. The counting starts anew with each of them, so none is counted or judged
            recount(nowMs)

            return
        }

        worst = maxOf(worst, nowMs - before)
        before = nowMs
        counted++

        if (nowMs - since >= 500) {
            fps = counted * 1000 / (nowMs - since)
            worstShown = worst.toFixed(0)
            counted = 0
            worst = 0.0
            since = nowMs

            if (adapts && sharpness.judge(fps)) {
                sample()
            }
        }

        // the line is written ten times a second, not with every frame: writing it makes the page lay itself out
        if (nowMs - hudAt >= HUD_MS && !show.switches.has(Switch.NoHud)) {
            hudAt = nowMs

            val line = hud ?: element(HUD_ID).also { hud = it }
            val drawn = renderer?.info?.render

            line?.textContent = listOf(
                "music: ${show.music.source}",
                "camera: ${now.view.what()}",
                "seed ${show.seed}",
                "oversampling ${SAMPLES[over]}x (key o)",
                "${fps.toFixed(0)} fps",
                "slowest frame $worstShown ms",
                "pixels ${sharpness.ratio.toFixed(2)}x",
                "${drawn?.calls ?: "-"} draws, ${drawn?.triangles ?: "-"} triangles",
                "t ${t.toFixed(2)} s",
                "beat ${(t * show.bpm / 60).toFixed(2)}",
            ).joinToString("  |  ")
        }

        // what the next drawing counts is this frame's alone (see build())
        renderer?.info?.reset()

        frames++

        if (frames > 2) {
            // for whoever takes a picture of the page: the show has drawn
            window.asDynamic().__ready = true
        }
    }

    /** Starts the counting of frames anew at [nowMs]: what was counted so far is not judged. */
    private fun recount(nowMs: Double) {
        since = nowMs
        before = nowMs
        counted = 0
        worst = 0.0
    }

    /**
     * Sets the renderer's pixels: the screen's (at most 2) or fewer where the frames come too slowly ([sharpness]),
     * times the oversampling chosen.
     */
    private fun sample() {
        renderer?.setPixelRatio(sharpness.ratio * sqrt(SAMPLES[over].toDouble()))
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

    private companion object {
        const val NOTICE_ID = "liveact-notice"
        const val HUD_ID = "liveact-hud"

        /** The line is written this often, in milliseconds. */
        const val HUD_MS = 100.0

        /** The screen's pixels for each of the page's, each way, as it is now; at most 2. */
        fun screenRatio(): Double = minOf(window.devicePixelRatio, 2.0)

        /** How many times the screen's pixels are drawn. */
        val SAMPLES = listOf(1, 2, 4)
    }
}
