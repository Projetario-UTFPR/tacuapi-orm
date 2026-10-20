package tucuapi.lib

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Modelo usado nos testes: a "versão antiga" e a "versão nova" da classe são
 * representadas pelo esquema atual montados à mão.
 */
@Model(title = "Usuário", table = "users")
class TestUser {
    @PrimaryKey(autoIncrease = true)
    val id: Int = 0

    val name: String

    @Column(unique = true)
    val email: String

    val age: Int?

    constructor(name: String, email: String, age: Int?) {
        this.name = name
        this.email = email
        this.age = age
    }
}

/**
 * Modelo com campo multivalorado: gera, além da tabela principal, uma tabela
 * intermediária.
 */
@ModelEnum(name = "test_user_role_e")
enum class TestRole {
    ADMIN,
    USER
}

@Model(title = "Usuário", table = "users")
class WithCapabilities {
    @PrimaryKey
    val id: Int

    @Multivalued
    val capabilities: List<TestRole>

    constructor(id: Int, capabilities: List<TestRole>) {
        this.id = id
        this.capabilities = capabilities
    }
}

class SchemaTest {
    @Test
    fun `descreve o esquema exigido pela classe`() {
        val schema = Tucuapi.describeSchemas(TestUser::class).single()

        assertEquals("users", schema.name)
        assertEquals(listOf("id", "name", "email", "age"), schema.columns.map { it.name })
        assertEquals("varchar(255)", schema.columns.single { it.name == "name" }.type)
        assertEquals("integer", schema.columns.single { it.name == "age" }.type)
        assertTrue(schema.columns.single { it.name == "age" }.nullable)
        assertFalse(schema.columns.single { it.name == "id" }.nullable)
        assertTrue(schema.columns.single { it.name == "id" }.identity)

        assertEquals(listOf("id"), schema.primaryKey?.columns)
        assertEquals(listOf("users_email_key"), schema.uniqueConstraints.map { it.name })
    }

    @Test
    fun `nao gera nada quando o banco ja esta em dia com a classe`() {
        val schema = Tucuapi.describeSchemas(TestUser::class)

        assertEquals(emptyList(), Tucuapi.generateSqlUpdateStatements(TestUser::class, schema))
    }

    @Test
    fun `gera o alter das colunas que entraram na classe`() {
        val current = listOf(
            TableSchema(
                name = "users",
                columns = listOf(ColumnSchema("name", "varchar(255)", nullable = false))
            )
        )

        val statements = Tucuapi.generateSqlUpdateStatements(TestUser::class, current)

        assertEquals(
            listOf(
                "alter table users add column id integer generated always as identity not null",
                "alter table users add column email varchar(255) not null",
                "alter table users add column age integer",
                "alter table users add constraint users_pkey primary key (id)",
                "alter table users add constraint users_email_key unique (email)"
            ),
            statements
        )
    }

    @Test
    fun `gera o alter das colunas que sairam da classe`() {
        val current = listOf(
            TableSchema(
                name = "users",
                columns = listOf(
                    ColumnSchema("name", "varchar(255)", nullable = false),
                    ColumnSchema("nickname", "varchar(255)", nullable = true)
                )
            )
        )

        val statements = Tucuapi.generateSqlUpdateStatements(TestUser::class, current)

        assertTrue(statements.contains("alter table users drop column nickname"))
    }

    @Test
    fun `nao remove nada quando as operacoes destrutivas sao proibidas`() {
        val current = listOf(
            TableSchema(
                name = "users",
                columns = listOf(
                    ColumnSchema("name", "varchar(255)", nullable = false),
                    ColumnSchema("nickname", "varchar(255)", nullable = true)
                )
            )
        )

        val statements = Tucuapi.generateSqlUpdateStatements(TestUser::class, current, allowDestructive = false)

        assertFalse(statements.any { it.contains("drop column") })
    }

    @Test
    fun `gera o alter da mudanca de tipo, nulidade e valor padrao`() {
        val current = listOf(
            TableSchema(
                name = "users",
                columns = listOf(
                    ColumnSchema("id", "integer", nullable = false, identity = true),
                    ColumnSchema("name", "varchar(255)", nullable = true),
                    ColumnSchema("email", "varchar(255)", nullable = false, default = "'a@b.com'"),
                    ColumnSchema("age", "bigint", nullable = false)
                ),
                primaryKey = ConstraintSchema("users_pkey", listOf("id")),
                uniqueConstraints = listOf(ConstraintSchema("users_email_key", listOf("email")))
            )
        )

        val statements = Tucuapi.generateSqlUpdateStatements(TestUser::class, current)

        assertTrue(statements.contains("alter table users alter column name set not null"))
        assertTrue(statements.contains("alter table users alter column email drop default"))
        assertTrue(statements.contains("alter table users alter column age type integer using age::integer"))
        assertTrue(statements.contains("alter table users alter column age drop not null"))
    }

    @Test
    fun `gera o alter da troca da chave primaria`() {
        val desired = Tucuapi.describeSchemas(TestUser::class).single()
        val current = desired.copy(primaryKey = ConstraintSchema("users_pkey", listOf("email")))

        val statements = Tucuapi.generateSqlUpdateStatements(TestUser::class, listOf(current))

        assertEquals(
            listOf(
                "alter table users drop constraint users_pkey",
                "alter table users add constraint users_pkey primary key (id)"
            ),
            statements
        )
    }

    @Test
    fun `descreve e compara as tabelas dos campos multivalorados`() {
        val schemas = Tucuapi.describeSchemas(WithCapabilities::class)
        assertEquals(listOf("users", "users_capabilities"), schemas.map { it.name })

        val multivalued = schemas.last()
        assertEquals(listOf("users_id", "value"), multivalued.columns.map { it.name })
        assertEquals(listOf("users_id", "value"), multivalued.primaryKey?.columns)
        assertEquals("users", multivalued.foreignKeys.single().referencedTable)
        assertEquals(listOf("id"), multivalued.foreignKeys.single().referencedColumns)
    }
}
