package net.siberanka.discordsocialspy.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContentFilterTest {

    @Test
    void whitelistOnlyExcludesItsOwnOccurrence() {
        ContentFilter filter = ContentFilter.compile(true,
                Collections.singletonList("blocked"),
                Collections.singletonList("play.example.com"),
                Collections.singletonList("evil\\.example"));

        assertNull(filter.find("Join play.example.com"));
        assertEquals(ContentFilter.Match.WORD, filter.find("play.example.com plus blocked"));
        assertEquals(ContentFilter.Match.REGEX, filter.find("evil.example"));
    }

    @Test
    void disabledFilterNeverMatches() {
        ContentFilter filter = ContentFilter.compile(false,
                Arrays.asList("blocked", "word"), Collections.emptyList(), Collections.emptyList());
        assertNull(filter.find("blocked word"));
    }
}
