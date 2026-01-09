package io.peekandpoke.klang.strudel.compat

object JsCompatTestData {

    private const val RUN_PROBLEMS = false

    val simplePatterns: List<Triple<Boolean, String, String>> = listOf(
        // Scales
        Triple(true, "C-Major notes", """note("c3 d3 e3 f3 g3 a3 b3 c4")"""),
        Triple(true, "n() without scale", """n("0 1 2 3")"""),
        Triple(true, "C4:minor scale", """n("0 2 4").scale("C4:minor")"""),
        Triple(true, "C4:major scale", """n("0 2 4").scale("C4:major")"""),
        // Oscillators & Generators
        Triple(true, "Oscillators", """s("<sine saw isaw tri square>").fast(2)"""),
        Triple(true, "Noise Generators", """s("<white brown pink crackle dust>").gain(0.5)"""),
        Triple(true, "Impulse", """s("impulse").gain(0.5)"""),
        // Sounds
        Triple(true, "Drum Sounds", """s("bd hh sd oh")"""),
        Triple(true, "Drum Sounds with Sound Index", """s("bd:0 hh:1 sd:2 oh:3")"""),
        // Structure & Control
        Triple(true, "Arrange", """arrange([1, note("c")], [2, note("e")])"""),
        // TODO: pickRestart cannot be used at the top-level in strudel ... need another test patterns here
        Triple(RUN_PROBLEMS, "PickRestart", """pickRestart([note("c"), note("e")])"""),
        Triple(true, "Cat", """cat(note("c"), note("e"))"""),
        Triple(true, "Stack", """stack(note("c"), note("e"))"""),
        Triple(true, "Euclidean", """note("c(3,8)")"""),
        Triple(true, "Euclidean with rotation", """note("c(3,8,2)")"""),
        // Time & Tempo
        Triple(true, "Reverse", """note("c e g").rev()"""),
        Triple(true, "Palindrome", """note("c e g").palindrome()"""),
        Triple(true, "Slow & Fast", """note("c e g").slow(2).fast(2)"""),
        // Voice Attributes
        Triple(true, "Gain & Pan", """note("c").gain(0.5).pan("-0.5 0.5")"""),
        Triple(true, "Legato", """note("c e").legato(0.5)"""),
        Triple(true, "Clip", """note("c e").clip(0.5)"""),
        Triple(true, "Unison/Detune/Spread", """note("c").unison(4).detune(0.1).spread(0.5)"""),
        // ADSR Envelopes
        Triple(true, "ADSR single", """note("c").attack(0.1).decay(0.2).sustain(0.5).release(1.0)"""),
        Triple(true, "ADSR String", """note("c").adsr("0.1:0.2:0.5:1.0")"""),
        // Filters
        Triple(true, "LowPass", """s("saw").lpf(333)"""),
        Triple(true, "HighPass", """s("saw").hpf(444)"""),
        Triple(true, "BandPass", """s("saw").bandf(555)"""),
        // TODO: notchf does not seem to exist in strudel ... or we need to figure how?
        Triple(RUN_PROBLEMS, "Notch", """s("saw").notchf(666)"""),
        // Effects
        Triple(true, "Distortion low", """note("c").distort(0.5)"""),
        Triple(true, "Distortion medium", """note("c").distort(7.0)"""),
        Triple(true, "Distortion high", """note("c").distort(50.0)"""),
        Triple(true, "Bitcrush", """note("c").crush(4)"""),
        Triple(true, "Downsample", """note("c").coarse(4)"""),
        Triple(true, "Reverb", """note("c").room(0.5).roomsize(2.0)"""),
        Triple(true, "Delay", """note("c").delay(0.5).delaytime(0.25).delayfeedback(0.5)"""),
        // Continuous patterns Sine
        Triple(true, "Continuous | Sine", """note("a b c d").pan(sine)"""),
        Triple(true, "Continuous | Sine range", """note("a b c d").pan(sine.range(-0.5, 0.5))"""),
        // Continuous patterns Saw
        Triple(true, "Continuous | Saw", """note("a b c d").pan(saw)"""),
        Triple(true, "Continuous | Saw range", """note("a b c d").pan(saw.range(-0.5, 0.5))"""),
        // Continuous patterns ISaw
        Triple(true, "Continuous | ISaw", """note("a b c d").pan(isaw)"""),
        Triple(true, "Continuous | ISaw range", """note("a b c d").pan(isaw.range(-0.5, 0.5))"""),
        // Continuous patterns Tri
        Triple(true, "Continuous | Tri", """note("a b c d").pan(tri)"""),
        Triple(true, "Continuous | Tri range", """note("a b c d").pan(tri.range(-0.5, 0.5))"""),
        // Continuous patterns Square
        Triple(true, "Continuous | Square", """note("a b c d").pan(square)"""),
        Triple(true, "Continuous | Square range", """note("a b c d").pan(square.range(-0.5, 0.5))"""),
        // Modulation
        Triple(true, "Vibrato", """note("c").vib(5).vibmod(0.1)"""),
        Triple(true, "Accelerate", """note("c").accelerate(1)"""),
        // Masking
        Triple(true, "Struct #1", """note("c,eb,g").struct("x ~ x ~ ~ x ~ x ~ ~ ~ x ~ x ~ ~").slow(2)"""),
        Triple(true, "Struct #2", """note("c3 d3").fast(2).struct("x")"""),
        Triple(true, "Mask #1", """note("c [eb,g] d [eb,g]").mask("<1 [0 1]>")"""),
        Triple(true, "Mask #2", """note("c3*8").mask(square.fast(4))"""),
    )

