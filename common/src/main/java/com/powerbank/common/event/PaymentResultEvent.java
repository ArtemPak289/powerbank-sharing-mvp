package com.powerbank.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Событие: Результат проведения платежа.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResultEvent {
    /** Идентификатор аренды. */
    private String rentalId;
    /** ID платежной транзакции из БД Payment Service. */
    private String paymentId;
    /** Статус платежа (COMPLETED, FAILED). */
    private String status;
    /** Ключ идемпотентности, переданный в запросе. */
    private String idempotencyKey;
    /** Сообщение об ошибке (при FAILED). */
    private String errorMessage;
}
