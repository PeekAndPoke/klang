# KlangScript - Claude Context

## What is KlangScript?

**JavaScript-like scripting language for live coding**, built with Kotlin Multiplatform. Embeddable in Kotlin
applications, designed for the Strudel live coding environment.

**Status**: Production-ready, 735 tests passing (100%)

## Recent Changes (2026-02-17)

**Parser rewritten from better-parse to hand-rolled recursive descent parser**

- **Why**: better-parse broken in Kotlin/JS production builds (issue #66)
- **Result**: Zero dependencies, Kotlin/JS builds work ✅
- **Implementation**: 1005-line hand-rolled lexer + recursive descent parser
- **All tests pass**: JVM ✅, JS ✅, Production build ✅

**Multiline string source location tracking fixed**

- **Issue**: Multiline backtick strings had incorrect endLine (always 1)
- **Fix**: Token class now tracks `endLine` separately, tokenizer saves `startLine` before processing
- **Result**: Source locations correctly span multiple lines for multiline strings
- **Test**: `MultilineStringLocationTest.kt` validates multiline location tracking

## Architecture

### Three Layers
```
KlangScriptEngine (facade)
    ↓
KlangScriptParser (hand-rolled recursive descent)
    ↓
AST (sealed classes) → Interpreter (tree-walking)
```

### Key Files

**Parser** (hand-rolled, zero dependencies):

- `parser/KlangScriptParser.kt` (1005 lines) - Lexer + recursive descent parser
- `parser/ParseException.kt` - Custom exception (replaces better-parse)

**AST**:

- `ast/Ast.kt` (756 lines) - All AST node types, **NEVER MODIFY**

**Runtime**:

- `runtime/Interpreter.kt` - Tree-walking evaluator
- `runtime/RuntimeValue.kt` - Value types (Number, String, Function, Object, Array)
- `runtime/Environment.kt` - Lexical scoping
- `runtime/Errors.kt` - Typed exceptions with stack traces

**API**:

- `KlangScriptEngine.kt` - Main facade
- `builder/KlangScriptExtensionBuilder.kt` - Native function registration DSL

## Parser Implementation Details

**Token types**: 40+ tokens (NUMBER, STRING, IDENTIFIER, keywords, operators)

**Expression precedence** (lowest to highest):

1. Arrow functions (`x => expr`) with backtracking
2. Logical OR (`||`)
3. Logical AND (`&&`)
4. Comparison (`==`, `!=`, `<`, `<=`, `>`, `>=`)
5. Addition/Subtraction (`+`, `-`)
6. Multiplication/Division/Modulo (`*`, `/`, `%`)
7. Unary (`-`, `+`, `!`)
8. Call/Member (`foo()`, `obj.prop`) - allows alternating in any order ✅
9. Primary (literals, identifiers, `(...)`, `{...}`, `[...]`)

**Critical fixes**:

- ✅ Method chaining: `obj.method().prop.method2()` (any order of calls/members)
- ✅ Arrow object literal: `x => { key: value }` disambiguated from block body
- ✅ Multi-char operators: `=>` before `=`, `==` before `=`, etc.

## Language Features

**Literals**: numbers, strings (double/single/backtick), booleans, null, objects, arrays

**Operators**: arithmetic, comparison, logical, unary

**Functions**: Arrow functions with expression or block bodies

**Statements**: let, const, return, import, export, expression statements

**Imports/Exports**:
```javascript
import * from "lib"
import * as math from "lib"
import {x, y as z} from "lib"

export {x, y as z}
```

## Testing

**Run tests**: `./gradlew :klangscript:jvmTest` or `./gradlew :klangscript:jsTest`

**Test count**: 735 tests, all passing

**Test structure**: `src/commonTest/kotlin/` - organized by feature

**Critical tests**:

- `MethodChainingNoArgsTest.kt` - Method chaining fix validation
- `ArrowFunctionTest.kt` - Arrow function backtracking
- `DirectParserTest.kt` - Parser-specific tests

## Native Interop

**Register functions**:
```kotlin
engine.registerFunction("add") { a: Int, b: Int -> a + b }
engine.registerVarargFunction("sum") { nums: List<Double> -> nums.sum() }
```

**Register types**:
```kotlin
registerType<MyClass> {
    registerMethod("process") { input: String -> this.process(input) }
}
```

## Known Limitations

- No array indexing: `arr[0]` not supported
- No control flow: `if/else`, `while`, `for` not implemented
- No template strings: `` `hello ${name}` ``
- No spread operator: `...args`
- No higher-order array methods: `map`, `filter`, `reduce`

## Common Patterns

**Create engine**:
```kotlin
val engine = klangScript {
    registerLibrary(myLib)
    registerFunction("myFunc") { x: Int -> x * 2 }
}
```

**Execute code**:
```kotlin
val result = engine.execute("let x = 10; x * 2", sourceName = "test.klang")
```

**Parse only**:
```kotlin
val ast = KlangScriptParser.parse(source, sourceName)
```

## Integration with Strudel

Primary use case: Scripting layer for Strudel live coding:

```javascript
import * from "strudel"

note("a b c").gain(0.5).pan(sine2.fromBipolar().range(0.1, 0.9))
```

## Important Notes

1. **AST types are immutable** - never change `ast/Ast.kt` without understanding downstream impacts
2. **Parser is hand-rolled** - no external parsing library dependencies
3. **All tests must pass** before any commit
4. **Source locations are 1-based** - line and column start at 1
5. **Method chaining uses loop pattern** - allows any order of calls and member access

## Quick Reference

**Build**: `./gradlew :klangscript:compileKotlinJvm`

**Test JVM**: `./gradlew :klangscript:jvmTest`

**Test JS**: `./gradlew :klangscript:jsTest`

**Clean**: `./gradlew :klangscript:clean`

---

**Last Updated**: 2026-02-17
**Parser**: Hand-rolled recursive descent (zero dependencies)
**Tests**: 735/735 passing ✅
**Production Status**: Ready for Kotlin/JS builds ✅
