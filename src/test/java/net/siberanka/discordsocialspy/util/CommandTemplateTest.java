package net.siberanka.discordsocialspy.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandTemplateTest {

    @Test
    void rendersPlayerAndSourceWithoutLeadingSlash() {
        assertEquals("warn ZeroX_18 Uygunsuz tabela kullanımı",
                CommandTemplate.render("/warn %player% Uygunsuz %trigger% kullanımı", "ZeroX_18", "tabela"));
    }

    @Test
    void preservesUnknownPlaceholdersForOtherPlugins() {
        assertEquals("warn Alex kitap %reason%",
                CommandTemplate.render("warn %player% %trigger% %reason%", "Alex", "kitap"));
    }
}
