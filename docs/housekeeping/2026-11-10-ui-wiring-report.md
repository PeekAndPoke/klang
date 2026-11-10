# ui-wiring

## The rails

| Rail | Files | Components | subscribingTo | by value | Stream sources | Storage refs |
|---|---|---|---|---|---|---|
| storage | 16 | 0 | 0 | 0 | 13 | 32 |
| spine | 17 | 0 | 0 | 0 | 18 | 0 |
| app | 8 | 1 | 0 | 0 | 2 | 4 |
| layout | 10 | 6 | 11 | 1 | 3 | 0 |
| pages | 44 | 35 | 76 | 46 | 10 | 4 |
| components | 39 | 28 | 48 | 22 | 3 | 0 |
| library | 130 | 61 | 66 | 106 | 15 | 1 |
| content | 59 | 0 | 0 | 0 | 0 | 0 |

What a `subscribingTo` reads: holder 135, local 44, props 13, router 9.

## The holders

| Holder | Rail | Kind | Keeps | Read as a stream by | Reached directly by | File |
|---|---|---|---|---|---|---|
| `EditorSettings` | storage | object | stream source, browser storage, derived stream | 6 | 3 | `src/jsMain/kotlin/storage/EditorSettings.kt` |
| `KeymapStorage` | storage | object | browser storage | 0 | 1 | `src/jsMain/kotlin/storage/KeymapStorage.kt` |
| `LookStorage` | storage | object | browser storage | 0 | 5 | `src/jsMain/kotlin/storage/LookStorage.kt` |
| `MidiInstrumentStorage` | storage | object | browser storage | 0 | 1 | `src/jsMain/kotlin/storage/MidiInstrumentStorage.kt` |
| `MidiLoadoutStorage` | storage | object | browser storage | 0 | 1 | `src/jsMain/kotlin/spine/storage/MidiLoadoutStorage.kt` |
| `MidiPlayCodeStorage` | storage | object | browser storage | 0 | 1 | `src/jsMain/kotlin/spine/storage/MidiPlayCodeStorage.kt` |
| `MidiSettingsStorage` | storage | object | browser storage | 0 | 1 | `src/jsMain/kotlin/spine/storage/MidiSettingsStorage.kt` |
| `OutputVolumeStorage` | storage | object | browser storage | 0 | 1 | `src/jsMain/kotlin/spine/storage/OutputVolumeStorage.kt` |
| `RecentsStorage` | storage | object | browser storage | 0 | 1 | `src/jsMain/kotlin/spine/storage/RecentsStorage.kt` |
| `SeedStorage` | storage | object | browser storage | 0 | 3 | `src/jsMain/kotlin/storage/SeedStorage.kt` |
| `TutorialStorage` | storage | object | stream source, browser storage, derived stream | 6 | 1 | `src/jsMain/kotlin/storage/TutorialStorage.kt` |
| `WorkbenchLayoutStorage` | storage | object | browser storage | 0 | 1 | `src/jsMain/kotlin/storage/WorkbenchLayoutStorage.kt` |
| `ConsoleLog` | spine | object | stream source, var, derived stream | 2 | 2 | `src/jsMain/kotlin/spine/ConsoleLog.kt` |
| `MidiSettings` | spine | object | stream source, var, derived stream | 2 | 2 | `src/jsMain/kotlin/spine/MidiSettings.kt` |
| `MotorReadings` | spine | object | derived stream | 3 | 1 | `src/jsMain/kotlin/spine/MotorReadings.kt` |
| `Playbacks` | spine | object | stream source, var, derived stream | 6 | 16 | `src/jsMain/kotlin/spine/Playbacks.kt` |
| `Player` | spine | object | stream source, var, derived stream | 6 | 19 | `src/jsMain/kotlin/spine/Player.kt` |
| `Recents` | spine | object | stream source, derived stream | 1 | 5 | `src/jsMain/kotlin/spine/Recents.kt` |
| `UiWork` | spine | object | derived stream | 5 | 8 | `src/jsMain/kotlin/spine/UiWork.kt` |
| `VisualState` | spine | object | stream source, mutable collection, derived stream | 2 | 5 | `src/jsMain/kotlin/spine/VisualState.kt` |
| `KlangGuiDance` | app | object | var, browser storage | 8 | 11 | `src/jsMain/kotlin/KlangGuiDance.kt` |
| `fs` | app | val | top-level value | 0 | 2 | `src/jsMain/kotlin/index.kt` |
| `keysManager` | app | val | top-level value | 0 | 0 | `src/jsMain/kotlin/index.kt` |
| `kraft` | app | val | top-level value | 0 | 0 | `src/jsMain/kotlin/index.kt` |
| `version` | app | val | top-level value | 4 | 0 | `src/jsMain/kotlin/index.kt` |
| `WorkbenchSlots` | layout | object | stream source | 1 | 3 | `src/jsMain/kotlin/layouts/WorkbenchSlots.kt` |
| `CodeSongPage` | pages | companion | var | 0 | 0 | `src/jsMain/kotlin/pages/CodeSongPage.kt` |
| `Drafts` | pages | object | var, mutable collection | 0 | 0 | `src/jsMain/kotlin/pages/LooksPage.kt` |
| `KlangScriptLibraryDocsPage` | pages | companion | var | 0 | 0 | `src/jsMain/kotlin/pages/docs/KlangScriptLibraryDocsPage.kt` |
| `KlangampDeck` | pages | object | var | 0 | 3 | `src/jsMain/kotlin/visuals/KlangampDeck.kt` |
| `LearnSearch` | pages | object | stream source | 1 | 1 | `src/jsMain/kotlin/pages/docs/tutorials/LearnTracksColumn.kt` |
| `TutorialPage` | pages | companion | var | 0 | 0 | `src/jsMain/kotlin/pages/docs/tutorials/TutorialPage.kt` |
| `VisualsScreen` | pages | companion | stream source, derived stream | 3 | 3 | `src/jsMain/kotlin/visuals/VisualsScreen.kt` |
| `MotorBackground` | components | companion | var | 0 | 0 | `src/jsMain/kotlin/comp/MotorBackground.kt` |
| `motorBackgroundRef` | components | val | top-level value | 0 | 1 | `src/jsMain/kotlin/comp/MotorBackground.kt` |
| `KlangBrushedMetal` | library | object | var | 0 | 1 | `klangui/src/jsMain/kotlin/feel/KlangBrushedMetal.kt` |
| `KlangDialog` | library | companion | var | 0 | 0 | `klangui/src/jsMain/kotlin/comp/KlangDialog.kt` |
| `KlangLookShelf` | library | object | stream source | 4 | 2 | `klangui/src/jsMain/kotlin/feel/KlangLookShelf.kt` |
| `KlangTheme` | library | object | stream source, var, derived stream | 42 | 12 | `klangui/src/jsMain/kotlin/feel/KlangTheme.kt` |
| `KlangToolAutoUpdate` | library | object | stream source, browser storage | 25 | 2 | `klangui/src/jsMain/kotlin/codetools/KlangToolAutoUpdate.kt` |
| `KlangUiToolRegistry` | library | object | mutable collection | 0 | 3 | `klangui/src/jsMain/kotlin/KlangUiTool.kt` |
| `MarkdownDisplay` | library | companion | mutable collection | 0 | 0 | `klangui/src/jsMain/kotlin/comp/MarkdownDisplay.kt` |

