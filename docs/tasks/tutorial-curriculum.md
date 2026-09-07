# Tutorial Curriculum — Rework Plan

> **Hand-off from the DSL work, 2026-09-06.** The authoring surface changed under this plan before
> any Ignitor/Master/Pipeline tutorial was written; teach the NEW forms only
> (`docs/tasks-archive/2026-09/20260906-dsl-configure-lambdas.md`, `/dsl-design`):
> - Oscillator knobs live in a configure lambda: `Osc.supersaw(x => x.voices(9).spread(0.1)).lowpass(800)`.
>   There is no `Osc.supersaw().voices(9)` and no `.analog()` on a sound any more.
> - Equalizer: `.eq(e => e.band(300, 1.0, -4).tap(850, 0.707, 1.7))`; phaser/shimmer wet knobs:
>   `.phaser(0.3, x => x.wet(0.3))`.
> - Master: `master(Master(m => m.reverb(r => r.wet(0.05)).gain(2.5).limiter()))`, `master(Master())`
>   for unity. `Master.of` and `MasterFx` are gone.
> - Pipeline: `Pipeline(p => p.filterMod().vca().distort().filter().vca())`,
>   `Pipeline.modern(p => p.tuneVca(v => v.expK(2.5)))`. `Pipeline.of` and `Stage` are gone.
> The song-usage counts below (line "Master: 5/14 songs ...") predate this; the five songs now use
> the `Master(m => ...)` form.

> **Second hand-off, from the sprudel field accessors, 2026-09-07**
> (`docs/tasks/sprudel-field-accessors.md`, four batches, all specced and reviewed): every numeric
> setter is now also a READER and accepts a mapper, so two spellings that did nothing before are
> real surface:
> - `gain(mul(0.5))`: change a knob relative to what the chain already set.
> - `bpf(freq)`, `bpf(freq.mul(2))`: one knob reads another field of the same event.
>
> 88 accessors, 54 alias constants, and a built-in song (**Greensleeves**) built on it. This is
> **purely additive**: before the sweep a mapper handed to a setter was silently dropped and the
> field cleared, which was a bug nobody taught, so no shipped lesson needs rewriting. Teaching it
> is **A9**'s job (ladder and obligations register below); no other lesson should introduce it in
> passing.

Status: **ACTIVE**, last updated 2026-08-31. **17 lessons shipped**: the Stage-1 onramp (B1-B3), all of
Stage 2 (A1-A4, B4-B7), and Stage 3 so far (A5, A6, A7, B8, B9, B10). The ladder below is no longer a
proposal, it is the contract; the obligations register is the debt ledger against it. Next slots: **A8
Body**, **B11 Chords & voicing** and **A9 The note moves the knobs** (recorded 2026-09-07, the surface
shipped before the lesson), which close Stage 3. Stage 4 (C1-C10, the Motor track) is unwritten.
A6 and A7 are authored but have NOT been through the review loop, and the by-ear pass is owed on both.

## Why (diagnosis, short version)

The 38 existing tutorials were generated unattended by the `tutorial-factory` loop. Measurable symptoms:
fixed template skeleton (32/38 end with "Putting It All Together"), mandated sculptor metaphor (17 files),
zero ear training ("listen for" appears 0 times), difficulty-scrambled Prev/Next navigation (registry is in
generation order), finale sections that use functions never taught in that tutorial, and zero coverage of
Ignitor / Master / Pipeline (they weren't on the generator's function allow-list). One factual error:
`tut_EveryTrick.kt` describes `.every(3, slow(2))` as "reverses" — fix regardless.

Ground truth from the 14 built-in songs (full tally in session analysis, key facts):

- Used by **all 14** songs: `stack`, `note`/`n`, `sound`/`s`, `gain`, `adsr`. Second tier (10+):
  `orbit`, `hpf`/`lpf`, `pan`, `superimpose`, `fast`/`slow`, `room`, `postgain`, `distort`, `onepole`, `analog`.
- Signals-as-modulators is the highest-value intermediate concept: 72 `.range(` calls across 7 songs.
- Mini-notation actually used: sequences, `~`, `[]`, `<>`, `*`, `!`, `@`, comma-chords, `|`, `` >/n `` suffix,
  `struct` gates. Never used: polymeter. `.euclid()` in one song only.
- Ignitor building is opt-in (6/14 songs) with a natural teaching ladder:
  Sandsturm → IrishLament → DialogueWithTheStars → Sakura → ATruthWorthLyingFor.
- Master: 5/14 songs, always the static `master(Master.of(...))` carrier. Pipeline: 2/14, both `"pedal"`.
- Two arrangement idioms both in real use: `arrange([bars, section], ...)` vs. one big `stack` + `filterWhen(t => ...)`.

## Principles (the anti-slop rules)

1. **Sound before vocabulary.** Hear the thing first, name it after.
2. **One running example per lesson.** Each section mutates the same phrase; no disconnected snippets.
3. **Main topic + touches.** Every lesson has ONE main topic. It may freely use:
   the **carrier kit** (taught in lessons 1–3: `sound`, sequences, `~`, `note`, `gain`) plus everything
   earlier in the path. Anything else must be a labeled **preview** with a link to the lesson that teaches it.
   → *Lintable:* parse code blocks, check identifiers against (carrier ∪ taught-so-far ∪ declared previews).
4. **Every lesson has at least one "listen for …" A/B moment.** Same code twice, one change, directed ear.
   Write each side of the pair as ONE whole statement on its own line, so a single `//` toggles it and the
   reader can swap without editing anything else. The commented-out side is real code the lesson tells the
   reader to run, so it is compiled by the spec exactly like the live line (lint-enforced): roughly a
   quarter of the corpus's runnable code sits behind a `//`, and none of it was checked until 2026-08-31.
