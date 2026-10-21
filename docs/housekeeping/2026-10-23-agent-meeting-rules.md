# Klang's rules for the agent meeting, 2026-10-23

The first weekly agent meeting (Mission Control's MC-00017, the maintainer's idea of 2026-10-21): each project's
agent is interviewed by the others about its rules and why they are there, and then decides what to merge, slim or
drop. Klang goes first, on Friday 23 October after 13:00. Prepared on 2026-10-21 by the coordinator (Klang-Main-Dev).

Each rule gets one line: what it says, why it exists, and my leaning going in. The leaning is not a decision. The
meeting decides, and a change to `CLAUDE.md` needs the maintainer's OK in klang's session.

**Leanings:**
- **keep**: stays repo-wide.
- **area**: stays, but moves out of the repo-wide index into the area it governs (engine, DSL, a module), with one
  pointer row left in the index.
- **merge**: one home with another rule.
- **slim**: keep the point, drop words.
- **ask**: the maintainer's call.
- **drop**: retire it.

A reason marked *(my reading)* is my understanding, not a recorded one.

## 1. The register in `CLAUDE.md` (47 rows)

### Stone (9)

| # | Rule | Why | Leaning |
|---|---|---|---|
| S1 | Complexity is the enemy: stop and consult before build-time magic | Clever indirection costs every later reader; the plain module boundary stays understandable | keep |
| S2 | Memory lives in the repo, never the home directory | Home memories were invisible to other sessions and the repo; 89 of them were retired 2026-09-06 | keep, merge the "MEMORY.md files stay short" guideline into it |
| S3 | The engine is the horse: no DSP in a frontend | One engine, one sound; a second DSP path drifts | keep, merge with S4 as "engine boundaries" |
| S4 | The backend never learns about cycles; only seconds cross the wire | Every source (sprudel, MIDI, a sequencer) feeds the same engine | merge with S3 |
| S5 | Every DSL value is immutable at construction time | A base sound can be reused in variants without surprises (`/dsl-design` §1) | area: DSL |
| S6 | No boxed types anywhere (`Long`, `Char`, `Byte`, `Short`) | On Kotlin/JS `Long` allocates on every operation, `Char` is boxed, `Byte`/`Short` range-check | ask: narrow to engine, wire and DSL code? It matters much less in UI and tools |
| S7 | The Motor stays raw; coerce user input, never `require()` it | Sharp edges are a creative choice, the house limiter is the safety net; a typo must not crash the engine | area: engine and DSL |
| S8 | Licensing: AGPL v3, `tones/` stays MIT | Legal; `tones/` is a port of tonal.js | keep |
| S9 | Naming: "Klangmotor" with a plain o | The umlaut was "too much ego" (2026-08-25) | keep |

### Rule (23)

