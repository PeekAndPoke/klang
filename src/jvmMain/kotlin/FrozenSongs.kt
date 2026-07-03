/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

/**
 * FROZEN, IMMUTABLE snapshots of complex built-in songs — captured verbatim on 2026-07-03
 * so the CPU benchmark has a stable baseline that does NOT move when the live
 * builtinsongs sources are edited.
 *
 * DO NOT edit these to track song changes. If you want to re-baseline against a newer
 * version of a song, add a NEW dated constant instead (e.g. derSchmetterling_2026_09),
 * keeping the old one for historical comparison.
 *
 * Source at snapshot time:
 *  - Der Schmetterling  → builtinsongs/DerSchmetterling.kt  (rpm 34.5)
 *  - Seltsamere Dinge   → builtinsongs/StrangerThings.kt    (rpm 34.0)
 */
object FrozenSongs {

    /** "Der Schmetterling" — the ~50%-CPU song this benchmark investigates. Snapshot 2026-07-03. */
    const val derSchmetterling_2026_07_03: String = """
import * from "stdlib"                                                                                                                         //.
import * from "sprudel"                                                                                                                       ////.
                                                                                                                                             //  //.
let feel = 8.0    // 0.0 .. ice | 100.0 .. fire                                                                                             //    //.
                                                                                                                                           //      //.
                                                                                                                                          //        //.
stack(                                                                                                                       //////////////          //////////////.
  // Lead                                                                                                                      //                              //.
  n(`<[-7 0 2 4] [-7 0 4 [2 6]|[4 2]|2|2|2] [-5 -1 2 4] [-6 -1 [4 3]|5|3|3|3 [1 -1]|1|1|1|1]>*2`)                                //          DISCO!          //.
    .orbit(0).scale("<e4:minor!48 e5:minor!16 e4:minor!48 e3:minor!16>").sound("superramp").unison(5).spread(0.08)                 //       FOREVER!       //.
    .hpf(1500).lpf(1575).lpe(berlin.range(2, 2.10).fast(4)).lpq(2.3).lpadsr("0.007:1.3:0.0:0.01")                                    //                  //.
    .gain(0.50).distort("0.620:tube:4").postgain("<0.220!48 0.110!16 0.220!48 0.330!16>") // . solo()                                 //       //      //.
    .adsr("0.007:4.0:0.0:0.01").clip(0.89)  // . mute()                                                                              //     //.   //    //.
    .release("<0.04!16 0.11!16>").vibrato(8).vibmod(0.01)                                                                           //   //.         //  //.
    .shuffle("<1!64 0!16 1!1 4/8!14 1!33>")                                                                                        // //.              // //.
    .superimpose(x => x.transpose(12).spread(0.12).mute("<1!16 0!16>").velocity(0.10).pan(0.15).superimpose(pan(0.85)))           //.                      //.
    .mute("<1!32 0!192>").analog(feel).pipeline("pedal").room("0.3:5:0.1")
  , // Guitar 1
  n(`<[7 [4@4 2 -1] 2 1 [0 -1 -3 -1] [0 -3] -2 <[-1 5@3] [5 6@3] [[4 5] 8@3] [[3 4] 3@3]>]!4
      [[4@2 [2 0] 0] [-1 -4] [-3 1 -3 1 -3!10 1 -3] [2 [2 6@3]]]!2
      [[-3,-7] [[-4,-5] [-1,-3]] [0,-3] <[[4 6],[0 -1]] [0,-1]>] [<[7,4] [[7 4 6 2]!4]> [-5 -6] [-7,-14] [-5 <-1 -4 -4 1>]]>/4`)
    .orbit(1).scale("<e3:minor!48 e4:minor!16 e3:minor!48 e4:minor!16>").struct("<[x!16]!7 [x!24]!1 [x!16]!16>") //  .mute()
    .velocity("0.98 0.95!7 0.97 0.95!7".fast(2)).analog(feel)  // . solo()
    .sound("supersaw").unison(9).spread(0.08).gain(0.75).postgain(0.12).distort("1:tube:4").distort(0.80)    
    .clip("<0.86!31 0.77 0.86!31 0.85 0.86!30 0.80 0.70>".fast(2)).adsr("0.005:2.5:0.0:0.029").lpadsr("0.005:1.1:0.0:0.015")    
    .hpf("<550!16 360!16 550!16 800!16>").lpf("3450".add(saw.range(1, 0).pow(1.8).mul(800)).slow(4)).lpe(0.6).lpq(2.0)
    .coarse(2).coarseos(4).pan(0.15).superimpose(pan(0.85)).superimpose(hpf(3800).lpf(6700).postgain(0.03))
    .pipeline("pedal").body("wood").bodyMix(0.3)
  , // Guitar 2
  n("<0 0 2 4 0 0 -2 -1>")  //  . solo()
    .orbit(1).scale("<e2:minor>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2)
    .velocity("0.98 0.95!7 0.97 0.95!7".fast(2)).analog(feel)
    .sound("supersaw").unison(7).spread(0.09).gain(0.75).postgain(0.11).distort("1:tube:4").distort(0.85)
    .clip("<0.86!31 0.77 0.86!31 0.85 0.86!30 0.80 0.70>".fast(2)).adsr("0.005:2.5:0.0:0.027").lpadsr("0.005:1.0:0.0:0.01")    
    .hpf(120).lpf(3200).lpe(0.6).lpq(1.8)
    .coarse(2).coarseos(4).pan(0.3).superimpose(
      x => x.pan(0.7),
      x => x.postgain(0.09).hpf(240).lpf(3400).scaleTranspose("<4!7 [2 [3 4@3]]!1 4!7 [-7 -3] 4!7 [2 [3 4@3]]!1 4!7 [-3 [2 4@3]]>")
           .pan(0.2).superimpose(pan(0.8))
    ).superimpose(hpf(3500).lpf(6200).postgain(0.03)).mute("<0!128 1!16 0!16>").pipeline("pedal").body("wood").bodyMix(0.30)
  , // Bass
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!1]!16 [x@3 x]!48 [x!4]!80>").fast(2).velocity("0.98 0.98 0.99 0.98".fast(2))  // . mute()
    .orbit(4).scale("e1:minor").sound("saw").gain(0.5).distort("0.05:soft:2").postgain(0.20).clip(0.65)
    .adsr("0.007:5.0:0.0:0.015").lpadsr("0.001:0.05:0.0:0.01").hpf(60).hpq(1.0).lpf(200).lpe(35).lpq(1.0)  //  .solo()
    .pan(0.50).mute("<0!128 1!32>") // .pipeline("pedal")
  , // Drums
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd  ~ bd  ~]!32 [bd!4]!16 [bd ~ bd [~ bd]]!15 [bd!]!1>").mute("<0!128 1!32>")  // . solo()
    .pan(0.5).orbit(5).gain(0.24).hpf(45).lpf(11500).adsr("0.002:0.10:0.5:0.2"),
  sound("<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd  ~ sd]!32 [~ sd ~ sd]!32>").mute("<0!128 1!32>")  // . solo()
    .pan(0.485).late(0.0025).orbit(5).gain(0.32).hpf(350).lpf(11500).adsr("0.002:0.10:0.2:0.2")
    .superimpose(x => x.bandf("205".add(berlin.mul(10))).bandq(4).vel(0.60).hpf(190).lpf(350)),
  sound("<[hh hh hh hh]!16 [hh hh oh hh]!24 [cr hh cr hh]!24 [~ rd ~ rd]!32>").fast(2).mute("<0!128 1!32>") // . solo()
    .pan(0.515).late(0.0005).orbit(5).gain(0.33).hpf(800).lpf("11500".add(perlin.mul(300))).adsr("0.005:0.15:0.8:0.2"), // . mute()
  sound("pink!8").orbit(6).gain(0.08).hpf(8000).pan(sine.range(0.25, 0.75).slow(3)).adsr("0.007:0.3:0.0:0.05") //  .solo(),
  // Master
).room("0.10:8:0.12").rlp(12500).seed(timeOfDay.mul(60*60*24))
 .compressor("-6:2:5:0.02:0.05")


// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW




// Written by: peekandpoke

// Epilepsy Warning: Do not click the oscilloscope!










    """