5. **The finale consolidates, never introduces.** Closing example = this lesson's topic + earlier material only.
6. **No template headings, no mandated metaphors.** Structure grows from the topic.
7. **Short path, fat reference.** The ladder stays lean; exhaustive parameter lists live in the Lexikon, cross-linked.
8. **Every finale must sound at least ok, ideally good.** Render QA (below) enforces the floor;
   the by-ear polish pass owns the ceiling.
9. **Cross-lesson references go by lesson NAME, never by reading order.** Readers browse lessons
   freely — the registry order is a default path, not a promise. "Last lesson"/"next lesson" are
   banned (lint-enforced); prose interpolates the shared `Tut` title constants (`TutorialModel.kt`)
   so a rename updates every reference. Lessons that don't exist yet are referenced by topic
   ("the Layers lesson", "the scales lesson").
10. **Long pattern strings split into halves with a double space** — more than four events per
    cycle reads better as two groups: `"bd hh sd hh  bd hh sd oh"`, `"a a a  b b b"`; four or
    fewer stay single-spaced (`"a a a"`). Whitespace is semantically free in mini-notation;
    lint-enforced.
11. **Useful simplification beats technical precision in early lessons** (user ruling 2026-08-17,
    from the lpf/hpf case). A first mental model may be "roughly" true — lpf(800)/hpf(800)
    "split the sound into two halves" is exactly right for building understanding, even though
    filter slopes and corner bias make it technically fuzzy. Mark approximations lightly
    ("roughly") rather than explaining them; precision arrives in the lesson that needs it.
    Reviewers must not flag calibrated simplifications as factual errors — the bar is "builds a
    correct-enough model", not "survives a DSP audit". **Corollary (user ruling, same day): the
    filters and effects are still in flux — prose must NEVER warrant tunable engine internals**
    (slopes, corner positions, combination arithmetic, curve constants, internal defaults of
    effect stages). Describe the knob's intent and what to hear; exact numbers live only in code
    examples, where retuning is cheap and the render/by-ear pass owns them. Review mandate:
    verify prose against the CODE EXAMPLE and the stable intent, not against DSP source that may
    change under it. The real "hardcore" technical tier comes at the very end of the ladder,
    in the future, once the engine is considered stable — no lesson before then goes deeper
    than intent + ear.
12. **Mostly every code line carries a comment saying what that line does** — and, where relevant, what to
   listen for on that line (`// saw.fast(4): a pump 4× per cycle — the sidechain feel`). Comments are
   narration, never decoration (no mood/metaphor comments — that rule produced the sculptor slop). Comment
   vocabulary follows the same taught-so-far rule as code. Familiar carrier boilerplate may go bare once
   it has been commented in earlier lessons.
   **Inside one code block, every line comment starts at the same column** (lint-enforced): the comments
   are a narration column running down the right, and a ragged one reads as sloppy code. The column is
   set by the longest code line in the block; the gap after it is free (the corpus uses one or two
   spaces). A line that is nothing but a commented-out A/B alternative has no trailing comment of its
   own unless it carries one, in which case it aligns with the rest.
13. **Concepts before use — including WITHIN a lesson (user notice to content reviewers, 2026-08-18).**
   A concept may only be used after it has been explained: earlier in the SAME lesson (section order
   counts — the vocab lint only checks lesson order, so reviewers must check section order by hand),
   or in a lesson that comes earlier on one of the paths. This covers PROSE concepts, not just
   lintable function calls. It will not always work 100% — e.g. B1 plays `gain()` from its first
   block and explains it in its last section; such carrier exceptions are acceptable when the code
   comment carries a minimal gloss — but every reviewer panel must actively look for
   used-before-explained concepts and flag them.

Model resources: Ableton Learning Synths (topic order, one param at a time), Syntorial (match-by-ear),
Strudel workshop (short path + separate reference, inline "try it" nudges), SoS Synth Secrets
(sequencing + instrument-recreation capstones — copy the order, not the delivery).

## The three tracks, braided

Track A = Sound (synthesis ear). Track B = Pattern (sprudel hand). Track C = Motor (Klang engine).
The published Prev/Next path interleaves them; each lesson lists main / touches / running example / listen-for.

### Stage 1 — Onramp (carrier kit)

The `GettingStarted` tag marks Stage-1 lessons only — it feeds a live filter view, so later
stages must not carry it.

| # | Lesson | Main | Touches | Running example / Listen for |
|---|--------|------|---------|------------------------------|
| B1 | Your first beat | `sound()`, sequences, the cycle | `gain` | Kick–snare loop built up step by step. *Listen for: the loop seam — where the cycle restarts.* (The old `tut_YourFirstBeat` had the right shape — steal its structure from git history, write fresh.) |
| B2 | Space and rests | `~` | accents via `gain` | Same beat; groove appears by removing hits. *Listen for: the hole where the kick was.* |
| B3 | First notes | `note()`, letter names, octaves | `.sound("sine")` as carrier | Tiny melody on sine. *Listen for: octave jump vs. step.* |

### Stage 2 — Core sound + core notation (alternating)

