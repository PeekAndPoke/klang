@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val smallTownBoySong = Song(
    id = "${BuiltInSongs.PREFIX}-smalltown-synth",
    title = "Smalltown Synth",
    rpm = 37.2,
    icon = "record vinyl",
    code = """
import * from "stdlib"
import * from "sprudel"

stack(
    // melody
    arrange(
      [8, silence],
      [8, n("<[~ 0] 2 [0 2] [~ 2][~ 0] 1 [0 1] [~ 1][~ 0] 3 [0 3] [~ 3][~ 0] 2 [0 2] [~ 2]>*4")],
    ).sound("triangle").scale("C4:minor")
    .orbit(1).gain(0.3).adsr("0.05:0.2:0.3:0.2")
    .hpf(800)
    .superimpose(x => x.transpose("12").gain(0.5))
    , // bass  ---------------------------------------------------------
    note("<[c2 c3]*4 [bb1 bb2]*4 [f2 f3]*4 [eb2 eb3]*4>")
    .orbit(3).sound("supersaw").unison(8).detune(0.1)
    .adsr("0.02:0.3:0.0:0.1").lpf(1200).hpf(80).gain(1.0).postgain(1.25)
    , // Drums ---------------------------------------------------------
    sound("[bd hh sd hh] [bd [bd, hh] sd oh]").fast(1)
     .orbit(4).pan(0.5).gain(0.95)
     .delay(0.2).delaytime(pure(1/8).div(cps)).delayfeedback(0.3),
).room(0.1).rsize(5.0)

    """.trimIndent(),
)
