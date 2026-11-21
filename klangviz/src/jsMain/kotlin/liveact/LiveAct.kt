/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.peekandpoke.klang.viz.liveact.band.dress.EyeWarmth
import io.peekandpoke.klang.viz.liveact.band.dress.Lacquer
import io.peekandpoke.klang.viz.liveact.band.dress.ShadowEyes
import io.peekandpoke.klang.viz.liveact.band.figure.breathing
import io.peekandpoke.klang.viz.liveact.band.figure.eyeContact
import io.peekandpoke.klang.viz.liveact.band.figure.eyesWarm
import io.peekandpoke.klang.viz.liveact.band.figure.eyesWaver
import io.peekandpoke.klang.viz.liveact.band.members.Members
import io.peekandpoke.klang.viz.liveact.band.notstrom.Notstrom
import io.peekandpoke.klang.viz.liveact.camera.HOME
import io.peekandpoke.klang.viz.liveact.camera.View
import io.peekandpoke.klang.viz.liveact.lights.ShadowQuality
import io.peekandpoke.klang.viz.liveact.sound.Downshift
import io.peekandpoke.klang.viz.liveact.sound.Sharpness
import io.peekandpoke.klang.viz.liveact.stage.EyesLook
import io.peekandpoke.klang.viz.liveact.stage.FogQuality
import io.peekandpoke.klang.viz.liveact.stage.LookFlip
import io.peekandpoke.klang.viz.liveact.stage.LookVariants
import io.peekandpoke.klang.viz.liveact.stage.ReflectionQuality
import io.peekandpoke.klang.viz.liveact.stage.SkinLook
import io.peekandpoke.klang.viz.liveact.stage.Stage
import io.peekandpoke.klang.viz.liveact.stage.WorldLook
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
 * The live act: the band Notstrom on its stage, playing [show]'s song by its [score] (who plays what, and where each
 * one stands).
 *
 * [models] is the address of the folder that holds the figures (ending in a slash); [version] the figures' release,
 * asked for with each file (a release's figures may be cached for long, and a new release asks anew). The music is
 * the show's ([Show.music]). [walkInAtOnce]: the walk-in begins as soon as the band stands, without a click; [frameTimes]: told
 * the time of every frame drawn; [stepsDown]: the oversampling steps down where the frames stay slow; [picture]: the
 * oversampling, the fog, the shadows and the reflections seen and set, and the frame rate seen, from outside (all
 * four: see [LiveAct.Props]).
 */
@Suppress("FunctionName")
fun Tag.LiveAct(
    show: Show,
    score: Score,
    models: String,
    version: String,
    walkInAtOnce: Boolean = false,
    frameTimes: ((Double) -> Unit)? = null,
    stepsDown: Boolean = false,
    picture: Picture? = null,
) = comp(
    LiveAct.Props(
        show = show,
        score = score,
        models = models,
        version = version,
        walkInAtOnce = walkInAtOnce,
        frameTimes = frameTimes,
        stepsDown = stepsDown,
        picture = picture,
    )
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
 * lights; then (with `?eyes=shadow`) Sirene's eyes in her shadow, which follow the front lamp and her eyes' glow;
 * then (with `?skin=lacquer`) how bright the lacquer mirrors its room, with the house, and how it looks at the stage's
 * time of day (`?world=night`); then the camera, which follows
 * the figures; and the viewer's place for the next frame's looks.
 *
 * The keys: space plays and pauses, the arrows skip ([ShowClock]); q, 0 (or escape), 1 to 6, a, s, d, e, r, t, y (or
 * z), w and u move the camera by hand ([View]); f tries the front lamp's colours ([Lights]); o steps through the
 * oversampling, g through the fog; m turns over what stands behind the band (wall or fence), p the floor (plain or
 * mirror), h the haze behind the fence (whole, or giving way to the band's shadows), i Sirene's eyes under the
 * front lamp (in her face, or in her shadow), l the band's skin (the suit, or black lacquer with gold), n the two
 * worlds' time of day (off, day, night, ember, or a drift from day to night and back), v the underworld in the
 * mirror floor (off, or on: the other world, cold, for a moment of a show), b how uneven the mirror floor's stone is
 * (none, faint, gentle or strong), c how blurred its reflection is (crisp, soft or stone), k its stone (plain or
 * marble) and j the stage's outline, a working aid (off or on) ([LookVariants]); x flips the whole look between today
 * and the new look, each side as it was last tuned by the keys before ([LookFlip]). The shadows and the mirror
 * floor's reflections have no key (every letter is taken): `?shadows=low`, `?reflections=off`, or an app's controls
 * ([Props.picture]). A key that turns a variant over
 * says the new one for two seconds below the line at the top. The first turn of x to the new look builds what it
 * needs (a hitch of the frames while the materials compile); from then on it flips at once both ways.
 *
 * Two limits, as the prototype it was ported from has them. It takes the page for its own: its keys (space, the
 * arrows, single letters and digits) and a click anywhere are the show's, except while the user types in a field;
 * its notice and its line sit at the window's edges; and there is one of it on a page. And it shows the [Props] it
 * was mounted with: a new show needs a new component (give it a key of the show).
 */
class LiveAct(ctx: Ctx<Props>) : Component<LiveAct.Props>(ctx) {

    data class Props(
        val show: Show,
        /** The song's score: the band is cast and placed by it, and the camera, the lights and the eye contact go by it. */
        val score: Score,
        val models: String,
        /** The figures' release (see [LiveAct]): every page that shows the live act names its own. */
        val version: String,
        /**
         * The walk-in begins as soon as the band stands, without waiting for a click, and no notice asks for one
         * ([ShowClock]'s `begun`). For an app whose own start screen took the click already (notstrom's, KLANG-00465).
         */
        val walkInAtOnce: Boolean = false,
        /**
         * Told the time of every frame drawn, in milliseconds since the one before, once the band stands and while the
         * page is seen (the frames the line at the top counts). For an app that measures how fast this machine draws
         * the stage (notstrom's picture test, KLANG-00465). A display caps the frames at its refresh rate: above the
         * cap nothing is told apart. Null: nobody is told.
         */
        val frameTimes: ((Double) -> Unit)? = null,
        /**
         * Where the picture starts with oversampling (`?oversample=2`), it takes a step down to fewer samples when
         * the frames stay slow for a while ([Downshift]), never below 1x, and never steps up again by itself. For an app that chose
         * the oversampling by a test of this machine (notstrom's, KLANG-00465). False: the oversampling stays where
         * it is set, as before.
         */
        val stepsDown: Boolean = false,
        /**
         * The picture seen and set from outside the live act ([Picture]): it is told every change of the oversampling,
         * the fog, the shadows and the reflections, and the frame rate counted for the line at the top. An
         * oversampling chosen through it is taken as the key o's is (by hand: no step down by itself after it), a fog
         * as the key g's, the shadows and the reflections at once. Null: none.
         */
        val picture: Picture? = null,
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
     * Oversampling, the samples drawn for each pixel ([Oversampling]): at 2x and 4x the picture is drawn with more
     * pixels than the screen shows and scaled down, which smooths edges and the glitter; at 0.5x with fewer, softer
     * and faster. The cost in frames goes about with the samples. The key o steps through them; `?oversample=2`
     * starts with one.
     */
    private var samples: Double = Oversampling.parse(props.show.switches[Switch.Oversample])

    /** The oversampling was chosen by hand (the key o, or [Props.picture]): it holds, no step down by itself. */
    private var byHand = false

    /**
     * How the fog on the floor is drawn ([FogQuality]): `?fog=low` (or `?nofog`) starts with another than high; the
     * key g steps through them. It does not step down by itself: where the frames are slow, [sharpness] and the
     * oversampling's step down are the safety net.
     */
    private var fog: FogQuality = FogQuality.of(props.show.switches)

    /**
     * How finely the lamps' shadows are drawn ([ShadowQuality]): `?shadows=low` starts with low. Changed only by hand
     * ([Props.picture]), no key.
     */
    private var shadows: ShadowQuality = ShadowQuality.of(props.show.switches)

    /**
     * Whether the mirror floor's reflections are drawn ([ReflectionQuality]), where the look has the mirror floor:
     * `?reflections=off` starts without them. Changed only by hand ([Props.picture]), no key; the fog has no say in it.
     */
    private var reflections: ReflectionQuality = ReflectionQuality.of(props.show.switches)

    /**
     * How finely the picture is drawn: it follows the frame rate (see [Sharpness]), at 1x and at 0.5x (half the
     * pixels of what 1x draws then). Not where the picture is frozen (a still is the same on every machine), not
     * with oversampling above 1x (that asks for more pixels), and not with `?sharp`, which keeps the screen's own
     * pixels whatever the frames do.
     */
    private val sharpness = Sharpness(full = screenRatio())

    /** When the oversampling takes a step down, where [Props.stepsDown] asks for it. */
    private val downshift = Downshift()

    private val adapts: Boolean
        get() = Oversampling.follows(samples) && props.show.frozen == null && !props.show.switches.has(Switch.Sharp)

    /**
     * The whole look, today's and the new one ([LookFlip], `?look=new`, the key x), the one shown and the one kept for
     * the next x.
     */
    private var look: LookFlip = LookFlip.of(props.show.switches)

    /**
     * The look's variants shown now ([LookVariants]): what stands behind the band (`?stage=mesh`, the key m), the floor
     * (`?floor=mirror`, the key p), the haze (`?haze=parts`, the key h), Sirene's eyes (`?eyes=shadow`, the key
     * i), the band's skin (`?skin=lacquer`, the key l), the time of day (`?world=night`, the key n), the underworld
     * in the mirror (`?underworld=on`, the key v), the stone's unevenness (`?ripple=faint`, the key b) and the
     * reflection's blur (`?blur=crisp`, the key c), the stone (`?stone=plain`, the key k) and the stage's outline
     * (`?edge=on`, the key j).
     */
    private val variants: LookVariants
        get() = look.shown

    /**
     * The eyes in the shadow of the one whose dress has them there (Sirene's, see [Members.shadowEyed]), made the
     * first time they are chosen (`?eyes=shadow`, the key i) once the band stands, and kept resting while they are not.
     */
    private var shadowEyes: ShadowEyes? = null

    /**
     * The band in black lacquer and gold, made the first time it is chosen (`?skin=lacquer`, the key l) once the band
     * stands, and kept off while it is not ([Lacquer.wear]).
     */
    private var lacquer: Lacquer? = null

    /** The timer that clears the line a variant's key wrote ([flash]), while it runs. */
    private var flashTimer: Int? = null

    /** The line that says what the camera does, once it was looked up, and when it was last written. */
    private var hud: HTMLElement? = null
    private var hudAt = 0.0

    private val onKey: (Event) -> Unit = { event ->
        val e = event as? KeyboardEvent
        val plain = e != null && !event.fromTyping() && !e.ctrlKey && !e.metaKey && !e.altKey
        val key = e?.key?.lowercase()

        if (plain && key == "o") {
            oversample(to = Oversampling.next(samples), byHand = true)
        }

        if (plain && key == "g") {
            chooseFog(FogQuality.entries[(fog.ordinal + 1) % FogQuality.entries.size])
        }

        if (plain && key == "m") {
            chooseLook(variants.nextStage())
        }

        if (plain && key == "p") {
            chooseLook(variants.nextFloor())
        }

        if (plain && key == "h") {
            chooseLook(variants.nextHaze())
        }

        if (plain && key == "i") {
            chooseLook(variants.nextEyes())
        }

        if (plain && key == "l") {
            chooseLook(variants.nextSkin())
        }

        if (plain && key == "n") {
            chooseLook(variants.nextWorld())
        }

        if (plain && key == "v") {
            chooseLook(variants.nextUnderworld())
        }

        if (plain && key == "b") {
            chooseLook(variants.nextRipple())
        }

        if (plain && key == "c") {
            chooseLook(variants.nextBlur())
        }

        if (plain && key == "k") {
            chooseLook(variants.nextStone())
        }

        if (plain && key == "j") {
            chooseLook(variants.nextOutline())
        }

        if (plain && key == "x") {
            flipLook()
        }
    }

    init {
        lifecycle {
            onMount {
                props.picture?.let {
                    it.attach(
                        take = { samples -> oversample(to = samples, byHand = true) },
                        takeFog = { quality -> chooseFog(quality) },
                        takeShadows = { quality -> chooseShadows(quality) },
                        takeReflections = { quality -> chooseReflections(quality) },
                        takeLook = { to -> chooseLook(to) },
                        flipLook = { flipLook() },
                        tryFront = { stage?.tryFront() },
                    )
                    it.tellSamples(samples)
                    it.tellFog(fog)
                    it.tellShadows(shadows)
                    it.tellReflections(reflections)
                    it.tellLook(look)
                }
                window.addEventListener("resize", onResize)
                window.document.addEventListener("visibilitychange", onVisibility)
                window.addEventListener("keydown", onKey)
            }

            onUnmount {
                props.picture?.detach()
                window.removeEventListener("resize", onResize)
                window.document.removeEventListener("visibilitychange", onVisibility)
                window.removeEventListener("keydown", onKey)
                flashTimer?.let { window.clearTimeout(it) }
                flashTimer = null
                // the loading too, where it still runs: nothing is built after this
                scope.cancel()
                running?.let {
                    it.view.dispose()
                    it.clock.dispose()
                }
                running = null
                shadowEyes?.dispose()
                shadowEyes = null
                // the bodies get their suits back first: the scene frees what they wear
                lacquer?.dispose()
                lacquer = null
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

            div {
                id = FLASH_ID
                // below the top line's six lines
                style = "position: fixed; left: 12px; top: 112px; font: 15px monospace; color: #d4dbea; " +
                    "pointer-events: none; white-space: pre;" + (if (props.show.switches.has(Switch.NoHud)) " display: none;" else "")
            }
        }
    }

    private fun element(id: String): HTMLElement? = window.document.getElementById(id) as? HTMLElement

    /** Builds the stage at once, and the rest once the figures are there. */
    private fun build(ctx: ThreeJsContext) {
        val show = props.show
        val score = props.score
        val lens = ctx.camera.asPerspectiveCamera ?: error("The live act's camera is not a perspective camera")

        renderer = ctx.renderer
        camera = lens
        // a frame is drawn in several goes (the fog's maps have their own): what was drawn is counted over all of them, and
        // set back to nothing in frame(), with every frame that is counted (after loading or a hidden page the
        // line's first number is too high, for a tenth of a second)
        ctx.renderer.info.autoReset = false

        val stage = Stage(
            show = show,
            scene = ctx.scene,
            camera = lens,
            renderer = ctx.renderer,
            kick = score.cast.drums.kit.kick,
            fog = fog,
            shadows = shadows,
            reflections = reflections,
            variants = variants,
        )

        this.stage = stage
        // while the mirror draws, Sirene's eyes in her shadow also shine in her reflection, as far as the underworld shows
        stage.mirrorDrawing = { drawing -> shadowEyes?.inMirror(drawing = drawing, share = stage.underworld) }
        scene = ctx.scene
        // Nothing is shown until the band stands: Kraft draws every frame from now on, and the stage alone, fully
        // lit, seen from where the camera happens to be, is no picture of the show
        ctx.scene.visible = false

        resize()

        if (samples != Oversampling.PLAIN) {
            sample()
        }

        scope.launch {
            val band = try {
                Notstrom.load(
                    show = show,
                    cast = score.cast,
                    plot = score.plot,
                    scene = ctx.scene,
                    models = props.models,
                    version = props.version,
                    viewer = viewer,
                    arrived = { count, of -> props.picture?.tellFigures(Picture.Figures(arrived = count, of = of, failed = false)) },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // without its figures there is no show: the stage stays dark, and the reason is said where a
                // developer looks
                console.error("The live act's band could not be made (its figures are looked for in '${props.models}')", e)
                props.picture?.let { it.tellFigures(it.figures().copy(failed = true)) }

                return@launch
            }

            stage.shade(band.drummer.kit.group)

            val view = View(
                show = show,
                scene = ctx.scene,
                camera = lens,
                rigs = band.rigs,
                homes = score.plot.places,
                activity = band::playsAt,
                seldom = score.cast.seldom,
                strolls = score.cast.strolls,
            )
            // the clock last: it starts to listen to the music and to the page's keys, and from here on nothing can
            // fail before it is kept (and with it given up on unmount)
            val clock = ShowClock(
                show = show,
                notice = element(NOTICE_ID),
                intro = score.plot.intro,
                begun = props.walkInAtOnce,
                opens = WorldLook.opens(show.switches),
            )

            clock.start()

            // the frames are counted from here, not from the loading
            since = window.performance.now()
            before = since
            running = Running(stage = stage, band = band, clock = clock, view = view)
            eyesAsChosen()
            skinAsChosen()
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

        // she who plays the trumpet looks at nobody while she plays
        val her = props.score.cast.trumpet.player

        eyeContact(
            show = show,
            rigs = now.band.rigs,
            her = her,
            herNotes = now.band.playsAt(her),
            t = t,
            skills = { player -> Members.of(player).skills },
        )
        // (`?warm=1` holds everyone's eyes at their full colour, `?warm=0` at rest: to look at them)
        val held = show.switches.double(Switch.Warm)?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)

        eyesWarm(rigs = now.band.rigs) { player -> held ?: eyeWarmth.at(notes = now.band.playsAt(player), t = t) }
        eyesWaver(show = show, rigs = now.band.rigs, t = t)
        // what goes with a head as it is held at last: after every turn of it above, and before the breath, which
        // widens bones and would blur how the head is read
        now.band.afterPose(t)
        breathing(show = show, rigs = now.band.rigs, t = t, slow = energy.slow)
        val house = now.clock.houseAt()

        now.stage.light(t = t, house = house, waiting = now.clock.waiting())
        if (variants.eyes == EyesLook.Shadow) {
            shadowEyes?.update(
                front = now.stage.front,
                lamp = now.stage.frontLamp,
                planeZ = now.stage.shadowsAt,
            )
        }

        lacquer?.takeIf { it.worn }?.light(house = house, world = now.stage.world)
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

        props.frameTimes?.invoke(nowMs - before)
        worst = maxOf(worst, nowMs - before)
        before = nowMs
        counted++

        if (nowMs - since >= 500) {
            fps = counted * 1000 / (nowMs - since)
            props.picture?.tellFps(fps)
            worstShown = worst.toFixed(0)
            counted = 0
            worst = 0.0
            since = nowMs

            if (adapts && sharpness.judge(fps)) {
                sample()
            }

            // one step fewer samples where the frames stay slow, as the key o changes them (`stepsDown`); never
            // below 1x: 0.5x is a choice by hand only
            val below = Oversampling.below(samples)

            if (props.stepsDown && below != null && downshift.judge(fps = fps, byHand = byHand)) {
                oversample(to = below, byHand = false)
            }
        }

        // the line is written ten times a second, not with every frame: writing it makes the page lay itself out
        if (nowMs - hudAt >= HUD_MS && !show.switches.has(Switch.NoHud)) {
            hudAt = nowMs

            val line = hud ?: element(HUD_ID).also { hud = it }
            val drawn = renderer?.info?.render

            // in lines of a few items each, so it stays within a window's width (the `white-space: pre` keeps the breaks)
            line?.textContent = listOf(
                listOf(
                    "music: ${show.music.source}",
                    "camera: ${now.view.what()}",
                    "seed ${show.seed}",
                    "t ${t.toFixed(2)} s",
                    "beat ${(t * show.bpm / 60).toFixed(2)}",
                ),
                listOf(
                    "${fps.toFixed(0)} fps",
                    "slowest frame $worstShown ms",
                    "pixels ${sharpness.ratio.toFixed(2)}x",
                    "${drawn?.calls ?: "-"} draws, ${drawn?.triangles ?: "-"} triangles",
                ),
                listOf(
                    "oversampling ${Oversampling.label(samples)} (key o)",
                    "fog ${fog.word} (key g)",
                    "shadows ${shadows.word}",
                    "reflections ${reflections.word}",
                ),
                listOf(
                    "${look.line()} (key x)",
                    "${variants.stageLine()} (key m)",
                    "${variants.floorLine(reflections)} (key p)",
                    "${variants.hazeLine(fog)} (key h)",
                ),
                listOf(
                    "${variants.eyesLine()} (key i)",
                    "${variants.skinLine()} (key l)",
                    "${variants.worldLine()}${if (now.stage.worldOpens) " (opening)" else ""} (key n)",
                    variants.ghostsLine(),
                    "${variants.underworldLine(reflections)} (key v)",
                ),
                listOf(
                    "${variants.rippleLine(reflections)} (key b)",
                    "${variants.blurLine(reflections)} (key c)",
                    "${variants.stoneLine(reflections)} (key k)",
                    "${variants.outlineLine()} (key j)",
                ),
            ).joinToString("\n") { items -> items.joinToString("  |  ") }
        }

        // what the next drawing counts is this frame's alone (see build())
        renderer?.info?.reset()

        frames++

        if (frames > 2) {
            // for whoever takes a picture of the page: the show has drawn
            window.asDynamic().__ready = true
        }
    }

    /**
     * Says [line] for two seconds below the line at the top (`stage: fence`): what a variant's key just chose. A key
     * pressed again before that writes its own line, for two seconds from then. Not with `?nohud`.
     */
    private fun flash(line: String) {
        val shown = element(FLASH_ID) ?: return

        shown.textContent = line
        flashTimer?.let { window.clearTimeout(it) }
        flashTimer = window.setTimeout({
            shown.textContent = ""
            flashTimer = null
        }, FLASH_MS)
    }

    /**
     * The variants shown turned to [to] (a variant's own key, or a choice through [Props.picture]): each one that
     * differs is turned as it is (the stage builds what it needs, Sirene's eyes and the band's skin are put on or
     * off), and the line at the top says it for two seconds (each changed variant's line, where several changed).
     * The other side of the key x stays as it was ([LookFlip.tune]). Tells [Props.picture].
     */
    private fun chooseLook(to: LookVariants) {
        val was = variants

        if (to == was) {
            return
        }

        look = look.tune(to)

        val lines = listOfNotNull(
            to.stageLine().takeIf { to.stage != was.stage },
            to.floorLine(reflections).takeIf { to.floor != was.floor },
            to.hazeLine(fog).takeIf { to.haze != was.haze },
            to.eyesLine().takeIf { to.eyes != was.eyes },
            to.skinLine().takeIf { to.skin != was.skin },
            to.worldLine().takeIf { to.world != was.world },
            to.ghostsLine().takeIf { to.ghosts != was.ghosts },
            to.underworldLine(reflections).takeIf { to.underworld != was.underworld },
            to.rippleLine(reflections).takeIf { to.ripple != was.ripple },
            to.blurLine(reflections).takeIf { to.blur != was.blur },
            to.stoneLine(reflections).takeIf { to.stone != was.stone },
            to.outlineLine().takeIf { to.outline != was.outline },
        )

        // the stage turns to all of them at once, as for the key x: a variant that stays the same stays as it is
        stage?.turn(to)
        eyesAsChosen()
        skinAsChosen()
        flash(lines.joinToString("  |  "))
        props.picture?.tellLook(look)
    }

    /**
     * The whole look flipped (the key x, or [Props.picture]): the other side shown as it was last tuned
     * ([LookFlip.flip]), and the line at the top says which for two seconds. Tells [Props.picture].
     */
    private fun flipLook() {
        look = look.flip()
        stage?.turn(variants)
        eyesAsChosen()
        skinAsChosen()
        flash(look.line())
        props.picture?.tellLook(look)
    }

    /**
     * The eyes of the one whose dress has them in the shadow ([Members.shadowEyed]: Sirene's) as [variants] say: in
     * her shadow ([ShadowEyes]) where it is chosen and the band stands, made where there are none; resting where they
     * are not chosen (her face's eyes are hers again from the next frame on), kept for the next time.
     */
    private fun eyesAsChosen() {
        val wanted = variants.eyes == EyesLook.Shadow
        val rig = Members.shadowEyed?.let { running?.band?.rigs?.get(it) }
        val into = scene

        if (wanted && shadowEyes == null && rig != null && into != null) {
            shadowEyes = ShadowEyes.of(scene = into, rig = rig)
        }

        if (!wanted) {
            shadowEyes?.rest()
        }
    }

    /**
     * The band's skin as [variants] say: lacquer ([Lacquer]) where it is chosen and the band stands, made where there
     * is none; taken off where it is not chosen (the bodies wear their suits again from the next frame on), kept for
     * the next time ([Lacquer.wear]).
     */
    private fun skinAsChosen() {
        val wanted = variants.skin == SkinLook.Lacquer
        val band = running?.band
        val drawer = renderer

        if (wanted && lacquer == null && band != null && drawer != null) {
            lacquer = Lacquer.of(renderer = drawer, bodies = band.bodies, brass = listOf(band.frontwoman.trumpet.brass))
        }

        lacquer?.wear(wanted)
    }

    /** Starts the counting of frames anew at [nowMs]: what was counted so far is not judged. */
    private fun recount(nowMs: Double) {
        since = nowMs
        before = nowMs
        counted = 0
        worst = 0.0
    }

    /**
     * The oversampling at [to] samples (one of [Oversampling.STEPS]; another is not taken): the pixels judged anew
     * from full, and drawn so. [byHand]: the key o or a choice from outside, which holds (no step down by itself
     * after it). Tells [Props.picture].
     */
    private fun oversample(to: Double, byHand: Boolean) {
        if (to !in Oversampling.STEPS) {
            return
        }

        samples = to
        this.byHand = this.byHand || byHand
        sharpness.reset(full = screenRatio())
        sample()
        props.picture?.tellSamples(samples)
    }

    /**
     * The fog on the floor at [quality], at once: the stage makes it anew where it is another (before the stage is
     * built, it is built so). The mirror in the floor stays as it is: the reflections decide it ([chooseReflections]).
     * Tells [Props.picture].
     */
    private fun chooseFog(quality: FogQuality) {
        fog = quality
        stage?.fog(quality)
        props.picture?.tellFog(quality)
    }

    /** The lamps' shadows at [quality], at once ([Stage.shadows]). Tells [Props.picture]. */
    private fun chooseShadows(quality: ShadowQuality) {
        shadows = quality
        stage?.shadows(quality)
        props.picture?.tellShadows(quality)
    }

    /**
     * The mirror floor's reflections at [quality], at once ([Stage.reflections]): off, the mirror floor is drawn plain
     * and its pictures are freed; on, it mirrors again where the look has it. Tells [Props.picture].
     */
    private fun chooseReflections(quality: ReflectionQuality) {
        reflections = quality
        stage?.reflections(quality)
        props.picture?.tellReflections(quality)
    }

    /**
     * Sets the renderer's pixels: the screen's (at most 2) or fewer where the frames come too slowly ([sharpness]),
     * times the oversampling chosen, each way.
     */
    private fun sample() {
        renderer?.setPixelRatio(sharpness.ratio * sqrt(samples))
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
        const val FLASH_ID = "liveact-flash"

        /** How long a variant's key's line stays, in milliseconds. */
        const val FLASH_MS = 2000

        /** The line is written this often, in milliseconds. */
        const val HUD_MS = 100.0

        /** The screen's pixels for each of the page's, each way, as it is now; at most 2. */
        fun screenRatio(): Double = minOf(window.devicePixelRatio, 2.0)
    }
}
