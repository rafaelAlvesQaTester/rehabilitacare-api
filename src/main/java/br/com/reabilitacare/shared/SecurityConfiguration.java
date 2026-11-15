package br.com.reabilitacare.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfiguration {

    private static final RequestMatcher ACTUATOR_HEALTH_REQUEST =
            request -> request.getRequestURI().endsWith("/actuator/health")
                    || request.getServletPath().endsWith("/actuator/health");
    private static final RequestMatcher PROMETHEUS_REQUEST =
            request -> request.getRequestURI().endsWith("/actuator/prometheus")
                    || request.getServletPath().endsWith("/actuator/prometheus");

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService emptyUserDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("No password-based accounts are configured.");
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper) throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint = (request, response, exception) ->
                writeError(
                        response,
                        objectMapper,
                        401,
                        "unauthorized",
                        "É necessário entrar para acessar este recurso.");
        AccessDeniedHandler accessDeniedHandler = (request, response, exception) ->
                writeError(
                        response,
                        objectMapper,
                        403,
                        "forbidden",
                        "Você não tem permissão para acessar este recurso.");

        http
                .csrf(csrf -> csrf.disable())
                .requestCache(requestCache -> requestCache.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-ui.html",
                                "/swagger-ui/**").permitAll()
                        .requestMatchers("/api/v1/health").permitAll()
                        .requestMatchers(ACTUATOR_HEALTH_REQUEST).permitAll()
                        .requestMatchers(PROMETHEUS_REQUEST).authenticated()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers("/api/v1/patients", "/api/v1/patients/**")
                        .authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(authenticationEntryPoint));
        return http.build();
    }

    private void writeError(
            jakarta.servlet.http.HttpServletResponse response,
            ObjectMapper objectMapper,
            int status,
            String code,
            String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), new ApiError(code, message));
    }

}
