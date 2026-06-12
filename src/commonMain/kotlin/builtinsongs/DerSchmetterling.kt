@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val derSchmetterlingSong = Song(
    id = "${BuiltInSongs.PREFIX}-der-schmetterling",
    title = "Der Schmetterling",
    rpm = 34.0,
    icon = "bug",
    code = """
import * from "stdlib"                                                                                                           //
import * from "sprudel"                                                                                                         ////
                                                                                                                               //  //
let feel = 2.5   // 0.0 .. mechanical | 10.0 .. old vinyl                                                                     //    //
                                                                                                                             //      //
                                                                                                                            //        //
stack(                                                                                                         //////////////          //////////////
  // Lead                                                                                                        //                              //
  n(`<[-7 0 2 4] [-7 0 4 [2 6]|[4 2]|2|2|2] [-5 -1 2 4] [-6 -1 [4 3]|5|3|3|3 [1 -1]|1|1|1|1]>*2`)                  //          DISCO!          //
    .orbit(0).scale("<e4:minor!48 e5:minor!16>").sound("superramp").unison(11).detune(0.03).analog(feel)             //       FOREVER!       //
    .hpf(1300).lpf(1550).lpe(berlin.range(1.6, 1.8).fast(4)).lpq(1.8).lpadsr("0.001:1.0:0.2:0.01")                     //                  //
    .gain(1.00).distort("0.350:tube:4").postgain("<0.775!48 0.350!16>") // . solo()                                     //       //      //
    .adsr("0.01:1.5:0.0:0.01").adsrCurves("exp:exp:exp").clip(0.8)  // . mute()                                        //     //    //    //
    .release("<0.20!16 0.35!16>").vibrato(5).vibmod(0.07)                                                             //   //          //  //
    .phaser(1/8).phaserdepth(0.10).phasersweep(500).phasercenter(1000)                                               // //               // //
    .shuffle("<1!64 0!16 1!1 4/8!14 1!33>").coarse(2).coarseos(8)                                                   //                       //
    .superimpose(x => x.transpose(12).detune(0.10).velocity("<0!32 0.15!32>").pan(0.3),
                 x => x.transpose(12).detune(0.15).velocity("<0!32 0.15!32>").pan(0.7))
    .mute("<1!32 0!256>").engine("pedal"),
  // Guitar 1
  n(`<[7 4 2 <-1 4 1 3> [0 -1 -3 -1] [0 -3] -2 <[-1 5@3] [5 6@3] [4 9@3] [4 3@3]>]!4
      [[4 2] [-1 -3] 0 [2 [2 6@3]]]!2 [[0 -3] [-1 -3] 0 <[4 6] 0>] [<7 [[7 4 6 4]!4]> [-5 -6] -7 [-2 <3 -1>]]>/4`) // . solo()
    .orbit(1).scale("<e3:minor!48 e4:minor!16 e3:minor!48 e4:minor!16>").struct("<[x!16]!7 [x!24]!1 [x!16]!16>") //  .mute()
    .velocity("1.00 0.95!3 0.98 0.95!3".fast(4)).analog(feel).vibrato(3).vibmod(0.04)
    .sound("supersaw").unison(9).detune(0.08).gain(0.8).postgain(0.30).distort("1.0:tube:4").distort(1.0).vibrato(4).vibmod(0.02)    
    .clip("<0.91!31 0.80 0.91!31 0.70>".fast(2)).adsr("0.005:1.2:0.1:0.01").adsrCurves("exp:exp:exp").lpadsr("0.005:1.0:0.4:0.005")    
    .hpf("<320!48 440!16 320!48 440!16>").lpf(saw.range(1,0).pow(2.0).mul(650).add(1000).slow(4)).lpe(3.0).lpq(1.25)
    .coarse(2).coarseos(2).pan(0.25).superimpose(pan(0.75))
    .engine("pedal").body("wood").bodyMix(0.4)
  ,
  // Guitar 2
  n("<0 0 2 4 0 0 -2 -1>") //   . solo()
    .orbit(2).scale("<e2:minor>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2)
    .velocity("1.00 0.9!3 0.95 0.90!3".fast(4)).analog(feel)
    .sound("supersaw").unison(5).detune(0.07).gain(0.8).postgain(0.26).distort("1:tube:2").distort(1.0)
    .clip("<0.91!31 0.80 0.91!31 0.70>".fast(2)).adsr("0.007:1.2:0.0:0.01").adsrCurves("exp:exp:exp").lpadsr("0.007:1.0:0.3:0.005")    
    .hpf(170).hpq(1.0).lpf(1025).lpe(3.0).lpq(1.25)
    .coarse(2).coarseos(2).pan(0.35).superimpose(
      x => x.pan(0.65),
      x => x.orbit(3).postgain(0.25).hpf(190).lpf(1025) // .lpe(1.0)
            .scaleTranspose("<4!7 [2 [3 4@3]]!1 4!7 [-3 [-4 -3@3]]>").pan(0.3).superimpose(pan(0.7))
    ).mute("<0!128 1!16 0!16>").engine("pedal").body("wood").bodyMix(0.3)
  , // Bass
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!1]!32 [x@3 x]!32 [x!4]!64 [x [x!2] x [x!2]]!32>").fast(2).velocity("1.00 0.93!3 0.95 0.93!3".fast(2)) // . mute()
    .scale("<e2:minor!88 e2:minor!8>").sound("saw").gain(0.5).distort("0.25:tube:1").postgain(0.53).clip(0.8)
    .adsr("0.010:0.8:0.1:0.03").adsrCurves("exp:exp:exp").lpadsr("0.010:0.5:0.0:0.15").hpf(60).hpq(1.25).lpf(100).lpe(1.0).hpq(1.25) //  .solo()
    .mute("<0!128 1!32>") // .engine("pedal")
  , // Drums
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd ~  bd ~ ]!24 [bd bd bd bd]!24>").mute("<0!128 1!32>")  // . solo()
    .orbit(5).gain(1.0).hpf(80).hpq(1.5).lpf(6000).adsr("0.005:0.12:0.2:0.1"),
  sound("<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd ~  sd]!24 [~  sd ~  sd]!24>").mute("<0!128 1!32>") // . solo()
    .late(0.0015).orbit(5).gain(0.9).hpf(190).lpf(6500).adsr("0.006:0.15:0.2:0.1").superimpose(bandf(200).bandq(4).gain(0.5)),
  sound("<[hh hh oh hh]!48 [cr hh cr hh]!16 [0 hh 0 hh]!16>").fast(2).mute("<0!128 1!32>")  //  . solo()
    .late(0.003).orbit(5).gain(0.90).hpf(4000).lpf(6800).adsr("0.01:0.1:0.5:0.2") // . mute()
  // Master
).room("0.2:2").roomlp(500).roomdim(500)
  .compressor("-10:2:10:0.02:0.25").seed(timeOfDay.mul(60*60*24))


// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW




// Written by: peekandpoke

// Epilepsy Warning: Do not click the oscilloscope!










            """,
)
