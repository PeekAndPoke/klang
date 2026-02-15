@file:Suppress("unused")

package io.peekandpoke.klang

object TestTextPatterns {
    val cMajorNotes = """
        note("c3 d3 e3 f3 g3 a3 b3 c4")
    """.trimIndent()

    val smallTownBoyBass = """
                note("<[c2 c3]*4 [bb1 bb2]*4 [f2 f3]*4 [eb2 eb3]*4>")
                .sound("supersaw").unison(16).lpf(sine.range(400, 2000).slow(4))
            """.trimIndent()

    val smallTownBoyMelody = """
                n("<[~ 0] 2 [0 2] [~ 2][~ 0] 1 [0 1] [~ 1][~ 0] 3 [0 3] [~ 3][~ 0] 2 [0 2] [~ 2]>*4")
                .scale("C4:minor")
                .sound("saw")
            """.trimIndent()

    val smallTownBoy = """
import * from "stdlib"
import * from "strudel"

stack(
    // melody
    arrange(
      [8, silence],
      [8, n("<[~ 0] 2 [0 2] [~ 2][~ 0] 1 [0 1] [~ 1][~ 0] 3 [0 3] [~ 3][~ 0] 2 [0 2] [~ 2]>*4")],
    ).sound("triangle").scale("C4:minor")
    .orbit(1).gain(0.3).adsr("0.05:0.7:0.0:0.2")
    .hpf(800)
    .superimpose(x => x.transpose("12").gain(0.5))
    , // bass  ---------------------------------------------------------
    note("<[c2 c3]*4 [bb1 bb2]*4 [f2 f3]*4 [eb2 eb3]*4>")
    .orbit(3).sound("supersaw").unison(8).detune(0.1)
    .adsr("0.02:0.3:0.0:0.1").lpf(1200).hpf(80).gain(1.0).postgain(1.5)
    , // Drums ---------------------------------------------------------
    sound("[bd hh sd hh] [bd [bd, hh] sd oh]").fast(1)
     .orbit(4).pan(0.5).gain(0.9)
     .delay(0.2).delaytime(pure(1/8).div(cps)).delayfeedback(0.3),
).room(0.1).rsize(5.0)
                                
    """.trimIndent()

