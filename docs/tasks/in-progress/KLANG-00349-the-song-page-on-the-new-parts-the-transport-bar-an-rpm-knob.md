# KLANG-00349: The song page on the new parts: the transport bar, an RPM knob, Alt+Q's help panel, the song in the left column

> **Status (2026-10-26): IN PROGRESS.** started
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

After the start page (KLANG-00347) the user lands on the song page, the first song open. Its header is still the old
one: Fomantic buttons, the browser's number fields with their +/- arrows ("this looks weird in different browsers and
os-es"), the title as a field in the header. The canvas (board A, `Main.dc.html`) draws a slim transport bar over the
editor, the song in the left column ("This song", files, imports, sounds in use, versions) and the help as a tool
window flying in from the right.

## Today (read from `CodeSongPage.kt`)

The header holds Play/Update, Stop, the cycle LCD (`LcdDisplay`), a reset to the original (built-in songs, when
changed), RPM and EVT (highlights per audio event) as Fomantic number fields, the title as a field, copy the code, and
the fullscreen toggle. The editor shows a symbol's quick help on hover (`hoverPopup`, `hoverContent` in
`CodeMirrorComp`).

## Decided (the maintainer, 2026-10-26)

- **RPM is a knob with an LCD**: a knob like the volume knob, its value on a small LCD beside it like the cycle
  counter; it turns by drag (up, down, and sideways for a knob at the screen's top) and by the arrow keys, and a
  click on the LCD lets one type a number. No browser steppers anywhere. A new klangui part, `KlangValueKnob`, with a
  configurable precision that the LCD always shows ("the RPM might really need 2 decimal points"); RPM 10 to 120,
  step 0.5, 2 decimals.
- **The header is the canvas's transport bar**: Play/Update, Stop, the cycle LCD, the RPM knob, and fullscreen as a
  small key.
- **The left column shows the song** ("This song"): its title (editable), reset to the original, copy the code;
  files, imports, sounds in use and versions as the canvas draws them, where there is data for them today.
- **The song list gets a page of its own** (the maintainer, 2026-10-26, asked where the list goes once the column
  shows the open song: "A songs page"): the drawer's "Songs" opens it, the built-in songs and the user's; on a song
  the column shows only that song.
- **EVT** (highlights per audio event) moves to a settings place.
- **Alt+Q on a name opens its help in a tool window** flying in from the right, as designed. The hover help becomes
  a setting, **off by default**, in the same settings place.
- **Teaching the keys**: the first song (Final Synthasy VII Prelude) opens with a few short comments: Alt+Q on a name
  for its help, Alt+M for the Motor's menu, and the like. The first-visit trail can add a lamp later.

## Decide before implementing

- The settings place for EVT and the hover help (a tool window, the Motor's drawer, or a page): proposed when it is
  built.
- The RPM knob's range and step, and what typing accepts (taken from today's field: step 0.5).
- What "files", "imports", "sounds in use" and "versions" show with today's data (one file, the imports parsed from
  the code, the sounds the code names, no versions until the backend).

## The work

1. **klangui: the RPM knob with its LCD** (a knob, an LCD that takes typing), its spec, the kitchen sink.
2. **The transport bar** on klangui parts.
3. **The song in the left column**, the title and the actions moved there.
3a. **The songs page**: the song list, reached from the drawer's "Songs".
4. **Alt+Q and the help tool window**; the hover help behind a setting, off by default; the settings place with EVT.
5. **The first song's comments** that teach the keys.
6. The review loop; the maintainer's look in the browser.

## Review and checks (2026-10-26)

- **Step 1, the value knob, round 1** (level 1): 1 MAJOR (the field's text re-set on every redraw, and the song page
  redraws on every cycle: typing was nearly impossible while a song played), fixed: the field takes the value once
  when it opens; 5 MINOR (the typed value lost when grabbing the knob, a percent to screen readers, the focus lost
  after Enter or Esc, Space, the drag off the step grid) fixed but the last, kept by the maintainer's wish (a fine
  drag); 4 NIT, 3 fixed, iOS focus noted.
- **Step 1, round 2** (level 2, two phases): clean; 1 MINOR (no spec for the drag's base read at the first move) and
  3 NIT applied.
- **Mutation-checked:** 16 mutants over the value knob and the knob's drag, all killed for the right reason (one
  spec host first let a mutant survive: its lambda captured nothing, so Kotlin/JS kept one instance and the knob did
  not redraw; it captures state now).

## Acceptance

- The song page's header, left column and help follow the canvas on klangui parts; no browser number steppers.
- Alt+Q opens a name's help; hovering does nothing unless the setting is on.
- The specs of the new parts green and mutation-checked; the review loop ends clean.

## History

- 2026-10-26: created; the design decided with the maintainer (the RPM knob, the hover setting, the comments, the
  header).
- 2026-10-26: IN PROGRESS. started
