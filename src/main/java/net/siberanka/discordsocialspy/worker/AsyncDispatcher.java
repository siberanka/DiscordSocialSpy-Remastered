package net.siberanka.discordsocialspy.worker;

import net.siberanka.discordsocialspy.config.PluginSettings;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** A bounded, non-blocking Discord delivery pipeline with retry and 429 handling. */
@SuppressWarnings("deprecation")
public final class AsyncDispatcher {

    private final Plugin plugin;
    private final ArrayBlockingQueue<Delivery> queue;
    private final ScheduledThreadPoolExecutor coordinator;
    private final ExecutorService httpExecutor;
    private final HttpClient client;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong lastOverflowWarning = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicInteger scheduledRetries = new AtomicInteger();
    private final int maximumConcurrency;
    private final int queueCapacity;
    private volatile PluginSettings settings;

    public AsyncDispatcher(Plugin plugin, PluginSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        maximumConcurrency = settings.senderThreads();
        queueCapacity = settings.queueSize();
        queue = new ArrayBlockingQueue<>(queueCapacity);
        coordinator = new ScheduledThreadPoolExecutor(1, runnable -> daemon(runnable, "dss-dispatch"));
        coordinator.setRemoveOnCancelPolicy(true);
        httpExecutor = Executors.newFixedThreadPool(maximumConcurrency,
                runnable -> daemon(runnable, "dss-http"));
        client = HttpClient.newBuilder()
                .executor(httpExecutor)
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        coordinator.scheduleWithFixedDelay(this::drain, 0, 25, TimeUnit.MILLISECONDS);
    }

    public void reconfigure(PluginSettings updated) {
        settings = updated;
    }

    public boolean hasSameTopology(PluginSettings updated) {
        return maximumConcurrency == updated.senderThreads() && queueCapacity == updated.queueSize();
    }

    public boolean queueTextMessage(String text) {
        return queueTextMessage(text, "");
    }

    public boolean queueTextMessage(String text, String roleId) {
        PluginSettings snapshot = settings;
        if (snapshot.webhook().isEmpty()) {
            return false;
        }
        String body = JsonPayloads.text(snapshot.discordPrefix() + text, snapshot.username(),
                snapshot.avatarUrl(), roleId == null ? "" : roleId);
        return enqueue(new Delivery(snapshot.webhook(), body, 0));
    }

    public boolean queueEmbed(String title, String description, String footer, boolean sign, long timestamp,
            String roleId) {
        PluginSettings snapshot = settings;
        String target = sign && !snapshot.signWebhook().isEmpty() ? snapshot.signWebhook() : snapshot.webhook();
        if (target.isEmpty()) {
            return false;
        }
        String body = JsonPayloads.embed(title, description, footer,
                snapshot.username(), snapshot.avatarUrl(), Instant.ofEpochMilli(timestamp).toString(),
                roleId == null ? "" : roleId);
        return enqueue(new Delivery(target, body, 0));
    }

    private boolean enqueue(Delivery delivery) {
        if (closed.get()) {
            return false;
        }
        boolean accepted = queue.offer(delivery);
        if (!accepted) {
            long now = System.nanoTime();
            long previous = lastOverflowWarning.get();
            if ((previous == 0L || now - previous > TimeUnit.SECONDS.toNanos(30))
                    && lastOverflowWarning.compareAndSet(previous, now)) {
                plugin.getLogger().warning("Discord queue is full; new audit messages are being dropped safely.");
            }
        }
        return accepted;
    }

    private void drain() {
        try {
            while (!closed.get() && inFlight.get() < maximumConcurrency) {
                Delivery delivery = queue.poll();
                if (delivery == null) {
                    return;
                }
                send(delivery);
            }
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("Discord dispatcher recovered from an internal error: " + safe(failure));
        }
    }

