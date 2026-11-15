package br.com.reabilitacare.care;

import br.com.reabilitacare.auth.LoginRequest;
import br.com.reabilitacare.auth.RegisterRequest;
import br.com.reabilitacare.auth.RelationshipType;
import br.com.reabilitacare.patient.PatientCondition;
import br.com.reabilitacare.patient.PatientCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CareRecordControllerIntegrationTest {

    private static final String FAKE_PASSWORD = "FakePassword!2026";
    private static final String FAKE_SECRET =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-bookworm")
                    .withDatabaseName("rehabilitacare_care_test")
                    .withUsername("test_user")
                    .withPassword("test_password");

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

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
    void migrationCreatesConstrainedTableAndBothStatusesPersistWithServerTime()
            throws Exception {
        TestCaregiver caregiver = registerCaregiver();
        TestCaregiver forgedCaregiver = registerCaregiver();
        UUID patientId = createPatient(caregiver);
        OffsetDateTime eventTime = OffsetDateTime.parse("2026-11-13T21:15:00-03:00");
        Instant requestStartedAt = Instant.now();
        Map<String, Object> performedRequest = careRequest(
                CareType.HIGIENE,
                CareRecordStatus.REALIZADO,
                eventTime,
                "Observação fictícia",
                null);
        performedRequest.put("caregiverId", forgedCaregiver.id().toString());
        performedRequest.put("recordedAt", "2000-01-01T00:00:00Z");

        HttpResponse<String> performed = post(
                careRecordsPath(patientId), performedRequest, caregiver.token());
        HttpResponse<String> notPerformed = post(
                careRecordsPath(patientId),
                careRequest(
                        CareType.MOBILIDADE,
                        CareRecordStatus.NAO_REALIZADO,
                        OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5),
                        null,
                        "A atividade não foi realizada."),
                caregiver.token());

        assertThat(performed.statusCode()).isEqualTo(201);
        assertThat(notPerformed.statusCode()).isEqualTo(201);
        JsonNode performedBody = json(performed);
        JsonNode notPerformedBody = json(notPerformed);
        assertThat(Instant.parse(performedBody.get("occurredAt").asText()))
                .isEqualTo(eventTime.toInstant());
        assertThat(Instant.parse(performedBody.get("recordedAt").asText()))
                .isAfterOrEqualTo(requestStartedAt);
        assertThat(performedBody.get("caregiverId").asText())
                .isEqualTo(caregiver.id().toString());
        assertThat(performedBody.has("email")).isFalse();
        assertThat(notPerformedBody.get("status").asText()).isEqualTo("NAO_REALIZADO");
        assertThat(notPerformedBody.get("reasonNotPerformed").asText())
                .isEqualTo("A atividade não foi realizada.");

        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name = 'care_records'",
                Integer.class);
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history "
                        + "WHERE version = '3' AND success = TRUE",
                Integer.class);
        Integer persisted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM care_records "
                        + "WHERE patient_id = ? AND caregiver_id = ?",
                Integer.class,
                patientId,
                caregiver.id());
        Integer foreignKeyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = 'public' AND table_name = 'care_records' "
                        + "AND constraint_type = 'FOREIGN KEY'",
                Integer.class);
        Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes "
                        + "WHERE schemaname = 'public' AND tablename = 'care_records' "
                        + "AND indexname = 'idx_care_records_patient_caregiver_occurred'",
                Integer.class);

        assertThat(tableCount).isEqualTo(1);
        assertThat(migrationCount).isEqualTo(1);
        assertThat(persisted).isEqualTo(2);
        assertThat(foreignKeyCount).isEqualTo(2);
        assertThat(indexCount).isEqualTo(1);
    }

    @Test
    void invalidCategoryStatusTimeReasonAndNoteLengthAreRejected() throws Exception {
        TestCaregiver caregiver = registerCaregiver();
        UUID patientId = createPatient(caregiver);
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);

        HttpResponse<String> invalidCategory = post(
                careRecordsPath(patientId),
                careRequest("INVALIDO", CareRecordStatus.REALIZADO, past, null, null),
                caregiver.token());
        HttpResponse<String> invalidStatus = post(
                careRecordsPath(patientId),
                careRequest(CareType.HIGIENE, "INVALIDO", past, null, null),
                caregiver.token());
        HttpResponse<String> missingReason = post(
                careRecordsPath(patientId),
                careRequest(
                        CareType.HIGIENE,
                        CareRecordStatus.NAO_REALIZADO,
                        past,
                        null,
                        null),
                caregiver.token());
        HttpResponse<String> incoherentReason = post(
                careRecordsPath(patientId),
                careRequest(
                        CareType.HIGIENE,
                        CareRecordStatus.REALIZADO,
                        past,
                        null,
                        "Motivo não compatível"),
                caregiver.token());
        HttpResponse<String> futureTime = post(
                careRecordsPath(patientId),
                careRequest(
                        CareType.HIGIENE,
                        CareRecordStatus.REALIZADO,
                        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5),
                        null,
                        null),
                caregiver.token());
        HttpResponse<String> excessiveNotes = post(
                careRecordsPath(patientId),
                careRequest(
                        CareType.HIGIENE,
                        CareRecordStatus.REALIZADO,
                        past,
                        "x".repeat(2001),
                        null),
                caregiver.token());
        HttpResponse<String> invalidTime = post(
                careRecordsPath(patientId),
                careRequest(
                        CareType.HIGIENE,
                        CareRecordStatus.REALIZADO,
                        "horario-invalido",
                        null,
                        null),
                caregiver.token());

        assertThat(invalidCategory.statusCode()).isEqualTo(400);
        assertThat(invalidStatus.statusCode()).isEqualTo(400);
        assertThat(missingReason.statusCode()).isEqualTo(400);
        assertThat(incoherentReason.statusCode()).isEqualTo(400);
        assertThat(futureTime.statusCode()).isEqualTo(400);
        assertThat(excessiveNotes.statusCode()).isEqualTo(400);
        assertThat(invalidTime.statusCode()).isEqualTo(400);
    }

    @Test
    void historyIsChronologicalFilterableAndPaginated() throws Exception {
        TestCaregiver caregiver = registerCaregiver();
        UUID patientId = createPatient(caregiver);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        OffsetDateTime oldest = now.minusDays(3);
        OffsetDateTime middle = now.minusDays(2);
        OffsetDateTime newest = now.minusDays(1);
        createRecord(
                caregiver,
                patientId,
                CareType.HIGIENE,
                oldest,
                null);
        createRecord(
                caregiver,
                patientId,
                CareType.ALIMENTACAO,
                middle,
                null);
        createRecord(
                caregiver,
                patientId,
                CareType.HIGIENE,
                newest,
                null);

        HttpResponse<String> history = get(
                careRecordsPath(patientId), caregiver.token());
        HttpResponse<String> filtered = get(
                careRecordsPath(patientId)
                        + "?from=" + middle.plusHours(1)
                        + "&to=" + now
                        + "&careType=HIGIENE&page=0&size=1",
                caregiver.token());
        HttpResponse<String> excessivePage = get(
                careRecordsPath(patientId) + "?size=51",
                caregiver.token());
        HttpResponse<String> invalidPeriod = get(
                careRecordsPath(patientId)
                        + "?from=" + now
                        + "&to=" + oldest,
                caregiver.token());

        assertThat(history.statusCode()).isEqualTo(200);
        JsonNode historyBody = json(history);
        assertThat(historyBody.get("content").size()).isEqualTo(3);
        assertThat(Instant.parse(historyBody.get("content").get(0)
                .get("occurredAt").asText())).isEqualTo(newest.toInstant());
        assertThat(Instant.parse(historyBody.get("content").get(2)
                .get("occurredAt").asText())).isEqualTo(oldest.toInstant());
        assertThat(historyBody.get("size").asInt()).isEqualTo(20);

        assertThat(filtered.statusCode()).isEqualTo(200);
        JsonNode filteredBody = json(filtered);
        assertThat(filteredBody.get("content").size()).isEqualTo(1);
        assertThat(filteredBody.get("totalElements").asLong()).isEqualTo(1);
        assertThat(filteredBody.get("content").get(0).get("careType").asText())
                .isEqualTo("HIGIENE");
        assertThat(Instant.parse(filteredBody.get("content").get(0)
                .get("occurredAt").asText())).isEqualTo(newest.toInstant());
        assertThat(excessivePage.statusCode()).isEqualTo(400);
        assertThat(invalidPeriod.statusCode()).isEqualTo(400);
    }

    @Test
    void caregiverCannotCreateOrReadRecordsForAnotherPatient() throws Exception {
        TestCaregiver first = registerCaregiver();
        TestCaregiver second = registerCaregiver();
        UUID firstPatientId = createPatient(first);
        UUID secondPatientId = createPatient(second);
        JsonNode firstRecord = createRecord(
                first,
                firstPatientId,
                CareType.BEM_ESTAR,
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10),
                null);
        JsonNode secondRecord = createRecord(
                second,
                secondPatientId,
                CareType.HIDRATACAO,
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(8),
                null);

        HttpResponse<String> crossPatientCreate = post(
                careRecordsPath(secondPatientId),
                careRequest(
                        CareType.HIGIENE,
                        CareRecordStatus.REALIZADO,
                        OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                        null,
                        null),
                first.token());
        HttpResponse<String> unknownPatientCreate = post(
                careRecordsPath(UUID.randomUUID()),
                careRequest(
                        CareType.HIGIENE,
                        CareRecordStatus.REALIZADO,
                        OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                        null,
                        null),
                first.token());
        HttpResponse<String> crossPatientHistory = get(
                careRecordsPath(secondPatientId), first.token());
        HttpResponse<String> crossRecordOnOwnedPatient = get(
                careRecordPath(firstPatientId, UUID.fromString(secondRecord.get("id").asText())),
                first.token());
        HttpResponse<String> missingRecordOnOwnedPatient = get(
                careRecordPath(firstPatientId, UUID.randomUUID()), first.token());
        HttpResponse<String> ownRecord = get(
                careRecordPath(firstPatientId, UUID.fromString(firstRecord.get("id").asText())),
                first.token());
        HttpResponse<String> secondOwnRecord = get(
                careRecordPath(secondPatientId, UUID.fromString(secondRecord.get("id").asText())),
                second.token());

        assertThat(crossPatientCreate.statusCode()).isEqualTo(404);
        assertThat(crossPatientCreate.body()).isEqualTo(unknownPatientCreate.body());
        assertThat(crossPatientHistory.statusCode()).isEqualTo(404);
        assertThat(crossRecordOnOwnedPatient.statusCode()).isEqualTo(404);
        assertThat(crossRecordOnOwnedPatient.body()).isEqualTo(missingRecordOnOwnedPatient.body());
        assertThat(ownRecord.statusCode()).isEqualTo(200);
        assertThat(secondOwnRecord.statusCode()).isEqualTo(200);
        assertThat(json(ownRecord).get("caregiverId").asText()).isEqualTo(first.id().toString());
    }

    @Test
    void careRecordRoutesRequireJwtAndRejectMalformedIdentifiers() throws Exception {
        TestCaregiver caregiver = registerCaregiver();
        String path = careRecordsPath(UUID.randomUUID());
        HttpResponse<String> noTokenHistory = get(path, null);
        HttpResponse<String> noTokenCreate = post(
                path,
                careRequest(
                        CareType.HIGIENE,
                        CareRecordStatus.REALIZADO,
                        OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1),
                        null,
                        null),
                null);
        HttpResponse<String> invalidJwt = get(path, "invalid.jwt.token");
        HttpResponse<String> malformedPatient = get(
                "/api/v1/patients/invalid-patient/care-records", caregiver.token());
        HttpResponse<String> malformedRecord = get(
                careRecordsPath(UUID.randomUUID()) + "/invalid-record", caregiver.token());
        HttpResponse<String> missingPatient = get(path, caregiver.token());

        assertThat(noTokenHistory.statusCode()).isEqualTo(401);
        assertThat(noTokenCreate.statusCode()).isEqualTo(401);
        assertThat(invalidJwt.statusCode()).isEqualTo(401);
        assertThat(malformedPatient.statusCode()).isEqualTo(400);
        assertThat(malformedRecord.statusCode()).isEqualTo(400);
        assertThat(missingPatient.statusCode()).isEqualTo(404);
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
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        addAuthorization(builder, token);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void addAuthorization(HttpRequest.Builder request, String token) {
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
    }

    private TestCaregiver registerCaregiver() throws Exception {
        String email = "care-record-" + UUID.randomUUID() + "@example.test";
        RegisterRequest registration = new RegisterRequest(
                "Cuidador Fictício",
                email,
                FAKE_PASSWORD,
                RelationshipType.FAMILIAR);
        HttpResponse<String> registered = post("/api/v1/auth/register", registration, null);
        assertThat(registered.statusCode()).isEqualTo(201);
        UUID caregiverId = UUID.fromString(json(registered).get("id").asText());

        HttpResponse<String> login = post(
                "/api/v1/auth/login",
                new LoginRequest(email, FAKE_PASSWORD),
                null);
        assertThat(login.statusCode()).isEqualTo(200);
        return new TestCaregiver(
                caregiverId,
                json(login).get("accessToken").asText());
    }

    private UUID createPatient(TestCaregiver caregiver) throws Exception {
        PatientCreateRequest request = new PatientCreateRequest(
                "Paciente Fictício",
                null,
                PatientCondition.NAO_INFORMADO,
                null);
        HttpResponse<String> response = post(
                "/api/v1/patients", request, caregiver.token());
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(json(response).get("id").asText());
    }

    private JsonNode createRecord(
            TestCaregiver caregiver,
            UUID patientId,
            CareType careType,
            OffsetDateTime occurredAt,
            String reason) throws Exception {
        HttpResponse<String> response = post(
                careRecordsPath(patientId),
                careRequest(
                        careType,
                        reason == null
                                ? CareRecordStatus.REALIZADO
                                : CareRecordStatus.NAO_REALIZADO,
                        occurredAt,
                        null,
                        reason),
                caregiver.token());
        assertThat(response.statusCode()).isEqualTo(201);
        return json(response);
    }

    private Map<String, Object> careRequest(
            Object careType,
            Object status,
            Object occurredAt,
            String notes,
            String reason) {
        Map<String, Object> request = new HashMap<>();
        request.put("careType", careType);
        request.put("status", status);
        request.put("occurredAt", occurredAt);
        request.put("notes", notes);
        request.put("reasonNotPerformed", reason);
        return request;
    }

    private String careRecordsPath(UUID patientId) {
        return "/api/v1/patients/" + patientId + "/care-records";
    }

    private String careRecordPath(UUID patientId, UUID recordId) {
        return careRecordsPath(patientId) + "/" + recordId;
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private record TestCaregiver(UUID id, String token) {
    }
}
