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
import * from "stdlib"                                                                                                           //
import * from "sprudel"                                                                                                         ////
                                                                                                                               //  //
let feel = 3.0    // 0.0 .. cold | 10.0 .. warm                                                                               //    //
                                                                                                                             //      //
                                                                                                                            //        //
stack(                                                                                                         //////////////          //////////////
  // Lead                                                                                                        //                              //
  n(`<[-7 0 2 4] [-7 0 4 [2 6]|[4 2]|2|2|2] [-5 -1 2 4] [-6 -1 [4 3]|5|3|3|3 [1 -1]|1|1|1|1]>*2`)                  //          DISCO!          //
    .orbit(0).scale("<e4:minor!48 e5:minor!16>").sound("superramp").unison(9).detune(0.10).analog(feel)              //       FOREVER!       //
    .hpf(1600).lpf(1750).lpe(berlin.range(1.6, 1.7).fast(4)).lpq(2.2).lpadsr("0.010:1.5:0.5:0.05")                     //                  //
    .gain(0.50).distort("0.550:tube:4").postgain("<0.600!48 0.300!16>") // . solo()                                     //       //      //
    .adsr("0.010:2.0:0.0:0.01").adsrCurves("exp:exp:exp").clip(0.9) //  . mute()                                       //     //    //    //
    .release("<0.075!16 0.20!16>").vibrato(5).vibmod(0.01)                                                            //   //          //  //
    .phaser(1/8).phaserdepth(0.05).phasersweep(500).phasercenter(2000)                                               // //               // //
    .shuffle("<1!64 0!16 1!1 4/8!14 1!33>")                                                                         //                       //
    .superimpose(x => x.transpose(0).detune(0.12).velocity("<0!32 0.25!32>").pan(0.1).superimpose(pan(0.9)))
    .mute("<1!32 0!192>").engine("pedal"),
  // Guitar 1
  n(`<[7 4 2 <-1 3 1 3> [0 -1 -3 -1] [0 -3] -2 <[-1 5@3] [5 6@3] [[4 5] 8@3] [[3 4] 3@3]>]!4
      [[4@2 [2 0] 0] [-1 -4] [-3 1 -3 1 -3!10 1 -3] [2 [2 6@3]]]!2
      [[-3,-7] [[-4,-5] [-1,-3]] [0,-3] <[[4 6],[0 -1]] [0,-1]>] [<[7,4] [[7 4 6 2]!4]> [-5 -6] [-7,-14] [-2 <-4 -1>]]>/4`)
    .orbit(1).scale("<e3:minor!48 e4:minor!16 e3:minor!48 e4:minor!16>").struct("<[x!16]!7 [x!24]!1 [x!16]!16>") //  .mute()
    .velocity("1.00 0.93!7 0.96 0.93!7".fast(2)).analog(feel) // . solo()
    .sound("supersaw").unison(17).detune(0.10).gain(0.8).postgain(0.25).distort("1:diode:4").distort(0.8)    
    .clip("<0.88!31 0.80 0.88!31 0.91 0.88!30 0.80 0.73>".fast(2)).adsr("0.006:4.0:0.1:0.02").adsrCurves("exp:exp:exp").lpadsr("0.006:1.0:0.4:0.007")    
    .hpf("<450!48 780!16>").lpf(saw.range(1,0).pow(2.0).mul(500).add(1475).slow(4)).lpe(2.3).lpq(0.825)
    .coarse(2).pan(0.3).superimpose(pan(0.7))
    .engine("pedal").body("wood").bodyMix(0.3)
  ,
  // Guitar 2
  n("<0 0 2 4 0 0 -2 -1>")  //  . solo()
    .orbit(2).scale("<e2:minor>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2)
    .velocity("1.00 0.94!7 0.97 0.94!7".fast(2)).analog(feel)
    .sound("supersaw").unison(11).detune(0.10).gain(0.8).postgain(0.25).distort("1:diode:4").distort(0.8)
    .clip("<0.88!31 0.80 0.88!31 0.91 0.89!30 0.80 0.73>".fast(2)).adsr("0.004:5.0:0.0:0.012").adsrCurves("exp:exp:exp").lpadsr("0.004:1.0:0.4:0.005")    
    .hpf(130).lpf(1450).lpe(2.3).lpq(0.8)
    .coarse(2).pan(0.35).superimpose(
      x => x.pan(0.65),
      x => x.orbit(3).postgain(0.23).hpf(130).lpf(1450) // .lpe(1.0)
            .scaleTranspose("<4!7 [2 [3 4@3]]!1 4!7 [-3 [-4 -3@3]]>").pan(0.3).superimpose(pan(0.7))
    ).mute("<0!128 1!16 0!16>").engine("pedal").body("wood").bodyMix(0.3)
  , // Bass
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!1]!16 [x@3 x]!48 [x!4]!48>").fast(2).velocity("1.00 0.93!3 0.95 0.93!3".fast(2)) // . mute()
    .orbit(4).coarse(2).scale("<e2:minor!88 e2:minor!8>").sound("saw").gain(0.5).distort("0.3:tube:1").postgain(0.56).clip(0.7)
    .adsr("0.003:0.3:0.2:0.015").adsrCurves("exp:exp:exp").lpadsr("0.007:0.5:0.0:0.15").hpf(60).hpq(1.5).lpf(100).lpe(1.0).lpq(1.25) //  .solo()
    .mute("<0!128 1!32>") // .engine("pedal")
  , // Drums
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd  ~ bd  ~]!24 [bd bd bd bd]!24>").mute("<0!128 1!32>")  // . solo()
    .orbit(5).gain(0.85).hpf(80).lpf(6000).adsr("0.003:0.3:0.5:0.5"),
  sound("<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd  ~ sd]!24 [~    sd    ~    sd]!24>").mute("<0!128 1!32>") // . solo()
    .late(0.002).orbit(6).gain(0.70).hpf(200).lpf(5500).adsr("0.005:0.3:0.7:0.5").superimpose(x => x.bandf(200).bandq(4).gain(0.4)),
  sound("<[hh hh hh hh]!16 [hh hh oh hh]!16 [cr hh cr hh]!24 [~ hh ~ hh]!24>").fast(2).mute("<0!128 1!32>")  //  . solo()
    .late(0.001).orbit(7).gain(0.70).hpf(3000).lpf(5800).adsr("0.005:0.3:0.8:0.5") // . mute()
  // Master
).room("0.2:3").roomlp(2000).roomdim(500)
  .compressor("-10:2:10:0.02:0.25").seed(timeOfDay.mul(60*60*24))


// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW




// Written by: peekandpoke

// Epilepsy Warning: Do not click the oscilloscope!










    """,
)