## Rail to rail (file-to-holder pairs)

| From | To | Stream | Direct |
|---|---|---|---|
| app | library | 0 | 2 |
| app | spine | 0 | 3 |
| app | storage | 0 | 3 |
| components | app | 4 | 4 |
| components | layout | 0 | 1 |
| components | library | 4 | 2 |
| components | pages | 1 | 1 |
| components | spine | 9 | 13 |
| components | storage | 5 | 4 |
| layout | app | 2 | 3 |
| layout | layout | 1 | 2 |
| layout | pages | 1 | 1 |
| layout | spine | 4 | 2 |
| layout | storage | 1 | 2 |
| library | library | 52 | 11 |
| pages | app | 6 | 6 |
| pages | components | 0 | 1 |
| pages | library | 14 | 4 |
| pages | pages | 2 | 5 |
| pages | spine | 12 | 28 |
| pages | storage | 6 | 4 |
| spine | spine | 2 | 11 |
| spine | storage | 0 | 6 |
| storage | library | 1 | 1 |
| storage | spine | 0 | 1 |
| storage | storage | 0 | 1 |

## Findings

### state held outside the Spine (22)

- `src/jsMain/kotlin/pages/CodeSongPage.kt`: CodeSongPage (companion): var
- `src/jsMain/kotlin/pages/LooksPage.kt`: Drafts (object): var, mutable collection
- `klangui/src/jsMain/kotlin/feel/KlangBrushedMetal.kt`: KlangBrushedMetal (object): var
- `klangui/src/jsMain/kotlin/comp/KlangDialog.kt`: KlangDialog (companion): var
- `src/jsMain/kotlin/KlangGuiDance.kt`: KlangGuiDance (object): var, browser storage
- `klangui/src/jsMain/kotlin/feel/KlangLookShelf.kt`: KlangLookShelf (object): stream source
- `src/jsMain/kotlin/pages/docs/KlangScriptLibraryDocsPage.kt`: KlangScriptLibraryDocsPage (companion): var
- `klangui/src/jsMain/kotlin/feel/KlangTheme.kt`: KlangTheme (object): stream source, var, derived stream
- `klangui/src/jsMain/kotlin/codetools/KlangToolAutoUpdate.kt`: KlangToolAutoUpdate (object): stream source, browser storage
- `klangui/src/jsMain/kotlin/KlangUiTool.kt`: KlangUiToolRegistry (object): mutable collection
- `src/jsMain/kotlin/visuals/KlangampDeck.kt`: KlangampDeck (object): var
- `src/jsMain/kotlin/pages/docs/tutorials/LearnTracksColumn.kt`: LearnSearch (object): stream source
- `klangui/src/jsMain/kotlin/comp/MarkdownDisplay.kt`: MarkdownDisplay (companion): mutable collection
- `src/jsMain/kotlin/comp/MotorBackground.kt`: MotorBackground (companion): var
- `src/jsMain/kotlin/pages/docs/tutorials/TutorialPage.kt`: TutorialPage (companion): var
- `src/jsMain/kotlin/visuals/VisualsScreen.kt`: VisualsScreen (companion): stream source, derived stream
- `src/jsMain/kotlin/layouts/WorkbenchSlots.kt`: WorkbenchSlots (object): stream source
- `src/jsMain/kotlin/index.kt`: fs (val): top-level value
- `src/jsMain/kotlin/index.kt`: keysManager (val): top-level value
- `src/jsMain/kotlin/index.kt`: kraft (val): top-level value
- `src/jsMain/kotlin/comp/MotorBackground.kt`: motorBackgroundRef (val): top-level value
- `src/jsMain/kotlin/index.kt`: version (val): top-level value

