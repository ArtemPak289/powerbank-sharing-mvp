package com.powerbank.rental.kafka;

import com.powerbank.common.event.*;
import com.powerbank.rental.service.RentalOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Слушатель ответов от других микросервисов для Saga аренды.
 * Передает события в RentalOrchestrator, который двигает конечный автомат.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RentalKafkaConsumer {

    private final RentalOrchestrator orchestrator;

    /**
     * Ответ от Station Service: удалось ли заблокировать слот/повербанк.
     */
    @KafkaListener(topics = KafkaTopics.ACQUIRE_CABINET_LOCK_RESULT, groupId = "rental-service-group")
    public void consumeLockResult(AcquireCabinetLockResultEvent event) {
        log.info("Получен ответ о блокировке слота: {}", event);
        orchestrator.handleLockResult(event);
    }

    /**
     * Ответ от Payment Service: удалось ли списать деньги с карты.
     */
    @KafkaListener(topics = KafkaTopics.PAYMENT_RESULT, groupId = "rental-service-group")
    public void consumePaymentResult(PaymentResultEvent event) {
        log.info("Получен результат оплаты: {}", event);
        orchestrator.handlePaymentResult(event);
    }

    /**
     * Ответ от Station Service: удалось ли физически отстрелить повербанк клиенту.
     */
    @KafkaListener(topics = KafkaTopics.EJECT_POWERBANK_RESULT, groupId = "rental-service-group")
    public void consumeEjectResult(EjectPowerbankResultEvent event) {
        log.info("Получен результат выдачи повербанка: {}", event);
        orchestrator.handleEjectResult(event);
    }

    /**
     * Ответ от Station Service: клиент вставил повербанк обратно в слот (и замок защелкнулся).
     */
    @KafkaListener(topics = KafkaTopics.RETURN_POWERBANK_RESULT, groupId = "rental-service-group")
    public void consumeReturnResult(ReturnPowerbankResultEvent event) {
        log.info("Получен результат возврата повербанка: {}", event);
        orchestrator.handleReturnResult(event);
    }
}
