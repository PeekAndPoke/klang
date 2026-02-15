Got it. Here’s a more technical, neutral spec without checkmarks.

---

# Samples Library Page — Technical Specification (Handoff)

## Scope

Frontend page that enumerates the samples library with client-side search and grouping. Playback is stubbed for now.
Default grouping is **by bank**.

---

## Functional Requirements

### 1) Data Access

Expose a public accessor on `Player` to retrieve the `Samples` instance (from its deferred initialization).

### 2) Catalogue Construction

Build a frontend catalogue from `Samples`:

- Structure: list of entries `{ bankKey, soundKey }`
- Ordering:
    - bank with key `""` appears first
    - other banks sorted by key
- Display label:
    - empty bank key renders as `"(default)"`

### 3) Search

Input is normalized via `trim().lowercase()`.

- If normalized input is empty → show all entries
- Otherwise filter entries where:
    - `soundKey` contains query OR
    - `bankKey` contains query

### 4) Grouping Modes

Grouping options exposed via UI toggle:

1. Group by bank (default)
2. Group by sound
3. No grouping

Grouping is applied **after** filtering. Each displayed item includes both sound and bank labels for disambiguation.

### 5) Playback Hook (Stub)

Provide function `playSample(bankKey, soundKey)` with body containing a `TODO("Implement sample playback")`. UI play
button invokes this.

---

## UI Requirements

### Layout

- Search input with clear action
- Grouping toggle (segmented buttons or equivalent)
- Results list

### Results Rendering

- Default view: grouped by bank
- Each group header shows bank label (`"(default)"` for empty)
- Each item shows:
    - sound name
    - bank label
    - play button (stub)

---

## Acceptance Criteria

- Default grouping is **by bank**
- Default bank is listed first and labeled `"(default)"`
- Blank search shows all samples
- Same sound name in different banks appears as separate entries
- Grouping toggle updates display without losing search state
- Play button calls stub function

---

If you want this turned into a task checklist or expanded into a step-by-step implementation plan, say the word.
