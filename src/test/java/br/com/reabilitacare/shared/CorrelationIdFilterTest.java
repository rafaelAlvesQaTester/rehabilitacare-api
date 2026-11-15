package br.com.reabilitacare.shared;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void usesSafeClientIdInMdcAndResponseAndRestoresPreviousContext() throws Exception {
        String clientId = "request-123";
        AtomicReference<String> observedId = new AtomicReference<>();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, clientId);
        MDC.put(CorrelationIdFilter.MDC_KEY, "previous-request");

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                observedId.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(observedId.get()).isEqualTo(clientId);
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(clientId);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("previous-request");
    }

    @Test
    void replacesUnsafeClientIdWithGeneratedUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "invalid value\r\n");

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            assertThat(UUID.fromString(correlationId)).isNotNull();
        });

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME))
                .matches("[0-9a-f-]{36}");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertThat(UUID.fromString(MDC.get(CorrelationIdFilter.MDC_KEY)))
                        .isNotNull());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME))
                .matches("[0-9a-f-]{36}");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
