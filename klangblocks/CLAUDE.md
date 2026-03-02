# KlangBlocks — Dispatcher

Visual block editor for KlangScript. Kotlin Multiplatform (JVM + JS).
Converts KlangScript source ↔ a tree of typed block model objects.

## Pipeline

```
KlangScript source
    ↓  KlangScriptParser.parse()
AST (Program)
    ↓  AstToKBlocks.convert()
KBProgram (block model)
    ↓  KBProgram.toCode() / toCodeGen()
Generated code + position maps (CodeGenResult)
```

## Key Files

| File                    | Role                                                   |
|-------------------------|--------------------------------------------------------|
| `model/KBBlock.kt`      | All `KBStmt` and `KBChainItem` sealed types            |
| `model/KBArgValue.kt`   | All argument value types                               |
| `model/KBProgram.kt`    | Root model; immutable list of `KBStmt`                 |
| `model/AstToKBlocks.kt` | AST → `KBProgram` conversion                           |
| `model/KBCodeGen.kt`    | `KBProgram.toCode()` / `toCodeGen()` + `CodeGenResult` |

## Reference Files — Read Only What You Need

| Topic                                                    | File                                 |
|----------------------------------------------------------|--------------------------------------|
| KBStmt, KBChainItem, KBArgValue types                    | `ref/block-model.md`                 |
| AST → blocks conversion rules                            | `ref/ast-to-blocks.md`               |
| Code generation, CodeGenResult, source map / hit testing | `ref/code-gen.md`                    |
| Round-trip + source map testing (6-step requirement)     | `ref/round-trip-testing.md`          |
| All features: klangscript ↔ klangblocks sync matrix      | `klangscript/ref/feature-catalog.md` |
| DnD behaviour spec (all source × target cases)           | `ref/dnd-behaviour.md`               |

## Build & Test

```bash
./gradlew :klangblocks:jvmTest
./gradlew :klangblocks:jvmTest --tests KBCodeGenTest
./gradlew :klangblocks:jsTest
```
