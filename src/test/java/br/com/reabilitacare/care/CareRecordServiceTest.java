package br.com.reabilitacare.care;

import br.com.reabilitacare.auth.Caregiver;
import br.com.reabilitacare.auth.CaregiverNotFoundException;
import br.com.reabilitacare.auth.CaregiverRepository;
import br.com.reabilitacare.patient.Patient;
import br.com.reabilitacare.patient.PatientCondition;
import br.com.reabilitacare.patient.InvalidPaginationException;
import br.com.reabilitacare.patient.PatientNotFoundException;
import br.com.reabilitacare.patient.PatientRepository;
import br.com.reabilitacare.auth.RelationshipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CareRecordServiceTest {

    @Mock
    private CareRecordRepository careRecordRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private CaregiverRepository caregiverRepository;

    @InjectMocks
    private CareRecordService careRecordService;

    private Caregiver caregiver;
    private Patient patient;

    @BeforeEach
    void setUp() {
        caregiver = new Caregiver(
                "Cuidador Fictício",
                "caregiver@example.test",
                "$2a$12$fake-hash",
                RelationshipType.FAMILIAR);
        patient = new Patient(
                "Paciente Fictício",
                null,
                PatientCondition.NAO_INFORMADO,
                null,
                caregiver);
    }

    @Test
    void createStoresOffsetAsAnInstantAndUsesAuthenticatedCaregiver() {
        stubAuthorizedPatient();
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-11-14T20:30:00-03:00");
        when(careRecordRepository.save(any(CareRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CareRecordResponse response = careRecordService.create(
                caregiver.getId(),
                patient.getId(),
                new CareRecordCreateRequest(
                        CareType.HIGIENE,
                        CareRecordStatus.REALIZADO,
                        occurredAt,
                        "Observação fictícia",
                        null));

        assertThat(response.patientId()).isEqualTo(patient.getId());
        assertThat(response.caregiverId()).isEqualTo(caregiver.getId());
        assertThat(response.occurredAt()).isEqualTo(occurredAt.toInstant());
        assertThat(response.recordedAt()).isAfterOrEqualTo(response.occurredAt());
    }

    @Test
    void createAcceptsNotPerformedOnlyWithAReason() {
        stubAuthorizedPatient();
        when(careRecordRepository.save(any(CareRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CareRecordResponse response = careRecordService.create(
                caregiver.getId(),
                patient.getId(),
                new CareRecordCreateRequest(
                        CareType.MOBILIDADE,
                        CareRecordStatus.NAO_REALIZADO,
                        OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5),
                        null,
                        "  A atividade não foi realizada.  "));

        assertThat(response.status()).isEqualTo(CareRecordStatus.NAO_REALIZADO);
        assertThat(response.reasonNotPerformed())
                .isEqualTo("A atividade não foi realizada.");
    }

    @Test
    void createAllowsSmallClockSkewForAnEventSlightlyInTheFuture() {
        stubAuthorizedPatient();
        when(careRecordRepository.save(any(CareRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1);

        CareRecordResponse response = careRecordService.create(
                caregiver.getId(),
                patient.getId(),
                new CareRecordCreateRequest(
                        CareType.HIDRATACAO,
                        CareRecordStatus.REALIZADO,
                        occurredAt,
                        null,
                        null));

        assertThat(response.occurredAt()).isEqualTo(occurredAt.toInstant());
    }

    @Test
    void createRejectsInvalidStatusReasonAndFutureTime() {
        stubAuthorizedPatient();
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);

        assertThatThrownBy(() -> careRecordService.create(
                caregiver.getId(),
                patient.getId(),
                new CareRecordCreateRequest(
                        CareType.HIGIENE,
                        CareRecordStatus.NAO_REALIZADO,
                        past,
                        null,
                        " ")))
                .isInstanceOf(InvalidCareRecordException.class);
        assertThatThrownBy(() -> careRecordService.create(
                caregiver.getId(),
                patient.getId(),
                new CareRecordCreateRequest(
                        CareType.HIGIENE,
                        CareRecordStatus.REALIZADO,
                        past,
                        null,
                        "Motivo incoerente")))
                .isInstanceOf(InvalidCareRecordException.class);
        assertThatThrownBy(() -> careRecordService.create(
                caregiver.getId(),
                patient.getId(),
                new CareRecordCreateRequest(
                        CareType.HIGIENE,
                        CareRecordStatus.REALIZADO,
                        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(3),
                        null,
                        null)))
                .isInstanceOf(InvalidCareRecordException.class);

        verify(careRecordRepository, never()).save(any(CareRecord.class));
    }

    @Test
    void listFiltersByAuthorizedPatientAndOrdersByOccurredAt() {
        stubAuthorizedPatient();
        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3);
        OffsetDateTime to = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        when(careRecordRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<CareRecord>>any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        CareRecordPageResponse response = careRecordService.list(
                caregiver.getId(),
                patient.getId(),
                0,
                10,
                from,
                to,
                CareType.HIGIENE);

        assertThat(response.content()).isEmpty();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(careRecordRepository).findAll(
                org.mockito.ArgumentMatchers.<Specification<CareRecord>>any(),
                pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("occurredAt").getDirection())
                .isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
        assertThat(pageable.getValue().getSort().getOrderFor("id").getDirection())
                .isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
    }

    @Test
    void listRejectsInvalidPeriodAndUnboundedPageSize() {
        stubAuthorizedPatient();
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime end = start.minusMinutes(1);

        assertThatThrownBy(() -> careRecordService.list(
                caregiver.getId(), patient.getId(), 0, 20, start, end, null))
                .isInstanceOf(InvalidCareRecordException.class);
        assertThatThrownBy(() -> careRecordService.list(
                caregiver.getId(), patient.getId(), 0, 51, null, null, null))
                .isInstanceOf(InvalidPaginationException.class);
        verify(careRecordRepository, never()).findAll(
                org.mockito.ArgumentMatchers.<Specification<CareRecord>>any(),
                any(Pageable.class));
    }

    @Test
    void getRequiresAnAuthorizedPatientAndScopedRecordQuery() {
        stubAuthorizedPatient();
        UUID recordId = UUID.randomUUID();
        when(careRecordRepository.findByIdAndPatient_IdAndCaregiver_Id(
                recordId, patient.getId(), caregiver.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> careRecordService.get(
                caregiver.getId(), patient.getId(), recordId))
                .isInstanceOf(CareRecordNotFoundException.class);
        verify(careRecordRepository).findByIdAndPatient_IdAndCaregiver_Id(
                recordId, patient.getId(), caregiver.getId());
    }

    @Test
    void createRejectsMissingOrForeignPatientWithoutSaving() {
        when(caregiverRepository.findByIdAndAtivoTrue(caregiver.getId()))
                .thenReturn(Optional.of(caregiver));
        when(patientRepository.findByIdAndCuidadorResponsavel_Id(
                patient.getId(), caregiver.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> careRecordService.create(
                caregiver.getId(),
                patient.getId(),
                new CareRecordCreateRequest(
                        CareType.HIGIENE,
                        CareRecordStatus.REALIZADO,
                        OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                        null,
                        null)))
                .isInstanceOf(PatientNotFoundException.class);
        verify(careRecordRepository, never()).save(any(CareRecord.class));
    }

    @Test
    void inactiveCaregiverCannotReadHistory() {
        when(caregiverRepository.findByIdAndAtivoTrue(caregiver.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> careRecordService.list(
                caregiver.getId(), patient.getId(), 0, 20, null, null, null))
                .isInstanceOf(CaregiverNotFoundException.class);
        verify(patientRepository, never()).findByIdAndCuidadorResponsavel_Id(
                any(UUID.class), any(UUID.class));
    }

    private void stubAuthorizedPatient() {
        when(caregiverRepository.findByIdAndAtivoTrue(caregiver.getId()))
                .thenReturn(Optional.of(caregiver));
        when(patientRepository.findByIdAndCuidadorResponsavel_Id(
                patient.getId(), caregiver.getId())).thenReturn(Optional.of(patient));
    }
}
