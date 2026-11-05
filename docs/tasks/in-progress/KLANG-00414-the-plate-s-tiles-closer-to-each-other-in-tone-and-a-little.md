# KLANG-00414: The plate's tiles: closer to each other in tone, and a little more metal than wood

> **Status (2026-11-05): IN PROGRESS.** Built; before and after screenshots sent. Waits for the maintainer's eye on the
> start page and the Klang Amp tiles visual.

## Why this exists

The maintainer (2026-11-05): "In the visual and the StartPage the color difference of the tiles is too stark. They
seem to be point to much into different directions. We need to make this variance a bit smaller." Then: "Can we also
make the tiles look a little more 'metallic' in terms of how they react to the light? Just a little, people still see
it as 'wood' and not as metal."

## The work

All in `MotorBackground`, the plate the start page and the visuals share:

- **Each tile's lean halved** (`TILE_TILT`, 0.08 to 0.04): every tile's surface leans its own random way, so the lamp
  caught each one differently.
- **A tighter shine** (`PLATE_ROUGHNESS`, 0.28 to 0.22): the lamp's highlight is brighter and smaller and follows the
  lamp as it swings, as on metal, where a soft wide sheen read as satin wood.
- **Fine hairlines under the strokes** (`HAIRLINE_DEPTH`, 0.03): one depth a texture row, along all of it, so the light
  runs along the lines as on brushed steel. The hand-brushed strokes stay on top.

Then (the maintainer, same day: "Can we increase the distance of the lamp a bit, so that the light cone gets wider
and the reflections are not as strong? The roughness is fine i think."; then "The look is more metallic now, good.
But the corners are pretty dark", the visuals "ok"):

- **The lamp hangs further away** (`SwingingLamp.DEFAULT_Z` 4.5 to 5.2, over "Make Music Now" 5.2 to 6.0), and its
  light at rest is a little lower (2.0 to 1.8): the wider cone caught more of the plate.
- **The start page's corners lighter**: its own dark oval over the plate (the visuals have none) ends at about 0.75 in
  the corners, once nearly black (0.97).

Taste values, judged by eye: no spec holds them.

## History

- 2026-11-05: created from the maintainer's two requests; built, screenshots before and after.
- 2026-11-05: the lamp further away and a little lower, the start page's corners lighter.
