package net.siberanka.discordsocialspy.util;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ConfigAutoUpdater {

    private final Plugin plugin;

    public ConfigAutoUpdater(Plugin plugin) {
        this.plugin = plugin;
    }

    public void updateConfig() {

        File configFile = new File(plugin.getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
            return;
        }

        String defaultText = readResource("config.yml");
        String currentText = readFile(configFile);

        if (!isValidYaml(currentText)) {
            backupCorrupted(configFile, currentText);
            writeFile(configFile, defaultText);
            return;
        }

        YamlConfiguration def = new YamlConfiguration();
        YamlConfiguration cur = new YamlConfiguration();

        try {
            def.loadFromString(defaultText);
            cur.loadFromString(currentText);
        } catch (Exception ignored) {}

        boolean changed = false;

        for (String key : def.getKeys(true)) {
            if (!cur.contains(key)) {
                cur.set(key, def.get(key));
                changed = true;
            }
        }

        if (changed) {
            try {
                cur.save(configFile);
            } catch (Exception ignored) {}
        }
    }

    private boolean isValidYaml(String text) {
        try {
            new YamlConfiguration().loadFromString(text);
            return true;
        } catch (InvalidConfigurationException e) {
            return false;
        }
    }

    private String readResource(String name) {
        try (InputStream in = plugin.getResource(name)) {
            return new String(in.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Cannot read default config.yml");
        }
    }

    private String readFile(File f) {
        try {
            return new String(java.nio.file.Files.readAllBytes(f.toPath()));
        } catch (Exception e) {
            return "";
        }
    }

    private void writeFile(File f, String content) {
        try (FileWriter w = new FileWriter(f, false)) {
            w.write(content);
        } catch (Exception ignored) {}
    }

    private void backupCorrupted(File file, String contents) {
        try {
            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            File corrupted = new File(plugin.getDataFolder(), "config-" + stamp + "-corrupted.yml");
            writeFile(corrupted, contents);
        } catch (Exception ignored) {}
    }
}
package net.siberanka.discordsocialspy.util;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

public class ConfigAutoUpdater {

    private final Plugin plugin;

    public ConfigAutoUpdater(Plugin plugin) {
        this.plugin = plugin;
    }

    public void updateConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
            return;
        }

        String defaultConfig = readResourceConfig();
        String currentConfig = readFile(configFile);

        // YAML doğrulama
        if (!isYamlValid(currentConfig)) {
            backupCorruptedConfig(configFile, currentConfig);
            writeToFile(configFile, defaultConfig);
            return;
        }

        List<String> defaultLines = Arrays.asList(defaultConfig.split("\n"));
        List<String> currentLines = Arrays.asList(currentConfig.split("\n"));

        List<String> merged = mergeConfigs(defaultLines, currentLines);

        writeToFile(configFile, String.join("\n", merged));
    }

    private boolean isYamlValid(String text) {
        try {
            new YamlConfiguration().loadFromString(text);
            return true;
        } catch (InvalidConfigurationException ignored) {
            return false;
        }
    }

    private void backupCorruptedConfig(File file, String contents) {
        try {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            File corrupted = new File(plugin.getDataFolder(), "config-" + timestamp + "-corrupted.yml");
            writeToFile(corrupted, contents);
        } catch (Exception ignored) {}
    }

    private String readResourceConfig() {
        try (InputStream in = plugin.getResource("config.yml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Default config not found in JAR!", e);
        }
    }

    private String readFile(File file) {
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private List<String> mergeConfigs(List<String> def, List<String> cur) {

        // Kullanıcı configinde var olan anahtarlar (key: value)
        Map<String, String> userValues = extractKeyValueMap(cur);

        List<String> output = new ArrayList<>();

        for (String line : def) {

            if (!isKeyLine(line)) {
                output.add(line);
                continue;
            }

            String key = extractKey(line);
            if (userValues.containsKey(key)) {
                output.add(key + ": " + userValues.get(key));
                userValues.remove(key);
            } else {
                // default satır olduğu gibi eklenir
                output.add(line);
            }
        }

        return output;
    }

    private boolean isKeyLine(String line) {
        line = line.trim();
        return !line.startsWith("#") && line.contains(":") && !line.endsWith(":");
    }

    private String extractKey(String line) {
        return line.trim().split(":")[0];
    }

    private Map<String, String> extractKeyValueMap(List<String> lines) {
        Map<String, String> map = new LinkedHashMap<>();

        for (String line : lines) {
            if (!isKeyLine(line)) continue;
            String trimmed = line.trim();
            String[] parts = trimmed.split(":", 2);
            map.put(parts[0].trim(), parts[1].trim());
        }

        return map;
    }

    private void writeToFile(File file, String text) {
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(text);
        } catch (Exception ignored) {}
    }
}

