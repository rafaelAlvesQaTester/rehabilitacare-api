package br.com.reabilitacare.patient;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PatientCreateRequest(
        @NotBlank
        @Size(max = 120)
        String nome,
        @PastOrPresent
        LocalDate dataNascimento,
        @NotNull
        PatientCondition tipoCondicao,
        @Size(max = 2000)
        String observacoesGerais) {
}
