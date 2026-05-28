@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val derSchmetterlingSong = Song(
    id = "${BuiltInSongs.PREFIX}-der-schmetterling",
    title = "Der Schmetterling",
    rpm = 32.0,
    icon = "bug",
    code = """
import * from "stdlib"                                                                                                           //
import * from "sprudel"                                                                                                         ////
                                                                                                                               //  //
let feel = 2.5   // 0.0 .. mechanical | 10.0 .. old vinyl                                                                     //    //
let snd  = 900   // sine.range(125, 300).slow(5).add(sine.range(125, 300).slow(13))   // 100 .. 2000                         //      //
                                                                                                                            //        //
stack(                                                                                                         //////////////          //////////////
  // Lead                                                                                                        //                              //
  n("<[-7 0 2 4] [-7 0 4 2] [-5 -1 2 4] [-6 -1 3 1]>*2")                                                           //          DISCO!          //
    .orbit(0).scale("e4:minor").sound("supersaw").unison(9).detune(0.05).analog(feel)                                //       FOREVER!       //
    .hpf(1000).lpf(2600).lpq(1.2).lpe(1.0).lpadsr("0.03:0.30:0.40:0.11").warmth(0.1)                                   //                  //
    .gain(0.70).distort("0.01:gentle:4").postgain(0.53) // . solo()                                                     //       //      //
    .adsr("0.01:0.2:0.65:0.11").clip(0.95) //. mute()                                                                  //     //    //    //
    .release("<0.105!16 0.4!16 0.110!16 0.325!16 0.16!16 0.35!16 0.09!16 0.4!16 0.095!16 0.5!16>")                    //   //          //  //
    .superimpose(                                                                                                    // //               // //
      x => x.shuffle("<1!64 0!16 1!1 4/8!14 1!33>").seed(sinOfNight.add(1).mul(24 * 60 * 15))                       //                       //
        .superimpose(transpose(12).detune(0.10).velocity("<0!32 0.20!32>").lpf(1600).pan(0.2).early(0.001))
        .superimpose(transpose(12).detune(0.15).velocity("<0!32 0.20!32>").lpf(1800).pan(0.8).late(0.001))
    ).phaser(1/8).phaserdepth("<0.05!64 0.35!16 0.1!48>").phasersweep(1000).phasercenter(1800).mute("<0!192 1!32>"),
  // Pad
  n(`<[7 4 2 <4 -1 4 3> [0 -1 -3 -1] [0 -3] -2 <[-1 4@3] [5 6@3] [1 2@3] [2 5@3]>]!4 
      [[4 2] [-1 -3] 0 [2 [2 6@3]]]!2 [[0 -3] [2 4] 0 <[4 1] [-2 0]>] [<0 4> [-5 -10] -7 [-2 <3 -4>]]>/4`)
    .struct("<[x!16]!7 [x!24]!1 [x!16]!16>").velocity("1.02 0.95!3 0.98 0.95!3".fast(2)).analog(feel)
    .scale("<e2:minor!48 e3:minor!16>").sound("supersaw").unison(5).detune(0.09)
    .lpf(400).lpe(3.0).hpf("<240!48 360!16>")
    .distort("1.2:tube:4").distort(sine.range(0.3, 0.6).slow(10*32))
    .adsr("0.01:0.15:0.66:0.13").lpadsr("0.025:0.25:0.66:0.15").clip(0.50).crush(saw.range(8, 4).slow(32)) // . solo()
    .gain(0.3).postgain(0.9).orbit(1).pan(0.4).late(0.001)
    .superimpose(
      x => x.lpadsr("0.023:0.3:0.5:0.15").lpf(2800).scaleTranspose(7).postgain(0.9).pan(0.2).hpf(400).lpe(0.5).superimpose(pan(0.8).early(0.001))
    )
  ,
  // Bass
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2).velocity("1.02 0.95!3 0.98 0.95!3".fast(2))
    .scale("<e2:minor!28 e3:minor!4>").analog(feel)
    .sound("supersaw").unison(5).detune(0.11) // .notchf(snd)
    .lpf(300).lpe(3.0).hpf(80).distort("1.2:tube:4")
    .adsr("0.01:0.15:0.66:0.13").lpadsr("0.01:0.03:0.66:0.15").clip(0.50)  // . solo()
    .gain(0.3).postgain(0.75).orbit(2).pan(0.5).mute("<0!128 1!16 0!16>")
    .superimpose(
      x => x.postgain(0.55).pan(0.33).hpf(280).lpf(2500).lpe(0.5).adsr("0.025:0.15:0.66:0.13").lpadsr("0.021:0.02:0.8:0.15")
            .scaleTranspose("<4!15 [2 4]>").superimpose(pan(0.66).late(0.001))
    )
  ,
  // Drums
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd sd bd sd]!24 [bd [bd,sd] bd [bd,sd]]!24>").mute("<0!128 1!32>")// . solo()
    .early(0.002).orbit(3).gain(0.95).hpf(80).lpf("2200:1:1.5").adsr("0.009:0.2:0.5:0.1"),
  sound("<[hh hh oh hh]!48 [cr hh cr hh]!16 [0 hh 0 hh]!16>").fast(2).mute("<0!128 1!32>")  // . solo()
    .late(0.004).orbit(4).gain(0.90).hpf(800).lpf(5000).adsr("0.01:0.2:0.5:0.2")
  // Master
).compressor("-12:3:8:0.01:0.15").engine("pedal").room("0.02:5")


// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW




// Written by: peekandpoke

// Epilepsy Warning: Do not click the oscilloscope!











            """,
)
