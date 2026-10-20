package org.example.models

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Cargos do usuário
 */
@ModelEnum(name = "user_role_e")
enum class UserRole {
    @ModelEnumVariant("admin")
    ADMIN,
    @ModelEnumVariant("admin")
    USER
}

/**
 * Permissões do usuário dentro do site
 */
@ModelEnum(name = "user_capabilities_e")
enum class UserCapabilities {
    @ModelEnumVariant("comment")
    Comment,
    @ModelEnumVariant("report")
    Report,
    @ModelEnumVariant("like")
    Like,
}

/**
 * Modelo do usuário
 */
@Model(name = "users")
class User {
    @PrimaryKey
    @InputOmit
    val id = Uuid.random()
    @VarcharConfig(size = 200)
    val name: String
    @Unique
    val email: String
    val password: String
    @DateTime(timezoneOffset = true)
    @InputOmit
    val createdAt: Instant
    @InputOmit
    val updatedAt: Instant? = null
    val role: UserRole
    @InputOmit
    val likes: Int = 1
    @InputMultiselect
    val capabilities: List<UserCapabilities>
    @Text
    val biography: String

    constructor(name: String, email: String, password: String, role: UserRole) {
        this.name = name
        this.email = email
        this.password = password
        this.createdAt = Clock.System.now()
        this.role = role
    }
}
