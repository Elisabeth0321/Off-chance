package com.lizaveta.service.auth;

import com.lizaveta.model.User;
import com.lizaveta.model.userDTO.AuthResponseDTO;
import com.lizaveta.repository.UserRepository;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final PasswordService passwordService;

    public User register(String login, String rawPassword, String email) {
        if (login == null || login.isBlank() || rawPassword == null || rawPassword.isBlank() || email == null || email.isBlank()) {
            throw new IllegalArgumentException("Все поля обязательны для заполнения.");
        }

        if (userRepository.existsByEmail(email)) {
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
        return user;
    }

    public Pair<Optional<AuthResponseDTO>, Optional<User>> login(String email, String rawPassword, boolean rememberMe, HttpServletResponse response) {
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) return Pair.of(Optional.empty(), Optional.empty());

        User user = userOpt.get();

        if (!passwordService.verifyPassword(rawPassword, user.getSalt(), user.getPasswordHash())) {
            return Pair.of(Optional.empty(), Optional.empty());
        }

        String accessToken = tokenService.generateToken();
        String refreshToken = tokenService.generateToken();

        user.setAccessToken(accessToken);
        user.setRefreshToken(refreshToken);
        user.setTimeLastLogin(Instant.now());
        userRepository.save(user);

        Cookie accessCookie = tokenService.createAccessTokenCookie(accessToken, rememberMe);
        Cookie refreshCookie = tokenService.createRefreshTokenCookie(refreshToken, rememberMe);
        tokenService.addCookies(response, accessCookie, refreshCookie);

        AuthResponseDTO authResponse = new AuthResponseDTO(accessToken, refreshToken);
        return Pair.of(Optional.of(authResponse), Optional.of(user));
    }

    public Optional<AuthResponseDTO> refreshAccessToken(String refreshToken) {
        return userRepository.findByRefreshToken(refreshToken)
                .map(user -> {
                    String newAccessToken = tokenService.generateToken();
                    String newRefreshToken = tokenService.generateToken();
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

        if (userOpt.isEmpty()) return false;

        User user = userOpt.get();
        user.setAccessToken(null);
        user.setRefreshToken(null);
        userRepository.save(user);

        tokenService.addCookies(response,
                tokenService.deleteAccessTokenCookie(),
                tokenService.deleteRefreshTokenCookie()
        );

        return true;
    }

    public void updateUser(User user, String newUsername, String newPassword) {
        boolean changed = false;

        if (newUsername != null && !newUsername.isBlank() && !newUsername.equals(user.getName())) {
            user.setName(newUsername);
            changed = true;
        }

        if (newPassword != null && !newPassword.isBlank()) {
            String newSalt = passwordService.generateSalt();
            String newHash = passwordService.hashPassword(newPassword, newSalt);
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
        return tokenService.extractTokenFromCookies(request);
    }
}
