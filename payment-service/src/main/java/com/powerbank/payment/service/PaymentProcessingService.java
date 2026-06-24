package com.powerbank.payment.service;

import com.powerbank.common.event.*;
import com.powerbank.payment.entity.Card;
import com.powerbank.payment.entity.Payment;
import com.powerbank.payment.entity.Payment.PaymentStatus;
import com.powerbank.payment.kafka.PaymentKafkaProducer;
import com.powerbank.payment.repository.CardRepository;
import com.powerbank.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Основная логика обработки платежей.
 * Обеспечивает транзакционное списание средств и реализует паттерн Идемпотентность (Idempotency).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentProcessingService {

    private final CardRepository cardRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentKafkaProducer kafkaProducer;

    /**
     * Обрабатывает запрос на оплату аренды.
     * Метод идемпотентен: если Kafka доставит одно и то же сообщение дважды (At-Least-Once delivery),
     * мы не спишем деньги второй раз, а просто вернем результат первого списания.
     */
    @Transactional
    public void processPayment(PaymentRequestEvent event) {
        log.info("Обработка платежа: аренда={}, сумма={}, ключ_идемпотентности={}",
                event.getRentalId(), event.getAmount(), event.getIdempotencyKey());

        // 1. Проверка идемпотентности
        // Если транзакция с таким ключом уже есть в базе — значит это дубликат сообщения
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(event.getIdempotencyKey());
        if (existing.isPresent()) {
            Payment payment = existing.get();
            log.info("Обнаружен дубликат запроса (idempotency_key={}), возвращаем старый статус: {}",
                    event.getIdempotencyKey(), payment.getStatus());
            // Снова отправляем результат в Kafka, на случай если предыдущий ответ потерялся
            sendResult(payment, event.getRentalId());
            return;
        }

        // 2. Создаем новую запись о платеже со статусом PENDING
        Payment payment = Payment.builder()
                .cardId(UUID.fromString(event.getCardId()))
                .rentalId(UUID.fromString(event.getRentalId()))
                .userId(UUID.fromString(event.getUserId()))
                .amount(event.getAmount())
                .idempotencyKey(event.getIdempotencyKey())
                .status(PaymentStatus.PENDING)
                .build();

        try {
            // 3. Ищем карту
            Card card = cardRepository.findById(java.util.Objects.requireNonNull(UUID.fromString(event.getCardId())))
                    .orElseThrow(() -> new RuntimeException("Карта не найдена: " + event.getCardId()));

            // 4. Проверка типа платежа: Возврат или Списание
            if (event.getPaymentType() == PaymentRequestEvent.PaymentType.REFUND) {
                // Возврат средств (пополнение)
                card.setBalance(card.getBalance().add(event.getAmount()));
                log.info("Осуществлен возврат средств (REFUND) на сумму: {}", event.getAmount());
            } else {
                // Обычное списание (INITIAL, RECURRING, FINAL)
                if (card.getBalance().compareTo(event.getAmount()) < 0) {
                    throw new RuntimeException("Недостаточно средств на карте");
                }
                card.setBalance(card.getBalance().subtract(event.getAmount()));
            }

            cardRepository.save(card);

            // 6. Помечаем платеж как успешный
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);

            log.info("Платеж успешно завершен (тип={}): id={}, сумма={}", event.getPaymentType(), payment.getId(), payment.getAmount());

        } catch (Exception e) {
            // Если что-то пошло не так (нет карты, нет денег) — сохраняем статус FAILED
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            log.error("Ошибка проведения платежа: {}", e.getMessage());
        }

        // 7. Отправляем результат в Rental Service (чтобы он выдал повербанк или отменил аренду)
        sendResult(payment, event.getRentalId());

        // 8. Публикуем событие об изменении статуса платежа (для истории / аналитики)
        kafkaProducer.sendPaymentStatusChanged(PaymentStatusChangedEvent.builder()
                .paymentId(payment.getId().toString())
                .rentalId(event.getRentalId())
                .oldStatus(PaymentStatus.PENDING.name())
                .newStatus(payment.getStatus().name())
                .build());
    }

    /**
     * Обработка запроса на привязку новой банковской карты.
     */
    @Transactional
    public void bindCard(CardBindRequestEvent event) {
        log.info("Привязка новой карты для пользователя: {}", event.getUserId());

        // Валидация карты
        if (!isValidCardNumber(event.getCardNumber()) || !isValidExpiryDate(event.getExpiryDate())) {
            log.warn("Ошибка валидации карты для пользователя {}: неверный номер или срок действия", event.getUserId());
            kafkaProducer.sendCardBindResult(CardBindResultEvent.builder()
                    .requestId(event.getRequestId())
                    .userId(event.getUserId())
                    .success(false)
                    .errorMessage("Некорректные данные карты")
                    .build());
            return;
        }

        // В MVP мы эмулируем банковский процессинг и просто сохраняем данные "как есть",
        // начислив бонусные 10 000 условных единиц для тестов.
        Card card = Card.builder()
                .userId(UUID.fromString(event.getUserId()))
                .cardNumber(event.getCardNumber())
                .holderName(event.getHolderName())
                .expiryDate(event.getExpiryDate())
                .balance(new BigDecimal("10000.00"))  // Эмуляция начального баланса
                .build();

        card = java.util.Objects.requireNonNull(cardRepository.save(card));

        // Отправляем результат об успешной привязке обратно
        kafkaProducer.sendCardBindResult(CardBindResultEvent.builder()
                .requestId(event.getRequestId())
                .userId(event.getUserId())
                .cardId(card.getId().toString())
                .success(true)
                .build());
    }

    /** Валидация номера карты по алгоритму Луна (Luhn algorithm) */
    private boolean isValidCardNumber(String cardNumber) {
        if (cardNumber == null || !cardNumber.matches("\\d{13,19}")) {
            return false;
        }
        int sum = 0;
        boolean alternate = false;
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int n = Integer.parseInt(cardNumber.substring(i, i + 1));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    /** Простейшая валидация срока действия карты (формат MM/YY и дата в будущем) */
    private boolean isValidExpiryDate(String expiryDate) {
        if (expiryDate == null || !expiryDate.matches("^(0[1-9]|1[0-2])/\\d{2}$")) {
            return false;
        }
        try {
            String[] parts = expiryDate.split("/");
            int expMonth = Integer.parseInt(parts[0]);
            int expYear = Integer.parseInt(parts[1]) + 2000;
            
            java.time.YearMonth currentYearMonth = java.time.YearMonth.now();
            java.time.YearMonth cardYearMonth = java.time.YearMonth.of(expYear, expMonth);
            
            return !cardYearMonth.isBefore(currentYearMonth);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Вспомогательный метод для отправки результата транзакции в Kafka.
     */
    private void sendResult(Payment payment, String rentalId) {
        String errorMessage = null;
        if (payment.getStatus() == PaymentStatus.FAILED) {
            errorMessage = "Отказ в проведении платежа (возможно недостаточно средств)";
        }
        
        kafkaProducer.sendPaymentResult(PaymentResultEvent.builder()
                .rentalId(rentalId)
                .paymentId(payment.getId().toString())
                .status(payment.getStatus().name())
                .idempotencyKey(payment.getIdempotencyKey())
                .errorMessage(errorMessage)
                .build());
    }
}
