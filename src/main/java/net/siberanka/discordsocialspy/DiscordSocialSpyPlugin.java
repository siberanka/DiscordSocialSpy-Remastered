package net.siberanka.discordsocialspy;

import net.siberanka.discordsocialspy.worker.AsyncDispatcher;
import net.siberanka.discordsocialspy.command.DiscordSocialSpyCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

import java.util.List;

public class DiscordSocialSpyPlugin extends JavaPlugin implements Listener {

    private AsyncDispatcher dispatcher;
    private List<String> filteredCommands;
    private String prefix;
    private String excludedPermission;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();

        dispatcher = new AsyncDispatcher(this, getConfig().getString("webhook"), prefix);
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
    }

    private void loadSettings() {
        filteredCommands = getConfig().getStringList("logged-commands");
        excludedPermission = getConfig().getString("exclude-permission");
        prefix = getConfig().getString("prefix", "[Spy] ");
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        // Skip players with permission
        if (excludedPermission != null && !excludedPermission.isBlank()) {
            if (player.hasPermission(excludedPermission)) {
                return;
            }
        }

        String msg = event.getMessage().toLowerCase();

        for (String cmd : filteredCommands) {
            if (msg.startsWith("/" + cmd.toLowerCase())) {
                dispatcher.queue(player.getName() + ": " + event.getMessage());
                return;
            }
        }
    }
}
