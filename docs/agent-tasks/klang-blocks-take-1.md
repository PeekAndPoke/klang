# KlangBlocks — Implementation Plan (Take 1, Rev 2)

> **Status**: Design / pre-implementation
> **Author**: Claude + user
> **Date**: 2026-02-26
> **Revision**: 3 — updated with user feedback (round 2)

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
        KBBlock.kt          ← sealed block-model hierarchy
        KBSlot.kt           ← KBSlotKind sealed class + slot typing
        KBProgram.kt        ← top-level program container
        KBTypeMapping.kt    ← canonical TypeModel.simpleName ↔ KBSlotKind mapping
      transform/
        AstToKBlocks.kt     ← KlangScript AST  →  KBBlock model
        KBlocksToCode.kt    ← KBBlock model    →  KlangScript String
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

### 3.1 Where it lives

The type system foundation lives in **`klangscript` / KlangDocs** (`DslDocs.kt`),
not in `klangblocks`. This makes type information available to any consumer of
the docs registry without depending on `klangblocks`.

`klangblocks/commonMain` adds `KBSlotKind` and `KBTypeMapping` on top of those
`klangscript` types — they are the block-editor's view of the same information.

### 3.2 Union types in `TypeModel` (klangscript change)

`PatternLike` is a Kotlin type alias for `Any?`. The current `TypeModel` has no
way to express this — it only carries `simpleName`. We extend it to optionally
hold union-member types:

```kotlin
// klangscript/commonMain — DslDocs.kt

data class TypeModel(
    val simpleName: String,
    val isTypeAlias: Boolean = false,
    val isNullable: Boolean = false,

    /**
     * Non-null when this type is a union / type alias satisfiable by several
     * concrete types.  e.g. PatternLike → [String, Number, StrudelPattern]
     *
     * v1: hardcoded for known aliases in KBTypeMapping.
     * v2: populated by the KSP processor via @KlangType annotation.
     */
    val unionMembers: List<TypeModel>? = null,
) {
    val isUnion: Boolean get() = !unionMembers.isNullOrEmpty()
}
```

**v1**: `unionMembers` is hardcoded in `KBTypeMapping` for `PatternLike`.
**v2**: a new `@KlangType(unionOf = [...])` annotation on the `PatternLike`
typealias, processed by `strudel-ksp`, populates `unionMembers` automatically.

### 3.3 `@KlangBlocks` annotation (strudel-ksp, v2)

Block-editor-specific metadata attached to DSL functions:

```kotlin
// annotation definition (strudel module)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class KlangBlocks(
    val color: String = "",   // override default category colour
    val label: String = "",   // override display label on the block pill
)
```

`FunctionDoc` gains `blocksMeta: KlangBlocksMeta?` populated by the KSP processor.
This is a **v2 feature** — the CSS custom-property hooks in §7.2 ensure no UI
rewrites are needed when it lands.

### 3.4 `KBSlotKind` — sealed class (klangblocks/commonMain)

```kotlin
sealed interface KBSlotKind {

    /** Free-text / mini-notation input; emitted as a quoted string. */
    object Str : KBSlotKind

    /** Numeric input; emitted as a number literal. */
    object Num : KBSlotKind

    /** Boolean checkbox; emitted as `true` or `false`. */
    object Bool : KBSlotKind

    /**
     * A StrudelPattern produced by a block chain.
     * Slots of this kind show a drop-zone for chain pills.
     */
    object PatternResult : KBSlotKind

    /**
     * Union type: the slot accepts any of its [members].
     *
     * PatternLike → Union([Str, Num, PatternResult]) is the primary example.
     *
     * Slot UI behaviour for a Union slot:
     * - If [acceptsString] is true → default to an editable text field
     *   (treating the value as mini-notation).
     * - The user can drop a block chain into the slot, which replaces the
     *   text with a KBNestedChainArg.
     * - Removing the dropped chain reverts the slot back to text-edit mode.
     * - This dual-mode behaviour is derived automatically from the union
     *   members — no per-function configuration needed.
     */
    data class Union(val members: List<KBSlotKind>) : KBSlotKind {
        val acceptsString: Boolean get() = members.any { it is Str }
        val acceptsBlock: Boolean get() = members.any { it is PatternResult }
    }

    /** Named type with no specific renderer — falls back to text field. */
    data class NamedObject(val typeName: String) : KBSlotKind

    /** Unknown type — falls back to text field. */
    data class Unknown(val typeName: String) : KBSlotKind
}
```

