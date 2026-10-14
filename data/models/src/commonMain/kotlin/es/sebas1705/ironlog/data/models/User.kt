package es.sebas1705.ironlog.data.models

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: Instant? = null,
)

@Serializable
data class UserProfile(
    val user: User,
    val bio: String? = null,
    val location: String? = null,
    val website: String? = null,
)
