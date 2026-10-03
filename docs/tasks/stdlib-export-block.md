# The stdlib `export { ... }` block governs nothing, and selective imports from "stdlib" fail

Status: **found 2026-10-03** by the Ignitor/Katalyst rename (worker C2, confirmed by both reviewers of round R-B with
the code paths below). Not started; needs a decision: delete the block, or make it work.

## What happens today

- Any `import ... from "stdlib"` first registers the library's native objects into the engine's native environment,
  the parent of every script scope (`klangscript/.../Environment.kt`, around line 300; `KlangScriptEngine.kt`, lines
  78 to 83). That is how every stdlib name (`Math`, `Ignitor`, `Katalyst`, ...) is visible, listed in the block or not.
- The library's `source(...)` block (`klangscript-libs/.../KlangStdLib.kt`) then runs in its own empty child
  environment. `getExportedSymbols()` (`Environment.kt`, lines 240 to 252) looks names up only in that child's own
  values, so the exported map is always empty.
- Consequences: `import * from "stdlib"` copies nothing and works anyway; `import * as s from "stdlib"` binds an
  EMPTY object; `import { Math } from "stdlib"` throws "Cannot import non-exported symbols" for every name
  (`Interpreter.kt`, lines 357 to 395).
- Selective imports DO work for symbols a library defines in script, which is why no test caught this. No other code
  reads the block, stdlib is the only library with one, and no test imports selectively from "stdlib".

## The decision

1. Delete the block (and say in the docs that stdlib names are always in scope), or
2. make native objects exportable (the export map consults the native environment for listed names), with specs for
   the three import forms.

Either way, a spec pins the chosen behaviour of `import { Math } from "stdlib"` and `import * as s from "stdlib"`.
