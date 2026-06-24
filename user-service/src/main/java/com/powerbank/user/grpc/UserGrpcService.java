package com.powerbank.user.grpc;

import com.powerbank.proto.user.*;
import com.powerbank.user.entity.User;
import com.powerbank.user.service.AuthService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * Внутренний gRPC-сервис для межсервисного взаимодействия.
 * Не торчит наружу (не доступен через Kong Gateway).
 * Используется другими микросервисами для получения данных пользователя.
 */
@GrpcService
@RequiredArgsConstructor
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    private final AuthService authService;

    /**
     * Возвращает внутренние данные пользователя по его Keycloak ID (из токена).
     */
    @Override
    public void getUserByExternalId(GetUserByExternalIdRequest request,
                                     StreamObserver<UserResponse> responseObserver) {
        try {
            // 1. Ищем пользователя в базе
            User user = authService.getUserByExternalId(request.getExternalId());
            
            // 2. Строим ответ. Ручной маппинг.
            UserResponse response = UserResponse.newBuilder()
                    .setId(user.getId().toString())
                    .setPhone(user.getPhone())
                    .setExternalId(user.getExternalId())
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            // Если не нашли — возвращаем ошибку NOT_FOUND
            responseObserver.onError(io.grpc.Status.NOT_FOUND
                    .withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
