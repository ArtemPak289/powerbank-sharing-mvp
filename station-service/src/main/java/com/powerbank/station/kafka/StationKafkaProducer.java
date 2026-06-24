package com.powerbank.station.kafka;

import com.powerbank.common.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Отправитель Kafka-событий (Producer).
 * Сообщает Rental Service о результатах физических операций со станцией.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StationKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Отправляет результат резервации слота (успех/ошибка).
     */
    public void sendAcquireLockResult(AcquireCabinetLockResultEvent event) {
        log.info("Отправка результата резервации слота: {}", event);
        kafkaTemplate.send(KafkaTopics.ACQUIRE_CABINET_LOCK_RESULT, event.getRentalId(), event);
    }

    /**
     * Отправляет результат физической выдачи повербанка.
     */
    public void sendEjectPowerbankResult(EjectPowerbankResultEvent event) {
        log.info("Отправка результата выдачи повербанка: {}", event);
        kafkaTemplate.send(KafkaTopics.EJECT_POWERBANK_RESULT, event.getRentalId(), event);
    }

    /**
     * Отправляет результат возврата (вставки) повербанка клиентом.
     */
    public void sendReturnPowerbankResult(ReturnPowerbankResultEvent event) {
        log.info("Отправка результата возврата повербанка: {}", event);
        kafkaTemplate.send(KafkaTopics.RETURN_POWERBANK_RESULT, event.getRentalId(), event);
    }
}
