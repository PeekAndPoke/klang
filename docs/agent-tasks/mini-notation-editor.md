# Mini-Notation Point-and-Click Editor

> **Status**: Planned
> **Author**: Claude + user
> **Date**: 2026-03-06

---

## 1. What Is This?

A visual, point-and-click editor for mini-notation pattern strings (e.g. `"bd sd [hh cp] ~"`).
It is a `KlangUiTool` modal — the same pattern as `StrudelAdsrEditorTool` and
`StrudelScaleEditorTool`. Right-clicking a pattern argument in the code editor opens the
mini-notation editor instead of making the user type raw syntax.

**Key design insight — composable shell**: the mini-notation editor is a *container*.
Each atom inside the pattern may itself be a structured value (ADSR string, scale name, …).
When an atom chip is clicked, the editor delegates to whatever *sub-tool* is registered for
that parameter. Example:

```
adsr("0.01:0.1:0.8:0.3  0.05:0.2:0.7:0.2")
      └──── atom 1 ────┘  └──── atom 2 ────┘
            ↓ click "edit"        ↓ click "edit"
     Opens ADSR editor    Opens ADSR editor
```

The mini-notation editor manages sequence structure; the sub-tool manages atom content.

---

## 2. Parser Rework — Two-Phase Pipeline

The current `MiniNotationParser` builds `StrudelPattern` objects *directly* during parsing
(recursive descent with inline pattern construction). This will be refactored into a clean
two-phase pipeline so that the intermediate structure can be shared with the visual editor.

```
String
  │
  ▼  Phase 1 — syntax only (MiniNotationParser reworked)
MnPattern  ◄──────────────────────────── used by visual editor
  │
  ▼  Phase 2 — semantics (new MnPatternToStrudelPattern)
StrudelPattern  ◄──────────────────────── used by runtime
```

### Why this matters

- The editor can parse, display, and round-trip patterns **without** any strudel runtime dependency.
- Source-location information (for live code highlighting) is stored on `MnNode.Atom` nodes
  and passed through to the strudel patterns in phase 2 — no regression in highlight behaviour.
- All existing `MiniNotationParser` tests become phase-1 + phase-2 integration tests and must
  continue to pass.

---

## 3. Data Model — `MnNode`

**New file**: `strudel/src/commonMain/kotlin/lang/parser/MnNode.kt`

Source positions are carried on atoms so phase 2 can attach `SourceLocation` to the
resulting `AtomicPattern` nodes (preserving live highlighting).

```kotlin
sealed class MnNode {

    /** A single literal step with optional modifiers. */
    data class Atom(
        val value: String,
        val sourceRange: IntRange? = null,  // char positions in the original string
        val multiplier: Int? = null,  // *n
        val divisor: Int? = null,  // /n
        val weight: Double? = null,  // @n
        val probability: Double? = null,  // ? or ?0.5
        val euclidean: Euclidean? = null,  // (pulses, steps[, rotation])
    ) : MnNode()

    data class Euclidean(val pulses: Int, val steps: Int, val rotation: Int = 0)

    /** Bracketed sub-sequence `[ children ]` with optional group-level modifiers. */
    data class Group(
        val items: List<MnNode>,
        val multiplier: Int? = null,
        val divisor: Int? = null,
        val weight: Double? = null,
    ) : MnNode()

    /** Cycle alternation `< a b c >` — one item per cycle. */
    data class Alternation(val items: List<MnNode>) : MnNode()

    /** Simultaneous stack `a, b` — each layer runs a full cycle. */
    data class Stack(val layers: List<List<MnNode>>) : MnNode()

    /** Silence `~`. */
    object Rest : MnNode()
}

/** Top-level sequence — the root of a parsed pattern string. */
data class MnPattern(val items: List<MnNode>)
```

---

## 4. Phase 1 — `MiniNotationParser` Rework

**Rework existing file**: `strudel/src/commonMain/kotlin/lang/parser/MiniNotationParser.kt`

The tokeniser (`tokenize()`) stays unchanged. The recursive descent methods currently build
`StrudelPattern` objects inline — they are rewritten to build `MnNode` trees instead.

New public API:

```kotlin
object MiniNotationParser {
    /** Phase 1: string → intermediate tree. Pure syntax, no strudel dependency. */
    fun parse(input: String): MnPattern

    /** Phase 1 + 2 combined: backward-compatible entry point. */
    fun <T> parse(
        input: String,
        atomFactory: (String, SourceLocation?) -> T,
        baseLocation: SourceLocation? = null,
    ): StrudelPattern  // delegates to parse(input) then MnPatternToStrudelPattern.convert(…)
}
```

The existing call sites that use the two-argument form keep working unchanged.

---

## 5. Phase 2 — `MnPatternToStrudelPattern`

