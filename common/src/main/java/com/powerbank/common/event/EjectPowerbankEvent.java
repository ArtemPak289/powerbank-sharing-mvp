package com.powerbank.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Событие: Команда на физическую выдачу (отстрел) повербанка.
 * Отправляется после успешной блокировки депозита.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EjectPowerbankEvent {
    /** Уникальный идентификатор аренды. */
    private String rentalId;
    /** Уникальный идентификатор станции. */
    private String stationId;
    /** Номер слота, из которого нужно выдать повербанк. */
    private int slotNumber;
    /** Идентификатор выдаваемого повербанка. */
    private String powerBankId;
}
