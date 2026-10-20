package app.stickerkaleidoscope

data class AiProviderConfig(val url: String, val model: String, val apiKey: String)

expect object AiProvider {
    suspend fun test(config: AiProviderConfig): Result<String>
}
