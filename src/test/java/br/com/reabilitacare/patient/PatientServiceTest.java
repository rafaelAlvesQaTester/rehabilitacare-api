package br.com.reabilitacare.patient;

import br.com.reabilitacare.auth.Caregiver;
import br.com.reabilitacare.auth.CaregiverNotFoundException;
import br.com.reabilitacare.auth.CaregiverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
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
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private CaregiverRepository caregiverRepository;

    @InjectMocks
    private PatientService patientService;

    private Caregiver caregiver;

    @BeforeEach
    void setUp() {
        caregiver = new Caregiver(
                "Cuidador Fictício",
                "caregiver@example.test",
                "$2a$12$fake-hash",
                br.com.reabilitacare.auth.RelationshipType.FAMILIAR);
    }

    @Test
    void createUsesTheActiveAuthenticatedCaregiver() {
        stubActiveCaregiver();
        PatientCreateRequest request = new PatientCreateRequest(
                "  Paciente Fictício  ",
                LocalDate.of(1980, 1, 1),
                PatientCondition.NAO_INFORMADO,
                null);
        when(patientRepository.save(any(Patient.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PatientResponse response = patientService.create(caregiver.getId(), request);

        assertThat(response.nome()).isEqualTo("Paciente Fictício");
        assertThat(response.cuidadorResponsavelId()).isEqualTo(caregiver.getId());
        assertThat(response.tipoCondicao()).isEqualTo(PatientCondition.NAO_INFORMADO);
    }

    @Test
    void listUsesCaregiverScopedQueryAndBoundedPageSize() {
        stubActiveCaregiver();
        Patient patient = patient(caregiver);
        when(patientRepository.findAllByCuidadorResponsavel_Id(
                caregiver.getId(), PageRequest.of(
                        0,
                        10,
                        org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Order.desc("criadoEm"),
                                org.springframework.data.domain.Sort.Order.asc("id")))))
                .thenReturn(new PageImpl<>(List.of(patient)));

        PatientPageResponse response = patientService.list(caregiver.getId(), 0, 10);

        assertThat(response.content()).extracting(PatientResponse::id)
                .containsExactly(patient.getId());
        verify(patientRepository).findAllByCuidadorResponsavel_Id(
                org.mockito.ArgumentMatchers.eq(caregiver.getId()),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listRejectsUnboundedOrInvalidPagination() {
        stubActiveCaregiver();
        assertThatThrownBy(() -> patientService.list(caregiver.getId(), 0, 51))
                .isInstanceOf(InvalidPaginationException.class);
        assertThatThrownBy(() -> patientService.list(caregiver.getId(), -1, 10))
                .isInstanceOf(InvalidPaginationException.class);

        verify(patientRepository, never()).findAllByCuidadorResponsavel_Id(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getAndUpdateRequireAnOwnedPatient() {
        stubActiveCaregiver();
        UUID patientId = UUID.randomUUID();
        Patient patient = patient(caregiver);
        when(patientRepository.findByIdAndCuidadorResponsavel_Id(
                patientId, caregiver.getId())).thenReturn(Optional.of(patient));
        PatientUpdateRequest update = new PatientUpdateRequest(
                "Nome Atualizado",
                null,
                PatientCondition.PARAPLEGIA,
                "Observação fictícia");

        PatientResponse response = patientService.update(caregiver.getId(), patientId, update);

        assertThat(response.nome()).isEqualTo("Nome Atualizado");
        assertThat(response.dataNascimento()).isNull();
        assertThat(response.cuidadorResponsavelId()).isEqualTo(caregiver.getId());
        verify(patientRepository).findByIdAndCuidadorResponsavel_Id(
                patientId, caregiver.getId());
    }

    @Test
    void getReturnsTheSameNotFoundForMissingOrUnownedPatient() {
        stubActiveCaregiver();
        UUID patientId = UUID.randomUUID();
        when(patientRepository.findByIdAndCuidadorResponsavel_Id(
                patientId, caregiver.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.get(caregiver.getId(), patientId))
                .isInstanceOf(PatientNotFoundException.class);
        verify(patientRepository).findByIdAndCuidadorResponsavel_Id(
                patientId, caregiver.getId());
    }

    @Test
    void inactiveCaregiverCannotAccessPatients() {
        UUID inactiveId = UUID.randomUUID();
        when(caregiverRepository.findByIdAndAtivoTrue(inactiveId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.list(inactiveId, 0, 20))
                .isInstanceOf(CaregiverNotFoundException.class);
        verify(patientRepository, never()).findAllByCuidadorResponsavel_Id(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private Patient patient(Caregiver owner) {
        return new Patient(
                "Paciente Fictício",
                null,
                PatientCondition.NAO_INFORMADO,
                null,
                owner);
    }

    private void stubActiveCaregiver() {
        when(caregiverRepository.findByIdAndAtivoTrue(caregiver.getId()))
                .thenReturn(Optional.of(caregiver));
    }
}
