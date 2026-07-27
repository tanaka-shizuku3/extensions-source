package eu.kanade.tachiyomi.extension.zh.acgnshizuku

import kotlinx.serialization.Serializable

@Serializable
class SearchResults(val results: List<SearchManga>)

@Serializable
class SearchManga(
    val richSnippet: RichSnippet,
    val url: String,
    private val titleNoFormatting: String,
) {
    val title = titleNoFormatting.substringAfter("《").substringBeforeLast("》")
}

@Serializable
class RichSnippet(val metatags: Metatags, val cseImage: CseImage)

@Serializable
class CseImage(val src: String)

@Serializable
class Metatags(
    private val ogTitle: String?,
) {
    val title = ogTitle?.substringAfter("《")?.substringBeforeLast("》")
}
