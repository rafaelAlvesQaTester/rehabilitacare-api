package br.com.reabilitacare.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CaregiverRepository extends JpaRepository<Caregiver, UUID> {

    boolean existsByEmail(String email);

    Optional<Caregiver> findByEmail(String email);

    Optional<Caregiver> findByIdAndAtivoTrue(UUID id);
}
