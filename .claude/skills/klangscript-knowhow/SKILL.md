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

| Task                                        | Read                                  |
|---------------------------------------------|---------------------------------------|
| Understanding language design / limitations | `klangscript/ref/language-design.md`  |
| Working on the parser / lexer               | `klangscript/ref/parser-impl.md`      |
| Working on the interpreter / runtime        | `klangscript/ref/interpreter-impl.md` |
| Adding a new language feature end-to-end    | `klangscript/ref/adding-features.md`  |
| Running or writing tests                    | `klangscript/ref/testing-strategy.md` |

## Testing

```bash
./gradlew :klangscript:jvmTest    # preferred (fast)
./gradlew :klangscript:jsTest     # when testing JS-specific behavior
```

## Notes

- Do NOT read all ref files upfront — load only what the task requires.
- Update `klangscript/MEMORY.md` after completing significant work.
- `klangscript/TODOS.MD` has the pending feature checklist.