    /** "Seltsamere Dinge" (Stranger Things) — even heavier (unison 15 + body wood/glass + vowel). Snapshot 2026-07-03. */
    const val strangerThings_2026_07_03: String = """
import * from "stdlib"
import * from "sprudel"

let wait = 16
let keep = 32 * 6
let notch = sine.range(1 * 440, 3 * 440).slow(16) // 440, 880, 1560 ?

stack(
  // Claps --------------------------------------------------------------------------------------------------------------------
  sound("cp ~ cp ~ ~ cp cp ~  cp ~ ~ ~ cp cp ~ ~").slow(4).orbit(0).gain(0.285).legato(2.0)
    .bandf(sine.range(2000, 2200).fast(3.14)).hpf(800)
    .filterWhen(x => x >= wait * 8 && x < (wait * 12 + keep))
  , // Lyrics ---------------------------------------------------------------------------------------------------------------------------
  n("0").morse("Schön ist es auf der Welt zu sein!").orbit(0)
    .scale("C5:major").scaleTranspose("0 -2 2 2".slow(32)).bandf(2000).bandq(7.0).hpf(1000).analog(2)
    .sound("pulse").warmth(0.75).crush(5).gain(0.06).clip(0.35).pan(berlin.slow(2)).adsr("0.03:0.08:0.2:0.1") // .solo()
    .filterWhen(x => x >= wait * 12 && x < (wait * 6 + keep)).body("membrane")
  , // Melody -----------------------------------------------------------------------------------------------------------------
  n("<[0 2 4 6 7 6 4 2]!14 [0 -1 0 4 6 9 7 6] [-2 -1 0 2 7 4 -1 -3]>") // .solo()
    .scale("[c3:major c3:pentatonic c3:major c3:major]/16")
    .orbit(1).s("supersaw").unison(15).spread(saw.range(0.05, 0.35).slow(16))
    .gain(0.6).distort(1.0).postgain(0.10).adsr("0.005:2.0:0.5:0.1").lpadsr("0.005:5.0:0.5:0.1").clip(1.0)
    .pan(0.5) // . solo()
    .hpf(400).lpf(1200).lpenv(perlin.range(2.5, 4.0).lpq(3.0).slow(8)).analog(5).body("wood")
    .superimpose(x =>
      x.hpf(800).lpf(1500).lpq(5).bandf(notch).bandq(1.0).transpose(12).postgain(0.06).pan(0.2).superimpose(pan(0.8)).body("glass")
    ).filterWhen(x => x >= wait * 4 && x < (wait * 4 + keep)) // . solo()
  , // Bass -----------------------------------------------------------------------------------------------------------------------------
  note("<a1 [f1 c2 e1 [f1 c2]] [a1 [c2 f1] a1 [f1@3 e1]] [a1@2 [c2@3] [d1,d2] [c1,c2,c3] [d1,d1,d2,a2]]>/4").clip(0.67).struct("x!4").slow(16)
    .orbit(2).s("supersaw").unison(9).spread(saw.range(0.05, 0.45).slow(64)).warmth(0.01) // . mute()
    .gain(1.0).adsr("0.01:0.6:0.8:2.75").postgain(0.50).coarse(2).coarseos(2) // solo()
    .superimpose(
      x => x.orbit(3).scaleTranspose("<[12 12 7 12 12 [12 12] 0 -12] [12 12 0 12 12 [0 12] 0 -12]>/32")
        .pan(sine.range(0.15, 0.8).slow(32)).clip(0.79)
    ).lpf(4.5 * 440).lpq(2.5).hpf(60).notchf(notch).notchq(0.75).body("glass").vowel("i a e".slow(12)).vowelMix(0.2)
    .superimpose(
      x => x.gain(saw.range(0.2, 1.0).slow(64).pow(1.25).mul(2.0)).vibrato("0.51".add(perlin.div(10))).vibmod(0.05)
        .crush("1.85".add(berlin2.mul(0.5).slow(4))).crushos(2).lpf(5.5 * 440).hpf(300).postgain(0.45)
        .pan(0.2).superimpose(pan(0.8))                
    ).velocity(cat(saw.range(0.25, 1.0).pow(1.5).slow(32), pure(1).slow(256)).mul("1 0.95 0.975 0.95".fast(2)))
    .analog(10).filterWhen(x => x < (wait * 4 + keep))
  , // Perc 2 ------------------------------------------------------------------------------------------------------------------
  sound("<[hh hh oh hh] [hh hh ~ hh] [hh hh oh hh] [hh hh ~ <cr!7 rd>]>")
    .orbit(4).gain(0.5).pan(0.4).adsr("0.01:0.15:0.8:1.0").fast(2).degrade(0.1).lpf(6800).late(0.001)
    .filterWhen(x => x >= wait * 1 && x < (wait * 2 + keep))
  , // Perc 1 -----------------------------------------------------------------------------------------------------------------------
  sound("[bd bd bd ~  bd ~ bd ~] [bd bd sd:5 ~  bd ~ bd|sd:5 ~]").slow("[8 8 8 8 8 8 4 [2 4]]/32").fast(2)
    .orbit(5).gain(0.7).pan(0.5).adsr("0.017:0.3:0.5:1").degrade(0.01).hpf(120).lpf(7500)
    .filterWhen(x => x >= wait * 1.75 && x < (wait * 1 + keep))
  , // Shore ---------------------------------------------------------------------------------------------------------
  note("c").fast(7).sound("brown") // .solo()
    .orbit(0).gain(0.12).pan(perlin.early(1.7).range(0.3, 0.7).slow(7)).adsr("0.5:1.0:1.0:2.5")
    .bandf(perlin.range(440, 440 * 4).segment(16).slow(48)).bandq(sine.range(0.25, 5.0).slow(48).early(12))
  ,
).delay("0.2::0.5").delaytime(pure(1/8).div(cps)).room("0.1:10.0").compressor("-10:2:6:0.01:0.05")










        """
}
