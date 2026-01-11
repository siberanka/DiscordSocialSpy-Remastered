package net.siberanka.discordsocialspy.worker;

import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.*;

public class AsyncDispatcher {

    private final Plugin plugin;

    private BlockingQueue<Runnable> queue;
    private ScheduledExecutorService dispatcherExecutor;
    private ExecutorService senderExecutor;
    private HttpClient client;

    private volatile String webhook;
    private volatile String signWebhook;
    private volatile String prefix;
    private volatile String username;
    private volatile String avatarUrl;

    private int maxRetries;
    private int retryInterval;
    private int rateLimitWait;

    public AsyncDispatcher(
            Plugin plugin,
            int senderThreads,
            int dispatcherThreads,
            int queueSize,
            int maxRetries,
            int retryInterval,
            int rateLimitWait
    ) {
        this.plugin = plugin;

        queue = new LinkedBlockingQueue<>(queueSize);
        dispatcherExecutor = Executors.newScheduledThreadPool(dispatcherThreads <= 0 ? 1 : dispatcherThreads);
        senderExecutor = Executors.newFixedThreadPool(senderThreads <= 0 ? 1 : senderThreads);

        this.maxRetries = maxRetries;
        this.retryInterval = retryInterval;
        this.rateLimitWait = rateLimitWait;

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        dispatcherExecutor.scheduleAtFixedRate(this::processQueue, 1, 1, TimeUnit.SECONDS);
    }

    // === SETTERS ===

    public void setWebhook(String v) {
        webhook = v;
    }

    public void setSignWebhook(String v) {
        signWebhook = v;
    }

    public String getSignWebhook() {
        return signWebhook;
    }

    public void setPrefix(String v) {
        prefix = v;
    }

    public void setUsername(String v) {
        username = v;
    }

    public void setAvatarUrl(String v) {
        avatarUrl = v;
    }


    public void queueTextMessage(String text) {
        queue.offer(() -> sendTextMessage(prefix + text, webhook));
    }


    public void queueEmbed(String title, String desc, String footer, boolean isSign, long timestamp) {

        String targetWebhook = isSign && signWebhook != null && !signWebhook.isEmpty()
                ? signWebhook
                : webhook;

        queue.offer(() -> sendEmbed(title, desc, footer, targetWebhook, timestamp));
    }


    private void processQueue() {

        Runnable task;
        while ((task = queue.poll()) != null) {

            try {
                senderExecutor.submit(task);
            } catch (Exception ex) {
                plugin.getLogger().warning("[DiscordSocialSpy] Failed to submit task: " + ex.getMessage());
            }
        }
    }


    private void sendTextMessage(String text, String targetWebhook) {

        if (targetWebhook == null || targetWebhook.isBlank()) return;

        String json = "{"
                + "\"content\":\"" + escape(text) + "\","
                + "\"username\":\"" + escape(username) + "\","
                + "\"avatar_url\":\"" + escape(avatarUrl) + "\","
                + "\"allowed_mentions\":{\"parse\":[]}"
                + "}";

        sendJSON(json, targetWebhook);
    }


    private void sendEmbed(String title, String desc, String footer, String targetWebhook, long timestamp) {

        if (targetWebhook == null || targetWebhook.isBlank()) return;

        String isoTime = Instant.ofEpochMilli(timestamp).toString();

        String json = "{"
                + "\"username\":\"" + escape(username) + "\","
                + "\"avatar_url\":\"" + escape(avatarUrl) + "\","
                + "\"embeds\":[{"
                + "\"title\":\"" + escape(title) + "\","
                + "\"description\":\"" + escape(desc) + "\","
                + "\"footer\":{\"text\":\"" + escape(footer) + "\"},"
                + "\"timestamp\":\"" + isoTime + "\""
                + "}],"
                + "\"allowed_mentions\":{\"parse\":[]}"
                + "}";

        sendJSON(json, targetWebhook);
    }


    private void sendJSON(String json, String targetWebhook) {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetWebhook))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {

            if (error != null) {
                plugin.getLogger().warning("[DiscordSocialSpy] HTTP error: " + error.getMessage());
                return;
            }

            int code = response.statusCode();

            if (code == 204 || code == 200)
                return;

            plugin.getLogger().warning("[DiscordSocialSpy] HTTP " + code + " Response: " + response.body());
        });
    }

    private String escape(String s) {
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    public void shutdown() {
        dispatcherExecutor.shutdownNow();
        senderExecutor.shutdownNow();
    }
}