    val tetris = """
import * from "stdlib"                                              ////////                   ////////
import * from "strudel"                                             ////////                   ////////
                                                                    ////////                   ////////
stack(                                                              ////////                   ////////
  note(`<
    [e5 [b4 c5] d5 [c5 b4]]    [a4 [a4 c5] e5 [d5 c5]]     //////// //////// ////////          ////////                  ////////
    [b4 [~ c5] d5 e5]          [c5 a4 a4 ~]                //////// //////// ////////          ////////                  ////////
                                                           //////// //////// ////////          ////////                  ////////
    [[~ d5] [~ f5] a5 [g5 f5]] [e5 [~ c5] e5 [d5 c5]]      //////// //////// ////////          ////////                  ////////
    [b4 [b4 c5] d5 e5]         [c5 a4 a4 ~]
  >`)                                                                                          ////////          //////// ////////
    .sound("tri").clip(0.33).hpf(600)                                                          ////////          //////// ////////
    .superimpose(x => x.transpose("<0 12 0 -12>/8"))                                           ////////          //////// ////////
    .orbit(0).gain(0.22).pan(cosine2.range(0.3, 0.7).oneMinusValue().slow(64))                 ////////          //////// ////////
    .delay(0.3).delaytime(pure(1/8).div(cps)).delayfeedback(0.25)      
    .filterWhen(x => x >= 16)                                                                  ////////          ////////
  ,                                                                                            ////////          ////////
  note(`<                                                                                      ////////          ////////
    [[e2 e3]*4]                   [[a2 a3]*4]                                                  ////////          ////////
    [[g#2 g#3]*2 [e2 e3]*2]       [a3 a2 a2 a1 a1 a2 [a2 a3] [a4 a5|a5|a5|e5]]                                           
                                                                                                        //////// //////// ////////
    [[d2 d3]*4]                   [[c2 c3]*4]                                                           //////// //////// ////////
    [[b1 b2 b1 b2] [e2 e3 e2 e3]] [a3 a2 a2 a1 a1 [a2 e2] [a5|a5|a5|e5 a4] [a2 a3]]                     //////// //////// ////////
  >`)                                                                                                   //////// //////// ////////
    .sound("supersaw").spread(0.5).unison(sine.range(8, 16).slow(32)).warmth(0.5)                       
    .orbit(1).gain(1.0).pan(cosine2.slow(64).range(0.3, 0.7)).adsr("0.01:0.25:0.5:0.25")                                  ////////
    .superimpose(x => x.transpose("<0 12 0 -12>/8").bandf(sine.range(2000, 6000).slow(24)).bandq(1.2).gain(0.75))         ////////
    .detune(sine.range(0.05, 0.3).early(1.5).slow(12))                                                                    ////////
    .filterWhen(x => x >= 31.4)                                                                                           ////////
  ,                                                                                                                       
  sound(`<
    [[bd:2,cr,cr]  hh  sd       hh]       [bd  hh  sd    oh]  [bd       hh  sd  hh]       [bd  hh       sd             hh        ]
    [[bd,hh]       hh  sd       hh]       [bd  hh  sd    oh]  [bd       hh  sd  hh]       [bd  hh       [mt mt,sd]     [ht ht,oh]]
    [[bd:2,cr]     hh  sd       hh]       [bd  hh  sd    oh]  [bd       hh  sd  hh]       [bd  hh       sd             hh        ]
    [[bd,hh]       hh  [sd,hh]  oh]       [bd  hh  sd    oh]  [bd       hh  sd  hh]       [bd  hh       [sd sd]        [sd sd]   ]
                                                                           
    [[bd:2,cr,cr]  hh  sd       sd]       [bd  hh  sd    oh]  [bd       hh  sd  hh]       [bd  hh       sd             hh        ]
    [[bd,hh]       hh  sd       [hh sd]]  [bd  hh  sd    oh]  [bd       hh  sd  hh]       [bd  hh       [lt lt,sd sd]  ~         ]
    [[bd:2,cr]     hh  sd       [sd,hh]]  [bd  hh  sd:8  oh]  [bd       hh  sd  hh]       [bd  hh       sd             [bd,oh]   ]
    [[bd,cr]       hh  [sd,hh]  cr]       [cr  hh  cr    hh]  [[sd,oh]  bd  sd  [bd,hh]]  [sd  [bd,hh]  [bd bd]        [bd bd,hh]]
  >`)
    .orbit(2).gain("0.8".add(berlin.range(-0.1, 0.0).fast(16))).adsr("0.01:0.2:0.8:0.5")
    .fast(2)
  ,

).room(0.1).rsize(2.0).compressor("-24:1.2:8:0.03:0.2")
    
    """.trimIndent()

