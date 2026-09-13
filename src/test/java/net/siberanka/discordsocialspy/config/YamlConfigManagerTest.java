package net.siberanka.discordsocialspy.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import net.siberanka.discordsocialspy.util.RegexSafety;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlConfigManagerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void leavesCorrectDefaultsUntouchedOnRepeatedRuns() throws Exception {
        YamlConfigManager manager = new YamlConfigManager(plugin(temporaryDirectory));
        manager.updateAll();
        byte[] first = Files.readAllBytes(temporaryDirectory.resolve("config.yml"));

        manager.updateAll();
        byte[] second = Files.readAllBytes(temporaryDirectory.resolve("config.yml"));

        assertArrayEquals(first, second);
        assertFalse(Files.exists(temporaryDirectory.resolve("backups")));
    }

    @Test
    void backsUpThenRepairsMissingUnknownAndInvalidEntries() throws Exception {
        YamlConfigManager manager = new YamlConfigManager(plugin(temporaryDirectory));
        manager.updateAll();
        Path configPath = temporaryDirectory.resolve("config.yml");
        YamlConfiguration broken = manager.loadConfig();
        broken.set("debug", null);
        broken.set("async.queue_size", "too-many");
        broken.set("unknown.entry", true);
        Files.writeString(configPath, broken.saveToString(), StandardCharsets.UTF_8);

        manager.updateAll();
        YamlConfiguration repaired = manager.loadConfig();

        assertTrue(repaired.contains("debug"));
        assertEquals(1024, repaired.getInt("async.queue_size"));
        assertFalse(repaired.contains("unknown.entry"));
        try (java.util.stream.Stream<Path> backups = Files.list(temporaryDirectory.resolve("backups"))) {
            assertEquals(1L, backups.count());
        }

        manager.updateAll();
        try (java.util.stream.Stream<Path> backups = Files.list(temporaryDirectory.resolve("backups"))) {
            assertEquals(1L, backups.count());
        }
    }

    @Test
    void upgradesLegacyFilterListsWithoutChangingWhitelist() throws Exception {
        YamlConfigManager manager = new YamlConfigManager(plugin(temporaryDirectory));
        manager.updateAll();
        Path configPath = temporaryDirectory.resolve("config.yml");
        YamlConfiguration legacy = manager.loadConfig();
        legacy.set("filter.words", java.util.Arrays.asList("amk", "fuck", "play.", "custom-block"));
        legacy.set("filter.regex", java.util.Collections.singletonList("legacy-expression"));
        legacy.set("filter.whitelisted-words", java.util.Collections.singletonList("allowed.example.com"));
        legacy.set("filter.command-action", null);
        Files.writeString(configPath, legacy.saveToString(), StandardCharsets.UTF_8);

        manager.updateAll();
        YamlConfiguration upgraded = manager.loadConfig();

        assertTrue(upgraded.getStringList("filter.words").contains("custom-block"));
        assertTrue(upgraded.getStringList("filter.words").contains("orospu"));
        assertFalse(upgraded.getStringList("filter.words").contains("play."));
        assertTrue(upgraded.getStringList("filter.regex").contains("legacy-expression"));
        assertTrue(upgraded.getStringList("filter.regex").size() > 3);
        assertEquals(java.util.Collections.singletonList("allowed.example.com"),
                upgraded.getStringList("filter.whitelisted-words"));
        assertTrue(upgraded.getBoolean("filter.check-books"));
        assertTrue(upgraded.getBoolean("filter.check-signs"));
        assertFalse(upgraded.getBoolean("filter.command-action.enabled"));
        assertEquals("warn %player% Uygunsuz %trigger% kullanımı",
                upgraded.getString("filter.command-action.command"));
        assertEquals("sohbet", upgraded.getString("filter.command-action.trigger-labels.chat"));
    }

    @Test
    void everyBundledRegexIsSafeAndCompilable() throws Exception {
        YamlConfigManager manager = new YamlConfigManager(plugin(temporaryDirectory));
        manager.updateAll();

        for (String expression : manager.loadConfig().getStringList("filter.regex")) {
            assertTrue(RegexSafety.isSafe(expression), () -> "Unsafe bundled regex: " + expression);
            Pattern.compile(expression);
        }
    }

    private static Plugin plugin(Path dataDirectory) {
        Logger logger = Logger.getLogger("YamlConfigManagerTest");
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (proxy, method, arguments) -> {
                    switch (method.getName()) {
                        case "getDataFolder": return dataDirectory.toFile();
                        case "getLogger": return logger;
                        case "getResource":
                            InputStream resource = YamlConfigManagerTest.class.getClassLoader()
                                    .getResourceAsStream(String.valueOf(arguments[0]));
                            return resource;
                        case "getName": return "DiscordSocialSpy";
                        case "isEnabled": return true;
                        case "toString": return "TestPlugin";
                        default: return primitiveDefault(method.getReturnType());
                    }
                });
    }

    private static Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }
}
