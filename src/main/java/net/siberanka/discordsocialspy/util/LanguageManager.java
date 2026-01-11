package net.siberanka.discordsocialspy.util;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

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
            copyDefault(code);
        }

        if (!langFile.exists()) {
            copyDefault("en");
            langFile = new File(langFolder, "en.yml");
        }

        String content = readFile(langFile);

        if (!isValidYaml(content)) {
            copyDefault("en");
            langFile = new File(langFolder, "en.yml");
        }

        lang = new YamlConfiguration();
        try {
            lang.load(langFile);
        } catch (Exception ignored) {}
    }

    public String get(String key) {
        if (lang == null) return key;
        return lang.getString(key, key);
    }

    private void copyDefault(String code) {
        try {
            InputStream in = plugin.getResource("lang/" + code + ".yml");
            if (in == null) return;

            File out = new File(plugin.getDataFolder() + "/lang", code + ".yml");
            Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);

        } catch (Exception ignored) {}
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
