/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switch
import io.peekandpoke.klang.viz.liveact.Switches

/**
 * The variants of the band's look chosen while the show plays (KLANG-00478): what stands behind the band ([stage]),
 * the floor ([floor]), the haze behind the fence ([haze]), where Sirene's eyes glow under the front lamp ([eyes]),
 * what the band's bodies are made of ([skin]), the two worlds' time of day ([world]) and the ghosts' colour
 * ([ghosts]; no key, it shows in the ghosts world only), the other world in the mirror
 * floor ([underworld]), how uneven the mirror floor's stone is ([ripple]), how blurred its reflection is ([blur]) and
 * what stone it is ([stone]); and a working aid, the stage's outline ([outline]). A later variant joins them here, with
 * its key and its line.
 *
 * They start as the address asks ([of]); a key turns one of them over ([nextStage], the key m; [nextFloor], the key
 * p; [nextHaze], the key h; [nextEyes], the key i; [nextSkin], the key l; [nextWorld], the key n; [nextUnderworld],
 * the key v; [nextRipple], the key b; [nextBlur], the key c; [nextStone], the key k; [nextOutline], the key j), and
 * the line at the top says the new one in a few words for a moment ([stageLine], [floorLine], [hazeLine], [eyesLine],
 * [skinLine], [worldLine], [underworldLine], [rippleLine], [blurLine], [stoneLine], [outlineLine]). The key x flips all
 * of them at once between the look as made and the new look ([NEW], [LookFlip]).
 */
