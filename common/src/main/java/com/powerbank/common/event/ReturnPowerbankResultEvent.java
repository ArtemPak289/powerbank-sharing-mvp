package com.powerbank.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Событие: Результат фиксации возврата повербанка.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnPowerbankResultEvent {
    /** Идентификатор аренды. */
    private String rentalId;
    /** ID станции возврата. */
    private String stationId;
    /** Номер слота, куда вставили повербанк. */
    private int slotNumber;
    /** Успешно ли зафиксирован возврат. */
    private boolean success;
    /** Причина ошибки, если есть. */
    private String errorMessage;
}
