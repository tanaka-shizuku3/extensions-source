package eu.kanade.tachiyomi.extension.zh.pubushizuku

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.lang.UnsupportedOperationException

@Source
abstract class PubuShizuku : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/bookshelf").asJsoup()
        val mangas = document.select(".cover-list article").map {
            SManga.create().apply {
                val href = it.selectFirst("a[href^=/epubReader/]")!!.absUrl("href").toHttpUrl()
                val bookId = href.pathSegments.last()
                val productId = href.queryParameter("productId")
                url = "$bookId,$productId"
                title = it.selectFirst(".info-name h3 a")!!.text()
                thumbnail_url = it.selectFirst("img")?.attr("src")
            }
        }
        // FIXME: detect hasNextPage when I have more than one page
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val popularManga = getPopularManga(page)
        return MangasPage(popularManga.mangas.filter { it.title.contains(query) }, popularManga.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.toString().startsWith("$baseUrl/reader-service/epub/") && !url.toString().startsWith("$baseUrl/epubReader/")) return null
        val bookId = url.pathSegments.last()
        val productId = url.queryParameter("productId")
        val manga = SManga.create().apply { this.url = "$bookId,$productId" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Chapter"
        }
        return SMangaUpdate(manga, listOf(chapter))
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/reader-service/epub/${manga.url.substringBefore(",")}?productId=${manga.url.substringAfter(",")}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader-service/epub/${chapter.url.substringBefore(",")}?productId=${chapter.url.substringAfter(",")}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val bookId = chapter.url.substringBefore(",")
        val productId = chapter.url.substringAfter(",")
        val body = FormBody.Builder()
            .addEncoded("productId", productId)
            .addEncoded("isBookbuffet", "false")
            .addEncoded("isLibrary", "false")
            .addEncoded("isPreview", "false")
            .build()
        val response = client.post("$baseUrl/epubReader/$bookId/opfData", body).parseAs<PageListDto>()
        val indexMapJson = response.indexMapJson
        val directionMapJson = response.directionMapJson
        val signDM = response.signDM
        val signIM = response.signIM
        val pages = indexMapJson.substringAfter("\"").substringBefore("\"").toInt()
        return (pages downTo 0).mapIndexed { index, it ->
            Page(index, "$it,bookId$bookId,productId$productId,indexMapJson$indexMapJson,directionMapJson$directionMapJson,signDM$signDM,signIM$signIM")
        }
    }

    override suspend fun getImageUrl(page: Page): String {
        val index = page.url.substringBefore(",bookId")
        val bookId = page.url.substringAfter("bookId").substringBefore(",productId")
        val productId = page.url.substringAfter("productId").substringBefore(",indexMapJson")
        val indexMapJson = page.url.substringAfter("indexMapJson").substringBefore(",directionMapJson")
        val directionMapJson = page.url.substringAfter("directionMapJson").substringBefore(",signDM")
        val signDM = page.url.substringAfter("signDM").substringBefore(",signIM")
        val signIM = page.url.substringAfter("signIM")
        val body = FormBody.Builder()
            .addEncoded("indexMappingJson", indexMapJson)
            .addEncoded("directionMapJson", directionMapJson)
            .addEncoded("index", index)
            .addEncoded("productId", productId)
            .addEncoded("isBookbuffet", "false")
            .addEncoded("isLibrary", "false")
            .addEncoded("isPreview", "false")
            .addEncoded("isLastPage", "false")
            .addEncoded("signDM", signDM)
            .addEncoded("signIM", signIM)
            .build()
        val response = client.post("$baseUrl/epubReader/$bookId/load", body).parseAs<ImageUrlDto>()
        return if (response.smallerChapterIndex.toString() == index) {
            response.toUrl().substringBefore(" ")
        } else {
            response.toUrl().substringAfter(" ")
        }
    }
}
