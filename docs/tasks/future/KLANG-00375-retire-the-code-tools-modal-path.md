# KLANG-00375: Retire the code tools' modal path

> **Status (2026-10-27): IDEA.** the modal path is dead since KLANG-00374 step 3
>
> **Part of:** KLANG-00374 (The Sprudel tool: a function's tools in the bottom panel, following the cursor)

## Why this exists

Since KLANG-00374 step 3 the editor's tools show in the Sprudel tab only, embedded. Nothing in the app opens a tool
in a modal any more, except two fallbacks no sprudel tool reaches: the Sprudel tab's `render` branch for a tool that
is not `KlangUiToolEmbeddable`, and the sequence editor's atom-modal button (`CodeToolModal`) for a non-embeddable atom
tool. Reachable only through them (review of step 3, 2026-10-27): every tool's modal `render` branch,
`ToolButtonBar`, `KlangToolAutoUpdate` and its stored key `klang-tool-auto-update` (`StorageKeys.kt`),
`CodeToolModal`.

## Decide before implementing

- The tutorials' code players lost their tools with step 3 (they used the badge and the modal). If they get a
  Sprudel panel of their own, it is embedded too, and the modal path stays dead; if the maintainer wants the modal
  back there, this ticket does not happen.
- Whether `KlangUiTool` keeps `render` at all, or every tool is embeddable by type.

## The work

1. Remove the two fallbacks, the modal `render` branches, `ToolButtonBar`, `KlangToolAutoUpdate` (and its key),
   `CodeToolModal`.
2. Specs that read the bar (`ToolButtonBarSpec`, `ToolsEmbeddedSpec`'s modal rows) go or change with it.

## Acceptance

## History

- 2026-10-27: created.