    val strangerThingsNetflix = """
import * from "stdlib"
import * from "strudel"

let wait = 16
let keep = 32 * 6

stack(
  // Claps --------------------------------------------------------------------------------------------------
  sound("cp ~ cp ~ ~ cp cp ~  cp ~ ~ ~ cp cp ~ ~").slow(4).gain(0.3).legato(2.0)
    .bandf(sine.range(1400, 1800).fast(3.14))
    .filterWhen(x => x >= wait * 10 && x < (wait * 10 + keep))
  , // Lyrics -----------------------------------------------------------------------------------------------
  n("0").morse("Schön ist es auf der Welt zu sein!")
    .scale("C6:major").scaleTranspose("0 -2 2 0".slow(32)).hpf(1000)
    .sound("square").warmth(1.0).gain(1.0).pan(berlin.slow(2)).adsr("0.05:0.2:0.2:0.0")
    .filterWhen(x => x >= wait * 6 && x < (wait * 6 + keep))
  , // Melody -----------------------------------------------------------------------------------------------
  n("<[0 2 4 6 7 6 4 2]>").scale("[c3:major c3:pentatonic c3:major c3:major]/16")
    .s("supersaw").unison(8).detune(saw.range(0.0, 0.25).slow(16)).spread(1.0)
    .gain(0.09).pan(sine.range(0.3, 0.7).oneMinusValue().slow(16)).adsr("0.05:0.5:0.7:0.1")
    .distort(1.2).warmth(0.5)
    .lpenv(perlin.slow(4).range(0, 3))
    .filterWhen(x => x >= wait * 3 && x < (wait * 4 + keep))
  , // Bass -------------------------------------------------------------------------------------------------
  note("<a1 [f1 c2 e1 [f2 c2]] [a1 [c2 f1] a1 [f1@3 e1]] [c2@2 e2@5 [e1 e1]]>/8").clip(0.75).struct("x!8")
    .gain(0.75).pan(sine.range(0.3, 0.7).slow(16)).adsr("0.02:0.5:0.5:0.2")
    .superimpose(x => x.scaleTranspose("[12 12 7 12 12 12 0 -12]/16").gain(0.65).legato(1.2))
    .s("supersaw").unison(8).detune(saw.range(0.05, 0.4).slow(16))
    .lpf(6 * 440).hpf(80).crush(sine.range(3.5, 12.0).slow(32))
    .filterWhen(x => x >= wait * 0.5 && x < (wait * 3 + keep))
  , // Perc 2 -----------------------------------------------------------------------------------------------
  sound("<[hh hh oh hh] [hh hh ~ hh] [hh hh oh hh] [hh hh ~ cr]>")
    .gain(0.8).pan(0.4).adsr("0.01:1.0:1.0:1.0").fast(2).degrade(0.1)
    .filterWhen(x => x >= wait * 1 && x < (wait * 2 + keep))
  , // Perc 1 -----------------------------------------------------------------------------------------------
  sound("[bd bd bd ~  bd ~ bd ~] [bd bd sd:5 ~  bd ~ bd|sd:5 ~]").slow("[8 8 8 8 8 8 4 [2 4]]/32").fast(2)
    .gain(0.8).pan(0.5).adsr("0.01:0.2:0.5:1.0").degrade(0.01).hpf(60)        
    .filterWhen(x => x >= wait * 0.5 && x < (wait * 1 + keep))
  , // Shore ------------------------------------------------------------------------------------------------
  note("c").fast(8).sound("brown")
    .gain(0.07).pan(perlin.early(1.7).range(0.3, 0.7).slow(21)).adsr("0.2:1.0:1.0:2.5")
    .bandf(perlin.range(440, 440 * 4).segment(16).slow(64)).bandq(sine.range(-0.05, 5.0).slow(12))
  ,
).delay(0.15).delaytime(pure(1/8).div(cps)).delayfeedback(0.33)
  .room(0.05).rsize(5.0) 





   """.trimIndent()

