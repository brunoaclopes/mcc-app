package mcc.phone.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mcc.app.RecipeStore
import mcc.app.ui.Icon
import mcc.app.ui.Icons
import mcc.app.ui.T

/** The machine's palette (shared [T] tokens) as Compose colours. Read after [T.apply]. */
object Mc {
    /** Wallpaper (Material You) scheme when the user turned it on; the accents follow it, surfaces stay the machine's. */
    internal var dyn: androidx.compose.material3.ColorScheme? = null
    /** Toggled from Settings; read by [MccTheme] so the whole UI recomposes. */
    val dynamicOn = androidx.compose.runtime.mutableStateOf(false)
    val bg get() = Color(T.BG)
    val surface get() = Color(T.SURFACE)
    val surface2 get() = Color(T.SURFACE_2)
    val surface3 get() = Color(T.SURFACE_3)
    val stroke get() = Color(T.STROKE)
    val text get() = Color(T.TEXT)
    val text2 get() = Color(T.TEXT_2)
    val text3 get() = Color(T.TEXT_3)
    val ember get() = dyn?.primary ?: Color(T.EMBER)
    val emberHot get() = dyn?.primary ?: Color(T.EMBER_HOT)
    val amber get() = dyn?.tertiary ?: Color(T.AMBER)
    val teal get() = dyn?.secondary ?: Color(T.TEAL)
    val tealDeep get() = dyn?.secondary ?: Color(T.TEAL_DEEP)
    val ok get() = Color(T.OK)
    val danger get() = Color(T.DANGER)
    val backdrop get() = Color(T.BACKDROP)
    val titleEnd get() = Color(T.TITLE_END)
    val onAccent get() = dyn?.onPrimary ?: Color(T.ON_ACCENT)
    val onPhoto = Color(T.ON_PHOTO)
}

@OptIn(ExperimentalTextApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MccTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    T.apply(dark)
    val ctx = LocalContext.current
    val dynamic = Mc.dynamicOn.value && android.os.Build.VERSION.SDK_INT >= 31
    Mc.dyn = if (dynamic) (if (dark) androidx.compose.material3.dynamicDarkColorScheme(ctx) else androidx.compose.material3.dynamicLightColorScheme(ctx)) else null
    val manrope = remember {
        FontFamily(
            Font("fonts/manrope_400.ttf", ctx.assets, FontWeight.Normal),
            Font("fonts/manrope_600.ttf", ctx.assets, FontWeight.SemiBold),
            Font("fonts/manrope_700.ttf", ctx.assets, FontWeight.Bold),
            Font("fonts/manrope_800.ttf", ctx.assets, FontWeight.ExtraBold),
        )
    }
    val base = Typography()
    fun TextStyle.m() = copy(fontFamily = manrope)
    val typo = Typography(
        displayLarge = base.displayLarge.m().copy(fontWeight = FontWeight.ExtraBold),
        displayMedium = base.displayMedium.m().copy(fontWeight = FontWeight.ExtraBold),
        displaySmall = base.displaySmall.m().copy(fontWeight = FontWeight.ExtraBold),
        headlineLarge = base.headlineLarge.m().copy(fontWeight = FontWeight.ExtraBold),
        headlineMedium = base.headlineMedium.m().copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = base.headlineSmall.m().copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = base.titleLarge.m().copy(fontWeight = FontWeight.ExtraBold),
        titleMedium = base.titleMedium.m().copy(fontWeight = FontWeight.Bold),
        titleSmall = base.titleSmall.m().copy(fontWeight = FontWeight.Bold),
        bodyLarge = base.bodyLarge.m().copy(fontWeight = FontWeight.SemiBold),
        bodyMedium = base.bodyMedium.m().copy(fontWeight = FontWeight.SemiBold),
        bodySmall = base.bodySmall.m().copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.m().copy(fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.m().copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
        labelSmall = base.labelSmall.m().copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
    )
    val scheme = if (dark) darkColorScheme(
        primary = Mc.ember, onPrimary = Mc.onAccent, primaryContainer = Mc.amber, onPrimaryContainer = Mc.onAccent,
        secondary = Mc.teal, onSecondary = Mc.onAccent, secondaryContainer = Mc.surface3, onSecondaryContainer = Mc.text,
        tertiary = Mc.amber, background = Mc.bg, onBackground = Mc.text, surface = Mc.surface, onSurface = Mc.text,
        surfaceVariant = Mc.surface2, onSurfaceVariant = Mc.text2, surfaceContainer = Mc.surface, surfaceContainerHigh = Mc.surface2,
        surfaceContainerHighest = Mc.surface3, surfaceContainerLow = Mc.surface, outline = Mc.stroke, outlineVariant = Mc.stroke, error = Mc.danger,
    ) else lightColorScheme(
        primary = Mc.ember, onPrimary = Color.White, primaryContainer = Mc.amber, onPrimaryContainer = Mc.onAccent,
        secondary = Mc.teal, onSecondary = Color.White, secondaryContainer = Mc.surface3, onSecondaryContainer = Mc.text,
        tertiary = Mc.amber, background = Mc.bg, onBackground = Mc.text, surface = Mc.surface, onSurface = Mc.text,
        surfaceVariant = Mc.surface2, onSurfaceVariant = Mc.text2, surfaceContainer = Mc.surface, surfaceContainerHigh = Mc.surface2,
        surfaceContainerHighest = Mc.surface3, surfaceContainerLow = Mc.surface, outline = Mc.stroke, outlineVariant = Mc.stroke, error = Mc.danger,
    )
    // Material 3 Expressive: springy motion everywhere (buttons, sheets, indicators)
    androidx.compose.material3.MaterialExpressiveTheme(colorScheme = scheme, typography = typo,
        motionScheme = androidx.compose.material3.MotionScheme.expressive(), content = content)
}

/** One of the machine's line icons (shared [Icons]), drawn in Compose. */
@Composable
fun McIcon(icon: Icon, color: Color, size: Dp = 22.dp, stroke: Float = 2f, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        drawIntoCanvas { Icons.draw(it.nativeCanvas, icon, 0f, 0f, this.size.minDimension, color.toArgb(), stroke) }
    }
}

/** Recipe images through the shared [RecipeStore] cache (downloaded once, then offline). */
object Images {
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) { override fun sizeOf(k: String, v: Bitmap) = v.byteCount }

    @Composable
    fun load(store: RecipeStore, image: String?, width: Int): ImageBitmap? {
        val key = "$image@$width"
        // keyed state: a new image must never show (or keep) the previous one
        val state = remember(key) { mutableStateOf(cache.get(key)?.asImageBitmap()) }
        LaunchedEffect(key) {
            if (state.value != null || image == null) return@LaunchedEffect
            state.value = withContext(Dispatchers.IO) {
                runCatching {
                    store.imageFile(image)?.let { f ->
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(f.path, o)
                        var s = 1
                        while (o.outWidth / (s * 2) >= width) s *= 2
                        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })?.also { cache.put(key, it) }
                    }
                }.getOrNull()
            }?.asImageBitmap()
        }
        return state.value
    }
}
