# KlangBlocks — Implementation Plan (Take 1, Rev 2)

> **Status**: Design / pre-implementation
> **Author**: Claude + user
> **Date**: 2026-02-26
> **Revision**: 2 — updated with user feedback

---

## 1. What is KlangBlocks?

KlangBlocks is a visual block editor for KlangScript — a custom-built block editor
that replaces the Google Blockly integration. It is a first-class module
(`klangblocks`), a sibling of `klangscript`, with all platform-agnostic logic in
`commonMain` and the web UI in `jsMain` (using Kraft components). Future non-web
frontends (e.g. Compose Multiplatform) would only need to implement the `jsMain`
layer against the same `commonMain` model.

The editor and the text editor in `CodeSongPage` share a single `code: String` state.
Switching between modes converts that string via the parser and code generator.
Because the blocks are a pure, lossless mapping of the code, undo/redo is implemented
by maintaining a stack of code snapshots — no separate command infrastructure needed.

---

## 2. Module Layout

```
klangblocks/
  build.gradle.kts
  src/
    commonMain/kotlin/io/peekandpoke/klang/blocks/
      model/
        KBlock.kt           ← sealed block-model hierarchy
        KSlot.kt            ← KSlotKind sealed class + slot typing
        KProgram.kt         ← top-level program container
        KTypeMapping.kt     ← canonical TypeModel.simpleName ↔ KSlotKind mapping
      transform/
        AstToKBlocks.kt     ← KlangScript AST  →  KBlock model
        KBlocksToCode.kt    ← KBlock model     →  KlangScript String
    commonTest/kotlin/io/peekandpoke/klang/blocks/
      RoundTripTest.kt      ← parse → blocks → code → parse must be stable
      AstToKBlocksTest.kt
      KBlocksToCodeTest.kt

    jsMain/kotlin/io/peekandpoke/klang/blocks/
      ui/
        KlangBlocksEditorComp.kt   ← top-level Kraft component
        KlangBlocksPaletteComp.kt  ← left panel: categories + draggable pills
        KlangBlocksCanvasComp.kt   ← main editing canvas with line numbers
        KlangBlocksChainComp.kt    ← renders one statement row + S-curve wrapping
        KlangBlocksBlockComp.kt    ← renders one block pill
        KlangBlocksSlotComp.kt     ← renders one argument slot
      drag/
        DragState.kt        ← global drag state (what is being dragged)
        DropTarget.kt       ← drop zone model (where it can land)
        DragAndDrop.kt      ← pointer-event machinery (desktop + touch)
```

### `build.gradle.kts` dependencies

```kotlin
// commonMain
api(project(":klangscript"))   // AST types + DslDocsRegistry + TypeModel

// jsMain — Kraft is inherited from the root build; no extra npm deps
```

---

## 3. Type System

### 3.1 The unification problem

There are currently two places that classify KlangScript types:

| Location                                                      | What it does                                   |
|---------------------------------------------------------------|------------------------------------------------|
| `BlockFieldNaming.isNumericType/isBoolType/isPatternLikeType` | Ad-hoc predicates for Blockly                  |
| `ParamModel.type: TypeModel` in DslDocs                       | Structured type from KSP annotation processing |

The KSP processor emits type names derived from Kotlin's reflection:
`"Double"`, `"Float"`, `"Int"`, `"PatternLike"`, `"StrudelPattern"`, `"Boolean"`,
`"String"`, etc.  `PatternLike` is a Kotlin type alias for `Any?` — its simpleName
is literally `"PatternLike"`.

The AST does **not** carry type information — the parser is untyped. Types are
inferred from context: `StringLiteral` → it's a string, `NumberLiteral` → it's
a number, `CallExpression` → it produces a `StrudelPattern` (PatternLike), etc.

**The goal**: one canonical mapping in `klangblocks/commonMain` that both the
slot builder (DslDocs → `KSlot`) and the arg classifier (AST node → `KArgValue`)
use as their single source of truth.

### 3.2 `KSlotKind` — sealed class

```kotlin
/**
 * The kind of value a slot expects.
 * Sealed class (not enum) to support future custom/object type descriptors.
 */
sealed class KSlotKind {

    /** Free-text input; value emitted as a quoted string in generated code. */
    object Str : KSlotKind()

    /** Numeric spinner / text field; value emitted as a number literal. */
    object Num : KSlotKind()

    /** Checkbox; value emitted as `true` or `false`. */
    object Bool : KSlotKind()

    /**
     * Accepts any pattern-like expression: a string literal, a number
     * literal, a nested chain, or a variable identifier.
     * Structural blocks (stack, seq, gap) use this kind for their pockets.
     */
    object PatternLike : KSlotKind()

    /**
     * A known named type that is not one of the above, e.g. a custom
     * data class. Rendered as a text field until a specific renderer
     * is registered for [typeName].
     */
    data class NamedObject(val typeName: String) : KSlotKind()

    /** Type name not recognized at all — fall back to text field. */
    data class Unknown(val typeName: String) : KSlotKind()
}
```

