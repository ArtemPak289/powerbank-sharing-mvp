package com.powerbank.rental.service;

import com.powerbank.common.event.*;
import com.powerbank.rental.entity.Rental;
import com.powerbank.rental.entity.Rental.RentalStatus;
import com.powerbank.rental.kafka.RentalKafkaProducer;
import com.powerbank.rental.repository.RentalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Оркестратор распределенной транзакции (Saga) аренды.
 * Управляет жизненным циклом аренды, отправляя команды в Kafka и реагируя на события
 * от Station Service и Payment Service.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RentalOrchestrator {

    private final RentalRepository rentalRepository;
    private final RentalKafkaProducer kafkaProducer;

    // DECISIONS: Для MVP тариф захардкожен.
    // 50 рублей при старте (первоначальный взнос), и +10 рублей за каждый час.
    private static final BigDecimal INITIAL_FEE = new BigDecimal("50.00");
    private static final BigDecimal HOURLY_RATE = new BigDecimal("10.00");

    /**
     * Шаг 1: Пользователь нажал "Взять повербанк".
     * Инициализируем аренду и просим станцию заблокировать слот.
     */
    @Transactional
    public Rental startRental(UUID userId, UUID stationId, UUID cardId, String idempotencyKey) {
        log.info("Запуск аренды: user={}, station={}", userId, stationId);
        
        // 1. Проверяем, нет ли уже такого запроса (защита от двойного клика)
        Optional<Rental> existing = rentalRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        // 2. Создаем сущность аренды
        Rental rental = Rental.builder()
                .userId(userId)
                .stationId(stationId)
                .cardId(cardId)
                .idempotencyKey(idempotencyKey)
                .status(RentalStatus.LOCK_REQUESTED)
                .build();
        rentalRepository.save(rental);

        // 3. Отправляем в Kafka команду для Station Service: "Заблокируй любой свободный повербанк"
        kafkaProducer.sendAcquireCabinetLock(AcquireCabinetLockEvent.builder()
                .rentalId(rental.getId().toString())
                .stationId(stationId.toString())
                .build());

        return rental;
    }

    /**
     * Клиент нажал "Завершить аренду" или вставил повербанк в станцию.
     * Мы просим станцию принять повербанк.
     */
    @Transactional
    public void finishRental(UUID rentalId, UUID returnStationId) {
        log.info("Запрос на завершение аренды: rental={}, returnStation={}", rentalId, returnStationId);
        
        Rental rental = rentalRepository.findById(rentalId)
                .orElseThrow(() -> new RuntimeException("Аренда не найдена"));

        if (rental.getStatus() != RentalStatus.ACTIVE) {
            throw new RuntimeException("Аренда не активна (нечего завершать)");
        }

        // Меняем статус и фиксируем станцию возврата
        rental.setStatus(RentalStatus.FINISH_REQUESTED);
        rental.setReturnStationId(returnStationId);
        rentalRepository.save(rental);

        // Просим Station Service принять повербанк обратно в слот
        kafkaProducer.sendReturnPowerbank(ReturnPowerbankEvent.builder()
                .rentalId(rental.getId().toString())
                .stationId(returnStationId.toString())
                .powerBankId(rental.getPowerBankId().toString())
                .build());
    }

    // ==========================================
    // Обработчики ответов (Event Handlers)
    // ==========================================

    /**
     * Шаг 2: Station Service ответил, удалось ли заблокировать повербанк.
     */
    @Transactional
    public void handleLockResult(AcquireCabinetLockResultEvent event) {
        Rental rental = rentalRepository.findById(UUID.fromString(event.getRentalId())).orElseThrow();
        
        // Проверка идемпотентности стейт-машины (можем ли мы двигаться дальше)
        if (rental.getStatus() != RentalStatus.LOCK_REQUESTED) return;

        if (event.isSuccess()) {
            // Если слот успешно заблокирован, переходим к оплате
            rental.setStatus(RentalStatus.PAYMENT_REQUESTED);
            rental.setPowerBankId(UUID.fromString(event.getPowerBankId()));
            rental.setSlotNumber(event.getSlotNumber());
            rentalRepository.save(rental);

            // Просим Payment Service списать стартовый взнос
            kafkaProducer.sendPaymentRequest(PaymentRequestEvent.builder()
                    .rentalId(rental.getId().toString())
                    .cardId(rental.getCardId().toString())
                    .userId(rental.getUserId().toString())
                    .amount(INITIAL_FEE)
                    .idempotencyKey("init_" + rental.getId().toString())
                    .paymentType(PaymentRequestEvent.PaymentType.INITIAL)
                    .build());
        } else {
            // Если на станции нет свободных повербанков, отменяем аренду
            failRental(rental, "Не удалось заблокировать повербанк (нет в наличии)");
        }
    }

    /**
     * Шаг 3 / Шаг Финал: Payment Service ответил о статусе оплаты.
     */
    @Transactional
    public void handlePaymentResult(PaymentResultEvent event) {
        Rental rental = rentalRepository.findById(UUID.fromString(event.getRentalId())).orElseThrow();

        // Если это ответ на стартовый платеж
        if (event.getIdempotencyKey().startsWith("init_") && rental.getStatus() == RentalStatus.PAYMENT_REQUESTED) {
            if ("COMPLETED".equals(event.getStatus())) {
                // Если оплата прошла, просим станцию выдать (отстрелить) повербанк
                rental.setStatus(RentalStatus.EJECT_REQUESTED);
                rental.setTotalAmount(rental.getTotalAmount().add(INITIAL_FEE));
                rentalRepository.save(rental);

                kafkaProducer.sendEjectPowerbank(EjectPowerbankEvent.builder()
                        .rentalId(rental.getId().toString())
                        .stationId(rental.getStationId().toString())
                        .slotNumber(rental.getSlotNumber())
                        .powerBankId(rental.getPowerBankId().toString())
                        .build());
            } else {
                failRental(rental, "Ошибка стартового платежа (недостаточно средств)");
            }
        } 
        // Если это ответ на финальный платеж (при завершении аренды)
        else if (event.getIdempotencyKey().startsWith("final_") && rental.getStatus() == RentalStatus.FINAL_PAYMENT_REQUESTED) {
            if ("COMPLETED".equals(event.getStatus())) {
                // Все успешно, аренда полностью завершена
                rental.setStatus(RentalStatus.COMPLETED);
                rentalRepository.save(rental);
                log.info("Аренда {} успешно завершена и полностью оплачена.", rental.getId());
            } else {
                // В реальном мире мы бы перевели аренду в статус DEBT (Долг) и пытались бы списывать деньги каждый день.
                // Для MVP просто пишем ошибку в лог.
                log.error("Финальная оплата не прошла для аренды {}", rental.getId());
            }
        }
    }

    /**
     * Шаг 4: Station Service ответил, удалось ли физически выдать повербанк клиенту.
     */
    @Transactional
    public void handleEjectResult(EjectPowerbankResultEvent event) {
        Rental rental = rentalRepository.findById(UUID.fromString(event.getRentalId())).orElseThrow();
        if (rental.getStatus() != RentalStatus.EJECT_REQUESTED) return;

        if (event.isSuccess()) {
            // Ура! Клиент получил повербанк в руки. Запускаем таймер.
            rental.setStatus(RentalStatus.ACTIVE);
            rental.setStartedAt(OffsetDateTime.now());
            rentalRepository.save(rental);
            log.info("Аренда {} перешла в статус ACTIVE.", rental.getId());
        } else {
            // Если физически отстрелить не удалось (сломался механизм)
            // В реальной системе нужно отправить команду на возврат средств (Refund).
            failRental(rental, "Ошибка механизма выдачи повербанка");
        }
    }

    /**
     * Шаг Финал (Начало): Station Service ответил, что клиент вставил повербанк обратно в слот.
     */
    @Transactional
    public void handleReturnResult(ReturnPowerbankResultEvent event) {
        Rental rental = rentalRepository.findById(UUID.fromString(event.getRentalId())).orElseThrow();
        if (rental.getStatus() != RentalStatus.FINISH_REQUESTED) return;

        if (event.isSuccess()) {
            // Останавливаем таймер
            rental.setStatus(RentalStatus.FINAL_PAYMENT_REQUESTED);
            rental.setFinishedAt(OffsetDateTime.now());
            
            // Считаем часы аренды (минимум 1 час, округляем вверх)
            Duration duration = Duration.between(rental.getStartedAt(), rental.getFinishedAt());
            long hours = Math.max(1, duration.toHours() + 1);
            
            // Считаем сумму к списанию (Сумма = Часы * Ставка)
            BigDecimal finalAmount = HOURLY_RATE.multiply(new BigDecimal(hours));
            rental.setTotalAmount(rental.getTotalAmount().add(finalAmount));
            rentalRepository.save(rental);

            // Просим Payment Service списать итоговую сумму
            kafkaProducer.sendPaymentRequest(PaymentRequestEvent.builder()
                    .rentalId(rental.getId().toString())
                    .cardId(rental.getCardId().toString())
                    .userId(rental.getUserId().toString())
                    .amount(finalAmount)
                    .idempotencyKey("final_" + rental.getId().toString())
                    .paymentType(PaymentRequestEvent.PaymentType.FINAL)
                    .build());
        } else {
            log.error("Возврат повербанка не удался (аренда {}): {}", rental.getId(), event.getErrorMessage());
        }
    }

    /**
     * Вспомогательный метод для перевода аренды в ошибочный статус.
     */
    private void failRental(Rental rental, String reason) {
        log.error("Аренда {} завершилась с ошибкой: {}", rental.getId(), reason);
        rental.setStatus(RentalStatus.FAILED);
        rentalRepository.save(rental);
    }
}
