package mcc.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import mcc.app.RecipeStore
import java.util.concurrent.Executors

/** Async, memory-cached recipe images. Views are tagged so recycled grid cells never show a stale image. */
object ImageLoader {
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3)
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    private val failed = java.util.Collections.synchronizedSet(HashSet<String>())

    fun load(store: RecipeStore, image: String?, view: ImageView, reqWidth: Int) {
        val key = "$image@$reqWidth"
        view.tag = key
        if (image == null || key in failed) { view.setImageDrawable(null); return }
        cache.get(key)?.let { view.setImageBitmap(it); view.alpha = 1f; return }
        view.setImageDrawable(null)
        pool.execute {
            val bmp = runCatching { store.imageFile(image)?.let { decode(it.path, reqWidth) } }.getOrNull()
            if (bmp == null) { failed.add(key); return@execute }
            cache.put(key, bmp)
            main.post {
                if (view.tag == key) {
                    view.alpha = 0f
                    view.setImageBitmap(bmp)
                    view.animate().alpha(1f).setDuration(220).start()
                }
            }
        }
    }

    private fun decode(path: String, reqWidth: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        var sample = 1
        while (o.outWidth / (sample * 2) >= reqWidth) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 })
    }
}
