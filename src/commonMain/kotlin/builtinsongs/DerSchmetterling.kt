@file:Suppress("unused")

package io.peekandpoke.klang.builtinsongs

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song

internal val derSchmetterlingSong = Song(
    id = "${BuiltInSongs.PREFIX}-der-schmetterling",
    title = "Der Schmetterling",
    rpm = 34.5,
    icon = "bug",
    code = """
import * from "stdlib"                                                                                                                   //
import * from "sprudel"                                                                                                                 ////
                                                                                                                                       //  //
let feel = 3.0    // 0.0 .. ice | 100.0 .. fire                                                                                       //    //
                                                                                                                                     //      //
                                                                                                                                    //        //
stack(                                                                                                                 //////////////          //////////////
  // Lead                                                                                                                //                              //
  n(`<[-7 0 2 4] [-7 0 4 [2 6]|[4 2]|2|2|2] [-5 -1 2 4] [-6 -1 [4 3]|5|3|3|3 [1 -1]|1|1|1|1]>*2`)                          //          DISCO!          //
    .orbit(0).scale("<e4:minor!48 e5:minor!16>").sound("superramp").unison(9).detune(0.08).analog(feel)                      //       FOREVER!       //
    .hpf(1600).lpf(1650).lpe(berlin.range(1.7, 1.75).fast(4)).lpq(2.5).lpadsr("0.008:1.2:0.00:0.01")                           //                  //
    .gain(0.50).distort("0.600:tube:4").postgain("<0.320!48 0.140!16>") // . solo()                                             //       //      //
    .adsr("0.008:2.5:0.0:0.01").adsrCurves("exp:exp:exp").clip(0.9)  // . mute()                                               //     //    //    //
    .release("<0.04!16 0.11!16>").vibrato(5).vibmod(0.02)                                                                     //   //          //  //
    .shuffle("<1!64 0!16 1!1 4/8!14 1!33>")                                                                                  // //               // //
    .superimpose(x => x.transpose(12).detune(0.12).mute("<1!16 0!16>").velocity(0.20).pan(0.1).superimpose(pan(0.9)))       //                       //
    .mute("<1!32 0!192>").engine("pedal").room("0.3:5:0.1"),
  // Guitar 1
  n(`<[7 4 2 <-1 3 1 3> [0 -1 -3 -1] [0 -3] -2 <[-1 5@3] [5 6@3] [[4 5] 8@3] [[3 4] 3@3]>]!4
      [[4@2 [2 0] 0] [-1 -4] [-3 1 -3 1 -3!10 1 -3] [2 [2 6@3]]]!2
      [[-3,-7] [[-4,-5] [-1,-3]] [0,-3] <[[4 6],[0 -1]] [0,-1]>] [<[7,4] [[7 4 6 2]!4]> [-5 -6] [-7,-14] [-2 <-4 -1>]]>/4`)
    .orbit(1).scale("<e3:minor!48 e4:minor!16 e3:minor!48 e4:minor!16>").struct("<[x!16]!7 [x!24]!1 [x!16]!16>") //  .mute()
    .velocity("0.98 0.95!7 0.97 0.95!7".fast(2)).analog(feel) // . solo()
    .sound("supersaw").unison(7).detune(0.08).gain(0.8).postgain(0.12).distort("1:diode:4").distort(0.8)    
    .clip("<0.88!31 0.80 0.88!31 0.85 0.88!30 0.81 0.71>".fast(2)).adsr("0.004:4.0:0.0:0.011").adsrCurves("exp:exp:exp").lpadsr("0.010:1.0:0.0:0.02")    
    .hpf("<500!16 500!16 500!16 800!16>").lpf("3150".add(saw.range(1, 0).pow(1.8).mul(450)).slow(4)).lpe(0.3).lpq(1.4)
    .coarse(2).coarseos(2).pan(0.3).superimpose(pan(0.9)).warmth(0.1)
    .engine("pedal").body("wood").bodyMix(0.15)
  ,
  // Guitar 2
  n("<0 0 2 4 0 0 -2 -1>")  //  . solo()
    .orbit(1).scale("<e2:minor>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2)
    .velocity("0.98 0.95!7 0.97 0.95!7".fast(2)).analog(feel)
    .sound("supersaw").unison(5).detune(0.09).gain(0.8).postgain(0.13).distort("1:diode:4").distort(0.8)
    .clip("<0.88!31 0.80 0.88!31 0.85 0.88!30 0.81 0.71>".fast(2)).adsr("0.005:4.2:0.0:0.011").adsrCurves("exp:exp:exp").lpadsr("0.010:1.0:0.0:0.02")    
    .hpf(130).lpf(3000).lpe(0.3).lpq(1.2).warmth(0.1)
    .coarse(2).coarseos(2).pan(0.45).superimpose(
      x => x.pan(0.55),
      x => x.postgain(0.12).scaleTranspose("<4!7 [2 [3 4@3]]!1 4!7 [-3 [-4 -3@3]]>").pan(0.18).superimpose(pan(0.62))
    ).mute("<0!128 1!16 0!16>").engine("pedal").body("wood").bodyMix(0.15)
  , // Bass
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!1]!16 [x@3 x]!48 [x!4]!48>").fast(2).velocity("0.98 0.96!3 0.97 0.95!3".fast(1)) // . mute()
    .orbit(4).scale("<e2:minor!88 e2:minor!8>").sound("saw").gain(0.8).distort("0.15:diode:1").postgain(0.23).clip(0.75)
    .adsr("0.007:1.5:0.0:0.015").adsrCurves("exp:exp:exp").lpadsr("0.007:0.6:0.0:0.01").hpf(140).lpf(240).lpe(0.5) // .solo()
    .superimpose(x => x.sound("sine").transpose(-12).hpf(60).hpq(1.2).lpf(160).lpe(1).lpq(2).gain(0.5).pan(0.5))
    .mute("<0!128 1!32>").duck(5).duckdepth(0.15).duckattack(0.10) // .engine("pedal")
  , // Drums
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd  ~ bd  ~]!24 [bd bd bd bd]!24>").mute("<0!128 1!32>")  // . solo()
    .pan(0.5).orbit(5).gain(0.37).hpf(35).lpf(5500).adsr("0.004:0.1:0.5:0.2"),
  sound("<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd  ~ sd]!24 [~ sd ~ sd]!24>").mute("<0!128 1!32>")  // . solo()
    .pan(0.475).late(0.002).orbit(6).gain(0.48).hpf(200).lpf(5000).adsr("0.007:0.1:0.5:0.2")
    .superimpose(x => x.bandf(250).bandq(4).vel(0.70)),
  sound("<[hh hh hh hh]!16 [hh hh oh hh]!16 [cr hh cr hh]!24 [~ hh ~ hh]!24>").fast(2).mute("<0!128 1!32>")  //  . solo()
    .pan(0.525).late(0.003).orbit(7).gain(0.37).hpf(500).lpf(5500).adsr("0.005:0.2:0.5:0.2"), // . mute()
  sound("brown").gain(0.03),
  // Master
).room("0.16:8:0.12").rlp(8000).analog(feel)
 .compressor("-10:2:10:0.02:0.1").seed(timeOfDay.mul(60*60*24))


// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW




// Written by: peekandpoke

// Epilepsy Warning: Do not click the oscilloscope!











    """,
)
