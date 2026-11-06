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
   **DONE 2026-11-06.** `cylinder` is the accessor object (`@KlangScript.Object("cylinder")`, its call form the mapper)
   with the pattern, string and chained-mapper functions; `orbit` and `o` are alias constants of it plus the same
   function forms, as `o` was of `orbit` before. The file is `lang_dynamics_cylinder.kt` (was `lang_dynamics_orbit.kt`).
   `FreqAccessorIntelSpec` and `CallableObjectDocsSpec` name `cylinder` the canonical object and both words its aliases;
   `LangDoorFormsSpec` already covered all three forms.
2. **The Katalyst vocabulary.** The duck stage's sidechain knob `orbit` becomes `cylinder` on both doors
   (`KatalystStageDsl.Duck`, `KatalystBuilder.duck`, the KlangScript `duck(...)`, sprudel's `duck(...)` and its
   accessor), and the slot `duck.orbit` becomes `duck.cylinder` (a wire key, so the schema hash moves). No alias on
   the Katalyst side: D1 keeps `orbit` as an alias in sprudel only.
   **DONE 2026-11-06.** `KatalystStageDsl.Duck.cylinder`, `Slots.duck.cylinder` (key `duck.cylinder`), the Kotlin and
   KlangScript `duck(cylinder = ...)`, `Katalyst.slot.duck.cylinder`, sprudel's `duck(cylinder, depth, attack)` with
   its accessor `duck.cylinder`, and the backend's duck writer; the specs, the allocation cases and the songs' slot
   keys followed. No alias kept on this knob, as planned. The wire schema hash moved.
3. **The songs and examples.** The built-in songs, the frozen pieces' live copies where the corpus allows, the skills'
   examples and the KDoc examples say `cylinder(...)`. The frozen pieces keep their text (they are snapshots).
   **Songs DONE 2026-11-06:** the 13 built-in songs that routed with `.orbit(` say `.cylinder(` (no `.o(` was in use),
   as do the sprudel KDoc examples outside the alias's own and the music-writing skill's reference (its door table
   flipped).
4. **The words in the docs and the UI.** KDoc, module docs, the Lexikon (the "Orbit" and "Orbit bus" entries and their
   link words), the scope badge (`@scope orbit` and `orbit-send`, the label text), the warehouse panel, the Katalyst
   reference. History keeps its words (archived tickets, memory history, dated records).
   **DONE 2026-11-06** (maintainer: "yes update everything"): the engine's identifiers and prose too, not only what a
   user sees. About 2,500 lines in 320 files: KDoc and comments, test names and helpers (`createCylinder`,
   `claimsCylinder`, `cylinderBlockRan`, `WARMUP_CYLINDERS`, `CylinderOwnershipSpec`), the `orbit: Int` parameters of
   the phase pools, the scope badge (`KlangScope.CYLINDER`, tag `@scope cylinder`, label "CYLINDER BUS"), the warehouse
   panel's hints, the module docs, `audio/ref`, the skills, the open tickets, the whitepaper and its figures (its
   "bus, orbit, cylinder?" box now names `orbit` as the Strudel word and the alias), the dev patterns, the jingle and
   the benchmark songs. Where the engine used to tell the number from the instance by the two words, the prose now says
   "cylinder number" and "`Cylinder` instance". The Lexikon: the "Orbit" entry is folded into "Cylinder" (which names
   the alias), "Orbit bus" is "Cylinder bus" (its page is `cylinder-bus` now; no live text linked the old slugs).
   Kept on purpose: the two sprudel aliases and the specs that prove them, the Strudel compatibility tests (they run
   real Strudel, whose word is `orbit`), the frozen pieces, the published blog posts, dated records and histories,
   ticket titles, the linker's own unit fixture (`LexikonAutoLinksSpec`, invented entries that test word boundaries),
   and the workshop's domain-word check, which still lists `orbit`.
5. **The tutorials.** They keep working on the `orbit` alias. Their text is the tutorial stream's (KLANG-00145
   (tutorial curriculum), PAUSED): the change is offered to it, not made over its head.

**Steps 1 to 3, the checks (2026-11-06):** reviews level 1, one round each, clean (step 1: each alias names both
other words, the canonical rows in `LangFieldAccessorsSpec`; step 2: KDoc and skill tables). Every suite green, the
corpus identical in sound (the 13 songs edited in text only), mutations 2 of 2 KILLED on the new canonical rows. Left:
step 4 (the words) and step 5 (the tutorials, offered).

## Acceptance

- `cylinder(...)` is the canonical door on every form; `orbit(...)` and `o(...)` are aliases and render the same.
- The duck's knob and slot say cylinder on every door; nothing in live code or live docs says orbit for the concept,
  except the two sprudel aliases and history.
- The corpus is identical (a rename changes no sound).

## History

- 2026-11-06: created; steps written from decision D1.
- 2026-11-06: steps 1 to 3 done (the door, the duck's knob, the songs and examples).
- 2026-11-06: step 4 done, everything, the engine's identifiers included (maintainer: "yes update everything").
