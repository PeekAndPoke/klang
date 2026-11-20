# KLANG-00478: Notstrom: ideas for the band's look

> **Status (2026-11-19): IDEA.** A place to collect ideas for how the band and the stage look; each one becomes a
> ticket of its own when it is taken up.
>
> **Part of:** KLANG-00447 (Live act: a band on a stage, played by the song)

## Why this exists

Ideas for the live act's look come up while the band is watched, and get lost in a session. They are kept here, each
with who had it and when, until one is chosen and gets its own ticket.

## The guiding feeling

The maintainer, 2026-11-19: "The whole style should always elude two feelings at once: f.e. beauty and fear ... On
the edge between them so it is hard to decide what it is." Every idea below is judged by it: never only beautiful,
never only frightening.

## Keep (the maintainer, 2026-11-19)

What the figures already have and must not be lost in any change of style or body:

- **Sirene's head:** its shape makes her look like an alien: "This we need to keep somehow."
- **Skalpell's shadow:** its shape "has something handsome we need to keep too".
- **Rune:** the Medusa hair in her shadow, and from the front she looks a little like the creature of the Alien
  films.
- **The ladies' eyes sit low in their heads** (Sirene, Rune): keep these proportions or stay close to them.
- **The hands stay mittens.** The critic proposed separate fingers for every figure; the maintainer, 2026-11-19, on
  the restyled bodies: "the hands make the looks to much more 'real'". The abstract hands are part of the uncanny.

Sirene's head as a sea shell (the maintainer, 2026-11-19, with a photo of a snail shell from above: "replace Sirenes
'hair' with a sea shell ... only the upper half. imagine the dark on the right is the face", and: "But the shape of the
'hair' / 'hat' must stay the same to still look 'alien'"): the outline of her egg skull stays exactly; its surface
becomes the shell's spiral, the apex at the back of the head, the whorls widening toward the front, and her face looks
out of the shell's opening with her low eyes inside it. Painted by Gemini in the gallery's characters section; the
turnaround keeps the size, the portrait drew the shell too large.

## The characters (a start)

The true selves on the wall need the characters first (the maintainer, 2026-11-19: "Before we can show the true self
we need the real figures... And their characters..."):

- **Rune:** mysterious ("geheimnisvoll"); her true self is the Medusa. Her own build and head (the target chosen
  2026-11-20: long and columnar, a little face, a skull that sweeps back into the tail of hair), and the tail hangs
  and swings on its own. The heels are hers (below).
- **Sirene:** attention seeking; her eyes go onto the wall (below). Her feet: the body of 2026-11-20 (2ffaee471)
  reads as high heels, which the maintainer does not want for her (2026-11-20): "she should wear comfortable
  'footware' so she can walk around a lot easily. Rune can he the heals." To be reworked once Rune is done.
  Her dress: "the 'belt' on her dress painting is sitting a little too low, which is most apparent from her back
  side view" (the maintainer, 2026-11-20). The belt is the braid pattern's hoop at 0.90 m, the height of her hip
  joint (the hoops lie every 0.45 m from the floor: knee, hip, collar); her waist sits higher. To be moved
  with the same rework.
  Her seat and legs, in the same rework (the maintainer, 2026-11-20): "Sirene now has to much 'butt', not a lot
  but a little too much. ... I would like to keep the high calf muscles and maybe we can slight accentuate the thigh
  muscles". And, the same day: "what i mean now, is that Sirenes 'butt' and the hipps are a little too much... as
  said not much just a little."
- **Klaue:** could be the band's "manager". The maintainer, 2026-11-19, on a Shadow Play restyle whose wall
  showed a boy with tousled hair behind him: "Klaue could be very styled hair (manager hair) but in reality he is
  still a child with 'wild' hair somehow." So: on the figure, sculpted, lacquered manager hair (faceless head
  kept); his true self on the wall, a wild-haired child in the same stance.
- **Skalpell, Beben, Wucht:** open.