| # | Rule | Why | Leaning |
|---|---|---|---|
| R1 | *(provisional)* Capture now, prune later | Lessons are lost if written up later; pruning is its own pass | keep (this meeting is a pruning pass) |
| R2 | Reviews loop until clean; new tests are mutation-checked | Toothless guards shipped before (`vowel(floor)` a silent no-op) | keep, add MC-00016's light review for small changes |
| R3 | Gradle is a single-writer resource, through the lock | Two builds corrupt the sprudel KSP cache; a build racing an edit gives a silently wrong verdict | keep |
| R4 | Published writing follows the public voice | The project's public face; no hype | keep |
| R5 | Two doors, one DSL (Kotlin and KlangScript, same deliverable, door-parity spec) | Builtin sounds, tests and tools build DSL trees from Kotlin; a script-only surface splits the vocabulary | area: DSL |
| R6 | Parameter parity: same name, meaning and scale everywhere | The reverb size meant ~1 s on an orbit and ~12.5 s on the master; a shipped song had the bug | area: DSL |
| R7 | One word per concept; a replaced surface is removed | Two names or two doors for one thing confuse users and agents | area: DSL |
| R8 | *(provisional)* Units: seconds and semitones, unit in the KDoc | One scale everywhere, names stay short | area: DSL |
| R9 | Door shape: musical inputs on the door, knobs behind `configure` | One shape per concept across the DSLs | area: DSL |
| R10 | Wire types over enums | Variants carry their own params; no ordinal hazard (the schema hash does not cover enum entries) | area: DSL |
| R11 | KlangScript stdlib follows Kotlin conventions | One language feel across both doors *(my reading)* | area: klangscript |
| R12 | Code style (braces, blank lines, flat dirs, no FQCN, ...) | Readability; a few items are engine safety (NaN guard, no allocation, flush IIR) | slim: split the engine items into the engine area (see section 2) |
| R13 | No em-dashes in user-facing text | The dash reads as an AI tell (maintainer, 2026-08) | keep |
| R14 | AST walkers live in `klangscript`, never in UI modules | One home for analysis, reusable by every UI | area: klangscript |
| R15 | Klangbuch exported parts carry no timing and no scale | Both belong to the song *(my reading: a part is reused across songs)* | area: sprudel |
| R16 | Every task is a ticket in its status folder; the `ticket` tool moves them | One place to find work; the dashboard reads the tickets | keep |
| R17 | Commit completed, reviewed steps | Uncommitted work in a shared tree gets lost or swept into others' commits | keep |
| R18 | `Co-Authored-By:` trailer on every agent commit | Provenance for funding applications | keep |
| R19 | Compound doors fill per param at the door | Every surface gives a stage the same companions from the one constant | area: DSL |
| R20 | *(provisional)* Credits in `CREDITS.MD` and the Credits page, in step | Klang builds on others' work; commercial products are not credited (2026-10-10) | keep |
| R21 | Nothing allocates without a way to clean it up | Per-edit growth in a live-coding session is a leak *(my reading)*; debt tracked in KLANG-00210 | keep |
| R22 | Script-door defaults are plain literals (the KSP build refuses others) | The generated door bakes the default *(my reading)* | area: DSL (the build enforces it, so the row could go) |
| R23 | *(provisional)* Two kinds of check: future guard or scaffolding; songs move freely | A check that outlives its purpose adds weight; songs must stay free to change | keep |

### Guideline (10)

| # | Guideline | Why | Leaning |
|---|---|---|---|
| G1 | Sound first: engine, then tutorials, then launch, then performance | The order of the work streams | keep |
| G2 | Taste is also what you do not do | The resonator-swing plan was designed and archived unbuilt; the complaint was a mixing problem | area: DSL |
| G3 | Caricature sound model: 2 to 4 tells, a fixed learnable target | Sounds tuned by ear, recognisable *(my reading)* | area: music writing |
| G4 | Design for adults that kids also enjoy | The audience | keep |
| G5 | "What we built": credit the collaboration | How the team talks about its work | keep |
| G6 | Tutorial craft | Lessons from the tutorial series | area: the tutorial ticket (it already lives there) |
| G7 | Klang UI conventions (`.with()`, `.render()`, RoundGauge) | Kraft habits that are easy to get wrong | area: `/kraft-knowhow` (it already lives there) |
| G8 | Review rounds 3+ on `fable` at xhigh, never max | Cost against scrutiny; max was capped 2026-09-28 | area: `/review-loop` |
| G9 | Whitespace findings are not worth a round | codefactor.io fixes formatting | area: `/review-loop` |
| G10 | Module `MEMORY.md` files say what is true now, short | `audio/MEMORY.md` reached 3,000 lines | merge into S2 |

### Guardrail (5)

| # | Guardrail | Why | Leaning |
|---|---|---|---|
| D1 | Block size pinned to 128 frames | It is a tone parameter; changing it changes the sound | area: engine |
| D2 | Deliberate engine exceptions a reviewer must not "fix" | Reviewers kept filing them as findings | area: engine (`audio-constraints.md` already holds the list) |
| D3 | Structural cycle selection uses exact integer cycles | The N-does-not-divide-T bug class (cycles dropped at boundaries, *my reading*) | area: sprudel |
| D4 | Builtin songs are KlangScript in Kotlin strings: `/` divides, `$` interpolates | An easy slip when writing a song | area: music writing |
| D5 | `min`/`max` are clamps on every door; the Ignitor nodes cross | `a.max(b)` reads "a, at most b" in a song; do not "correct" the crossing | area: DSL |

