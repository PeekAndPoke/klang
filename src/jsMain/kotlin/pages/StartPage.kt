/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_engine.KlangBenchmark
import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.audio_engine.playOnce
import io.peekandpoke.klang.comp.PlayerMiniStats
import io.peekandpoke.klang.comp.motorBackgroundRef
import io.peekandpoke.klang.sprudel.lang.adsr
import io.peekandpoke.klang.sprudel.lang.fast
import io.peekandpoke.klang.sprudel.lang.gain
import io.peekandpoke.klang.sprudel.lang.reverb
import io.peekandpoke.klang.sprudel.lang.sound
import io.peekandpoke.klang.ui.comp.KlangButton
import io.peekandpoke.klang.ui.comp.KlangControlStyles
import io.peekandpoke.klang.ui.comp.KlangFinish
import io.peekandpoke.klang.ui.comp.KlangGauge
import io.peekandpoke.klang.ui.comp.KlangIgnition
import io.peekandpoke.klang.ui.comp.KlangLamp
import io.peekandpoke.klang.ui.comp.KlangPanel
import io.peekandpoke.klang.ui.comp.KlangSpinner
import io.peekandpoke.klang.ui.comp.YesNoModal
import io.peekandpoke.klang.version
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
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.html.onMouseEnter
import io.peekandpoke.ultra.html.onMouseLeave
import io.peekandpoke.ultra.maths.Ease
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.smooth
import io.peekandpoke.ultra.streams.ops.ticker
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
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

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

        /** How long the key stays at START, the lamp light swelling, before the dyno shows. */
        const val KEY_AT_START_MS = 1800.0
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
        return when {
            voiceCount >= 300 -> PerformanceRating(
                tier = "God-Tier",
                message = "Your machine is a god among mortals!",
                showWarning = false,
                lamp = KlangLamp.State.On,
            )

            voiceCount >= 265 -> PerformanceRating(
                tier = "Excellent",
                message = "Your machine is a true work-horse!",
                showWarning = false,
                lamp = KlangLamp.State.On,
            )

            voiceCount >= 230 -> PerformanceRating(
                tier = "Great",
                message = "Your machine handles this like a champ!",
                showWarning = false,
                lamp = KlangLamp.State.On,
            )

            voiceCount >= 195 -> PerformanceRating(
                tier = "Good",
                message = "Your machine is ready to make some music!",
                showWarning = false,
                lamp = KlangLamp.State.On,
            )

            voiceCount >= 160 -> PerformanceRating(
                tier = "Fair",
                message = "Your machine is doing okay. Nothing fancy, it'll work... mostly...",
                showWarning = false,
                lamp = KlangLamp.State.On,
            )

            voiceCount >= 130 -> PerformanceRating(
                tier = "Limited",
                message = "C'mon, it's 2026... maybe consider an upgrade?",
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

            motorBackgroundRef { it.prepare { backgroundReady = true } }
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
        /** The run fades in while the key fades out (the maintainer, 2026-10-26). */
        val uiFadeDurationMs = 800.0
        val uiFadeEase = Ease.In.quad
        var benchmarkStarted = false

        init {
            // The background light fades in at once; the scan and the test wait
            motorBackgroundRef { it.powerOn() }
        }

        private fun elapsedMs() = Kronos.systemUtc.millisNow() - startMs

        /** The run shows once the key has stood at START. */
        fun showsDyno(): Boolean = elapsedMs() >= KEY_AT_START_MS

        /** The key is gone once the run has faded in. */
        fun keyGone(): Boolean = elapsedMs() >= KEY_AT_START_MS + uiFadeDurationMs

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

        override fun update() {
            // noop
        }

        /**
         * Make music: the jingle starts and the first song opens at once, the jingle playing on into it (the
         * maintainer, 2026-10-26: "after clicking Make Music now we can start the "jingle" and go straight to next
         * page"). The song page stops only its own playback, so the jingle ends by itself.
         */
        fun go() {
            val song = sound("<[sd sd sd sd  [bd, cr] ~ ~ ~] ~>").fast(1)
                .gain(0.7)
                .adsr(0.005, 0.2, 0.3, 10.0)
                .reverb(wet = 0.2, size = 3.0, lowpass = 5000.0)

            // No player (the worklet failed?): no jingle, the way on all the same
            Player.get()?.playOnce(song)?.start(KlangCyclicPlayback.Options(rpm = 50.0))

            router.navToUri(Nav.editSongCode(BuiltInSongs.songs.first().id))
        }
    }

    private var state: State by value(StateOffline())

    private val benchmark = KlangBenchmark()

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

    /** The heat is the render's load: the time a block takes against the time it plays. */
    private val dynoHeat by subscribingTo(
        benchmark.progress.map<KlangBenchmark.Progress, Double?> { it.currentRtf.coerceAtMost(1.0) }
            .smooth(sharePer10Ms = PlayerMiniStats.NEEDLE_SMOOTHING, near = PlayerMiniStats.NEEDLE_NEAR)
    )

    private val dynoVoices by subscribingTo(
        benchmark.progress.map<KlangBenchmark.Progress, Double?> { it.activeVoices.toDouble() }
            .smooth(sharePer10Ms = PlayerMiniStats.NEEDLE_SMOOTHING, near = DYNO_VOICES_END * PlayerMiniStats.NEEDLE_NEAR)
    )

    /** The dyno fades in; everything else stands at full opacity. */
    private val currentOpacity get() = (state as? StateBenchmarking)?.getOpacity() ?: 1.0

    private val browserDetectAddon: BrowserDetectAddon? by subscribingTo(addons.browserDetect)

    /** Needed for ui updates */
    @Suppress("unused")
    private val ticker by subscribingTo(ticker(16.milliseconds)) { state.update() }

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    /** A phone or a browser other than Chrome: ask before the Motor starts; no keeps the key at OFF. */
    private fun askToContinue(check: BrowserCheck) {
        val from = state

        modals.show<Boolean> { handle ->
            YesNoModal(
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
                paddingTop = 32.px
                paddingBottom = 32.px
            }

            if (showsRun) {
                // Room for KLANGMOTOR, engraved in the Motor background's plate (its normal map), not drawn as text
                div {
                    key = "title-spacer"
                    css { marginBottom = 32.px }
                }

                // From the run on, everything stands where it stays, so nothing moves when the result comes in (the
                // maintainer, 2026-10-26: "Once the Motor is started all of this should be on screen all the time")
                renderFlexGrowContent { renderRun(current as? StateBenchmarkComplete) }
            }
        }
    }

    /**
     * The key in a layer of its own, centred over the page: one element from OFF through ON to START, so it turns
     * rather than being redrawn, and it fades out over the run while that fades in (the maintainer, 2026-10-26).
     */
    private fun DIV.renderKeyLayer() {
        val current = state

        if (current is StateBenchmarkComplete || (current is StateBenchmarking && current.keyGone())) {
            return
        }

        val fading = current is StateBenchmarking && current.showsDyno()

        div {
            key = "key-layer"
            css {
                position = Position.absolute
                put("inset", "0")
                display = Display.flex
                alignItems = Align.center
                justifyContent = JustifyContent.center
                opacity = if (fading) 1.0 - currentOpacity else 1.0

                if (fading) {
                    pointerEvents = PointerEvents.none
                }
            }

            renderKey(current)
        }
    }

    private fun DIV.renderFlexGrowContent(content: DIV.() -> Unit) {
        div {
            css {
                flexGrow = 1.0
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = Align.center
                justifyContent = JustifyContent.center
                width = 100.pct
            }

            content()
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
     * The run and its result in one layout: the Motor's three gauges, big; under them a panel in the dialog's look,
     * which shows the test while it runs and the tier when it is done, with "Make Music Now" in its key row, waiting
     * until then (the maintainer, 2026-10-26: "put the "Make Music Now" Button where the yes no buttons would be").
     * [complete] is null while the test runs.
     */
    private fun DIV.renderRun(complete: StateBenchmarkComplete?) {
        val styles = KlangControlStyles
        val rating = complete?.let { getPerformanceRating(it.result.maxSafeVoices) }

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

            div {
                css {
                    marginTop = 24.px
                    width = 100.pct
                    textAlign = TextAlign.left
                }

                KlangPanel(bottomRivets = false) {
                    div(classes = styles.dialogTitle.toString()) {
                        if (rating == null) {
                            renderTestingTitle()
                        } else {
                            KlangLamp(state = rating.lamp, size = 10.px, label = rating.tier.uppercase())
                        }
                    }

                    div(classes = styles.dialogBody.toString()) {
                        div {
                            css {
                                display = Display.flex
                                alignItems = Align.center
                                justifyContent = JustifyContent.spaceBetween
                                put("gap", "24px")
                                // The test and the tier take the same room
                                put("min-height", "80px")
                            }

                            div {
                                css { flexGrow = 1.0 }

                                if (rating == null) {
                                    renderRounds()
                                } else {
                                    renderTier(rating)
                                }
                            }

                            renderVoices(complete)
                        }
                    }

                    div(classes = styles.dialogKeys.toString()) {
                        div(classes = styles.dialogKey.toString()) {
                            // The light swells only over a key that can be pressed
                            if (complete != null) {
                                onMouseEnter { motorBackgroundRef { it.hoverStart() } }
                                onMouseLeave { motorBackgroundRef { it.hoverEnd() } }
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
                                    fontSize = 18.px
                                }

                                +"Make Music Now"
                            }
                        }
                    }
                }
            }
        }
    }

    /** The Motor's gauges at the dyno's size, glowing in the background lamp's colour as the Motor's do here. */
    private fun DIV.renderDynoGauges() {
        div {
            css {
                display = Display.flex
                justifyContent = JustifyContent.center
                alignItems = Align.flexEnd
                put("gap", "24px")
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
    private fun DIV.renderTestingTitle() {
        div {
            css {
                display = Display.flex
                alignItems = Align.center
                put("gap", "10px")
                put("letter-spacing", "0.14em")
            }

            KlangSpinner(label = "Testing the Motor", size = 16.px)
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
                fontSize = 12.px
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
    private fun DIV.renderTier(rating: PerformanceRating) {
        div {
            css {
                fontSize = 16.px
                put("line-height", "1.5")
                color = Color("var(--kl-ink-strong)")
            }

            +rating.message
        }

        if (rating.showWarning) {
            div {
                css {
                    marginTop = 8.px
                    fontSize = 13.px
                    color = Color("var(--kl-ink-label)")
                }

                +"Performance may be limited"
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
                // Room for any count, so the panel keeps its width while the number grows
                put("min-width", "150px")
            }

            div {
                css {
                    fontSize = 44.px
                    put("font-weight", "700")
                    put("line-height", "1")
                    put("font-variant-numeric", "tabular-nums")
                    color = Color("var(--kl-ink-strong)")
                }

                +"$voices PS"
            }

            div {
                css {
                    marginTop = 8.px
                    fontSize = 12.px
                    color = Color("var(--kl-ink-label)")
                }

                +if (complete != null) "Average of ${complete.result.rounds} rounds" else "Voices now"
            }
        }
    }
}