    // https://patorjk.com/software/taag/#p=display&f=BlurVision+ASCII&t=THE+HALO+EFFECT&x=none&v=4&h=4&w=80&we=false
    val aTruthWorthFightingFor = """
import * from "stdlib"
import * from "strudel"

let tp = "[0 -1 -3 -5 -7 -8 -10 -12]/8".slow(64) // <---- transposition ... wait for it ... or change it ...

stack( // Gitarre! ----------------------------------------------------------------------------
  morse("Gitarre!").n("0").scale("c4:chromatic").fast(2).transpose(tp)
    .gain(1.0).distort(0.5).postgain("1.0 0.0!3".slow(64)).hpf(4350).lpf(4450).pan(0.6) //.solo()
  ,// Melody 1 ---------------------------------------------------------------------------------
  n(`<   [0 0 0 7] [0 5 0 2] [0 3 0 5] [0 3 0 0]  [ 0 0 0 7] [0  5 0 8] [0 7 0 5] [ 0 7 0 0]
         [0 0 0 7] [0 5 0 2] [0 3 0 5] [0 3 0 0]  [12 0 0 0] [0 10 0 7] [0 8 7 8] [10 8 7@2]>`)
    .fast(4).scale("C3:chromatic").clip(0.85).hpf(400).lpf(5000).pan(0.45).notchf(1320).transpose(tp)
    .s("supersaw").unison(6).detune(0.02).gain(0.25).distort(saw.range(0.5, 1.0).slow(32)).postgain(0.55) // .solo()
    .adsr("0.01:0.3:0.7:0.05").filterWhen(t => t % 64 > 16)
  , // Melody 2 --------------------------------------------------------------------------------------------------
  n(`<   [0 0 0 7] [0 5 0 2] [0 3 0 5] [0 3 0 0]  [ 0 0 0 7] [0  5 0 8] [0 7 0 5] [ 0 7 0 0]
         [0 0 0 7] [0 5 0 2] [0 3 0 5] [0 3 0 0]  [12 0 0 0] [0 10 0 7] [0 8 7 8] [10 8 7@2]>`)
    .fast(4).scale("C4:chromatic").clip(0.85).hpf(750).lpf(5000).pan(0.35).notchf(1320).transpose(tp)
    .s("supersaw").unison(6).detune(0.02).gain(0.25).distort(saw.range(0.5, 1.0).slow(32)).postgain(0.6) // .solo()
    .adsr("0.01:0.3:0.7:0.03").filterWhen(t => t % 64 > 32)
  , // Rhythm -----------------------------------------------------------------------------------------------------------------
  cat(n(`<[0,7,12]                                [[0,7,12]!3 ~               ~!12]
          [0,7,12]                                [[[8,15,20]@12 [8,15,20]@4] [10,10,17|17|22|22]*8]>`).repeat(2),
      n(`<[0 0 0 0 0 0 0 0 0 0 0 8 8 8 8 7]       [0!9 8 8 5 5 5 5 3] 
          [0!11 5 8 8 [8,15] [7,14]]              [[8,15]!4 [8,15]!3          [10,10|10|17|17|22]!9]>`).repeat(2),
  ).fast(1).scale("C2:chromatic").pan(0.55).hpf(90).lpf(4500).warmth(0.2)
    .s("supersaw").unison(6).detune(0.08).gain(0.5).distort(saw.range(3.5, /*  ---->  */ 11.0 /*  <----  */ ).slow(32)).postgain(0.19)
    .superimpose(x => x.pan(0.65).bandf("800 1150 950 1250|1250|1275|1300".slow(64)).bandq(saw.range(1.0, 5.0).slow(64)).postgain(0.18))    
    .adsr("0.01:0.25:0.5:0.001").clip(1.01).filterWhen(t => t % 64 >= 4).transpose(tp) // .solo()
  , // Noise --------------------------------------------------------------------------------------------------------------
  s("cp cp cp cp").bandf("2400 600 1200 600").gain(0.25) // .solo()
  ,note("a").sound("brown").gain(0.2).crush(8) //.solo()
  , // Drums 1 -----------------------------------------------------------------------------------------------
  cat(s(`<[lt,sd]                                 [[lt,sd]!3 ~                ~!12]
          [lt,sd]                                 [[[mt,sd]@12 [lt]@4]        [mt,sd]]>`).repeat(2),
      s(`<[bd bd] [sd bd] [~ bd] [sd bd]          [~ bd] [sd bd]              [~ bd] [sd bd]
          [bd bd] [sd bd] [~ bd] [sd bd]          [~ bd] [sd bd]              [~ bd] sd>`).fast(8).repeat(4)
  ).adsr("0.01:0.5:0.5:1.0").gain(0.70).hpf(60).filterWhen(t => t % 64 >= 4) // .solo()
  , // Drums 1 -----------------------------------------------------------------------------------------------
  s("<[cr hh!7]!3 [cr hh!3 [hh hh] [hh hh] [cr hh] [oh hh]]>").adsr("0.01:0.3:1.0:1.0").gain("0.95".add(berlin2.range(-0.05, 0.0).segment(8)).slow(4)) // .solo()
).room(0.02).rsize(3.0) /*

  

░▒▓████████▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓████████▓▒░    ░▒▓█▓▒░░▒▓█▓▒░░▒▓██████▓▒░░▒▓█▓▒░      ░▒▓██████▓▒░     ░▒▓████████▓▒░▒▓████████▓▒░▒▓████████▓▒░▒▓████████▓▒░▒▓██████▓▒░▒▓████████▓▒░ 
   ░▒▓█▓▒░   ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░           ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░    ░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░ ░▒▓█▓▒░     
   ░▒▓█▓▒░   ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░           ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░    ░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓█▓▒░     ░▒▓█▓▒░        ░▒▓█▓▒░     
   ░▒▓█▓▒░   ░▒▓████████▓▒░▒▓██████▓▒░      ░▒▓████████▓▒░▒▓████████▓▒░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░    ░▒▓██████▓▒░ ░▒▓██████▓▒░ ░▒▓██████▓▒░ ░▒▓██████▓▒░░▒▓█▓▒░        ░▒▓█▓▒░     
   ░▒▓█▓▒░   ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░           ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░    ░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓█▓▒░     ░▒▓█▓▒░        ░▒▓█▓▒░     
   ░▒▓█▓▒░   ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░           ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░    ░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓█▓▒░     ░▒▓█▓▒░░▒▓█▓▒░ ░▒▓█▓▒░     
   ░▒▓█▓▒░   ░▒▓█▓▒░░▒▓█▓▒░▒▓████████▓▒░    ░▒▓█▓▒░░▒▓█▓▒░▒▓█▓▒░░▒▓█▓▒░▒▓████████▓▒░▒▓██████▓▒░     ░▒▓████████▓▒░▒▓█▓▒░      ░▒▓█▓▒░      ░▒▓████████▓▒░▒▓██████▓▒░  ░▒▓█▓▒░     




                                             https://open.spotify.com/intl-de/track/58Hx7vKWjxuQyZ9XgUh3Wl?si=9c254ec279fe47f3





*/ 
   """.trimIndent()

