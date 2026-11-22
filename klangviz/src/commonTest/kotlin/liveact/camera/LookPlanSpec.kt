/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.hash01
import io.peekandpoke.klang.viz.liveact.song.RecordedEvent
import io.peekandpoke.klang.viz.liveact.song.Recording
import io.peekandpoke.klang.viz.liveact.song.Song
import kotlin.math.floor

/** The recording's pace: a bar of some 1.64 s, a round of some 79 s. */
private const val RPM = 36.5
private const val BAR = 240.0 / (RPM * 4)

/** The cast's: the marimba and the trumpet play seldom, and the frontwoman strolls. */
private val SELDOM = listOf(Player.Rune, Player.Sirene)

private fun rounds(seed: Int = 0, force: RoundKind? = null) = Rounds(
    seed = seed,
    bar = BAR,
    looks = Player.entries.associateWith { looksOf(it) },
    seldom = SELDOM,
    strolls = Player.Sirene,
    force = force,
)

private fun look(rounds: Rounds, n: Int, i: Int, now: Double, playsAt: (Player) -> List<Double>, before: LookPlan = LookPlan()) =
    chooseLook(rounds = rounds, n = n, i = i, now = now, playsAt = playsAt, before = before)

private fun kind(rounds: Rounds, n: Int, now: Double, playsAt: (Player) -> List<Double>, before: LookPlan = LookPlan()) =
    chooseKind(rounds = rounds, n = n, now = now, playsAt = playsAt, before = before)

/** What a look shows, so two can be compared: the player (null for the stage) and the placement. */
private fun Subject.seen(): Pair<Player?, Placement> = when (this) {
    is Subject.Angle -> null to angle
    is Subject.OnPlayer -> player to look
}

/** The song as far as it is known: [players] each play a note every quarter second from [from] to [to]. */
private fun playing(vararg players: Player, from: Double, to: Double): (Player) -> List<Double> = { player ->
    if (player in players) {
        (0 until ((to - from) * 4).toInt()).map { from + it * 0.25 }
    } else {
        emptyList()
    }
}

/** The first round whose looks [looks] the seed does not give to the stage from another angle. */
private fun Rounds.withPlayers(vararg looks: Int): Int = (0 until 100).first { n -> looks.all { chance(n = n, i = it, salt = 1) >= 0.2 } }

/**
 * Six players for six minutes, each in stretches of four seconds in which they play a note every half second or
 * rest: most of them in about half of the stretches, the two who play seldom in a fifth.
 */
private fun band(): List<RecordedEvent> {
    val out = mutableListOf<RecordedEvent>()

    for (player in Player.entries) {
        val share = if (player in SELDOM) 0.2 else 0.55

        for (k in 0 until 720) {
            val t = k * 0.5

            if (hash01(floor(t / 4) * 3.7 + player.ordinal * 11.3) < share) {
                out.add(RecordedEvent(t = t, d = 0.2, tags = listOf(player.key)))
            }
        }
    }

    return out
}

