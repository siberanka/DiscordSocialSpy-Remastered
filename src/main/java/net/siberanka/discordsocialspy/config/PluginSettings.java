package net.siberanka.discordsocialspy.config;

import net.siberanka.discordsocialspy.util.ContentFilter;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

public final class PluginSettings {

    private final String language;
    private final String webhook;
    private final String signWebhook;
    private final String bookWebhook;
    private final String username;
    private final String avatarUrl;
    private final String discordPrefix;
    private final String messagePrefix;
    private final String excludePermission;
    private final Set<String> loggedCommands;
    private final ContentFilter contentFilter;
    private final boolean checkChat;
    private final boolean checkSigns;
    private final boolean checkBooks;
    private final boolean commandActionEnabled;
    private final String commandActionTemplate;
    private final Map<String, String> commandActionTriggerLabels;
    private final String roleId;
    private final boolean logSigns;
    private final boolean logSignsToConsole;
    private final boolean logBooks;
    private final boolean logBooksToConsole;
    private final boolean defaultSignNotify;
    private final boolean updateCheck;
    private final boolean debug;
    private final int senderThreads;
    private final int queueSize;
    private final int maxRetries;
    private final int retryIntervalSeconds;
    private final int rateLimitWaitSeconds;
    private final int requestTimeoutSeconds;
    private final int shutdownTimeoutSeconds;
    private final int rateLimitCapacity;
    private final double rateLimitRefill;

    private PluginSettings(YamlConfiguration config) {
        language = config.getString("language", "en");
        webhook = usableWebhook(config.getString("webhook", ""));
        signWebhook = usableWebhook(config.getString("sign-webhook", ""));
        bookWebhook = usableWebhook(config.getString("book-webhook", ""));
        username = config.getString("username", "SocialSpy");
        avatarUrl = config.getString("avatar_url", "");
        discordPrefix = config.getString("prefix", "[Spy] ");
        messagePrefix = config.getString("message-prefix", "&8[&bDSS&8] &r");
        excludePermission = config.getString("exclude-permission", "discordspy.bypass");

        LinkedHashSet<String> commands = new LinkedHashSet<>();
        for (String command : config.getStringList("logged-commands")) {
            commands.add(command.toLowerCase(Locale.ROOT));
        }
        loggedCommands = Collections.unmodifiableSet(commands);

        List<String> words = config.getStringList("filter.words");
        List<String> whitelist = config.getStringList("filter.whitelisted-words");
        List<String> expressions = config.getStringList("filter.regex");
        contentFilter = ContentFilter.compile(config.getBoolean("filter.enabled"),
                config.getBoolean("filter.smart-detection", true), words, whitelist, expressions);
        checkChat = config.getBoolean("filter.check-chat");
        checkSigns = config.getBoolean("filter.check-signs", true);
        checkBooks = config.getBoolean("filter.check-books", true);
        commandActionEnabled = config.getBoolean("filter.command-action.enabled", false);
        commandActionTemplate = config.getString("filter.command-action.command", "");
        commandActionTriggerLabels = Map.of(
                "chat", config.getString("filter.command-action.trigger-labels.chat", "sohbet"),
                "command", config.getString("filter.command-action.trigger-labels.command", "komut"),
                "sign", config.getString("filter.command-action.trigger-labels.sign", "tabela"),
                "book", config.getString("filter.command-action.trigger-labels.book", "kitap"));
        roleId = config.getString("filter.role-uuid", "");
        logSigns = config.getBoolean("log-signs", true);
        logSignsToConsole = config.getBoolean("log-signs-to-console", false);
        logBooks = config.getBoolean("log-books", true);
        logBooksToConsole = config.getBoolean("log-books-to-console", false);
        defaultSignNotify = config.getBoolean("sign-notify", false);
        updateCheck = config.getBoolean("update-check.enabled", true);
        debug = config.getBoolean("debug", false);

        int configuredThreads = config.getInt("async.sender_threads", -1);
        senderThreads = configuredThreads == -1 ? 2 : configuredThreads;
        queueSize = config.getInt("async.queue_size", 1024);
        maxRetries = config.getInt("async.max_retries", 3);
        retryIntervalSeconds = config.getInt("async.retry_interval", 2);
        rateLimitWaitSeconds = config.getInt("async.rate_limit_wait", 3);
        requestTimeoutSeconds = config.getInt("async.request_timeout", 10);
        shutdownTimeoutSeconds = config.getInt("async.shutdown_timeout", 5);
        rateLimitCapacity = config.getInt("rate-limit.capacity", 8);
        rateLimitRefill = config.getDouble("rate-limit.refill-per-second", 2.0);
    }

    public static PluginSettings from(YamlConfiguration config) {
        return new PluginSettings(config);
    }

    public boolean isLoggedCommand(String command) {
        String normalized = command.toLowerCase(Locale.ROOT);
        if (loggedCommands.contains(normalized)) {
            return true;
        }
        int namespace = normalized.indexOf(':');
        return namespace >= 0 && loggedCommands.contains(normalized.substring(namespace + 1));
    }

    private static String usableWebhook(String value) {
        if (value == null || value.isBlank() || value.startsWith("PUT_YOUR_")) {
            return "";
        }
        return value;
    }

    public String language() { return language; }
    public String webhook() { return webhook; }
    public String signWebhook() { return signWebhook; }
    public String bookWebhook() { return bookWebhook; }
    public String username() { return username; }
    public String avatarUrl() { return avatarUrl; }
    public String discordPrefix() { return discordPrefix; }
    public String messagePrefix() { return messagePrefix; }
    public String excludePermission() { return excludePermission; }
    public Set<String> loggedCommands() { return loggedCommands; }
    public ContentFilter contentFilter() { return contentFilter; }
    public boolean checkChat() { return checkChat; }
    public boolean checkSigns() { return checkSigns; }
    public boolean checkBooks() { return checkBooks; }
    public boolean commandActionEnabled() { return commandActionEnabled; }
    public String commandActionTemplate() { return commandActionTemplate; }
    public String commandActionTriggerLabel(String trigger) {
        return commandActionTriggerLabels.getOrDefault(trigger, trigger);
    }
    public String roleId() { return roleId; }
    public boolean logSigns() { return logSigns; }
    public boolean logSignsToConsole() { return logSignsToConsole; }
    public boolean logBooks() { return logBooks; }
    public boolean logBooksToConsole() { return logBooksToConsole; }
    public boolean defaultSignNotify() { return defaultSignNotify; }
    public boolean updateCheck() { return updateCheck; }
    public boolean debug() { return debug; }
    public int senderThreads() { return senderThreads; }
    public int queueSize() { return queueSize; }
    public int maxRetries() { return maxRetries; }
    public int retryIntervalSeconds() { return retryIntervalSeconds; }
    public int rateLimitWaitSeconds() { return rateLimitWaitSeconds; }
    public int requestTimeoutSeconds() { return requestTimeoutSeconds; }
    public int shutdownTimeoutSeconds() { return shutdownTimeoutSeconds; }
    public int rateLimitCapacity() { return rateLimitCapacity; }
    public double rateLimitRefill() { return rateLimitRefill; }
}
