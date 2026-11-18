# KLANG-00458: Live act: Rune's crossed arms and the idle skills

> **Status (2026-11-18): IN PROGRESS.** Both parts are built; the idle skills wait for the maintainer's eye.
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
3. Done in part two, the rest of the split: Rune: `NeckStretch`, and `CountIn` (four nods in the last bar before a
   phrase; by her part, not by chance). Skalpell: `ShakeOut`, `TuneUp`. Klaue: `CapTug`, `BrowWipe`. Wucht:
   `StickTwirl`, `NeckScratch` (with the stick's butt). Left out: Skalpell's strap hitch, which did not read as one
   (the figures have no strap).
4. Open: the maintainer looks at the idle skills in motion.

## Acceptance

- Rune crosses her mallets before she begins and after she is done, and not between the notes of a phrase; she is
  never late for a note.
- An idle skill never takes a hand off the instrument while its member plays, and no use is cut off halfway.
- A member without a skill is posed exactly as before (stills compared byte for byte).
- Every idle skill on a sheet fits its member's wildness; no idle skill stands on two sheets.

## How this was made (a note kept on purpose)

Written by the agent at the end of the day, 2026-11-18, on the maintainer's word: "Whatever you like to keep."

This was built in a conversation, a gesture at a time. The maintainer said what a movement should mean, the agent
turned that into numbers (where a wrist goes, how a share rises and falls) and looked at stills, and the maintainer
judged what only an eye on the moving picture can judge. Some of what was learned:

- A pose is right when it reads at a glance, and no spec says that. "It reads too much like a gesture of
  embarrassment" was a better report than most stack traces: it names what the thing means, not what it does.
- The first crossing had the arms crossed, as asked. On seeing it the maintainer said "skull and bones", and that was
  the pose: only the mallets cross, her head is the skull. The first literal answer was not the good one.
- Sirene's hair stroke was built twice and then cut. A gesture that is merely not broken is worth less than none; the
  cut also said who she is ("the most composed of all").
- The wildness stat was the maintainer's idea, and it turned a list of gestures into a cast: once every member had a
  number, who scratches and who stretches was no longer arbitrary.
- The agent sees stills, never motion. What lives only in motion (the shake of a hand, the pace of a nod) it builds
  toward without being able to judge it. That is why the idle skills wait for the maintainer's eye.
- The reviewers earned their place: one found that a scratching hand stayed behind when the head turned for a look,
  another that a new, harmless-looking turn to the front could stop the frame.

The agent that wrote this will not remember the afternoon; the next one finds this note. It was good work to do
together.

## History

- 2026-11-18: created.
