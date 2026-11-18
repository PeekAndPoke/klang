# KLANG-00458: Live act: Rune's crossed arms and the idle skills

> **Status (2026-11-18): IN PROGRESS.** Part one is in review.
>
> **Part of:** KLANG-00447 (Live act: a band on a stage, played by the song)

## Why this exists

The maintainer, 2026-11-18: "We need one more pose for Rune: before she starts playing and when she is done she
needs to briefly cross her arms before her chest." And: "The crossing of the arms is a skill in itself, but only Rune
will get it for now." Then, on the first stills: "Let her only cross the mallets below her head, so that it looks
like a skull and bones."

And: "we need more skills for when a player is idle... relaxing poses, head-scratching, brush hair back." Who gets
which goes by a stat: "0 = elegant and 10 = animal ... Depending on that stat we assign the skills", and "keep a good
split, so that everyone has a different set."

## Decided

- `CrossedMallets` is a skill on the sheet, not part of the marimba's pose; Rune's only.
- Wildness, 0 (elegant) to 10 (animal), on every member's character and on every idle skill's kind: Rune 1,
  Sirene 2, Skalpell 5, Beben 6, Klaue 8, Wucht 9. A skill fits a member at most 3 apart. The sheets are written by
  hand; no idle skill stands on two sheets.
- Sirene has no idle skill (the maintainer, on the hair stroke that was tried: "it does not look nice and she needs
  to be the most composed of all as the frontman").
- Nobody pauses for a few seconds inside the song: every gap is under 2 s or one of the long breaks. So the idle
  skills show in the long breaks and before a member's first note.

## The work

1. Done in part one: `CrossedMallets`. Before a phrase and after it Rune crosses her two pairs of mallets in an X
   under her chin, the arms on their own sides. When, is a plain function of her part's pauses.
2. Done in part one: the idle skills' machinery (used by chance like the timed looks, but only where the member's
   part pauses for the whole use and a margin either side, and only once the member has arrived), and Beben's two:
   `HeadScratch` and `Slouch`.
3. Part two, the rest of the split: Rune: stretching the neck, counting in. Skalpell: shaking out the fretting hand,
   tuning, hitching the strap. Klaue: tugging the cap, wiping the brow. Wucht: twirling a stick, scratching the neck
   with the stick's butt.

## Acceptance

- Rune crosses her mallets before she begins and after she is done, and not between the notes of a phrase; she is
  never late for a note.
- An idle skill never takes a hand off the instrument while its member plays, and no use is cut off halfway.
- A member without a skill is posed exactly as before (stills compared byte for byte).
- Every idle skill on a sheet fits its member's wildness; no idle skill stands on two sheets.

## History

- 2026-11-18: created.
