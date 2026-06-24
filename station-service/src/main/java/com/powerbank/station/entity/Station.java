package com.powerbank.station.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Сущность: Станция (автомат) выдачи повербанков.
 */
@Entity
@Table(name = "stations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Station {

    /** Уникальный идентификатор станции. */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /** Понятное имя или номер станции. */
    @Column(nullable = false, length = 100)
    private String name;

    /** Физический адрес расположения. */
    @Column(nullable = false, length = 255)
    private String address;

    /** Координаты (широта). */
    @Column(nullable = false)
    private Double latitude;

    /** Координаты (долгота). */
    @Column(nullable = false)
    private Double longitude;

    /** Максимальное количество слотов (вместимость станции). */
    @Column(name = "total_slots", nullable = false)
    private Integer totalSlots;

    /** Время добавления станции в БД. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // DECISIONS: Убрали @OneToMany(mappedBy = "station") список повербанков.
    // KISS: Хранить и загружать списки вложенных сущностей через ORM — это частая причина N+1 проблем
    // и огромного потребления памяти. Если нам нужны повербанки станции, мы сделаем простой SELECT по station_id.

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
