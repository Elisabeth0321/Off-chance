package com.lizaveta.controller;

import com.lizaveta.model.User;
import com.lizaveta.service.auth.UserService;
import com.lizaveta.service.storage.StorageService;
import com.lizaveta.model.userDTO.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final StorageService storageService;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        String token = userService.extractTokenFromCookies(request);
        if (token == null) {
            logger.warn("Попытка получить текущего пользователя без access token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Нет токена");
        }

        return userService.getUserByAccessToken(token)
                .<ResponseEntity<?>>map(user -> {
                    logger.info("Текущий пользователь получен: {}", user.getEmail());
                    return ResponseEntity.ok(user);
                })
                .orElseGet(() -> {
                    logger.warn("Access token недействителен или просрочен");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Неверный или просроченный токен");
                });
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequestDTO request) {
        logger.info("Регистрация пользователя: username={}, email={}", request.username(), request.email());
        try {
            User user = userService.register(request.username(), request.password(), request.email());
            createUserDirectory(user.getId());
            logger.info("Пользователь '{}' успешно зарегистрирован", user.getEmail());
            return ResponseEntity.ok(Map.of("message", "Пользователь зарегистрирован."));
        } catch (IllegalArgumentException e) {
            logger.warn("Ошибка при регистрации: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Внутренняя ошибка при регистрации пользователя", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Ошибка на сервере при регистрации."));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO request, HttpServletResponse response) {
        logger.info("Попытка входа: email={}", request.email());
        Pair<Optional<AuthResponseDTO>, Optional<User>> result = userService.login(
                request.email(),
                request.password(),
                request.rememberMe(),
                response
        );
        Optional<AuthResponseDTO> authOpt = result.getFirst();

        if (authOpt.isEmpty()) {
            logger.warn("Ошибка входа: неверный email или пароль");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Неверный email или пароль"));
        }

        Optional<User> userOpt = result.getSecond();
        if (userOpt.isEmpty()) {
            logger.warn("Ошибка входа: пользователь с email '{}' не найден", request.email());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Пользователь с таким email не найден"));
        }

        createUserDirectory(userOpt.get().getId());
        logger.info("Пользователь '{}' успешно вошёл", userOpt.get().getEmail());
        return ResponseEntity.ok(authOpt.get());
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequestDTO request) {
        logger.info("Запрос обновления access токена");
        return userService.refreshAccessToken(request.refreshToken())
                .<ResponseEntity<?>>map(token -> {
                    logger.info("Access токен успешно обновлён");
                    return ResponseEntity.ok(token);
                })
                .orElseGet(() -> {
                    logger.warn("Ошибка обновления токена: неверный refresh токен");
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Неверный refresh токен");
                });
    }

    @DeleteMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = userService.extractTokenFromCookies(request);
        if (accessToken == null) {
            logger.warn("Попытка выхода без access токена");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access token отсутствует");
        }

        boolean success = userService.logoutByAccessToken(accessToken, response);
        if (success) {
            logger.info("Пользователь успешно вышел из аккаунта");
            return ResponseEntity.ok(Map.of("message", "Вы успешно вышли из аккаунта"));
        } else {
            logger.warn("Ошибка выхода: недействительный токен");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Неверный или просроченный токен");
        }
    }

    @PutMapping("/update")
    public ResponseEntity<Map<String, String>> update(@RequestBody UpdateRequestDTO request,
                                                      HttpServletRequest httpRequest) {
        String token = userService.extractTokenFromCookies(httpRequest);
        if (token == null) {
            logger.warn("Попытка обновления без access токена");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Access token отсутствует"));
        }

        return userService.getUserByAccessToken(token)
                .map(user -> {
                    try {
                        logger.info("Обновление пользователя '{}'", user.getEmail());
                        userService.updateUser(user, request.username(), request.password());
                        return ResponseEntity.ok(Map.of("message", "Пользователь обновлён"));
                    } catch (IllegalArgumentException e) {
                        logger.warn("Ошибка обновления пользователя: {}", e.getMessage());
                        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
                    }
                })
                .orElseGet(() -> {
                    logger.warn("Ошибка обновления: access токен недействителен");
                    return ResponseEntity
                            .status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("message", "Неверный или просроченный токен"));
                });
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, String>> deleteAccount(HttpServletRequest request, HttpServletResponse response) {
        String token = userService.extractTokenFromCookies(request);
        if (token == null) {
            logger.warn("Попытка удаления аккаунта без access токена");
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Access token отсутствует"));
        }

        return userService.getUserByAccessToken(token)
                .map(user -> {
                    logger.info("Удаление аккаунта пользователя '{}'", user.getEmail());
                    userService.logoutByAccessToken(token, response);
                    userService.deleteAccount(user.getId());
                    deleteUserDirectory(user.getId());
                    logger.info("Аккаунт '{}' успешно удалён", user.getEmail());
                    return ResponseEntity.ok(Map.of("message", "Аккаунт удалён"));
                })
                .orElseGet(() -> {
                    logger.warn("Ошибка удаления: access токен недействителен");
                    return ResponseEntity
                            .status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("message", "Неверный или просроченный токен"));
                });
    }

    private void createUserDirectory(String userId) {
        try {
            storageService.createUserFolder(userId);
            logger.info("Папка пользователя '{}' создана (или уже существует)", userId);
        } catch (IOException e) {
            logger.error("Ошибка при создании папки пользователя '{}'", userId, e);
            throw new RuntimeException("Ошибка при проверке/создании пользовательской папки", e);
        }
    }

    private void deleteUserDirectory(String userId) {
        try {
            storageService.deleteUserFolder(userId);
            logger.info("Папка пользователя '{}' удалена", userId);
        } catch (IOException e) {
            logger.error("Ошибка при удалении папки пользователя '{}'", userId, e);
            throw new RuntimeException("Ошибка при удалении пользовательской папки", e);
        }
    }
}
