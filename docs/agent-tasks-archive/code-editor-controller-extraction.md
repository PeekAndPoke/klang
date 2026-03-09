# Code Editor Helper Extraction

## Goal

Extract the duplicated helper logic shared between `CodeSongPage` and
`PlayableCodeExample` into a single top-level file `editor_helpers.kt`.
No new class is introduced — the helpers are plain functions that each
component calls directly, keeping component structure unchanged.

---

## Background / Motivation

Both `CodeSongPage` (`pages/CodeSongPage.kt`) and `PlayableCodeExample`
(`comp/PlayableCodeExample.kt`) independently duplicate two things:

1. **`mapToEditorError(e)`** — a ~25-line function that parses line/column info
   from exception messages and produces an `EditorError`.

2. **The `try/catch` + error-display shell** — every play/update action has an
   identical catch block:
   ```kotlin
   } catch (e: Throwable) {
       console.error("...", e)
       val editorError = mapToEditorError(e)
       editorRef { it.setErrors(listOf(editorError)) }
   }
   ```
   This appears in three places across both components (start, update, and the
   combined block in `PlayableCodeExample`).

Everything else — the `when(playback)` branching, compile method, start options,
signal subscriptions, extra state updates — differs enough between the two
components that extracting it would require as many callback parameters as there
are lines saved. It should stay in the components.

---

## Proposed New File

**`src/jsMain/kotlin/comp/editor_helpers.kt`**

```kotlin
package io.peekandpoke.klang.comp

import de.peekandpoke.kraft.components.ComponentRef
import io.peekandpoke.klang.codemirror.CodeMirrorComp
import io.peekandpoke.klang.codemirror.EditorError

/**
 * Maps a Throwable to an [EditorError] by extracting line/column information
 * from the exception message.
 *
 * Recognised patterns:
 *   "Parse error at 14:3: ..."
 *   "at line X, column Y: ..."
 *   "line X: ..."
 */
fun mapToEditorError(e: Throwable): EditorError {
    val message = e.message ?: "Unknown error"

    val atLineColRegex = Regex("at\\s+(\\d+):(\\d+)", RegexOption.IGNORE_CASE)
    val atLineColMatch = atLineColRegex.find(message)

    val line: Int
    val col: Int

    if (atLineColMatch != null) {
        line = atLineColMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
        col = atLineColMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
    } else {
        val lineRegex = Regex("line[:\\s]+(\\d+)", RegexOption.IGNORE_CASE)
        val columnRegex = Regex("column[:\\s]+(\\d+)", RegexOption.IGNORE_CASE)
        line = lineRegex.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        col = columnRegex.find(message)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
    }

    val cleanMessage = message
        .replace(Regex("Parse error at \\d+:\\d+[:\\s]*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("at line \\d+(, column \\d+)?[:\\s]*", RegexOption.IGNORE_CASE), "")
        .replace(Regex("line \\d+[:\\s]*", RegexOption.IGNORE_CASE), "")
        .trim()
        .takeIf { it.isNotEmpty() } ?: message

    return EditorError(message = "Line $line: $cleanMessage", line = line, col = col, len = 1)
}

/**
 * Executes [block] and, if it throws, maps the exception to an [EditorError]
 * and displays it in the editor.  Clears any existing errors before running.
 *
 * Usage:
 * ```kotlin
 * withEditorErrorHandling(editorRef) {
 *     val pattern = compile(code)
 *     playback.updatePattern(pattern)
 * }
 * ```

*/
suspend fun withEditorErrorHandling(
editorRef: ComponentRef.Tracker<CodeMirrorComp>,
block: suspend () -> Unit,
) {
editorRef { it.setErrors(emptyList()) }
try {
block()
} catch (e: Throwable) {
console.error("Editor action failed:", e)
editorRef { it.setErrors(listOf(mapToEditorError(e))) }
}
}

```

---

## Changes to `CodeSongPage`

