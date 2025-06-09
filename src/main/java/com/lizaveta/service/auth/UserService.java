package com.lizaveta.service.auth;

import com.lizaveta.model.User;
import com.lizaveta.model.userDTO.AuthResponseDTO;
import com.lizaveta.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final PasswordService passwordService;

    public User register(String login, String rawPassword, String email) {
        log.info("Попытка регистрации нового пользователя: email={}, login={}", email, login);

        if (rawPassword == null || rawPassword.isBlank() || email == null || email.isBlank()) {
            log.warn("Ошибка регистрации: отсутствуют обязательные поля.");
            throw new IllegalArgumentException("Все поля обязательны для заполнения.");
        }

        if (userRepository.existsByEmail(email)) {
            log.warn("Регистрация отклонена: пользователь с email {} уже существует.", email);
            throw new IllegalArgumentException("Пользователь с таким email уже существует.");
        }

        String salt = passwordService.generateSalt();
        String passwordHash = passwordService.hashPassword(rawPassword, salt);

        User user = User.builder()
                .name(login)
                .email(email)
                .salt(salt)
                .passwordHash(passwordHash)
                .timeLastLogin(Instant.now())
                .build();

        userRepository.save(user);
        log.info("Пользователь успешно зарегистрирован: id={}, email={}", user.getId(), email);
        return user;
    }

    public Pair<Optional<AuthResponseDTO>, Optional<User>> login(String email,
                                                                 String rawPassword,
                                                                 boolean rememberMe,
                                                                 HttpServletResponse response) {
        log.info("Попытка входа с email={}", email);
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            log.warn("Вход неудачен: пользователь с email {} не найден", email);
            return Pair.of(Optional.empty(), Optional.empty());
        }

        User user = userOpt.get();

        if (!passwordService.verifyPassword(rawPassword, user.getSalt(), user.getPasswordHash())) {
            log.warn("Вход неудачен: неверный пароль для email={}", email);
            return Pair.of(Optional.empty(), Optional.empty());
        }

        String accessToken = tokenService.generateToken();
        String refreshToken = tokenService.generateToken();

        user.setAccessToken(accessToken);
        user.setRefreshToken(refreshToken);
        user.setTimeLastLogin(Instant.now());
        userRepository.save(user);

        tokenService.addCookies(response,
                "access_token", accessToken,
                "refresh_token", refreshToken,
                rememberMe
        );

        log.info("Вход успешен: userId={}, email={}", user.getId(), email);
        AuthResponseDTO authResponse = new AuthResponseDTO(accessToken, refreshToken);
        return Pair.of(Optional.of(authResponse), Optional.of(user));
    }

    public Optional<AuthResponseDTO> refreshAccessToken(String refreshToken) {
        log.info("Попытка обновления access token по refresh токену");
        return userRepository.findByRefreshToken(refreshToken)
                .map(user -> {
                    String newAccessToken = tokenService.generateToken();
                    String newRefreshToken = tokenService.generateToken();
                    user.setAccessToken(newAccessToken);
                    user.setRefreshToken(newRefreshToken);
                    userRepository.save(user);
                    log.info("Токены обновлены для пользователя id={}", user.getId());
                    return new AuthResponseDTO(newAccessToken, newRefreshToken);
                });
    }

    public Optional<User> getUserByAccessToken(String accessToken) {
        log.debug("Получение пользователя по access токену");
        return userRepository.findByAccessToken(accessToken);
    }

    public boolean logoutByAccessToken(String accessToken, HttpServletResponse response) {
        log.info("Попытка выхода по access токену");
        Optional<User> userOpt = getUserByAccessToken(accessToken);

        if (userOpt.isEmpty()) {
            log.warn("Выход неудачен: access токен не найден");
            return false;
        }

        User user = userOpt.get();
        user.setAccessToken(null);
        user.setRefreshToken(null);
        userRepository.save(user);

        tokenService.deleteCookies(response);
        log.info("Пользователь успешно вышел: id={}", user.getId());

        return true;
    }

    public void updateUser(User user, String newUsername, String newPassword) {
        log.info("Обновление данных пользователя id={}", user.getId());
        boolean changed = false;

        if (newUsername != null && !newUsername.isBlank() && !newUsername.equals(user.getName())) {
            log.debug("Изменение имени пользователя: {} → {}", user.getName(), newUsername);
            user.setName(newUsername);
            changed = true;
        }

        if (newPassword != null && !newPassword.isBlank()) {
            log.debug("Изменение пароля пользователя id={}", user.getId());
            String newSalt = passwordService.generateSalt();
            String newHash = passwordService.hashPassword(newPassword, newSalt);
            user.setSalt(newSalt);
            user.setPasswordHash(newHash);
            changed = true;
        }

        if (changed) {
            userRepository.save(user);
            log.info("Пользователь обновлён: id={}", user.getId());
        } else {
            log.info("Данные пользователя не изменены: id={}", user.getId());
        }
    }

    public void deleteAccount(String userId) {
        log.info("Удаление аккаунта пользователя: id={}", userId);
        userRepository.deleteById(userId);
    }

    public String extractTokenFromCookies(HttpServletRequest request) {
        log.debug("Извлечение access токена из cookies");
        return tokenService.extractTokenFromCookies(request);
    }
}
