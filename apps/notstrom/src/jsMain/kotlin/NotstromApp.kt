/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.peekandpoke.klang.apps.common.BrowserCheck
import io.peekandpoke.klang.apps.common.BuiltInSongs
import io.peekandpoke.klang.apps.common.FrameStats
import io.peekandpoke.klang.apps.common.MachineScore
import io.peekandpoke.klang.apps.common.PictureQuality
import io.peekandpoke.klang.apps.common.createScriptEngine
import io.peekandpoke.klang.apps.common.liveact.PlayingMusic
import io.peekandpoke.klang.apps.common.liveact.ShowPlayback
import io.peekandpoke.klang.apps.common.liveact.SongReading
import io.peekandpoke.klang.audio_engine.KlangBenchmark
import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.audio_engine.KlangPlayer
import io.peekandpoke.klang.audio_engine.klangPlayer
import io.peekandpoke.klang.audio_engine.play
import io.peekandpoke.klang.audio_engine.resolveBestSampleRate
import io.peekandpoke.klang.audio_fe.create
import io.peekandpoke.klang.audio_fe.samples.SampleCatalogue
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.sprudel.SprudelDiagnostic
import io.peekandpoke.klang.sprudel.SprudelDiagnostics
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.viz.liveact.LiveAct
import io.peekandpoke.klang.viz.liveact.Picture
import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches
import io.peekandpoke.kraft.addons.browserdetect.BrowserDetectAddon
import io.peekandpoke.kraft.addons.browserdetect.browserDetect
import io.peekandpoke.kraft.addons.registry.AddonRegistry.Companion.addons
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import kotlinx.browser.window
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.random.Random

@Suppress("FunctionName")
fun Tag.NotstromApp(query: String) = comp(
    NotstromApp.Props(query = query)
) {
    NotstromApp(it)
}

/**
 * The band's app (KLANG-00465): a start screen, a check of this machine (the sound, then the picture), then the band
 * walks onto the stage and plays Der Schmetterling live with the engine's sound, for as long as the page is open (the
 * song is a live pattern: it has no end).
 *
 * Nothing that costs is made before the click on the start button (a browser allows sound only after one). A page
 * that is not secure (plain http on a network address) cannot have the engine's sound: the start screen says so and
 * starts nothing. Outside desktop Chrome it warns first ([BrowserCheck], as the studio does). With the click (or
 * "Continue anyway") the player is made, then the machine is tested: the engine's dynamometer ([KlangBenchmark], the
 * studio's PS) and the stage drawn for a few seconds ([FrameStats]), at 1x and, where it may matter, at 2x
 * oversampling ([PictureQuality]). The result is shown as a score ([MachineScore]) with the choices for the show (the
 * stroboscope, fullscreen) right above the button that starts it; `?notest` skips the tests ([skipsTests]) and shows
 * the choices on the start screen. Nothing of the result is sent or kept.
 *
 * For the show the song is compiled once, its playback is made and read ahead for the band ([SongReading]: the band
 * plays the notes that will sound), and it is played for the show alone ([ShowPlayback]): the walk-in begins at once
 * (the click was taken here), and the live act's clock starts the song when the walk-in is over. The live act steps the oversampling down by itself where the frames stay slow. While the
 * show plays, a moving mouse brings up a drawer at the bottom ([ShowDrawer]: fullscreen, the oversampling, the volume). What fails on
 * the way is said on the start screen, in plain words.
 *
 * The address's switches are the live act's ([Switch]: `?seed=7`, `?still`, `?oversample=2` and the rest);
 * `?nostrobe` presets the choice for the stroboscope. The line of text at the top is hidden for visitors; `?hud` shows
 * it ([showSwitches]).
 */
class NotstromApp(ctx: Ctx<Props>) : Component<NotstromApp.Props>(ctx) {

    /** [query]: the page's address from the question mark on (the live act's switches). */
    data class Props(val query: String)

    /** Where the page is. */
    private enum class Phase {
        /** The start screen. */
        Start,

        /** After the click: the player is made, or the song and the band get ready. */
        Readying,

        /** The engine's dynamometer runs. */
        TestingSound,

        /** The stage is drawn and its frames are counted. */
        TestingPicture,

        /** The tests' result, the choices for the show, and the button that starts it. */
        Result,

