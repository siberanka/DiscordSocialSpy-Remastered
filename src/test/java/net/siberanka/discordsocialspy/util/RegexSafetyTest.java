package net.siberanka.discordsocialspy.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegexSafetyTest {

    @Test
    void acceptsBundledBoundedPatterns() {
        assertTrue(RegexSafety.isSafe("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]{1,5})?\\b"));
    }

    @Test
    void rejectsCommonBacktrackingAndBackReferenceHazards() {
        assertFalse(RegexSafety.isSafe("(a+)+$"));
        assertFalse(RegexSafety.isSafe("(a|aa)+$"));
        assertFalse(RegexSafety.isSafe("((a+))+$"));
        assertFalse(RegexSafety.isSafe("(secret)\\1"));
    }
}