### component references (a parent's own child, or lateral: read which) (9)

- `klangscript-ui/src/jsMain/kotlin/codemirror/CodeMirrorComp.kt`: 1 references
- `src/jsMain/kotlin/comp/KlangCodeEditorComp.kt`: 2 references
- `src/jsMain/kotlin/comp/KlangScriptReplComp.kt`: 1 references
- `src/jsMain/kotlin/comp/MotorBackground.kt`: 4 references
- `src/jsMain/kotlin/comp/PlayableCodeExample.kt`: 1 references
- `src/jsMain/kotlin/comp/editor_helpers.kt`: 1 references
- `src/jsMain/kotlin/layouts/WorkbenchSlots.kt`: 1 references
- `src/jsMain/kotlin/pages/CodeSongPage.kt`: 1 references
- `src/jsMain/kotlin/visuals/VisualsScreen.kt`: 1 references

### stream sources made outside the Spine (15)

- `klangui/src/jsMain/kotlin/GuiDance.kt`: 1: litSource
- `klangui/src/jsMain/kotlin/codetools/KlangToolAutoUpdate.kt`: 1: source
- `klangui/src/jsMain/kotlin/comp/KlangWorkbench.kt`: 1: NoStepLit
- `klangui/src/jsMain/kotlin/feel/KlangLookShelf.kt`: 2: source
- `klangui/src/jsMain/kotlin/feel/KlangTheme.kt`: 4: source, lookSource
- `src/jsMain/kotlin/comp/QuickHelpPanel.kt`: 3: toolCall, help, lexikonSlug
- `src/jsMain/kotlin/layouts/WorkbenchSlots.kt`: 3: column, tools, bottomTabs
- `src/jsMain/kotlin/midi/MidiConnector.kt`: 2: _state, _events
- `src/jsMain/kotlin/pages/docs/tutorials/LearnTracksColumn.kt`: 2: text
- `src/jsMain/kotlin/pages/docs/tutorials/TutorialPage.kt`: 3: noSignals
- `src/jsMain/kotlin/utils/FullscreenController.kt`: 1: source
- `src/jsMain/kotlin/utils/VersionController.kt`: 1: source
- `src/jsMain/kotlin/visuals/VisualsScreen.kt`: 3: shown, klangampTiles, rainSpeed
- `workshop/src/jsMain/kotlin/keys/KeyHintStage.kt`: 2: _entries, _changes
- `workshop/src/jsMain/kotlin/keys/KeysManager.kt`: 4: _topLayer, _keymap, _hints, _waiting