**New file**: `strudel/src/commonMain/kotlin/lang/parser/MnPatternToStrudelPattern.kt`

Walks an `MnPattern` tree and produces a `StrudelPattern`, applying the same rules as the
current parser:

| `MnNode`           | `StrudelPattern`                                                 |
|--------------------|------------------------------------------------------------------|
| `Atom`             | `atomFactory(value, location)` wrapped in tempo/weight modifiers |
| `Group`            | `seq(children)` wrapped in tempo/weight modifiers                |
| `Alternation`      | `slow(n, seq(children))`                                         |
| `Stack`            | `stack(layers)`                                                  |
| `Rest`             | `silence`                                                        |
| `Atom.euclidean`   | `EuclideanPattern(atom, pulses, steps, rotation)`                |
| `Atom.probability` | `.degradeBy(p)`                                                  |

Source location: `Atom.sourceRange` is converted to `SourceLocation` using `baseLocation`
offset, then passed to `atomFactory` — identical to current behaviour.

---

## 6. Renderer — `MnRenderer`

**New file**: `strudel/src/commonMain/kotlin/lang/parser/MnRenderer.kt`

Serialises `MnPattern` back to a canonical mini-notation string.

Round-trip invariant: `parse(render(parse(s)))` produces an equal `MnPattern`
(ignoring whitespace normalisation).

---

## 7. Visual Editor — `StrudelMiniNotationEditorTool`

**New file**: `src/jsMain/kotlin/codetools/StrudelMiniNotationEditorTool.kt`

The editor only uses **Phase 1** (`MiniNotationParser.parse(string): MnPattern`) and
`MnRenderer`. It never constructs strudel patterns.

The tool takes an optional **atom tool** for sub-tool delegation:

```kotlin
class StrudelMiniNotationEditorTool(
    val atomTool: KlangUiTool? = null,
) : KlangUiTool { … }
```

### Visual layout

```
┌──────────────┐ ┌──────────────┐ ┌──────────────────────────────────┐ ┌───┐
│ 0.01:0.1:0.8 │ │ 0.05:0.2:0.7 │ │ [  0.01:0.1:0.8   0.1:0.3:0.6  ] │ │ ~ │  [+]
│  ╱╲___        │ │  ╱╲__        │ │   ╱╲___           ╱╲__           │ │   │
│      [edit]   │ │      [edit]  │ │       [edit]          [edit]     │ │   │
└──────────────┘ └──────────────┘ └──────────────────────────────────┘ └───┘

  "0.01:0.1:0.8:0.3 0.05:0.2:0.7:0.2 [0.01:0.1:0.8:0.3 0.1:0.3:0.6:0.4] ~"
```

### Chip types

| Chip                  | Content                             | Interaction                                     |
|-----------------------|-------------------------------------|-------------------------------------------------|
| **Atom (plain)**      | value text + modifier badges        | click → inline text input; [×] delete           |
| **Atom (sub-tool)**   | thumbnail + value + modifier badges | "edit" → opens sub-tool modal for this atom     |
| **Group `[ ]`**       | bordered container with child chips | [+] inside; modifier badges on group            |
| **Alternation `< >`** | bordered container, `<>` label      | [+] inside                                      |
| **Rest `~`**          | `~` pill                            | [×] delete                                      |
| **Add `[+]`**         | end of level                        | appends atom; opens sub-tool immediately if set |

### Atom sub-tool flow

1. User clicks "edit" on an atom chip.
2. A child `KlangUiToolContext` is synthesised:
   `currentValue = "\"${atom.value}\""`, `onCommit = { atom.value = unquote(it); redraw }`
3. Sub-tool modal opens (via existing `CodeToolModal` infrastructure).
4. On commit → atom value updates → rendered string in preview regenerates.

### Modifier popover

Clicking a modifier badge on any chip opens an inline popover with number inputs for
multiplier, divisor, weight, probability, and Euclidean (pulses / steps / rotation).

### Bottom bar

Live rendered string preview · **Cancel** · **Reset** · **Update**
(same commit / cancel pattern as ADSR and Scale editors).

---

## 8. Registration & DSL Integration

### 8.1 Tool variants (`src/jsMain/kotlin/index.kt`)

```kotlin
val plainMnEditor = StrudelMiniNotationEditorTool(atomTool = null)
val adsrMnEditor = StrudelMiniNotationEditorTool(atomTool = StrudelAdsrEditorTool)
val scaleMnEditor = StrudelMiniNotationEditorTool(atomTool = StrudelScaleEditorTool)

KlangUiToolRegistry.register("StrudelMiniNotationEditor", plainMnEditor)
KlangUiToolRegistry.register("StrudelAdsrSequenceEditor", adsrMnEditor)
KlangUiToolRegistry.register("StrudelScaleSequenceEditor", scaleMnEditor)
```

### 8.2 DSL annotations

