package es.sebas1705.ironlog.core.network

import es.sebas1705.ironlog.core.logging.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun buildHttpClient(
    tokenProvider: (() -> String?)? = null,
    json: Json = Json {
        ignoreUnknownKeys = true
        isLenient          = true
        prettyPrint        = false
        encodeDefaults     = true
    },
): HttpClient = HttpClient(httpEngineFactory()) {
    install(ContentNegotiation) { json(json) }
    install(Logging) {
        level  = LogLevel.BODY
        logger = object : Logger {
            override fun log(message: String) = AppLogger.d("Ktor", message)
        }
    }
    if (tokenProvider != null) {
        install(Auth) {
            bearer {
                loadTokens { tokenProvider()?.let { BearerTokens(it, it) } }
            }
        }
    }
}

expect fun httpEngineFactory(): io.ktor.client.engine.HttpClientEngineFactory<*>
