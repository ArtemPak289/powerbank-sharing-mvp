# MVP Шеринга Повербанков (Powerbank Sharing MVP)

Это микросервисный MVP для приложения по аренде повербанков.

## Технологический стек
* **Java 17** & **Spring Boot 3.3.6**
* **PostgreSQL** (Multi-tenant через один инстанс, но раздельные логические БД)
* **Kafka** (KRaft mode, асинхронное взаимодействие на основе событий)
* **gRPC** (Синхронное общение между микросервисами)
* **Kong API Gateway** (Транскодирование REST в gRPC)
* **Keycloak** (Аутентификация OAuth2/OIDC)
* **Liquibase** (Миграции баз данных)

## Микросервисы
1. **User Service** (Порт 8081, 9091 gRPC): Аутентификация по OTP, интеграция с Keycloak, управление токенами.
2. **Payment Service** (Порт 8082): Эмуляция управления банковскими картами, списание баланса через Kafka.
3. **Station Service** (Порт 8083, 9093 gRPC): Эмулятор IoT станции. Управляет слотами, обрабатывает команды на блокировку/отстрел (eject).
4. **Rental Service** (Порт 8084, 9094 gRPC): Главный оркестратор. Реализует State Machine (Конечный автомат) для процесса аренды (Saga).

## Требования
* Docker & Docker Compose
* Java 17 & Maven (для локальной сборки)

## Как запустить

1. **Сборка проекта** (необходима для работы самих приложений):
   ```bash
   mvn clean install -DskipTests
   ```
   *(Примечание: Микросервисы еще не контейнеризованы в docker-compose, поэтому после запуска инфраструктуры вы будете запускать их локально).*

2. **Запуск инфраструктуры (Postgres, Kafka, Keycloak, Kong)**:
   ```bash
   docker-compose up -d
   ```
   Подождите 30-60 секунд, пока Keycloak и Kafka не перейдут в статус healthy.

3. **Запуск микросервисов**:
   Запустите каждое Spring Boot приложение локально в отдельных терминалах:
   ```bash
   mvn -pl user-service spring-boot:run
   mvn -pl payment-service spring-boot:run
   mvn -pl station-service spring-boot:run
   mvn -pl rental-service spring-boot:run
   ```

## Как тестировать API (через Kong Gateway)

Kong доступен на `localhost:8000`. Он транскодирует REST-запросы в вызовы gRPC.

### 1. Получить OTP и Токен
```bash
# Запросить OTP
curl -X POST http://localhost:8000/auth/phone \
     -H "Content-Type: application/json" \
     -d '{"phone": "+998901234567"}'

# Подтвердить OTP (Код печатается в логах User Service)
curl -X POST http://localhost:8000/auth/verify \
     -H "Content-Type: application/json" \
     -d '{"phone": "+998901234567", "code": "<CODE_FROM_LOGS>"}'
```
*Сохраните `access_token` и `user_id`.*

### 2. Привязать карту
Так как Payment Service слушает только Kafka, этот запрос выполняется через REST эндпоинт Rental Service (который транскодируется в gRPC, а затем отправляет команду в Kafka).
```bash
curl -X POST http://localhost:8000/v1/cards \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <TOKEN>" \
     -d '{"cardNumber": "4242424242424242", "holderName": "Artem", "expiryDate": "12/25"}'
```
*Проверьте логи Payment Service, чтобы увидеть создание карты.*

### 3. Список станций
```bash
curl -X GET http://localhost:8000/v1/stations \
     -H "Authorization: Bearer <TOKEN>"
```
*Сохраните один `stationId` из ответа.*

### 4. Начать аренду
```bash
curl -X POST http://localhost:8000/v1/rental \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <TOKEN>" \
     -d '{"stationId": "<STATION_ID>", "cardId": "<CARD_ID>"}'
```
*Это запускает конечный автомат: `LOCK_REQUESTED` -> Kafka -> Station Service блокирует слот -> `PAYMENT_REQUESTED` -> Kafka -> Payment Service списывает 50.00 -> `EJECT_REQUESTED` -> Kafka -> Station Service отстреливает повербанк -> `ACTIVE`.*

### 5. Проверить статус аренды
```bash
curl -X GET http://localhost:8000/v1/rental/<RENTAL_ID>/status \
     -H "Authorization: Bearer <TOKEN>"
```

### 6. Завершить аренду
```bash
curl -X POST http://localhost:8000/v1/rental/finish \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer <TOKEN>" \
     -d '{"rentalId": "<RENTAL_ID>", "returnStationId": "<STATION_ID>"}'
```
*Это запускает процесс возврата и финальный расчет (списание) стоимости аренды.*