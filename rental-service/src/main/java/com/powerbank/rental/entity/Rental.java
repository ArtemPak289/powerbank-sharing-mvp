package com.powerbank.rental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Сущность: Сессия аренды повербанка.
 * Это агрегатная сущность, которая хранит весь жизненный цикл аренды
 * (от запроса до возврата повербанка и финальной оплаты).
 */
@Entity
@Table(name = "rentals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rental {

    /** Внутренний ID аренды. */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // DECISIONS: Храним только UUID других сущностей (User, Station, Card),
    // так как они лежат в других микросервисах. Это микросервисный паттерн "Shared Nothing".

    /** ID пользователя, взявшего аренду. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** ID станции, на которой повербанк был взят. */
    @Column(name = "station_id", nullable = false)
    private UUID stationId;

    /** ID банковской карты, с которой списываются деньги. */
    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    /** ID физического повербанка (заполняется после его успешной блокировки станцией). */
    @Column(name = "power_bank_id")
    private UUID powerBankId;

    /** ID станции, на которую повербанк был возвращен. */
    @Column(name = "return_station_id")
    private UUID returnStationId;

    /** Номер слота, из которого был выдан или в который был возвращен повербанк. */
    @Column(name = "slot_number")
    private Integer slotNumber;

    /** Текущий шаг конечного автомата (Saga) этой аренды. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RentalStatus status;

    /** Ключ идемпотентности для защиты от двойных списаний и дублей запросов. */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    /** Итоговая сумма, списанная за аренду. */
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** Время физической выдачи повербанка (старт таймера аренды). */
    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    /** Время физического возврата повербанка. */
    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    /** Время создания заявки на аренду. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /** Время последнего изменения статуса. */
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
        if (status == null) status = RentalStatus.CREATED;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /**
     * Статусы аренды, описывающие шаги распределенной транзакции (Saga).
     */
    public enum RentalStatus {
        CREATED,
        LOCK_REQUESTED,
        LOCKED,
        PAYMENT_REQUESTED,
        PAYMENT_COMPLETED,
        EJECT_REQUESTED,
        ACTIVE,
        FINISH_REQUESTED,
        RETURN_REQUESTED,
        RETURNED,
        FINAL_PAYMENT_REQUESTED,
        COMPLETED,
        FAILED
    }
}
