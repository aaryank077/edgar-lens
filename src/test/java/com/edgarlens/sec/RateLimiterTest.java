package com.edgarlens.sec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    @Test
    void spacesCallsToTheConfiguredRate() throws Exception {
        RateLimiter limiter = new RateLimiter(20); // one permit every 50 ms
        long start = System.nanoTime();
        for (int i = 0; i < 11; i++) {
            limiter.acquire();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        // First call is immediate, the next 10 wait 50 ms each.
        assertThat(elapsedMs).isGreaterThanOrEqualTo(450);
    }

    @Test
    void rejectsNonPositiveRate() {
        assertThatThrownBy(() -> new RateLimiter(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
