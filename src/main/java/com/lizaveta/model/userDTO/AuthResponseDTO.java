package com.lizaveta.model.userDTO;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class AuthResponseDTO {
    public String accessToken;
    public String refreshToken;
}
