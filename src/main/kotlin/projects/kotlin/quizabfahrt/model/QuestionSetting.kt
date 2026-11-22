package projects.kotlin.quizabfahrt.model

class SettingException(val setting: QuestionSetting<*>, message: String) : RuntimeException(message)

sealed class QuestionSetting<T : Any>(val key: String, val label: String, private val default: T?) {

    open val hint: String? = null
    open val options: List<KeyedType> = emptyList()
    open val numberRange: IntRange? = null

    fun isRequired() = default == null

    fun hasOptions() = options.isNotEmpty()

    fun isNumber() = numberRange != null

    fun getMin() = numberRange?.first

    fun getMax() = numberRange?.last

    protected abstract fun parse(value: String): T?

    protected abstract fun describe(): String

    open fun format(value: T): String = value.toString()

    fun read(raw: String?): T {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
            ?: return default ?: throw SettingException(this, "„$label“ fehlt")
        return parse(value) ?: throw SettingException(this, "„$label“ muss ${describe()} sein, gefunden: '$value'")
    }

    sealed class NumberSetting(key: String, label: String, default: Int?, override val numberRange: IntRange) :
        QuestionSetting<Int>(key, label, default) {
        override fun parse(value: String) = value.toIntOrNull()?.takeIf { it in numberRange }
        override fun describe() = "eine ganze Zahl zwischen ${numberRange.first} und ${numberRange.last}"
    }

    sealed class ChoiceSetting<E>(key: String, label: String, default: E, override val options: List<E>) :
        QuestionSetting<E>(key, label, default) where E : Enum<E>, E : KeyedType {
        override fun parse(value: String) = options.firstOrNull { it.key == value }
        override fun describe() = "eins von ${options.joinToString { it.key }}"
        override fun format(value: E) = value.key
    }

    data object Points : NumberSetting("points", "Punkte", default = null, 0..100_000) {
        override val hint = "Eine falsche Antwort zieht die Hälfte ab."
    }

    data object Input : ChoiceSetting<EInputTypes>("input", "Eingabe der Teilnehmer", EInputTypes.DEFAULT, EInputTypes.entries)

    companion object {
        // Lazy, because the objects aren't initialised yet while this companion is
        val entries: List<QuestionSetting<*>> by lazy { listOf(Points, Input) }
    }
}

@ConsistentCopyVisibility
data class QuestionSettings private constructor(private val values: Map<QuestionSetting<*>, Any>) {

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(setting: QuestionSetting<T>): T = values.getValue(setting) as T

    fun getFormatted(): Map<String, String> = QuestionSetting.entries.associate { it.key to format(it) }

    private fun <T : Any> format(setting: QuestionSetting<T>): String = setting.format(get(setting))

    companion object {
        fun of(raw: Map<String, String?>) = QuestionSettings(QuestionSetting.entries.associateWith { it.read(raw[it.key]) })
    }
}
