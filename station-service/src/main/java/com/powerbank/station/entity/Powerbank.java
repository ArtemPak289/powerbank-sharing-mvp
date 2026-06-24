package com.powerbank.station.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Сущность: Повербанк (внешний аккумулятор).
 */
@Entity
@Table(name = "power_banks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Powerbank {

    /** Уникальный идентификатор повербанка (MAC-адрес, чип или сгенерированный UUID). */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // DECISIONS: Заменили @ManyToOne(Station) на простой UUID stationId.
    // KISS: Нам не нужно вытягивать всю сущность станции каждый раз, когда мы читаем повербанк.
    // Это радикально упрощает SQL-запросы (никаких JOIN-ов под капотом).
    /** ID станции, в которой сейчас находится повербанк (null, если он на руках у клиента). */
    @Column(name = "station_id")
    private UUID stationId;

    /** Номер слота в текущей станции. */
    @Column(name = "slot_number")
    private Integer slotNumber;

    /** Текущий статус повербанка. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PowerbankStatus status;

    /** Дата добавления в систему. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        if (status == null) status = PowerbankStatus.AVAILABLE;
    }

    /** Статусы, в которых может находиться повербанк. */
    public enum PowerbankStatus {
        /** Свободен и заряжен, готов к выдаче. */
        AVAILABLE, 
        /** Заблокирован (например, идет процесс оплаты и выдачи). */
        LOCKED, 
        /** Выдан клиенту, находится в аренде. */
        RENTED
    }
}
