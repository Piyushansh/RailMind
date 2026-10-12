package com.railmind.common;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Hand-wired unit test (no Spring context needed - this filter has no
 * dependencies) covering every behaviour Phase 2 called out explicitly:
 * accepting an incoming correlation ID, generating one when absent, echoing
 * it back to the caller, and always clearing MDC afterwards even when the
 * rest of the chain throws.
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void usesIncomingHeader_whenPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "caller-supplied-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] seenDuringChain = new String[1];
        filter.doFilter(request, response, (req, res) -> seenDuringChain[0] = MDC.get(CorrelationIdFilter.MDC_KEY));

        assertThat(seenDuringChain[0]).isEqualTo("caller-supplied-id-123");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("caller-supplied-id-123");
    }

    @Test
    void generatesAValidId_whenHeaderAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] seenDuringChain = new String[1];
        filter.doFilter(request, response, (req, res) -> seenDuringChain[0] = MDC.get(CorrelationIdFilter.MDC_KEY));

        assertThat(seenDuringChain[0]).isNotBlank();
        // Must be usable as a correlation id even with nothing supplied - a parseable UUID
        // confirms it's a real generated value, not an accidental empty string slipping through.
        assertThat(java.util.UUID.fromString(seenDuringChain[0])).isNotNull();
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(seenDuringChain[0]);
    }

    @Test
    void generatesAValidId_whenHeaderIsBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] seenDuringChain = new String[1];
        filter.doFilter(request, response, (req, res) -> seenDuringChain[0] = MDC.get(CorrelationIdFilter.MDC_KEY));

        assertThat(seenDuringChain[0]).isNotBlank();
        assertThat(seenDuringChain[0].trim()).isNotEqualTo("");
    }

    @Test
    void clearsMdc_afterSuccessfulChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { /* no-op */ });

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void clearsMdc_evenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new IllegalStateException("downstream failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