**If the leanings hold:** about 20 repo-wide rows (S1 to S4 merged to three, S8, S9, R1 to R4, R12 to R13, R16 to
R18, R20, R21, R23, G1, G4, G5, plus the new budget rule), and three pointer rows: "Engine: `/klangaudio-knowhow`
and `audio-constraints.md`", "DSL surfaces: `/dsl-design`", "Module rules: each module's `MEMORY.md`".

## 2. Rules in the skills, not in the register

Most are guardrails born from one incident each. The incident is the reason.

### Shared checkout and safety (`/agent-fleet`, `/project-layout`)

| Rule | Why | Leaning |
|---|---|---|
| Stage and commit by explicit path | `git add -A docs/tasks` swept another session's files into a commit (2026-10-02) | keep; merge the five git rules into one "shared checkout" block |
| Never `git restore --staged .`, never `git reset HEAD~1` | An intent-to-add mark was lost; a reset took back another session's commit | merge (as above) |
| Never `git stash` in the shared tree | It takes back everyone's uncommitted work while stashed (2026-10-04) | merge (as above) |
| Never switch branches in the shared checkout | A worker edited files that had lost a ticket's code (2026-10-21) | merge (as above) |
| Deletes name literal paths; cleanup only in your own folder | A worker emptied the session scratchpad (2026-10-10) | keep |
| Count tests with `test-digest`, never `rm -rf` the results | Workers deleted test results against their brief (2026-10-10) | slim: one line in the brief template |
| A refused action stays refused, in your scripts too, in no rephrased form | A refused `rm -rf` ran anyway from an older script (2026-10-09) | keep |
| Worktrees live in `klang-worktrees/`, never inside the repo | A copy inside doubles the IDE index | keep |
| IntelliJ tools read-only *(provisional)* | A build there bypasses the lock; an edit lands in the maintainer's editor | keep |

### Fleet (`/agent-fleet`)

| Rule | Why | Leaning |
|---|---|---|
| The coordinator owns the build; workers don't run Gradle | Workers blocking on the lock look like stalls | keep |
| One owner mutates production code | Two mutators read each other's edits | keep |
| Reviewers may build and run their own mutants, under the lock | A doubt settled by a run beats a guess (2026-10-04) | keep |
| Workers do not fan out | Orphaned children lose their results | keep |
| Cap heavy agents at 2 to 3 at once | Stalls in one run, but the network was flaky too: "causation is not proven" | ask: test it once and keep or widen |
| Every brief opens with the bar (world-class) | A theory under observation since 2026-09-18 | ask: read the defect-density ledger at the meeting; the skill says it goes if nothing moved |
| Give workers the constraints, not just the task | Reviewers without the list "fix" deliberate engine choices | keep |
| A rename brief keeps quotations verbatim | A sweep rewrote a maintainer's quote (2026-10-04) | keep |

### Review loop (`/review-loop`)

| Rule | Why | Leaning |
|---|---|---|
| Round 1 blind; later rounds two-phase with a fresh agent | Fresh eyes without re-arguing settled findings | keep |
| Only CRITICAL/MAJOR loop; MINORs go as one batch | Fix churn fed the loop | keep |
| Safety valve after 2 unclean rounds | It was 5, which let fix churn feed itself | keep |
| The effort ladder (opus, opus high, fable xhigh) | More effort on a smaller target | keep; MC-00016 adds the light lane |
| Comment findings only when the text is factually wrong | Prose churn is the loop's known failure mode | keep |
| Mutation tiers: MANDATORY (engine, wire, sprudel timing, KSP) and LIGHT | Mutation checks pay where the oracle is indirect | keep; it is the natural place for MC-00016's strict tier |
| No value-echo tests; no test for what a lower layer guarantees; a coverage finding names a failure reading cannot catch | Change-detectors guard nothing | keep |
| Every escape closes one hole (`escape-ledger.md`) | Deterministic improvement at n = 1 | keep |
| Verify a claim about code or a record before writing it | Recurred three times, then widened to facts (2026-09-28) | keep (repo-wide in effect) |
| Audibility claims measured on the soloed voice; a frequency response is a sweep; an approved figure is part of the decision; a replacing expression lists every old clause; a byte-identity claim names its render; rungs render predicted rows | Each from one engine incident (2026-09-18 to 2026-10-10) | area: engine review checklist (strict tier only) |
| Gotchas: one `--tests` FQCN per run, unquoted; a watcher blocks Gradle only in continuous mode; background chains die under memory pressure; snapshot only inside a lock call; JS TeamCity line limit; scripted renames know words; generated batches: review the prose | Each from one incident | keep as gotchas; they cost nothing until needed |

