package eu.kanade.tachiyomi.extension.all.webdav

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferences
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Webdav :
    KeiSource(),
    ConfigurableSource {
    override val supportsLatest: Boolean = false
    private val preferences: SharedPreferences = getPreferences()
    private val path = preferences.getString("PATH", "")!!
    private val username = preferences.getString("USERNAME", "")!!
    private val password = preferences.getString("PASSWORD", "")!!
    private val credentials = Credentials.basic(username, password)
    override fun Headers.Builder.configureHeaders(): Headers.Builder = this.add("Authorization", credentials)
    private val depth1Headers = headers.newBuilder().add("Depth", "1").build()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.propfind("$baseUrl$path", depth1Headers).asJsoup()
        val mangas = document.select("*|response:has(*|collection)")
            .mapNotNull(::popularMangaFromElement)
            .filter { it.url != path }
        return MangasPage(mangas, hasNextPage = false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("*|href")!!.text())
        if (!url.endsWith("/")) url += "/"
        title = url.dropLast(1).substringAfterLast("/")
        thumbnail_url = "$baseUrl${url}cover.jpg"
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = getPopularManga(page).mangas.filter {
            it.title.contains(query, ignoreCase = true)
        }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.propfind("$baseUrl${manga.url}", depth1Headers).asJsoup()
        return SMangaUpdate(
            manga = SManga.create(),
            chapters = document.select("*|response:has(*|collection)").map {
                SChapter.create().apply {
                    setUrlWithoutDomain(it.selectFirst("*|href")!!.text())
                    if (!url.endsWith("/")) url += "/"
                    name = url.dropLast(1).substringAfterLast("/")
                    val creationDate = it.selectFirst("*|creationdate")!!.text()
                    date_upload = dateFormat.parse(creationDate)!!.time
                }
            }.filter { it.url != manga.url }.sortedByDescending { it.name },
        )
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.propfind("$baseUrl${chapter.url}", depth1Headers).asJsoup()
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

    override fun getHomeUrl(): String = "$baseUrl$path"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            EditTextPreference(screen.context).apply {
                key = "PATH"
                title = "path"
                summary = "Folder of your manga collection, starts and ends with slash /. e.g. /Tachiyomi/"
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

    private suspend fun OkHttpClient.propfind(
        url: String,
        headers: Headers,
        body: RequestBody = FormBody.Builder().build(),
        ensureSuccess: Boolean = true,
    ): Response {
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", body)
            .headers(headers)
            .build()
        val call = newCall(request)

        return if (ensureSuccess) {
            call.awaitSuccess()
        } else {
            call.await()
        }
    }
}
