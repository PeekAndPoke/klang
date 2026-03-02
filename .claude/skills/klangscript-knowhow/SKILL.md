---
name: klangscript-knowhow
description: Use when working on the klangscript module, implementing klangscript language features, working on the parser or interpreter, or needing klangscript architecture knowledge.
---

## What This Skill Does

Loads context for working on the `klangscript` Kotlin/Multiplatform module — a JavaScript-like
scripting language for live coding, built with a hand-rolled recursive descent parser and
tree-walking interpreter.

## Context to Read

**Always read first:**

1. **`klangscript/CLAUDE.md`** — dispatcher: architecture overview + which ref file to read next
2. **`klangscript/MEMORY.md`** — current status and lessons learned

**Then read only the ref file(s) relevant to your task:**

| Task                                                                               | Read                                  |
|------------------------------------------------------------------------------------|---------------------------------------|
| Understanding language design / limitations                                        | `klangscript/ref/language-design.md`  |
| Working on the parser / lexer                                                      | `klangscript/ref/parser-impl.md`      |
| Working on the interpreter / runtime                                               | `klangscript/ref/interpreter-impl.md` |
| Adding a new language feature end-to-end                                           | `klangscript/ref/adding-features.md`  |
| Running or writing tests                                                           | `klangscript/ref/testing-strategy.md` |
| Checking which features exist, which tests cover them, and klangblocks sync status | `klangscript/ref/feature-catalog.md`  |

## Testing

```bash
./gradlew :klangscript:jvmTest    # preferred (fast)
./gradlew :klangscript:jsTest     # when testing JS-specific behavior
```

## Notes

- Do NOT read all ref files upfront — load only what the task requires.
- Update `klangscript/MEMORY.md` after completing significant work.
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
