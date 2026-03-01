---
name: klangblocks-knowhow
description: Use when working on the klangblocks module, implementing block editor features, working on AST-to-blocks conversion, code generation, or round-trip testing for the visual block editor.
---

## What This Skill Does

Loads context for working on the `klangblocks` Kotlin/Multiplatform module — a visual block
editor that converts KlangScript source code into typed block model objects and back.

## Context to Read

**Always read first:**

1. **`klangblocks/CLAUDE.md`** — dispatcher: pipeline overview, key files, which ref to read next
2. **`klangblocks/MEMORY.md`** — current status, architecture decisions, lessons learned

**Then read only the ref file(s) relevant to your task:**

| Task                                                      | Read                                    |
|-----------------------------------------------------------|-----------------------------------------|
| Understanding `KBStmt`, `KBChainItem`, `KBArgValue` types | `klangblocks/ref/block-model.md`        |
| Working on AST → blocks conversion (`AstToKBlocks`)       | `klangblocks/ref/ast-to-blocks.md`      |
| Working on code generation or `CodeGenResult` hit testing | `klangblocks/ref/code-gen.md`           |
| Writing or fixing round-trip tests                        | `klangblocks/ref/round-trip-testing.md` |

## Cross-Module Rules

**Every new KlangScript language feature must be represented in klangblocks.**

When a new language feature is added to KlangScript:

1. Read `klangscript/ref/adding-features.md` for the feature's grammar/syntax/semantics
2. Add support in `AstToKBlocks.kt` (new AST node → appropriate `KBArgValue` or `KBChainItem`)
3. Add support in `KBCodeGen.kt` if needed
4. Write a round-trip test (see `klangblocks/ref/round-trip-testing.md`)

## Testing

```bash
./gradlew :klangblocks:jvmTest                          # preferred (fast)
./gradlew :klangblocks:jvmTest --tests KBCodeGenTest    # specific class — NO quotes
./gradlew :klangblocks:jsTest                           # JS platform
```

## Notes

- Do NOT read all ref files upfront — load only what the task requires.
- Update `klangblocks/MEMORY.md` after completing significant work.
- Round-trip equality: always compare **ASTs** (not strings or `KBProgram` instances).
