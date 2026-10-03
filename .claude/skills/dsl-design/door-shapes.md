# The door shapes, function by function

> The living home of the per-function door-shape table (decision D6, maintainer, 2026-09-23). Moved here on
> 2026-09-28 from section 3b of the archived phase 3 record
> (`docs/tasks-archive/2026-09/20260928-builtin-instruments.md`). The rule itself is `SKILL.md` section 2.

Walked one function at a time. The rule applied: the stage's own musical inputs stay on the door (the
phaser precedent), secondary knobs move to a builder behind `configure`, and a door whose only
defaulted knob is temporary stays flat. A knob that nothing reads is removed, not moved.

| Door | Door parameters | Builder knobs | Notes |
|---|---|---|---|
| `lowpass`, `highpass` | `freq, q = 0.707, configure` | `passes`, `analog`, `humanize`, `env(semitones)`, `adsr(attackSec, decaySec, sustainLevel, releaseSec, e => e.curves(attack, decay, release))` (nested since 3c) | the envelope is ONE `adsr` call, the pattern of the chain's own `adsr`; `env` and `adsr` each switch the cutoff envelope on and the other fills from the constants (the compound fill, now at the end of the lambda). The third positional argument becomes the lambda, which ends the `lowpass`/`lpf` positional trap |
| `bandpass`, `notch` | `freq, q = 0.707, configure` | as above without `passes` | |
| `drive` | `amount` | none | `driveType` is REMOVED everywhere (door, `IgnitorDsl.Drive`, the wire, `DriveIgnitor`): it had one value and nothing read it. `drive` is boost without shaping; every colour belongs to `shape` |
| `shape` | `shape = "soft", oversample = 0` | none | flat: `oversample` is the D7 stopgap and leaves with `oversampling-regions.md` |
| `distort` | `amount, shape = "soft", oversample = 0` | none | flat for the same reason; matches sprudel's `distort` position for position |
| `pitchEnvelope` | `semitones, configure` | `adsr(attackSec, decaySec, sustainLevel, releaseSec, e => e.curves(attack, decay, release))` (nested since 3c) | `releaseSec`, `curve` and `anchor` leave: release and curve were read and DISCARDED on both surfaces (the Ignitor runtime and the strip's `PitchEnvelopeRenderer`); the sustain replaces `anchor` and the release becomes real. An ADSR attacks from 0 where the old envelope attacked from the anchor; no song sets `anchor` or calls `penv`. The 12 call sites become `pitchEnvelope(24, x => x.adsr(0.001, 0.04, 0, 0))` |
| `tremolo` | `rate, depth, configure` | `shape(name)` | 3b added `shape`, `skew` and `phase`; `skew` and `phase` were dropped on 2026-09-29 when the tremolo became a composition of the oscillators (`docs/tasks-archive/2026-10/20261002-tremolo-as-composition.md`). Rate first, like every Ignitor LFO door (`phaser`, `vibrato`); sprudel's `tremolo(depth, rate, shape)` says `rate` too (renamed from `sync` 2026-10-03, `docs/tasks-archive/2026-10/20261003-tremolo-rate-naming-parity.md`); its order differs on purpose: depth first is sprudel's bare-call rule, a bare call reads the pattern's values as the first parameter |
| `shimmer` | `wet, feedback = 0.5, tone = 4000, pitches, configure` | `floor` | the wet rule below; `dryFloor` becomes `floor` |
| `fm` | `modulator, ratio, depth, configure` | `adsr(attackSec, decaySec, sustainLevel, releaseSec)` | `freq` stays HIDDEN: the walk first put `freq(hz)` on the builder, then the maintainer, shown the recorded decision of 2026-08-30 ("a hidden internal of the pitch machinery; the default IS the semantics"), kept it hidden (2026-09-24). The node always had the index envelope; the Kotlin door exposed it (the built-in `sgbell` uses it) and the script door did not, a two-doors gap this closes. No `adsrCurves` yet: the FM envelope has no curve support, and a knob that does nothing is not offered. The maintainer wants it LATER; follow-up in `docs/tasks/future/envelope-shape-followups.md` §4 |
| `phaser` (Ignitor AND Katalyst) | `wet, rate, center, sweep, configure` | `floor` | ONE shape on both hosts. On the Katalyst the door parameters are OPTIONAL, and an omitted one means exactly what an omitted builder knob meant before: the bare stage's fixed default, never the owner voice. Only a `Param` slot reads the orbit's `katp` state, whether `classic()` places it or an author writes it with `Katalyst.param`. The one home of what each bare stage carries is `KatalystStageDsl`'s KDoc in `KatalystDsl.kt`, with the data classes' constructor defaults. (Corrected 2026-09-24 by the 3d(ii) implementer and the round 1 reviewers, who read the code; the first wording was the coordinator's, written without it.) On the Ignitor `rate` stays required, so writing it means writing `wet` too. The PRINCIPLE, decided here for every bus effect: the effect's musical inputs on the door; secondary knobs on the builder |
| `vibrato` | `rate, semitones` | none | no defaults, nothing to decide |
| `eq` (Ignitor AND Katalyst) | `configure` | `band(freq, q = 0.707, db = 0)`, `tap(freq, q = 0.707, gain = 1)` | already one shape; the lambda stays optional like every `configure` (maintainer: all configure callbacks are optional), and an `eq()` with no bands is a transparent stage |
| `reverb` (Katalyst AND Master) | `wet, size, lowpass` | none | all optional (an omitted one is the bare stage's fixed default on both hosts, see the phaser row; never the voice's slot). Flat because nothing is left for a builder. The songs' `m.reverb(r => r.wet(0.05).size(7))` becomes `m.reverb(0.05, 7)` (five songs and the frozen pieces) |
| `delay` (Katalyst AND Master) | `wet, time, feedback, configure` | `cap(level)` | all door parameters optional; `cap` is secondary, like `floor`, so it sits on a builder even as its only knob. The prefix matches sprudel's `delay(wet, time, feedback, cap)`, which stays flat (sprudel has no builder layer) |
| `gain` (Katalyst AND Master) | `gain = 1.0` | none | a single construction input, nothing to decide |
| `compressor` (Katalyst) | `threshold, ratio, knee, attack, release` | none | FLAT, all optional, identical to sprudel's: every knob of a dynamics stage is a musical input |
| `limiter` (Master) | `threshold, ratio, knee, attack, lookahead, release` | none | flat, as the compressor. `thresholdDb` and `kneeDb` are RENAMED `threshold` and `knee` (builder, node, wire): the compressor and sprudel already say so, and the dB unit lives in the KDoc |
| `body` (Katalyst) | `wet, material, configure` | `floor` | the wet rule; `material` moves from the builder to the door, and the door parameter takes a name OR a number/`Katalyst.param` slot, because the builder knob was the only way to write a slot there and step 5a-2 decided the names are index slots (maintainer, 2026-09-18) |
| `vowel` (Katalyst) | `wet, vowel, configure` | `floor` | as `body` |
| `duck` (Katalyst) | `orbit, depth, attack` | none | flat and all optional, like the compressor (a dynamics stage); identical to sprudel's |

Not walked, nothing to decide: `onepole`, `crush`, `coarse`, `detune`, `accelerate` (no defaults), the
oscillators (already `(freq, configure)`).

**The envelopes, walked 2026-09-25 for step 3c (maintainer):**

| Door | Door parameters | Builder knobs | Notes |
|---|---|---|---|
| chain `adsr` | `attackSec, decaySec, sustainLevel, releaseSec, configure` | `curves(attack, decay, release)`, `declick(seconds)` | the reach-back chain methods `adsrCurves`, `declickSeconds` and `expK` RETIRE (they modified the preceding `adsr`, or wrapped a fresh default one). Inside a builder the prefix goes: `curves`, `declick` |
| `adsr` inside the filter and pitch builders | the same | `curves(attack, decay, release)` | NESTED, one shape everywhere: `.lowpass(800, 1.2, x => x.env(24).adsr(0.01, 0.3, 0.2, 0.5, e => e.curves(...)))`. The 3d(i) builder knob `adsrCurves` on the filter and pitch builders moves into the envelope's builder as `curves`. No `declick`: those envelopes have no de-click stage, and a knob that does nothing is not offered |
| `adsr` inside the fm builder | the same, without `configure` | none yet | FM's index envelope has no curve support (the filed follow-up); it gains `configure` with `curves` when that lands |
| `expK` | REMOVED | | "maybe the user wants to set different k for each individual curve": a per-curve bend is its own later design. Until then every exp stage bends at `ADSR_EXP_K` = 3 |
| the ADSR on/off switch | node field only, no Ignitor door | | `classic()` fills it from sprudel's `adsrOn`/`adsrOff` (a built-in's envelope is ON by default and only the slot turns it off); on the Ignitor door, not writing `adsr()` already means no envelope. A deliberate two-door asymmetry |


**Consequences recorded with the decisions:**
- **The default curve of a modulation envelope (filter and pitch) is ONE decision, and it is D3's.** Every
  pitch sweep today is linear on both surfaces and the chain's `adsr` defaults to exp; the filter
  envelope is linear on the node and exp on the strip. With `curves` on every envelope (nested inside `adsr` since 3c), D3 becomes
  "which default", not "which law".
  (Settled 2026-09-25, phase 3 step 5b (b): `MOD_ENV_CURVE = Exponential`, so every modulation envelope defaults to the house exponential on both hosts; the strip pitch envelope joins in (c).)
- **The wet rule (maintainer, 2026-09-23): `wet` is the VERY FIRST parameter of every door that has
  one, in all four DSLs, and `floor` lives on the builder.** Consistency over each door's local
  logic. It applies to phaser, shimmer, reverb, delay, body and vowel. SPRUDEL MOVES TOO: its
  `phaser(rate, wet, ...)`, `body(material, wet, floor)` and `vowel(vowel, wet, floor)` become
  wet-first. Measured cost: 7 positional `body("...")` calls in built-in songs (StrangerThings, Sakura,
  IrishLamentTechno, SoundOfTheSea) and 4 in the frozen songs, which become `body(material = "...")`,
  bit-identical; no song calls `vowel` or `phaser` positionally. One semantic knock-on: sprudel's
  "no argument reinterprets the pattern's values as the first parameter" now reinterprets them as
  `wet`, so `seq("<0.2 0.5>").body()` patterns the wet (the first wording here said `n(...)`, which carries
  no value to reinterpret; corrected by the 3d(iii) implementer). What it cost, mapped in 3d(iii)'s plan:
  the bare `"<wood glass>".body()` / `"<a e>".vowel()` shortcut no longer names the stage (write
  `body(material = "<wood glass>")`), and the bare call's CLEAR path for material and vowel is gone (the
  off switch is `material = "none"` / `vowel = "none"`). No song, frozen piece or doc used either. The
  Lexikon, the sprudel reference and the KDoc examples moved with it (`body(0.7, "wood")`, `body(material = "wood")`).
  AND a positional `body("wood")` / `vowel("a")` in a user script outside the repo now SILENTLY does
  nothing: `"wood"` is a valid mini-notation pattern, so it lands in `wet` as a non-number, and the wet head
  writes nothing (the house raw rule forbids a `require`). `phaser(0.3)` becomes wet 0.3 at the default
  rate. Release-note item; whether the editor should WARN on a string literal in a wet slot is open for
  the maintainer.
