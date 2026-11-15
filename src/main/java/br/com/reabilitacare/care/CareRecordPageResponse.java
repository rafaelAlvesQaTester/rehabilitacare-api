package br.com.reabilitacare.care;

import java.util.List;

public record CareRecordPageResponse(
        List<CareRecordResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {
}
