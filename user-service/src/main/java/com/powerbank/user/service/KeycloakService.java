package com.powerbank.user.service;

import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Интеграция с Keycloak.
 * Управляет созданием теневых аккаунтов пользователей и получением/обновлением JWT токенов.
 */
@Service
@Slf4j
public class KeycloakService {

    private final Keycloak keycloakAdmin;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    public KeycloakService(
            @Value("${keycloak.server-url}") String serverUrl,
            @Value("${keycloak.admin.username}") String adminUsername,
            @Value("${keycloak.admin.password}") String adminPassword) {
        // Инициализируем админский клиент Keycloak
        this.keycloakAdmin = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm("master")
                .username(adminUsername)
                .password(adminPassword)
                .clientId("admin-cli")
                .build();
    }

    /**
     * Создать нового пользователя в Keycloak.
     * 
     * @param phone номер телефона (используется как username).
     * @param password сгенерированный сервером пароль.
     * @return внутренний ID пользователя в Keycloak.
     */
    public String createUser(String phone, String password) {
        // 1. Формируем сущность пользователя для Keycloak
        UserRepresentation user = new UserRepresentation();
        user.setUsername(phone);
        user.setEmail(phone.replace("+", "") + "@powerbank.local");
        user.setFirstName("User");
        user.setLastName(phone);
        user.setEnabled(true);

        // 2. Устанавливаем пароль
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        user.setCredentials(List.of(credential));

        // 3. Отправляем запрос на создание
        try (Response response = keycloakAdmin.realm(realm).users().create(user)) {
            String userId;
            if (response.getStatus() == 201) {
                String locationHeader = response.getHeaderString("Location");
                userId = locationHeader.substring(locationHeader.lastIndexOf('/') + 1);
                log.info("Создан пользователь в Keycloak: phone={}, keycloakId={}", phone, userId);
            } else if (response.getStatus() == 409) {
                userId = findUserIdByUsername(phone);
                log.info("Пользователь уже существует в Keycloak: phone={}, keycloakId={}", phone, userId);
            } else {
                throw new RuntimeException("Не удалось создать пользователя в Keycloak. Статус: " + response.getStatus());
            }

            // Обязательно принудительно задаем пароль (спасает при 409 Conflict и в новых версиях Keycloak)
            keycloakAdmin.realm(realm).users().get(userId).resetPassword(credential);

            return userId;
        }
    }

    /**
     * Получить JWT токены (access, refresh) через механизм пароля (Direct Access Grants).
     * 
     * @param username логин (телефон).
     * @param password технический пароль.
     * @return Map с токенами.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getToken(String username, String password) {
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", clientId);
        body.add("username", username);
        body.add("password", password);

        org.springframework.core.ParameterizedTypeReference<Map<String, Object>> responseType = 
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {};

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                tokenUrl, HttpMethod.POST, new HttpEntity<>(body, headers), responseType);

        return response.getBody();
    }

    /**
     * Обновить access токен с помощью refresh токена.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> refreshToken(String refreshToken) {
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", clientId);
        body.add("refresh_token", refreshToken);

        org.springframework.core.ParameterizedTypeReference<Map<String, Object>> responseType = 
                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {};

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                tokenUrl, HttpMethod.POST, new HttpEntity<>(body, headers), responseType);

        return response.getBody();
    }

    /**
     * Найти ID пользователя по его логину (телефону).
     */
    private String findUserIdByUsername(String username) {
        List<UserRepresentation> users = keycloakAdmin.realm(realm).users()
                .searchByUsername(username, true);
        if (users.isEmpty()) {
            throw new RuntimeException("Пользователь не найден в Keycloak: " + username);
        }
        return users.get(0).getId();
    }
}
