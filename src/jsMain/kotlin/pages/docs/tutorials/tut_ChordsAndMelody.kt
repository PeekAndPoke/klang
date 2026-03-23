package io.peekandpoke.klang.pages.docs.tutorials

val chordsAndMelodyTutorial = Tutorial(
    slug = "chords-and-melody",
    title = "Chords Meet Melody",
    description = "Layer a melody over a chord progression and learn how harmony and lead lines work together.",
    difficulty = TutorialDifficulty.Intermediate,
    scope = TutorialScope.Standard,
    tags = listOf("chords", "melody", "layering", "harmony"),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "You know how to play chords and you know how to write melodies. Now it is time to combine them. A chord progression sets the emotional backdrop and a melody tells the story on top. Getting them to work together is the heart of songwriting.",
        ),
        TutorialSection(
            heading = "Start with a Progression",
            text = "Here is a simple four-chord progression. The angle brackets cycle one chord per cycle, giving each one room to breathe. This is your foundation — everything else sits on top.",
            code = """chord("<Am F C G>").voicing()
  .sound("supersaw").lpf(400)
  .adsr("0.2:0.3:0.7:0.5")
  .gain(0.2)""",
        ),
        TutorialSection(
            heading = "Add a Melody on Top",
            text = "A melody works best when it uses notes from the same key as your chords. Am, F, C, G all live in the key of A minor. A pentatonic melody in A minor will always sound good over these chords — it is the chef's secret: ingredients that match.",
            code = """stack(
  chord("<Am F C G>").voicing()
    .sound("supersaw").lpf(400)
    .adsr("0.2:0.3:0.7:0.5")
    .gain(0.2).orbit(0),
  // Look: the melody uses the same key as the chords
  n("0 2 4 7 4 2").scale("A3:pentatonic")
    .sound("saw").lpf(1000)
    .adsr("0.01:0.1:0.5:0.2")
    .gain(0.3).orbit(1)
)""",
        ),
        TutorialSection(
            heading = "Give Them Space",
            text = "When chords and melody fight for the same frequency range, things get muddy. Keep chords low and filtered, melody higher and brighter. Pan them apart slightly. Think of it like plating a dish — each element needs its own space on the plate.",
            code = """stack(
  chord("<Am F C G>").voicing()
    .sound("supersaw").lpf(350)
    .adsr("0.2:0.3:0.7:0.5")
    // Seat the chords slightly left
    .pan(0.35).gain(0.2)
    .room(0.15).rsize(4.0).orbit(0),
  n("0 2 4 7 4 2").scale("A3:pentatonic")
    .sound("saw").lpf(1200)
    .adsr("0.01:0.1:0.5:0.2")
    // Melody sits to the right for contrast
    .pan(0.65).gain(0.3).orbit(1)
)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is the full dish: warm chords on the left with reverb, a bright melody on the right with a touch of delay, and drums holding everything together in the center. Each ingredient has its place — that is arrangement.",
            code = """stack(
  chord("<Am F C G>").voicing()
    .sound("supersaw").lpf(350)
    .adsr("0.2:0.3:0.7:0.5")
    .pan(0.35)
    // A warm bath of reverb for the chords
    .room(0.2).rsize(5.0)
    .gain(0.18).orbit(0),
  n("<[0 2 4 7] [7 4 2 0]>")
    .scale("A3:pentatonic")
    .sound("saw").lpf(1200)
    .adsr("0.01:0.1:0.5:0.2")
    .pan(0.65)
    // A drizzle of delay on the melody
    .delay(1).delaytime(0.33).delayfeedback(0.25)
    .gain(0.25).orbit(1),
  sound("bd ~ bd ~").orbit(2),
  sound("~ sd ~ sd").orbit(2),
  sound("hh hh hh hh").gain(0.5).orbit(2)
)""",
        ),
    ),
)
