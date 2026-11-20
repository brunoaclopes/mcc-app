package mcc.machine

/** Temperature level <-> °C, from de.silpion.mc2 v292 cooking/Limits.java. Level 3 = 50 °C verified live. */
object TemperatureTable {
    val CELSIUS: List<Int> = listOf(0, 37, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, 105, 110, 115, 120, 125, 130)
    val MAX_LEVEL: Int = CELSIUS.size - 1

    fun celsius(level: Int): Int = CELSIUS[level.coerceIn(0, MAX_LEVEL)]

    /**
     * Highest level whose temperature does not exceed [celsius] (never rounds up, unlike the stock app,
     * which picks the first level >= the request). 0 °C or below -> level 0 (heater off).
     */
    fun levelAtMost(celsius: Int): Int = CELSIUS.indexOfLast { it <= celsius }.coerceAtLeast(0)
}