    private void send(Delivery delivery) {
        PluginSettings snapshot = settings;
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(delivery.webhook))
                    .timeout(Duration.ofSeconds(snapshot.requestTimeoutSeconds()))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "DiscordSocialSpy/" + plugin.getDescription().getVersion())
                    .POST(HttpRequest.BodyPublishers.ofString(delivery.json))
                    .build();
        } catch (RuntimeException invalid) {
            plugin.getLogger().warning("Discarded a delivery with an invalid webhook configuration.");
            return;
        }

        inFlight.incrementAndGet();
        try {
            client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete((response, failure) -> {
                inFlight.decrementAndGet();
                if (closed.get()) {
                    return;
                }
                if (failure != null) {
                    retry(delivery, snapshot.retryIntervalSeconds(), "network failure: " + safe(failure));
                } else if (response.statusCode() == 200 || response.statusCode() == 204) {
                    if (snapshot.debug()) {
                        plugin.getLogger().info("Discord delivery completed with HTTP " + response.statusCode() + '.');
                    }
                } else if (response.statusCode() == 429) {
                    long wait = parseRetryAfter(response, snapshot.rateLimitWaitSeconds());
                    retry(delivery, wait, "Discord rate limit");
                } else if (response.statusCode() >= 500) {
                    retry(delivery, snapshot.retryIntervalSeconds(), "Discord HTTP " + response.statusCode());
                } else {
                    plugin.getLogger().warning("Discord rejected a delivery with HTTP " + response.statusCode()
                            + "; the webhook URL and permissions should be checked.");
                }
            });
        } catch (RuntimeException failure) {
            inFlight.decrementAndGet();
            retry(delivery, snapshot.retryIntervalSeconds(), "request submission failure: " + safe(failure));
        }
    }

    private void retry(Delivery delivery, long delaySeconds, String reason) {
        PluginSettings snapshot = settings;
        if (delivery.attempt >= snapshot.maxRetries()) {
            plugin.getLogger().warning("Discord delivery abandoned after " + (delivery.attempt + 1)
                    + " attempt(s): " + reason);
            return;
        }
        long exponential = Math.min(120L, Math.max(1L, delaySeconds) * (1L << Math.min(delivery.attempt, 6)));
        if (scheduledRetries.incrementAndGet() > queueCapacity) {
            scheduledRetries.decrementAndGet();
            plugin.getLogger().warning("Discord retry capacity is exhausted; a delivery was dropped safely.");
            return;
        }
        try {
            coordinator.schedule(() -> {
                scheduledRetries.decrementAndGet();
                enqueue(delivery.next());
            }, exponential, TimeUnit.SECONDS);
        } catch (RuntimeException rejected) {
            scheduledRetries.decrementAndGet();
            if (!closed.get()) {
                plugin.getLogger().warning("Discord retry scheduling failed safely: " + safe(rejected));
            }
        }
    }

    private static long parseRetryAfter(HttpResponse<?> response, int fallback) {
        String header = response.headers().firstValue("Retry-After").orElse("");
        try {
            double seconds = Double.parseDouble(header);
            return Math.max(1L, Math.min(120L, (long) Math.ceil(seconds)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public void shutdown() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        queue.clear();
        coordinator.shutdownNow();
        httpExecutor.shutdown();
        try {
            if (!httpExecutor.awaitTermination(settings.shutdownTimeoutSeconds(), TimeUnit.SECONDS)) {
                httpExecutor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            httpExecutor.shutdownNow();
        }
    }

    public int queuedMessages() {
        return queue.size();
    }

    private Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((ignored, failure) ->
                plugin.getLogger().warning("Uncaught " + name + " failure: " + safe(failure)));
        return thread;
    }

    private static String safe(Throwable failure) {
        String message = failure.getMessage();
        String safe = message == null ? failure.getClass().getSimpleName() : message;
        safe = safe.replaceAll("[\\r\\n\\p{Cntrl}]", " ");
        return safe.length() <= 240 ? safe : safe.substring(0, 240);
    }

    private static final class Delivery {
        private final String webhook;
        private final String json;
        private final int attempt;

        private Delivery(String webhook, String json, int attempt) {
            this.webhook = webhook;
            this.json = json;
            this.attempt = attempt;
        }

        private Delivery next() {
            return new Delivery(webhook, json, attempt + 1);
        }
    }
}
