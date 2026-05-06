package io.peekandpoke.klang.sprudel.ui

import io.peekandpoke.klang.ui.KlangUiToolRegistry

/**
 * Registers all Sprudel UI tools with the given [registry].
 *
 * Call this once at application startup.
 */
fun registerSprudelUiTools(registry: KlangUiToolRegistry = KlangUiToolRegistry) {
    registry.register("SprudelAdsrEditor", SprudelAdsrEditorTool)
    registry.register("SprudelAdsrSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelAdsrEditorTool))

    registry.register("SprudelScaleEditor", SprudelScaleEditorTool)

    registry.register("SprudelMiniNotationEditor", SprudelMiniNotationEditorTool())

    registry.register("SprudelNoteEditor", SprudelNoteEditorTool)
    registry.register("SprudelScaleDegreeEditor", SprudelScaleDegreeEditorTool)

    // New tools
    registry.register("SprudelGainEditor", SprudelGainEditorTool)
    registry.register("SprudelGainSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelGainEditorTool))

    registry.register("SprudelPanEditor", SprudelPanEditorTool)
    registry.register("SprudelPanSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelPanEditorTool))

    registry.register("SprudelEuclidEditor", SprudelEuclidEditorTool)
    registry.register("SprudelEuclidSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelEuclidEditorTool))

    registry.register("SprudelLpFilterEditor", SprudelLpFilterEditorTool)
    registry.register("SprudelLpFilterSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelLpFilterEditorTool))
    registry.register("SprudelHpFilterEditor", SprudelHpFilterEditorTool)
    registry.register("SprudelHpFilterSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelHpFilterEditorTool))
    registry.register("SprudelBpFilterEditor", SprudelBpFilterEditorTool)
    registry.register("SprudelBpFilterSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelBpFilterEditorTool))
    registry.register("SprudelNotchFilterEditor", SprudelNotchFilterEditorTool)
    registry.register("SprudelNotchFilterSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelNotchFilterEditorTool))

    registry.register("SprudelDelayEditor", SprudelDelayEditorTool)
    registry.register("SprudelDelaySequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelDelayEditorTool))

    registry.register("SprudelDelayTimeEditor", SprudelDelayTimeEditorTool)
    registry.register("SprudelDelayTimeSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelDelayTimeEditorTool))

    registry.register("SprudelDelayFeedbackEditor", SprudelDelayFeedbackEditorTool)
    registry.register("SprudelDelayFeedbackSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelDelayFeedbackEditorTool))

    registry.register("SprudelReverbEditor", SprudelReverbEditorTool)
    registry.register("SprudelReverbSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelReverbEditorTool))

    registry.register("SprudelRoomSizeEditor", SprudelRoomSizeEditorTool)
    registry.register("SprudelRoomSizeSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelRoomSizeEditorTool))

    registry.register("SprudelWaveformEditor", SprudelWaveformEditorTool)
    registry.register("SprudelWaveformSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelWaveformEditorTool))

    registry.register("SprudelSampleEditor", SprudelSampleEditorTool)
    registry.register("SprudelSampleSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelSampleEditorTool))

    registry.register("SprudelTremoloEditor", SprudelTremoloEditorTool)
    registry.register("SprudelTremoloSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelTremoloEditorTool))

    // ── Distort tools ─────────────────────────────────────────────────────────
    registry.register("SprudelDistortEditor", SprudelDistortEditorTool)
    registry.register("SprudelDistortSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelDistortEditorTool))
    registry.register("SprudelDistortShapeEditor", SprudelDistortShapeEditorTool)
    registry.register("SprudelDistortShapeSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelDistortShapeEditorTool))
    registry.register("SprudelDistortAmountEditor", SprudelDistortAmountEditorTool)
    registry.register("SprudelDistortAmountSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelDistortAmountEditorTool))

    // ── Phaser tools ──────────────────────────────────────────────────────────
    registry.register("SprudelPhaserEditor", SprudelPhaserEditorTool)
    registry.register("SprudelPhaserSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelPhaserEditorTool))

    registry.register("SprudelCompressorEditor", SprudelCompressorEditorTool)
    registry.register("SprudelCompressorSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelCompressorEditorTool))

    // ── Pluck tools ─────────────────────────────────────────────────────────
    registry.register("SprudelPluckEditor", SprudelPluckEditorTool)
    registry.register("SprudelPluckSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelPluckEditorTool))
    registry.register("SprudelSuperPluckEditor", SprudelSuperPluckEditorTool)
    registry.register("SprudelSuperPluckSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelSuperPluckEditorTool))

    // ── Sound source tools ──────────────────────────────────────────────────
    registry.register("SprudelSuperSawEditor", SprudelSuperSawEditorTool)
    registry.register("SprudelSuperSawSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelSuperSawEditorTool))
    registry.register("SprudelPulzeEditor", SprudelPulzeEditorTool)
    registry.register("SprudelPulzeSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelPulzeEditorTool))
    registry.register("SprudelDustEditor", SprudelDustEditorTool)
    registry.register("SprudelDustSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelDustEditorTool))

    // ── LP filter tools ──────────────────────────────────────────────────────
    registry.register("SprudelLpCutoffEditor", SprudelLpCutoffEditorTool)
    registry.register("SprudelLpCutoffSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelLpCutoffEditorTool))
    registry.register("SprudelLpResonanceEditor", SprudelLpResonanceEditorTool)
    registry.register("SprudelLpResonanceSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelLpResonanceEditorTool))
    registry.register("SprudelLpEnvEditor", SprudelLpEnvEditorTool)
    registry.register("SprudelLpEnvSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelLpEnvEditorTool))
    registry.register("SprudelLpAttackEditor", SprudelLpAttackEditorTool)
    registry.register("SprudelLpAttackSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelLpAttackEditorTool))
    registry.register("SprudelLpDecayEditor", SprudelLpDecayEditorTool)
    registry.register("SprudelLpDecaySequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelLpDecayEditorTool))
    registry.register("SprudelLpSustainEditor", SprudelLpSustainEditorTool)
    registry.register("SprudelLpSustainSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelLpSustainEditorTool))
    registry.register("SprudelLpReleaseEditor", SprudelLpReleaseEditorTool)
    registry.register("SprudelLpReleaseSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelLpReleaseEditorTool))
    registry.register("SprudelLpAdsrEditor", SprudelLpAdsrEditorTool)
    registry.register("SprudelLpAdsrSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelLpAdsrEditorTool))

    // ── HP filter tools ──────────────────────────────────────────────────────
    registry.register("SprudelHpCutoffEditor", SprudelHpCutoffEditorTool)
    registry.register("SprudelHpCutoffSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelHpCutoffEditorTool))
    registry.register("SprudelHpResonanceEditor", SprudelHpResonanceEditorTool)
    registry.register("SprudelHpResonanceSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelHpResonanceEditorTool))
    registry.register("SprudelHpEnvEditor", SprudelHpEnvEditorTool)
    registry.register("SprudelHpEnvSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelHpEnvEditorTool))
    registry.register("SprudelHpAttackEditor", SprudelHpAttackEditorTool)
    registry.register("SprudelHpAttackSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelHpAttackEditorTool))
    registry.register("SprudelHpDecayEditor", SprudelHpDecayEditorTool)
    registry.register("SprudelHpDecaySequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelHpDecayEditorTool))
    registry.register("SprudelHpSustainEditor", SprudelHpSustainEditorTool)
    registry.register("SprudelHpSustainSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelHpSustainEditorTool))
    registry.register("SprudelHpReleaseEditor", SprudelHpReleaseEditorTool)
    registry.register("SprudelHpReleaseSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelHpReleaseEditorTool))
    registry.register("SprudelHpAdsrEditor", SprudelHpAdsrEditorTool)
    registry.register("SprudelHpAdsrSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelHpAdsrEditorTool))

    // ── BP filter tools ──────────────────────────────────────────────────────
    registry.register("SprudelBpFreqEditor", SprudelBpFreqEditorTool)
    registry.register("SprudelBpFreqSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelBpFreqEditorTool))
    registry.register("SprudelBpQEditor", SprudelBpQEditorTool)
    registry.register("SprudelBpQSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelBpQEditorTool))
    registry.register("SprudelBpEnvEditor", SprudelBpEnvEditorTool)
    registry.register("SprudelBpEnvSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelBpEnvEditorTool))
    registry.register("SprudelBpAttackEditor", SprudelBpAttackEditorTool)
    registry.register("SprudelBpAttackSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelBpAttackEditorTool))
    registry.register("SprudelBpDecayEditor", SprudelBpDecayEditorTool)
    registry.register("SprudelBpDecaySequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelBpDecayEditorTool))
    registry.register("SprudelBpSustainEditor", SprudelBpSustainEditorTool)
    registry.register("SprudelBpSustainSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelBpSustainEditorTool))
    registry.register("SprudelBpReleaseEditor", SprudelBpReleaseEditorTool)
    registry.register("SprudelBpReleaseSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelBpReleaseEditorTool))
    registry.register("SprudelBpAdsrEditor", SprudelBpAdsrEditorTool)
    registry.register("SprudelBpAdsrSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelBpAdsrEditorTool))

    // ── Notch filter tools ───────────────────────────────────────────────────
    registry.register("SprudelNotchFreqEditor", SprudelNotchFreqEditorTool)
    registry.register("SprudelNotchFreqSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelNotchFreqEditorTool))
    registry.register("SprudelNotchQEditor", SprudelNotchQEditorTool)
    registry.register("SprudelNotchQSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelNotchQEditorTool))
    registry.register("SprudelNResonanceEditor", SprudelNResonanceEditorTool)
    registry.register("SprudelNResonanceSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelNResonanceEditorTool))
    registry.register("SprudelNfEnvEditor", SprudelNfEnvEditorTool)
    registry.register("SprudelNfEnvSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelNfEnvEditorTool))
    registry.register("SprudelNfAttackEditor", SprudelNfAttackEditorTool)
    registry.register("SprudelNfAttackSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelNfAttackEditorTool))
    registry.register("SprudelNfDecayEditor", SprudelNfDecayEditorTool)
    registry.register("SprudelNfDecaySequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelNfDecayEditorTool))
    registry.register("SprudelNfSustainEditor", SprudelNfSustainEditorTool)
    registry.register("SprudelNfSustainSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelNfSustainEditorTool))
    registry.register("SprudelNfReleaseEditor", SprudelNfReleaseEditorTool)
    registry.register("SprudelNfReleaseSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelNfReleaseEditorTool))
    registry.register("SprudelNfAdsrEditor", SprudelNfAdsrEditorTool)
    registry.register("SprudelNfAdsrSequenceEditor", SprudelMiniNotationEditorTool(atomTool = SprudelNfAdsrEditorTool))
}
