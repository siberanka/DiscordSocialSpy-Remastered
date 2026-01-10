package net.siberanka.discordsocialspy.worker;

import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;

public class AsyncDispatcher {

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final ExecutorService executor;
    private final HttpClient client;
    private volatile String webhook;
    private volatile String prefix;

    private long lastSendTime = 0;
    private final long rateLimitMs = 750; // spam protection: 0.75s between posts

    private final Plugin plugin;

    public AsyncDispatcher(Plugin plugin, String webhook, String prefix) {
        this.plugin = plugin;
        this.webhook = webhook;
        this.prefix = prefix;
        this.client = HttpClient.newHttpClient();
        this.executor = Executors.newSingleThreadExecutor();

        executor.submit(this::processQueue);
    }

    public void queue(String msg) {
        queue.add(msg);
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

                sendWebhook(msg);
                lastSendTime = System.currentTimeMillis();
            }
        } catch (InterruptedException ignored) {}
    }

    private void sendWebhook(String message) {
        if (webhook == null || webhook.isBlank()) return;

        try {
            String json = "{\"content\":\"" + prefix + message.replace("\"","'") + "\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhook))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> {
                        plugin.getLogger().warning("Async webhook failed: " + ex.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            plugin.getLogger().warning("Webhook error: " + e.getMessage());
        }
    }

    public void setWebhook(String w) {
        this.webhook = w;
    }

    public void setPrefix(String p) {
        this.prefix = p;
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
