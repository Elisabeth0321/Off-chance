package com.lizaveta.repository;

import com.lizaveta.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    Optional<User> findByAccessToken(String token);
    Optional<User> findByRefreshToken(String token);
    boolean existsByEmail(String email);
}