package com.lizaveta.controller;

import com.lizaveta.model.User;
import com.lizaveta.service.storage.StorageService;
import com.lizaveta.service.auth.UserService;
import com.lizaveta.model.userDTO.AuthResponseDTO;
import com.lizaveta.model.userDTO.RefreshRequestDTO;
import com.lizaveta.model.userDTO.RegisterRequestDTO;
import com.lizaveta.model.userDTO.LoginRequestDTO;
import com.lizaveta.model.userDTO.UpdateRequestDTO;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final StorageService storageService;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        String token = userService.extractTokenFromCookies(request);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Нет токена");
        }

        return userService.getUserByAccessToken(token)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Неверный или просроченный токен"));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequestDTO request) {
        try {
            User user = userService.register(request.username(), request.password(), request.email());
            createUserDirectory(user.getId());
            return ResponseEntity.ok(Map.of("message", "Пользователь зарегистрирован."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Ошибка на сервере при регистрации."));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO request, HttpServletResponse response) {
        Pair<Optional<AuthResponseDTO>, Optional<User>> result = userService.login(
                request.email(),
                request.password(),
                request.rememberMe(),
                response
        );
        Optional<AuthResponseDTO> authOpt = result.getFirst();
        if (authOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Неверный email или пароль"));
        }

        Optional<User> userOpt = result.getSecond();
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Пользователь с таким email не найден"));
        }

        createUserDirectory(userOpt.get().getId());
        return ResponseEntity.ok(authOpt.get());
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequestDTO request) {
        return userService.refreshAccessToken(request.refreshToken())
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Неверный refresh токен"));
    }

    @DeleteMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = userService.extractTokenFromCookies(request);

        if (accessToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Access token отсутствует");
        }

        boolean success = userService.logoutByAccessToken(accessToken, response);

        if (success) {
            return ResponseEntity.ok(Map.of("message", "Вы успешно вышли из аккаунта"));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Неверный или просроченный токен");
        }
    }

    @PutMapping("/update")
    public ResponseEntity<Map<String, String>> update(@RequestBody UpdateRequestDTO request,
                                                          HttpServletRequest httpRequest) {
        String token = userService.extractTokenFromCookies(httpRequest);

        if (token == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Access token отсутствует"));
        }

        return userService.getUserByAccessToken(token)
                .map(user -> {
                    try {
                        userService.updateUser(user, request.username(), request.password());
                        return ResponseEntity.ok(Map.of("message", "Пользователь обновлён"));
                    } catch (IllegalArgumentException e) {
                        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
                    }
                })
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Неверный или просроченный токен")));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, String>> deleteAccount(HttpServletRequest request, HttpServletResponse response) {
        String token = userService.extractTokenFromCookies(request);

        if (token == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Access token отсутствует"));
        }

        return userService.getUserByAccessToken(token)
                .map(user -> {
                    userService.logoutByAccessToken(token, response);
                    userService.deleteAccount(user.getId());

                    deleteUserDirectory(user.getId());
                    return ResponseEntity.ok(Map.of("message", "Аккаунт удалён"));
                })
                .orElseGet(() -> ResponseEntity
                        .status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Неверный или просроченный токен")));
    }

    private void createUserDirectory(String userId) {
        try {
            storageService.createUserFolder(userId);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при проверке/создании пользовательской папки", e);
        }
    }

    private void deleteUserDirectory(String userId) {
        try {
            storageService.deleteUserFolder(userId);
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при удалении пользовательской папки", e);
        }
    }
}
