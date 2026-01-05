package io.peekandpoke.klang.tones.time

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DurationValueTest : StringSpec({
    "get shorthand" {
        val q = DurationValue.get("q")
        q.empty shouldBe false
        q.name shouldBe "q"
        q.value shouldBe 0.25
        q.fraction shouldBe Pair(1, 4)
        q.dots shouldBe ""
        q.shorthand shouldBe "q"
        q.names shouldBe listOf("quarter", "crotchet")

        val dlDot = DurationValue.get("dl.")
        dlDot.empty shouldBe false
        dlDot.name shouldBe "dl."
        dlDot.dots shouldBe "."
        dlDot.value shouldBe 12.0
        dlDot.fraction shouldBe Pair(12, 1)
        dlDot.names shouldBe listOf("large", "duplex longa", "maxima", "octuple", "octuple whole")
        dlDot.shorthand shouldBe "dl"
    }

    "get long name" {
        val largeDot = DurationValue.get("large.")
        largeDot.empty shouldBe false
        largeDot.name shouldBe "large."
    }

    "value" {
        val dlValues = listOf("dl", "dl.", "dl..", "dl...").map { DurationValue.get(it).value }
        dlValues shouldBe listOf(8.0, 12.0, 14.0, 15.0)

        val lValues = listOf("l", "l.", "l..", "l...").map { DurationValue.get(it).value }
        lValues shouldBe listOf(4.0, 6.0, 7.0, 7.5)

        val qValues = listOf("q", "q.", "q..", "q...").map { DurationValue.get(it).value }
        qValues shouldBe listOf(0.25, 0.375, 0.4375, 0.46875)
    }

    "fraction" {
        val wFractions = listOf("w", "w.", "w..", "w...").map { DurationValue.get(it).fraction }
        wFractions shouldBe listOf(
            Pair(1, 1),
            Pair(3, 2),
            Pair(7, 4),
            Pair(15, 8)
        )
    }

    "shorthands" {
        DurationValue.shorthands().joinToString(",") shouldBe "dl,l,d,w,h,q,e,s,t,sf,h,th"
    }

    "names" {
        DurationValue.names()
            .joinToString(",") shouldBe "large,duplex longa,maxima,octuple,octuple whole,long,longa,double whole,double,breve,whole,semibreve,half,minim,quarter,crotchet,eighth,quaver,sixteenth,semiquaver,thirty-second,demisemiquaver,sixty-fourth,hemidemisemiquaver,hundred twenty-eighth,two hundred fifty-sixth"
    }
})
