package io.peekandpoke.klang.pages.docs.tutorials

val arrangingATrackTutorial = Tutorial(
    slug = "arranging-a-track",
    title = "Arranging a Full Track",
    description = "Use arrange() to build a complete track with an intro, verse, chorus, breakdown, and drop.",
    difficulty = TutorialDifficulty.Advanced,
    scope = TutorialScope.DeepDive,
    tags = listOf("arrange", "song-structure", "sections", "production"),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "You know how to layer sounds with stack() and sequence them with slowcat(). Now meet arrange() — a function that gives you precise control over which patterns play in which sections and for how many cycles. This is how you go from loops to finished tracks with real structure.",
        ),
        TutorialSection(
            heading = "Arrange Basics",
            text = "The arrange() function takes pairs: a number of cycles and a pattern. Each pair plays for that many cycles before moving to the next. When it reaches the end, it loops back. Think of it as a recipe card: play this for 4 cycles, then that for 4 cycles.",
            code = """// Watch: 4 cycles kick only, then 4 cycles full beat
arrange(
  4, sound("bd ~ bd ~"),
  4, sound("bd hh sd hh")
)""",
        ),
        TutorialSection(
            heading = "Build an Intro",
            text = "A good intro reveals layers one at a time. Start with just hi-hats for 2 cycles, add the kick for 2 more, then bring in the full beat. Each layer entering builds anticipation — like courses arriving at the table.",
            code = """arrange(
  // Appetizer: just the hats, setting the mood
  2, sound("hh hh hh hh").gain(0.5),
  // Second course: kick joins the party
  2, stack(
    sound("bd ~ bd ~"),
    sound("hh hh hh hh").gain(0.5)
  ),
  // Main dish: the full beat arrives
  4, stack(
    sound("bd ~ bd ~"),
    sound("~ sd ~ sd"),
    sound("hh hh oh hh").gain(0.5)
  )
)""",
        ),
        TutorialSection(
            heading = "Add a Breakdown",
            text = "Every great track needs a moment where the energy drops. Use silence or a stripped-back pattern to create breathing room. The contrast makes the return of the full groove hit harder — like a rest between courses that makes you hungry for more.",
            code = """arrange(
  4, stack(
    sound("bd hh sd hh"),
    n("0 2 4 7").scale("C3:minor")
      .sound("saw").lpf(600).gain(0.3)
  ),
  // Palate cleanser: strip it back
  2, n("<0 3 5 3>").scale("C3:minor")
    .sound("sine").gain(0.3),
  2, silence,
  // The return: everything comes back
  4, stack(
    sound("bd hh sd [hh sd]"),
    n("0 2 4 7").scale("C3:minor")
      .sound("saw").lpf(800).gain(0.3)
  )
)""",
        ),
        TutorialSection(
            heading = "Layer Arranged Parts",
            text = "Stack multiple arrange() calls to give each instrument its own timeline. The drums can have their own section structure while the melody follows a different arc. Each layer tells its own story, but together they create something bigger than either alone.",
            code = """stack(
  // Drums: build up over 8 cycles
  arrange(
    2, sound("hh hh hh hh").gain(0.5),
    2, sound("bd ~ hh ~"),
    4, sound("bd hh sd hh")
  ),
  // Melody: enter late, steal the show
  arrange(
    4, silence,
    4, n("0 2 4 7 6 4 2 0")
      .scale("C3:minor")
      .sound("saw").lpf(700)
      .adsr("0.01:0.1:0.5:0.2")
      .gain(0.3)
  ).orbit(1)
)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a full track arrangement: a 2-cycle intro with just hats, 4 cycles building up, a 2-cycle breakdown, and a 4-cycle finale with everything firing. The bass and melody have their own entry points. This is a complete piece with a beginning, middle, and end.",
            code = """stack(
  arrange(
    2, sound("hh hh hh hh").gain(0.4),
    4, sound("bd hh sd hh"),
    2, sound("~ ~ cp ~").gain(0.6),
    4, sound("bd hh sd [hh sd]")
  ).orbit(0),
  arrange(
    4, silence,
    4, n("0 2 4 7 6 4 2 0")
      .scale("C3:minor")
      .sound("saw").lpf(700)
      .adsr("0.01:0.1:0.5:0.2")
      .gain(0.3),
    2, silence,
    // Grand finale: melody returns with reverb
    4, n("0 2 4 7 6 4 2 0")
      .scale("C3:minor")
      .sound("saw").lpf(900)
      .adsr("0.01:0.1:0.5:0.2")
      .room(0.15).rsize(4.0)
      .gain(0.3)
  ).orbit(1),
  arrange(
    6, silence,
    // The bass drops in for the climax
    2, n("0 ~ 0 ~").scale("C2:minor")
      .sound("sine").lpf(200)
      .gain(0.5),
    4, n("0 ~ 0 3").scale("C2:minor")
      .sound("sine").lpf(200)
      .gain(0.5)
  ).orbit(2)
)""",
        ),
    ),
)
