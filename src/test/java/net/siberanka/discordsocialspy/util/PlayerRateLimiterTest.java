package net.siberanka.discordsocialspy.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerRateLimiterTest {

    @Test
    void boundsEachPlayerIndependently() {
        PlayerRateLimiter limiter = new PlayerRateLimiter(2, 0.1);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(limiter.tryAcquire(first));
        assertTrue(limiter.tryAcquire(first));
        assertFalse(limiter.tryAcquire(first));
        assertTrue(limiter.tryAcquire(second));

        limiter.remove(first);
        assertTrue(limiter.tryAcquire(first));
    }
}
