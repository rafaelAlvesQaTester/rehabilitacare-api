package br.com.reabilitacare.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Saúde", description = "Verificação básica de disponibilidade.")
public class HealthController {

    @GetMapping("/api/v1/health")
    @Operation(summary = "Verificar saúde da API")
    public HealthResponse health() {
        return new HealthResponse("UP");
    }
}
