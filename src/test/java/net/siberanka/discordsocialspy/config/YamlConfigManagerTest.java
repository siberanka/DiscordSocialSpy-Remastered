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
