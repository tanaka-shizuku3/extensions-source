package eu.kanade.tachiyomi.extension.zh.tibiushizuku

import eu.kanade.tachiyomi.multisrc.mccms.MCCMS
import eu.kanade.tachiyomi.multisrc.mccms.PageDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import okhttp3.Request
import okhttp3.Response

@Source
abstract class TibiuShizuku : MCCMS() {
    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/api/data/pic?cid=${chapter.url.substringAfterLast('/')}", headers)

    override fun pageListParse(response: Response): List<Page> = response.parseAs<List<PageDto>>().asReversed().mapIndexed { i, img ->
        Page(i, imageUrl = img.img)
    }
}
