package eu.kanade.tachiyomi.extension.zh.acgnshizuku

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
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class AcgnShizuku : KeiSource() {
    override val supportsLatest: Boolean = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select(".tabs-panel li").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst("div a")!!.absUrl("href"))
                title = it.selectFirst("div a")!!.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val response = client.get("https://cse.google.com/cse.js?cx=008124722090293425135:k7bx4juliog")
        val token = response.body.string().substringAfter("\"cse_token\": \"").substringBefore("\"")
        val url = "https://cse.google.com/cse/element/v1".toHttpUrl().newBuilder()
            .addQueryParameter("start", (page * 10 - 10).toString())
            .addQueryParameter("cselibv", "")
            .addQueryParameter("cx", "008124722090293425135:k7bx4juliog")
            .addQueryParameter("q", query)
            .addQueryParameter("cse_tok", token)
            .addQueryParameter("callback", "google.search.cse.api2028")
            .addQueryParameter("rurl", baseUrl)
            .build()
        val json = client.get(url).body.string()
            .substringAfter("google.search.cse.api2028(").substringBefore(");")
            .parseAs<SearchResults>()
        val mangas = json.results.map {
            SManga.create().apply {
                setUrlWithoutDomain(it.url)
                title = it.richSnippet.metatags.title ?: it.title
                thumbnail_url = it.richSnippet.cseImage.src
            }
        }.filter { it.url.startsWith("/manhua-") }
        return MangasPage(mangas, true)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.toString().startsWith("$baseUrl/manhua-")) return null
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
            title = document.selectFirst("#breadcrumb h1")!!.text()
            thumbnail_url = document.selectFirst("dl.gameshows img")?.absUrl("src")
            author = document.selectFirst(".load01_r .mss:containsOwn(漫畫作者：) a")?.text()
            artist = author
            genre = document.select(".load01_r .mss:containsOwn(漫畫分類：) span").text()
            status = when (document.selectFirst(".load01_r .mss:containsOwn(漫畫狀態：) span")?.text()) {
                "連載中" -> SManga.ONGOING
                "已完結" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            description = document.select(".gameshows dd").getOrNull(1)?.ownText()?.trim()?.removePrefix("漫畫 ，")
        }
        val chapters = document.select("#comic_chapter li a").map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                name = it.text()
            }
        }
        return SMangaUpdate(manga, chapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()
        return document.select("#pic_list div.pic").mapIndexed { index, it ->
            Page(index, imageUrl = it.attr("_src"))
        }
    }
}
