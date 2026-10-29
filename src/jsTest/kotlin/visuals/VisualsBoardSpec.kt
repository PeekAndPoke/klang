/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.visuals

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.comp.MotorBackground
import io.peekandpoke.klang.spine.SwingingLamp
import io.peekandpoke.klang.ui.KlangKeyBindings
import io.peekandpoke.klang.workshop.keys.KeyPlatform
import io.peekandpoke.klang.workshop.keys.KeysManager
import io.peekandpoke.klang.workshop.keys.keys
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.browser.window
import kotlinx.html.div
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

/**
 * The visual's board over the plate (the maintainer, 2026-10-29): the scope's hole and the spectrum's screen on the
 * board's plate the texture draws, each in its row; the glass domes over both, reflecting the lamp.
 */
class VisualsBoardSpec : StringSpec({

    /** The board over the window, with the app's key manager its knob and its menu button answer through. */
    suspend fun board(light: Stream<SwingingLamp.Light>, test: suspend (Element) -> Unit) {
        val manager = KeysManager(platform = KeyPlatform.Pc, hintDelayMs = 10).apply { define(KlangKeyBindings) }

        try {
            TestBed.preact(appSetup = { keys(manager) }, view = {
                // Over the whole window, as the visual lays it
                div {
                    css {
                        put("position", "fixed")
                        put("inset", "0")
                    }

                    VisualsBoard(composition = VisualsCompositions.motorBoard, onLeave = {}, light = light)
                }
            }) { root ->
                test(root.single())
            }
        } finally {
            manager.dispose()
        }
    }

    /** The glass domes, in the order drawn: the hole's, then the screen's. */
    fun Element.domes(): List<HTMLElement> {
        val found = querySelectorAll(".kl-glass-dome")

        return (0 until found.length).map { found.item(it) as HTMLElement }
    }

    "the glass mirrors the lamp: each dome's highlight moved toward it and lit by it, nothing painted again" {
        val lamp = SwingingLamp.Light(
            x = 0.5 * SwingingLamp.SWAY,
            y = -0.25 * SwingingLamp.SWAY,
            z = SwingingLamp.DEFAULT_Z,
            intensity = 3.0,
            level = 3.0,
            fill = 0.0,
        )

        board(light = StreamSource(lamp)) { view ->
            eventually {
                val highlights = view.querySelectorAll(".${VisualsParts.HIGHLIGHT}")

                highlights.length shouldBe 2

                for (i in 0 until highlights.length) {
                    val style = (highlights.item(i) as HTMLElement).style

                    // The browser keeps six digits of each per cent
                    val shown = Regex("""-?[0-9.]+""").findAll(style.transform).map { it.value.toDouble() }.toList()
                    val wanted = Regex("""-?[0-9.]+""").findAll(VisualsParts.highlightTransform(swayX = 0.5, swayY = -0.25))
                        .map { it.value.toDouble() }.toList()

                    shown.size shouldBe 2
                    shown[0] shouldBe (wanted[0] plusOrMinus 1e-3)
                    shown[1] shouldBe (wanted[1] plusOrMinus 1e-3)
                    // At three times its rest the lamp lights the glass as at twice, the most
                    style.opacity shouldBe "1"
                }
            }
        }
    }

    "the highlight follows the lamp: right is right, up is up, its light from nought to twice the rest" {
        VisualsParts.highlightTransform(swayX = 0.0, swayY = 0.0) shouldBe "translate(0%, 0%)"
        VisualsParts.highlightTransform(swayX = 1.0, swayY = 1.0).let { t ->
            t.substringAfter("translate(").substringBefore("%").toDouble() shouldBeGreaterThan 0.0
            t.substringAfter(", ").substringBefore("%").toDouble() shouldBeLessThan 0.0
        }
        VisualsParts.highlightOpacity(level = 0.0) shouldBe 0.0
        VisualsParts.highlightOpacity(level = 1.0) shouldBe 0.5
        VisualsParts.highlightOpacity(level = 9.0) shouldBe 1.0
    }

    "a dome over the hole and one over the screen, both with their corners cut" {
        board(light = StreamSource(SwingingLamp.Light.dark)) { view ->
            eventually {
                val domes = view.domes()

                domes.size shouldBe 2

                for (dome in domes) {
                    (dome.parentElement as HTMLElement).style.getPropertyValue("clip-path") shouldContain "polygon("
                }
            }
        }
    }

    "the hole and the screen lie on the board's plate, each in the middle of its row, the margins kept" {
        board(light = StreamSource(SwingingLamp.Light.dark)) { view ->
            val w = window.innerWidth.toDouble()
            val h = window.innerHeight.toDouble()
            val plate = MotorBackground.tilesOnScreen(w, h, VisualsCompositions.motorBoard).getValue(VisualsCompositions.BOARD)
            val tile = MotorBackground.tileWidthPx(h)

            eventually {
                val (hole, screen) = view.domes().map { it.parentElement as HTMLElement }.map { it.getBoundingClientRect() }

                val clue = "window ${w}x$h, tile $tile, plate $plate, hole ${hole.left}..${hole.right}, " +
                    "screen ${screen.left}..${screen.right}"

                for (box in listOf(hole, screen)) withClue(clue) {
                    (box.left + box.right) / 2 shouldBe (w / 2 plusOrMinus 1.0)
                    box.width shouldBe (plate.width - 2 * VisualsBoard.SIDE_MARGIN * tile plusOrMinus 1.0)
                }

                (hole.top + hole.bottom) / 2 shouldBe (plate.top + tile / 2 plusOrMinus 1.0)
                (screen.top + screen.bottom) / 2 shouldBe (plate.top + tile * 1.5 plusOrMinus 1.0)
            }
        }
    }

    "the rain's speed knob: a quarter at the left, three times at the right, its speed and position one way and back" {
        VisualsBoard.rainSpeedOf(0.0) shouldBe (0.25 plusOrMinus 1e-9)
        VisualsBoard.rainSpeedOf(1.0) shouldBe (3.0 plusOrMinus 1e-9)

        for (speed in listOf(0.25, 0.5, 1.0, 2.0, 3.0)) {
            VisualsBoard.rainSpeedOf(VisualsBoard.rainKnobOf(speed)) shouldBe (speed plusOrMinus 1e-9)
        }

        // As made a little past the middle, so slower has as much room as faster on the log scale
        VisualsBoard.rainKnobOf(1.0) shouldBe (0.558 plusOrMinus 0.001)
    }
})
