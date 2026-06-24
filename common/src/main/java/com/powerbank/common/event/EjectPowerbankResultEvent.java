package com.powerbank.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Событие: Результат выдачи повербанка клиенту.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EjectPowerbankResultEvent {
    /** Уникальный идентификатор аренды. */
    private String rentalId;
    /** Уникальный идентификатор станции. */
    private String stationId;
    /** Идентификатор выданного повербанка. */
    private String powerBankId;
    /** Флаг успеха. */
    private boolean success;
    /** Причина ошибки, если не удалось выдать. */
    private String errorMessage;
}
