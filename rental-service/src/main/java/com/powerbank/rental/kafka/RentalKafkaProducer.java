package com.powerbank.rental.kafka;

import com.powerbank.common.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Отправитель команд в другие микросервисы (Producer).
 * Используется Оркестратором (RentalOrchestrator) для управления сагой.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RentalKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Просит Station Service заблокировать (зарезервировать) любой свободный повербанк на станции.
     */
    public void sendAcquireCabinetLock(AcquireCabinetLockEvent event) {
        log.info("Отправка команды на блокировку слота: {}", event);
        kafkaTemplate.send(KafkaTopics.ACQUIRE_CABINET_LOCK_EVENT, event.getStationId(), event);
    }

    /**
     * Просит Payment Service списать деньги с карты клиента (стартовый взнос или финал).
     */
    public void sendPaymentRequest(PaymentRequestEvent event) {
        log.info("Отправка команды на оплату: {}", event);
        kafkaTemplate.send(KafkaTopics.PAYMENT_REQUEST, event.getRentalId(), event);
    }

    /**
     * Просит Station Service физически отстрелить (eject) повербанк клиенту.
     */
    public void sendEjectPowerbank(EjectPowerbankEvent event) {
        log.info("Отправка команды на выдачу повербанка: {}", event);
        kafkaTemplate.send(KafkaTopics.EJECT_POWERBANK_EVENT, event.getStationId(), event);
    }

    /**
     * Просит Station Service принять повербанк обратно в слот (клиент завершает аренду).
     */
    public void sendReturnPowerbank(ReturnPowerbankEvent event) {
        log.info("Отправка команды на возврат повербанка: {}", event);
        kafkaTemplate.send(KafkaTopics.RETURN_POWERBANK_EVENT, event.getStationId(), event);
    }

    /**
     * Просит Payment Service привязать новую банковскую карту (обычно вызывается напрямую, не в саге аренды).
     */
    public void sendCardBindRequest(CardBindRequestEvent event) {
        log.info("Отправка запроса на привязку карты: {}", event);
        kafkaTemplate.send(KafkaTopics.CARD_BIND_REQUEST, event.getUserId(), event);
    }
}
