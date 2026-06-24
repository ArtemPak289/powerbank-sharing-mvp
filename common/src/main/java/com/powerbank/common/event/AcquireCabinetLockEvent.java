package com.powerbank.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Событие: Запрос на блокировку слота в станции.
 * Отправляется сервисом аренды (Rental Service) в станцию (Station Service), 
 * чтобы зарезервировать и заблокировать случайный свободный повербанк для клиента.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcquireCabinetLockEvent {
    
    /**
     * Уникальный идентификатор аренды.
     */
    private String rentalId;
    
    /**
     * Уникальный идентификатор станции, на которой клиент хочет взять повербанк.
     */
    private String stationId;
}
