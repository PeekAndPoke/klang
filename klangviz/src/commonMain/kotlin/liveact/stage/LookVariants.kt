/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.Switches

/**
 * The variants of the band's look chosen while the show plays (KLANG-00478): what stands behind the band ([stage]),
 * the floor ([floor]), the haze behind the fence ([haze]), where Sirene's eyes glow under the front lamp ([eyes]),
 * what the band's bodies are made of ([skin]) and the two worlds' time of day ([world]). A later variant joins them
 * here, with its key and its line.
 *
 * They start as the address asks ([of]); a key turns one of them over ([nextStage], the key m; [nextFloor], the key
 * p; [nextHaze], the key h; [nextEyes], the key i; [nextSkin], the key l; [nextWorld], the key n), and the line at
 * the top says the new one in a few words for a moment ([stageLine], [floorLine], [hazeLine], [eyesLine], [skinLine],
 * [worldLine]).
 */
data class LookVariants(
    val stage: StageLook = StageLook.Wall,
    val floor: FloorLook = FloorLook.Plain,
    val haze: HazeLook = HazeLook.Whole,
    val eyes: EyesLook = EyesLook.Face,
    val skin: SkinLook = SkinLook.Suit,
    val world: WorldLook = WorldLook.Off,
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

    /** The next time of day (off, day, night, ember, drift), after the last off again. */
    fun nextWorld(): LookVariants = copy(world = WorldLook.entries[(world.ordinal + 1) % WorldLook.entries.size])

    /**
     * Whether the haze gives way to the band's shadows: where it is chosen and the fence stands (only the fence has the
     * haze).
     */
    val partsHaze: Boolean
        get() = haze == HazeLook.Parts && stage == StageLook.Mesh

    /**
     * Whether the mirror is drawn with the fog at [fog]: where it is chosen and the fog is drawn at medium or finer.
     * The mirror draws the stage a second time, and where the fog had to go down to low or off (a slow machine, a
     * phone) it would cost the frames the fog gave back.
     */
    fun mirrors(fog: FogQuality): Boolean = floor == FloorLook.Mirror && fog.layers >= FogQuality.Medium.layers

    /** What stands behind the band, in a word: the wall, or the fence (`?stage=mesh`). */
    val stageWord: String
        get() = when (stage) {
            StageLook.Wall -> "wall"
            StageLook.Mesh -> "fence"
        }

    /** The line shown for a moment when the stage's look is turned over: `stage: fence`. */
    fun stageLine(): String = "stage: $stageWord"

    /**
     * The line shown for a moment when the floor is turned over, with the fog at [fog]: `floor: mirror`, and where the
     * mirror is chosen but not drawn at that fog, why (`floor: mirror (not drawn at fog low)`).
     */
    fun floorLine(fog: FogQuality): String {
        val held = if (floor == FloorLook.Mirror && !mirrors(fog)) " (not drawn at fog ${fog.word})" else ""

        return "floor: ${floor.word}$held"
    }

    /**
     * The line shown for a moment when the haze is turned over, with the fog at [fog]: `haze: parts`, and where the
     * parting is chosen but nothing parts, why: before the wall (`haze: parts (with the fence only)`), or with no haze
     * at all (`haze: parts (no haze at fog off)`).
     */
    fun hazeLine(fog: FogQuality): String {
        val held = when {
            haze == HazeLook.Whole -> ""
            stage != StageLook.Mesh -> " (with the fence only)"
            fog == FogQuality.Off -> " (no haze at fog off)"
            else -> ""
        }

        return "haze: ${haze.word}$held"
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
        val held = if (world != WorldLook.Off && stage != StageLook.Mesh) " (sun, haze, cold world with the fence only)" else ""

        return "world: ${world.word}$held"
    }

    companion object {
        /**
         * The variants the address asks for (`?stage=mesh`, `?floor=mirror`, `?haze=parts`, `?eyes=shadow`,
         * `?skin=lacquer`, `?world=night`); without them the look as it was made.
         */
        fun of(switches: Switches): LookVariants = LookVariants(
            stage = StageLook.of(switches),
            floor = FloorLook.of(switches),
            haze = HazeLook.of(switches),
            eyes = EyesLook.of(switches),
            skin = SkinLook.of(switches),
            world = WorldLook.of(switches),
        )
    }
}
