package com.powerbank.station.service;

import com.powerbank.common.event.*;
import com.powerbank.station.entity.Powerbank;
import com.powerbank.station.entity.Powerbank.PowerbankStatus;
import com.powerbank.station.entity.Station;
import com.powerbank.station.kafka.StationKafkaProducer;
import com.powerbank.station.repository.PowerbankRepository;
import com.powerbank.station.repository.StationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис для физических операций со станцией (бронирование, выдача, возврат).
 * Получает команды из Kafka и отправляет результаты обратно.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StationOperationService {

    private final StationRepository stationRepository;
    private final PowerbankRepository powerbankRepository;
    private final StationKafkaProducer kafkaProducer;

    /**
     * Заблокировать (зарезервировать) случайный свободный повербанк в станции.
     * Вызывается, когда клиент нажал "Взять" и началась процедура оплаты.
     */
    @Transactional
    public void acquireCabinetLock(AcquireCabinetLockEvent event) {
        log.info("Запрос на резервацию слота: аренда={}, станция={}", event.getRentalId(), event.getStationId());

        try {
            // 1. Парсим ID станции
            UUID stationId = UUID.fromString(event.getStationId());
            
            // 2. Ищем все свободные повербанки на этой станции
            List<Powerbank> availablePbs = powerbankRepository.findByStationIdAndStatus(stationId, PowerbankStatus.AVAILABLE);

            // 3. Если свободных нет — кидаем ошибку
            if (availablePbs.isEmpty()) {
                throw new RuntimeException("На данной станции нет доступных повербанков");
            }

            // 4. Берем первый попавшийся свободный повербанк
            Powerbank pb = availablePbs.get(0);
            
            // 5. Меняем его статус на LOCKED, чтобы никто другой его не забрал
            pb.setStatus(PowerbankStatus.LOCKED);
            powerbankRepository.save(pb);

            log.info("Заблокирован повербанк: {} в слоте: {}", pb.getId(), pb.getSlotNumber());

            // 6. Отправляем в Kafka сообщение об успешной блокировке
            kafkaProducer.sendAcquireLockResult(AcquireCabinetLockResultEvent.builder()
                    .rentalId(event.getRentalId())
                    .stationId(event.getStationId())
                    .slotNumber(pb.getSlotNumber())
                    .powerBankId(pb.getId().toString())
                    .success(true)
                    .build());

        } catch (Exception e) {
            log.error("Ошибка при резервации слота", e);
            // 7. Если что-то пошло не так (нет повербанков, упала БД) — отправляем ошибку в Rental Service
            kafkaProducer.sendAcquireLockResult(AcquireCabinetLockResultEvent.builder()
                    .rentalId(event.getRentalId())
                    .stationId(event.getStationId())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build());
        }
    }

    /**
     * Физическая выдача (отстрел) повербанка клиенту после успешной оплаты.
     */
    @Transactional
    public void ejectPowerbank(EjectPowerbankEvent event) {
        log.info("Запрос на выдачу повербанка: {} из слота: {}", event.getPowerBankId(), event.getSlotNumber());

        try {
            // 1. Ищем повербанк по ID
            Optional<Powerbank> optionalPb = powerbankRepository.findById(UUID.fromString(event.getPowerBankId()));
            if (optionalPb.isEmpty()) {
                throw new RuntimeException("Повербанк не найден");
            }
            Powerbank pb = optionalPb.get();

            // 2. Проверяем, что он действительно был зарезервирован под эту аренду
            if (pb.getStatus() != PowerbankStatus.LOCKED) {
                throw new RuntimeException("Повербанк должен быть в статусе LOCKED для выдачи");
            }

            // 3. Отвязываем его от станции и помечаем как "в аренде"
            pb.setStatus(PowerbankStatus.RENTED);
            pb.setStationId(null);
            pb.setSlotNumber(null);
            powerbankRepository.save(pb);

            // 4. Сообщаем Rental Service, что выдача прошла успешно (аренда началась)
            kafkaProducer.sendEjectPowerbankResult(EjectPowerbankResultEvent.builder()
                    .rentalId(event.getRentalId())
                    .stationId(event.getStationId())
                    .powerBankId(event.getPowerBankId())
                    .success(true)
                    .build());

        } catch (Exception e) {
            log.error("Ошибка при выдаче повербанка", e);
            // 5. Сообщаем Rental Service об ошибке выдачи (нужно будет вернуть деньги клиенту)
            kafkaProducer.sendEjectPowerbankResult(EjectPowerbankResultEvent.builder()
                    .rentalId(event.getRentalId())
                    .stationId(event.getStationId())
                    .powerBankId(event.getPowerBankId())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build());
        }
    }

    /**
     * Клиент вставил повербанк обратно в станцию.
     */
    @Transactional
    public void returnPowerbank(ReturnPowerbankEvent event) {
        log.info("Запрос на возврат повербанка: {} на станцию: {}", event.getPowerBankId(), event.getStationId());

        try {
            // 1. Ищем повербанк и станцию
            Optional<Powerbank> optionalPb = powerbankRepository.findById(UUID.fromString(event.getPowerBankId()));
            if (optionalPb.isEmpty()) {
                throw new RuntimeException("Повербанк не найден");
            }
            Powerbank pb = optionalPb.get();
            
            Optional<Station> optionalStation = stationRepository.findById(UUID.fromString(event.getStationId()));
            if (optionalStation.isEmpty()) {
                throw new RuntimeException("Станция не найдена");
            }
            Station station = optionalStation.get();

            // 2. Ищем свободный физический слот на станции
            int freeSlot = findFreeSlot(station);

            // 3. Привязываем повербанк к новому слоту станции
            pb.setStatus(PowerbankStatus.AVAILABLE);
            pb.setStationId(station.getId());
            pb.setSlotNumber(freeSlot);
            powerbankRepository.save(pb);

            // 4. Отправляем в Rental Service подтверждение возврата (аренда завершена)
            kafkaProducer.sendReturnPowerbankResult(ReturnPowerbankResultEvent.builder()
                    .rentalId(event.getRentalId())
                    .stationId(event.getStationId())
                    .slotNumber(freeSlot)
                    .success(true)
                    .build());

        } catch (Exception e) {
            log.error("Ошибка при возврате повербанка", e);
            kafkaProducer.sendReturnPowerbankResult(ReturnPowerbankResultEvent.builder()
                    .rentalId(event.getRentalId())
                    .stationId(event.getStationId())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build());
        }
    }

    /**
     * Находит первый свободный номер слота на указанной станции.
     */
    private int findFreeSlot(Station station) {
        // DECISIONS: Заменили Stream API на классический вложенный цикл для простоты (KISS).
        // Достаем все повербанки станции прямым запросом (никакого LazyLoading).
        List<Powerbank> pbs = powerbankRepository.findByStationId(station.getId());
        
        // Проходимся по всем возможным слотам от 1 до totalSlots
        for (int slot = 1; slot <= station.getTotalSlots(); slot++) {
            boolean isOccupied = false;
            
            // Проверяем, занят ли этот слот каким-либо повербанком
            for (Powerbank p : pbs) {
                if (p.getSlotNumber() != null && p.getSlotNumber() == slot) {
                    isOccupied = true;
                    break;
                }
            }
            
            // Если слот свободен — возвращаем его номер
            if (!isOccupied) {
                return slot;
            }
        }
        
        throw new RuntimeException("На станции нет свободных слотов");
    }
}
