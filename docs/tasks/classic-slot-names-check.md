# The `classic()` slot names: one name per concept

Status: **decided (maintainer, 2026-10-08, Q21), pulled ahead: it runs after the engine tidy-up and BEFORE pitch
pipeline step 1** ([`pitch-pipeline-into-the-tree.md`](pitch-pipeline-into-the-tree.md)). Only `analog` is still open (Q22).

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

## Why it was pulled ahead

First planned after the pitch pipeline, so two kinds of change would not share a review. Pulled ahead by the
maintainer (2026-10-08), because the pipeline's new slots (`penv.attack`, `fm.attack`) would otherwise be born with
the envelope words this check renames.

## Decided (maintainer, 2026-10-08, Q21; the table: `tmp/reviews/classic-slot-naming-table.md`)

Pulled ahead of the pitch pipeline, so no slot is renamed twice.

1. **The envelope words on the Ignitor side become the slots' words:** `attack`, `decay`, `sustain`, `release`
   (was `attackSec`, `decaySec`, `sustainLevel`, `releaseSec`), on every `adsr` (the voice envelope, and the ones
   inside the filter, pitch envelope and FM builders), the flat Kotlin doors, the node fields (FM's `envAttackSec`
   and its siblings too) and the wire. Example: `Ign.saw().adsr(attack = 0.01, decay = 0.3, sustain = 0.5,
   release = 0.2)`. Positional calls are unchanged.
2. **`crush.amount` becomes `crush.bits`, `coarse.amount` becomes `coarse.factor`,** on the slot, the sprudel
   parameter and accessor, both Ignitor doors and the node field. The songs, the frozen songs, the tutorials, the
   whitepaper and the skill refs are adjusted (maintainer: "please adjust the songs / frozen songs / tutorials").
3. **`distort.amount` stays.** After 2, `amount` means only the distort drive.
4. **The filter `env` stays,** documented in semitones (the engine's rule: pitch in semitones).
5. **`tremolo.depth` stays;** "depth" is how far a modulation swings, in the stage's unit.
6. **`declickSeconds` becomes `declick`, flat.** The general rule (now in `/dsl-design` §4 and the register): time
   always in seconds, pitch always in semitones where it can be, the unit in the KDoc and not in the name.
7. **The two source slots get unique, concise names now** (maintainer: "choose unique useful but concise names"):
   - the pluck's `decay` (a loop feedback coefficient, 0.9 to 0.999, not seconds) becomes **`feedback`**: the same
     concept as the delay's `feedback` (a loop gain), which keeps one word per concept;
   - brown noise's `depth` (a per-sample white leak) becomes **`leak`**.
8. **The Katalyst keeps the namespacing,** `<sprudel door>.<Katalyst door knob>`: all 27 slots already fit, so no
   renames; `vowel.vowel` and `gain.gain` stay.
9. **`analog`, one name and two scales:** open, the maintainer asked what unifying would take (Q22).
10. **The one-knob slots stay flat:** `onepole`, `pregain`, the sample slots `begin`, `end`, `speed`, `loop`.

## The work, in order

1. **The envelope words (decision 1, 6):** a mechanical rename across `audio_bridge` (`IgnitorDsl.kt`, the classic
   and walk files), `audio_be` (`IgnitorEnvelopes`, `IgnitorDslRuntime`, `PitchModFactories`, `IgnitorFilters`),
   `klangscript-libs` (`KlangScriptIgnitorExtensions`, `EffectBuilders`), the wire golden, `/dsl-design` §2 and
   `door-shapes.md`. Bit-identical (the corpus), a wire-schema hash change, door-parity rows.
2. **`crush.bits`, `coarse.factor`, `feedback`, `leak` (decisions 2, 7):** the slots, the doors, the accessors,
   the songs and every user-facing text; old names into `docs/retired-names.md`. Bit-identical.
3. **The findings of the check:** the script `Ignitor.slot` gets the 8 slots it lacks (`bipolar`, `chaos`,
   `color`, `declick`, `leak`, `octaves`, `persistence`, `tail`; a two-door gap), the stale
   `KlangScriptIgnitorSlots` KDoc, the three comments that still say "named after sprudel's readers". The Lexikon's
   Crush and Phaser entries are fixed (`66781690`).

Before the pitch pipeline's step 1, so its new slots are born with the final words (`penv.attack`, `fm.attack`).
