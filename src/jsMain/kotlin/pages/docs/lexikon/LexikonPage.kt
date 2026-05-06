package io.peekandpoke.klang.pages.docs.lexikon

import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.kraft.components.NoProps
import io.peekandpoke.kraft.components.PureComponent
import io.peekandpoke.kraft.components.comp
import io.peekandpoke.kraft.semanticui.forms.UiInputField
import io.peekandpoke.kraft.vdom.VDom
import io.peekandpoke.ultra.common.toggle
import io.peekandpoke.ultra.html.css
import io.peekandpoke.ultra.html.onClick
import io.peekandpoke.ultra.semanticui.icon
import io.peekandpoke.ultra.semanticui.semanticIcon
import io.peekandpoke.ultra.semanticui.ui
import kotlinx.css.BorderCollapse
import kotlinx.css.Color
import kotlinx.css.Cursor
import kotlinx.css.Display
import kotlinx.css.FlexWrap
import kotlinx.css.FontStyle
import kotlinx.css.FontWeight
import kotlinx.css.Padding
import kotlinx.css.TextAlign
import kotlinx.css.backgroundColor
import kotlinx.css.borderBottomColor
import kotlinx.css.borderCollapse
import kotlinx.css.color
import kotlinx.css.cursor
import kotlinx.css.display
import kotlinx.css.flexWrap
import kotlinx.css.fontSize
import kotlinx.css.fontStyle
import kotlinx.css.fontWeight
import kotlinx.css.gap
import kotlinx.css.letterSpacing
import kotlinx.css.marginBottom
import kotlinx.css.marginTop
import kotlinx.css.padding
import kotlinx.css.pct
import kotlinx.css.px
import kotlinx.css.rem
import kotlinx.css.textAlign
import kotlinx.css.width
import kotlinx.html.FlowContent
import kotlinx.html.Tag
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

@Suppress("FunctionName")
fun Tag.LexikonPage() = comp {
    LexikonPage(it)
}

class LexikonPage(ctx: NoProps) : PureComponent(ctx) {

    //  STATE  //////////////////////////////////////////////////////////////////////////////////////////////////

    private val laf by subscribingTo(KlangTheme)

    private var searchText by value("")
    private var selectedDomain: LexikonDomain? by value(null)
    private var selectedCategories: Set<LexikonCategory> by value(emptySet())
    private var selectedTags: Set<LexikonTag> by value(emptySet())

    //  FILTERING  //////////////////////////////////////////////////////////////////////////////////////////////

    private fun filteredEntries(): List<LexikonEntry> {
        return allLexikonEntries.filter { entry ->
            val matchesSearch = searchText.isBlank() ||
                    entry.term.contains(searchText, ignoreCase = true) ||
                    entry.summary.contains(searchText, ignoreCase = true) ||
                    entry.detail.contains(searchText, ignoreCase = true) ||
                    entry.conventional?.contains(searchText, ignoreCase = true) == true ||
                    entry.tags.any { it.label.contains(searchText, ignoreCase = true) }

            val matchesDomain = selectedDomain == null || entry.domain == selectedDomain
            val matchesCategory = selectedCategories.isEmpty() || entry.category in selectedCategories
            val matchesTags = selectedTags.isEmpty() || selectedTags.all { it in entry.tags }

            matchesSearch && matchesDomain && matchesCategory && matchesTags
        }
    }

    private fun availableCategories(): List<LexikonCategory> {
        return if (selectedDomain != null) {
            LexikonCategory.entries.filter { it.domain == selectedDomain }
        } else {
            LexikonCategory.entries.toList()
        }
    }

    private fun clearFilters() {
        searchText = ""
        selectedDomain = null
        selectedCategories = emptySet()
        selectedTags = emptySet()
    }

    private val hasActiveFilters: Boolean
        get() = searchText.isNotBlank() || selectedDomain != null ||
                selectedCategories.isNotEmpty() || selectedTags.isNotEmpty()

    //  RENDER  /////////////////////////////////////////////////////////////////////////////////////////////////

    override fun VDom.render() {
        ui.fluid.container {
            css { padding = Padding(2.rem) }

            // Header
            ui.segment {
                ui.header { +"Lexikon" }
                ui.sub.header { +"The Klang vocabulary — audio and pattern terms, decoded" }
            }

            // Filters
            ui.segment {
                renderSearch()
                ui.divider()
                renderDomainFilter()
                ui.divider()
                renderCategoryFilter()
                ui.divider()
                renderTagFilter()
            }

            // Results
            renderResults()
        }
    }

    //  SEARCH  /////////////////////////////////////////////////////////////////////////////////////////////////

    private fun FlowContent.renderSearch() {
        ui.form {
            UiInputField(value = searchText, onChange = { searchText = it }) {
                placeholder("Search the Lexikon...")
                rightClearingIcon()
                leftLabel {
                    ui.grey.label { icon.search(); +"Search" }
                }
            }
        }
    }