### 3.5 `KBTypeMapping` — canonical mapping (klangblocks/commonMain)

```kotlin
object KBTypeMapping {

    /** Map a DslDocs TypeModel to a KBSlotKind (union members recursively mapped). */
    fun slotKindFor(type: TypeModel): KBSlotKind {
        if (type.isUnion) {
            val members = type.unionMembers!!.map { slotKindFor(it) }.distinct()
            return KBSlotKind.Union(members)
        }
        return primitiveKindFor(type.simpleName)
    }

    fun primitiveKindFor(typeName: String): KBSlotKind = when (typeName) {
        "String" -> KBSlotKind.Str
        "Double", "Float", "Int", "Long", "Number" -> KBSlotKind.Num
        "Boolean" -> KBSlotKind.Bool
        "StrudelPattern", "Pattern" -> KBSlotKind.PatternResult
        // PatternLike: hardcoded union until @KlangType annotation is wired up
        "PatternLike" -> KBSlotKind.Union(
            listOf(
                KBSlotKind.Str, KBSlotKind.Num, KBSlotKind.PatternResult,
            )
        )
        else -> KBSlotKind.NamedObject(typeName)
    }

    /** Infer the KBSlotKind produced by an AST expression (for arg classification). */
    fun slotKindOf(expr: Expression): KBSlotKind = when (expr) {
        is StringLiteral -> KBSlotKind.Str
        is NumberLiteral -> KBSlotKind.Num
        is BooleanLiteral -> KBSlotKind.Bool
        is CallExpression -> KBSlotKind.PatternResult
        is MemberAccess -> KBSlotKind.PatternResult
        is Identifier -> KBSlotKind.PatternResult   // variable or named pattern
        else -> KBSlotKind.Unknown("expr")
    }

    /** True when [argValue] can be placed into a slot of [kind]. */
    fun compatible(argValue: KBArgValue, kind: KBSlotKind): Boolean = when (kind) {
        is KBSlotKind.Union -> kind.members.any { compatible(argValue, it) }
        KBSlotKind.PatternResult -> argValue is KBNestedChainArg || argValue is KBIdentifierArg
        KBSlotKind.Str -> argValue is KBStringArg || argValue is KBEmptyArg
        KBSlotKind.Num -> argValue is KBNumberArg || argValue is KBEmptyArg
        KBSlotKind.Bool -> argValue is KBBoolArg || argValue is KBEmptyArg
        else -> true   // permissive fallback
    }
}

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
data class KBProgram(
    val statements: MutableList<KBStmt> = mutableListOf(),
)

// ─────────────────────────────────────────────────────
// Statements  (one visual row on the canvas)
// ─────────────────────────────────────────────────────

sealed class KBStmt {
    /** Stable ID used for diffing and drag references. */
    abstract val id: String
}

/** import * from "strudel"  /  import { x } from "lib"  /  import * as ns from "lib" */
data class KBImportStmt(
    override val id: String,
    val libraryName: String,
    val imports: List<Pair<String, String>>? = null,  // null = wildcard
    val namespaceAlias: String? = null,
) : KBStmt()

/** let x = <expr> */
data class KBLetStmt(
    override val id: String,
    val name: String,
    val value: KBArgValue?,    // null when uninitialized
) : KBStmt()

/** const x = <expr> */
data class KBConstStmt(
    override val id: String,
    val name: String,
    val value: KBArgValue,
) : KBStmt()

/**
 * The main statement type: a method chain rendered as a row of connected
 * block pills.
 *
 *   sound("bd hh sd")  .gain(0.5)  .speed(2)
 *   └─── head block ──┘└─ block ──┘└─ block ┘
 *
 * The steps list may contain KBNewlineHint markers that tell the renderer
 * to draw an S-curve connector and continue on the next visual row.
 * KBNewlineHint items are transparent to code generation.
 */
data class KBChainStmt(
    override val id: String,
    val steps: MutableList<KBChainItem> = mutableListOf(),
) : KBStmt()

// ─────────────────────────────────────────────────────
// Chain items
// ─────────────────────────────────────────────────────

sealed class KBChainItem

/**
 * One function/method call in a chain.
 *
 * @param funcName     The function name (without dot).
 * @param args         Argument list; length == slotsFor(funcName).size,
 *                     empty slots filled with KBEmptyArg.
 * @param isHead       True for the first block (no leading dot in code).
 * @param pocketLayout How PatternLike pocket slots are arranged in the UI.
 *                     AstToKBlocks infers this from whether the original
 *                     source args span multiple lines; can be toggled by user.
 */
data class KBCallBlock(
    val id: String,
    val funcName: String,
    val args: MutableList<KBArgValue> = mutableListOf(),
    val isHead: Boolean = true,
    val pocketLayout: KBPocketLayout = KBPocketLayout.HORIZONTAL,
) : KBChainItem()

/**
 * Visual-only marker: draw an S-curve connector here and continue the
 * chain on the next visual row.  No effect on code generation.
 */
data object KBNewlineHint : KBChainItem()

/** Controls how PatternLike pocket slots are rendered inside a structural block. */
enum class KBPocketLayout { HORIZONTAL, VERTICAL }

// ─────────────────────────────────────────────────────
// Argument values  (what fills a slot inside a block)
// ─────────────────────────────────────────────────────

sealed class KBArgValue

/**
 * Slot is empty.
 * The UI shows [paramName] as a dimmed placeholder.
 * Code generation emits `null` for required slots.
 */
data class KBEmptyArg(val paramName: String) : KBArgValue()

/** A string literal. Emitted as `"value"` in code. */
data class KBStringArg(val value: String) : KBArgValue()

/** A numeric literal. Emitted as the number in code. */
data class KBNumberArg(val value: Double) : KBArgValue()

/** A boolean. Emitted as `true` or `false`. */
data class KBBoolArg(val value: Boolean) : KBArgValue()

/**
 * A nested chain snapped into a PatternLike pocket.
 * The nested chain does not appear in the program's statement list.
 */
data class KBNestedChainArg(val chain: KBChainStmt) : KBArgValue()

/** A variable reference. Emitted as the bare identifier. */
data class KBIdentifierArg(val name: String) : KBArgValue()

/**
 * A binary expression: left op right.
 * Emitted as `(left op right)`.
 * The UI renders this as a small inline block with two input sub-slots.
 */
data class KBBinaryArg(
    val left: KBArgValue,
    val operator: String,   // "+", "-", "*", "/", "%", "==", "!=", etc.
    val right: KBArgValue,
) : KBArgValue()

/**
 * A unary expression.
 * Emitted as `op operand` (e.g. `-0.5`, `!flag`).
 */
data class KBUnaryArg(
    val operator: String,   // "-", "+", "!"
    val operand: KBArgValue,
) : KBArgValue()

/**
 * An arrow function literal used as a callback argument.
 * The body is stored as a KlangScript source string for v1
 * (no block decomposition of function bodies yet).
 * Rendered as a compact text field.
 */
data class KBArrowFunctionArg(
    val params: List<String>,
    val bodySource: String,
) : KBArgValue()
```

