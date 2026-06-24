package com.powerbank.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Сущность: Пользователь системы.
 * Хранит привязку номера телефона к внутренней системе и к Keycloak.
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /** Внутренний уникальный идентификатор пользователя (Primary Key). */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** Номер телефона пользователя (уникальный). Выступает в роли логина. */
    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    /** ID пользователя в системе Keycloak (выдается при регистрации). */
    @Column(name = "external_id", unique = true)
    private String externalId;

    /** Технический пароль для Keycloak (пользователь его не знает, он генерируется сервером). */
    @Column(name = "keycloak_password")
    private String keycloakPassword;

    /** Дата и время создания записи. Заполняется автоматически. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Дата и время последнего обновления записи. Заполняется автоматически. */
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // DECISIONS: Используем простые @PrePersist и @PreUpdate вместо сложного Hibernate Envers или Spring Data Auditing.
    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
