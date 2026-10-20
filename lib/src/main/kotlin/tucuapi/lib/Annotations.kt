package tucuapi.lib

annotation class Model(val name: String)

annotation class ModelEnum(val name: String)

annotation class ModelEnumVariant(val value: String, val label: String)

annotation class PrimaryKey

annotation class InputOmit

annotation class VarcharConfig(val size: Int = 255)

annotation class Unique

annotation class DateTime(val timezoneOffset: Boolean = false)

annotation class InputMultiselect

annotation class Text