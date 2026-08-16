# Tutorial Curriculum — Rework Plan

Status: DRAFT 2026-08-15 — three-track structure confirmed; lesson ladder below is the proposal to react to.

## Why (diagnosis, short version)

The 38 existing tutorials were generated unattended by the `tutorial-factory` loop. Measurable symptoms:
fixed template skeleton (32/38 end with "Putting It All Together"), mandated sculptor metaphor (17 files),
zero ear training ("listen for" appears 0 times), difficulty-scrambled Prev/Next navigation (registry is in
generation order), finale sections that use functions never taught in that tutorial, and zero coverage of
Ignitor / Master / Pipeline (they weren't on the generator's function allow-list). One factual error:
`tut_EveryTrick.kt` describes `.every(3, slow(2))` as "reverses" — fix regardless.

Ground truth from the 14 built-in songs (full tally in session analysis, key facts):

- Used by **all 14** songs: `stack`, `note`/`n`, `sound`/`s`, `gain`, `adsr`. Second tier (10+):
  `orbit`, `hpf`/`lpf`, `pan`, `superimpose`, `fast`/`slow`, `room`, `postgain`, `distort`, `warmth`, `analog`.
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
11. **Mostly every code line carries a comment saying what that line does** — and, where relevant, what to
   listen for on that line (`// saw.fast(4): a pump 4× per cycle — the sidechain feel`). Comments are
   narration, never decoration (no mood/metaphor comments — that rule produced the sculptor slop). Comment
   vocabulary follows the same taught-so-far rule as code. Familiar carrier boilerplate may go bare once
   it has been commented in earlier lessons.

Model resources: Ableton Learning Synths (topic order, one param at a time), Syntorial (match-by-ear),
Strudel workshop (short path + separate reference, inline "try it" nudges), SoS Synth Secrets
(sequencing + instrument-recreation capstones — copy the order, not the delivery).

## The three tracks, braided

Track A = Sound (synthesis ear). Track B = Pattern (sprudel hand). Track C = Motör (Klang engine).
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
| B6 | Layers — stack & orbit | `stack`, `orbit` | `room` on one orbit | Beat + bass + melody combined; reverb on melody's orbit only. *Listen for: dry drums under a wet lead.* |
| B7 | Chords in one step | comma-chords `[0,7,12]`, random pick `\|` | — | Power-chord stabs; a step that gambles. *Listen for: which variant played this cycle.* |

### Stage 3 — Where the tracks meet

| # | Lesson | Main | Touches | Running example / Listen for |
|---|--------|------|---------|------------------------------|
| A5 | Signals move the knobs | `sine`/`saw`/`perlin` + `.range()` into `gain`/`pan`/`lpf` | `.slow`/`.fast` on signals | Tremolo → autopan → filter sweep → sidechain pump (`saw.fast(4).range(0.65, 0.42)` — Sandsturm). *Listen for: the pump breathing with the kick.* **The merge lesson: patterns and sound design become one idea.** |
| B8 | Scales & melodies | `n()`, `scale()`, `transpose` | — | Numbers instead of note names; same line, swap the scale. *Listen for: major vs. minor mood flip.* |
| B9 | The transform toolkit | `fast`/`slow`, `superimpose`, `legato`, `clip` | — | One melody, four transformations, by song-frequency order. *Listen for: superimpose's thickening vs. an octave doubling.* |
| B10 | Gates — struct | `.struct("x ~ ~ x ...")` | `chord` preview | The tresillo gate from Sandsturm. *Listen for: 3-3-2.* |
| A6 | Thickness — unison, spread, analog | `unison`, `spread`, `analog` | — | Supersaw anatomy: 1 voice → 9 voices → spread out → drift. *Listen for: mono vs. wide on headphones.* |
| A7 | Space & dirt | `room`/`rsize`, `delay` family, `distort`, `warmth`, `postgain` | — | Effect ORDER matters (distort→filter vs. filter→distort). *Listen for: same settings, swapped order.* |
| A8 | Body | `body()`, `bodyMix` | — | Same pluck through mahogany / glass / membrane. *Listen for: the cabinet in front of the speaker.* (8/14 songs use it; zero tutorials.) |
| B11 | Chords & voicing | `chord()` + `voicing()`, why Am–F–C–G works | `struct` | Progression built from song examples, one paragraph of real harmony. (The old `tut_ChordsAndHarmony` staging was sound — reuse the staging, not the file.) |

### Stage 4 — Track C: the Motör

