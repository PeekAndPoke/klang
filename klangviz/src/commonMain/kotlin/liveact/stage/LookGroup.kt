/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

/**
 * One of the look's variants as an app's controls show it (notstrom's drawer with `?dev`): its [label] (`Stage`), the
 * [key] that turns it over while the show plays (`m`; null where it has none, as the ghosts' colour), its [options] in
 * the order the key steps through them, and where the variant does nothing right now, why ([held], in the words its
 * line at the top uses: `with the fence only`); null where it shows. A held variant can still be chosen: it shows
 * once what holds it back is gone.
 */
data class LookGroup(val label: String, val key: String?, val options: List<Option>, val held: String?) {

    /**
     * One choice of a variant: its [label] (`Fence`), whether it is the one in use ([marked]), and what choosing it
     * does: the variants shown then ([to]), or, where [flips], the whole look flipped as the key x does (the other side
     * is shown, as it was last tuned: [to] says what it holds).
     */
    data class Option(val label: String, val marked: Boolean, val to: LookVariants, val flips: Boolean = false)

    companion object {
        /**
         * Every variant of [look] as the controls show it with the fog at [fog] (the haze needs it) and the
         * reflections at [reflections] (the mirror needs them), the whole look (x) first, then each variant as its key
         * turns it over. A new variant is one line more here. The reflection's blur (the key c) is labelled `Blur`, so
         * it is not taken for the reflections themselves, which are a quality of the picture beside the fog.
         */
        fun all(look: LookFlip, fog: FogQuality, reflections: ReflectionQuality): List<LookGroup> {
            val v = look.shown
            val mirror = v.mirrorHeld(reflections)

            return listOf(
                whole(look),
                of(label = "Stage", key = "m", entries = StageLook.entries, chosen = v.stage, word = { v.copy(stage = it).stageWord }) {
                    v.copy(stage = it)
                },
                of(
                    label = "Floor", key = "p", entries = FloorLook.entries, chosen = v.floor, word = { it.word },
                    held = v.copy(floor = FloorLook.Mirror).mirrorHeld(reflections),
                ) { v.copy(floor = it) },
                of(label = "Haze", key = "h", entries = HazeLook.entries, chosen = v.haze, word = { it.word }, held = v.hazeHeld(fog)) {
                    v.copy(haze = it)
                },
                of(label = "Eyes", key = "i", entries = EyesLook.entries, chosen = v.eyes, word = { it.word }) { v.copy(eyes = it) },
                of(label = "Skin", key = "l", entries = SkinLook.entries, chosen = v.skin, word = { it.word }) { v.copy(skin = it) },
                of(label = "World", key = "n", entries = WorldLook.entries, chosen = v.world, word = { it.word }) { v.copy(world = it) },
                of(
                    label = "Ghosts", key = null, entries = GhostLight.entries, chosen = v.ghosts, word = { it.word },
                    held = v.ghostsHeld(),
                ) { v.copy(ghosts = it) },
                of(
                    label = "Underworld", key = "v", entries = UnderworldLook.entries, chosen = v.underworld, word = { it.word },
                    held = mirror,
                ) { v.copy(underworld = it) },
                of(label = "Ripple", key = "b", entries = RippleLook.entries, chosen = v.ripple, word = { it.word }, held = mirror) {
                    v.copy(ripple = it)
                },
                of(label = "Blur", key = "c", entries = BlurLook.entries, chosen = v.blur, word = { it.word }, held = mirror) {
                    v.copy(blur = it)
                },
                of(label = "Stone", key = "k", entries = StoneLook.entries, chosen = v.stone, word = { it.word }, held = mirror) {
                    v.copy(stone = it)
                },
                of(label = "Outline", key = "j", entries = OutlineLook.entries, chosen = v.outline, word = { it.word }) {
                    v.copy(outline = it)
                },
            )
        }

        /** The whole look (the key x): today and the new look, the side shown marked, the other one flipped to. */
        private fun whole(look: LookFlip): LookGroup {
            val today = if (look.new) look.kept else look.shown
            val new = if (look.new) look.shown else look.kept

            return LookGroup(
                label = "Look",
                key = "x",
                options = listOf(
                    Option(label = "Today", marked = !look.new, to = today, flips = look.new),
                    Option(label = "New", marked = look.new, to = new, flips = !look.new),
                ),
                held = null,
            )
        }

        /**
         * A variant labelled [label], turned over by [key]: one option for each of its [entries], labelled by its
         * [word] with a capital, [chosen] marked, each choosing the variants [with] it; [held] as [LookGroup.held].
         */
        private fun <T> of(
            label: String,
            key: String?,
            entries: List<T>,
            chosen: T,
            word: (T) -> String,
            held: String? = null,
            with: (T) -> LookVariants,
        ): LookGroup = LookGroup(
            label = label,
            key = key,
            options = entries.map { entry ->
                Option(label = word(entry).replaceFirstChar { it.uppercase() }, marked = entry == chosen, to = with(entry))
            },
            held = held,
        )
    }
}
