package io.peekandpoke.klang.pages

import de.peekandpoke.kraft.addons.browserdetect.BrowserDetect
import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.routing.JoinedPageTitle
import de.peekandpoke.kraft.routing.Router.Companion.router
import de.peekandpoke.kraft.utils.launch
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.common.datetime.Kronos
import de.peekandpoke.ultra.common.maths.Ease
import de.peekandpoke.ultra.common.maths.Ease.timed
import de.peekandpoke.ultra.html.css
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.ui
import de.peekandpoke.ultra.streams.ops.ticker
import io.peekandpoke.klang.Nav
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_engine.KlangBenchmark
import io.peekandpoke.klang.audio_engine.KlangPlaybackSignal.PlaybackStopped
import io.peekandpoke.klang.comp.*
import io.peekandpoke.klang.strudel.StrudelPlayback
import io.peekandpoke.klang.strudel.lang.fast
import io.peekandpoke.klang.strudel.lang.sound
import io.peekandpoke.klang.strudel.playStrudelOnce
import kotlinx.css.*
import kotlinx.css.properties.LineHeight
import kotlinx.css.properties.scaleX
import kotlinx.css.properties.transform
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds

@Suppress("FunctionName")
fun Tag.StartPage() = comp {
    StartPage(it)
}

class StartPage(ctx: NoProps) : PureComponent(ctx) {

    //  PERFORMANCE TIERS  //////////////////////////////////////////////////////////////////////////////////////

    data class PerformanceRating(
        val tier: String,
        val message: String,
        val showWarning: Boolean,
        val color: Color,
    )

