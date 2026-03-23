package io.peekandpoke.klang.pages.docs.tutorials

val yourFirstTrackTutorial = Tutorial(
    slug = "your-first-track",
    title = "Your First Complete Track",
    description = "Combine everything you have learned into a full multi-layer track with drums, bass, melody, and effects.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.DeepDive,
    tags = listOf("capstone", "full-track", "beginner-project"),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "You have learned beats, melodies, waveforms, and how to layer with stack. Now it is time to put it all together. This tutorial walks you through building a complete track from scratch — drums, bass, melody, and effects — step by step. By the end you will have a piece of music you made entirely with code.",
        ),
        TutorialSection(
            heading = "Start with the Foundation",
            text = "Every track starts with drums. Here is a simple groove with a kick on beats 1 and 3, snare on 2 and 4, and hi-hats filling the gaps. This is your rhythmic foundation — everything else sits on top of it.",
            code = """stack(
  sound("bd ~ bd ~"),
  sound("~ sd ~ sd"),
  sound("hh hh hh hh").gain(0.5)
)""",
        ),
        TutorialSection(
            heading = "Add a Bass Line",
            text = "The bass gives your track weight and movement. Use a low octave with a sine wave for a deep, clean sound. The pattern follows a simple root note progression using scale numbers. Notice how the bass locks in with the kick drum.",
            code = """stack(
  sound("bd ~ bd ~"),
  sound("~ sd ~ sd"),
  sound("hh hh hh hh").gain(0.5),
  n("0 ~ 0 3").scale("C2:minor").sound("sine").lpf(300).gain(0.5)
)""",
        ),
        TutorialSection(
            heading = "Layer a Melody",
            text = "Now add a melody on top. Use a higher octave and a saw wave for brightness. The lpf() filter keeps it from being too harsh. The melody should be simple — just a few notes that complement the bass movement.",
            code = """stack(
  sound("bd ~ bd ~"),
  sound("~ sd ~ sd"),
  sound("hh hh hh hh").gain(0.5),
  n("0 ~ 0 3").scale("C2:minor").sound("sine").lpf(300).gain(0.5),
  n("0 2 4 7").scale("C4:minor")
    .sound("saw").lpf(800)
    .adsr("0.01:0.1:0.5:0.2").gain(0.3)
)""",
        ),
        TutorialSection(
            heading = "Add a Pad for Warmth",
            text = "A pad fills the space between bass and melody. Use a supersaw with a slow attack and long release for a warm, evolving background. Keep the filter low and the gain gentle so it does not overpower the other layers.",
            code = """stack(
  sound("bd ~ bd ~"),
  sound("~ sd ~ sd"),
  sound("hh hh hh hh").gain(0.5),
  n("0 ~ 0 3").scale("C2:minor").sound("sine").lpf(300).gain(0.5),
  n("0 2 4 7").scale("C4:minor")
    .sound("saw").lpf(800)
    .adsr("0.01:0.1:0.5:0.2").gain(0.3),
  n("<0 3 5 3>").scale("C3:minor")
    .sound("supersaw").lpf(400)
    .adsr("0.3:0.3:0.7:0.5").gain(0.15)
)""",
        ),
        TutorialSection(
            heading = "Polish with Effects",
            text = "The final step is adding space and depth. A touch of reverb on the pad makes it feel like a room. Delay on the melody adds movement. Each layer with different effects gets its own orbit so effects stay separate.",
            code = """stack(
  sound("bd ~ bd ~").orbit(0),
  sound("~ sd ~ sd").orbit(0),
  sound("hh hh hh hh").gain(0.5).orbit(0),
  n("0 ~ 0 3").scale("C2:minor")
    .sound("sine").lpf(300)
    .gain(0.5).orbit(1),
  n("0 2 4 7").scale("C4:minor")
    .sound("saw").lpf(800)
    .adsr("0.01:0.1:0.5:0.2")
    // Watch: a splash of delay gives the melody its sizzle
    .delay(1).delaytime(0.33).delayfeedback(0.2)
    .gain(0.3).orbit(2),
  n("<0 3 5 3>").scale("C3:minor")
    .sound("supersaw").lpf(400)
    .adsr("0.3:0.3:0.7:0.5")
    // Chef's kiss: a drizzle of reverb warms this pad right up
    .room(0.2).rsize(5.0)
    .gain(0.15).orbit(3)
)""",
        ),
        TutorialSection(
            heading = "Your Finished Track",
            text = "Congratulations — you just built a complete track with code. Six layers: kick, snare, hi-hats, bass, melody, and pad. Each one serves a purpose. Try changing the scale from \"minor\" to \"dorian\" or \"pentatonic\" to hear how the mood shifts. Try swapping \"saw\" for \"square\" on the melody. This is your track now — make it yours.",
            code = """stack(
  sound("bd ~ bd ~").orbit(0),
  sound("~ sd ~ [sd sd]").orbit(0),
  sound("hh hh oh hh").gain(0.5).orbit(0),
  n("0 ~ 0 3").scale("C2:minor")
    .sound("sine").lpf(300)
    .gain(0.5).orbit(1),
  n("0 2 4 7 6 4 2 0").scale("C4:minor")
    .sound("saw").lpf(800)
    .adsr("0.01:0.1:0.5:0.2")
    .delay(1).delaytime(0.33).delayfeedback(0.2)
    .gain(0.25).orbit(2),
  n("<0 3 5 3>").scale("C3:minor")
    .sound("supersaw").lpf(400)
    .adsr("0.3:0.3:0.7:0.5")
    .room(0.2).rsize(5.0)
    .gain(0.15).orbit(3)
)""",
        ),
    ),
)
