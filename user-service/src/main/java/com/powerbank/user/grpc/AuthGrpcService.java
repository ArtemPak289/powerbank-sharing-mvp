package com.powerbank.user.grpc;

import com.powerbank.proto.auth.*;
import com.powerbank.user.service.AuthService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Map;

/**
 * gRPC-контроллер для аутентификации.
 * Предоставляет внешнее API, которое транскодируется Kong-ом в обычный REST/JSON.
 */
@GrpcService
@Slf4j
@RequiredArgsConstructor
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private final AuthService authService;

    /**
     * Обработчик запроса на отправку OTP кода.
     */
    @Override
    public void requestOtp(RequestOtpRequest request, StreamObserver<RequestOtpResponse> responseObserver) {
        try {
            // 1. Вызываем бизнес-логику генерации кода
            String message = authService.requestOtp(request.getPhone());
            
            // 2. Строим успешный ответ
            RequestOtpResponse response = RequestOtpResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage(message)
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Ошибка запроса OTP", e);
            // 3. Строим ответ с ошибкой, чтобы клиент (Kong) получил JSON { "success": false, "message": "..." }
            RequestOtpResponse response = RequestOtpResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * Обработчик проверки OTP кода и выдачи токенов.
     */
    @Override
    public void verifyOtp(VerifyOtpRequest request, StreamObserver<VerifyOtpResponse> responseObserver) {
        try {
            // 1. Передаем введенный код на проверку
            Map<String, Object> tokens = authService.verifyOtp(request.getPhone(), request.getCode());
            
            // 2. Строим ответ. Ручной маппинг полей из Map в gRPC-класс.
            VerifyOtpResponse response = VerifyOtpResponse.newBuilder()
                    .setAccessToken((String) tokens.get("access_token"))
                    .setRefreshToken((String) tokens.get("refresh_token"))
                    .setExpiresIn(((Number) tokens.get("expires_in")).longValue())
                    .setUserId((String) tokens.getOrDefault("user_id", ""))
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Ошибка проверки OTP", e);
            // Возвращаем статус ошибки (Kong переведет его в 400 Bad Request)
            responseObserver.onError(io.grpc.Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage()).asRuntimeException());
        }
    }

    /**
     * Обработчик обновления токенов.
     */
    @Override
    public void refreshToken(RefreshTokenRequest request, StreamObserver<TokenResponse> responseObserver) {
        try {
            Map<String, Object> tokens = authService.refreshToken(request.getRefreshToken());
            
            TokenResponse response = TokenResponse.newBuilder()
                    .setAccessToken((String) tokens.get("access_token"))
                    .setRefreshToken((String) tokens.get("refresh_token"))
                    .setExpiresIn(((Number) tokens.get("expires_in")).longValue())
                    .build();
                    
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Ошибка обновления токена", e);
            // Статус UNAUTHENTICATED (Kong переведет в 401 Unauthorized)
            responseObserver.onError(io.grpc.Status.UNAUTHENTICATED
                    .withDescription(e.getMessage()).asRuntimeException());
        }
    }

    /**
     * Обработчик получения профиля пользователя.
     */
    @Override
    public void getProfile(GetProfileRequest request, StreamObserver<ProfileResponse> responseObserver) {
        // DECISIONS: В MVP мы пока не реализовали передачу `user_id` из JWT-метаданных от Kong в gRPC-контекст.
        // Поэтому возвращаем UNIMPLEMENTED. Это частая практика для первых версий, если фича не критична для старта.
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED
                .withDescription("Метод GetProfile требует извлечения JWT из метаданных (еще не реализовано)").asRuntimeException());
    }
}
