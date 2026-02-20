Here is a comprehensive plan to implement error displaying using CodeMirror's native capabilities.

### Overall Design

We will leverage CodeMirror's **Lint** extension (`@codemirror/lint`). This is the standard way to display code errors
in CodeMirror 6.

* **Visuals**: It automatically provides the "red wiggly line" (squiggles) under the error range.
* **Messages**: It supports hover tooltips (when you mouse over the error) and can even show a panel list of errors.
  This answers your question: **Yes, CodeMirror can show the error message directly; you do not need a custom div.**

We will distinguish between the **HighlightBuffer** (used for playback visualization) and the **Lint System** (used for
static/compilation errors). They will coexist independently.

### Implementation Plan

#### Phase 1: dependencies

Ensure the linting package is available.

* **Action**: Add `npm("@codemirror/lint", "6.x.x")` to the `jsMain` dependencies in `build.gradle.kts` if it's not
  already transitively included by `basicSetup`. Even if it is, adding it explicitly allows us to import
  `setDiagnostics`.

#### Phase 2: Define Error Data Model

We need a structure to pass errors from the business logic (`CodeSongPage`) to the UI component (`CodeMirrorComp`).

* **Action**: Create a simple data class, e.g., `ScriptError`.

```kotlin
data class ScriptError(
    val message: String,
    val line: Int,      // 1-based
    val column: Int?,   // 1-based, nullable
    val length: Int? = null
)
```

#### Phase 3: Update `CodeMirrorComp`

We need to upgrade the component to accept errors and translate them into CodeMirror `Diagnostics`.

1. **Update Props**: Add `errors: List<ScriptError>` to `CodeMirrorComp.Props`.
2. **Import Lint Features**: Use `setDiagnostics` from `@codemirror/lint`. You might need to use `kotlinext.js.require`
   or `dynamic` if type wrappers are missing.
3. **Handle Updates**: In the component's update lifecycle (or a specific `useEffect` equivalent):
    * Iterate through the `props.errors`.
    * Convert `ScriptError` (line/col) to CodeMirror `Diagnostic` (from/to indices). Use `state.doc.line(n)` to
      calculate offsets.
    * Dispatch a transaction using `setDiagnostics(state, diagnostics)`.

#### Phase 4: Error Handling in `CodeSongPage`

This is where we capture the errors.

1. **State Management**: Add a state variable `compilationErrors` (List of `ScriptError`) to `CodeSongPage`.
2. **Capture Logic**: Wrap the `StrudelPattern.compileRaw(code)` call in `onPlay()` with a `try-catch` block.
3. **Exception Parsing**:
    * **KlangScript Errors**: Catch `ParseException` (or the specific parser exception class). Extract `line`, `column`,
      and `message`.
    * **Mini-notation Errors**: These likely occur during the runtime evaluation of the script (e.g., inside a `note()`
      function call). Catch `RuntimeException` (or the specific runtime exception class).
        * *Strategy*: The interpreter should provide a stack trace or a "caused by" location. If the error comes from
          the mini-notation parser, wrap it in a `ScriptError` using the location of the *calling* function (e.g., where
          `note(...)` was called).
4. **Clear Errors**: Ensure errors are cleared (`compilationErrors = emptyList()`) whenever the user edits the code (in
   `onCodeChanged`) or successfully compiles.

### Plan for the Coding Agent

Please hand over the following instructions to the coding agent:

---

**Task: Implement Error Display in CodeSongPage using CodeMirror Lint**

**Context**: We need to show compilation and runtime errors (KlangScript parsing + Mini-notation parsing) in the editor
with red squiggly lines and tooltips.

**Step 1: Dependencies**
Check `build.gradle.kts` in `jsMain`. If `@codemirror/lint` is missing, add it:
`api(Deps.Npm("codemirror/lint", "6.4.2"))` (or compatible version).

**Step 2: Create Data Model**
In `CodeMirrorComp.kt` (or a shared file), define:

```kotlin
data class EditorError(
    val message: String,
    val line: Int, // 1-based
    val col: Int,  // 1-based
    val len: Int = 1
)
```

**Step 3: Update CodeMirrorComp**
Modify `CodeMirrorComp.kt`:

1. Add `errors: List<EditorError>` to `Props`.
2. Implement a method `updateDiagnostics(errors: List<EditorError>)`.
    * Use `state.doc.line(line)` to convert 1-based line/col to 0-based `from` and `to` indices.
    * Construct JS objects matching the CodeMirror `Diagnostic` interface: `{ from, to, severity: "error", message }`.
    * Use `setDiagnostics` from `@codemirror/lint` to dispatch the update.
    * *Note*: Since wrappers might be partial, use `dynamic` for `setDiagnostics` if needed:
      `val setDiagnostics = require("@codemirror/lint").setDiagnostics`.
3. Call `updateDiagnostics` whenever `props.errors` changes.

**Step 4: Update CodeSongPage Logic**
Modify `CodeSongPage.kt`:

1. Add state: `private var errors by value(emptyList<EditorError>())`.
2. In `onCodeChanged`, clear the errors: `errors = emptyList()`.
3. In `onPlay`, wrap the compilation logic:

```kotlin
try {
    val pattern = StrudelPattern.compileRaw(code)
    // ... success logic ...
    errors = emptyList() // Clear on success
} catch (e: Throwable) {
    // ... error mapping logic ...
    errors = listOf(mapToEditorError(e))
}
```

4. Implement `mapToEditorError(e: Throwable): EditorError`.
    * Inspect `e` to find line/column numbers.
    * If it's a **KlangScript parse error**, use the direct location.
    * If it's a **Runtime/Mini-notation error**, try to extract the location from the interpreter's error info (e.g.,
      the statement that triggered the mini-notation parse).
    * Fallback: If no location is found, map it to line 1 with the error message.

**Step 5: Pass Props**
Add another highlighter class. But one dedicated for information code highlights, like errors.
