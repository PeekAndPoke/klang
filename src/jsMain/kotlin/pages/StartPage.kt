/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.BuiltInJingles
import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_engine.KlangBenchmark
import io.peekandpoke.klang.comp.KlangCodePlaybackCtrl
import io.peekandpoke.klang.comp.MotorBackground
import io.peekandpoke.klang.comp.PlateComposition
import io.peekandpoke.klang.comp.PlayerMiniStats
import io.peekandpoke.klang.comp.motorBackgroundRef
import io.peekandpoke.klang.compile
import io.peekandpoke.klang.fs
import io.peekandpoke.klang.playJingle
import io.peekandpoke.klang.spine.SwingingLamp
import io.peekandpoke.klang.spine.UiWork
import io.peekandpoke.klang.spine.VisualState
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangCheckbox
import io.peekandpoke.klang.ui.comp.KlangFinish
import io.peekandpoke.klang.ui.comp.KlangGauge
import io.peekandpoke.klang.ui.comp.KlangIgnition
import io.peekandpoke.klang.ui.comp.KlangLamp
import io.peekandpoke.klang.ui.comp.KlangSpinner
import io.peekandpoke.klang.ui.comp.KlangYesNoModal
import io.peekandpoke.klang.version
import io.peekandpoke.klang.visuals.VisualsBoard
import io.peekandpoke.klang.visuals.VisualsParts
import io.peekandpoke.klang.visuals.VisualsParts.glassDome
import io.peekandpoke.kraft.addons.browserdetect.BrowserDetect
import io.peekandpoke.kraft.addons.browserdetect.BrowserDetectAddon
import io.peekandpoke.kraft.addons.browserdetect.browserDetect
import io.peekandpoke.kraft.addons.registry.AddonRegistry.Companion.addons
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.modals.ModalsManager.Companion.modals
import io.peekandpoke.kraft.routing.JoinedPageTitle
import io.peekandpoke.kraft.routing.Router.Companion.router
import io.peekandpoke.kraft.utils.launch
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.datetime.Kronos
import io.peekandpoke.ultra.datetime.MpTimezone
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onMouseEnter
import io.peekandpoke.ultra.html.onMouseLeave
import io.peekandpoke.ultra.maths.Ease
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.smooth
import kotlinx.browser.window
import kotlinx.css.Align
import kotlinx.css.Color
import kotlinx.css.Display
import kotlinx.css.FlexDirection
import kotlinx.css.JustifyContent
import kotlinx.css.PointerEvents
import kotlinx.css.Position
import kotlinx.css.TextAlign
import kotlinx.css.WhiteSpace
import kotlinx.css.alignItems
import kotlinx.css.backgroundColor
import kotlinx.css.color
import kotlinx.css.display
import kotlinx.css.flexDirection
import kotlinx.css.flexGrow
import kotlinx.css.flexShrink
import kotlinx.css.fontFamily
import kotlinx.css.fontSize
import kotlinx.css.height
import kotlinx.css.justifyContent
import kotlinx.css.marginBottom
import kotlinx.css.marginTop
import kotlinx.css.maxWidth
import kotlinx.css.opacity
import kotlinx.css.paddingBottom
import kotlinx.css.paddingTop
import kotlinx.css.pct
import kotlinx.css.pointerEvents
import kotlinx.css.position
import kotlinx.css.px
import kotlinx.css.textAlign
import kotlinx.css.vh
import kotlinx.css.whiteSpace
import kotlinx.css.width
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import kotlinx.html.title
import org.w3c.dom.HTMLElement
import kotlin.math.roundToInt

@Suppress("FunctionName")
fun Tag.StartPage() = comp {
    StartPage(it)
}

/**
 * The start page as starting the Motor (KLANG-00347, the epic's board G): the ignition key, one click, which is also
 * the click the browser needs before it plays audio; the glow lamp while the Motor loads; the dyno run in the Motor's
 * own three gauges, big; the result on a riveted panel, and the way into the first song.
 */
class StartPage(ctx: NoProps) : PureComponent(ctx) {

    companion object {
        /** The dyno's middle gauge, the heat, as the kitchen sink names it; the Motor's is 62. */
        const val DYNO_MIDDLE_PX = 130.0

        /** The dyno's gauges beside it, at the Motor's proportion (52 to 62), so the glide later is a pure shrink. */
        const val DYNO_SIDE_PX = 109.0

        /** The voices gauge's end; a machine past it (the top tier starts at 300) pins the needle there. */
        const val DYNO_VOICES_END = 300.0

        /** The key's cap, as big as the dyno's heat gauge after it (the maintainer, 2026-10-26: 200 was "too massive"). */
        const val KEY_PX = 130.0

        /** How long the lamp stays clean after the run, before its bulb starts to stutter now and then. */
        const val LIGHT_CLEAN_AFTER_RUN_MS = 5000.0

        /** How long the key stays at START, the lamp light swelling, before the dyno shows. */
        const val KEY_AT_START_MS = 1800.0

        /** How long the key is seen at START before it fades; it is gone when the dyno starts to show. */
        const val KEY_FADE_FROM_MS = 400.0

        /** The gauges' row at the least: the heat gauge and some room, on a window whose tiles are smaller. */
        const val GAUGES_ROW_MIN_PX = DYNO_MIDDLE_PX + 16.0

        /** The page's padding at its top. */
        const val PAGE_PADDING_PX = 32

        /** How far the dyno's screen's corners are cut, as the visuals' screens are. */
        private const val DYNO_SCREEN_CUT_PX = 14

        /**
         * The dyno screen's content's own size, scaled as a whole to the screen: the screen's at a tile 140 px wide (a
         * window about 800 px high), three tiles less the margins by two.
         */
        private const val DYNO_SCREEN_WIDTH_PX = 140.0 * (3 - 2 * VisualsBoard.SIDE_MARGIN)
        private const val DYNO_SCREEN_HEIGHT_PX = 140.0 * (2 - 2 * VisualsBoard.SIDE_MARGIN)
    }

