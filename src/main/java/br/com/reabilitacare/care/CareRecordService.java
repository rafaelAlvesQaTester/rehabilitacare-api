package br.com.reabilitacare.care;

import br.com.reabilitacare.auth.Caregiver;
import br.com.reabilitacare.auth.CaregiverNotFoundException;
import br.com.reabilitacare.auth.CaregiverRepository;
import br.com.reabilitacare.patient.Patient;
import br.com.reabilitacare.patient.InvalidPaginationException;
import br.com.reabilitacare.patient.PatientNotFoundException;
import br.com.reabilitacare.patient.PatientRepository;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CareRecordService {

    private static final Duration ALLOWED_CLOCK_SKEW = Duration.ofMinutes(2);
    private static final int MAX_PAGE_SIZE = 50;

    private final CareRecordRepository careRecordRepository;
    private final PatientRepository patientRepository;
    private final CaregiverRepository caregiverRepository;

    public CareRecordService(
            CareRecordRepository careRecordRepository,
            PatientRepository patientRepository,
            CaregiverRepository caregiverRepository) {
        this.careRecordRepository = careRecordRepository;
        this.patientRepository = patientRepository;
        this.caregiverRepository = caregiverRepository;
    }

    @Transactional
    public CareRecordResponse create(
            UUID caregiverId,
            UUID patientId,
            CareRecordCreateRequest request) {
        Caregiver caregiver = requireActiveCaregiver(caregiverId);
        Patient patient = findOwnedPatient(patientId, caregiverId);
        validateRequest(request);
        Instant occurredAt = request.occurredAt().toInstant();
        if (occurredAt.isAfter(Instant.now().plus(ALLOWED_CLOCK_SKEW))) {
            throw new InvalidCareRecordException();
        }

        CareRecord careRecord = new CareRecord(
                patient,
                caregiver,
                request.careType(),
                request.status(),
                occurredAt,
                request.notes(),
                normalizedReason(request.reasonNotPerformed()));
        return CareRecordResponse.from(careRecordRepository.save(careRecord));
    }

    @Transactional(readOnly = true)
    public CareRecordPageResponse list(
            UUID caregiverId,
            UUID patientId,
            int page,
            int size,
            OffsetDateTime from,
            OffsetDateTime to,
            CareType careType) {
        requireActiveCaregiver(caregiverId);
        findOwnedPatient(patientId, caregiverId);
        validatePagination(page, size);
        validatePeriod(from, to);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        Page<CareRecord> records = careRecordRepository.findAll(
                searchSpecification(patientId, caregiverId, from, to, careType),
                pageable);
        return new CareRecordPageResponse(
                records.getContent().stream().map(CareRecordResponse::from).toList(),
                records.getNumber(),
                records.getSize(),
                records.getTotalElements(),
                records.getTotalPages(),
                records.isLast());
    }

    @Transactional(readOnly = true)
    public CareRecordResponse get(UUID caregiverId, UUID patientId, UUID recordId) {
        requireActiveCaregiver(caregiverId);
        findOwnedPatient(patientId, caregiverId);
        CareRecord careRecord = careRecordRepository
                .findByIdAndPatient_IdAndCaregiver_Id(recordId, patientId, caregiverId)
                .orElseThrow(CareRecordNotFoundException::new);
        return CareRecordResponse.from(careRecord);
    }

    private Caregiver requireActiveCaregiver(UUID caregiverId) {
        return caregiverRepository.findByIdAndAtivoTrue(caregiverId)
                .orElseThrow(CaregiverNotFoundException::new);
    }

    private Patient findOwnedPatient(UUID patientId, UUID caregiverId) {
        return patientRepository.findByIdAndCuidadorResponsavel_Id(patientId, caregiverId)
                .orElseThrow(PatientNotFoundException::new);
    }

    private void validateRequest(CareRecordCreateRequest request) {
        boolean reasonProvided = request.reasonNotPerformed() != null
                && !request.reasonNotPerformed().isBlank();
        if (request.status() == CareRecordStatus.NAO_REALIZADO && !reasonProvided) {
            throw new InvalidCareRecordException();
        }
        if (request.status() == CareRecordStatus.REALIZADO && reasonProvided) {
            throw new InvalidCareRecordException();
        }
    }

    private String normalizedReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPaginationException();
        }
    }

    private void validatePeriod(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && from.toInstant().isAfter(to.toInstant())) {
            throw new InvalidCareRecordException();
        }
    }

    private Instant toInstant(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.toInstant();
    }

    private Specification<CareRecord> searchSpecification(
            UUID patientId,
            UUID caregiverId,
            OffsetDateTime from,
            OffsetDateTime to,
            CareType careType) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(
                    root.<Patient>get("patient").<UUID>get("id"), patientId));
            predicates.add(criteriaBuilder.equal(
                    root.<Caregiver>get("caregiver").<UUID>get("id"), caregiverId));

            Path<Instant> occurredAt = root.get("occurredAt");
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        occurredAt, from.toInstant()));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        occurredAt, to.toInstant()));
            }
            if (careType != null) {
                predicates.add(criteriaBuilder.equal(root.get("careType"), careType));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
