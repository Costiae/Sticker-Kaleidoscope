package app.stickerkaleidoscope

import java.util.prefs.Preferences

actual object AppPreferences {
    private val store = Preferences.userRoot().node("app/stickerkaleidoscope")
    actual var language: String
        get() = store.get("language", "system")
        set(value) = store.put("language", value)
    actual var font: String
        get() = store.get("font", "system")
        set(value) = store.put("font", value)
    actual var fontSize: String
        get() = store.get("fontSize", "medium")
        set(value) = store.put("fontSize", value)
    actual var fontWeight: String
        get() = store.get("fontWeight", "normal")
        set(value) = store.put("fontWeight", value)
    actual var syncSource: String
        get() = store.get("syncSource", "")
        set(value) = store.put("syncSource", value)
    actual var modelProvider: String
        get() = store.get("modelProvider", "")
        set(value) = store.put("modelProvider", value)
    actual var providerUrl: String
        get() = store.get("providerUrl", "http://localhost:11434/v1")
        set(value) = store.put("providerUrl", value)
    actual var providerModel: String
        get() = store.get("providerModel", "")
        set(value) = store.put("providerModel", value)
    actual var providerApiKey: String
        get() = store.get("providerApiKey", "")
        set(value) = store.put("providerApiKey", value)
}
