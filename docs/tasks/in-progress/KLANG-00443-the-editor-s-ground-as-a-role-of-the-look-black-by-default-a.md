# KLANG-00443: The editor's ground as a role of the look: black by default, a faint pink in Pink Noir

> **Status (2026-11-13): IN PROGRESS.** Built; in review.

## Why this exists

The maintainer (2026-11-13), on the Pink Noir look: "In the pink theme can we give the editor a very faint pink
background?" The code editor's ground was a fixed black (`CodeMirrorTheme.background = "#000000"`) for every look,
so no look could tint it.

## The work

- **A role of the look: `KlangRole.GroundEditor`** (`--kl-ground-editor`); first black by default, then `#010102`
  (below). The look editor lists it in "Screen, an old tube" as "Ground" (the maintainer: "Would fit
  here"), and that box's preview screen stands on it, so an own look can tint the editor and see it.
- **Pink Noir sets it to `#10080d`**, a very faint pink. The value is the maintainer's to tune by eye.
- **Every look says its own, almost black towards its menu's ground** (the maintainer, 2026-11-13: "every theme
  has to define it. Almost black but in the direction of the menu background color. Default #010102"). The role's
  default is `#010102` (Classic, which sets nothing, about 7 % of the menu ground `#12131c`); the other built-in
  looks carry 7 % of their own `GroundDeep`. An own look made before has no value and gets the default.
- **Its users:** the CodeMirror theme (the editor's surface and its gutters), the song page's screen around the
  editor (review round 1, MAJOR: it was left black, a black band beside the gutter), the screen around a
  tutorial's example (`PlayableCodeExample`) and the look editor's preview, all through the CSS variable, so a
  look change shows at once.
- **How dark the tube's corners get: `KlangRole.Vignette`** (`--kl-vignette`, an amount, 0.3 by default, the value
  the glass had fixed), "Dark corners" in the same box (the maintainer: "a variable that stears how much the editor
  screen gets darker towards the corner"). On a black ground the corners do not show; on a tinted one they do.
  The darkening is four rims, one along each edge, each easing out towards the middle over a quarter of the screen
  (160px at most), so it follows the screen's rectangle and has no edge of its own (the maintainer: "a little
  smoother", then "less like a simple ellipse but closer to the rectangular shape of the screen"). Two rims lie
  over each other in a corner, which comes out about as dark as the amount says; the middle of an edge about half.
  Before, it was one ellipse, clear to 70 % of the way out and then a straight ramp.
- **The minimap's shadow is of the editor's ground, not of black** (the maintainer: "the minimap background should
  be a little transparent, maybe 10%"): the veil over the part of the minimap not in view dimmed everything towards
  black, so the strip stood black beside a tinted editor. It now dims the minimap's code towards the ground, at the
  strengths it had (72 %, 60 % while dragging, 55 % under the mouse); with a black ground nothing changes. The
  minimap's own filter (less colour, 70 % brightness) still makes its ground a little darker than the editor's.
- `GroundSunken`'s KDoc named the editor among its wells; the editor never used it, and the KDoc no longer says so.

## Not done

- The glass lays a faint grey-green over the whole screen (3 %), so dark lines show on pure black; on a tinted
  ground it mutes the tint a little. Left as it is.

## Acceptance

- The maintainer finds the editor's ground, the corners and the minimap right in Pink Noir; the other looks keep
  their black editor.
- `KlangLooksSpec` says which looks set the editor's ground.

## History

- 2026-11-13: created.
- 2026-11-13: built: `GroundEditor` and `Vignette`, Pink Noir's faint pink, the song page's and the look editor's
  screens on the editor's ground, the minimap's shadow of the ground's colour. Review round 1 (reviewer-opus-high):
  NOT CLEAN, 1 MAJOR (the song page's screen left black), 1 MINOR, 2 NIT; fixed. Round 2 (reviewer-opus-high): CLEAN,
  2 NIT applied. The corners' darkening eased and made rectangular afterwards, on the maintainer's word. The spec pins constants; no mutation
  campaign. The values are the maintainer's to tune by eye.
- 2026-11-13: the default is `#010102` and every built-in look but Classic sets its own editor ground, 7 % of its
  menu ground (the maintainer's word).
