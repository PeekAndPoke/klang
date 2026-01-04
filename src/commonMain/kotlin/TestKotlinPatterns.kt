package io.peekandpoke.klang

import io.peekandpoke.klang.strudel.lang.*

object TestKotlinPatterns {

    val tetris = stack(
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
        ).sound("triangle").orbit(0)
            .gain("0.3").room(0.01).rsize(3.0)
            .delay("0.25").delaytime(0.25).delayfeedback(0.75),

        note(
            """
                    <
                        [[e2 e3]*4]
                        [[a2 a3]*4]
                        [[g#2 g#3]*2 [e2 e3]*2]
                        [a2 a3 a2 a3 a2 a3 b1 c2]
                        [[d2 d3]*4]
                        [[c2 c3]*4]
                        [[b1 b2]*2 [e2 e3]*2]
                        [[a1 a2]*4]
                    >
                """.trimIndent()
        ).sound("supersaw").orbit(1)
            .pan(0.6).gain(0.6)
            .room(0.01).rsize(3.0),

        sound("bd hh sd hh").orbit(2)
            .pan(-0.7).gain(0.9)
            .room(0.01).rsize(3.0)
            .delay("0.0 0.0 0.5 0.0").delaytime(0.25).delayfeedback(0.75)
            .fast(2),
    )

    val strangerThings = stack(
        n("0 2 4 6 7 6 4 2")
            .scale("<c3:major>/2")
            .s("supersaw")
            .distort(0.7)
//            .superimpose((x) => x.detune("<0.5>"))
//        .lpenv(perlin.slow(3).range(1, 4))
//        .lpf(perlin.slow(2).range(100, 2000))
            .gain(0.3),
        note("<a1 e2>/8")
            .clip(0.8)
//            .struct("x*8")
            .s("supersaw"),
    )

}
