---
name: kraft-knowhow
description: Use when working with Kraft UI framework, io.peekandpoke.kraft, Kraft components, VDom, Kraft routing,
  Kraft forms, Kraft modals, Kraft toasts, Kraft popups, or any io.peekandpoke.kraft.* package.
---

# Kraft UI Framework — Source Reference

All Kraft library source code lives at:

```
/opt/dev/peekandpoke/ultra/kraft/
```

## Modules

| Module       | Package prefix                    | Source path       |
|--------------|-----------------------------------|-------------------|
| `core`       | `io.peekandpoke.kraft`            | `core/src/`       |
| `semanticui` | `io.peekandpoke.kraft.semanticui` | `semanticui/src/` |
| `testing`    | `io.peekandpoke.kraft.testing`    | `testing/src/`    |

## Source Root Layout (core)

```
core/src/
├── commonMain/kotlin/routing/    # Shared routing model (Route, Route.Match, Route.Bound)
└── jsMain/kotlin/
    ├── components/               # Component base classes, Ctx, refs, state
    ├── forms/                    # FormComponent, FormController, FormField, validation
    ├── modals/                   # Modal system
    ├── toasts/                   # Toast notifications
    ├── popups/                   # Popup/tooltip system
    ├── messages/                 # Message handling
    ├── routing/                  # Client-side router, route builder DSL
    ├── vdom/                     # VDom abstraction + Preact integration
    ├── addons/                   # Built-in addons (files, images, pagination, styling, analytics)
    └── utils/                    # Responsive controller, misc utilities
```

## How to Use This Skill

When you need to know what a Kraft module or class provides, read the relevant source files directly.
Don't guess — the source is authoritative.

## Key Files Quick Reference

### Components (`io.peekandpoke.kraft.components`)

| File                                  | What it contains                                     |
|---------------------------------------|------------------------------------------------------|
| `components/Component.kt`             | `Component<PROPS>` base class, lifecycle hooks       |
| `components/PureComponent.kt`         | `PureComponent` for rendering optimization           |
| `components/Ctx.kt`                   | `Ctx` — component context passed to render           |
| `components/attributes.kt`            | Attribute helpers for components                     |
| `components/helpers.kt`               | `comp {}` builder, `noProps`, helper fns             |
| `components/functional_components.kt` | Functional component DSL                             |
| `components/functional_state.kt`      | State helpers for functional components              |
| `components/state/`                   | `ComponentStateProperty`, `ComponentStreamProperty`  |
| `components/ComponentRef.kt`          | `ComponentRef<C>` — typed ref to a mounted component |

### VDom (`io.peekandpoke.kraft.vdom`)

| File                      | What it contains                          |
|---------------------------|-------------------------------------------|
| `vdom/VDom.kt`            | `VDom` interface — the render context     |
| `vdom/VDomEngine.kt`      | `VDomEngine` — abstract rendering engine  |
| `vdom/VDomTagConsumer.kt` | Tag consumer bridge                       |
| `vdom/VDomElement.kt`     | VDom element wrapper                      |
| `vdom/CustomTag.kt`       | `CustomTag` — renders arbitrary HTML tags |
| `vdom/preact/`            | Preact-specific engine implementation     |

### Forms (`io.peekandpoke.kraft.forms`)

| File                          | What it contains                                            |
|-------------------------------|-------------------------------------------------------------|
| `forms/FormComponent.kt`      | `FormComponent<T>` — form root component                    |
| `forms/FormController.kt`     | `FormController` — manages form state and submission        |
| `forms/FormField.kt`          | `FormField<T>` — typed form field                           |
| `forms/FormFieldComponent.kt` | Base class for custom field components                      |
| `forms/AbstractFormField.kt`  | Base abstract field                                         |
| `forms/forms.kt`              | DSL helpers for building forms                              |
| `forms/events.kt`             | Form event types                                            |
| `forms/validation/`           | Validation rules: strings, numbers, comparable, collections |
| `forms/collections/`          | `ListFieldComponent` — list/collection field                |

### Routing (`io.peekandpoke.kraft.routing`)

| File                              | What it contains                                     |
|-----------------------------------|------------------------------------------------------|
| `commonMain/.../routing/Route.kt` | `Route`, `Route.Match`, `Route.Bound` — shared model |
| `jsMain/.../routing/`             | Client-side router, route builder DSL, history API   |

### Modals / Toasts / Popups

