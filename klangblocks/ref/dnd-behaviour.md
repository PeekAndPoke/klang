# Drag-and-Drop Behaviour Reference

This document is the authoritative specification for all drag-and-drop interactions in the KlangBlocks editor.
Use it when writing tests or implementing DnD operations in `KBProgramEditingCtx` and `KlangBlocksEditorComp`.

---

## Drag Sources

| ID | Source          | How triggered                   | Payload                                  |
|----|-----------------|---------------------------------|------------------------------------------|
| P  | Palette drag    | Mouse-down on a palette block   | A function name (no existing block)      |
| A  | Single block    | Drag any canvas block (no Ctrl) | 1 block                                  |
| B  | Chain drag      | Ctrl + drag any canvas block    | That block + all following in same chain |
| C  | Row-handle drag | Drag the row number on the left | Full chain (reorder only — see C1)       |

**Notes:**

- Ctrl can be pressed or released at any time during a drag to switch between A and B mode.
- Dragging the first block without Ctrl is a single-block drag (source A), not a chain drag.
- Sources A and B follow identical rules — the payload size is the only difference.

---

## Drop Targets

| ID | Target            | Description                                                            |
|----|-------------------|------------------------------------------------------------------------|
| 1  | Row gap           | The gap between top-level rows; creates/moves a chain to that position |
| 2  | Chain append zone | The `⊕` circle at the end of a chain; appends to that chain            |
| 3  | Chain insert zone | The `⊕` between two blocks; inserts at that position                   |
| 4  | Empty slot        | An argument slot with no content yet                                   |
| 5  | Existing block    | A specific block already in a chain (replaces it)                      |

**There is no "slot background" drop zone.** Non-empty slots are only reachable via target 5
(replace a specific block) or via the `⊕` zones (targets 2/3) within the nested chain.

---

## Unified Operation Model (Implementation Idea)

All canvas drags (A and B) should:

1. **Clone** the payload blocks with fresh IDs (same content, new UUIDs).
2. **Remove** the original blocks from their source locations.
3. **Insert** the clones at the target location.

This unifies palette and canvas drags: for palette drags, step 2 is a no-op.
The only special case that still needs guarding is **descendant-cycle detection** (see B5/B6 edge cases).

---

## Palette Drag (Source P)

### P1 — Palette → row gap

New single-block chain created at that row. All existing rows shift.
**Status:** Implemented ✓

### P2 — Palette → chain append zone

New block appended to end of target chain (top-level or nested).
**Status:** Implemented ✓

### P3 — Palette → chain insert zone

New block inserted at that position; block to the right shifts forward.
**Status:** Implemented ✓

### P4 — Palette → empty slot

New single-block nested chain placed in the slot.
**Status:** Implemented ✓ (but string-literal special-casing should be removed — see P4 note)

### P5 — Palette → existing block (replace)

The target block is replaced by the new block. The rest of the chain is preserved.
Example: chain `[A, B, C]`, drop `X` onto `B` → `[A, X, C]`.
**Status:** Implemented ✓

---

## Single Block Drag (Source A)

### A1 — Block → row gap

Block removed from source chain. New single-block top-level chain created at target row.
If source chain becomes empty, it is automatically deleted.

**Constraint:** The dragged block must have a **top-level call variant**. If it is only an
extension method, the drop to a row gap must be rejected.

**Constraint:** If the dragged block was the head of its source chain, the new head (the next
block) must also have a top-level variant. If it does not, the drag should be rejected or the
chain should become invalid (TBD — validation concern).

**Status:** Implemented ✓ (top-level variant check not yet implemented)

### A2 — Block → same chain's append zone

Block removed from its current position, appended to the end of the same chain.
Example: `[A, B, C, D]`, drag `B` → same append zone → `[A, C, D, B]`.
**Status:** Implemented ✓

### A3 — Block → different chain's append zone

Block removed from source chain, appended to end of target chain.
If source chain becomes empty, it is deleted.
**Status:** Implemented ✓

### A4 — Block → chain insert zone (same or different chain)

Block removed from source, inserted before target block in target chain.

**Implementation note:** Clone the dragged block (new ID, same content) before remove+insert.
This eliminates all same-ID edge cases (e.g. drop before self) without any special-casing.

**Status:** Implemented ✓

### A5 — Block → empty slot

Block removed from source chain, placed as a new single-block nested chain in the slot.
**Status:** Implemented ✓

### A6 — Block → existing block (replace)

Target block is replaced by the dragged block. The rest of the chain is preserved.
Example: `[A, B, C]`, drop `X` onto `B` → `[A, X, C]`.

**Edge case — self-replace:** Dropping a block onto itself → no-op.
**Edge case — cycle:** Dropping block A onto a block nested inside A → no-op (silently ignored).
**Status:** Implemented ✓

---

## Chain Drag (Source B — Ctrl + block)

Payload = dragged block + all following blocks in the same source chain.
Follows the same rules as source A; the only difference is payload size.

### B1 — Chain drag → row gap

Payload blocks removed from source chain. Source chain retains blocks before the dragged one.
New top-level chain created at target row from the payload.
If source chain becomes empty, it is deleted.

**Constraint:** Same top-level variant check as A1 applies to the first block of the payload.
**Status:** Implemented ✓ (top-level variant check not yet implemented)

### B2 — Chain drag → same chain's append zone

Payload removed, appended to end of same chain. Always a net no-op (tail was already at end).
**Status:** Implemented ✓

### B3 — Chain drag → different chain's append zone

Payload removed from source, appended in order to end of target chain.
If source becomes empty, it is deleted.
**Status:** Implemented ✓

### B4 — Chain drag → chain insert zone (same or different chain)

Payload blocks inserted in order before target block.
**Implementation note:** Clone all payload blocks before remove+insert (same rationale as A4).
**Status:** Implemented ✓

### B5 — Chain drag → empty slot

Payload blocks removed from source, placed as a new nested chain in the slot.
**Status:** Implemented ✓

### B6 — Chain drag → existing block (replace)

Target block is replaced by the entire payload sequence in-place.
Example: target `[A, T, B]`, payload `[P, Q, R]` dropped onto `T` → `[A, P, Q, R, B]`.

**Edge case — cycle:** If the target block is a descendant of any block in the payload → no-op.
**Status:** Implemented ✓

---

## Row-Handle Drag (Source C)

### C1 — Row handle → row gap

The entire chain row is moved to the new row position. All other rows shift accordingly.
This is the **only** active drop target during a row-handle drag.
All chain append zones, insert zones, and slot zones are **hidden/inactive** during this drag.
**Status:** Implemented ✓

> **Note:** The source chain's own append/insert zones are suppressed during any drag that
> originates from that chain (applies to A, B, and C). Implemented via `DndState.sourceChainId`.

---

## Missing / Not Yet Implemented

| Case | Description                                              |
|------|----------------------------------------------------------|
| A1   | Top-level variant check before allowing row-gap drop     |
| B1   | Top-level variant check before allowing row-gap drop     |
