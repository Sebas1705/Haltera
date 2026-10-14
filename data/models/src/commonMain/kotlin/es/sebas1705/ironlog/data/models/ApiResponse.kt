package es.sebas1705.ironlog.data.models

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val data: T? = null,
    val error: String? = null,
    val success: Boolean = true,
    val page: Int? = null,
    val totalPages: Int? = null,
)

@Serializable
data class PaginatedList<T>(
    val items: List<T>,
    val page: Int,
    val totalPages: Int,
    val totalItems: Int,
)