| Package                       | Source path             | Key classes        |
|-------------------------------|-------------------------|--------------------|
| `io.peekandpoke.kraft.modals` | `jsMain/kotlin/modals/` | `Modal`, modal DSL |
| `io.peekandpoke.kraft.toasts` | `jsMain/kotlin/toasts/` | `Toast`, toast DSL |
| `io.peekandpoke.kraft.popups` | `jsMain/kotlin/popups/` | `Popup`, popup DSL |

### SemanticUI / FomanticUI Integration (`io.peekandpoke.kraft.semanticui`)

```
semanticui/src/jsMain/kotlin/
├── forms/        # field_input.kt, field_checkbox.kt, field_textarea.kt, form_helpers.kt
├── dnd/          # Dnd.kt, drag-handle.kt, drop-target.kt
├── components/   # CollapsableComponent
├── modals/       # OkCancelModal, FadingModal
├── menu/         # DropdownMenu
├── popups/       # SemanticUiPopupComponent
├── pagination/   # PaginationEpp, PaginationPages
└── toasts/       # SemanticUI toast impl
```

### Optional Addons (separate Gradle modules)

| Addon           | Wraps                |
|-----------------|----------------------|
| `chartjs`       | Chart.js             |
| `konva`         | Konva canvas library |
| `marked`        | Markdown rendering   |
| `pdfjs`         | PDF.js               |
| `prismjs`       | Syntax highlighting  |
| `signaturepad`  | Signature pad        |
| `avatars`       | Avatar components    |
| `browserdetect` | Browser detection    |
| `jwtdecode`     | JWT decoding         |

## App Initialization Pattern

```kotlin
kraftApp {
    routing { /* route builder DSL */ }
    responsive(controller)
    modals()
    toasts()
    popups()
}.mount(selector, engine) { view() }
```

## Notes

- Always read the actual source file before answering questions about a Kraft class or API.
- Kraft renders via **Preact** (not React) — a lightweight VDom engine with zero JS dependencies.
- HTML markup uses the `kotlinx-html` DSL via the `VDom` render context.
- CSS styling uses `kotlinx-css` via the `css {}` block.
- `@KraftDsl` marks DSL builder functions — check for this annotation when exploring the API surface.

## Klang UI conventions (maintainer feedback, carried over 2026-09-06)

- **Custom classes via `.with()`**, not the `+` operator:
  `ui.compact.segment.with(laf.styles.popup()) { }`, never `(ui.compact.segment + laf.styles.popup()) { }`.
- **Stored `SemanticIconFn` values need `.render()`**: `icon.(domainIconFn(domain))().render()`.
  Direct calls such as `icon.search()` auto-render inside the builder; a stored function returns
  an unrendered `SemanticTag` and emits nothing without `.render()`.

- **Two sheets, two mount times** (KLANG-00337, 2026-10-25): `RawStyleSheet` mounts in its constructor, `StyleSheet`
  on the next animation frame, both into `<head>`. A rule in a raw sheet therefore loses every tie against a
  `StyleSheet` rule of the same weight, and a media query adds no weight. A raw rule that overrides (a
  `prefers-reduced-motion` block) carries one class more than its target. kotlinx-css writes `rule("&::after")` as a
  nested rule (native CSS nesting): it weighs like the flat selector, but reads one level down in `cssRules`.

- **A number that feeds kept state** (KLANG-00338, 2026-10-25): whatever keeps state derived from a number (a smoothed
  needle, an accumulator) treats a NaN or an infinity as no value at all, so one bad reading cannot latch the state
  for good. The audio engine's rule for cached configs, on the UI. Smoothing now lives in a stream, not in the view:
  Ultra's `smooth()` (`io.peekandpoke.ultra.streams.ops`, since Ultra 0.116.0; born in Klang's workshop, KLANG-00345)
  passes a non-finite value as `null`.