### 3.3 `KTypeMapping` — canonical mapping (`commonMain`)

```kotlin
/**
 * Single source of truth for mapping between:
 *   TypeModel.simpleName (from DslDocs KSP output)
 *   KlangScript AST nodes (implicit types)
 *   KSlotKind (used by the block model and UI)
 */
object KTypeMapping {

    /** Map a DslDocs type name to a KSlotKind. */
    fun slotKindFor(typeName: String): KSlotKind = when (typeName) {
        "String" -> KSlotKind.Str
        "Double", "Float", "Int", "Long",
        "Number" -> KSlotKind.Num
        "Boolean" -> KSlotKind.Bool
        "PatternLike", "StrudelPattern",
        "Pattern" -> KSlotKind.PatternLike
        else -> KSlotKind.NamedObject(typeName)
    }

    /** Infer the KSlotKind of an AST expression (for arg classification). */
    fun slotKindOf(expr: Expression): KSlotKind = when (expr) {
        is StringLiteral -> KSlotKind.Str
        is NumberLiteral -> KSlotKind.Num
        is BooleanLiteral -> KSlotKind.Bool
        // Call expressions produce a pattern — treat as PatternLike
        is CallExpression -> KSlotKind.PatternLike
        is MemberAccess -> KSlotKind.PatternLike
        // Identifier may refer to a variable or a named pattern (sine, etc.)
        is Identifier -> KSlotKind.PatternLike
        // Binary/unary ops — keep as text for now
        else -> KSlotKind.Unknown("expr")
    }

    /**
     * True when a given arg value is compatible with a slot of [kind].
     * PatternLike accepts everything; specific kinds require exact match.
     */
    fun compatible(argValue: KArgValue, kind: KSlotKind): Boolean = when (kind) {
        KSlotKind.PatternLike -> true           // accepts anything
        KSlotKind.Str -> argValue is KStringArg || argValue is KEmptyArg
        KSlotKind.Num -> argValue is KNumberArg || argValue is KEmptyArg
        KSlotKind.Bool -> argValue is KBoolArg || argValue is KEmptyArg
        else -> true           // permissive fallback
    }
}
```

---

## 4. The Block Model (`commonMain`)

### 4.1 Design principle

The block model is a **mutable, ordered representation** of a KlangScript program.
It is not the AST — it is a higher-level view optimised for rendering and editing.
The key constraint is that it must always produce **semantically equivalent code**
when serialised by `KBlocksToCode`, and must faithfully reconstruct from any
KlangScript source via `AstToKBlocks`.

### 4.2 Full type hierarchy

