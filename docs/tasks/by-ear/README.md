# By ear — the listening queue

Tasks whose **next action is to listen**, not to write code. A task belongs here when the
maintainer's ears are the acceptance test and no amount of arithmetic or test coverage can settle
it. Once a listening round produces a verdict, the task either closes or moves back out with a code
deliverable.

Two things live here: task files (moved in from `docs/tasks/`), and **owed rounds** that were
recorded inside ledgers or commit messages and had no home of their own. The owed rounds are the
reason this folder exists: they are easy to lose, and every one of them is already shipped code that
nobody has heard.

Created 2026-08-31.

---

## Task files

| File | What the ear work is |
|---|---|
| [`c3-depth-migration-flags.md`](c3-depth-migration-flags.md) | The C3 semitone-depth migration changed mid-sweep trajectories even where endpoints are exact. A ranked list of 5 song sites to check, deepest and slowest first (IrishLamentTechno hitStab, SoundOfTheSea "Waves", TetrisRemix sub, StrangerThings melody, then the fast percussive group). |
| [`analog-drift-ratio-tuning.md`](analog-drift-ratio-tuning.md) | Is Klang's `analog` character built the wrong way round? Real VCOs hold pitch well and VCF cutoff wanders; Klang does the opposite. An A/B ratio experiment on held/unison material: `(0.5 / 0.5)` then `(0.25 / 1.0)` then `(0.1 / 2.5)`. **Unblocked** since its precursor `audio-bridge-constants.md` shipped 2026-08-11. |

---

## Owed rounds (shipped code nobody has heard)

### 1. W10 tremolo shapes — committed unheard

**Commit `9cb896ff`.** Recorded only in the W10 row of `docs/plans/block-framing-invariance.md` and
in the BUILD-LOCK. The oldest debt in this folder and the one with the most new sound in it.

Six shipped sprudel functions were **inert end to end** (full KDoc, examples and aliases, dropped at
`FilterPipelineBuilder`): `tremoloskew`/`tremskew`, `tremolophase`/`tremphase`,
`tremoloshape`/`tremshape`. All three are now implemented, plus a new `LfoShape.kt` with five
waveforms drawn from the oscillator vocabulary.

What to listen for:

- The five shapes at a musical rate, and whether the vocabulary reads as the same thing the
  oscillators mean by those names.
- **Skew.** Decided as `-1..+1` with 0 symmetric, and `+ skew` means "sits higher" on all five
  shapes. Sawtooth's duty is deliberately flipped to make that true. Does it feel that way?
- **Phase is in cycles, not radians.** The old KDoc examples (`1.57`) were 565 degrees under the
  factory's own conversion.
- Bit-identity at neutral settings is by construction, so an existing tremolo should sound
  untouched. Worth confirming on a shipped song.

Note while listening: **every documented tremolo example was inaudible** before this round (rate and
depth both default to 0, so the stage was never built). 54 examples were fixed. They have not been
heard either.

### 2. Mini-notation tweaks — the design could still come back

Item 2 of [`../future/mini-notation-tweaks-followups.md`](../future/mini-notation-tweaks-followups.md).
Shipped and archived 2026-08-31; every claim is backed by tests, which here is half a verification.

Needs a session writing real music with `note("c3 e3{swell}").tweaks({ swell: x => ... })`, asking
two things a test cannot:

- Is `{swell}` legible at a glance in a dense line, or does it crowd the notes?
- Does *naming a treatment* hold up as a way of working once a song has five of them, or does it
  turn into a dictionary you have to keep in your head?

The second question is the one that could still send the design back. Item 7 (`tweak` vs `tweaks`
ergonomics, fallback name `withTweaks`) and item 5 (whether `[c4 e4]{swell}` should apply per note
or to the group as a unit) both depend on this round and cannot be judged without it.

### 3. Body resonator material tables

The replacement work from the resonator-swing closure
([`../../tasks-archive/2026-08/20260820-resonator-swing.md`](../../tasks-archive/2026-08/20260820-resonator-swing.md),
won't-implement 2026-08-20). Swing was closed on taste, and the deferred "POC starting points" item
became: **tune the material tables by ear.**

The tables are hard-coded modal fingerprints, deliberately a tune-by-ear surface. `cedar` and
`brass` shipped alongside a `BODY_FLOOR` change of 0.6 to 0.4 and the live `SprudelBodyEditorTool`
fingerprint view, which is the instrument for this round. Oak and other tonewoods were brainstormed
and never tried.

Long-term path if hand-tuning stops paying:
[`../future/ir-to-modal-table-extraction.md`](../future/ir-to-modal-table-extraction.md).

### 4. Der Schmetterling re-voicing — uncommitted right now

Live in the working tree as of 2026-08-31, not committed, not reviewed:

- supersaw `voices` 17 to 21, `spread` 0.11 to 0.10, `spreadPower` 6.0 to 8.0
- guitar bus `rsize` 3.0 to 1.0
- drum bus `roomWet` 0.20 to 0.30

This sits on top of the master-round fix that changed how the song opens: the first master
application used to crossfade the opening 60 ms up from unmastered, so Der Schmetterling started
**8.3 dB down and swelled**. The song has not been heard against a correct opening yet, and these
voicing numbers were chosen before that landed.

### 5. Unified EQ D7 — song bell migration (optional)

`docs/plans/unified-eq.md` §D7. Per-band exactly solvable, but it carries a topology warning and is
explicitly a taste pass. Optional, and the plan's CPU goal is already met (Fairphone 4, ~75%), so
this one is pure sound.

### 6. Soundfont attacks are back — every `gm_` instrument sounds different now

Landed 2026-09-02 (`VoiceFactory` playhead start). Until now a looped soundfont started **inside**
its loop and never played its attack: the FluidR3 violin skipped 1.27 s of bow onset and looped a
180 ms slice of steady state; the flute skipped 0.66 s; the nylon guitar skipped its pluck because
it started at `anchor`, which turned out to be the loudest sample's position, not a start offset.

The fix plays from frame 0 and loops when the playhead gets there — the SF2 / WebAudioFont
behaviour. **No shipped song uses a `gm_` soundfont**, so nothing released moved, but everything a
tutorial might reach for did. Worth hearing, in this order, before any tutorial ships one:

- `note("c4 e4 g4").s("gm_violin")` — the bow should be audible now, then a steady sustain.
- `s("gm_acoustic_guitar_nylon")` — the pluck transient is back; check it is not now too clicky.
- `note("c3").s("gm_accordion").sustain(4)` — should breathe in, then hold cleanly across the loop.

Open question for the ear: with the attack restored, is the fixed **sustain envelope**
(`getSampleMetadata`: attack 10 ms, release 200 ms for looped zones) still right, or was it tuned
to mask the missing onset? Record: `docs/tasks/soundfont-looping-investigation.md`.
