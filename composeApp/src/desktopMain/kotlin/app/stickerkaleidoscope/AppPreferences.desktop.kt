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
    actual var syncSource: String
        get() = store.get("syncSource", "")
        set(value) = store.put("syncSource", value)
    actual var modelProvider: String
        get() = store.get("modelProvider", "")
        set(value) = store.put("modelProvider", value)
}
