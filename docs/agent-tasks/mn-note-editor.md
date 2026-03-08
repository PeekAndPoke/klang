# Mini-Notation Note Staff Editor — Agent Task Log

## What This Is

The note staff editor is an interactive SVG treble-clef staff that lets users edit
mini-notation patterns by clicking/dragging. It integrates with `MnPatternTextEditor`
(pure Kotlin/common) for all text mutation logic.

---

## Architecture

### Key Files

| File                                 | Module     | Role                                                                 |
|--------------------------------------|------------|----------------------------------------------------------------------|
| `lang/editor/MnNodeOps.kt`           | commonMain | Shared tree-walking + AST insertion/replacement                      |
| `lang/editor/MnPatternTextEditor.kt` | commonMain | Pure editing logic: insertBetween, insertAt, removeNode, updateNode  |
| `lang/editor/NoteStaffLayout.kt`     | commonMain | Platform-independent layout model + insert target building           |
| `ui/NoteStaffEditor.kt`              | jsMain     | SVG rendering, mouse events, pixel-position mapping                  |
| `ui/MnEditorBase.kt`                 | jsMain     | Base class connecting NoteStaffEditor actions to MnPatternTextEditor |

### Data Flow

```
User click → BoundarySlot.target (InsertTarget.Between / InsertTarget.At)
    → Action.InsertBetween / Action.InsertAt
    → MnEditorBase.insertBetween / insertAt
    → MnPatternTextEditor.insertBetween / insertAt
    → MnNodeOps.insertBetweenAst / insertAfterExitingBrackets
    → MnRenderer.render(modifiedAST)
    → new text
```

### LayoutItem Types (NoteStaffLayout)

- `LayoutItem.Note(node)` — a single atom or rest
- `LayoutItem.Stack(items)` — a chord (simultaneous notes, from MnNode.Stack)
- `LayoutItem.BracketMark(type, isOpen)` — visual bracket column

### InsertTarget Types (NoteStaffLayout)

- `InsertTarget.Between(leftNode, rightNode, skipOpeningBrackets, exitBrackets)` — sequential insert
- `InsertTarget.At(node)` — stack/chord insert onto existing note

### Boundary Slot Layout (NoteStaffEditor / NoteStaffLayout)

Each Note/Stack column produces **three** snap slots:

1. **Left-push** (at column start): `Between(prevNode, firstNodeAfter[idx], skip)`
2. **Center overlay** (at column center): `At(firstItem)` — for stacking
3. **Right-push** (at `colWidth-2`): `Between(lastItem, firstNodeAfter[idx+1], 0)`

Closing brackets produce **exit-bracket push slots**:

- Each closing bracket generates a slot with `Between(leftNode, null, 0, exitBrackets=N)`
  where N increments for consecutive close brackets.
- This allows insertion at intermediate nesting depths (e.g., inside a group but after a stack).

`firstNodeAfter[idx+1]` (not `null`) is critical for right-push slots: it ensures
AST-based insertion places the new node at the correct structural position between groups.

---

## Insertion Mechanisms

### AST-Based Insertion (MnNodeOps.insertBetweenAst)

All insertion is now AST-based — no character-level scanning or bracket-skipping phases.
`MnPatternTextEditor.insertBetween` delegates to `MnNodeOps`:

- Walks the parse tree to find the parent sequence containing the insertion point
- Splices the new atom into that list
- Re-renders to text via `MnRenderer.render()`

**Search strategy depends on `rightNode`:**

- `rightNode != null`: breadth-first (finds shallowest common ancestor for cross-group inserts)
- `rightNode == null`: depth-first (finds deepest match so inserts land at the correct nesting level)

### Exit-Bracket Insertion (MnNodeOps.insertAfterExitingBrackets)

When `exitBrackets > 0` and `leftNode` is present:

1. Recursively finds `leftNode` at its deepest position
2. Bubbles up through container levels, decrementing `exitBrackets` at each
3. Inserts `newNode` when the counter reaches zero

This enables all 24 insertion positions in deeply nested patterns like
`<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>`.

### skipOpeningBrackets

When `leftNode == null` and `skipOpeningBrackets > 0`:

- Walks N levels of nesting from the top of the tree toward `rightNode`
- Inserts at that depth (e.g., inside `<[` instead of before it)

---

## Bug History

### Bug 1: insertBetween(d,e) in `<a [c d] [e f]>` inserted inside `[e f]`

**Root cause**: No bracket-skipping when leftNode present.
**Fix**: (Now moot — AST-based insertion handles this structurally.)

### Bug 2: `< INSERT [...]` produced `NEW < [...]` instead of `< NEW [...]`