### 4.3 Slot descriptor (`KBSlot.kt`)

```kotlin
data class KBSlot(
    val index: Int,
    val paramName: String,
    val kind: KBSlotKind,
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
): List<KBSlot>
```

A `KBCallBlock`'s `args` list always has the same length as `slotsFor(funcName)`.

---

## 5. Transforms (`commonMain`)

### 5.1 `AstToKBlocks`

```
KlangScriptParser.parse(code) → Program (AST) → AstToKBlocks.convert() → KBProgram
```

| AST node                                             | KB result                                           |
|------------------------------------------------------|-----------------------------------------------------|
| `ImportStatement`                                    | `KBImportStmt`                                      |
| `LetDeclaration`                                     | `KBLetStmt`                                         |
| `ConstDeclaration`                                   | `KBConstStmt`                                       |
| `ExpressionStatement(chain)`                         | `KBChainStmt` via left-recursive chain walk         |
| `StringLiteral` / `NumberLiteral` / `BooleanLiteral` | `KBStringArg` / `KBNumberArg` / `KBBoolArg`         |
| `Identifier` in arg                                  | `KBIdentifierArg`                                   |
| `CallExpression(Identifier)` in PatternLike pocket   | `KBNestedChainArg` with full chain                  |
| `BinaryOperation`                                    | `KBBinaryArg` (recursive)                           |
| `UnaryOperation`                                     | `KBUnaryArg`                                        |
| `ArrowFunction`                                      | `KBArrowFunctionArg(bodySource = exprToText(body))` |

