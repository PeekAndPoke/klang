# Klang Project

Enjoy the ride. Errors happen, no worries, we find them, we fix them. 
We write exceptional software. We are an awesome team and we give our very best.
Der Weg ist das Ziel. Sound first!

## Rules register

Every standing rule of this project, with its hardness. Detail lives in the skill or file named in
the last column; this table is the index. Curated 2026-09-06
(`docs/housekeeping/2026-09-06-memory-and-rules-inventory.md`); slimmed 2026-10-23 at the rules retro
(Mission Control's MC-00017), when the rules of one area moved to that area (below).

**Hardness levels**

| Level         | Meaning                                                                                                        |
|---------------|----------------------------------------------------------------------------------------------------------------|
| **stone**     | Never deviate. Changing it is a maintainer decision, recorded here with a date.                                |
| **rule**      | The default. Deviate only with a stated reason in the report or the diff.                                      |
| **guideline** | Taste and preference. Apply judgement; deviating needs no justification, but say so when it matters.           |
| **guardrail** | A check against a known failure. Skip it only when you have verified that the failure cannot occur here.       |

**Session instructions are not rules.** What the maintainer says in a session applies to that
session or that phase of work. It becomes a rule only when it lands in this table or in a skill
with a date. Examples that were never meant to last: "stop before commit" (a per-phase request,
superseded 2026-09-06 by "commit completed steps"), "this file is owned by another session",
"do not compile the frontend right now".

**A new general rule starts provisional** (maintainer, 2026-10-08): "Treat them as provisional until they are proven
to be healthy rules. There is always the option to question these rules. Good rules make life easier, wrong rules lead
to messy outcomes." A rule marked *(provisional)* is followed like any other, and a case where it makes the outcome
messier is reported to the maintainer, not worked around. It loses the mark when it has proven itself.

**Rules are guard-rails, not commandments** (maintainer, 2026-10-13): "Let us not create a German tax-code like
ruleset. Let us stay reasonable." A reasonable exception for a good reason gets no rule of its own; it gets short,
clear documentation where it lives (a KDoc saying which rule it departs from and why). When a rule looks as if it
should be broken on purpose, consult the maintainer, and the rules do not have to be decided again and again.

### Stone

| Rule                                                                                                                                                   | Since      | Detail                                    |
|--------------------------------------------------------------------------------------------------------------------------------------------------------|------------|-------------------------------------------|
| Complexity is the enemy: before build-time magic or similar weight (cross-module generated sources, processor options, unusual Gradle wiring, clever indirection), STOP and consult. Prefer the plain module boundary, function, data class. | 2026-09-06 | this file                                 |
| Memory lives in the repo: module `MEMORY.md` files, `docs/tasks/`, skills. Never the home-directory auto-memory; only a machine's own setup (local directories) stays local. No home for a fact? Ask where it belongs. A module `MEMORY.md` states what is true NOW and stays short: a change updates its section in place and adds one History line; the full dated record lives in `<module>/ref/memory-history.md`. | 2026-09-05, short 2026-09-29, machine setup 2026-10-23 | this file, the knowhow skills |
| The engine is the horse: a frontend (sprudel, MIDI, a sequencer) never gets DSP of its own; it maps onto existing engine components via the wire. Only seconds cross the wire: the backend never learns about cycles. | 2026-07 | `/dsl-design` §8 |
| No boxed types in high-performance Kotlin/JS code: no `Long`/`ULong`/`Byte`/`Short`/`Char` (`Int` or `Double`) in `audio_be`, `audio_bridge` with the wire codecs, sprudel's timing core and KlangScript's interpreter, where Kotlin/JS pays on every operation. Elsewhere allowed; a slow case found outside is fixed on its own. | 2026-04, narrowed 2026-10-23 | `/code-style` §5 |
| Licensing: AGPL v3 with `AUTHORS.MD`; `tones/` stays MIT and never gets the AGPL header; `workshop/` is Apache-2.0 like Ultra, bound for Kraft (2026-10-25). Commercial use waits for copyright-audit task 07 (lawyer).    | 2026-06-24, workshop 2026-10-25 | `LICENSE`, KLANG-00118 (copyright audit 07 control vocabulary legal review), KLANG-00222 (copyright audit 00 overview) |
| Naming: "Klangmotor" / "Motor" with a plain o (the umlaut was retired 2026-08-25, "too much ego"); historic diary and strategist records keep whatever they say; Motörhead keeps its umlaut. | 2026-08-25 | `/code-style` §17 (header)                |

### Rule

| Rule                                                                                                                        | Since      | Detail                                          |
|-----------------------------------------------------------------------------------------------------------------------------|------------|-------------------------------------------------|
| Capture the incident when it happens: one line in `docs/tasks/_incidents.md`. Fix the code and the tests at once; a rule waits for the weekly meeting, which also prunes. Damage that can't be undone (harm to ears or speakers, lost work or data, an exposed secret, a push or a publish) gets a stopgap at once, preferably a check or a deny-list request, else one line marked PROVISIONAL until the meeting; unconfirmed, it lapses. | 2026-10-23 | `docs/tasks/_incidents.md` |
| Reviews loop until a clean round; every new test is mutation-checked (mandatory tier for engine/wire/KSP, light elsewhere). | 2026-08-02 | `/review-loop`                                  |
| Gradle is a single-writer resource: one build at a time, always through `console/with-build-lock.sh`; the coordinator owns it, reviewers may build under the lock (a doubt tested by a mutant runs as one `mutate` campaign), other workers never fan out. | 2026-08-04, reviewers 2026-10-04 | `/agent-fleet`, `/console-tools` |
| Published writing (whitepaper, blog, README, release notes, site copy) follows the public voice: friendly, calm and humble, inviting, human, "we", no hype, cautious with claims. Tutorials and in-app text have their own style. | 2026-09-28 | `/public-voice` |
| Code style: braces always, blank lines around `if`, flat directories, no FQCN, exhaustive `when`, NaN-guard comment, named arguments where they could be swapped (2026-10-07), copyright header (hot-path code: the engine rules). | 2026-04 | `/code-style` |
| No em-dashes in user-facing text. The commit hooks block them in docs, KDoc and commit messages; UI strings, tutorials and reports are on us. | 2026-08, hooks 2026-10-24 | `/code-style` §22, `/console-tools` |
| Every task and plan is a ticket, `KLANG-<nnnnn>-<title>.md`, in the folder its status word names: ideas and plans in `docs/tasks/future/`, the rest in `docs/tasks/`, the tickets being worked on in `docs/tasks/in-progress/`, finished ones in `docs/tasks-archive/<month>/`. References name the number, never the path; the `ticket` tool takes numbers and moves files. | 2026-06, in-progress 2026-10-10, tickets 2026-10-20 | `/project-layout`, `/console-tools` |
| Commit completed, reviewed steps on the working branch; leave work uncommitted only when the maintainer asks to inspect first. | 2026-09-06 | this file                                     |
| Every commit an AI agent wrote or co-wrote carries a `Co-Authored-By:` trailer naming the model; a commit the maintainer wrote alone carries none. The commit-msg hook blocks a Claude Code commit without one. It is the provenance record funding applications rely on. Commits before 2026-08 are inconsistent: `docs/funding/gaps.md` G4. | 2026-09-24, hook 2026-10-24 | this file |
| *(provisional)* Credits: every person, idea, paper, standard, tool or piece of music Klang builds on is credited in `CREDITS.MD` AND on the in-app Credits page (`src/jsMain/kotlin/pages/CreditsPage.kt`), the two kept in step, in the same change that brings it in. An idea only named in a plan or a research note is credited when it lands; a song credits its inspirations in its own "Inspired by" lines. Commercial products are not credited (maintainer, 2026-10-10): a public idea behind one is credited as the idea, an idea that is not public is not credited at all. | 2026-10-09, products 2026-10-10 | this line |
| Nothing in the backend or the frontend allocates without a way to clean it up: per-playback state lives in a storage the playback owns or in a per-playbackId registry that is freed when the playback dies; process-wide maps that grow per edit are debt (tracked in KLANG-00210 (signal flow redesign) §11). | 2026-09-17 | this file |
| *(provisional)* Two kinds of check: a check for the future (it guards behaviour we keep), or scaffolding (it guards one rewrite or addition, names that work, and goes in the change that finishes it, so it adds no weight after). Decide which when the check is written. Songs move freely: no check goes red because a song was edited, except when the edit breaks the song itself: it no longer compiles, or an engine pass fails on it (a real break, not a moved value) (maintainer, 2026-10-13, wording 2026-10-16). | 2026-09-06, kinds 2026-10-13 | this line |

### Guideline

| Guideline                                                                                                                  | Since      | Detail                                     |
|----------------------------------------------------------------------------------------------------------------------------|------------|--------------------------------------------|
| Sound first: engine work-streams, then the tutorial quarter, then launch, then hard performance work. Do not push launch planning or native/Wasm backends before then. | 2026-07-03 | `docs/tasks/_priorities.md`, KLANG-00129 (high performance audio backend) |
| Design for adults that kids also enjoy, never the reverse (Pixar, not PBS Kids).                                          | 2026-07    | this line                                  |
| "What we built": credit the collaboration in reports and docs.                                                            | 2026-07    | this line                                  |

### Rules that live with their area

Moved out of this register at the rules retro (2026-10-23), each with its level and date. Read them when you work there.

| Area | Home | What it holds |
|------|------|---------------|
| The engine (`audio_be`, `audio_bridge`) | `audio/CLAUDE.md`, Engine rules (loaded first by `/klangaudio-knowhow`) | the Motor stays raw (stone: no safety clamp on an audio parameter without asking), hot-path code, the 128-frame block, the deliberate engine exceptions |
| The DSLs (Ignitor, Katalyst, sprudel, every door) | `/dsl-design`, Rules | immutability (stone), two doors, parameter parity, one word, units, door shape, wire types, the compound-door fill, `min`/`max` as clamps, taste |
| What Klang builds for Kraft | `workshop/CLAUDE.md` | Kraft and Ultra only (stone), Apache-2.0 (stone), Kraft's patterns, machinery and look apart |
| Modules and songs | `klangscript/CLAUDE.md`, `klangscript-libs/CLAUDE.md`, `sprudel/CLAUDE.md`, `/klang-music-writing` | AST walkers live in `klangscript`; the stdlib follows Kotlin; Klangbuch parts and structural cycle selection; songs as Kotlin strings and the caricature sound model |

### Retired names

A retired name is never restored and never cited as current. The full list, newest first, with what replaced each
name and where it was decided, is `docs/retired-names.md`; read it when an old name turns up (for example when
porting an old song). History keeps its words.

## Current experiments

Each runs until the meeting named; the meeting deletes its line or turns it into a rule. Notes go into the ticket,
never here (maintainer, 2026-10-23).

| Experiment | Until | Ticket |
|------------|-------|--------|
| Tool look-out: note each case where a tool (IntelliJ Optimize Imports, Auto-Format, Rename, Move) would have beaten a hand edit, and where Auto-Format differs from `/code-style`. IntelliJ stays read-only except Optimize Imports (allowed 2026-10-23) and Save All, which writes its result to disk (2026-10-24); every other write action waits for the meeting. | 2026-10-30 | KLANG-00329 |

## Agents and skills

The project skills live in `.claude/skills/` (each `SKILL.md` says when to use it) and the agents in
`.claude/agents/`; the harness lists both, with their triggers, to every session. Use `/skill-name` or describe
the task. The `music-platform-strategist` agent runs only when asked for by name ("talk to the strategist").

**Console tools** in `console/tools/`, how and when in `/console-tools`. Use them instead of a hand snippet:
- `test-digest`: what really ran; a spec counts only with XML from this run, `--expect` names the specs that must run.
- `mutate`: Standard 2 in one lock call; hash-verified byte restore, and red counts only for the expected reason.
- `review-pack`: each round's full diff and fix delta, tree untouched; in a shared tree it holds others' edits too.
- `docs-lint`: broken links, an old task path left in docs or KDoc, dashes.
- `stack-merge`: reviewed branches onto a new `stack/<name>` branch; only pure insertions auto-resolved, main untouched.
- `ticket`: take a ticket number, move a ticket to its status's folder, find one, check them all; never by hand.
- `hooks`: pre-commit and commit-msg block dashes, control bytes and a missing agent trailer on added lines; never go around them.
