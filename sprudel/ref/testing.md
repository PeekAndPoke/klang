# Strudel — Testing Strategy

## Commands

```bash
./gradlew :strudel:jvmTest                          # preferred (fast)
./gradlew :strudel:jvmTest --tests LangBpmSpec      # specific class — NO quotes
./gradlew :strudel:jsTest                           # browser-specific only
```

## Rules

- **Test across ≥12 cycles** — timing bugs compound and only surface after several cycles
- **Always verify both `part` and `whole`** explicitly
- **Filter by `isOnset`** when testing playback behavior; `queryArc()` returns all events

## Test Structure

```kotlin
"pattern test" {
    val subject = createPattern()
    assertSoftly {
        repeat(12) { cycle ->
            withClue("Cycle $cycle") {
                val cycleDbl = cycle.toDouble()
                val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                    .filter { it.isOnset }
                events[0].part.begin.toDouble() shouldBe (expected plusOrMinus EPSILON)
                events[0].whole.begin.toDouble() shouldBe (expected plusOrMinus EPSILON)
            }
        }
    }
}
```

## JS Compatibility

`compat/JsCompatTests.kt` — compares Kotlin output directly against JS implementation.
Add compat test cases when implementing any new DSL function.