**Pocket layout inference**: for structural functions, check whether the source
locations of the pocket arguments span more than one line.

```
stack(a, b)          → args on same line  → KBPocketLayout.HORIZONTAL
stack(                → args on separate  → KBPocketLayout.VERTICAL
  a,
  b,
)
```

`SourceLocation.startLine` on each argument expression is used for this check.

**Empty slot padding**: after extracting real args, pad the arg list with
`KBEmptyArg(paramName)` up to the slot count for the function.

### 5.2 `KBlocksToCode`

```
KBProgram → KBlocksToCode.generate() → String
```

- `KBImportStmt` → `import * from "lib"` / `import { x, y } from "lib"` / `import * as ns from "lib"`
- `KBLetStmt` → `let name = value`
- `KBConstStmt` → `const name = value`
- `KBChainStmt` → `head(args…).step(args…).step(args…)` (one KlangScript expression per chain)
- `KBNewlineHint` → silently skipped
- `KBEmptyArg` → `null` (required slots); trailing empty vararg slots stripped
- `KBNestedChainArg` → recursively generated chain expression
- `KBBinaryArg` → `(left op right)`
- `KBUnaryArg` → `op operand`
- `KBArrowFunctionArg` → `(params) => bodySource` or `param => bodySource` for single param
- `KBIdentifierArg` → bare name
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
       └─ statement rows (one KlangBlocksChainComp per KBStmt)
            └─ KlangBlocksBlockComp  (per KBCallBlock / special statement)
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

- `var program: KBProgram` — the live block model
- `var undoStack: ArrayDeque<String>` — code snapshots for undo
- `var redoStack: ArrayDeque<String>` — code snapshots for redo

**Every model mutation** follows this pattern:

```kotlin
fun mutate(action: KBProgram.() -> Unit) {
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
  slots pre-filled with `KBEmptyArg`.
- Search field at top filters by name / tag / category in real time.
- Category expand/collapse state persisted in `localStorage`.

### 6.4 `KlangBlocksCanvasComp`

- Right panel, fills remaining width, vertically scrollable.
- Shows a **line number gutter** on the left margin (1-based, one number per `KBStmt`).
- Renders one `KlangBlocksChainComp` per `KBStmt` in program order.
- Drop zone at bottom: dropping a block here appends a new `KBChainStmt`.
- Drop zone between rows: inserting a new statement at that position.
- Hosts the **drag ghost overlay** (`position: fixed`, follows pointer).
- Keyboard shortcuts: Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z for undo/redo.
- Touch: two-finger swipe back/forward for undo/redo.

**Dropped blocks snap to the row grid** — there is no free 2D positioning.
A block dropped "somewhere" on the canvas lands at the nearest valid row position.

### 6.5 `KlangBlocksChainComp`

Renders a `KBChainStmt` as one or more visual rows of connected pills.

**Normal rendering (no wrapping):**

```
  1 │ [ sound "bd hh sd" ]──[ .gain  0.5 ]──[ .speed  2 ]
```

**Wrapped rendering (KBNewlineHint present):**

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
adjacent blocks. Clicking it inserts a `KBNewlineHint` at that position.
The `⏎` icon on the hint itself removes it.

**Row handle**: a `⠿` drag handle on the far left of each statement row allows
reordering statements vertically. Visible always on mobile; hover-revealed on desktop.

**Special statement renderers** (for non-chain statements):

```
  1 │ [ import  *  from  "strudel"    ✎ ]
  2 │ [ let  myVar  =  ⬜ ]
```

### 6.6 `KlangBlocksBlockComp`

Renders a single `KBCallBlock` as a styled pill:

```
 ──●  .gain  [ 0.5 ]  ●──
