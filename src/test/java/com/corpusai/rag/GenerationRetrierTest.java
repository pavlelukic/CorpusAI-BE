package com.corpusai.rag;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.service.output.OutputParsingException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Pure unit test of the retry logic - no Spring, no LLM, no DB. Uses the package-private
// zero-backoff constructor so the give-up case does not actually sleep between attempts.
class GenerationRetrierTest {

    private final GenerationRetrier retrier = new GenerationRetrier(0);

    @Test
    void returnsImmediatelyWhenTheCallSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        String result = retrier.retry("t", () -> {
            calls.incrementAndGet();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void retriesAfterAnOutputParsingFailureThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        String result = retrier.retry("t", () -> {
            if (calls.incrementAndGet() == 1) {
                throw new OutputParsingException("truncated", new RuntimeException("eof"));
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void retriesAfterATransient401ThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        String result = retrier.retry("t", () -> {
            if (calls.incrementAndGet() == 1) {
                throw new AuthenticationException("401");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void givesUpAfterThreeAttemptsAndRethrowsTheLastFailure() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retrier.retry("t", () -> {
            calls.incrementAndGet();
            throw new AuthenticationException("401");
        })).isInstanceOf(AuthenticationException.class);

        assertThat(calls.get()).isEqualTo(3);
    }

    // A non-transient failure (here: the empty-content / malformed-quiz guard shape) must surface
    // on the first attempt, not be retried into a slower identical failure.
    @Test
    void doesNotRetryAnUnlistedException() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retrier.retry("t", () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("empty result guard");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(calls.get()).isEqualTo(1);
    }
}