package br.com.reabilitacare.care;

import br.com.reabilitacare.auth.Caregiver;
import br.com.reabilitacare.patient.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "care_records",
        indexes = @Index(
                name = "idx_care_records_patient_caregiver_occurred",
                columnList = "patient_id, caregiver_id, occurred_at, id"))
public class CareRecord {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caregiver_id", nullable = false)
    private Caregiver caregiver;

    @Enumerated(EnumType.STRING)
    @Column(name = "care_type", nullable = false, length = 30)
    private CareType careType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CareRecordStatus status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "reason_not_performed", length = 500)
    private String reasonNotPerformed;

    protected CareRecord() {
    }

    public CareRecord(
            Patient patient,
            Caregiver caregiver,
            CareType careType,
            CareRecordStatus status,
            Instant occurredAt,
            String notes,
            String reasonNotPerformed) {
        this.id = UUID.randomUUID();
        this.patient = patient;
        this.caregiver = caregiver;
        this.careType = careType;
        this.status = status;
        this.occurredAt = occurredAt;
        this.recordedAt = Instant.now();
        this.notes = notes;
        this.reasonNotPerformed = reasonNotPerformed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPatientId() {
        return patient.getId();
    }

    public UUID getCaregiverId() {
        return caregiver.getId();
    }

    public CareType getCareType() {
        return careType;
    }

    public CareRecordStatus getStatus() {
        return status;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getNotes() {
        return notes;
    }

    public String getReasonNotPerformed() {
        return reasonNotPerformed;
    }

    @PrePersist
    protected void initializeRecordedAt() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }
}
