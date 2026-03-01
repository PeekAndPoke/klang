# KlangScript — Testing Strategy

## Running Tests

```bash
./gradlew :klangscript:jvmTest          # preferred — fast, run first
./gradlew :klangscript:jsTest           # run after jvmTest for JS-specific issues
./gradlew :klangscript:jvmTest --tests MyTestClass   # specific class, NO quotes
```

## Test Structure

All tests live in `src/commonTest/kotlin/`. Kotest `StringSpec` style.

```kotlin
class MyFeatureTest : StringSpec({
    val engine = klangScript { /* register needed functions */ }

    "should do something" {
        val result = engine.execute("let x = 1 + 2; x")
        result shouldBe NumberValue(3.0)
    }
})
```

## Critical Test Files

| File                                                                         | What it guards                                             |
|------------------------------------------------------------------------------|------------------------------------------------------------|
| `MethodChainingNoArgsTest.kt`                                                | Postfix loop — call/member any order                       |
| `ArrowFunctionTest.kt`                                                       | Arrow function backtracking, object literal disambiguation |
| `DirectParserTest.kt`                                                        | Parser-specific edge cases                                 |
| `MultilineStringLocationTest.kt`                                             | Source location tracking for multiline strings             |
| `ComparisonOperatorsTest.kt`                                                 | All 6 comparison operators + precedence                    |
| `ArrowFunctionBlockBodyTest.kt`                                              | Block body, `return`, early return                         |
| `ImportAliasingTest.kt` / `NamespaceImportTest.kt` / `ExportAliasingTest.kt` | Import/export variants                                     |
| `ArrayMethodsTest.kt`                                                        | Built-in array methods                                     |
| `KlangScriptExtensionBuilderTest.kt`                                         | All native registration variants (0–5 params + vararg)     |

## What to Test for Each New Feature

1. **Happy path** — basic usage works
2. **Edge cases** — empty input, null, wrong types
3. **Error cases** — verify the right `KlangScriptError` subtype is thrown
4. **Operator precedence** — if adding an operator, test it composes correctly with existing ones
5. **Both platforms** — `jvmTest` then `jsTest`

## Test Count Baseline

- 735 tests passing as of 2026-02-17
- If count drops, a test was broken — do not commit
