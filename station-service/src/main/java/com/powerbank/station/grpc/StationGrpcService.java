package com.powerbank.station.grpc;

import com.powerbank.proto.station.*;
import com.powerbank.station.entity.Powerbank;
import com.powerbank.station.entity.Station;
import com.powerbank.station.repository.PowerbankRepository;
import com.powerbank.station.repository.StationRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * gRPC-контроллер для работы со станциями (поиск ближайших, просмотр слотов).
 * Транскодируется через Kong в REST API для мобильного приложения.
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
public class StationGrpcService extends StationServiceGrpc.StationServiceImplBase {

    private final StationRepository stationRepository;
    private final PowerbankRepository powerbankRepository;

    /**
     * Возвращает список всех станций (с фильтром по радиусу, если переданы координаты).
     */
    @Override
    public void getStations(GetStationsRequest request, StreamObserver<GetStationsResponse> responseObserver) {
        try {
            List<Station> stations;
            
            // 1. Проверяем, передан ли радиус и координаты
            if (request.getRadiusMeters() > 0 && request.getLatitude() != 0 && request.getLongitude() != 0) {
                // Ищем станции в заданном радиусе (радиус переводим из метров в градусы/километры для SQL)
                stations = stationRepository.findNearbyStations(
                        request.getLatitude(),
                        request.getLongitude(),
                        request.getRadiusMeters() / 1000.0
                );
            } else {
                // Если нет — отдаем все (в MVP это ОК)
                stations = stationRepository.findAll();
            }

            // 2. Вручную мапим список станций из БД в gRPC ответ
            GetStationsResponse.Builder responseBuilder = GetStationsResponse.newBuilder();
            for (Station station : stations) {
                StationResponse stationResponse = mapToProto(station);
                responseBuilder.addStations(stationResponse);
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Ошибка при получении списка станций", e);
            responseObserver.onError(io.grpc.Status.INTERNAL
                    .withDescription("Внутренняя ошибка сервера: " + e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Возвращает детальную информацию по одной станции (включая список слотов и их статус).
     */
    @Override
    public void getStationById(GetStationByIdRequest request, StreamObserver<StationResponse> responseObserver) {
        try {
            // 1. Ищем станцию
            Station station = stationRepository.findById(UUID.fromString(request.getStationId()))
                    .orElseThrow(() -> new IllegalArgumentException("Станция не найдена"));

            // 2. Конвертируем и отправляем
            responseObserver.onNext(mapToProto(station));
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Ошибка при получении данных станции", e);
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Ручной маппер Станции из БД в gRPC формат (KISS).
     */
    private StationResponse mapToProto(Station station) {
        // DECISIONS: Делаем отдельный запрос в БД для получения повербанков этой станции.
        // Это лучше, чем использовать @OneToMany(fetch = EAGER), потому что мы сами контролируем, когда нам нужны связанные данные.
        List<Powerbank> powerbanks = powerbankRepository.findByStationId(station.getId());

        int availableSlotsCount = 0;
        List<SlotInfo> slotInfos = new ArrayList<>();

        // Проходимся по всем повербанкам обычным циклом (без Stream API для прозрачности)
        for (Powerbank pb : powerbanks) {
            
            // Считаем свободные (готовые к выдаче) повербанки
            if (pb.getStatus() == Powerbank.PowerbankStatus.AVAILABLE) {
                availableSlotsCount++;
            }

            // Определяем, занят ли физический слот. 
            // Слот занят, если повербанк физически в нем (то есть статус не RENTED и stationId указан).
            boolean isOccupied = (pb.getStatus() != Powerbank.PowerbankStatus.RENTED) && (pb.getStationId() != null);
            
            // Обрабатываем возможный null в номере слота
            int slotNumber = 0;
            if (pb.getSlotNumber() != null) {
                slotNumber = pb.getSlotNumber();
            }

            // Формируем DTO слота
            SlotInfo slotInfo = SlotInfo.newBuilder()
                    .setSlotNumber(slotNumber)
                    .setOccupied(isOccupied)
                    .setPowerBankId(pb.getId().toString())
                    .build();
                    
            slotInfos.add(slotInfo);
        }

        // Формируем итоговый объект станции
        return StationResponse.newBuilder()
                .setId(station.getId().toString())
                .setName(station.getName())
                .setAddress(station.getAddress())
                .setLatitude(station.getLatitude())
                .setLongitude(station.getLongitude())
                .setTotalSlots(station.getTotalSlots())
                .setAvailableSlots(availableSlotsCount)
                .addAllSlots(slotInfos)
                .build();
    }
}
