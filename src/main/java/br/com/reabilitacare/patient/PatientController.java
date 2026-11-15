package br.com.reabilitacare.patient;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/patients")
@Tag(name = "Pacientes", description = "Pacientes vinculados ao cuidador autenticado.")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping
    @Operation(
            summary = "Cadastrar paciente",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Paciente cadastrado."),
            @ApiResponse(responseCode = "400", description = "Dados inválidos."),
            @ApiResponse(responseCode = "401", description = "Autenticação necessária.")
    })
    public ResponseEntity<PatientResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PatientCreateRequest request) {
        PatientResponse response = patientService.create(caregiverId(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(
            summary = "Listar pacientes do cuidador",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de pacientes."),
            @ApiResponse(responseCode = "400", description = "Paginação inválida."),
            @ApiResponse(responseCode = "401", description = "Autenticação necessária.")
    })
    public PatientPageResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return patientService.list(caregiverId(jwt), page, size);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Consultar paciente",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paciente encontrado."),
            @ApiResponse(responseCode = "401", description = "Autenticação necessária."),
            @ApiResponse(responseCode = "404", description = "Paciente não encontrado.")
    })
    public PatientResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        return patientService.get(caregiverId(jwt), id);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Atualizar paciente",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paciente atualizado."),
            @ApiResponse(responseCode = "400", description = "Dados inválidos."),
            @ApiResponse(responseCode = "401", description = "Autenticação necessária."),
            @ApiResponse(responseCode = "404", description = "Paciente não encontrado.")
    })
    public PatientResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody PatientUpdateRequest request) {
        return patientService.update(caregiverId(jwt), id, request);
    }

    private UUID caregiverId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
