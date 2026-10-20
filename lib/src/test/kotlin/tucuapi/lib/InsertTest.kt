package tucuapi.lib

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

@ModelEnum(name = "insert_test_role_e")
enum class InsertTestRole {
    @ModelEnumVariant("admin")
    ADMIN,

    @ModelEnumVariant("user", label = "Usuário")
    USER
}

/**
 * Modelo usado nos testes de inserção.
 */
@Model(title = "Pessoa", table = "people")
class Person {
    @PrimaryKey
    @InputOmit
    @Column(type = "uuid")
    val id: Uuid = Uuid.random()

    val name: String

    @Column(unique = true)
    val email: String

    val age: Int?

    val role: InsertTestRole

    val active: Boolean

    @Multivalued
    val tags: List<InsertTestRole>

    constructor(
        name: String,
        email: String,
        age: Int?,
        role: InsertTestRole,
        active: Boolean,
        tags: List<InsertTestRole>
    ) {
        this.name = name
        this.email = email
        this.age = age
        this.role = role
        this.active = active
        this.tags = tags
    }
}

class InsertTest {

    private val params = mapOf(
        "name" to listOf("Ana"),
        "email" to listOf("ana@example.com"),
        "age" to listOf("30"),
        "role" to listOf("admin"),
        "active" to listOf("on"),
        "tags" to listOf("admin", "user")
    )

    @Test
    fun `constrói a instância do modelo a partir dos parâmetros`() {
        val person = Tucuapi.createInstance(Person::class, params) as Person

        assertEquals("Ana", person.name)
        assertEquals("ana@example.com", person.email)
        assertEquals(30, person.age)
        assertEquals(InsertTestRole.ADMIN, person.role)
        assertTrue(person.active)
        assertEquals(listOf(InsertTestRole.ADMIN, InsertTestRole.USER), person.tags)
    }

    @Test
    fun `usa o valor padrão quando o parâmetro opcional não vem`() {
        val withoutAge = params - "age"

        val person = Tucuapi.createInstance(Person::class, withoutAge) as Person

        assertNull(person.age)
    }

    @Test
    fun `gera o insert da linha principal`() {
        val person = Tucuapi.createInstance(Person::class, params)
        val instruction = Tucuapi.generateSqlInsertStatements(Person::class, person).first()

        assertEquals("people", instruction.table)
        assertEquals(
            "insert into people (id, name, email, age, role, active) values (?, ?, ?, ?, ?, ?)",
            instruction.sql
        )
        assertEquals(6, instruction.values.size)
        assertEquals(java.util.UUID.fromString((person as Person).id.toString()), instruction.values[0])
        assertEquals("Ana", instruction.values[1])
        assertEquals("ana@example.com", instruction.values[2])
        assertEquals(30, instruction.values[3])
        assertEquals(InferredValue("admin"), instruction.values[4])
        assertEquals(true, instruction.values[5])
    }

    @Test
    fun `gera um insert por valor dos campos multivalorados`() {
        val person = Tucuapi.createInstance(Person::class, params)
        val instructions = Tucuapi.generateSqlInsertStatements(Person::class, person)

        assertEquals(3, instructions.size)
        assertEquals("people_tags", instructions[1].table)
        assertEquals(
            "insert into people_tags (people_id, value) values (?, ?)",
            instructions[1].sql
        )
        assertEquals(InferredValue("admin"), instructions[1].values[1])
        assertEquals(InferredValue("user"), instructions[2].values[1])
        assertEquals(instructions[1].values[0], instructions[2].values[0])
    }

    @Test
    fun `converte a data do formulário para o tipo do campo`() {
        @Model(title = "Evento", table = "events")
        class Event {
            @PrimaryKey(autoIncrease = true)
            @InputOmit
            val id: Int = 0

            @DateTime(timezoneOffset = true)
            val happensAt: kotlin.time.Instant

            constructor(happensAt: kotlin.time.Instant) {
                this.happensAt = happensAt
            }
        }

        val event = Tucuapi.createInstance(
            Event::class,
            mapOf("happens_at" to listOf("2026-10-20T15:30"))
        ) as Event

        assertEquals(
            kotlin.time.Instant.fromEpochSeconds(
                java.time.LocalDateTime.parse("2026-10-20T15:30").toEpochSecond(java.time.ZoneOffset.UTC),
                0
            ),
            event.happensAt
        )

        // a coluna de identidade fica de fora do insert: quem gera é o banco
        val instruction = Tucuapi.generateSqlInsertStatements(Event::class, event).first()
        assertEquals("insert into events (happens_at) values (?)", instruction.sql)
    }

    @Test
    fun `valida os parâmetros antes de inserir`() {
        val errors = Tucuapi.validate(Person::class, params - "email")

        assertEquals(setOf("email"), errors.keys)
        assertEquals("'Email' é obrigatório.", errors["email"])
    }

    @Test
    fun `o valor do enum é o da variante, não o da constante`() {
        val person = Tucuapi.createInstance(
            Person::class,
            params + ("role" to listOf("user"))
        ) as Person

        assertEquals(InsertTestRole.USER, person.role)

        val instruction = Tucuapi.generateSqlInsertStatements(Person::class, person).first()
        assertEquals(InferredValue("user"), instruction.values[4])
    }

    @Test
    fun `o instante do modelo vira um valor aceito pelo driver`() {
        @Model(title = "Registro", table = "records")
        class Record {
            @PrimaryKey(autoIncrease = true)
            @InputOmit
            val id: Int = 0

            @DateTime(timezoneOffset = true)
            val createdAt: kotlin.time.Instant

            constructor(createdAt: kotlin.time.Instant) {
                this.createdAt = createdAt
            }
        }

        val record = Record(Clock.System.now())
        val instruction = Tucuapi.generateSqlInsertStatements(Record::class, record).first()

        assertTrue(instruction.values[0] is java.time.OffsetDateTime)
    }
}
