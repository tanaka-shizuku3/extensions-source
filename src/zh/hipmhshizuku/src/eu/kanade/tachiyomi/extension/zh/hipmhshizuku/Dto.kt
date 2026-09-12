package eu.kanade.tachiyomi.extension.zh.hipmhshizuku

import android.util.Base64
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import kotlin.time.Instant

@Serializable
data class MangasResponseDto(val data: MangasDataDto)

@Serializable
data class MangasDataDto(
    @JsonNames("items", "data") val items: List<MangaDto>,
    @SerialName("total_pages") val pages: Int,
)

@Serializable
data class MangaDto(
    @JsonNames("id", "mid") private val mid: String,
    private val title: String,
    @SerialName("vertical_image_url") private val image: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = mid
        title = this@MangaDto.title
        thumbnail_url = "https://cover.s3imgs.top$image"
    }
}

@Serializable
data class ChaptersResponseDto(val data: ChaptersDataDto)

@Serializable
data class ChaptersDataDto(
    val items: List<ChapterDto>,
    @SerialName("total_pages") val pages: Int,
)

@Serializable
data class ChapterDto(
    private val title: String,
    private val hid: String,
    @SerialName("updated_at") private val time: String,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        name = title
        url = hid
        date_upload = Instant.parseOrNull(time)?.toEpochMilliseconds() ?: 0L
    }
}

@Serializable
data class PagesResponseDto(val data: PagesDataDto)

@Serializable
data class PagesDataDto(private val images: String) {
    fun toList(): List<String> {
        val sliced = images.drop(3).dropLast(2)
        val length = sliced.length - 5
        val length2 = length / 3
        val length3 = (length - length2) / 2
        val length4 = length - length2 - length3
        val sliced2 = sliced.substring(0, length3)
        val sliced4 = sliced.substring(length3 + 2, length3 + 2 + length4)
        val sliced6 = sliced.substring(length3 + 2 + length4 + 3)
        return decodeToList(remap(reverseBlock(sliced6 + sliced2 + sliced4)))
    }

    fun reverseBlock(input: String): String {
        val output = StringBuilder()
        for (i in 0..input.length / 7) {
            val index = i * 7
            if (i % 2 == 0) {
                output.append(input.drop(index).take(7))
            } else {
                output.append(input.drop(index).take(7).reversed())
            }
        }
        return output.toString()
    }

    fun remap(input: String): String = input.map { charMap[it] }.joinToString("")

    fun decodeToList(input: String): List<String> {
        val decoded = Base64.decode(input, Base64.URL_SAFE)
        return String(decoded).parseAs()
    }

    companion object {
        private const val SOURCE = "_-9876543210abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val TARGET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        private val charMap = SOURCE.zip(TARGET).toMap()
    }
}
