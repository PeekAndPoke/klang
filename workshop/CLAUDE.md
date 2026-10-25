# Workshop

What Klang builds for Kraft before it moves there (maintainer, 2026-10-25). A piece grows here, proves itself in
Klang, and then moves into Kraft (the `ultra` repository); Klang then uses it from Kraft. The first piece is the key
manager (`keys/`, KLANG-00325).

## Rules of this module

| Rule | Level | Since | Why |
|------|-------|-------|-----|
| Depends on Kraft and Ultra only, never on a Klang module, not even through a test. | stone | 2026-10-25 | The move into Kraft is then a copy. The build keeps it: `build.gradle.kts` names Kraft alone. |
| Licensed Apache-2.0, as Ultra and Kraft are: `LICENSE` in this folder, no AGPL header in its files (the exception to the root licensing rule, as `tones/` is MIT). | stone | 2026-10-25 | The maintainer: "Same license as ultra, but we can also relicense once it moves." |
| Follows Kraft's patterns, so a Kraft maintainer finds what they expect: a service as an app attribute (`KraftApp.Builder.xyz()`, `val Component<*>.xyz`), a stage as `AutoMountedUi`, a component's machinery as a controller (`onKeyController()`, like `formController()`). | rule | 2026-10-25 | The maintainer liked exactly this in the first piece. |
| Machinery and look apart: the machinery never draws; the look is a renderer the app replaces, once for the app or per use, with a plain default. | rule | 2026-10-25 | The maintainer: "the user must be able to have its own components in own styles". |
| Klang's own looks and wiring stay in Klang (`klangui`, the app), never here: `KlangKeyHint`, `KlangKeyBindings`, the storage. | rule | 2026-10-25 | Only what any app could use belongs here. |
| Browser tests for everything (kotest, Kraft's `TestBed` for components), mutation-checked; a pure part (a layout) is a pure function with its own spec. | rule | 2026-10-25 | The project's review standard. |

The code style is Klang's (`/code-style`) apart from the header.

## The key manager (`keys/`)

| File | Role |
|------|------|
| `KeyPlatform.kt` | PC or Mac, detected once: `Mod` and the Mac's own keys follow it. |
| `KeyChord.kt` | One key with its modifiers (`"Mod-Shift-Enter"`): reading, matching against a key event, the label and the key cap parts. |
| `KeyAction.kt` | `KeyAction`, the `KeyBindings` bag, the `KeymapStore` interface. |
| `Keymap.kt` | Which keys trigger which action at one moment, with its problems (clash, reserved, unreachable, unreadable). |
| `KeysManager.kt` | The one listener: bags, the handler stack, the user's keys, the hints. |
| `OnKeyController.kt` | The machinery behind a key on a component. |
| `OnKey.kt` | The slim default: a controller and an entry on the stage. |
| `KeyHintStage.kt` | The layer all hints are drawn on, measured and placed. |
| `KeyHintLayout.kt` | Where hints go: a pure function. |
| `KeyOverview.kt` | A bag's overview: `KeyActionGroup`, the rows with their keys, the `KeyBindingsOverlay` component. |
| `KeyHints.kt`, `KeyCapGlyphs.kt` | The plain default look; the key signs as vector icons. |
| `_kraft.kt` | `KraftApp.Builder.keys(...)`, `Component.keys`, `Component.keyHintStage`. |

The plan, the decisions and what is open: KLANG-00325.

## Tests

`./gradlew :workshop:jsBrowserTest` through `console/with-build-lock.sh`; Karma port 10047.
