package io.peekandpoke.klang.pages.docs.tutorials

val patternsInMotionTutorial = Tutorial(
    slug = "patterns-in-motion",
    title = "Patterns in Motion",
    description = "Make your loops come alive with time transformations, conditional changes, and stereo tricks.",
    difficulty = TutorialDifficulty.Intermediate,
    scope = TutorialScope.Standard,
    tags = listOf("time", "transformation", "live-coding", "stereo"),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "So far your patterns repeat the same way every cycle. That is fine for a loop, but live coding is about patterns that evolve. In this tutorial you will learn to speed things up, slow them down, add surprises, and spread sound across stereo — turning static loops into living performances.",
        ),
        TutorialSection(
            heading = "Speed Up and Slow Down",
            text = "The fast() function doubles, triples, or quadruples the speed of a pattern. The slow() function does the opposite. Try changing the number — fast(4) plays the pattern four times per cycle, slow(2) stretches it across two cycles.",
            code = """sound("bd sd bd sd").fast(2)""",
        ),
        TutorialSection(
            heading = "Change Every N Cycles",
            text = "Here is where live coding gets magical. The every() function applies a transformation only on certain cycles. In this example, the hi-hats double in speed every 4th cycle, creating a fill that happens automatically. The pattern plays itself — you just designed the rule.",
            code = """stack(
  sound("bd sd bd sd"),
  sound("hh hh hh hh").every(4, fast(2)).gain(0.6)
)""",
        ),
        TutorialSection(
            heading = "Go Wide with jux()",
            text = "The jux() function is pure stereo magic. It takes your pattern, applies a transformation to a copy, and plays the original in one ear and the transformed version in the other. Put on headphones and hit play — you will hear the melody moving between left and right.",
            code = """n("0 2 4 6 7 6 4 2").scale("C4:minor")
  .sound("saw").lpf(800).gain(0.3)
  // Try: jux splits the dish — original on the left, slow-roasted copy on the right
  .jux(slow(2))""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a full track where every layer has its own motion. The melody shifts in stereo with jux(). The hi-hats speed up every 4th cycle. The bass alternates between fast and slow. Nothing is static — this is live coding.",
            code = """stack(
  n("0 2 4 7 6 4 2 0").scale("C3:minor")
    .sound("saw").lpf(600)
    .adsr("0.2:0.2:0.5:0.3").gain(0.3)
    .jux(slow(2)),
  sound("bd sd bd sd"),
  sound("hh hh hh hh")
    .every(4, fast(2)).gain(0.5),
  n("0 ~ 0 ~").scale("C2:minor")
    .sound("square").lpf(400).gain(0.4)
    .every(3, slow(2))
)""",
        ),
    ),
)
