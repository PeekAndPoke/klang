Here's a detailed plan for adding the **expanded dual-canvas oscilloscope mode** to the `Oscilloscope` component.

---

## Plan: Expanded Oscilloscope Mode

### 1. New Props

Add two optional props to `Oscilloscope.Props`:

| Prop                  | Type     | Default               | Purpose                                       |
|-----------------------|----------|-----------------------|-----------------------------------------------|
| `expandedStrokeColor` | `Color`  | `Color.black`         | Waveform color on the expanded (right) canvas |
| `expandedStrokeWidth` | `Double` | same as `strokeWidth` | Line width on the expanded canvas             |

---

### 2. New State

Add the following fields to the component:

| Field                  | Type                        | Purpose                                                |
|------------------------|-----------------------------|--------------------------------------------------------|
| `expanded`             | `Boolean`                   | Toggle for normal vs. expanded mode                    |
| `overlayCanvas`        | `HTMLCanvasElement?`        | Reference to the second (right) canvas                 |
| `overlayCtx2d`         | `CanvasRenderingContext2D?` | 2D context for the overlay canvas                      |
| `overlayContainer`     | `HTMLDivElement?`           | A fixed-position container appended to `document.body` |
| `windowResizeListener` | `((Event) -> Unit)?`        | Listener to resize the overlay on window resize        |

---

### 3. Click Handler — Toggle Logic

Attach an `onClick` handler to the oscilloscope's root `div`:

- **Click (normal → expanded):**
    1. Set `expanded = true`
    2. Create the overlay (see step 4)
- **Click (expanded → normal):**
    1. Set `expanded = false`
    2. Tear down the overlay (see step 5)

---

### 4. Creating the Overlay

When entering expanded mode:

1. **Create a container `<div>`** programmatically (`document.createElement("div")`).
2. **Style it** with:
    - `position: fixed`
    - `top: 0`
    - `left: <oscilloscope right edge>px` — computed from `dom!!.getBoundingClientRect().right`
    - `width: calc(100vw - <left>px)` — spans the rest of the screen
    - `height: 100vh`
    - `pointer-events: none` — so it doesn't block interaction with underlying page elements
    - `z-index: 9999` — on top of everything
    - `background: transparent`
3. **Create a `<canvas>`** inside it, sized to fill the container.
4. **Append the container to `document.body`**.
5. **Store references** to the container, canvas, and its 2D context.
6. **Add a `window.onresize` listener** that:
    - Recalculates `left` from the oscilloscope's current bounding rect
    - Resizes the overlay canvas to match the new container dimensions
7. **Also observe the original oscilloscope's position** (via the existing `ResizeObserver` or a new one) so the
   overlay's `left` stays correct if the oscilloscope's container changes size.

---

### 5. Tearing Down the Overlay

When leaving expanded mode:

1. Remove the overlay container from `document.body`.
2. Remove the `window.onresize` listener.
3. Null out `overlayCanvas`, `overlayCtx2d`, `overlayContainer`.
4. Also clean up in `onUnmount` if still expanded (defensive).

---

### 6. Refactor `drawWaveform()` → Split Drawing

Refactor the current `drawWaveform()` into a **generic drawing function** that accepts parameters:

```
drawWaveformSlice(
    ctx: CanvasRenderingContext2D,
    canvasWidth: Double,
    canvasHeight: Double,
    strokeColor: Color,
    strokeWidth: Double,
    centerLineColor: Color?,
    centerLineWidth: Double,
    bufferStartFraction: Double,   // e.g., 0.0
    bufferEndFraction: Double,     // e.g., 0.1
    clearColor: String?            // null = transparent (clearRect only)
)
```

This function:

- Clears the canvas (with `clearRect`; if `clearColor` is non-null, fills with that color first)
- Draws the center line (if color provided)
- Draws only the **slice** of `visualizerBuffer` from `floor(bufferLength * startFraction)` to
  `floor(bufferLength * endFraction)`
- Maps that slice across the full canvas width
- Uses the provided stroke color and width

---

### 7. Update `drawWaveform()` Call Site

In `processVisualizer()` (or the renamed `drawWaveform()`):

**Normal mode** (`expanded == false`):

- Call `drawWaveformSlice(ctx2d, ..., bufferStart=0.0, bufferEnd=1.0, clearColor="black-ish")` — same as today, full
  buffer on the original canvas.

**Expanded mode** (`expanded == true`):

- Compute the **split fraction**: `splitFraction = originalCanvasWidth / (originalCanvasWidth + overlayCanvasWidth)`
    - e.g., if original is 200px and overlay is 1800px → `splitFraction = 0.1`
- **Left canvas** (original):
  `drawWaveformSlice(ctx2d, ..., bufferStart=0.0, bufferEnd=splitFraction, clearColor=<normal background>)` with normal
  stroke color.
- **Right canvas** (overlay):
  `drawWaveformSlice(overlayCtx2d, ..., bufferStart=splitFraction, bufferEnd=1.0, clearColor=null)` with
  `expandedStrokeColor` (default black), **no center line**, transparent background.

This means the waveform is seamlessly continuous across both canvases, each showing its proportional share.

---

### 8. Render Changes

The `render()` method stays mostly the same — it only renders the original `<div>` + `<canvas>`. The overlay is
created/destroyed imperatively via the DOM API (since it lives outside the component's DOM tree, appended to
`document.body`).

Add a `cursor: pointer` CSS to signal clickability:

```kotlin
override fun VDom.render() {
    div {
        key = "oscilloscope"
        css {
            height = 100.pct
            cursor = Cursor.pointer
        }
        onClickFunction = { toggleExpanded() }
        canvas("oscilloscope-canvas") {}
    }
}
```

---

### 9. Summary of Changes

| Area                      | What Changes                                                                         |
|---------------------------|--------------------------------------------------------------------------------------|
| **`Props`**               | Add `expandedStrokeColor`, `expandedStrokeWidth`                                     |
| **State**                 | Add `expanded`, `overlayCanvas`, `overlayCtx2d`, `overlayContainer`, resize listener |
| **`render()`**            | Add `cursor: pointer`, `onClick` → `toggleExpanded()`                                |
| **`toggleExpanded()`**    | New method: creates/destroys overlay, flips `expanded`                               |
| **`drawWaveform()`**      | Refactored into `drawWaveformSlice(...)` with buffer range params                    |
| **`processVisualizer()`** | Calls `drawWaveformSlice` once (normal) or twice (expanded) with correct fractions   |
| **`onUnmount`**           | Also tears down overlay if still active                                              |
| **`DashboardPage.kt`**    | No changes required (props are optional with defaults)                               |

### 10. Edge Cases to Handle

- **Window/container resize while expanded**: Recalculate overlay position and canvas dimensions.
- **Component unmount while expanded**: Clean up overlay from `document.body`.
- **Zero-width canvas**: Guard against division by zero if either canvas has zero width.
- **Overlay click-through**: `pointer-events: none` ensures the overlay doesn't steal clicks from the page — but the
  original oscilloscope's click still works to toggle back.

---

Want me to go ahead and implement this?