**Root cause**: No left-push inside `<` — the slot used `Between(null, firstAtom, skip=0)`.
**Fix**: Track `openBracketsSinceLastNode` counter; pass as `skipOpeningBrackets`.

### Bug 3: `<[a,b,c] INSERT [c,d,e]>` inserted inside `[a,b,c]` as a new layer

**Root cause**: Close-bracket push slot used `rightNode=null`.
**Fix**: Right-edge push slots use `firstNodeAfter[idx+1]` as rightNode.

### Bug 4: `[c4,e4] d4` — right-edge of stack inserts INSIDE `[c4,e4]`

**Root cause**: Right-edge push used `Between(e4, null)`.
**Fix**: Right-edge push uses `Between(e4, d4)` via `firstNodeAfter[idx+1]`.

---

## Tests

### MnPatternTextEditorSpec (commonTest, ~90+ tests)

- removeNode: flat, group, alternation, stack
- updateNode: modifiers (multiplier, divisor, weight, probability, euclidean), nested
- insertBetween: flat, group, alternation, cross-group, modifier suffix, skipOpeningBrackets
- insertAt: flat, group, alternation, stack extension
- **Comprehensive 24-position test** for `<[0,7,12] [12,19,24] [[1,8,13] [1,8,13]] [[1,8,13]]>`
  covering all insertion points including exitBrackets positions (5, 16, 17, 22, 23)

### NoteStaffLayoutSpec (commonTest, ~16 tests)

- `buildLayoutItems`: flat, group, alternation, stack (`[a,b]` = Group+Stack), nested
- `buildInsertTargets`: verifies right-push of Stack uses `firstNodeAfter[idx+1]` as rightNode
- Integration tests: full chain from pattern string → layout → targets → insertBetween → verify text

---

## UI Testability

### What can be tested from commonTest/jvmTest (no browser needed)

- **`MnPatternTextEditor`**: 100% testable — all text mutation logic is pure/common.
- **`MnNodeOps`**: 100% testable — all tree-walking and AST insertion is pure/common.
- **`NoteStaffLayout.buildLayoutItems`**: testable — maps `MnPattern` → `List<LayoutItem>`.
- **`NoteStaffLayout.buildInsertTargets`**: testable — maps layout items → ordered slot targets.
- **Integration**: given a pattern, enumerate all insert targets, execute each via
  `MnPatternTextEditor.insertBetween/insertAt`, assert output text.

### What requires a browser (not currently tested)

- Pixel-accurate snap slot positions (depend on `NOTE_COL_W=22`, `BRACKET_COL_W=10`)
- Mouse event handling and drag logic
- SVG rendering correctness
- Ghost note preview animation

### How to extend UI tests

1. Add a test in `NoteStaffLayoutSpec` using `NoteStaffLayout.buildInsertTargets(layout("pattern"))`
2. Find the target of interest (e.g., `pushTargets.first { it == Between(b, c, 0) }`)
3. Execute via `editor("pattern").insertBetween(b, c, 0)`
4. Assert the output text

---

## Open Issues / TODOs

1. **No browser / Selenium tests** for the SVG staff: snap positions, drag, ghost note rendering.

---

## Architectural Improvement Proposals (All Implemented)

### Proposal 1: Eliminate Tree-Walking Duplication ✅

Moved all shared tree-walking logic into `MnNodeOps` object in commonMain.
Both `MnPatternTextEditor` and `MnEditorBase` delegate to `MnNodeOps`.

### Proposal 2: Delegate Boundary Building to NoteStaffLayout ✅

`NoteStaffEditor` now calls `NoteStaffLayout.buildInsertTargets(layoutItems)` and only
adds pixel positions on top. The tested `buildInsertTargets` is the single source of truth.

### Proposal 3: Fix rightNode=null Fallback for Pattern-Trailing Groups ✅

Solved via the `exitBrackets` mechanism. Close brackets generate push slots with
`exitBrackets=N`, allowing insertion at any intermediate nesting depth.

### Proposal 4: Cache the Parsed Pattern ✅

`MnPatternTextEditor.pattern` uses `by lazy`. One parse per immutable instance.

### Proposal 5: AST-Based Insertion ✅

All insertion now uses AST manipulation + `MnRenderer.render()` instead of character-level
scanning. This eliminates the entire class of bracket-skipping bugs.

### Proposal 6: Unify staffItems with LayoutItems ✅

`NoteStaffEditor.staffNodes` now extracts all `MnNode` instances from the full layout
(including atoms inside `LayoutItem.Stack`), ensuring event handlers work for stacked notes.
