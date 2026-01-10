package net.siberanka.discordsocialspy.worker;

import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;

public class AsyncDispatcher {

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(2000);

    private final ScheduledExecutorService executor;
    private final HttpClient client;

    private volatile String webhook;
    private volatile String prefix;
    private volatile String username;
    private volatile String avatarUrl;

    private long lastSendTime = 0;
    private final long rateLimitMs = 750;

    private final Plugin plugin;

    // Old constructor (backwards compatibility)
    public AsyncDispatcher(Plugin plugin, String webhook, String prefix) {
        this(plugin, webhook, prefix, "", "");
    }

    // Full constructor
    public AsyncDispatcher(Plugin plugin, String webhook, String prefix, String username, String avatarUrl) {
        this.plugin = plugin;
        this.prefix = prefix;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.client = HttpClient.newHttpClient();
        this.executor = Executors.newSingleThreadScheduledExecutor();

        setWebhook(webhook);
        executor.submit(this::processQueue);
    }

    public void queue(String msg) {
        if (!queue.offer(msg)) {
            plugin.getLogger().warning("[DiscordSocialSpy] Queue full, message dropped.");
        }
    }

    private void processQueue() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                String msg = queue.take();

                long now = System.currentTimeMillis();
                long diff = now - lastSendTime;

                if (diff < rateLimitMs) {
                    Thread.sleep(rateLimitMs - diff);
                }

                sendWebhookWithRetry(msg, 0);
                lastSendTime = System.currentTimeMillis();
            }
        } catch (InterruptedException ignored) {}
    }

    private String sanitizeJson(String input) {
        if (input == null) return "";

        String sanitized = input;
        sanitized = sanitized.replace("\\", "\\\\");
        sanitized = sanitized.replace("\"", "\\\"");
        sanitized = sanitized.replace("\n", "\\n");
        sanitized = sanitized.replace("\r", "\\r");
        sanitized = sanitized.replace("\t", "\\t");
        sanitized = sanitized.replaceAll("[\\x00-\\x1F]", "");

        return sanitized;
    }

    private void sendWebhookWithRetry(String message, int attempt) {

        if (webhook == null) return;

        String sanitized = sanitizeJson(prefix + message);

        // Build JSON dynamically based on Discord rules
        StringBuilder json = new StringBuilder("{");

        // Always required
        json.append("\"content\":\"").append(sanitized).append("\"");

        // username only if not blank
        if (username != null && !username.isBlank()) {
            json.append(",\"username\":\"").append(sanitizeJson(username)).append("\"");

            // avatar_url only valid if username is also present
            if (avatarUrl != null && !avatarUrl.isBlank()) {
                json.append(",\"avatar_url\":\"").append(sanitizeJson(avatarUrl)).append("\"");
            }
        }

        json.append("}");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhook))
                .header("Content-Type", "application/json")
                .header("User-Agent", "DiscordSocialSpy/1.0") // Critical fix
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((res, ex) -> {

                    // Null or exception: retry
                    if (ex != null || res == null) {
                        retryOrDrop(message, attempt, "Null response or exception");
                        return;
                    }

                    // HTTP error code
                    int code = res.statusCode();

                    if (code >= 400) {
                        retryOrDrop(message, attempt, "HTTP " + code);
                        return;
                    }

                    // SUCCESS → stop retrying
                });
    }

    private void retryOrDrop(String message, int attempt, String reason) {
        if (attempt >= 3) {
            plugin.getLogger().warning("[DiscordSocialSpy] Dropped message after retries (" + reason + ")");
            return;
        }

        long delay = switch (attempt) {
            case 0 -> 1000;  // 1s
            case 1 -> 3000;  // 3s
            default -> 7000; // 7s
        };

        plugin.getLogger().warning("[DiscordSocialSpy] Webhook failed (" + reason + "). Retrying in " + delay + "ms");

        executor.schedule(() -> sendWebhookWithRetry(message, attempt + 1),
                delay, TimeUnit.MILLISECONDS);
    }

    public void setWebhook(String w) {
        if (w == null || w.isBlank()) {
            this.webhook = null;
            plugin.getLogger().warning("[DiscordSocialSpy] Webhook disabled (empty)");
            return;
        }

        if (!w.startsWith("https://discord.com/api/webhooks/")) {
            this.webhook = null;
            plugin.getLogger().warning("[DiscordSocialSpy] Invalid webhook URL, disabled: " + w);
            return;
        }

        this.webhook = w;
    }

    public void setPrefix(String p) {
        this.prefix = p;
    }

    public void setUsername(String u) {
        this.username = u;
    }

    public void setAvatarUrl(String a) {
        this.avatarUrl = a;
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
