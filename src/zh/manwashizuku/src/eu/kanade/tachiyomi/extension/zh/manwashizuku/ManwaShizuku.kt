package eu.kanade.tachiyomi.extension.zh.manwashizuku

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
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class ManwaShizuku :
    KeiSource(),
    ConfigurableSource {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)
    private val json = Json { encodeDefaults = true }

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(rewriteOctetStream)

    private val rewriteOctetStream: Interceptor = Interceptor { chain ->
        val response: Response = chain.proceed(chain.request())
        val imageHost = preferences.getString(IMAGE_HOST_KEY, IMAGE_HOST_ENTRIES[0])!!.substringBefore(":")
        if (response.request.url.host != imageHost || !response.request.url.toString().endsWith(".jpg")) {
            return@Interceptor response
        }
        // Decrypt images in mangas
        val source = response.body.source()
        val iv = source.readByteArray(16)
        val key = SecretKeySpec("0B6666A0-BB59-1381-B746-a0E4C9AC".toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        val result = source.cipherSource(cipher).buffer()
        val newBody = result.asResponseBody(response.body.contentType())
        response.newBuilder()
            .body(newBody)
            .build()
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val body = MangaPayload(page = PagePayload(page), sort = 3)
            .toJsonRequestBody(json)
        val response = client.post("$baseUrl/api/cate/", headers, body).parseAs<MangaResponseDto>()
        val mangas = response.data.list.map { it.toSManga() }
        val pages = (response.data.total + 35) / 36
        return MangasPage(mangas, page < pages)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val body = MangaPayload(page = PagePayload(page), sort = 0)
            .toJsonRequestBody(json)
        val response = client.post("$baseUrl/api/cate/", headers, body).parseAs<MangaResponseDto>()
        val mangas = response.data.list.map { it.toSManga() }
        val pages = (response.data.total + 35) / 36
        return MangasPage(mangas, page < pages)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("type", "mh")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("pageSize", "20")
            .addQueryParameter("keyword", query)
            .build()
        val response = client.get(url, headers).parseAs<MangaResponseDto>()
        val mangas = response.data.list.map { it.toSManga() }
        val pages = (response.data.total + 19) / 20
        return MangasPage(mangas, page < pages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments[0] != "comic") return null
        val id = url.pathSegments[1]
        val manga = SManga.create().apply { this.url = "/comic/$id" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}", headers).asJsoup()
        manga.apply {
            title = document.selectFirst("#page-title")!!.text()
            thumbnail_url = document.selectFirst("img.comic-cover")?.absUrl("src")
            author = document.selectFirst("#author-container")?.text()
            artist = author
            genre = document.select("#tagsContainer span.tag").eachText().joinToString()
            status = when (document.selectFirst("#status")?.text()) {
                "连载中" -> SManga.ONGOING
                "已完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            description = document.selectFirst("p.comic-desc")?.text()
        }
        val chapters = document.select("#chapter-grid-container > a").map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                name = it.selectFirst("div.chapter-name")!!.text()
                date_upload = runCatching {
                    LocalDate.parse(it.select("div.chapter-meta span").last()?.text(), dateFormat)
                        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                }.getOrDefault(0L)
            }
        }.asReversed()
        return SMangaUpdate(manga, chapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val imageHost = preferences.getString(IMAGE_HOST_KEY, IMAGE_HOST_ENTRIES[0])
        val url = "$baseUrl/api/comic/image/${chapter.url.substringAfterLast("/")}".toHttpUrl().newBuilder()
            .addQueryParameter("page", "1")
            .addQueryParameter("page_size", "9999")
            .addQueryParameter("image_source", "https://$imageHost")
            .build()
        val response = client.get(url, headers).parseAs<PageListResponseDto>()
        return response.data.images.mapIndexed { index, it ->
            Page(index, imageUrl = it.url)
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
                "tu.mwzu.cc",
                "mwtuwu.cc",
                "124.221.66.202:36662",
            )
        private const val IMAGE_HOST_KEY = "IMG_HOST"
    }
}
