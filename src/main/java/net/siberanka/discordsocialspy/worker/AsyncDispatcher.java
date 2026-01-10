package net.siberanka.discordsocialspy.worker;

import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;

public class AsyncDispatcher {

    // Queue overflow protection: max 2000 entries
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(2000);

    private final ExecutorService executor;
    private final HttpClient client;

    private volatile String webhook;
    private volatile String prefix;
    private volatile String username;
    private volatile String avatarUrl;

    private long lastSendTime = 0;
    private final long rateLimitMs = 750; // spam protection: 0.75s between posts

    private final Plugin plugin;

    public AsyncDispatcher(Plugin plugin, String webhook, String prefix, String username, String avatarUrl) {
        this.plugin = plugin;
        this.prefix = prefix;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.client = HttpClient.newHttpClient();
        this.executor = Executors.newSingleThreadExecutor();

        setWebhook(webhook); // validate webhook

        executor.submit(this::processQueue);
    }

    /**
     * Safely add message with overflow protection.
     */
    public void queue(String msg) {
        if (!queue.offer(msg)) {
            plugin.getLogger().warning("DiscordSocialSpy queue is full! Message dropped: " + msg);
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

    /**
     * Full JSON sanitizer to prevent injection, formatting breaks, and escape exploits.
     */
    private String sanitizeJson(String input) {
        if (input == null) return "";

        String sanitized = input;

        sanitized = sanitized.replace("\\", "\\\\");
        sanitized = sanitized.replace("\"", "\\\"");
        sanitized = sanitized.replace("\n", "\\n");
        sanitized = sanitized.replace("\r", "\\r");
        sanitized = sanitized.replace("\t", "\\t");

        // Remove ASCII control characters (0–31)
        sanitized = sanitized.replaceAll("[\\x00-\\x1F]", "");

        return sanitized;
    }

    /**
     * Retry / backoff system for webhook failures.
     */
    private void sendWebhookWithRetry(String message, int attempt) {

        if (webhook == null) return;

        String sanitized = sanitizeJson(prefix + message);

        String json = "{"
                + "\"username\":\"" + sanitizeJson(username) + "\","
                + "\"avatar_url\":\"" + sanitizeJson(avatarUrl) + "\","
                + "\"content\":\"" + sanitized + "\""
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhook))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((res, ex) -> {
                    if (ex != null || res.statusCode() >= 400) {
                        if (attempt >= 3) {
                            plugin.getLogger().warning("Webhook failed after 3 retries, dropping message: " + message);
                            return;
                        }

                        long backoffDelay = (attempt == 0 ? 1000 : attempt == 1 ? 3000 : 7000);

                        plugin.getLogger().warning(
                                "Webhook failed (attempt " + (attempt + 1) + "), retrying in " + backoffDelay + "ms");

                        executor.schedule(() -> sendWebhookWithRetry(message, attempt + 1),
                                backoffDelay, TimeUnit.MILLISECONDS);
                    }
                });
    }

    /**
     * Validate webhook format before setting.
     */
    public void setWebhook(String w) {
        if (w == null || w.isBlank()) {
            this.webhook = null;
            plugin.getLogger().warning("Webhook disabled: empty or null URL.");
            return;
        }

        if (!w.startsWith("https://discord.com/api/webhooks/")) {
            this.webhook = null;
            plugin.getLogger().warning("Invalid webhook URL format! Webhook disabled: " + w);
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
