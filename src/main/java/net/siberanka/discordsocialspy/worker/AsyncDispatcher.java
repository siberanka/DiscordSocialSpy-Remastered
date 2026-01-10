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

    private final Plugin plugin;

    public AsyncDispatcher(Plugin plugin, String webhook, String prefix, String username, String avatarUrl) {
        this.plugin = plugin;
        this.prefix = prefix;
        this.username = username == null ? "" : username;
        this.avatarUrl = avatarUrl == null ? "" : avatarUrl;

        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        this.executor = Executors.newSingleThreadScheduledExecutor();

        setWebhook(webhook);
        executor.submit(this::processQueue);
    }

    private String sanitize(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", " ");
    }

    public void queue(String msg) {
        queue.offer(msg);
    }

    private void processQueue() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                String msg = queue.take();
                sendWebhook(msg);
            }
        } catch (InterruptedException ignored) {}
    }

    private void sendWebhook(String msg) {

        if (webhook == null) {
            plugin.getLogger().warning("[DiscordSocialSpy] Webhook NULL!");
            return;
        }

        String safeContent = sanitize(prefix + msg);

        // **DOĞRU JSON FORMATINI KUR**
        StringBuilder json = new StringBuilder("{");

        // Username varsa her zaman content'ten ÖNCE yazılmalı
        if (!username.isBlank()) {
            json.append("\"username\":\"").append(sanitize(username)).append("\",");

            if (!avatarUrl.isBlank()) {
                json.append("\"avatar_url\":\"").append(sanitize(avatarUrl)).append("\",");
            }
        }

        json.append("\"content\":\"").append(safeContent).append("\"");

        json.append("}");

        String jsonStr = json.toString();

        plugin.getLogger().info("[DiscordSocialSpy] DEBUG SEND JSON: " + jsonStr);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(webhook))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonStr))
                .build();

        client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    plugin.getLogger().info("[DiscordSocialSpy] STATUS: " + res.statusCode());
                    if (res.body() != null && !res.body().isEmpty()) {
                        plugin.getLogger().info("[DiscordSocialSpy] BODY: " + res.body());
                    }
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("[DiscordSocialSpy] ERROR: " + ex.getMessage());
                    return null;
                });
    }

    public void setWebhook(String w) { this.webhook = w; }
    public void setPrefix(String p) { this.prefix = p; }
    public void setUsername(String u) { this.username = u; }
    public void setAvatarUrl(String a) { this.avatarUrl = a; }

    public void shutdown() {
        executor.shutdownNow();
    }
}
