package br.com.reabilitacare.care;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Situação informada para o cuidado.")
public enum CareRecordStatus {
    @Schema(description = "O cuidado foi informado como realizado.")
    REALIZADO,
    @Schema(description = "O cuidado não foi realizado; exige justificativa.")
    NAO_REALIZADO
}
