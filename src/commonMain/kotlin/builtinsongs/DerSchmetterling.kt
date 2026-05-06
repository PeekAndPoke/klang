@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val derSchmetterlingSong = Song(
    id = "${BuiltInSongs.PREFIX}-der-schmetterling",
    title = "Der Schmetterling",
    rpm = 32.5,
    icon = "bug",
    code = """
import * from "stdlib"                                                                                                      //
import * from "sprudel"                                                                                                    ////
                                                                                                                          //  //
let feel = 3.0   // 0.0 .. mechanical | 10.0 .. old vinyl                                                                //    //
let snd  = 550   // 100 .. 2000                                                                                         //      //
                                                                                                                       //        //
stack(                                                                                                    //////////////          //////////////
  // Lead                                                                                                   //                              //
  n("<[-7 0 2 4] [-7 0 4 2] [-5 -1 2 4] [-6 -1 3 1]>*2")                                                      //          DISCO!          //
    .orbit(0).scale("e4:minor").sound("supersaw").unison(2).detune(0.05)                                        //       FOREVER!       //
    .hpf(431).lpf(5000).warmth(0.2)                                                                               //                  //
    .gain(0.32).distort("0.8:gentle:2").postgain(0.1) // . solo()                                                  //       //      //
    .adsr("0.01:0.2:0.5:0.1").clip(0.8)                                                                           //     //    //    //
    .release("<0.105!16 0.275!16 0.110!16 0.3!16 0.06!16 0.25!16 0.09!16 0.4!16 0.075!16 0.5!16>")               //   //          //  //
    .superimpose(                                                                                               // //               // //
      x => x.shuffle("<1!64 0!16 1!1 4/8!14 1!33>").seed(sinOfNight.add(1).mul(24 * 60 * 15))                  //                       //
        .superimpose(transpose(12).detune(0.07).velocity("<0!32 0.35!32>").lpf(5500).pan(0.7))
        .superimpose(transpose(24).detune(0.10).velocity("<0!96 0.20!32>").lpf(6000).pan(0.33))
    ).phaser(1/8).phaserdepth("<0.0!64 0.6!16 0.0!48>").phasersweep(1000).phasercenter(1500),
  // Pad
  n("<[0 0 2 4 0 0 -2 -1]!4 [0 [2 4] 0 [2 [2 -1@3]]]!2 [0 [6 4] 0 <[2 3] [-2 -0]>] [4 [2 1] 0 [-2 <-1 -4>]]>/4")
    .struct("<[x!16]!7 [x!24]!1 [x!16]!16>").velocity("1.02 0.95!3 0.98 0.95!3".fast(2))
    .scale("<e2:minor!48 e3:minor!16>").sound("supersaw").unison(3).detune(0.1)
    .notchf(snd).notchq(0.5).lpf("<1400!48 2400!16>").hpf("<140!48 240!16>").warmth(saw.range(0.3, 0.1).slow(5*32))
    .distort("0.3:gentle:2").distort(sine.range(0.15, 0.3).slow(10*32))
    .adsr("0.01:0.2:0.5:0.035").clip(0.75).crush(saw.range(8, 4).slow(32))  // . solo()
    .gain(0.35).orbit(1).pan(0.33),
  // Bass
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2).velocity("1.02 0.95!3 0.98 0.95!3".fast(2))
    .scale("e2:minor").sound("saw").warmth(saw.range(0.3, 0.1).slow(5*32))
    .notchf(snd).notchq(0.5).lpf("1000").hpf(90).distort("0.2.75:gentle:2")
    .adsr("0.01:0.2:0.5:0.045").clip(0.75)  // . solo()
    .gain(0.42).orbit(2).pan(0.66),
  // Drums
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd sd bd sd]!24 [bd [bd,sd] bd [bd,sd]]!8>") // . solo()
    .orbit(3).gain(0.8).crush(10).crushos(2).hpf(60).lpf("2400:1").lpe("<2!128 2.3!128 2.7!128>").adsr("0.01:0.2:0.5:0.1"),
  sound("<[hh hh oh hh]!24 [cr hh cr hh]!16>") //. solo()
    .orbit(4).gain(0.4).fast(2).crush(8).crushos(2).hpf(400).lpf("3500:1:2").adsr("0.01:0:1.0:0.1")
  // Master
).compressor("-10:2:10:0.02:0.25").analog(feel).engine("pedal").room("0.01:5")


// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW




// Written by: peekandpoke

// Epilspsy Warning: Do not click the oscilloscope!









            """,
)
