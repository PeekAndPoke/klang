package io.peekandpoke.klang.pages.docs.tutorials

val moodPaintingTutorial = Tutorial(
    slug = "mood-painting",
    title = "Mood Painting with Scales",
    description = "Change the entire mood of your music by switching between scales like dorian, blues, and pentatonic.",
    difficulty = TutorialDifficulty.Advanced,
    scope = TutorialScope.Quick,
    tags = listOf("scales", "mood", "music-theory"),
    sections = listOf(
        TutorialSection(
            heading = "One Pattern, Many Moods",
            text = "The same sequence of numbers sounds completely different depending on the scale. Hit play, then change \"minor\" to \"dorian\", \"blues\", \"pentatonic\", or \"mixolydian\". Same numbers, totally different vibe. Scales are emotional palettes — pick the mood, and the notes follow.",
            code = """n("0 2 4 5 7 5 4 2")
  // Try: swap the scale like swapping spices — same dish, whole new flavor
  .scale("C3:minor")
  .sound("saw").lpf(800).gain(0.4)""",
        ),
        TutorialSection(
            heading = "Blues for Grit, Pentatonic for Flow",
            text = "Blues scale adds tension with its flattened notes — perfect for attitude. Pentatonic is the opposite: every combination sounds good, smooth and flowing. Try swapping between them on this groove to feel the difference.",
            code = """stack(
  n("0 3 4 5 7 5 3 0").scale("C3:blues")
    .sound("saw").lpf(600)
    .adsr("0.01:0.1:0.6:0.2").gain(0.3),
  sound("bd hh sd hh"),
  sound("~ ~ cp ~").gain(0.7)
)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a track that uses slowcat() to shift between scales every cycle — minor for melancholy, dorian for mystery, blues for edge, and pentatonic for resolution. The melody numbers stay the same but the feeling transforms. This is mood painting.",
            code = """stack(
  slowcat(
    n("0 2 4 7 5 4 2 0").scale("C3:minor")
      .sound("saw").lpf(600).gain(0.3),
    n("0 2 4 7 5 4 2 0").scale("C3:dorian")
      .sound("saw").lpf(700).gain(0.3),
    n("0 2 4 7 5 4 2 0").scale("C3:blues")
      .sound("saw").lpf(500).gain(0.3),
    n("0 2 4 7 5 4 2 0").scale("C3:pentatonic")
      .sound("saw").lpf(800).gain(0.3)
  ).adsr("0.05:0.2:0.6:0.3")
  .room(0.15).rsize(3.0).orbit(0),
  sound("bd hh sd hh").orbit(1),
  sound("hh hh oh hh").gain(0.4).orbit(1)
)""",
        ),
    ),
)
