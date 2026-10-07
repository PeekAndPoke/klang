# Code-style pass: name the arguments that could be swapped

Status: **queued 2026-10-07 (maintainer).** After step 5 of `voice-lifecycle-state-machine.md` lands (the same
files are being edited there).

## Why

The new `/code-style` §24 (maintainer, 2026-10-07): at a call site, pass arguments by name whenever two or more of
them could be swapped and still compile, or a parameter reorder would silently shift them. Found in review:
`Cylinders.checkIn(id: Int, voiceId: Int, ...)`, two ids that swap silently, in a different order than
`Cylinder.checkIn`.

## Scope

`audio_be` and `audio_bridge` first (main and test sources), then the other modules as a follow-up if the
maintainer wants it.

## How

- Find every call with two or more arguments of the same or confusable type (`Int`, `Double`, `Boolean`, `String`,
  frames vs seconds, ids), and name them. Constructors and data-class copies included.
- Where a declaration invites the mistake (two adjacent ids, parameter orders that differ between sibling
  functions), fix the declaration too, if it is plain to do.
- Mechanical and behaviour-neutral: the 18-song corpus bit-identical, the suites green, no mutation campaign needed
  for pure renames (the review checks the diff).
