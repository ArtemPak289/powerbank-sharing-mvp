package com.powerbank.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Событие: Клиент вставил повербанк обратно в станцию. Запрос на фиксацию возврата.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReturnPowerbankEvent {
    /** Идентификатор аренды. */
    private String rentalId;
    /** ID станции возврата. */
    private String stationId;
    /** Идентификатор возвращенного повербанка. */
    private String powerBankId;
}
