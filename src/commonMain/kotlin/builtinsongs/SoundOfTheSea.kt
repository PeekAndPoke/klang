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
            let wind       = 0.080
             let water      = 0.080
              let waves      = 0.110
               let windSpiel  = 3.250

                 stack( //   Lean back and relax... let the waves carry you away
              // Wind ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

           note("c").fast(8).sound("brown").adsr("0.5:1.0:1.0:3.5").warmth(0.1)  // . solo()
         .gain(wind).pan(berlin.range(0.2, 0.8).slow(34))//.lpf(1500)
        .hpf(120).bandf(perlin.range(110, 110 * 15).slow(64)).bandq(perlin.range(0.5, 5.0).slow(21))

        , // Water ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
       sound("pink pink pink pink").legato(2).degrade(0.5).adsr("1.5:3.0:0.5:5.0") //  . solo()
       .gain(water).hpf(120).lpf(4000).bandf(300).bandq(1.0).early(2)

        , // Waves ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        note("c c c c").legato(4).degrade(95/100).sound("pink").adsr("1.0:5.0:0.6:20.0").warmth(0.1) // . solo()
        .gain(waves).hpf(120).lpf(5000).lpadsr("1.0:3.0:0.4:15.0").lpenv(10)
        .bandf(perlin.range(120, 500).slow(22)).bandq(rand.range(0.5, 1.5))
         .pan(sine.range(0.1, 0.4).slow(4)).superimpose(x => x.pan(sine.range(0.9, 0.6).slow(5)))
          .superimpose(x => x.sound("pink").adsr("0.3:0.8:0.2:1.5").velocity(0.2).hpf(2500).lpf(8000))

             , // Windspiel ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
               n(randrun(16)).fast(4).sound("glockenspiel").scale("f2:pentatonic").pan(0.3)
                 .gain(0.25).distort(0.05).postgain(windSpiel).adsr("0.05:0.3:0.5:5.0").hpf(400).degradeBy(0.995)
                     .orbit(1).delay(0.25).delaytime(pure(1/4).div(cps)).delayfeedback(0.75) // . solo()
                           ).room(0.25).rsize(10.0).seed(sinOfDay.add(1).mul(24 * 60 * 60 * 100))










            """,
)
