/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val finalFantasy7PreludeSong = Song(
    id = "${BuiltInSongs.PREFIX}-final-synthasy-7-prelude",
    title = "Final Synthasy VII Prelude",
    rpm = 40.0,
    icon = "gamepad",
    code = """import * from "stdlib"
import * from "sprudel"

// Press "Play"! ... Headphones are advisable ...
//
// Try changing the "reverbSize" value, and the click Update.
// 
// 0.0 .. your home   |   10.0 .. cathedral

const reverbSize = 6.0  

// Hints:
// - Put the cursor on a function and press Alt+Q: its help opens below in the Quick Help tab
// - Put the cursor inside a call, as lpf( ): its tool opens below, in the Sprudel tab
// - Hold Ctrl and the UI will show available keyboard shortcuts
// - Alt+M opens the Motor's menu: the songs, the docs, the looks

stack(
  cat(n(`<[ 0  1 2 4] [7 8 9 11] [14 15 16 18] [21 22 23 25] [28 25 23 22] [21 18 16 15] [14 11 9 8] [7 4 2  1]
          [-2 -1 0 2] [5 6 7  9] [12 13 14 16] [19 20 21 23] [26 23 21 20] [19 16 14 13] [12  9 7 6] [5 2 0 -1]>`),
  )
  // Put the cursor on "rosewood": its tool opens below, in the Sprudel tab
  .fast(2).scale("C3:major").body(material = "rosewood", wet = 1.0, floor = 0.2)
  .sound("sine").hpf(110).lpf(5000).gain(0.5).clip(0.5)
  // Put the cursor in the adsr( ): drag the curve, and hear it change
  .adsr(0.05, 0.2, 0.5, 0.15)

).reverb(wet = 0.25, size = reverbSize).analog(5)


    """,
)
