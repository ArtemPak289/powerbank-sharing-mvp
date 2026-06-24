package com.powerbank.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Сущность: Одноразовый пароль (OTP).
 * Хранит сгенерированные коды для проверки номеров телефонов.
 */
@Entity
@Table(name = "otp_codes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OtpCode {

    /** Уникальный идентификатор записи (Primary Key). */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** Номер телефона, для которого сгенерирован код. */
    @Column(nullable = false, length = 20)
    private String phone;

    /** Сам одноразовый код (например, "123456"). */
    @Column(nullable = false, length = 6)
    private String code;

    /** Флаг, указывающий, был ли этот код уже успешно проверен. */
    @Column(nullable = false)
    private boolean verified;

    /** Дата и время, после которых код считается недействительным. */
    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /** Дата и время генерации кода. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    /**
     * Проверяет, не истек ли срок действия кода.
     * @return true если код просрочен, иначе false.
     */
    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }
}
