package net.siberanka.discordsocialspy.util;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.file.Files;

public class LanguageManager {

    private final Plugin plugin;
    private YamlConfiguration lang;

    public LanguageManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void loadLanguage(String code) {

        if (code == null || code.isBlank()) code = "en";

        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) langFolder.mkdirs();

        File langFile = new File(langFolder, code + ".yml");

        if (!langFile.exists()) {
            if (!copyDefault(code)) {
                plugin.getLogger().warning("Language file not found, using EN fallback.");
                copyDefault("en");
                langFile = new File(langFolder, "en.yml");
            }
        }

        String content = readFile(langFile);

        if (!isValidYaml(content)) {
            plugin.getLogger().warning("Language file " + code + " is corrupted. Falling back to EN.");
            copyDefault("en");
            langFile = new File(langFolder, "en.yml");
        }

        lang = new YamlConfiguration();
        try {
            lang.load(langFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load " + code + ".yml. Falling back to EN.");
            copyDefault("en");
            try {
                lang.load(new File(langFolder, "en.yml"));
            } catch (Exception ignored) {}
        }
    }

    public String get(String key) {
        if (lang == null) return key;
        return lang.getString(key, key);
    }

    private boolean copyDefault(String code) {
        try {
            InputStream in = plugin.getResource("lang/" + code + ".yml");
            if (in == null) return false;

            File target = new File(plugin.getDataFolder() + "/lang", code + ".yml");
            Files.copy(in, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            return true;

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to copy default language file: " + code);
            return false;
        }
    }

    private String readFile(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()));
        } catch (Exception e) {
            return "";
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
}