class LookPlanSpec : StringSpec({

    "a look that has begun stays, whatever the song forgets" {
        val r = rounds()
        val n = r.withPlayers(0)
        val a = r.begins(n = n, i = 0)
        val begun = look(r, n = n, i = 0, now = a, playsAt = playing(Player.Wucht, Player.Beben, from = a - 10, to = a + 20))
        // the song has forgotten the notes of the look, and knows only what comes after it
        val forgotten = playing(Player.Wucht, Player.Beben, from = a + 3 * BAR + 1, to = a + 40)

        begun.what.player shouldNotBe null

        withClue("the plan after the look began") {
            begun.plan.begun.keys shouldBe setOf(n * LOOKS_A_ROUND)
        }

        withClue("the look begun, asked again after the song forgot its notes") {
            look(r, n = n, i = 0, now = a + 20, playsAt = forgotten, before = begun.plan).what.seen() shouldBe begun.what.seen()
        }

        // without what was carried over: nobody plays then, as far as the song knows, so the stage from an angle
        look(r, n = n, i = 0, now = a + 20, playsAt = forgotten).what.player shouldBe null
    }

    "a look not yet begun is not kept: it is chosen anew from what the song knows by then" {
        val r = rounds()
        val n = r.withPlayers(0)
        val a = r.begins(n = n, i = 0)
        val early = look(r, n = n, i = 0, now = a - 1, playsAt = playing(from = 0.0, to = 0.0))

        early.what.player shouldBe null

        withClue("the plan before the look begins") {
            early.plan.begun shouldBe emptyMap()
        }

        look(r, n = n, i = 0, now = a - 0.5, playsAt = playing(Player.Wucht, from = a - 10, to = a + 20), before = early.plan)
            .what.player shouldBe Player.Wucht
    }

    "a look after another shows someone else, the look before as it was kept, and asking for it keeps that one too" {
        val r = rounds()
        val n = r.withPlayers(0, 1)
        val a = r.begins(n = n, i = 0)
        val b = r.begins(n = n, i = 1)
        // the one the second look tries first, who also plays in the first look alone, and another one
        val tried = ORDER[floor(r.chance(n = n, i = 1, salt = 4) * ORDER.size).toInt()]
        val other = if (tried == Player.Wucht) Player.Beben else Player.Wucht
        val known: (Player) -> List<Double> = { player ->
            when (player) {
                tried -> playing(tried, from = a - 10, to = a + 20)(player)
                other -> playing(other, from = b, to = b + 20)(player)
                else -> emptyList()
            }
        }
        val first = look(r, n = n, i = 0, now = a, playsAt = known)
        val second = look(r, n = n, i = 1, now = b, playsAt = known)

        first.what.player shouldBe tried

        withClue("the second look's player") {
            second.what.player shouldBe other
        }

        withClue("the plan after the second look") {
            second.plan.begun.keys shouldBe setOf(n * LOOKS_A_ROUND, n * LOOKS_A_ROUND + 1)
        }

        second.plan.begun.getValue(n * LOOKS_A_ROUND).seen() shouldBe first.what.seen()

        // the song has forgotten the first look's notes when the second one begins
        val forgotten = playing(tried, other, from = b, to = b + 20)

        withClue("the second look, after the song forgot the first look's notes") {
            look(r, n = n, i = 1, now = b, playsAt = forgotten, before = first.plan).what.player shouldBe other
        }

        // without what was carried over, nobody was shown before it, as far as the song knows
        look(r, n = n, i = 1, now = b, playsAt = forgotten).what.player shouldBe tried
    }

    "an arc goes round the player of the round's first look, never round the one who strolls nor round the stage, and stays" {
        val r = rounds(force = RoundKind.Arc)
        val n = r.withPlayers(0)
        val a = r.begins(n = n, i = 0)
        val drums = playing(Player.Wucht, from = a - 10, to = a + 20)

        withClue("an arc round the one who strolls") {
            kind(r, n = n, now = a, playsAt = playing(Player.Sirene, from = a - 10, to = a + 20)).what shouldBe RoundKind.Looks
        }

        withClue("an arc round the stage") {
            kind(r, n = n, now = a, playsAt = playing(from = 0.0, to = 0.0)).what shouldBe RoundKind.Looks
        }

        val begun = kind(r, n = n, now = a, playsAt = drums)

        begun.what shouldBe RoundKind.Arc

        withClue("the arc, asked again after the song forgot the first look's notes") {
            kind(r, n = n, now = a + 20, playsAt = playing(from = 0.0, to = 0.0), before = begun.plan).what shouldBe RoundKind.Arc
        }
    }

    "the plan in a round keeps the looks of that round and of the one before" {
        val plan = LookPlan(begun = (0 until 3 * LOOKS_A_ROUND).associateWith { Subject.Angle(STADIUM) })

        withClue("the looks kept in round 2") {
            plan.inRound(2).begun.keys shouldBe (LOOKS_A_ROUND until 3 * LOOKS_A_ROUND).toSet()
        }

        plan.inRound(1) shouldBe plan
        plan.inRound(0) shouldBe plan
    }

    "a song that grows and forgets: every look, once begun, and every round's kind, once its first look has, are the whole song's" {
        // read as the app reads a song while it plays: fourteen seconds ahead of what is heard, kept sixteen behind it
        val events = band()
        val whole = Song(Recording(rpm = RPM, events = events))
        val wholeAt: (Player) -> List<Double> = { whole.times(listOf(it.key)) }
        var compared = 0
        var forgot = 0
        var kinds = 0
        val shown = mutableSetOf<Player?>()

        for (seed in listOf(0, 7)) {
            val r = rounds(seed = seed)
            val song = Song(rpm = RPM)
            val known: (Player) -> List<Double> = { song.times(listOf(it.key)) }
            var plan = LookPlan()
            var now = -14.0

            while (now < r.start + 4 * r.length) {
                song.forget(before = now - 16.0)
                song.add(events.filter { it.t >= now + 13.3 && it.t < now + 14.0 })

                if (now >= r.start) {
                    val n = floor((now - r.start) / r.length).toInt()

                    plan = plan.inRound(n)

                    val roundKind = kind(r, n = n, now = now, playsAt = known, before = plan)

                    plan = roundKind.plan

                    for (i in listOf(3, 4, 0, 1, 2)) {
                        val kept = look(r, n = n, i = i, now = now, playsAt = known, before = plan)

                        plan = kept.plan

                        if (now >= r.begins(n = n, i = i)) {
                            val all = look(r, n = n, i = i, now = now, playsAt = wholeAt)

                            compared++
                            shown.add(all.what.player)

                            // (the song as it is known now, without what was carried over)
                            if (look(r, n = n, i = i, now = now, playsAt = known).what.seen() != all.what.seen()) {
                                forgot++
                            }

                            withClue("seed $seed, round $n, look $i at $now") {
                                kept.what.seen() shouldBe all.what.seen()
                            }
                        }
                    }

                    if (now >= r.begins(n = n, i = 0)) {
                        kinds++

                        withClue("seed $seed, the kind of round $n at $now") {
                            roundKind.what shouldBe kind(r, n = n, now = now, playsAt = wholeAt).what
                        }
                    }
                }

                now += 0.7
            }
        }

        // (many a look was asked for after the song had forgotten its notes, every player was shown, and the stage)
        compared shouldBeGreaterThan 500
        forgot shouldBeGreaterThan 100
        kinds shouldBeGreaterThan 50
        shown shouldBe (Player.entries + null).toSet()
    }

    "the choice itself, over the whole song: who is shown, by which look, and each round's kind (a change here changes the picture)" {
        val whole = Song(Recording(rpm = RPM, events = band()))
        val wholeAt: (Player) -> List<Double> = { whole.times(listOf(it.key)) }

        fun chosen(seed: Int): List<String> = (0 until 4).map { n ->
            val r = rounds(seed = seed)
            val now = r.start + (n + 1) * r.length
            val looks = (0 until LOOKS_A_ROUND).joinToString(separator = " ") { i ->
                when (val what = look(r, n = n, i = i, now = now, playsAt = wholeAt).what) {
                    is Subject.Angle -> "stage/${ANGLES.indexOf(what.angle)}"
                    is Subject.OnPlayer -> "${what.player.name}/${looksOf(what.player).indexOf(what.look)}"
                }
            }

            "${kind(r, n = n, now = now, playsAt = wholeAt).what.name}: $looks"
        }

        chosen(seed = 0) shouldBe listOf(
            "Looks: Klaue/0 Sirene/2 Beben/3 Skalpell/0 Beben/1",
            "Flight: Rune/0 Wucht/0 Skalpell/1 Skalpell/0 Klaue/3",
            "Reverse: stage/7 stage/7 Skalpell/0 stage/0 Skalpell/1",
            "Flight: Beben/0 stage/2 Beben/3 Rune/2 Klaue/0",
        )
        chosen(seed = 7) shouldBe listOf(
            "Circle: Sirene/2 Rune/0 stage/2 Skalpell/3 Klaue/0",
            "Looks: Klaue/2 stage/8 stage/10 Skalpell/3 Beben/3",
            "Reverse: Skalpell/2 stage/6 Skalpell/1 Skalpell/2 Beben/2",
            "Looks: stage/11 Beben/2 Klaue/2 Beben/0 Klaue/2",
        )

        // where both who play seldom play as a round begins, its first look is one of the two, by the roll, in the
        // order they were handed in
        val both = playing(Player.Rune, Player.Sirene, from = 0.0, to = 1000.0)
        val r = rounds(seed = 0)

        (0 until 8).joinToString(separator = " ") { n ->
            look(r, n = n, i = 0, now = r.begins(n = n, i = 0), playsAt = both).what.player?.name ?: "stage"
        } shouldBe "Rune Rune stage Rune Sirene stage Sirene Sirene"
    }
})