```kotlin
// ─────────────────────────────────────────────────────
// Top-level container
// ─────────────────────────────────────────────────────

/**
 * A KlangBlocks program: an ordered list of statement rows.
 * Order is significant — the same order is used in generated code.
 * Line numbers (1-based) are derived from position in this list.
 */
data class KProgram(
    val statements: MutableList<KStatement> = mutableListOf(),
)

// ─────────────────────────────────────────────────────
// Statements  (one visual row on the canvas)
// ─────────────────────────────────────────────────────

sealed class KStatement {
    /** Stable ID used for diffing and drag references. */
    abstract val id: String
}

/** import * from "strudel"  /  import { x } from "lib"  /  import * as ns from "lib" */
data class KImportStatement(
    override val id: String,
    val libraryName: String,
    val imports: List<Pair<String, String>>? = null,  // null = wildcard
    val namespaceAlias: String? = null,
) : KStatement()

/** let x = <expr> */
data class KLetStatement(
    override val id: String,
    val name: String,
    val value: KArgValue?,    // null when uninitialized
) : KStatement()

/** const x = <expr> */
data class KConstStatement(
    override val id: String,
    val name: String,
    val value: KArgValue,
) : KStatement()

/**
 * The main statement type: a method chain rendered as a row of connected
 * block pills.
 *
 *   sound("bd hh sd")  .gain(0.5)  .speed(2)
 *   └─── head block ──┘└─ block ──┘└─ block ┘
 *
 * The steps list may contain KNewlineHint markers that tell the renderer
 * to draw an S-curve connector and continue on the next visual row.
 * KNewlineHint items are transparent to code generation.
 */
data class KChainStatement(
    override val id: String,
    val steps: MutableList<KChainItem> = mutableListOf(),
) : KStatement()

// ─────────────────────────────────────────────────────
// Chain items
// ─────────────────────────────────────────────────────

sealed class KChainItem

/**
 * One function/method call in a chain.
 *
 * @param funcName     The function name (without dot).
 * @param args         Argument list; length == slotsFor(funcName).size,
 *                     empty slots filled with KEmptyArg.
 * @param isHead       True for the first block (no leading dot in code).
 * @param pocketLayout How PatternLike pocket slots are arranged in the UI.
 *                     AstToKBlocks infers this from whether the original
 *                     source args span multiple lines; can be toggled by user.
 */
data class KCallBlock(
    val id: String,
    val funcName: String,
    val args: MutableList<KArgValue> = mutableListOf(),
    val isHead: Boolean = true,
    val pocketLayout: PocketLayout = PocketLayout.HORIZONTAL,
) : KChainItem()

/**
 * Visual-only marker: draw an S-curve connector here and continue the
 * chain on the next visual row.  No effect on code generation.
 */
data object KNewlineHint : KChainItem()

/** Controls how PatternLike pocket slots are rendered inside a structural block. */
enum class PocketLayout { HORIZONTAL, VERTICAL }

// ─────────────────────────────────────────────────────
// Argument values  (what fills a slot inside a block)
// ─────────────────────────────────────────────────────

sealed class KArgValue

/**
 * Slot is empty.
 * The UI shows [paramName] as a dimmed placeholder.
 * Code generation emits `null` for required slots.
 */
data class KEmptyArg(val paramName: String) : KArgValue()

/** A string literal. Emitted as `"value"` in code. */
data class KStringArg(val value: String) : KArgValue()

/** A numeric literal. Emitted as the number in code. */
data class KNumberArg(val value: Double) : KArgValue()

/** A boolean. Emitted as `true` or `false`. */
data class KBoolArg(val value: Boolean) : KArgValue()

/**
 * A nested chain snapped into a PatternLike pocket.
 * The nested chain does not appear in the program's statement list.
 */
data class KNestedChainArg(val chain: KChainStatement) : KArgValue()

/** A variable reference. Emitted as the bare identifier. */
data class KIdentifierArg(val name: String) : KArgValue()

/**
 * A binary expression: left op right.
 * Emitted as `(left op right)`.
 * The UI renders this as a small inline block with two input sub-slots.
 */
data class KBinaryArg(
    val left: KArgValue,
    val operator: String,   // "+", "-", "*", "/", "%", "==", "!=", etc.
    val right: KArgValue,
) : KArgValue()

/**
 * A unary expression.
 * Emitted as `op operand` (e.g. `-0.5`, `!flag`).
 */
data class KUnaryArg(
    val operator: String,   // "-", "+", "!"
    val operand: KArgValue,
) : KArgValue()

/**
 * An arrow function literal used as a callback argument.
 * The body is stored as a KlangScript source string for v1
 * (no block decomposition of function bodies yet).
 * Rendered as a compact text field.
 */
data class KArrowFunctionArg(
    val params: List<String>,
    val bodySource: String,
) : KArgValue()
```

### 4.3 Slot descriptor (`KSlot.kt`)

```kotlin
data class KSlot(
    val index: Int,
    val paramName: String,
    val kind: KSlotKind,
    val isVararg: Boolean,
    val isOptional: Boolean,  // true for vararg slots beyond index 0
)

/**
 * Derive the slot list for a DSL function from the registry.
 * Vararg params expand to MAX_VARARG_SLOTS slots.
 */
fun slotsFor(
    funcName: String,
    registry: DslDocsRegistry,
    maxVarargSlots: Int = 4,
): List<KSlot>
```

A `KCallBlock`'s `args` list always has the same length as `slotsFor(funcName)`.

---

## 5. Transforms (`commonMain`)

### 5.1 `AstToKBlocks`

```
KlangScriptParser.parse(code) → Program (AST) → AstToKBlocks.convert() → KProgram
```

| AST node                                             | KBlock result                                      |
|------------------------------------------------------|----------------------------------------------------|
| `ImportStatement`                                    | `KImportStatement`                                 |
| `LetDeclaration`                                     | `KLetStatement`                                    |
| `ConstDeclaration`                                   | `KConstStatement`                                  |
| `ExpressionStatement(chain)`                         | `KChainStatement` via left-recursive chain walk    |
| `StringLiteral` / `NumberLiteral` / `BooleanLiteral` | `KStringArg` / `KNumberArg` / `KBoolArg`           |
| `Identifier` in arg                                  | `KIdentifierArg`                                   |
| `CallExpression(Identifier)` in PatternLike pocket   | `KNestedChainArg` with full chain                  |
| `BinaryOperation`                                    | `KBinaryArg` (recursive)                           |
| `UnaryOperation`                                     | `KUnaryArg`                                        |
| `ArrowFunction`                                      | `KArrowFunctionArg(bodySource = exprToText(body))` |

**Pocket layout inference**: for structural functions, check whether the source
locations of the pocket arguments span more than one line.

```
stack(a, b)          → args on same line  → PocketLayout.HORIZONTAL
stack(                → args on separate  → PocketLayout.VERTICAL
  a,
  b,
)
```

