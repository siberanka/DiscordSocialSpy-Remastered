package net.siberanka.discordsocialspy;

import net.siberanka.discordsocialspy.command.DiscordSocialSpyCommand;
import net.siberanka.discordsocialspy.compat.SchedulerBridge;
import net.siberanka.discordsocialspy.config.PluginSettings;
import net.siberanka.discordsocialspy.config.YamlConfigManager;
import net.siberanka.discordsocialspy.listener.SignListener;
import net.siberanka.discordsocialspy.listener.BookListener;
import net.siberanka.discordsocialspy.util.ContentFilter;
import net.siberanka.discordsocialspy.util.CommandTemplate;
import net.siberanka.discordsocialspy.util.LanguageManager;
import net.siberanka.discordsocialspy.util.PlayerRateLimiter;
import net.siberanka.discordsocialspy.util.UpdateChecker;
import net.siberanka.discordsocialspy.worker.AsyncDispatcher;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public final class DiscordSocialSpyPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Boolean> signNotifications = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> rateLimitWarnings = new ConcurrentHashMap<>();

    private YamlConfigManager configManager;
    private LanguageManager language;
    private SchedulerBridge scheduler;
    private volatile AsyncDispatcher dispatcher;
    private ExecutorService configurationExecutor;
    private volatile PlayerRateLimiter playerRateLimiter;
    private volatile PluginSettings settings;

    @Override
    public void onEnable() {
        configManager = new YamlConfigManager(this);
        language = new LanguageManager(configManager);
        scheduler = new SchedulerBridge(this);
        configurationExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "dss-config");
            thread.setDaemon(true);
            return thread;
        });

        try {
            Snapshot initial = readSnapshot();
            settings = initial.settings;
            language.apply(initial.messages);
        } catch (Exception failure) {
            getLogger().severe("DiscordSocialSpy could not load a safe configuration: " + safeMessage(failure));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        dispatcher = new AsyncDispatcher(this, settings);
        playerRateLimiter = new PlayerRateLimiter(settings.rateLimitCapacity(), settings.rateLimitRefill());

        DiscordSocialSpyCommand commandHandler = new DiscordSocialSpyCommand(this);
        PluginCommand command = getCommand("discordsocialspy");
        if (command == null) {
            getLogger().severe("The discordsocialspy command is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new SignListener(this), this);
        getServer().getPluginManager().registerEvents(new BookListener(this), this);
        commandHandler.rebuildCommandCache();

        getLogger().info("Enabled v" + getDescription().getVersion() + " for Paper 1.16.x-26.1.x"
                + (scheduler.isFoliaSchedulerAvailable() ? " using Folia-aware schedulers." : " using Bukkit schedulers."));
        if (settings.updateCheck()) {
            configurationExecutor.execute(() -> UpdateChecker.check(this, settings.debug()));
        }
    }

    @Override
    public void onDisable() {
        signNotifications.clear();
        rateLimitWarnings.clear();
        if (playerRateLimiter != null) {
            playerRateLimiter.clear();
        }
        if (dispatcher != null) {
            dispatcher.shutdown();
        }
        if (configurationExecutor != null) {
            configurationExecutor.shutdownNow();
        }
    }

    public void reloadAsync(CommandSender requester, Runnable success, Consumer<Throwable> failure) {
        configurationExecutor.execute(() -> {
            try {
                Snapshot snapshot = readSnapshot();
                applySnapshot(snapshot);
                runForSender(requester, success);
            } catch (Throwable problem) {
                getLogger().warning("Configuration reload failed safely: " + safeMessage(problem));
                runForSender(requester, () -> failure.accept(problem));
            }
        });
    }

    public void updateLoggedCommandAsync(CommandSender requester, String command, boolean add,
            Consumer<Boolean> completion, Consumer<Throwable> failure) {
        configurationExecutor.execute(() -> {
            try {
                Snapshot diskSnapshot = readSnapshot();
                Set<String> updated = new LinkedHashSet<>(diskSnapshot.settings.loggedCommands());
                boolean changed = add ? updated.add(command) : updated.remove(command);
                if (changed) {
                    configManager.setLoggedCommands(updated);
                    applySnapshot(readSnapshot());
                } else {
                    applySnapshot(diskSnapshot);
                }
                runForSender(requester, () -> completion.accept(changed));
            } catch (Throwable problem) {
                getLogger().warning("Command configuration update failed safely: " + safeMessage(problem));
                runForSender(requester, () -> failure.accept(problem));
            }
        });
    }

    private Snapshot readSnapshot() throws IOException {
        configManager.updateAll();
        PluginSettings loaded = PluginSettings.from(configManager.loadConfig());
        Map<String, String> messages = language.readSnapshot(loaded.language());
        return new Snapshot(loaded, messages);
    }

    private void applySnapshot(Snapshot snapshot) {
        settings = snapshot.settings;
        language.apply(snapshot.messages);
        if (dispatcher != null) {
            AsyncDispatcher current = dispatcher;
            if (current.hasSameTopology(snapshot.settings)) {
                current.reconfigure(snapshot.settings);
            } else {
                dispatcher = new AsyncDispatcher(this, snapshot.settings);
                current.shutdown();
            }
        }
        if (playerRateLimiter != null) {
            playerRateLimiter.reconfigure(snapshot.settings.rateLimitCapacity(), snapshot.settings.rateLimitRefill());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().trim();
        String root = commandRoot(message);
        PluginSettings snapshot = settings;
        if (root == null || !snapshot.isLoggedCommand(root)) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission(snapshot.excludePermission())) {
            return;
        }

        ContentFilter.Match blocked = snapshot.contentFilter().find(message);
        if (blocked != null) {
            event.setCancelled(true);
            send(player, language.get("message-blocked"));
            runBlockedContentCommand(player, "command", snapshot);
            queuePlayerAudit(player, language.get("prefix-blocked-cmd") + player.getName() + ": " + message,
                    snapshot.roleId());
            return;
        }
        queuePlayerAudit(player, player.getName() + ": " + message, "");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        PluginSettings snapshot = settings;
        if (!snapshot.checkChat() || event.getPlayer().hasPermission(snapshot.excludePermission())) {
            return;
        }
        if (snapshot.contentFilter().find(event.getMessage()) == null) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        String playerName = player.getName();
        send(player, language.get("message-blocked"));
        runBlockedContentCommand(player, "chat", snapshot);
        queuePlayerAudit(player, language.get("prefix-blocked-chat") + playerName + ": " + event.getMessage(),
                snapshot.roleId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        signNotifications.remove(playerId);
        rateLimitWarnings.remove(playerId);
        playerRateLimiter.remove(playerId);
    }

    private void queuePlayerAudit(Player player, String text, String roleId) {
        UUID playerId = player.getUniqueId();
        if (playerRateLimiter.tryAcquire(playerId)) {
            dispatcher.queueTextMessage(text, roleId);
            return;
        }

        long now = System.nanoTime();
        AtomicLong last = rateLimitWarnings.computeIfAbsent(playerId, ignored -> new AtomicLong());
        long previous = last.get();
        if ((previous == 0L || now - previous > 30_000_000_000L) && last.compareAndSet(previous, now)) {
            getLogger().warning(language.get("spam-warning").replace("{player}", player.getName()));
        }
    }

    public void runBlockedContentCommand(Player player, String trigger, PluginSettings snapshot) {
        if (!snapshot.commandActionEnabled()) {
            return;
        }
        String command = CommandTemplate.render(snapshot.commandActionTemplate(), player.getName(),
                snapshot.commandActionTriggerLabel(trigger));
        if (command.isEmpty()) {
            return;
        }
        scheduler.runGlobal(() -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (RuntimeException failure) {
                getLogger().warning("Blocked-content command failed safely: " + safeMessage(failure));
            }
        });
    }

    public void send(CommandSender sender, String message) {
        String rendered = ChatColor.translateAlternateColorCodes('&', settings.messagePrefix() + message);
        if (sender instanceof Player) {
            runForSender(sender, () -> sender.sendMessage(rendered));
        } else {
            sender.sendMessage(rendered);
        }
    }

    public void runForSender(CommandSender sender, Runnable action) {
        if (sender instanceof Player) {
            scheduler.runForPlayer((Player) sender, action);
        } else {
            scheduler.runGlobal(action);
        }
    }

    public static String commandRoot(String message) {
        if (message == null || message.length() < 2 || message.charAt(0) != '/') {
            return null;
        }
        int end = message.indexOf(' ');
        String root = message.substring(1, end < 0 ? message.length() : end).trim();
        return root.isEmpty() ? null : root;
    }

    public PluginSettings settings() { return settings; }
    public LanguageManager language() { return language; }
    public SchedulerBridge scheduler() { return scheduler; }
    public AsyncDispatcher dispatcher() { return dispatcher; }
    public Map<UUID, Boolean> signNotifications() { return signNotifications; }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        String safe = message == null ? failure.getClass().getSimpleName() : message;
        safe = safe.replaceAll("[\\r\\n\\p{Cntrl}]", " ");
        return safe.length() <= 300 ? safe : safe.substring(0, 300);
    }

    private static final class Snapshot {
        private final PluginSettings settings;
        private final Map<String, String> messages;

        private Snapshot(PluginSettings settings, Map<String, String> messages) {
            this.settings = settings;
            this.messages = messages;
        }
    }
}
