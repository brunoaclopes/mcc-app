package mcc.app

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("mcc", Context.MODE_PRIVATE)

    /** First-run language: Portuguese on a Portuguese tablet, English everywhere else. */
    private val systemLang = if (java.util.Locale.getDefault().language == "pt") "pt" else "en"

    /** Base URL of the recipe repository (folder containing index.json), e.g. http://nas.local:8088/ */
    var repoUrl: String
        get() = sp.getString("repoUrl", "") ?: ""
        set(v) = sp.edit().putString("repoUrl", v.trim()).apply()

    /** Weigh steps move on by themselves once the amount is in the bowl (never starts the machine). */
    var autoWeigh: Boolean
        get() = sp.getBoolean("autoWeigh", true)
        set(v) = sp.edit().putBoolean("autoWeigh", v).apply()

    /** Last choices on the soup screen: vegetables (TENDER/MIXED/FIRM), blend level (0-3), onion base. */
    var soupKind: String
        get() = sp.getString("soupKind", "MIXED") ?: "MIXED"
        set(v) = sp.edit().putString("soupKind", v).apply()
    var soupBlend: Int
        get() = sp.getInt("soupBlend", 2)
        set(v) = sp.edit().putInt("soupBlend", v).apply()
    var soupOnion: Boolean
        get() = sp.getBoolean("soupOnion", true)
        set(v) = sp.edit().putBoolean("soupOnion", v).apply()

    /** UI language: "pt" (European Portuguese) or "en". */
    var uiLanguage: String
        get() = sp.getString("uiLanguage", systemLang) ?: systemLang
        set(v) = sp.edit().putString("uiLanguage", v).apply()

    /** Home Assistant area for the device (e.g. "Kitchen"); also prefixes the entity ids. Empty = none. */
    var haArea: String
        get() = sp.getString("haArea", "") ?: ""
        set(v) = sp.edit().putString("haArea", v.trim()).apply()

    /** "lat, lon" for the Auto day/night look. Empty = estimated from the time zone. */
    var location: String
        get() = sp.getString("location", "") ?: ""
        set(v) = sp.edit().putString("location", v.trim()).apply()

    /** Parsed [location], or null when empty or invalid. */
    val latLon: Pair<Double, Double>?
        get() = location.split(',').map { it.trim().toDoubleOrNull() }.takeIf { it.size == 2 && it.all { v -> v != null } }
            ?.let { (a, b) -> a!! to b!! }?.takeIf { it.first in -90.0..90.0 && it.second in -180.0..180.0 }

    /** Settings cards folded shut, by key. */
    var collapsed: Set<String>
        get() = sp.getStringSet("collapsed", setOf("about"))?.toSet() ?: setOf("about")
        set(v) = sp.edit().putStringSet("collapsed", v).apply()

    /** Look: "auto" (sunrise/sunset), "day" or "night". */
    var theme: String
        get() = sp.getString("theme", "auto") ?: "auto"
        set(v) = sp.edit().putString("theme", v).apply()


    /** Richer LED animations (motor-speed pulse, temperature colour, weighing fill, rainbow). Off = stock-like. */
    var ledAnimated: Boolean
        get() = sp.getBoolean("ledAnimated", true)
        set(v) = sp.edit().putBoolean("ledAnimated", v).apply()
    /** LED brightness, percent (25, 50, 100). */
    /** Empty-bowl reading saved as "bootId:grams"; only valid while the tablet (and the board) stay powered. */
    var emptyBowl: String
        get() = sp.getString("emptyBowl", "") ?: ""
        set(v) = sp.edit().putString("emptyBowl", v).apply()

    var ledBrightness: Int
        get() = sp.getInt("ledBrightness", 100)
        set(v) = sp.edit().putInt("ledBrightness", v).apply()
    /** 0 = off, 1 = quiet, 2 = normal. */
    var soundLevel: Int
        get() = sp.getInt("soundLevel", 2)
        set(v) = sp.edit().putInt("soundLevel", v).apply()

    /** OpenAI-compatible API base, e.g. http://nas.local:8080/v1 (empty = AI features off). */
    var aiUrl: String
        get() = sp.getString("aiUrl", "") ?: ""
        set(v) = sp.edit().putString("aiUrl", v.trim()).apply()
    var aiModel: String
        get() = sp.getString("aiModel", "") ?: ""
        set(v) = sp.edit().putString("aiModel", v.trim()).apply()
    var aiKey: String
        get() = sp.getString("aiKey", "") ?: ""
        set(v) = sp.edit().putString("aiKey", v.trim()).apply()

    /** Lets Home Assistant start a run that was prepared and permitted at the machine. Off by default. */
    var remoteStart: Boolean
        get() = sp.getBoolean("remoteStart", false)
        set(v) = sp.edit().putBoolean("remoteStart", v).apply()
    /** How long a remote-start permit stays valid. */
    /** The phone app may open (never start) a recipe on the Cook screen. */
    var remoteLoad: Boolean
        get() = sp.getBoolean("remoteLoad", true)
        set(v) = sp.edit().putBoolean("remoteLoad", v).apply()

    var remoteMinutes: Int
        get() = sp.getInt("remoteMinutes", 30)
        set(v) = sp.edit().putInt("remoteMinutes", v).apply()

    /** Stock-app standby: screen off after [standbyMinutes] without use (never while anything runs). */
    var standbyEnabled: Boolean
        get() = sp.getBoolean("standbyEnabled", true)
        set(v) = sp.edit().putBoolean("standbyEnabled", v).apply()
    var standbyMinutes: Int
        get() = sp.getInt("standbyMinutes", 7)
        set(v) = sp.edit().putInt("standbyMinutes", v).apply()

    var mqttEnabled: Boolean
        get() = sp.getBoolean("mqttEnabled", false)
        set(v) = sp.edit().putBoolean("mqttEnabled", v).apply()
    var mqttHost: String
        get() = sp.getString("mqttHost", "") ?: ""
        set(v) = sp.edit().putString("mqttHost", v.trim()).apply()
    var mqttPort: Int
        get() = sp.getInt("mqttPort", 1883)
        set(v) = sp.edit().putInt("mqttPort", v).apply()
    var mqttUser: String
        get() = sp.getString("mqttUser", "") ?: ""
        set(v) = sp.edit().putString("mqttUser", v).apply()
    var mqttPassword: String
        get() = sp.getString("mqttPassword", "") ?: ""
        set(v) = sp.edit().putString("mqttPassword", v).apply()
}
