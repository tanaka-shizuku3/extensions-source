package eu.kanade.tachiyomi.extension.all.webdav

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.getPreferences
import okhttp3.CacheControl
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Webdav : ParsedHttpSource(), ConfigurableSource {
    override val name: String = "WebDAV"
    override val lang: String = "all"
    override val supportsLatest: Boolean = false
    private val preferences: SharedPreferences = getPreferences()
    override val baseUrl = preferences.getString("BASEURL", "")!!
    private val path = preferences.getString("PATH", "")!!
    private val username = preferences.getString("USERNAME", "")!!
    private val password = preferences.getString("PASSWORD", "")!!
    private val credentials = Credentials.basic(username, password)
    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder().add("Authorization", credentials)
    }
    private val depth1Headers = headers.newBuilder().add("Depth", "1").build()

    private fun PROPFIND(
        url: String,
        headers: Headers = Headers.Builder().build(),
        body: RequestBody = FormBody.Builder().build(),
        cache: CacheControl = CacheControl.Builder().maxAge(10, TimeUnit.MINUTES).build(),
    ): Request {
        return Request.Builder()
            .url(url)
            .method("PROPFIND", body)
            .headers(headers)
            .cacheControl(cache)
            .build()
    }

    // Popular

    override fun popularMangaRequest(page: Int) = PROPFIND("$baseUrl$path", depth1Headers)
    override fun popularMangaNextPageSelector() = null
    override fun popularMangaSelector(): String = "*|response:has(*|collection)"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("*|href")!!.text())
        if (!url.endsWith("/")) url += "/"
        title = url.dropLast(1).substringAfterLast("/")
        thumbnail_url = "$baseUrl${url}cover.jpg"
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val basePath = URLDecoder.decode(response.request.url.encodedPath, "UTF-8")
        val mangas = super.popularMangaParse(response).mangas.filter {
            it.url != basePath
        }
        return MangasPage(mangas, false)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = PROPFIND("$baseUrl$path#$query", depth1Headers)
    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()
    override fun searchMangaSelector(): String = throw UnsupportedOperationException()
    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.fragment!!
        val mangas = popularMangaParse(response).mangas.filter {
            it.title.contains(query, true)
        }
        return MangasPage(mangas, false)
    }

    // Details

    override fun mangaDetailsRequest(manga: SManga) = PROPFIND("$baseUrl${manga.url}", depth1Headers)
    override fun mangaDetailsParse(document: Document) = SManga.create()

    // Chapters

    override fun chapterListRequest(manga: SManga) = PROPFIND("$baseUrl${manga.url}", depth1Headers)
    override fun chapterListSelector(): String = "*|response:has(*|collection)"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("*|href")!!.text())
        if (!url.endsWith("/")) url += "/"
        name = url.dropLast(1).substringAfterLast("/")
        val creationDate = element.selectFirst("*|creationdate")!!.text()
        date_upload = dateFormat.parse(creationDate)!!.time
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaPath = URLDecoder.decode(response.request.url.encodedPath, "UTF-8")
        return super.chapterListParse(response).filter {
            it.url != mangaPath
        }.sortedByDescending { it.name }
    }

    // Pages

    override fun pageListRequest(chapter: SChapter) = PROPFIND("$baseUrl${chapter.url}", depth1Headers)

    override fun pageListParse(document: Document): List<Page> {
        return document.select("*|response:not(:has(*|collection))").mapIndexed { index, img ->
            val href = img.selectFirst("*|href")!!.text()
            if (href.startsWith(baseUrl)) {
                Page(index, imageUrl = href)
            } else {
                Page(index, imageUrl = baseUrl + href)
            }
        }.sortedWith(
            Comparator { p1, p2 ->
                val p1IsCover = p1.imageUrl!!.contains("cover", true)
                val p2IsCover = p2.imageUrl!!.contains("cover", true)
                when {
                    p1IsCover && !p2IsCover -> -1
                    p2IsCover && !p1IsCover -> 1
                    else -> p1.imageUrl!!.compareTo(p2.imageUrl!!)
                }
            },
        )
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            EditTextPreference(screen.context).apply {
                key = "BASEURL"
                title = "baseUrl"
                summary = "Scheme + host of server. e.g. https://example.com"
            }.let(screen::addPreference)

            EditTextPreference(screen.context).apply {
                key = "PATH"
                title = "path"
                summary = "Folder of your manga collection, starts with slash /. e.g. /Tachiyomi/"
            }.let(screen::addPreference)

            EditTextPreference(screen.context).apply {
                key = "USERNAME"
                title = "username"
            }.let(screen::addPreference)

            EditTextPreference(screen.context).apply {
                key = "PASSWORD"
                title = "password"
            }.let(screen::addPreference)
        }
    }

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'", Locale.ENGLISH)
    }
}
