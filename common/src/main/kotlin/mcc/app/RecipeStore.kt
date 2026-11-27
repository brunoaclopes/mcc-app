package mcc.app

import android.content.Context
import mcc.recipe.Programs
import mcc.recipe.Recipe
import mcc.recipe.RecipeIndex
import mcc.recipe.online.McOnline
import mcc.recipe.online.Moulinex
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A recipe id without its language ending: both languages of a recipe share it (history, favourites, AI improvements). */
fun recipeKey(id: String) = id.replace(Regex("-(pt|en)$"), "")

/**
 * Recipes come from the HTTP repository at [repoUrl] (static files: index.json + recipes/<id>.json).
 * Every downloaded file is cached in the app's external files dir, which is also where recipes can be
 * pushed by hand (adb push … /sdcard/Android/data/mcc.app/files/recipes-repo/). Works offline from the cache.
 *
 * With [online] on, the official recipes of the Monsieur Cuisine website are listed too ([McOnline]): the list is
 * refreshed at most every [ONLINE_MAX_AGE_MS], and a recipe is downloaded and converted only when it is opened,
 * then kept in the cache. Online copies of recipes the repository already has (same stock recipe number) are hidden.
 * With [members] on as well, the recipes members published on the website are listed too (tagged "Community").
 * With [moulinex] on, the Moulinex Companion recipes of moulinex.pt are listed the same way ([Moulinex]):
 * the list is refreshed at most weekly, and a recipe is converted for this machine when it is opened.
 */
