# KLANG-00442: Page changes without the mounted fade: the old page goes at once, the browser's view transition tried on top

> **Status (2026-11-13): DONE.** The maintainer: "transition is nice now!" The browser's view transition stays.

## Why this exists

The maintainer (2026-11-13): "I think i would like to remove the fade in/out between the pages ... it does not
really work, especially when the vdom has a lot to do then the transition is never smooth."

`RouterWithTransitions` kept the page left mounted for 200 ms on top of the next one and faded it with a CSS
animation. That could not be smooth:

- the page left was drawn again in the very frames in which the next page mounts, the heaviest work of a page change;
- the fade ran while the main thread was busy, so most of its 200 ms had passed before the browser painted again;
- a ticker of 100 ms cleaned up, so the page left lived up to 100 ms longer, with its subscriptions and editors.

## Decided (the maintainer, 2026-11-13)

- The mounted fade goes. In its place the browser's view transition is tried (`document.startViewTransition`): the
  browser takes a picture of the page left, the next page is drawn, and the browser fades the picture into it, off the
  main thread and only once the next page is there. Asked whether a screenshot of the old page could be faded instead
  of the component, this is that, done by the browser; a screenshot of our own (the DOM drawn into a canvas) is slow
  and wrong for the editor, canvases and WebGL.
- "If the browser's help is also not enough we fully remove it and rename the component."

## The work

1. **Done (2026-11-13): the page left goes at once, the browser fades.** `RouterWithTransitions`:
   - one route is drawn (`shown`); the copies of pages left, their `inert` handling, the ticker and the `.fade-out`
     CSS are gone;
   - a new path is handed to `startViewTransition`; the latest route is drawn inside its callback. Kraft redraws
     in a microtask (Preact's `setState`), which runs before the browser hears that the callback is done, so the
     callback's promise needs no waiting (first built with one that resolved on the redraw or after 100 ms; review
     round 2 showed by a mutant that it could not be observed, and it went). If Kraft's redraw ever moves off the
     microtask, the fade would start on the page left: that is the place to look;
   - a page change that follows before the browser called back skips the earlier one, whose callback draws
     nothing: the last one draws the latest page, so its picture is still of the page left;
   - **the whole window fades**, the browser's default. First built with only the page fading (`.router-page`
     named, the root not): review round 1 found that the page's picture is drawn above everything and is not
     clipped by its scroller, so it would cover a tool window flying in, spill over a pinned bottom panel, and
     slide by the scroll offset. One picture of the window has none of that;
   - no fade where the browser has no view transitions or the user asked for reduced motion: the page changes at once;
   - a new query alone changes nothing of this, as before; the layouts stay mounted and the scrollers go up, as before.
   - **A page takes its route from its mount, not from `router.current`.** The browser calls back a frame after it
     was asked; for that frame the page left still stands while the router already says the next route. The three
     tutorial pages followed `router.current` and would have drawn the next lesson inside the page left, before the
     picture (twice the work, and nothing to fade). They now get the route as props (`mount(route) { Page(it) }`),
     which changes with the page. The old fade had the same flaw: its fading copy of a tutorial showed the next one.
     The columns and the layout still follow the router; they are no pages.
   - **What guarded the fading copy is gone:** `PageWorkbench.fill()`'s and `CodeSongPage`'s `inert` checks and the
     spec case for them (scaffolding for the removed copy; a page that mounts fills, also under the open Motor
     menu). `TutorialPage` reports only its own lesson as opened. `EditorKeysCorner` keeps its check: the page is
     inert under the Motor's menu.
2. **Done (2026-11-13): the maintainer looked at it:** "transition is nice now!"
3. **It stays.** The other outcome (the fade removed fully, the component renamed) is not needed.

## Known limits

- While the next page mounts, the picture of the window stands still: a heavy page shows as a short pause, then
  the fade. The wait itself is not shortened.
- During the fade a click lands on the picture, not on the page (about 200 ms).
- The whole window fades, so what moves on it (a meter, a visual) shows doubled for the fade's 200 ms.
- For the one frame until the browser calls back, what follows the router itself (the layout, the sidebar, the
  status bar, the columns) already shows the next route: those parts switch before the picture and do not fade.
- Whether Firefox has view transitions was not looked up (web lookups were off in the session); where they are
  missing the page changes at once.
- What the browser does with a picture was reasoned from memory of the View Transitions specification, not looked
  up; the specs prove the component's side (what it hands over, and that the page left stands until the callback),
  the look is the maintainer's to judge.

## Acceptance

- The maintainer finds page changes smooth, or the fade is removed and the component renamed.
- `RouterWithTransitionsSpec` and `LearnPagesSpec` green; the new cases mutation-checked.

## History

- 2026-11-13: created.
- 2026-11-13: step 1: the mounted fade is gone, a new path is handed to the browser's view transition, the whole
  window fades; the tutorial pages take their route from their mount; the guards of the fading copy removed. Review
  round 1 (reviewer-opus-high): NOT CLEAN, 2 MAJOR (the page's stacking rule removed; a picture of the page alone is
  drawn above everything, unclipped), 3 MINOR, 1 NIT; fixed. Round 2 (reviewer-opus-high): CLEAN, 3 MINOR (the wait
  for the redraw not observable and removed; a skipped page change drew before the next one's picture) and 2 NIT
  applied. 14 mutants killed over three campaigns. Waiting for the maintainer's eye.
- 2026-11-13: DONE. The maintainer: "transition is nice now!" The browser's view transition stays.
