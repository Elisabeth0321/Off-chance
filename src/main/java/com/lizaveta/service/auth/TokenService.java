package com.lizaveta.service.auth;

import org.springframework.stereotype.Service;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;

@Service
public class TokenService {

    public String generateToken() {
        return UUID.randomUUID().toString();
    }

    public Cookie createAccessTokenCookie(String accessToken, boolean rememberMe) {
        return createCookie("access_token", accessToken, rememberMe);
    }

    public Cookie createRefreshTokenCookie(String refreshToken, boolean rememberMe) {
        return createCookie("refresh_token", refreshToken, rememberMe);
    }

    public Cookie deleteAccessTokenCookie() {
        return deleteCookie("access_token");
    }

    public Cookie deleteRefreshTokenCookie() {
        return deleteCookie("refresh_token");
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

    public void addCookies(HttpServletResponse response, Cookie... cookies) {
        for (Cookie cookie : cookies) {
            response.addCookie(cookie);
        }
    }

    private Cookie createCookie(String name, String value, boolean rememberMe) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(rememberMe ? 60 * 60 * 24 * 30 : -1);
        return cookie;
    }

    private Cookie deleteCookie(String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        return cookie;
    }
}