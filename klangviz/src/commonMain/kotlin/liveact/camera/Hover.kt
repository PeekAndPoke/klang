/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import kotlin.math.sin

// A camera on a drone that hovers: almost steady in its place. It drifts a very little, very slowly, and does not
// tremble (the maintainer, 2026-11-17: "a camera-drone that is almost steady in place").

/**
 * How far a hovering camera is off its place at [t] (seconds), from -1 to 1: two slow drifts laid over each other,
 * one swing in about ten seconds and one in about seventeen. [phase] gives each direction a course of its own (give
 * numbers well apart). It follows from the time alone, so the same moment has the same picture.
 */
fun hover(t: Double, phase: Double): Double {
    return 0.6 * sin(0.61 * t + phase) + 0.4 * sin(0.37 * t + 1.9 * phase + 1.0)
}
