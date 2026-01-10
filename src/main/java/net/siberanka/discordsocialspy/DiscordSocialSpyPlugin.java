package net.siberanka.discordsocialspy;

import net.siberanka.discordsocialspy.worker.AsyncDispatcher;
import net.siberanka.discordsocialspy.command.DiscordSocialSpyCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class DiscordSocialSpyPlugin extends JavaPlugin implements Listener {

    private AsyncDispatcher dispatcher;
    private List<String> filteredCommands;
    private String prefix;
    private String excludedPermission;
    private String username;
    private String avatar;

    @Override
    public void onEnable() {

        saveDefaultConfig();
        reloadConfig();
        loadSettings();

        // CONFIG'DE AYARLANAN username ve avatar_url KULLANILIYOR
        dispatcher = new AsyncDispatcher(
                this,
                getConfig().getString("webhook"),
                prefix,
                username,
                avatar
        );

        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("discordsocialspy") != null)
            getCommand("discordsocialspy").setExecutor(new DiscordSocialSpyCommand(this));

        getLogger().info("DiscordSocialSpy started.");
    }

    @Override
    public void onDisable() {
        if (dispatcher != null)
            dispatcher.shutdown();
    }

    public void reload() {
        reloadConfig();
        loadSettings();

        dispatcher.setWebhook(getConfig().getString("webhook"));
        dispatcher.setPrefix(prefix);
        dispatcher.setUsername(username);     // YENİ EKLENDİ
        dispatcher.setAvatarUrl(avatar);      // YENİ EKLENDİ
    }

    private void loadSettings() {
        filteredCommands = getConfig().getStringList("logged-commands");
        excludedPermission = getConfig().getString("exclude-permission");
        prefix = getConfig().getString("prefix", "[Spy] ");

        // CONFIG'DEN USERNAME VE AVATAR OKU
        username = getConfig().getString("username", "");
        avatar = getConfig().getString("avatar_url", "");
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {

        Player player = event.getPlayer();
        String msg = event.getMessage();

        if (excludedPermission != null && !excludedPermission.isBlank() &&
                player.hasPermission(excludedPermission))
            return;

        if (!msg.startsWith("/")) return;

        String base = msg.split(" ")[0].substring(1).toLowerCase();

        for (String cmd : filteredCommands) {
            if (base.equalsIgnoreCase(cmd)) {
                dispatcher.queue(player.getName() + ": " + msg);
                return;
            }
        }
    }
}