`SourceLocation.startLine` on each argument expression is used for this check.

**Empty slot padding**: after extracting real args, pad the arg list with
`KEmptyArg(paramName)` up to the slot count for the function.

### 5.2 `KBlocksToCode`

```
KProgram → KBlocksToCode.generate() → String
```

- `KImportStatement` → `import * from "lib"` / `import { x, y } from "lib"` / `import * as ns from "lib"`
- `KLetStatement` → `let name = value`
- `KConstStatement` → `const name = value`
- `KChainStatement` → `head(args…).step(args…).step(args…)` (one KlangScript expression per chain)
- `KNewlineHint` → silently skipped
- `KEmptyArg` → `null` (required slots); trailing empty vararg slots stripped
- `KNestedChainArg` → recursively generated chain expression
- `KBinaryArg` → `(left op right)`
- `KUnaryArg` → `op operand`
- `KArrowFunctionArg` → `(params) => bodySource` or `param => bodySource` for single param
- `KIdentifierArg` → bare name
- Strings are quoted (`"value"`), numbers unquoted, booleans `true`/`false`

---

## 6. UI Architecture (`jsMain`)

### 6.1 Component tree

```
KlangBlocksEditorComp
  ├─ KlangBlocksPaletteComp   (~220px left panel)
  │    ├─ search field
  │    └─ category accordion
  │         └─ palette block pills (draggable, with hover-tooltip)
  └─ KlangBlocksCanvasComp   (fills remaining space, vertically scrollable)
       ├─ line number gutter  (left margin, 1-based)
       └─ statement rows (one KlangBlocksChainComp per KStatement)
            └─ KlangBlocksBlockComp  (per KCallBlock / special statement)
                 └─ KlangBlocksSlotComp  (per argument slot)
```

### 6.2 `KlangBlocksEditorComp` — state owner

```kotlin
data class Props(
    val code: String,
    val onCodeChanged: OnChange<String>,
)
```

Owns:

- `var program: KProgram` — the live block model
- `var undoStack: ArrayDeque<String>` — code snapshots for undo
- `var redoStack: ArrayDeque<String>` — code snapshots for redo

**Every model mutation** follows this pattern:

```kotlin
fun mutate(action: KProgram.() -> Unit) {
    undoStack.addLast(lastCode)   // snapshot before change
    redoStack.clear()
    program.action()
    val newCode = KBlocksToCode.generate(program)
    lastCode = newCode
    onCodeChanged(newCode)
}
```

**Undo** (Ctrl+Z / two-finger back on touch):

```kotlin
val previous = undoStack.removeLastOrNull() ?: return
redoStack.addLast(lastCode)
program = AstToKBlocks.convert(KlangScriptParser.parse(previous))
lastCode = previous
onCodeChanged(previous)
```

Undo stack depth: cap at 100 entries.

### 6.3 `KlangBlocksPaletteComp`

- Left panel, ~220 px, vertically scrollable.
- Lists all `DslDocsRegistry.categories` as collapsible accordion sections.
- Each function → a compact pill in category colour.
- **Hover tooltip**: shows the function's description and first example from `FunctionDoc`.
- Palette pills are drag sources: dragging creates a `DragSource.PaletteBlock(funcName)`,
  slots pre-filled with `KEmptyArg`.
- Search field at top filters by name / tag / category in real time.
- Category expand/collapse state persisted in `localStorage`.

### 6.4 `KlangBlocksCanvasComp`

- Right panel, fills remaining width, vertically scrollable.
- Shows a **line number gutter** on the left margin (1-based, one number per `KStatement`).
- Renders one `KlangBlocksChainComp` per `KStatement` in program order.
- Drop zone at bottom: dropping a block here appends a new `KChainStatement`.
- Drop zone between rows: inserting a new statement at that position.
- Hosts the **drag ghost overlay** (`position: fixed`, follows pointer).
- Keyboard shortcuts: Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z for undo/redo.
- Touch: two-finger swipe back/forward for undo/redo.

**Dropped blocks snap to the row grid** — there is no free 2D positioning.
A block dropped "somewhere" on the canvas lands at the nearest valid row position.

### 6.5 `KlangBlocksChainComp`

Renders a `KChainStatement` as one or more visual rows of connected pills.

**Normal rendering (no wrapping):**

```
  1 │ [ sound "bd hh sd" ]──[ .gain  0.5 ]──[ .speed  2 ]
```

**Wrapped rendering (KNewlineHint present):**

```
  1 │ [ sound "bd hh sd" ]──[ .gain  0.5 ]──[ .speed  2 ] ↩
    │     [ .slow  2 ]──[ .reverb  0.3 ]
```

