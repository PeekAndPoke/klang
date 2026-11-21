/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.dress

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class EyesInShadowSpec : StringSpec({

    "the lamp throws an eye onto the surface behind it, as many times its way from the lamp as the surface is farther" {
        // the front lamp at 8.6, an eye at 1.0, the wall at -8 and the surface behind the fence at -10.5
        EyesInShadow.scale(lampZ = 8.6, eyeZ = 1.0, planeZ = -8.0)!! shouldBe ((16.6 / 7.6) plusOrMinus 1e-12)
        EyesInShadow.scale(lampZ = 8.6, eyeZ = 1.0, planeZ = -10.5)!! shouldBe ((19.1 / 7.6) plusOrMinus 1e-12)
        // half way between the lamp and the surface: twice its size
        EyesInShadow.scale(lampZ = 10.0, eyeZ = 0.0, planeZ = -10.0)!! shouldBe 2.0
        // and the same with the lamp on the other side
        EyesInShadow.scale(lampZ = -10.0, eyeZ = 0.0, planeZ = 10.0)!! shouldBe 2.0
    }

    "no shadow where the surface is not behind the eye, seen from the lamp, or the eye is level with the lamp" {
        // the surface before the eye
        EyesInShadow.scale(lampZ = 8.6, eyeZ = -9.0, planeZ = -8.0).shouldBeNull()
        // the surface behind the lamp
        EyesInShadow.scale(lampZ = 8.6, eyeZ = 1.0, planeZ = 12.0).shouldBeNull()
        // the eye on the surface itself: no shadow larger than it
        EyesInShadow.scale(lampZ = 8.6, eyeZ = -8.0, planeZ = -8.0).shouldBeNull()
        // level with the lamp, and no number
        EyesInShadow.scale(lampZ = 8.6, eyeZ = 8.6, planeZ = -8.0).shouldBeNull()
        EyesInShadow.scale(lampZ = 8.6, eyeZ = Double.NaN, planeZ = -8.0).shouldBeNull()
    }

    "she looks a way within 75 degrees of it, no more beyond 88, and between the two it stays as it was" {
        fun at(degrees: Double, was: Boolean): Boolean {
            val a = degrees * PI / 180

            // her face along z, the way so many degrees off it, some metres long
            return EyesInShadow.facing(was = was, faceX = 0.0, faceZ = 0.2, toX = 7 * sin(a), toZ = 7 * cos(a))
        }

        for (was in listOf(false, true)) {
            at(degrees = 0.0, was = was) shouldBe true
            at(degrees = 74.0, was = was) shouldBe true
            at(degrees = -74.0, was = was) shouldBe true
            at(degrees = 89.0, was = was) shouldBe false
            at(degrees = -89.0, was = was) shouldBe false
            at(degrees = 180.0, was = was) shouldBe false
            // between the two: as it was
            at(degrees = 81.0, was = was) shouldBe was
            at(degrees = -81.0, was = was) shouldBe was
        }
    }

    "a way of no length or no number says nothing: it stays as it was" {
        for (was in listOf(false, true)) {
            EyesInShadow.facing(was = was, faceX = 0.0, faceZ = 0.0, toX = 1.0, toZ = 1.0) shouldBe was
            EyesInShadow.facing(was = was, faceX = 0.0, faceZ = 1.0, toX = 0.0, toZ = 0.0) shouldBe was
            EyesInShadow.facing(was = was, faceX = Double.NaN, faceZ = 1.0, toX = 0.0, toZ = 1.0) shouldBe was
        }
    }

    "the eyes cross: all in her face at 0, all in her shadow at 1, each fade eased" {
        EyesInShadow.face(0.0) shouldBe 1.0
        EyesInShadow.wall(0.0) shouldBe 0.0
        EyesInShadow.face(1.0) shouldBe 0.0
        EyesInShadow.wall(1.0) shouldBe 1.0
        // her face's eyes are gone before the shadow's come, up to where the crossing's two halves overlap
        EyesInShadow.face(EyesInShadow.CROSS) shouldBe 0.0
        EyesInShadow.wall(1.0 - EyesInShadow.CROSS) shouldBe 0.0
        // eased: slow at the start, as slow as it ends
        EyesInShadow.face(0.06) shouldBe (EyesInShadow.wall(0.94) plusOrMinus 1e-12)
        (1.0 - EyesInShadow.face(0.06)) shouldBeLessThan 0.06 / EyesInShadow.CROSS
    }

    "the two never both shine at full: together never more than one, and only faint where both shine" {
        for (i in 0..1000) {
            val share = i / 1000.0
            val face = EyesInShadow.face(share)
            val wall = EyesInShadow.wall(share)

            (face + wall) shouldBeLessThanOrEqual 1.0

            if (face > 0.0 && wall > 0.0) {
                minOf(face, wall) shouldBeLessThan 0.1
            }
        }

        // in the middle both are faint, and as faint
        EyesInShadow.face(0.5) shouldBe (EyesInShadow.wall(0.5) plusOrMinus 1e-12)
        EyesInShadow.face(0.5) shouldBeLessThan 0.1
    }
})
