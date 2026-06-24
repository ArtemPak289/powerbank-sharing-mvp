package com.powerbank.payment.kafka;

import com.powerbank.common.event.CardBindResultEvent;
import com.powerbank.common.event.KafkaTopics;
import com.powerbank.common.event.PaymentResultEvent;
import com.powerbank.common.event.PaymentStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Отправитель Kafka-событий (Producer).
 * Возвращает ответы о статусе платежа и привязке карт.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Отправляет результат списания средств в Rental Service.
     */
    public void sendPaymentResult(PaymentResultEvent event) {
        log.info("Отправка результата платежа: {}", event);
        kafkaTemplate.send(KafkaTopics.PAYMENT_RESULT, java.util.Objects.requireNonNull(event.getRentalId()), event);
    }

    /**
     * Публикует событие об изменении статуса платежа.
     * Может использоваться для истории транзакций и аналитики.
     */
    public void sendPaymentStatusChanged(PaymentStatusChangedEvent event) {
        log.info("Отправка события изменения статуса платежа: {}", event);
        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, java.util.Objects.requireNonNull(event.getPaymentId()), event);
    }

    /**
     * Отправляет результат привязки карты в Rental Service.
     */
    public void sendCardBindResult(CardBindResultEvent event) {
        log.info("Отправка результата привязки карты: {}", event);
        kafkaTemplate.send(KafkaTopics.CARD_BIND_RESULT, java.util.Objects.requireNonNull(event.getUserId()), event);
    }
}
