package io.peekandpoke

object TestPatterns {
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
                stack(
                    // bass
                    note("<[c2 c3]*4 [bb1 bb2]*4 [f2 f3]*4 [eb2 eb3]*4>")
                    .sound("supersaw").unison(8)
                    .adsr("0.0:0.5:0.0:0.8")
                    .lpf(400)
                    .gain(0.5),
                    // melody
                    arrange(
                      [8, silence],
                      [8, n("<[~ 0] 2 [0 2] [~ 2][~ 0] 1 [0 1] [~ 1][~ 0] 3 [0 3] [~ 3][~ 0] 2 [0 2] [~ 2]>*4")],
                    )
                    .scale("C4:minor")
                    .adsr("0.05:0.5:0.5:0.5")
                    .hpf(400)
                    .sound("triangle").gain(0.5),
                    // Drums
                    sound("bd hh sd hh")
                     .orbit(1)
                     .gain(0.5)
                     .delay("0.0 0.0 0.5 0.0").delaytime(0.25).delayfeedback(0.66)
                     .fast(2),
                )
                
            """.trimIndent()

    val tetris = """
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
                    >`).sound("piano")
                    .gain("1.0"),
                    note(`<
                        [[e2 e3]*4]
                        [[a2 a3]*4]
                        [[g#2 g#3]*2 [e2 e3]*2]
                        [a2 a3 a2 a3 a2 a3 b1 c2]
                        [[d2 d3]*4]
                        [[c2 c3]*4]
                        [[b1 b2]*2 [e2 e3]*2]
                        [[a1 a2]*4]
                    >`).sound("piano")
                    .gain(0.6),
                    sound("bd hh sd hh").fast(wchoose([2, 1.0], [1, 0.1])).gain(0.5),
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

    val simpleDrums = """
            stack(
              n("0 1 2 3 4 5 6 7").scale("C4:minor").sound("piano"),
              sound("bd hh sd oh").fast(2).gain(0.8),
            )
        """.trimIndent()

    /**
     * This produces each drum sound twice.
     * Why? Because of the lpf() producing twice as many events as the sound()
     * Therefore the drum sounds re schedules twice ...
     *
     * TODO: fix this in the [io.peekandpoke.player.StrudelPlayer]
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

    val strangerThings = """
                stack(
                    n("0 2 4 6 7 6 4 2")
                      .scale("<c3:major>/2")
                      .s("supersaw")
                      .distort(0.7)
                      .superimpose((x) => x.detune("<0.5>"))
                      .lpenv(perlin.slow(3).range(1, 4))
                      .lpf(perlin.slow(2).range(100, 2000))
                      .gain(0.6),
                    note("<a1 e2>/8").clip(0.8).struct("x*8").s("supersaw").gain(0.5),
                )
            """.trimIndent()

    val piano = """
            note("c e g e").sound("piano")
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

    //    val active = smallTownBoyBass
//    val active = smallTownBoyMelody
    val active = smallTownBoy
//    val active = tetris
//    val active = c4Minor
//    val active = numberNotes
//    val active = crackle
//    val active = dust
//    val active = impulse
//    val active = whiteNoise
//    val active = brownNoise
//    val active = pinkNoise
//    val active = supersaw
//    val active = polyphone
//    val active = simpleDrums
//    val active = snareScale
//    val active = strangerThings
//    val active = piano

//    val active = asdrTest

    //    val active = delayOffBeatDrums
//    val active = delayDubTripletsDrums
//    val active = delaySlapBackDrums
//    val active = twoOrbits
}
