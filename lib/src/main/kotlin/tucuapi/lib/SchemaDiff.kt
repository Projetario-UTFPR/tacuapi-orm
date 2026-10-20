package tucuapi.lib

/**
 * Compara o esquema desejado (gerado a partir da classe do modelo) com o
 * esquema atual do banco e gera os comandos `alter table` quetransformam um
 * no outro.
 *
 * É puro: não conhece o banco de dados, apenas manipula [TableSchema].
 */
internal object SchemaDiff {

    /**
     * Gera os comandos que transformam [actual] em [desired].
     *
     * @param allowDestructive quando `false`, colunas e restrições que existem no
     * banco mas não são mais exigidas pela classe são preservadas.
     */
    fun diff(desired: TableSchema, actual: TableSchema, allowDestructive: Boolean = true): List<String> {
        val removals = mutableListOf<String>()
        val changes = mutableListOf<String>()

        val desiredColumns = desired.columns.associateBy { it.name }
        val actualColumns = actual.columns.associateBy { it.name }

        // --- remoções (antes das alterações, para não travar em dependências) ---
        removals += removeForeignKeys(desired, actual, allowDestructive)
        removals += removePrimaryKey(desired, actual)
        removals += removeUniques(desired, actual, allowDestructive)
        if (allowDestructive) {
            actual.columns.filter { it.name !in desiredColumns }.forEach { column ->
                removals += "alter table ${desired.name} drop column ${column.name}"
            }
        }

        // --- alterações e inclusões ---
        desired.columns.filter { it.name !in actualColumns }.forEach { column ->
            changes += addColumn(desired.name, column)
        }
        desired.columns.filter { it.name in actualColumns }.forEach { column ->
            changes += changeColumn(desired.name, actualColumns.getValue(column.name), column)
        }
        changes += addPrimaryKey(desired, actual)
        changes += addUniques(desired, actual)
        changes += addForeignKeys(desired, actual)

        return removals + changes
    }

    // ===============================================================
    // colunas
    // ===============================================================

    private fun addColumn(table: String, column: ColumnSchema): String = buildString {
        append("alter table $table add column ${column.name} ${column.type}")
        if (column.identity) append(" generated always as identity")
        if (!column.nullable) append(" not null")
        column.default?.let { append(" default $it") }
    }

    private fun changeColumn(table: String, before: ColumnSchema, after: ColumnSchema): List<String> {
        val statements = mutableListOf<String>()

        if (!sameType(before.type, after.type)) {
            statements += "alter table $table alter column ${after.name} type ${after.type} using ${after.name}::${after.type}"
        }

        if (before.nullable != after.nullable) {
            statements += if (after.nullable) {
                "alter table $table alter column ${after.name} drop not null"
            } else {
                "alter table $table alter column ${after.name} set not null"
            }
        }

        // colunas `serial` já têm um `default` gerado pelo próprio banco: não é
        // a mesma coisa que um `default` declarado na classe.
        val serial = before.default?.startsWith("nextval(") == true
        if (!serial && !sameDefault(before.default, after.default)) {
            statements += if (after.default == null) {
                "alter table $table alter column ${after.name} drop default"
            } else {
                "alter table $table alter column ${after.name} set default ${after.default}"
            }
        }

        if (after.identity && !before.identity && !serial) {
            statements += "alter table $table alter column ${after.name} add generated always as identity"
        } else if (!after.identity && before.identity) {
            statements += "alter table $table alter column ${after.name} drop identity"
        }

        return statements
    }

    // ===============================================================
    // chaves primárias
    // ===============================================================

    private fun removePrimaryKey(desired: TableSchema, actual: TableSchema): List<String> {
        val current = actual.primaryKey ?: return emptyList()
        val wanted = desired.primaryKey
        if (wanted != null && wanted.columns == current.columns) return emptyList()
        return listOf("alter table ${desired.name} drop constraint ${current.name}")
    }

    private fun addPrimaryKey(desired: TableSchema, actual: TableSchema): List<String> {
        val wanted = desired.primaryKey ?: return emptyList()
        val current = actual.primaryKey
        if (current != null && current.columns == wanted.columns) return emptyList()
        return listOf(
            "alter table ${desired.name} add constraint ${wanted.name} primary key (${wanted.columns.joinToString()})"
        )
    }

