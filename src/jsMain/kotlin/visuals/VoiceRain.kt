/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.visuals

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.switchMapNotNull
import kotlinx.browser.window
import kotlinx.html.Tag
import kotlinx.html.canvas
import kotlinx.html.div
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import kotlin.js.Date
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

@Suppress("FunctionName")
fun Tag.VoiceRain(text: VoiceRain.Text) = comp(
    VoiceRain.Props(text = text)
) {
    VoiceRain(it)
}

/**
 * The voices played, falling down a display as columns of text, matrix style (the maintainer, 2026-10-29: "This display
 * will show the voice played falling from the top text vertically, matrix style"): each voice, as it sounds, drops its
 * [Text] from the top in a column of its own, the newest letter at the bottom bright, those above it fading, until it
 * has fallen out of the display. Whatever plays, from any page.
 *
 * Drawn on a canvas a frame at a time; at most [MAX_DROPS] fall at once, a voice past that is let go.
 */
class VoiceRain(ctx: Ctx<Props>) : Component<VoiceRain.Props>(ctx) {

    data class Props(
        val text: Text,
    )

    /** What a voice drops: the Motor button steps through them. */
    enum class Text(val title: String) {
        /** Its note, "c4", or its pitch in Hz, or its sound. */
        Note("Notes"),

        /** Its sound, "bd", "supersaw", with its index when it has one. */
        Sound("Sounds"),

        /** The code that played it: the snippet of the song its voice came from. */
        Code("Code");

        /** The text the Motor button steps on to, round. */
        val next: Text get() = entries[(ordinal + 1) % entries.size]
    }

    private companion object {
        /** How many drops fall at once at the most. */
        const val MAX_DROPS = 140

        /** The letters' height, as a share of the display's height, within a few px. */
        const val LETTER_SHARE = 1.0 / 30
        const val LETTER_MIN_PX = 12.0
        const val LETTER_MAX_PX = 22.0

        /** How fast a drop falls, in letters a second, from the slowest to the fastest. */
        const val SPEED_MIN = 5.0
        const val SPEED_MAX = 11.0

        /** The longest text a drop carries; a longer snippet is cut. */
        const val MAX_LETTERS = 18
    }

    /** A voice still to fall: when, and its texts. */
    private class Pending(val atMs: Double, val note: String, val sound: String, val code: String?)

    /** A falling drop: its column, its head's place in px, its speed in px a second, its text. */
    private class Drop(val column: Int, var headY: Double, val speed: Double, val text: String)

    private val pending = ArrayDeque<Pending>()
    private val drops = mutableListOf<Drop>()
    private val random = Random(Date.now().toInt())

    /** What plays, for the code its voices came from; when nothing plays, no voice is left to fall. */
    private val nowPlaying by subscribingTo(Player.nowPlaying) { if (it == null) pending.clear() }

    @Suppress("unused")
    private val voices by subscribingTo(
        Player.nowPlaying.map { it?.playback }.switchMapNotNull { it.signals }
    ) { signal ->
        if (signal is KlangPlaybackSignal.VoicesScheduled) {
            for (voice in signal.voices) {
                accept(voice)
            }
        }
    }

    /** The display's ink, read from the look: the canvas needs a colour, not a variable. */
    private var ink = "#7fe0ff"

    @Suppress("unused")
    private val look by subscribingTo(KlangTheme.look) { readInk() }

    private var frame: Int? = null
    private var lastMs = Date.now()

    init {
        lifecycle {
            onMount {
                readInk()
                frame = window.requestAnimationFrame { step() }
            }
            onUnmount { frame?.let { window.cancelAnimationFrame(it) } }
        }
    }

    private fun readInk() {
        val el = dom as? HTMLElement ?: return
        val value = window.getComputedStyle(el).getPropertyValue("--kl-lcd-ink").trim()

        if (value.isNotEmpty()) {
            ink = value
        }
    }

    /** A voice to fall when it sounds, its three texts worked out now: the code may change before it falls. */
    private fun accept(voice: KlangPlaybackSignal.VoicesScheduled.VoiceEvent) {
        if (pending.size > MAX_DROPS * 2) {
            return
        }

        val data = voice.data
        val sound = data.sound?.let { s -> data.soundIndex?.let { "$s:$it" } ?: s } ?: "~"
        val note = data.note ?: data.freqHz?.let { "${it.roundToInt()}Hz" } ?: sound

        pending.addLast(Pending(atMs = voice.startTime * 1000.0, note = note, sound = sound, code = snippetOf(voice)))
    }

