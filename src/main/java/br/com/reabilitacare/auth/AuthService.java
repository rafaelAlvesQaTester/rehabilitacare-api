package br.com.reabilitacare.auth;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(15);
    private static final String TOKEN_ISSUER = "rehabilitacare";

    private final CaregiverRepository caregiverRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    public AuthService(
            CaregiverRepository caregiverRepository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder) {
        this.caregiverRepository = caregiverRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
    }

    @Transactional
    public CaregiverResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (caregiverRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        Caregiver caregiver = new Caregiver(
                request.nome().trim(),
                normalizedEmail,
                passwordEncoder.encode(request.senha()),
                request.tipoVinculo());

        try {
            return CaregiverResponse.from(caregiverRepository.saveAndFlush(caregiver));
        } catch (DataIntegrityViolationException exception) {
            if (isEmailUniqueConstraintViolation(exception)) {
                throw new EmailAlreadyRegisteredException();
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Caregiver caregiver = caregiverRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(InvalidCredentialsException::new);
        boolean passwordMatches = passwordEncoder.matches(
                request.senha(), caregiver.getSenhaHash());
        if (!caregiver.isAtivo() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(TOKEN_LIFETIME);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(TOKEN_ISSUER)
                .subject(caregiver.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("scope", "caregiver")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        Jwt token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims));

        return new LoginResponse(
                token.getTokenValue(),
                "Bearer",
                TOKEN_LIFETIME.toSeconds(),
                CaregiverResponse.from(caregiver));
    }

    @Transactional(readOnly = true)
    public CaregiverResponse currentCaregiver(UUID caregiverId) {
        Caregiver caregiver = caregiverRepository.findByIdAndAtivoTrue(caregiverId)
                .orElseThrow(CaregiverNotFoundException::new);
        return CaregiverResponse.from(caregiver);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isEmailUniqueConstraintViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())
                    && String.valueOf(sqlException.getMessage()).contains("uk_caregivers_email")) {
                return true;
            }
        }
        return false;
    }
}
