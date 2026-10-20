package tucuapi.lib

import java.sql.Connection

/**
 * Lê o esquema atual das tabelas no PostgreSQL.
 *
 * Serve de entrada para o motor de comparação: com o esquema do banco em mãos,
 * é possível dizer o que mudou na classe e gerar os `alter table` necessários.
 */
object PostgresSchema {

    /**
     * Lê o esquema da tabela [tableName].
     *
     * @return o esquema atual da tabela ou `null`, caso a tabela não exista.
     */
    fun readTableSchema(connection: Connection, tableName: String): TableSchema? {
        val columns = readColumns(connection, tableName)
        if (columns.isEmpty()) return null

        val constraints = readConstraints(connection, tableName)
        return TableSchema(
            name = tableName,
            columns = columns,
            primaryKey = constraints.firstOrNull { it.type == 'p' }?.toSchema(),
            uniqueConstraints = constraints.filter { it.type == 'u' }.map { it.toSchema() },
            foreignKeys = constraints.filter { it.type == 'f' }.map { it.toSchema() }
        )
    }

    private fun readColumns(connection: Connection, tableName: String): List<ColumnSchema> {
        val sql = """
            select a.attname,
                   pg_catalog.format_type(a.atttypid, a.atttypmod),
                   a.attnotnull,
                   pg_catalog.pg_get_expr(d.adbin, d.adrelid),
                   (a.attidentity <> '') as is_identity
            from pg_catalog.pg_attribute a
            join pg_catalog.pg_class c on c.oid = a.attrelid
            join pg_catalog.pg_namespace n on n.oid = c.relnamespace
            left join pg_catalog.pg_attrdef d on d.adrelid = a.attrelid and d.adnum = a.attnum
            where n.nspname = 'public' and c.relname = ? and a.attnum > 0 and not a.attisdropped
            order by a.attnum
        """.trimIndent()

        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, tableName)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            ColumnSchema(
                                name = result.getString(1),
                                type = result.getString(2),
                                nullable = !result.getBoolean(3),
                                default = result.getString(4),
                                identity = result.getBoolean(5)
                            )
                        )
                    }
                }
            }
        }
    }

    private fun readConstraints(connection: Connection, tableName: String): List<RawConstraint> {
        val sql = """
            select con.conname,
                   con.contype,
                   (select string_agg(att.attname, ',' order by k.ord)
                      from unnest(con.conkey) with ordinality as k(attnum, ord)
                      join pg_catalog.pg_attribute att
                        on att.attrelid = con.conrelid and att.attnum = k.attnum),
                   case when con.contype = 'f' then con.confrelid::regclass::text end,
                   (select string_agg(att.attname, ',' order by k.ord)
                      from unnest(con.confkey) with ordinality as k(attnum, ord)
                      join pg_catalog.pg_attribute att
                        on att.attrelid = con.confrelid and att.attnum = k.attnum)
            from pg_catalog.pg_constraint con
            join pg_catalog.pg_class c on c.oid = con.conrelid
            join pg_catalog.pg_namespace n on n.oid = c.relnamespace
            where n.nspname = 'public' and c.relname = ?
            order by con.conname
        """.trimIndent()

        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, tableName)
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            RawConstraint(
                                name = result.getString(1),
                                type = result.getString(2).first(),
                                columns = result.getString(3)?.split(",").orEmpty(),
                                referencedTable = result.getString(4),
                                referencedColumns = result.getString(5)?.split(",").orEmpty()
                            )
                        )
                    }
                }
            }
        }
    }

    private data class RawConstraint(
        val name: String,
        val type: Char,
        val columns: List<String>,
        val referencedTable: String?,
        val referencedColumns: List<String>
    ) {
        fun toSchema() = ConstraintSchema(
            name = name,
            columns = columns,
            referencedTable = referencedTable,
            referencedColumns = referencedColumns
        )
    }
}