The critic's proposals (2026-11-19, not decided; turnarounds, body changes and each true self painted by Gemini in
the third section of the gallery page): Skalpell the Surgeon (beauty and cruelty; his shadow holds a sword), Beben
the Watcher (tenderness and dread; a heron-giant whose neck bends down over the band), Sirene the Lure (a winged
siren with talons, her eyes in the shadow), Wucht the Bull (innocence and violence; a minotaur), Rune the Oracle
(the Medusa), Klaue the Manager (charm and predation; a wolf pulling strings, before the maintainer's child).
Below the neck all six share one body today (mitten hands, flipper feet, tube legs); the critic gives each its own
hands, shorter feet and a posture. Gemini gave Wucht and Klaue faces, which loses the uncanny: keep the heads
faceless.

## How the figures are built (decided 2026-11-20)

The figures come from Blender scripts (`klangviz/figures/`): a skeleton that Blender wraps in a skin. A head with a
face, a skull that flows into a tail and a neck that flows into the jaw cannot come out of that, so the head is built
as a surface of its own from a character's outlines. Asked under "complexity is the enemy", the maintainer,
2026-11-20: "yes a shared head builder seems to be the right move. We will end up with shared builder for the hands
as well i assume. And other parts maybe as well. ... In the end we might be even able to generate all meshes on the
clients, then we do not need to ship any assets. ... Keep the builders in python and blender for [now], we are
currently trying to find the right shapes, so use any tools that are easy to use for this task."

- One shared head builder beside `figure.py`; a figure's own file holds only its outlines and numbers. Rune is the
  first to use it.
- A shared hands builder and other parts may follow the same way.
- The builders stay Python and Blender while the shapes are being found; generating the meshes in the browser is a
  possible later step, not a goal now.

## The ideas

### The eyes: inside the skull, with a slight glow

The maintainer, 2026-11-20, for Rune and Sirene first, "also as a precedent for the rest of the chars": "Currently
the eyes appear 'bolted on to the head'. They need so sit inside the skull like real eyes do. When they are light
can they emit a slight glow? ... caricature glow again, very slightly to give the eyes a little more 'life' and less
of a painted on look."

- The eyes sit in sockets: set back into the skull under a brow, not laid onto its surface.
- A lit eye has a faint halo of its own colour round it, drawn, not computed (a caricature of a glow, as the kit's
  and the sun's are); very slight.
- After Rune's body and Sirene's feet; then the same for the other four.

### Sirene's eyes move to her shadow

The maintainer, 2026-11-19: "I like the idea that when the front light is on Sirene eyes move to the shadow."

When the low front lamp throws the band's big shadows on the back wall, Sirene's glowing eyes leave her face and
appear in her shadow on the wall: the shadow is her true self (the wall shadows already show each member's alter ego:
Rune's Medusa, Sirene's afro, Klaue's quiff), and the eyes say so. With the front lamp off, they are back in her face.

It came from a Gemini restyle of the warm still (direction A, Shadow Play) that put eyes into her wall shadow by
mistake; the maintainer liked the mistake.

Open: whether the eyes cross over (fade in the face, fade in on the wall as the lamp comes up) or jump with the lamp;
whether the eyes on the wall look where her face looks or at the camera; whether only Sirene does it (the frontwoman)
or the others too, rarely.

### Four style directions from a blind critique

2026-11-19: a Claude agent that was told nothing about the images judged six stills of the band (one per lighting
condition) and six close-ups, and proposed four directions; Gemini (Nano Banana 2.1) restyled the stills in each. The
stills and the restyles are in `prototypes/band/ignored/previews/` (git-ignored, on the maintainer's machine), the
side-by-side page is `prototypes/band/ignored/previews/gemini/index.html`.

- **A, Shadow Play** (the critic's pick): matte black figures with a thin rim light, a lit single-colour back wall at
  every lighting condition, crisp hard alter-ego shadows, a calm matte floor. The shadows become the signature.
- **B, Black Lacquer and Gold Inlay:** mirror-black figures, each member's pattern as gold inlay, a polished stone
  floor with reflections.
- **C, Blacklight:** everything matte black, each member's pattern glowing in their own neon colour; the most
  musical (the lines can pulse with each part), but the wall shadows go.
- **D, Ink Noir:** cel shading, thick ink outlines, halftone; solves readability, but looks like many games.

What the critic found weak in the renders as they are: the floor fog reads as cloudy smears with banding at the
riser and the ankles; the glitter on the skin reads as noise; black figures on the dark speaker-tile wall get lost
(the chain-link wall is much better); the instruments look primitive (headstocks, flat cymbals, the marimba's plain
blocks); the drum's lettering looks like a placeholder. It asked for alter egos as strong as Rune's Medusa for Wucht
and Beben.

The maintainer, 2026-11-19, on the four: "There are styles I like and quite few things I would avoid, f.e. the
cartoonish outlines. Also don't like the daft punk like glowing suites... But I like the gloomy light behind the
drummer with the realistic looking wire mesh and the reflective floor." So: no ink outlines, no cel shading, no
glowing suits; keep from B the gloomy glow behind the drummer, the wall as a realistic wire mesh, the mirror floor.

### Two worlds

The maintainer, 2026-11-19: "I would really like to express 'two worlds'... day and night or good and evil." The
critic, asked again with what was liked, proposed four ways in the realistic, gloomy style (lacquer and gold that
only catches light, a wire-mesh wall, a mirror floor); Gemini restyled the stills in each (the second section of the
gallery page). The maintainer: "The two worlds shots are amazing."

- **The fence** (the critic's pick): the mesh is the border between two worlds; inside each wall shadow one looks
  through the fence into a cold, moonlit world, so each alter ego is a window, not a dark patch; Sirene's eyes cross
  over into her shadow. In real time: the wall shader opens where the low lamp's shadow falls, a backdrop behind.
- **The underworld in the mirror:** the floor reflects the other world, cold, with the alter egos in the
  reflection; for one moment of a show (a bridge, the camera low), not all the time.
- **The divided stage:** day on one side, night on the other, the seam through the drummer (half sun, half moon);
  the seam can move with the song, and a member changes world as it passes.
- **Day and night as two stage states**, plus "ember" for good and evil: one time-of-day value drives the glow, the
  lacquer, the gold, the low lamp and the alter egos over a song.

The critic's recommendation: the fence as the core, driven over a song by the time-of-day value; the mirror for one
moment. Not decided.

The maintainer, 2026-11-19, on the restyles (the mirror underworld on the cold/hot still, kept as
`prototypes/band/ignored/previews/gemini/liked-fog-behind-mesh.webp`, and the day still with the sun behind the
drummer): "The idea of heading the fog behind the wire mesh is also nice. And the fog could give way to the
'shadows' ... I also like the image with 'sun' behind the drummer. These moods also already work well without the
'shadows'." So:

- **Fog behind the mesh:** the haze lives in the space behind the wire-mesh wall, lit from behind (a warm glow
  through the fog), not on the floor in front of the band.
- **The fog gives way to the shadows:** where a true self is shown, the fog behind the mesh parts or clears and the
  shadow appears in its place.
- **The sun behind the drummer:** a big low glow behind the drum riser, as in the day restyle.
- These moods stand on their own: a show can run whole stretches with no alter-ego shadows at all.

### The ghosts: a dark stage, the light behind the fence

The maintainer, 2026-11-20: "And on the scenery ... what could also have a mode, where the stage and the musician are
almost fully dark (different light colors possible) but behind the fence we see the 'ghosts'".

- The stage lights are almost off: the band, the instruments and the floor are near-black shapes with a thin rim of
  colour, their eyes the only bright points on them.
- All the light is behind the fence: the haze glows in one colour, and the pale figures in it (the shadows the haze
  gives way to) are the brightest thing in the picture, seen through the mesh as a black grid.
- The mirror floor carries only that glow, and the band's dark shapes cut out of it.
- Different light colours are possible. Painted by Gemini from a night still in three: a cold moonlit blue, a deep
  red, a sea green (`prototypes/band/ignored/previews/ghosts/ghost-mode-*.jpg`).
- A way to build it (not decided): one more world beside day, night and ember, with its colour as a second choice;
  later the show could go there by the music, in a quiet stretch.

## Built so far (2026-11-20, all behind switches and keys; the show's default look is unchanged)

The maintainer asked to build as many of the ideas as possible, with a key for each variant, judged by eye, without
code review for these steps. In the order built: the fence with the haze behind it and the sun behind the drummer
(key m, `?stage=mesh`); the mirror floor (key p, `?floor=mirror`); the haze giving way to the shadows (key h,
`?haze=parts`); Sirene's eyes in her shadow (key i, `?eyes=shadow`); black lacquer with gold that only catches the
light (key l, `?skin=lacquer`); two worlds as a time of day, with the cold world seen through the shadows at night
(key n, `?world=day|night|ember|drift`); the underworld in the mirror (key v, `?underworld=on`); and the whole new
look at once (key x, `?look=new`). Stills of each step: `prototypes/band/ignored/previews/step1/` to `step6/`.
Not built: the bodies and the true-self shadows (they need modelling and the characters decided).

## History

- 2026-11-19: created, with the eyes in Sirene's shadow and the four style directions.
- 2026-11-19: the guiding feeling, what to keep, the characters as far as named, the maintainer's choice among the styles, and the two worlds.
- 2026-11-19: Klaue's styled hair over a wild child; the critic's characters for all six.
- 2026-11-19: the fog behind the mesh that gives way to the shadows; the sun behind the drummer.
- 2026-11-19: the hands stay mittens; Sirene's head as a sea shell, its outline kept.
- 2026-11-20: steps 1 to 6 of the new look built behind keys (e17a1ffa3 to e52c06039).
- 2026-11-20: the ghosts: a dark stage with the light behind the fence, in several colours.
- 2026-11-20: Sirene's feet become comfortable ones, the heels are Rune's; Rune's target chosen.
- 2026-11-20: the belt of Sirene's dress; the eyes inside the skull with a slight glow.
- 2026-11-20: a shared head builder for the figures (the maintainer's yes).
- 2026-11-20: Sirene's seat a little smaller, calves kept, thighs a little more defined, with her feet.