    val tetrisOriginal = """
                stack(
                    note(`<
                        [e5 [b4 c5] d5 [c5 b4]]
                        [a4 [a4 c5] e5 [d5 c5]]
                        [b4 [~ c5] d5 e5]
                        [c5 a4 a4 ~]
                        [[~ d5] [~ f5] a5 [g5 f5]]
                        [e5 [~ c5] e5 [d5 c5]]
                        [b4 [b4 c5] d5 e5]
                        [c5 a4 a4 ~]
                    >`).sound("triangle").orbit(0)
                    .gain("0.3")
                    .fast(1)
                    .room(0.01).rsize(3.0)
                    .delay("0.25").delaytime(0.25).delayfeedback(0.75),

                    note(`<
                        [[e2 e3]*4]
                        [[a2 a3]*4]
                        [[g#2 g#3]*2 [e2 e3]*2]
                        [a2 a3 a2 a3 a2 a3 b1 c2]
                        [[d2 d3]*4]
                        [[c2 c3]*4]
                        [[b1 b2]*2 [e2 e3]*2]
                        [[a1 a2]*4]
                    >`).sound("supersaw").orbit(1)
                    .pan(0.6).gain(0.6)
                    .room(0.01).rsize(3.0),

                    sound("bd hh sd hh").orbit(2)
                    .pan(-0.7).gain(0.9)
                    .room(0.01).rsize(3.0)
                    .delay("0.0 0.0 0.5 0.0").delaytime(0.25).delayfeedback(0.75)
                    .fast(2),
                )

            """.trimIndent()

    val tetrisRemix = """
                stack(
                    note(`<
                        [e5 [b4 c5] d5 [c5 b4]]
                        [a4 [a4 c5] e5 [d5 c5]]
                        [b4 [~ c5] d5 e5]
                        [c5 a4 a4 ~]
                        [[~ d5] [~ f5] a5 [g5 f5]]
                        [e5 [~ c5] e5 [d5 c5]]
                        [b4 [b4 c5] d5 e5]
                        [c5 a4 a4 ~]
                    >`).sound("triangle").orbit(0)
                    .gain("0.3")
                    .fast(0.5)
                    .room(0.01).rsize(3.0)
                    .delay("0.25").delaytime(0.25).delayfeedback(0.75),

                    note(`<
                        [[e2 e3]*4]
                        [[a2 a3]*4]
                        [[g#2 g#3]*2 [e2 e3]*2]
                        [a2 a3 a2 a3 a2 a3 b1 c2]
                        [[d2 d3]*4]
                        [[c2 c3]*4]
                        [[b1 b2]*2 [e2 e3]*2]
                        [[a1 a2]*4]
                    >`).sound("supersaw").orbit(1)
                    .pan(0.6).gain(0.6)
                    .room(0.01).rsize(3.0),

                    sound("bd hh sd hh").orbit(2)
                    .pan(-0.7).gain(0.8)
                    .room(0.01).rsize(3.0)
                    .delay("0.0 0.0 0.5 0.0").delaytime(0.25).delayfeedback(0.75)
                    .fast(2),
                )
            """.trimIndent()