        /** The band walks in and plays. */
        Playing,
    }

    /**
     * A mounted stage: the show or the picture test ([test]); [key] is another one for every stage; [picture] sees and
     * sets its oversampling (the test's 2x part, the drawer) and sees its frame rate (the drawer).
     */
    private class Running(val show: Show, val key: String, val test: Boolean, val picture: Picture = Picture())

    /** What the tests found, and the oversampling the picture test chose. */
    private class Tested(val voices: Int, val frames: FrameStats, val score: MachineScore, val oversample: Int)

    private val scope = MainScope()

    /** This release's name, from the fields baked into the page ([pageRelease]): the figures are asked for with it. */
    private val release: String = pageRelease()

    private var phase: Phase by value(Phase.Start)

    /** What went wrong at the last start, in plain words; null where nothing did. */
    private var problem: String? by value(null)

    /** Whether the stroboscope flashes: a choice right before the show; `?nostrobe` presets it off. */
    private var strobe: Boolean by value(!Switches.parse(props.query).has(Switch.NoStrobe))

    /**
     * Whether the show starts in fullscreen: a choice right before the show, made at first (the maintainer,
     * 2026-11-18). Unlike the studio's start page on purpose: this is a show. Where the browser refuses fullscreen or
     * has none, the show starts all the same.
     */
    private var fullscreen: Boolean by value(true)

    private var running: Running? by value(null)

    /** The warning the start screen shows before the show, outside desktop Chrome; null while there is none. */
    private var warning: BrowserCheck.Warning? by value(null)

    /** The dynamometer's progress while it runs. */
    private var soundProgress: KlangBenchmark.Progress? by value(null)

    /** How far the picture test is, in tenths: 0 to 10, over both its parts. */
    private var pictureTenths: Int by value(0)

    /** The tests' result, once there is one. */
    private var tested: Tested? by value(null)

    /** Whether a click on Start waits for the browser's detection (it loads with the page). */
    private var checking = false

    /** The browser's detection, once it has loaded. */
    private val detected = CompletableDeferred<BrowserDetectAddon>()

    private val browserDetectAddon: BrowserDetectAddon? by subscribingTo(addons.browserDetect) { addon ->
        addon?.let { detected.complete(it) }
    }

    /** Who is told of each frame the picture test's stage draws, while it measures. */
    private var frameSink: ((Double) -> Unit)? = null

    /** Handed to the picture test's stage once, so its props stay the same. */
    private val onFrame: (Double) -> Unit = { ms -> frameSink?.invoke(ms) }

    /**
     * The player's making, once begun: one for the page, ever. A click that waited too long for it gives up waiting,
     * not the making: the next click waits for the same one, and a player made late is the one used.
     */
    private var playerMaking: Deferred<KlangPlayer>? = null

    /** The player, once made. */
    private var player: KlangPlayer? = null

    /** What the sound's preparation does now, in plain words; null while it does nothing. */
    private var preparing: String? by value(null)

    /** How far the band's figures have loaded on the stage that is mounted; null while none loads. */
    private var figures: Picture.Figures? by value(null)

    /** Stops following the mounted stage's figures. */
    private var figuresFollow: (() -> Unit)? = null

    /** What runs beside the show: the song read on. */
    private var beside: Job? = null

    private var stages = 0

    init {
        lifecycle {
            onUnmount { scope.cancel() }
        }
    }

    /**
     * The click on the start button. A page that is not secure starts nothing (it says why). Outside desktop Chrome
     * the start screen warns first ([BrowserCheck], as the studio does), else it goes on ([go]), still in the click
     * where the browser's detection is there (it loads with the page; where it is not there yet, the click waits for
     * it, at most [DETECT_MS], then it goes on unchecked, said in the console).
     */
    private fun start() {
        if (phase != Phase.Start || checking) {
            return
        }

        // the engine's sound runs in an audio worklet, which a browser gives only to a secure page (https, or
        // localhost): elsewhere it would fail inside the engine, and the start screen would only know after its time-out
        if (window.asDynamic().isSecureContext != true) {
            problem = notSecure(address = window.location.origin)

            return
        }

        val ready = browserDetectAddon

        if (ready != null) {
            checked(ready)

            return
        }

        checking = true

        scope.launch {
            val addon = withTimeoutOrNull(DETECT_MS) { detected.await() }

            checking = false

            if (addon == null) {
                console.warn("Notstrom: the browser's detection did not load, the show starts unchecked")
                goOn()
            } else {
                checked(addon)
            }
        }
    }

