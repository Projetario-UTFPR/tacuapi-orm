package org.example.models

import tucuapi.lib.Column
import tucuapi.lib.DateTime
import tucuapi.lib.InputField
import tucuapi.lib.Multivalued
import tucuapi.lib.InputOmit
import tucuapi.lib.Model
import tucuapi.lib.ModelEnum
import tucuapi.lib.ModelEnumVariant
import tucuapi.lib.PrimaryKey
import tucuapi.lib.Text
import tucuapi.lib.VarcharConfig
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Cargos do usuário
 */
@ModelEnum(name = "user_role_e")
enum class UserRole {
    @ModelEnumVariant("admin", label = "Administrador")
    ADMIN,
    @ModelEnumVariant("user", label = "Usuário comum")
    USER
}

/**
 * Permissões do usuário dentro do site
 */
@ModelEnum(name = "user_capabilities_e")
enum class UserCapabilities {
    @ModelEnumVariant("comment", label = "Comentar")
    Comment,
    @ModelEnumVariant("report", label = "Denunciar")
    Report,
    @ModelEnumVariant("like", label = "Avaliar")
    Rate,
}

/**
 * Modelo do usuário
 */
@Model(title = "Usuário", table = "users")
class User {
    @PrimaryKey
    @InputOmit
    @Column(type = "uuid")
    val id = Uuid.random()

    @VarcharConfig(size = 200)
    @InputField(label = "Nome")
    val name: String

    @Column(unique = true)
    @InputField(label = "Endereço de e-mail", placeholder = "johndoe@utfpr.com", type = "email")
    val email: String

    @InputField(label = "Senha", footnote = "Deve ter, no mínimo, 6 dígitos.", type = "password")
    val password: String

    @DateTime(timezoneOffset = true)
    @InputOmit
    @Column(name = "created_at")
    val createdAt: Instant

    @InputOmit
    @Column(name = "updated_at")
    val updatedAt: Instant? = null

    @InputField(label = "Cargo")
    val role: UserRole

    @InputOmit
    val likes: Int = 1

    @Multivalued
    @InputField(label = "Permissões", footnote = "Permissões do usuário dentro do site. (Apenas para mostrar o multiselect.)")
    val capabilities: List<UserCapabilities>

    @Text
    @InputField(label = "Biografia")
    val biography: String

    constructor(
        name: String,
        email: String,
        password: String,
        biography: String,
        role: UserRole,
        capabilities: List<UserCapabilities>
    ) {
        this.name = name
        this.email = email
        this.password = password
        this.createdAt = Clock.System.now()
        this.role = role
        this.capabilities = capabilities
        this.biography = biography
    }
}
