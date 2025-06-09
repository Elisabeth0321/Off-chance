package com.lizaveta.service.auth;

import org.springframework.stereotype.Service;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
public class TokenService {

    public String generateToken() {
        String token = UUID.randomUUID().toString();
        log.debug("Сгенерирован новый токен: {}", token);
        return token;
    }

    public String extractTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) {
            log.debug("Cookie отсутствуют в запросе");
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if ("access_token".equals(cookie.getName())) {
                log.debug("Найден access_token в cookie");
                return cookie.getValue();
            }
        }

        log.debug("access_token не найден в cookie");
        return null;
    }

    public void addCookies(HttpServletResponse response,
                           String name1, String value1,
                           String name2, String value2,
                           boolean rememberMe) {

        ResponseCookie cookie1 = buildCookie(name1, value1, rememberMe);
        ResponseCookie cookie2 = buildCookie(name2, value2, rememberMe);

        response.addHeader("Set-Cookie", cookie1.toString());
        response.addHeader("Set-Cookie", cookie2.toString());

        log.debug("Установлены cookie: {}={}, {}={}", name1, value1, name2, value2);
    }

    public void deleteCookies(HttpServletResponse response) {
        ResponseCookie expiredAccess = buildExpiredCookie("access_token");
        ResponseCookie expiredRefresh = buildExpiredCookie("refresh_token");

        response.addHeader("Set-Cookie", expiredAccess.toString());
        response.addHeader("Set-Cookie", expiredRefresh.toString());

        log.debug("Удалены cookie: access_token, refresh_token");
    }

    private ResponseCookie buildCookie(String name, String value, boolean rememberMe) {
        long maxAge = rememberMe ? Duration.ofDays(30).getSeconds() : -1;
        log.debug("Создание cookie: name={}, rememberMe={}, maxAge={}", name, rememberMe, maxAge);

        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Lax")
                .maxAge(rememberMe ? Duration.ofDays(30) : Duration.ofSeconds(-1))
                .build();
    }

    private ResponseCookie buildExpiredCookie(String name) {
        log.debug("Создание просроченной cookie: {}", name);
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
    }
}
