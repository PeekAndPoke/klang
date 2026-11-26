/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.peekandpoke.klang.apps.common.Song
import io.peekandpoke.klang.viz.liveact.Oversampling
import io.peekandpoke.klang.viz.liveact.Picture
import io.peekandpoke.klang.viz.liveact.PicturePreset
import io.peekandpoke.klang.viz.liveact.lights.ShadowQuality
import io.peekandpoke.klang.viz.liveact.stage.FogQuality
import io.peekandpoke.klang.viz.liveact.stage.LookFlip
import io.peekandpoke.klang.viz.liveact.stage.LookGroup
import io.peekandpoke.klang.viz.liveact.stage.ReflectionQuality
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.html.onInput
import io.peekandpoke.ultra.html.onKeyDown
import io.peekandpoke.ultra.html.onKeyUp
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.FlowContent
import kotlinx.html.InputType
import kotlinx.html.Tag
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.span
import kotlinx.html.title
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.Node
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.ShowDrawer(
    picture: Picture,
    release: String,
    dev: Boolean,
    song: Song,
    volume: Double,
    onVolume: (Double) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) = comp(
    ShowDrawer.Props(
        picture = picture,
        release = release,
        dev = dev,
        song = song,
        volume = volume,
        onVolume = onVolume,
        onPrevious = onPrevious,
        onNext = onNext,
        onBack = onBack,
    )
) {
    ShowDrawer(it)
}

/**
 * The drawer at the bottom of the window while the show plays, shown and hidden as [DrawerShowing] decides. With a
 * mouse: a move brings it, and it goes again 3 s after the last move (while the pointer rests on it, it stays: a
 * visitor reading it does not move); the mouse pointer hides with it. With a touch screen (or a pen): a tap on the
 * stage brings it, another tap on the stage hides it (that tap does nothing else in the show), taps in the drawer
 * keep it, and it goes by itself 10 s after the last touch on it, so a drawer left open does not stay over the show.
 * Mouse and touch are told apart by the pointer events' kind: a tap also sends the browser's emulated mouse events,
 * which would hold the drawer for ever as a mouse resting on it. It holds its rows (and with `?dev` the look, below).
 *
 * The first is the picture: its quality as a whole, the four presets ([PicturePreset], KLANG-00492), the one the
 * settings make marked, else "Custom" lit (shown, not chosen); then the live act's oversampling, its steps (0.5x to
 * 4x) with the one in use marked, the fog on the floor, its qualities (off, low, high) likewise, the lamps' shadows
 * (low, high), and the mirror floor's reflections (off, low, mid, high), each following the real state (the test's
 * choice, the keys o and g, the live act's own step down), where a choice is the visitor's and holds
 * ([Props.picture]); and the frames a second the live act counts (read once a second while the drawer is shown,
 * nothing while it is hidden).
 * Before them the song: its title and its tempo ([Props.song], [tempoLine]) between "Previous song" and "Next song"
 * ([Props.onPrevious], [Props.onNext]: the show of the other song begins; KLANG-00481).
 * The second: the way back to the start page ([Props.onBack]: the show ends there, as its stage goes; the maintainer,
 * 2026-11-21: "a button in the drawer that goes back to the start page"); a button for fullscreen, whose label follows
 * the page's real state (also when fullscreen was entered by the choice before the show, or left with Esc); the volume, 0 to 100 percent, as the show has it ([Props.volume]: full at first, kept from song to song)
 * ([Props.onVolume] is told a gain from 0 to 1); and at its end, small and quiet, the [Props.release]. On a narrow
 * window a row wraps within itself, a group of buttons too, so nothing runs past the window's edge.
 *
 * With `?dev` ([Props.dev], [DEV]), for whoever works on the show: below the two rows a third part, the look, with
 * each of its variants as a group of buttons ([LookGroup]: `Stage (m)  [Wall] [Fence]`), the one in use marked, a
 * variant that does nothing now (the haze before the wall, the ripple without the mirror or with the reflections off)
 * dimmed with the reason, and
 * a quiet line with the show's other keys; the oversampling and the fog name their keys too. A choice there is taken
 * as the key would take it, with its line at the top ([Picture.chooseLook], [Picture.flipLook]). The drawer then
 * takes at most about a third of the window's height and scrolls within itself where it needs more.
 *
 * The live act listens for clicks and keys on the whole window: what is clicked or typed in the drawer goes no
 * further while it is shown, so it reaches the show neither as a pause nor as a camera key. With `?dev` the show's
 * single keys (letters, digits) go on to it all the same, so the look's keys work while the drawer is open; the space,
 * Enter and the arrows stay with the drawer's controls ([DrawerShowing.passesKey]). When it hides it gives the
 * keyboard's focus back, so no hidden control takes the keys (an arrow on the volume, Space on fullscreen) and the
 * live act's keys work again.
 *
 * No pause, by design: a live act has none (the maintainer, 2026-11-19: "it is a liveact and there is no pause... when
 * you miss something you missed it"; KLANG-00474, won't implement).
 */
