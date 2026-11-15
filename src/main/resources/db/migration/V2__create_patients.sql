CREATE TABLE patients (
    id UUID PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    data_nascimento DATE,
    tipo_condicao VARCHAR(20) NOT NULL,
    observacoes_gerais TEXT,
    cuidador_responsavel_id UUID NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_patients_caregiver
        FOREIGN KEY (cuidador_responsavel_id)
        REFERENCES caregivers(id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_patients_tipo_condicao
        CHECK (tipo_condicao IN ('PARAPLEGIA', 'TETRAPLEGIA', 'NAO_INFORMADO'))
);

CREATE INDEX idx_patients_caregiver_created
    ON patients (cuidador_responsavel_id, criado_em DESC, id);
