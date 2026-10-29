/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.visuals

import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.spine.UiWork
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.Component
import io.peekandpoke.kraft.components.Ctx
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.utils.onResize
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.key
import io.peekandpoke.ultra.streams.ops.map
import io.peekandpoke.ultra.streams.ops.switchMapNotNull
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.html.Tag
import kotlinx.html.canvas
import kotlinx.html.div
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import kotlin.js.Date
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

@Suppress("FunctionName")
fun Tag.VoiceRain(speed: Double) = comp(
    VoiceRain.Props(speed = speed)
) {
    VoiceRain(it)
}

/**
 * The voices played, falling down a display as columns of text, matrix style (the maintainer, 2026-10-29: "This display
 * will show the voice played falling from the top text vertically, matrix style"): each voice, as it sounds, drops its
 * text from the top in a column of its own, the newest letter at the bottom bright, those above it fading, until it has
 * fallen out of the display. A voice's text is its tags when it has some ("When there is a tag on the event display the
 * tag"), else its note, else its sound. Whatever plays, from any page.
 *
 * [Props.speed] is how fast they fall, 1 as made, for the falling drops too (the maintainer, 2026-10-29: "another knob
 * changing the speed at which the "notes" fall"). Drawn on a canvas a frame at a time; at most [MAX_DROPS] fall at once,
 * a voice past that is let go.
 */
class VoiceRain(ctx: Ctx<Props>) : Component<VoiceRain.Props>(ctx) {

    data class Props(
        /** How fast the drops fall: 1 as made, more is faster. */
        val speed: Double,
    )

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

        /** The longest text a drop carries; a longer one is cut. */
        const val MAX_LETTERS = 18

        /** The most drop pictures kept ([sprites]). */
        const val MAX_SPRITES = 96

        /** The head's glow, and the room round a sprite for it, as a share of a letter. */
        const val GLOW_SHARE = 0.8

