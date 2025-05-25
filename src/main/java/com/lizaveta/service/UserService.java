package com.lizaveta.service;

import com.lizaveta.model.User;
import com.lizaveta.model.userDTO.AuthResponseDTO;
import com.lizaveta.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User register(String login, String rawPassword, String email) {
        if (login == null || login.isBlank() || rawPassword == null || rawPassword.isBlank() || email == null || email.isBlank()) {
            throw new IllegalArgumentException("Все поля обязательны для заполнения.");
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Пользователь с таким email уже существует.");
        }

        String salt = generateSalt();
        String passwordHash = hashPassword(rawPassword, salt);

        User user = User.builder()
                .name(login)
                .email(email)
                .salt(salt)
                .passwordHash(passwordHash)
                .timeLastLogin(Instant.now())
                .build();

        userRepository.save(user);
        return user;
    }

    public Pair<Optional<AuthResponseDTO>, User> login(String email, String rawPassword, boolean rememberMe, HttpServletResponse response) {
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) return Pair.of(Optional.empty(), null);

        User user = userOpt.get();
        String hash = hashPassword(rawPassword, user.getSalt());

        if (!user.getPasswordHash().equals(hash)) return Pair.of(Optional.empty(), user);

        String accessToken = generateToken();
        String refreshToken = generateToken();

        user.setAccessToken(accessToken);
        user.setRefreshToken(refreshToken);
        user.setTimeLastLogin(Instant.now());
        userRepository.save(user);

        int maxAge = rememberMe ? 60 * 60 * 24 * 30 : -1; // 30 дней или сессионная

        Cookie accessCookie = new Cookie("access_token", accessToken);
        accessCookie.setHttpOnly(true);
        accessCookie.setPath("/");
        accessCookie.setMaxAge(maxAge);

        Cookie refreshCookie = new Cookie("refresh_token", refreshToken);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(maxAge);

        response.addCookie(accessCookie);
        response.addCookie(refreshCookie);

        AuthResponseDTO authResponse = new AuthResponseDTO(accessToken, refreshToken);
        return Pair.of(Optional.of(authResponse), user);
    }

    public Optional<AuthResponseDTO> refreshAccessToken(String refreshToken) {
        return userRepository.findByRefreshToken(refreshToken)
                .map(user -> {
                    String newAccessToken = generateToken();
                    String newRefreshToken = generateToken();
                    user.setAccessToken(newAccessToken);
                    user.setRefreshToken(newRefreshToken);
                    userRepository.save(user);
                    return new AuthResponseDTO(newAccessToken, newRefreshToken);
                });
    }

    public Optional<User> getUserByAccessToken(String accessToken) {
        return userRepository.findByAccessToken(accessToken);
    }

    public boolean logoutByAccessToken(String accessToken, HttpServletResponse response) {
        Optional<User> userOpt = getUserByAccessToken(accessToken);

        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();
        user.setAccessToken(null);
        user.setRefreshToken(null);
        userRepository.save(user);

        Cookie accessCookie = new Cookie("access_token", "");
        accessCookie.setPath("/");
        accessCookie.setHttpOnly(true);
        accessCookie.setMaxAge(0);

        Cookie refreshCookie = new Cookie("refresh_token", "");
        refreshCookie.setPath("/");
        refreshCookie.setHttpOnly(true);
        refreshCookie.setMaxAge(0);

        response.addCookie(accessCookie);
        response.addCookie(refreshCookie);

        return true;
    }

    public void updateUser(User user, String newUsername, String newPassword) {
        boolean changed = false;

        if (newUsername != null && !newUsername.isBlank() && !newUsername.equals(user.getName())) {
            user.setName(newUsername);
            changed = true;
        }

        if (newPassword != null && !newPassword.isBlank()) {
            String newSalt = generateSalt();
            String newHash = hashPassword(newPassword, newSalt);
            user.setSalt(newSalt);
            user.setPasswordHash(newHash);
            changed = true;
        }

        if (changed) {
            userRepository.save(user);
        }
    }

    public void deleteAccount(String userId) {
        userRepository.deleteById(userId);
    }

    public String extractTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) return null;

        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (cookie.getName().equals("access_token")) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String generateToken() {
        return UUID.randomUUID().toString();
    }

    private String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salted = password + salt;
            byte[] hash = digest.digest(salted.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка хэширования", e);
        }
    }
}
