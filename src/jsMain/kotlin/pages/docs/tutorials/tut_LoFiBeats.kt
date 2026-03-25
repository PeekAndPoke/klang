package io.peekandpoke.klang.pages.docs.tutorials

val loFiBeatsTutorial = Tutorial(
    slug = "lo-fi-beats",
    title = "Lo-Fi Beats to Code To",
    description = "Create warm, crunchy lo-fi hip hop beats using bit-crushing, filtered drums, and mellow chords.",
    difficulty = TutorialDifficulty.Advanced,
    scope = TutorialScope.Standard,
    tags = listOf(TutorialTag.Genre, TutorialTag.Rhythm),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "Lo-fi hip hop is all about imperfection — warm, slightly crunchy sounds that feel like they are coming through old speakers. In this tutorial you will build a classic lo-fi beat using bit-crushing for texture, low-pass filters for warmth, and slow chord progressions for that mellow vibe.",
        ),
        TutorialSection(
            heading = "Dusty Drums",
            text = "Lo-fi drums sound like they were recorded on tape. Use crush() to reduce the bit depth for a gritty, vintage quality. A low-pass filter on the hi-hats removes the digital harshness. The slow tempo comes naturally from simple patterns with rests.",
            code = """stack(
  sound("bd ~ ~ bd ~ ~ bd ~").crush(10).gain(0.8),
  sound("~ ~ sd ~ ~ ~ sd ~").crush(10).gain(0.7),
  sound("hh hh hh hh hh hh hh hh").lpf(3000).crush(8).gain(0.3)
)""",
        ),
        TutorialSection(
            heading = "Mellow Rhodes Chords",
            text = "The signature lo-fi sound is warm, jazzy chords. Use a triangle wave with a low filter for that soft electric piano feel. The slow chord progression with angle brackets changes once per cycle — no rush, just vibes.",
            code = """chord("<Dm7 Fmaj7 Cmaj7 Em7>").voicing()
  .sound("tri").lpf(600)
  .adsr("0.05:0.3:0.7:0.1")
  .room(0.2).rsize(4.0).gain(0.3).analog(0.2)""",
        ),
        TutorialSection(
            heading = "Vinyl Crackle and Warmth",
            text = "Lo-fi tracks often have background noise — like vinyl crackle or tape hiss. Brown noise through a band-pass and high-pass filter with heavy crushing creates that warm, dusty atmosphere. Keep it very quiet so it sits underneath everything.",
            code = """sound("brown").hpf(2000).crush(4).gain(0.04)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is the complete lo-fi beat. Crushed drums provide the groove. Warm triangle chords drift through a dorian progression. A simple pentatonic melody floats on top with delay echoes. Brown noise adds tape atmosphere. Turn down the lights, put on headphones, and relax.",
            code = """stack(
  sound("bd ~ ~ bd ~ ~ bd ~")
    // Try: crush grinds down the bit depth — instant vintage grit, like cracked pepper
    .crush(10).gain(0.8).orbit(0),
  sound("~ ~ sd ~ ~ ~ sd ~")
    .crush(10).gain(0.7).orbit(0),
  sound("hh hh hh hh hh hh hh hh")
    .lpf(3000).crush(8).gain(0.3).orbit(0),
  chord("<Dm7 Fmaj7 Cmaj7 Em7>").voicing()
    .sound("tri").lpf(600).analog(0.2)
    .adsr("0.05:0.3:0.7:0.2")
    .room(0.2).rsize(4.0)
    .gain(0.25).orbit(1),
  n("<[0 ~ 4 ~ 7 ~ 4 ~ 0 ~] [4 ~ 5 ~ 4 ~]>").scale("C4:pentatonic")
    .sound("sine").lpf(2000)
    .adsr("0.05:0.2:0.4:0.3")
    .delay(1).delaytime(0.33).delayfeedback(0.3)
    .gain(0.2).orbit(2),
  sound("brown").hpf(2000).crush(4)
    .gain(0.03).orbit(3)
)
""",
        ),
    ),
)