class ShowDrawer(ctx: Ctx<Props>) : Component<ShowDrawer.Props>(ctx) {

    data class Props(
        val picture: Picture,
        val release: String,
        val dev: Boolean,
        /** The song that plays: its title and its tempo stand in the first row. */
        val song: Song,
        /** The volume as the show has it now, 0 to 1 (kept from one song to the next). */
        val volume: Double,
        val onVolume: (Double) -> Unit,
        val onPrevious: () -> Unit,
        val onNext: () -> Unit,
        val onBack: () -> Unit,
    )

    private var shown: Boolean by value(false)

    private var fullscreen: Boolean by value(isFullscreen())

    /** The volume, in percent. */
    private var volume: Int by value((props.volume.coerceIn(0.0, 1.0) * 100).roundToInt())

    /** The samples drawn for each pixel now, as the live act tells. */
    private val samples: Double by subscribingTo(props.picture.samples)

    /** The fog's quality now, as the live act tells. */
    private val fog: FogQuality by subscribingTo(props.picture.fog)

    /** The shadows' quality now, as the live act tells. */
    private val shadows: ShadowQuality by subscribingTo(props.picture.shadows)

    /** Whether the reflections are drawn now, as the live act tells. */
    private val reflections: ReflectionQuality by subscribingTo(props.picture.reflections)

    /** The look now, as the live act tells (shown with `?dev`). */
    private val look: LookFlip by subscribingTo(props.picture.look)

    /** The frames a second, as last read (while the drawer is shown). */
    private var fps: Int by value(0)

    /** Reads the frame rate once a second while the drawer is shown. */
    private var fpsTimer: Int? = null

    /** When the drawer is shown and hidden. */
    private val showing = DrawerShowing()

    /** Asks [showing] four times a second, while the drawer is shown, whether its time has come. */
    private var dueTimer: Int? = null

    /** The click of a tap that hid the drawer: the show does not take it. */
    private var swallowClick = false

    /** A mouse moved: over the drawer, or over the stage. */
    private val onPointerMove: (Event) -> Unit = { event ->
        if (kindOf(event) == MOUSE) {
            showing.mouseMoved(now = window.performance.now(), onDrawer = onDrawer(event))
            follow()
        }
    }

    /** A touch or a pen came down: on the drawer, or on the stage. */
    private val onPointerDown: (Event) -> Unit = { event ->
        // only the click of this very tap is swallowed, never a later one
        swallowClick = false

        if (kindOf(event) != MOUSE) {
            swallowClick = showing.tapped(now = window.performance.now(), onDrawer = onDrawer(event))
            follow()
        }
    }

    /** The mouse left the drawer (also out of the window): it rests on it no more. */
    private val onPointerLeave: (Event) -> Unit = { event ->
        if (kindOf(event) == MOUSE) {
            showing.mouseMoved(now = window.performance.now(), onDrawer = false)
            follow()
        }
    }

    /** The click of a tap that hid the drawer goes no further: the live act listens for clicks on the window. */
    private val onClickFirst: (Event) -> Unit = { event ->
        if (swallowClick) {
            swallowClick = false
            event.stopPropagation()
            event.preventDefault()
        }
    }

    private val onFullscreenChange: (Event) -> Unit = { fullscreen = isFullscreen() }

    init {
        lifecycle {
            onMount {
                window.addEventListener("pointermove", onPointerMove)
                window.addEventListener("pointerdown", onPointerDown)
                // before the live act's own listener on the window
                window.addEventListener("click", onClickFirst, true)
                dom?.addEventListener("pointerleave", onPointerLeave)
                document.addEventListener("fullscreenchange", onFullscreenChange)
                pointer()
            }

            onUnmount {
                window.removeEventListener("pointermove", onPointerMove)
                window.removeEventListener("pointerdown", onPointerDown)
                window.removeEventListener("click", onClickFirst, true)
                dom?.removeEventListener("pointerleave", onPointerLeave)
                document.removeEventListener("fullscreenchange", onFullscreenChange)
                dueTimer?.let { window.clearInterval(it) }
                dueTimer = null
                fpsTimer?.let { window.clearInterval(it) }
                fpsTimer = null
                document.documentElement?.asDynamic()?.style?.cursor = ""
            }
        }
    }

