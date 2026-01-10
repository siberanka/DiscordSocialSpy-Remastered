package net.siberanka.discordsocialspy;

import net.siberanka.discordsocialspy.worker.AsyncDispatcher;
import net.siberanka.discordsocialspy.command.DiscordSocialSpyCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Arrays;
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

        // Validate / repair config before loading
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
     * - If YAML is corrupted → rename and regenerate
     * - If missing keys → auto-fill missing defaults
     */
    private void validateAndRepairConfig() {

        File configFile = new File(getDataFolder(), "config.yml");

        // 1) YAML parse check
        try {
            getConfig();
        } catch (Exception ex) {

            getLogger().warning("Config is corrupted! Creating backup and regenerating fresh config.yml...");

            File backup = new File(getDataFolder(),
                    "corrupted-config-" + System.currentTimeMillis() + ".yml");

            configFile.renameTo(backup);

            saveDefaultConfig();
            reloadConfig();
            return;
        }

        // 2) Fill missing defaults automatically
        getConfig().addDefault("webhook", "");
        getConfig().addDefault("prefix", "[Spy] ");
        getConfig().addDefault("logged-commands", Arrays.asList("msg", "tell", "w"));
        getConfig().addDefault("exclude-permission", "discordsocialspy.ignore");
        getConfig().addDefault("username", "DiscordSocialSpy");
        getConfig().addDefault("avatar_url", "");

        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        // Skip players with bypass permission
        if (excludedPermission != null && !excludedPermission.isBlank()) {
            if (player.hasPermission(excludedPermission)) {
                return;
            }
        }

        String fullMessage = event.getMessage().toLowerCase();

        // Extract base command without "/" and without arguments
        String baseCommand = fullMessage.split(" ")[0].substring(1);

        // Exact match check
        for (String cmd : filteredCommands) {
            if (baseCommand.equalsIgnoreCase(cmd)) {
                dispatcher.queue(player.getName() + ": " + event.getMessage());
                return;
            }
        }
    }
}
