package net.siberanka.discordsocialspy.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {

    @Test
    void comparesReleaseVersionsWithoutTreatingEqualTagsAsUpdates() {
        assertTrue(UpdateChecker.isNewer("v2.0.1", "2.0.0"));
        assertTrue(UpdateChecker.isNewer("2.1.0", "2.0.99"));
        assertFalse(UpdateChecker.isNewer("v2.0.1", "2.0.1"));
        assertFalse(UpdateChecker.isNewer("2.0.0", "2.0.1"));
    }
}