```
strudel/src/commonMain/kotlin/lang/lang_dynamics.kt
  adsr(params)  →  @param-tool params StrudelAdsrSequenceEditor

strudel/src/commonMain/kotlin/lang/lang_tonal.kt
  scale(name)   →  @param-tool name StrudelScaleSequenceEditor
```

### 8.3 Default tool (Option A — recommended)

Rather than annotating every DSL function, extend the tool system so that any string/pattern
param *without* an explicit `@param-tool` tag offers the plain mini-notation editor by default.

Files:

- `klangui/src/jsMain/kotlin/KlangUiTool.kt` — add `defaultTool` to registry
- `src/jsMain/kotlin/codemirror/DslGoToDocsExtension.kt` — fall back to default tool when
  `param.uitools.isEmpty()` and param type is string/pattern

---

## 9. Key Files

| Role                        | File                                                                     | Status                     |
|-----------------------------|--------------------------------------------------------------------------|----------------------------|
| Data model                  | `strudel/src/commonMain/kotlin/lang/parser/MnNode.kt`                    | new                        |
| Phase 1 (rework)            | `strudel/src/commonMain/kotlin/lang/parser/MiniNotationParser.kt`        | rework                     |
| Phase 2 (new)               | `strudel/src/commonMain/kotlin/lang/parser/MnPatternToStrudelPattern.kt` | new                        |
| Renderer                    | `strudel/src/commonMain/kotlin/lang/parser/MnRenderer.kt`                | new                        |
| Phase 1 tests               | `strudel/src/commonTest/kotlin/lang/parser/MnParserSpec.kt`              | new                        |
| Phase 2 / integration tests | `strudel/src/commonTest/kotlin/lang/parser/MiniNotationParserSpec.kt`    | existing — must still pass |
| UI tool                     | `src/jsMain/kotlin/codetools/StrudelMiniNotationEditorTool.kt`           | new                        |
| Tool registration           | `src/jsMain/kotlin/index.kt`                                             | modify                     |
| Default tool hook           | `klangui/src/jsMain/kotlin/KlangUiTool.kt`                               | modify                     |
| Default tool trigger        | `src/jsMain/kotlin/codemirror/DslGoToDocsExtension.kt`                   | modify                     |
| ADSR annotation             | `strudel/src/commonMain/kotlin/lang/lang_dynamics.kt`                    | modify                     |
| Scale annotation            | `strudel/src/commonMain/kotlin/lang/lang_tonal.kt`                       | modify                     |

---

## 10. Implementation Phases

### Phase 1 — Model + Parser rework + Renderer (commonMain)

- [ ] Define `MnNode` + `MnPattern` in `MnNode.kt`
- [ ] Rework `MiniNotationParser` to build `MnNode` tree in phase 1
- [ ] Add `MnPatternToStrudelPattern` as phase 2 — preserve all current behaviour
- [ ] Keep backward-compatible two-argument `parse()` entry point
- [ ] Write `MnRenderer`
- [ ] Write `MnParserSpec` round-trip tests
- [ ] All existing parser/spec tests still pass: `./gradlew :strudel:jvmTest`

### Phase 2 — Basic UI (flat atoms, sub-tool delegation)

- [ ] `StrudelMiniNotationEditorTool(atomTool)` skeleton (follows ADSR editor pattern)
- [ ] Flat atom chips: plain-text edit, delete, add
- [ ] Sub-tool delegation: "edit" → synthesise `KlangUiToolContext` → open sub-tool modal
- [ ] Bottom bar wired to outer `KlangUiToolContext`
- [ ] Register `StrudelAdsrSequenceEditor`; update `adsr()` annotation; manual smoke test

### Phase 3 — Full UI (groups, alternation, modifiers)

- [ ] Group chip `[ ]` with nested chip list
- [ ] Alternation chip `< >`
- [ ] Modifier badge popover
- [ ] Rest chip `~`
- [ ] Register `StrudelScaleSequenceEditor`; update `scale()` annotation; manual test

### Phase 4 — Default tool integration

- [ ] `KlangUiToolRegistry` default tool slot
- [ ] `DslGoToDocsExtension` fallback
- [ ] Smoke-test with `note()`, `sound()`, `n()`

---

## 11. Verification

```bash
./gradlew :strudel:jvmTest   # all parser tests must pass after rework
```

Manual checks:

1. Existing patterns still play correctly (phase 2 regression test).
2. Right-click `adsr("0.01:0.1:0.8:0.3")` → ADSR sequence editor with one chip.
3. Click "edit" → ADSR editor opens; change; Update → chip updates; outer Update → code changes.
4. Add second atom → `"… …"` in code.
5. Right-click `note("c4 d4 e4")` → three plain chips; add group → `"c4 [d4 e4]"`.
6. Parse generated string → same `MnPattern` as before the edit round-trip.
