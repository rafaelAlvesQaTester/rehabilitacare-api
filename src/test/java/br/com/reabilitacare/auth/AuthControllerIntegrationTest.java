package br.com.reabilitacare.auth;

import br.com.reabilitacare.patient.PatientCondition;
import br.com.reabilitacare.patient.PatientCreateRequest;
import br.com.reabilitacare.patient.PatientRepository;
import br.com.reabilitacare.patient.PatientUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "springdoc.api-docs.enabled=true",
                "springdoc.swagger-ui.enabled=true"
        })
class AuthControllerIntegrationTest {

    private static final String FAKE_PASSWORD = "FakePassword!2026";
    private static final String FAKE_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-bookworm")
                    .withDatabaseName("rehabilitacare_test")
                    .withUsername("test_user")
                    .withPassword("test_password");

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CaregiverRepository caregiverRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.jwt.secret-base64", () -> FAKE_SECRET);
    }

    @Test
    void registrationPersistsPasswordHashAndReturnsCreatedWithoutSecrets() throws Exception {
        String email = newEmail();

        HttpResponse<String> response = post(
                "/api/v1/auth/register", registration(email, FAKE_PASSWORD));

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode body = json(response);
        assertThat(body.has("senha")).isFalse();
        assertThat(body.has("senhaHash")).isFalse();
        assertThat(body.get("email").asText()).isEqualTo(email);

        Caregiver saved = caregiverRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getSenhaHash()).isNotEqualTo(FAKE_PASSWORD);
        assertThat(passwordEncoder.matches(FAKE_PASSWORD, saved.getSenhaHash())).isTrue();
        assertThat(saved.getTipoVinculo()).isEqualTo(RelationshipType.FAMILIAR);
    }

    @Test
    void registrationRejectsInvalidEmail() throws Exception {
        HttpResponse<String> response = post(
                "/api/v1/auth/register", registration("invalid-email", FAKE_PASSWORD));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).doesNotContain("invalid-email", FAKE_PASSWORD);
    }

    @Test
    void registrationRejectsPasswordOutsideTheDocumentedPolicy() throws Exception {
        HttpResponse<String> response = post(
                "/api/v1/auth/register", registration(newEmail(), "short"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).doesNotContain("short");
    }

    @Test
    void registrationRejectsDuplicateEmailRegardlessOfCase() throws Exception {
        String email = newEmail();
        assertThat(post("/api/v1/auth/register", registration(email, FAKE_PASSWORD))
                .statusCode()).isEqualTo(201);

        HttpResponse<String> duplicate = post(
                "/api/v1/auth/register", registration(email.toUpperCase(), FAKE_PASSWORD));

        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(json(duplicate).get("code").asText()).isEqualTo("email_conflict");
    }

    @Test
    void loginIssuesBearerTokenAndMeReturnsOnlyTheTokenOwner() throws Exception {
        String firstEmail = newEmail();
        String secondEmail = newEmail();
        JsonNode first = json(post(
                "/api/v1/auth/register", registration(firstEmail, FAKE_PASSWORD)));
        JsonNode second = json(post(
                "/api/v1/auth/register", registration(secondEmail, FAKE_PASSWORD)));

        HttpResponse<String> login = post(
                "/api/v1/auth/login",
                new LoginRequest(firstEmail.toUpperCase(), FAKE_PASSWORD));

        assertThat(login.statusCode()).isEqualTo(200);
        JsonNode loginBody = json(login);
        assertThat(loginBody.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(loginBody.get("expiresIn").asLong()).isEqualTo(900);
        assertThat(loginBody.get("accessToken").asText()).isNotBlank();
        assertThat(loginBody.toString()).doesNotContain("senhaHash", FAKE_PASSWORD);

        String token = loginBody.get("accessToken").asText();
        HttpResponse<String> current = get(
                "/api/v1/auth/me?id=" + second.get("id").asText(), token);

        assertThat(current.statusCode()).isEqualTo(200);
        JsonNode currentBody = json(current);
        assertThat(currentBody.get("id").asText()).isEqualTo(first.get("id").asText());
        assertThat(currentBody.get("id").asText()).isNotEqualTo(second.get("id").asText());
        assertThat(currentBody.has("senhaHash")).isFalse();
    }

    @Test
    void loginUsesTheSameUnauthorizedResponseForUnknownEmailAndWrongPassword() throws Exception {
        String email = newEmail();
        post("/api/v1/auth/register", registration(email, FAKE_PASSWORD));

        HttpResponse<String> wrongPassword = post(
                "/api/v1/auth/login", new LoginRequest(email, "WrongPassword!2026"));
        HttpResponse<String> unknownEmail = post(
                "/api/v1/auth/login",
                new LoginRequest(newEmail(), "WrongPassword!2026"));

        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownEmail.statusCode()).isEqualTo(401);
        assertThat(wrongPassword.body()).isEqualTo(unknownEmail.body());
        assertThat(wrongPassword.body()).doesNotContain(email, "WrongPassword!2026");
    }

    @Test
    void meRequiresAuthenticationAndHealthRemainsPublic() throws Exception {
        HttpResponse<String> unauthorized = get("/api/v1/auth/me", null);
        HttpResponse<String> health = get(
                "/api/v1/health", null, "health-correlation-123");

        assertThat(unauthorized.statusCode()).isEqualTo(401);
        assertThat(json(unauthorized).get("code").asText()).isEqualTo("unauthorized");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(json(health).get("status").asText()).isEqualTo("UP");
        assertThat(health.headers().firstValue("X-Correlation-ID"))
                .contains("health-correlation-123");
    }

    @Test
    void openApiContractDocumentsRoutesAndBearerSecurity() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs", null);
        JsonNode openApi = json(response);
        JsonNode paths = openApi.path("paths");
        JsonNode register = paths.path("/api/v1/auth/register").path("post");
        JsonNode authenticated = paths.path("/api/v1/auth/me").path("get");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(openApi.path("openapi").asText()).startsWith("3.");
        assertThat(openApi.path("info").path("title").asText())
                .isEqualTo("ReabilitaCare API");
        assertThat(paths.has("/api/v1/health")).isTrue();
        assertThat(paths.has("/api/v1/auth/login")).isTrue();
        assertThat(paths.has("/api/v1/patients")).isTrue();
        assertThat(paths.has("/api/v1/patients/{id}")).isTrue();
        assertThat(paths.has("/api/v1/patients/{patientId}/care-records")).isTrue();
        assertThat(paths.has(
                "/api/v1/patients/{patientId}/care-records/{recordId}")).isTrue();
        assertThat(register.path("responses").has("201")).isTrue();
        assertThat(register.has("security")).isFalse();
        assertThat(authenticated.path("security").isArray()).isTrue();
        assertThat(authenticated.path("security").get(0).has("bearerAuth")).isTrue();
        assertThat(openApi.path("components").path("securitySchemes")
                .path("bearerAuth").path("scheme").asText()).isEqualTo("bearer");
        assertThat(get("/swagger-ui/index.html", null).statusCode()).isEqualTo(200);
    }

    @Test
    void prometheusMetricsRequireAuthenticationAndExposeOperationalData() throws Exception {
        TestCaregiver caregiver = newAuthenticatedCaregiver();
        get("/api/v1/health", null);

        HttpResponse<String> unauthorized = get("/actuator/prometheus", null);
        HttpResponse<String> metrics = get("/actuator/prometheus", caregiver.token());

        assertThat(unauthorized.statusCode()).isEqualTo(401);
        assertThat(metrics.statusCode())
                .as("Prometheus response: %s", metrics.body())
                .isEqualTo(200);
        assertThat(metrics.body()).contains("jvm_memory_used_bytes");
        assertThat(metrics.body()).contains("http_server_requests_seconds_bucket");
    }

    @Test
    void flywayMigrationCreatedTheCaregiversTable() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = 'caregivers'",
                Integer.class);
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '1' AND success = TRUE",
                Integer.class);

        assertThat(tableCount).isEqualTo(1);
        assertThat(migrationCount).isEqualTo(1);
    }

    @Test
    void patientMigrationAddsForeignKeyIndexAndPreservesCaregiverRecords() throws Exception {
        TestCaregiver first = newAuthenticatedCaregiver();
        TestCaregiver second = newAuthenticatedCaregiver();
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = 'patients'",
                Integer.class);
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '2' AND success = TRUE",
                Integer.class);
        Integer caregiverCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM caregivers",
                Integer.class);
        Integer foreignKeyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = 'public' AND table_name = 'patients' "
                        + "AND constraint_name = 'fk_patients_caregiver'",
                Integer.class);
        Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes "
                        + "WHERE schemaname = 'public' AND tablename = 'patients' "
                        + "AND indexname = 'idx_patients_caregiver_created'",
                Integer.class);

        assertThat(tableCount).isEqualTo(1);
        assertThat(migrationCount).isEqualTo(1);
        assertThat(caregiverCount).isGreaterThanOrEqualTo(2);
        assertThat(caregiverRepository.findById(first.id())).isPresent();
        assertThat(caregiverRepository.findById(second.id())).isPresent();
        assertThat(foreignKeyCount).isEqualTo(1);
        assertThat(indexCount).isEqualTo(1);
    }

    @Test
    void patientCreationPersistsOnlyUnderAuthenticatedCaregiver() throws Exception {
        TestCaregiver caregiver = newAuthenticatedCaregiver();

        HttpResponse<String> response = post(
                "/api/v1/patients",
                patientRequest("Paciente Fictício"),
                caregiver.token());

        assertThat(response.statusCode()).isEqualTo(201);
        JsonNode body = json(response);
        assertThat(body.get("cuidadorResponsavelId").asText())
                .isEqualTo(caregiver.id().toString());
        assertThat(body.has("senhaHash")).isFalse();
        Integer persisted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM patients WHERE id = ? AND cuidador_responsavel_id = ?",
                Integer.class,
                UUID.fromString(body.get("id").asText()),
                caregiver.id());
        assertThat(persisted).isEqualTo(1);
    }

    @Test
    void patientCreationRejectsBlankNameFutureBirthDateAndUnknownCondition()
            throws Exception {
        TestCaregiver caregiver = newAuthenticatedCaregiver();
        HttpResponse<String> blankName = post(
                "/api/v1/patients",
                new PatientCreateRequest(
                        " ",
                        null,
                        PatientCondition.NAO_INFORMADO,
                        null),
                caregiver.token());
        HttpResponse<String> futureBirthDate = post(
                "/api/v1/patients",
                new PatientCreateRequest(
                        "Paciente Fictício",
                        LocalDate.now().plusDays(1),
                        PatientCondition.NAO_INFORMADO,
                        null),
                caregiver.token());
        HttpResponse<String> unknownCondition = post(
                "/api/v1/patients",
                Map.of(
                        "nome", "Paciente Fictício",
                        "tipoCondicao", "CONDICAO_INVALIDA"),
                caregiver.token());

        assertThat(blankName.statusCode()).isEqualTo(400);
        assertThat(futureBirthDate.statusCode()).isEqualTo(400);
        assertThat(unknownCondition.statusCode()).isEqualTo(400);
    }

    @Test
    void patientListIsIsolatedAndUsesBoundedPagination() throws Exception {
        TestCaregiver first = newAuthenticatedCaregiver();
        TestCaregiver second = newAuthenticatedCaregiver();
        JsonNode firstPatient = createPatient(first, "Paciente da primeira conta");
        createPatient(first, "Outro paciente da primeira conta");
        JsonNode secondPatient = createPatient(second, "Paciente da segunda conta");

        HttpResponse<String> firstPage = get(
                "/api/v1/patients?page=0&size=1", first.token());
        HttpResponse<String> excessivePage = get(
                "/api/v1/patients?size=51", first.token());

        assertThat(firstPage.statusCode()).isEqualTo(200);
        JsonNode page = json(firstPage);
        assertThat(page.get("content").size()).isEqualTo(1);
        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.get("content").get(0).get("id").asText())
                .isNotEqualTo(secondPatient.get("id").asText());
        assertThat(firstPatient.get("cuidadorResponsavelId").asText())
                .isEqualTo(first.id().toString());
        assertThat(excessivePage.statusCode()).isEqualTo(400);
    }

    @Test
    void patientLookupHidesOtherCaregiversAndMissingPatients() throws Exception {
        TestCaregiver first = newAuthenticatedCaregiver();
        TestCaregiver second = newAuthenticatedCaregiver();
        JsonNode foreignPatient = createPatient(second, "Paciente da segunda conta");
        String missingId = UUID.randomUUID().toString();

        HttpResponse<String> foreign = get(
                "/api/v1/patients/" + foreignPatient.get("id").asText(), first.token());
        HttpResponse<String> missing = get("/api/v1/patients/" + missingId, first.token());
        HttpResponse<String> owned = get(
                "/api/v1/patients/" + foreignPatient.get("id").asText(), second.token());

        assertThat(foreign.statusCode()).isEqualTo(404);
        assertThat(foreign.body()).isEqualTo(missing.body());
        assertThat(owned.statusCode()).isEqualTo(200);
    }

    @Test
    void patientUpdateIsOwnerScopedAndCannotChangeTheCaregiver() throws Exception {
        TestCaregiver first = newAuthenticatedCaregiver();
        TestCaregiver second = newAuthenticatedCaregiver();
        JsonNode patient = createPatient(first, "Paciente Fictício");
        String patientPath = "/api/v1/patients/" + patient.get("id").asText();
        Map<String, Object> update = new HashMap<>();
        update.put("nome", "Nome Atualizado");
        update.put("dataNascimento", null);
        update.put("tipoCondicao", "TETRAPLEGIA");
        update.put("observacoesGerais", "Observação fictícia");
        update.put("cuidadorResponsavelId", second.id().toString());

        HttpResponse<String> forbiddenUpdate = put(
                patientPath,
                new PatientUpdateRequest(
                        "Tentativa externa",
                        null,
                        PatientCondition.NAO_INFORMADO,
                        null),
                second.token());
        HttpResponse<String> ownedUpdate = put(patientPath, update, first.token());
        HttpResponse<String> current = get(patientPath, first.token());

        assertThat(forbiddenUpdate.statusCode()).isEqualTo(404);
        assertThat(ownedUpdate.statusCode()).isEqualTo(200);
        assertThat(json(ownedUpdate).get("cuidadorResponsavelId").asText())
                .isEqualTo(first.id().toString());
        assertThat(json(current).get("nome").asText()).isEqualTo("Nome Atualizado");
        assertThat(json(current).get("cuidadorResponsavelId").asText())
                .isEqualTo(first.id().toString());
        assertThat(json(current).get("tipoCondicao").asText()).isEqualTo("TETRAPLEGIA");
    }

    @Test
    void patientCreateIgnoresClientSuppliedCaregiverIdentity() throws Exception {
        TestCaregiver first = newAuthenticatedCaregiver();
        TestCaregiver second = newAuthenticatedCaregiver();
        Map<String, Object> request = Map.of(
                "nome", "Paciente Fictício",
                "tipoCondicao", "NAO_INFORMADO",
                "cuidadorResponsavelId", second.id().toString());

        HttpResponse<String> response = post("/api/v1/patients", request, first.token());

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(json(response).get("cuidadorResponsavelId").asText())
                .isEqualTo(first.id().toString());
    }

    @Test
    void patientRoutesRequireValidNonExpiredJwtAndRejectMalformedIds() throws Exception {
        TestCaregiver caregiver = newAuthenticatedCaregiver();

        HttpResponse<String> missingToken = get("/api/v1/patients", null);
        HttpResponse<String> invalidToken = get("/api/v1/patients", "invalid.jwt.token");
        HttpResponse<String> expiredToken = get(
                "/api/v1/patients", expiredToken(caregiver.id()));
        HttpResponse<String> malformedId = get(
                "/api/v1/patients/not-a-uuid", caregiver.token());

        assertThat(missingToken.statusCode()).isEqualTo(401);
        assertThat(invalidToken.statusCode()).isEqualTo(401);
        assertThat(expiredToken.statusCode()).isEqualTo(401);
        assertThat(malformedId.statusCode()).isEqualTo(400);
    }

    @Test
    void patientEndpointsReturnNotFoundForMissingUpdate() throws Exception {
        TestCaregiver caregiver = newAuthenticatedCaregiver();

        HttpResponse<String> response = put(
                "/api/v1/patients/" + UUID.randomUUID(),
                new PatientUpdateRequest(
                        "Paciente Fictício",
                        null,
                        PatientCondition.NAO_INFORMADO,
                        null),
                caregiver.token());

        assertThat(response.statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> post(String path, Object body) throws Exception {
        return post(path, body, null);
    }

    private HttpResponse<String> post(String path, Object body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        addAuthorization(builder, token);
        HttpRequest request = builder
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return get(path, token, null);
    }

    private HttpResponse<String> get(
            String path,
            String token,
            String correlationId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (correlationId != null) {
            request.header("X-Correlation-ID", correlationId);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String path, Object body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        addAuthorization(builder, token);
        HttpRequest request = builder
                .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void addAuthorization(HttpRequest.Builder request, String token) {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
    }

    private TestCaregiver newAuthenticatedCaregiver() throws Exception {
        String email = newEmail();
        JsonNode registered = json(post(
                "/api/v1/auth/register", registration(email, FAKE_PASSWORD)));
        JsonNode login = json(post(
                "/api/v1/auth/login",
                new LoginRequest(email, FAKE_PASSWORD)));
        return new TestCaregiver(
                UUID.fromString(registered.get("id").asText()),
                login.get("accessToken").asText());
    }

    private JsonNode createPatient(TestCaregiver caregiver, String name) throws Exception {
        HttpResponse<String> response = post(
                "/api/v1/patients", patientRequest(name), caregiver.token());
        assertThat(response.statusCode()).isEqualTo(201);
        return json(response);
    }

    private PatientCreateRequest patientRequest(String name) {
        return new PatientCreateRequest(
                name,
                null,
                PatientCondition.NAO_INFORMADO,
                null);
    }

    private String expiredToken(UUID caregiverId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("rehabilitacare")
                .subject(caregiverId.toString())
                .issuedAt(now.minusSeconds(120))
                .expiresAt(now.minusSeconds(60))
                .claim("scope", "caregiver")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims)).getTokenValue();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private RegisterRequest registration(String email, String password) {
        return new RegisterRequest(
                "Cuidador Fictício",
                email,
                password,
                RelationshipType.FAMILIAR);
    }

    private String newEmail() {
        return "caregiver-" + UUID.randomUUID() + "@example.test";
    }

    private record TestCaregiver(UUID id, String token) {
    }
}
