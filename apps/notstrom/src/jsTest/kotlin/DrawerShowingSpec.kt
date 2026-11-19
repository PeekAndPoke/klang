/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.notstrom

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class DrawerShowingSpec : StringSpec({

    "a mouse move shows it; it goes 3 s after the last move" {
        val showing = DrawerShowing()

        showing.mouseMoved(now = 1000.0, onDrawer = false)
        showing.shown shouldBe true
        showing.due(now = 3999.0) shouldBe false
        showing.mouseMoved(now = 3000.0, onDrawer = false)
        showing.due(now = 5999.0) shouldBe false
        showing.due(now = 6000.0) shouldBe true
        showing.shown shouldBe false
    }

    "a mouse resting on the drawer holds it; off the drawer, the 3 s run again" {
        val showing = DrawerShowing()

        showing.mouseMoved(now = 0.0, onDrawer = true)
        showing.due(now = 60_000.0) shouldBe false
        showing.shown shouldBe true
        showing.mouseMoved(now = 60_000.0, onDrawer = false)
        showing.due(now = 63_000.0) shouldBe true
    }

    "a tap on the stage shows it, the next tap on the stage hides it, and that tap is the drawer's" {
        val showing = DrawerShowing()

        showing.tapped(now = 0.0, onDrawer = false) shouldBe false
        showing.shown shouldBe true
        showing.tapped(now = 2000.0, onDrawer = false) shouldBe true
        showing.shown shouldBe false
        showing.tapped(now = 4000.0, onDrawer = false) shouldBe false
        showing.shown shouldBe true
    }

    "taps in the drawer keep it, and start the 10 s anew; a tap never holds it as a resting mouse does" {
        val showing = DrawerShowing()

        showing.tapped(now = 0.0, onDrawer = false)
        showing.tapped(now = 8000.0, onDrawer = true) shouldBe false
        showing.shown shouldBe true
        showing.due(now = 17_999.0) shouldBe false
        showing.due(now = 18_000.0) shouldBe true
        showing.shown shouldBe false
    }

    "after 10 s with no touch it goes by itself" {
        val showing = DrawerShowing()

        showing.tapped(now = 0.0, onDrawer = false)
        showing.due(now = 9999.0) shouldBe false
        showing.due(now = 10_000.0) shouldBe true
    }

    "a tap as the time runs out: its intent wins, it shows the drawer anew and does not hide it" {
        val showing = DrawerShowing()

        showing.tapped(now = 0.0, onDrawer = false)
        // the time has come, and nobody has asked yet: the tap on the stage shows it for another 10 s
        showing.tapped(now = 10_000.0, onDrawer = false) shouldBe false
        showing.shown shouldBe true
        showing.due(now = 10_250.0) shouldBe false
        showing.due(now = 20_000.0) shouldBe true
        // just gone: a tap shows it
        showing.tapped(now = 20_100.0, onDrawer = false) shouldBe false
        showing.shown shouldBe true
    }

    "a tap after a mouse rested on the drawer (a touch screen with a mouse): the mouse holds it no more" {
        val showing = DrawerShowing()

        showing.mouseMoved(now = 0.0, onDrawer = true)
        showing.tapped(now = 1000.0, onDrawer = true) shouldBe false
        showing.due(now = 11_000.0) shouldBe true
        showing.shown shouldBe false
    }
})
