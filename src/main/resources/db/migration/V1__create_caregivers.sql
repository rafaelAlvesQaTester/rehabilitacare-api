CREATE TABLE caregivers (
    id UUID PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    senha_hash VARCHAR(100) NOT NULL,
    tipo_vinculo VARCHAR(20) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_caregivers_email UNIQUE (email),
    CONSTRAINT ck_caregivers_tipo_vinculo
        CHECK (tipo_vinculo IN ('FAMILIAR', 'PROFISSIONAL'))
);
