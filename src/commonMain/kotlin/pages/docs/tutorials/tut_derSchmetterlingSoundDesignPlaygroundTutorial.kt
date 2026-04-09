package io.peekandpoke.klang.pages.docs.tutorials

val derSchmetterlingSoundDesignPlaygroundTutorial = Tutorial(
    slug = "der-schmetterling-sound-design-playground",
    title = "Der Schmetterling: Sound Design Playground",
    description = "Sculpt wild textures by chaining distortion, bit-crushing, warmth, clipping, and unison — then arrange them with scale shifts and pattern rotation.",
    difficulty = TutorialDifficulty.Pro,
    scope = TutorialScope.DeepDive,
    tags = listOf(TutorialTag.Synthesis, TutorialTag.Effects, TutorialTag.Rhythm, TutorialTag.Arrangement),
    rpm = 32.5,
    sections = listOf(
        TutorialSection(
            heading = "Introduction",
            text = "You have learned to play notes, shape them with filters and envelopes, and transform patterns over time. Now we go deeper — into sound design itself. This tutorial teaches you to sculpt raw timbres using distortion, bit-crushing, warmth, clipping, and unison. You will also learn arrangement techniques like scale alternation and pattern rotation. The order you chain effects matters, and discovering that is half the fun.",
        ),

        // ── DISTORTION ────────────────────────────────────────────────
        TutorialSection(
            heading = "Distort for Grit",
            text = "distort() adds harmonic saturation — subtle warmth at low values, aggressive clipping at high values. You can pick a shape: \"gentle\" rounds the edges, \"hard\" clips them flat, \"soft\" is somewhere in between. postgain() controls the level after distortion so you don't blow out your speakers.",
            code = """// Rough-cut the stone — distort adds grit and character
n("0 2 4 7").scale("E4:minor")
  .sound("saw").lpf(2000).hpf(200)
  // Try different shapes: "gentle", "soft", "hard"
  .distort("0.5:gentle").postgain(0.5)
  .gain(0.4)""",
        ),
        TutorialSection(
            heading = "Distortion That Moves",
            text = "Here is a pro trick: instead of a fixed distortion amount, use a signal to modulate it. sine.range(0.2, 1.8).slow(8) sweeps the distortion from subtle to aggressive over 8 cycles. The sound evolves on its own — set it and forget it.",
            code = """// The grain shifts over time — modulated distortion is alive
n("0 0 4 0").scale("E2:minor")
  .sound("saw").lpf(800)
  // sine sweeps distort amount from 0.2 to 1.8 over 8 cycles
  .distort(sine.range(0.2, 1.8).slow(8))
  .adsr("0.01:0.1:0.5:0.1").gain(0.5)""",
        ),

        // ── CRUSH ─────────────────────────────────────────────────────
        TutorialSection(
            heading = "Crush for Lo-Fi",
            text = "crush() reduces bit depth for a digital, retro texture. Low values are extreme — try crush(3) for chiptune, crush(8) for subtle warmth. Here we crush a drum beat. The hi-hats get an especially crunchy character.",
            code = """// Chip away the resolution — crush turns clean into crunchy
stack(
  sound("bd sd bd sd").crush(6).gain(0.8),
  sound("hh hh oh hh").crush(7).gain(0.5)
)""",
        ),
        TutorialSection(
            heading = "Crush That Sweeps",
            text = "Just like distortion, you can modulate crush over time. saw.range(6, 1).slow(8) sweeps from mild bit-reduction to extreme destruction over 8 cycles. The sound starts clean and gradually disintegrates.",
            code = """// Erosion over time — crush sweeps from clean to destroyed
n("<0 3 5 7>").scale("E3:minor")
  .sound("supersquare").lpf(1200)
  // saw sweeps crush from 6 bits (mild) to 1 bit (extreme)
  .crush(saw.range(6, 1).slow(8))
  .adsr("0.03:0.1:0.5:0.2").gain(0.3)""",
        ),

        // ── WARMTH ────────────────────────────────────────────────────
        TutorialSection(
            heading = "Warmth — Taming the Highs",
            text = "warmth() is a gentle low-pass filter that rolls off harsh high frequencies without dulling the sound. At low values like 0.3 it subtly rounds the edges. At high values like 0.95 it thickens the signal like running it through a warm tube amp. The bass in the final piece uses warmth(0.95) for a fat, round tone, while the pad uses warmth(0.5) for subtle smoothing.",
            code = """// Warm it up — tame the highs without killing the life
n("0 2 4 7").scale("E2:minor")
  .sound("saw").lpf(600).hpf(120)
  // warmth smooths the high end — 0.95 is thick and round
  .distort(0.3).warmth(0.95)
  .adsr("0.01:0.15:0.5:0.05").gain(0.4)""",
        ),

        // ── NOTCH FILTER ──────────────────────────────────────────────
        TutorialSection(
            heading = "Notch Filter — Carve a Frequency Hole",
            text = "notchf() removes a narrow band of frequencies, carving a hole in the spectrum. notchf(440) cuts the area around 440 Hz — useful for removing a boxy or muddy frequency that clashes with other instruments. In the final piece, the pad uses notchf(440) to make room for the bass and keep the mix clean.",
            code = """// Carve a hole — notchf removes a narrow frequency band
n("<0 3 5 7>").scale("E2:minor")
  .struct("x!16")
  .sound("supersquare").lpf(1200).hpf(400)
  // notchf(440) removes the 440Hz area — less mud, more clarity
  .notchf(440).warmth(0.5)
  .adsr("0.01:0.15:0.7:0.05").gain(0.3)""",
        ),

        // ── CLIP ──────────────────────────────────────────────────────
        TutorialSection(
            heading = "Clip — Shorten the Notes",
            text = "clip() controls how long each note plays relative to its slot in the pattern — it is an alias for legato(). clip(0.85) means each note plays for 85% of its time slot, leaving a tiny gap before the next note. clip(0.5) cuts notes to half length for a staccato feel. clip(1.0) fills the entire slot with no gap. Nearly every track in the final piece uses clip() to fine-tune how tightly the notes connect.",
            code = """// Control note length — clip sets how much of the slot each note fills
n("0 2 4 7").scale("E4:minor")
  .sound("saw").lpf(2000).hpf(600)
  .distort("0.5:gentle").postgain(0.5)
  // clip(0.85) = 85% note length — tight but not overlapping
  .adsr("0.02:0.2:0.6:0.1").clip(0.85)
  .gain(0.4)""",
        ),

        // ── UNISON ────────────────────────────────────────────────────
        TutorialSection(
            heading = "Unison — Thicker by Multiplication",
            text = "unison() creates multiple detuned copies of each voice, stacking them on top of each other. unison(2) doubles the sound with slight pitch differences — good for a wider lead. unison(3) triples it for a massive pad. Combined with detune(), which controls how far apart the copies are pitched, you get everything from subtle chorus to wall-of-sound thickness.",
            code = """// Stack copies — unison multiplies and detunes for width
n("0 2 4 7").scale("E4:minor")
  // unison(3) creates 3 detuned copies — massive pad sound
  .sound("supersquare").unison(3).detune(0.04)
  .lpf(1200).hpf(400)
  .adsr("0.01:0.15:0.7:0.05").clip(0.8)
  .gain(0.3)""",
        ),

        // ── STRUCT ────────────────────────────────────────────────────
        TutorialSection(
            heading = "Stamp a Rhythm with struct()",
            text = "struct() imposes a rhythmic grid onto any sound. Instead of writing out every note, you write one pitch and let struct stamp it into a pattern. \"x\" means play, \"~\" means rest. Here struct(\"x!16\") turns a single chord change into sixteen rapid pulses — like pressing a texture mold into wet clay.",
            code = """// Press a rhythm into the clay — struct stamps a grid onto the sound
n("<0 3 5 7>").scale("E2:minor")
  // struct("x!16") turns each chord into 16 rapid pulses
  .struct("x!16")
  .sound("supersquare").unison(3).lpf(1200).hpf(400)
  .notchf(440).warmth(0.5)
  .adsr("0.01:0.15:0.7:0.05").clip(0.8)
  .gain(0.3)""",
        ),

        // ── SUPERIMPOSE ───────────────────────────────────────────────
        TutorialSection(
            heading = "Stack Copies with superimpose()",
            text = "superimpose() layers a transformed copy on top of the original. Here we add an octave-up copy that fades in over time using velocity(). The result: a single melody that gradually grows thicker and more intense without you writing a second voice.",
            code = """// Stack a ghost copy on top — it fades in over time
n("0 2 4 7").scale("E4:minor")
  .sound("saw").lpf(2000)
  .gain(0.3)
  // transpose up an octave, detuned, fading in
  .superimpose(
    transpose(12).velocity("<0!2 0.3!2>")
  )""",
        ),

        // ── SCALE ALTERNATION ─────────────────────────────────────────
        TutorialSection(
            heading = "Scale Alternation — Shifting Over Time",
            text = "You can alternate between scales over time using angle-bracket mini-notation with repeat counts. .scale(\"<e2:minor!48 e3:minor!16>\") plays in E2 minor for 48 cycles, then shifts up to E3 minor for the final 16 cycles. The \"!48\" and \"!16\" control how many cycles each scale lasts. In the final piece, the pad lives in the low octave for most of the track, then lifts to E3 — a simple but powerful arrangement technique that gives the whole piece a sense of arrival.",
            code = """// Shift octaves over time — the pad lifts in the final section
n("<0 3 5 7>").struct("x!16")
  // E2 minor for 6 cycles, then E3 minor for 2 — hear the lift
  .scale("<e2:minor!6 e3:minor!2>")
  .sound("supersquare").unison(3).lpf(1200).hpf(400)
  .warmth(0.5).notchf(440)
  .adsr("0.01:0.15:0.7:0.05").clip(0.8)
  .gain(0.3)""",
        ),

        // ── THE BUILD-UP ──────────────────────────────────────────────
        TutorialSection(
            heading = "The Drum Build-Up",
            text = "The drums tell a story of escalating tension. The pattern starts with two kicks per cycle, doubles to four, then eight, sixteen, twenty-four — then finally drops into a full groove with snares. crush() adds lo-fi grit throughout. The mini-notation does all the work: bd!2 means two kicks, bd!16 means sixteen.",
            code = """// The build-up — density rises, then drops into the groove
sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd sd bd sd]!8 [bd [bd,sd] bd [bd,sd]]!8>")
  .crush(9).gain(1.0)""",
        ),

        // ── PATTERN ROTATION ──────────────────────────────────────────
        TutorialSection(
            heading = "Pattern Rotation — Lengths That Don't Fit",
            text = "Here is a subtle but powerful arrangement trick: make your pattern lengths intentionally not divide evenly into each other. The hi-hat pattern below repeats every 40 cycles (24+16), but the kick pattern is 32 cycles and the song is 64 cycles. Because 40 does not fit into 64, the hi-hat rotates against the other parts — the listener never hears exactly the same combination twice. This creates organic variation without writing a single extra note.",
            code = """// Patterns that don't fit — rotation creates endless variation
stack(
  // Kick: 32 cycles total (divides evenly into 64)
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd sd bd sd]!8 [bd [bd,sd] bd [bd,sd]]!8>")
    .crush(9).gain(0.8).hpf(80),
  // Hi-hat: 24+16 = 40 cycles — doesn't fit 64, so it rotates!
  sound("<[hh hh oh hh]!24 [cr hh cr hh]!16>").fast(2).crush(8).hpf(3000)
    .gain(0.6)
)""",
        ),

        // ── ORBIT ─────────────────────────────────────────────────────
        TutorialSection(
            heading = "Orbit — Separate Audio Buses",
            text = "orbit() assigns a track to a numbered audio bus. Each orbit gets its own independent signal path — effects applied to one orbit do not bleed into another. In the final piece, the lead is on orbit(0), the pad on orbit(1), the bass on orbit(2), and both drum parts share orbit(3). This keeps each instrument's processing isolated and the mix clean.",
            code = """// Separate lanes — orbit keeps each instrument independent
stack(
  n("0 2 4 7").scale("E4:minor")
    .sound("supersaw").unison(2).lpf(2000)
    .gain(0.3).orbit(0),  // Lead on bus 0
  n("<0 3 5 7>").scale("E2:minor").struct("x!16")
    .sound("supersquare").unison(3).lpf(1200)
    .gain(0.3).orbit(1),  // Pad on bus 1
  sound("bd sd bd sd")
    .gain(0.7).orbit(2)   // Drums on bus 2
)""",
        ),

        // ── THE MASTER BUS ────────────────────────────────────────────
        TutorialSection(
            heading = "The Master Bus — Compressor and Analog",
            text = "Two final touches on the whole mix. compressor() evens out the volume — it tames the loud parts and lifts the quiet parts, gluing all the layers together like varnish on the finished sculpture. analog() adds random pitch drift to every voice — a subtle imperfection that makes the whole thing feel alive instead of clinical.",
            code = """// Varnish the sculpture — compressor glues, analog breathes life
stack(
  n("0 2 4 7").scale("E4:minor")
    .sound("saw").lpf(2000)
    .distort("0.3:gentle").postgain(0.5)
    .gain(0.3),
  sound("bd sd bd sd").crush(6).gain(0.7)
).compressor("-15:2:6:0.01:0.2").analog(0.5)""",
        ),

        // ── PUTTING IT ALL TOGETHER ───────────────────────────────────
        TutorialSection(
            heading = "Putting It All Together",
            text = "Here is the finished sculpture, inspired by Editors — Papillon. Every layer has its own sonic identity. The lead uses supersaw with unison(2) for width, distortion for grit, and clip() for tight note lengths — two superimposed octave copies fade in over time for growing intensity. The pad uses struct() for rhythmic pulsing, unison(3) for thickness, warmth and notchf to shape the tone, and scale alternation to lift an octave in the final section. The bass is warm with distortion and a steady struct pulse. The hi-hat pattern is deliberately 40 cycles against the 64-cycle structure — it rotates so no two passes sound identical. Each instrument lives on its own orbit for clean separation. The compressor on the master bus glues everything together, and analog() adds random pitch drift for an organic, imperfect feel.",
            code = """// The finished sculpture — every texture carved with purpose
stack(
  // Lead — distorted supersaw with unison width
  n("<[-7 0 2 4] [-7 0 4 2] [-5 -1 2 4] [-6 -1 3 1]>*2")
    .scale("E4:minor").sound("supersaw").unison(2).detune(0.04)
    .lpf(4300).hpf(600)
    .gain(0.3).distort("0.5:gentle").postgain(0.5) // . solo()
    .adsr("0.02:0.2:0.6:0.1").clip(0.85)
    .release("<0.1!16 0.3!16 0.1!16 0.4!16 0.1!16 0.6!16>")
    .superimpose(transpose(12).detune(0.08).velocity("<0!32 0.2!32>").lpf(4500).pan(0.2))
    .superimpose(transpose(24).detune(0.12).velocity("<0!96 0.075!32>").lpf(5000).pan(0.8))
    .orbit(0),
  // Pad — struct stamps the rhythm, warmth + notch shape the tone
  n("<[0 0 2 4 0 0 -2 -1]!4 [0 [2 4] 0 [2 -1]]!2 [0 [6 4] 0 [6 2]] [4 [2 4] 4 [-2 -1]]>/4")
    .struct("<[x!16]!7 [x!24]!1 [x!16]!16>")
    .scale("<e2:minor!48 e3:minor!16>").sound("supersquare").unison(3)
    .lpf("1200").hpf(400).notchf(440).warmth(0.5)
    .adsr("0.01:0.15:0.7:0.05").clip(0.8).crush(saw.range(10, 4).slow(32)) //  . solo()
    .gain(0.425).orbit(1).pan(0.3),
  // Bass — warm distorted saw, struct for steady pulse
  n("<0 0 2 4 0 0 -2 -1>").struct("<[x!8]!14 [x!12]!2 [x!8]!32>").fast(2).velocity("1 0.95!3".fast(4))
    .scale("e2:minor").sound("saw")
    .lpf("600").hpf(120).distort(0.3).warmth(0.95)
    .adsr("0.01:0.15:0.5:0.05").clip(0.8) // . solo()
    .gain(0.325).orbit(2).pan(0.7),
  // Drums — crushed, building from sparse to dense
  sound("<[bd!2]!2 [bd!4]!2 [bd!8]!2 [bd!16] [bd!24] [bd sd bd sd]!8 [bd [bd,sd] bd [bd,sd]]!8>")
    .crush(9).gain(0.8).orbit(3).hpf(80),
  sound("<[hh hh oh hh]!24 [cr hh cr hh]!16>").fast(2).crush(8).hpf(3000)
    .gain(0.6).orbit(3)
// Compressor glues it, analog adds organic drift
).compressor("-15:2:6:0.01:0.2").analog(0.5)

// Inspired by: Editors - Papillon
// https://open.spotify.com/intl-de/track/7hYiX6LMP8w8d0kEc4KWuW""",
        ),
    ),
)
