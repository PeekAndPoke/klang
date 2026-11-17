/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.theatre

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.math.acos
import kotlin.math.sqrt

private fun Heading.long(): Double = sqrt(x * x + y * y + z * z)

private fun between(a: Heading, b: Heading): Double = acos((a.x * b.x + a.y * b.y + a.z * b.z).coerceIn(-1.0, 1.0))

class SkySpec : StringSpec({

    "the stars: as many as said, each a way to look that is one long, none down where the audience's lights are" {
        val stars = Sky.stars

        stars.size shouldBe Sky.STARS

        stars.forEach { star ->
            star.at.long() shouldBe (1.0 plusOrMinus 1e-9)
            star.at.height shouldBeGreaterThan Sky.LOWEST - 1e-9
            (star.seed in 0.0..1.0) shouldBe true
        }
    }

    "the stars stand all round and at every height, and are the same ones every time" {
        val stars = Sky.stars

        // each quarter of the sky round the stage has its share
        stars.count { it.at.x > 0 && it.at.z > 0 } shouldBeGreaterThan Sky.STARS / 6
        stars.count { it.at.x < 0 && it.at.z > 0 } shouldBeGreaterThan Sky.STARS / 6
        stars.count { it.at.x > 0 && it.at.z < 0 } shouldBeGreaterThan Sky.STARS / 6
        stars.count { it.at.x < 0 && it.at.z < 0 } shouldBeGreaterThan Sky.STARS / 6
        // low ones and high ones
        stars.count { it.at.height < 0.5 } shouldBeGreaterThan Sky.STARS / 4
        stars.count { it.at.height > 1.0 } shouldBeGreaterThan Sky.STARS / 20
        // no two at one place
        stars.map { it.at }.toSet().size shouldBe Sky.STARS

        Sky.stars shouldBe stars
    }

    "the moon stands low over the audience, a little to one side" {
        Sky.MOON.long() shouldBe (1.0 plusOrMinus 1e-9)
        Sky.MOON.z shouldBeGreaterThan 0.9
        Sky.MOON.height shouldBeGreaterThan 0.15
        Sky.MOON.height shouldBeLessThan 0.25
    }

    "a shooting star is rare, short, and falls: the same one at the same time" {
        // a tenth of a second at a time over an hour of show
        val times = (0 until 36000).map { it * 0.1 }
        val seen = times.mapNotNull { t -> Sky.shootingStar(t)?.let { t to it } }

        // some do fall, and all in all for a small share of the time
        seen.size shouldBeGreaterThan 100
        (seen.size.toDouble() / times.size) shouldBeLessThan Sky.FALLS / Sky.EVERY

        seen.forEach { (t, star) ->
            (star.share in 0.0..1.0) shouldBe true
            star.from.long() shouldBe (1.0 plusOrMinus 1e-9)
            star.to.long() shouldBe (1.0 plusOrMinus 1e-9)
            // it falls: it ends lower than it began, still above the horizon, a good way from where it began
            star.to.height shouldBeLessThan star.from.height
            star.to.height shouldBeGreaterThan 0.1
            between(star.from, star.to) shouldBeGreaterThan 0.15
            between(star.from, star.to) shouldBeLessThan 0.45

            Sky.shootingStar(t) shouldBe star
        }
    }

    "while one falls it goes on its way steadily, from its start to its end" {
        val first = (0 until 36000).map { it * 0.1 }.first { Sky.shootingStar(it) != null }
        val start = first - Sky.shootingStar(first).shouldNotBeNull().share * Sky.FALLS
        val shares = (0..9).map { Sky.shootingStar(start + it * Sky.FALLS / 10 + 1e-6).shouldNotBeNull() }

        shares.map { it.share }.zipWithNext().all { (a, b) -> b > a } shouldBe true
        shares.first().share shouldBeLessThan 0.01
        // one star all the way: the same start and end
        shares.map { it.from to it.to }.toSet().size shouldBe 1
        // before and after: none
        Sky.shootingStar(start - 0.01) shouldBe null
        Sky.shootingStar(start + Sky.FALLS + 0.01) shouldBe null
    }

    "before the song begins the sky is as at any other time: shooting stars fall there too, by the same rules" {
        val before = (1..40000).map { -it * 0.1 }.mapNotNull { Sky.shootingStar(it) }

        before.size shouldBeGreaterThan 100

        before.forEach { star ->
            (star.share in 0.0..1.0) shouldBe true
            star.to.height shouldBeLessThan star.from.height
            star.to.height shouldBeGreaterThan 0.1
        }
    }
})
