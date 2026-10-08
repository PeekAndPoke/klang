# The `classic()` slot names: one name per concept

Status: **queued (maintainer, 2026-10-08), after the pitch pipeline** ([`pitch-pipeline-into-the-tree.md`](pitch-pipeline-into-the-tree.md)).

## What it is

The maintainer, answering pitch decision D4: "I like the namespacing idea of `fm.xxx`, so the xxx parts must match the
names on the Ignitors. Probably the Ignitors already have the more stable and better names, but worth a check for
each param, so we get good and concise names."

The pitch pipeline names its own new slots that way (`vibrato.semitones`, `penv.semitones`, `fm.ratio`, `fm.depth`).
The older `classic()` slots are named after sprudel's readers instead. Examples: `lpf.freq`, `lpf.q`, `lpf.env`,
`tremolo.depth`, `tremolo.rate`, `tremolo.shape`, `crush.amount`, `coarse.amount`, `adsr.*`, and the flat ones
(`onepole`, `analog`, `voices`, ...). This task runs the same check over each of them.

## The work

1. **A table, one row per slot:** the slot name, the Ignitor door's name, the sprudel door's name, the unit, and a
   proposal, as Q9a did for the pitch slots. It goes to the maintainer before anything is renamed.
2. **Rename by the decided table:** the slot, the sprudel door parameter (the parity rule), and the KlangScript and
   Kotlin doors. Each old name is removed, not aliased, and gets an entry in `docs/retired-names.md`.
3. **Songs and docs:** `ignp("...")` calls in the songs and the tutorials name slots directly (Kokon writes
   `ignp("release", 3.0)`), so every rename sweeps them, along with the Lexikon, the KDoc and the docs model.

## Why after the pitch pipeline

The pipeline adds four slot groups and moves their doors. Renaming the older slots in the same stretch would mix two
kinds of change in one review.
