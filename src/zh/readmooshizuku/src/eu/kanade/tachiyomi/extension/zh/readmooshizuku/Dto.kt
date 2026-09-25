package eu.kanade.tachiyomi.extension.zh.readmooshizuku

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class InstallmentsResponseDto(val data: List<InstallmentsDataDto>)

@Serializable
class MangaResponseDto(val data: InstallmentsDataDto)

@Serializable
class InstallmentsDataDto(val id: Int, val attributes: InstallmentsAttributesDto, val relationships: InstallmentsRelationshipsDto)

@Serializable
class InstallmentsAttributesDto(val title: String)

@Serializable
class InstallmentsRelationshipsDto(val books: InstallmentsBooksDto)

@Serializable
class InstallmentsBooksDto(val data: List<InstallmentsBooksDataDto>)

@Serializable
class InstallmentsBooksDataDto(val id: String)

@Serializable
class LibraryItemsResponseDto(val included: List<LibraryItemsIncludedDto>)

@Serializable
class LibraryItemsIncludedDto(val type: String, val id: String, val attributes: LibraryItemsAttributesDto)

@Serializable
class LibraryItemsAttributesDto(
    val title: String?,
    val author: String?,
    val cover: LibraryItemsCoverDto?,
    @SerialName("short_description") val shortDescription: String?,
)

@Serializable
class LibraryItemsCoverDto(val large: LibraryItemsCoverItemDto)

@Serializable
class LibraryItemsCoverItemDto(val href: String)

@Serializable
class NavResponseDto(val base: String)