```

- Leading dot for non-head blocks; none for head.
- Background and border in category colour (tinted / semi-transparent).
- `×` remove button on hover (on mobile: long-press reveals it).
- For structural functions with `KBPocketLayout.HORIZONTAL`:
  pocket slots are rendered inline, side-by-side inside the pill.
- For structural functions with `KBPocketLayout.VERTICAL`:
  pocket slots are rendered as stacked rows below the function label,
  the pill expands downward.
- A small `⇄` / `⇅` toggle icon appears on structural blocks to switch layout.

### 6.7 `KlangBlocksSlotComp`

Renders one argument slot. Behaviour is driven by `KBSlotKind`:

| Kind                                | Empty state                       | Filled: text/literal          | Filled: block     |
|-------------------------------------|-----------------------------------|-------------------------------|-------------------|
| `Str`                               | dimmed `[  paramName  ]`          | `[ "value"  ✎ ]`              | —                 |
| `Num`                               | dimmed `[  paramName  ]`          | `[ 0.5      ✎ ]`              | —                 |
| `Bool`                              | dimmed `[  paramName  ]`          | `[ ☑ true ]`                  | —                 |
| `PatternResult`                     | `[  paramName  ]` drop-zone only  | —                             | nested chain pill |
| `Union(acceptsString+acceptsBlock)` | dimmed `[  paramName  ]` editable | `[ "value" ✎ ]`               | nested chain pill |
| `KBBinaryArg`                       | —                                 | `[ lhs op rhs ]` inline block | —                 |
| `KBUnaryArg`                        | —                                 | `[ op val ]` inline block     | —                 |

**`Union` slot dual-mode behaviour** (the key design for PatternLike):

The mode is derived automatically from `KBSlotKind.Union.acceptsString` and
`acceptsBlock` — no per-function configuration needed.

1. **Default (text mode)**: slot shows an editable text field.
   A string entered here is mini-notation (e.g. `"c d e f"`).
   This is the natural mode for `note("c d e f")`, `sound("bd hh")`, etc.

2. **Block dropped in**: dragging a chain pill onto the slot replaces the
   text with a `KBNestedChainArg` and renders the chain pill inside the slot.
   The text value is remembered in case the block is removed.

3. **Block removed**: removing the nested chain reverts the slot to text-edit
   mode, restoring the previously held string value.

There is **no explicit `T ↔ ⬡` toggle button** — the transition happens
implicitly by dropping or removing a block. This is simpler and more
discoverable.

**Mini-notation note**: a string in a Union slot is always treated as
mini-notation. In a future iteration, a dedicated "Mini-Notation pill" could
wrap the string visually (wrapping the existing `mini()` DSL function), but
for v1 it is simply a plain editable text field.

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
| `KBImportStmt`         | `[ import * from "strudel" ✎ ]` — grey/neutral, compact, full-width |
| `KBLetStmt`            | `[ let  name  =  ⬜ ]` — muted teal, expression slot on right        |
| `KBConstStmt`          | `[ const  name  =  ⬜ ]` — same, lock icon to distinguish            |
| `KBNewlineHint`        | A small ↩ icon between pills; click removes it                      |
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
        val originalChain: KBChainStmt,  // for cancel/rollback
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
| Drag palette pill → canvas empty area    | Append new `KBChainStmt`                                |
| Drag palette pill → between rows         | Insert new `KBChainStmt` at that line                   |
| Drag palette pill → after block in chain | Insert block at that chain position                     |
| Drag palette pill → PatternLike pocket   | Create `KBNestedChainArg`                               |
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
| Click `⏎` hover button between pills     | Insert `KBNewlineHint`                                  |
| Click `↩` newline hint                   | Remove hint                                             |
| Click `⇄` / `⇅` on structural block      | Toggle `KBPocketLayout`                                 |
| Drop block onto Union slot               | Replace text with nested chain                          |
| Remove block from Union slot             | Revert to editable text (prior string restored)         |
| Ctrl+Z / two-finger swipe left           | Undo                                                    |
| Ctrl+Y / Ctrl+Shift+Z / swipe right      | Redo                                                    |
| Hover palette pill                       | Show function doc tooltip                               |

---

## 10. Code Generation Round-Trip

```
Code editor ──parse──► AST ──AstToKBlocks──► KBProgram
                                                  │
                ◄──KBlocksToCode──────────────────┘