### browser storage touched outside the storage rail (3)

- `klangui/src/jsMain/kotlin/codetools/KlangToolAutoUpdate.kt`: 1 references
- `src/jsMain/kotlin/KlangGuiDance.kt`: 4 references
- `src/jsMain/kotlin/pages/DevStoragePage.kt`: 4 references

### hand-written JSON (8)

- `klangui/src/jsMain/kotlin/feel/KlangLookText.kt`: 3 references
- `src/commonMain/kotlin/storage/KeymapJson.kt`: 4 references
- `src/jsMain/kotlin/spine/storage/MidiLoadoutStorage.kt`: 4 references
- `src/jsMain/kotlin/spine/storage/RecentsStorage.kt`: 8 references
- `src/jsMain/kotlin/storage/LookStorage.kt`: 5 references
- `src/jsMain/kotlin/storage/MidiInstrumentStorage.kt`: 6 references
- `src/jsMain/kotlin/storage/WorkbenchLayoutStorage.kt`: 9 references
- `src/jsMain/kotlin/utils/VersionController.kt`: 1 references

### raw DOM listeners (11)

- `sprudel/src/jsMain/kotlin/ui/NoteStaffEditor.kt`: 2 references
- `sprudel/src/jsMain/kotlin/ui/SprudelGainEditorTool.kt`: 2 references
- `sprudel/src/jsMain/kotlin/ui/SprudelNumericEditorTool.kt`: 3 references
- `sprudel/src/jsMain/kotlin/ui/SprudelPanEditorTool.kt`: 2 references
- `src/jsMain/kotlin/codemirror/CodeMirrorHighlightBuffer.kt`: 1 references
- `src/jsMain/kotlin/comp/MotorBackground.kt`: 1 references
- `src/jsMain/kotlin/pages/LooksPage.kt`: 1 references
- `src/jsMain/kotlin/spine/ConsoleLog.kt`: 2 references
- `src/jsMain/kotlin/utils/FullscreenController.kt`: 1 references
- `workshop/src/jsMain/kotlin/keys/KeyHintStage.kt`: 2 references
- `workshop/src/jsMain/kotlin/keys/KeysManager.kt`: 1 references

### storage reached from the UI (18)

