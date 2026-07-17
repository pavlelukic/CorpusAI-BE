package com.corpusai.rag;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.service.output.OutputParsingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Retries a generation call over the two transient failures langchain4j will not retry on its own:
 * <ul>
 *   <li>a transient 401 — mapped to {@link AuthenticationException}, which extends
 *       {@code NonRetriableException}, so the model's built-in {@code maxRetries} deliberately
 *       skips it (it assumes 401 means a permanently bad key);</li>
 *   <li>a truncated response the AiService could not parse — {@link OutputParsingException} is
 *       thrown after the model call returns, outside the model's retry scope entirely.</li>
 * </ul>
 * Everything else is left alone on purpose: rate limits, 5xx and timeouts are already retried
 * inside the langchain4j model (they are not {@code NonRetriable}), and genuinely non-transient
 * failures — a real bad key, the empty-content guard, a malformed-quiz guard — must surface
 * immediately rather than be retried into a slower identical failure.
 */
@Slf4j
@Component
public class GenerationRetrier {

    private static final int MAX_ATTEMPTS = 3;
    private static final long DEFAULT_BACKOFF_MILLIS = 500;

    private final long backoffMillis;

    public GenerationRetrier() {
        this(DEFAULT_BACKOFF_MILLIS);
    }

    GenerationRetrier(long backoffMillis) {
        this.backoffMillis = backoffMillis;
    }

    public <T> T retry(String description, Supplier<T> call) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call.get();
            } catch (AuthenticationException | OutputParsingException e) {
                lastFailure = e;
                log.warn("Retryable generation failure on attempt {}/{} for {}: {}",
                        attempt, MAX_ATTEMPTS, description, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    backoff();
                }
            }
        }
        log.error("Generation failed after {} attempts for {}", MAX_ATTEMPTS, description);
        throw lastFailure;
    }

    private void backoff() {
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off before a generation retry", ie);
        }
    }
}