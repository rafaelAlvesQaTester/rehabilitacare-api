package br.com.reabilitacare.care;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface CareRecordRepository extends
        JpaRepository<CareRecord, UUID>,
        JpaSpecificationExecutor<CareRecord> {

    Optional<CareRecord> findByIdAndPatient_IdAndCaregiver_Id(
            UUID recordId,
            UUID patientId,
            UUID caregiverId);
}
