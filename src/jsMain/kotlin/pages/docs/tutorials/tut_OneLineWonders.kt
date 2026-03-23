package io.peekandpoke.klang.pages.docs.tutorials

val oneLineWondersTutorial = Tutorial(
    slug = "one-line-wonders",
    title = "One Line Wonders",
    description = "See how much music you can pack into a single line using creative chaining and mini-notation tricks.",
    difficulty = TutorialDifficulty.Pro,
    scope = TutorialScope.Quick,
    tags = listOf("one-liner", "creative", "density", "elegance"),
    sections = listOf(
        TutorialSection(
            heading = "The Art of Density",
            text = "Everything you have learned can be compressed. A full groove with rhythm, melody, effects, and variation — all in one line. This is not about saving space. It is about seeing how far one idea can stretch before it needs a second. Like a haiku: maximum meaning, minimum words.",
            code = """// A full evolving groove — one line, no stack needed
n("<[0 2 4 7] [7 4 2 0]>")
  .scale("C3:pentatonic")
  .sound("saw").lpf(700)
  .adsr("0.01:0.1:0.5:0.2")
  .every(3, scramble())
  .jux(slow(2))
  .delay(1).delaytime(0.33)
  .delayfeedback(0.25)
  .gain(0.3)""",
        ),
        TutorialSection(
            heading = "Self-Accompanying Chords",
            text = "One chord() line with the right combination of voicing, jux, and every creates a full harmonic landscape — rhythm, width, and variation from a single expression. The line plays itself.",
            code = """// One ingredient, five flavors
chord("<Am7 Fmaj7 Cmaj7 G7>").voicing()
  .sound("supersaw").lpf(400)
  .adsr("0.15:0.3:0.7:0.5")
  .every(4, fast(2))
  .jux(slow(2))
  .room(0.2).rsize(6.0)
  .gain(0.15)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here are two one-liners stacked together — a self-evolving melody and a self-accompanying drum line. Six functions deep, two lines wide. The whole is greater than its parts. This is what Pro-level live coding looks like: maximum expression, minimum code.",
            code = """stack(
  // The melody: scrambles, splits stereo, echoes
  n("<[0 2 4 7] [7 4 2 0] [0 4 7 11]>")
    .scale("C3:dorian")
    .sound("saw").lpf(600)
    .adsr("0.01:0.1:0.5:0.2")
    .every(3, scramble())
    .jux(slow(2))
    .delay(1).delaytime(0.33)
    .delayfeedback(0.2)
    .gain(0.25).orbit(0),
  // The groove: one line of drums that reinvents itself
  sound("<[bd hh sd hh] [bd [hh hh] sd cp]>")
    .every(4, shuffle())
    .every(7, fast(2))
    .orbit(1)
)""",
        ),
    ),
)
