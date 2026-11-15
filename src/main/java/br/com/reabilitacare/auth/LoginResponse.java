package br.com.reabilitacare.auth;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        CaregiverResponse usuario) {
}
