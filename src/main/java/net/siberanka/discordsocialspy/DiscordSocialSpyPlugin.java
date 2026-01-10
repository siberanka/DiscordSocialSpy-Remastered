package net.siberanka.discordsocialspy;

import net.siberanka.discordsocialspy.worker.AsyncDispatcher;
import net.siberanka.discordsocialspy.command.DiscordSocialSpyCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class DiscordSocialSpyPlugin extends JavaPlugin implements Listener {

    private AsyncDispatcher dispatcher;
    private List<String> filteredCommands;
    private String prefix;
    private String excludedPermission;
    private String username;
    private String avatarUrl;

    @Override
    public void onEnable() {

        validateAndRepairConfig();

        reloadConfig();
        loadSettings();

        dispatcher = new AsyncDispatcher(
                this,
                getConfig().getString("webhook"),
                prefix,
                username,
                avatarUrl
        );

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("discordsocialspy") != null) {
            getCommand("discordsocialspy").setExecutor(new DiscordSocialSpyCommand(this));
        }

        getLogger().info("DiscordSocialSpy Async Advanced mode enabled.");
    }

    @Override
    public void onDisable() {
        dispatcher.shutdown();
    }

    public void reload() {
        reloadConfig();
        loadSettings();
        dispatcher.setWebhook(getConfig().getString("webhook"));
        dispatcher.setPrefix(prefix);
        dispatcher.setUsername(username);
        dispatcher.setAvatarUrl(avatarUrl);
    }

    private void loadSettings() {
        filteredCommands = getConfig().getStringList("logged-commands");
        excludedPermission = getConfig().getString("exclude-permission");
        prefix = getConfig().getString("prefix", "[Spy] ");
        username = getConfig().getString("username", "DiscordSocialSpy");
        avatarUrl = getConfig().getString("avatar_url", "");
    }


    /**
     * CONFIG SELF-REPAIR SYSTEM
     * - If YAML broken: rename and regenerate new config
     * - If missing keys: patch missing keys in correct order
     * - Always append last update timestamp
     */
    private void validateAndRepairConfig() {

        File configFile = new File(getDataFolder(), "config.yml");

        // 1) YAML parse validation
        try {
            getConfig();
        } catch (Exception ex) {
            getLogger().warning("Config is corrupted! Creating backup and regenerating default config.yml...");

            File backup = new File(getDataFolder(),
                    "corrupted-config-" + System.currentTimeMillis() + ".yml");

            configFile.renameTo(backup);

            saveDefaultConfig();
            reloadConfig();

            appendTimestamp(configFile);
            return;
        }

        // 2) Fix missing values (but preserve the existing values)
        boolean modified = false;

        modified |= addDefaultIfMissing("webhook", "");
        modified |= addDefaultIfMissing("prefix", "[Spy] ");
        modified |= addDefaultIfMissing("logged-commands", Arrays.asList("msg", "tell", "w"));
        modified |= addDefaultIfMissing("exclude-permission", "discordsocialspy.ignore");
        modified |= addDefaultIfMissing("username", "DiscordSocialSpy");
        modified |= addDefaultIfMissing("avatar_url", "");

        // If nothing changed, stop
        if (!modified) return;

        // Copy defaults and save
        getConfig().options().copyDefaults(true);
        saveConfig();

        appendTimestamp(configFile);
    }


    /**
     * Add missing config key and return true if modified
     */
    private boolean addDefaultIfMissing(String key, Object value) {
        if (!getConfig().isSet(key)) {
            getConfig().addDefault(key, value);
            return true;
        }
        return false;
    }


    /**
     * Append last edit timestamp at end of config.yml
     */
    private void appendTimestamp(File configFile) {
        try {
            FileWriter writer = new FileWriter(configFile, true);
            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            writer.write("\n# last-fixed: " + date + "\n");
            writer.close();
        } catch (Exception e) {
            getLogger().warning("Failed to write last-fixed timestamp: " + e.getMessage());
        }
    }


    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (excludedPermission != null && !excludedPermission.isBlank()) {
            if (player.hasPermission(excludedPermission)) {
                return;
            }
        }

        String fullMessage = event.getMessage().toLowerCase();
        String baseCommand = fullMessage.split(" ")[0].substring(1);

        for (String cmd : filteredCommands) {
            if (baseCommand.equalsIgnoreCase(cmd)) {
                dispatcher.queue(player.getName() + ": " + event.getMessage());
                return;
            }
        }
    }
}
