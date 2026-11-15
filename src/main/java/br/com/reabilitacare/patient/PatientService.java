package br.com.reabilitacare.patient;

import br.com.reabilitacare.auth.Caregiver;
import br.com.reabilitacare.auth.CaregiverNotFoundException;
import br.com.reabilitacare.auth.CaregiverRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PatientService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PatientRepository patientRepository;
    private final CaregiverRepository caregiverRepository;

    public PatientService(
            PatientRepository patientRepository,
            CaregiverRepository caregiverRepository) {
        this.patientRepository = patientRepository;
        this.caregiverRepository = caregiverRepository;
    }

    @Transactional
    public PatientResponse create(UUID caregiverId, PatientCreateRequest request) {
        Caregiver caregiver = caregiverRepository.findByIdAndAtivoTrue(caregiverId)
                .orElseThrow(CaregiverNotFoundException::new);
        Patient patient = new Patient(
                request.nome().trim(),
                request.dataNascimento(),
                request.tipoCondicao(),
                request.observacoesGerais(),
                caregiver);
        return PatientResponse.from(patientRepository.save(patient));
    }

    @Transactional(readOnly = true)
    public PatientPageResponse list(UUID caregiverId, int page, int size) {
        ensureActiveCaregiver(caregiverId);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPaginationException();
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("criadoEm"), Sort.Order.asc("id")));
        Page<Patient> patients = patientRepository
                .findAllByCuidadorResponsavel_Id(caregiverId, pageable);
        return new PatientPageResponse(
                patients.getContent().stream().map(PatientResponse::from).toList(),
                patients.getNumber(),
                patients.getSize(),
                patients.getTotalElements(),
                patients.getTotalPages(),
                patients.isLast());
    }

    @Transactional(readOnly = true)
    public PatientResponse get(UUID caregiverId, UUID patientId) {
        ensureActiveCaregiver(caregiverId);
        Patient patient = findOwnedPatient(caregiverId, patientId);
        return PatientResponse.from(patient);
    }

    @Transactional
    public PatientResponse update(
            UUID caregiverId,
            UUID patientId,
            PatientUpdateRequest request) {
        ensureActiveCaregiver(caregiverId);
        Patient patient = findOwnedPatient(caregiverId, patientId);
        patient.update(
                request.nome().trim(),
                request.dataNascimento(),
                request.tipoCondicao(),
                request.observacoesGerais());
        return PatientResponse.from(patient);
    }

    private Patient findOwnedPatient(UUID caregiverId, UUID patientId) {
        return patientRepository
                .findByIdAndCuidadorResponsavel_Id(patientId, caregiverId)
                .orElseThrow(PatientNotFoundException::new);
    }

    private void ensureActiveCaregiver(UUID caregiverId) {
        caregiverRepository.findByIdAndAtivoTrue(caregiverId)
                .orElseThrow(CaregiverNotFoundException::new);
    }
}
