package com.powerbank.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Сущность: Привязанная банковская карта пользователя.
 * Эмулирует хранение карты и баланса (в MVP).
 */
@Entity
@Table(name = "cards")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Card {

    /** Внутренний ID карты. */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // DECISIONS: Нет @ManyToOne(User).
    // Мы в другом микросервисе, пользователя здесь физически нет. Храним только UUID.
    /** ID владельца (Keycloak User ID). */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Полный номер карты (в реальности нельзя хранить без PCI-DSS сертификации). */
    @Column(name = "card_number", nullable = false, length = 19)
    private String cardNumber;

    /** Маскированный номер для отображения (например, **** **** **** 1234). */
    @Column(name = "masked_number", length = 19)
    private String maskedNumber;

    /** Имя владельца карты (на латинице). */
    @Column(name = "holder_name", nullable = false, length = 100)
    private String holderName;

    /** Срок действия в формате MM/YY. */
    @Column(name = "expiry_date", nullable = false, length = 5)
    private String expiryDate;

    /** Текущий баланс карты (для эмуляции списания). */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;

    /** Дата привязки карты. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        // Автоматически генерируем маскированный номер перед сохранением
        if (maskedNumber == null && cardNumber != null && cardNumber.length() >= 4) {
            maskedNumber = "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
        }
    }
}