    /** The code a voice came from: its innermost place in the song that plays, on one line, cut to [MAX_LETTERS]. */
    private fun snippetOf(voice: KlangPlaybackSignal.VoicesScheduled.VoiceEvent): String? {
        val code = nowPlaying?.code ?: return null
        val location = voice.sourceLocations?.locations?.lastOrNull { it.isValid() } ?: return null
        val line = code.lines().getOrNull(location.startLine - 1) ?: return null
        val from = (location.startColumn - 1).coerceIn(0, line.length)
        val to = if (location.endLine == location.startLine) (location.endColumn - 1).coerceIn(from, line.length) else line.length

        return line.substring(from, to).trim().take(MAX_LETTERS).takeIf { it.isNotEmpty() }
    }

    private fun step() {
        frame = window.requestAnimationFrame { step() }

        val canvas = dom?.querySelector("canvas") as? HTMLCanvasElement ?: return
        val box = canvas.parentElement as? HTMLElement ?: return
        val ratio = window.devicePixelRatio
        val width = box.clientWidth
        val height = box.clientHeight

        if (width <= 0 || height <= 0) {
            return
        }

        if (canvas.width != (width * ratio).toInt() || canvas.height != (height * ratio).toInt()) {
            canvas.width = (width * ratio).toInt()
            canvas.height = (height * ratio).toInt()
        }

        val now = Date.now()
        // NaN-guard: a first frame, or one after a long pause, moves the drops by a frame at the most
        val dt = min(0.1, max(0.0, (now - lastMs) / 1000.0))
        lastMs = now

        val letter = (height * LETTER_SHARE).coerceIn(LETTER_MIN_PX, LETTER_MAX_PX)
        val columns = max(1, (width / letter).toInt())

        // The voices whose time has come drop, each in a column of its own where one is free
        while (pending.isNotEmpty() && pending.first().atMs <= now) {
            val voice = pending.removeFirst()
            val text = when (props.text) {
                Text.Note -> voice.note
                Text.Sound -> voice.sound
                Text.Code -> voice.code ?: voice.note
            }

            if (drops.size < MAX_DROPS) {
                val column = random.nextInt(columns)
                val speed = letter * (SPEED_MIN + random.nextDouble() * (SPEED_MAX - SPEED_MIN))

                drops.add(Drop(column = column, headY = 0.0, speed = speed, text = text))
            }
        }

        val g = canvas.getContext("2d") as CanvasRenderingContext2D

        g.setTransform(ratio, 0.0, 0.0, ratio, 0.0, 0.0)
        g.clearRect(0.0, 0.0, width.toDouble(), height.toDouble())
        g.font = "${letter.roundToInt()}px monospace"
        g.asDynamic().textAlign = "center"
        g.asDynamic().textBaseline = "bottom"

        val iterator = drops.iterator()

        while (iterator.hasNext()) {
            val drop = iterator.next()

            drop.headY += drop.speed * dt

            val letters = drop.text.length
            val x = (drop.column + 0.5) * (width.toDouble() / columns)

            // The text reads downwards, its last letter the head; the letters above it fade
            for (i in 0 until letters) {
                val y = drop.headY - (letters - 1 - i) * letter

                if (y < 0 || y - letter > height) {
                    continue
                }

                val isHead = i == letters - 1

                g.globalAlpha = if (isHead) 1.0 else 0.25 + 0.6 * (i + 1).toDouble() / letters
                g.fillStyle = if (isHead) "#ffffff" else ink
                g.asDynamic().shadowColor = ink
                g.asDynamic().shadowBlur = if (isHead) letter * 0.8 else 0.0
                g.fillText(drop.text[i].toString(), x, y)
            }

            // Fallen out: its first letter past the bottom
            if (drop.headY - (letters - 1) * letter - letter > height) {
                iterator.remove()
            }
        }

        g.globalAlpha = 1.0
        g.asDynamic().shadowBlur = 0.0
    }

    override fun VDom.render() {
        div {
            key = "voice-rain"
            css {
                put("position", "relative")
                put("flex", "1")
                put("min-height", "0")
                put("width", "100%")
                put("height", "100%")
            }

            canvas {
                css {
                    put("position", "absolute")
                    put("inset", "0")
                    put("width", "100%")
                    put("height", "100%")
                }
            }
        }
    }
}
