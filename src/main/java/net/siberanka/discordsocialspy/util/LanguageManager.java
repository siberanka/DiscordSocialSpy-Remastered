package net.siberanka.discordsocialspy.util;

import net.siberanka.discordsocialspy.config.YamlConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class LanguageManager {

    private final YamlConfigManager configManager;
    private volatile Map<String, String> messages = Collections.emptyMap();

    public LanguageManager(YamlConfigManager configManager) {
        this.configManager = configManager;
    }

    public Map<String, String> readSnapshot(String code) throws IOException {
        YamlConfiguration yaml = configManager.loadLanguage(code);
        Map<String, String> loaded = new HashMap<>();
        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key)) {
                loaded.put(key, yaml.getString(key, key));
            }
        }
        return Collections.unmodifiableMap(loaded);
    }

    public void apply(Map<String, String> snapshot) {
        messages = snapshot;
    }

    public String get(String key) {
        return messages.getOrDefault(key, key);
    }
}
