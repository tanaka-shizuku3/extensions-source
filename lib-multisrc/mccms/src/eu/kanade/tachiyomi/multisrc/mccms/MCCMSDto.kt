package eu.kanade.tachiyomi.multisrc.mccms

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import org.jsoup.nodes.Entities
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
data class MangaDto(
    private val id: String,
    private val name: String,
    private val pic: String,
    private val serialize: String,
    private val author: String,
    private val content: String,
    private val addtime: String,
    private val url: String,
    private val tags: List<String>,
) {
    val cleanUrl get() = url.removePathPrefix()

    fun toSManga() = SManga.create().apply {
        url = cleanUrl
        title = Entities.unescape(name)
        author = Entities.unescape(this@MangaDto.author)
        description = Entities.unescape(content)
        genre = Entities.unescape(tags.joinToString())
        status = when (serialize) {
            "连载", "連載中", "En cours", "OnGoing" -> SManga.ONGOING
            "完结", "已完結", "Terminé", "Complete", "Complété" -> SManga.COMPLETED
            else -> if (isUpdating(addtime)) SManga.ONGOING else SManga.UNKNOWN
        }
        thumbnail_url = "$pic#$id"
        initialized = true
    }

    companion object {
        private val dateFormat by lazy { getDateFormat() }

        private fun isUpdating(dateStr: String): Boolean {
            val date = dateFormat.parse(dateStr) ?: return false
            return System.currentTimeMillis() - date.time <= 30L * 24 * 3600 * 1000 // a month
        }
    }
}

@Serializable
class ChapterDto(val id: String, private val name: String, private val link: String) {
    fun toSChapter(date: Long) = SChapter.create().apply {
        url = link.removePathPrefix()
        name = Entities.unescape(this@ChapterDto.name)
        date_upload = date
    }
}

@Serializable
class ChapterDataDto(val id: String, private val addtime: String) {
    val date get() = dateFormat.parse(addtime, ParsePosition(0))?.time ?: dateFormat2.tryParse(addtime)

    companion object {
        private val dateFormat by lazy { getDateFormat() }
        private val dateFormat2 by lazy { getDateFormat2() }
    }
}

@Serializable
class ResultDto<T>(val data: T)

@Serializable
class PageDto(val img: String)

fun getDateFormat() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
fun getDateFormat2() = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
