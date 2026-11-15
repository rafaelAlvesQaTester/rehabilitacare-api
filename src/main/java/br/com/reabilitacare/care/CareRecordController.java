package br.com.reabilitacare.care;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/care-records")
@Tag(name = "Diário de cuidados", description = "Registros administrativos de cuidados.")
public class CareRecordController {

    private final CareRecordService careRecordService;

    public CareRecordController(CareRecordService careRecordService) {
        this.careRecordService = careRecordService;
    }

    @PostMapping
    @Operation(
            summary = "Registrar cuidado",
            description = "Registra um evento informado para um paciente autorizado.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registro criado."),
            @ApiResponse(responseCode = "400", description = "Dados inválidos."),
            @ApiResponse(responseCode = "401", description = "Autenticação necessária."),
            @ApiResponse(responseCode = "404", description = "Paciente não encontrado.")
    })
    public ResponseEntity<CareRecordResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID patientId,
            @Valid @RequestBody CareRecordCreateRequest request) {
        CareRecordResponse response = careRecordService.create(
                caregiverId(jwt), patientId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(
            summary = "Consultar histórico de cuidados",
            description = "Lista registros em ordem cronológica decrescente com paginação.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página do histórico."),
            @ApiResponse(responseCode = "400", description = "Filtros ou paginação inválidos."),
            @ApiResponse(responseCode = "401", description = "Autenticação necessária."),
            @ApiResponse(responseCode = "404", description = "Paciente não encontrado.")
    })
    public CareRecordPageResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) CareType careType) {
        return careRecordService.list(
                caregiverId(jwt), patientId, page, size, from, to, careType);
    }

    @GetMapping("/{recordId}")
    @Operation(
            summary = "Consultar registro de cuidado",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registro encontrado."),
            @ApiResponse(responseCode = "401", description = "Autenticação necessária."),
            @ApiResponse(responseCode = "404", description = "Registro não encontrado.")
    })
    public CareRecordResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID patientId,
            @PathVariable UUID recordId) {
        return careRecordService.get(caregiverId(jwt), patientId, recordId);
    }

    private UUID caregiverId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
