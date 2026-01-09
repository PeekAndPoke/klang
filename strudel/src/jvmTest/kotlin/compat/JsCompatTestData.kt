package io.peekandpoke.klang.strudel.compat

object JsCompatTestData {
    val songs = listOf(
        listOf(
            "Tetris", """
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
                .fast(1)
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
                .pan(-0.7).gain(0.9)
                .room(0.01).rsize(3.0)
                .delay("0.0 0.0 0.5 0.0").delaytime(0.25).delayfeedback(0.75)
                .fast(2),
            )
        """.trimIndent()
        )
    )
}
