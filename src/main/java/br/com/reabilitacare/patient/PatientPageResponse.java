package br.com.reabilitacare.patient;

import java.util.List;

public record PatientPageResponse(
        List<PatientResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {
}
