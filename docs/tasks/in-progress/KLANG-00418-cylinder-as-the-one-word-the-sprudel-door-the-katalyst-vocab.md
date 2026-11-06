# KLANG-00418: Cylinder as the one word: the sprudel door, the Katalyst vocabulary, the docs

> **Status (2026-11-06): IN PROGRESS.** Decision D1 (maintainer, 2026-10-07), started 2026-11-06 (maintainer: "yes
> cylinder become the main door, we can do it now").
>
> **Part of:** KLANG-00353 (The Motor: correct at the edges)

## Why this exists

One word per concept. The engine has always said cylinder (the wire field `VoiceData.cylinder`, `Cylinder`,
`Cylinders`), the user-facing surface says orbit. Decision D1 of the engine tidy-up (KLANG-00286 (engine tidy up),
"Decided", 2026-10-07): "cylinder" is the engine's and the user's word. `orbit()` stays only as an alias in sprudel, to
honour its Strudel origin. Sprudel's canonical door becomes `cylinder` (aliases `orbit`, `o`), and the docs, KDoc,
tutorials, UI text and the Katalyst vocabulary say cylinder. Tracked as item 24 of KLANG-00290 (engine follow ups).

A shape change: the sorting rule of `_v1-scope.md` puts it before the tutorials.

## The steps

1. **The sprudel door.** `cylinder` becomes the canonical function, accessor object and mapper (today an alias of
   `orbit`); `orbit` and `o` become its aliases, with the same forms. KDoc examples say `cylinder`. The door parity
   and form specs follow.
2. **The Katalyst vocabulary.** The duck stage's sidechain knob `orbit` becomes `cylinder` on both doors
   (`KatalystStageDsl.Duck`, `KatalystBuilder.duck`, the KlangScript `duck(...)`, sprudel's `duck(...)` and its
   accessor), and the slot `duck.orbit` becomes `duck.cylinder` (a wire key, so the schema hash moves). No alias on
   the Katalyst side: D1 keeps `orbit` as an alias in sprudel only.
3. **The songs and examples.** The built-in songs, the frozen pieces' live copies where the corpus allows, the skills'
   examples and the KDoc examples say `cylinder(...)`. The frozen pieces keep their text (they are snapshots).
4. **The words in the docs and the UI.** KDoc, module docs, the Lexikon (the "Orbit" and "Orbit bus" entries and their
   link words), the scope badge (`@scope orbit` and `orbit-send`, the label text), the warehouse panel, the Katalyst
   reference. History keeps its words (archived tickets, memory history, dated records).
5. **The tutorials.** They keep working on the `orbit` alias. Their text is the tutorial stream's (KLANG-00145
   (tutorial curriculum), PAUSED): the change is offered to it, not made over its head.

## Acceptance

- `cylinder(...)` is the canonical door on every form; `orbit(...)` and `o(...)` are aliases and render the same.
- The duck's knob and slot say cylinder on every door; nothing in live code or live docs says orbit for the concept,
  except the two sprudel aliases and history.
- The corpus is identical (a rename changes no sound).

## History

- 2026-11-06: created; steps written from decision D1.
