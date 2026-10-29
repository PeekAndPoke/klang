/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val soundOfTheSeaSong = Song(
    id = "${BuiltInSongs.PREFIX}-synth-of-the-sea",
    title = "Gestrandet",
    rpm = 30.0,
    icon = "umbrella beach",
    code = """


      import * from  "stdlib"
       import * from "sprudel"
        let wind       = 0.800
         let water      = 0.120
          let waves      = 0.165
           let windSpiel  = 3.250
            let randSeed   = sineOfDay.add(1).mul(100)

            stack( //   Lean back and relax... let the waves carry you away
          // Wind ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

       note("c").fast(8).sound("brown").adsr(1.0, 1.0, 1.0, 3.5).vowel("u|o|a".slow(8))  // . solo()
     .gain(wind).pan(berlin.range(0.2, 0.8).slow(34))//.lpf(1500)
    .hpf(300).bpf(freq = perlin.range(110, 110 * 20).slow(7), q = berlin.range(1.5, 5.0).slow(0.5))

   , // Water ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
   sound("pink pink pink pink".slow(9)).late(perlin(0.0, 0.5)).legato(2).degrade(0.33).adsr(2.5, 3.0, 0.3, 5.0) //  . solo()
   .gain(water).hpf(120).lpf(freq = 8000, env = perlin(5, 6)).bpf(freq = perlin(300, 400), q = 1.0).early(2)

    , // Waves ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    note("~ c ~ c ~ c ~").legato(perlin(6, 8)).degrade(96/100).sound("pink").adsr(1.0, 7.0, 0.3, 10.0) // . solo()
    .gain(waves).hpf(60).lpf(freq = 400, env = perlin(30, 35), attack = 2.0, decay = 3.0, sustain = 0.4, release = 20.0)
    .bpf(freq = perlin.range(120, 500).slow(3), q = rand.range(0.5, 1.5))
     .pan(sine.range(0.1, 0.4).slow(4)).superimpose(x => x.pan(sine.range(0.9, 0.6).slow(5)))
      .superimpose(x => x.sound("white").adsr(1.2, 5.0, 0.2, 15.0).velocity(0.3).hpf(1000).lpf(12500))

         , // Windspiel ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ ~~~~~~~~~~~
           n(randrun(16)).orbit(2).fast(4).degradeBy(0.995).sound("glockenspiel").scale("f2:pentatonic").body(material = "glass")
             .gain(0.25 * windSpiel).distort(0.05).adsr(0.05, 0.2, 0.5, 5.0).hpf(400).lpf(freq = 3000, q = 2, env = 24)
                 .orbit(1).delay(wet = 0.25, time = pure(1/4).div(cps), feedback = 0.85) // . solo()
                       ).reverb(wet = 0.35, size = 8.0, lowpass = 12500).seed(randSeed).analog(20)



    
    """,
)
