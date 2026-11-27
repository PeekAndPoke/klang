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
import io.peekandpoke.klang.viz.liveact.band.skills.Look
import io.peekandpoke.klang.viz.liveact.headReach
import io.peekandpoke.klang.viz.liveact.overShoulder
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
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

/**
 * Sirene's stroll in Der Schmetterling's stage plot, at the pace [slow], with its route's clock begun at the stroll
 * [time]; [others]: whom she may visit (everyone else unless given).
 */
private fun stroller(
    slow: Double,
    time: StrollTime,
    others: Set<Player> = Player.entries.toSet() - Player.Sirene,
): Stroller {
    val place = DER_SCHMETTERLING.plot.places.getValue(Player.Sirene)
    val home = vec(place.x, place.y, place.z)

    return Stroller(
        seed = 7,
        home = home,
        homeTurn = place.turn,
        s = 1.0,
        closest = Stroller.closestToCamera(home = home, chin = 1.45, s = 1.0, height = 1.7),
        others = DER_SCHMETTERLING.plot.places.filterKeys { it in others },
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

    "the stroll's looks: the stare at the camera as a still look, the look at one visited at their eyes, over the shoulder" {
        withThree { three ->
            Three.addon = three

            // the drummer on his riser and Rune on the floor, the only ones to visit
            val slow = stroller(slow = 1.5, time = StrollTime(from = 30.0, to = 2430.0), others = setOf(Player.Wucht, Player.Rune))
            val camera = vec(1.0, 2.0, 9.0)
            val places = DER_SCHMETTERLING.plot.places
            var stares = 0
            val visited = mutableSetOf<Player>()
            var t = 0.0

            while (t < 2450.0) {
                val share = slow.stareAt(t)
                val stare = slow.stareLook(t = t, camera = camera)
                val visit = slow.visitAt(t)
                val look = slow.visitLook(t)

                withClue("at $t") {
                    if (share > 0) {
                        stares++
                        listOf(stare?.share, stare?.at === camera, stare?.still, stare?.behind) shouldBe listOf(share, true, true, false)
                    } else {
                        stare shouldBe null
                    }

                    if (visit != null && visit.share > 0) {
                        val place = places.getValue(visit.who)
                        // so high over their place: 1.25 m over the riser, as the drummer sits at the kit, else 1.6 m
                        val eyes = if (visit.who == Player.Wucht) place.y + 1.25 else 1.6

                        visited.add(visit.who)
                        listOf(look?.share, look?.at?.x, look?.at?.y, look?.at?.z, look?.behind, look?.still) shouldBe
                            listOf(visit.share, place.x, eyes, place.z, true, false)
                    } else {
                        look shouldBe null
                    }
                }

                t += 0.25
            }

            stares shouldBeGreaterThan 10
            visited shouldBe setOf(Player.Wucht, Player.Rune)
        }
    }

    "on a stroll a look the figure turns away from is let go no faster than over 1.6 s, where the head alone snaps" {
        withThree { three ->
            Three.addon = three

            val slow = stroller(slow = 1.5, time = StrollTime(from = 30.0, to = 630.0))
            // a look at where the total stands, as a stare at the camera is
            val look = Look(share = 1.0, at = vec(0.0, 1.85, 9.4))
            val dt = 1.0 / 30
            // the steepest a look falls over 1.6 s (the soft S's slope is 1.5 at the most), over a frame
            val most = 1.5 / 1.6 * dt
            var before = Double.NaN
            var plainBefore = Double.NaN
            var snaps = 0
            var t = 30.0

            while (t < 640.0) {
                val walk = slow.walkAt(t)
                val off = atan2(look.at.x - walk.at.x, look.at.z - walk.at.z) - walk.turn
                val yaw = atan2(sin(off), cos(off))
                val held = slow.reach(look = look, yaw = yaw, t = t)
                val plain = headReach(angle = yaw, behind = false)

                // (where it nears straight behind, the look over the shoulder ends it: not a fall of the look's own)
                if (before == before && overShoulder(yaw) >= 1.0) {
                    withClue("at $t") {
                        (held >= before - most - 1e-9) shouldBe true
                    }
                }

                if (plainBefore == plainBefore && plain < plainBefore - most) {
                    snaps++
                }

                before = held
                plainBefore = plain
                t += dt
            }

            // (where the head alone never let go faster, the check would say nothing)
            snaps shouldBeGreaterThan 0
        }
    }
})
