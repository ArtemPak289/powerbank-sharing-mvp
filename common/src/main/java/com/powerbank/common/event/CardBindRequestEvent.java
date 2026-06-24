package com.powerbank.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Событие: Запрос на привязку банковской карты.
 * Отправляется в Payment Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardBindRequestEvent {
    /** ID запроса для идемпотентности. */
    private String requestId;
    /** Идентификатор пользователя. */
    private String userId;
    /** Номер карты. */
    private String cardNumber;
    /** Имя держателя карты. */
    private String holderName;
    /** Срок действия (MM/YY). */
    private String expiryDate;
}
