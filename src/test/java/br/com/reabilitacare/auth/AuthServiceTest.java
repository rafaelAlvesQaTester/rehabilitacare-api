package br.com.reabilitacare.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CaregiverRepository caregiverRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtEncoder jwtEncoder;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest(
                "Cuidador Fictício",
                "Caregiver@example.test",
                "FakePassword!2026",
                RelationshipType.FAMILIAR);
    }

    @Test
    void registerNormalizesEmailAndStoresOnlyPasswordHash() {
        when(caregiverRepository.existsByEmail("caregiver@example.test")).thenReturn(false);
        when(passwordEncoder.encode(registerRequest.senha())).thenReturn("$2a$12$fake-hash");
        when(caregiverRepository.saveAndFlush(any(Caregiver.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CaregiverResponse response = authService.register(registerRequest);

        assertThat(response.email()).isEqualTo("caregiver@example.test");
        assertThat(response.nome()).isEqualTo("Cuidador Fictício");
        assertThat(response.id()).isNotNull();
        assertThat(response.ativo()).isTrue();
        verify(caregiverRepository).saveAndFlush(any(Caregiver.class));
    }

    @Test
    void registerRejectsAnExistingEmailWithoutEncodingPassword() {
        when(caregiverRepository.existsByEmail("caregiver@example.test")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(EmailAlreadyRegisteredException.class);

        verify(passwordEncoder, never()).encode(any());
        verify(caregiverRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerMapsConcurrentDuplicateEmailConstraintToConflict() {
        when(caregiverRepository.existsByEmail("caregiver@example.test")).thenReturn(false);
        when(passwordEncoder.encode(registerRequest.senha())).thenReturn("$2a$12$fake-hash");
        when(caregiverRepository.saveAndFlush(any(Caregiver.class))).thenThrow(
                new DataIntegrityViolationException(
                        "unique constraint",
                        new SQLException(
                                "duplicate key violates uk_caregivers_email", "23505")));

        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void loginReturnsShortLivedBearerTokenForValidCredentials() {
        Caregiver caregiver = caregiver();
        Jwt jwt = Jwt.withTokenValue("fictitious-access-token")
                .header("alg", "HS256")
                .subject(caregiver.getId().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        when(caregiverRepository.findByEmail("caregiver@example.test"))
                .thenReturn(Optional.of(caregiver));
        when(passwordEncoder.matches("FakePassword!2026", caregiver.getSenhaHash()))
                .thenReturn(true);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);

        LoginResponse response = authService.login(
                new LoginRequest("Caregiver@example.test", "FakePassword!2026"));

        assertThat(response.accessToken()).isEqualTo("fictitious-access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        assertThat(response.usuario().id()).isEqualTo(caregiver.getId());
    }

    @Test
    void loginRejectsInvalidPasswordWithGenericException() {
        Caregiver caregiver = caregiver();
        when(caregiverRepository.findByEmail("caregiver@example.test"))
                .thenReturn(Optional.of(caregiver));
        when(passwordEncoder.matches("WrongPassword!2026", caregiver.getSenhaHash()))
                .thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("caregiver@example.test", "WrongPassword!2026")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsUnknownEmailWithTheSameException() {
        when(caregiverRepository.findByEmail("unknown@example.test"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("unknown@example.test", "WrongPassword!2026")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void currentCaregiverUsesOnlyTheAuthenticatedIdentifier() {
        UUID authenticatedId = UUID.randomUUID();
        Caregiver caregiver = caregiver();
        when(caregiverRepository.findByIdAndAtivoTrue(authenticatedId))
                .thenReturn(Optional.of(caregiver));

        CaregiverResponse response = authService.currentCaregiver(authenticatedId);

        assertThat(response.id()).isEqualTo(caregiver.getId());
        verify(caregiverRepository).findByIdAndAtivoTrue(authenticatedId);
    }

    @Test
    void currentCaregiverRejectsMissingOrInactiveUsers() {
        UUID authenticatedId = UUID.randomUUID();
        when(caregiverRepository.findByIdAndAtivoTrue(authenticatedId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.currentCaregiver(authenticatedId))
                .isInstanceOf(CaregiverNotFoundException.class);
    }

    private Caregiver caregiver() {
        return new Caregiver(
                "Cuidador Fictício",
                "caregiver@example.test",
                "$2a$12$fake-hash",
                RelationshipType.FAMILIAR);
    }
}
