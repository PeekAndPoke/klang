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

    registry.register("StrudelLpFilterEditor", StrudelLpFilterEditorTool)
    registry.register("StrudelLpFilterSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelLpFilterEditorTool))
    registry.register("StrudelHpFilterEditor", StrudelHpFilterEditorTool)
    registry.register("StrudelHpFilterSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelHpFilterEditorTool))
    registry.register("StrudelBpFilterEditor", StrudelBpFilterEditorTool)
    registry.register("StrudelBpFilterSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelBpFilterEditorTool))
    registry.register("StrudelNotchFilterEditor", StrudelNotchFilterEditorTool)
    registry.register("StrudelNotchFilterSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelNotchFilterEditorTool))

    registry.register("StrudelDelayEditor", StrudelDelayEditorTool)
    registry.register("StrudelDelaySequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelDelayEditorTool))

    registry.register("StrudelDelayTimeEditor", StrudelDelayTimeEditorTool)
    registry.register("StrudelDelayTimeSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelDelayTimeEditorTool))

    registry.register("StrudelDelayFeedbackEditor", StrudelDelayFeedbackEditorTool)
    registry.register("StrudelDelayFeedbackSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelDelayFeedbackEditorTool))

    registry.register("StrudelReverbEditor", StrudelReverbEditorTool)
    registry.register("StrudelReverbSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelReverbEditorTool))

    registry.register("StrudelRoomSizeEditor", StrudelRoomSizeEditorTool)
    registry.register("StrudelRoomSizeSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelRoomSizeEditorTool))

    registry.register("StrudelWaveformEditor", StrudelWaveformEditorTool)
    registry.register("StrudelWaveformSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelWaveformEditorTool))

    registry.register("StrudelSampleEditor", StrudelSampleEditorTool)
    registry.register("StrudelSampleSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelSampleEditorTool))

    registry.register("StrudelCompressorEditor", StrudelCompressorEditorTool)
    registry.register("StrudelCompressorSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelCompressorEditorTool))

    // ── LP filter tools ──────────────────────────────────────────────────────
    registry.register("StrudelLpCutoffEditor", StrudelLpCutoffEditorTool)
    registry.register("StrudelLpCutoffSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelLpCutoffEditorTool))
    registry.register("StrudelLpResonanceEditor", StrudelLpResonanceEditorTool)
    registry.register("StrudelLpResonanceSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelLpResonanceEditorTool))
    registry.register("StrudelLpEnvEditor", StrudelLpEnvEditorTool)
    registry.register("StrudelLpEnvSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelLpEnvEditorTool))
    registry.register("StrudelLpAttackEditor", StrudelLpAttackEditorTool)
    registry.register("StrudelLpAttackSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelLpAttackEditorTool))
    registry.register("StrudelLpDecayEditor", StrudelLpDecayEditorTool)
    registry.register("StrudelLpDecaySequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelLpDecayEditorTool))
    registry.register("StrudelLpSustainEditor", StrudelLpSustainEditorTool)
    registry.register("StrudelLpSustainSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelLpSustainEditorTool))
    registry.register("StrudelLpReleaseEditor", StrudelLpReleaseEditorTool)
    registry.register("StrudelLpReleaseSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelLpReleaseEditorTool))
    registry.register("StrudelLpAdsrEditor", StrudelLpAdsrEditorTool)
    registry.register("StrudelLpAdsrSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelLpAdsrEditorTool))

    // ── HP filter tools ──────────────────────────────────────────────────────
    registry.register("StrudelHpCutoffEditor", StrudelHpCutoffEditorTool)
    registry.register("StrudelHpCutoffSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelHpCutoffEditorTool))
    registry.register("StrudelHpResonanceEditor", StrudelHpResonanceEditorTool)
    registry.register("StrudelHpResonanceSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelHpResonanceEditorTool))
    registry.register("StrudelHpEnvEditor", StrudelHpEnvEditorTool)
    registry.register("StrudelHpEnvSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelHpEnvEditorTool))
    registry.register("StrudelHpAttackEditor", StrudelHpAttackEditorTool)
    registry.register("StrudelHpAttackSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelHpAttackEditorTool))
    registry.register("StrudelHpDecayEditor", StrudelHpDecayEditorTool)
    registry.register("StrudelHpDecaySequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelHpDecayEditorTool))
    registry.register("StrudelHpSustainEditor", StrudelHpSustainEditorTool)
    registry.register("StrudelHpSustainSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelHpSustainEditorTool))
    registry.register("StrudelHpReleaseEditor", StrudelHpReleaseEditorTool)
    registry.register("StrudelHpReleaseSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelHpReleaseEditorTool))
    registry.register("StrudelHpAdsrEditor", StrudelHpAdsrEditorTool)
    registry.register("StrudelHpAdsrSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelHpAdsrEditorTool))

    // ── BP filter tools ──────────────────────────────────────────────────────
    registry.register("StrudelBpFreqEditor", StrudelBpFreqEditorTool)
    registry.register("StrudelBpFreqSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelBpFreqEditorTool))
    registry.register("StrudelBpQEditor", StrudelBpQEditorTool)
    registry.register("StrudelBpQSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelBpQEditorTool))
    registry.register("StrudelBpEnvEditor", StrudelBpEnvEditorTool)
    registry.register("StrudelBpEnvSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelBpEnvEditorTool))
    registry.register("StrudelBpAttackEditor", StrudelBpAttackEditorTool)
    registry.register("StrudelBpAttackSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelBpAttackEditorTool))
    registry.register("StrudelBpDecayEditor", StrudelBpDecayEditorTool)
    registry.register("StrudelBpDecaySequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelBpDecayEditorTool))
    registry.register("StrudelBpSustainEditor", StrudelBpSustainEditorTool)
    registry.register("StrudelBpSustainSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelBpSustainEditorTool))
    registry.register("StrudelBpReleaseEditor", StrudelBpReleaseEditorTool)
    registry.register("StrudelBpReleaseSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelBpReleaseEditorTool))
    registry.register("StrudelBpAdsrEditor", StrudelBpAdsrEditorTool)
    registry.register("StrudelBpAdsrSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelBpAdsrEditorTool))

    // ── Notch filter tools ───────────────────────────────────────────────────
    registry.register("StrudelNotchFreqEditor", StrudelNotchFreqEditorTool)
    registry.register("StrudelNotchFreqSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelNotchFreqEditorTool))
    registry.register("StrudelNotchQEditor", StrudelNotchQEditorTool)
    registry.register("StrudelNotchQSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelNotchQEditorTool))
    registry.register("StrudelNResonanceEditor", StrudelNResonanceEditorTool)
    registry.register("StrudelNResonanceSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelNResonanceEditorTool))
    registry.register("StrudelNfEnvEditor", StrudelNfEnvEditorTool)
    registry.register("StrudelNfEnvSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelNfEnvEditorTool))
    registry.register("StrudelNfAttackEditor", StrudelNfAttackEditorTool)
    registry.register("StrudelNfAttackSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelNfAttackEditorTool))
    registry.register("StrudelNfDecayEditor", StrudelNfDecayEditorTool)
    registry.register("StrudelNfDecaySequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelNfDecayEditorTool))
    registry.register("StrudelNfSustainEditor", StrudelNfSustainEditorTool)
    registry.register("StrudelNfSustainSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelNfSustainEditorTool))
    registry.register("StrudelNfReleaseEditor", StrudelNfReleaseEditorTool)
    registry.register("StrudelNfReleaseSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelNfReleaseEditorTool))
    registry.register("StrudelNfAdsrEditor", StrudelNfAdsrEditorTool)
    registry.register("StrudelNfAdsrSequenceEditor", StrudelMiniNotationEditorTool(atomTool = StrudelNfAdsrEditorTool))
}