    //  DOMAIN FILTER  //////////////////////////////////////////////////////////////////////////////////////////

    private fun FlowContent.renderDomainFilter() {
        div {
            css {
                display = Display.flex
                flexWrap = FlexWrap.wrap
                gap = 4.px
            }

            // "All" button
            ui.mini.givenNot(selectedDomain == null) { basic }
                .given(selectedDomain == null) { with(laf.styles.goldButton()) }
                .button {
                    onClick {
                        selectedDomain = null
                        selectedCategories = emptySet()
                    }
                    icon.circle()
                    +"All Domains"
                }

            LexikonDomain.entries.forEach { domain ->
                val isSelected = selectedDomain == domain
                ui.mini.givenNot(isSelected) { basic }
                    .given(isSelected) { with(laf.styles.goldButton()) }
                    .button {
                        onClick {
                            selectedDomain = if (isSelected) null else domain
                            // Clear category selection when domain changes
                            selectedCategories = if (isSelected) {
                                emptySet()
                            } else {
                                selectedCategories.filter { it.domain == domain }.toSet()
                            }
                        }
                        icon.(domainIconFn(domain))().render()
                        +domain.label
                    }
            }

            // Clear all filters
            if (hasActiveFilters) {
                ui.mini.basic.red.button {
                    onClick { clearFilters() }
                    icon.times()
                    +"Clear All"
                }
            }
        }
    }

    //  CATEGORY FILTER  ////////////////////////////////////////////////////////////////////////////////////////

    private fun FlowContent.renderCategoryFilter() {
        val categories = availableCategories()

        div {
            css {
                display = Display.flex
                flexWrap = FlexWrap.wrap
                gap = 4.px
            }

            categories.forEach { category ->
                val isSelected = category in selectedCategories
                ui.mini.givenNot(isSelected) { basic }
                    .given(isSelected) { with(laf.styles.goldButton()) }
                    .button {
                        onClick { selectedCategories = selectedCategories.toggle(category) }
                        +category.label
                    }
            }
        }
    }

    //  TAG FILTER  /////////////////////////////////////////////////////////////////////////////////////////////

    private fun FlowContent.renderTagFilter() {
        div {
            css {
                display = Display.flex
                flexWrap = FlexWrap.wrap
                gap = 4.px
            }

            LexikonTag.entries.forEach { tag ->
                val isSelected = tag in selectedTags
                ui.mini.basic.given(isSelected) { with(laf.styles.goldButton()) }
                    .button {
                        onClick { selectedTags = selectedTags.toggle(tag) }
                        +tag.label
                    }
            }
        }
    }

    //  RESULTS  ////////////////////////////////////////////////////////////////////////////////////////////////