| # | Lesson | Main | Touches | Running example / Listen for |
|---|--------|------|---------|------------------------------|
| A1 | The four waveforms | oscillator timbre: sine, tri, saw, square (+ noise) | carrier only | ONE fixed phrase, swap `.sound()` per section. *Listen for: the buzz the saw adds over the sine; the hollowness of the square.* |
| A2 | ADSR — a note's shape in time | `adsr` (amp envelope), dedicated lesson (currently missing) | — | Same phrase morphs pluck → organ → pad by moving one letter at a time. *Listen for: attack snap vs. fade-in.* |
| B4 | Subdivision | `[]`, `*` | — | Hi-hat line densifies. *Listen for: how `[hh hh]` fits the same time slot.* |
| B5 | Alternation & repetition | `<>`, `!`, `@` | — | Bassline that changes per cycle (Sandsturm's `<bar1 bar2 bar3 bar4>` idiom). *Listen for: the 4-bar rotation.* |
| A3 | Filters — LPF & HPF | `lpf`, `hpf`, `lpq` | — | Saw phrase under a moving blanket; then thin it from below. *Listen for: which disappears first — the body or the sparkle.* (The old `tut_FilterPlayground` had good bones — same idea, fresh writing.) |
| A4 | The filter envelope | `lpadsr`, `lpe` | `lpq` | The classic synth pluck: cutoff rides its own envelope. *Listen for: the "öw" the filter sweep adds to each note.* (Zero coverage today.) |
| B6 | Layers — stack & orbit | `stack`, `orbit` | `room(wet, size)` on the lead's own orbit | Beat + bass + melody combined; reverb on melody's orbit only. *Listen for: dry drums under a wet lead.* |
| B7 | Chords in one step | comma-chords `[0,7,12]`, random pick `\|` | — | Power-chord stabs; a step that gambles. *Listen for: which variant played this cycle.* |

### Stage 3 — Where the tracks meet

| # | Lesson | Main | Touches | Running example / Listen for |
|---|--------|------|---------|------------------------------|
| A5 | Signals move the knobs | `sine`/`saw`/`perlin` + `.range()` into `gain`/`pan`/`lpf` | `.slow`/`.fast` on signals | Tremolo → autopan → filter sweep → sidechain pump (`saw.fast(4).range(0.65, 0.42)` — Sandsturm). *Listen for: the pump breathing with the kick.* **The merge lesson: patterns and sound design become one idea.** |
| B8 | Scales & melodies | `n()`, `scale()`, `transpose` | — | Numbers instead of note names; same line, swap the scale. *Listen for: major vs. minor mood flip.* |
| B9 | The transform toolkit | `fast`/`slow`, `superimpose`, `legato`, `clip` | — | One melody, four transformations, by song-frequency order. *Listen for: superimpose's thickening vs. an octave doubling.* |
| B10 | Gates — struct | `.struct("x ~ ~ x ...")` | `chord` preview | The tresillo gate from Sandsturm. *Listen for: 3-3-2.* |
| A6 | Thickness — unison, spread, analog | `unison`, `spread`, `analog` | — | Supersaw anatomy: one copy → many → spread apart → drifting. *Listen for: the shimmer of copies disagreeing about the pitch.* ⚠️ The original *mono vs. wide on headphones* listen-for was ENGINE-FALSE and is dropped: the super oscillators sum to mono and `panSpread` is wired but inaudible. Stereo width belongs to B9, which earns it with a transposed copy panned opposite. |
| A7 | Space & dirt | `room`, `delay`, `distort`, `onepole`, `postgain` | — | Dress the sound (room/delay), dirty it (distort/onepole), lift it (postgain). ⚠️ Chain order is FIXED by the PipelineDsl (FilterPipelineBuilder iterates the preset's stages) — sprudel CALL order does NOT reorder the chain, so never A/B "swapped order" here; the order-matters demo belongs to C7 via `.pipeline()`. |
| A8 | Body | `body()`, `bodyWet` | — | Same pluck through mahogany / glass / membrane. *Listen for: the cabinet in front of the speaker.* (8/14 songs use it; zero tutorials.) |
| B11 | Chords & voicing | `chord()` + `voicing()`, why Am–F–C–G works | `struct` | Progression built from song examples, one paragraph of real harmony. (The old `tut_ChordsAndHarmony` staging was sound — reuse the staging, not the file.) |
| A9 | The note moves the knobs | field accessors: a knob changed relative to itself (`gain(mul(0.5))`), and a knob that reads another field (`bpf(freq)`, `bpf(freq.mul(2))`) | `bpf`/`bpq` and the `mul`/`add` mappers, none of which anything teaches yet | Pink noise through a bandpass sitting on the note: the wind whistles the melody. *Listen for: noise turning into a pitch as the filter locks onto each note.* Built-in song **Greensleeves** is the reference; the surface landed 2026-09-07, the lesson did not. |

### Stage 4 — Track C: the Motor

| # | Lesson | Main | Touches | Running example / Listen for |
|---|--------|------|---------|------------------------------|
| C1 | Caricature drums (recipes) | kick = sine + `pitchEnvelope`, hat = noise + `hpf`, snare | everything so far | Build a drum kit from raw waves, pattern-level. *Listen for: the pitch drop that makes a kick a kick.* Caricature model: 2–4 acoustic tells, tune by ear. |
| C2 | Your first Ignitor | `Osc.*` chains: osc → filter → adsr | — | Sandsturm's lead, explained line by line; rebuild C1's kick as an Ignitor. *Listen for: detuned square joining the saw.* |
| C3 | Layered ignitors | additive `.plus()` stacks | — | IrishLament's flute/fingerpick/contrabass: many layers, flat wiring. *Listen for: the noise crackle that makes the pluck "wood".* |
| C4 | Knobs & variants | `Osc.param`, `.oscp()`, `Osc.variants` | — | DialogueWithTheStars' three guitars, round-robin. *Listen for: open vs. muted variant.* |
| C5 | Living instruments | signal-arithmetic cutoffs, pitch-tracking filters, `Osc.slot.analog`, perlin vibrato | — | Sakura's shakuhachi & pad, dissected. *Listen for: the filter following the note's pitch.* |
| C6 | The Master bus | `master(Master(m => m.gain(2.5).limiter()))` | `compressor` | Build a quiet mix, lift and limit at the end (ATruthWorthLyingFor / StrangerThings chains). *Listen for: limiter grabbing the peaks.* |
| C7 | Pipeline — modern vs. pedal | `.pipeline()` topology (VCA-last vs. VCA-first) | `distort` | ONE word swapped on the TetrisRemix dub bass. *Listen for: quiet attacks staying clean in "pedal".* |
| C8 | Arranging a song | `arrange([bars, section])` AND `filterWhen(t => ...)` | — | The same 3 sections arranged both ways; when to use which. |
| C9 | Live technique & remixing | mute/solo, live edits, `.oscp()` tweaks, `export`/`import` | — | Remix lesson: import Tetris' `leadPattern` like TetrisRemix does. |
| C10 | Capstone: a song from zero | everything | — | Build a Sandsturm-lite start to finish — the "worked song". |

### Obligations register (promises earlier lessons made — the named lesson must keep them)

- **A1 (waveforms):** B3 calls sine "the plainest voice" and points the siblings (saw/square/triangle)
  to "their own lesson: The Four Waveforms". A1 must keep that promise and formalize **"voice"** as the standing term for
  oscillator timbre (B3 introduced it informally).
- **A2 (ADSR):** B3's finale states each note "holds for its whole step and then stops" and points
  shaping attack/fade to "its own lesson: The Shape of a Note". A2 opens from that fact (default
  sustain is organ-like).
- **C1 (caricature drums):** A1's noise section promises "building your own [hi-hat] from raw
  noise comes in the Motor track" and frames drum-machine hats as "a short burst of shaped noise" —
  C1 must deliver exactly that recipe (noise + shaping), and may echo A1's "the hh you have been
  playing is a recording of one".
- **A3 (filters):** A2's finale promises "loudness is only half of a note's life; the other half is
  colour over time, and that is its own Sound-track lesson: filters" — A3 opens from that framing.
  DELIVERED IN TWO INSTALMENTS by design: A3 = static colour + per-cycle stepping, A4 = colour
  moving within a note. Both lessons cross-link; do not "fix" A3 by pulling A4's material forward.
- **A5 (signals) — DELIVERED (certified 2026-08-17):** opens from the stepped/inside/smooth
  progression; disambiguates sine-the-voice vs sine-the-signal in one code line. ⚠️ Engine truth
  learned in its review (lesson KDoc + memory `project_signal_sampling_semantics.md`): a control
  signal is read ONCE PER NOTE at onset — held notes freeze the curve; the pump therefore uses
  `note("[a2,e3]*16")` and an ASCENDING range (saw resets to 0 on the count → duck on the drum).
  Sandsturm's own bass "pump" line is inert (off-8th onsets all read 0.5) — never cite it as a
  pump precedent; whether to fix the song is the maintainer's by-ear call.
- **B4 (subdivision):** B1/B2 established counts-vs-steps on the 8-step grid and used "off-beats" for
  the between-count positions. B4 inherits those terms; don't redefine.
- **B6 (Layers) — DELIVERED (certified 2026-08-17):** combines the B2 groove and the B3 melody
  literally; redeems B1's mixing promise by name in §2. ⚠️ Engine truth learned in its review
  (recorded in the lesson's KDoc + docs/tasks/orbit-level-effect-docs.md): reverb processor is
  per-orbit but `room(wet)` is a per-voice SEND; a bare `room(wet)` is SILENT (gate needs `size`); orbit
  bus settings are first-writer-wins. The lesson only demos uncontested configurations and never
  claims contested-channel behavior — keep it that way.
- **A7 (Space and Dirt) — AUTHORED 2026-08-31, review loop NOT yet run:** delivers B6's
  `room(wet, size)` preview under its own intuitions, plus the delay family, `distort`,
  `onepole` and `postgain`. One of the two biggest `teaches` lists in the corpus (5) and the only `Standard`
  scope; if the panel finds it dense the natural split is space (§§1-3) and dirt-plus-level
  (§§4-6). ⚠️ Engine truths (all in the lesson KDoc), two of which killed a drafted section:
  (a) BOTH space effects are sends WITH A GATE and the gate is the SECOND number, not the
  send: reverb is inactive unless `roomSize >= 0.01` (defaults to 0.0) and delay is Off
  unless `time >= 0.01` (defaults to 0.0), so a bare `room(0.4)` and a bare
  `delay(0.4)` are SILENT. That trap became the lesson's spine (§2 proves it by ear), and
  three silent `KlangScript(Playable)` KDoc examples were fixed at source in `lang_effects.kt`.
  (b) `onepole` is an OSC PARAM inside the ignitor, NOT a post-effect, so it sets what the
  distortion is fed; the draft's "the distortion is untouched" was plausible and FALSE.
  (c) `gain` and `postgain` are BOTH applied at the voice output in SendRenderer, so `gain`
  does NOT drive the distortion and on one line the two are the same arithmetic; the draft's
  "dropping gain feeds the distortion less" was also FALSE. The real split is that `velocity`
  and mute/solo scale `gain` only, which is now what §6 teaches (and the misleading
  "applied before synthesis" line in the `postgain` KDoc was corrected at source too).
  Open: review loop not run; the dry-vs-distorted pair is a render-QA level item by nature.
- **A7 (space & dirt):** B6 previews `room(wet, size)` ("how much goes in" / "how big the room is")
  and points to "a Sound-track lesson still to come" — A7 must deliver both under those intuitions.
- **B11 (chords & voicing):** B7 defers harmony ("Which notes agree like this, and which clash …
  a chords lesson still to come takes that up properly") and licenses only the power chord; B11
  must pick that up. B7 also glossed "riff" ("a short figure that repeats") — reuse, don't re-gloss.
- **A8 (body resonator):** A3 spends **"body"** as the standing term for the low half of the
  spectrum ("body below, sparkle above"). A8 teaches `body()`/`bodyWet` — the cabinet resonator —
  and must disambiguate the collision explicitly at first use, the way A6 must for "voice".
- **B5 re-licences "bar" (decided in review):** B1 retired the word; B5 brings it back with a
  split meaning — the **cycle** is the container (window in time), a **bar** is one cycle's worth
  of notes (content). Later lessons must hold that split and never drift back to bar-as-time.
  C8's `arrange([bars, section])` counts bars and depends on it.
- **B9 (transform toolkit) — DELIVERED (certified 2026-08-17):** connects `.fast(n)` to B4's `*n`
  (literally true: mini-notation `*n` compiles to `.fast(n)`) and redeems A5's fast/slow preview by
  name. `clip` licensed as legato's true alias (songs use BOTH spellings). ⚠️ Engine truth from its
  review: superimpose copies of a bare voice are bit-identical (phase 0) and equal-power pans are
  symmetric — pan-only superimpose gives a dead-centre image, NOT width; width needs the copies to
  differ in content (e.g. `.pan(0.3).superimpose(transpose(12).pan(0.7))`). Never teach the songs'
  pan-mirror idiom on a plain voice.
- **B10 (gates) — DELIVERED (certified 2026-08-17):** struct via the what/when split; B7's stab
  riff revealed as the tresillo (steps 1/4/7 = gaps 3-3-2). §1's A/B identical by design —
  sanctioned render-QA audibility exception (KDoc). Licensed terms: "gate", "section" (band sense),
  "tresillo". The planned `chord` preview was dropped — the B7 comma-chord carries the lesson.
- **B8 (Scales) — DELIVERED (certified 2026-08-17):** §2 answers B3's deferred in-between-notes
  question and lands the major/minor mood A/B (0 2 4 on c4 = c4 e4 g4 vs c4 eb4 g4). Continuity
  locked in: the course melody IS `n("0 2 3 ~  4 3 2 ~").scale("a3:minor")` and the B6 bass IS
  `n("0 ~ ~ 0  ~ ~ -3 ~").scale("a2:minor")` — later lessons may lean on both. §1's numbers-vs-
  note-names A/B is identical by design — sanctioned render-QA audibility exception (KDoc). B8
  licenses "ladder/rung" for scale degrees and "semitone" (with the vs-walking-step caveat).
- **A6 (Thickness) — AUTHORED 2026-08-31, review loop NOT yet run:** delivers `supersaw`
  (A1's promise) and disambiguates "voice" by calling the count **unison layers** throughout.
  ⚠️ Engine truths learned while writing it (all recorded in the lesson KDoc): `unison`/`spread`
  are INERT on the plain voices (only the `super` family carries the `voices`/`spread` slots);
  the stack is SUM-NORMALIZED, so layer-count A/Bs are level-matched by construction; defaults
  are `voices` 8, `spread` 0.2, `analog` 0.0, so a bare `sound("supersaw")` is already eight
  layers; `spread` is in SEMITONES and `analog` is peak drift in CENTS (the sprudel KDoc claimed
  0.0-1.0 and was WRONG, corrected at source in `lang_osc_addons.kt` the way A4's `lpe` was, and
  eight stale `.detune(0.3)` examples on `unison`/`uni`/`voices` were repaired at the same time,
  since sprudel's `detune()` no longer exists); the slow drift layer is seeded at CENTRE, so
  drift only develops on HELD notes, which is why the lesson's drift section wears A2's pad
  shape plus B9's `.slow(2)`. Licensed term: "unison layers". Open: the compile gate has not
  run (build lock held elsewhere), the review loop has not run, and §1's `saw` vs `supersaw`
  pair is the first render-QA level measurement item.
- **A6 (unison/thickness):** the word "voice" is taken — B3 introduced and A1 formalized it as the
  term for oscillator timbre ("not a recording ... a sound Klang builds on the spot"). A6 must
  disambiguate explicitly: the `unison`/`voices` parameter counts internal copies — call them
  "unison layers" in prose, never bare "voices". ⚠️ The Lexikon separately uses "voice" in the
  ENGINE sense ("Voices are routed to a Cylinder by their Orbit") — resolve that collision once,
  in one place, when a lesson first touches engine voices. A1 also names `supersaw` among the
  "further voices" promised for later lessons; A6 owns delivering it.

- **A9 (field accessors) NOT WRITTEN, recorded 2026-09-07 so it cannot be forgotten.** The surface
  shipped before its lesson. Every other row here is a promise one lesson made to another; this one
  is a promise the ENGINE made to the curriculum. 88 accessors and 54 alias constants landed with
  specs in a pilot plus four batches (`docs/tasks/sprudel-field-accessors.md`), a built-in song was
  written on them (**Greensleeves**, the wind whistling the tune), and the curriculum says nothing.
  What the lesson owes:
  - **Both roles, one idea.** A name like `gain` is the setter it always was, AND takes a mapper
    that changes the field relative to what the chain already set (`gain(mul(0.5))`), AND, bare,
    reads its own field so another knob can use it (`bpf(freq)`, `bpf(freq.mul(2))`). Same text in
    KlangScript and Kotlin, character for character (door-parity spec exists).
  - **The order rule, which is the one thing a reader will trip over.** An accessor reads what the
    chain has set SO FAR: `note("c e").bpf(freq)` works, `bpf(freq).note("c e")` writes nothing,
    because `freqHz` was still unset when it read. Decision D6 in the plan: documented, not
    engineered around. A "Try it" that swaps the two lines and gets silence teaches it honestly.
  - **It is the sequel to Signals Move the Knobs**, and should be written as one: A5 taught that
    something from OUTSIDE the pattern (`sine`, `perlin`, `.range()`) can move a knob; A9 teaches
    that the pattern's OWN numbers can. Track A for that reason, even though the mechanism is
    pattern-surface. Difficulty is probably Advanced; A5 is the prerequisite, not the carrier kit.
  - **Prerequisites nothing teaches yet.** `bpf`/`bpq` appear in NO lesson (A3 teaches `lpf`, `hpf`,
    `lpq` only), and `mul`/`add` as standalone mappers appear in NO lesson. Both are needed for the
    headline example, so A9 either introduces them as declared previews (principle 3) or picks a
    demo built from taught filters. The bandpass is the better teacher here (a filter that keeps a
    band is what makes a pitch out of noise), so introducing it is the likelier call.
  - **The finale is Greensleeves REDUCED, not Greensleeves.** The song also uses `chord()` +
    `voicing()` (B11's), `filterWhen` (C8's), `perlin.seg()` and `late()` (nothing teaches either),
    so it cannot be pasted in whole without breaking principle 5. Take the whistle line
    (`s("pink").bpf(freq.mul(...)).bpq(...)`) over a melody the reader already has, and link the
    song for the full thing.
  - **"Every knob" is FALSE in two ways, so never say it.** String and boolean setters (`note`, `n`,
    `sound`, `bank`, `scale`, `vowel`, `body`, `unit`, `loop`, the `*shape`/`*curve` family) are
    won't-implement (maintainer, 2026-09-07), and the fields that only have a compound door (the
    `lpadsr`/`hpadsr`/`bpadsr` envelope stages, the compressor's other slots) have no accessor yet
    (`docs/tasks/sprudel-accessors-compound-slots.md`). "Every NUMBER knob, with a door of its own"
    is the true sentence.
  - **A mapper handed to a setter that lacks the branch is still silently dropped, with no
    diagnostic** (OPEN in the accessor plan). If that is still true when A9 is written, the lesson
    must not encourage exploratory guessing at which knobs read.
  - ⚠️ **Engine gaps found during the sweep, documented as "reserved" in the object KDocs, that must
    never appear in a lesson example:** `panSpread` has no engine stage at all; `density` is the
    dust grain rate, not a unison knob; `duckattack` is the recovery time (the duck-down is
    instant); `pcurve` is not read by `PitchEnvelopeRenderer`; `loopBegin`/`loopEnd` are not read by
    `VoiceFactory`; negative `speed` is silence, not reverse.
  - **Title is provisional.** "The note moves the knobs" pairs with A5 and matches the headline
    example, but §2 generalizes past the note (`pan(gain)` is just as legal). Settle it when the
    lesson is written; it goes in `Tut` (`TutorialModel.kt`) either way.
  - **Also owed: a Lexikon entry** for the concept (Pattern domain, `Mapping` category fits), per
    principle 7, since the per-function docs already generate themselves from the KDoc.

### Extras shelf (not on the path)

One-off technique pages, written fresh later as a browsable "fun corner": `morse()`, `swingBy`, `shuffle`,
`degrade`/randomness, `.euclid()` footnote, time-of-day seeding (DerSchmetterling / SoundOfTheSea),
vowel/notch/crush, a sound-design playground deep-dive.

## Data-model / infra changes (minimal)

- Registry order becomes the learning path (drives Prev/Next); tracks as sections in the list page.
- Optional `previews: List<String>` per section or lesson to make rule 3 lintable.
- A small lint (test) that walks the registry order and flags identifiers used before taught and undeclared.
- Fix `tut_EveryTrick.kt` prose (`slow`, not reverse) immediately, independent of the rework.

## Tracks (BUILT 2026-08-17 — data model, per-track lint, and UI)

Two levels only: named, ordered **tracks** of lessons; a lesson can be in multiple tracks. The
"course" layer proved one level too much — because membership is many-to-many, the braided main
path is itself simply a track, sitting beside thematic ones like "Sound Design Basics". Nothing a
course could express is lost.

**Data model:**

- `TutorialTrackDef(slug, title, description, lessons: List<Tutorial>, buildsOn: List<TutorialTrackDef>)`
- `allTracks` is the registry; the flat `allTutorials` is derived (distinct lessons, main-track
  order first) so the existing list page and lint survive the migration.
- `buildsOn` makes prerequisites honest: a thematic track like "Sound Design Basics" assumes the
  carrier kit (note/sound/gain) taught in the onramp — it declares that instead of hiding it. The
  per-track vocabulary lint then checks: every lesson's vocabulary ∈ (teaches of buildsOn tracks)
  ∪ (taught earlier in this track) ∪ (declared previews). The main track builds on nothing and
  stays the strictest check (today's global lint, unchanged).
- The `TutorialTrack` enum (Sound/Pattern/Motor) dissolves — membership defines flavour. The
  `GettingStarted` tag rule likewise retires; the tag was a proxy for track membership.

**UI/state model (user-specified):**

1. The tutorial page knows: which tutorial + which track the reader is on — track is **nullable**.
   Track context travels as a route/query param (`?track=<slug>`), keeping one canonical URL per
   tutorial.
2. Prev/Next derive from the track. With track = null (arrived via All Tutorials or a direct
   link): no Prev/Next at all — instead show "part of:" chips for every track containing this
   lesson; clicking a chip adopts that track context. The chips are the discovery mechanism, and
   no-context navigation stays impossible (the old corpus's scrambled Prev/Next must never return).
3. Alongside the existing "All tutorials" button, a "Track" button appears when track ≠ null —
   it opens the track overview: the track's lessons in order, current highlighted, completion
   checkmarks from TutorialStorage (per-track progress n/m comes free).

**Initial track assignment (draft, from current + planned lessons):**

- *The Klang Path* (main, braided — IS today's registry order): B1 B2 B3 A1 A2 B4 B5 A3 A4 → the
  full ladder as it grows.
- *Sound Design Basics*: A1 A2 A3 A4 (later + A5–A8); buildsOn: The Klang Path onramp.
- *The Pattern Language*: B1–B3 B4 B5 (later + B6–B11).
- *The Klangmotor*: C1–C10 when written; buildsOn: The Klang Path.

## Section blocks + visuals (user design 2026-08-17 — BUILT; markdown since 2026-08-18)

**Markdown migration (user request 2026-08-18, DONE):** prose is now `Block.Markdown` rendered
via MarkdownDisplay (marked.js); `Block.Text` is retired from the lessons (type kept for
compatibility). Authoring contract for ALL new/edited prose:

- Triple-quoted `""" … """.trimIndent()` with REAL blank lines between paragraphs (no `\n\n`).
- Backtick every code-ish token — function names, mini-notation (`~`, `*2`, `[hh hh]`, `<>`,
  `|`, `x`, `//`), note/drum names, colon-strings, scale names. ⚠️ Unprotected `*` `<>` `[]`
  `|` `_` get EATEN by marked — the migration verified zero bare occurrences; keep it that way.
- Bold each lesson's new concept ONCE, at its coining sentence; never later mentions.
- Callout labels are bold (`**Try it:**` / `**Listen for:**`) and still open their own
  paragraph — the spec's callout lint strips `**` before anchoring.
- `${'$'}{Tut.x}` interpolations stay plain.
- B1 §1 carries the player-UI bullet tour (order matches the chrome) + the hover-help tip;
  B1 §4 lists drum names as bullets. Bullet lists are for enumerations prose would bury.
- **Player-button names are bold**: `press **Update**` (lint-enforced), never `press Update`. B1's
  player tour introduces **Play**, **Stop**, **Reset** and **Update** in bold, and every later
  instruction to press one has to look like the thing the reader is hunting for in the chrome.
- **NO EM-DASHES (user ruling 2026-08-18): never "—" or "–" in any user-facing text** (prose,
  descriptions, code comments). They read as an AI tell; rewrite with commas, colons,
  semicolons, parentheses, or a new sentence. The A/B comment suffix is ", swap" /
  ", swap to compare". List bullets use "`bd`: bass drum" colon form. Plain hyphens stay.

## Original design notes (2026-08-17)

A section is now a heading + ordered list of **blocks** (user-specified layout):

```kotlin
TutorialSection(
    heading = "...",
    blocks = listOf(
        Block.Text("..."),                       // \n\n paragraphs, callout rules apply
        Block.Visual.Adsr("0.3:0.1:1:0.05"),     // schematic SVG, drawn from the SAME
                                                 // string the code uses (drift-linted)
        Block.Code(lang = "KlangScript", code = "..."),
    ),
)
```

Visual design rules: schematic only (mental model, never engine internals — flux ruling);
parameterized from the neighbouring code's values verbatim (lint: value must appear in the
section's code); theme colors, no image assets; a visual earns its place only for a SHAPE the
prose describes for the ear (envelope, waveform, step grid, filter curve). Three stages:
(1) static parameterized SVG — NOW, Adsr pilot; Waveform/PatternGrid/FilterCurve variants later;
(2) playback-synced playhead using PlayableCodeExample's existing per-event callbacks — renderer
upgrade only, no model change; (3) interactive drag-the-handles — much later, playground tier.

**Implementation status:**

- [x] `Block` sealed interface (Text / Code(lang, code) / Visual.Adsr(value, label)) in
      TutorialModel.kt; `TutorialSection(heading, blocks)`.
- [x] All 9 lesson files migrated (51 sections) to the block layout.
- [x] TutorialCurriculumSpec rewritten for blocks; new check: visuals parse + value-in-code
      drift guard (12 tests).
- [x] TutorialPage.kt render loop iterates blocks: Text → \n\n paragraphs; Code →
      PlayableCodeExample (KlangScript; other langs render as plain pre); Visual.Adsr → AdsrVisual.
- [x] AdsrVisual jsMain component: parses "a:d:s:r", schematic SVG via the SvgDsl helpers
      (straight segments, proportional widths with 3-unit minimums, hold = fixed 30 % share since
      it has no duration parameter, accent fill + stroke, A/D/S/R letters, rotated label).
- [x] Retrofit visuals: A2 §§2–4 (attack/sustain/release live-line values), A4 §§2–3
      (label = "cutoff"). Values verbatim from live lines so the drift lint passes.
- [x] Spec run via build lock (12/12 green incl. the drift guard); committed as one commit.

Stage-1 pilot DONE 2026-08-17. Next visual variants (Waveform / PatternGrid / FilterCurve) get
added the same way when a lesson needs them; stages 2 (playhead) and 3 (interactive) stay future.

## Render QA gate

The offline render pipeline (KlangOfflineRenderer / record.sh, see `/klang-music-recording`) turns "does it
sound ok" into something partly checkable. Two tiers:

- **Every code block, hard checks:** compiles/runs, produces non-silent audio, no clipping, no NaN/blowup.
  Catches broken examples automatically — this alone protects learner trust.
- **Finales, advisory spectral report:** band-energy balance (mud buildup 200–500 Hz, harshness 2–5 kHz,
  missing sub/air), crest factor, approximate loudness, stereo correlation. The report flags candidates for
  shaping; it never auto-"fixes". Analysis proposes, the ear disposes — final polish stays the by-ear pass.
- **Level parity inside listen-for pairs:** render both halves of each A/B moment and check they sit within
  ~1 dB of each other. Louder reads as "better" to every human ear; unmatched levels would quietly sabotage
  exactly the ear training we're building.
- Consistent target loudness across all finales, so browsing the ladder doesn't whiplash.
- **Authoring loudness targets (set by ear 2026-08-15, after First Notes pierced at the default
  gain 1.0):** every code block sets an explicit gain — never rely on the default. Bare synth voices
  (sine etc.) sit at ≈0.5, never above 0.6; drum samples at ≈0.8. Perceived loudness must be level
  across all tutorials — the render-QA loudness check enforces this parity once it runs.
- **Audibility check on A/B pairs:** the two renders of a listen-for pair must actually differ (spectral
  distance above a threshold). If the text claims "listen for the buzz" and the renders are near-identical,
  the lesson is teaching an inaudible difference — hard flag.
- **Sanctioned parity exceptions:** an A/B whose level difference is inherent to the concept (B3
  "Steps and leaps": a leap must change register) or explicitly narrated in the lesson (B2 finale's
  "judge the groove, not the level") is exempt from the ±1 dB gate — the harness needs an allowlist so
  nobody "fixes" these by mangling the music. Measurement items for the gate: B2 "Take the skeleton
  away" lifts hats 0.8→1.0 (~+2 dB) against a sample-level gap that may be 10 dB+ — verify the
  compensation actually lands, adjust by ear. A1's per-voice trims (square 0.35, triangle 0.55 vs
  0.5, and white 0.3 — the last a PERCEPTUAL trim, −6.2 dB RMS vs sine, deliberate) are narrated
  in the lesson ("judge the colour, not the level") — verify by ear/render and tune — measure the
  square-vs-saw pair (A1 §3) first. B4's finale pair is IDENTICAL BY DESIGN (two spellings, one
  beat — the audibility gate must allowlist it, not flag it), and B4's §1/§2/§4 pairs differ in hit
  count because density is the concept (§3 differs in placement only and passes parity unaided). B5's bar-4 `e2` (~82 Hz) is a by-ear item: confirm it
  reads on laptop speakers.

Shape: a JVM-side harness in the style of `runSongBenchmark` that walks the registry, renders each block,
hard-fails on tier-1 violations, and emits the tier-2 report for the polish pass.

## Process

- **Start from scratch (decided 2026-08-15).** All 38 existing tutorials get deleted — no triage, no
  migration. Old files remain in git history as reference. The wipe (files + registry) is the first
  implementation commit of the rework, kept separate from unrelated working-tree changes.
- `tutorial-factory` autonomous mode is retired. New lessons fill a **named slot** from this ladder;
  per the established workflow: Claude frames, user polishes the musical examples.
- Vocabulary lint + render QA run as tests over the registry, so regressions surface on every build.

### Review regime (same standard as code — `/review-loop`)

Every lesson passes the review loop before it enters the registry: rounds loop until a clean round, every
fix is re-reviewed by fresh reviewers. Machine gates run first each round (vocabulary lint, render QA incl.
the A/B audibility check) so human-grade reviewers never burn a round on what a test can catch.

Dedicated reviewer lenses, each targeting a failure mode the old corpus actually exhibited:

| Reviewer | Mandate | Old-corpus failure it guards against |
|---|---|---|
| **Code** | Examples are idiomatic sprudel; every prose claim about code behavior — line comments included — is verified against actual semantics | the `every(3, slow(2))` = "reverses" hallucination |
| **Didactics / comprehensiveness** | Main topic taught completely; one new thing per step; running-example continuity; finale consolidates only; listen-for moments present and well-aimed | finale complexity spikes, concepts dumped at once, no ear direction |
| **Text** | Prose quality and concision; adult tone; no filler, no template smell, no stock metaphors | "Introduction"/"Putting It All Together" mold, sculptor voice |
| **Consistency** | Cross-ladder: one word per concept, terminology and formatting match neighboring lessons, previews labeled, difficulty ramp smooth against adjacent slots | difficulty-scrambled ordering, unexplained operators borrowed from other tutorials |

**Who writes, who reviews (decided 2026-08-15):** lessons are authored by **Fable 5 in the main session**,
in stage-sized batches with the neighboring lessons in context — user-facing content gets the top model, and
a single author-context keeps voice and terminology coherent (no fleet of isolated authors). Reviewers run
deliberately on *different* models than the author to avoid shared blind spots: code reviewer **Opus**,
didactics reviewer **Opus** with a **Fable** escalation pass per completed stage, text + consistency
reviewers **Sonnet** (these loop the most rounds; consistency needs the neighboring lessons in its context).
Surrounding chores (registry wiring, lint/render harness) are normal Opus coding work. The user's by-ear
polish pass sits after the loop; polished examples re-run the machine gates before merge.

## Open decisions

- **Graded ear training** (Syntorial-style "reproduce this hidden sound in code"): killer feature, separate
  build — not a prerequisite for the rework.
- Lesson naming/tone pass: titles above are placeholders, not final voice.

## Appendix: craft rules carried over from the earlier tutorial sessions (added 2026-09-06)

Restored from the maintainer's session memory during the memory housekeeping; the tutorial
workstream owns them from here. Hardness: guideline, except where the engine dictates the fact.

- **Per-orbit effects.** Layers with different delay/reverb need their own `orbit()`; reverb and
  delay are per-orbit, two patterns on one orbit share the bus. (engine fact)
- **`chord("...").voicing()`.** `chord()` alone plays nothing; `n().scale().chord("minor")` is wrong.
  (engine fact)
- **`pan()` is 0.0 (left) to 1.0 (right), centre 0.5.** Not -1 to 1. (engine fact)
- **Always `.lpf()` saw / supersaw / square** unless the text explicitly demonstrates the raw
  sound; harsh first impressions get blamed on the platform.
- **Musicality over cleverness.** A simple pattern that sounds good beats a complex one that sounds
  bad; proven progressions and pentatonic lines.
- **No inflated titles** ("masterclass", "ultimate", "definitive", "comprehensive"). Describe what
  the learner does or learns.
- **Break long chains** after logical groups, 2-space continuation indent, about 80 chars per line.
- **Mark the teaching moment** with a comment on the line before the new function so the eye lands
  on it. Tone: playful sculptor (chisel, carve, shape, polish, grain), never cooking metaphors.
- **Tags stay tight.** `TutorialTag` has 12 entries; do not add one without a strong reason.
- **Series over dense tutorials.** Complex topics (mini-notation, chords, effect chains,
  arrangement) span several tutorials across difficulty levels; fill missing levels of an
  existing series before starting a new one.
- **Workflow.** Claude drafts the frame and intermediate examples; the maintainer composes the
  final "Putting It All Together" jingle; Claude then rewrites the intermediate steps backwards
  from that jingle.
