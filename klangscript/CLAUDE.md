# KlangScript — Dispatcher

JavaScript-like scripting language for live coding. Kotlin Multiplatform (JVM + JS).
Hand-rolled lexer + recursive descent parser. Tree-walking interpreter.

**This module is the language and runtime only.** The standard library (`Osc`, `Master`,
`Pipeline`, `Math`, `Object`, `console`, value-type extensions) lives in `:klangscript-libs`
(see `klangscript-libs/CLAUDE.md`), which depends on this module, never the other way round.
`klangScriptEngine()` here builds a bare engine; `klangScript()` in the libs module builds one
with the stdlib registered.

## Architecture

```
KlangScriptEngine (facade)
    ↓
KlangScriptParser  →  AST (sealed classes in ast/Ast.kt)
    ↓
Interpreter (tree-walking)  →  RuntimeValue / Environment
```

## Key Files

| File                                     | Role                                                   |
|------------------------------------------|--------------------------------------------------------|
| `parser/KlangScriptParser.kt`            | Lexer + recursive descent parser (1005 lines)          |
| `ast/Ast.kt`                             | All AST node types — **never modify blindly**          |
| `runtime/Interpreter.kt`                 | Tree-walking evaluator                                 |
| `runtime/RuntimeValue.kt`                | Value types                                            |
| `runtime/Environment.kt`                 | Lexical scoping                                        |
| `runtime/Errors.kt`                      | Typed exceptions + `ReturnException`                   |
| `KlangScriptEngine.kt`                   | Public facade                                          |
| `builder/KlangScriptExtensionBuilder.kt` | Native registration DSL                                |
| `index_common.kt`                        | `klangScriptEngine()` (bare engine), `klangScriptLibrary()` |
| `../klangscript-libs/`                   | Standard library, in its own module (see its CLAUDE.md) |

## Reference Files — Read Only What You Need

| Topic                                                         | File                      |
|---------------------------------------------------------------|---------------------------|
| Language design & limitations                                 | `ref/language-design.md`  |
| Parser / lexer internals                                      | `ref/parser-impl.md`      |
| Interpreter / runtime internals                               | `ref/interpreter-impl.md` |
| Editor intelligence (AnalyzedAst, symbolAt, scope, Origin)    | `ref/intel-analyzer.md`   |
| Adding a new language feature                                 | `ref/adding-features.md`  |
| Testing strategy                                              | `ref/testing-strategy.md` |
| All implemented features + test file links | `ref/feature-catalog.md`  |

## Build & Test

```bash
./gradlew :klangscript:jvmTest          # language tests (fast)
./gradlew :klangscript:jsTest           # JS platform tests
./gradlew :klangscript-libs:jvmTest     # stdlib tests (separate module)
./gradlew :klangscript:compileKotlinJvm # compile only
```