    /** Whether [event] happened on the drawer (its buttons, the volume) rather than on the stage. */
    private fun onDrawer(event: Event): Boolean {
        val target = event.target as? Node ?: return false

        return dom?.contains(target) == true
    }

    /** Shows or hides the drawer as [showing] has it now, with what goes with that. */
    private fun follow() {
        if (showing.shown == shown) {
            return
        }

        shown = showing.shown

        if (shown) {
            pointer()
            readFps()
            fpsTimer = window.setInterval({ readFps() }, FPS_MS)
            dueTimer = window.setInterval({
                showing.due(now = window.performance.now())
                follow()
            }, DUE_MS)
        } else {
            pointer()
            letGoOfFocus()
            fpsTimer?.let { window.clearInterval(it) }
            fpsTimer = null
            dueTimer?.let { window.clearInterval(it) }
            dueTimer = null
        }
    }

    /** The keyboard's focus leaves the drawer, where one of its controls had it. */
    private fun letGoOfFocus() {
        val drawer = dom ?: return
        val focused = document.activeElement as? HTMLElement ?: return

        if (drawer.contains(focused)) {
            focused.blur()
        }
    }

    /** The frame rate the live act counted last, in whole frames. */
    private fun readFps() {
        val now = props.picture.fps()

        // NaN-guard: a rate that is no number shows as none
        fps = if (now == now) now.roundToInt() else 0
    }

    /** The mouse pointer is shown with the drawer, and hidden over the stage without it. */
    private fun pointer() {
        document.documentElement?.asDynamic()?.style?.cursor = if (shown) "" else "none"
    }

    override fun VDom.render() {
        div {
            css {
                put("position", "fixed")
                put("left", "0")
                put("right", "0")
                put("bottom", "0")
                put("z-index", "20")
                put("display", "flex")
                put("flex-direction", "column")
                put("align-items", "center")
                put("gap", "10px")
                put("padding", "12px 16px")
                put("background", "rgba(2, 3, 8, 0.82)")
                put("border-top", "1px solid #1b2030")
                put("color", "#c8cfdc")
                put("font-size", "15px")
                put("opacity", if (shown) "1" else "0")
                put("transform", if (shown) "translateY(0)" else "translateY(100%)")
                // hidden, it catches no click meant for the stage
                put("pointer-events", if (shown) "auto" else "none")
                // hidden, its controls cannot be reached by Tab either (after the slide out)
                put("visibility", if (shown) "visible" else "hidden")
                put("transition", if (shown) "opacity 0.3s, transform 0.3s" else "opacity 0.3s, transform 0.3s, visibility 0s 0.3s")

                // with the look's controls: at most about a third of the window, scrolling within itself beyond
                if (props.dev) {
                    put("max-height", "34vh")
                    put("overflow-y", "auto")
                }
            }

            // what happens in the drawer stays in it: the live act's window listeners hear none of it
            onClick { it.stopPropagation() }
            onKeyDown {
                if (shown && !passes(it)) {
                    it.stopPropagation()
                }
            }
            onKeyUp {
                if (shown && !passes(it)) {
                    it.stopPropagation()
                }
            }

            // the song: the one before, what plays, the one after
            row {
                plainButton(text = "Previous song", primary = false) { props.onPrevious() }

                span {
                    css {
                        put("color", "#d7dbe6")
                        put("white-space", "nowrap")
                    }
                    +props.song.title
                }

                span {
                    css {
                        put("color", "#8a93a8")
                        put("font-variant-numeric", "tabular-nums")
                        put("white-space", "nowrap")
                    }
                    +tempoLine(props.song)
                }

                plainButton(text = "Next song", primary = false) { props.onNext() }
            }

            // the picture's quality as a whole: a preset, or custom where the settings below are none of them
            row {
                group {
                    +"Quality"

                    val preset = PicturePreset.matching(samples = samples, fog = fog, shadows = shadows, reflections = reflections)

                    for (each in PicturePreset.entries) {
                        plainButton(text = presetLabel(each), primary = each == preset) { props.picture.choosePreset(each) }
                    }

                    // custom is not chosen: it is what the settings below make where they are no preset
                    span {
                        css {
                            put("padding", "0 0.5em")
                            put("white-space", "nowrap")
                            put("color", if (preset == null) "#d7dbe6" else "#5c6478")
                        }
                        +"Custom"
                    }
                }
            }

            // the picture
            row {
                group {
                    keyed(label = "Oversampling", key = "o")

                    for (step in Picture.STEPS) {
                        plainButton(text = Oversampling.label(step), primary = step == samples) { props.picture.choose(step) }
                    }
                }

                group {
                    keyed(label = "Fog", key = "g")

                    for (quality in FogQuality.entries) {
                        plainButton(text = fogLabel(quality), primary = quality == fog) { props.picture.chooseFog(quality) }
                    }
                }

                group {
                    +"Shadows"

                    for (quality in ShadowQuality.entries) {
                        plainButton(text = shadowsLabel(quality), primary = quality == shadows) {
                            props.picture.chooseShadows(quality)
                        }
                    }
                }

                group {
                    +"Reflections"

                    for (quality in ReflectionQuality.entries) {
                        plainButton(text = reflectionsLabel(quality), primary = quality == reflections) {
                            props.picture.chooseReflections(quality)
                        }
                    }
                }

                div {
                    css {
                        put("width", "4.5em")
                        put("color", "#8a93a8")
                        put("font-variant-numeric", "tabular-nums")
                        put("white-space", "nowrap")
                    }
                    +"$fps fps"
                }
            }

            // the way back, the window and the sound
            row {
                plainButton(text = "Back to start", primary = false) { props.onBack() }

                plainButton(text = if (fullscreen) "Leave fullscreen" else "Enter fullscreen", primary = false) {
                    if (fullscreen) {
                        leaveFullscreen()
                    } else {
                        enterFullscreen()
                    }
                }

                label {
                    css {
                        put("display", "flex")
                        put("align-items", "center")
                        put("gap", "10px")
                    }

                    +"Volume"

                    input(type = InputType.range) {
                        attributes["min"] = "0"
                        attributes["max"] = "100"
                        attributes["step"] = "1"
                        value = "$volume"
                        css {
                            put("width", "160px")
                            put("max-width", "40vw")
                        }
                        onInput { event ->
                            val percent = (event.target as? HTMLInputElement)?.value?.toIntOrNull()?.coerceIn(0, 100)

                            if (percent != null) {
                                volume = percent
                                props.onVolume(percent / 100.0)
                            }
                        }
                    }

                    div {
                        css {
                            put("width", "3.5em")
                            put("white-space", "nowrap")
                            put("font-variant-numeric", "tabular-nums")
                        }
                        +"$volume %"
                    }
                }

                div {
                    css {
                        put("margin-left", "12px")
                        put("color", "#6b7385")
                        put("font-size", "13px")
                        put("white-space", "nowrap")
                    }
                    +props.release
                }
            }

            if (props.dev) {
                looks()
            }
        }
    }