- `src/jsMain/kotlin/comp/HelpWindow.kt`: EditorSettings (stream)
- `src/jsMain/kotlin/comp/HoverHelpToggle.kt`: EditorSettings (direct, stream)
- `src/jsMain/kotlin/comp/KlangCodeEditorComp.kt`: EditorSettings (direct, stream)
- `src/jsMain/kotlin/comp/MotorBackground.kt`: SeedStorage (direct)
- `src/jsMain/kotlin/comp/PlayableCodeExample.kt`: EditorSettings (stream)
- `src/jsMain/kotlin/comp/SongSettings.kt`: EditorSettings (direct, stream)
- `src/jsMain/kotlin/layouts/MenuPickUp.kt`: TutorialStorage (stream)
- `src/jsMain/kotlin/layouts/WorkbenchLayout.kt`: LookStorage (direct)
- `src/jsMain/kotlin/layouts/WorkbenchLayout.kt`: WorkbenchLayoutStorage (direct)
- `src/jsMain/kotlin/pages/CodeSongPage.kt`: EditorSettings (stream)
- `src/jsMain/kotlin/pages/LooksPage.kt`: LookStorage (direct)
- `src/jsMain/kotlin/pages/UiKitchenSinkPage.kt`: LookStorage (direct)
- `src/jsMain/kotlin/pages/docs/tutorials/LearnTracksColumn.kt`: TutorialStorage (stream)
- `src/jsMain/kotlin/pages/docs/tutorials/TutorialColumn.kt`: TutorialStorage (stream)
- `src/jsMain/kotlin/pages/docs/tutorials/TutorialPage.kt`: TutorialStorage (direct, stream)
- `src/jsMain/kotlin/pages/docs/tutorials/TutorialTrackPage.kt`: TutorialStorage (stream)
- `src/jsMain/kotlin/pages/docs/tutorials/TutorialsListPage.kt`: TutorialStorage (stream)
- `src/jsMain/kotlin/visuals/VisualsScreen.kt`: LookStorage (direct)

## Every holder's members, and who names them

### `EditorSettings` (storage)

- `HIGHLIGHTS_PER_EVENT_RANGE`: direct from 1: SongSettings
- `HIGHLIGHT_STRENGTH_RANGE`: direct from 1: SongSettings
- `highlightStrength`: direct from 1: KlangCodeEditorComp; stream from 2: KlangCodeEditorComp, SongSettings
- `highlightsOn`: direct from 1: KlangCodeEditorComp; stream from 2: KlangCodeEditorComp, SongSettings
- `highlightsPerEvent`: stream from 3: CodeSongPage, PlayableCodeExample, SongSettings
- `hoverHelp`: direct from 1: KlangCodeEditorComp; stream from 3: HelpWindow, HoverHelpToggle, SongSettings
- `setHighlightStrength`: direct from 1: SongSettings
- `setHighlightsOn`: direct from 1: SongSettings
- `setHighlightsPerEvent`: direct from 1: SongSettings
- `setHoverHelp`: direct from 2: HoverHelpToggle, SongSettings

### `KeymapStorage` (storage)

- `(itself)`: direct from 1: index

### `LookStorage` (storage)

- `load`: direct from 1: index
- `restoreShelf`: direct from 1: index
- `save`: direct from 4: LooksPage, UiKitchenSinkPage, VisualsScreen, WorkbenchLayout

### `MidiInstrumentStorage` (storage)

- `loadAll`: direct from 1: MidiPlayCodeStorage
- `loadDraft`: direct from 1: MidiPlayCodeStorage

### `MidiLoadoutStorage` (storage)

- `load`: direct from 1: MidiRig
- `save`: direct from 1: MidiRig

### `MidiPlayCodeStorage` (storage)

- `load`: direct from 1: MidiRig
- `save`: direct from 1: MidiRig

### `MidiSettingsStorage` (storage)

- `loadLayout`: direct from 1: MidiSettings
- `saveLayout`: direct from 1: MidiSettings

### `OutputVolumeStorage` (storage)

- `load`: direct from 1: Player
- `save`: direct from 1: Player

### `RecentsStorage` (storage)

- `load`: direct from 1: Recents
- `save`: direct from 1: Recents

### `SeedStorage` (storage)

- `metal`: direct from 3: MotorBackground, VisualState, index

### `TutorialStorage` (storage)

- `done`: stream from 6: LearnTracksColumn, MenuPickUp, TutorialColumn, TutorialPage, TutorialTrackPage, TutorialsListPage
- `setCompleted`: direct from 1: TutorialPage

### `WorkbenchLayoutStorage` (storage)

- `load`: direct from 1: WorkbenchLayout
- `save`: direct from 1: WorkbenchLayout

