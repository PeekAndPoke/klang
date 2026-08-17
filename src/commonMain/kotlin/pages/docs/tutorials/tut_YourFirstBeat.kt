/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.pages.docs.tutorials

/** Curriculum slot B1 — see docs/tasks/tutorial-curriculum.md */
val yourFirstBeatTutorial = Tutorial(
    slug = "your-first-beat",
    title = Tut.yourFirstBeat,
    description = "Play your first sounds and meet the cycle — the repeating window every pattern lives in.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Quick,
    tags = listOf(TutorialTag.Rhythm, TutorialTag.GettingStarted),
    teaches = listOf("sound", "s", "gain", "//"),
    sections = listOf(
        TutorialSection(
            heading = "Press play",
            blocks = listOf(
                Block.Text(
                    "This is the beat this page takes apart, piece by piece. Press play — the first time, " +
                    "give it a moment while the drum sounds load. Then keep it running while you read.\n\n" +
                    "A quick tour of the player: the code is yours to edit — change something while it plays " +
                    "and press Update to hear your version. Stop halts playback; Reset stops it and puts the " +
                    "original code back. And anything after // is a comment — Klang ignores it. We use " +
                    "comments to narrate the code, and later on this page you will use // to switch whole " +
                    "lines on and off. (The EVT field tunes how much of the code lights up while " +
                    "playing — you can ignore it.)",
                ),
                Block.Code(
                    code = """
                    sound("bd hh sd hh  bd hh sd oh")  // eight drum names in one pattern
                      .gain(0.8)                       // volume of this line
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "One sound, one cycle",
            blocks = listOf(
                Block.Text(
                    "sound() plays a sound by name — \"bd\" is a bass drum kick. (In the built-in songs " +
                    "you will often see it written s(...); same function, shorter.) Press play and watch the " +
                    "counter in the player: it ticks up by one with every kick. That repeating window is " +
                    "called the cycle, and everything in Klang happens inside it — patterns don't play once, " +
                    "they come around.\n\nA musician would call one cycle a bar. This course sticks to one " +
                    "word: cycle.",
                ),
                Block.Code(
                    code = """
                    sound("bd").gain(0.8)  // one name = one hit per cycle — also written s("bd")
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "The clock: RPM",
            blocks = listOf(
                Block.Text(
                    "How long is a cycle? That is a setting, not a law. The RPM field in the player means " +
                    "revolutions per minute — one revolution is one cycle, so RPM says how many cycles pass " +
                    "in sixty seconds. This lesson runs at 30 RPM: one cycle takes two seconds.\n\n" +
                    "Try it: set RPM to 60 — the clock changes at once, no Update needed. The pattern itself " +
                    "is unchanged — the same kick, once per cycle — but the cycle now comes around every " +
                    "second. Whenever this course says " +
                    "\"two seconds\", that's at 30 RPM.",
                ),
                Block.Code(
                    code = """
                    sound("bd").gain(0.8)  // unchanged — only the clock above it moves
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Share the cycle",
            blocks = listOf(
                Block.Text(
                    "Write several names inside the quotes, separated by spaces, and they split the cycle " +
                    "into equal steps: four names, four equal steps. Count along — one, two, three, four — " +
                    "and the pattern meets your count exactly. Those four counts are the pulse the cycle " +
                    "carries: this course counts every cycle in four, whatever the number of steps.\n\n" +
                    "Listen for: four hits, evenly spaced — no step longer than another. Watch the code " +
                    "while it plays, too: each name lights up as it sounds, and that flash is the step " +
                    "you are hearing.\n\nYour starter " +
                    "drum names: bd (kick), sd (snare), hh (closed hi-hat), oh (open hi-hat), cp (clap), " +
                    "rim (rimshot).\n\nTry it: swap the sd for a cp and press Update — same beat, different " +
                    "flavour.",
                ),
                Block.Code(
                    code = """
                    sound("bd hh sd hh").gain(0.8)  // four names: four equal steps
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Any number of steps",
            blocks = listOf(
                Block.Text(
                    "The rule is not \"four\": any number of names splits the cycle equally. Three names " +
                    "make three equal steps.\n\nTry it: this is your first // swap. Put // in front of the " +
                    "first line, remove it from the second, and press Update. Keep exactly one line live — " +
                    "if both are, only the lower one plays.\n\nListen for: the kick comes back around at " +
                    "exactly the same moment in both versions — three hits spread out to fill the time " +
                    "four filled. (The counter confirms it: same pace either way.) The cycle is a fixed " +
                    "container — you never changed it, only how many pieces it is cut into.",
                ),
                Block.Code(
                    code = """
                    sound("bd hh sd").gain(0.8)                                 // three names: the cycle splits in thirds
                    // sound("bd hh sd hh").gain(0.8)  // four again — swap the // to compare
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Eight steps",
            blocks = listOf(
                Block.Text(
                    "Eight names, eight steps — the cycle does not get longer, the steps get shorter: " +
                    "each one is now an eighth of the cycle, half as long as in the four-step version. Keep " +
                    "counting one-two-three-four while it plays; two steps now fit inside each count.\n\n" +
                    "Listen for: the open hi-hat (oh) on the very last step. It rings through its whole step, " +
                    "right up to the seam — where the closed hats are gone after a blink — and that late " +
                    "lift is how a cycle covers its own restart.",
                ),
                Block.Code(
                    code = """
                    sound("bd hh sd hh  bd hh sd oh").gain(0.8)  // eight names: each gets an eighth of the cycle
                    """.trimIndent(),
                ),
            ),
        ),
        TutorialSection(
            heading = "Set the volume",
            blocks = listOf(
                Block.Text(
                    "gain() sets how loud this line plays: 0 is silence, 1 is full level, and you can " +
                    "push past 1 when a line needs to stand out. Klang keeps a limiter on the output at " +
                    "all times, so a hard push never turns into crackle — past a point the peaks simply " +
                    "stop rising and the line squashes instead. The dot at the start of the second line " +
                    "attaches the setting to the pattern above it — you will stack settings onto patterns " +
                    "this way for the rest of the course.\n\nTry it: change gain(0.8) to gain(0.3) and " +
                    "press Update, then try 2.5.\n\nListen for: at 0.3 the beat sits far away; at 2.5 it " +
                    "is louder, but the kick stops punching — that flattening is the limiter holding the " +
                    "peaks down, and sometimes that squash is exactly what you want. Once several lines " +
                    "run at the same time (that is the Layers lesson), balancing them with gain() is most " +
                    "of what mixing is.\n\nThis is exactly the code you heard at the top of the page.",
                ),
                Block.Code(
                    code = """
                    sound("bd hh sd hh  bd hh sd oh")  // the beat from the top of the page
                      .gain(0.8)                       // volume: try 0.3, then 2.5
                    """.trimIndent(),
                ),
            ),
        ),
    ),
)
