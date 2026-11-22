/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.guitar

import io.peekandpoke.klang.viz.liveact.band.instruments.strings.BodyOutline
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.ClawMark
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.Ornaments
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.StringDesign
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.Tuning

/**
 * The electric guitars, by model: each its own shape (the body's outline, the head, the pickups, six strings and
 * their tuners) and its gold, all with the guitar's scale and tuning, its right hand strumming from the wrist, wide
 * and quick, and hanging as a guitar does. Played by a string player (see `StringPlayer`); the cast says who plays
 * which (the maintainer, 2026-11-21: "for now letters are fine: ... E-Guitar A, .. B").
 */
object Guitar {
    /**
     * E-Guitar A's body, Skalpell's (his name means scalpel): slim, two horns like blades, the upper one long, and a
     * pointed lower end; sharp at those three tips, smooth everywhere else.
     */
    val BLADE: BodyOutline = BodyOutline(
        points = listOf(
            0.12 to -0.04, 0.145 to -0.06, 0.18 to -0.08, 0.228 to -0.098, 0.16 to -0.113, 0.08 to -0.113, 0.0 to -0.118,
            -0.10 to -0.137, -0.20 to -0.157, -0.30 to -0.175, -0.25 to -0.11, -0.225 to -0.04, -0.205 to 0.03,
            -0.17 to 0.09, -0.11 to 0.122, -0.04 to 0.128, 0.04 to 0.12, 0.12 to 0.124, 0.20 to 0.133, 0.29 to 0.145,
            0.215 to 0.108, 0.155 to 0.072, 0.12 to 0.04,
        ),
        corners = setOf(0.228 to -0.098, -0.30 to -0.175, 0.29 to 0.145),
    )

    /** E-Guitar A's head: a blade too, tapering to a point on the tuners' side. */
    val BLADE_HEAD: BodyOutline = BodyOutline(
        points = listOf(0.0 to -0.022, 0.03 to -0.034, 0.17 to -0.042, 0.23 to -0.05, 0.12 to 0.01, 0.05 to 0.028, 0.0 to 0.022),
        corners = setOf(0.17 to -0.042, 0.23 to -0.05),
    )

    /** Two horns: E-Guitar B's body. */
    val DOUBLE_CUTAWAY: BodyOutline = BodyOutline(
        listOf(
            0.12 to -0.045, 0.15 to -0.09, 0.185 to -0.125, 0.14 to -0.15, 0.06 to -0.14, -0.02 to -0.12, -0.13 to -0.175,
            -0.225 to -0.105, -0.24 to 0.0, -0.225 to 0.10, -0.13 to 0.17, -0.02 to 0.125, 0.07 to 0.145, 0.16 to 0.15,
            0.215 to 0.115, 0.17 to 0.085, 0.12 to 0.045,
        ),
    )

    /** One horn, the other side round: E-Guitar C's body. */
    val SINGLE_CUTAWAY: BodyOutline = BodyOutline(
        listOf(
            0.12 to -0.045, 0.165 to -0.075, 0.19 to -0.115, 0.14 to -0.148, 0.05 to -0.135, -0.02 to -0.118,
            -0.13 to -0.172, -0.22 to -0.108, -0.235 to 0.0, -0.22 to 0.108, -0.13 to 0.170, -0.02 to 0.122,
            0.07 to 0.140, 0.13 to 0.112, 0.145 to 0.07, 0.12 to 0.045,
        ),
    )

    /** The round head of E-Guitar B and C. */
    val ROUND_HEAD: BodyOutline = BodyOutline(
        listOf(0.0 to -0.022, 0.035 to -0.032, 0.16 to -0.038, 0.19 to -0.012, 0.172 to 0.026, 0.04 to 0.03, 0.0 to 0.022),
    )

    /**
     * E-Guitar A, Skalpell's (KLANG-00478; the maintainer, 2026-11-21: "I like guitar A for its sharpness, it fits to
     * Skalpell"): the blade, with a thin gold line along its edge, gold dots on the fretboard and gold hardware.
     */
    val A: StringDesign = guitar(
        name = "E-Guitar A",
        outline = BLADE,
        head = BLADE_HEAD,
        knobs = listOf(-0.13 to -0.09, -0.18 to -0.07),
        ornaments = Ornaments(edge = true, markers = true, goldHardware = true),
    )

    /** E-Guitar B: two horns, no gold (the band's guitar before the models had names). */
    val B: StringDesign = guitar(name = "E-Guitar B", outline = DOUBLE_CUTAWAY, head = ROUND_HEAD)

    /**
     * E-Guitar C, Klaue's rhythm guitar (KLANG-00478; the maintainer, 2026-11-22: "I like the shape of Klaue's guitar,
     * it needs some makeup"): one horn, and across its lower half, which his arm leaves free, three scratches of a claw in
     * gold (his name means claw), raked from the strings toward the edge; gold dots on the fretboard and gold hardware.
     */
    val C: StringDesign = guitar(
        name = "E-Guitar C",
        outline = SINGLE_CUTAWAY,
        head = ROUND_HEAD,
        ornaments = Ornaments(
            markers = true,
            goldHardware = true,
            claws = listOf(
                ClawMark(from = 0.090 to -0.052, to = -0.020 to -0.105, bow = -0.006),
                ClawMark(from = 0.052 to -0.042, to = -0.070 to -0.130, bow = -0.007),
                ClawMark(from = 0.012 to -0.040, to = -0.112 to -0.140, bow = -0.006),
            ),
        ),
    )

    /** A guitar of the model [name]: what the string player reads of it. */
    private fun guitar(
        name: String,
        outline: BodyOutline,
        head: BodyOutline,
        knobs: List<Pair<Double, Double>> = listOf(-0.15 to -0.085, -0.19 to -0.05),
        ornaments: Ornaments = Ornaments.NONE,
    ): StringDesign = StringDesign(
        name = name, scale = 0.64, bodyHalfLength = 0.22, bodyHalfWidth = 0.165, strings = 6, neck = 0.044, tilt = 0.50,
        swing = 0.095, tau = 0.028, hang = -0.09, arm = 0.15, outline = outline,
        pickups = listOf(0.01, 0.075),
        knobs = knobs,
        head = head,
        tuning = Tuning.GUITAR,
        ornaments = ornaments,
    )
}
