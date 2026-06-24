package com.powerbank.station.kafka;

import com.powerbank.common.event.AcquireCabinetLockEvent;
import com.powerbank.common.event.EjectPowerbankEvent;
import com.powerbank.common.event.KafkaTopics;
import com.powerbank.common.event.ReturnPowerbankEvent;
import com.powerbank.station.service.StationOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Слушатель Kafka-событий (Consumer).
 * Принимает команды от Rental Service на выполнение физических операций со станцией.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StationKafkaConsumer {

    private final StationOperationService stationOperationService;

    /**
     * Слушает команду на резервацию повербанка.
     */
    @KafkaListener(topics = KafkaTopics.ACQUIRE_CABINET_LOCK_EVENT, groupId = "station-service-group")
    public void consumeAcquireLock(AcquireCabinetLockEvent event) {
        log.info("Получена команда на резервацию слота: {}", event);
        stationOperationService.acquireCabinetLock(event);
    }

    /**
     * Слушает команду на выдачу (отстрел) повербанка.
     */
    @KafkaListener(topics = KafkaTopics.EJECT_POWERBANK_EVENT, groupId = "station-service-group")
    public void consumeEjectPowerbank(EjectPowerbankEvent event) {
        log.info("Получена команда на выдачу повербанка: {}", event);
        stationOperationService.ejectPowerbank(event);
    }

    /**
     * Слушает событие возврата повербанка (клиент вставил его обратно).
     * В реальном мире это событие генерировалось бы самой физической станцией (IoT),
     * но для MVP мы эмулируем это через REST/Kafka.
     */
    @KafkaListener(topics = KafkaTopics.RETURN_POWERBANK_EVENT, groupId = "station-service-group")
    public void consumeReturnPowerbank(ReturnPowerbankEvent event) {
        log.info("Получено событие возврата повербанка: {}", event);
        stationOperationService.returnPowerbank(event);
    }
}