    /**
     * The look's variants with `?dev`, each a group of buttons ([LookGroup.all]), wrapping; below them the front lamp
     * by hand (the key f), which throws the band's shadows, and the show's other keys in a quiet line.
     */
    private fun FlowContent.looks() {
        div {
            css {
                put("display", "flex")
                put("flex-wrap", "wrap")
                put("justify-content", "center")
                put("align-items", "center")
                put("gap", "6px 20px")
                put("max-width", "100%")
                put("padding-top", "8px")
                put("border-top", "1px solid #1b2030")
                put("font-size", "13px")
            }

            for (variant in LookGroup.all(look = look, fog = fog, reflections = reflections)) {
                div {
                    css {
                        put("display", "flex")
                        put("flex-wrap", "wrap")
                        put("justify-content", "center")
                        put("align-items", "center")
                        put("gap", "4px")
                    }
                    variant.held?.let { title = "Does nothing now: $it" }

                    keyed(label = variant.label, key = variant.key)

                    for (option in variant.options) {
                        lookButton(text = option.label, marked = option.marked, dimmed = variant.held != null) {
                            if (option.flips) {
                                props.picture.flipLook()
                            } else {
                                props.picture.chooseLook(option.to)
                            }
                        }
                    }

                    variant.held?.let { reason ->
                        span {
                            css {
                                put("color", "#6b7385")
                                put("font-style", "italic")
                            }
                            +reason
                        }
                    }
                }
            }
        }

        div {
            css {
                put("display", "flex")
                put("justify-content", "center")
                put("align-items", "center")
                put("gap", "4px")
                put("font-size", "13px")
            }
            title = FRONT_HINT

            keyed(label = "Front lamp", key = "f")

            lookButton(text = "Next", marked = false, dimmed = false) {
                props.picture.tryFront()
            }
        }

        div {
            css {
                put("color", "#6b7385")
                put("font-size", "12px")
                put("text-align", "center")
            }
            +OTHER_KEYS
        }
    }

