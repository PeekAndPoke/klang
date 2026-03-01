# KlangBlocks — Code Generation

## Entry Points

```kotlin
KBProgram.toCode(): String          // generated source only
KBProgram.toCodeGen(): CodeGenResult // source + position tracking maps
```

`toCode()` is a convenience wrapper around `toCodeGen().code`.

## Source Map — Live Visual Feedback

`CodeGenResult` is the source map: it links every character position in the generated KlangScript
code back to the block (and string-slot content) that produced it. This is used for live visual
feedback — when the audio engine reports a playback position as `(line, col)`, the editor can
highlight the active block in real time.

```kotlin
class CodeGenResult(
    val code: String,
    val blockRanges: Map<String, IntRange>,                   // blockId → char range in code
    val slotContentRanges: Map<Pair<String, Int>, IntRange>,  // (blockId, slotIdx) → content range
)
```

- `blockRanges` — every `KBCallBlock.id` → the `IntRange` of characters it occupies (nested blocks included)
- `slotContentRanges` — `(blockId, slotIndex)` → the range of the **content** of string args (excluding surrounding
  quotes/backticks)

Both maps are built incrementally during `toCodeGen()` via `CodeBuilder.trackBlock` and `trackSlotContent`.

### Hit Testing API

```kotlin
result.findBlockAt(line: Int, col: Int): String?  // block id at 1-based line/col
result.findAt(line: Int, col: Int): HitResult?    // full hit: blockId + slotIndex + offsetInSlot
```

```kotlin
data class HitResult(
    val blockId: String,
    val slotIndex: Int?,       // non-null when position is inside a tracked string slot
    val offsetInSlot: Int?,    // 0-based char offset within the slot content
)
```

**Position conventions** (matching the audio engine):

- `line` and `col` are **1-based**
- `offsetInSlot` is **0-based** relative to the first character after the opening quote

**Resolution rules:**

- Chain separator (`.`) → `null` (not inside any block range)
- Function name → `HitResult(blockId, slotIndex=null, offsetInSlot=null)`
- Opening / closing quote → `HitResult(blockId, slotIndex=null, ...)` (quotes excluded from content range)
- Inside string content → `HitResult(blockId, slotIndex=N, offsetInSlot=M)`
- Non-string args (numbers, bools, etc.) → `HitResult(blockId, slotIndex=null, ...)`
- Nested blocks → innermost (smallest) range wins
- Past end of code → `null`

Results are cached keyed by `line shl 32 or col` — repeated calls from the audio tick loop are O(1).

## Code Generation Rules

### KBChainStmt layout

- If **any** block in the chain has `VERTICAL` layout: chain separator = `\n  .`; individual args on separate lines with
  `\n  ` indent
- Otherwise: chain separator = `.`; args inline

### String quoting

- Single-line strings → `"..."` double-quoted
- Multiline strings → `` `...` `` backtick-quoted
- Content range tracked excluding the surrounding quotes/backticks

### Arg serialisation

| KBArgValue           | Generated                                         |
|----------------------|---------------------------------------------------|
| `KBEmptyArg`         | _(omitted / empty string)_                        |
| `KBStringArg`        | `"value"` or `` `value` ``                        |
| `KBNumberArg`        | integer if whole, decimal otherwise               |
| `KBBoolArg`          | `true` / `false`                                  |
| `KBIdentifierArg`    | bare name                                         |
| `KBBinaryArg`        | `left op right`                                   |
| `KBUnaryArg`         | `opoperand`                                       |
| `KBArrowFunctionArg` | `param => bodySource` or `(p1, p2) => bodySource` |
| `KBNestedChainArg`   | full chain code (recursively tracked)             |

### Import serialisation

| fields          | generated                      |
|-----------------|--------------------------------|
| `names != null` | `import {a, b} from "lib"`     |
| `alias != null` | `import * as alias from "lib"` |
| both null       | `import * from "lib"`          |

## CodeBuilder (internal)

```kotlin
class CodeBuilder {
    fun append(s: String): CodeBuilder
    fun trackBlock(blockId: String, content: CodeBuilder.() -> Unit): CodeBuilder
    fun trackSlotContent(blockId: String, slotIndex: Int, range: IntRange)
    fun build(): CodeGenResult
}
```

`trackBlock` wraps a block's output and records the start/end char offsets.
`trackSlotContent` records the content range for a string-valued slot.