The S-curve connector is rendered as an inline SVG element. It starts at the
right edge of the last pill on line 1, arcs downward and to the left, and
arrives at the left edge of the first pill on line 2. The left edge aligns
with the position of the chain's head block (indented by the line-number gutter
width), matching the visual convention of code editors wrapping a long line.

**Inserting a newline hint**: a `⏎` button appears on hover between any two
adjacent blocks. Clicking it inserts a `KNewlineHint` at that position.
The `⏎` icon on the hint itself removes it.

**Row handle**: a `⠿` drag handle on the far left of each statement row allows
reordering statements vertically. Visible always on mobile; hover-revealed on desktop.

**Special statement renderers** (for non-chain statements):

```
  1 │ [ import  *  from  "strudel"    ✎ ]
  2 │ [ let  myVar  =  ⬜ ]
```

### 6.6 `KlangBlocksBlockComp`

Renders a single `KCallBlock` as a styled pill:

```
 ──●  .gain  [ 0.5 ]  ●──
```

- Leading dot for non-head blocks; none for head.
- Background and border in category colour (tinted / semi-transparent).
- `×` remove button on hover (on mobile: long-press reveals it).
- For structural functions with `PocketLayout.HORIZONTAL`:
  pocket slots are rendered inline, side-by-side inside the pill.
- For structural functions with `PocketLayout.VERTICAL`:
  pocket slots are rendered as stacked rows below the function label,
  the pill expands downward.
- A small `⇄` / `⇅` toggle icon appears on structural blocks to switch layout.

### 6.7 `KlangBlocksSlotComp`

Renders one argument slot. Behaviour per kind:

| Kind          | Empty state                          | Filled: text/literal          | Filled: block     |
|---------------|--------------------------------------|-------------------------------|-------------------|
| `Str`         | dimmed `[  paramName  ]`             | `[ "value"  ✎ ]`              | —                 |
| `Num`         | dimmed `[  paramName  ]`             | `[ 0.5      ✎ ]`              | —                 |
| `Bool`        | dimmed `[  paramName  ]`             | `[ ☑ true ]`                  | —                 |
| `PatternLike` | dimmed `[  paramName  ]` (drop zone) | `[ "value"  ✎ ]`              | nested chain pill |
| `KBinaryArg`  | —                                    | `[ lhs op rhs ]` inline block | —                 |
| `KUnaryArg`   | —                                    | `[ op val ]` inline block     | —                 |

**PatternLike slots** can hold either:

- A text/literal value (typed inline) — for functions like `note("c d e f")`
- A nested chain pill — for structural combinators like `stack`

The user can switch between the two modes with a small `T ↔ ⬡` toggle icon on
the slot. The default mode for a PatternLike slot is determined by whether the
slot belongs to a structural-category function (block mode) or not (text mode).

**Inline editing**: clicking a text/number slot opens an `<input>` inline inside
the slot. Enter or blur commits the value. Escape reverts.

---

## 7. Visual Design

### 7.1 Block pill anatomy

```
 ──●  funcName  [ slot1 ]  [ slot2 ]  ●──
```

- `──●` / `●──`: 6 px diameter filled circles on left/right, connected by
  a 1.5 px horizontal rule in the category colour. Implemented as CSS `::before`
  / `::after` pseudo-elements or as a narrow SVG strip beside the pill.
- Pill body: `border-radius: 8px`, 1.5 px solid border in category colour,
  background: category colour at 12–15% opacity (light theme) / 25% (dark).
- Function label: monospace font, bold, same font as the code editor.
- Slots: slightly recessed rounded rectangle insets inside the pill.
- Minimum pill height: 36 px on desktop, 48 px on mobile (touch target).

### 7.2 Category colours (CSS custom properties)

```
synthesis  → --kb-color-synthesis:  hsl(230, 70%, 55%)
sample     → --kb-color-sample:     hsl(160, 60%, 45%)
effects    → --kb-color-effects:    hsl(120, 55%, 45%)
tempo      → --kb-color-tempo:      hsl( 60, 70%, 50%)
structural → --kb-color-structural: hsl(300, 55%, 55%)
random     → --kb-color-random:     hsl( 20, 70%, 55%)
tonal      → --kb-color-tonal:      hsl(260, 65%, 55%)
continuous → --kb-color-continuous: hsl(180, 55%, 45%)
filters    → --kb-color-filters:    hsl( 90, 55%, 45%)
(default)  → --kb-color-default:    hsl(210, 20%, 55%)
```

Colours are defined as CSS custom properties so future `@klangblocks` annotation
overrides can inject per-function overrides without code changes.

### 7.3 S-curve newline connector (SVG)

The S-curve is rendered as an inline SVG absolutely positioned to span from the
right edge of the last pill on row N down to the left edge of the first pill
on row N+1. The curve is a cubic Bézier that starts going right, turns down,
turns left, and arrives horizontal — a typographic "line continuation" shape.

Example dimensions (subject to tuning):

