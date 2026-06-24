package com.powerbank.payment.kafka;

import com.powerbank.common.event.CardBindRequestEvent;
import com.powerbank.common.event.KafkaTopics;
import com.powerbank.common.event.PaymentRequestEvent;
import com.powerbank.payment.service.PaymentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Слушатель Kafka-событий для Payment Service.
 * Принимает запросы на списание денег и привязку карт.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentKafkaConsumer {

    private final PaymentProcessingService paymentProcessingService;

    /**
     * Слушает запросы на списание средств за аренду.
     */
    @KafkaListener(topics = KafkaTopics.PAYMENT_REQUEST, groupId = "payment-service-group")
    public void consumePaymentRequest(PaymentRequestEvent event) {
        log.info("Получен запрос на проведение платежа: {}", event);
        try {
            paymentProcessingService.processPayment(event);
        } catch (Exception e) {
            log.error("Критическая ошибка при обработке платежа: {}", event, e);
            // В продакшене сообщение ушло бы в DLT (Dead Letter Topic)
        }
    }

    /**
     * Слушает запросы на привязку банковской карты.
     */
    @KafkaListener(topics = KafkaTopics.CARD_BIND_REQUEST, groupId = "payment-service-group")
    public void consumeCardBindRequest(CardBindRequestEvent event) {
        log.info("Получен запрос на привязку карты: {}", event);
        try {
            paymentProcessingService.bindCard(event);
        } catch (Exception e) {
            log.error("Критическая ошибка при привязке карты: {}", event, e);
        }
    }
}
