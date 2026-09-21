package eu.kanade.tachiyomi.extension.zh.pubushizuku

import kotlinx.serialization.Serializable
import org.jsoup.Jsoup

@Serializable
class PageListDto(
    val indexMapJson: String,
    val directionMapJson: String,
    val signDM: String,
    val signIM: String,
)

@Serializable
class ImageUrlDto(private val content: String, val smallerChapterIndex: Int) {
    fun toUrl(): String {
        val image = Jsoup.parse(content).select("image")
        return "${image[0].attr("xlink:href")} ${image.last()!!.attr("xlink:href")}"
    }
}
