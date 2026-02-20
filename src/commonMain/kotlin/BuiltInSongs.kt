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
               let wind       = 0.045
                let water      = 0.035
                 let waves      = 0.075
                  let windSpiel  = 1.500
           
                    stack( //   Lean back and relax... let the waves carry you away
                // Wind ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

             note("c").fast(4).sound("brown").adsr("0.5:1.0:1.0:3.5").warmth(0.1) // . solo()
               .gain(wind).pan(sine.range(0.3, 0.7).slow(21))
               .hpf(1000).bandf(perlin.range(110, 110 * 20).slow(64)).bandq(berlin.range(1.0, 4.0).slow(39))
                     
           , // Water ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            note("c").fast(5).sound("pink").adsr("0.7:0.5:1.0:3.0") // . solo()
              .gain(water)
              .hpf(120).lpf(4000).bandf(300).bandq(1.0).early(2)
           , // Waves ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            note("<c@4 ~!15>").fast(2).sound("pink").adsr("0.75:0.5:1.0:10.0").warmth(0.2) // . solo()
             .gain(waves).pan(sine.range(0.45, 0.55).slow(21))
              .hpf(90).lpf(3500).bandf(perlin.range(100, 660).slow(22)).bandq(rand.range(1.0, 1.5))
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
            cps = 0.47,
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
            title = "Final Fantasy VII Prelude",
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
}
