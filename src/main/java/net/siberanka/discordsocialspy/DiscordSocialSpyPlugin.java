package net.siberanka.discordsocialspy;

import net.siberanka.discordsocialspy.util.ConfigAutoUpdater;
import net.siberanka.discordsocialspy.worker.AsyncDispatcher;
import net.siberanka.discordsocialspy.command.DiscordSocialSpyCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class DiscordSocialSpyPlugin extends JavaPlugin implements Listener {

    private AsyncDispatcher dispatcher;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        new ConfigAutoUpdater(this).updateConfig();

        reloadConfig();

        int senderThreads = getConfig().getInt("async.sender_threads");
        int dispatcherThreads = getConfig().getInt("async.dispatcher_threads");
        int queueSize = getConfig().getInt("async.queue_size");
        int maxRetries = getConfig().getInt("async.max_retries");
        int retryInterval = getConfig().getInt("async.retry_interval");
        int rateLimitWait = getConfig().getInt("async.rate_limit_wait");

        dispatcher = new AsyncDispatcher(
                this,
                senderThreads,
                dispatcherThreads,
                queueSize,
                maxRetries,
                retryInterval,
                rateLimitWait
        );

        loadConfigValues();

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("discordsocialspy").setExecutor(new DiscordSocialSpyCommand(this));

        getLogger().info("Plugin enabled.");
    }

    @Override
    public void onDisable() {
        if (dispatcher != null) dispatcher.shutdown();
        getLogger().info("Plugin disabled.");
    }

    public void loadConfigValues() {
        dispatcher.setWebhook(getConfig().getString("webhook"));
        dispatcher.setPrefix(getConfig().getString("prefix"));
        dispatcher.setUsername(getConfig().getString("username"));
        dispatcher.setAvatarUrl(getConfig().getString("avatar_url"));
    }

    public void reload() {
        reloadConfig();
        loadConfigValues();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        for (String cmd : getConfig().getStringList("logged-commands")) {
            if (message.startsWith("/" + cmd)) {
                if (!player.hasPermission(getConfig().getString("exclude-permission"))) {
                    dispatcher.queueMessage(player.getName() + ": " + message);
                }
                return;
            }
        }
    }
}
