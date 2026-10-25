# UI workbench canvas (2026-10-25)

The source of the design canvas for KLANG-00334 (UI workbench), made with the maintainer on 2026-10-25. The live
canvas is private to the maintainer's claude.ai account: https://claude.ai/artifact/Gvk4hUJkSWiZfphMrCrnXT

This folder is the copy that lasts. The canvas on claude.ai is a hosted page; this copy is what we keep in the repo.

## What is here

| File | Board |
|------|-------|
| `canvas.json` | The index: where each board sits on the canvas, the titles, the notes. |
| `Main.dc.html` | A: the workbench, tool windows flying in from the right, the song menu; the `trailStep` tweak plays the first-visit trail. |
| `LeftStack.dc.html` | B: tools stacked in the left column above the Motor (for comparison). |
| `Launcher.dc.html` | C: the menu drawer open, and the search on Mod K. |
| `MotorDetail.dc.html` | D: the Motor at 2.5 times its size, the menu button in the fifth spot. |
| `Trail.dc.html` | E: the first-visit trail, six steps. |
| `KitchenSink.dc.html` | Every UI element in the app today and every new one, with the file it lives in. |
| `Motor.dc.html`, `Editor.dc.html`, `Drawer.dc.html` | Shared parts the boards import. |

## The format

Each `.dc.html` is one board of a claude.ai Design canvas: plain HTML with `{{holes}}`, `<sc-for>`, `<sc-if>` and
`<dc-import>`, and a small `class Component extends DCLogic` script. The files do not run on their own (they need
the canvas runtime, `support.js`); they are kept to be read, compared and republished.

To bring the canvas back, or to start a new one from these files: ask Claude Code to publish this folder as a
Design canvas (each file goes under `project/` with the same name).

The canvas may be newer than this copy when the maintainer edits it in the browser; copy it again after a design
round.
