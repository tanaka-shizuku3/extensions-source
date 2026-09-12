package eu.kanade.tachiyomi.extension.zh.manhuamangashizuku

import eu.kanade.tachiyomi.multisrc.mccms.MCCMSWeb
import eu.kanade.tachiyomi.multisrc.mccms.parseGenres
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator

@Source
abstract class ManhuamangaShizuku : MCCMSWeb() {

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().set("referer", "$baseUrl/")

    override fun parseListing(document: Document): MangasPage {
        parseGenres(document, config.genreData)
        val mangas = document.select(".comic-item").map(::simpleMangaFromElement)
        val hasNextPage = run {
            // default pagination
            val buttons = document.selectFirst(".pagination")!!.select(Evaluator.Tag("a"))
            val count = buttons.size
            // Next page != Last page
            buttons[count - 1].attr("href") != buttons[count - 2].attr("href")
        }
        return MangasPage(mangas, hasNextPage)
    }

    override fun simpleMangaFromElement(element: Element) = SManga.create().apply {
        val titleElement = element.selectFirst("h3 a")!!
        url = titleElement.attr("href")
        title = titleElement.ownText()
        thumbnail_url = element.selectFirst(Evaluator.Tag("img"))?.attr("src")
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun mangaDetailsParse(response: Response): SManga = run {
        SManga.create().apply {
            val document = response.asJsoup()
            title = document.selectFirst(".comic-meta-info h1")!!.ownText().drop(1).dropLast(1)
            thumbnail_url = document.selectFirst(".comic-cover-large img")?.attr("src")
            author = document.selectFirst(".stat-item .fa-user")?.nextElementSibling()?.text()?.removePrefix("作者：")
            genre = document.selectFirst(".comic-tags")?.child(0)?.text()?.replace(" ", ", ")
            status = when (document.selectFirst(".comic-tags")?.child(1)?.text()) {
                "连载", "连载中" -> SManga.ONGOING
                "已完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            description = document.selectFirst(".comic-description p")?.text()
        }
    }

    override fun chapterListSelector() = "#chapter-list > .chapter-item"

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img.comic-image").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("src"))
        }
    }

    override fun imageRequest(page: Page): Request {
        val header = headers.newBuilder().add("Referer", "$baseUrl/").build()
        return GET(page.imageUrl!!, header)
    }
}
