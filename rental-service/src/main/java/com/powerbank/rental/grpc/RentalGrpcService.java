package com.powerbank.rental.grpc;

import com.powerbank.proto.rental.*;
import com.powerbank.rental.entity.Rental;
import com.powerbank.rental.repository.RentalRepository;
import com.powerbank.rental.service.RentalOrchestrator;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * gRPC-контроллер для управления арендой.
 * Обрабатывает запросы от мобильного приложения (через Kong Gateway).
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
public class RentalGrpcService extends RentalServiceGrpc.RentalServiceImplBase {

    private final RentalOrchestrator orchestrator;
    private final RentalRepository rentalRepository;
    private final com.powerbank.rental.kafka.RentalKafkaProducer kafkaProducer;

    /**
     * Привязка банковской карты (передача события в Kafka)
     */
    @Override
    public void bindCard(BindCardRequest request, StreamObserver<BindCardResponse> responseObserver) {
        try {
            if (request.getUserId() == null || request.getUserId().isEmpty()) {
                throw new IllegalArgumentException("В запросе отсутствует userId");
            }
            kafkaProducer.sendCardBindRequest(com.powerbank.common.event.CardBindRequestEvent.builder()
                    .requestId(UUID.randomUUID().toString())
                    .userId(request.getUserId())
                    .cardNumber(request.getCardNumber())
                    .holderName(request.getHolderName())
                    .expiryDate(request.getExpiryDate())
                    .build());
                    
            BindCardResponse response = BindCardResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Запрос на привязку карты отправлен в обработку")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Ошибка при привязке карты", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Запрос на создание новой аренды (Пользователь нажал "Взять повербанк").
     */
    @Override
    public void createRental(CreateRentalRequest request, StreamObserver<CreateRentalResponse> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            String idempotencyKey = request.getIdempotencyKey();

            // 1. Запускаем сагу
            Rental rental = orchestrator.startRental(
                    userId,
                    UUID.fromString(request.getStationId()),
                    UUID.fromString(request.getCardId()),
                    idempotencyKey
            );

            // 2. Отвечаем клиенту (он дальше будет поллить статус через getRentalStatus)
            CreateRentalResponse response = CreateRentalResponse.newBuilder()
                    .setRentalId(rental.getId().toString())
                    .setStatus(rental.getStatus().name())
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Ошибка при создании аренды", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Возвращает текущий статус аренды (используется для лонг-поллинга или просто проверки с фронта).
     */
    @Override
    public void getRentalStatus(GetRentalStatusRequest request, StreamObserver<RentalStatusResponse> responseObserver) {
        try {
            Rental rental = rentalRepository.findById(UUID.fromString(request.getRentalId()))
                    .orElseThrow(() -> new IllegalArgumentException("Аренда не найдена"));

            responseObserver.onNext(mapToProto(rental));
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * История поездок (аренд) пользователя.
     */
    @Override
    public void getRentalHistory(GetRentalHistoryRequest request, StreamObserver<GetRentalHistoryResponse> responseObserver) {
        try {
            UUID userId = UUID.fromString(request.getUserId());
            List<Rental> rentals = rentalRepository.findByUserIdOrderByCreatedAtDesc(userId);

            // DECISIONS: Заменили Stream API на обычный цикл для простоты отладки и чтения
            GetRentalHistoryResponse.Builder responseBuilder = GetRentalHistoryResponse.newBuilder();
            for (Rental rental : rentals) {
                responseBuilder.addRentals(mapToProto(rental));
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Принудительное завершение аренды клиентом.
     * В реальности это событие чаще приходит от физической станции (через MQTT -> Kafka), 
     * но для тестов и MVP оставляем возможность дернуть ручку из приложения.
     */
    @Override
    public void finishRental(FinishRentalRequest request, StreamObserver<FinishRentalResponse> responseObserver) {
        try {
            orchestrator.finishRental(
                    UUID.fromString(request.getRentalId()),
                    UUID.fromString(request.getReturnStationId())
            );
            
            Rental rental = rentalRepository.findById(UUID.fromString(request.getRentalId())).orElseThrow();

            FinishRentalResponse response = FinishRentalResponse.newBuilder()
                    .setRentalId(rental.getId().toString())
                    .setStatus(rental.getStatus().name())
                    .setTotalAmount(rental.getTotalAmount().toString())
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Ошибка при завершении аренды", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Ручной маппер сущности Rental в gRPC формат.
     */
    private RentalStatusResponse mapToProto(Rental rental) {
        RentalStatusResponse.Builder builder = RentalStatusResponse.newBuilder()
                .setRentalId(rental.getId().toString())
                .setStatus(rental.getStatus().name())
                .setStationId(rental.getStationId().toString())
                .setTotalAmount(rental.getTotalAmount().toString());

        if (rental.getPowerBankId() != null) {
            builder.setPowerBankId(rental.getPowerBankId().toString());
        }
        
        if (rental.getStartedAt() != null) {
            builder.setStartedAt(rental.getStartedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        
        if (rental.getFinishedAt() != null) {
            builder.setFinishedAt(rental.getFinishedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }

        return builder.build();
    }
}
