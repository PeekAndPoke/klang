# Tremolo: sprudel's `sync` against the Ignitor's `rate`

Status: **DONE 2026-10-03.** Recorded "for the sprudel side" in the phase 3 door-shape walk (2026-09-23);
opened as a future file 2026-09-28 when the phase 3 record was archived
(`docs/tasks-archive/2026-09/20260928-builtin-instruments.md`, section 3b, the `tremolo` row). Taken up after
`beatRate` (`docs/tasks-archive/2026-10/20261001-sprudel-beat-rate-helper.md`): the helper's name says where it
goes, `rate`, and the sprudel tremolo was the one LFO door that did not say it.

## What it was

One concept, two spellings:

- the Ignitor door is `tremolo(rate, depth, configure)`, rate first like every Ignitor LFO door (`phaser`,
  `vibrato`), with `shape` on the builder;
- sprudel's door was `tremolo(depth, sync, shape)` (`sprudel/.../lang/lang_effects_modulation.kt`), depth
  first, calling the rate `sync`, while sprudel's own `vibrato(rate, depth)` and `phaser(wet, rate, ...)` say
  `rate`.

`sync` was an LFO rate in Hz, the same meaning and scale as `rate` (since 2026-09-29 both feed the frequency of
the LFO oscillator, `docs/tasks-archive/2026-10/20261002-tremolo-as-composition.md`).

## Decided (maintainer, 2026-10-03)

- **Rename `sync` to `rate`**, everywhere, end to end: one word per concept, and the replaced surface is removed,
  not deprecated. A song that still writes `tremolo(sync = ...)` fails to compile, which is the point.
- **The order stays `tremolo(depth, rate, shape)`.** Depth first is sprudel's bare-call rule (a bare call reads the
  pattern's values as the first parameter), and the maintainer called the tremolo's order the good one. It is
  recorded as a deliberate asymmetry with the Ignitor door in `.claude/skills/dsl-design/door-shapes.md`.
- **The songs and examples move with it** (the maintainer committed all songs first).

## What moved

- The `classic()` slot: `Slots.tremolo.rate`, key `tremolo.rate` (`audio_bridge/.../IgnitorDslClassic.kt`), its
  KDoc in `IgnitorDsl.kt`, and the script door's slot objects (`KlangScriptClassicSlots.kt`, `KlangScriptOscSlot.kt`,
  `KlangScriptOscExtensions.kt`).
- Sprudel: the door parameter and the `tremolo.rate` reader (`lang_effects_modulation.kt`), the voice field
  `tremoloRate` (`SvdGroups.kt`, `SprudelVoiceData.kt`), the slot writer (`_classic_slot_params.kt`), the
  `beats` / `beatRate` KDoc.
- The tremolo editor tool: the named-argument key, and the label, which said "Rate (cycles)" for a value in Hz.
- The songs: `Kokon.kt`, `DrunkenSailor.kt` (the only two that named the knob).
- The benchmarks, the whitepaper figure `fig-classic-stages.html`, the music-writing references (sprudel and
  ignitor), `docs/tasks/sprudel-ui-tools.md`.
- Beyond the rename, in the same change: the sprudel music-writing reference had not learned `beats` /
  `beatRate` (2026-10-01); it now has a short section on both, the warning that `"1/8"` is not a fraction in
  mini-notation, and its two tempo-synced delay examples use `beats(0.5)` (the same eighth note as
  `pure(1/8).div(cps)`).
- The tests. (A guard that `sync =` and `tremolo.sync` are refused was added and then removed the same day, by
  the maintainer: KlangScript refuses an unknown named argument and its own suite tests that; such a row only
  restates it.)
- Not changed: history (`docs/tasks-archive/`, the `memory-history.md` files, the retired-list in `CLAUDE.md`,
  which names the old wire field `tremoloSync`), the superseded `docs/tasks/_priorities.md`, and the Graal
  compat test's read of Strudel's own `tremolosync` field (the JS name; only the Kotlin side moved).
- The bundled worklet (`src/jsMain/resources/klang-worklet.js`, untracked) carries the slot key; every frontend
  build re-runs `copyWorkletProd` first, so it regenerates.

## Guard

The existing tremolo rows of `LangDoorFormsSpec`, `LangFieldAccessorsSpec`, `LangControlRestSpec`,
`ClassicSlotParamsSpec`, `ClassicDoorRenderParitySpec`, `KlangScriptClassicDoorParitySpec` and the audio_be classic
rigs, renamed.

Mutation-checked: the engine-side slot key put back to `"sync"` turns `ClassicSlotParamsSpec` red (two rows), so a
writer/reader mismatch cannot pass silently.

## Review

Round 1 (blind, one coding reviewer, by table over the whole repo; no audio reviewer: a rename with identical
behaviour, the one wire risk, a key mismatch, is the coding reviewer's table plus the mutation above): no CRITICAL
or MAJOR. Four MINORs:

1. The skill's link to this record pointed at the archive before it was archived. Closed by archiving it, with
   the `sprudel/MEMORY.md` History line, in the same commit.
2. `audio/MEMORY.md` still listed this file among the future voice tasks. Removed.
3. The music-writing reference change goes beyond the rename. Kept, recorded above.
4. The rules register's retired list (`CLAUDE.md`) does not name the removed `sync` surface, so nothing stops a
   later "Strudel-compat alias". Decided by the maintainer: not in the register ("no need to pollute the main
   files"). Instead `sprudel/README.MD` (with a pointer in `sprudel/CLAUDE.md`) says sprudel no longer mirrors
   Strudel, and the archived records that mention `sync` carry a one-line hint.

Not this change: `SongBenchmarkCasesCompileSpec` fails on a text anchor in `DerSchmetterling.kt`
(`.plus(beater)` followed by `.distort(..., "tube", 2)`), broken by the `makeGuitar` refactor (`5cdf2c8d`).
