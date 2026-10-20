# Next steps after the pause (2026-10-20)

We paused after v0.7.2 for the token budget (maintainer, 2026-10-20), on a clean point. This note says where each
stream picks up again. Read it first, then the file it names. The note of the previous pause (2026-10-13) is in git
history.

## Where things stand

- `main` holds everything released: v0.7.2, tagged 2026-10-20 (the version commit `1eca7511`). The full check of
  `b18d837d`, the commit before it (2026-10-19): tests GREEN (18,284), corpus 3 of 3 frozen identical, allocation
  GREEN (80 of 80), timing done.
- New since v0.7.1: completion offers the song's own names and a call's unfilled parameter names (both hand-tested by
  the maintainer 2026-10-20); engine follow-ups 10c and 10d (two fm sites), 16 to 19, 28, 35, 36 and 38; the delay
  docs; a fixed Karma port per module; the build lock found from every worktree.
- Two branches hold unmerged work, each in its worktree under `.claude/worktrees/`. All other work is merged; the
  finished worktrees and their branches were removed 2026-10-20.

## Engine stream (Klang-Main-Dev)

1. **`engine-alloc-batch-2`** (`add8fd7c`, based on `f13dcdbf`, so rebase onto `main` first): engine follow-ups 4
   (the superpluck's base delay cached), 45 (the duck and gain hand-offs, the distort's `isFiniteInline`) and 13
   (checked, nothing to change). Not reviewed yet. Before `main`: the review loop (code and audio), a clean mutation
   campaign (the first one was disturbed by an outside writer, likely the IDE writing into the worktree),
   `:audio_be:jsBrowserTest`, the allocation check, then a full check.
2. **Master follow-up 8, option (a)**, decided 2026-10-19: the offline render counts the master chain's latency
   ([`../master-dsl-followups.md`](../master-dsl-followups.md) §8). It moves two corpus rows (Der Schmetterling,
   Kokon), accepted with the change.
3. Then the order in [`engine-follow-ups.md`](engine-follow-ups.md), its first paragraph. 10c and 10d wait for the
   FM round.

## Tools and housekeeping (Klang-Main-Dev)

1. **`console-tools-adoption`** (`addbfb37`): test-digest, mutate, docs-lint, review-pack and stack-merge in
   `console/tools/`, the skill `/console-tools`. Review round 1 is done (7 MAJORs fixed). Next: review round 2, then
   the condense proposal the maintainer approved 2026-10-19 ("as you proposed"; it adds the tool block to
   `CLAUDE.md`), kept on that branch in `docs/tasks/in-progress/console-tools-condense/` with the script that applies
   it. Then merge, and send Mission Control the commit: it ports the MAJOR fixes into its blueprints and builds one
   merge tool (stack-merge and Spross's land) on ours.
2. **With that merge, Mission Control's blueprint update** (2026-10-20, low priority): copy the new skills
   `mission-control-link` and `project-layout` from `/opt/dev/peekandpoke/missioncontrol/blueprints/skills/`; check
   `project-layout` against Klang, record our deviations under its "Repo notes" (for example `blog/`, `funding/`,
   `plans/future/`, this note's pause rule) and tell Mission Control where it describes Klang wrongly; take the
   blueprint's "Proposed upstream" and "Changes" sections into our console-tools skill. The tool masters now live in
   `/opt/dev/peekandpoke/missioncontrol/blueprints/tools/`. Pointer lines for the new skills in `CLAUDE.md` need the
   maintainer's OK in the session.
3. **The docs-lint sweep** (maintainer, 2026-10-19: after the release): run docs-lint from the tools branch; fix the
   26 stale task paths it finds in KDoc and docs; ask the maintainer about its two task-place findings
   (`sprudel-test-coverage-and-review.md`, `tutorial-curriculum.md`: is the status line stale, or does the file belong
   elsewhere?); and the `_priorities.md` tremolo entry (tools review round 1, MINOR 17).
4. **Mission Control's review-loop RFC**: one answer to its draft
   (`/opt/dev/peekandpoke/missioncontrol/blueprints/skills/review-loop/SKILL.md`), its four questions and its seven
   open ones. The tier names are agreed (strict / standard / light). Nothing is adopted until the maintainer decides.
   Our two Kraft pitfalls (`jsObject { dom = ... }` inside a component, a static `@JsModule` defeating lazy loading)
   come back with Mission Control's merged `kraft-knowhow`; hold them until then.
5. **The lexer accepts an unclosed block comment** ([`klangscript/MEMORY.md`](../../../klangscript/MEMORY.md), Open
   threads): a fix to make.

## Katalyst stream (Klang-Research)

Unchanged since 2026-10-13: its task is closed and archived, and what is left waits for a song that asks for it
([`../katalyst-knob-followups.md`](../katalyst-knob-followups.md)).

## Open with the maintainer

- An FM round together: [`../future/fm-revisit.md`](../future/fm-revisit.md), now with the open sites of engine
  follow-ups 10c and 10d.
- Completion, left open: a local that shadows a library name shows two rows; a local bound to an arrow shows no type;
  after a named argument the list still shows locals and library names; a new line inside a multi-line call offers
  no names until a comma. Records:
  [`20261019-editor-local-symbol-completion.md`](../../tasks-archive/2026-10/20261019-editor-local-symbol-completion.md)
  and [`../klangscript-named-args-docs-polish.md`](../klangscript-named-args-docs-polish.md), "Open, for the
  maintainer".
- Ultra 0.114.0: we wait for it (0.113.0 skipped, 2026-10-19), then upgrade.
- sprudel's own typed Kotlin door (its doors take `PatternLike = Any`): needs its own design and a home
  ([`../../tasks-archive/2026-10/20261016-kotlin-typed-doors.md`](../../tasks-archive/2026-10/20261016-kotlin-typed-doors.md),
  "Left after the rollout").
- The stdlib's `hashCode(dynamic)` closure (every Kotlin/JS `HashMap` operation allocates on V8) is noted for an
  upstream report, not filed (engine follow-ups, item 2).
- In IntelliJ, marking `.claude/worktrees` as Excluded keeps the IDE from writing into an agent's worktree.
