package com.lizaveta.service.auth;

import org.springframework.stereotype.Service;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class PasswordService {

    public String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salted = password + salt;
            byte[] hash = digest.digest(salted.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка хэширования", e);
        }
    }

    public boolean verifyPassword(String rawPassword, String salt, String expectedHash) {
        String actualHash = hashPassword(rawPassword, salt);
        return expectedHash.equals(actualHash);
    }
}