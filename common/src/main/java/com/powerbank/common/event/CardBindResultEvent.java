package com.powerbank.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Событие: Результат привязки карты.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardBindResultEvent {
    /** ID запроса. */
    private String requestId;
    /** Идентификатор пользователя. */
    private String userId;
    /** Внутренний ID привязанной карты (в payment_db). */
    private String cardId;
    /** Успешно ли привязана карта. */
    private boolean success;
    /** Сообщение об ошибке (при наличии). */
    private String errorMessage;
}
