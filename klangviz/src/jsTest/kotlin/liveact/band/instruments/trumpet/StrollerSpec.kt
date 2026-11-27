/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.trumpet

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Three
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.scores.DER_SCHMETTERLING
import io.peekandpoke.klang.viz.liveact.vec
import io.peekandpoke.kraft.addons.registry.AddonRegistry.Companion.addons
import io.peekandpoke.kraft.addons.registry.addons
import io.peekandpoke.kraft.addons.threejs.ThreeJsAddon
import io.peekandpoke.kraft.addons.threejs.threeJs
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.testing.TestBed
import io.peekandpoke.kraft.testing.eventually
import io.peekandpoke.kraft.vdom.VDom
import kotlinx.html.Tag
import kotlinx.html.div
import kotlin.time.Duration.Companion.seconds

private var loaded: ThreeJsAddon? = null

@Suppress("TestFunctionName")
private fun Tag.StrollerThreeCatcher() = comp { StrollerThreeCatcher(it) }

private class StrollerThreeCatcher(ctx: NoProps) : PureComponent(ctx) {
    private val three by subscribingTo(addons.threeJs)

    override fun VDom.render() {
        loaded = three
        div { }
    }
}

private suspend fun withThree(block: suspend (ThreeJsAddon) -> Unit) {
    TestBed.preact(appSetup = { addons { threeJs() } }, view = { StrollerThreeCatcher() }) {
        block(eventually(timeout = 5.seconds) { loaded.shouldNotBeNull() })
    }
}

/** Sirene's stroll in Der Schmetterling's stage plot, at the pace [slow], with its route's clock begun at the stroll [time]. */
private fun stroller(slow: Double, time: StrollTime): Stroller {
    val place = DER_SCHMETTERLING.plot.places.getValue(Player.Sirene)
    val home = vec(place.x, place.y, place.z)

    return Stroller(
        seed = 7,
        home = home,
        homeTurn = place.turn,
        s = 1.0,
        closest = Stroller.closestToCamera(home = home, chin = 1.45, s = 1.0, height = 1.7),
        others = DER_SCHMETTERLING.plot.places.filterKeys { it != Player.Sirene },
        slow = slow,
    ).apply {
        plan(times = listOf(time), now = Double.NEGATIVE_INFINITY)
    }
}

/**
 * The strolls' time scale ([Stroller]'s slow): a check for the future, for whoever walks a stroll at another pace.
 * The stroller at a slower pace is the one at the trumpet player's pace with its clock stretched.
 */
class StrollerSpec : StringSpec({

    "at a pace of two thirds, the stroll at t is the one at the trumpet's pace at t / 1.5, a third slower" {
        withThree { three ->
            Three.addon = three

            // the same stroll, from 30 s to 1230 s on the show's clock: on the slow one's route clock 20 s to 820 s
            val slow = stroller(slow = 1.5, time = StrollTime(from = 30.0, to = 1230.0))
            val plain = stroller(slow = 1.0, time = StrollTime(from = 30.0 / 1.5, to = 1230.0 / 1.5))
            var moved = 0
            var stares = 0
            var visits = 0
            var t = 0.0

            while (t < 1250.0) {
                val a = slow.walkAt(t)
                val b = plain.walkAt(t / 1.5)
                val visit = slow.visitAt(t)
                val seen = plain.visitAt(t / 1.5)

                withClue("at $t") {
                    listOf(a.at.x, a.at.z, a.turn, a.going, a.hips) shouldBe listOf(b.at.x, b.at.z, b.turn, b.going, b.hips)
                    a.speed shouldBe b.speed / 1.5
                    slow.stareAt(t) shouldBe plain.stareAt(t / 1.5)
                    listOf(visit?.who, visit?.share, visit?.turn) shouldBe listOf(seen?.who, seen?.share, seen?.turn)
                    // when the stroll began, on the show's clock: until she has turned to the front after it (three
                    // seconds of the route's clock)
                    slow.strollFrom(t) shouldBe (if (t > 30.0 && t < 1230.0 + 3.0 * 1.5) 30.0 else null)
                }

                if (a.going > 0.5) {
                    moved++
                }

                if (slow.stareAt(t) > 0) {
                    stares++
                }

                if (visit != null) {
                    visits++
                }

                t += 0.25
            }

            // (a comparison of places that never move, of stares and visits that never happen, would say nothing)
            moved shouldBeGreaterThan 100
            stares shouldBeGreaterThan 10
            visits shouldBeGreaterThan 10
        }
    }
})
