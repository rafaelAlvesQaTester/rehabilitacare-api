package br.com.reabilitacare.patient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PatientResponse(
        UUID id,
        String nome,
        LocalDate dataNascimento,
        PatientCondition tipoCondicao,
        String observacoesGerais,
        UUID cuidadorResponsavelId,
        Instant criadoEm,
        Instant atualizadoEm) {

    public static PatientResponse from(Patient patient) {
        return new PatientResponse(
                patient.getId(),
                patient.getNome(),
                patient.getDataNascimento(),
                patient.getTipoCondicao(),
                patient.getObservacoesGerais(),
                patient.getCuidadorResponsavelId(),
                patient.getCriadoEm(),
                patient.getAtualizadoEm());
    }
}
