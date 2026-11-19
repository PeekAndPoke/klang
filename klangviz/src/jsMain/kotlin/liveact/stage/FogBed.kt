/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

/**
 * Where the fog's sheets ([GroundFog]) stand, and how large they and their maps are: [wide] by [tall] metres (for
 * level sheets, [tall] is their depth), worked out at [mapWide] by [mapTall] points each.
 */
enum class FogBed(val wide: Double, val tall: Double, val mapWide: Int, val mapTall: Int) {
    /** Level sheets on the floor, from ankle to knee height: the fog of the machines. A point every 7 cm. */
    Floor(wide = 26.0, tall = 22.0, mapWide = 384, mapTall = 324),

    /**
     * Upright sheets, parallel to the wall, between the wire mesh and the dark surface behind it ([Fence],
     * `?stage=mesh`): a haze from the floor to well above the band's heads. Soft, so a point every 9 cm will do.
     */
    BehindMesh(wide = 24.0, tall = 7.5, mapWide = 256, mapTall = 80),
}
