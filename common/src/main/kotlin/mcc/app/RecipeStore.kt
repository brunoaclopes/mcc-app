package mcc.app

import android.content.Context
import mcc.recipe.Programs
import mcc.recipe.Recipe
import mcc.recipe.RecipeIndex
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Recipes come from the HTTP repository at [repoUrl] (static files: index.json + recipes/<id>.json).
 * Every downloaded file is cached in the app's external files dir, which is also where recipes can be
 * pushed by hand (adb push … /sdcard/Android/data/mcc.app/files/recipes-repo/). Works offline from the cache.
 */
class RecipeStore(private val context: Context, private val repoUrl: () -> String) {
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
        // built-in programs first, then recipes imported on this machine, then the server's
        val all = programs.map { entry(it, PROGRAM + it.id) } + mine() + (server?.recipes ?: emptyList())
        return RecipeIndex(format = "mcc-index/1", generated = server?.generated, recipes = all) to warning
    }

    private val programs = Programs.cooking()

    private fun entry(r: Recipe, file: String) = RecipeIndex.Entry(r.id, r.title, r.language, r.tags, r.totalMinutes, r.image, file)

    /** Recipes made on this machine (AI import), newest first. */
    private fun mine(): List<RecipeIndex.Entry> =
        (mineDir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray()).sortedByDescending { it.lastModified() }.mapNotNull { f ->
            runCatching { entry(Recipe.parse(f.readText()), "mine/" + f.name) }.getOrNull()
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

    fun loadRecipe(entry: RecipeIndex.Entry): Recipe {
        if (entry.file.startsWith(PROGRAM)) return Programs.all().first { it.id == entry.file.removePrefix(PROGRAM) }
        if (isMine(entry)) return Recipe.parse(File(localDir, entry.file).readText())
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

    companion object { const val PROGRAM = "program:" }

    /** Copies a bundled image out of the APK once, so the image loader can decode it like any cached file. */
    private fun assetFile(path: String): File? {
        // a local build may carry nicer photos (tools/fetch_program_photos.py; never committed): prefer them
        val own = path.replaceFirst("programs/", "programs-pixabay/")
        if (own != path && runCatching { context.assets.open(own).close() }.isSuccess) return copyAsset(own)
        return copyAsset(path)
    }

    private fun copyAsset(path: String): File? {
        val f = File(localDir, "assets/$path")
        if (f.exists() && f.length() > 0) return f
        return try {
            f.parentFile?.mkdirs()
            context.assets.open(path).use { input -> File(f.path + ".part").apply { outputStream().use { input.copyTo(it) }; renameTo(f) } }
            f
        } catch (e: Exception) { null }
    }

    private fun fetch(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 5_000
        c.readTimeout = 10_000
        try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode} for $url")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private fun join(base: String, path: String) = base.trimEnd('/') + "/" + path.trimStart('/')

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
