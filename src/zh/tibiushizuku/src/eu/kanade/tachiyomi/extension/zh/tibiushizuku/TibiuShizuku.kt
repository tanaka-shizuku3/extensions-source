package eu.kanade.tachiyomi.extension.zh.tibiushizuku

import eu.kanade.tachiyomi.multisrc.mccms.MCCMS
import eu.kanade.tachiyomi.multisrc.mccms.PageDto
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.get

@Source
abstract class TibiuShizuku : MCCMS() {
    override suspend fun getPageList(chapter: SChapter): List<Page> = client
        .get("$baseUrl/api/data/pic?cid=${chapter.url.substringAfterLast('/')}")
        .parseAs<List<PageDto>>()
        .asReversed()
        .mapIndexed { i, img ->
            Page(i, imageUrl = img.img)
        }
}
