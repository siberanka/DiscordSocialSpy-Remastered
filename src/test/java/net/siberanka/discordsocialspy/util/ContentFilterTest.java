package net.siberanka.discordsocialspy.util;

import net.siberanka.discordsocialspy.config.PluginSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void blocksObfuscatedWordsWithoutSubstringFalsePositives() {
        ContentFilter filter = ContentFilter.compile(true, true,
                Arrays.asList("amk", "orospu", "oruspu", "bacınıza kayim"),
                Collections.emptyList(), Collections.emptyList());

        assertEquals(ContentFilter.Match.WORD, filter.find("a.m.k"));
        assertEquals(ContentFilter.Match.WORD, filter.find("o r o s p u"));
        assertEquals(ContentFilter.Match.WORD, filter.find("ORUSPU evlatları"));
        assertEquals(ContentFilter.Match.WORD, filter.find("baciniza---kayim"));
        assertNull(filter.find("Bu akşamki etkinlikte Minecraft oynayalım"));
        assertNull(filter.find("Bunu masaya koyayım"));
    }

    @Test
    void blocksPlainAndFragmentedServerAddresses() {
        ContentFilter filter = ContentFilter.compile(true, true,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertEquals(ContentFilter.Match.ADDRESS, filter.find("play.eldoria.com"));
        assertEquals(ContentFilter.Match.ADDRESS, filter.find("p.l.a.y.dsadsad.c.o.m"));
        assertEquals(ContentFilter.Match.ADDRESS, filter.find("pl ay eldoria com"));
        assertEquals(ContentFilter.Match.ADDRESS, filter.find("eldoria dot net"));
        assertEquals(ContentFilter.Match.ADDRESS, filter.find("31 . 186 . 250 . 10 : 25565"));
        assertNull(filter.find("Paper 1.20.4 sürümüne geçtik"));
        assertNull(filter.find("Koordinatlar 120 64 -350"));
        assertNull(filter.find("Sohbete hoş geldin, oyun başlayacak"));
    }

    @Test
    void whitelistStillMasksOnlyTheAllowedOccurrence() {
        ContentFilter filter = ContentFilter.compile(true, true,
                Collections.singletonList("amk"), Collections.singletonList("play.example.com"),
                Collections.emptyList());

        assertNull(filter.find("Sunucumuz play.example.com"));
        assertEquals(ContentFilter.Match.ADDRESS,
                filter.find("play.example.com veya p.l.a.y.evil.c.o.m"));
        assertEquals(ContentFilter.Match.WORD, filter.find("play.example.com amk"));
    }

    @Test
    void bundledDefaultsBlockBypassesButAllowLegitimateMessages() throws Exception {
        InputStream resource = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(resource);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
        ContentFilter filter = PluginSettings.from(yaml).contentFilter();

        String[] blocked = {
                "play.eldoria.com", "p.l.a.y.dsadsad.c.o.m", "pl ay eldoria com",
                "m c korsan n e t", "oyna rakip nokta net", "sunucu: rakip dot gg",
                "31.186.250.10:25565", "31 . 186 . 250 . 10 : 25565", "31_186_250_10",
                "discord dot gg / davet", "a.m.k", "4.m.k", "f u c k",
                "o\u200Br\u200Bo\u200Bs\u200Bp\u200Bu evlatları", "ORUSPU EVLATLARI",
                "bacınıza kayim", "baciniza---kayim", "ananı s.i.k.e.y.i.m"
        };
        for (String message : blocked) {
            assertNotNull(filter.find(message), () -> "Expected blocked: " + message);
        }

        String[] legitimate = {
                "Selam, nasılsınız?", "Bu akşamki etkinlikte Minecraft oynayalım",
                "Akşamki maç saat 20.00'de", "Bunu masaya koyayım mı?",
                "Bacınıza ve ailenize selam söyleyin", "Paper 1.20.4 sürümüne geçtik",
                "Koordinatlar 120 64 -350", "Pi sayısı yaklaşık 3.1415 değerindedir",
                "Play tuşuna basınca oyun başlar", "Minecraft sunucusu yeniden başlıyor",
                "İnternet bağlantım bugün biraz yavaş", "Discord'da görüşürüz",
                "Mail adresimi daha sonra gönderirim", "Saat 12.30 için randevulaştık",
                "Sunucumuz play.example.com", "Yedek adresimiz my.server.ip",
                "Welcome to our community", "The class assignment is ready",
                "This gameplay mechanic is excellent", "Version 2.0.1 is installed"
        };
        for (String message : legitimate) {
            assertNull(filter.find(message), () -> "False positive: " + message);
        }
    }
}
