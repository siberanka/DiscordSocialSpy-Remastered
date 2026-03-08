package net.siberanka.discordsocialspy;

import net.siberanka.discordsocialspy.util.ConfigAutoUpdater;
import net.siberanka.discordsocialspy.util.LanguageManager;
import net.siberanka.discordsocialspy.worker.AsyncDispatcher;
import net.siberanka.discordsocialspy.command.DiscordSocialSpyCommand;
import net.siberanka.discordsocialspy.listener.SignListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.regex.Pattern;

import java.util.concurrent.ConcurrentHashMap;

public class DiscordSocialSpyPlugin extends JavaPlugin implements Listener {

    private AsyncDispatcher dispatcher;
    private LanguageManager lang;

    private final Map<UUID, String> lastMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> repeatCount = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> spamWarned = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> signNotify = new ConcurrentHashMap<>();

    private boolean filterEnabled = false;
    private boolean checkChat = false;
    private final List<Pattern> filterPatterns = new ArrayList<>();
    private final List<String> filterWords = new ArrayList<>();
    private final List<String> whitelistedWords = new ArrayList<>();
    private String filterRoleUuid = "";

    @Override
    public void onEnable() {

        saveDefaultConfig();
        new ConfigAutoUpdater(this).updateConfig();
        reloadConfig();

        lang = new LanguageManager(this);
        lang.loadLanguage(getConfig().getString("language"));

        dispatcher = new AsyncDispatcher(
                this,
                getConfig().getInt("async.sender_threads"),
                getConfig().getInt("async.dispatcher_threads"),
                getConfig().getInt("async.queue_size"),
                getConfig().getInt("async.max_retries"),
                getConfig().getInt("async.retry_interval"),
                getConfig().getInt("async.rate_limit_wait"));

        loadConfigValues();

        DiscordSocialSpyCommand executor = new DiscordSocialSpyCommand(this, lang, signNotify);
        getCommand("discordsocialspy").setExecutor(executor);
        getCommand("discordsocialspy").setTabCompleter(executor);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new SignListener(this, signNotify), this);
    }

    public AsyncDispatcher getDispatcher() {
        return dispatcher;
    }

    public LanguageManager getLanguageManager() {
        return lang;
    }

    public void loadConfigValues() {
        dispatcher.setWebhook(getConfig().getString("webhook"));
        dispatcher.setPrefix(getConfig().getString("prefix"));
        dispatcher.setUsername(getConfig().getString("username"));
        dispatcher.setAvatarUrl(getConfig().getString("avatar_url"));
        dispatcher.setSignWebhook(getConfig().getString("sign-webhook"));

        filterEnabled = getConfig().getBoolean("filter.enabled", false);
        checkChat = getConfig().getBoolean("filter.check-chat", false);
        filterPatterns.clear();
        filterWords.clear();
        whitelistedWords.clear();

        filterRoleUuid = getConfig().getString("filter.role-uuid", "");

        if (filterEnabled) {
            List<String> regexes = getConfig().getStringList("filter.regex");
            for (String regex : regexes) {
                try {
                    filterPatterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
                } catch (Exception e) {
                    getLogger().warning("Invalid regex in filter: " + regex);
                }
            }

            List<String> words = getConfig().getStringList("filter.words");
            for (String w : words) {
                filterWords.add(w.toLowerCase(Locale.ROOT));
            }

            List<String> whitelist = getConfig().getStringList("filter.whitelisted-words");
            for (String w : whitelist) {
                whitelistedWords.add(w.toLowerCase(Locale.ROOT));
            }
        }
    }

    public String getBlockCause(String text) {
        if (!filterEnabled || text == null || text.isEmpty()) {
            return null;
        }

        String lowerText = text.toLowerCase(Locale.ROOT);

        // Whitelist check first
        for (String whiteWord : whitelistedWords) {
            if (lowerText.contains(whiteWord)) {
                return null;
            }
        }

        for (String word : filterWords) {
            if (lowerText.contains(word)) {
                return "WORD";
            }
        }

        for (Pattern pattern : filterPatterns) {
            if (pattern.matcher(text).find()) {
                return "REGEX";
            }
        }
        return null;
    }

    public boolean isBlocked(String text) {
        return getBlockCause(text) != null;
    }

    @Override
    public void onDisable() {
        if (dispatcher != null)
            dispatcher.shutdown();
    }

    public void reloadAll() {
        reloadConfig();
        loadConfigValues();
        lang.loadLanguage(getConfig().getString("language"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {

        Player player = event.getPlayer();
        String msg = event.getMessage().trim();
        String root = msg.split(" ")[0].substring(1).toLowerCase(Locale.ROOT);

        for (String cmd : getConfig().getStringList("logged-commands")) {

            if (!root.equals(cmd.toLowerCase(Locale.ROOT)))
                continue;

            if (player.hasPermission(getConfig().getString("exclude-permission")))
                return;

            UUID id = player.getUniqueId();
            String last = lastMessage.getOrDefault(id, null);

            if (last != null && last.equalsIgnoreCase(msg)) {

                int count = repeatCount.getOrDefault(id, 0) + 1;
                repeatCount.put(id, count);

                if (count >= 3) {
                    if (!spamWarned.getOrDefault(id, false)) {
                        getLogger().warning(
                                getConfig().getString("message-prefix") +
                                        lang.get("spam-warning").replace("{player}", player.getName()));
                        spamWarned.put(id, true);
                    }
                    return;
                }

            } else {
                repeatCount.put(id, 0);
                spamWarned.put(id, false);
                lastMessage.put(id, msg);
            }

            String blockCause = getBlockCause(msg);
            if (blockCause != null) {
                event.setCancelled(true);
                player.sendMessage(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                                .deserialize(lang.get("message-blocked")));
                String pingRole = filterRoleUuid;
                dispatcher.queueTextMessage(lang.get("prefix-blocked-cmd") + player.getName() + ": " + msg, pingRole);
                return;
            }

            // FIXED: correct method name
            dispatcher.queueTextMessage(player.getName() + ": " + msg);
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!filterEnabled || !checkChat) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission(getConfig().getString("exclude-permission"))) {
            return;
        }

        String msg = event.getMessage();
        String blockCause = getBlockCause(msg);
        if (blockCause != null) {
            event.setCancelled(true);
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(lang.get("message-blocked")));
            String pingRole = filterRoleUuid;
            dispatcher.queueTextMessage(lang.get("prefix-blocked-chat") + player.getName() + ": " + msg, pingRole);
        }
    }
}
