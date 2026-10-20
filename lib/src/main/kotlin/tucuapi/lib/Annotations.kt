package tucuapi.lib

@Target(AnnotationTarget.CLASS)
annotation class Model(val table: String = "", val title: String = "")

@Target(AnnotationTarget.PROPERTY)
annotation class InputField(
    val label: String = "",
    val placeholder: String = "",
    val footnote: String = "",
    val type: String = ""
    )

@Target(AnnotationTarget.CLASS)
annotation class ModelEnum(val name: String = "")

// cannot have @Target so that it fits enum variants fields
annotation class ModelEnumVariant(val value: String = "", val label: String = "")

@Target(AnnotationTarget.PROPERTY) annotation class PrimaryKey(val autoIncrease: Boolean = false)

@Target(AnnotationTarget.PROPERTY) annotation class InputOmit

@Target(AnnotationTarget.PROPERTY) annotation class VarcharConfig(val size: Int = 255)

@Target(AnnotationTarget.PROPERTY) annotation class Column(
    val unique: Boolean = false,
    val name: String = "",
    val type: String = "",
    )

@Target(AnnotationTarget.PROPERTY) annotation class DateTime(val timezoneOffset: Boolean = false)

@Target(AnnotationTarget.PROPERTY) annotation class Multivalued

@Target(AnnotationTarget.PROPERTY) annotation class Text