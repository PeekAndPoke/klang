# KLANG-00329: experiment: tool look-out, where a tool would beat a hand edit

> **Status (2026-10-23): IN PROGRESS.** Experiment until the meeting of 2026-10-30 (maintainer, 2026-10-23, Mission Control's MC-00029); the meeting deletes it or turns it into a rule.

## Why this exists

The maintainer (2026-10-23, relayed by Mission Control): "For the next week agents will look out for where they
would like to use tools instead of tweaking code "by hand". This might include: IntelliJ Optimize Imports -> make
sure the no wildcard imports setting is on in each projects; IntelliJ Auto-Format code -> record differences in
code-style so we can update the code-style settings in IntelliJ; IntelliJ Refactor Rename Symbol; IntelliJ Refactor
Move". And: "we need to make sure that these tool uses really land in the correct project. Not sure how stable the
IntelliJ MCP is".

## The work

- **Note every case** where a tool would have beaten a hand edit, in the table below: the date, what, which tool,
  what it would have saved.
- **IntelliJ stays read-only** for klang's agents (maintainer, 2026-10-20) until the meeting allows a write action,
  one by one, with one exception: **Optimize Imports is allowed from 2026-10-23** (the maintainer, relayed by Mission
  Control: "Ultra has success with the optimize imports action on Intellij. I updated the permission for the two
  other repos as well"). Every other write action stays off until the meeting. The safeguards for an allowed one: always pass klang's absolute `projectPath`; before the write,
  `get_repositories` with that path lists klang's root; after it, `git status` and `git diff` show the change in
  klang and nothing elsewhere (anything else is an incident); never for work meant for a worktree (IntelliJ edits
  the main checkout); only where the maintainer expects it (it lands in their open editor).
- **Record every IntelliJ call that lands wrong or fails** (also in `docs/tasks/_incidents.md` if it did damage).
- **Auto-Format against `/code-style`:** record each difference between IntelliJ's format and klang's code style,
  so the IntelliJ settings can be updated.

## Findings so far

- **Wildcard imports, 2026-10-23:** `.idea/codeStyles/Project.xml` sets both Kotlin star-import thresholds to
  `Int.MAX` (2147483647), so a count never triggers a star import. But it does not override IntelliJ's default
  "Packages to Use Import with '*'" list (`java.util.*`, `kotlinx.android.synthetic.**`, `io.ktor.**`), and the only
  five star imports in klang are exactly that list's: `io.ktor` in `audio_fe/.../utils/AssetLoader.kt` (4) and
  `audio_fe/.../cache/DiskUrlCache.kt` (1). To forbid wildcards fully, the list is emptied in the settings and
  those five imports are written out. The list was emptied the same day at the maintainer's word (the form Spross
  uses, `<option name="PACKAGES_TO_USE_STAR_IMPORTS"><value /></option>`); the five `io.ktor` star imports were
  then written out by hand (the first case below), so klang has no star import left.
- **The MCP picks no project by itself, 2026-10-23:** `get_repositories` without `projectPath` refused and listed
  the 13 open projects instead of guessing. The safe behaviour; the safeguard above still applies.

- **Optimize Imports, how, 2026-10-23:** the call is `invoke_ide_action` with `actionId: "OptimizeImports"`,
  `filePaths` relative to the project root and `projectPath: /opt/dev/peekandpoke/klang`. `get_repositories` with
  that path returns one Git root at the project root (checked the same day). The permission the maintainer set is
  `mcp__idea__invoke_ide_action` in the machine's local settings, and it allows every IDE action, not only this one:
  that only Optimize Imports runs rests on the agents, not on the setting.

## Cases

| date | what | tool | what it would have saved |
|---|---|---|---|
| 2026-10-23 | The five `io.ktor` star imports in `audio_fe` (`AssetLoader.kt`, `DiskUrlCache.kt`) written out by hand at the maintainer's word: the used names found by reading each file, and one resolution checked by hand (`Charsets`: a star import outranks Kotlin's default `kotlin.text.Charsets`, so the explicit import is ktor's, the same as before) | IntelliJ Optimize Imports, once the emptied star list is loaded | reading two files and the resolution question, a few minutes; the compile check on JVM and JS is needed either way |

## Uses of Optimize Imports

One row per use: the files, the `get_repositories` check, what `git diff` showed (anything but import lines is an
incident).

| date | files | checks | result |
|---|---|---|---|

## History

- 2026-10-23: created.
- 2026-10-23: Optimize Imports allowed as the first IntelliJ write action (maintainer, via Mission Control).
