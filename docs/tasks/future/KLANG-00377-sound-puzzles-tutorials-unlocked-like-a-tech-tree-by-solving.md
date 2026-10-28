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
