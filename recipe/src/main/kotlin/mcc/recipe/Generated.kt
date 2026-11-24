package mcc.recipe

/**
 * Programs that are worked out from a weighed amount (`source.kind` "program", `source.ref` naming the generator).
 * The runner asks here when a measuring weigh step ends.
 */
object Generated {
    /** Grams the measuring step accepts, or null when [recipe] is not generated. */
    fun range(recipe: Recipe): IntRange? = when {
        Soup.isSoup(recipe) -> Soup.MIN_VEG..Soup.MAX_VEG
        else -> null
    }

    /** [recipe] worked out again for [grams], with the same steps; null when it is not generated. */
    fun measured(recipe: Recipe, grams: Int): Recipe? = when {
        Soup.isSoup(recipe) -> Soup.measured(recipe, grams)
        else -> null
    }
}
