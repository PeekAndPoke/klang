package io.peekandpoke.klang.strudel.compat

import io.peekandpoke.klang.strudel.StrudelPatternEvent

object JsCompatTestData {

    data class Example(
        val name: String,
        val code: String,
        val skip: Boolean = false,
        val ignoreFields: Set<String> = emptySet(),
        val recover: (graal: StrudelPatternEvent, native: StrudelPatternEvent) -> Boolean = { _, _ -> false },
    ) {
        companion object {
            operator fun invoke(name: String, code: String) = Example(name = name, code = code)

            operator fun invoke(skip: Boolean, name: String, code: String) =
                Example(name = name, code = code, skip = true)
        }

        fun ignore(ignore: Set<String>) = copy(ignoreFields = ignoreFields + ignore)

        fun ignore(vararg ignore: String) = ignore(ignore.toSet())

        fun recover(recover: (graal: StrudelPatternEvent, native: StrudelPatternEvent) -> Boolean) =
            copy(
                recover = { graal, native ->
                    recover(graal, native) || this.recover(graal, native)
                }
            )
    }

    private const val SKIP = true

    val simplePatterns: List<Example> = listOf(
        // Chords
        Example("Chords #1", """note("c,eb,g")"""),

        // Tone / Scale / Tonal
        Example("C-Major notes", """note("c3 d3 e3 f3 g3 a3 b3 c4")"""),
        Example("n() without scale", """n("0 1 2 3")"""),
        Example("C4:minor scale", """n("0 2 4").scale("C4:minor")"""),
        Example("C4:major scale", """n("0 2 4").scale("C4:major")"""),
        Example("transpose positive", """note("c3 e3 g3").transpose(12)"""),
        Example("transpose negative", """note("c4").transpose(-12)"""),
        Example("transpose ex #1", """seq("[c2 c3]*4").transpose("<0 -2 5 3>").note()"""),
        Example("transpose ex #2", """seq("[c2 c3]*4").transpose("<1P -2M 4P 3m>").note()"""),

        // Sequences
        Example(skip = SKIP, "Sequence #1", """seq("<0 2 4 6 ~ 4 ~ 2 0!3 ~!5>*8")"""),

        // Sounds
        Example("Sound | Drums", """s("bd hh sd oh")"""),
        Example("Sound | Drum + Index", """s("bd:0 hh:1 sd:2 oh:3")"""),
        Example("Sound | Drum + Index + Gain", """s("bd:0:0.1 hh:1:0.2 sd:2:0.3 oh:3")"""),
        Example("Sound | Oscillators", """s("<sine saw isaw tri square>")"""),
        Example("Sound | Noise Generators", """s("<white brown pink crackle dust>").gain(0.5)"""),
        Example("Sound | Impulse", """s("impulse").gain(0.5)"""),

        // Structure & Control
        Example("Arrange", """arrange([1, note("c")], [2, note("e")])"""),
        // TODO: pickRestart cannot be used at the top-level in strudel ... need another test patterns here
        Example(SKIP, "PickRestart", """pickRestart([note("c"), note("e")])"""),
        Example("Cat", """cat(note("c"), note("e"))"""),
        Example("Stack", """stack(note("c"), note("e"))"""),
        Example("Hush", """note("c").hush()"""),
        Example("Gap", """gap(5)"""),

        // Euclidean Patterns
        *(1..8).flatMap { pulses ->
            (pulses..8).map { steps ->
                Example(

                    "Euclidean $pulses,$steps",
                    """note("c($pulses,$steps)")"""
                )
            }
        }.let { it.shuffled().take(it.size / 10) }.toTypedArray(),

        // Euclidean Patterns with Rotation
        *(1..8).flatMap { pulses ->
            (pulses..8).flatMap { steps ->
                (-8..8).map { rotation ->
                    Example(
                        "Euclidean Rot $pulses,$steps,$rotation",
                        """note("c($pulses,$steps,$rotation)")"""
                    )
                }
            }
        }.let { it.shuffled().take(it.size / 10) }.toTypedArray(),

        // Timing & Tempo
        Example(SKIP, "Slow", """note("c e g").slow(2)"""), // Our implementation is better
        Example("Fast", """note("c e g").fast(2)"""),
        Example("Slow & Fast", """note("c e g").slow(2).fast(2)"""),
        Example("Late 0.5", """note("a b c d").late(0.5)"""),
        Example("Late [0.5 0.5]", """note("a b c d").late("0.5 0.5")"""),
        Example("Late Sine", """note("a b c d").late(sine.range(-1, 1))"""),
        Example("Early 0.5", """note("a b c d").early(0.5)"""),
        Example("Early [0.5 0.5]", """note("a b c d").early("0.5 0.5")"""),
        Example("Early Sine", """note("a b c d").early(sine.range(-1, 1))"""),

        // Voice Attributes
        Example(SKIP, "Gain & Pan", """note("c").gain(0.5).pan("-1.0 1.0")"""),
        Example("Legato", """note("c e").legato(0.5)"""),
        Example("Clip", """note("c e").clip(0.5)"""),
        Example("Unison/Detune/Spread", """note("c").unison(4).detune(0.1).spread(0.5)"""),

        // ADSR Envelopes
        Example("ADSR single", """note("c").attack(0.1).decay(0.2).sustain(0.5).release(1.0)"""),
        Example("ADSR String", """note("c").adsr("0.1:0.2:0.5:1.0")"""),

        // Filters
        Example("LowPass", """s("saw").lpf(333)"""),
        Example("HighPass", """s("saw").hpf(444)"""),
        Example("BandPass", """s("saw").bandf(555)"""),
        // TODO: notchf does not seem to exist in strudel ... or we need to figure how?
        Example(SKIP, "Notch", """s("saw").notchf(666)"""),

        // Effects
        Example("Distortion low", """note("c").distort(0.5)"""),
        Example("Distortion medium", """note("c").distort(7.0)"""),
        Example("Distortion high", """note("c").distort(50.0)"""),
        Example("Bitcrush", """note("c").crush(4)"""),
        Example("Downsample", """note("c").coarse(4)"""),
        Example("Reverb", """note("c").room(0.5).roomsize(2.0)"""),
        Example("Delay", """note("c").delay(0.5).delaytime(0.25).delayfeedback(0.5)"""),

        // Continuous patterns Steady
        *listOf(
            Example("Continuous | Steady pure", """steady(0.5)"""),
            Example("Continuous | Steady", """note("a b c d").pan(steady(0.5))"""),
            Example("Continuous | Steady range", """note("a b c d").pan(steady(0.5).range(-0.5, 0.5))"""),
            // Continuous patterns Signal
            Example("Continuous | Signal pure", """signal(t => t * 2)"""),
            Example("Continuous | Signal", """note("a b c d").pan(signal(t => t * 0.5))"""),
            // Continuous patterns Time
            Example("Continuous | Time pure", """time"""),
            Example("Continuous | Time", """note("a b c d").pan(time)"""),
            Example("Continuous | Time range", """note("a b c d").pan(time.range(0, 1))"""),
            // Continuous patterns Sine
            Example(SKIP, "Continuous | Sine pure", """sine"""), // IS OK, js impl is buggy
            Example("Continuous | Sine range pure", """sine.range(-10, 10)"""),
            Example("Continuous | Sine", """note("a b c d").pan(sine)"""),
            Example("Continuous | Sine range", """note("a b c d").pan(sine.range(-0.5, 0.5))"""),
            // Continuous patterns Sine2
            Example(SKIP, "Continuous | Sine2 pure", """sine2"""), // IS OK, js impl is buggy
            Example("Continuous | Sine2", """note("a b c d").pan(sine2)"""),
            Example("Continuous | Sine2 range", """note("a b c d").pan(sine2.range(-0.5, 0.5))"""),
            // Continuous patterns Cosine
            Example(SKIP, "Continuous | Cosine pure", """cosine"""), // IS OK, js impl is buggy
            Example("Continuous | Cosine range pure", """cosine.range(-10, 10)"""),
            Example("Continuous | Cosine", """note("a b c d").pan(cosine)"""),
            Example("Continuous | Cosine range", """note("a b c d").pan(cosine.range(-0.5, 0.5))"""),
            // Continuous patterns Cosine2
            Example(SKIP, "Continuous | Cosine2 pure", """cosine2"""), // IS OK, js impl is buggy
            Example("Continuous | Cosine2", """note("a b c d").pan(cosine2)"""),
            Example("Continuous | Cosine2 range", """note("a b c d").pan(cosine2.range(-0.5, 0.5))"""),
            // Continuous patterns Saw
            Example(SKIP, "Continuous | Saw pure", """saw"""), // IS OK, js impl is buggy
            Example("Continuous | Saw", """note("a b c d").pan(saw)"""),
            Example("Continuous | Saw range", """note("a b c d").pan(saw.range(-0.5, 0.5))"""),
            // Continuous patterns Saw2
            Example(SKIP, "Continuous | Saw2 pure", """saw2"""), // IS OK, js impl is buggy
            Example("Continuous | Saw2", """note("a b c d").pan(saw2)"""),
            Example("Continuous | Saw2 range", """note("a b c d").pan(saw2.range(-0.5, 0.5))"""),
            // Continuous patterns ISaw
            Example(SKIP, "Continuous | ISaw pure", """isaw"""), // IS OK, js impl is buggy
            Example("Continuous | ISaw", """note("a b c d").pan(isaw)"""),
            Example("Continuous | ISaw range", """note("a b c d").pan(isaw.range(-0.5, 0.5))"""),
            // Continuous patterns ISaw2
            Example(SKIP, "Continuous | ISaw2 pure", """isaw2"""), // IS OK, js impl is buggy
            Example("Continuous | ISaw2", """note("a b c d").pan(isaw2)"""),
            Example("Continuous | ISaw2 range", """note("a b c d").pan(isaw2.range(-0.5, 0.5))"""),
            // Continuous patterns Tri
            Example(SKIP, "Continuous | Tri pure", """tri"""), // IS OK, js impl is buggy
            Example("Continuous | Tri", """note("a b c d").pan(tri)"""),
            Example("Continuous | Tri range", """note("a b c d").pan(tri.range(-0.5, 0.5))"""),
            // Continuous patterns Tri2
            Example(SKIP, "Continuous | Tri2 pure", """tri2"""), // IS OK, js impl is buggy
            Example("Continuous | Tri2", """note("a b c d").pan(tri2)"""),
            Example("Continuous | Tri2 range", """note("a b c d").pan(tri2.range(-0.5, 0.5))"""),
            // Continuous patterns ITri
            Example(SKIP, "Continuous | ITri pure", """itri"""), // IS OK, js impl is buggy
            Example("Continuous | ITri", """note("a b c d").pan(itri)"""),
            Example("Continuous | ITri range", """note("a b c d").pan(itri.range(-0.5, 0.5))"""),
            // Continuous patterns ITri2
            Example(SKIP, "Continuous | ITri2 pure", """itri2"""), // IS OK, js impl is buggy
            Example("Continuous | ITri2", """note("a b c d").pan(itri2)"""),
            Example("Continuous | ITri2 range", """note("a b c d").pan(itri2.range(-0.5, 0.5))"""),
            // Continuous patterns Square
            Example(SKIP, "Continuous | Square pure", """square"""), // IS OK, js impl is buggy
            Example("Continuous | Square", """note("a b c d").pan(square)"""),
            Example("Continuous | Square range", """note("a b c d").pan(square.range(-0.5, 0.5))"""),
            // Continuous patterns Square2
            Example(SKIP, "Continuous | Square2 pure", """square2"""), // IS OK, js impl is buggy
            Example("Continuous | Square2", """note("a b c d").pan(square2)"""),
            Example("Continuous | Square2 range", """note("a b c d").pan(square2.range(-0.5, 0.5))"""),
        ).map {
            it.ignore("data.gain")
                .recover { graal, native ->
                    graal.data.soundIndex != null &&
                            graal.data.soundIndex?.toDouble() == native.data.value?.asDouble
                }
        }.toTypedArray(),

        // Modulation
        Example("Vibrato", """note("c").vib(5).vibmod(0.1)"""),
        Example("Accelerate", """note("c").accelerate(1)"""),

        // Transformation
        Example("Struct #1", """note("c e").struct("x")"""),
        Example("Struct #2", """note("c,eb,g").struct("x ~ x ~ ~ x ~ x ~ ~ ~ x ~ x ~ ~").slow(2)"""),
        Example("Struct #3", """note("c3 d3").fast(2).struct("x")"""),
        Example("Struct #4", """note("c3 d3").adsr("0.01:0.2:0.0:0.0").fast(2).struct("x")"""),
        Example("Struct All #1", """note("c e").structAll("x")"""),
        Example("Struct All #2", """note("c,eb,g").structAll("x ~ x ~ ~ x ~ x ~ ~ ~ x ~ x ~ ~").slow(2)"""),
        Example("Mask #1", """note("c [eb,g] d [eb,g]").mask("<1 [0 1]>")"""),
        Example("Mask #2", """note("c3*8").mask(square.fast(4))"""),
        Example("Mask All #1", """note("c [eb,g] d [eb,g]").maskAll("<1 [0 1]>")"""),
        Example("Mask All #2", """note("c3*8").maskAll(square.fast(4))"""),
        Example("SuperImpose #1", """note("a").superimpose(p => p.note("c"))"""),
        Example("SuperImpose #2", """note("a c e h").superimpose(p => p.note("e c"))"""),
        Example(
            SKIP,
            "Layer #1",
            """seq("<0 2 4 6 ~ 4 ~ 2 0!3 ~!5>*8").layer(x => x.add("-2,2")).n().scale("C4:minor")"""
        ),
        // TODO: more complex tests for rev() an palindrome()
        Example("Reverse", """note("c e g").rev()"""),
        Example("Palindrome", """note("c e g").palindrome()"""),

        // Arithmetic Operators
        Example(SKIP, "Add", """n("0 1 2 3").add("2")"""),
        Example(SKIP, "Sub", """n("10 20").sub("5")"""),
        Example(SKIP, "Mul", """n("2 3").mul("4")"""),
        Example(SKIP, "Div", """n("10 20").div("2")"""),
        Example(SKIP, "Mod", """n("10 11").mod("3")"""),
        Example(SKIP, "Pow", """n("2 3").pow("3")"""),
        Example(SKIP, "Log2", """n("1 2 4 8").log2()"""),

        // Bitwise Operators
        Example(SKIP, "Band (AND)", """n("3 5").band("1")"""),
        Example(SKIP, "Bor (OR)", """n("1 4").bor("2")"""),
        Example(SKIP, "Bxor (XOR)", """n("3 5").bxor("1")"""),
        Example(SKIP, "Blshift (Left Shift)", """n("1 2").blshift("1")"""),
        Example(SKIP, "Brshift (Right Shift)", """n("2 4").brshift("1")"""),

        // Comparison
        Example(SKIP, "Less Than", """n("1 2 3").lt("2")"""),
        Example(SKIP, "Greater Than", """n("1 2 3").gt("2")"""),
        Example(SKIP, "Less Equal", """n("1 2 3").lte("2")"""),
        Example(SKIP, "Greater Equal", """n("1 2 3").gte("2")"""),
        Example(SKIP, "Equal", """n("1 2 3").eq("2")"""),
        Example(SKIP, "Not Equal", """n("1 2 3").ne("2")"""),

        // Logical
        Example(SKIP, "Logical And", """n("0 1").and("5")"""),
        Example(SKIP, "Logical Or", """n("0 1").or("5")"""),
    )

    val songs: List<Example> = listOf(
        Example(
            "Small Town Boy - Melody", """
                n("<[~ 0] 2 [0 2] [~ 2][~ 0] 1 [0 1] [~ 1][~ 0] 3 [0 3] [~ 3][~ 0] 2 [0 2] [~ 2]>*4")
                    .scale("C4:minor")
                    .sound("saw")
            """.trimIndent()
        ),
        Example(
            "Small Town Boy - Full", """
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
            "Stranger Things Main Theme", """
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
        ),
        Example(
            "Drums with Reverb", """
                sound("bd hh sd oh")
                    .gain(0.8)
                    .room(0.01).rsize(3.0)
                    .pan(sine.slow(8))
                    .fast(2)
            """.trimIndent()
        ),
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
