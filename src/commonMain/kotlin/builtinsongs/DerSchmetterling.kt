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
let feel = 2.0   // 0.0 .. mechanical | 10.0 .. old vinyl                                                                     //    //
                                                                                                                             //      //
                                                                                                                            //        //
stack(                                                                                                         //////////////          //////////////
  // Lead                                                                                                        //                              //
  n("<[-7 0 2 4] [-7 0 4 2] [-5 -1 2 4] [-6 -1 3 1]>*2")                                                           //          DISCO!          //
    .orbit(0).scale("e4:minor").sound("supersaw").unison(9).detune(0.05).analog(feel)                                //       FOREVER!       //
    .hpf(1000).lpf(2300).lpe(2.0).lpadsr("0.02:0.25:0.40:0.11").warmth(0.1)                                            //                  //
    .gain(0.70).distort("0.01:gentle:4").postgain(0.60) // . solo()                                                     //       //      //
    .adsr("0.01:0.25:0.65:0.11").clip(0.95) //. mute()                                                                 //     //    //    //
    .release("<0.105!16 0.4!16 0.110!16 0.325!16 0.16!16 0.35!16 0.09!16 0.4!16 0.095!16 0.5!16>")                    //   //          //  //
    .superimpose(                                                                                                    // //               // //
      x => x.shuffle("<1!64 0!16 1!1 4/8!14 1!33>").seed(sinOfNight.add(1).mul(24 * 60 * 15))                       //                       //
        .superimpose(x => x.transpose(12).detune(0.10).velocity("<0!32 0.20!32>").pan(0.2).early(0.001))
        .superimpose(x => x.transpose(12).detune(0.15).velocity("<0!32 0.20!32>").pan(0.8).late(0.001))
    ).phaser(1/8).phaserdepth("<0.05!64 0.35!16 0.05!48>").phasersweep(1000).phasercenter(1800).mute("<0!192 1!32>"),
  // Pad
  n(`<[7 4 2 <4 -1 4 3> [0 -1 -3 -1] [0 -3] -2 <[-1 4@3] [5 6@3] [1 2@3] [2 5@3]>]!4 
      [[4 2] [-1 -3] 0 [2 [2 6@3]]]!2 [[0 -3] [2 -3] 0 <[4 1] [-2 0]>] [<0 4> [-5 -10] -7 [-2 <3 -6>]]>/4`)
    .struct("<[x!16]!7 [x!24]!1 [x!16]!16>").velocity("1.02 0.95!3 0.98 0.95!3".fast(2)).analog(feel)
    .scale("<e2:minor!48 e3:minor!16>").sound("supersaw").unison(5).detune(0.12)
    .lpf(900).lpe(2.5).hpf(300)
    .distort("1.2:tube:4").distort(sine.range(0.4, 0.8).slow(10*32))
    .adsr("0.01:0.15:0.66:0.105").lpadsr("0.025:0.225:0.66:0.15").clip(0.50).crush(saw.range(8, 4).slow(32)) // . solo()
    .gain(0.3).postgain(0.75).orbit(1).pan(0.67)
    .superimpose(
      x => x.pan(0.33).late(0.0015),
      x => x.postgain(0.55).lpadsr("0.02:0.3:0.5:0.15").hpf(600).lpf(3000).lpe(0.45).scaleTranspose(7).postgain(0.90).pan(0.9)
            .superimpose(p => p.pan(0.1).early(0.0025))
    )
  ,
  // Bass
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2).velocity("1.02 0.95!3 0.98 0.95!3".fast(2))
    .scale("<e2:minor!28 e3:minor!4>").analog(feel)
    .sound("supersaw").unison(5).detune(0.15) 
    .lpf(600).lpe(3.0).hpf(85).distort("1.3:tube:4")
    .adsr("0.015:0.15:0.66:0.10").lpadsr("0.025:0.25:0.66:0.15").clip(0.50)  // . solo()
    .gain(0.3).postgain(0.6).orbit(2).pan(0.33).mute("<0!128 1!16 0!16>")
    .superimpose(
      x => x.pan(0.67).late(0.002),
      x => x.postgain(0.4).pan(0.2).hpf(400).lpf(2800).lpe(0.45).adsr("0.025:0.15:0.66:0.13").scaleTranspose("<4!15 [2 4]>")
            .superimpose(p => p.pan(0.8).late(0.002))
    )
  ,
  // Drums
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd ~  bd ~ ]!24 [bd bd bd bd]!24>").mute("<0!128 1!32>")// . solo()
    .early(0.002).orbit(3).gain(0.95).hpf(120).lpf(6000).adsr("0.01:0.15:0.5:0.1"),
  sound("<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd ~  sd]!24 [~  sd ~  sd]!24>").mute("<0!128 1!32>")// . solo()
    .early(0.002).orbit(3).gain(0.85).hpf(190).lpf(10000).adsr("0.006:0.15:0.5:0.1").superimpose(bandf(190).gain(0.35)),
  sound("<[hh hh oh hh]!48 [cr hh cr hh]!16 [0 hh 0 hh]!16>").fast(2).mute("<0!128 1!32>")  // . solo()
    .late(0.004).orbit(4).gain(0.75).hpf(800).lpf(8000).adsr("0.01:0.2:0.5:0.2")
  // Master
).compressor("-6:2:10:0.02:0.25").engine("pedal").room("0.02:5")


// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW




// Written by: peekandpoke

// Epilepsy Warning: Do not click the oscilloscope!











            """,
)
