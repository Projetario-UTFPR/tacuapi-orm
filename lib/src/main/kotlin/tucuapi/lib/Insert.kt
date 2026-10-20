package tucuapi.lib

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlin.reflect.KClass
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Uma instrução de inserção.
 *
 * @param table a tabela alvo
 * @param sql o comando, com um `?` no lugar de cada valor
 * @param values os valores, na mesma ordem dos `?`
 */
data class InsertInstruction(
    val table: String,
    val sql: String,
    val values: List<Any?>
)

/**
 * Valor cujo tipo SQL é definido pelo banco a partir da coluna de destino.
 *
 * O driver JDBC não conhece os _enums_ do PostgreSQL, então o valor é enviado
 * sem tipo definido — é o PostgreSQL que o converte para o tipo da coluna.
 *
 * @param value o valor do _enum_ (a variante, não o nome da constante)
 */
data class InferredValue(val value: String)

/**
 * Conversões entre os valores dos campos (Kotlin) e os valores aceitos pelo
 * driver JDBC, e vice-versa.
 */
internal object Insert {

    /**
     * Converte o valor bruto de um formulário para o tipo do campo.
     */
    fun convert(raw: String, type: KClass<*>): Any? = when (type) {
        String::class -> raw
        Int::class -> raw.trim().toInt()
        Long::class -> raw.trim().toLong()
        Double::class -> raw.trim().toDouble()
        Boolean::class -> raw.trim().let {
            it.equals("on", ignoreCase = true) || it.equals("true", ignoreCase = true) || it == "1"
        }
        LocalDate::class -> LocalDate.parse(raw.trim())
        LocalDateTime::class -> LocalDateTime.parse(raw.trim())
        Instant::class -> parseInstant(raw.trim())
        else -> constantOf(type, raw.trim())
    }

    /**
     * O `input` de data/hora do HTML não tem fuso horário: nesse caso, assume UTC.
     */
    private fun parseInstant(raw: String): Instant = try {
        Instant.parse(raw)
    } catch (_: IllegalArgumentException) {
        val local = LocalDateTime.parse(raw).toJavaLocalDateTime()
        Instant.fromEpochSeconds(local.toEpochSecond(java.time.ZoneOffset.UTC), local.nano.toLong())
    }

    /**
     * Converte o valor de um campo para o valor aceito pelo driver JDBC.
     */
    fun toJdbcValue(value: Any?): Any? = when (value) {
        null -> null
        is Instant -> java.time.Instant
            .ofEpochSecond(value.epochSeconds, value.nanosecondsOfSecond.toLong())
            .atOffset(java.time.ZoneOffset.UTC)
        is LocalDateTime -> value.toJavaLocalDateTime()
        is LocalDate -> value.toJavaLocalDate()
        is Uuid -> java.util.UUID.fromString(value.toString())
        is Enum<*> -> InferredValue(variantOf(value).value)
        else -> value
    }

    /**
     * A variante de um enum do modelo — o valor que fica guardado no banco.
     */
    fun variantOf(constant: Enum<*>): Variant {
        val annotation = constant.declaringJavaClass
            .getField(constant.name)
            .getAnnotation(ModelEnumVariant::class.java)

        return Variant(
            value = annotation?.value?.ifBlank { null } ?: constant.name,
            label = annotation?.label?.ifBlank { null } ?: constant.name
        )
    }

    /**
     * A constante de um enum do modelo a partir do valor guardado no banco.
     */
    @Suppress("UNCHECKED_CAST")
    fun constantOf(enum: KClass<*>, value: String): Enum<*> {
        val constants = enum.java.enumConstants as Array<out Enum<*>>
        return constants.firstOrNull { variantOf(it).value == value }
            ?: error("Valor inválido para o enum ${enum.simpleName}: '$value'")
    }
}
