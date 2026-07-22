package com.corpusai.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Guards the dispatcherTypeMatchers rule in SecurityConfig. When an SseEmitter completes, the
// container runs a second pass over the filter chain with DispatcherType.ASYNC. authorizeHttpRequests
// applies to every dispatcher type, JwtAuthenticationFilter (an OncePerRequestFilter) skips async
// dispatches by default, and STATELESS leaves no SecurityContext to restore - so without that rule
// the async pass arrives unauthenticated and is denied, cutting the stream off before its last event.
// Uses a throwaway probe endpoint rather than /api/chats/{id}/messages so the test needs no LLM keys.
@SpringBootTest
@AutoConfigureMockMvc
@Import(SseAsyncDispatchSecurityTest.SseProbeController.class)
class SseAsyncDispatchSecurityTest {

    private static final String PROBE_PATH = "/api/sse-async-probe";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void asyncDispatchOfACompletedEmitterIsNotDenied() throws Exception {
        MvcResult result = mockMvc.perform(get(PROBE_PATH).header("Authorization", "Bearer " + userToken()))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));
    }

    // The ASYNC exemption only lets an already-authorized request finish; it is not a way in.
    @Test
    void theProbeEndpointStillRejectsAnUnauthenticatedRequestPass() throws Exception {
        mockMvc.perform(get(PROBE_PATH))
                .andExpect(status().isUnauthorized());
    }

    private String userToken() {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", Role.USER.name())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    // Registered only for this test class, so it never reaches the running application. Sends and
    // completes up front; ResponseBodyEmitter buffers both until the container initializes it, which
    // makes the async dispatch happen deterministically rather than on a background thread.
    @RestController
    static class SseProbeController {

        @GetMapping(PROBE_PATH)
        SseEmitter probe() throws IOException {
            SseEmitter emitter = new SseEmitter(5_000L);
            emitter.send(SseEmitter.event().name("token").data("hi"));
            emitter.send(SseEmitter.event().name("done").data("{}"));
            emitter.complete();
            return emitter;
        }
    }
}