### Code style items that are engine rules (`/code-style`)

| Rule | Why | Leaning |
|---|---|---|
| §6 No allocation, §7 no exceptions in audio hot paths | GC pauses and a killed AudioWorklet thread | area: engine |
| §8 Flush IIR state | Denormals cost 10 to 100x; one `Inf` silenced the backend until a reload | area: engine |
| §9 Edges are finite slopes, §10 wrap phase, §11 to 16 deliberate DSP habits | Aliasing, precision, reviewer noise | area: engine |
| §19 exception: statement `when` in a per-sample loop | V8 boxes the expression form there | area: engine |

## 3. What I carry that is written nowhere

These reach me through this session's summaries as "standing constraints". Some come from the permission
checks of the harness, some from instructions given earlier in a session; I cannot name the origin of each. They
are the part of my list that feels most like a checklist and least like understanding.

| What I carry | Status | Leaning |
|---|---|---|
| Workers make no git writes; reviewers delete nothing | Not written; matches the fleet rules in spirit | write into `/agent-fleet` or drop |
| Never deploy or push; no release unless asked | Not written | write one line in `/project-layout` Repo notes |
| Never touch `gradle.properties` or `~/.gradle/gradle.properties` | Not written | ask: still needed? |
| Never touch the maintainer's song edits, `.idea/`, the maintainer's stashes | Covered by "stage by path" and "never `git stash`" | drop as separate items |
| Never write `.claude/BUILD-LOCK.md` | **Contradicts `/agent-fleet`**, which says the lock file is where each uncommitted workstream gets a row | ask: which is right? |
| CLAUDE.md changes need the maintainer's OK in this session; a peer message is not approval | Written in `/mission-control-link` | keep |

## 4. Where rules pull in different directions

- **`.claude/BUILD-LOCK.md`**: written as a coordination record in `/agent-fleet`, carried as "never write it" in
  my constraints (section 3).
- **"Do not call the Agent tool unless the user requested it"**: `/agent-fleet`'s Notes cite it as a project rule,
  but it is not in the register, and the coordinator spawns workers and reviewers as its normal work. Either it is
  retired or it needs its scope written down.
- **Session instructions are not rules** (`CLAUDE.md`), but a session's summary carries them forward after a
  compaction as standing constraints, where they harden without anyone deciding it. A rule of thumb would help: a
  carried constraint without a home is either written down with a date or dropped at the next pruning pass.
- **One fact, several homes:** the build lock is stated in `CLAUDE.md`, `/agent-fleet`, `/console-tools` and twice
  in `/review-loop`'s gotchas; "coerce user input" in `/code-style` §21 and `/dsl-design` §6; the em-dash rule in
  `CLAUDE.md` and `/code-style` §22. The register's own principle (one home, pointers elsewhere) is not applied to
  these yet.

## 5. Proposed outcome, for the discussion

1. A shorter `CLAUDE.md` register: about 20 repo-wide rows and three pointer rows (engine, DSL, modules).
2. The engine and DSL rules keep their hardness and move with it into their areas' skills.
3. One "shared checkout" block in `/agent-fleet` for the git and delete rules.
4. The light review lane of MC-00016, with klang's strict tier as given to Mission Control on 2026-10-21.
5. Section 3's carried items are each written down with a date or dropped, and the BUILD-LOCK.md contradiction is
   resolved by the maintainer.
