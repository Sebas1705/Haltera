package es.sebas1705.ironlog.core.network

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

actual fun httpEngineFactory(): HttpClientEngineFactory<*> = OkHttp
