package br.com.reabilitacare.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Informe seu email.")
        @Email(message = "Informe um email válido.")
        @Size(max = 254, message = "O email deve ter até 254 caracteres.")
        String email,
        @NotBlank(message = "Informe sua senha.")
        @Size(max = 72, message = "Email ou senha inválidos.")
        String senha) {
}
