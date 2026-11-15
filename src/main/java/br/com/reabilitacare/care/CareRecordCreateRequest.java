package br.com.reabilitacare.care;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CareRecordCreateRequest(
        @NotNull
        CareType careType,
        @NotNull
        CareRecordStatus status,
        @NotNull
        OffsetDateTime occurredAt,
        @Size(max = 2000)
        String notes,
        @Size(max = 500)
        String reasonNotPerformed) {
}
