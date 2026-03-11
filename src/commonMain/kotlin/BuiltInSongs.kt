@file:Suppress("unused")

package io.peekandpoke.klang

object BuiltInSongs {

    const val PREFIX = "builtin-song"

    private val _song = mutableListOf<Song>()
    val songs get() = _song.toList()

    private fun add(song: Song): Song = song.also {
        _song.add(song)
    }

    val tetris = add(
        Song(
            id = "$PREFIX-0001",
            title = "Synthris",
            cps = 0.63,
            code = TestTextPatterns.tetris,
            icon = "gamepad",
        )
    )

    val smallTownBoy = add(
        Song(
            id = "$PREFIX-0002",
            title = "Synthtown Boy",
            cps = 0.62,
            code = TestTextPatterns.smallTownBoy,
            icon = "record vinyl",
        )
    )

    val soundOfTheSea = add(
        Song(
            id = "$PREFIX-0006",
            title = "Sound of the sea",
            cps = 0.5,
            code = """






             import * from  "stdlib"
              import * from "strudel"
               let wind       = 0.025
                let water      = 0.035
                 let waves      = 0.075
                  let windSpiel  = 1.500
           
                    stack( //   Lean back and relax... let the waves carry you away
                // Wind ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

             note("c").fast(4).sound("brown").adsr("0.5:1.0:1.0:3.5").warmth(0.1) // . solo()
               .gain(wind).pan(sine.range(0.3, 0.7).slow(21))
               .hpf(1000).bandf(perlin.range(110, 110 * 20).slow(64)).bandq(berlin.range(0.0, 2.0).slow(39))
                     
           , // Water ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            note("c").fast(5).sound("pink").adsr("0.7:0.5:1.0:3.0") // . solo()
              .gain(water)
              .hpf(120).lpf(4000).bandf(300).bandq(1.0).early(2)
           , // Waves ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            note("<c@4 ~!15>").fast(2).sound("pink").adsr("0.75:0.5:1.0:10.0").warmth(0.2) // . solo()
             .gain(waves).pan(sine.range(0.45, 0.55).slow(21))
              .hpf(90).lpf(3500).bandf(perlin.range(100, 500).slow(22)).bandq(rand.range(0.5, 1.5))
                 .pan(0.39).superimpose(x => x.pan(0.61))
              , // Windspiel ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                n(randrun(16)).fast(4).sound("glockenspiel").scale("c2:pentatonic").pan(0.3)
                  .gain(0.25).distort(0.2).postgain(windSpiel).adsr("0.1:1.0:1.0:5.0").hpf(400).degradeBy(0.995)
                      .orbit(1).delay(0.25).delaytime(pure(1/4).div(cps)).delayfeedback(0.5) // . solo()
                           ).room(0.25).rsize(10.0)              
      














                  
            """,
            icon = "umbrella beach",
        )
    )

    val aTruthWorthLyingFor = add(
        Song(
            id = "$PREFIX-0003",
            title = "A Synth Worth Lying For",
            cps = 0.50,
            code = TestTextPatterns.aTruthWorthLyingFor,
            icon = "guitar",
        )
    )

    val strangerThings = add(
        Song(
            id = "$PREFIX-0004",
            title = "Stranger Synths",
            cps = 0.58,
            code = TestTextPatterns.strangerThingsNetflix,
            icon = "film",
        )
    )

    val finalFantasy7Prelude = add(
        Song(
            id = "$PREFIX-0005",
            title = "Final Synthasy VII Prelude",
            cps = 0.7,
            icon = "gamepad",
            code = """
import * from "stdlib"
import * from "strudel"

stack(
  cat(n(`<[ 0  1 2 4] [7 8 9 11] [14 15 16 18] [21 22 23 25] [28 25 23 22] [21 18 16 15] [14 11 9 8] [7 4 2  1]
          [-2 -1 0 2] [5 6 7  9] [12 13 14 16] [19 20 21 23] [26 23 21 20] [19 16 14 13] [12  9 7 6] [5 2 0 -1]>`).repeat(2),
      ).fast(2)
  .sound("sine").warmth(0.5).scale("C3:major").gain(0.5).clip(0.5)
  .adsr("0.05:0.2:0.5:0.15")
  
).room(0.2).rsize(5.0)
            """,
        )
    )

    val osiris = add(
        Song(
            id = "$PREFIX-0007",
            title = "Osynthris",
            cps = 0.55,
            code = """
import * from "stdlib"
import * from "strudel"

stack(
  // Guitar 1
  cat(
    n(`<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13] [3,10,15]@2 [5,12,17]] [0,7,12] 
        [12,19,24] [[6,13,18] [6,13,18]] [[6,13,18] [5,12,17]@2 [6,13,18]]>`).repeat(4),
    n(`<[[0,7]!4 [0,7]@4]!8 [[0,6]!4 [0,6]@4]!4 [[0,7]!4 [0,7]@4]!4 [[0,7]!4 [0,7]@4]!9 [[0,7]!4 [0,8]@4]!7>`)
  )
  .fast(4).scale("e2:chromatic").clip(0.99).hpf(60).lpf(3000).pan(0.5).adsr("0.01:0.1:0.3:0.05")
  .s("supersaw").unison(6).detune(0.025).gain(1.0).distort(5).postgain(0.175).warmth(0.5)
  .superimpose(
    x => x.bandf(120).bandq(1.5).crush(6).sound("pulse").adsr("0.01:0.05:0.25:0.0"),
    x => x.bandf(1920).bandq(1.25).crush(6).sound("pulse").adsr("0.01:0.05:0.25:0.0"),
    x => x.bandf(2880).bandq(1).crush(8).sound("pulse").adsr("0.01:0.2:0.25:0.0"),
  ) // .solo()

  , // Guitar 2
  cat(
    n("<~!8>").repeat(4),
    n(`<[36!8]!4 [35!8]!4 [24!8]!4 [25!8] [25!4 28!4] [28!4 29!4] [29!4 31!4]
        [32!8] [32!4 36!4] [36!8]!2 [31!8]!4 [30!8]!4 [27!8] [27!4 26!4] [26!4 25!4] [25!4 24!4]
    >`),
  )
  .fast(4).scale("e2:chromatic").clip(0.5).hpf(200).lpf(5000).pan(0.5).adsr("0.05:0.1:0.1:0.02")
  .s("pulse").unison(12).detune(0.01).gain(1.0).distort(5).postgain(0.15).warmth(0.5)
  .superimpose(x => x.bandf(960).bandq(1))
   // .mute()

  // Drums 1 
  , s("<hh!8!8 [oh hh!5 cr hh]!2!8>").adsr("0.00:0.1:0.5:0.1").hpf(200).postgain(1.25) // .solo()
  // Drums 2
  , s(`<[[bd sd]!2]!4 [bd [bd,sd] bd [bd,sd]]!3 [bd bd [bd,sd] bd  [sd!2]!4]!1
        [[bd sd]!16]!8>`).n("<7!8 0!8>").adsr("0.00:0.3:0.5:0.1").gain(0.8).hpf(30)  // . solo()
)
  .room(0.025).rsize(5)
        """.trimIndent(),
            icon = "guitar",
        )
    )
}
