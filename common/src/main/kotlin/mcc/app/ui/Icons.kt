package mcc.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/** Line icons drawn on a 24x24 grid (no icon font or image assets needed). */
enum class Icon { HOME, DIAL, BOOK, POT, GEAR, LOCK, UNLOCK, LID, SCALE, THERMO, STOP, PLAY, PAUSE, BACK, NEXT, CHECK,
    REVERSE, CLOCK, FAN, FLAME, SEARCH, LIST, WIFI, TURBO, KNEAD, STEAM, REFRESH, CHEF, BOLT, DOWN, CLOSE, HAND, GLOBE, SUN, MOON, SPARK, TRASH, EGG, RICE, CHOCO, GLASS, SOUP, DROP, GRID,
    STAR, STAR_FULL, WHISK, BASKET, BEAKER, STEAMER }

object Icons {
    fun draw(c: Canvas, icon: Icon, x: Float, y: Float, size: Float, color: Int, strokeDp: Float = 2f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            this.color = color; strokeWidth = strokeDp // in 24-unit grid space: scales with the icon
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }
        c.save()
        c.translate(x, y)
        c.scale(size / 24f, size / 24f)
        val path = Path()
        when (icon) {
            Icon.HOME -> { path.moveTo(3f, 11f); path.lineTo(12f, 3.5f); path.lineTo(21f, 11f); c.drawPath(path, p)
                c.drawRoundRect(RectF(5.5f, 10f, 18.5f, 20.5f), 2f, 2f, p); c.drawLine(10f, 20.5f, 10f, 15f, p); c.drawLine(10f, 15f, 14f, 15f, p); c.drawLine(14f, 15f, 14f, 20.5f, p) }
            Icon.DIAL -> { c.drawArc(RectF(3f, 3f, 21f, 21f), 135f, 270f, false, p); c.drawLine(12f, 12f, 16.5f, 7.5f, p); c.drawCircle(12f, 12f, 1.6f, fill) }
            Icon.BOOK -> { c.drawRoundRect(RectF(4f, 3f, 20f, 21f), 2.5f, 2.5f, p); c.drawLine(8f, 3f, 8f, 21f, p); c.drawLine(11f, 8f, 16.5f, 8f, p); c.drawLine(11f, 11.5f, 15f, 11.5f, p) }
            Icon.POT -> { c.drawRoundRect(RectF(4f, 9f, 20f, 20f), 3f, 3f, p); c.drawLine(2f, 9f, 22f, 9f, p)
                path.moveTo(9f, 6f); path.quadTo(8f, 4.5f, 9f, 3f); path.moveTo(12.5f, 6f); path.quadTo(11.5f, 4.5f, 12.5f, 3f); path.moveTo(16f, 6f); path.quadTo(15f, 4.5f, 16f, 3f); c.drawPath(path, p) }
            Icon.GEAR -> { c.drawCircle(12f, 12f, 3f, p)
                for (i in 0 until 8) { c.save(); c.rotate(i * 45f, 12f, 12f); c.drawLine(12f, 2.8f, 12f, 5.2f, p); c.restore() }
                c.drawCircle(12f, 12f, 6.8f, p) }
            Icon.LOCK, Icon.UNLOCK -> { c.drawRoundRect(RectF(5f, 10.5f, 19f, 20.5f), 2.5f, 2.5f, p)
                if (icon == Icon.LOCK) c.drawArc(RectF(8f, 3.5f, 16f, 13.5f), 180f, 180f, false, p).also { c.drawLine(8f, 8.5f, 8f, 10.5f, p); c.drawLine(16f, 8.5f, 16f, 10.5f, p) }
                else { c.drawArc(RectF(8f, 3.5f, 16f, 13.5f), 180f, 150f, false, p); c.drawLine(8f, 8.5f, 8f, 10.5f, p) }
                c.drawLine(12f, 14.5f, 12f, 16.5f, p) }
            Icon.LID -> { c.drawArc(RectF(4f, 6f, 20f, 18f), 180f, 180f, false, p); c.drawLine(3f, 12f, 21f, 12f, p); c.drawLine(10f, 4.5f, 14f, 4.5f, p); c.drawLine(12f, 4.5f, 12f, 6f, p)
                c.drawRoundRect(RectF(6f, 14f, 18f, 20f), 2f, 2f, p) }
            Icon.SCALE -> { c.drawRoundRect(RectF(3f, 15f, 21f, 20f), 2f, 2f, p); c.drawArc(RectF(6f, 5f, 18f, 17f), 200f, 140f, false, p); c.drawLine(12f, 11f, 14.5f, 8f, p) }
            Icon.THERMO -> { c.drawRoundRect(RectF(9.5f, 3f, 14.5f, 15f), 2.5f, 2.5f, p); c.drawCircle(12f, 17.5f, 3.5f, p); c.drawCircle(12f, 17.5f, 1.6f, fill); c.drawLine(12f, 8f, 12f, 16f, p) }
            Icon.STOP -> { c.drawRoundRect(RectF(6f, 6f, 18f, 18f), 2.5f, 2.5f, fill) }
            Icon.PLAY -> { path.moveTo(8f, 5f); path.lineTo(19f, 12f); path.lineTo(8f, 19f); path.close(); c.drawPath(path, fill) }
            Icon.PAUSE -> { c.drawRoundRect(RectF(6.5f, 5f, 10.5f, 19f), 1.5f, 1.5f, fill); c.drawRoundRect(RectF(13.5f, 5f, 17.5f, 19f), 1.5f, 1.5f, fill) }
            Icon.BACK -> { path.moveTo(15f, 5f); path.lineTo(8f, 12f); path.lineTo(15f, 19f); c.drawPath(path, p) }
            Icon.NEXT -> { path.moveTo(9f, 5f); path.lineTo(16f, 12f); path.lineTo(9f, 19f); c.drawPath(path, p) }
            Icon.CHECK -> { path.moveTo(4.5f, 12.5f); path.lineTo(9.5f, 17.5f); path.lineTo(19.5f, 6.5f); c.drawPath(path, p) }
            Icon.REVERSE -> { c.drawArc(RectF(4f, 4f, 20f, 20f), 300f, 280f, false, p); path.moveTo(4f, 6f); path.lineTo(5.6f, 10.2f); path.lineTo(9.6f, 8.6f); c.drawPath(path, p) }
            Icon.CLOCK -> { c.drawCircle(12f, 12f, 8.5f, p); c.drawLine(12f, 12f, 12f, 7.5f, p); c.drawLine(12f, 12f, 15.5f, 14f, p) }
            Icon.FAN -> { for (i in 0 until 3) { c.save(); c.rotate(i * 120f, 12f, 12f); path.reset(); path.moveTo(12f, 12f); path.cubicTo(12f, 6f, 16f, 3.5f, 18f, 5.5f); path.cubicTo(19.5f, 7.5f, 16f, 10f, 12f, 12f); c.drawPath(path, p); c.restore() }
                c.drawCircle(12f, 12f, 1.4f, fill) }
            Icon.FLAME -> { path.moveTo(12f, 21f); path.cubicTo(7f, 21f, 5f, 17.5f, 6f, 14f); path.cubicTo(7f, 11f, 10f, 9.5f, 10f, 5f); path.cubicTo(13.5f, 6.5f, 15f, 9f, 14.5f, 11.5f)
                path.cubicTo(15.5f, 11f, 16.3f, 10f, 16.5f, 9f); path.cubicTo(18.5f, 11.5f, 19f, 15f, 17.5f, 17.5f); path.cubicTo(16.5f, 19.5f, 14.5f, 21f, 12f, 21f); c.drawPath(path, p) }
            Icon.SEARCH -> { c.drawCircle(10.5f, 10.5f, 6f, p); c.drawLine(15f, 15f, 20f, 20f, p) }
            Icon.LIST -> { for (yy in listOf(6f, 12f, 18f)) { c.drawCircle(4.5f, yy, 1.2f, fill); c.drawLine(8.5f, yy, 20f, yy, p) } }
            Icon.WIFI -> { c.drawArc(RectF(2f, 5f, 22f, 25f), 225f, 90f, false, p); c.drawArc(RectF(6f, 9f, 18f, 21f), 225f, 90f, false, p); c.drawCircle(12f, 17.5f, 1.6f, fill) }
            Icon.EGG -> { path.moveTo(12f, 3f); path.cubicTo(16.5f, 3f, 19f, 10f, 19f, 14f); path.cubicTo(19f, 18f, 16f, 21f, 12f, 21f); path.cubicTo(8f, 21f, 5f, 18f, 5f, 14f); path.cubicTo(5f, 10f, 7.5f, 3f, 12f, 3f); path.close(); c.drawPath(path, p)
                c.drawArc(RectF(8f, 12f, 13f, 17f), 100f, 80f, false, p) }
            Icon.RICE -> { path.moveTo(3f, 12f); path.lineTo(21f, 12f); path.cubicTo(21f, 17f, 17f, 20f, 12f, 20f); path.cubicTo(7f, 20f, 3f, 17f, 3f, 12f); path.close(); c.drawPath(path, p)
                for ((xx, yy) in listOf(8f to 9.5f, 11f to 8f, 14f to 9.5f, 9.5f to 6f, 12.5f to 5.5f, 16f to 7f)) c.drawCircle(xx, yy, 0.9f, fill) }
            Icon.CHOCO -> { c.drawRoundRect(RectF(6f, 3f, 18f, 21f), 1.5f, 1.5f, p); c.drawLine(12f, 3f, 12f, 21f, p); c.drawLine(6f, 9f, 18f, 9f, p); c.drawLine(6f, 15f, 18f, 15f, p) }
            Icon.GLASS -> { path.moveTo(6f, 5f); path.lineTo(18f, 5f); path.lineTo(16.5f, 21f); path.lineTo(7.5f, 21f); path.close(); c.drawPath(path, p); c.drawLine(6.6f, 11f, 17.4f, 11f, p); c.drawLine(14f, 5f, 16f, 1.5f, p) }
            Icon.SOUP -> { path.moveTo(3f, 12f); path.lineTo(21f, 12f); path.cubicTo(21f, 17f, 17f, 20f, 12f, 20f); path.cubicTo(7f, 20f, 3f, 17f, 3f, 12f); path.close(); c.drawPath(path, p)
                for (xx in listOf(9f, 12f, 15f)) { path.reset(); path.moveTo(xx, 9.5f); path.cubicTo(xx - 1.5f, 7.5f, xx + 1.5f, 6f, xx, 3.5f); c.drawPath(path, p) } }
            Icon.DROP -> { path.moveTo(12f, 3f); path.cubicTo(15f, 7.5f, 18.5f, 11f, 18.5f, 14.5f); path.cubicTo(18.5f, 18.5f, 15.5f, 21f, 12f, 21f); path.cubicTo(8.5f, 21f, 5.5f, 18.5f, 5.5f, 14.5f); path.cubicTo(5.5f, 11f, 9f, 7.5f, 12f, 3f); path.close(); c.drawPath(path, p)
                c.drawArc(RectF(8.5f, 12.5f, 13.5f, 17.5f), 100f, 80f, false, p) }
            Icon.GRID -> { for ((xx, yy) in listOf(3.5f to 3.5f, 13.5f to 3.5f, 3.5f to 13.5f, 13.5f to 13.5f)) c.drawRoundRect(RectF(xx, yy, xx + 7f, yy + 7f), 2f, 2f, p) }
            Icon.SPARK -> { path.moveTo(11f, 3f); path.quadTo(12f, 10f, 19f, 11f); path.quadTo(12f, 12f, 11f, 19f); path.quadTo(10f, 12f, 3f, 11f); path.quadTo(10f, 10f, 11f, 3f); path.close(); c.drawPath(path, p)
                c.drawLine(18.5f, 3.5f, 18.5f, 7.5f, p); c.drawLine(16.5f, 5.5f, 20.5f, 5.5f, p) }
            Icon.TRASH -> { c.drawLine(4f, 6.5f, 20f, 6.5f, p); c.drawRoundRect(RectF(6.5f, 6.5f, 17.5f, 20.5f), 2f, 2f, p); c.drawLine(9.5f, 3.5f, 14.5f, 3.5f, p)
                c.drawLine(10f, 10.5f, 10f, 16.5f, p); c.drawLine(14f, 10.5f, 14f, 16.5f, p) }
            Icon.TURBO, Icon.BOLT -> { path.moveTo(13.5f, 2.5f); path.lineTo(5.5f, 13.5f); path.lineTo(11.5f, 13.5f); path.lineTo(10.5f, 21.5f); path.lineTo(18.5f, 10.5f); path.lineTo(12.5f, 10.5f); path.close(); c.drawPath(path, p) }
            Icon.KNEAD -> { path.moveTo(4f, 15f); path.cubicTo(4f, 9f, 9f, 7f, 12f, 9f); path.cubicTo(15f, 7f, 20f, 9f, 20f, 15f); path.cubicTo(20f, 19f, 4f, 19f, 4f, 15f); c.drawPath(path, p); c.drawLine(9f, 12.5f, 10f, 14.5f, p); c.drawLine(14f, 12.5f, 15f, 14.5f, p) }
            Icon.STAR, Icon.STAR_FULL -> {
                for (i in 0 until 10) {
                    val a = Math.toRadians(-90.0 + i * 36.0); val r = if (i % 2 == 0) 9.5f else 4.2f
                    val px = 12f + (r * Math.cos(a)).toFloat(); val py = 12.6f + (r * Math.sin(a)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close(); c.drawPath(path, if (icon == Icon.STAR_FULL) fill else p); if (icon == Icon.STAR_FULL) c.drawPath(path, p)
            }
            // the mixer attachment: a ring of wire clipped onto the blade shaft
            Icon.WHISK -> { c.drawLine(12f, 3f, 12f, 12f, p); c.drawOval(RectF(4f, 12f, 20f, 19f), p)
                path.moveTo(5.5f, 15.5f); path.quadTo(8f, 13.8f, 10f, 15.5f); path.quadTo(12f, 17.2f, 14f, 15.5f); path.quadTo(16f, 13.8f, 18.5f, 15.5f); c.drawPath(path, p)
                c.drawLine(12f, 19f, 12f, 21f, p) }
            // the cooking pot: a basket with slots and a rim
            Icon.BASKET -> { c.drawLine(3f, 7f, 21f, 7f, p); path.moveTo(5f, 7f); path.lineTo(6.5f, 20f); path.lineTo(17.5f, 20f); path.lineTo(19f, 7f); c.drawPath(path, p)
                for (xx in listOf(9.5f, 12f, 14.5f)) c.drawLine(xx, 10.5f, xx, 16.5f, p) }
            // the measuring beaker
            Icon.BEAKER -> { path.moveTo(6f, 4f); path.lineTo(7.5f, 20f); path.lineTo(16.5f, 20f); path.lineTo(18f, 4f); c.drawPath(path, p)
                c.drawLine(4.5f, 4f, 19.5f, 4f, p); c.drawLine(8f, 9f, 11f, 9f, p); c.drawLine(8.3f, 12.5f, 11f, 12.5f, p); c.drawLine(8.6f, 16f, 11f, 16f, p) }
            // the steamer baskets on top of each other, with steam
            Icon.STEAMER -> { c.drawRoundRect(RectF(4f, 13f, 20f, 20f), 2f, 2f, p); c.drawRoundRect(RectF(5f, 9f, 19f, 13f), 1.5f, 1.5f, p)
                c.drawLine(7f, 9f, 17f, 9f, p); for (xx in listOf(9f, 15f)) { path.reset(); path.moveTo(xx, 6.5f); path.quadTo(xx - 1.5f, 5f, xx, 3.5f); c.drawPath(path, p) } }
            Icon.STEAM -> { for (xx in listOf(7f, 12f, 17f)) { path.reset(); path.moveTo(xx, 20f); path.cubicTo(xx - 3f, 16f, xx + 3f, 13f, xx, 9f); path.cubicTo(xx - 2f, 7f, xx + 1f, 5f, xx, 3.5f); c.drawPath(path, p) } }
            Icon.REFRESH -> { c.drawArc(RectF(4.5f, 4.5f, 19.5f, 19.5f), 30f, 290f, false, p); path.moveTo(19.5f, 4.5f); path.lineTo(19.2f, 9.3f); path.lineTo(14.5f, 8.8f); c.drawPath(path, p) }
            Icon.DOWN -> { path.moveTo(5f, 9f); path.lineTo(12f, 16f); path.lineTo(19f, 9f); c.drawPath(path, p) }
            Icon.CLOSE -> { c.drawLine(6f, 6f, 18f, 18f, p); c.drawLine(18f, 6f, 6f, 18f, p) }
            Icon.HAND -> { path.moveTo(7f, 13f); path.lineTo(7f, 6.5f); path.quadTo(7f, 5f, 8.5f, 5f); path.quadTo(10f, 5f, 10f, 6.5f); path.lineTo(10f, 11f)
                path.moveTo(10f, 5.5f); path.quadTo(10f, 4f, 11.5f, 4f); path.quadTo(13f, 4f, 13f, 5.5f); path.lineTo(13f, 11f)
                path.moveTo(13f, 6.5f); path.quadTo(13f, 5f, 14.5f, 5f); path.quadTo(16f, 5f, 16f, 6.5f); path.lineTo(16f, 12f)
                path.moveTo(16f, 9f); path.quadTo(16f, 7.5f, 17.5f, 7.5f); path.quadTo(19f, 7.5f, 19f, 9f); path.lineTo(19f, 14f)
                path.quadTo(19f, 20.5f, 13f, 20.5f); path.quadTo(9f, 20.5f, 7f, 17f); path.lineTo(4.8f, 13.2f); path.quadTo(4.2f, 11.6f, 5.6f, 11.4f); path.quadTo(6.5f, 11.4f, 7f, 13f)
                c.drawPath(path, p) }
            Icon.GLOBE -> { c.drawCircle(12f, 12f, 8.5f, p); c.drawOval(RectF(8f, 3.5f, 16f, 20.5f), p); c.drawLine(3.5f, 12f, 20.5f, 12f, p) }
            Icon.SUN -> { c.drawCircle(12f, 12f, 4f, p)
                for (i in 0 until 8) { val a = Math.PI / 4 * i; val cs = Math.cos(a).toFloat(); val sn = Math.sin(a).toFloat()
                    c.drawLine(12f + cs * 7f, 12f + sn * 7f, 12f + cs * 9.5f, 12f + sn * 9.5f, p) } }
            Icon.MOON -> { path.moveTo(19.5f, 14.5f); path.cubicTo(15f, 16.5f, 8.5f, 13f, 9.5f, 4.5f)
                path.cubicTo(4.5f, 6f, 2.8f, 12.5f, 6.5f, 17f); path.cubicTo(10f, 21f, 17f, 20.5f, 19.5f, 14.5f); path.close(); c.drawPath(path, p) }
            Icon.CHEF -> { path.moveTo(7f, 14f); path.cubicTo(3f, 13.5f, 3f, 7f, 8f, 7.5f); path.cubicTo(8.5f, 3f, 15.5f, 3f, 16f, 7.5f); path.cubicTo(21f, 7f, 21f, 13.5f, 17f, 14f); c.drawPath(path, p)
                c.drawRoundRect(RectF(7f, 14f, 17f, 20.5f), 1.5f, 1.5f, p) }
        }
        c.restore()
    }
}

/** A view that just draws one icon, tintable. */
class IconView(context: Context, var icon: Icon, var color: Int = T.TEXT, private val stroke: Float = 2f) : View(context) {
    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        Icons.draw(canvas, icon, (width - s) / 2, (height - s) / 2, s, color, stroke)
    }
    fun set(i: Icon, c: Int) { if (i != icon || c != color) { icon = i; color = c; invalidate() } }
}
