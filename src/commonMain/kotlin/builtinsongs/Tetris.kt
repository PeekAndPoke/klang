@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val tetrisSong = Song(
    id = "${BuiltInSongs.PREFIX}-synthris",
    title = "Stein um Stein",
    rpm = 39.5,
    icon = "gamepad",
    code = """
import * from "stdlib"                                              ////////                   ////////
import * from "sprudel"                                              ////////                   ////////
                                                                      ////////                   ////////
stack(                                                                 ////////                   ////////
  note(`<
    [e5 [b4 c5] d5 [c5 b4]]    [a4 [a4 c5] e5 [d5 c5]]           //////// //////// ////////          ////////                  ////////
    [b4 [~ c5] d5 e5]          [c5 a4 a4 ~]                       //////// //////// ////////          ////////                  ////////
                                                                   //////// //////// ////////          ////////                  ////////
    [[~ d5] [~ f5] a5 [g5 f5]] [e5 [~ c5] e5 [d5 c5]]               //////// //////// ////////          ////////                  ////////
    [b4 [b4 c5] d5 e5]         [c5 a4 a4 ~]
  >`)                                                                                                     ////////          //////// ////////
    .sound("tri").clip(0.33).hpf(500).lpf("5000").warmth(0.1)                                              ////////          //////// ////////
    .adsr("0.02:0.2:0.5:0.1").superimpose(x => x.transpose("<0 12 0 -12>/8"))                               ////////          //////// ////////
    .orbit(0).gain(0.115).pan(0.33).superimpose(pan(0.66))                                                   ////////          //////// ////////
    .delay(0.2).delaytime(pure(1/8).div(cps)).delayfeedback(0.2)
    .filterWhen(x => x >= 16) // .solo()                                                                       ////////          ////////
  ,                                                                                                             ////////          ////////
  note(`<                                                                                                        ////////          ////////
    [[e2 e3]*4]                   [[a2 a3]*4]                                                                     ////////          ////////
    [[g#2 g#3]*2 [e2 e3]*2]       [a3 a2 a2 a1 a1 a2 [a2 a3] [a4 a5|a5|a5|e5]]

    [[d2 d3]*4]                   [[c2 c3]*4]                                                                        //////// //////// ////////
    [[b1 b2 b1 b2] [e2 e3 e2 e3]] [a3 a2 a2 a1 a1 [a2 e2] [a5|a5|a5|e5 a4] [a2 a3]]                                   //////// //////// ////////
  >`)                                                                                                                  //////// //////// ////////
    .sound("supersaw").spread(0.5).unison(sine.range(8, 16).slow(32)).warmth(0.1)                                       //////// //////// ////////
    .orbit(1).gain(0.65).adsr("0.005:0.2:0.7:0.15").pan(0.3)
    .superimpose(transpose("<0 12 0 -12>/8").pan(0.7)).phaser(1/13).phaserdepth(0.20).phasercenter(3500).phasersweep(1000)                 ////////
    .detune(sine.range(0.05, 0.45).early(1.5).slow(24)).hpf(120).lpf(4200)                                                                  ////////
    .filterWhen(x => x > 31.4 && x % 64 > 15.4) // .solo()                                                                                   ////////
  ,                                                                                                                                           ////////
  note("<[[e2 b1]!2 [a1 c2]!2] [[g#1 b1] [e1 b1] [a1 e1] [a1 g#1 b1 c2]] [[d2 d2]!4 [c2 c2]!4] [[b1]!q [g#1]!2 [a1]!3 ~]>")
    .slow(2).orbit(2).clip(0.5).sound("tri").gain(0.8).warmth(0.5).distort("0.3:soft:2").postgain(0.4)
    .adsr("0.005:0.2:0.3:0.075").tremolo("0.1:8::0:0").hpf(60).lpf(3000).engine("pedal") //  .solo()
    .filterWhen(x => x > 31.4 && x % 128 > 15.4)
  , sound(`<
    [[bd:2,cr,cr] hh sd      hh     ]  [bd      hh sd      oh]  [bd      hh sd hh]       [bd hh      sd            hh        ]
    [[bd,hh]      hh sd      hh     ]  [bd      hh sd      oh]  [bd      hh sd hh]       [bd hh      [mt mt,sd]    [ht ht,oh]]
    [[bd:2,cr]    hh sd      hh     ]  [bd      hh sd      oh]  [bd      hh sd hh]       [bd hh      sd            hh        ]
    [[bd,hh]      hh [sd,hh] oh     ]  [bd      hh sd      oh]  [bd      hh sd hh]       [bd hh      [sd sd]       [sd sd]   ]

    [[bd:2,cr,cr] hh sd      sd     ]  [bd      hh sd      oh]  [bd      hh sd hh]       [bd hh      sd            hh        ]
    [[bd,hh]      hh sd      [hh sd]]  [bd      hh sd      oh]  [bd      hh sd hh]       [bd hh      [lt lt,sd sd] ~         ]
    [[bd:2,cr]    hh sd      [sd,hh]]  [bd      hh sd:8    oh]  [bd      hh sd hh]       [bd hh      sd            [bd,oh]   ]
    [[bd,cr]      hh [sd,hh] cr     ]  [[bd,cr] hh [bd,cr] hh]  [[sd,oh] bd sd [bd,hh]]  [sd [bd,hh] [bd bd]       [bd bd,hh]]
  >`)
    .orbit(3).gain(0.9).pan(0.6).adsr("0.007:0.2:0.5:1.0").hpf(80).lpf("6000")
    .fast(2)  // .solo()
  ,
).room(0.1).rsize(5.0).compressor("-10:2:10:0.02:0.25").analog(0.5)





        """,
)
