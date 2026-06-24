package com.powerbank.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Сущность: Транзакция (оплата).
 * Хранит историю попыток списания или холдирования средств.
 */
@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    /** Внутренний ID транзакции. */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // DECISIONS: Нет @ManyToOne(Card).
    // Мы обращаемся к карте по её ID. Избегаем сложных JPA-связей.
    /** ID карты, с которой происходит списание. */
    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    /** ID сессии аренды, за которую происходит оплата. */
    @Column(name = "rental_id", nullable = false)
    private UUID rentalId;

    /** ID пользователя, который совершает оплату. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Сумма транзакции. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Текущий статус транзакции. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /**
     * Ключ идемпотентности. 
     * Гарантирует, что при дублировании Kafka-события или повторном запросе 
     * мы не спишем деньги дважды.
     */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    /** Время создания транзакции. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Время последнего обновления (например, когда статус сменился на COMPLETED). */
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (status == null) status = PaymentStatus.PENDING;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /** Возможные статусы платежа. */
    public enum PaymentStatus {
        /** Создан, но еще не обработан. */
        PENDING, 
        /** Успешно завершен. */
        COMPLETED, 
        /** Ошибка (недостаточно средств и т.д.). */
        FAILED, 
        /** Отменен (например, если станция не смогла выдать повербанк). */
        CANCELLED
    }
}