        /** A sprite's width, besides its glow, as a share of a letter. */
        const val SPRITE_WIDTH_SHARE = 1.2
    }

    /** A voice still to fall: when, and its text. */
    private class Pending(val atMs: Double, val text: String)

    /** A falling drop: its column, its head's place in px, its speed in px a second, its text. */
    private class Drop(val column: Int, var headY: Double, val speed: Double, val text: String)

    private val pending = ArrayDeque<Pending>()
    private val drops = mutableListOf<Drop>()
    private val random = Random(Date.now().toInt())

    /** When nothing plays, no voice is left to fall. */
    @Suppress("unused")
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

    /** The display's size in CSS px: read once, then kept by [onResize]. */
    private var boxWidth = 0
    private var boxHeight = 0

    /** Whether showing things rests ([UiWork]): [step] then draws nothing. */
    private val resting by subscribingTo(UiWork.resting)

    init {
        lifecycle {
            onResize { entries ->
                for (entry in entries) {
                    boxWidth = entry.contentRect.width.toInt()
                    boxHeight = entry.contentRect.height.toInt()
                }
            }
            onMount {
                (dom as? HTMLElement)?.let { box ->
                    boxWidth = box.clientWidth
                    boxHeight = box.clientHeight
                }
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

    /** A voice to fall when it sounds: its tags when it has some, else its note, else its sound. */
    private fun accept(voice: KlangPlaybackSignal.VoicesScheduled.VoiceEvent) {
        if (pending.size > MAX_DROPS * 2) {
            return
        }

        val data = voice.data
        val text = data.tags?.takeIf { it.isNotEmpty() }?.joinToString(" ")
            ?: data.note
            ?: data.freqHz?.let { "${it.roundToInt()}Hz" }
            ?: data.sound?.let { s -> data.soundIndex?.let { "$s:$it" } ?: s }
            ?: "~"

        pending.addLast(Pending(atMs = voice.startTime * 1000.0, text = text.take(MAX_LETTERS)))
    }

    /**
     * The drops' columns drawn once, by their text, for the [spriteLetter], [spriteInk] and [spriteRatio] they were drawn
     * at: a frame then copies a drop's picture instead of drawing each of its letters, its glow too (the maintainer,
     * 2026-10-29: "The display as is currently is pretty hard on the cpu"). At most [MAX_SPRITES], the least used let go
     * first: it never grows.
     */
    private val sprites = LinkedHashMap<String, HTMLCanvasElement>()
    private var spriteLetter = 0.0
    private var spriteInk = ""
    private var spriteRatio = 0.0

    /** Whether the canvas was left empty: with nothing falling, a frame has nothing to clear. */
    private var blank = true


    /** A drop's column as a picture: its letters downwards, the last one bright and glowing, those above it fading. */
    private fun spriteOf(text: String, letter: Double, ratio: Double): HTMLCanvasElement {
        if (letter != spriteLetter || ink != spriteInk || ratio != spriteRatio) {
            sprites.clear()
            spriteLetter = letter
            spriteInk = ink
            spriteRatio = ratio
        }

        sprites.remove(text)?.let { kept ->
            // Back at the end: the most recently used
            sprites[text] = kept
            return kept
        }

        val glow = letter * GLOW_SHARE
        val width = letter * SPRITE_WIDTH_SHARE + 2 * glow
        val height = text.length * letter + 2 * glow
        val sprite = document.createElement("canvas") as HTMLCanvasElement

        sprite.width = ceil(width * ratio).toInt()
        sprite.height = ceil(height * ratio).toInt()

        val g = sprite.getContext("2d") as CanvasRenderingContext2D

        g.setTransform(ratio, 0.0, 0.0, ratio, 0.0, 0.0)
        g.font = "${letter.roundToInt()}px monospace"
        g.asDynamic().textAlign = "center"
        g.asDynamic().textBaseline = "bottom"

        val letters = text.length

        for (i in 0 until letters) {
            val isHead = i == letters - 1

            g.globalAlpha = if (isHead) 1.0 else 0.25 + 0.6 * (i + 1).toDouble() / letters
            g.fillStyle = if (isHead) "#ffffff" else ink
            g.asDynamic().shadowColor = ink
            g.asDynamic().shadowBlur = if (isHead) glow else 0.0
            g.fillText(text[i].toString(), width / 2, glow + (i + 1) * letter)
        }

        sprites[text] = sprite

        if (sprites.size > MAX_SPRITES) {
            sprites.remove(sprites.keys.first())
        }

        return sprite
    }

    private fun step() {
        frame = window.requestAnimationFrame { step() }

        val now = Date.now()

        // Resting, nothing is drawn, the loop just goes on: checked here, every frame, so no start or stop can get out
        // of step with it (the maintainer, 2026-10-29). The voices due meanwhile are let go and the clock moves along,
        // so on return nothing falls all at once and nothing jumps; the voices still to come stay, they come seconds
        // ahead and fall on time after
        if (resting) {
            while (pending.isNotEmpty() && pending.first().atMs <= now) {
                pending.removeFirst()
            }

            lastMs = now
            return
        }

        // Nothing falling and nothing to come: the canvas is already empty
        if (drops.isEmpty() && pending.isEmpty() && blank) {
            lastMs = now
            return
        }

        val canvas = dom?.querySelector("canvas") as? HTMLCanvasElement ?: return
        val ratio = window.devicePixelRatio
        // Remembered, not read every frame: a size read after the page changed makes the browser lay it out first (the
        // maintainer's trace, 2026-10-29: 30 ms a second in `clientWidth`)
        val width = boxWidth
        val height = boxHeight

        if (width <= 0 || height <= 0) {
            return
        }

        if (canvas.width != (width * ratio).toInt() || canvas.height != (height * ratio).toInt()) {
            canvas.width = (width * ratio).toInt()
            canvas.height = (height * ratio).toInt()
        }

        // NaN-guard: a first frame, or one after a long pause, moves the drops by a frame at the most
        val dt = min(0.1, max(0.0, (now - lastMs) / 1000.0))
        lastMs = now

        val letter = (height * LETTER_SHARE).coerceIn(LETTER_MIN_PX, LETTER_MAX_PX).roundToInt().toDouble()
        val columns = max(1, (width / letter).toInt())

        // The voices whose time has come drop, each in a column of its own where one is free
        while (pending.isNotEmpty() && pending.first().atMs <= now) {
            val voice = pending.removeFirst()

            if (drops.size < MAX_DROPS) {
                val column = random.nextInt(columns)
                val speed = letter * (SPEED_MIN + random.nextDouble() * (SPEED_MAX - SPEED_MIN))

                drops.add(Drop(column = column, headY = 0.0, speed = speed, text = voice.text))
            }
        }

        val g = canvas.getContext("2d") as CanvasRenderingContext2D

        g.setTransform(ratio, 0.0, 0.0, ratio, 0.0, 0.0)
        g.clearRect(0.0, 0.0, width.toDouble(), height.toDouble())

        val glow = letter * GLOW_SHARE
        val iterator = drops.iterator()

        while (iterator.hasNext()) {
            val drop = iterator.next()

            drop.headY += drop.speed * props.speed * dt

            val sprite = spriteOf(drop.text, letter, ratio)
            val spriteWidth = sprite.width / ratio
            val spriteHeight = sprite.height / ratio
            val x = (drop.column + 0.5) * (width.toDouble() / columns)
            // The sprite's last letter sits on the head
            val top = drop.headY - drop.text.length * letter - glow

            if (top < height) {
                g.drawImage(sprite, x - spriteWidth / 2, top, spriteWidth, spriteHeight)
            }

            // Fallen out: its first letter past the bottom
            if (top + glow > height) {
                iterator.remove()
            }
        }

        blank = drops.isEmpty()
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