- **A stream read before anyone subscribes may not have its value yet** (KLANG-00345, 2026-10-26): Ultra's filtered
  and mapped streams compute their value from their source, and a filter reads `null` until it is subscribed. An
  operator that keeps state decides on the first value of a subscription (`smooth()`'s `settledIn`), never on what it
  read at construction; and read while nobody listens, it reads through to its source, as Ultra's own operators do.
- **`onNextProps` hands (new, old)** (KLANG-00338): the new props come first. The old `RoundGauge` (gone with
  KLANG-00347) took the second and followed one update behind until 2026-10-25.

- **A composite widget has one tab stop** (KLANG-00339, 2026-10-25): a menu, a list, tabs keep exactly one item in
  the tab order (`tabindex=0`, the last focused, else the first that can be chosen; the rest `-1`), and the arrow keys
  move between the items. Every item at `-1` makes the widget unreachable by Tab (`KlangMenu`).

- **A popup takes the focus and gives it back** (KLANG-00340, 2026-10-25): a popup that opens (a drawer, a dialog)
  moves the focus in, a frame after it is drawn (its first control, else the popup itself with `tabindex=-1`), so
  its Esc works from the first moment. Closing, it returns the focus to where it came from, but only when the focus
  was lost with it (`document.activeElement` is the body): a pick that focused a page's editor keeps it there
  (`KlangWorkbench`).
- **An `eventually` after a state change can pass on the old DOM** (KLANG-00340): a `by value()` write redraws in a
  microtask, so an assertion that the old DOM already satisfies ("still closed") passes before the redraw. Wait
  first for a sign of the new render (an attribute the change flips), then assert; for an effect a frame later
  (a focus moved in `requestAnimationFrame`), await the frames, then assert once.
- **A TestBed block runs in a `TestBedScope`** (Kraft 0.117.0, KLANG-00396, 2026-10-31): the block gets the `app` and
  its `router`, so a function reference handed on as the block (`test = test`) no longer compiles; wrap it
  (`{ test(it) }`, `KlangWorkbenchSpec`'s `keyedBed`). A spec moves between pages with `router.navToUri(uri)` and then
  `router.awaitUri(uri)` (`io.peekandpoke.kraft.testing`) before it reads the new page (`LearnPagesSpec`).
- **Moving a key boundary keeps alive what used to be fresh** (KLANG-00341, 2026-10-26): when a subtree stops
  remounting (the layouts kept mounted across pages), list what the remount did implicitly (a scroller's position,
  the focus, component state, a CSS animation restarted) and redo what must still happen (`RouterWithTransitions`
  scrolls the scrollers around the page up). A wrapper put between a parent and its child becomes the flex item or
  the block the child was: give it the child's box (`width: 100%` in a flex row), or the child shrinks.
- **Siblings that come and go carry keys** (KLANG-00341, 2026-10-26): Preact matches an unkeyed child by its type
  and place, so a `div` that appears before another `div` takes over that one's element, and a reference to it (a
  focus to give back) points at a detached node. Key the parts of a layout that appear and vanish (`KlangWorkbench`:
  the pinned window before the strip, the bottom panel before a floating window).
- **What is in front declares its own key, by priority** (KLANG-00341, KLANG-00325, 2026-10-26): Kraft's modals and
  popups open no key layer of their own (a code tool's modal, its popover), so a key in the page's layer would answer
  behind them. No guard checks for them: the thing in front declares its own Esc (`OnKey`, `onKeyController`) at a
  higher `KlangKeyPriorities` level (a code tool's `DialogClose` at Tool via `ToolButtonBar`, a panel's `PanelClose`
  at Panel, the page's at Page), and on one key the highest priority answers, the newest within one. Declarative only:
  a key lives and dies with the component that declares it (the maintainer: no imperative guards). A guard that read
  the open modals and popups was tried first and dropped: the editor's hover docs are a popup that stays open while
  one types, so it swallowed keys for no visible reason.

- **A yes/no question is klangui's `KlangYesNoModal`** (headline, content, `yesText`, `noText`, one `onResult`;
  KLANG-00323, 2026-10-21; named `YesNoModal` until 2026-10-26, now drawn as a `KlangDialog` inside Kraft's
  `FadingModal`: KLANG-00349), not Kraft's `OkCancelModal`: its buttons are divs that cannot take the keyboard.
  Fomantic paints a modal's `> .header`, `> .content` and `> .actions` white; klang.css themes them (for Fomantic
  modals; KlangYesNoModal no longer uses them). Kraft's
  `FadingModal.close()` fades for 500 ms before the handle's `onClose` runs, and `closeAll()` runs no `onClose` at all.
  Ultra confirmed all three and fixes them in Kraft (ULTRA-00233, after ultra's pause); keep KlangYesNoModal's workaround
  until a Kraft release carries the fix (Mission Control passes the version on).

## HARD RULE

**Never edit, create, or delete any file under `/opt/dev/peekandpoke/ultra/kraft/`.** These are external library
sources used for documentation and reference only. All changes must happen in the Klang project
(`/opt/dev/peekandpoke/klang/`).
