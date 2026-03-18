# KlangScript Documentation & REPL

## Goal

Create interactive documentation for KlangScript with runnable code examples and a REPL,
living in a dedicated module that doesn't depend on strudel or audio.

## Current Dependency Analysis

The CodeMirror editor files in `src/jsMain/kotlin/codemirror/` import from:

| Dependency        | Used By                                                        | Purpose                                              |
|-------------------|----------------------------------------------------------------|------------------------------------------------------|
| `klangscript`     | EditorDocContext, DslCompletionSource, ArgFinder               | Parser, docs registry, symbols                       |
| `klangui`         | CodeMirrorTheme, DslEditorExtension, ArgFinder, CodeMirrorComp | KlangTheme, hover popup, tool context, tool registry |
| `audio_bridge`    | CodeMirrorHighlightBuffer, CodeMirrorComp                      | Playback highlight events                            |
| `kraft` / `ultra` | All                                                            | UI framework                                         |
| CodeMirror npm    | ext/*.kt                                                       | Editor bindings                                      |

### What's audio/strudel-specific vs generic

| File                             | Audio/Strudel Deps                                  | Can move to klangscript-ui?      |
|----------------------------------|-----------------------------------------------------|----------------------------------|
| `ext/*.kt` (CodeMirror bindings) | none                                                | yes                              |
| `CodeMirrorTheme.kt`             | KlangTheme (klangui)                                | yes                              |
| `EditorDocContext.kt`            | none (klangscript only)                             | yes                              |
| `DslCompletionSource.kt`         | none (klangscript only)                             | yes                              |
| `DslEditorExtension.kt`          | KlangTheme, hover popup, tool context (all klangui) | yes                              |
| `ArgFinder.kt`                   | KlangUiTool, KlangUiToolRegistry (klangui)          | yes                              |
| `CodeMirrorHighlightBuffer.kt`   | KlangPlaybackSignal (audio_bridge)                  | **no** — audio specific          |
| `CodeMirrorComp.kt`              | audio_bridge for highlights                         | split: editor yes, highlights no |

## Proposed Module Structure

### New module: `klangscript-ui` (JS only)

```
klangscript-ui/
├── build.gradle.kts
└── src/jsMain/kotlin/io/peekandpoke/klang/scriptui/
    ├── editor/
    │   ├── KlangScriptEditorComp.kt      ← full editor (hover, completion, tool badges)
    │   ├── EditorDocContext.kt            ← moved from main
    │   ├── DslCompletionSource.kt         ← moved from main
    │   ├── DslEditorExtension.kt          ← moved from main (hover + tool badges)
    │   ├── ArgFinder.kt                   ← moved from main
    │   ├── CodeMirrorTheme.kt             ← moved from main
    │   └── EditorError.kt                 ← moved from main
    ├── ext/                               ← CodeMirror external declarations
    │   ├── Autocomplete.kt
    │   ├── BasicSetup.kt
    │   ├── Commands.kt
    │   ├── Language.kt
    │   ├── Lint.kt
    │   └── View.kt
    ├── repl/
    │   ├── KlangScriptReplComp.kt         ← editor + output panel + run button
    │   └── ReplOutputPanel.kt             ← console.log output display
    └── docs/
        ├── KlangScriptDocsPage.kt         ← documentation page with sections
        ├── DocSection.kt                  ← section model (title, description, examples)
        └── content/
            ├── VariablesDocs.kt           ← example content: variables & literals
            ├── OperatorsDocs.kt
            ├── ControlFlowDocs.kt
            ├── FunctionsDocs.kt
            ├── ArraysDocs.kt
            ├── ObjectsDocs.kt
            └── StringsDocs.kt
```

### Dependencies

```
klangscript-ui
├── klangscript          (parser, docs registry, engine, symbols)
├── klangui              (KlangTheme only — for dark theme styling)
├── kraft / ultra         (UI framework)
└── codemirror npm pkgs  (editor)
```

Does NOT depend on: `strudel`, `audio_bridge`, `audio_be`, `audio_fe`, `audio_jsworklet`

### What stays in the main `klang` module

```
src/jsMain/kotlin/codemirror/
├── KlangEditorComp.kt              ← extends KlangScriptEditorComp with:
│                                       - playback highlight buffer
├── CodeMirrorHighlightBuffer.kt    ← stays (audio_bridge dependency)
```

The main app's editor component wraps `klangscript-ui`'s editor and adds
only the audio playback highlight buffer on top. Everything else (hover docs,
tool badges, arg finder, completion) lives in `klangscript-ui` since `klangui`
is already a dependency.

## REPL Component Design

```
┌─────────────────────────────────────┐
│ KlangScriptReplComp                │
│                                     │
│ ┌─────────────────────────────────┐ │
│ │ KlangScriptEditorComp          │ │
│ │ (code editor with completion)  │ │
│ └─────────────────────────────────┘ │
│ ┌──────┐ ┌───────┐                  │
│ │ Run  │ │ Clear │                  │
│ └──────┘ └───────┘                  │
│ ┌─────────────────────────────────┐ │
│ │ Output Panel                   │ │
│ │ > 42                           │ │
│ │ > "hello world"                │ │
│ │ > [1, 2, 3]                    │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

- Editor uses `KlangScriptEditorComp` with `availableLibraries = [stdlibLib]`
- "Run" executes code via `KlangScriptEngine`, captures `console.log` output
- Output panel shows results and print output
- Errors show inline in editor (existing error display) + in output panel
- No audio, no strudel — pure language playground

## Documentation Page Design

```
┌──────────────────────────────────────────┐
│ KlangScript Documentation                │
│                                          │
│ ┌──────────────────────────────────────┐ │
│ │ Variables & Literals                 │ │
│ │                                      │ │
│ │ Use `let` for mutable variables      │ │
│ │ and `const` for constants.           │ │
│ │                                      │ │
│ │ ┌──────────────────────────────────┐ │ │
│ │ │ let x = 42                      │ │ │
│ │ │ let name = "hello"              │ │ │
│ │ │ const PI = 3.14159              │ │ │
│ │ │ [Run]                [Output: ] │ │ │
│ │ └──────────────────────────────────┘ │ │
│ └──────────────────────────────────────┘ │
│                                          │
│ ┌──────────────────────────────────────┐ │
│ │ Arrow Functions                      │ │
│ │                                      │ │
│ │ Functions are defined with arrow     │ │
│ │ syntax. They capture their scope.    │ │
│ │                                      │ │
│ │ ┌──────────────────────────────────┐ │ │
│ │ │ let add = (a, b) => a + b       │ │ │
│ │ │ console.log(add(2, 3))          │ │ │
│ │ │ [Run]            [Output: 5]    │ │ │
│ │ └──────────────────────────────────┘ │ │
│ └──────────────────────────────────────┘ │
│                                          │
│ ... more sections ...                    │
└──────────────────────────────────────────┘
```

Each section is a `DocSection` data class:

```kotlin
data class DocSection(
    val title: String,
    val description: String,       // brief text
    val examples: List<DocExample>,
)

data class DocExample(
    val code: String,
    val description: String? = null,  // optional per-example note
)
```

## Implementation Plan

### Step 1: Create `klangscript-ui` module

- `build.gradle.kts` with dependencies on `klangscript`, `klangui`, `kraft`, codemirror npm
- Move CodeMirror ext/ bindings
- Move `CodeMirrorTheme.kt`, `EditorDocContext.kt`, `DslCompletionSource.kt`, `EditorError.kt`

### Step 2: Create lean `KlangScriptEditorComp`

- Editor with code completion + hover docs + error display
- No audio highlight buffer, no tool badges, no tool context
- Optional `hoverPopup` and `popups` (for hover docs)

### Step 3: Adapt main app's editor

- Main app's `KlangEditorComp` wraps `klangscript-ui`'s editor
- Adds playback highlight buffer + tool badge overlay on top
- `ArgFinder` stays in main app

### Step 4: Create `KlangScriptReplComp`

- Editor + Run button + output panel
- Captures `console.log` by injecting a custom console into the engine
- Shows last expression value as result

### Step 5: Create `KlangScriptDocsPage`

- Scrollable page with `DocSection` components
- Each section has text + embedded REPL instances
- Content files define the examples per topic

### Step 6: Wire into app navigation

- Add route for `/docs/klangscript`
- Link from sidebar or docs page

## Key Files

| File                                       | Module         | Purpose                       |
|--------------------------------------------|----------------|-------------------------------|
| `klangscript-ui/build.gradle.kts`          | klangscript-ui | Module setup                  |
| `scriptui/editor/KlangScriptEditorComp.kt` | klangscript-ui | Lean editor                   |
| `scriptui/repl/KlangScriptReplComp.kt`     | klangscript-ui | REPL component                |
| `scriptui/docs/KlangScriptDocsPage.kt`     | klangscript-ui | Docs page                     |
| `scriptui/docs/content/*.kt`               | klangscript-ui | Example content               |
| `codemirror/KlangEditorComp.kt`            | klang (main)   | Audio-enriched editor wrapper |

## Open Questions

- Should the REPL support importing strudel? If yes, the REPL needs to be
  configurable with `availableLibraries` (already planned in the editor props).
  The docs page would use `[stdlibLib]` only, but a standalone REPL page could
  accept `[stdlibLib, strudelLib]`.
- Should the docs page be static markdown rendered at build time, or dynamic
  Kraft components? Dynamic is more flexible (runnable examples), but static
  is easier to author. Recommendation: dynamic Kraft components with content
  defined in Kotlin data classes.