    /**
     * A control's [label], and with `?dev` the [key] that does the same while the show plays, small beside it (none
     * where it has no key).
     */
    private fun FlowContent.keyed(label: String, key: String?) {
        +label

        if (props.dev && key != null) {
            span {
                css {
                    put("color", "#6b7385")
                    put("font-size", "12px")
                    put("margin-left", "-2px")
                }
                +"($key)"
            }
        }
    }

    /**
     * A small button of the look's: [marked] filled as the oversampling's in use is, [dimmed] faint where its variant
     * does nothing now (it can still be chosen).
     */
    private fun FlowContent.lookButton(text: String, marked: Boolean, dimmed: Boolean, onClick: () -> Unit) {
        button {
            css {
                put("padding", "3px 10px")
                put("font", "inherit")
                put("font-size", "13px")
                put("border-radius", "5px")
                put("cursor", "pointer")
                put("opacity", if (dimmed) "0.45" else "1")

                if (marked) {
                    put("color", "#020308")
                    put("background", "#e8ecf4")
                    put("border", "1px solid #e8ecf4")
                } else {
                    put("color", "#e8ecf4")
                    put("background", "transparent")
                    put("border", "1px solid #6b7385")
                }
            }
            onClick { onClick() }

            +text
        }
    }

    /**
     * Whether a key pressed in the drawer goes on to the show: with `?dev` the show's single keys do
     * ([DrawerShowing.passesKey]); without it, none.
     */
    private fun passes(event: KeyboardEvent): Boolean =
        props.dev && DrawerShowing.passesKey(key = event.key, modified = event.ctrlKey || event.altKey || event.metaKey)

    /** A row of the drawer: its parts side by side, centred, wrapping within the row where the window is narrow. */
    private fun FlowContent.row(content: FlowContent.() -> Unit) {
        div {
            css {
                put("display", "flex")
                put("flex-wrap", "wrap")
                put("justify-content", "center")
                put("align-items", "center")
                put("gap", "10px 24px")
                put("max-width", "100%")
            }

            content()
        }
    }

    /** A label and its buttons, side by side, wrapping where the window is narrow. */
    private fun FlowContent.group(content: FlowContent.() -> Unit) {
        div {
            css {
                put("display", "flex")
                put("flex-wrap", "wrap")
                put("justify-content", "center")
                put("align-items", "center")
                put("gap", "8px")
            }

            content()
        }
    }

    /** A shadow quality's label on its button: `Low`, `High`. */
    private fun shadowsLabel(quality: ShadowQuality): String = when (quality) {
        ShadowQuality.Low -> "Low"
        ShadowQuality.High -> "High"
    }

    /** A preset's label on its button: `Low`, `Medium`, `High`, `Max`. */
    private fun presetLabel(preset: PicturePreset): String = when (preset) {
        PicturePreset.Low -> "Low"
        PicturePreset.Medium -> "Medium"
        PicturePreset.High -> "High"
        PicturePreset.Max -> "Max"
    }

    /** The reflections' label on their button: `Off`, `Low`, `Mid`, `High`. */
    private fun reflectionsLabel(quality: ReflectionQuality): String = when (quality) {
        ReflectionQuality.Off -> "Off"
        ReflectionQuality.Low -> "Low"
        ReflectionQuality.Mid -> "Mid"
        ReflectionQuality.High -> "High"
    }

    /** A fog's label on its button: `Off`, `Low`, `High`. */
    private fun fogLabel(quality: FogQuality): String = when (quality) {
        FogQuality.Off -> "Off"
        FogQuality.Low -> "Low"
        FogQuality.High -> "High"
    }

    private companion object {
        /** What the front lamp's button does, as its tooltip. */
        const val FRONT_HINT = "The front lamp by hand, so the band's shadows stand behind them: amber, red, blue, " +
            "violet, then back to the show's own"

        /** The show's other keys, in a quiet line below the look's controls with `?dev`. */
        const val OTHER_KEYS = "Other keys: q total camera, 1 to 6 players, 0 back to the show's camera, " +
            "the right arrow skips the walk-in"

        /** How often the drawer asks whether its time to go has come, while it is shown, in milliseconds. */
        const val DUE_MS = 250

        /** A pointer event's kind for a mouse (the others: `touch`, `pen`). */
        const val MOUSE = "mouse"

        /** The kind of pointer that sent [event]: [MOUSE], `touch` or `pen`; a mouse where it does not say. */
        fun kindOf(event: Event): String = event.asDynamic().pointerType as? String ?: MOUSE

        /** How often the frame rate is read while the drawer is shown, in milliseconds. */
        const val FPS_MS = 1000
    }
}
