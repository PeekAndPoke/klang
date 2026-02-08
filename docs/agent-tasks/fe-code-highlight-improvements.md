Request:

Hi! Please look at DashboardPage. Here we are getting "signals" from the audio frontend (StrudelPlayback in this case).

I am especially interested in this part:

```kotlin
song?.signals?.on<KlangPlaybackSignal.VoicesScheduled> { signal ->
    signal.voices.forEach { voiceEvent ->
        scheduleHighlight(voiceEvent)
    }
}
```

We have to be a bit careful here to not overload the code editor with too many highlight.

So what I want to implement is the following.

1. We extract the logic of `scheduleHighlight()` into a CodeHighlightingBuffer (or similar name)

2. this buffer keeps track of all the scheduled highlights.

3. When it detects, that a specific highlight (code locations) does already exists, it will first remove the existing
   one from the editor and then create a new one.

4. It will also have a configurable max refresh rate per code location (default 16 per second per location)

5. It will also have a configurable max simultaneous highlights (don't know what a good number is)

6. When a song stops, we call cleanup on the backend. We also need to remove all future scheduled highlights, so that
   the highlighting in the code stops as well.

7. Currently the highlighting is done through css classes defined in index.html . They all have the same duration. We
   need a clever way to adjust the highlight duration to the actual duration of the voice the is played.

------------------------------------------------------------------------------------------------------------------------




Here is a detailed, self-contained implementation plan ready to hand over to Claude Code.

---

## Implementation Plan: `CodeHighlightBuffer`

### Goal

Extract all highlight scheduling logic from `DashboardPage.scheduleHighlight()` into a new `CodeHighlightBuffer` class
that handles deduplication, rate-limiting, concurrency capping, clean cancellation, and dynamic highlight durations.

---

### Files to modify or create

| File                                                  | Action     |
|-------------------------------------------------------|------------|
| `src/jsMain/kotlin/codemirror/CodeHighlightBuffer.kt` | **CREATE** |
| `src/jsMain/kotlin/codemirror/CodeMirrorComp.kt`      | **MODIFY** |
| `src/jsMain/kotlin/pages/DashboardPage.kt`            | **MODIFY** |
| `src/jsMain/resources/index.html`                     | **MODIFY** |

---

### Step 1: Create `CodeHighlightBuffer.kt`

**File:** `src/jsMain/kotlin/codemirror/CodeHighlightBuffer.kt`

**Package:** `io.peekandpoke.klang.codemirror`

#### 1.1 Class signature

```kotlin
class CodeHighlightBuffer(
    private val editorRef: ComponentRef.Tracker<CodeMirrorComp>,
    private val maxRefreshRatePerLocation: Int = 16,
    private val maxSimultaneousHighlights: Int = 64,
)
```

Required imports:

- `de.peekandpoke.kraft.components.ComponentRef`
- `de.peekandpoke.kraft.utils.setTimeout`
- `io.peekandpoke.klang.audio_engine.KlangPlaybackSignal`
- `io.peekandpoke.klang.script.ast.SourceLocation`
- `io.peekandpoke.klang.script.ast.SourceLocationChain`
- `kotlin.js.Date`
- `kotlinx.browser.window`

#### 1.2 Location key

Create a **private helper** that converts a `SourceLocation` to a dedup key string:

```kotlin
private fun SourceLocation.toKey(): String = "$startLine:$startColumn:$endLine:$endColumn"
```

#### 1.3 Internal data class

```kotlin
private data class ActiveHighlight(
    val locationKey: String,
    val highlightId: String,
    val startTimeoutId: Int?,
    val removeTimeoutId: Int?,
)
```

#### 1.4 Internal state

```kotlin
// Location key → currently active highlight
private val activeHighlights = mutableMapOf<String, ActiveHighlight>()

// All setTimeout IDs for bulk cancellation
private val pendingTimeouts = mutableSetOf<Int>()

// Location key → last time (Date.now()) a highlight was applied
private val lastHighlightTime = mutableMapOf<String, Double>()

// Computed minimum interval between highlights for the same location
private val minIntervalMs: Double get() = 1000.0 / maxRefreshRatePerLocation
```

#### 1.5 Public method: `scheduleHighlight(event)`

```kotlin
fun scheduleHighlight(event: KlangPlaybackSignal.VoicesScheduled.VoiceEvent)
```

Implementation:

1. Cast `event.sourceLocations` to `SourceLocationChain`. If not a `SourceLocationChain`, return early.
2. Iterate over `chain.locations`. For each `SourceLocation`, call a private `scheduleForLocation(location, event)`.

#### 1.6 Private method: `scheduleForLocation(location, event)`

```kotlin
private fun scheduleForLocation(
    location: SourceLocation,
    event: KlangPlaybackSignal.VoicesScheduled.VoiceEvent,
)
```

Implementation steps:

1. **Compute location key:** `val key = location.toKey()`

2. **Compute timing:**

```kotlin
val now = Date.now()
val startFromNowMs = maxOf(1.0, (event.startTimeMs - now) - 25.0)
val durationMs = maxOf(100.0, minOf(2000.0, event.endTimeMs - event.startTimeMs))
```

The `durationMs` is the voice duration, clamped to `[100, 2000]` ms.

3. **Rate-limit check:** Check `lastHighlightTime[key]`. If `(now + startFromNowMs) - lastTime < minIntervalMs`, skip
   this location (return early). The check uses the *projected highlight time* (`now + startFromNowMs`), not the current
   time, because the highlight is scheduled in the future.

4. **Max simultaneous check:** If `activeHighlights.size >= maxSimultaneousHighlights`, return early (drop).

5. **Schedule the start** using `setTimeout(startFromNowMs.toInt())`:

   Inside the timeout callback:

   a. **Deduplication:** Check `activeHighlights[key]`. If an entry exists:
    - Cancel its `removeTimeoutId` via `window.clearTimeout(...)` and remove from `pendingTimeouts`.
    - Call `editorRef { it.removeHighlight(existing.highlightId) }`.
    - Remove the entry from `activeHighlights`.

   b. **Add highlight:**

```kotlin
editorRef { editor ->
    val highlightId = editor.addHighlight(
        line = location.startLine,
        column = location.startColumn,
        length = if (location.startLine == location.endLine) {
            location.endColumn - location.startColumn
        } else {
            2 // multiline fallback, matches current behavior
        },
        durationMs = durationMs,
    )
```

      If `highlightId` is empty (editor returned ""), clean up and return.

c. **Record last highlight time:** `lastHighlightTime[key] = Date.now()`

d. **Schedule removal** using `setTimeout((durationMs + 50).toInt())` (the `+50` gives a small buffer after the
animation ends):

- Inside: call `editorRef { it.removeHighlight(highlightId) }`, remove from `activeHighlights`, remove timeout from
  `pendingTimeouts`.
- Store the removal timeout ID.

e. **Store active highlight:**

```kotlin
activeHighlights[key] = ActiveHighlight(
    locationKey = key,
    highlightId = highlightId,
    startTimeoutId = startTimeoutId, // the outer timeout
    removeTimeoutId = removeTimeoutId,
)
```

f. **Track timeouts:** Add both the start timeout ID and the removal timeout ID to `pendingTimeouts`.

6. **Track the start timeout:** `pendingTimeouts.add(startTimeoutId)`

**Important note on `setTimeout` return values:** In Kotlin/JS, `kotlinx.browser.window.setTimeout(handler, timeout)`
returns `Int`. The Kraft `setTimeout` utility may not return the ID. Use `window.setTimeout(...)` directly so you can
capture the returned `Int` ID for cancellation. Check the Kraft `setTimeout` implementation — if it returns `Int`, use
it; otherwise use `window.setTimeout` from `kotlinx.browser.window`.

#### 1.7 Public method: `cancelAll()`

```kotlin
fun cancelAll() {
    // 1. Cancel all pending timeouts
    for (id in pendingTimeouts) {
        window.clearTimeout(id)
    }
    pendingTimeouts.clear()

    // 2. Clear all active highlights from editor
    editorRef { it.clearHighlights() }

    // 3. Reset state
    activeHighlights.clear()
    lastHighlightTime.clear()
}
```

---

### Step 2: Modify `CodeMirrorComp.kt`

**File:** `src/jsMain/kotlin/codemirror/CodeMirrorComp.kt`

#### 2.1 Change `addHighlight` signature

Add an optional `durationMs` parameter:

```kotlin
fun addHighlight(line: Int, column: Int, length: Int, durationMs: Double = 300.0): String
```

#### 2.2 Set inline `animation-duration` on the decoration

Inside the `addHighlight` method, where `Decoration.mark(...)` is called in `createHighlightExtension`, the CSS class
`cm-highlight-playing` is applied. We need the animation duration to be dynamic.

**Approach:** Pass the `durationMs` as part of `HighlightRange`, then use it when creating the decoration mark.

**2.2.1 Update `HighlightRange`:**

```kotlin
data class HighlightRange(
    val from: Int,
    val to: Int,
    val id: String,
    val durationMs: Double = 300.0,
)
```

**2.2.2 Update the `addHighlight` method** to pass `durationMs` when creating `HighlightRange`:

```kotlin
val range = HighlightRange(from, to, highlightId, durationMs)
```

**2.2.3 Update `createHighlightExtension`** — in the `addHighlightEffect` handler, where `Decoration.mark(...)` is
called, add a `style` attribute:

Find this block inside the `addHighlightEffect` handler:

```kotlin
this.`class` = "cm-highlight-playing"
this.attributes = jsObject {
    this.`data-highlight-id` = range.id
}
```

Change it to:

```kotlin
this.`class` = "cm-highlight-playing"
this.attributes = jsObject {
    this.`data-highlight-id` = range.id
    this.style = "animation-duration: ${range.durationMs}ms"
}
```

This works because CodeMirror's `Decoration.mark` applies `attributes` directly to the generated `<span>` element. The
inline `style` attribute will override the CSS rule's `animation` shorthand duration, letting each highlight pulse for
exactly its voice duration.

---

### Step 3: Modify `index.html`

**File:** `src/jsMain/resources/index.html`

Change the `.cm-highlight-playing` rule to **not** specify a duration in the shorthand, so the inline style takes
precedence:

```css
/* CodeMirror live code highlighting */
.cm-highlight-playing {
    background-color: rgba(255, 100, 0, 0.0);
    border: 2px solid rgba(255, 100, 0, 0.0);
    border-radius: 3px;
    animation-name: pulse;
    animation-timing-function: ease-out;
    animation-fill-mode: forwards;
    /* duration comes from inline style via CodeMirrorComp */
    padding: 1px;
    margin: -3px;
}
```

Remove the `animation: pulse 0.3s ease-out;` shorthand and replace with the longhand properties shown above (
`animation-name`, `animation-timing-function`, `animation-fill-mode`). The `animation-duration` is intentionally omitted
here — it will come from the inline `style` attribute set by `CodeMirrorComp`.

The `@keyframes pulse` block stays exactly as-is.

---

### Step 4: Modify `DashboardPage.kt`

**File:** `src/jsMain/kotlin/pages/DashboardPage.kt`

#### 4.1 Remove the `scheduleHighlight` method entirely

Delete the entire `private fun scheduleHighlight(event: ...)` method.

#### 4.2 Add `highlightBuffer` field

In the state section (near `editorRef`), add:

```kotlin
val highlightBuffer = CodeHighlightBuffer(editorRef)
```

Add import: `import io.peekandpoke.klang.codemirror.CodeHighlightBuffer`

#### 4.3 Update signal wiring in `onPlay()`

Replace:

```kotlin
song?.signals?.on<KlangPlaybackSignal.VoicesScheduled> { signal ->
    signal.voices.forEach { voiceEvent ->
        scheduleHighlight(voiceEvent)
    }
}
```

With:

```kotlin
song?.signals?.on<KlangPlaybackSignal.VoicesScheduled> { signal ->
    signal.voices.forEach { voiceEvent ->
        highlightBuffer.scheduleHighlight(voiceEvent)
    }
}
```

#### 4.4 Add cleanup on stop

Find the stop button's `onClick` handler (the one that calls `song?.stop()` and sets `song = null`). Add
`highlightBuffer.cancelAll()` **before** setting `song = null`:

```kotlin
onClick {
    song?.stop()
    highlightBuffer.cancelAll()
    song = null
}
```

Also check if there is a pause button or any other place where playback stops — apply the same
`highlightBuffer.cancelAll()` there.

---

### Verification checklist

After implementation, verify:

1. **Basic highlighting works:** Play a simple pattern like `sound("bd hh sd oh")`. Each token should flash in the
   editor at the right time.

2. **Dynamic duration:** A short note (e.g., at high CPS) should have a short pulse animation. A long pad should have a
   visibly longer glow. Verify by inspecting the `<span>` element's `style` attribute in browser DevTools.

3. **Deduplication:** Play a pattern where the same token triggers rapidly (e.g., fast `sound("bd")`). The editor should
   not stack multiple overlapping highlights on the same span — the old one should be removed before the new one is
   added.

4. **Rate limiting:** Set CPS very high (e.g., 4.0). The highlight buffer should cap at 16 updates/sec per location.
   Verify no visual stuttering or console errors.

5. **Max simultaneous cap:** Play a complex polyphonic pattern. Open DevTools console and verify no runaway
   memory/timeout accumulation. The total active highlight count should stay ≤ 64.

6. **Clean stop:** Press Stop. All highlights should disappear instantly. No more highlights should appear after
   stopping (no lingering scheduled timeouts).

7. **Restart:** After stopping, pressing Play again should work normally with fresh highlights.

---

### Notes for the implementer

- **`window.setTimeout` vs Kraft `setTimeout`:** The Kraft utility `de.peekandpoke.kraft.utils.setTimeout` likely wraps
  `window.setTimeout` but may not return the timeout ID. You need the ID for cancellation. Check the Kraft source. If it
  doesn't return `Int`, use `kotlinx.browser.window.setTimeout(handler, timeout)` directly in the buffer class.

- **Thread safety:** This is all single-threaded JS. No synchronization needed. The signal callbacks from
  `StrudelPlayback` are dispatched on the `callbackDispatcher` which runs on the main JS thread.

- **The `+50ms` buffer on removal:** The removal timeout is `durationMs + 50` so the CSS animation finishes completely
  before the decoration is removed from CodeMirror's state. Without this, the animation can be cut short.

- **Rate limit uses projected time:** The rate limit check compares `(now + startFromNowMs)` against the last highlight
  time, not `now` itself. This is because events arrive in batches ahead of time. Without this, a batch of
  future-scheduled events for the same location would all pass the rate limit check (since `now` hasn't changed) but
  then fire too rapidly.
