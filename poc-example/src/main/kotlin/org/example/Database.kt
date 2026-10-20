package org.example

import tucuapi.lib.InferredValue
import tucuapi.lib.Tucuapi
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.reflect.KClass

/**
 * Conexão com o PostgreSQL da PoC.
 *
 * Tudo aqui é _hardcoded_ de propósito: o objetivo da PoC é mostrar o caminho
 * completo (modelo -> SQL -> banco -> formulário HTML) sem complicate a
 * configuração. As variáveis de ambiente só existem para permitir que o mesmo
 * jar seja usado fora do Docker.
 */
object Database {
    private const val HOST = "localhost"
    private const val PORT = 5432
    private const val NAME = "tacuapi"
    private const val USER = "postgres"
    private const val PASSWORD = "postgres"

    /** Identificador da migração: garante que o esquema seja criado uma única vez. */
    private const val MIGRATION_ID = "setup"

    private val url: String
        get() = System.getenv("TACUAPI_DB_URL") ?: "jdbc:postgresql://$HOST:$PORT/$NAME"

    private val user: String
        get() = System.getenv("TACUAPI_DB_USER") ?: USER

    private val password: String
        get() = System.getenv("TACUAPI_DB_PASSWORD") ?: PASSWORD

    /**
     * Abre uma conexão com o banco, aguardando o PostgreSQL ficar pronto
     * (o banco pode estar subindo junto com a aplicação no Docker).
     */
    fun connect(attempts: Int = 30): Connection {
        var lastError: SQLException? = null
        repeat(attempts) {
            try {
                return DriverManager.getConnection(url, user, password)
            } catch (error: SQLException) {
                lastError = error
                println("[db] Banco indisponível (${error.message}), tentando de novo...")
                Thread.sleep(1000)
            }
        }
        throw lastError ?: SQLException("Não foi possível conectar ao banco.")
    }

    /**
     * Roda a migração — isto é, mantém o banco em dia com as classes.
     *
     * Na primeira execução, o esquema é criado a partir dos modelos. Depois
     * disso, a cada execução o esquema do banco é comparado com as classes e
     * as diferenças viram comandos `alter table`.
     */
    fun migrate(vararg models: KClass<*>) {
        connect().use { connection ->
            createMigrationsTable(connection)

            if (isMigrationApplied(connection)) {
                println("[db] Esquema já criado anteriormente.")
            } else {
                println("[db] Criando o esquema:")
                Tucuapi.generateSqlSetupStatements(*models).forEach { statement ->
                    println("[db] $statement")
                    connection.createStatement().use { it.execute(statement) }
                }
                markMigrationApplied(connection)
            }

            // O esquema é conferido a cada execução: mudanças na classe viram `alter table`.
            models.forEach { model -> updateSchema(connection, model) }
        }
    }

    /**
     * Compara a classe com o que existe no banco e aplica as diferenças.
     */
    private fun updateSchema(connection: Connection, model: KClass<*>) {
        val statements = Tucuapi.generateSqlUpdateStatements(connection, model)
        if (statements.isEmpty()) {
            println("[db] O esquema de ${model.simpleName} já está em dia com a classe.")
            return
        }

        println("[db] Atualizando o esquema de ${model.simpleName} conforme a classe:")
        statements.forEach { statement ->
            println("[db] $statement")
            connection.createStatement().use { it.execute(statement) }
        }
    }

    /**
     * Insere uma instância do modelo no banco.
     *
     * A linha principal e as linhas das tabelas intermediárias (`@Multivalued`)
     * são inseridas na mesma transação: ou entra tudo, ou nada entra.
     */
    fun insert(model: KClass<*>, instance: Any) {
        val instructions = Tucuapi.generateSqlInsertStatements(model, instance)

        connect().use { connection ->
            connection.autoCommit = false
            try {
                instructions.forEach { instruction ->
                    connection.prepareStatement(instruction.sql).use { statement ->
                        instruction.values.forEachIndexed { index, value ->
                            when (value) {
                                null -> statement.setNull(index + 1, java.sql.Types.NULL)
                                // o driver JDBC não conhece os enums: quem converte é o banco
                                is InferredValue -> statement.setObject(index + 1, value.value, java.sql.Types.OTHER)
                                else -> statement.setObject(index + 1, value)
                            }
                        }
                        statement.executeUpdate()
                    }
                }
                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            }
        }
    }

    private fun createMigrationsTable(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                create table if not exists tucuapi_migrations (
                  id varchar(255) primary key,
                  applied_at timestamptz not null default now()
                )
                """.trimIndent()
            )
        }
    }

    private fun isMigrationApplied(connection: Connection): Boolean =
        connection.prepareStatement("select count(*) from tucuapi_migrations where id = ?").use { statement ->
            statement.setString(1, MIGRATION_ID)
            statement.executeQuery().use { result ->
                result.next() && result.getInt(1) > 0
            }
        }

    private fun markMigrationApplied(connection: Connection) {
        connection.prepareStatement("insert into tucuapi_migrations (id) values (?)").use { statement ->
            statement.setString(1, MIGRATION_ID)
            statement.executeUpdate()
        }
    }
}
