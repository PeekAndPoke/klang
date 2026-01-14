package io.peekandpoke.klang

import io.peekandpoke.klang.strudel.lang.*
import io.peekandpoke.klang.strudel.lang.addons.negateValue

object TestKotlinPatterns {

    val tetris = stack(
        // Melody
        note(
            """
                <
                    [e5 [b4 c5] d5 [c5 b4]]
                    [a4 [a4 c5] e5 [d5 c5]]
                    [b4 [~ c5] d5 e5]
                    [c5 a4 a4 ~]
                    [[~ d5] [~ f5] a5 [g5 f5]]
                    [e5 [~ c5] e5 [d5 c5]]
                    [b4 [b4 c5] d5 e5]
                    [c5 a4 a4 ~]
                >
                """.trimIndent()
        ).sound("tri")
//            .struct("x(3,8,1)")
            .orbit(0).gain(0.25)
            .pan(sine2.slow(16).range(-0.7, 0.7).negateValue())
            .delay(0.25).delaytime(0.5).delayfeedback(0.5)
            .room(0.05).rsize(1.0)
            .clip(0.3)
            .superimpose { x -> x.transpose("<0 12 0 -12>/8") }
//            .rev(4.1).rev(3)
        ,

        note(
            """
                <
                    [[e2 e3]*4]
                    [[a2 a3]*4]
                    [[g#2 g#3]*2 [e2 e3]*2]
                    [a3 a2 a1 a1 a1 a2 [a2 a3] [a4 a5]]
                    [[d2 d3]*4]
                    [[c2 c3]*4]
                    [[b1 b2]*2 [e3 e2]*2]
                    [a3 a2 a1 a0 a1 [c1 e2] [a5 a4] [a3 a2]]
                >
            """.trimIndent()
        ).sound("supersaw")
            .spread(0.5).unison(8).detune(0.3)
            .orbit(2).gain(0.4)
            .pan(sine2.slow(16).range(-0.7, 0.7))
//            .adsr("0.05:0.2:0.0:1.0")
            .room(0.05).rsize(1.0)
            .superimpose { x -> x.transpose("<0 12 0 -12>/8") }
            .struct("x*8")
//            .rev(4).rev(3)
        ,

        sound(
            """
            <
            [[bd:2, cr] hh sd hh] 
            [bd hh sd oh]
            [bd hh sd hh] 
            [bd hh sd hh]
            [[bd, cr] hh sd hh] 
            [bd hh sd oh]
            [bd hh sd hh] 
            [bd hh [mt mt, sd] [ht ht, oh]]
            [[bd:2, cr] hh [sd, cr] hh] 
            [bd hh sd oh]
            [bd hh sd hh] 
            [bd hh sd oh]
            [[bd, cr] oh sd oh] 
            [bd hh sd hh]
            [bd hh sd oh] 
            [bd hh [sd sd] [[sd sd], oh]]
            [[bd:2, cr] hh sd [sd hh]] 
            [bd hh sd oh]
            [bd hh sd hh] 
            [bd hh sd oh]
            [[bd, cr] hh sd [hh sd]] 
            [bd hh sd oh]
            [bd hh sd hh] 
            [bd hh [lt lt, sd sd] ~]
            [[bd:2, cr] hh [sd, cr] [sd, hh]] 
            [bd hh [sd:4 cr] oh]
            [bd hh sd hh] 
            [bd hh sd [bd, oh]]
            [[bd, cr] oh sd oh] 
            [bd oh sd oh]
            [[sd, cr] mt sd [mt, hh]] 
            [sd lt [bd bd] [bd bd]]
            >            
        """.trimIndent()
//                [[sd, hr] [bd, hh] [sd, hr] [bd, hh] [sd, hr] [bd, hh] [sd, hr] [bd, hh]]
//                [[sd, hr] [bd, hh] [sd, hr] [bd, hh] [sd, hr] [bd, hh] [sd, hr] [bd, hh]]
//                [[sd, hr] [bd, hh] [sd, hr] [bd, hh] [sd, hr] [bd, hh] [sd, hr] [bd, hh]]
//                [[sd, hr] [bd, hh] [sd, hr] [bd, hh] [sd, hr] [bd, hh] [sd, hr] [bd, hh]]
        )
            .orbit(3).pan(-0.0).gain(0.6)
            .room(0.05).rsize(2.0)
//            .delay("0.0 0.0 0.5 0.0").delaytime(0.25).delayfeedback(0.5)
            .adsr("0.01:0.2:0.5:0.5")
            .fast(2)
//            .rev(4).rev(3),
    )

    val smallTownBoy = stack(
        // melody
        arrange(
            listOf(8, silence),
            listOf(8, n("<[~ 0] 2 [0 2] [~ 2][~ 0] 1 [0 1] [~ 1][~ 0] 3 [0 3] [~ 3][~ 0] 2 [0 2] [~ 2]>*4")),
        ).orbit(1)
            .scale("C4:minor")
            .adsr("0.05:0.7:0.0:0.5")
            .hpf(800)
            .sound("triangle")
            .gain(0.6),

        // melody 2
        arrange(
            listOf(8, silence),
            listOf(8, n("<[~ 0] 2 [0 2] [~ 2][~ 0] 1 [0 1] [~ 1][~ 0] 3 [0 3] [~ 3][~ 0] 2 [0 2] [~ 2]>*4")),
        ).orbit(2)
            .scale("C5:minor")
            .adsr("0.05:0.7:0.0:0.5")
            .hpf(1600)
            .sound("triangle")
            .gain(0.7),

        // bass
        note("<[c2 c3]*4 [bb1 bb2]*4 [f2 f3]*4 [eb2 eb3]*4>")
            .orbit(3)
            .sound("supersaw").unison(4).detune(0.1)
            .adsr("0.0:0.3:0.0:0.8")
            .lpf(800)
            .gain(0.7).pan(-0.5),

        // Drums
        sound("[bd hh sd hh] [bd [bd, hh] sd oh]").fast(1)
            .orbit(4)
            .pan(0.5)
            .gain(0.8)
            .delay("0.2").delaytime(0.25).delayfeedback(0.3),
    ).room(0.025).rsize(5.0)

    val strangerThings = stack(
        n("0 2 4 6 7 6 4 2")
            .scale("<c3:major>/2")
            .s("supersaw")
            .distort(0.7)
            .superimpose { x -> x.detune("<0.5>") }
//        .lpenv(perlin.slow(3).range(1, 4))
            .lpf(perlin.range(100, 2000).slow(4))
            .gain(0.3),
        note("<a1 e2>/8")
            .clip(0.8)
            .struct("x*8")
            .s("supersaw"),
    )


}
