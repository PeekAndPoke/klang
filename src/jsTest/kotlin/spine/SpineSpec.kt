/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.peekandpoke.klang.comp.MotorHistoryGraphs
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.streams.StreamSource
import kotlinx.html.Tag
import kotlinx.html.span

/** Remembers the Spine it finds, and the one a component below it finds. */
private class SpineReader(ctx: NoProps) : PureComponent(ctx) {
    companion object {
        val found = mutableListOf<Spine>()
    }

    init {
        found.add(spine)
    }

    override fun VDom.render() {
        span {
            +"READER"

            if (found.size < 2) {
                SpineReader()
            }
        }
    }
}

@Suppress("FunctionName")
private fun Tag.SpineReader() = comp { SpineReader(it) }

/**
 * The Spine as one object (KLANG-00422): registered on the app, found by every component, and one Spine's parts are
 * its own.
 */
class SpineSpec : StringSpec({

    "a component finds the Spine the app registered, and so does a component below it" {
        val own = Spine()

        SpineReader.found.clear()

        TestBed.preact(appSetup = { spine(own) }, view = { SpineReader() }) {
            SpineReader.found.size shouldBe 2

            withClue("the component") { SpineReader.found[0] shouldBeSameInstanceAs own }
            withClue("the component below it") { SpineReader.found[1] shouldBeSameInstanceAs own }
        }
    }

    "a Spine's parts are its own" {
        val one = Spine()
        val other = Spine()

        withClue("the work") { one.work shouldNotBeSameInstanceAs other.work }
        withClue("the readings") { one.motor shouldNotBeSameInstanceAs other.motor }
        withClue("the lamp") { one.visuals.lamp shouldNotBeSameInstanceAs other.visuals.lamp }
        withClue("the recents") { one.recents shouldNotBeSameInstanceAs other.recents }
        withClue("the Console") { one.console shouldNotBeSameInstanceAs other.console }
        withClue("the player") { one.player shouldNotBeSameInstanceAs other.player }
        withClue("the playbacks") { one.playbacks shouldNotBeSameInstanceAs other.playbacks }

        withClue("one Spine gives the same part every time") {
            one.work shouldBeSameInstanceAs one.work
            one.visuals.light shouldBeSameInstanceAs one.visuals.light
        }
    }

    "the Console given to the Spine is its Console" {
        val console = ConsoleLog()

        Spine(console = console).console shouldBeSameInstanceAs console
        Spine().console shouldNotBeSameInstanceAs console
    }

    "a Spine built starts nothing: the engine not loaded, no samples asked for" {
        val own = Spine()

        own.player.status() shouldBe Player.Status.NOT_LOADED
        own.player.get() shouldBe null
        withClue("no samples asked for by building it") { own.player.samplesRequested shouldBe false }
    }

    "the graphs draw with no pause of their own in an app with a Spine, and with one given" {
        val own = Spine()
        val paused = StreamSource(false)

        TestBed.preact(appSetup = { spine(own) }, view = { MotorHistoryGraphs() }) { root ->
            withClue("no pause given") { (root.single().childElementCount > 0) shouldBe true }
        }

        TestBed.preact(appSetup = { spine(own) }, view = { MotorHistoryGraphs(paused = paused) }) { root ->
            withClue("a pause given") { (root.single().childElementCount > 0) shouldBe true }
        }
    }
})