- **The dry floor is `floor` everywhere** (maintainer, 2026-09-23): the Ignitor's `dryFloor` on the phaser
  and shimmer builders, their nodes and the wire is renamed; the Katalyst and sprudel already say `floor`.
  Re-asked 2026-09-24 with the reason the Ignitor KDoc gave for `dryFloor` (`floor()` is the arithmetic
  round-down, one word must not mean two things); the maintainer kept `floor` ON A CONDITION: "as long as
  floor() is only on effect builders we can live with the duplication in naming". So `floor` is a knob on
  effect builders (and a named parameter of sprudel's effect doors, which have no builder layer), never a
  door parameter of an Ignitor effect and never a method on `IgnitorDsl`.
- **Identity while D3 is open.** The curve knobs on the filter and pitch envelopes (`adsr(..., e => e.curves(...))` since 3c) must DEFAULT to
  the law each envelope has today (linear on both nodes), or every filter sweep and every kick in the songs
  changes in a door-shape step. D3 then decides the default for both at once, at its own ear checkpoint.
  (Done: D3 decided exponential, landed in step 5b (b), 2026-09-25.)
- **Wire changes** (the wire golden is regenerated, never hand-edited): `driveType` removed; `dryFloor`
  becomes `floor`; the limiter's `thresholdDb`/`kneeDb` become `threshold`/`knee`; the pitch envelope loses
  `releaseSec`, `curve` and `anchor` and gains the ADSR fields.
- **Positional reinterpretation (step 3d(i), 2026-09-24).** Wet-first moves a positional meaning: `phaser(0.3,
  800)` was rate 0.3 Hz and center 800, and is now wet 0.3 with an 800 Hz LFO; `shimmer(0.4)` was feedback 0.4
  and is now wet 0.4, on both doors. No in-tree caller was affected (grepped), but a user script outside the
  repo changes SILENTLY. The old positional filter and `pitchEnvelope` forms fail loudly instead. A release
  note must say it.
- **The limiter's positional order (step 3d(ii)).** `limiter(threshold, ratio, knee, attack, lookahead, release)`
  puts `lookahead` FIFTH, where the compressor and sprudel put `release`: a compressor-shaped 5-argument call
  `m.limiter(-3, 4, 2, 0.01, 0.2)` sets 200 ms of lookahead (bounded to 50 ms, i.e. latency), not a release. It
  is the maintainer's table; the release note recommends named arguments for the limiter.
- **Recorded two-door asymmetries (step 3b, 2026-09-25).** The Kotlin `tremolo` door stays FLAT
  (`tremolo(rate, depth, shape = "sine")`) where the script door takes a builder, the
  filter doors' precedent. The Kotlin `shape` and `distort` doors keep a `String` shape and an `Int` factor, while
  the script doors also take a number or a slot; a Kotlin caller writes a slot through the node constructor, as
  for `floor` and the pitch envelope.
- **Recorded two-door asymmetries (step 3d(i)).** The Kotlin filter doors stay flat and name the four envelope
  stages separately (audio_bridge cannot see the script builders; a superset of the builder). The Kotlin
  `phaser`/`shimmer` set `floor` only by `.copy(floor = ...)`: `floor` exists only as an effect-builder knob
  (the maintainer's condition), so the Kotlin node extensions `.wet()`/`.dryFloor()` were REMOVED, not renamed.
  There is no Kotlin `pitchEnvelope` door (there was none before either); Kotlin builds the node.
- **Sprudel's `penv` carries two dead slots**, `release` and `curve`, and an `anchor` whose meaning moves
  to a sustain. Raised for the sprudel side, not decided here. (Decided in D3 (4), landed in step 5b c1,
  2026-09-25: `penv(amount, attack, decay, sustain, release)`, a real release, `curve` and `anchor` removed.)
