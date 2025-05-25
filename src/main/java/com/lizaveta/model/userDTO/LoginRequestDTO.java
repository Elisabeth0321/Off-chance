package com.lizaveta.model.userDTO;

public record LoginRequestDTO(String email, String password, boolean rememberMe) {}