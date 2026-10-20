package app.stickerkaleidoscope

import app.stickerkaleidoscope.library.AiAnalyzer
import app.stickerkaleidoscope.library.OriginalFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.util.Base64

class DesktopAiAnalyzer : AiAnalyzer {
    private val client = HttpClient(CIO) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    override suspend fun analyze(file: OriginalFile): List<String> {
        val config = AiProviderConfig(AppPreferences.providerUrl, AppPreferences.providerModel, AppPreferences.providerApiKey)
        require(config.model.isNotBlank()) { "Select a model first" }
        val mime = when (file.format.lowercase()) { "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "webp" -> "image/webp"; else -> "image/png" }
        val data = Base64.getEncoder().encodeToString(Files.readAllBytes(java.nio.file.Path.of(file.path)))
        val body = buildJsonObject {
            put("model", config.model)
            putJsonArray("messages") { addJsonObject { put("role", "user"); putJsonArray("content") { addJsonObject { put("type", "text"); put("text", "Analyze this sticker and return JSON only: {\\\"tags\\\":[\\\"short descriptive tags\\\"]}. Use Chinese tags when appropriate, maximum 8 tags.") }; addJsonObject { put("type", "image_url"); putJsonObject("image_url") { put("url", "data:$mime;base64,$data") } } } } }
            put("temperature", 0.2)
        }
        val response = client.post(config.url.trimEnd('/') + "/chat/completions") { header(HttpHeaders.Authorization, "Bearer ${config.apiKey.removePrefix("Bearer ").trim()}"); header(HttpHeaders.ContentType, ContentType.Application.Json); setBody(body.toString()) }
        if (response.status.value !in 200..299) error("HTTP ${response.status.value}: ${response.bodyAsText().take(240)}")
        val text = Json.parseToJsonElement(response.bodyAsText()).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
        val jsonText = text.substringAfter("{").substringBeforeLast("}", "").let { if (it.isBlank()) text else "{$it}" }
        return Json.parseToJsonElement(jsonText).jsonObject["tags"]!!.jsonArray.map { it.jsonPrimitive.content }.distinct().take(8)
    }
}
