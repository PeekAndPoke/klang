# Legato in mini-notation: a concise way to let a note ring

Status: **future, idea, not designed.** Kept 2026-09-27 (maintainer) when
`mini-notation-extensions.md` was archived
(`docs/tasks-archive/2026-09/20260927-mini-notation-extensions.md`); mini-notation will get a new
revision at some point, and this is the one open question from that file worth carrying.

## The question

How does an author say "this note rings on over the next ones" (polyphonic sustain, a let-ring, a
pedalled piano note) inside the mini-notation string itself, concisely?

## What exists today

- **`legato(amount)`** (alias `clip`, `lang_tonal_note.kt`) scales every event's duration: 1.0 fills
  the slot, 2.0 rings twice as long. It is a pattern-level door, so a per-note value needs a pattern
  argument (`legato("1 1 4 1")`) that has to be kept in step with the notes by hand.
- **`@N` (weight)** makes a note take N slots. That is a longer slot, not a note that rings over its
  neighbours: the following notes move.
- **Tweaks** (`docs/tasks-archive/2026-08/20260831-mini-notation-tweaks.md`) can do it per note, but
  only through a named modifier defined outside the string:
  `n("e2{ring} b3 e3 d4").tweaks({ ring: x => x.legato(4) })`. It works; it is not concise for
  something this common.

## Dropped for good (maintainer, 2026-09-27)

- **`{legato=2}` / `{l=2}`**, the attribute block. Shipped 2026-04-13, removed 2026-08-30 with zero
  recorded use; the braces carry tweak names now. It will not come back.

## Considered earlier, open again

- **A `_` step** ("the previous note keeps sounding here"). Strudel and TidalCycles have `_`, but there it
  elongates the previous step, the same as `@`, so it is precedent for the spelling, not for the ring. It was
  dropped in the attribute-block design (2026-04) for these reasons, each to be re-weighed:
  - `@N` already covers "takes N slots" (but not "rings over the next note", see above);
  - `{legato=N}` covered polyphonic sustain (gone now, so this reason is gone);
  - ambiguity: in `[c d] _`, which note sustains, `d` or the whole group?
  - "one less concept to maintain".

## When it is picked up

- Decide the meaning first: extend the slot (`@`-like, the next note moves) or ring over the next
  notes (legato-like, the next note stays where it is), and whether both are needed.
- Settle the group case (`[c d] _`) and the rest case (`~ _`) before any syntax.
- MIDI recording will want the same thing: a held key or the sustain pedal (CC 64) has to serialise
  to something, and this is it.
- One word per concept (`/dsl-design` §5): whatever the notation is, it maps onto `legato`, not onto
  a new duration concept.
