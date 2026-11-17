/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.band.instruments.bass.BASS_EQUIPMENT
import io.peekandpoke.klang.viz.liveact.band.instruments.drums.DRUMS_EQUIPMENT
import io.peekandpoke.klang.viz.liveact.band.instruments.guitar.GUITAR_EQUIPMENT
import io.peekandpoke.klang.viz.liveact.band.instruments.marimba.MARIMBA_EQUIPMENT
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.TRUMPET_EQUIPMENT

/**
 * What each instrument's musician needs to equip: a check for the future, the list a scene reads to dress a member
 * for what they play. The lengths are the ones the kit and the marimba build their sticks and mallets with.
 */
class EquipmentSpec : StringSpec({

    "the drummer: a stick in each hand" {
        DRUMS_EQUIPMENT shouldBe Equipment.Sticks(perHand = 1, length = 0.40)
    }

    "the marimba player: mallets, not sticks, two in each hand" {
        MARIMBA_EQUIPMENT shouldBe Equipment.Mallets(perHand = 2, length = 0.40)
    }

    "the guitar and the bass hang on a strap" {
        GUITAR_EQUIPMENT shouldBe Equipment.OnStrap
        BASS_EQUIPMENT shouldBe Equipment.OnStrap
    }

    "the trumpet is held in the hands" {
        TRUMPET_EQUIPMENT shouldBe Equipment.InHands
    }
})
