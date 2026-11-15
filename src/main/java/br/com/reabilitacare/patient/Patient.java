package br.com.reabilitacare.patient;

import br.com.reabilitacare.auth.Caregiver;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "patients",
        indexes = @Index(
                name = "idx_patients_caregiver_created",
                columnList = "cuidador_responsavel_id, criado_em, id"))
public class Patient {

    @Id
    private UUID id;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_condicao", nullable = false, length = 20)
    private PatientCondition tipoCondicao;

    @Column(name = "observacoes_gerais", columnDefinition = "TEXT")
    private String observacoesGerais;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuidador_responsavel_id", nullable = false)
    private Caregiver cuidadorResponsavel;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Patient() {
    }

    public Patient(
            String nome,
            LocalDate dataNascimento,
            PatientCondition tipoCondicao,
            String observacoesGerais,
            Caregiver cuidadorResponsavel) {
        this.id = UUID.randomUUID();
        this.nome = nome;
        this.dataNascimento = dataNascimento;
        this.tipoCondicao = tipoCondicao;
        this.observacoesGerais = observacoesGerais;
        this.cuidadorResponsavel = cuidadorResponsavel;
        Instant now = Instant.now();
        this.criadoEm = now;
        this.atualizadoEm = now;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public PatientCondition getTipoCondicao() {
        return tipoCondicao;
    }

    public String getObservacoesGerais() {
        return observacoesGerais;
    }

    public UUID getCuidadorResponsavelId() {
        return cuidadorResponsavel.getId();
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public void update(
            String nome,
            LocalDate dataNascimento,
            PatientCondition tipoCondicao,
            String observacoesGerais) {
        this.nome = nome;
        this.dataNascimento = dataNascimento;
        this.tipoCondicao = tipoCondicao;
        this.observacoesGerais = observacoesGerais;
    }

    @PrePersist
    protected void initializeTimestamps() {
        Instant now = Instant.now();
        if (criadoEm == null) {
            criadoEm = now;
        }
        if (atualizadoEm == null) {
            atualizadoEm = now;
        }
    }

    @PreUpdate
    protected void updateTimestamp() {
        atualizadoEm = Instant.now();
    }
}
