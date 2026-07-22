package net.siberanka.discordsocialspy.worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonPayloadsTest {

    @Test
    void escapesJsonAndDisablesUnrequestedMentions() {
        String json = JsonPayloads.text("hello \"world\"\n@everyone", "Spy", "", "");

        assertTrue(json.contains("hello \\\"world\\\"\\n@everyone"));
        assertTrue(json.contains("\"allowed_mentions\":{\"parse\":[]}"));
    }

    @Test
    void onlyAllowsValidatedRoleSlot() {
        String json = JsonPayloads.text("alert", "Spy", "", "12345678901234567");

        assertTrue(json.contains("<@&12345678901234567>"));
        assertTrue(json.contains("\"roles\":[\"12345678901234567\"]"));
        assertFalse(json.contains("\"parse\":[\"roles\"]"));
    }

    @Test
    void limitsByUnicodeCodePointWithoutSplittingSurrogates() {
        String limited = JsonPayloads.limit("😀😀😀", 2);
        assertTrue(limited.startsWith("😀"));
        assertFalse(Character.isHighSurrogate(limited.charAt(limited.length() - 1)));
    }
}
