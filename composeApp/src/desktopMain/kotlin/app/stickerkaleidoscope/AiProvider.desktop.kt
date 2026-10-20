package app.stickerkaleidoscope

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

actual object AiProvider {
    actual suspend fun test(config: AiProviderConfig): Result<String> = runCatching {
        val endpoint = config.url.trimEnd('/') + "/models"
        val builder = HttpRequest.newBuilder(URI(endpoint)).GET().header("Accept", "application/json")
        if (config.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${config.apiKey}")
        val response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) error("HTTP ${response.statusCode()}")
        "HTTP ${response.statusCode()}"
    }
}
