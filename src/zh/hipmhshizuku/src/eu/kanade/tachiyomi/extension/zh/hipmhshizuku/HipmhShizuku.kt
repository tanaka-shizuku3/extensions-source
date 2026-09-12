package eu.kanade.tachiyomi.extension.zh.hipmhshizuku

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.getValue

@Source
abstract class HipmhShizuku :
    KeiSource(),
    ConfigurableSource {

    val readerUrl = "https://reader.hipmh.top"
    val apiUrl = "https://hipapi1.s3file.top"
    private val preferences by getPreferencesLazy()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/popularity?page=$page").asJsoup()
        val mangas = document.select(".manga-section-grid .manga-section-item").map {
            SManga.create().apply {
                url = it.selectFirst("a")!!.attr("href")
                    .substringAfterLast("/")
                title = it.selectFirst("h3.manga-card-title")!!.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("src")
            }
        }
        val nextPage = document.selectFirst("a.pagination-next")
        return MangasPage(mangas, nextPage?.attr("aria-disabled") == "false")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client
            .get("$apiUrl/v1/mangas?status=ongoing&sort=updated&page=$page&per_page=18")
            .parseAs<MangasResponseDto>()
        return MangasPage(response.data.items.map { it.toSManga() }, page < response.data.pages)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", "20")
            .build()
        val response = client.get(url).parseAs<MangasResponseDto>()
        return MangasPage(response.data.items.map { it.toSManga() }, page < response.data.pages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.toString().startsWith("$baseUrl/works/")) return null
        val manga = SManga.create().apply { this.url = url.pathSegments.last() }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (fetchDetails) {
            val document = client.get("$baseUrl/works/${manga.url}").asJsoup()
            manga.apply {
                title = document.selectFirst("aside h1")!!.text()
                thumbnail_url = document.selectFirst("aside img")?.absUrl("src")
                author = document.selectFirst("aside > div.text-center > div")?.text()
                artist = author
                genre = document
                    .select("aside > div.text-center > div:nth-of-type(2) div div a")
                    .eachText().joinToString()
                status = when (document.selectFirst("aside > div.text-center > div:nth-of-type(3) a")?.text()) {
                    "連載中" -> SManga.ONGOING
                    "完結" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
                description = document.selectFirst("#d-info-content p")?.text()
            }
        }

        if (fetchChapters) {
            val chapterList = arrayListOf<SChapter>()
            val mangaId = manga.url.substringBefore("-")
            var page = 1
            while (true) {
                val chapterResponse = client
                    .get("$apiUrl/v1/manga/chapters?mid=$mangaId&page=$page&per_page=20&order=desc")
                    .parseAs<ChaptersResponseDto>()
                chapterList.addAll(chapterResponse.data.items.map { it.toSChapter() })
                if (page >= chapterResponse.data.pages) break
                page++
            }
            return SMangaUpdate(manga, chapterList)
        } else {
            return SMangaUpdate(manga, chapters)
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/works/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$readerUrl/chapter/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$readerUrl/chapter/${chapter.url}").asJsoup()
        val apiHid = document.selectFirst("#chapcontent")!!.attr("data-api-hid")
        val imageHost = preferences.getString(IMAGE_HOST_KEY, IMAGE_HOST_ENTRIES[0])
        return client.get("$apiUrl/v2/chapter?hid=$apiHid")
            .parseAs<PagesResponseDto>().data.toList().mapIndexed { index, it ->
                Page(index, imageUrl = "https://$imageHost$it")
            }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = IMAGE_HOST_KEY
            title = "图源"
            entries = IMAGE_HOST_ENTRIES
            entryValues = IMAGE_HOST_ENTRIES
            setDefaultValue(IMAGE_HOST_ENTRIES[0])
        }.let { screen.addPreference(it) }
    }

    companion object {
        private val IMAGE_HOST_ENTRIES
            get() = arrayOf(
                "hip-tx-1.s3imgs.top",
                "hip-cf-1.s3imgs.top",
            )
        private const val IMAGE_HOST_KEY = "IMG_HOST"
    }
}