    private fun getPerformanceRating(voiceCount: Int): PerformanceRating {
        return when {
            voiceCount >= 140 -> PerformanceRating(
                tier = "God-Tier",
                message = "Your machine is a god among mortals! 🚀",
                showWarning = false,
                color = Color("#00D9FF")  // Cyan
            )

            voiceCount >= 120 -> PerformanceRating(
                tier = "Excellent",
                message = "Your machine is a true work-horse! 💪",
                showWarning = false,
                color = Color("#4CAF50")  // Green
            )

            voiceCount >= 100 -> PerformanceRating(
                tier = "Great",
                message = "Your machine handles this like a champ!",
                showWarning = false,
                color = Color("#8BC34A")  // Light Green
            )

            voiceCount >= 80 -> PerformanceRating(
                tier = "Good",
                message = "Your machine is ready to make some music!",
                showWarning = false,
                color = Color("#CDDC39")  // Lime
            )

            voiceCount >= 60 -> PerformanceRating(
                tier = "Fair",
                message = "Your machine is doing okay. Nothing fancy, but it'll work.",
                showWarning = false,
                color = Color("#FFC107")  // Amber
            )

            voiceCount >= 40 -> PerformanceRating(
                tier = "Limited",
                message = "C'mon, it's 2026... maybe consider an upgrade? 🤔",
                showWarning = true,
                color = Color("#FF9800")  // Orange
            )

            else -> PerformanceRating(
                tier = "Struggling",
                message = "Seriously? Get a real computer! This thing is running on hopes and dreams. 💀",
                showWarning = true,
                color = Color("#F44336")  // Red
            )
        }
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private sealed interface State {
        fun update()

        fun getOpacity(): Double

        fun gotoNext()
    }

    private inner class StateOffline : State {
        override fun update() {
            // noop
        }

        override fun getOpacity(): Double = 0.07

        override fun gotoNext() {
            console.log("Starting flow: browser check")
            state = StateBrowserCheck()
        }
    }

    private inner class StateBrowserCheck : State {
        private val platform = browserDetact.getPlatform()
        private val browser = browserDetact.getBrowser()
        private val isMobileDevice = platform.type == "mobile" || platform.type == "tablet"
        private val isChrome = browser.name.lowercase().contains("chrome") && !browser.name.lowercase().contains("edg")
        private var hasChecked = false

        override fun update() {
            // If not mobile and is Chrome, auto-proceed to booting after first render
            if (!hasChecked && !isMobileDevice && isChrome) {
                hasChecked = true
                state = StateBooting(this)
            }
        }

        override fun getOpacity(): Double = 1.0

        override fun gotoNext() {
            console.log("User accepted browser warning, proceeding to booting")
            state = StateBooting(this)
        }

        fun needsWarning() = isMobileDevice || !isChrome
        fun isMobile() = isMobileDevice
        fun isNonChrome() = !isChrome
    }

    private inner class StateBooting(previous: State) : State {
        val durationMs = 2500.toDouble()
        val start = Kronos.systemUtc.millisNow()
        val opacityEase = Ease.In.quad.timed(previous.getOpacity(), 1.0, durationMs.milliseconds)

        init {
            launch {
                Player.ensure().await()
            }
        }

        private fun elapsedMs() = Kronos.systemUtc.millisNow() - start

        override fun update() {
            if (elapsedMs() >= durationMs) {
                state = StateBenchmarking()
            }
        }

        override fun getOpacity(): Double = opacityEase((elapsedMs()) / durationMs)

        override fun gotoNext() {
            // noop
        }
    }

    private inner class StateBenchmarking : State {
        init {
            launch {
                benchmark.run()
            }
        }

        override fun update() {
            // noop - progress is handled by stream subscription
        }

        override fun getOpacity(): Double = 1.0

        override fun gotoNext() {
            // noop - automatic transition on benchmark completion
        }
    }

    private inner class StateBenchmarkComplete(val result: KlangBenchmark.Result) : State {
        override fun update() {
            // noop
        }

        override fun getOpacity(): Double = 1.0

        override fun gotoNext() {
            val song = sound("[bd bd bd bd  [ds, cr] ~ ~ ~]").fast(1)

            val playback = Player.get()?.playStrudelOnce(song)

            // Wait for the song to finish before navigating
            playback?.signals?.on<PlaybackStopped> {
                playback.signals.clear()
                console.log("Playback stopped, navigating to new song page")
                router.navToUri(Nav.newSongCode())
            }

            playback?.start(StrudelPlayback.Options(cyclesPerSecond = 1.0))
        }

        fun getResult() = result
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

    private val currentOpacity get() = state.getOpacity()

    private val ticker by subscribingTo(ticker(16.milliseconds)) { state.update() }

    private val browserDetact = BrowserDetect.forCurrentBrowser()

    //  IMPL  ///////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        JoinedPageTitle { listOf("KLANG", "AUDIO", "MOTÖR") }

        div {
            key = "start-page"
            css {
                height = 100.vh
                width = 100.pct
                display = Display.flex
                flexDirection = FlexDirection.column
                alignItems = Align.center
                justifyContent = JustifyContent.center
                backgroundColor = Color.black
                color = Color.white
                textAlign = TextAlign.center
            }


            renderContent()
        }
    }

    private fun DIV.renderContent() {
        div {
            key = "content-${state::class.simpleName}"

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

            // Render state-specific UI
            when (val currentState = state) {
                is StateOffline -> renderStateOffline()
                is StateBrowserCheck -> renderStateBrowserCheck(currentState)
                is StateBooting -> renderStateBooting()
                is StateBenchmarking -> renderStateBenchmarking()
                is StateBenchmarkComplete -> renderStateBenchmarkComplete(currentState)
            }
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

    private fun DIV.renderVisualizers() {
        div {
            css {
                width = 100.pct
            }

            div {
                key = "stats"
                css {
                    marginBottom = 6.px
                    opacity = currentOpacity
                }
                PlayerMiniStats()
            }

            div {
                key = "oscilloscope"
                css {
                    height = 60.px
                    opacity = currentOpacity
                }
                Oscilloscope { Player.get() }
            }

            div {
                key = "spectrum-visualizer"

                val spectHeight = 150
                css {
                    position = Position.absolute
                    pointerEvents = PointerEvents.none
                    // Anchor to bottom
                    bottom = 0.px
                    left = 0.px
                    right = 0.px
                    // Dimensions
                    height = spectHeight.px
                    width = 100.pct

                    opacity = 0.5
                }

                Spectrumeter { Player.get() }
            }
        }
    }

    private fun DIV.renderStateOffline() {
        renderOfflineState()
    }

    private fun DIV.renderStateBrowserCheck(browserCheckState: StateBrowserCheck) {
        renderBrowserCheck(browserCheckState)
    }

    private fun DIV.renderStateBooting() {
        renderBootingState()
    }

    private fun DIV.renderStateBenchmarking() {
        renderHeader()
        renderFlexGrowContent {
            renderBenchmarkingState()
        }
    }

    private fun DIV.renderStateBenchmarkComplete(completeState: StateBenchmarkComplete) {
        renderHeader()
        renderFlexGrowContent {
            renderBenchmarkCompleteState(completeState)
        }
        renderVisualizers()
    }

    private fun DIV.renderHeader() {
        div {
            key = "title"

            css {
                marginBottom = 32.px
            }

            div {
                css {
                    whiteSpace = WhiteSpace.nowrap
                    opacity = currentOpacity
                }

                icon.music {
                    css {
                        marginRight = 16.px
                        fontSize = 3.em
                    }
                }

                ui.text {
                    css {
                        fontFamily = "monospace"
                        fontSize = 3.em
                        lineHeight = LineHeight("2.0em")
                        color = Color.white
                        display = Display.inlineBlock
                        fontWeight = FontWeight.bold
                    }
                    +"KLANG AUDIO MOTÖR"
                }

                icon.music {
                    css {
                        transform { scaleX(-1.0) }
                        marginLeft = 16.px
                        fontSize = 3.em
                    }
                }
            }

            // Pre-alpha sub-headline
            div {
                css {
                    fontSize = 1.4.em
                    color = Color("#888")
                    opacity = currentOpacity
                    marginTop = 8.px
                }
                +"pre-alpha"
            }
        }
    }

    private fun DIV.renderOfflineState() {
        div {
            RoundButton(
                icon = { power_off },
                color = Color.red,
                onClick = {
                    state.gotoNext()
                }
            )
        }
    }

    private fun DIV.renderBrowserCheck(browserCheckState: StateBrowserCheck) {
        if (browserCheckState.needsWarning()) {
            div {
                css {
                    display = Display.inlineBlock
                    maxWidth = 500.px
                    marginTop = 20.px
                    marginBottom = 20.px
                    paddingTop = 20.px
                    paddingBottom = 20.px
                    paddingLeft = 20.px
                    paddingRight = 20.px
                    backgroundColor = Color("#333")
                    borderRadius = 8.px
                }

                ui.icon.warning.large { css { color = Color.orange } }

                div {
                    css {
                        marginTop = 16.px
                        fontSize = 1.1.em
                    }
                    when {
                        browserCheckState.isMobile() -> +"Mobile Device Detected"
                        browserCheckState.isNonChrome() -> +"Non-Chrome Browser Detected"
                    }
                }

                div {
                    css {
                        marginTop = 12.px
                        fontSize = 0.95.em
                        color = Color("#ccc")
                        lineHeight = LineHeight("1.5")
                    }
                    when {
                        browserCheckState.isMobile() -> {
                            +"This version only works reliably on desktop computers or laptops using Google Chrome."
                        }

                        browserCheckState.isNonChrome() -> {
                            +"Currently, only Google Chrome is well tested. Other browsers may have compatibility issues."
                        }
                    }
                }

                ui.button.orange {
                    css {
                        marginTop = 16.px
                    }
                    +"Continue Anyway"

                    onClick { browserCheckState.gotoNext() }
                }
            }
        }
    }

    private fun DIV.renderBootingState() {
        div {
            css {
                marginTop = 20.px
            }
            ui.active.inline.loader {}
            div {
                css {
                    marginTop = 10.px
                    fontSize = 0.9.em
                    color = Color("#aaa")
                }
                +"Initializing Audio Engine..."
            }
        }
    }

    private fun DIV.renderBenchmarkGauges(progress: KlangBenchmark.Progress?) {
        // Gauges container
        div {
            css {
                display = Display.flex
                justifyContent = JustifyContent.center
                alignItems = Align.flexEnd
                marginTop = 20.px
                marginBottom = 20.px
            }

            // Round gauge (iteration)
            div {
                css {
                    textAlign = TextAlign.center
                    marginLeft = 8.px
                    marginRight = 8.px
                }

                val currentRound = progress?.currentIteration?.toDouble() ?: 0.0
                val totalRounds = progress?.totalIterations?.toDouble() ?: 5.0

                RoundGauge(
                    value = { currentRound },
                    display = { "${ceil(it).toInt()}" },  // Ceil to always show the current/next round number
                    title = "Round",
                    range = 0.0..totalRounds,
                    icon = { redo },
                    iconColors = listOf(
                        (0.0..totalRounds) to GaugeColors.excellent
                    ),
                    disabled = false,
                    size = 70.px
                )
            }

            // CPU gauge (middle, larger)
            div {
                css {
                    textAlign = TextAlign.center
                    marginLeft = 8.px
                    marginRight = 8.px
                }

                val rtfPct = if (progress != null) {
                    (progress.currentRtf * 100).coerceAtMost(100.0)
                } else {
                    0.0
                }

                RoundGauge(
                    value = { rtfPct },
                    display = { "${it.toInt()}%" },
                    title = "CPU",
                    range = 0.0..100.0,
                    icon = { microchip },
                    iconColors = listOf(
                        (0.0..30.0) to GaugeColors.excellent,
                        (30.0..50.0) to GaugeColors.good,
                        (50.0..70.0) to GaugeColors.moderate,
                        (70.0..85.0) to GaugeColors.warning,
                        (85.0..100.0) to GaugeColors.critical
                    ),
                    disabled = false,
                    size = 100.px
                )
            }

            // Active voices gauge
            div {
                css {
                    textAlign = TextAlign.center
                    marginLeft = 8.px
                    marginRight = 8.px
                }

                RoundGauge(
                    value = { progress?.activeVoices?.toDouble() ?: 0.0 },
                    display = { "${it.toInt()}" },
                    title = "Active Voices",
                    range = 0.0..200.0,
                    icon = { music },
                    iconColors = listOf(
                        (0.0..50.0) to GaugeColors.excellent,
                        (50.0..100.0) to GaugeColors.good,
                        (100.0..150.0) to GaugeColors.moderate,
                        (150.0..180.0) to GaugeColors.warning,
                        (180.0..200.0) to GaugeColors.critical
                    ),
                    disabled = false,
                    size = 70.px
                )
            }
        }
    }

    private fun DIV.renderBenchmarkingState() {
        div {
            css {
                display = Display.inlineBlock
                maxWidth = 600.px
                marginTop = 20.px
                marginBottom = 20.px
                paddingTop = 20.px
                paddingBottom = 20.px
                paddingLeft = 20.px
                paddingRight = 20.px
            }

            val progress = benchmarkProgress

            div {
                css {
                    fontSize = 1.2.em
                    marginBottom = 24.px
                }
                +"Performance Benchmark"
            }

            renderBenchmarkGauges(progress)
        }
    }

    private fun DIV.renderBenchmarkCompleteState(completeState: StateBenchmarkComplete) {
        div {
            css {
                display = Display.inlineBlock
                maxWidth = 600.px
                marginTop = 20.px
                marginBottom = 20.px
                paddingTop = 20.px
                paddingBottom = 20.px
                paddingLeft = 20.px
                paddingRight = 20.px
            }

            div {
                css {
                    fontSize = 1.2.em
                    marginBottom = 24.px
                    color = Color.white
                }
                +"Benchmark Complete ✓"
            }

            val result = completeState.getResult()

            // Show the same gauges as during benchmarking
            renderBenchmarkGauges(benchmarkProgress)

            // Performance rating
            val rating = getPerformanceRating(result.maxSafeVoices)

            div {
                css {
                    marginTop = 24.px
                    paddingTop = 20.px
                    paddingBottom = 20.px
                    paddingLeft = 20.px
                    paddingRight = 20.px
                    backgroundColor = if (rating.showWarning) Color("#3d2a1f") else Color("#1a3d2a")
                    borderRadius = 8.px
                    if (rating.showWarning) {
                        borderWidth = 2.px
                        borderStyle = BorderStyle.solid
                        borderColor = rating.color
                    }
                }

                // Tier badge
                div {
                    css {
                        fontSize = 0.9.em
                        fontWeight = FontWeight.bold
                        color = Color.white
                        marginBottom = 12.px
                        textTransform = TextTransform.uppercase
                        letterSpacing = 1.px
                    }
                    +rating.tier
                }

                // Message
                div {
                    css {
                        fontSize = 1.1.em
                        color = Color.white
                        lineHeight = LineHeight("1.5")
                    }
                    +rating.message
                }

                // Warning icon for low performance
                if (rating.showWarning) {
                    div {
                        css {
                            marginTop = 12.px
                            fontSize = 0.9.em
                            color = rating.color
                        }
                        ui.icon.warning {
                            css {
                                color = rating.color
                            }
                        }
                        +" Performance may be limited"
                    }
                }
            }

            // Results summary
            div {
                css {
                    marginTop = 24.px
                    paddingTop = 20.px
                    paddingBottom = 20.px
                    paddingLeft = 20.px
                    paddingRight = 20.px
                    backgroundColor = Color("#2a2a2a")
                    borderRadius = 8.px
                }

                // Main stat
                div {
                    css {
                        display = Display.flex
                        alignItems = Align.center
                        justifyContent = JustifyContent.center
                        marginBottom = 16.px
                    }

                    div {
                        css {
                            fontSize = 3.em
                            fontWeight = FontWeight.bold
                            color = rating.color
                            marginRight = 12.px
                        }
                        +"${result.maxSafeVoices}"
                    }

                    div {
                        css {
                            fontSize = 1.1.em
                            color = Color("#ccc")
                            textAlign = TextAlign.left
                        }
                        div {
                            +"simultaneous"
                        }
                        div {
                            +"voices"
                        }
                    }
                }

                // Subtext
                div {
                    css {
                        fontSize = 0.9.em
                        color = Color("#888")
                        textAlign = TextAlign.center
                    }
                    +"Average from 5 test runs"
                }
            }

            // Start Coding button
            ui.button.white.large {
                css {
                    marginTop = 24.px
                }
                icon.music()
                +"Make Music Now"

                onClick { completeState.gotoNext() }
            }
        }
    }
}
