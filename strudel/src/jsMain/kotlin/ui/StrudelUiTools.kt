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

    registry.register("StrudelMiniNotationEditor", StrudelMiniNotationEditorTool())

    registry.register("StrudelNoteEditor", StrudelNoteEditorTool)
    registry.register("StrudelScaleDegreeEditor", StrudelScaleDegreeEditorTool)

    // New tools
    registry.register("StrudelGainEditor", StrudelGainEditorTool)
    registry.register("StrudelGainSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelGainEditorTool))

    registry.register("StrudelPanEditor", StrudelPanEditorTool)
    registry.register("StrudelPanSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelPanEditorTool))

    registry.register("StrudelEuclidEditor", StrudelEuclidEditorTool)
    registry.register("StrudelEuclidSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelEuclidEditorTool))

    registry.register("StrudelFilterEditor", StrudelFilterEditorTool)
    registry.register("StrudelFilterSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelFilterEditorTool))

    registry.register("StrudelDelayEditor", StrudelDelayEditorTool)
    registry.register("StrudelDelaySequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelDelayEditorTool))

    registry.register("StrudelReverbEditor", StrudelReverbEditorTool)
    registry.register("StrudelReverbSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelReverbEditorTool))

    registry.register("StrudelWaveformEditor", StrudelWaveformEditorTool)
    registry.register("StrudelWaveformSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelWaveformEditorTool))

    registry.register("StrudelSampleEditor", StrudelSampleEditorTool)
    registry.register("StrudelSampleSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelSampleEditorTool))
}
