package com.powerbank.user.service;

import com.powerbank.user.entity.OtpCode;
import com.powerbank.user.entity.User;
import com.powerbank.user.repository.OtpCodeRepository;
import com.powerbank.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Сервис аутентификации.
 * Главная точка входа для генерации OTP, их проверки и выдачи токенов (JWT) через Keycloak.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final KeycloakService keycloakService;


    private static final int OTP_TTL_MINUTES = 5;
    private final Random random = new Random();

    /**
     * Запросить одноразовый пароль (OTP) для указанного номера телефона.
     * 
     * @param phone номер телефона пользователя.
     * @return информационное сообщение об успешной отправке.
     */
    @Transactional
    public String requestOtp(String phone) {
        // 1. Генерируем случайный 6-значный код.
        String code = generateOtp();

        // 2. Создаем запись в БД о том, что код выдан.
        OtpCode otp = OtpCode.builder()
                .phone(phone)
                .code(code)
                .verified(false)
                .expiresAt(OffsetDateTime.now().plusMinutes(OTP_TTL_MINUTES))
                .build();
                
        // 3. Сохраняем в базу данных.
        otpCodeRepository.save(otp);

        // 4. В реальном проекте здесь мы бы отправили код через SMS или Telegram.
        // Для MVP мы просто выводим его в логи.
        log.info(">>> Сгенерирован OTP для {}: {} (срок действия {} мин) <<<", phone, code, OTP_TTL_MINUTES);

        return "OTP код отправлен на номер " + phone + " (см. логи сервера)";
    }

    /**
     * Проверить OTP и выдать токены доступа.
     * 
     * @param phone номер телефона пользователя.
     * @param code код, который ввел пользователь.
     * @return Map с токенами (access_token, refresh_token) и ID пользователя.
     */
    @Transactional
    public Map<String, Object> verifyOtp(String phone, String code) {
        // 1. Ищем последний непроверенный код для этого номера телефона.
        Optional<OtpCode> optionalOtp = otpCodeRepository.findTopByPhoneAndVerifiedFalseOrderByCreatedAtDesc(phone);
        
        if (optionalOtp.isEmpty()) {
            throw new IllegalArgumentException("Для данного номера нет ожидающих проверки кодов.");
        }
        
        OtpCode otp = optionalOtp.get();

        // 2. Проверяем, не истек ли срок действия кода.
        if (otp.isExpired()) {
            throw new IllegalArgumentException("Срок действия OTP кода истек.");
        }
        
        // 3. Сверяем сам код.
        if (!otp.getCode().equals(code)) {
            throw new IllegalArgumentException("Неверный код OTP.");
        }

        // 4. Отмечаем код как успешно проверенный, чтобы его нельзя было использовать дважды.
        otp.setVerified(true);
        otpCodeRepository.save(otp);

        // 5. Ищем пользователя в БД. Если его нет — регистрируем нового.
        User user = null;
        Optional<User> optionalUser = userRepository.findByPhone(phone);
        if (optionalUser.isPresent()) {
            user = optionalUser.get();
        } else {
            user = registerNewUser(phone);
        }

        // 6. Запрашиваем JWT токены у Keycloak, используя сгенерированный технический пароль.
        Map<String, Object> keycloakTokens = keycloakService.getToken(phone, user.getKeycloakPassword());
        
        // 7. Создаем новую изменяемую мапу (т.к. RestTemplate может вернуть неизменяемую).
        Map<String, Object> responseTokens = new HashMap<>();
        for (Map.Entry<String, Object> entry : keycloakTokens.entrySet()) {
            responseTokens.put(entry.getKey(), entry.getValue());
        }
        
        // 8. Добавляем внутренний ID пользователя в ответ.
        responseTokens.put("user_id", user.getId().toString());

        return responseTokens;
    }

    /**
     * Обновить access_token по refresh_token.
     * 
     * @param refreshToken токен обновления.
     * @return Map с новыми токенами.
     */
    public Map<String, Object> refreshToken(String refreshToken) {
        return keycloakService.refreshToken(refreshToken);
    }

    /**
     * Получить пользователя по ID из Keycloak (вызывается другими сервисами по токену).
     */
    public User getUserByExternalId(String externalId) {
        Optional<User> userOpt = userRepository.findByExternalId(externalId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("Пользователь с таким Keycloak ID не найден в БД.");
        }
        return userOpt.get();
    }

    /**
     * Получить пользователя по внутреннему UUID.
     */
    public User getUserById(UUID userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("Пользователь с таким ID не найден.");
        }
        return userOpt.get();
    }

    // DECISIONS: Создание пользователя вынесено в приватный метод для упрощения логики verifyOtp.
    // При регистрации мы сразу создаем теневой аккаунт в Keycloak со случайным сложным паролем.
    private User registerNewUser(String phone) {
        // 1. Генерируем сложный случайный пароль (пользователь будет входить только по OTP).
        String password = UUID.randomUUID().toString();
        
        // 2. Создаем аккаунт в Keycloak.
        String keycloakId = keycloakService.createUser(phone, password);

        // 3. Сохраняем пользователя в нашу локальную БД.
        User user = User.builder()
                .phone(phone)
                .externalId(keycloakId)
                .keycloakPassword(password)
                .build();

        return userRepository.save(user);
    }

    // DECISIONS: Простая генерация случайного 6-значного числа. 
    // Для production стоит использовать криптографически безопасный SecureRandom.
    private String generateOtp() {
        int otp = 100_000 + random.nextInt(900_000);
        return String.valueOf(otp);
    }
}
