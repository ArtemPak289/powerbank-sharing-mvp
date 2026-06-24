package com.powerbank.common.event;

/**
 * Константы с названиями Kafka-топиков.
 * Хранятся в общем модуле, чтобы избежать опечаток при отправке/чтении.
 */
public final class KafkaTopics {

    // --- Команды для станций (Rental -> Station) ---
    public static final String ACQUIRE_CABINET_LOCK_EVENT = "acquire-cabinet-lock-event";
    public static final String EJECT_POWERBANK_EVENT = "eject-powerbank-event";
    public static final String RETURN_POWERBANK_EVENT = "return-powerbank-event";

    // --- Ответы от станций (Station -> Rental) ---
    public static final String ACQUIRE_CABINET_LOCK_RESULT = "acquire-cabinet-lock-result";
    public static final String EJECT_POWERBANK_RESULT = "eject-powerbank-result";
    public static final String RETURN_POWERBANK_RESULT = "return-powerbank-result";

    // --- Оплата (Rental <-> Payment) ---
    public static final String PAYMENT_REQUEST = "payment-request";
    public static final String PAYMENT_RESULT = "payment-result";
    public static final String PAYMENT_EVENTS = "payment-events";

    // --- Привязка карт (Rental -> Payment -> Rental) ---
    public static final String CARD_BIND_REQUEST = "card-bind-request";
    public static final String CARD_BIND_RESULT = "card-bind-result";

    private KafkaTopics() {
    }
}