class RecipeStore(
    private val context: Context,
    private val online: () -> Boolean = { false },
    private val members: () -> Boolean = { false },
    private val moulinex: () -> Boolean = { false },
    private val repoUrl: () -> String,
) {
    val localDir: File = File(context.getExternalFilesDir(null) ?: context.filesDir, "recipes-repo")

    /** Downloads index.json from the repository (if configured) and returns the index (cached copy on failure). */
    fun loadIndex(refresh: Boolean): Pair<RecipeIndex?, String?> {
        val base = repoUrl()
        var warning: String? = null
        if (refresh && base.isNotBlank()) {
            try {
                val bytes = fetch(join(base, "index.json"))
                RecipeIndex.parse(bytes.toString(Charsets.UTF_8)) // validate before replacing the cache
                File(localDir, "index.json").also { it.parentFile?.mkdirs() }.writeBytes(bytes)
            } catch (e: Exception) {
                warning = mcc.app.i18n.L.s.serverUnreachable
            }
        } else if (base.isBlank()) {
            warning = mcc.app.i18n.L.s.offlineCopy
        }
        val f = File(localDir, "index.json")
        var server: RecipeIndex? = null
        if (f.exists()) {
            try { server = RecipeIndex.parse(f.readText()) } catch (e: Exception) { warning = "local index.json is invalid: ${e.message}" }
        }
        // built-in programs first, then recipes imported on this machine, then the server's, then online ones
        val mineRecipes = mineRecipes()
        // a recipe improved with the AI (a copy in mine/) is shown instead of the one it came from
        val replaced = mineRecipes.mapNotNull { (r, _) -> r.source?.takeIf { it.kind == AiClient.TWEAK }?.ref }.toSet()
        fun kept(e: RecipeIndex.Entry) = mcc.app.recipeKey(e.id) !in replaced
        val own = mineRecipes.map { (r, f) -> entry(r, "mine/" + f.name) } + (server?.recipes ?: emptyList()).filter(::kept)
        var onlineEntries = emptyList<RecipeIndex.Entry>()
        if (online()) {
            val (list, w) = onlineIndex(refresh)
            // without a server the "offline copy" note would be wrong: only the online state matters then
            warning = if (base.isBlank()) w else warning ?: w
            val have = own.mapNotNull { stockNumber.find(it.id)?.groupValues?.get(1) }.toSet()
            onlineEntries = list.filter { McOnline.recipeNumber(it.image)?.let { n -> n in have } != true && kept(it) }
        }
        if (moulinex()) {
            val (list, w) = moulinexIndex(refresh)
            warning = if (base.isBlank() && !online()) w else warning ?: w
            onlineEntries = onlineEntries + list.filter(::kept)
        }
        val all = programs.map { entry(it, PROGRAM + it.id) }.filter(::kept) + own + onlineEntries
        return RecipeIndex(format = "mcc-index/1", generated = server?.generated, recipes = all) to warning
    }

    /** "mc2-24407-pt" / "mc3-24407-en": recipes converted from the stock apps carry the stock recipe number. */
    private val stockNumber = Regex("^mc[23]-(\\d{5})-")

    private val onlineDir get() = File(localDir, "online")
    private fun onlineLang() = mcc.app.i18n.L.s.code

    /** The online list in the UI language, from the cache, downloaded again when [refresh] and older than a day. */
    private fun onlineIndex(refresh: Boolean): Pair<List<RecipeIndex.Entry>, String?> {
        val lang = onlineLang()
        val withMembers = members()
        // one cached list per choice, so switching members on or off shows the right list at once
        val f = File(onlineDir, if (withMembers) "index-$lang-all.json" else "index-$lang.json")
        var warning: String? = null
        if (refresh && (!f.exists() || System.currentTimeMillis() - f.lastModified() > ONLINE_MAX_AGE_MS)) {
            try {
                val out = ArrayList<RecipeIndex.Entry>()
                var page = 1
                do {
                    val data = McOnline.parseList(fetch(McOnline.listUrl(page, withMembers), McOnline.headers(lang)).toString(Charsets.UTF_8))
                    out += McOnline.entries(data, lang, withMembers, mcc.app.i18n.L.s.tagMember)
                    page++
                } while (page <= data.totalPage && page <= 50)
                if (out.isNotEmpty()) {
                    onlineDir.mkdirs()
                    val idx = RecipeIndex(format = "mcc-index/1", generated = java.util.Date().toString(), recipes = out)
                    File(f.path + ".part").apply { writeText(Recipe.json.encodeToString(RecipeIndex.serializer(), idx)); renameTo(f) }
                }
            } catch (e: Exception) {
                warning = mcc.app.i18n.L.s.onlineUnreachable
            }
        }
        val cached: List<RecipeIndex.Entry> = if (f.exists()) runCatching { RecipeIndex.parse(f.readText()).recipes }.getOrDefault(emptyList()) else emptyList()
        val list = cached.map { e -> e.copy(tags = (listOf(mcc.app.i18n.L.s.tagOnline) + e.tags).take(4)) }
        return list to warning
    }

    /**
     * The Moulinex Companion list, from the cache, downloaded again when older than a week. Always the Portuguese
     * catalogue: the English one (moulinex.co.uk) only has old recipes without machine settings.
     */
    private fun moulinexIndex(refresh: Boolean): Pair<List<RecipeIndex.Entry>, String?> {
        val lang = Moulinex.LANG
        val f = File(onlineDir, "moulinex-$lang.json")
        var warning: String? = null
        if (refresh && (!f.exists() || System.currentTimeMillis() - f.lastModified() > MOULINEX_MAX_AGE_MS)) {
            try {
                val out = ArrayList<RecipeIndex.Entry>()
                var page = 0
                do {
                    val data = Moulinex.parseList(fetch(Moulinex.searchUrl(page, lang), Moulinex.headers(), Moulinex.searchBody(lang)).toString(Charsets.UTF_8), lang)
                    out += data.entries
                    page++
                } while (page < data.pages && page < 10)
                if (out.isNotEmpty()) {
                    onlineDir.mkdirs()
                    val idx = RecipeIndex(format = "mcc-index/1", generated = java.util.Date().toString(), recipes = out)
                    File(f.path + ".part").apply { writeText(Recipe.json.encodeToString(RecipeIndex.serializer(), idx)); renameTo(f) }
                }
            } catch (e: Exception) {
                warning = mcc.app.i18n.L.s.moulinexUnreachable
            }
        }
        val cached: List<RecipeIndex.Entry> = if (f.exists()) runCatching { RecipeIndex.parse(f.readText()).recipes }.getOrDefault(emptyList()) else emptyList()
        return cached.map { e -> e.copy(tags = (listOf(mcc.app.i18n.L.s.tagOnline) + e.tags).distinct().take(4)) } to warning
    }

    /** A recipe read live from a website (Monsieur Cuisine or Moulinex) and converted when opened. */
    fun isOnline(e: RecipeIndex.Entry) = e.file.startsWith(McOnline.FILE_PREFIX) || e.file.startsWith(Moulinex.FILE_PREFIX)

    private val programs = Programs.cooking()

    private fun entry(r: Recipe, file: String) = RecipeIndex.Entry(r.id, r.title, r.language, r.tags, r.totalMinutes, r.image, file)

    /** Recipes made on this device (AI import, AI improvements), newest first, with their files. */
    private fun mineRecipes(): List<Pair<Recipe, File>> =
        (mineDir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()).sortedByDescending { it.lastModified() }.mapNotNull { f ->
            runCatching { Recipe.parse(f.readText()) to f }.getOrNull()
        }

    private val mineDir get() = File(localDir, "mine")

    /** Saves an imported recipe (tagged "My recipes" in its language) and returns it; it shows up in the list after [loadIndex]. */
    fun saveMine(r: Recipe): Recipe {
        val tag = (if (r.language == "en") mcc.app.i18n.En else mcc.app.i18n.Pt).tagMine
        val out = r.copy(tags = (listOf(tag) + r.tags.filter { it != tag }).take(4))
        mineDir.mkdirs()
        File(mineDir, out.id + ".json").writeText(Recipe.json.encodeToString(Recipe.serializer(), out))
        return out
    }

    fun isMine(e: RecipeIndex.Entry) = e.file.startsWith("mine/")
    /** Deletes one of my recipes, with its other-language copy. */
    fun deleteMine(e: RecipeIndex.Entry) {
        if (!isMine(e)) return
        File(localDir, e.file).delete()
        val base = e.id.replace(Regex("-(pt|en)$"), "")
        if (base != e.id) listOf("pt", "en").forEach { File(mineDir, "$base-$it.json").delete() }
    }

    /**
     * The recipe behind an index entry, downloading it when it is not cached. With [download] false, an online
     * recipe that has not been opened yet is not fetched (the ingredient search reads every recipe).
     */
    fun loadRecipe(entry: RecipeIndex.Entry, download: Boolean = true): Recipe {
        if (entry.file.startsWith(PROGRAM)) return Programs.all().first { it.id == entry.file.removePrefix(PROGRAM) }
        if (isMine(entry)) return Recipe.parse(File(localDir, entry.file).readText())
        if (entry.file.startsWith(Moulinex.FILE_PREFIX)) {
            val lang = entry.language ?: Moulinex.LANG
            val f = File(onlineDir, "recipes/${entry.id}.json")
            if (f.exists()) return Recipe.parse(f.readText())
            require(download) { "online recipe not downloaded yet" }
            val id = entry.file.removePrefix(Moulinex.FILE_PREFIX)
            require(id.all { it.isDigit() }) { "bad Moulinex id" }
            val r = Moulinex.convert(fetch(Moulinex.detailUrl(id), Moulinex.headers()).toString(Charsets.UTF_8), lang)
            val problems = r.validate()
            require(problems.isEmpty()) { problems.joinToString("; ") }
            f.parentFile?.mkdirs()
            File(f.path + ".part").apply { writeText(Recipe.json.encodeToString(Recipe.serializer(), r)); renameTo(f) }
            return r
        }
        if (isOnline(entry)) {
            val id = entry.file.removePrefix(McOnline.FILE_PREFIX).toLong()
            val lang = entry.language ?: onlineLang()
            val f = File(onlineDir, "recipes/${entry.id}.json")
            if (f.exists()) return Recipe.parse(f.readText())
            require(download) { "online recipe not downloaded yet" }
            val r = McOnline.convert(McOnline.parseDetail(fetch(McOnline.detailUrl(id), McOnline.headers(lang)).toString(Charsets.UTF_8)), lang)
            val problems = r.validate()
            require(problems.isEmpty()) { problems.joinToString("; ") }
            f.parentFile?.mkdirs()
            File(f.path + ".part").apply { writeText(Recipe.json.encodeToString(Recipe.serializer(), r)); renameTo(f) }
            return r
        }
        val f = File(localDir, entry.file)
        if (!f.exists() || (entry.sha256 != null && sha256(f.readBytes()) != entry.sha256)) {
            val base = repoUrl()
            require(base.isNotBlank()) { "recipe file missing and no repository configured" }
            val bytes = fetch(join(base, entry.file))
            if (entry.sha256 != null) require(sha256(bytes) == entry.sha256) { "checksum mismatch for ${entry.file}" }
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
        }
        return Recipe.parse(f.readText())
    }

    /**
     * Local file for a recipe image: a path relative to the repository (cached under [localDir]) or an
     * absolute http(s) URL (cached by hash). Downloads on first use. Call from a background thread.
     */
    fun imageFile(image: String?): File? {
        if (image.isNullOrBlank()) return null
        if (image.startsWith("asset:")) return assetFile(image.removePrefix("asset:"))
        val remote = image.startsWith("http://") || image.startsWith("https://")
        val f = if (remote) File(localDir, "remote/" + sha256(image.toByteArray()).take(20) + ".img") else File(localDir, image)
        if (f.exists() && f.length() > 0) return f
        val url = if (remote) image else repoUrl().takeIf { it.isNotBlank() }?.let { join(it, image) } ?: return null
        return try {
            val bytes = fetch(url)
            f.parentFile?.mkdirs()
            File(f.path + ".part").apply { writeBytes(bytes); renameTo(f) }
            f
        } catch (e: Exception) { null }
    }

    companion object {
        const val PROGRAM = "program:"
        const val ONLINE_MAX_AGE_MS = 24 * 3600_000L
        /** The Moulinex list changes slowly and is bigger (about 1.8 MB in Portuguese): once a week. */
        const val MOULINEX_MAX_AGE_MS = 7 * 24 * 3600_000L
    }

    private val apkUpdated: Long by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrDefault(0L)
    }

    /** Copies a bundled image out of the APK once per install, so the image loader can decode it like any cached file. */
    private fun assetFile(path: String): File? {
        // a local build may carry nicer photos (tools/fetch_program_photos.py; never committed): prefer them
        val own = path.replaceFirst("programs/", "programs-pixabay/")
        if (own != path && runCatching { context.assets.open(own).close() }.isSuccess) return copyAsset(own)
        return copyAsset(path)
    }

    private fun copyAsset(path: String): File? {
        val f = File(localDir, "assets/$path")
        // a copy older than the installed APK may be a photo an update replaced: copy it again
        if (f.exists() && f.length() > 0 && f.lastModified() >= apkUpdated) return f
        return try {
            f.parentFile?.mkdirs()
            context.assets.open(path).use { input -> File(f.path + ".part").apply { outputStream().use { input.copyTo(it) }; f.delete(); renameTo(f) } }
            f
        } catch (e: Exception) { null }
    }

    /** GET, or POST with [body] (JSON). */
    private fun fetch(url: String, headers: Map<String, String> = emptyMap(), body: String? = null): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5_000
        c.readTimeout = if (body != null) 30_000 else 10_000
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        try {
            if (body != null) { c.requestMethod = "POST"; c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode} for $url")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private fun join(base: String, path: String) = base.trimEnd('/') + "/" + path.trimStart('/')

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