### `mapToEditorError`
1. Delete the private `mapToEditorError()` method.
2. `CodeSongPage` is in `io.peekandpoke.klang.pages`, so add the import:
   ```kotlin
   import io.peekandpoke.klang.comp.mapToEditorError
   import io.peekandpoke.klang.comp.withEditorErrorHandling
   ```

### `onPlay()` — null branch (new playback)

Wrap the launch body with `withEditorErrorHandling`:

```kotlin
// BEFORE
null -> launch {
    if (!loading) {
        try {
            getPlayer().let { p ->
                ...
                editorRef { it.setErrors(emptyList()) }
            }
        } catch (e: Throwable) {
            console.error("Error compiling/playing pattern:", e)
            val editorError = mapToEditorError(e)
            editorRef { it.setErrors(listOf(editorError)) }
        }
    }
}

// AFTER
null -> launch {
    if (!loading) {
        withEditorErrorHandling(editorRef) {
            getPlayer().let { p ->
                ...
                // no manual setErrors(emptyList()) needed — helper clears before block
            }
        }
    }
}
```

### `onPlay()` — else branch (update)

```kotlin
// BEFORE
else -> {
    try {
        val pattern = StrudelPattern.compileRaw(code) ?: error("...")
        s.updatePattern(pattern)
        editorRef { it.setErrors(emptyList()) }
    } catch (e: Throwable) {
        ...
        editorRef { it.setErrors(listOf(editorError)) }
    }
}

// AFTER
else -> launch {
    withEditorErrorHandling(editorRef) {
        val pattern = StrudelPattern.compileRaw(code) ?: error("...")
        s.updatePattern(pattern)
    }
}
```

Note: the else branch becomes a `launch` so it can call the `suspend` helper.

---

## Changes to `PlayableCodeExample`

1. Delete the private `mapToEditorError()` method.
2. Same package — no import needed.
3. Wrap the `play()` launch body:

```kotlin
// BEFORE
launch {
    try {
        ...
        editorRef { it.setErrors(emptyList()) }  // x2 (both when branches)
    } catch (e: Throwable) {
        val editorError = mapToEditorError(e)
        editorRef { it.setErrors(listOf(editorError)) }
    }
}

// AFTER
launch {
    withEditorErrorHandling(editorRef) {
        ...
        // no manual setErrors(emptyList()) calls needed
    }
}
```

---

## Why the `when(playback)` branching is NOT extracted

Both components share the same structural skeleton:

```
compile(code) → pattern
when (playback) {
    null  → getPlayer, playStrudel, start, setupSignals
    else  → updatePattern
}
```

But the differences require too many callbacks to make extraction worthwhile:

| Aspect        | CodeSongPage                                               | PlayableCodeExample                 |
|---------------|------------------------------------------------------------|-------------------------------------|
| Compile fn    | `StrudelPattern.compileRaw`                                | `StrudelPattern.compile`            |
| Start options | `Options(cyclesPerSecond = cps)`                           | `start()` no-arg                    |
| Signals       | `VoicesScheduled`, `PreloadingSamples`, `SamplesPreloaded` | `VoicesScheduled`, `CycleCompleted` |
| Extra state   | —                                                          | `playingCode`, `currentCycle`       |

A helper with all these as lambdas would be harder to read than the original code.
If a third component appears with the same structure, revisit.

---

## Files Summary

| Action     | File                                                                                      |
|------------|-------------------------------------------------------------------------------------------|
| **Create** | `src/jsMain/kotlin/comp/editor_helpers.kt`                                                |
| **Modify** | `src/jsMain/kotlin/pages/CodeSongPage.kt` — delete method, add imports, wrap catch blocks |
| **Modify** | `src/jsMain/kotlin/comp/PlayableCodeExample.kt` — delete method, wrap catch block         |

---

## What stays in each component (unchanged)

- `editorRef` and `highlightBuffer` fields.
- `when(playback)` branching and all compile/playback/signal logic.
- CPS / title / stream persistence (`CodeSongPage`-specific).
- Cycle counter and `playingCode` (`PlayableCodeExample`-specific).
