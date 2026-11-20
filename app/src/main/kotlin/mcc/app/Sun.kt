package mcc.app

import java.util.Calendar
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise and sunset (NOAA approximation, about a minute of error) for the Auto day/night look. The tablet has
 * no GPS or location service: the position comes from Settings ([where]); without one it is estimated from the
 * time zone (longitude = UTC offset x 15 degrees, latitude 45 N), which is usually within half an hour.
 */
object Sun {
    /** Set by the activity from Prefs.latLon. */
    var where: Pair<Double, Double>? = null

    private fun position(cal: Calendar): Pair<Double, Double> =
        where ?: (45.0 to cal.timeZone.rawOffset / 3_600_000.0 * 15.0)

    /** Minutes after local midnight of sunrise and sunset for the day of [cal]. */
    fun times(cal: Calendar = Calendar.getInstance()): Pair<Int, Int> {
        val (lat, lon) = position(cal)
        val day = cal.get(Calendar.DAY_OF_YEAR)
        val tzMin = (cal.timeZone.getOffset(cal.timeInMillis) / 60_000)
        val g = 2 * Math.PI / 365 * (day - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) -
            0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val latR = Math.toRadians(lat)
        val cosHa = (cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)).coerceIn(-1.0, 1.0)
        val ha = Math.toDegrees(acos(cosHa))
        val noon = 720 - 4 * lon - eqTime + tzMin
        return (noon - 4 * ha).toInt() to (noon + 4 * ha).toInt()
    }

    fun isDay(cal: Calendar = Calendar.getInstance()): Boolean {
        val (rise, set) = times(cal)
        val now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return now in rise until set
    }

    fun fmt(min: Int) = "%d:%02d".format(min / 60, min % 60)
}