    val c4Minor = """
                n("0 1 2 3 4 5 6 7").scale("C4:minor")
            """.trimIndent()

    val numberNotes = """
                note("40 42 44 46")
            """.trimIndent()

    val crackle = """
                s("crackle*4")
                .density("<0.01 0.04 0.2 0.5>").slow(2).gain(0.1)
            """.trimIndent()

    val dust = """
                s("dust*4")
                .density("<0.01 0.04 0.2 0.5>").slow(2).gain(0.01)
            """.trimIndent()

    val impulse = """note("c6").sound("impulse").gain(0.05)""".trimIndent()

    val whiteNoise = """
                s("white").gain("<0.01 0.04 0.2 0.5>")
                """.trimIndent()

    val brownNoise = """
                s("brown").gain("<0.01 0.04 0.2 0.5>")
                """.trimIndent()

    val pinkNoise = """
                s("pink").gain("<0.01 0.04 0.2 0.5>")
                """.trimIndent()

    val supersaw = """
                note("<[c2 c3]*4 [bb1 bb2]*4 [f2 f3]*4 [eb2 eb3]*4>")
                  .sound("sine")
//                  .detune("<.3 .3 .3 1.0>")
                  .gain(0.25)
//                  .hpf(100)
//                  .spread(".8")
//                  .unison("2 7")
            """.trimIndent()

    val polyphone = """
                note("c!2 [eb,<g a bb a>]")
            """.trimIndent()

    val simpleDrumsMultipleSounds = """
        sound("bd hh sd oh bd:1 hh:1 sd:1 oh:1 bd:2 hh:2 sd:2 oh:2 bd:3 hh:3 sd:3 oh:3")
        .gain(0.8).slow(2)
    """.trimIndent()

    val simpleDrumsDelay = """
        sound("bd hh sd oh")
        .gain(0.8)
        .delay("0.0 0.0 0.5 0.0").delaytime(0.25).delayfeedback(0.5)
        .pan(sine.slow(8))
        .fast(2)
    """.trimIndent()

    val simpleDrumsReverb = """
        sound("bd hh sd oh")
        .gain(0.8)
        .room(0.01).rsize(3.0)
        .pan(sine.slow(8))
        .fast(2)
    """.trimIndent()

    /**
     * This produces each drum sound twice.
     * Why? Because of the lpf() producing twice as many events as the sound()
     * Therefore the drum sounds re schedules twice ...
     */
    val doubleSampleBug = """
            stack(
              //n("0 1 2 3 4 5 6 7").scale("C4:minor"),
              sound("bd hh sd oh")
              .lpf("100 200 300 400 500 600 700 800")
              .fast(2)
              .gain(1.0),
              
            )
        """.trimIndent()

    val snareScale = """
            n("0 1 2 3 4 5 6 7").scale("c3:major").sound("sd")
        """.trimIndent()

    val piano = """
        note("c e g e").sound("piano")
    """.trimIndent()

    val pianoDistorted = """
        note("c e g e").sound("piano").distort(2)
    """.trimIndent()

    val asdrTest = """
            note("c3")
              .s("sine")
              .attack(0.5)
              .decay(0.2)
              .sustain(0.3)
              .release(1.0)            
        """.trimIndent()

    /**
     * The "Off-Beat" (Ping Pong feel)
     * This puts the echo exactly in the middle of the drum hits (like adding an 8th note between quarter notes). It doubles the speed of the rhythm.
     */
    val delayOffBeatDrums = """
        sound("bd hh sd oh")
         .gain(1.0)
//         .delay("0.0 0.0 0.5 0.0")
         .delay(0.5)
         .delaytime(0.25)
         .delayfeedback(0.5)
        """.trimIndent()