    // ===============================================================
    // `unique`
    // ===============================================================

    private fun removeUniques(desired: TableSchema, actual: TableSchema, allowDestructive: Boolean): List<String> {
        if (!allowDestructive) return emptyList()
        return actual.uniqueConstraints
            .filter { constraint -> desired.uniqueConstraints.none { it.columns == constraint.columns } }
            .map { constraint -> "alter table ${desired.name} drop constraint ${constraint.name}" }
    }

    private fun addUniques(desired: TableSchema, actual: TableSchema): List<String> =
        desired.uniqueConstraints
            .filter { wanted -> actual.uniqueConstraints.none { it.columns == wanted.columns } }
            .map { wanted ->
                "alter table ${desired.name} add constraint ${wanted.name} unique (${wanted.columns.joinToString()})"
            }

    // ===============================================================
    // chaves estrangeiras
    // ===============================================================

    private fun removeForeignKeys(desired: TableSchema, actual: TableSchema, allowDestructive: Boolean): List<String> {
        if (!allowDestructive) return emptyList()
        return actual.foreignKeys
            .filter { constraint -> desired.foreignKeys.none { sameForeignKey(it, constraint) } }
            .map { constraint -> "alter table ${desired.name} drop constraint ${constraint.name}" }
    }

    private fun addForeignKeys(desired: TableSchema, actual: TableSchema): List<String> =
        desired.foreignKeys
            .filter { wanted -> actual.foreignKeys.none { sameForeignKey(it, wanted) } }
            .map { wanted ->
                buildString {
                    append("alter table ${desired.name} add constraint ${wanted.name} foreign key (${wanted.columns.joinToString()})")
                    append(" references ${wanted.referencedTable} (${wanted.referencedColumns.joinToString()})")
                }
            }

    private fun sameForeignKey(one: ConstraintSchema, other: ConstraintSchema): Boolean =
        one.columns == other.columns &&
            one.referencedTable == other.referencedTable &&
            one.referencedColumns == other.referencedColumns

    // ===============================================================
    // comparações
    // ===============================================================

    private val typeAliases = mapOf(
        "character varying" to "varchar",
        "character" to "char",
        "timestamp with time zone" to "timestamptz",
        "timestamp without time zone" to "timestamp",
        "time with time zone" to "timetz",
        "time without time zone" to "time",
        "bit varying" to "varbit",
        "double precision" to "double precision"
    )

    /**
     * Compara dois tipos SQL, normalizando as diferenças de nomenclatura entre
     * o que a biblioteca gera e o que o PostgreSQL devolve.
     */
    private fun sameType(one: String, other: String): Boolean = normalizeType(one) == normalizeType(other)

    private fun normalizeType(type: String): String {
        var normalized = type.trim().lowercase()
        normalized = when {
            normalized.startsWith("timestamp with time zone") -> normalized.replace("timestamp with time zone", "timestamptz")
            normalized.startsWith("timestamp without time zone") -> normalized.replace("timestamp without time zone", "timestamp")
            normalized.startsWith("time with time zone") -> normalized.replace("time with time zone", "timetz")
            normalized.startsWith("time without time zone") -> normalized.replace("time without time zone", "time")
            else -> normalized
        }

        val base = normalized.substringBefore("(").trim()
        val modifier = normalized.substringAfter("(", "").takeIf { it.isNotEmpty() }?.let { "($it)" } ?: ""
        val alias = typeAliases[base] ?: base
        return alias + modifier
    }

    /**
     * Compara dois valores padrão, ignorando as diferenças de sintaxe entre
     * o que foi declarado na classe e o que o PostgreSQL armazena.
     */
    private fun sameDefault(one: String?, other: String?): Boolean {
        if (one == null && other == null) return true
        if (one == null || other == null) return false
        return normalizeDefault(one) == normalizeDefault(other)
    }

    private fun normalizeDefault(value: String): String {
        var normalized = value.trim().lowercase()
        normalized = normalized.replace(Regex("::[a-z_ ]+(\\(\\d+(,\\s*\\d+)?\\))?"), "")  // casts: `'x'::text`
        normalized = normalized.replace("'", "")                                          // aspas
        while (normalized.startsWith("(") && normalized.endsWith(")")) {
            normalized = normalized.substring(1, normalized.length - 1).trim()
        }
        return normalized
    }
}