| # | Lesson | Main | Touches | Running example / Listen for |
|---|--------|------|---------|------------------------------|
| C1 | Caricature drums (recipes) | kick = sine + `pitchEnvelope`, hat = noise + `hpf`, snare | everything so far | Build a drum kit from raw waves, pattern-level. *Listen for: the pitch drop that makes a kick a kick.* Caricature model: 2–4 acoustic tells, tune by ear. |
| C2 | Your first Ignitor | `Osc.*` chains: osc → filter → adsr | — | Sandsturm's lead, explained line by line; rebuild C1's kick as an Ignitor. *Listen for: detuned square joining the saw.* |
| C3 | Layered ignitors | additive `.plus()` stacks | — | IrishLament's flute/fingerpick/contrabass: many layers, flat wiring. *Listen for: the noise crackle that makes the pluck "wood".* |
| C4 | Knobs & variants | `Osc.param`, `.oscp()`, `Osc.variants` | — | DialogueWithTheStars' three guitars, round-robin. *Listen for: open vs. muted variant.* |
| C5 | Living instruments | signal-arithmetic cutoffs, pitch-tracking filters, `Osc.slot.analog`, perlin vibrato | — | Sakura's shakuhachi & pad, dissected. *Listen for: the filter following the note's pitch.* |
| C6 | The Master bus | `master(Master.of(...))`, `MasterFx` gain + limiter | `compressor` | Build a quiet mix, lift and limit at the end (ATruthWorthLyingFor / StrangerThings chains). *Listen for: limiter grabbing the peaks.* |
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
  noise comes in the Motör track" and frames drum-machine hats as "a short burst of shaped noise" —
  C1 must deliver exactly that recipe (noise + shaping), and may echo A1's "the hh you have been
  playing is a recording of one".
- **A3 (filters):** A2's finale promises "loudness is only half of a note's life; the other half is
  colour over time, and that is its own Sound-track lesson: filters" — A3 must open from that framing.
- **B4 (subdivision):** B1/B2 established counts-vs-steps on the 8-step grid and used "off-beats" for
  the between-count positions. B4 inherits those terms; don't redefine.
- **B6 (Layers):** B1 promises "balancing them with gain() is most of what mixing is" once several lines
  run; B3's finale says its melody was "also written to sit on top of the groove you shaped in
  Space and Rests". B6
  should literally combine the B2 groove and the B3 melody as its running example.
- **B5 re-licences "bar" (decided in review):** B1 retired the word; B5 brings it back with a
  split meaning — the **cycle** is the container (window in time), a **bar** is one cycle's worth
  of notes (content). Later lessons must hold that split and never drift back to bar-as-time.
  C8's `arrange([bars, section])` counts bars and depends on it.
- **B9 (transform toolkit):** must connect `.fast(n)` back to B4's `*n` — the same operation at
  pattern level vs. inside one step; introducing it as unrelated would confuse learners who own `*`.
- **B8 (Scales):** B3 introduces sharps/flats (`eb3` played in "The notes between", `cs3` via its
  directed try-it) and defers
  "which of these in-between notes belong together with which letters" to the scales lesson; B8 also
  owes the major/minor mood A/B
  (`c4 e4 g4` vs `c4 eb4 g4`) descoped from B3.
- **A6 (unison/thickness):** the word "voice" is taken — B3 introduced and A1 formalized it as the
  term for oscillator timbre ("not a recording ... a sound Klang builds on the spot"). A6 must
  disambiguate explicitly: the `unison`/`voices` parameter counts internal copies — call them
  "unison layers" in prose, never bare "voices". ⚠️ The Lexikon separately uses "voice" in the
  ENGINE sense ("Voices are routed to a Cylinder by their Orbit") — resolve that collision once,
  in one place, when a lesson first touches engine voices. A1 also names `supersaw` among the
  "further voices" promised for later lessons; A6 owns delivering it.

### Extras shelf (not on the path)

One-off technique pages, written fresh later as a browsable "fun corner": `morse()`, `swingBy`, `shuffle`,
`degrade`/randomness, `.euclid()` footnote, time-of-day seeding (DerSchmetterling / SoundOfTheSea),
vowel/notch/crush, a sound-design playground deep-dive.

## Data-model / infra changes (minimal)

- Registry order becomes the learning path (drives Prev/Next); tracks as sections in the list page.
- Optional `previews: List<String>` per section or lesson to make rule 3 lintable.
- A small lint (test) that walks the registry order and flags identifiers used before taught and undeclared.
- Fix `tut_EveryTrick.kt` prose (`slow`, not reverse) immediately, independent of the rework.

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
