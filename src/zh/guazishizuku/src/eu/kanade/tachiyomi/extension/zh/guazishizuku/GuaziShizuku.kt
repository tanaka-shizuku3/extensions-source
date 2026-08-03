package eu.kanade.tachiyomi.extension.zh.guazishizuku

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class GuaziShizuku : KeiSource() {

    // If Android is detected by site, some chapters don't load
    private val userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"
    override fun Headers.Builder.configureHeaders(): Headers.Builder = this.set("User-Agent", userAgent)

    fun parseManga(document: Document): MangasPage {
        val mangas = document.select("section.grid article.card").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst("h3 a")!!.absUrl("href"))
                title = it.selectFirst("h3 a")!!.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }
        val lastPage = document.select("nav.pager a").last()?.text()
        return MangasPage(mangas, lastPage == ">")
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/category.php?sort=hits&page=$page").asJsoup()
        return parseManga(document)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/category.php?sort=update&page=$page").asJsoup()
        return parseManga(document)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/category.php".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", query)
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url).asJsoup()
        return parseManga(document)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.toString().startsWith("$baseUrl/comic.php")) return null
        val manga = SManga.create().apply { setUrlWithoutDomain(url.toString()) }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        manga.apply {
            title = document.selectFirst("#cinema-title")!!.text()
            thumbnail_url = document.selectFirst("img.cinema-cover")?.absUrl("src")
            author = document.selectFirst("div.cinema-strip a")?.text()
            artist = author
            genre = document.select(".side-topic-grid a").eachText().joinToString()
            status = when (document.selectFirst("div.meta span")?.text()) {
                "连载" -> SManga.ONGOING
                "完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            description = document.selectFirst(".cinema-info p")?.text()
        }
        val chapters = document.select("#chapters .all-chapter-grid a").map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                name = it.text()
            }
        }
        return SMangaUpdate(manga, chapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()
        return document.select(".reader-images img").mapIndexed { index, it ->
            Page(index, imageUrl = it.absUrl("src"))
        }
    }
}
