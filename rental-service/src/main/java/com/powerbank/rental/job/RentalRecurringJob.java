package com.powerbank.rental.job;

import com.powerbank.common.event.PaymentRequestEvent;
import com.powerbank.rental.entity.Rental;
import com.powerbank.rental.kafka.RentalKafkaProducer;
import com.powerbank.rental.repository.RentalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;


/**
 * Планировщик для реккурентных списаний.
 * Выполняет требование: "Реализация реккурентных платежей для аренд".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RentalRecurringJob {

    private final RentalRepository rentalRepository;
    private final RentalKafkaProducer kafkaProducer;

    // В MVP запускаем проверку каждую минуту для тестирования. В проде это был бы час или день.
    @Scheduled(fixedRateString = "60000")
    @Transactional
    public void processRecurringPayments() {
        log.info("Запуск джоба реккурентных платежей...");
        List<Rental> activeRentals = rentalRepository.findByStatus(Rental.RentalStatus.ACTIVE);

        if (activeRentals.isEmpty()) {
            log.info("Нет активных аренд для реккурентных списаний.");
            return;
        }

        for (Rental rental : activeRentals) {
            try {
                // Создаем уникальный ключ для реккурентного платежа (например, на основе текущего времени)
                // В реальном проекте мы бы хранили время последнего списания. Для MVP упрощаем.
                String idempotencyKey = "rec_" + rental.getId() + "_" + System.currentTimeMillis() / 60000;

                PaymentRequestEvent request = PaymentRequestEvent.builder()
                        .rentalId(rental.getId().toString())
                        .cardId(rental.getCardId().toString())
                        .userId(rental.getUserId().toString())
                        .amount(new BigDecimal("10.00")) // Сумма реккурентного списания (например, 10 руб/час)
                        .idempotencyKey(idempotencyKey)
                        .paymentType(PaymentRequestEvent.PaymentType.RECURRING)
                        .build();

                log.info("Отправка реккурентного платежа для аренды {}: {}", rental.getId(), request);
                kafkaProducer.sendPaymentRequest(request);
            } catch (Exception e) {
                log.error("Ошибка при обработке реккурентного платежа для аренды {}", rental.getId(), e);
            }
        }
    }
}
