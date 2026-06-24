package com.powerbank.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Событие: Публикуется каждый раз при изменении статуса транзакции (для истории).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusChangedEvent {
    private String paymentId;
    private String rentalId;
    private String oldStatus;
    private String newStatus;
}
