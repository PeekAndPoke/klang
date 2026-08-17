# Mark effect scope (per-orbit vs per-voice) in the sprudel docs

Captured 2026-08-17 (user, mid tutorial session — B6 "Layers" teaches exactly this distinction).

## The problem

Whether a function is a **per-orbit (bus) effect** or a **per-voice setting** is load-bearing
knowledge — `room()` on one pattern wets every pattern sharing the orbit — but today it lives
only in one table inside `.claude/skills/klang-music-writing/ref/sprudel-reference.md`
("Effect scope" section). The user-facing docs (KDoc on the `lang_*.kt` DSL functions, and
whatever the editor docs popup renders from them) don't carry it at all.

Ground truth (from the skill ref — verify against the engine before publishing):

- **PER-ORBIT (bus)**, shared by all voices on the orbit: `body`/`vowel`, `room`/`reverb`
  (+ `roomsize`/`roomdim`/`roomfade`/`roomlp`/`ir`), `delay` family, `phaser` family,
  `compressor`, ducking.
- **PER-VOICE**: filters (`lpf`/`hpf`/`bandf`/`notchf` + envs/qs), `distort`, `crush`,
  `coarse`, `gain`/`velocity`/`pan`/`postgain`, envelopes, `vibrato`/`tremolo`, `fm*`,
  pitch env, `unison`/`spread`, `analog`, `sound`/`n`/`note`.
  ⚠️ Note `distort` is per-voice — the user's shorthand ("room, reverb, body, distort etc.")
  groups it with the bus effects; double-check each function against the engine, don't copy
  any list blindly.
- **PER-PLAYBACK (master)**: `master(Master.of(...))` fx.

⚠️ **The scope table above is itself an oversimplification** — proven during tutorial B6's
review (2026-08-17, verified in audio_be):

- The reverb **processor** is per-orbit, but `room` is a **per-voice SEND amount** into it
  (`SendRenderer.kt`: only voices with `room > 0` are summed into the orbit's reverb send
  buffer). "Shared by all voices on the orbit" is wrong for the send — a dry voice on a
  wet orbit stays dry.
- A bare `.room(x)` is **silent**: the reverb gates on `roomFade == null && roomSize < 0.01`
  (`KatalystReverbEffect.kt`), and `roomSize` defaults to 0.0. Every real song pairs `room`
  with `rsize`/`roomfade` or uses the colon form.
- Orbit bus **settings** are first-writer-wins (`Cylinder.kt`: "ONE owner per orbit … route
  to a different orbit if you want different bus settings").

So the docs metadata likely needs three notions, not two: *per-voice control*, *per-voice
send into a per-orbit effect*, and *per-orbit effect settings (first-writer-wins)*. Design
the vocabulary before mass-applying badges.

## Steps

1. **Docs metadata**: mark every effect function's scope at the source of the docs popup
   content (KDoc on the `lang_*.kt` functions — or wherever the popup's structured docs come
   from; investigate first). One consistent, short badge-like line, e.g.
   `Orbit-level effect — shared by every pattern on the same orbit.`
2. **Docs popup**: make the scope jump out visually in the editor docs popup (badge/label,
   not buried prose).
3. **Editor highlight color** (LATER, needs its own design round): bus-level functions get a
   different highlight color in the editor. Blocked on a broader decision: how to partition
   ALL functions into color groups (sources / per-voice settings / bus effects / master /
   structure?). Do not start this ad hoc — plan the grouping first.

## Related

- Tutorial B6 "Layers" teaches the concept ear-first (room bleeds on the shared orbit,
  `orbit(1)` isolates it) — the docs work makes the same fact discoverable at the function.
- Parameter-parity principle applies: one scope truth, stated in one place, propagated —
  never hand-maintained in several.
