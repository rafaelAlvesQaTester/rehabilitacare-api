CREATE TABLE care_records (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    caregiver_id UUID NOT NULL,
    care_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes VARCHAR(2000),
    reason_not_performed VARCHAR(500),
    CONSTRAINT fk_care_records_patient
        FOREIGN KEY (patient_id)
        REFERENCES patients(id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_care_records_caregiver
        FOREIGN KEY (caregiver_id)
        REFERENCES caregivers(id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_care_records_care_type
        CHECK (care_type IN (
            'HIGIENE',
            'ALIMENTACAO',
            'HIDRATACAO',
            'POSICIONAMENTO',
            'MOBILIDADE',
            'CUIDADOS_PELE',
            'ELIMINACAO_URINARIA',
            'ELIMINACAO_INTESTINAL',
            'MEDICAMENTO',
            'BEM_ESTAR',
            'OUTROS'
        )),
    CONSTRAINT ck_care_records_status
        CHECK (status IN ('REALIZADO', 'NAO_REALIZADO')),
    CONSTRAINT ck_care_records_reason
        CHECK (
            (status = 'REALIZADO' AND reason_not_performed IS NULL)
            OR (
                status = 'NAO_REALIZADO'
                AND reason_not_performed IS NOT NULL
                AND length(btrim(reason_not_performed)) > 0
            )
        )
);

CREATE INDEX idx_care_records_patient_caregiver_occurred
    ON care_records (patient_id, caregiver_id, occurred_at DESC, id DESC);

CREATE INDEX idx_care_records_caregiver
    ON care_records (caregiver_id);
