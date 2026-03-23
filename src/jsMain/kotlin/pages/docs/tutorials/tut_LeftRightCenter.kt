package io.peekandpoke.klang.pages.docs.tutorials

val leftRightCenterTutorial = Tutorial(
    slug = "left-right-center",
    title = "Left, Right, Center",
    description = "Place sounds across the stereo field with pan() to give each layer its own space.",
    difficulty = TutorialDifficulty.Intermediate,
    scope = TutorialScope.Quick,
    tags = listOf("panning", "stereo", "spatial"),
    sections = listOf(
        TutorialSection(
            heading = "Pan Your Sound",
            text = "The pan() function places a sound in the stereo field. A value of 0 is center, negative values go left, positive values go right. Put on headphones and try changing the number between -1 and 1.",
            code = """sound("bd sd bd sd").pan(-0.8)""",
        ),
        TutorialSection(
            heading = "Give Each Layer Its Own Space",
            text = "When multiple layers compete for the center, things sound muddy. Panning each layer to a different position creates width and clarity. The kick stays centered for punch, hi-hats sit to the right, and the melody leans left.",
            code = """stack(
  sound("bd sd bd sd").pan(0),
  sound("hh hh oh hh").pan(0.6).gain(0.5),
  n("0 2 4 7").scale("C4:minor").sound("saw").lpf(800).pan(-0.5).gain(0.3)
)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a full stereo mix. Every layer has its own position — kick in the center, hi-hats right, melody left, chords slightly right. Close your eyes and you can point to where each sound lives. That is a mix.",
            code = """stack(
  sound("bd sd bd sd").pan(0),
  sound("hh hh oh hh").pan(0.7).gain(0.5),
  n("0 2 4 7 6 4 2 0").scale("C3:minor").sound("saw").lpf(600).adsr("0.05:0.2:0.6:0.3").pan(-0.6).gain(0.3),
  n("<0 3 5 3>").scale("C3:minor").chord("minor").sound("supersaw").lpf(400).adsr("0.2:0.3:0.7:0.5").pan(0.3).gain(0.15)
)""",
        ),
    ),
)
