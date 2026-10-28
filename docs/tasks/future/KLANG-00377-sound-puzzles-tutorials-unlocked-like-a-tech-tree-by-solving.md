# KLANG-00377: Sound puzzles: tutorials unlocked like a tech tree by solving them

> **Status (2026-10-28): IDEA.** the maintainer, 2026-10-28: details to discuss
>
> **Part of:** KLANG-00362 (Learning: tutorials and the ear)

## Why this exists

The maintainer, 2026-10-28: "I like the tech tree unlocking by solving sound puzzles idea. Let's discuss details later... Have some ideas." It grows from KLANG-00334 item 13 ("unlock new tutorials like a tech tree in a game") and makes the interactive learning tools of Q4 concrete: a tutorial opens the next ones when its sound puzzle is solved.

## The maintainer's idea (2026-10-28)

"We create sounds and play them and the user has to recreate them." The code of the puzzle is shown, read-only,
with "???" where the user has to fill in:

```
note("c d e f").sound(Guess(correct = "saw", choices = ["sine", "saw", "square"]))
```

- Choices from a list (a waveform), and range choices for numbers (a filter's cutoff and the like).
- The code cannot be edited. The choices are made through UI elements, not by typing code; or in the code, as a
  small field where the "???" is.
- Example: the tutorial on the four waveforms ends with a puzzle that asks to identify them.

## Decided (the maintainer, 2026-10-28)

- **A builder, to stay flexible:** `Guess(b => b.correct(...).choice(...).choice(...))`, a configure lambda as the
  other DSLs have (`Ign.supersaw(x => x.voices(9))`), immutable like every door (`/dsl-design`). Sketches, to settle
  in the design round:

  ```
  note("c d e f").sound(Guess(b => b.correct("saw").choice("sine").choice("square").choice("tri")))
  note("c d e f").sound("saw").lpf(Guess(b => b.correct(800).range(200, 4000).log().tolerance(0.25)))
  ```

- **Feedback in steps:** first "2 of 3 right" without saying which, then per guess after a few tries or on request.

## More puzzle ideas

- **EQ back to pink** (the maintainer, 2026-10-28): pink noise, equalized away from pink, plays; the user gets an EQ
  of their own and brings it back to pink. Pink noise has equal energy per octave, so a boost or a dip shows
  plainly as a colour. Solved when the target's EQ and the user's together come out flat within a tolerance per
  band. Levels could grow from one wide band to several narrow ones, and from a spectrum shown to ears only. Built
  on the Katalyst `eq` (KLANG-00250); close to the ear trainer (KLANG-00274).

### Ideas from Klang-Main-Dev (2026-10-28, idea generation only)

Sorted by how they fit `Guess`. The fine-tuning comes when we build it (the maintainer).

**Fits `Guess` directly** (a gap in the code, choices or a range):

- **Envelope match:** a pluck, a pad, a swell; set attack and release to match (two or three range gaps). Pairs with
  the ADSR pictures.
- **Find the cutoff:** a filtered loop, the cutoff on a knob; resonance as a harder level (a range gap).
- **Name the distortion:** soft, tube, hard, fold; the same note four times (a choice gap).
- **How many voices:** a supersaw with 1, 3, 7 or 9 voices, or little against much detune (a choice gap).
- **Room size:** match a reverb's size and decay to a reference (range gaps).
- **Chord quality:** major, minor, sus, 7th (a choice gap).
- **The 1 dB ear:** match a level to a reference within 1 dB (a range gap with a tight tolerance).
- **Place it in stereo:** where does it sit; pan yours to the same place (a range gap).
- **Boss puzzle, rebuild the sound:** at the end of a branch of the tree, oscillator, filter and envelope at once;
  every earlier puzzle of the branch leads to it (several gaps).
- **Balance the mix:** four stems, four faders, match a reference mix (four range gaps, best drawn as faders).
- **EQ back to pink** (above): a few range gaps on the EQ's bands, best drawn as an EQ curve.

**Fits, but the gap sits inside a mini-notation string:**

- **Rhythm dictation:** `sound("bd ??? sd ???")`, the loop heard, the gaps filled (best drawn as a grid of toggles).
- **Melody dictation:** `note("c ??? ??? g")`, four notes, the intervals by ear.
- **Count the layers:** how many steps the hi-hat has (polyrhythms), or which Euclid pattern plays.

`Guess` is a KlangScript value, not a mini-notation token. The ways round it: strings glued with `+` (reads badly),
the whole pattern as the choice (coarse), or a gap token in mini-notation that the puzzle fills (clean, but touches
sprudel's parser).

**Needs its own UI:**

- **Which one is it:** the code is shown, three clips play; pick the one the code makes. Code read as sound, which
  only a code-first music tool can teach. Three players and a pick, nothing in the code is a gap.
- **Spot the difference:** two versions, one detail changed (a filter, the swing, one hi-hat moved); click the line
  in the read-only code that differs.
- **Fix the mix** (in between): a short song with one real problem (kick and bass fighting, a muddy low end, a
  clipping master); choose the fix (ducking, EQ, gain). A `Guess` at the end of a line when the choices are chain
  pieces with labels (`x => x.duck(...)`, `x => x.lpf(...)`); otherwise a "choose the fix" UI of its own.

**Around the puzzles:**

- **Branches instead of one path:** sound design, rhythm, mixing; each ends in a boss puzzle, and later tutorials
  need two branches.
- **A daily puzzle:** one small fresh puzzle a day from the same gaps, a reason to come back.
- **How a gap is drawn:** the builder can carry a hint (`.asGrid()` for a rhythm, `.asFader()` for a level,
  `.asEqBand()`), so one mechanism serves nearly every puzzle; only "which one is it" and "spot the difference"
  need UI of their own.

## Notes for the design (Klang-Main-Dev, 2026-10-28, to discuss)

- **Two players, one switch:** "the target" plays the code with the correct values, "yours" with the current picks.
  Both run in sync on the same pattern and one switch flips between them, so the ear compares the same moment, not
  two memories.
- **Match the loudness:** a saw is louder than a sine at the same gain, so without care a puzzle can be solved by
  loudness alone. The target and every choice are matched in loudness, so the ear has to judge the colour.
- **The "???" in the code:** an inline widget in the read-only editor (a CodeMirror replace decoration) over the
  whole `Guess(...)` call, so the shown code never gives the answer away. It reads "???" until a pick, then the
  pick (`"saw"`, `800`). A click opens the choices beside it: chips for a list, a knob or slider for a range.
- **Range guesses:** a range, a scale and a tolerance on the builder (above), right when within the tolerance
  (in octaves or a ratio for frequencies, since the ear hears ratios).
- **How `Guess` works:** a KlangScript value that the puzzle host resolves before playback: to the correct value
  for the target, to the user's pick for "yours". The engine never sees a `Guess`.
- **The tech tree:** a solved puzzle unlocks the next tutorials (KLANG-00334 item 13); progress kept in the browser
  until there is an account (Q1 2027). Close by: the ear trainer (KLANG-00274).

## Decide before implementing

- Choices beside the "???" (a popup) or in a panel below the code, or both.
- Whether a puzzle may be skipped in the tree, and whether wrong answers cost anything.
- The tolerance per kind of range, and whether the user may hear each choice on its own before deciding.
- The name in KlangScript: `Guess`, or another word that reads well in a song.

## The work

Not planned while this is an idea.

## Acceptance

Not set while this is an idea.

## History

- 2026-10-28: created in the Q4 planning with the maintainer.
- 2026-10-28: the maintainer's idea (recreate a sound by filling in the "???" of read-only code) and the design notes.
- 2026-10-28: decided: the builder form `Guess(b => ...)`, and feedback in steps (the maintainer).
- 2026-10-28: a second puzzle idea, EQ back to pink (the maintainer).
- 2026-10-28: Klang-Main-Dev's puzzle ideas, sorted by how they fit `Guess` (the maintainer: note them all down).
