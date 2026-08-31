# BUILD LOCK: one agent builds this worktree at a time

This file exists to coordinate agents, and nothing else. It answers the two questions the next
agent has before it touches Gradle:

1. **May I build right now?**
2. **What is already half-changed under me, and who owns it?**

It is **not** a ledger. What you achieved goes in the commit message; what you learned goes in
`docs/`; a green test run goes nowhere, because it is not news to anyone. Past handover notes live
in [`build-lock-log.md`](build-lock-log.md), which nobody reads to take the lock.

**Keep this file under ~120 lines.** If it is longer, the log has leaked back in. Move it.

---

**HOLDER: none**
**SINCE: —**
**STATE: FREE**

## Uncommitted in this tree

One row per live workstream, so nobody attributes your edits to their own bug. **Delete your row
the moment your work is committed** (git is the ledger from then on), and move the note, if it is
worth keeping at all, to `build-lock-log.md`.

| Owner (session) | Paths it owns | Half-done / would surprise you |
|-----------------|---------------|--------------------------------|

## The two layers

Convention adopted from the sibling `ultra` project. They catch different failures:

| Layer | What it is | Catches |
|-------|------------|---------|
| **This file** | Advisory. A holder record and the table above. | *Different sessions and days.* Tells the next agent what is half-done under it. A human reads it. |
| **`console/with-build-lock.sh`** | Real. An exclusive `flock` on `.claude/build.lock`. | *The same moment.* A second process physically cannot build while the first one is building. |

## Why Klang needs this

1. **Two concurrent Gradle invocations corrupt the sprudel KSP cache.** Recovery is
   `./gradlew :sprudel:clean`, but the symptom surfaces later as a stale-class or IR-lowering error somewhere
   unrelated, so the cause is easy to misdiagnose.
2. **Worse, during a mutation-check campaign: a build that races an edit produces a wrong verdict.**
   If one agent restores a mutation while another is running a test, the second agent sees code it never chose. A
   green that should have been red is indistinguishable from a toothless test, and the whole point of the audit is
   that a green must be *earned*. A raced verdict silently poisons the ledger.

## Take it before you build

1. **Read this file as its own step.** Do not chain the read into the build with `&&`: a check whose result cannot
   change what happens next is not a check. (Inherited from ultra, where exactly that happened.)
2. If `STATE: FREE`, rewrite `HOLDER` / `SINCE` / `STATE`, **then** build.
3. Add your row to "Uncommitted in this tree" as soon as you have edits, not at the end.
4. Re-read before every build and every commit, not once per session: the holder changes underneath you.

## Release it

1. Set `STATE: FREE` and clear `HOLDER`.
2. Committed everything? Delete your row. You are done, there is nothing to hand over.
3. Left something uncommitted? Keep the row, and write the note for a reader who has to *decide something*: what is
   half-done, what would look like a bug but is not, which paths are yours. Not a summary of your session.
4. Prune while you are here: any row whose work has since been committed is dead, delete it.

Anything longer than a table cell belongs in `build-lock-log.md` (newest first) or, if it is
knowledge rather than a handover, in `docs/` and in the code itself.

## The mechanical lock

```bash
console/with-build-lock.sh ./gradlew :audio_be:jvmTest --tests some.Fqcn
console/with-build-lock.sh bash -c 'apply-mutation && ./gradlew ... ; restore-mutation'
```

Waits up to `KLANG_LOCK_TIMEOUT` seconds (default 900), exits **75** if it cannot acquire.

**For a mutation check, the critical section is `mutate → build → restore`, not just the build.**
Wrapping only the Gradle call leaves the window open where it matters most.

## Rules for sub-agent fan-out

See `/agent-fleet`. The short version:

- **Default: the coordinator owns the build.** Workers read, analyse and propose; they do not build. Say so in the
  worker prompt: *"Do NOT run Gradle or any build command."*
- **Only ONE owner mutates production code**, ever. Mutation-checking is inherently serial: it edits shared files, so
  two mutators read each other's edits and both draw wrong conclusions. This is not fixable with a lock around the
  build, it needs a single owner.
- If a worker genuinely must build, it goes through `with-build-lock.sh`, and only one worker gets that permission.

## If the lock looks stale

If `SINCE` is more than a day old and the holder has committed nothing in that time, the holder probably died. **Do
not take the lock silently, ask the maintainer.** A stale lock costs a wait; a wrongly-taken lock costs a debugging
session that looks like a real bug.

For the mechanical lock, a stale `.claude/build.lock` is harmless: `flock` releases on process exit, so the file's
content may be stale but the lock itself never is. Only the printed holder record can lie.