### `ConsoleLog` (spine)

- `captureUncaught`: direct from 1: index
- `clear`: direct from 1: ConsoleTool
- `install`: direct from 1: index
- `notShowing`: direct from 1: ConsoleTool
- `showing`: direct from 1: ConsoleTool
- `state`: stream from 2: ConsoleTool, WorkbenchLayout

### `MidiSettings` (spine)

- `chooseLayout`: direct from 1: LaptopKeysView
- `keyBedOffset`: direct from 1: DeviceView; stream from 1: DeviceView
- `layout`: stream from 1: LaptopKeysView
- `learn`: direct from 1: LaptopKeysView
- `letters`: stream from 1: LaptopKeysView
- `readBrowserLetters`: direct from 1: LaptopKeysView

### `MotorReadings` (spine)

- `NEEDLE_NEAR`: direct from 1: StartPage
- `NEEDLE_SMOOTHING`: direct from 1: StartPage
- `cylinders`: stream from 3: KlangampPlayer, PlayerMiniStats, VisualsBoard
- `heat`: stream from 3: KlangampPlayer, PlayerMiniStats, VisualsBoard
- `voices`: stream from 3: KlangampPlayer, PlayerMiniStats, VisualsBoard

### `Playbacks` (spine)

- `NowPlaying`: direct from 3: KlangCodePlaybackCtrl, KlangampDeck, KlangampPlaylist
- `SongLine`: direct from 4: LooksPage, MenuPickUp, StatusBar, UiKitchenSinkPage
- `heardSeconds`: stream from 2: KlangampPlayer, KlangampPlaylist
- `live`: direct from 5: DeviceView, LaptopKeysView, MidiPlaygroundPage, MidiTool, index
- `nowPlaying`: stream from 4: KlangampPlayer, KlangampPlaylist, VisualsBoard, VoiceRain
- `previews`: direct from 1: KlangCodePlaybackCtrl
- `publishNowPlaying`: direct from 1: KlangCodePlaybackCtrl
- `requestExclusivePlayback`: direct from 2: Previews, SongDeck
- `song`: direct from 5: CodeSongPage, KlangampDeck, MenuPickUp, StartPage, StatusBar
- `songLine`: stream from 2: MenuPickUp, StatusBar
- `stopNowPlaying`: direct from 1: KlangampDeck

### `Player` (spine)

- `(itself)`: direct from 2: KlangCodePlaybackCtrl, SongDeck
- `Status`: direct from 5: KlangCodePlaybackCtrl, MidiRig, MidiTool, PlaybackEngine, RealtimePlaybackCtrl
- `createEngine`: direct from 3: KlangScriptReplComp, MidiRig, jingles
- `diagnostics`: direct from 1: MotorReadings; stream from 2: MotorHistoryGraphs, PlayerWarehouseStats
- `ensure`: direct from 4: DevJinglesPage, RealtimePlaybackCtrl, SamplesLibraryPage, StartPage
- `get`: direct from 4: KlangampPlayer, Motor, StartPage, VisualsBoard
- `outputVolume`: stream from 1: VolumeKnob
- `player`: direct from 5: KlangampPlayer, LooksPage, Motor, UiKitchenSinkPage, VisualsBoard
- `playing`: direct from 1: ScreenAwake
- `rememberOutputVolume`: direct from 1: VolumeKnob
- `samples`: stream from 1: SamplesLibraryPage
- `setOutputVolume`: direct from 1: VolumeKnob
- `status`: direct from 1: RealtimePlaybackCtrl; stream from 2: MidiRig, RealtimePlaybackCtrl

### `Recents` (spine)

- `Entry`: direct from 3: MenuPickUp, RecentsStorage, UiKitchenSinkPage
- `Kind`: direct from 3: MenuPickUp, RecentsStorage, UiKitchenSinkPage
- `entries`: stream from 1: MenuPickUp
- `openedSong`: direct from 1: CodeSongPage
- `openedTutorial`: direct from 1: TutorialPage

### `UiWork` (spine)