```

**Round-trip stability contract** (verified by `RoundTripTest`):

```
code₁ → parse → KBProgram → generate → code₂
code₂ → parse → KBProgram → generate → code₃
assert code₂ == code₃   // idempotent after first normalisation
```

The first round may change formatting (whitespace, import order). After that,
`generate(parse(code))` must be a fixed point.

**`KBNewlineHint` and round-trips**: hints are UI-only and are lost on `Code → Blocks`.
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
- Undo pops from `undoStack` → re-parses → rebuilds `KBProgram` → fires `onCodeChanged`.
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
| 2  | `KBSlotKind`, `KBTypeMapping`, `KBSlot`                           | Type system foundation             |
| 3  | `KBBlock.kt`, `KBProgram.kt`                                      | Block model hierarchy              |
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
| 14 | `KBNewlineHint` + S-curve SVG connector                           | Long chain wrapping                |
| 15 | `KBBinaryArg` / `KBUnaryArg` inline expression blocks             | Arithmetic args                    |
| 16 | Import / let / const statement blocks                             | Full KlangScript feature coverage  |
| 17 | Mobile touch interactions (long-press, swipe undo)                | Touch support                      |
| 18 | Remove old `blockly/` package + npm dep                           | Cleanup                            |

---

## 15. Open Questions

Legend: ✅ Resolved · 🔜 Deferred (decide when we reach that step) · ❓ Still open

---

**Q-A — Type-name unification scope** ✅
Type system lives in `klangscript` / KlangDocs (`TypeModel` extended with
`unionMembers`). `KBTypeMapping` and `KBSlotKind` live in `klangblocks`.
`BlockFieldNaming` is deleted together with the old `blockly/` package (step 18).

---

**Q-B — Union-slot default mode** ✅
Derived from `KBSlotKind.Union` automatically:

- If `acceptsString` → default to text-edit (mini-notation).
- Dropping a block pill replaces the text; removing it reverts to text.
- No explicit toggle button needed. No per-function configuration needed.
- Future: a "Mini-Notation pill" wrapper (v2).

---

**Q-C — S-curve SVG implementation** 🔜
Deferred to step 14. Two candidate approaches exist (SVG overlay vs CSS
`border-radius`). Will prototype both and pick the cleaner one at that point.

---

**Q-D — Pocket layout round-trip persistence** 🔜
Deferred. For now, `KBPocketLayout` is always re-inferred from AST source
locations on every `Code → Blocks` transition. If this causes noticeable
friction in practice we will revisit (e.g. `// @layout vertical` comment).

---

**Q-E — `@KlangBlocks` annotation** ✅ (v2 confirmed)
Annotation defined and wired through `strudel-ksp` into `FunctionDoc.blocksMeta`
is planned as a v2 feature. CSS custom-property hooks in the block styles ensure
no UI rewrites are needed when it arrives.

---

**Q-F — Import block UX** 🔜
Deferred to step 16 (import / let / const blocks). Key decisions needed then:

- How to add new imports (button vs palette drag).
- Whether imports are pinned to top or freely reorderable.
- What warning (if any) to show when a necessary import is deleted.

---

**Q-G — Expression slot escape hatch** 🔜
Deferred. A `KRawTextArg(source: String)` type that emits verbatim code
is a useful escape hatch for edge cases (complex expressions the block model
can't represent). Will add when implementing step 15 (arithmetic blocks).

---

**Q-H — Undo stack persistence** 🔜
Deferred. Session-only for v1 (in-memory `ArrayDeque`). The `code` state
is already persisted to `localStorage` by `CodeSongPage`, which provides
crash recovery for the most recent state. Full undo persistence can be added
later if it proves important.

---

**Q-I — Line numbers: block vs code correspondence** 🔜
Deferred. For v1, line numbers are sequential statement indexes (1, 2, 3…).
They will not match code-editor line numbers. If user feedback shows this is
confusing, we can annotate statements with their generated code line range.

---

**Q-J — `KBBinaryArg` nesting depth cap** 🔜
Deferred to step 15. Will cap at 2 levels for the initial implementation and
see if that covers all practical cases.

---

## 16. What to Carry Over from the Blockly Implementation

| Old file                                          | Reused in                                    |
|---------------------------------------------------|----------------------------------------------|
| `AstToBlockly.kt` — chain extraction algorithm    | `AstToKBlocks.kt` (port, no `dynamic`)       |
| `KlangScriptGenerator.kt` — code gen logic        | `KBlocksToCode.kt` (typed, cleaner)          |
| `BlockDefinitionBuilder.kt` — category colour map | `KlangBlocksPaletteComp` colour map          |
| `BlockFieldNaming.kt` — slot kind predicates      | `KBTypeMapping.kt` (supersedes, then delete) |

Removed entirely: `blockly/ext/Blockly.kt`, npm `blockly` dependency, all
Blockly JSON serialisation code.
