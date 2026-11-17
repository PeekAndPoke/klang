# KLANG-00453: A value that is no note name still reads as 440 Hz under scale and transpose

> **Status (2026-11-17): IDEA.** 

## Why this exists

Left over from KLANG-00452 (a scale or a transpose gave a drum sample 440 Hz), found by both reviewers of its second
step and not made worse by it.

`Tones.noteToFreq` returns 440 Hz for any text it cannot read as a note. KLANG-00452 closed the case of an event with
no note at all. An event that still carries a value which is no note name goes the old way:

- `"x ~ x".sound("hh").transpose(12)`: the control form of `sound()` keeps the source value, so the event reaches
  `transpose()` as the note name `"x"`, reads as 440 Hz, is shifted to 880 Hz, and the hat is re-pitched.
- `"0 1".sound("hh").transpose(12)`: the value is read as a MIDI number, a few Hz, and the sample's rate is held at
  its lower limit.
- `resolveNote` (the fallback branch) and `noteMutation` do the same for a value such as `"bd"`, `""` or `":3"`.

No built-in song, tutorial or frozen copy uses one of these forms today (read from the code, 2026-11-17).

## Decide before implementing

- What a value that is no note name counts as: no pitch (the event is left alone), or an error the editor shows.
- Whether the fix belongs in the three sprudel places or in `Tones.noteToFreq` itself, which lives in `tones/` (MIT)
  and whose 440 Hz for an unreadable name other callers may rely on.
- Whether the control form of `sound()` should clear the source value, as the plain form does.

## The work

Not started.

## Acceptance

A spec per form above that expects no frequency (or the decided error), mutation-checked.

## History

- 2026-11-17: created.
