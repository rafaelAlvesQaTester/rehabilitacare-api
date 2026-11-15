package br.com.reabilitacare.care;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Classificação administrativa do cuidado registrado.")
public enum CareType {
    @Schema(description = "Higiene pessoal.")
    HIGIENE,
    @Schema(description = "Alimentação.")
    ALIMENTACAO,
    @Schema(description = "Líquidos ingeridos.")
    HIDRATACAO,
    @Schema(description = "Mudança de posição.")
    POSICIONAMENTO,
    @Schema(description = "Auxílio para movimentação.")
    MOBILIDADE,
    @Schema(description = "Observação e cuidados com a pele.")
    CUIDADOS_PELE,
    @Schema(description = "Eliminação urinária.")
    ELIMINACAO_URINARIA,
    @Schema(description = "Eliminação intestinal.")
    ELIMINACAO_INTESTINAL,
    @Schema(description = "Registro relacionado a medicamento prescrito.")
    MEDICAMENTO,
    @Schema(description = "Bem-estar e observações emocionais.")
    BEM_ESTAR,
    @Schema(description = "Outro cuidado.")
    OUTROS
}
