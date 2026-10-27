# KLANG-00368: The editor's palette per look: the code's colours as look roles

> **Status (2026-10-27): SCHEDULED.** its first step, the comments, after the Quick help commit
>
> **Part of:** KLANG-00334 (UI workbench: the Motor-centred layout, tool windows and the first-visit trail)

## Why this exists

The maintainer, 2026-10-27, once the grey text of Copper and Cream had moved towards their looks: "the comments in
the editor them need to match the theme similar to the grey text that moved towards the theme", and then: "But i
think we need a dedicated palette for the editor or at least plan for it".

Today the code's colours are fixed in `CodeMirrorTheme` (klangscript-ui): a One Dark palette (violet keywords, coral
names, blue functions, green strings, the comments grey), partly from the old `KlangTheme.Hex`, the same in every
look. The looks (KLANG-00339) set the app's grounds, inks and caps through `--kl-*` roles, but not the code.

## Decided (2026-10-27)

- The code gets a palette of its own in each look: look roles for it, as the grounds and inks have, read by the
  editor through `var(--kl-code-...)`. Default keeps today's colours exactly.
- The first step, small, after the Quick help commit: the comments (and the plain code text) on the look's colours,
  so Copper's and Cream's comments stop being Default's grey.

## Decide before implementing

- The roles, proposed: the code's ground, text, comment, keyword, name, function, number, string, operator, constant,
  the selection, the cursor, the gutter's numbers, the current line; the highlights of playing events (today gold)
  as roles too, or kept.
- Each look's palette: designed with the maintainer by eye (Copper warm, Cream soft), checked for contrast on the
  editor's black.
- Whether a look may leave a role out and take Default's (as the other roles do today).
- Which editors follow the look: every CodeMirror in the app (the song page, the tutorials, the docs REPL, the MIDI
  playground), proposed yes.

## The work

1. The first step: the comments and the plain text from the look (`--kl-ink-label`, `--kl-ink-text`), Default
   unchanged.
2. The code roles in `KlangRole` with Default's values; `CodeMirrorTheme` reads them.
3. Copper's and Cream's palettes, by eye with the maintainer.
4. The look picker's preview shows a line of code, if it helps choosing.
5. Specs (the roles' defaults equal today's colours), the review loop.

## Acceptance

- Each look has its own code colours; Default looks as today; a look switch recolours the code at once.

## History

- 2026-10-27: created from the maintainer's request.