- Start: right edge of last pill + a few px gap
- End: x-position of chain head block, y-position of next row's vertical centre
- Control points: tuned so the curve looks smooth at various row-height gaps

### 7.4 Special block styles

| Block type             | Visual                                                              |
|------------------------|---------------------------------------------------------------------|
| `KImportStatement`     | `[ import * from "strudel" ✎ ]` — grey/neutral, compact, full-width |
| `KLetStatement`        | `[ let  name  =  ⬜ ]` — muted teal, expression slot on right        |
| `KConstStatement`      | `[ const  name  =  ⬜ ]` — same, lock icon to distinguish            |
| `KNewlineHint`         | A small ↩ icon between pills; click removes it                      |
| *hover newline button* | `⏎` appears between any two pills on hover / long-press             |
| *row handle*           | `⠿` on far left of each statement row                               |

---

## 8. Drag and Drop

### 8.1 Approach

**Pointer Events API** (`pointerdown`, `pointermove`, `pointerup`, `pointercancel`).
Works identically on mouse and touch — no separate touch handlers needed.
All tap/swipe equivalents use the same code path.

Minimum interaction sizes (touch targets): 44×44 px (Apple HIG / WCAG guideline).

### 8.2 `DragState`

```kotlin
object DragState {
    var source: DragSource? = null
    var pointerX: Double = 0.0
    var pointerY: Double = 0.0
    var grabOffsetX: Double = 0.0   // offset from block origin to pointer grab point
    var grabOffsetY: Double = 0.0
    val isDragging get() = source != null
}

sealed class DragSource {
    /** New block from palette — not yet in model. */
    data class PaletteBlock(val funcName: String) : DragSource()

    /** Existing block being moved — already in model, temporarily hidden. */
    data class CanvasBlock(
        val statementId: String,
        val blockId: String,
        val originalChain: KChainStatement,  // for cancel/rollback
    ) : DragSource()

    /** Whole statement row being reordered. */
    data class StatementRow(val statementId: String) : DragSource()
}
```

### 8.3 Drop targets

```kotlin
enum class DropTargetKind {
    CHAIN_BEFORE,      // insert block before another block in a chain
    CHAIN_AFTER,       // insert block after another block (including tail)
    PATTERN_POCKET,    // snap into a PatternLike pocket slot
    NEW_STATEMENT,     // empty canvas area or between rows → new statement
    STATEMENT_BEFORE,  // reorder: insert statement row before another
    STATEMENT_AFTER,   // reorder: insert statement row after another
}

data class DropTarget(
    val id: String,
    val kind: DropTargetKind,
    val getBoundingRect: () -> DOMRect,   // live — called during hit-test
    val onDrop: (DragSource) -> Unit,
)
```

`KlangBlocksCanvasComp` owns a `MutableList<DropTarget>` that components
register into on mount and remove from on unmount.

During `pointermove`, the canvas finds the drop target whose rect contains
(or is closest to) the current pointer position and highlights it.

### 8.4 Ghost overlay

A `position: fixed` `div` rendered by `KlangBlocksCanvasComp` that:

- Mirrors the visual appearance of the block being dragged (semi-transparent)
- Follows `(pointerX - grabOffsetX, pointerY - grabOffsetY)`
- Is shown for the duration of a drag operation

### 8.5 Touch interactions

| Gesture                | Action                                                       |
|------------------------|--------------------------------------------------------------|
| Tap slot               | Open inline editor                                           |
| Long-press block       | Show context menu (delete, duplicate)                        |
| Drag block             | Move block (pointer capture from first `pointermove` > 5 px) |
| Drag row handle        | Reorder row                                                  |
| Two-finger swipe left  | Undo                                                         |
| Two-finger swipe right | Redo                                                         |
| Pinch                  | Zoom canvas (stretch goal)                                   |

---

## 9. Interactions Summary

| User action                              | Result                                                  |
|------------------------------------------|---------------------------------------------------------|
| Drag palette pill → canvas empty area    | Append new `KChainStatement`                            |
| Drag palette pill → between rows         | Insert new `KChainStatement` at that line               |
| Drag palette pill → after block in chain | Insert block at that chain position                     |
| Drag palette pill → PatternLike pocket   | Create `KNestedChainArg`                                |
| Drag canvas block within same chain      | Reorder within chain                                    |
| Drag canvas block to different chain     | Move to that chain                                      |
| Drag canvas block to canvas empty area   | Become new standalone chain                             |
| Drag row handle                          | Reorder statement rows                                  |
| Click empty Str/Num slot                 | Open inline `<input>`                                   |
| Enter / blur                             | Commit value                                            |
| Escape                                   | Revert to previous value                                |
| Click empty Bool slot                    | Toggle true/false                                       |
| Click filled slot value                  | Open inline editor                                      |
| Click `×` on block                       | Remove from chain (if chain empties → remove statement) |
| Click `×` on statement                   | Remove whole statement                                  |
| Click `⏎` hover button between pills     | Insert `KNewlineHint`                                   |
| Click `↩` newline hint                   | Remove hint                                             |
| Click `⇄` / `⇅` on structural block      | Toggle `PocketLayout`                                   |
| Click `T ↔ ⬡` on PatternLike slot        | Toggle text vs block mode                               |
| Ctrl+Z / two-finger swipe left           | Undo                                                    |
| Ctrl+Y / Ctrl+Shift+Z / swipe right      | Redo                                                    |
| Hover palette pill                       | Show function doc tooltip                               |

