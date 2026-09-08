# Mark effect scope (per-orbit vs per-voice) in the sprudel docs

Captured 2026-08-17 (user, mid tutorial session — B6 "Layers" teaches exactly this distinction).

## The problem

Whether a function is a **per-orbit (bus) effect** or a **per-voice setting** is load-bearing
knowledge — `room()` on one pattern wets every pattern sharing the orbit — but today it lives
only in one table inside `.claude/skills/klang-music-writing/ref/sprudel-reference.md`
("Effect scope" section). The user-facing docs (KDoc on the `lang_*.kt` DSL functions, and
whatever the editor docs popup renders from them) don't carry it at all.

Ground truth (from the skill ref — verify against the engine before publishing):

- **PER-ORBIT (bus)**, shared by all voices on the orbit: `body`/`vowel`, `room` (slots
  `wet`/`size`/`fade`/`lowpass`/`dim`, plus `ir`), `delay` (slots `wet`/`time`/`feedback`/`cap`), `compressor`, ducking.
  ⚠ UPDATED (P chunk, 2026-08-24, supersedes the C4.2 correction): THE BUS OWNS THE
  PHASER — the built-in pipeline presets carry no per-voice phaser stage anymore, so the
  `phaser` family IS per-orbit (one sweep over the summed mix, first-writer-wins on the
  knobs). Only a CUSTOM pipeline that adds `StageDsl.Phaser` gets an extra per-voice pass
  (then a floor below 1 floors the dry twice). Document it as per-orbit with that caveat.
- **PER-VOICE**: filters (`lpf`/`hpf`/`bpf`/`notch` with their env and q slots), `distort`, `crush`,
  `coarse`, `gain`/`velocity`/`pan`/`postgain`, envelopes, `vibrato`/`tremolo`, `fm*`,
  pitch env, `unison`/`spread`, `analog`, `sound`/`n`/`note`.
  ⚠️ Note `distort` is per-voice — the user's shorthand ("room, reverb, body, distort etc.")
  groups it with the bus effects; double-check each function against the engine, don't copy
  any list blindly.
- **PER-PLAYBACK (master)**: `master(Master(m => ...))` fx.

⚠️ **The scope table above is itself an oversimplification** — proven during tutorial B6's
review (2026-08-17, verified in audio_be):

- The reverb **processor** is per-orbit, but `room` is a **per-voice SEND amount** into it
  (`SendRenderer.kt`: only voices with `room > 0` are summed into the orbit's reverb send
  buffer). "Shared by all voices on the orbit" is wrong for the send — a dry voice on a
  wet orbit stays dry.
- A bare `.room(x)` is **silent**: the reverb reads that config as OFF
  (`KatalystReverbEffect.configure`'s active test against `MIN_ACTIVE_ROOM_SIZE` — since the
  drain lifecycle it drains a leftover tail instead of freezing it), and `roomSize` defaults
  to 0.0. Every real song pairs `room(wet)`
  with `size`/`fade` (the colon form is gone since C0).
- Orbit bus **settings** are first-writer-wins (`Cylinder.kt`: "ONE owner per orbit … route
  to a different orbit if you want different bus settings"). The skill ref's scope box says
  "last-writer-wins" — stale; Cylinder.kt explicitly replaced last-writer-wins with the lease.
  Fix the ref when doing step 1.

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

---

## Done 2026-09-08 (steps 1 and 2; step 3 still deliberately parked)

The vocabulary the doc asked for turned out to need a fourth notion, and the third one landed at
function level rather than per slot:

| `@scope` | Badge | Members |
|---|---|---|
| `voice` | PER VOICE | filters, `distort`, `crush`, `coarse`, `gain`/`pan`/`postgain`/`velocity`, `adsr`, `unison`, `density`, `fm`, oscillator params, pitch modulation, `tremolo`, `pipeline` |
| `orbit` | ORBIT BUS | `body`, `vowel`, `phaser`, `compressor`, `duck`, `ir` |
| `orbit-send` | ORBIT BUS + SEND | `room`, `delay` |
| `master` | MASTER | `master` |

`orbit` / `o` / `cylinder` carry no badge on purpose: they choose a bus, they are not one.

**Why `orbit-send` exists.** The doc predicted the two-way split would not survive contact with the
engine, and it did not. A plain ORBIT badge on `room` would have taught the very misconception this
task was written to kill, because `wet` is a per-voice send while `size`/`fade`/`lowpass` are the
orbit's. One honest badge beats a badge plus a footnote nobody reads.

**Scope is metadata now, not prose.** `@scope` in the KDoc, parsed by `KDocParser`, carried on
`KlangSymbol.scope` as `KlangScope` (in `klangscript-annotations`, the only module both the processor
and the runtime see), rendered by one `klangScopeLabel` in the editor popup header and on the library
docs page. `SprudelScopeSpec` asserts the whole chain on the GENERATED docs, since a name like `room`
is emitted twice and only the merged symbol is what the popup renders, and it fails if a new effect
or dynamics function ships without a scope.

**The vocabulary page.** Concepts are linked, not re-explained per function: Lexikon entries gained a
slug and a page of their own at `/manuals/lexikon/{slug}`, and three entries were written for the
terms this task is about (Voice, Orbit bus, Send, the last one carrying the warning that `body` and
`vowel` have a `wet` that is NOT a send). `LexikonSpec` guards the link contract, including that a
renamed term cannot silently break every KDoc link to it.

**Params were cut to table width**, because the popup renders them as a table and the detail belongs
in the prose above it, which renders as markdown.

### Engine claims corrected on the way

The scope tags themselves were all verified correct in review. The false claims were around them:

- `density` does nothing on a supersaw (only the dust ignitor reads it); both "tight supersaw"
  examples were no-ops.
- `body("wood glass")` is mini-notation, wood then glass, not a blend.
- `penv`'s `curve` and `release` are dead: the pitch ramps are linear and there is no release phase.
- `ir` / `iresponse` is a stub with no convolution path anywhere; a badge on a no-op reads as working.
- `tremolo` lost `sawtooth` and `ramp` from its shape list, and its `skew` is -1 to 1, not 0 to 1.
- `sndDust` documented `density` as impulses per second, the same misconception, outside the diff.
- ranges that are guidance rather than clamps no longer read as hard limits (the Motor is raw).

The skill ref's scope table said **last-writer-wins**; the engine has said first-writer-wins since
`Cylinder.kt` took the lease. Corrected, with the send row added, and it now defers to the generated
badge on any disagreement.

### Step 3 stays parked

Editor highlight colours per function group are still blocked on the same broader decision the
original doc names: how to partition ALL functions into colour groups. Do not start it ad hoc.

### Found while doing this, not fixed here

The docs popup can overflow the bottom edge when it opens low on screen: `SemanticUiPopupComponent`
measures its content on its own mount/update only, so a child that renders asynchronously
(`MarkdownDisplay`) grows the card after it has been placed, and `calculatePopupPosition`'s
flip-up fallback then tests a stale height. The fix belongs in kraft (watch the popup for a size
change and recalculate), and the maintainer owns it; capping the card against the space actually
available below the anchor is the complementary half and lives here.
