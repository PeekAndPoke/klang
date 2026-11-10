# Workshop

What Klang builds for Kraft and Ultra before it moves there (maintainer, 2026-10-25; Ultra's pieces since
2026-11-10). A piece grows here, proves itself in Klang, and then moves into the `ultra` repository; Klang then uses
it from there. The first piece is the key manager (`keys/`, KLANG-00325), the second the stored documents
(`storage/`, KLANG-00422).

## Where the pieces are heading

The maintainer (2026-11-10): the workshop holds "a small readme or similar as a memory for where the features are
heading inside ultra". This is it; a piece gets its line when it is added.

| Piece | Here | Heading for | In place of, or beside |
|-------|------|-------------|------------------------|
| The key manager | `keys/` (JS) | Kraft: a service on the app, as `popups` and `modals` are | nothing there yet |
| `persist(storage)` and `WatchedStreamStorage` | `storage/persist.kt` (shared code) | Ultra's `streams`: an operator beside `persistInLocalStorage` | Ultra's `PersistentStreamSource` is internal and bound to one storage; this is the same idea with the storage handed in, and it follows a storage that changes from elsewhere |
| `DocumentStorage`, `StoredDocument`, `changedFields`, `mergeFields` | `storage/` (shared code) | Ultra's `streams`, or a small module of its own beside it (it needs kotlinx.serialization's JSON) | `LocalStorageStreamStorage`, which writes the whole value; this one merges what changed, keeps a version, puts aside what it cannot read, and reads old keys once |
| `KeyValueStore`, `MemoryKeyValueStore` | `storage/` (shared code) | with `DocumentStorage` | nothing there yet |
| `BrowserKeyValueStore` | `storage/` (JS) | Ultra's `streams`, its JS part | the `window.localStorage` calls inside `LocalStorageStreamStorage` |

Not built yet, and expected in the same place: a store that keeps on a server, and a store of several layers (the
browser first, the server behind it).

## Rules of this module

| Rule | Level | Since | Why |
|------|-------|-------|-----|
| Depends on Kraft and Ultra only (and what they depend on themselves: kotlinx.serialization), never on a Klang module, not even through a test. | stone | 2026-10-25 | The move is then a copy. The build keeps it: `build.gradle.kts` names Kraft, Ultra's streams and kotlinx.serialization. |
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
| `KeySequence.kt` | Keys in a row (`KeyBinding`): two modifier taps (`"Shift Shift"`) or two chords (`"Mod-K Mod-S"`). |
| `Keymap.kt` | Which keys trigger which action at one moment, with its problems (clash, sequence clash, prefix clash, reserved, unreachable, unreadable). |
| `KeysManager.kt` | The one listener: bags, the handler stack, the user's keys, the hints. |
| `OnKeyController.kt` | The machinery behind a key on a component. |
| `OnKey.kt` | The slim default: a controller and an entry on the stage. |
| `KeyHintStage.kt` | The layer all hints are drawn on, measured and placed. |
| `KeyHintLayout.kt` | Where hints go: a pure function. |
| `KeyOverview.kt` | A bag's overview: `KeyActionGroup`, the rows with their keys, the `KeyBindingsOverlay` component. |
| `KeyHints.kt`, `KeyCapGlyphs.kt` | The plain default look; the key signs as vector icons. |
| `_kraft.kt` | `KraftApp.Builder.keys(...)`, `Component.keys`, `Component.keyHintStage`. |

The plan, the decisions and what is open: KLANG-00325.

## The stored documents (`storage/`)

| File | Role |
|------|------|
| `persist.kt` | `StreamSource.persist(storage)`: shown from the storage, written to it, told of its changes from elsewhere (`WatchedStreamStorage`). |
| `DocumentStorage.kt` | A data class as one versioned JSON document under one key: a write merges the changed top-level fields into what is kept; an unreadable document is put aside; old keys are read once. |
| `JsonMerge.kt` | `changedFields`, `mergeFields`: the two pure functions a browser's store and a server share. |
| `StoredDocument.kt` | The document's shape (`v`, `data`) and the one `Json` that reads and writes it. |
| `KeyValueStore.kt` | Texts under keys, with `watch`; the store in memory. |
| `BrowserKeyValueStore.kt` (JS) | `localStorage` or `sessionStorage` as a store; the browser's `storage` event behind `watch`; `orMemory` where the browser gives no storage. |

Shared code (`commonMain`), so the JVM runs its specs too: a server keeps and merges the same documents. The plan,
the scopes (the user, this machine, this tab) and what is open: KLANG-00422.

## Tests

`./gradlew :workshop:jsBrowserTest` and `:workshop:jvmTest` (the shared code's specs run on both) through
`console/with-build-lock.sh`; Karma port 10047.
