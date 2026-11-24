package mcc.phone

import android.content.Context

/** Phone settings. The broker password is typed by the user and stays in the app's private storage. */
class PhonePrefs(c: Context) {
    private val sp = c.getSharedPreferences("mcc-phone", Context.MODE_PRIVATE)

    var host: String get() = sp.getString("host", "") ?: ""; set(v) = sp.edit().putString("host", v.trim()).apply()
    var port: Int get() = sp.getInt("port", 1883); set(v) = sp.edit().putInt("port", v).apply()
    var tls: Boolean get() = sp.getBoolean("tls", false); set(v) = sp.edit().putBoolean("tls", v).apply()
    var user: String get() = sp.getString("user", "") ?: ""; set(v) = sp.edit().putString("user", v.trim()).apply()
    var password: String get() = sp.getString("password", "") ?: ""; set(v) = sp.edit().putString("password", v).apply()
    var repoUrl: String get() = sp.getString("repoUrl", "") ?: ""; set(v) = sp.edit().putString("repoUrl", v.trim()).apply()
    // recipe import: the AI proxy (OpenAI-compatible) and the NAS upload service that adds recipes to the repository
    var aiUrl: String get() = sp.getString("aiUrl", "") ?: ""; set(v) = sp.edit().putString("aiUrl", v.trim()).apply()
    var aiModel: String get() = sp.getString("aiModel", "") ?: ""; set(v) = sp.edit().putString("aiModel", v.trim()).apply()
    var aiKey: String get() = sp.getString("aiKey", "") ?: ""; set(v) = sp.edit().putString("aiKey", v.trim()).apply()
    var publishUrl: String get() = sp.getString("publishUrl", "") ?: ""; set(v) = sp.edit().putString("publishUrl", v.trim()).apply()
    var publishToken: String get() = sp.getString("publishToken", "") ?: ""; set(v) = sp.edit().putString("publishToken", v.trim()).apply()
    /** "" = follow the phone, "pt" or "en". */
    var language: String get() = sp.getString("language", "") ?: ""; set(v) = sp.edit().putString("language", v).apply()

    var background: Boolean get() = sp.getBoolean("background", true); set(v) = sp.edit().putBoolean("background", v).apply()
    var live: Boolean get() = sp.getBoolean("live", true); set(v) = sp.edit().putBoolean("live", v).apply()
    var notifyNeedsYou: Boolean get() = sp.getBoolean("nNeeds", true); set(v) = sp.edit().putBoolean("nNeeds", v).apply()
    var notifyFinished: Boolean get() = sp.getBoolean("nDone", true); set(v) = sp.edit().putBoolean("nDone", v).apply()
    var notifyWeighed: Boolean get() = sp.getBoolean("nWeigh", false); set(v) = sp.edit().putBoolean("nWeigh", v).apply()
    var dynamicColor: Boolean get() = sp.getBoolean("dynColor", false); set(v) = sp.edit().putBoolean("dynColor", v).apply()
    /** Last machine state seen, for the widget and the tile when the link is down. */
    var lastState: String get() = sp.getString("lastState", "") ?: ""; set(v) = sp.edit().putString("lastState", v).apply()
    var notifyProblems: Boolean get() = sp.getBoolean("nProb", true); set(v) = sp.edit().putBoolean("nProb", v).apply()

    /** Unique per install: two phones with the same MQTT client id would keep kicking each other off. */
    val clientId: String get() = sp.getString("clientId", null) ?: ("mcc-phone-" + java.util.UUID.randomUUID().toString().take(8)).also {
        sp.edit().putString("clientId", it).apply()
    }

    var shopping: String get() = sp.getString("shopping", "[]") ?: "[]"; set(v) = sp.edit().putString("shopping", v).apply()

    val configured get() = host.isNotBlank()

    fun languageCode(): String = language.ifEmpty { if (java.util.Locale.getDefault().language == "pt") "pt" else "en" }
}
