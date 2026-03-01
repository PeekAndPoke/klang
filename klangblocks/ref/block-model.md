# KlangBlocks — Block Model

## Program Structure

```
KBProgram
  └─ List<KBStmt>
       ├─ KBImportStmt
       ├─ KBLetStmt
       ├─ KBConstStmt
       ├─ KBChainStmt  ──→  List<KBChainItem>
       │                         ├─ KBCallBlock  ──→  List<KBArgValue>
       │                         ├─ KBNewlineHint
       │                         ├─ KBStringLiteralItem
       │                         └─ KBIdentifierItem
       └─ KBBlankLine
```

## KBStmt Types

| Type           | Code example              | Notes                                                    |
|----------------|---------------------------|----------------------------------------------------------|
| `KBImportStmt` | `import * from "strudel"` | `names=null` → `import *`; `alias` → `import * as alias` |
| `KBLetStmt`    | `let x = note("c3")`      | `value` is null when no initialiser                      |
| `KBConstStmt`  | `const bpm = 120`         | always has `value`                                       |
| `KBChainStmt`  | `note("c3").gain(0.5)`    | most common; sequence of `KBChainItem`s                  |
| `KBBlankLine`  | _(empty line)_            | preserves whitespace, no code semantics                  |

All `KBStmt` subtypes carry a stable `id: String` for undo/redo tracking.

## KBChainItem Types

| Type                  | When used            | Constraints                                                                                             |
|-----------------------|----------------------|---------------------------------------------------------------------------------------------------------|
| `KBCallBlock`         | every function call  | primary building block; carries `id`, `funcName`, `args`, `isHead`, `pocketLayout`                      |
| `KBNewlineHint`       | between blocks       | records original source line break; not rendered as a tile                                              |
| `KBStringLiteralItem` | head of nested chain | e.g. `"C4".transpose(1)` → `[KBStringLiteralItem("C4"), KBCallBlock("transpose")]`; **first item only** |
| `KBIdentifierItem`    | head of nested chain | e.g. `sine.range(0.25, 0.75)` → `[KBIdentifierItem("sine"), KBCallBlock("range")]`; **first item only** |

### KBCallBlock Fields

```kotlin
data class KBCallBlock(
    val id: String,              // stable UUID for editor tracking
    val funcName: String,        // e.g. "note", "gain"
    val args: List<KBArgValue>,  // parallel to function parameters
    val isHead: Boolean,         // true = first call in chain (no leading ".")
    val pocketLayout: KBPocketLayout, // HORIZONTAL (default) or VERTICAL
)
```

`isHead = false` → rendered with a leading `.` in generated code.
`VERTICAL` → each arg on its own line; chain separator becomes `\n  .`.

## KBArgValue Types

| Type                                     | Example           | Notes                             |
|------------------------------------------|-------------------|-----------------------------------|
| `KBEmptyArg(paramName)`                  | _(unfilled slot)_ | placeholder label shown in editor |
| `KBStringArg(value)`                     | `"c3 e3"`         | multiline → backtick quoting      |
| `KBNumberArg(value: Double)`             | `0.5`, `2`        | integer rendered without decimal  |
| `KBBoolArg(value)`                       | `true`            |                                   |
| `KBIdentifierArg(name)`                  | `myPattern`       | bare variable/constant reference  |
| `KBBinaryArg(left, op, right)`           | `x + 1`           | not directly editable in UI       |
| `KBUnaryArg(op, operand)`                | `-1`, `!flag`     | not directly editable in UI       |
| `KBArrowFunctionArg(params, bodySource)` | `x => x * 2`      | body kept as raw source text      |
| `KBNestedChainArg(chain: KBChainStmt)`   | `cat("c3", "e3")` | rendered as inline mini-blocks    |
