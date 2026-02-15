package io.peekandpoke.klang.pages

import de.peekandpoke.kraft.components.NoProps
import de.peekandpoke.kraft.components.PureComponent
import de.peekandpoke.kraft.components.comp
import de.peekandpoke.kraft.semanticui.forms.UiInputField
import de.peekandpoke.kraft.utils.launch
import de.peekandpoke.kraft.vdom.VDom
import de.peekandpoke.ultra.html.key
import de.peekandpoke.ultra.html.onClick
import de.peekandpoke.ultra.semanticui.icon
import de.peekandpoke.ultra.semanticui.noui
import de.peekandpoke.ultra.semanticui.ui
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.audio_fe.samples.Samples
import io.peekandpoke.klang.strudel.lang.bank
import io.peekandpoke.klang.strudel.lang.n
import io.peekandpoke.klang.strudel.lang.s
import io.peekandpoke.klang.strudel.lang.slow
import io.peekandpoke.klang.strudel.playStrudelOnce
import kotlinx.html.DIV
import kotlinx.html.Tag
import kotlinx.html.b

@Suppress("FunctionName")
fun Tag.SamplesLibraryPage() = comp {
    SamplesLibraryPage(it)
}

class SamplesLibraryPage(ctx: NoProps) : PureComponent(ctx) {

    //  DATA STRUCTURES  ////////////////////////////////////////////////////////////////////////////////////////

    data class SampleEntry(
        val bankKey: String,
        val soundKey: String,
    ) {
        val displayBankName: String get() = bankKey.ifEmpty { "(default)" }
    }

    private enum class GroupBy {
        BANK,
        SOUND,
        NONE
    }

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val samples: Samples? by subscribingTo(Player.samplesStream)
    private var searchText: String by value("")
    private var groupBy: GroupBy by value(GroupBy.BANK)

    init {
        lifecycle {
            onMount {
                launch {
                    Player.ensure().await()
                }
            }
        }
    }

    //  DERIVED DATA  ///////////////////////////////////////////////////////////////////////////////////////////

    private val allEntries: List<SampleEntry>
        get() {
            val idx = samples?.index ?: return emptyList()

            return idx.banks.flatMap { bank ->
                bank.sounds.map { sound ->
                    SampleEntry(
                        bankKey = bank.key,
                        soundKey = sound.key,
                    )
                }
            }.sortedWith(compareBy({ if (it.bankKey.isEmpty()) 0 else 1 }, { it.bankKey }, { it.soundKey }))
        }

    private val normalizedSearch: String
        get() = searchText.trim().lowercase()

    private val filteredEntries: List<SampleEntry>
        get() {
            if (normalizedSearch.isEmpty()) return allEntries

            return allEntries.filter { entry ->
                entry.soundKey.lowercase().contains(normalizedSearch) ||
                        entry.bankKey.lowercase().contains(normalizedSearch)
            }
        }

    private val displayGroups: List<Pair<String, List<SampleEntry>>>
        get() {
            return when (groupBy) {
                GroupBy.BANK -> {
                    filteredEntries
                        .groupBy { it.bankKey }
                        .map { (key, entries) ->
                            (if (key.isEmpty()) "(default)" else key) to entries.sortedBy { it.soundKey }
                        }
                        .sortedWith(compareBy({ if (it.first == "(default)") 0 else 1 }, { it.first }))
                }

                GroupBy.SOUND -> {
                    filteredEntries
                        .groupBy { it.soundKey }
                        .map { (key, entries) -> key to entries.sortedBy { it.bankKey } }
                        .sortedBy { it.first }
                }

                GroupBy.NONE -> {
                    listOf("All Samples" to filteredEntries)
                }
            }
        }

    //  ACTIONS  ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun playSample(bank: String, sound: String, index: Int? = null) {
        // Ensure player is ready
        val player = Player.get() ?: return

        val pattern = s(sound).n(index).bank(bank)
            .slow(4)  // give enough time to ring out