- `allFrames`: stream from 1: Spectrumeter
- `frames`: direct from 1: VisualState; stream from 2: StartPage, VoiceRain
- `resting`: direct from 7: Motor, MotorBackground, MotorHistoryGraphs, MotorReadings, Oscilloscope, PlayerMiniStats, Spectrumeter; stream from 2: PlayerMiniStats, WorkbenchLayout

### `VisualState` (spine)

- `OpenSong`: direct from 1: CodeSongPage
- `lamp`: direct from 3: MotorBackground, StartPage, VisualsScreen
- `light`: direct from 1: VisualsBoard; stream from 2: MotorBackground, StartPage
- `offerSong`: direct from 1: CodeSongPage
- `openSong`: direct from 1: VisualsBoard

### `KlangGuiDance` (app)

- `BOTTOM_PANEL`: direct from 1: BottomTools
- `DRAWER_LEARN`: direct from 1: WorkbenchLayout
- `DRAWER_SONGS`: direct from 1: WorkbenchLayout
- `FULLSCREEN`: direct from 1: FullscreenToggleButton
- `LEARN_FIRST_STEPS`: direct from 1: TutorialsListPage
- `LOOK_EDIT`: direct from 1: WorkbenchLayout
- `LOOK_STEPS`: direct from 1: WorkbenchLayout
- `MOTOR_MENU`: direct from 1: PlayerMiniStats
- `SONGS_OPEN`: direct from 1: SongsPage
- `SONG_PLAY`: direct from 1: CodeSongPage
- `VISUALS_CLOSE`: direct from 1: VisualsScreen
- `VISUALS_LOOKS`: direct from 1: VisualsScreen
- `VISUALS_TAB`: direct from 1: VisualsTab
- `VISUAL_KLANGAMP_TILES`: direct from 1: VisualsTab
- `WALKTHROUGHS`: direct from 1: WalkthroughsButton
- `dance`: direct from 10: CodeSongPage, FullscreenToggleButton, PlayerMiniStats, SongsPage, TutorialsListPage, VisualsScreen, VisualsTab, WalkthroughsButton, WalkthroughsWindow, WorkbenchLayout; stream from 8: CodeSongPage, FullscreenToggleButton, PlayerMiniStats, SongsPage, TutorialsListPage, VisualsScreen, WalkthroughsButton, WorkbenchLayout
- `mute`: direct from 1: WalkthroughsWindow
- `unmute`: direct from 1: WalkthroughsWindow

### `fs` (app)

- `(itself)`: direct from 1: WorkbenchLayout
- `enter`: direct from 1: StartPage

### `version` (app)

- `(itself)`: stream from 3: StartPage, StatusBar, WorkbenchLayout
- `map`: stream from 1: CodeSongPage

### `WorkbenchSlots` (layout)

- `bottomTabs`: direct from 1: PageWorkbench; stream from 1: WorkbenchLayout
- `column`: direct from 1: PageWorkbench; stream from 1: WorkbenchLayout
- `tools`: direct from 1: PageWorkbench; stream from 1: WorkbenchLayout
- `workbench`: direct from 2: QuickHelpPanel, WorkbenchLayout

### `KlangampDeck` (pages)

- `indexOf`: direct from 2: KlangampPlayer, KlangampPlaylist
- `leave`: direct from 1: VisualsBoard
- `play`: direct from 2: KlangampPlayer, KlangampPlaylist
- `playAgain`: direct from 1: VisualsBoard
- `skip`: direct from 2: KlangampPlayer, KlangampPlaylist
- `stop`: direct from 3: KlangampPlayer, KlangampPlaylist, VisualsBoard

### `LearnSearch` (pages)

- `text`: direct from 1: WorkbenchLayout; stream from 1: TutorialsListPage

### `VisualsScreen` (pages)

- `klangampTiles`: direct from 1: VisualsScreen; stream from 1: VisualsScreen
- `open`: direct from 1: KlangCodeEditorComp; stream from 2: KlangCodeEditorComp, WorkbenchLayout
- `rainSpeed`: direct from 1: VisualsScreen; stream from 1: VisualsScreen
- `shown`: direct from 1: VisualsTab

### `motorBackgroundRef` (components)

