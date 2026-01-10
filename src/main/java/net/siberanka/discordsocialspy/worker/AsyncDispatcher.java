package net.siberanka.discordsocialspy.worker;

import org.bukkit.plugin.Plugin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;

public class AsyncDispatcher {

    private static class QueueItem {
        public String text;
        public int attempts = 0;
        public QueueItem(String text) { this.text = text; }
    }

    private BlockingQueue<QueueItem> queue;
    private ScheduledExecutorService dispatcherExecutor;
    private ExecutorService senderExecutor;
    private HttpClient client;
    private final Plugin plugin;

    private volatile String webhook;
    private volatile String prefix;
    private volatile String username;
    private volatile String avatarUrl;

    private int lastErrorCode = -1;
    private int maxRetries;
    private int retryInterval;
    private int rateLimitWait;

    public AsyncDispatcher(Plugin plugin, int senderThreads, int dispatcherThreads, int queueSize, int maxRetries, int retryInterval, int rateLimitWait) {
        this.plugin = plugin;

        if (senderThreads == -1) senderThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        if (dispatcherThreads == -1) dispatcherThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

        this.maxRetries = maxRetries;
        this.retryInterval = retryInterval;
        this.rateLimitWait = rateLimitWait;

        queue = new LinkedBlockingQueue<>(queueSize);
        dispatcherExecutor = Executors.newScheduledThreadPool(dispatcherThreads);
        senderExecutor = Executors.newFixedThreadPool(senderThreads);

        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        dispatcherExecutor.scheduleAtFixedRate(this::processQueue, 1, 1, TimeUnit.SECONDS);
    }

    public void queueMessage(String text) {
        if (text.length() > 2000) text = text.substring(0, 2000);
        boolean offered = queue.offer(new QueueItem(text));
        if (!offered) plugin.getLogger().warning("Message queue is full. Some logs are being skipped.");
    }

    private void processQueue() {
        try {
            QueueItem item;
            while ((item = queue.poll()) != null) sendMessageAsync(item);
        } catch (Exception ignored) {}
    }

    private void sendMessageAsync(QueueItem item) {

        if (webhook == null || webhook.isBlank()) return;

        String json = "{"
                + "\"content\":\"" + escapeJson(prefix + item.text) + "\","
                + "\"username\":\"" + escapeJson(username) + "\","
                + "\"avatar_url\":\"" + escapeJson(avatarUrl) + "\","
                + "\"allowed_mentions\":{\"parse\":[]}"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhook))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, ex) -> {
                    if (ex != null) {
                        retryMessage(item);
                        return;
                    }
                    handleResponse(response, item);
                });
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    private void handleResponse(HttpResponse<String> response, QueueItem item) {
        int status = response.statusCode();

        if (status == 200 || status == 204) {
            lastErrorCode = -1;
            return;
        }

        if (status == 429) {
            lastErrorCode = status;
            dispatcherExecutor.schedule(() -> retryMessage(item), rateLimitWait, TimeUnit.SECONDS);
            return;
        }

        if (status == 400) {
            lastErrorCode = status;
            return;
        }

        lastErrorCode = status;
        retryMessage(item);
    }

    private void retryMessage(QueueItem item) {
        item.attempts++;
        if (item.attempts > maxRetries) return;
        dispatcherExecutor.schedule(() -> queue.offer(item), retryInterval, TimeUnit.SECONDS);
    }

    public void setWebhook(String v) { webhook = v; }
    public void setPrefix(String v) { prefix = v; }
    public void setUsername(String v) { username = v; }
    public void setAvatarUrl(String v) { avatarUrl = v; }

    public void shutdown() {
        dispatcherExecutor.shutdownNow();
        senderExecutor.shutdownNow();
    }
}
