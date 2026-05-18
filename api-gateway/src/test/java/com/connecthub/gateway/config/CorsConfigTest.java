package com.connecthub.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorsConfigTest {

    private CorsWebFilter corsWebFilter;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        CorsConfig corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "frontendUrl", "https://connect-hub-frontend-one.vercel.app/");
        ReflectionTestUtils.setField(corsConfig, "frontendAllowedOriginPatterns", "https://custom-preview-*.vercel.app");
        corsWebFilter = corsConfig.corsWebFilter();

        chain = mock(WebFilterChain.class);
        when(chain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
    }

    @Test
    void corsWebFilter_allowsCurrentVercelPreviewPreflight() {
        String origin = "https://connect-hub-frontend-git-main-mohit-d360s-projects.vercel.app";
        MockServerWebExchange exchange = preflightExchange(origin);

        corsWebFilter.filter(exchange, chain).block();

        assertPreflightOk(exchange);
        assertEquals(origin, exchange.getResponse().getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertTrue(exchange.getResponse().getHeaders().getAccessControlAllowMethods().contains(HttpMethod.POST));
        assertTrue(exchange.getResponse().getHeaders().getAccessControlAllowHeaders().contains("content-type"));
        verify(chain, never()).filter(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void corsWebFilter_normalizesFrontendUrlTrailingSlash() {
        String origin = "https://connect-hub-frontend-one.vercel.app";
        MockServerWebExchange exchange = preflightExchange(origin);

        corsWebFilter.filter(exchange, chain).block();

        assertPreflightOk(exchange);
        assertEquals(origin, exchange.getResponse().getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        verify(chain, never()).filter(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void corsWebFilter_allowsConfiguredPreviewPattern() {
        String origin = "https://custom-preview-123.vercel.app";
        MockServerWebExchange exchange = preflightExchange(origin);

        corsWebFilter.filter(exchange, chain).block();

        assertPreflightOk(exchange);
        assertEquals(origin, exchange.getResponse().getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        verify(chain, never()).filter(org.mockito.ArgumentMatchers.any());
    }

    private MockServerWebExchange preflightExchange(String origin) {
        MockServerHttpRequest request = MockServerHttpRequest
                .options("https://api.connect-hub.dev/api/v1/auth/register")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type")
                .build();
        return MockServerWebExchange.from(request);
    }

    private void assertPreflightOk(MockServerWebExchange exchange) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        assertTrue(status == null || status == HttpStatus.OK);
    }
}
