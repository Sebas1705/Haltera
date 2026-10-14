package es.sebas1705.ironlog.data.network.api

import es.sebas1705.ironlog.data.models.ApiResponse
import es.sebas1705.ironlog.data.models.User
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class UserApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    suspend fun getMe(): ApiResponse<User> =
        client.get("$baseUrl/users/me").body()

    suspend fun getById(id: String): ApiResponse<User> =
        client.get("$baseUrl/users/$id").body()

    suspend fun updateProfile(user: User): ApiResponse<User> =
        client.put("$baseUrl/users/${user.id}") {
            contentType(ContentType.Application.Json)
            setBody(user)
        }.body()
}
