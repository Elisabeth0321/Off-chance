package com.lizaveta.service.auth;

import org.springframework.stereotype.Service;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.UUID;

@Service
public class TokenService {

    public String generateToken() {
        return UUID.randomUUID().toString();
    }

    public String extractTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) return null;

        for (Cookie cookie : request.getCookies()) {
            if (cookie.getName().equals("access_token")) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public void addCookies(HttpServletResponse response, String name1, String value1, String name2, String value2, boolean rememberMe) {
        response.addHeader("Set-Cookie", buildCookie(name1, value1, rememberMe).toString());
        response.addHeader("Set-Cookie", buildCookie(name2, value2, rememberMe).toString());
    }

    public void deleteCookies(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildExpiredCookie("access_token").toString());
        response.addHeader("Set-Cookie", buildExpiredCookie("refresh_token").toString());
    }

    private ResponseCookie buildCookie(String name, String value, boolean rememberMe) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Lax")
                .maxAge(rememberMe ? Duration.ofDays(30) : Duration.ofSeconds(-1))
                .build();
    }

    private ResponseCookie buildExpiredCookie(String name) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ZERO)
                .build();
    }
}