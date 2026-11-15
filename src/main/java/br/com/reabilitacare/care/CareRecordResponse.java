package br.com.reabilitacare.care;

import java.time.Instant;
import java.util.UUID;

public record CareRecordResponse(
        UUID id,
        UUID patientId,
        UUID caregiverId,
        CareType careType,
        CareRecordStatus status,
        Instant occurredAt,
        Instant recordedAt,
        String notes,
        String reasonNotPerformed) {

    public static CareRecordResponse from(CareRecord careRecord) {
        return new CareRecordResponse(
                careRecord.getId(),
                careRecord.getPatientId(),
                careRecord.getCaregiverId(),
                careRecord.getCareType(),
                careRecord.getStatus(),
                careRecord.getOccurredAt(),
                careRecord.getRecordedAt(),
                careRecord.getNotes(),
                careRecord.getReasonNotPerformed());
    }
}
