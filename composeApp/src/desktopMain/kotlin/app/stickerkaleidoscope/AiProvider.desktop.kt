package app.stickerkaleidoscope

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

actual object AiProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO) { install(ContentNegotiation) { json(json) }; expectSuccess = false }
    private fun normalizedKey(value: String): String = value.trim().removeSurrounding("\"").let {
        if (it.startsWith("Bearer ", ignoreCase = true)) it.substringAfter(' ').trim() else it
    }
    private suspend fun request(config: AiProviderConfig): String {
        val response = client.get(config.url.trimEnd('/') + "/models") {
            header(HttpHeaders.Accept, "application/json")
            if (config.apiKey.isNotBlank()) header(HttpHeaders.Authorization, "Bearer ${normalizedKey(config.apiKey)}")
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) error("HTTP ${response.status.value}: ${body.take(240)}")
        return body
    }
    actual suspend fun test(config: AiProviderConfig): Result<String> = runCatching { request(config); "HTTP 200" }
    actual suspend fun models(config: AiProviderConfig): Result<List<String>> = runCatching {
        json.parseToJsonElement(request(config)).jsonObject["data"]?.jsonArray?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }?.distinct() ?: emptyList()
    }
}