        // Play once for 1 cycle
        val playback = player.playStrudelOnce(pattern, cycles = 1)
        playback.start()
    }

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        ui.fluid.container.with("noise-bg") {
            key = "samples-library-page"

            // Controls section
            ui.basic.segment {
                ui.form {
                    ui.two.fields {
                        UiInputField(searchText, { searchText = it }) {
                            leftLabel {
                                ui.basic.label { icon.search(); +"Search" }
                            }
                        }

                        ui.field {
                            ui.menu {
                                ui.item.with(if (groupBy == GroupBy.BANK) "active" else "") {
                                    +"Group by Bank"
                                    onClick { groupBy = GroupBy.BANK }
                                }
                                ui.item.with(if (groupBy == GroupBy.SOUND) "active" else "") {
                                    +"Group by Sound"
                                    onClick { groupBy = GroupBy.SOUND }
                                }
                                ui.item.with(if (groupBy == GroupBy.NONE) "active" else "") {
                                    +"No Grouping"
                                    onClick { groupBy = GroupBy.NONE }
                                }
                            }
                        }
                    }
                }
            }

            // Results section
            when {
                samples == null -> {
                    ui.segment {
                        ui.active.loader {}
                    }
                }

                displayGroups.isEmpty() || displayGroups.all { it.second.isEmpty() } -> {
                    ui.placeholder.segment {
                        ui.header {
                            +"No samples found"
                        }
                    }
                }

                else -> {
                    displayGroups.forEach { (groupName, entries) ->
                        ui.segment {
                            key = "group-$groupName"

                            ui.header {
                                +"$groupName (${entries.size})"
                            }

                            ui.four.column.stackable.doubling.cards {
                                entries.forEach { entry ->
                                    renderCard(entry)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun DIV.renderCard(entry: SampleEntry) {
        val provider =
            samples?.index?.banksByKey?.get(entry.bankKey)?.getSound(entry.soundKey)

        val variantCount = provider?.variantCount ?: 1
        val sampleType = provider?.sampleType ?: Samples.SampleType.SINGLE
        val noteRange = provider?.noteRange

        noui.card {
            key = "sample-${entry.bankKey}-${entry.soundKey}"

            ui.content {
                ui.horizontal.list {
                    noui.item {
                        b { +entry.soundKey }
                    }
                    if (groupBy != GroupBy.BANK) {
                        noui.item {
                            ui.basic.label { +"Bank: ${entry.displayBankName}" }
                        }
                    }
                    noui.item {
                        when (sampleType) {
                            Samples.SampleType.SINGLE -> {
                                // noop
                            }

                            Samples.SampleType.PITCHED -> {
                                // Show note range badge + single play button
                                ui.basic.label {
                                    +"Pitched"
                                    if (noteRange != null) {
                                        ui.detail { +noteRange }
                                    }
                                }
                            }

                            Samples.SampleType.VARIANTS -> {
                                // Show variant count + individual play buttons
                                ui.basic.label {
                                    +"$variantCount variants"
                                }
                            }
                        }
                    }
                }

                ui.divider {}

                when (sampleType) {
                    Samples.SampleType.SINGLE -> {
                        // Single play button
                        ui.basic.fluid.button {
                            onClick {
                                playSample(bank = entry.bankKey, sound = entry.soundKey, index = null)
                            }
                            icon.play()
                        }
                    }

                    Samples.SampleType.PITCHED -> {
                        // Show note range badge + single play button
                        ui.basic.fluid.icon.button {
                            onClick {
                                playSample(bank = entry.bankKey, sound = entry.soundKey, index = null)
                            }
                            icon.play()
                        }
                    }

                    Samples.SampleType.VARIANTS -> {
                        // Show variant count + individual play buttons
                        ui.horizontal.list {
                            for (i in 0 until variantCount) {
                                noui.item {
                                    ui.mini.basic.label.button {
                                        onClick {
                                            playSample(bank = entry.bankKey, sound = entry.soundKey, index = i)
                                        }

                                        icon.play()
                                        +"#${i + 1}"
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
