package eu.kanade.tachiyomi.extension.zh.roumanwu

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Roumanwu :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val preferences = getPreferences()

    override val baseUrl get() = preferences.baseUrl

    private val updateMirror = UpdateMirror(preferences)

    override val client = network.client.newBuilder()
        .apply { interceptors().add(0, updateMirror) }
        .addInterceptor(ScrambledImageInterceptor())
        .build()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        setupUrlPreference(screen.context, screen, preferences)
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/home", headers)

    private fun parseEntries(container: Element): List<SManga> = container
        .select("a.site-comic[href*=/books/], a[href*=/books/]")
        .mapNotNull { anchor ->
            val href = anchor.attr("href")
            if (CHAPTER_URL_REGEX.matches(href)) return@mapNotNull null

            val title = anchor.selectFirst("h3")?.text()
                ?: anchor.selectFirst("div.truncate")?.text()
                ?: anchor.attr("title").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            val thumbnail = anchor.selectFirst("div.site-comic-cover img")?.absUrl("src")
                ?: anchor.selectFirst("div.bg-cover")?.attr("style")
                    ?.substringAfter("background-image:url(\"")
                    ?.substringBefore("\")")
                ?: return@mapNotNull null

            SManga.create().apply {
                this.title = title
                url = href
                thumbnail_url = thumbnail
            }
        }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return parseHomePage(document, Regex("正熱門|今日最佳|本週熱門"))
    }

    private fun parseHomePage(document: Document, sections: Regex): MangasPage {
        val entries = document.select("div.site-section-heading").flatMap { heading ->
            val title = heading.selectFirst("h1, h2")?.text().orEmpty()
            if (!sections.containsMatchIn(title)) return@flatMap emptyList()

            var sibling = heading.nextElementSibling()
            while (sibling != null) {
                if (sibling.hasClass("site-comic-grid")) {
                    return@flatMap parseEntries(sibling)
                }
                if (sibling.hasClass("site-section-heading")) break
                sibling = sibling.nextElementSibling()
            }
            emptyList()
        }.distinctBy { it.url }

        return MangasPage(entries, false)
    }

    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return parseHomePage(document, Regex("最近更新"))
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = if (query.isNotBlank()) {
        GET("$baseUrl/search?term=$query&page=${page - 1}", headers)
    } else {
        val parts = filters.filterIsInstance<UriPartFilter>().joinToString("") { it.toUriPart() }
        GET("$baseUrl/books?page=${page - 1}$parts", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val grid = document.selectFirst("div.site-comic-grid") ?: document.body()!!
        val entries = parseEntries(grid)
        val hasNextPage = document.select("a.site-button, nav a").any { it.text().contains("下一頁") }
        return MangasPage(entries, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()

        title = document.selectFirst("div.site-book-info h1")?.text()
            ?: document.selectFirst("div.basis-3\\/5 .text-xl")?.text()
            ?: return@apply

        thumbnail_url = document.selectFirst("img.site-detail-cover")?.absUrl("src")
            ?: document.selectFirst("div.basis-2\\/5 img")?.absUrl("src")
                ?.run { toHttpUrl().queryParameter("url") ?: this }
            ?: return@apply

        description = document.selectFirst("div.site-book-synopsis p")?.text()
            ?: document.selectFirst("p:contains(簡介:)")?.text()?.removePrefix("簡介:")
            ?: ""

        val genres = ArrayList<String>()
        for (dt in document.select("dl.site-book-data dt")) {
            val value = dt.nextElementSibling()?.text()?.substringBefore(" · ")?.trim().orEmpty()
            if (value.isEmpty()) continue
            when (dt.text()) {
                "別名" -> if (value != title) description = "別名: $value\n\n$description"
                "作者" -> author = value
                "狀態" -> status = when {
                    value.contains("連載") -> SManga.ONGOING
                    value.contains("完結") -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
                "地區" -> genres.add(value)
                "標籤" -> genres.addAll(value.split(","))
            }
        }
        document.selectFirst("meta[name=keywords]")?.attr("content")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.let { genres.addAll(it) }
        genre = genres.distinct().joinToString()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select("a.site-chapter-link, a[href~=/books/.*/\\d+]").map {
            SChapter.create().apply {
                url = it.attr("href")
                name = it.selectFirst("span")?.text() ?: it.text()
            }
        }
        if (chapters.isEmpty()) return chapters

        val updateDate = document.select("dl.site-book-data dt")
            .firstOrNull { it.text() == "更新" }
            ?.nextElementSibling()
            ?.text()
            ?.let { DATE_FORMAT.tryParse(it) }
            ?.takeIf { it != 0L }

        val reversed = chapters.asReversed()
        if (updateDate != null) {
            reversed.first().date_upload = updateDate
        }
        return reversed
    }

    override fun pageListRequest(chapter: SChapter): Request {
        // Rendered HTML might have links sitting on the boundary of two scripts
        return super.pageListRequest(chapter).newBuilder().addHeader("rsc", "1").build()
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        return IMAGE_URL_REGEX.findAll(html).mapIndexedTo(ArrayList()) { index, match ->
            Page(index, imageUrl = match.groupValues[1])
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("提示：搜尋時篩選無效"),
        StatusFilter(),
    )

    private abstract class UriPartFilter(name: String, values: Array<String>) : Filter.Select<String>(name, values) {
        abstract fun toUriPart(): String
    }

    private class StatusFilter : UriPartFilter("狀態", arrayOf("全部", "連載中", "已完結")) {
        override fun toUriPart() = when (state) {
            1 -> "&continued=true"
            2 -> "&continued=false"
            else -> ""
        }
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("M/d/yyyy", Locale.ROOT)
        private val IMAGE_URL_REGEX = Regex(""""imageUrl":"([^"]+)""")
        private val CHAPTER_URL_REGEX = Regex("^/books/[^/]+/\\d+$")
    }
}