- `(itself)`: direct from 1: StartPage

### `KlangBrushedMetal` (library)

- `install`: direct from 1: index

### `KlangLookShelf` (library)

- `(itself)`: direct from 1: LookStorage; stream from 3: KlangLookPicker, LooksPage, VisualsScreen
- `State`: direct from 1: LookStorage
- `delete`: direct from 1: LooksPage
- `keep`: direct from 1: LooksPage
- `newId`: direct from 1: LooksPage
- `place`: direct from 1: LooksPage
- `restore`: direct from 1: LookStorage
- `subscribeToStream`: stream from 1: LookStorage
- `unequip`: direct from 1: LooksPage

### `KlangTheme` (library)

- `(itself)`: direct from 3: DslEditorExtension, KlangToolInfoHelpers, SprudelScaleEditorTool; stream from 33: DevStatusPage, KlangScriptDocsPage, KlangScriptLibraryDocsPage, KlangScriptReplComp, KlangSymbolDocsComp, LexikonEntryPage, LexikonPage, MnEditorBase, NoteStaffEditor, ResourcesPage, SprudelAdsrEditorTool, SprudelBodyEditorTool, SprudelCompressorEditorTool, SprudelDelayEditorTool, SprudelDistortEditorTool, SprudelDistortShapeEditorTool, SprudelDustEditorTool, SprudelEuclidEditorTool, SprudelFilterAdsrEditorTool, SprudelFilterEditorTool, SprudelGainEditorTool, SprudelNumericEditorTool, SprudelPanEditorTool, SprudelPhaserEditorTool, SprudelPluckEditorTool, SprudelPulzeEditorTool, SprudelReverbEditorTool, SprudelSampleEditorTool, SprudelSuperPluckEditorTool, SprudelSuperSawEditorTool, SprudelTremoloEditorTool, SprudelWaveformEditorTool, ToolButtonBar
- `Hex`: direct from 4: BookmarksExtension, CodeMirrorTheme, DslEditorExtension, EditorKeysCorner
- `choose`: direct from 4: KlangLookPicker, LooksPage, VisualsScreen, index
- `chosen`: direct from 2: KlangLookPicker, VisualsScreen
- `critical`: direct from 1: Spectrumeter
- `endPreview`: direct from 1: LooksPage
- `excellent`: direct from 1: Spectrumeter
- `good`: direct from 1: Spectrumeter
- `initialize`: direct from 1: index
- `layers`: direct from 1: UiKitchenSinkPage
- `look`: stream from 9: KlangLookPicker, KlangampPlayer, LooksPage, Motor, MotorBackground, UiKitchenSinkPage, VisualsBoard, VisualsScreen, VoiceRain
- `moderate`: direct from 1: Spectrumeter
- `preview`: direct from 1: LooksPage
- `setLayer`: direct from 1: UiKitchenSinkPage
- `warning`: direct from 1: Spectrumeter
- `worn`: direct from 1: LooksPage

### `KlangToolAutoUpdate` (library)

- `(itself)`: stream from 25: MnEditorBase, SprudelAdsrEditorTool, SprudelBodyEditorTool, SprudelCompressorEditorTool, SprudelDelayEditorTool, SprudelDistortEditorTool, SprudelDistortShapeEditorTool, SprudelDustEditorTool, SprudelEuclidEditorTool, SprudelFilterAdsrEditorTool, SprudelFilterEditorTool, SprudelGainEditorTool, SprudelNumericEditorTool, SprudelPanEditorTool, SprudelPhaserEditorTool, SprudelPluckEditorTool, SprudelPulzeEditorTool, SprudelReverbEditorTool, SprudelSampleEditorTool, SprudelScaleEditorTool, SprudelSuperPluckEditorTool, SprudelSuperSawEditorTool, SprudelTremoloEditorTool, SprudelWaveformEditorTool, ToolButtonBar
- `toggle`: direct from 2: SprudelScaleEditorTool, ToolButtonBar

### `KlangUiToolRegistry` (library)

- `(itself)`: direct from 1: SprudelUiTools
- `resolve`: direct from 2: DslEditorExtension, ToolCallFollower