    private fun FlowContent.renderResults() {
        val entries = filteredEntries()

        if (entries.isEmpty()) {
            ui.placeholder.segment {
                ui.icon.header {
                    icon.search()
                    +"No entries match your filters"
                }
            }
            return
        }

        // Group by category, preserving category enum order
        val grouped: List<Pair<LexikonCategory, List<LexikonEntry>>> = entries
            .groupBy { it.category }
            .entries
            .sortedBy { it.key.ordinal }
            .map { it.key to it.value }

        grouped.forEach { (category, categoryEntries) ->
            val categoryDomain = category.domain
            val categoryLabel = category.label
            val domainLabel = categoryDomain.label
            val domColor = domainColor(categoryDomain)
            val domIcon = domainIconFn(categoryDomain)

            ui.segment {
                css {
                    backgroundColor = Color(laf.cardBackground)
                }

                // Category header with domain badge
                div {
                    css {
                        display = Display.flex
                        gap = 8.px
                        marginBottom = 1.rem
                    }

                    ui.large.basic.label {
                        css {
                            color = Color("$domColor !important")
                            put("border-color", "$domColor !important")
                        }
                        icon.domIcon().render()
                        +domainLabel
                    }

                    ui.large.header {
                        css {
                            color = Color(laf.textPrimary)
                            marginTop = 0.px
                        }
                        +categoryLabel
                    }
                }

                // Entries table
                ui.basic.table Table {
                    css {
                        width = 100.pct
                        borderCollapse = BorderCollapse.collapse
                    }

                    thead {
                        tr {
                            th {
                                css {
                                    width = 180.px
                                    padding = Padding(8.px)
                                    color = Color(laf.textSecondary)
                                    fontSize = 1.0.rem
                                    fontWeight = FontWeight.normal
                                    letterSpacing = 0.5.px
                                    borderBottomColor = Color(laf.textTertiary)
                                    textAlign = TextAlign.left
                                }
                                +"TERM"
                            }
                            th {
                                css {
                                    padding = Padding(8.px)
                                    color = Color(laf.textSecondary)
                                    fontSize = 1.0.rem
                                    fontWeight = FontWeight.normal
                                    letterSpacing = 0.5.px
                                    borderBottomColor = Color(laf.textTertiary)
                                    textAlign = TextAlign.left
                                }
                                +"DESCRIPTION"
                            }
                        }
                    }

                    tbody {
                        val items: List<LexikonEntry> = categoryEntries

                        items.forEach { lexEntry ->
                            val entryTags: Set<LexikonTag> = lexEntry.tags
                            val entryTerm: String = lexEntry.term
                            val entrySummary: String = lexEntry.summary
                            val entryDetail: String = lexEntry.detail
                            val entryConventional: String? = lexEntry.conventional
                            val entryTrivia: LexikonTrivia? = lexEntry.trivia

                            tr("top aligned") {
                                css {
                                    cursor = Cursor.default
                                }

                                // Term cell
                                td {
                                    css {
                                        padding = Padding(10.px, 8.px)
                                        borderBottomColor = Color("${laf.textTertiary}33")
                                    }

                                    span {
                                        css {
                                            fontWeight = FontWeight.bold
                                            fontSize = 1.1.rem
                                            color = Color(laf.gold)
                                        }
                                        +entryTerm
                                    }

                                    // Tags below term
                                    div {
                                        css {
                                            marginTop = 4.px
                                            display = Display.flex
                                            flexWrap = FlexWrap.wrap
                                            gap = 2.px
                                        }
                                        entryTags.forEach { entryTag ->
                                            val tagLabel = entryTag.label
                                            ui.mini.basic.label {
                                                css {
                                                    fontSize = 0.75.rem
                                                    color = Color("${laf.textSecondary} !important")
                                                    put("border-color", "${laf.textSecondary} !important")
                                                }
                                                +tagLabel
                                            }
                                        }
                                    }
                                }

                                // Description cell
                                td {
                                    css {
                                        padding = Padding(10.px, 8.px)
                                        borderBottomColor = Color("${laf.textTertiary}33")
                                    }

                                    // Summary
                                    p {
                                        css {
                                            color = Color(laf.textPrimary)
                                            fontSize = 1.1.rem
                                            marginTop = 0.px
                                            marginBottom = 6.px
                                        }
                                        +entrySummary
                                    }

                                    // Detail
                                    p {
                                        css {
                                            color = Color(laf.textPrimary)
                                            fontStyle = FontStyle.italic
                                            fontSize = 1.05.rem
                                            marginTop = 0.px
                                            marginBottom = 6.px
                                        }
                                        +entryDetail
                                    }

                                    // Conventional term
                                    if (entryConventional != null) {
                                        p {
                                            css {
                                                color = Color(laf.textSecondary)
                                                fontSize = 1.rem
                                                put("font-family", "monospace")
                                                marginTop = 0.px
                                                marginBottom = 0.px
                                            }
                                            +"aka: $entryConventional"
                                        }
                                    }

                                    // Trivia
                                    if (entryTrivia != null) {
                                        ui.segment {
                                            css {
                                                marginTop = 8.px
                                                padding = Padding(8.px, 12.px)
                                                put("background-color", "${laf.appBackground} !important")
                                                put("border-radius", "4px !important")
                                                put("border", "1px solid ${laf.warning} !important")
                                                put("box-shadow", "0 0 6px ${laf.warning}44 !important")
                                            }

                                            // Trivia text
                                            p {
                                                css {
                                                    color = Color(laf.textSecondary)
                                                    fontSize = 0.95.rem
                                                    marginTop = 0.px
                                                    marginBottom = 4.px
                                                }
                                                icon.history()
                                                +" "
                                                +entryTrivia.text
                                            }

                                            // Trivia metadata line
                                            val metaParts = listOfNotNull(
                                                entryTrivia.year,
                                                entryTrivia.person,
                                                entryTrivia.device,
                                            )
                                            if (metaParts.isNotEmpty()) {
                                                p {
                                                    css {
                                                        color = Color(laf.textSecondary)
                                                        fontSize = 0.88.rem
                                                        marginTop = 0.px
                                                        marginBottom = 0.px
                                                    }
                                                    +metaParts.joinToString(" · ")
                                                }
                                            }

                                            // Source link
                                            val triviaUrl = entryTrivia.url
                                            if (triviaUrl != null) {
                                                a {
                                                    css {
                                                        color = Color(laf.accent)
                                                        fontSize = 0.88.rem
                                                    }
                                                    href = triviaUrl
                                                    target = "_blank"
                                                    +"Read more"
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
        }
    }

    //  HELPERS  /////////////////////////////////////////////////////////////////////////////////////////////////

    private fun domainColor(domain: LexikonDomain): String = when (domain) {
        LexikonDomain.Audio -> laf.accent
        LexikonDomain.Pattern -> laf.good
        LexikonDomain.Technical -> laf.warning
    }

    private fun domainIconFn(domain: LexikonDomain) = when (domain) {
        LexikonDomain.Audio -> semanticIcon { volume_up }
        LexikonDomain.Pattern -> semanticIcon { wave_square }
        LexikonDomain.Technical -> semanticIcon { info_circle }
    }
}