---

## 10. Code Generation Round-Trip

```
Code editor ──parse──► AST ──AstToKBlocks──► KProgram
                                                  │
                ◄──KBlocksToCode──────────────────┘
```

**Round-trip stability contract** (verified by `RoundTripTest`):

```
code₁ → parse → KProgram → generate → code₂
code₂ → parse → KProgram → generate → code₃
assert code₂ == code₃   // idempotent after first normalisation
```

The first round may change formatting (whitespace, import order). After that,
`generate(parse(code))` must be a fixed point.

**`KNewlineHint` and round-trips**: hints are UI-only and are lost on `Code → Blocks`.
This is the accepted trade-off. The line-wrapping state is not preserved when switching
between editors.

---

## 11. Undo / Redo Architecture

Because blocks are a pure mapping of code, undo/redo is entirely code-snapshot based:

```
┌───────────────────────────────┐
│  undoStack:  [code₀, code₁]  │
│  current:    code₂            │
│  redoStack:  [code₃]          │
└───────────────────────────────┘
```

- Every model mutation pushes `current` to `undoStack` before applying.
- Undo pops from `undoStack` → re-parses → rebuilds `KProgram` → fires `onCodeChanged`.
- Redo pops from `redoStack`.
- Max stack depth: 100 snapshots.

This means undo/redo "just works" for free once the code↔model round-trip is solid.

---

## 12. Integration into `CodeSongPage`

1. Replace `BlocklyEditorComp` with `KlangBlocksEditorComp` — same props interface.
2. `EditorMode` enum and toggle button unchanged.
3. `switchToBlocks()` / `switchToCode()` logic unchanged.
4. Remove `blockly/` package and npm `blockly` dependency.

---

## 13. `settings.gradle.kts`

```kotlin
include(
    ":klang",
    ":common",
    ":audio_fe",
    ":audio_be",
    ":audio_bridge",
    ":audio_jsworklet",
    ":klangscript",
    ":klangblocks",    // ← new
    ":tones",
    ":strudel",
    ":strudel-ksp",
    ":klang-notebook",
)
```

---

## 14. Implementation Order

| #  | Task                                                              | Deliverable                        |
|----|-------------------------------------------------------------------|------------------------------------|
| 1  | Create `klangblocks` module scaffolding                           | Compiling empty module             |
| 2  | `KSlotKind`, `KTypeMapping`, `KSlot`                              | Type system foundation             |
| 3  | `KBlock.kt`, `KProgram.kt`                                        | Block model hierarchy              |
| 4  | `AstToKBlocks.kt` with chain extraction + pocket layout inference | AST → model                        |
| 5  | `KBlocksToCode.kt`                                                | Model → code                       |
| 6  | `RoundTripTest`, `AstToKBlocksTest`, `KBlocksToCodeTest`          | Round-trip verified on JVM         |
| 7  | `KlangBlocksEditorComp` skeleton + undo/redo                      | Compiling, wired into CodeSongPage |
| 8  | `KlangBlocksCanvasComp` with line numbers, plain div rows         | Non-styled layout                  |
| 9  | `KlangBlocksBlockComp` + `KlangBlocksSlotComp` styled pills       | Visual blocks, no drag             |
| 10 | `KlangBlocksPaletteComp` with search + hover tooltips             | Palette, no drag                   |
| 11 | `DragAndDrop` machinery + ghost overlay                           | Drag from palette → canvas         |
| 12 | Full drag: block ↔ block, reorder, pocket drop                    | All drag interactions              |
| 13 | Inline slot editing (text, number, bool toggle)                   | Editable slots                     |
| 14 | `KNewlineHint` + S-curve SVG connector                            | Long chain wrapping                |
| 15 | `KBinaryArg` / `KUnaryArg` inline expression blocks               | Arithmetic args                    |
| 16 | Import / let / const statement blocks                             | Full KlangScript feature coverage  |
| 17 | Mobile touch interactions (long-press, swipe undo)                | Touch support                      |
| 18 | Remove old `blockly/` package + npm dep                           | Cleanup                            |

---

## 15. Open Questions

Questions still requiring a decision before or during implementation.

---

