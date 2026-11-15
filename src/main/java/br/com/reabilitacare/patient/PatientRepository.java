package br.com.reabilitacare.patient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Page<Patient> findAllByCuidadorResponsavel_Id(UUID caregiverId, Pageable pageable);

    Optional<Patient> findByIdAndCuidadorResponsavel_Id(UUID patientId, UUID caregiverId);
}
