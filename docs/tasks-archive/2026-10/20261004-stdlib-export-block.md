# The stdlib `export { ... }` block governs nothing, and selective imports from "stdlib" fail

Status: **DONE 2026-10-04: the block is deleted** (maintainer: "Yes remove the confusing block please"). Found
2026-10-03 by the Ignitor/Katalyst rename (worker C2, confirmed by both reviewers of round R-B). A probe before and
after the deletion gave identical results: without an import the stdlib names are undefined; `import * from "stdlib"`
loads every one of them (the natives go to the engine's native environment, the parent of every script scope);
`import { Math } from "stdlib"` fails with "Cannot import non-exported symbols"; `import * as s from "stdlib"` binds
nothing. Even an explicit `export { Math }` cannot export a native, which is what made the list meaningless.
`StdLibScopeSpec` pins the three behaviours (the selective-import row mutation-checked through the interpreter's
export check). Note: the analysis below says the names are visible "listed in the block or not"; that holds once the
library is imported, not before.

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
