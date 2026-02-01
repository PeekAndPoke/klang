package io.peekandpoke.klang.strudel.compat

object JsCompatTestSongs {

    private const val SKIP = true

    val songs: List<Example> = listOf(
        Example(
            SKIP, "Small Town Boy - Melody", """
                n("<[~ 0] 2 [0 2] [~ 2][~ 0] 1 [0 1] [~ 1][~ 0] 3 [0 3] [~ 3][~ 0] 2 [0 2] [~ 2]>*4")
                    .scale("C4:minor")
                    .sound("saw")
            """.trimIndent()
        ),
        Example(
            SKIP, "Small Town Boy - Full", """
                stack(
                        // melody
                        arrange(
                          [8, silence],
                          [8, n("<[~ 0] 2 [0 2] [~ 2][~ 0] 1 [0 1] [~ 1][~ 0] 3 [0 3] [~ 3][~ 0] 2 [0 2] [~ 2]>*4")],
                        ).orbit(1)
                        .scale("C4:minor")
                        .adsr("0.05:0.7:0.0:0.5")
                        .hpf(800)
                        .sound("triangle")
                        .gain(0.3),

                        // melody 2
                        arrange(
                          [8, silence],
                          [8, n("<[~ 0] 2 [0 2] [~ 2][~ 0] 1 [0 1] [~ 1][~ 0] 3 [0 3] [~ 3][~ 0] 2 [0 2] [~ 2]>*4")],
                        ).orbit(2)
                        .scale("C5:minor")
                        .adsr("0.05:0.7:0.0:0.5")
                        .hpf(1600)
                        .sound("triangle")
                        .gain(0.4),

                        // bass
                        note("<[c2 c3]*4 [bb1 bb2]*4 [f2 f3]*4 [eb2 eb3]*4>")
                        .orbit(3)
                        .sound("supersaw").unison(4).detune(0.1)
                        .adsr("0.0:0.3:0.0:0.8")
                        .lpf(800)
                        .gain(0.8).pan(-0.5),

                        // Drums
                        sound("[bd hh sd hh] [bd [bd, hh] sd oh]").fast(1)
                         .orbit(4)
                         .pan(0.5)
                         .gain(0.4)
                         .delay("0.2").delaytime(0.25).delayfeedback(0.3),
                    ).room(0.025).rsize(5.0)
            """.trimIndent()
        ),
        Example(
            "Tetris Remix", """
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
        ),
        Example(
            "Stranger Things Main Theme | Bass line", """
                note("<a1 e2>/8")
                    .clip(0.8)
                    .struct("x*8")
                    .s("supersaw")
            """.trimIndent()
        ),
        Example(
            "Stranger Things Main Theme | Melody", """
                n("0 2 4 6 7 6 4 2")
                    .scale("<c3:major>/2")
                    .s("supersaw")
                    .distort(0.7)
                    .superimpose((x) => x.detune("<0.5>"))
                    //.lpenv(perlin.slow(3).range(1, 4))
                    .lpf(sine.slow(2).range(100, 2000))
                    .gain(0.3)
            """.trimIndent()
        ),
        Example(
            SKIP, "Stranger Things Main Theme", """
                stack(
                    n("0 2 4 6 7 6 4 2")
                        .scale("<c3:major>/2")
                        .s("supersaw")
                        .distort(0.7)
                        .superimpose((x) => x.detune("<0.5>"))
                        //.lpenv(perlin.slow(3).range(1, 4))
                        .lpf(sine.slow(2).range(100, 2000))
                        .gain(0.3),
                        
                    note("<a1 e2>/8").clip(0.8)
                        .struct("x*8")
                        .s("supersaw"),
                )
            """.trimIndent()
        ),
        Example(
            "Simple Drums (Poly)", """
                sound("bd hh sd oh bd:1 hh:1 sd:1 oh:1 bd:2 hh:2 sd:2 oh:2 bd:3 hh:3 sd:3 oh:3")
                    .gain(0.8).slow(2)
            """.trimIndent()
        ),
        Example(
            "Drums with Delay", """
                sound("bd hh sd oh")
                    .gain(0.8)
                    .delay("0.0 0.0 0.5 0.0").delaytime(0.25).delayfeedback(0.5)
                    .pan(sine.slow(8))
                    .fast(2)
            """.trimIndent()
        ).recover { graal, native -> graal.data.pan == 0.5 && native.data.pan == null },
        Example(
            "Drums with Reverb", """
                sound("bd hh sd oh")
                    .gain(0.8)
                    .room(0.01).rsize(3.0)
                    .pan(sine.slow(8))
                    .fast(2)
            """.trimIndent()
        ).recover { graal, native -> graal.data.pan == 0.5 && native.data.pan == null },
        Example(
            "Off-Beat Drums", """
                sound("bd hh sd oh")
                     .gain(1.0)
                    //         .delay("0.0 0.0 0.5 0.0")
                     .delay(0.5)
                     .delaytime(0.25)
                     .delayfeedback(0.5)
            """.trimIndent()
        ),
        Example(
            "Dub Triplets", """
                sound("bd hh sd oh")
                      .delay(0.6)
                      .delaytime(0.375)
                      // High feedback for long tails
                      .delayfeedback(0.7)
            """.trimIndent()
        ),
        Example(
            "Slapback", """
                sound("bd hh sd oh")
                      .delay(0.4)
                       // 50ms
                      .delaytime(0.05)
                      // Low feedback
                      .delayfeedback(0.2)
            """.trimIndent()
        ),
        Example(
            SKIP, "Double Sample Bug", """
                stack(
                      //n("0 1 2 3 4 5 6 7").scale("C4:minor"),
                      sound("bd hh sd oh")
                      .lpf("100 200 300 400 500 600 700 800")
                      .fast(2)
                      .gain(1.0),
                    )
            """.trimIndent()
        ),
        Example(
            SKIP, "Glissando Test", """
                n("1 3 5 7").scale("C4:minor")
                    .sound("sine")
                    .gain(1.0)
                    .slow(4)
                    .accelerate(1)
                    .vib(8)
                    .vmod(0.5)
            """.trimIndent()
        ),
        Example(
            "Two Orbits", """
                stack(
                      // Snare only delay on the drums
                      sound("bd hh sd oh").gain(0.7).delay("0.0 0.0 0.5 0.0").delaytime(0.25).delayfeedback(0.5).orbit(0),
                      // Full delay on the melody
                      note("c ~ d ~ e ~ f ~").delay("0.0").delaytime(0.25).orbit(1),
                    )
            """.trimIndent()
        )
    )
}
