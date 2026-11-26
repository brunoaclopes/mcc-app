package mcc.recipe

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** mcc-recipe/1, see recipe-repo/FORMAT.md. */
@Serializable
data class Recipe(
    val format: String,
    val id: String,
    val title: String,
    val language: String? = null,
    val servings: String? = null,
    val activeMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val difficulty: String? = null,
    val tags: List<String> = emptyList(),
    val image: String? = null,
    val source: Source? = null,
    val nutritionPerServing: Nutrition? = null,
    val ingredients: List<IngredientGroup> = emptyList(),
    val steps: List<Step>,
    val notes: List<String> = emptyList(),
) {
    companion object {
        const val FORMAT = "mcc-recipe/1"
        const val MAX_SECONDS = 99 * 60
        /** Long low-temperature segments (slow cooking, sous-vide, fermentation): see [isLongKind]. */
        const val MAX_LONG_SECONDS = 12 * 3600
        const val LONG_MAX_TEMP_C = 95
        /**
         * A run segment heated to 95 °C or less at speed 0 or 1 may last up to 12 h (FORMAT.md); the machine runs it
         * only with long programs switched on (SafetyLimits.maxLongRunMillis). Everything else: 99 min.
         */
        fun isLongKind(mode: Mode, g: Segment) = mode == Mode.RUN && g.tempC in 1..LONG_MAX_TEMP_C && g.speed <= 1
        fun maxSeconds(mode: Mode, g: Segment) = if (isLongKind(mode, g)) MAX_LONG_SECONDS else MAX_SECONDS
        val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type"; explicitNulls = false }

        /** Parses and validates; throws [IllegalArgumentException] with a readable message. */
        fun parse(text: String): Recipe {
            val r = try {
                json.decodeFromString(serializer(), text)
            } catch (e: kotlinx.serialization.SerializationException) {
                throw IllegalArgumentException("not a valid recipe: ${e.message}", e)
            }
            val problems = r.validate()
            require(problems.isEmpty()) { "${r.id}: ${problems.joinToString("; ")}" }
            return r
        }
    }

    fun validate(): List<String> {
        val p = ArrayList<String>()
        if (format != FORMAT) p += "format must be $FORMAT"
        if (id.isBlank() || !id.matches(Regex("[A-Za-z0-9._-]+"))) p += "bad id"
        if (title.isBlank()) p += "title missing"
        if (steps.isEmpty()) p += "no steps"
        steps.forEachIndexed { i, s ->
            when (s) {
                is Step.Instruction -> if (s.text.isBlank()) p += "step ${i + 1}: empty instruction"
                is Step.Weigh -> if (s.grams !in 1..5000) p += "step ${i + 1}: grams must be 1..5000"
                is Step.Machine -> {
                    if (s.segments.isEmpty()) p += "step ${i + 1}: no segments"
                    s.segments.forEach { g ->
                        val max = maxSeconds(s.mode, g)
                        if (g.seconds !in 1..max) p += "step ${i + 1}: seconds must be 1..$max"
                        if (g.speed !in 0..10) p += "step ${i + 1}: speed must be 0..10"
                        if (g.tempC !in 0..130) p += "step ${i + 1}: tempC must be 0..130"
                    }
                }
            }
        }
        return p
    }
}

@Serializable data class Source(val kind: String? = null, val ref: String? = null, val url: String? = null)

@Serializable data class Nutrition(val kcal: Int? = null, val proteinG: Int? = null, val carbsG: Int? = null, val fatG: Int? = null)

@Serializable data class IngredientGroup(val group: String? = null, val items: List<Ingredient> = emptyList())

@Serializable data class Ingredient(val text: String)

@Serializable
sealed class Step {
    abstract val text: String

    @Serializable @SerialName("instruction")
    data class Instruction(override val text: String) : Step()

    /**
     * [measure]: the amount is up to the cook ([grams] is then the most it expects). The scale measures what goes in,
     * and a generated program (the automatic soup) works out the following steps from that weight.
     */
    @Serializable @SerialName("weigh")
    data class Weigh(override val text: String = "", val grams: Int, val measure: Boolean = false) : Step()

    @Serializable @SerialName("machine")
    data class Machine(
        override val text: String = "",
        // always written: FORMAT.md requires it, and the encoder would leave out the default (RUN) otherwise
        @OptIn(ExperimentalSerializationApi::class) @EncodeDefault val mode: Mode = Mode.RUN,
        val segments: List<Segment>,
    ) : Step()
}

@Serializable
enum class Mode {
    @SerialName("run") RUN,
    @SerialName("turbo") TURBO,
    @SerialName("knead") KNEAD,
    @SerialName("roast") ROAST,
    @SerialName("steam") STEAM,
}

@Serializable
data class Segment(val seconds: Int, val speed: Int = 0, val tempC: Int = 0, val reverse: Boolean = false)

/** mcc-index/1 */
@Serializable
data class RecipeIndex(val format: String, val generated: String? = null, val recipes: List<Entry> = emptyList()) {
    @Serializable
    data class Entry(
        val id: String,
        val title: String,
        val language: String? = null,
        val tags: List<String> = emptyList(),
        val totalMinutes: Int? = null,
        val image: String? = null,
        val file: String,
        val sha256: String? = null,
        /** BlurHash of the photo (FORMAT.md), painted blurred while the photo loads; absent without a photo. */
        val blurhash: String? = null,
    )

    companion object {
        fun parse(text: String): RecipeIndex = Recipe.json.decodeFromString(serializer(), text)
    }
}
