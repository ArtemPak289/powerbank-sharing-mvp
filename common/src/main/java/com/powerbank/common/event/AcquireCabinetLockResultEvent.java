package com.powerbank.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Событие: Результат попытки заблокировать слот станции.
 * Отправляется станцией (Station Service) обратно в сервис аренды (Rental Service).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcquireCabinetLockResultEvent {
    /** Уникальный идентификатор аренды. */
    private String rentalId;
    /** Уникальный идентификатор станции. */
    private String stationId;
    /** Номер заблокированного слота (если успешно). */
    private Integer slotNumber;
    /** Идентификатор повербанка (если успешно). */
    private String powerBankId;
    /** Флаг успешности операции. */
    private boolean success;
    /** Сообщение об ошибке, если success = false. */
    private String errorMessage;
}
