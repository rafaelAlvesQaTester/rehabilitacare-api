package br.com.reabilitacare.auth;

import java.time.Instant;
import java.util.UUID;

public record CaregiverResponse(
        UUID id,
        String nome,
        String email,
        RelationshipType tipoVinculo,
        Instant criadoEm,
        boolean ativo) {

    public static CaregiverResponse from(Caregiver caregiver) {
        return new CaregiverResponse(
                caregiver.getId(),
                caregiver.getNome(),
                caregiver.getEmail(),
                caregiver.getTipoVinculo(),
                caregiver.getCriadoEm(),
                caregiver.isAtivo());
    }
}
