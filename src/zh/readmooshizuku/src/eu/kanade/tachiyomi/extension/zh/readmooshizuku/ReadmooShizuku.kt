package eu.kanade.tachiyomi.extension.zh.readmooshizuku

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Source
abstract class ReadmooShizuku : KeiSource() {

    override val supportsLatest = false

    fun getAuthHeaders(): Headers {
        val accessToken = client.cookieJar.loadForRequest(getHomeUrl().toHttpUrl())
            .filter { it.name.endsWith("accessToken") }.getOrNull(0)?.value ?: throw Exception("Not logged in")
        return headers.newBuilder().set("Authorization", "Bearer $accessToken").build()
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val libraryItems = client.get("$baseUrl/store/v3/me/library_items?page[count]=1000", getAuthHeaders())
            .parseAs<LibraryItemsResponseDto>().included.filter { it.type == "books" }.associate {
                it.id to it.attributes
            }.toMutableMap()
        val response = client.get("$baseUrl/store/v3/me/installments", getAuthHeaders()).parseAs<InstallmentsResponseDto>()
        val mangas = response.data.map {
            SManga.create().apply {
                url = it.id.toString()
                title = it.attributes.title
                val data = it.relationships.books.data.last { libraryItems.containsKey(it.id) }.id
                thumbnail_url = libraryItems[data]!!.cover!!.large.href
                it.relationships.books.data.forEach { book ->
                    libraryItems.remove(book.id)
                }
            }
        }.toMutableList()

        // Some mangas are not in installments
        mangas.addAll(
            libraryItems.map {
                SManga.create().apply {
                    url = "book:${it.key}"
                    title = it.value.title!!
                    thumbnail_url = it.value.cover!!.large.href
                }
            },
        )
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val popularManga = getPopularManga(page)
        return MangasPage(popularManga.mangas.filter { it.title.contains(query) }, popularManga.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.toString().startsWith("https://readmoo.com/installment/") && !url.toString().startsWith("https://next.readmoo.com/read#/library/installments/")) return null
        val id = url.pathSegments.last()
        val manga = SManga.create().apply { this.url = id }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val libraryItems = client.get("$baseUrl/store/v3/me/library_items?page[count]=1000", getAuthHeaders())
            .parseAs<LibraryItemsResponseDto>().included.filter { it.type == "books" }.associate {
                it.id to it.attributes
            }
        if (manga.url.startsWith("book:")) {
            val item = libraryItems[manga.url.drop(5)]!!
            val chapter = SChapter.create().apply {
                url = manga.url.drop(5)
                name = "Chapter"
                date_upload = Instant.tryParse(item.publicationDate)
            }
            manga.apply {
                thumbnail_url = item.cover!!.large.href
                author = item.author
                artist = author
                description = item.shortDescription
            }
            return SMangaUpdate(manga, listOf(chapter))
        }
        val response = client.get("$baseUrl/store/v3/me/installments/${manga.url}", getAuthHeaders()).parseAs<MangaResponseDto>()
        val data = response.data.relationships.books.data.filter { libraryItems.containsKey(it.id) }
        val chapters = data.map {
            SChapter.create().apply {
                url = it.id
                name = libraryItems[it.id]!!.title!!
                date_upload = Instant.tryParse(libraryItems[it.id]!!.publicationDate)
            }
        }
        manga.apply {
            val firstChapter = libraryItems[data.last().id]!!
            thumbnail_url = firstChapter.cover!!.large.href
            author = firstChapter.author
            artist = author
            description = firstChapter.shortDescription
        }
        return SMangaUpdate(manga, chapters)
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = throw UnsupportedOperationException()

    override fun getHomeUrl(): String = "https://next.readmoo.com/read"

    override fun getMangaUrl(manga: SManga): String = if (manga.url.startsWith("book:")) {
        "https://next.readmoo.com/reader/${manga.url.drop(5)}"
    } else {
        "https://next.readmoo.com/read/#/library/installments/${manga.url}"
    }

    override fun getChapterUrl(chapter: SChapter): String = "https://next.readmoo.com/reader/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val base = client.get("https://reader.readmoo.com/api/book/${chapter.url}/nav", getAuthHeaders())
            .parseAs<NavResponseDto>().base
        val container = client.get("https://next.readmoo.com${base}META-INF/container.xml").asJsoup()
        val opfPath = container.selectFirst("rootfile")!!.attr("full-path")
        val opf = client.get("https://next.readmoo.com${base}$opfPath").asJsoup()
        val items = opf.select("manifest item").associateBy { it.attr("id") }
        return opf.select("itemref").mapIndexed { index, it ->
            Page(index, items[it.attr("idref")]!!.absUrl("href"))
        }
    }

    override suspend fun getImageUrl(page: Page): String {
        val document = client.get(page.url).asJsoup()
        return document.selectFirst("image")!!.absUrl("xlink:href")
    }
}
