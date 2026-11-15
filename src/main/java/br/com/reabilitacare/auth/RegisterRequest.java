package br.com.reabilitacare.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Informe seu nome.")
        @Size(max = 120, message = "O nome deve ter até 120 caracteres.")
        String nome,
        @NotBlank(message = "Informe seu email.")
        @Email(message = "Informe um email válido.")
        @Size(max = 254, message = "O email deve ter até 254 caracteres.")
        String email,
        @NotBlank(message = "Informe uma senha.")
        @Pattern(
                regexp = "^[\\x20-\\x7E]{12,72}$",
                message = "A senha deve ter de 12 a 72 caracteres ASCII.")
        String senha,
        @NotNull(message = "Informe o tipo de vínculo.")
        RelationshipType tipoVinculo) {
}