    val songs: List<Triple<Boolean, String, String>> = listOf(
        Triple(
            true, "Small Town Boy - Melody", """
                n("<[~ 0] 2 [0 2] [~ 2][~ 0] 1 [0 1] [~ 1][~ 0] 3 [0 3] [~ 3][~ 0] 2 [0 2] [~ 2]>*4")
                    .scale("C4:minor")
                    .sound("saw")
            """.trimIndent()
        ),
        Triple(
            true, "Small Town Boy - Full", """
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
        Triple(
            true, "Tetris Remix", """
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
        Triple(
            true, "Stranger Things Main Theme | Bass line", """
                note("<a1 e2>/8")
                    .clip(0.8)
                    .struct("x*8")
                    .s("supersaw")
            """.trimIndent()
        ),
        Triple(
            true, "Stranger Things Main Theme | Melody", """
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
        Triple(
            true, "Stranger Things Main Theme", """
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
        Triple(
            true, "Simple Drums (Poly)", """
                sound("bd hh sd oh bd:1 hh:1 sd:1 oh:1 bd:2 hh:2 sd:2 oh:2 bd:3 hh:3 sd:3 oh:3")
                    .gain(0.8).slow(2)
            """.trimIndent()
        ),
        Triple(
            true, "Drums with Delay", """
                sound("bd hh sd oh")
                    .gain(0.8)
                    .delay("0.0 0.0 0.5 0.0").delaytime(0.25).delayfeedback(0.5)
                    .pan(sine.slow(8))
                    .fast(2)
            """.trimIndent()
        ),
        Triple(
            true, "Drums with Reverb", """
                sound("bd hh sd oh")
                    .gain(0.8)
                    .room(0.01).rsize(3.0)
                    .pan(sine.slow(8))
                    .fast(2)
            """.trimIndent()
        ),
        Triple(
            true, "Off-Beat Drums", """
                sound("bd hh sd oh")
                     .gain(1.0)
                    //         .delay("0.0 0.0 0.5 0.0")
                     .delay(0.5)
                     .delaytime(0.25)
                     .delayfeedback(0.5)
            """.trimIndent()
        ),
        Triple(
            true, "Dub Triplets", """
                sound("bd hh sd oh")
                      .delay(0.6)
                      .delaytime(0.375)
                      // High feedback for long tails
                      .delayfeedback(0.7)
            """.trimIndent()
        ),
        Triple(
            true, "Slapback", """
                sound("bd hh sd oh")
                      .delay(0.4)
                       // 50ms
                      .delaytime(0.05)
                      // Low feedback
                      .delayfeedback(0.2)
            """.trimIndent()
        ),
        Triple(
            true, "Double Sample Bug", """
                stack(
                      //n("0 1 2 3 4 5 6 7").scale("C4:minor"),
                      sound("bd hh sd oh")
                      .lpf("100 200 300 400 500 600 700 800")
                      .fast(2)
                      .gain(1.0),
                    )
            """.trimIndent()
        ),
        Triple(
            true, "Glissando Test", """
                n("1 3 5 7").scale("C4:minor")
                    .sound("sine")
                    .gain(1.0)
                    .slow(4)
                    .accelerate(1)
                    .vib(8)
                    .vmod(0.5)
            """.trimIndent()
        ),
        Triple(
            true, "Two Orbits", """
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
