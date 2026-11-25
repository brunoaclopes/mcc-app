package mcc.app.ui

import android.graphics.Bitmap
import android.util.LruCache
import mcc.recipe.BlurHash

/** A recipe photo's BlurHash (from the index) as a tiny bitmap; stretched with filtering it reads as a blur. */
object BlurPlaceholder {
    private val cache = LruCache<String, Bitmap>(256)   // 32 x 24 x 4 bytes each: 3 KB

    fun bitmap(hash: String?): Bitmap? {
        if (hash.isNullOrBlank()) return null
        cache.get(hash)?.let { return it }
        val px = BlurHash.decode(hash, 32, 24) ?: return null
        return Bitmap.createBitmap(px, 32, 24, Bitmap.Config.ARGB_8888).also { cache.put(hash, it) }
    }
}
