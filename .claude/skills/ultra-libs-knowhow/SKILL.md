---
name: ultra-libs-knowhow
description: Use when working with ultra libs, io.peekandpoke.ultra, ultra.html events, ultra streams, 
  ultra common utilities, or any io.peekandpoke.ultra.* package.
---

# Ultra Libs — Source Reference

All ultra library source code lives at:

```
/opt/dev/peekandpoke/ultra/ultra/
```

## Modules

| Module       | Package prefix                    | Source path       |
|--------------|-----------------------------------|-------------------|
| `html`       | `io.peekandpoke.ultra.html`       | `html/src/`       |
| `common`     | `io.peekandpoke.ultra.common`     | `common/src/`     |
| `streams`    | `io.peekandpoke.ultra.streams`    | `streams/src/`    |
| `kontainer`  | `io.peekandpoke.ultra.kontainer`  | `kontainer/src/`  |
| `semanticui` | `io.peekandpoke.ultra.semanticui` | `semanticui/src/` |
| `meta`       | `io.peekandpoke.ultra.meta`       | `meta/src/`       |
| `vault`      | `io.peekandpoke.ultra.vault`      | `vault/src/`      |
| `slumber`    | `io.peekandpoke.ultra.slumber`    | `slumber/src/`    |

## How to Use This Skill

When you need to know what an ultra module provides, read the relevant source files directly.
Don't guess — the source is authoritative.

## `ultra.html` Quick Reference

Key files:

- `html/src/jsMain/kotlin/dom_events.kt` — all DOM event handlers
- `html/src/commonMain/kotlin/helpers.kt` — `RenderFn`, `key`, `data()`, `debugId()`
- `html/src/commonMain/kotlin/inline_style.kt` — inline style helpers

### Mouse Events (JS only)

| Function       | Native event | Bubbles? | Handler type   |
|----------------|--------------|----------|----------------|
| `onMouseEnter` | `mouseenter` | **No**   | `MouseEvent`   |
| `onMouseLeave` | `mouseleave` | **No**   | `MouseEvent`   |
| `onMouseOver`  | `mouseover`  | **Yes**  | `MouseEvent`   |
| `onMouseOut`   | `mouseout`   | **Yes**  | `MouseEvent`   |
| `onMouseDown`  | `mousedown`  | Yes      | `MouseEvent`   |
| `onMouseUp`    | `mouseup`    | Yes      | `MouseEvent`   |
| `onMouseMove`  | `mousemove`  | Yes      | `MouseEvent`   |
| `onClick`      | `click`      | Yes      | `PointerEvent` |
| `onAuxClick`   | `auxclick`   | Yes      | `PointerEvent` |

**Important:** `onMouseEnter`/`onMouseLeave` do **not** bubble — `stopPropagation()` has no effect on them.
To get bubbling hover behaviour (e.g. "only innermost element hovered"), use `onMouseOver`/`onMouseOut`
with `event.stopPropagation()` instead.

### Other Notable Events

| Function                     | Notes                                              |
|------------------------------|----------------------------------------------------|
| `onClickStoppingEvent`       | calls `preventDefault` + `stopPropagation` for you |
| `onClickOrAuxClick`          | registers both click and middle-click              |
| `onContextMenuStoppingEvent` | prevents default context menu                      |
| `onFocus` / `onFocusIn`      | `onFocus` does NOT bubble; `onFocusIn` DOES        |
| `onBlur` / `onFocusOut`      | `onBlur` does NOT bubble; `onFocusOut` DOES        |
| `onKeyDown`                  | preferred over deprecated `onKeyPress`             |
| `onSubmit`                   | automatically calls `preventDefault`               |

## Notes

- Always read the actual source file before answering questions about an ultra module.
- The `html` module wraps `kotlinx-html` — event handlers are extension functions on `CommonAttributeGroupFacade`.
- CSS styling uses the `kotlinx-css` DSL via the `css {}` block (from the `kraft` framework, not ultra itself).

## HARD RULE

**Never edit, create, or delete any file under `/opt/dev/peekandpoke/ultra/`.** These are external library sources used
for documentation and reference only. All changes must happen in the Klang project (`/opt/dev/peekandpoke/klang/`).