data class LookVariants(
    val stage: StageLook = StageLook.Wall,
    val floor: FloorLook = FloorLook.Plain,
    val haze: HazeLook = HazeLook.Whole,
    val eyes: EyesLook = EyesLook.Face,
    val skin: SkinLook = SkinLook.Suit,
    val world: WorldLook = WorldLook.Off,
    val ghosts: GhostLight = GhostLight.Cold,
    val underworld: UnderworldLook = UnderworldLook.Off,
    val ripple: RippleLook = RippleLook.Gentle,
    val blur: BlurLook = BlurLook.Crisp,
    val stone: StoneLook = StoneLook.Plain,
    val outline: OutlineLook = OutlineLook.Off,
) {
    /** The next of the stage's looks, after the last the first again. */
    fun nextStage(): LookVariants = copy(stage = StageLook.entries[(stage.ordinal + 1) % StageLook.entries.size])

    /** The next of the floors, after the last the first again. */
    fun nextFloor(): LookVariants = copy(floor = FloorLook.entries[(floor.ordinal + 1) % FloorLook.entries.size])

    /** The other of the haze's looks. */
    fun nextHaze(): LookVariants = copy(haze = HazeLook.entries[(haze.ordinal + 1) % HazeLook.entries.size])

    /** The other place for Sirene's eyes. */
    fun nextEyes(): LookVariants = copy(eyes = EyesLook.entries[(eyes.ordinal + 1) % EyesLook.entries.size])

    /** The other of the bodies' skins. */
    fun nextSkin(): LookVariants = copy(skin = SkinLook.entries[(skin.ordinal + 1) % SkinLook.entries.size])

    /** The next time of day (off, day, night, ember, ghosts, drift), after the last off again. */
    fun nextWorld(): LookVariants = copy(world = WorldLook.entries[(world.ordinal + 1) % WorldLook.entries.size])

    /** The other of the mirror's worlds: the stage, or the underworld. */
    fun nextUnderworld(): LookVariants =
        copy(underworld = UnderworldLook.entries[(underworld.ordinal + 1) % UnderworldLook.entries.size])

    /** The next of the stone's unevennesses (none, faint, gentle, strong), after the last none again. */
    fun nextRipple(): LookVariants = copy(ripple = RippleLook.entries[(ripple.ordinal + 1) % RippleLook.entries.size])

    /** The next of the reflection's blurs (crisp, soft, stone), after the last crisp again. */
    fun nextBlur(): LookVariants = copy(blur = BlurLook.entries[(blur.ordinal + 1) % BlurLook.entries.size])

    /** The other of the mirror floor's stones: plain, or marble. */
    fun nextStone(): LookVariants = copy(stone = StoneLook.entries[(stone.ordinal + 1) % StoneLook.entries.size])

    /** The stage's outline on or off (a working aid). */
    fun nextOutline(): LookVariants = copy(outline = OutlineLook.entries[(outline.ordinal + 1) % OutlineLook.entries.size])

    /**
     * Whether the haze gives way to the band's shadows: where it is chosen and the fence stands (only the fence has the
     * haze).
     */
    val partsHaze: Boolean
        get() = haze == HazeLook.Parts && stage == StageLook.Mesh

    /**
     * Whether the mirror is drawn with the reflections at [reflections]: where the mirror floor is chosen and the
     * reflections are on, at every fog.
     */
    fun mirrors(reflections: ReflectionQuality): Boolean = floor == FloorLook.Mirror && reflections == ReflectionQuality.On

    /**
     * Whether the mirror shows the underworld with the reflections at [reflections]: where it is chosen and the mirror
     * is drawn ([mirrors]).
     */
    fun underworlds(reflections: ReflectionQuality): Boolean = underworld == UnderworldLook.On && mirrors(reflections)

    /** What stands behind the band, in a word: the wall, or the fence (`?stage=mesh`). */
    val stageWord: String
        get() = when (stage) {
            StageLook.Wall -> "wall"
            StageLook.Mesh -> "fence"
        }

    /** The line shown for a moment when the stage's look is turned over: `stage: fence`. */
    fun stageLine(): String = "stage: $stageWord"

    /**
     * The line shown for a moment when the floor is turned over, with the reflections at [reflections]: `floor:
     * mirror`, and where the mirror is chosen but not drawn, why (`floor: mirror (reflections off)`).
     */
    fun floorLine(reflections: ReflectionQuality): String {
        val held = if (floor == FloorLook.Mirror && !mirrors(reflections)) " ($REFLECTIONS_OFF)" else ""

        return "floor: ${floor.word}$held"
    }

    /**
     * The line shown for a moment when the haze is turned over, with the fog at [fog]: `haze: parts`, and where the
     * parting is chosen but nothing parts, why: before the wall (`haze: parts (with the fence only)`), or with no haze
     * at all (`haze: parts (no haze at fog off)`).
     */
    fun hazeLine(fog: FogQuality): String {
        val held = if (haze == HazeLook.Parts) hazeHeld(fog) else null

        return "haze: ${haze.word}${held.inBrackets()}"
    }

    /**
     * Why the haze's look does nothing with the fog at [fog], whichever is chosen: before the wall
     * (`with the fence only`), or with no haze at all (`no haze at fog off`); null where it shows.
     */
    fun hazeHeld(fog: FogQuality): String? = when {
        stage != StageLook.Mesh -> "with the fence only"
        fog == FogQuality.Off -> "no haze at fog off"
        else -> null
    }

    /** The line shown for a moment when Sirene's eyes are turned over: `eyes: shadow`. */
    fun eyesLine(): String = "eyes: ${eyes.word}"

    /** The line shown for a moment when the bodies' skin is turned over: `skin: lacquer`. */
    fun skinLine(): String = "skin: ${skin.word}"

    /**
     * The line shown for a moment when the time of day is turned over: `world: night`, and before the wall, what of it
     * only the fence shows (`world: night (sun, haze, cold world with the fence only)`).
     */
    fun worldLine(): String {
        val held = when {
            world == WorldLook.Off || stage == StageLook.Mesh -> ""
            world == WorldLook.Ghosts -> " (glow and figures with the fence only)"
            else -> " (sun, haze, cold world with the fence only)"
        }

        return "world: ${world.word}$held"
    }

    /**
     * The line shown for a moment when the ghosts' colour is chosen: `ghosts: red`, and outside the ghosts world, why
     * it does nothing ([ghostsHeld]: `ghosts: red (in the ghosts world only)`).
     */
    fun ghostsLine(): String = "ghosts: ${ghosts.word}${ghostsHeld().inBrackets()}"

    /** Why the ghosts' colour does nothing: the world is not the ghosts (`in the ghosts world only`); null where it shows. */
    fun ghostsHeld(): String? = if (world == WorldLook.Ghosts) null else "in the ghosts world only"

    /**
     * The line shown for a moment when the underworld is turned over, with the reflections at [reflections]:
     * `underworld: on`, and where it is chosen but not shown, why ([mirrorHeld]: `underworld: on (reflections off)`).
     */
    fun underworldLine(reflections: ReflectionQuality): String {
        val held = if (underworld == UnderworldLook.On) mirrorHeld(reflections) else null

        return "underworld: ${underworld.word}${held.inBrackets()}"
    }

    /**
     * The line shown for a moment when the stone's unevenness is stepped, with the reflections at [reflections]:
     * `floor: faint ripple`, and where the mirror is not drawn, why: on the plain floor (`floor: faint ripple (with
     * the mirror floor only)`), or with the reflections off (`floor: faint ripple (reflections off)`).
     */
    fun rippleLine(reflections: ReflectionQuality): String = "floor: ${ripple.word} ripple${mirrorHeld(reflections).inBrackets()}"

    /**
     * The line shown for a moment when the reflection's blur is stepped, with the reflections at [reflections]:
     * `floor: crisp reflection`, and where the mirror is not drawn, why, as [rippleLine] says it.
     */
    fun blurLine(reflections: ReflectionQuality): String = "floor: ${blur.word} reflection${mirrorHeld(reflections).inBrackets()}"

    /**
     * The line shown for a moment when the stone is turned over, with the reflections at [reflections]: `floor:
     * marble`, and where the mirror is not drawn, why, as [rippleLine] says it.
     */
    fun stoneLine(reflections: ReflectionQuality): String = "floor: ${stone.word}${mirrorHeld(reflections).inBrackets()}"

    /** The line shown for a moment when the stage's outline is turned over: `stage outline: on`. */
    fun outlineLine(): String = "stage outline: ${outline.word}"

    /**
     * Why a look of the mirror (the underworld, the ripple, the blur, the stone) does not show with the reflections at
     * [reflections]: no mirror floor (`with the mirror floor only`), or the reflections off (`reflections off`); null
     * where the mirror is drawn.
     */
    fun mirrorHeld(reflections: ReflectionQuality): String? = when {
        floor != FloorLook.Mirror -> "with the mirror floor only"
        !mirrors(reflections) -> REFLECTIONS_OFF
        else -> null
    }

    /** A reason as a line ends with it: ` (with the fence only)`; nothing where there is none. */
    private fun String?.inBrackets(): String = if (this == null) "" else " ($this)"

    companion object {
        /** Why the mirror is not drawn where it is chosen: the reflections are off ([ReflectionQuality.Off]). */
        const val REFLECTIONS_OFF = "reflections off"

        /**
         * The band's new look as a whole (KLANG-00478; `?look=new`, the key x): the fence, the mirror floor, the haze
         * giving way to the shadows, Sirene's eyes in her shadow, the lacquer, and the time of day drifting from day
         * to night and back. The underworld stays off: it is a moment of a show, called by its own key. The stone
         * faintly uneven, the reflection soft and the stone plain, as without the new look.
         */
        val NEW = LookVariants(
            stage = StageLook.Mesh,
            floor = FloorLook.Mirror,
            haze = HazeLook.Parts,
            eyes = EyesLook.Shadow,
            skin = SkinLook.Lacquer,
            world = WorldLook.Drift,
            ghosts = GhostLight.Cold,
            underworld = UnderworldLook.Off,
            ripple = RippleLook.Gentle,
            blur = BlurLook.Crisp,
            stone = StoneLook.Plain,
            outline = OutlineLook.Off,
        )

        /** Whether the address asks for the new look as a whole: `?look=new`. */
        fun asksNew(switches: Switches): Boolean = switches[Switch.Look] == "new"

        /**
         * The variants the address asks for (`?stage=mesh`, `?floor=mirror`, `?haze=parts`, `?eyes=shadow`,
         * `?skin=lacquer`, `?world=night`, `?ghosts=red`, `?underworld=on`, `?ripple=faint`, `?blur=crisp`,
         * `?stone=marble`, `?edge=on`), each over the look as made, or with `?look=new` over [NEW]
         * (`?look=new&world=night`: the new look at night). A variant's switch counts where it is given at all, also
         * empty or with another word (`?look=new&stage` is the wall).
         */
        fun of(switches: Switches): LookVariants {
            val base = if (asksNew(switches)) NEW else LookVariants()

            return LookVariants(
                stage = if (switches.has(Switch.Stage)) StageLook.of(switches) else base.stage,
                floor = if (switches.has(Switch.Floor)) FloorLook.of(switches) else base.floor,
                haze = if (switches.has(Switch.Haze)) HazeLook.of(switches) else base.haze,
                eyes = if (switches.has(Switch.Eyes)) EyesLook.of(switches) else base.eyes,
                skin = if (switches.has(Switch.Skin)) SkinLook.of(switches) else base.skin,
                world = if (switches.has(Switch.World)) WorldLook.of(switches) else base.world,
                ghosts = if (switches.has(Switch.Ghosts)) GhostLight.of(switches) else base.ghosts,
                underworld = if (switches.has(Switch.Underworld)) UnderworldLook.of(switches) else base.underworld,
                ripple = if (switches.has(Switch.Ripple)) RippleLook.of(switches) else base.ripple,
                blur = if (switches.has(Switch.Blur)) BlurLook.of(switches) else base.blur,
                stone = if (switches.has(Switch.Stone)) StoneLook.of(switches) else base.stone,
                outline = if (switches.has(Switch.Edge)) OutlineLook.of(switches) else base.outline,
            )
        }
    }
}
