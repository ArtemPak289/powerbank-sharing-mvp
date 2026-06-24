package com.powerbank.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Событие: Запрос на проведение платежа или холдирование средств.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestEvent {
    /** Идентификатор аренды. */
    private String rentalId;
    /** ID банковской карты. */
    private String cardId;
    /** ID пользователя. */
    private String userId;
    /** Сумма списания. */
    private BigDecimal amount;
    /** Ключ идемпотентности (чтобы не списать дважды). */
    private String idempotencyKey;
    /** Тип платежа. */
    private PaymentType paymentType;

    /** Типы возможных платежей. */
    public enum PaymentType {
        INITIAL,    // Первоначальный холд депозита
        RECURRING,  // Периодическое списание (раз в час/день)
        FINAL,      // Окончательный расчет при завершении аренды
        REFUND      // Возврат средств / отмена платежа
    }
}
