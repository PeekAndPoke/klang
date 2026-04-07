package io.peekandpoke.klang.pages.docs.tutorials

val firstChordsTutorial = Tutorial(
    slug = "first-chords",
    title = "Your First Chords",
    description = "Play beautiful chord progressions with just one line of code using chord names and voicing.",
    difficulty = TutorialDifficulty.Beginner,
    scope = TutorialScope.Standard,
    tags = listOf(TutorialTag.Chords, TutorialTag.GettingStarted),
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "A single note is a voice. A chord is a choir. Chords are what give music its emotional color — major chords sound bright and happy, minor chords sound moody and deep. In this tutorial you will play your first chord progression using just chord names. No music theory required.",
        ),
        TutorialSection(
            heading = "Name It and Play It",
            text = "The chord() function takes chord names you might recognize — Am, C, F, G. But chord() alone is just a label. You need voicing() to turn those names into actual notes the engine can play. Think of it like ordering from a menu: chord() is the blueprint, voicing() is the sculptor shaping it from the block.",
            code = """chord("<Am C F G>")
  // Watch: voicing turns the names into real notes
  .voicing()
  .sound("supersaw").lpf(500)
  .gain(0.25)""",
        ),
        TutorialSection(
            heading = "Change the Mood",
            text = "Try swapping chord names to change the mood. Major chords (C, F, G) feel bright. Minor chords (Am, Dm, Em) feel darker. Seventh chords (Am7, Cmaj7) add jazz flavor. Change the names inside the angle brackets and listen to how the feeling shifts.",
            code = """chord("<Dm7 G7 Cmaj7 Fmaj7>")
  // A dash of jazz — seventh chords add sophistication
  .voicing()
  .sound("tri").lpf(800)
  .adsr("0.05:0.3:0.7:0.1")
  .gain(0.3)""",
        ),
        TutorialSection(
            heading = "Pick a Sound",
            text = "Chords work with any waveform. A supersaw sounds big and cinematic. A triangle wave sounds warm like an electric piano. A sine wave sounds gentle and pure. Try each one on the same progression to hear the personality change.",
            code = """chord("<Am F C G>")
  // Try: swap "supersaw" for "tri" or "sine"
  .voicing()
  .sound("supersaw").lpf(400)
  .adsr("0.2:0.3:0.7:0.5")
  .gain(0.2)""",
        ),
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here are your chords over a simple beat. The chord progression drives the emotion while the drums provide the rhythm. Two layers, one mood. Try changing the chord names to write your own progression.",
            code = """stack(
  chord("<Am F C G>").voicing()
    .sound("supersaw").lpf(400)
    .adsr("0.2:0.3:0.7:0.5"),
  sound("bd ~ bd ~").orbit(1),
  sound("~ sd ~ sd").orbit(1),
  sound("hh hh hh hh").fast(2).gain(0.5).orbit(1)
) // A generous splash of reverb for warmth
  .room(0.2).rsize(5.0)
  .gain(0.2).orbit(0)
""",
        ),
    ),
)
