package io.peekandpoke.klang

import de.peekandpoke.kraft.routing.RootRouterBuilder
import de.peekandpoke.kraft.routing.Route1
import de.peekandpoke.kraft.routing.Static
import io.peekandpoke.klang.layouts.FullscreenLayout
import io.peekandpoke.klang.layouts.MenuLayout
import io.peekandpoke.klang.pages.*
import io.peekandpoke.klang.pages.docs.DocsPage
import io.peekandpoke.klang.pages.docs.KlangScriptDocsPage
import io.peekandpoke.klang.pages.docs.SprudelDocsPage

object Nav {
    val start = Static("")
    val startSlash = Static("/")

    val dashboard = Static("/dashboard")

    val newSongCode = Static("/song/code")
    val editSongCode = Route1("/song/code/{id}")

    val samplesLibrary = Static("/samples/library")

    const val manualsBase = "/manuals"
    val manuals = Static(manualsBase)
    val manualsSprudel = Static("$manualsBase/sprudel")
    fun manualsSprudelSearch(search: String) = manualsSprudel().withQueryParams(SprudelDocsPage.PARAM_SEARCH to search)
    val manualsKlangScript = Static("$manualsBase/klang-script")

    val credits = Static("/credits")

    val tour = Static("/tour")
}

fun RootRouterBuilder.mountNav() {

    layout({ FullscreenLayout { it() } }) {
        mount(Nav.start) { StartPage() }
        mount(Nav.startSlash) { StartPage() }

        mount(Nav.tour) { TourPage() }
    }

    layout({ MenuLayout { it() } }) {

        mount(Nav.dashboard) { DashboardPage() }

        mount(Nav.newSongCode) { CodeSongPage(id = null) }
        mount(Nav.editSongCode) { CodeSongPage(id = it["id"]) }

        mount(Nav.samplesLibrary) { SamplesLibraryPage() }

        mount(Nav.manuals) { DocsPage() }
        mount(Nav.manualsSprudel) { SprudelDocsPage() }
        mount(Nav.manualsKlangScript) { KlangScriptDocsPage() }

        mount(Nav.credits) { CreditsPage() }
    }

    layout({ FullscreenLayout { it() } }) {
        catchAll {
            NotFoundPage()
        }
    }
}
