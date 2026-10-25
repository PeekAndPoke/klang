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
- **RoundGauge proportions** (settled by iteration): icon about 0.15 x gauge size, positioned
  between centre and top (top 30 %, centred via transform); label uses `borderBox` with a 2.5 px
  border; the canvas paints the circular background, not the Semantic UI label.

- **Two sheets, two mount times** (KLANG-00337, 2026-10-25): `RawStyleSheet` mounts in its constructor, `StyleSheet`
  on the next animation frame, both into `<head>`. A rule in a raw sheet therefore loses every tie against a
  `StyleSheet` rule of the same weight, and a media query adds no weight. A raw rule that overrides (a
  `prefers-reduced-motion` block) carries one class more than its target. kotlinx-css writes `rule("&::after")` as a
  nested rule (native CSS nesting): it weighs like the flat selector, but reads one level down in `cssRules`.

- **A yes/no question is klangui's `YesNoModal`** (headline, content, `yesText`, `noText`, one `onResult`;
  KLANG-00323, 2026-10-21), not Kraft's `OkCancelModal`: its buttons are divs that cannot take the keyboard.
  Fomantic paints a modal's `> .header`, `> .content` and `> .actions` white; klang.css themes them. Kraft's
  `FadingModal.close()` fades for 500 ms before the handle's `onClose` runs, and `closeAll()` runs no `onClose` at all.
  Ultra confirmed all three and fixes them in Kraft (ULTRA-00233, after ultra's pause); keep YesNoModal's workaround
  until a Kraft release carries the fix (Mission Control passes the version on).

## HARD RULE

**Never edit, create, or delete any file under `/opt/dev/peekandpoke/ultra/kraft/`.** These are external library
sources used for documentation and reference only. All changes must happen in the Klang project
(`/opt/dev/peekandpoke/klang/`).