    /**
     * The "Dub" (Triplets / Poly-rhythm)
     * This time (0.375s) doesn't line up perfectly with the 0.5s grid, creating a rolling, funky "Dub Techno" feel.
     */
    val delayDubTripletsDrums = """
        sound("bd hh sd oh")
          .delay(0.6)
          .delaytime(0.375)
          // High feedback for long tails
          .delayfeedback(0.7)         
    """.trimIndent()

    /**
     * "Slapback" (Room feel)
     * Very short time. It just makes the drums sound "metallic" or like they are in a small tiled room.
     */
    val delaySlapBackDrums = """
         sound("bd hh sd oh")
          .delay(0.4)
           // 50ms
          .delaytime(0.05)    
          // Low feedback
          .delayfeedback(0.2) 
     """.trimIndent()

    val twoOrbits = """
        stack(
          // Snare only delay on the drums
          sound("bd hh sd oh").gain(0.7).delay("0.0 0.0 0.5 0.0").delaytime(0.25).delayfeedback(0.5).orbit(0),
          // Full delay on the melody
          note("c ~ d ~ e ~ f ~").delay("0.0").delaytime(0.25).orbit(1),
        )
    """.trimIndent()

    val vibratoTestOne = """
        n("1 3 5 7").scale("C4:minor")
        .sound("piano")
        .gain(1.0)
        .slow(4) 
        .vib(8)
        .vmod(0.5)        
    """.trimIndent()

    val crushTest = """
        n("1 3 5 7").scale("C4:minor")
        .sound("sine")
        .gain(1.0)
        .slow(4) 
        .crush("4 3 2 1")      
        
    """.trimIndent()

    val coarseTest = """
         sound("[bd hh sd oh]*4")
           .delay(0.4)
           .delaytime(0.05)    
           .delayfeedback(0.2)
           .slow(2)
           .coarse("1 2 4 8")
     """.trimIndent()

    val glisandoTest = """
        n("1 3 5 7").scale("C4:minor")
        .sound("sine")
        .gain(1.0)
        .slow(4) 
        .accelerate(1)
        .vib(8)
        .vmod(0.5)        
    """.trimIndent()

    val glisandoTest2 = """
        stack(        
          n("1 3 5 7 8 10 12 14").scale("C4:minor")
           .adsr("0.1:0.5:0.2:0.5").gain(0.5)
           .orbit(0).room(0.01).rsize(10.0).sound("sine")
           .slow(8).accelerate(3 / 12),
         n("8 10 12 14 1 3 5 7").scale("C4:minor")
           .adsr("0.1:0.5:0.2:0.5").gain(0.5)
           .orbit(2).room(0.01).rsize(10.0).sound("sine")
           .slow(8).accelerate(3 / 12),
        )
    """.trimIndent()

    val glisandoTest3 = """
        seq("1:3 3:5 5:7 7:1").scale("C4:minor")
        .n()
        .sound("sine")
        .gain(1.0)
        .slow(4) 
        .accelerate(1)
        .vib(8)
        .vmod(0.5)        
    """.trimIndent()

    val bandF = """
        n("0 1 2 3 4 5 6 7").scale("C4:minor")
         .bandf("500 1000 200").resonance(5)
         .slow(4)         
    """.trimIndent()

    // TODO: does not work, how to invoke it?
    val notchF = """
        // or "noise"
        s("white") 
            .notchf("100 500 2000 5000")
            .resonance(20)
            .gain(0.5)
    """.trimIndent()

    val legatoLong = """
        note("c3 e3 g3 a#3").slow(4).s("sine").clip(3.0)
    """.trimIndent()

    val legatoShort = """
        note("c3 e3 g3 a#3").slow(4).s("sine").clip(0.5)
    """.trimIndent()

    val soundFont_gm_xylophone = """
        note("c3 e3 g3 a#3").s("<gm_recorder gm_xylophone>").slow(4)
    """.trimIndent()

    val euclidean_3_8 = """
        stack(        
            note("[a b c d e f g]/8(3,8,1)").release(0.2),
//            note("[a b](1,2)").release(0.2),
            sound("hh").fast(2)
        )
    """.trimIndent()

    val active = strangerThingsNetflix
}
