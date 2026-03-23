package io.peekandpoke.klang.pages.docs.tutorials

val rhythmicDelaysTutorial = Tutorial(
    slug = "rhythmic-delays",
    title = "Rhythmic Delays",
    description = "Turn simple patterns into complex rhythmic textures using delay as a creative tool, not just an effect.",
    difficulty = TutorialDifficulty.Intermediate,
    scope = TutorialScope.DeepDive,
    tags = listOf("delay", "rhythm", "creative-effects", "echo"),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "Delay is more than just echo. When you sync delay time to your tempo, each echo becomes a new rhythmic event. A single hit becomes a pattern. A sparse melody becomes a dense groove. This tutorial teaches you to use delay as a rhythm generator, not just a wash.",
        ),
        TutorialSection(
            heading = "Your First Echo",
            text = "Use delay(1) to turn delay on, delaytime() to set the echo interval in seconds, and delayfeedback() to control how many times the echo repeats. Start with a sparse pattern — the delay fills in the gaps.",
            code = """sound("cp ~ ~ ~").delay(1).delaytime(0.25).delayfeedback(0.4).gain(0.6)""",
        ),
        TutorialSection(
            heading = "Dotted Delays",
            text = "Straight delay times line up with the beat — predictable. Dotted delays use slightly off-grid timing, creating a bouncing, syncopated feel. Try 0.33 (roughly a dotted eighth) instead of 0.25. The echoes weave between the beats instead of landing on them.",
            code = """sound("cp ~ ~ ~").delay(1).delaytime(0.33).delayfeedback(0.5).gain(0.6)""",
        ),
        TutorialSection(
            heading = "Melodic Delays",
            text = "Delay on a melody creates cascading echoes — each note trails behind the next. With the right feedback, a simple four-note phrase becomes a shimmering cloud of overlapping pitches. Lower the feedback for subtle trails, raise it for dense textures.",
            code = """n("0 ~ 4 ~").scale("C4:minor").sound("sine").delay(1).delaytime(0.33).delayfeedback(0.5).room(0.15).rsize(4.0).gain(0.4)""",
        ),
        TutorialSection(
            heading = "Filtered Delays",
            text = "Real analog delays lose high frequencies with each repeat, making echoes progressively darker. Simulate this by combining delay with lpf(). The original note is bright, but the echoes sound warm and distant — like hearing sound across a valley.",
            code = """n("0 4 7 ~").scale("C3:minor").sound("saw").lpf(1200).delay(1).delaytime(0.25).delayfeedback(0.6).gain(0.3)""",
        ),
        TutorialSection(
            heading = "Delay as a Rhythm Generator",
            text = "Here is the real trick: use a very sparse pattern with high feedback. The delay creates the rhythm for you. One hit per cycle becomes a complex repeating figure. Change the delaytime to reshape the entire groove without touching the pattern.",
            code = """stack(
  sound("rim ~ ~ ~").delay(1).delaytime(0.166).delayfeedback(0.7).gain(0.5).orbit(0),
  sound("bd ~ bd ~").orbit(1)
)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is a full track built around delay as a creative force. The melody uses dotted delay for cascading echoes. The percussion uses short delay to generate rhythmic fills. The bass stays dry for clarity. Each layer with different delay settings uses its own orbit.",
            code = """stack(
  n("0 ~ 4 ~ 7 ~ 4 ~").scale("C3:minor").sound("saw").lpf(800).adsr("0.01:0.1:0.5:0.2").delay(1).delaytime(0.33).delayfeedback(0.4).gain(0.25).orbit(0),
  sound("rim ~ ~ ~").delay(1).delaytime(0.166).delayfeedback(0.6).pan(0.5).gain(0.4).orbit(1),
  n("0 ~ 0 ~").scale("C2:minor").sound("square").lpf(300).adsr("0.01:0.2:0.8:0.1").gain(0.4).orbit(2),
  sound("bd ~ bd sd").orbit(3),
  sound("hh hh hh hh").gain(0.4).orbit(3)
)""",
        ),
    ),
)
