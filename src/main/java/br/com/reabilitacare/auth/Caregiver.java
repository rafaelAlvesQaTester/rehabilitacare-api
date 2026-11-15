package br.com.reabilitacare.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "caregivers")
public class Caregiver {

    @Id
    private UUID id;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_vinculo", nullable = false, length = 20)
    private RelationshipType tipoVinculo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    protected Caregiver() {
    }

    public Caregiver(
            String nome,
            String email,
            String senhaHash,
            RelationshipType tipoVinculo) {
        this.id = UUID.randomUUID();
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.tipoVinculo = tipoVinculo;
        this.criadoEm = Instant.now();
        this.ativo = true;
    }

    public UUID getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public RelationshipType getTipoVinculo() {
        return tipoVinculo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public boolean isAtivo() {
        return ativo;
    }
}
