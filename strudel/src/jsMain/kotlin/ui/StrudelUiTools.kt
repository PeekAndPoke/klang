package io.peekandpoke.klang.strudel.ui

import io.peekandpoke.klang.ui.KlangUiToolRegistry

/**
 * Registers all Strudel UI tools with the given [registry].
 *
 * Call this once at application startup, after [io.peekandpoke.klang.strudel.lang.initStrudelLang].
 */
fun registerStrudelUiTools(registry: KlangUiToolRegistry = KlangUiToolRegistry) {
    registry.register("StrudelAdsrEditor", StrudelAdsrEditorTool)
    registry.register("StrudelAdsrSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelAdsrEditorTool))

    registry.register("StrudelScaleEditor", StrudelScaleEditorTool)
    registry.register("StrudelScaleSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelScaleEditorTool))

    registry.register("StrudelMiniNotationEditor", StrudelMiniNotationEditorTool())
}
