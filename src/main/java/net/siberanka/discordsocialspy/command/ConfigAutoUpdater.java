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
