# KlangScript — Dispatcher

JavaScript-like scripting language for live coding. Kotlin Multiplatform (JVM + JS).
Hand-rolled lexer + recursive descent parser. Tree-walking interpreter.

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
| `stdlib/KlangStdLib.kt`                  | Standard library (Math, console, string/array methods) |

## Reference Files — Read Only What You Need

| Topic                           | File                      |
|---------------------------------|---------------------------|
| Language design & limitations   | `ref/language-design.md`  |
| Parser / lexer internals        | `ref/parser-impl.md`      |
| Interpreter / runtime internals | `ref/interpreter-impl.md` |
| Adding a new language feature   | `ref/adding-features.md`  |
| Testing strategy                | `ref/testing-strategy.md` |

## Build & Test

```bash
./gradlew :klangscript:jvmTest          # run all tests (fast)
./gradlew :klangscript:jsTest           # JS platform tests
./gradlew :klangscript:compileKotlinJvm # compile only
```
