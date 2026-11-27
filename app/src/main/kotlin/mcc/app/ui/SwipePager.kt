package mcc.app.ui

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Pages side by side, swiped horizontally (or switched with [show], e.g. from tabs). Vertical scrolling inside a page
 * keeps working: a drag only becomes a swipe when it is clearly sideways. [onPage] is told the page after each change.
 */
class SwipePager(context: Context, private val pages: List<View>, private val onPage: (Int) -> Unit) : FrameLayout(context) {
    var page = 0
        private set
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val fling = ViewConfiguration.get(context).scaledMinimumFlingVelocity * 4f
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var tracker: VelocityTracker? = null

    init { pages.forEach { addView(it, LayoutParams(-1, -1)) } }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        place(0f)
    }

    private fun place(drag: Float) = pages.forEachIndexed { i, v -> v.translationX = (i - page) * width + drag }

    fun show(p: Int, animate: Boolean = true) {
        page = p.coerceIn(0, pages.lastIndex)
        onPage(page)
        pages.forEachIndexed { i, v ->
            val x = ((i - page) * width).toFloat()
            v.animate().cancel()
            if (animate && width > 0) v.animate().translationX(x).setDuration(260).setInterpolator(DecelerateInterpolator()).start() else v.translationX = x
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x; downY = e.y; dragging = false
                tracker?.recycle(); tracker = VelocityTracker.obtain().also { it.addMovement(e) }
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(e)
                val dx = e.x - downX; val dy = e.y - downY
                if (!dragging && abs(dx) > slop && abs(dx) > abs(dy) * 1.5f) { dragging = true; parent?.requestDisallowInterceptTouchEvent(true) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (!dragging) { tracker?.recycle(); tracker = null }
        }
        return dragging
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        tracker?.addMovement(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                var dx = e.x - downX
                // past the first or last page it only gives a little
                if ((page == 0 && dx > 0) || (page == pages.lastIndex && dx < 0)) dx *= 0.3f
                pages.forEach { it.animate().cancel() }
                place(dx)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = e.x - downX
                tracker?.computeCurrentVelocity(1000)
                val vx = tracker?.xVelocity ?: 0f
                show(when {
                    dx < -width / 4f || vx < -fling -> page + 1
                    dx > width / 4f || vx > fling -> page - 1
                    else -> page
                })
                tracker?.recycle(); tracker = null; dragging = false
            }
        }
        return true
    }
}
