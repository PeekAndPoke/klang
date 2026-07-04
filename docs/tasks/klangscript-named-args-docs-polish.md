# KlangScript named arguments — docs-polish remainder

> Split from `klangscript-named-arguments.md` (~85% done). The core feature — parser, runtime,
> `createFunction` builders, KSP default extraction, the `NamedArgumentChecker`, and optional-param
> signature rendering — **shipped**. Full record: `../tasks-archive/2026-06/20260616-klangscript-named-arguments.md`.
> Priority: **low** (small polish on a done feature).

## Open items

- **7.2 — Usage-styles panel.** In the library docs page, render both a positional and a named-arg
  example per callable (`buildPositionalExample` / `renderUsageStyles`). Not built.
- **7.3 — KDoc conventions + `@sample` sweep.** Create `klangscript/ref/kdoc-conventions.md` and add
  `@sample` blocks across the stdlib so generated docs carry runnable examples. Not built.
- **6.4 — Param-name completion inside `(`.** Offer unfilled parameter names as completions when the
  cursor is inside a call's argument list. Nice-to-have; the editor-surfacing half folds into
  `klangscript-intellisense.md` (which must first wire the `[]` linter stub).
