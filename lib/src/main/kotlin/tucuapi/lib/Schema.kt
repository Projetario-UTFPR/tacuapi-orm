package tucuapi.lib

/**
 * Descrição de uma coluna do banco de dados.
 *
 * @param name nome da coluna
 * @param type tipo SQL da coluna (ex.: `varchar(200)`, `integer`, `user_role_e`)
 * @param nullable se a coluna aceita nulos
 * @param default valor padrão da coluna, se houver
 * @param identity se a coluna é gerada automaticamente (`generated always as identity`)
 */
data class ColumnSchema(
    val name: String,
    val type: String,
    val nullable: Boolean = true,
    val default: String? = null,
    val identity: Boolean = false
)

/**
 * Descrição de uma restrição de tabela (chave primária, `unique` ou chave estrangeira).
 *
 * @param name nome da restrição
 * @param columns colunas envolvidas, na ordem
 * @param referencedTable tabela referenciada (apenas em chaves estrangeiras)
 * @param referencedColumns colunas referenciadas (apenas em chaves estrangeiras)
 */
data class ConstraintSchema(
    val name: String,
    val columns: List<String>,
    val referencedTable: String? = null,
    val referencedColumns: List<String> = emptyList()
)

/**
 * Descrição de uma tabela do banco de dados.
 */
data class TableSchema(
    val name: String,
    val columns: List<ColumnSchema>,
    val primaryKey: ConstraintSchema? = null,
    val uniqueConstraints: List<ConstraintSchema> = emptyList(),
    val foreignKeys: List<ConstraintSchema> = emptyList()
)
