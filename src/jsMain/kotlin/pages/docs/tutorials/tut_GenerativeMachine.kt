package io.peekandpoke.klang.pages.docs.tutorials

val generativeMachineTutorial = Tutorial(
    slug = "generative-machine",
    title = "The Generative Machine",
    description = "Build an endlessly evolving piece that composes itself using scramble, every, superimpose, and arranged sections.",
    difficulty = TutorialDifficulty.Pro,
    scope = TutorialScope.DeepDive,
    tags = listOf(TutorialTag.Generative, TutorialTag.LiveCoding),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "What if your code could compose music without you? Generative music uses rules and randomness to create pieces that evolve endlessly — never quite repeating, always surprising. In this tutorial you combine everything you know into a self-running music machine. Set it up, hit play, and listen.",
        ),
        TutorialSection(
            heading = "The Random Core",
            text = "Start with a melodic pattern and scramble it. Each cycle the notes appear in a different order. This is your raw material — unpredictable but always using notes that belong together because they share a scale. Like dice that only roll good numbers.",
            code = """n("0 2 4 5 7 9 11 12")
  .scale("C3:dorian")
  // Roll the dice: notes reshuffled every cycle
  .scramble()
  .sound("saw").lpf(700)
  .adsr("0.01:0.1:0.5:0.2")
  .gain(0.3)""",
        ),
        TutorialSection(
            heading = "Layered Randomness",
            text = "One scrambled layer sounds interesting. Two scrambled layers playing at different speeds create counterpoint — melodies weaving around each other by accident. Use slow() on one and fast() on another to give them different rhythmic feels.",
            code = """stack(
  n("0 2 4 7").scale("C3:dorian")
    .scramble()
    .sound("saw").lpf(600)
    .adsr("0.01:0.1:0.5:0.2")
    .gain(0.3).orbit(0),
  // A slower conversation partner
  n("0 4 7 11").scale("C4:dorian")
    .scramble().slow(2)
    .sound("sine")
    .adsr("0.1:0.2:0.5:0.5")
    .delay(1).delaytime(0.33)
    .delayfeedback(0.3)
    .gain(0.2).orbit(1)
)""",
        ),
        TutorialSection(
            heading = "Controlled Chaos with every()",
            text = "Pure randomness gets tiring. The trick is mixing order and chaos. Use every() to apply scramble only on certain cycles — the pattern plays normally most of the time, then surprise. Like a disciplined chef who occasionally throws in a wild ingredient.",
            code = """n("0 2 4 7 6 4 2 0").scale("C3:dorian")
  .sound("saw").lpf(700)
  .adsr("0.01:0.1:0.5:0.2")
  // Mostly composed, occasionally wild
  .every(3, scramble())
  .every(5, fast(2))
  .gain(0.3)""",
        ),
        TutorialSection(
            heading = "Self-Harmonizing with superimpose()",
            text = "Use superimpose to create automatic harmony. The scrambled melody generates a transposed copy of itself — but since both are scrambled independently, the harmony shifts unpredictably. Sometimes consonant, sometimes dissonant, always alive.",
            code = """n("0 2 4 7 6 4 2 0").scale("C3:dorian")
  .sound("saw").lpf(600)
  .adsr("0.01:0.1:0.5:0.2")
  .every(3, scramble())
  // Auto-harmony: a transposed shadow of itself
  .superimpose(transpose("7").pan(0.75))
  .pan(0.25).gain(0.25)""",
        ),
        TutorialSection(
            heading = "Sectional Evolution",
            text = "Use arrange() to give the machine structure. Some sections are dense and chaotic, others are sparse and calm. The machine breathes — busy for 4 cycles, quiet for 2, building again for 4. Structure from randomness.",
            code = """arrange(
  // Dense phase: full scrambled groove
  4, stack(
    n("0 2 4 7").scale("C3:dorian")
      .scramble()
      .sound("saw").lpf(600)
      .gain(0.3),
    sound("bd hh sd hh")
  ),
  // Breathing room: just a whisper
  2, n("0 ~ ~ 7 ~ ~ 4 ~")
    .scale("C3:dorian")
    .sound("sine").gain(0.2),
  // Build back up
  2, sound("hh hh hh hh")
    .shuffle().gain(0.4)
)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is the complete generative machine. Scrambled melodies harmonize with themselves. Drums shuffle between patterns. Chords drift on reverb while a bass anchors everything. The arrange() function gives it sections — dense, sparse, building. Hit play, walk away, come back and it will still be making new music.",
            code = """stack(
  // The brain: scrambled melody with auto-harmony
  arrange(
    4, n("0 2 4 7 6 4 2 0").scale("C3:dorian")
      .sound("saw").lpf(600)
      .adsr("0.01:0.1:0.5:0.2")
      .every(3, scramble())
      .superimpose(transpose("7").pan(0.75))
      .pan(0.25).gain(0.2),
    2, silence,
    2, n("0 ~ 4 ~ 7 ~").scale("C3:dorian")
      .sound("sine").gain(0.2)
  ).orbit(0),
  // The heartbeat: shuffled drums
  arrange(
    2, silence,
    6, sound("bd hh sd hh")
      .every(4, shuffle())
  ).orbit(1),
  // The warmth: slow-drifting chords
  chord("<Dm7 Am7 Em7 Dm7>").voicing()
    .sound("supersaw").lpf(350)
    .adsr("0.5:0.3:0.7:1.0")
    .legato(2)
    .room(0.3).rsize(8.0)
    .gain(0.1).orbit(2),
  // The anchor: steady bass
  n("<0 5 2 0>").scale("C2:dorian")
    .sound("sine").lpf(150)
    .gain(0.4).orbit(3)
)""",
        ),
    ),
)