    /** The browser is checked: a warning where it is needed, else on. */
    private fun checked(addon: BrowserDetectAddon) {
        val detect = addon.forCurrentBrowser()
        val found = BrowserCheck.of(platformType = detect.getPlatform().type, browserName = detect.getBrowser().name)

        warning = found.warning

        if (warning == null) {
            goOn()
        }
    }

    /** On after Start (or "Continue anyway"): the tests, or with `?notest` the show at once. */
    private fun goOn() {
        go(test = !skipsTests(props.query))
    }

    /** "Continue anyway" on the warning: a click too, so it goes on as with Start. */
    private fun continueAnyway() {
        warning = null
        goOn()
    }

    /** "Not now" on the warning: back to the start screen. */
    private fun notNow() {
        warning = null
    }

    /**
     * Goes on after a click (Start, the warning, the tests' result): the machine is [test]ed first, or the show gets
     * ready and begins (in fullscreen where it was chosen: still in the click, which the browser asks for).
     */
    private fun go(test: Boolean) {
        if (phase != Phase.Start && phase != Phase.Result) {
            return
        }

        if (!test && fullscreen) {
            enterFullscreen()
        }

        phase = Phase.Readying
        problem = null
        beside?.cancel()
        beside = null
        mount(null)

        scope.launch {
            try {
                if (test) {
                    runTests()
                } else {
                    begin()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NotStarted) {
                console.error("Notstrom could not start: ${e.message}", e.cause)
                stop(problem = e.message)
            } catch (e: Throwable) {
                console.error("Notstrom could not start", e)
                stop(problem = "Something went wrong while the show got ready. Please reload the page and try again.")
            }
        }
    }

    /** Back to the start screen, saying why. */
    private fun stop(problem: String?) {
        beside?.cancel()
        beside = null
        frameSink = null
        mount(null)
        preparing = null
        this.problem = problem
        phase = Phase.Start
    }

    /**
     * Mounts [stage] (or none), and follows how far its band's figures have loaded ([figures]): where one cannot be
     * loaded, the start screen says so at once.
     */
    private fun mount(stage: Running?) {
        figuresFollow?.invoke()
        figuresFollow = null
        figures = null
        running = stage

        if (stage != null) {
            figuresFollow = stage.picture.figures.subscribeToStream { now ->
                figures = now.takeIf { it.of > 0 }

                if (now.failed) {
                    stop(problem = FIGURES_FAILED)
                }
            }
        }
    }

    /** The player is made (with the click), then the sound test, then the picture test, then their result. */
    private suspend fun runTests() {
        ensurePlayer()

        soundProgress = null
        phase = Phase.TestingSound

        KlangBenchmark().run(iterations = SOUND_ROUNDS, onProgress = { soundProgress = it })

        val voices = soundProgress?.result?.maxSafeVoices ?: 0

        phase = Phase.TestingPicture

        val density = window.devicePixelRatio

        pictureTenths = 0

        val stage = testStage()

        // the progress counts both parts as one, the 1x part the first two thirds; without a 2x part it jumps to full
        val frames = measurePicture(stage = stage, warmMs = PICTURE_WARM_MS, measureMs = PICTURE_MS, from = 0.0, to = 2.0 / 3)

        // not one frame: the band never stood (its figures did not load), and there is nothing to measure
        if (frames == FrameStats.none) {
            throw NotStarted(FIGURES_FAILED)
        }

        val twice = if (PictureQuality.measuresTwice(fps1x = frames.fps, density = density)) {
            // the same stage, at 2x: a new render target and new shaders, so a warm-up of its own
            stage.picture.choose(2)
            measurePicture(stage = stage, warmMs = TWICE_WARM_MS, measureMs = TWICE_MS, from = 2.0 / 3, to = 1.0).fps
        } else {
            null
        }

        mount(null)
        pictureTenths = 10

        tested = Tested(
            voices = voices,
            frames = frames,
            score = MachineScore.of(safeVoices = voices, fps = frames.fps),
            oversample = PictureQuality.oversampleFor(fps1x = frames.fps, fps2x = twice, density = density),
        )
        phase = Phase.Result
    }

    /**
     * The picture test's stage: the stage the show draws, with the band standing at their places (the show frozen at
     * its start, so nothing moves the measure: no music, no flashing stroboscope, the full picture the show starts
     * with), at 1x whatever the address says. It is not seen: the checking screen covers it (an opaque layer over the
     * full-size canvas, which stays in the layout at its real size and is really drawn; a canvas hidden or made small
     * could be skipped by the browser and would measure nothing true).
     */
    private fun testStage(): Running {
        // the address's switches, frozen at the start (`t=0`: the band at their places), at 1x, and without the
        // stroboscope (nobody has chosen yet)
        val switches = showSwitches(query = props.query + "&${Switch.Oversample.key}=1", strobe = false, frozenAt = 0.0)
        val stage = Running(
            show = Show(seed = showSeed(switches = switches, random = Random.Default), switches = switches),
            key = "stage-${++stages}",
            test = true,
        )

        mount(stage)

        return stage
    }

    /**
     * A part of the picture test, on the stage that is mounted: once it draws (the band stands, so the figures'
     * download is not measured; the screen shows their loading before), [warmMs] are drawn unmeasured (shaders
     * compile, the first frames are slow), then the frames of [measureMs] are counted. Where the band never stands (a
     * figure failed, which ends the wait at once, or nothing came within the time), nothing was drawn. The progress
     * shown goes [from] to [to] (shares of the whole test).
     */
    private suspend fun measurePicture(stage: Running, warmMs: Double, measureMs: Double, from: Double, to: Double): FrameStats {
        val frames = mutableListOf<Double>()
        val measured = CompletableDeferred<Unit>()
        var firstAt: Double? = null

        frameSink = { ms ->
            val now = window.performance.now()
            val first = firstAt ?: now.also { firstAt = it }

            if (now - first >= warmMs) {
                frames.add(ms)
            }

            if (now - first >= warmMs + measureMs) {
                measured.complete(Unit)
            }

            // told in tenths, so the screen is drawn anew a few times, not with every frame
            val tenths = (10 * (from + (to - from) * minOf(1.0, (now - first) / (warmMs + measureMs)))).toInt()

            if (tenths != pictureTenths) {
                pictureTenths = tenths
            }
        }

        val stood = CompletableDeferred<Unit>()

        // the band stands at the first frame; a figure that failed ends the wait at once
        val failed = stage.picture.figures.subscribeToStream { figures ->
            if (figures.failed) {
                stood.complete(Unit)
                measured.complete(Unit)
            }
        }
        val firstFrame = frameSink

        frameSink = { ms ->
            stood.complete(Unit)
            firstFrame?.invoke(ms)
        }

        // up to STAND_MS for the band to stand (late figures are waited for that long); the warm-up and the measure
        // then run without that cap, as long as frames come
        if (withTimeoutOrNull(STAND_MS) { stood.await() } != null) {
            measured.await()
        }

        failed()
        frameSink = null

        return FrameStats.of(frames)
    }

    /** Makes the song and the show, and mounts it; the walk-in starts once the band stands. */
    private suspend fun begin() {
        val player = ensurePlayer()
        val song = BuiltInSongs.derSchmetterling
        val pattern = compile(code = song.code)
        // made now and started after the walk-in: it answers its cycles before it sounds, so the band reads the very
        // notes that will sound (KLANG-00450). Its tempo is set first: the cycles are read at the tempo it starts at.
        val playback = player.play(pattern)

        playback.updateRpm(song.rpm)

        val reading = SongReading.begin(playback)

        val transport = ShowPlayback(
            begin = {
                playback.also { it.start(KlangCyclicPlayback.Options(rpm = song.rpm)) }
            },
        )

        val music = PlayingMusic(
            song = reading.song,
            analyser = { player.getAnalyzer() },
            listen = {
                // the clock is made: the band stands, the show can be seen and walks in (the click was taken here,
                // so the walk-in begins by itself: LiveAct's walkInAtOnce)
                phase = Phase.Playing
                transport
            },
        )

        val switches = showSwitches(query = props.query, strobe = strobe, oversample = tested?.oversample ?: 1)

        preparing = null

        mount(
            Running(
                show = Show(seed = showSeed(switches = switches, random = Random.Default), music = music, switches = switches),
                key = "stage-${++stages}",
                test = false,
            )
        )

        beside = scope.launch {
            launch(start = CoroutineStart.UNDISPATCHED) { reading.follow() }

            // the figures load while the start screen still says that the show gets ready: where the band never stands,
            // the start screen says so instead of waiting for ever
            delay(STAND_MS)

            if (phase == Phase.Readying) {
                stop(problem = FIGURES_FAILED)
            }
        }
    }

    /**
     * The player, made at the first start (after the click: the browser allows sound then) and kept. Its making is
     * waited for at most [PLAYER_MS]. A making that takes longer is given up (it may hang for good: a worklet that
     * did not load leaves the engine waiting) and the next click begins a new one; where the one given up arrives
     * after all, it is shut down at once, so one player is in use, ever. A making that failed is begun anew at the
     * next click too.
     */
    private suspend fun ensurePlayer(): KlangPlayer {
        player?.let { return it }

        val making = playerMaking ?: scope.async { makePlayer() }.also { playerMaking = it }

        val made = try {
            withTimeout(PLAYER_MS) { making.await() }
        } catch (e: TimeoutCancellationException) {
            playerMaking = null
            giveUp(making)

            throw NotStarted("The sound could not be started in this browser (it did not answer in time). Please reload the page.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            playerMaking = null

            throw NotStarted("The sound could not be started in this browser.", e)
        } finally {
            preparing = null
        }

        player = made

        return made
    }

    /** A making given up: cancelled, and where it arrives all the same, its player is shut down. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun giveUp(making: Deferred<KlangPlayer>) {
        making.invokeOnCompletion { cause ->
            if (cause == null) {
                making.getCompleted().shutdown()
            }
        }

        making.cancel()
    }

    /** Makes the player: the sample sets' lists, then the engine. */
    private suspend fun makePlayer(): KlangPlayer {
        preparing = "Preparing the sound: the lists of sounds"

        val sampleRate = resolveBestSampleRate(48000)
        val samples = Samples.create(sampleRate = sampleRate, catalogue = SampleCatalogue.default)

        preparing = "Preparing the sound: starting the engine"

        return klangPlayer(KlangPlayer.Options(samples = samples, sampleRate = sampleRate))
    }

    /** [code] as a pattern to play; what the build swallows is said in the console. */
    private fun compile(code: String): SprudelPattern {
        val swallowed = mutableListOf<SprudelDiagnostic>()
        val pattern = try {
            SprudelDiagnostics.collectingInto(swallowed) { SprudelPattern.compile(createScriptEngine(), code) }
        } catch (e: Throwable) {
            throw NotStarted("The song could not be compiled: ${e.message ?: e::class.simpleName}", e)
        }

        swallowed.forEach { console.warn("Der Schmetterling: ${it.context}", it.error) }

        return pattern ?: throw NotStarted("The song could not be compiled: it gives no pattern.")
    }

    override fun VDom.render() {
        div {
            css {
                put("position", "fixed")
                put("inset", "0")
                put("background", "#020308")
            }

            running?.let { now ->
                div {
                    // another stage is another component: the band is made anew
                    key = now.key
                    css {
                        put("position", "absolute")
                        put("inset", "0")
                    }

                    if (now.test) {
                        LiveAct(show = now.show, models = MODELS, version = release, frameTimes = onFrame, picture = now.picture)
                    } else {
                        LiveAct(
                            show = now.show,
                            models = MODELS,
                            version = release,
                            walkInAtOnce = true,
                            stepsDown = true,
                            picture = now.picture,
                        )
                    }
                }
            }

            when (phase) {
                Phase.Start -> startScreen()
                Phase.Readying -> overScreen { readying() }
                Phase.TestingSound -> overScreen { soundTest(soundProgress) }
                Phase.TestingPicture -> overScreen {
                    // the figures load first (their download is not measured), then the picture is measured
                    val loading = figures

                    if (loading != null && !loading.done) {
                        figuresLoading(loading)
                    } else {
                        pictureTest(tenths = pictureTenths)
                    }
                }
                Phase.Result -> overScreen {
                    tested?.let {
                        testResult(
                            voices = it.voices,
                            frames = it.frames,
                            score = it.score,
                            quality = quality(it.oversample),
                            options = { options() },
                            onStart = { go(test = false) },
                        )
                    }
                }
                Phase.Playing -> running?.let { now ->
                    ShowDrawer(picture = now.picture, release = release, onVolume = { gain -> player?.setOutputVolume(gain) })
                }
            }
        }
    }

    /** The picture's quality the show starts with, in one short line. */
    private fun quality(oversample: Int): String = when {
        setsOversample(props.query) -> "Picture quality: as the address sets it."
        oversample > 1 -> "Picture quality: high (${oversample}x oversampling)."
        else -> "Picture quality: standard."
    }

    /** The choices for the show, right above the button that starts it. */
    private fun FlowContent.options() {
        showOptions(
            strobe = strobe,
            fullscreen = fullscreen,
            onStrobe = { strobe = it },
            onFullscreen = { fullscreen = it },
        )
    }

    /** A screen before the show (start, checks, result): the band's name at the top, [content] centred below. */
    private fun FlowContent.overScreen(content: FlowContent.() -> Unit) {
        notstromScreen(content)
    }

    /** After a click, before the tests or the show: the sound's preparation, then the band's figures as they load. */
    private fun FlowContent.readying() {
        val loading = figures

        if (loading != null) {
            figuresLoading(loading)
        } else {
            leadLine("Getting ready")
            bodyText { +(preparing ?: "Preparing the show") }
        }
    }

    private fun FlowContent.startScreen() {
        notstromScreen {
            val asked = warning

            if (asked != null) {
                browserWarning(asked = asked, onContinue = { continueAnyway() }, onNotNow = { notNow() })

                return@notstromScreen
            }

            leadLine("$DEMO - ${BuiltInSongs.derSchmetterling.title}")
            bodyText { +"The show has sound, and fast flashing lights (a stroboscope)." }

            // without the tests, the choices for the show come right before its start
            val choosing = skipsTests(props.query)

            if (choosing) {
                options()
            }

            actions(gap = if (choosing) "28px" else "44px") {
                actionButton(text = "Start") { start() }
            }

            problem?.let { text ->
                bodyText(color = "#f0a8a0") { +text }
            }
        }
    }

    /** What the start screen says where the page is not secure, opened at [address]: the sound cannot start there. */
    private fun notSecure(address: String): String =
        "The sound needs a secure address, and this page was opened at $address. " +
            "Please open it with https, or as localhost on this machine."

    /** A start that failed, with what to say on the start screen. */
    private class NotStarted(message: String, cause: Throwable? = null) : Exception(message, cause)

    private companion object {
        /** Where the figures lie, beside the page (klangviz's resources). */
        const val MODELS = "liveact/notstrom/"

        /**
         * What the song is to notstrom, before its title on the start screen: the line reads exactly as the maintainer
         * wrote it, `First Demo - Der Schmetterling` (a plain hyphen between spaces).
         */
        const val DEMO = "First Demo"

        /** What the start screen says where the band's figures did not load. */
        const val FIGURES_FAILED = "The band could not get onto the stage (its figures did not load). Please reload the page."

        /** How long the player may take to start (the samples' index, the audio context, the worklet), in milliseconds. */
        const val PLAYER_MS = 30_000L

        /** How long the band's figures may take to load before the start screen gives up, in milliseconds. */
        const val STAND_MS = 60_000L

        /** How long a click on Start waits for the browser's detection, in milliseconds. */
        const val DETECT_MS = 10_000L

        /** The dynamometer's rounds, averaged (as the studio runs it). */
        const val SOUND_ROUNDS = 3

        /** How long the picture test draws at 1x before it measures (shaders compile, the first frames are slow), in ms. */
        const val PICTURE_WARM_MS = 1_500.0

        /** How long the picture test measures at 1x, in milliseconds. */
        const val PICTURE_MS = 4_000.0

        /** How long the picture test draws at 2x before it measures (a new render target, new shaders), in ms. */
        const val TWICE_WARM_MS = 1_000.0

        /** How long the picture test measures at 2x, in milliseconds. */
        const val TWICE_MS = 2_000.0
    }
}
