---
name: klangscript-knowhow
description: Use when working on the klangscript module, implementing klangscript language features, working on the parser or interpreter, or needing klangscript architecture knowledge.
---

## What This Skill Does

Loads context for working on the `klangscript` Kotlin/Multiplatform module — a JavaScript-like
scripting language for live coding, built with a hand-rolled recursive descent parser and
tree-walking interpreter. The standard library (`Osc`, `Katalyst`, `Math`, ...) is the
separate `klangscript-libs` module (read `klangscript-libs/CLAUDE.md` when the task touches the
stdlib or a DSL door).

## Context to Read

**Always read first:**

1. **`klangscript/CLAUDE.md`** — dispatcher: architecture overview + which ref file to read next
2. **`klangscript/MEMORY.md`**: what is true now (state, rules in force, traps, open threads), short by design; the full dated record is `klangscript/ref/memory-history.md`, read only when you need the history of a decision

**Then read only the ref file(s) relevant to your task:**

| Task                                                                               | Read                                  |
|------------------------------------------------------------------------------------|---------------------------------------|
| Understanding language design / limitations                                        | `klangscript/ref/language-design.md`  |
| Working on the parser / lexer                                                      | `klangscript/ref/parser-impl.md`      |
| Working on the interpreter / runtime                                               | `klangscript/ref/interpreter-impl.md` |
| Hover / completion / docs popup / AnalyzedAst / Origin / scope tracking            | `klangscript/ref/intel-analyzer.md`   |
| Adding a new language feature end-to-end                                           | `klangscript/ref/adding-features.md`  |
| Running or writing tests                                                           | `klangscript/ref/testing-strategy.md` |
| Checking which features exist and which tests cover them | `klangscript/ref/feature-catalog.md`  |

## Testing

```bash
./gradlew :klangscript:jvmTest         # language tests (fast)
./gradlew :klangscript-libs:jvmTest    # stdlib / DSL door tests
./gradlew :klangscript:jsTest          # when testing JS-specific behavior
```

## Notes

- Do NOT read all ref files upfront — load only what the task requires.
- After completing significant work, keep `klangscript/MEMORY.md` short (restructured 2026-09-29, it had grown into a log): update the current-state section your change touched IN PLACE, add ONE line to its History list (date, a few words, the link to the task record), and put the narrative in the task record, which gets archived. Never append a dated essay to MEMORY.md.
- `klangscript/TODOS.MD` has the pending feature checklist.

## Mandatory: Update language-features/ after every implementation

`klangscript/language-features/` contains one file per feature group (01–15).
Each section is marked with a status emoji:

- `✅` — fully implemented
- `🟡` — partially implemented (detail follows)
- `❌ [EASY/MEDIUM/HARD/OUT_OF_SCOPE/SEPARATE_STDLIB]` — not yet implemented

**After implementing any language feature**, update the corresponding section header
in the relevant `language-features/NN-*.md` file to reflect the new status.
Remove the `[EASY]`/`[MEDIUM]` tag and replace the heading with `✅` once done.

| Feature group file             | Covers                                      |
|--------------------------------|---------------------------------------------|
| `01-literals-and-variables.md` | let, const, number/string/bool/null/assign  |
| `02-operators.md`              | arithmetic, unary, comparison, ternary, in  |
| `03-control-flow.md`           | if/else, while, for, switch, break/continue |
| `04-functions.md`              | arrow functions, closures, HOF              |
| `05-arrays.md`                 | array literals, access, stdlib methods      |
| `06-objects.md`                | object literals, access, destructuring      |
| `07-strings.md`                | string methods, template literals           |
| `08-type-coercion.md`          | truthiness, Number/String/Boolean()         |
| `09-regexp.md`                 | regular expressions                         |
| `10-math.md`                   | Math object methods                         |
| `11-error-handling.md`         | try/catch/throw                             |
| `12-advanced-functions.md`     | generators, iterators                       |
| `13-promises-async.md`         | async/await                                 |
| `14-json.md`                   | JSON.parse/stringify                        |
| `15-sets-maps.md`              | Set, Map                                    |