**Q-A — Type-name unification scope**
`KTypeMapping` is defined in `klangblocks/commonMain`. The old `BlockFieldNaming`
in the root `src/` tree contains the same logic (ad-hoc). Should `BlockFieldNaming`
be deleted when we remove the `blockly/` package (step 18), or should the canonical
mapping live in `klangscript` itself so other consumers don't need to depend on
`klangblocks`?

---

**Q-B — `PatternLike` slot: default text vs block mode**
A PatternLike slot can hold a text string (`KStringArg`) or a nested chain
(`KNestedChainArg`). The default mode is determined by whether the function is
in the `structural` category. But what should happen when an unknown / custom
function has a PatternLike param? Default to text mode or block mode?

---

**Q-C — S-curve SVG implementation**
The S-curve connector requires measuring the DOM positions of the pills on both
rows after layout. This requires `getBoundingClientRect()` calls in `onMount` /
after a re-render. Two implementation options:

- **Option A**: absolute-positioned SVG overlay inside `KlangBlocksChainComp`
  that recomputes after every render.
- **Option B**: pure CSS using `border-radius` on a block-level element (simpler
  but less control over the curve shape).

Preference?

---

**Q-D — Pocket layout toggle: persisted in model or derived each time?**
`AstToKBlocks` infers `PocketLayout` from source line positions. If the user
toggles a structural block to `VERTICAL` and then generates code (which will
use single-line args), then switches back to Blocks view, the layout will be
re-inferred as `HORIZONTAL`. Should the user's manual layout preference be
preserved in the generated code (e.g. via a comment `// @layout vertical`)?
Or is re-inference from AST acceptable?

---

**Q-E — `@klangblocks` DSL annotation**
To allow per-function customisation (colour override, default pocket layout,
display label), the plan notes that CSS custom properties provide a hook.
Two implementation paths:

- **v1**: hardcode category → colour; no per-function overrides.
- **v2**: add a `klangBlocksMeta: Map<String, Any>` field to `FunctionDoc`
  (populated by the KSP processor from a new `@KlangBlocks` annotation).

Is v2 in scope for this iteration?

---

**Q-F — Import block UX**
Import statements appear as full blocks on the canvas. Questions:

- Can the user add a brand-new import (e.g. by typing into a "new import" field)?
  If yes: is there a dedicated "add import" button, or can they drag an import
  block from a palette category?
- Can import statements be freely reordered, or are they always pinned to the
  top of the canvas?
- What happens when the user deletes the `import * from "strudel"` block and
  then tries to use `sound`? (Parse / runtime error only, or a warning in the UI?)

---

**Q-G — Expression slots: mixed text + block mode**
A `PatternLike` slot that contains a `KBinaryArg` or `KIdentifierArg` is shown
as an inline expression. Should the `T ↔ ⬡` toggle allow the user to switch
*any* filled slot from its block representation back to raw text editing?
This would require a `KRawTextArg(source: String)` type that the code generator
emits verbatim. Powerful escape hatch, but bypasses type safety.

---

**Q-H — Undo stack per-session or persisted?**
The undo stack lives in memory (component state). Refreshing the page loses it.
The `code` state is already persisted to `localStorage` by `CodeSongPage`.
Should the undo stack also be persisted (e.g. as a JSON array in `localStorage`),
or is session-only acceptable?

---

**Q-I — Line numbers: code-editor correspondence**
Line numbers in the canvas gutter are 1-based indexes into the `KProgram.statements`
list. They do NOT correspond to line numbers in the generated code (a chain may
span many code lines). Is this acceptable, or should they mirror the actual code
line numbers so users can correlate Code view and Blocks view?

---

**Q-J — `KBinaryArg` and `KUnaryArg` rendering in slots**
These are rendered as compact "inline expression blocks" inside the slot.
Example: `bpm / 2` renders as `[ bpm ]  ÷  [ 2 ]` inside the slot.
The sub-slots of a `KBinaryArg` are themselves `KArgValue` — so they can be
arbitrarily nested. Should nesting depth be capped (e.g. max 2 levels) to
prevent the UI from becoming unwieldy?

---

## 16. What to Carry Over from the Blockly Implementation

| Old file                                          | Reused in                                   |
|---------------------------------------------------|---------------------------------------------|
| `AstToBlockly.kt` — chain extraction algorithm    | `AstToKBlocks.kt` (port, no `dynamic`)      |
| `KlangScriptGenerator.kt` — code gen logic        | `KBlocksToCode.kt` (typed, cleaner)         |
| `BlockDefinitionBuilder.kt` — category colour map | `KlangBlocksPaletteComp` colour map         |
| `BlockFieldNaming.kt` — slot kind predicates      | `KTypeMapping.kt` (supersedes, then delete) |

Removed entirely: `blockly/ext/Blockly.kt`, npm `blockly` dependency, all
Blockly JSON serialisation code.
