package com.lizaveta.service.auth;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@Slf4j
public class PasswordService {

    public String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String encodedSalt = Base64.getEncoder().encodeToString(salt);
        log.debug("Сгенерирована новая соль: {}", encodedSalt);
        return encodedSalt;
    }

    public String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salted = password + salt;
            byte[] hash = digest.digest(salted.getBytes());
            String encodedHash = Base64.getEncoder().encodeToString(hash);
            log.debug("Хэширован пароль: {}", encodedHash);
            return encodedHash;
        } catch (Exception e) {
            log.error("Ошибка хэширования пароля", e);
            throw new RuntimeException("Ошибка хэширования", e);
        }
    }

    public boolean verifyPassword(String rawPassword, String salt, String expectedHash) {
        String actualHash = hashPassword(rawPassword, salt);
        boolean match = expectedHash.equals(actualHash);
        log.debug("Проверка пароля: совпадение = {}", match);
        return match;
    }
}
