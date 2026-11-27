/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.peekandpoke.klang.apps.common.BrowserCheck
import io.peekandpoke.klang.apps.common.FrameClock
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
import io.peekandpoke.klang.viz.liveact.PicturePreset
import io.peekandpoke.klang.viz.liveact.Score
import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.SongChanges
import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches
import io.peekandpoke.klang.viz.liveact.band.Changeover
import io.peekandpoke.kraft.addons.browserdetect.BrowserDetectAddon
import io.peekandpoke.kraft.addons.browserdetect.browserDetect
import io.peekandpoke.kraft.addons.registry.AddonRegistry.Companion.addons
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.ops.persistInLocalStorage
import kotlin.js.Date
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.browser.document
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
import kotlinx.serialization.builtins.nullable
import org.w3c.dom.events.Event

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
 * studio's PS) and the stage drawn for a few seconds ([FrameStats]), as the high picture at 1x (at the pixels the
 * show may settle at, [testStage]) and then, where it may matter, at 2x or, where 1x was slow, as the medium
 * picture; it chooses the picture's detail ([presetFor],
 * [PicturePreset], KLANG-00492) and its oversampling apart. The result is shown as a score ([MachineScore]) with the
 * choices for the show (the stroboscope,
 * fullscreen) right above the button that
 * starts it; `?notest` skips the tests ([skipsTests]) and shows the choices on the start screen. Nothing of the
 * result is sent. It is kept in this browser ([StoredTest]): where a kept result fits this machine as it is now, the
 * click on Start shows it at once, with when it was measured and a button that runs the tests again; the player is
 * then made, and the band's figures load (their bar on the screen that says the show gets ready), with the click
 * that starts the show, as with `?notest`.
 *
 * For the show the song is compiled once, its playback is made and read ahead for the band ([SongReading]: the band
 * plays the notes that will sound), and it is played for the show alone ([ShowPlayback]): the walk-in begins at once
 * (the click was taken here), and the live act's clock starts the song when the walk-in is over. The show's opening
 * fades ([OpeningFade]): the screen of the click goes to black, the show gets ready on the black, and when the band
 * stands the black goes, the stage pitch black by itself: its light comes up slowly as the band walks in, in the
 * night (`opening=night`, [showSwitches]) that turns to the look's own world when the song begins; the tests' phases
 * have no fade. The live act steps
 * the oversampling down by itself where the frames stay slow. While the show plays, a moving mouse brings up a
 * drawer at the bottom ([ShowDrawer]: the oversampling, the fog, the shadows, the reflections, the way back to the
 * start screen, fullscreen, the volume). What fails on the way is said on the start screen, in plain words.
 *
 * Beside Start, a quiet button "Meet the band" leads to a page of its own ([MeetTheBandPage]): the six members round a
 * hexagon of fence panels, one at a time at the front in the light of a lamp on the floor, their true self in the
 * shadow behind, their name, what they play and how wild they are laid over it. It has no sound, runs no test and is
 * not fullscreen; it draws with the oversampling of the test this machine has (just measured, or kept and fitting),
 * else at 1x ([meetSamples]). Its "Back" (or Escape) unmounts it, everything it made freed, and the start screen is
 * there again; where its figures do not load, the start screen says so ([MEET_FAILED]).
 *
 * The address's switches are the live act's ([Switch]: `?seed=7`, `?still`, `?oversample=2`, `?fog=low` and the rest);
 * `?nostrobe` presets the choice for the stroboscope. The line of text at the top is hidden for visitors; `?dev` shows
 * it, and the look's controls in the drawer ([showSwitches], [showsDev]).
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

        /** The page "Meet the band" ([MeetTheBandPage]): the band one by one, no sound. */
        Meet,
    }

    /**
     * A mounted stage: the show or the picture test ([test]); [key] is another one for every stage; [score]: what its
     * band plays by (the show's: its song's); [picture] sees and
     * sets its oversampling and its fog (the test's second part, the drawer) and sees its frame rate (the drawer).
     */
    private class Running(
        val show: Show,
        val key: String,
        val test: Boolean,
        val score: Score = SHOW_SCORE,
        val picture: Picture = Picture(),
        val changes: SongChanges = SongChanges(),
    )

    /**
     * A step of the show's opening that fades ([OpeningFade]): [step], after a click on the screen of [from] (the start
     * screen, or the tests' result); [lastMs]: the page's clock at its last animation frame.
     */
    private class Fading(val step: OpeningFade.Step, val from: Phase, var lastMs: Double)

    /**
     * What the tests found, and the picture's detail ([preset]) and oversampling the picture test chose; [keptAt]: when it was measured,
     * where it is a kept result ([StoredTest]), null for one just measured; [notKept]: one just measured that is not
     * kept for the next visit, as the address changes the picture ([keepsTest]).
     */
    private class Tested(
        val voices: Int,
        val frames: FrameStats,
        val score: MachineScore,
        val preset: PicturePreset,
        val oversample: Int,
        val keptAt: Double? = null,
        val notKept: Boolean = false,
    )

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

    /** How far the picture test is, in tenths: 0 to 10, over its two parts (the first, then the 2x or the medium part). */
    private var pictureTenths: Int by value(0)

    /** Whether the picture test waits for the window to be there again (it pauses while away, [FrameClock]). */
    private var picturePaused: Boolean by value(false)

    /** The tests' result, once there is one. */
    private var tested: Tested? by value(null)

    /**
     * The last result this browser keeps ([StoredTest]); null where none is kept. Where the browser keeps nothing
     * (a private window, storage that is full or refused), it holds the result for this page only, and the next
     * visit tests again.
     */
    private val kept: StreamSource<StoredTest?> =
        StreamSource<StoredTest?>(null).persistInLocalStorage(
            key = StoredTest.STORAGE_KEY,
            serializer = StoredTest.serializer().nullable,
        )

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

    /** Which song of the [PROGRAMME] the show plays, or will play when it starts. */
    private var playing: Int by value(0)

    /** A change of song is under way ([goOnWith]): until the stage has taken it, no other one begins. */
    private var changing = false

    /** The show's volume, 0 to 1, as the drawer set it: kept from one song to the next. */
    private var volume: Double = 1.0

    /** The step of the show's opening that fades now; null outside the opening (the tests' phases have none). */
    private var fading: Fading? by value(null)

    /** The seconds since the [fading] step began, as its animation frames counted them ([OpeningFade.advance]). */
    private var fadeSince: Double by value(0.0)

    /** The animation frame that takes the fade on, while one is asked for. */
    private var fadeFrame: Int? = null

    init {
        lifecycle {
            onUnmount {
                scope.cancel()
                stopFading()
            }
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
            val addon = withTimeoutOrNull(DETECT_MS.milliseconds) { detected.await() }

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

    /**
     * On after Start (or "Continue anyway"): the tests; or the result this browser keeps, where it fits this machine
     * ([StoredTest.fits]); or with `?notest` the show at once.
     */
    private fun goOn() {
        if (skipsTests(props.query)) {
            go(test = false)

            return
        }

        val screen = window.screen
        val fitting = kept()?.takeIf { it.fits(ratio = window.devicePixelRatio, width = screen.width, height = screen.height) }
        val preset = fitting?.preset

        if (fitting == null || preset == null) {
            go(test = true)

            return
        }

        tested = Tested(
            voices = fitting.voices,
            frames = fitting.frames,
            score = MachineScore.of(safeVoices = fitting.voices, fps = fitting.fps),
            preset = preset,
            oversample = fitting.oversample,
            keptAt = fitting.at,
        )
        problem = null
        phase = Phase.Result
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

        // the screen of the click fades out to black on the way to the show; the tests have no fade
        if (test) {
            stopFading()
        } else {
            fade(step = OpeningFade.Step.Click, from = phase)
        }

        ready(test = test)
    }

    /**
     * The drawer's "previous song" and "next song" while the show plays: the song [by] places on in the [PROGRAMME]
     * ([stepSong]). The show does not end: it goes on with the other song ([goOnWith]), the stage, the window and
     * the volume as they are. Nothing while a change is under way.
     */
    private fun changeSong(by: Int) {
        val stage = running

        if (phase != Phase.Playing || changing || stage == null) {
            return
        }

        val from = playing
        val to = stepSong(now = playing, by = by, songs = PROGRAMME.size)

        changing = true
        playing = to

        scope.launch {
            try {
                // (where the stage is there and does not take it, as the change before is still under way, the song
                // stays; a show that ended meanwhile keeps the song chosen)
                if (!goOnWith(stage = stage, programmed = PROGRAMME[to]) && running === stage) {
                    playing = from
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                console.error("Notstrom could not change the song", e)
                stop(problem = "Something went wrong while the band changed the song. Please reload the page and try again.")
            } finally {
                changing = false
            }
        }
    }

    /**
     * The show on [stage] goes on with [programmed], without ending (KLANG-00481): the song is compiled and read
     * while the one before still sounds; then the stage takes it ([SongChanges.change]): the sound before stops,
     * the band goes to its new places over the changeover's time, and the song begins at the moment chosen here,
     * from which its readings are told. False where the show did not take it.
     */
    private suspend fun goOnWith(stage: Running, programmed: Programmed): Boolean {
        val player = ensurePlayer()
        val song = programmed.song
        val now = stage.changes.now() ?: return false
        // the song begins once it is ready (GETS_READY at most, else the band's ways are cut short by the rest) and the
        // band has had the changeover's time
        val leaves = now + GETS_READY
        val at = leaves + Changeover.INTRO
        val pattern = compile(code = song.code)
        val playback = player.play(pattern)

        playback.updateRpm(song.rpm)

        val reading = SongReading.begin(playback, startsAt = at)
        val transport = ShowPlayback(
            begin = {
                playback.also { it.start(KlangCyclicPlayback.Options(rpm = song.rpm)) }
            },
        )
        val music = PlayingMusic(song = reading.song, analyser = { player.getAnalyzer() }, listen = { transport })

        while ((stage.changes.now() ?: leaves) < leaves) {
            delay(CHANGE_WAIT_MS.milliseconds)
        }

        if (running !== stage || !stage.changes.change(score = programmed.score, music = music, at = at)) {
            // the show ended meanwhile, or the stage does not take the change: the song made for it is never heard
            reading.close()
            playback.stop()

            return false
        }

        // the reading of the song before ends, the next one's is followed
        beside?.cancel()
        beside = scope.launch(start = CoroutineStart.UNDISPATCHED) { reading.follow() }

        return true
    }

    /** What is there goes, and the machine is [test]ed or the show gets ready and begins. */
    private fun ready(test: Boolean) {
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
        stopFading()
        mount(null)
        preparing = null
        this.problem = problem
        phase = Phase.Start
    }

    /**
     * The drawer's way back while the show plays: the show ends (its stage goes, and with it its song stops:
     * the live act's clock lets go of the music), the page leaves fullscreen, and the start screen is there again,
     * from where the show can be started anew. The volume is full again, as the next show's drawer shows it.
     */
    private fun backToStart() {
        if (phase != Phase.Playing) {
            return
        }

        leaveFullscreen()
        stop(problem = null)
        volume = 1.0
        player?.setOutputVolume(1.0)
    }

    /**
     * The quiet button beside Start: the page "Meet the band" ([MeetTheBandPage]). No sound, so no player and no
     * check of the browser; the start screen's problem, if any, is gone.
     */
    private fun meet() {
        if (phase != Phase.Start || checking) {
            return
        }

        problem = null
        warning = null
        phase = Phase.Meet
    }

    /** From the page "Meet the band" back to the start screen, saying [problem] where there is one. */
    private fun leaveMeet(problem: String?) {
        if (phase != Phase.Meet) {
            return
        }

        this.problem = problem
        phase = Phase.Start
    }

    /**
     * The samples the page "Meet the band" draws with: the oversampling of the test this machine has, just measured
     * ([tested]) or kept ([StoredTest]) where it fits the machine as it is now (as [goOn] takes it); else 1.
     */
    private fun meetSamples(): Double {
        val screen = window.screen
        val fitting = kept()?.takeIf { it.fits(ratio = window.devicePixelRatio, width = screen.width, height = screen.height) }

        return (tested?.oversample ?: fitting?.oversample ?: 1).toDouble()
    }

    /**
     * Begins [step] of the show's opening's fade now, after a click on the screen of [from], and takes it on with each
     * animation frame until its layers settle ([OpeningFade.settles]).
     */
    private fun fade(step: OpeningFade.Step, from: Phase) {
        fading = Fading(step = step, from = from, lastMs = window.performance.now())
        fadeSince = 0.0

        if (fadeFrame == null) {
            fadeFrame = window.requestAnimationFrame { fadeOn() }
        }
    }

    /**
     * One animation frame of the fade: the frame is counted ([OpeningFade.advance]), and the next one asked for until
     * its layers settle. The black stays after the click until the band stands; once the stage is up, the fade is over.
     */
    private fun fadeOn() {
        fadeFrame = null

        val now = fading ?: return
        val nowMs = window.performance.now()
        val since = OpeningFade.advance(since = fadeSince, frame = (nowMs - now.lastMs) / 1000)

        now.lastMs = nowMs
        fadeSince = since

        if (since < OpeningFade.settles(now.step)) {
            fadeFrame = window.requestAnimationFrame { fadeOn() }

            return
        }

        if (now.step == OpeningFade.Step.Standing) {
            fading = null
        }
    }

    /** No fade (the tests, a stop): nothing of it is drawn, and no frame is asked for. */
    private fun stopFading() {
        fading = null
        fadeFrame?.let { window.cancelAnimationFrame(it) }
        fadeFrame = null
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

        // the progress counts the two parts as one, the first the first two thirds; without a second part it jumps to
        // full
        val frames = measurePicture(stage = stage, warmMs = PICTURE_WARM_MS, measureMs = PICTURE_MS, from = 0.0, to = 2.0 / 3)

        // not one frame: the band never stood (its figures did not load), and there is nothing to measure
        if (frames == FrameStats.none) {
            throw NotStarted(FIGURES_FAILED)
        }

        val twice = if (PictureQuality.measuresTwice(fps1x = frames.fps, density = density)) {
            // the same stage at 2x: a new render target and new shaders, so a warm-up of its own; at the screen's own
            // pixels times two, as the show draws 2x (`soft` holds at 1x only)
            stage.picture.choose(2.0)
            measurePicture(stage = stage, warmMs = TWICE_WARM_MS, measureMs = TWICE_MS, from = 2.0 / 3, to = 1.0).fps
        } else {
            null
        }

        // where the high picture was slow at 1x (and so there was no 2x part): the same stage at the medium detail (at
        // the pixels the show may settle at, as the first part), new shadow maps (lit materials built anew where the
        // lamps that cast change) and the mirror at low, so a warm-up of its own; not where the address sets the
        // picture's detail, which wins over the test's choice
        val medium = if (measuresMedium(fpsHigh = frames.fps, query = props.query)) {
            stage.picture.choosePreset(PicturePreset.Medium)
            measurePicture(stage = stage, warmMs = MEDIUM_WARM_MS, measureMs = MEDIUM_MS, from = 2.0 / 3, to = 1.0).fps
        } else {
            null
        }

        mount(null)
        pictureTenths = 10

        val result = Tested(
            voices = voices,
            frames = frames,
            // from the first part: the high picture, at the pixels the show may settle at
            score = MachineScore.of(safeVoices = voices, fps = frames.fps),
            preset = presetFor(fpsHigh = frames.fps, fpsMedium = medium),
            oversample = PictureQuality.oversampleFor(fps1x = frames.fps, fps2x = twice, density = density),
            notKept = !keepsTest(props.query),
        )

        tested = result

        // kept for the next visit on this machine, in place of what was kept before; not where the address made the
        // picture lighter or heavier than the show's own (keepsTest)
        if (keepsTest(props.query)) {
            kept(
                StoredTest(
                    version = StoredTest.TEST_VERSION,
                    at = Date.now(),
                    ratio = density,
                    width = window.screen.width,
                    height = window.screen.height,
                    voices = voices,
                    fps = frames.fps,
                    slowestMs = frames.slowestMs,
                    quality = result.preset.word,
                    oversample = result.oversample,
                )
            )
        }

        phase = Phase.Result
    }

    /**
     * The picture test's stage: the stage the show draws, with the band standing at their places (the show frozen at
     * its start, so nothing moves the measure: no music, no flashing stroboscope, the heaviest moment of the show),
     * at 1x whatever the address says: the high picture ([PicturePreset.High]: the fog and the shadows high, the
     * reflections mid), or the address's settings where it sets them; its second part draws the same at 2x, or the
     * medium picture at 1x.
     *
     * At 1x it draws the pixels the show may settle at (`soft`: a plain screen's one for each of the page's,
     * `Sharpness.leastFor`), not the screen's own: the show starts at the screen's own and steps down where the
     * frames are slow, but a frozen show does not step, so on a retina screen the test drew four times the pixels of
     * a plain one and chose low for a machine that plays the high picture at 60 frames a second at about 1.2 (the
     * maintainer, 2026-11-27: "we should be able to detect that the macbook air can run 60fps with the settings of
     * my first screenshot"). At 2x it draws what the show draws at 2x, the screen's own pixels times the
     * oversampling: there the show does not step down its pixels. It is not seen: the
     * checking screen covers it (an opaque layer over the full-size canvas, which stays in the layout at its real size
     * and is really drawn; a canvas hidden or made small could be skipped by the browser and would measure nothing
     * true).
     */
    private fun testStage(): Running {
        val switches = testSwitches(query = props.query)
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
        // the time drawn, not the time passed: while the window is away (no focus, or the page hidden) the test pauses
        // and goes on where it stopped (FrameClock)
        val clock = FrameClock(there = windowThere())
        val away: (Event) -> Unit = {
            clock.away()
            picturePaused = true
        }
        val back: (Event) -> Unit = {
            if (windowThere()) {
                clock.back()
                picturePaused = false
            } else {
                clock.away()
                picturePaused = true
            }
        }

        picturePaused = !windowThere()
        window.addEventListener("blur", away)
        window.addEventListener("focus", back)
        document.addEventListener("visibilitychange", back)

        frameSink = { ms ->
            val counts = clock.frame(ms)
            val drawn = clock.drawnMs

            if (counts && drawn >= warmMs) {
                frames.add(ms)
            }

            if (drawn >= warmMs + measureMs) {
                measured.complete(Unit)
            }

            // told in tenths, so the screen is drawn anew a few times, not with every frame
            val tenths = (10 * (from + (to - from) * minOf(1.0, drawn / (warmMs + measureMs)))).toInt()

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

        try {
            // up to STAND_MS for the band to stand (late figures are waited for that long), counted while the window
            // is there: a page hidden meanwhile draws nothing, which is no failure of the figures; the warm-up and the
            // measure then run without that cap, as long as frames come
            var standing = withTimeoutOrNull(STAND_MS.milliseconds) { stood.await() }

            while (standing == null && !windowThere()) {
                standing = withTimeoutOrNull(STAND_MS.milliseconds) { stood.await() }
            }

            if (standing != null) {
                measured.await()
            }
        } finally {
            // also where the test is cancelled (the page goes): nothing of it stays on the window
            failed()
            frameSink = null
            window.removeEventListener("blur", away)
            window.removeEventListener("focus", back)
            document.removeEventListener("visibilitychange", back)
            picturePaused = false
        }

        return FrameStats.of(frames)
    }

    /** Whether the window is there to be drawn in: it has the focus, and the page is not hidden ([FrameClock]). */
    private fun windowThere(): Boolean = document.hasFocus() && document.asDynamic().hidden != true

    /** Makes the song and the show, and mounts it; the walk-in starts once the band stands. */
    private suspend fun begin() {
        val player = ensurePlayer()

        // the song is compiled and the stage made once the screen of the click is out: both hold the page for a
        // moment, which would stop its fade halfway
        while (fading != null && fadeSince < OpeningFade.SCREEN_OUT) {
            delay(SCREEN_WAIT_MS.milliseconds)
        }

        val programmed = PROGRAMME[playing]
        val song = programmed.song
        val score = programmed.score
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
                // so the walk-in begins by itself: LiveAct's walkInAtOnce), and comes up from the black
                phase = Phase.Playing
                fade(step = OpeningFade.Step.Standing, from = Phase.Readying)
                transport
            },
        )

        val switches = showSwitches(
            query = props.query,
            strobe = strobe,
            quality = tested?.preset ?: PicturePreset.High,
            oversample = tested?.oversample ?: 1,
            opens = true,
        )

        preparing = null

        mount(
            Running(
                show = Show(seed = showSeed(switches = switches, random = Random.Default), music = music, switches = switches),
                key = "stage-${++stages}",
                test = false,
                score = score,
            )
        )

        beside = scope.launch {
            launch(start = CoroutineStart.UNDISPATCHED) { reading.follow() }

            // the figures load while the start screen still says that the show gets ready: where the band never stands,
            // the start screen says so instead of waiting for ever
            delay(STAND_MS.milliseconds)

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
            withTimeout(PLAYER_MS.milliseconds) { making.await() }
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
        val opening = fading
        val layers = opening?.let { OpeningFade.layers(step = it.step, since = fadeSince) }

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
                        LiveAct(
                            show = now.show,
                            score = SHOW_SCORE,
                            models = MODELS,
                            version = release,
                            frameTimes = onFrame,
                            picture = now.picture,
                        )
                    } else {
                        LiveAct(
                            show = now.show,
                            score = now.score,
                            models = MODELS,
                            version = release,
                            walkInAtOnce = true,
                            stepsDown = true,
                            picture = now.picture,
                            changes = now.changes,
                        )
                    }
                }
            }

            // while the show's opening fades: the black over the stage (which keeps its size and is really drawn)
            layers?.let { blackLayer(it.black) }

            when (phase) {
                Phase.Start -> startScreen()
                Phase.Readying -> if (opening != null && layers != null) {
                    // on the way to the show: the screen of the click fades out, and on the black, where getting
                    // ready takes a while, the readying screen's words come up
                    faded(layers.screen) {
                        if (opening.from == Phase.Result) {
                            resultScreen()
                        } else {
                            startScreen()
                        }
                    }
                    faded(layers.line) {
                        notstromScreen(marked = false) { readying() }
                    }
                } else {
                    overScreen { readying() }
                }
                Phase.TestingSound -> overScreen { soundTest(soundProgress) }
                Phase.TestingPicture -> overScreen {
                    // the figures load first (their download is not measured), then the picture is measured
                    val loading = figures

                    if (loading != null && !loading.done) {
                        figuresLoading(loading)
                    } else {
                        pictureTest(tenths = pictureTenths, paused = picturePaused)
                    }
                }
                Phase.Result -> resultScreen()
                Phase.Playing -> running?.let { now ->
                    ShowDrawer(
                        picture = now.picture,
                        release = release,
                        dev = showsDev(props.query),
                        song = PROGRAMME[playing].song,
                        volume = volume,
                        onVolume = { gain ->
                            volume = gain
                            player?.setOutputVolume(gain)
                        },
                        onPrevious = { changeSong(by = -1) },
                        onNext = { changeSong(by = 1) },
                        onBack = { backToStart() },
                    )
                }
                Phase.Meet -> div {
                    css {
                        put("position", "absolute")
                        put("inset", "0")
                    }

                    MeetTheBandPage(
                        release = release,
                        models = MODELS,
                        samples = meetSamples(),
                        onBack = { leaveMeet(problem = null) },
                        onFailed = { leaveMeet(problem = MEET_FAILED) },
                    )
                }
            }
        }
    }

    /** The tests' result, the choices for the show, and the button that starts it. */
    private fun FlowContent.resultScreen() {
        overScreen {
            tested?.let {
                testResult(
                    voices = it.voices,
                    frames = it.frames,
                    score = it.score,
                    quality = qualityLine(query = props.query, preset = it.preset, oversample = it.oversample),
                    measured = it.keptAt?.let { at -> measuredLine(at) } ?: NOT_KEPT.takeIf { _ -> it.notKept },
                    options = { options() },
                    onStart = { go(test = false) },
                    // a kept result can be measured anew: both tests, as after Start
                    onTestAgain = if (it.keptAt != null) ({ go(test = true) }) else null,
                )
            }
        }
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
        notstromScreen(content = content)
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

            leadLine(DEMO)
            songList(titles = PROGRAMME.map { it.song.title })
            bodyText { +"The show has sound, and fast flashing lights (a stroboscope)." }

            // without the tests, the choices for the show come right before its start
            val choosing = skipsTests(props.query)

            if (choosing) {
                options()
            }

            actions(gap = if (choosing) "28px" else "44px") {
                actionButton(text = "Start") { start() }
                actionButton(text = MEET_THE_BAND, primary = false) { meet() }
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
         * What the show is to notstrom: the start screen's leading line, a line of its own, with the programme's
         * songs listed under it (the maintainer, 2026-11-26).
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

        /** How often the show's making looks whether the screen of the click is out yet, in milliseconds. */
        const val SCREEN_WAIT_MS = 50L

        /** At a change of song: how long the next song is given to get ready before the band sets off, in seconds. */
        const val GETS_READY = 1.5

        /** How often a change of song looks whether its moment has come, in milliseconds. */
        const val CHANGE_WAIT_MS = 30L

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

        /** How long the picture test draws the medium picture before it measures (new shadow maps), in ms. */
        const val MEDIUM_WARM_MS = 1_000.0

        /** How long the picture test measures the medium picture, in milliseconds. */
        const val MEDIUM_MS = 2_000.0
    }
}