    //  PERFORMANCE TIERS  //////////////////////////////////////////////////////////////////////////////////////

    data class PerformanceRating(
        val tier: String,
        val message: String,
        val showWarning: Boolean,
        /** The tier's lamp: on for the machines that carry a song, warning and critical below. */
        val lamp: KlangLamp.State,
    )

    /**
     * The tier for the benchmark's safe voices. The thresholds are 0.85 of the earlier ones since the benchmark fills up
     * to 16 cylinders along its ramp, a reverb each, which measured about 0.84 of the voices on one cylinder (the
     * maintainer, 2026-10-26, KLANG-00347): a machine keeps its tier.
     */
    private fun getPerformanceRating(voiceCount: Int): PerformanceRating {
        val today = Kronos.systemUtc.localDateNow(MpTimezone.systemDefault)

        return when {
            voiceCount >= 300 -> PerformanceRating(
                tier = "God-Tier",
                message = "This machine is a god among mortals!",
                showWarning = false,
                lamp = KlangLamp.State.On,
            )

            voiceCount >= 265 -> PerformanceRating(
                tier = "Excellent",
                message = "This machine is a true work-horse!",
                showWarning = false,
                lamp = KlangLamp.State.On,
            )

            voiceCount >= 230 -> PerformanceRating(
                tier = "Great",
                message = "This machine handles this like a champ!",
                showWarning = false,
                lamp = KlangLamp.State.On,
            )

            voiceCount >= 195 -> PerformanceRating(
                tier = "Good",
                message = "This machine is ready to make some music!",
                showWarning = false,
                lamp = KlangLamp.State.On,
            )

            voiceCount >= 160 -> PerformanceRating(
                tier = "Fair",
                message = "This thing is doing okay. Nothing fancy, it'll work... hopefully...",
                showWarning = false,
                lamp = KlangLamp.State.On,
            )

            voiceCount >= 130 -> PerformanceRating(
                tier = "Limited",
                message = "C'mon, it's ${today.year}... maybe consider an upgrade?",
                showWarning = true,
                lamp = KlangLamp.State.Warning,
            )

            else -> PerformanceRating(
                tier = "Struggling",
                message = "Seriously? Get a real computer! This thing is running on hopes and dreams.",
                showWarning = true,
                lamp = KlangLamp.State.Critical,
            )
        }
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    // Build metadata, published as a stream; redraws this page once it loads.
    private val versionInfo by subscribingTo(version)

    private sealed interface State {
        fun update()

        /** The key: where it stands in this state. */
        val key: KlangIgnition.Position

        /** The glow lamp beside ON. */
        val glowing: Boolean get() = false
    }

    /** What the browser check found; only a phone or a browser other than Chrome asks before the Motor starts. */
    private class BrowserCheck(detect: BrowserDetect) {
        private val platform = detect.getPlatform()
        private val browser = detect.getBrowser()

        val isMobile = platform.type == "mobile" || platform.type == "tablet"
        val isChrome = browser.name.lowercase().contains("chrome") && !browser.name.lowercase().contains("edg")

        fun needsWarning() = isMobile || !isChrome
    }

    /** The key waits at OFF, the plate dim. */
    private inner class StateOffline : State {
        override val key = KlangIgnition.Position.Off

        init {
            // Black until the key turns, as ever: the lamp is one for every plate now (the Spine's), and a visual may
            // have left it on
            VisualState.lamp.off()
        }

        override fun update() {
            // noop
        }

        /** The key is turned: the browser check, then the Motor warms up. */
        fun turn() {
            val addon = browserDetectAddon

            if (addon == null) {
                console.log("Browser detect addon still loading, please wait...")

                return
            }

            val check = BrowserCheck(addon.forCurrentBrowser())

            if (check.needsWarning()) {
                askToContinue(check)
            } else {
                state = StateWarming()
            }
        }
    }

    /**
     * The key at ON, the glow lamp burning while both heavy jobs run: the audio worklet loads and the Motor
     * background is built (row-chunked, so the page stays animated). It goes on when both are done and at least
     * [durationMs] passed; prepare() also fires its callback on a failed WebGL setup, so this cannot hang.
     */
    private inner class StateWarming : State {
        override val key = KlangIgnition.Position.On
        override val glowing = true

        val durationMs = 2500.toDouble()
        val start = Kronos.systemUtc.millisNow()

        var workletReady = false
        var backgroundReady = false

        init {
            launch {
                try {
                    Player.ensure().await()
                } catch (e: Throwable) {
                    // A failed worklet must not wedge the warm-up: go on; the jingle and the navigation tolerate a
                    // missing player
                    console.error("Audio worklet failed to load", e)
                }

                workletReady = true
            }

            // The screen was black until now: the standby light fades in while the Motor warms up, once the plate is
            // built (the lamp is the Spine's, KLANG-00389)
            motorBackgroundRef { it.prepare { backgroundReady = true } }
            VisualState.lamp.warmUp()
        }

        private fun elapsedMs() = Kronos.systemUtc.millisNow() - start

        override fun update() {
            if (elapsedMs() >= durationMs && workletReady && backgroundReady) {
                state = StateBenchmarking()
            }
        }
    }

    /**
     * The key springs to START and the lamp light swells for [KEY_AT_START_MS]; then the dyno fades in and the
     * benchmark runs.
     */
    private inner class StateBenchmarking : State {
        override val key = KlangIgnition.Position.Start

        val startMs = Kronos.systemUtc.millisNow()
        /** The run's fade-in, once the key has faded out (the maintainer, 2026-10-26). */
        val uiFadeDurationMs = 800.0
        val uiFadeEase = Ease.In.quad
        var benchmarkStarted = false

        init {
            // The background light fades in at once; the scan and the test wait
            motorBackgroundRef { it.prepare() }
            VisualState.lamp.powerOn()
        }

        private fun elapsedMs() = Kronos.systemUtc.millisNow() - startMs

        /** The run shows once the key has stood at START. */
        fun showsDyno(): Boolean = elapsedMs() >= KEY_AT_START_MS

        /** The key fades out while it stands at START, a moment after it got there (the maintainer: "fade the button out earlier"). */
        fun keyFades(): Boolean = elapsedMs() >= KEY_FADE_FROM_MS

        /** The key is gone once it has faded, when the dyno starts to show. */
        fun keyGone(): Boolean = elapsedMs() >= KEY_AT_START_MS

        /** The key's opacity while it fades. */
        fun keyOpacity(): Double {
            val progress = ((elapsedMs() - KEY_FADE_FROM_MS) / (KEY_AT_START_MS - KEY_FADE_FROM_MS)).coerceIn(0.0, 1.0)

            return 1.0 - uiFadeEase(progress)
        }

        override fun update() {
            if (!benchmarkStarted && elapsedMs() >= KEY_AT_START_MS + uiFadeDurationMs) {
                benchmarkStarted = true
                launch { benchmark.run(iterations = 3) }
            }
        }

        /** The dyno's fade-in, after the key stood at START. */
        fun getOpacity(): Double {
            val progress = ((elapsedMs() - KEY_AT_START_MS) / uiFadeDurationMs).coerceIn(0.0, 1.0)

            return uiFadeEase(progress)
        }
    }

    private inner class StateBenchmarkComplete(val result: KlangBenchmark.Result) : State {
        override val key = KlangIgnition.Position.Start

        init {
            // The dyno is done: "Gestrandet" fades in while the result waits for "Make Music Now"
            songFadeIn.start()
            waitingSong.play()
            // Compiled now, so "Make Music Now" plays it at once
            launch { jinglePattern = jingle.compile() }
        }

        private val startMs = Kronos.systemUtc.millisNow()

        private var settled = false

        override fun update() {
            // The light stays clean until a while after the run (the maintainer, 2026-10-28), then the bulb settles
            if (!settled && Kronos.systemUtc.millisNow() - startMs >= LIGHT_CLEAN_AFTER_RUN_MS) {
                settled = true
                VisualState.lamp.settle()
            }
        }

        /**
         * Make music: the jingle starts and the first song opens at once, the jingle playing on into it (the
         * maintainer, 2026-10-26: "after clicking Make Music now we can start the "jingle" and go straight to next
         * page"). The song page stops only its own playback, so the jingle ends by itself.
         */
        fun go() {
            // The waiting song stops, its notes ringing out, and the jingle takes over at the user's volume
            songFadeIn.end()
            waitingSong.stop()

            // No player (the worklet failed?): no jingle, the way on all the same. Not compiled yet (a quick click):
            // compiled now, and it follows a moment later
            Player.get()?.let { player ->
                when (val ready = jinglePattern) {
                    null -> launch { jingle.compile()?.let { player.playJingle(jingle = jingle, pattern = it) } }
                    else -> player.playJingle(jingle = jingle, pattern = ready)
                }
            }

            // Still in the click: the browser grants fullscreen only to a user's gesture
            if (enterFullscreen) {
                fs.enter()
            }

            router.navToUri(Nav.editSongCode(BuiltInSongs.songs.first().id))
        }
    }

    private var state: State by value(StateOffline())

    /**
     * The jingle "Make Music Now" plays, one of [BuiltInJingles.all] at random (the maintainer, 2026-10-29: "prepare for
     * a set of jingles in the StartPage so that later we can randomly play one of them"; the drums before it went:
     * "Drop the current drums jingle ... it is bad").
     */
    private val jingle = BuiltInJingles.all.random()

    /** [jingle] compiled, once the dyno is done; null before, or when it does not compile. */
    private var jinglePattern: SprudelPattern? = null

    private val benchmark = KlangBenchmark()

    /**
     * "Gestrandet" in the background once the dyno is done, fading in, until "Make Music Now" is pressed (the
     * maintainer, 2026-10-27/28). Not during the run: it would share the main thread with the measurement and lower
     * the PS the run reports ("or we start the song after the dynamo, probably simpler").
     */
    private val waitingSong = KlangCodePlaybackCtrl.builder()
        .code(BuiltInSongs.soundOfTheSea.code)
        .rpm(BuiltInSongs.soundOfTheSea.rpm)
        .title(BuiltInSongs.soundOfTheSea.title)
        .build()

    /**
     * The waiting song's fade-in: the player's output from silence up to the user's volume over [DURATION_MS]. The
     * user's volume itself ([Player.outputVolume], the knob's and the stored one) is never touched; [end] gives the
     * player that volume back at once.
     */
    private class SongFadeIn {
        companion object {
            const val DURATION_MS = 15_000.0

            /** How often the volume steps; the backend glides between the steps. */
            const val STEP_MS = 100
        }

        private var timer: Int? = null

        fun start() {
            val startMs = Kronos.systemUtc.millisNow()

            Player.get()?.setOutputVolume(0.0)

            timer = window.setInterval({
                val progress = ((Kronos.systemUtc.millisNow() - startMs) / DURATION_MS).coerceIn(0.0, 1.0)

                // Squared: the ear hears the first part of a linear gain ramp as the loudest change
                Player.get()?.setOutputVolume(Player.outputVolume() * progress * progress)

                if (progress >= 1.0) {
                    end()
                }
            }, STEP_MS)
        }

        fun end() {
            timer?.let { window.clearInterval(it) }
            timer = null
            Player.get()?.setOutputVolume(Player.outputVolume())
        }
    }

    private val songFadeIn = SongFadeIn()

    init {
        lifecycle {
            // Left another way than by "Make Music Now" (the Motor's menu, the browser's back): the song stops too
            onUnmount {
                songFadeIn.end()
                waitingSong.stop()
            }
        }
    }

    private val benchmarkProgress: KlangBenchmark.Progress? by subscribingTo(benchmark.progress) { progress ->
        if (progress.isComplete) {
            val result = progress.result

            if (result != null) {
                state = StateBenchmarkComplete(result)
            }
        }
    }

    // The dyno's readings, smoothed as the Motor's are (KLANG-00345): the gauges draw what they get

    private val dynoCylinders by subscribingTo(
        benchmark.progress.map<KlangBenchmark.Progress, Double?> { it.activeCylinders.toDouble() }
            .smooth(sharePer10Ms = PlayerMiniStats.NEEDLE_SMOOTHING, near = KlangBenchmark.CYLINDERS * PlayerMiniStats.NEEDLE_NEAR)
    )

    /**
     * The heat is the render's load: the time a block takes against the time it plays. When the run is done it ends on
     * 99 %, a hair under full (the maintainer, 2026-10-26: 101 % did not fit the gauge's face).
     */
    private val dynoHeat by subscribingTo(
        benchmark.progress.map<KlangBenchmark.Progress, Double?> { if (it.isComplete) 0.99 else it.currentRtf.coerceAtMost(1.0) }
            .smooth(sharePer10Ms = PlayerMiniStats.NEEDLE_SMOOTHING, near = PlayerMiniStats.NEEDLE_NEAR)
    )

    private val dynoVoices by subscribingTo(
        benchmark.progress.map<KlangBenchmark.Progress, Double?> { it.activeVoices.toDouble() }
            .smooth(sharePer10Ms = PlayerMiniStats.NEEDLE_SMOOTHING, near = DYNO_VOICES_END * PlayerMiniStats.NEEDLE_NEAR)
    )

    /** The dyno fades in; everything else stands at full opacity. */
    private val currentOpacity get() = (state as? StateBenchmarking)?.getOpacity() ?: 1.0

    private val browserDetectAddon: BrowserDetectAddon? by subscribingTo(addons.browserDetect)

    /** "Make Music Now" also enters fullscreen, when the user asks; not checked at first, "too overwhelming" (the maintainer). */
    private var enterFullscreen: Boolean by value(false)

    /**
     * The page moves on and redraws a frame at a time, on the UI's frames ([UiWork.frames]): its fades and steps go by the
     * clock, so a rest only holds them, and they catch up on return (KLANG-00388: one animation frame for the UI).
     */
    @Suppress("unused")
    private val frame by subscribingTo(UiWork.frames) { state.update() }

    init {
        // The lamp in the dyno screen's glass, a frame at a time while the lamp is followed; ends with the page
        VisualState.light.subscribe { reflect(it) }
    }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    /** A phone or a browser other than Chrome: ask before the Motor starts; no keeps the key at OFF. */
    private fun askToContinue(check: BrowserCheck) {
        val from = state

        modals.show { handle ->
            KlangYesNoModal(
                handle = handle,
                headline = if (check.isMobile) "A phone or a tablet" else "Not Chrome",
                content = {
                    if (check.isMobile) {
                        +"This version only works reliably on desktop computers or laptops using Google Chrome."
                    } else {
                        +"Currently, only Google Chrome is well tested. Other browsers may have compatibility issues."
                    }
                },
                yesText = "Continue anyway",
                noText = "Not now",
                onResult = { yes ->
                    if (yes && state == from) {
                        state = StateWarming()
                    }
                },
            )
        }
    }

    override fun VDom.render() {
        JoinedPageTitle { listOf("KLANGMOTOR") }

        div {
            key = "start-page"
            css {
                // Anchor the absolutely-positioned version stamp to this container
                position = Position.relative
                height = 100.vh
                width = 100.pct
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = Align.center
                justifyContent = JustifyContent.center
                backgroundColor = Color.transparent
                color = Color.white
                textAlign = TextAlign.center
            }

            // Dark toward the edges, as board G draws it (the maintainer, 2026-10-26: "where things get dark toward the
            // edges of the screen"), the corners nearly black ("the color must fade into real black in the corners").
            // Over the plate, under everything else
            div {
                key = "vignette"
                css {
                    position = Position.absolute
                    put("inset", "0")
                    pointerEvents = PointerEvents.none
                    put(
                        "background",
                        // An oval round the screen's middle in the screen's proportions, a little taller (the
                        // maintainer, 2026-10-26: "back to the oval light so that it matches the screen proportions";
                        // "The y-radius needs to be a bit bigger"); black beyond it
                        // A slow fade, in steps, to the edges ("it needs to fade out slower into the edges"); the corners
                        // come out nearly black (about 0.97), at about 92 % of the oval's radius
                        "radial-gradient(ellipse 71% 85% at 50% 50%, transparent 0%, transparent 30%, " +
                                "rgba(0, 0, 0, 0.3) 50%, rgba(0, 0, 0, 0.65) 68%, rgba(0, 0, 0, 0.9) 85%, #000 95%)",
                    )
                }
            }

            renderContent()

            renderKeyLayer()
        }
    }

    /**
     * The build, a subtle stamp under the key; it goes with the key (the maintainer, 2026-10-26: "I think we do not
     * even need the version after the first step"). Nothing until it has loaded.
     */
    private fun DIV.renderVersionStamp() {
        val info = versionInfo

        if (!info.isAvailable) {
            return
        }

        div {
            key = "version-stamp"
            css {
                marginTop = 18.px
                whiteSpace = WhiteSpace.nowrap
                fontFamily = "monospace"
                fontSize = 11.px
                // Same tone as the engraved KLANGMOTOR title, full opacity for readability
                color = Color("#b8b8b8")
            }
            // e.g. "v0.1.0 · 5c7d2119"; full detail (branch, date) on hover via title
            title = "${info.project} ${info.version} · ${info.gitBranch} · ${info.gitRev}" +
                    (info.date?.let { " · $it" } ?: "")

            +"v${info.version} · ${info.gitRev} · pre-alpha · is glitchd glei"
        }
    }

    private fun DIV.renderContent() {
        val current = state
        val showsRun = current is StateBenchmarkComplete || (current is StateBenchmarking && current.showsDyno())

        div {
            css {
                textAlign = TextAlign.center
                position = Position.relative
                // Flex column ensures the container height includes children's margins
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = Align.center
                width = 100.pct
                height = 100.pct
                paddingTop = PAGE_PADDING_PX.px
                paddingBottom = PAGE_PADDING_PX.px
            }

            if (showsRun) {
                // Room for KLANGMOTOR on its plate in the Motor background, not drawn as text: the gauges' row starts
                // right under the plate, on the next tile row (the maintainer, 2026-10-27: each gauge centred in its tile)
                div {
                    key = "title-spacer"
                    css {
                        flexShrink = 0.0
                        // The page redraws on every frame (its ticker), so a resize moves the gauges along at once
                        put("height", "${(plateBottomPx() - PAGE_PADDING_PX).coerceAtLeast(0.0)}px")
                    }
                }

                // From the run on, everything stands where it stays, so nothing moves when the result comes in (the
                // maintainer, 2026-10-26: "Once the Motor is started all of this should be on screen all the time")
                renderRun(current as? StateBenchmarkComplete)
                renderDynoScreen(current as? StateBenchmarkComplete)
            }
        }
    }

    /** The gauges' row: one tile row high, so each gauge sits in the middle of its tile. */
    private fun gaugesRowPx(): Double =
        MotorBackground.tileWidthPx(window.innerHeight.toDouble()).coerceAtLeast(GAUGES_ROW_MIN_PX)

    /** Where the title's plate ends on the screen, in px from the top: the gauges and the key stand under it. */
    private fun plateBottomPx(): Double {
        val screenHeight = window.innerHeight.toDouble()

        return MotorBackground.titlePlateBottomShare(screenHeight) * screenHeight
    }

    /**
     * The key in a layer of its own over the page: one element from OFF through ON to START, so it turns rather than
     * being redrawn, and it fades out while it stands at START, before the run fades in (the maintainer, 2026-10-26:
     * "fade the button out earlier"). Its cap stands where the heat gauge comes, the same size: under the title's
     * plate, clear of it (the maintainer: the key's "ON" collided with the plate), and the gauge takes its place.
     */
    private fun DIV.renderKeyLayer() {
        val current = state

        if (current is StateBenchmarkComplete || (current is StateBenchmarking && current.keyGone())) {
            return
        }

        val fading = current is StateBenchmarking && current.keyFades()

        div {
            key = "key-layer"
            css {
                position = Position.absolute
                put("inset", "0")
                display = Display.flex
                alignItems = Align.flexStart
                justifyContent = JustifyContent.center

                // The cap's centre on the heat gauge's centre, in the middle of the gauges' row under the plate
                val gaugeCentre = plateBottomPx() + gaugesRowPx() / 2.0

                paddingTop = (gaugeCentre - KEY_PX * KlangIgnition.CAP_CENTRE_FROM_TOP).coerceAtLeast(0.0).px
                opacity = if (current is StateBenchmarking && fading) current.keyOpacity() else 1.0

                if (fading) {
                    pointerEvents = PointerEvents.none
                }
            }

            renderKey(current)
        }
    }

    /** The ignition key, and under it what it is waiting for. */
    private fun DIV.renderKey(current: State) {
        div {
            key = "ignition"
            css {
                opacity = 0.0
                put("animation", "fadeIn 5s ease forwards")
            }

            KlangIgnition(
                position = current.key,
                glowing = current.glowing,
                onTurn = { (state as? StateOffline)?.turn() },
                size = KEY_PX.px,
            )

            div {
                css {
                    display = Display.flex
                    alignItems = Align.center
                    justifyContent = JustifyContent.center
                    put("gap", "10px")
                    marginTop = 24.px
                    fontSize = 20.px
                    color = Color("#d7dadf")
                }

                when (current.key) {
                    KlangIgnition.Position.Off -> +"Turn the key to start the Motor"

                    KlangIgnition.Position.On -> {
                        KlangSpinner(label = "Warming up", size = 18.px)
                        +"Warming up"
                    }

                    KlangIgnition.Position.Start -> +"Starting"
                }
            }

            renderVersionStamp()
        }
    }

    /**
     * The run: the Motor's three gauges, big, on the gauges' row. The test and its result stand on the screen under
     * them ([renderDynoScreen]). [complete] is null while the test runs.
     */
    private fun DIV.renderRun(complete: StateBenchmarkComplete?) {
        div {
            key = "run"
            css {
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = Align.center
                width = 640.px
                maxWidth = 100.pct
                opacity = currentOpacity
            }

            renderDynoGauges()
        }
    }

    /**
     * The test and its result on a screen cut into the plate's three by two tile under the gauges
     * ([PlateComposition.SCREEN]), the visuals' kind: the test while it runs, the tier when it is done, "Make Music Now"
     * at its foot, waiting until then (the maintainer, 2026-10-30: "On the StartPage replace the Dynamo Test popup with
     * a 3x2 tile screen"; KLANG-00394). It stands where the tile lies on the window, so it moves with the plate.
     */
    private fun DIV.renderDynoScreen(complete: StateBenchmarkComplete?) {
        val rating = complete?.let { getPerformanceRating(it.result.maxSafeVoices) }
        val screenWidth = window.innerWidth.toDouble()
        val screenHeight = window.innerHeight.toDouble()
        val tile = MotorBackground.tileWidthPx(screenHeight)
        val rect = MotorBackground.tilesOnScreen(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            composition = PlateComposition.start,
        )[PlateComposition.SCREEN] ?: return
        val margin = tile * VisualsBoard.SIDE_MARGIN
        val width = rect.width - 2 * margin

        div {
            key = "dyno-screen"
            css {
                position = Position.absolute
                put("left", "${rect.left + margin}px")
                put("top", "${rect.top + margin}px")
                put("width", "${width}px")
                put("height", "${rect.height - 2 * margin}px")
                opacity = currentOpacity
                put("overflow", "hidden")
                // A well cut into the plate, as the visuals' screens are
                put("background", "#000")
                put("clip-path", VisualsParts.cutCorners(DYNO_SCREEN_CUT_PX))
                put("box-shadow", "inset 0 6px 14px rgba(0, 0, 0, 0.9), inset 0 0 0 1px rgba(255, 255, 255, 0.06)")
            }

            renderDynoScreenContent(complete = complete, rating = rating, scale = width / DYNO_SCREEN_WIDTH_PX)

            // Glass over it as over the visuals' screens, the lamp mirrored in it ([reflect]; the maintainer,
            // 2026-10-30: "Does this "screen" have the lamp reflection?")
            glassDome()
        }
    }

    /**
     * The dyno screen's content, laid out once at its own size ([DYNO_SCREEN_WIDTH_PX] by [DYNO_SCREEN_HEIGHT_PX]) and
     * scaled as a whole to the screen by [scale]: the screen follows the tiles, so the content looks the same at any
     * window size, resolution or browser zoom, nothing wraps otherwise (the maintainer, 2026-10-30: "The content in the
     * "screen" should always have the same size regardless of screen resolution", after a zoomed-in browser showed it
     * spilling out).
     */
    private fun DIV.renderDynoScreenContent(complete: StateBenchmarkComplete?, rating: PerformanceRating?, scale: Double) {
        div {
            key = "dyno-screen-content"
            css {
                position = Position.absolute
                put("left", "0")
                put("top", "0")
                put("width", "${DYNO_SCREEN_WIDTH_PX}px")
                put("height", "${DYNO_SCREEN_HEIGHT_PX}px")
                put("transform", "scale($scale)")
                put("transform-origin", "0 0")
                put("box-sizing", "border-box")
                display = Display.flex
                flexDirection = FlexDirection.column
                put("gap", "8px")
                put("padding", "12px 16px")
                textAlign = TextAlign.left
            }

            // The headline stays; the grade in the top right corner once the test is done (the maintainer, 2026-10-30:
            // "Keep "Dynamometer Test" as the headline", then "put the grade in the top right corner")
            div {
                css {
                    display = Display.flex
                    alignItems = Align.center
                    justifyContent = JustifyContent.spaceBetween
                    put("gap", "12px")
                    fontFamily = "monospace"
                    put("font-size", "12px")
                    color = Color("var(--kl-ink-label)")
                }

                renderDynoTitle(running = rating == null)

                if (rating != null) {
                    div {
                        css {
                            put("letter-spacing", "0.14em")
                            color = Color("var(--kl-ink-strong)")
                        }

                        KlangLamp(state = rating.lamp, size = 10.px, label = rating.tier.uppercase())
                    }
                }
            }

            div {
                // In the middle of the room between the title and the foot; one too long for it starts at its top
                // instead of growing over the title ("safe")
                css {
                    display = Display.flex
                    put("align-items", "safe center")
                    justifyContent = JustifyContent.spaceBetween
                    put("gap", "20px")
                    flexGrow = 1.0
                    put("min-height", "0")
                    // A little above the middle, so the screen does not feel hollow (the maintainer, 2026-10-30: "we
                    // can also try 3")
                    put("padding-bottom", "14px")
                }

                div {
                    css {
                        flexGrow = 1.0
                        // No word left alone on a line ("Your Motor runs ... on 16 / cylinders.")
                        put("text-wrap", "pretty")
                    }

                    if (rating == null || complete == null) {
                        renderRounds()
                    } else {
                        renderTier(rating = rating, voices = complete.result.maxSafeVoices)
                    }
                }

                renderVoices(complete = complete)
            }

            // The page's one way on, at the screen's foot
            div {
                css {
                    display = Display.flex
                    alignItems = Align.center
                    justifyContent = JustifyContent.spaceBetween
                    put("gap", "16px")
                }

                // The checkbox on the left, the key on the right (the maintainer, 2026-10-30: "Swap "Make Music Now" and
                // "enter fullscreen"")
                div {
                    // Its unlit lamp and its label brighter on the screen's black, where the plate's own were lost
                    // (2026-10-30)
                    css {
                        put("--kl-lamp-off", "color-mix(in srgb, var(--kl-ink-label) 45%, #000)")
                        color = Color("var(--kl-ink-text)")
                    }

                    KlangCheckbox(
                        label = "Enter fullscreen",
                        checked = enterFullscreen,
                        onChange = { enterFullscreen = it },
                    )
                }

                div {
                    // The light swells only over a key that can be pressed, and not over the checkbox beside it
                    if (complete != null) {
                        onMouseEnter { VisualState.lamp.hoverStart() }
                        onMouseLeave { VisualState.lamp.hoverEnd() }
                    }

                    KlangButton(
                        onClick = { complete?.go() },
                        finish = KlangFinish.Silver,
                        availability = if (complete != null) {
                            KlangButton.Availability.Enabled
                        } else {
                            KlangButton.Availability.Disabled
                        },
                        beacon = complete != null,
                    ) {
                        // The page's one way on: bigger than a dialog's key (the maintainer, 2026-10-26)
                        css {
                            put("min-height", "46px")
                            put("font-size", "18px")
                        }

                        +"Make Music Now"
                    }
                }
            }
        }
    }

    /**
     * The lamp mirrored in the dyno screen's glass: its highlight moved toward the lamp and lit with it, by its
     * transform and opacity alone, as the visuals' domes are (`VisualsBoard.reflect`).
     */
    private fun reflect(light: SwingingLamp.Light) {
        val highlight = (dom as? HTMLElement)?.querySelector(".${VisualsParts.HIGHLIGHT}") as? HTMLElement ?: return

        highlight.style.transform = VisualsParts.highlightTransform(swayX = light.swayX, swayY = light.swayY)
        highlight.style.opacity = VisualsParts.highlightOpacity(level = light.level).toString()
    }

    /** The Motor's gauges at the dyno's size, glowing in the background lamp's colour as the Motor's do here. */
    private fun DIV.renderDynoGauges() {
        // Each gauge in the middle of its tile: the heat gauge on the middle tile, the others a tile's width out (the
        // maintainer, 2026-10-27); the page redraws on every frame, so a resize moves them along
        val tile = MotorBackground.tileWidthPx(window.innerHeight.toDouble())
        val gap = (tile - DYNO_MIDDLE_PX / 2.0 - DYNO_SIDE_PX / 2.0).coerceAtLeast(8.0)

        div {
            css {
                display = Display.flex
                justifyContent = JustifyContent.center
                // Each in the middle of its tile both ways: the row one tile row high, the gauges centred in it
                alignItems = Align.center
                height = gaugesRowPx().px
                put("gap", "${gap}px")
                // The background's light is the look's metal: the gauges glow in it, at half strength
                put("--kl-gauge-glow", "color-mix(in srgb, var(--kl-metal-mid) 50%, transparent)")
            }

            KlangGauge(
                name = "Active cylinders",
                value = dynoCylinders,
                range = 0.0..KlangBenchmark.CYLINDERS.toDouble(),
                size = DYNO_SIDE_PX.px,
                icon = { layer_group },
                display = { it.roundToInt().toString() },
            )

            KlangGauge(
                name = "Motor heat",
                value = dynoHeat,
                size = DYNO_MIDDLE_PX.px,
                icon = { microchip },
                display = { "${(it * 100).roundToInt()}%" },
                hotFrom = PlayerMiniStats.HEAT_HOT_FROM,
            )

            KlangGauge(
                name = "Active voices",
                value = dynoVoices,
                range = 0.0..DYNO_VOICES_END,
                size = DYNO_SIDE_PX.px,
                icon = { music },
                display = { it.roundToInt().toString() },
            )
        }
    }

    /** While the test runs, the panel's title: its name with the spinner. */
    /** The screen's headline, while the test runs and after (the maintainer, 2026-10-30); a spinner while it runs. */
    private fun DIV.renderDynoTitle(running: Boolean) {
        div {
            css {
                display = Display.flex
                alignItems = Align.center
                put("gap", "10px")
                put("letter-spacing", "0.14em")
            }

            if (running) {
                KlangSpinner(label = "Testing the Motor", size = 16.px)
            }

            +"DYNAMOMETER TEST"
        }
    }

    /** While the test runs: a lamp per round, lit once the round has begun. */
    private fun DIV.renderRounds() {
        val progress = benchmarkProgress
        val total = progress?.totalIterations ?: 3
        val reached = progress?.currentIteration ?: 0

        div {
            css {
                display = Display.flex
                alignItems = Align.center
                put("gap", "10px")
                fontFamily = "monospace"
                put("font-size", "12px")
                put("letter-spacing", "0.14em")
                color = Color("var(--kl-ink-label)")
            }

            +"ROUND"

            for (round in 1..total) {
                KlangLamp(state = if (round <= reached) KlangLamp.State.On else KlangLamp.State.Off, size = 10.px)
            }
        }
    }

    /** When the test is done: the tier's message, and a warning below the tiers that carry a song. */
    /** What the run found, in words: one supersaw, one cylinder, in the singular; none, plainly. */
    private fun runsLine(voices: Int): String {
        val cylinders = KlangBenchmark.cylindersOf(voices)

        return when (voices) {
            0 -> "Your Motor found no supersaw it could run."
            else -> "Your Motor runs $voices ${if (voices == 1) "supersaw" else "supersaws"} " +
                    "on $cylinders ${if (cylinders == 1) "cylinder" else "cylinders"}."
        }
    }

    private fun DIV.renderTier(rating: PerformanceRating, voices: Int) {
        // What the run found, in the Motor's words, before what it means (the maintainer: "You have 299 supersaws in 16
        // cylinders" or similar); the run's voices are supersaws, each cylinder with its reverb
        div {
            css {
                put("margin-bottom", "6px")
                put("font-size", "12px")
                color = Color("var(--kl-ink-label)")
            }

            +runsLine(voices)
        }

        div {
            css {
                // As large as the lines above and below it (the maintainer, 2026-10-30: "make the "seriously" text the
                // same fontsize as the text above and below it"), brighter than they are
                put("font-size", "12px")
                put("line-height", "1.4")
                color = Color("var(--kl-ink-strong)")
            }

            +rating.message
        }

        if (rating.showWarning) {
            div {
                css {
                    put("margin-top", "6px")
                    put("font-size", "12px")
                    color = Color("var(--kl-ink-label)")
                }

                +"Performance will be limited."
            }
        }
    }

    /** The voices: live while the test runs, the average of the rounds when it is done. */
    private fun DIV.renderVoices(complete: StateBenchmarkComplete?) {
        val voices = complete?.result?.maxSafeVoices ?: (dynoVoices ?: 0.0).roundToInt()

        div {
            css {
                textAlign = TextAlign.center
                whiteSpace = WhiteSpace.nowrap
                // Room for any count, so the screen's text keeps its width while the number grows
                put("min-width", "120px")
            }

            div {
                css {
                    put("font-size", "36px")
                    put("font-weight", "700")
                    put("line-height", "1")
                    put("font-variant-numeric", "tabular-nums")
                    color = Color("var(--kl-ink-strong)")
                }

                +"$voices PS"
            }

            div {
                css {
                    put("margin-top", "6px")
                    put("font-size", "11px")
                    color = Color("var(--kl-ink-label)")
                }

                +if (complete != null) "Average of ${complete.result.rounds} rounds" else "Voices now"
            }
        }
    }
}
