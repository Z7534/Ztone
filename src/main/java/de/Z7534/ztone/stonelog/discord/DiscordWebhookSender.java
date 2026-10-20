package de.Z7534.ztone.stonelog.discord;

import de.Z7534.ztone.stonelog.Stonelog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Versendet Embeds asynchron per HTTP an Discord-Webhooks ueber eine Queue
 * mit Rate-Limit-Tracking und automatischem Backoff.
 * Dadurch werden auch bei vielen gleichzeitigen Anfragen (Spam, Massen-Tode, Joins)
 * keine Nachrichten verworfen.
 */
public class DiscordWebhookSender {

    private static final int MAX_RETRIES = 10;
    private static final int MAX_QUEUE_SIZE = 5000;

    private final Stonelog plugin;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    private final ConcurrentLinkedQueue<WebhookTask> queue = new ConcurrentLinkedQueue<>();
    private final Map<String, Long> rateLimitedUntil = new ConcurrentHashMap<>();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    public DiscordWebhookSender(Stonelog plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Stonelog-Webhook");
            t.setDaemon(true);
            return t;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public void send(List<String> webhookUrls, String username, String avatarUrl, Embed embed) {
        if (webhookUrls == null || webhookUrls.isEmpty()) return;
        String json = JsonUtil.buildPayload(username, avatarUrl, embed);
        for (String url : webhookUrls) {
            if (url != null && !url.isBlank()) {
                enqueue(url, json);
            }
        }
    }

    public void send(String webhookUrl, String username, String avatarUrl, Embed embed) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;
        String json = JsonUtil.buildPayload(username, avatarUrl, embed);
        enqueue(webhookUrl, json);
    }

    private void enqueue(String webhookUrl, String json) {
        if (queue.size() >= MAX_QUEUE_SIZE) {
            plugin.getLogger().warning("Stonelog Webhook Queue ist voll (" + MAX_QUEUE_SIZE + "), älteste Nachricht wird verworfen.");
            queue.poll();
        }
        queue.offer(new WebhookTask(webhookUrl, json, 0));
        triggerQueueProcessing();
    }

    private void triggerQueueProcessing() {
        if (scheduler.isShutdown()) return;
        scheduler.execute(this::processNextTask);
    }

    private void processNextTask() {
        if (scheduler.isShutdown()) return;
        if (!isProcessing.compareAndSet(false, true)) {
            return;
        }

        try {
            long now = System.currentTimeMillis();
            WebhookTask nextTask = null;
            long earliestRateLimit = Long.MAX_VALUE;

            Iterator<WebhookTask> it = queue.iterator();
            while (it.hasNext()) {
                WebhookTask t = it.next();
                Long until = rateLimitedUntil.get(t.url);
                if (until != null && until > now) {
                    if (until < earliestRateLimit) {
                        earliestRateLimit = until;
                    }
                    continue;
                }
                it.remove();
                nextTask = t;
                break;
            }

            if (nextTask == null) {
                isProcessing.set(false);
                if (!queue.isEmpty() && earliestRateLimit != Long.MAX_VALUE) {
                    long delayMs = Math.max(50, earliestRateLimit - now + 50);
                    scheduler.schedule(this::triggerQueueProcessing, delayMs, TimeUnit.MILLISECONDS);
                }
                return;
            }

            dispatch(nextTask);
        } catch (Throwable t) {
            isProcessing.set(false);
            plugin.getLogger().warning("Fehler beim Verarbeiten der Webhook-Queue: " + t.getMessage());
        }
    }

    private void dispatch(WebhookTask task) {
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(task.url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Stonelog (Minecraft-Plugin)")
                    .POST(HttpRequest.BodyPublishers.ofString(task.json, StandardCharsets.UTF_8))
                    .build();
        } catch (Exception e) {
            plugin.getLogger().warning("Ungueltige Webhook-URL: " + task.url);
            isProcessing.set(false);
            triggerQueueProcessing();
            return;
        }

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    long pauseMs = 80;

                    if (error != null) {
                        if (task.attempt < MAX_RETRIES) {
                            task.attempt++;
                            queue.offer(task);
                            pauseMs = 1500;
                        } else {
                            plugin.getLogger().warning("Discord-Webhook konnte nicht gesendet werden nach "
                                    + MAX_RETRIES + " Versuchen: " + rootMessage(error));
                        }
                    } else {
                        int code = response.statusCode();
                        if (code == 429) {
                            long retryAfterMs = parseRetryAfterMs(response);
                            rateLimitedUntil.put(task.url, System.currentTimeMillis() + retryAfterMs);
                            if (task.attempt < MAX_RETRIES) {
                                task.attempt++;
                                queue.offer(task);
                            } else {
                                plugin.getLogger().warning("Discord-Webhook: Rate-Limit überschritten (" + MAX_RETRIES + " Versuche), Nachricht verworfen.");
                            }
                            pauseMs = Math.max(100, retryAfterMs);
                        } else if (code >= 500) {
                            if (task.attempt < MAX_RETRIES) {
                                task.attempt++;
                                queue.offer(task);
                                pauseMs = 2000;
                            } else {
                                plugin.getLogger().warning("Discord-Webhook Serverfehler (" + code + "), Nachricht nach " + MAX_RETRIES + " Versuchen verworfen.");
                            }
                        } else if (code >= 300) {
                            plugin.getLogger().warning("Discord-Webhook Fehler (" + code + "): " + response.body());
                        }
                    }

                    isProcessing.set(false);
                    if (!scheduler.isShutdown()) {
                        scheduler.schedule(this::processNextTask, Math.max(50, pauseMs), TimeUnit.MILLISECONDS);
                    }
                });
    }

    private long parseRetryAfterMs(HttpResponse<String> response) {
        var headerOpt = response.headers().firstValue("Retry-After");
        if (headerOpt.isPresent()) {
            try {
                double sec = Double.parseDouble(headerOpt.get().trim());
                return (long) Math.ceil(sec * 1000.0) + 150L;
            } catch (Exception ignored) {}
        }

        String body = response.body();
        if (body != null) {
            try {
                int idx = body.indexOf("\"retry_after\"");
                if (idx >= 0) {
                    int colon = body.indexOf(':', idx);
                    StringBuilder num = new StringBuilder();
                    for (int i = colon + 1; i < body.length(); i++) {
                        char c = body.charAt(i);
                        if (Character.isDigit(c) || c == '.') {
                            num.append(c);
                        } else if (num.length() > 0) {
                            break;
                        }
                    }
                    if (num.length() > 0) {
                        double sec = Double.parseDouble(num.toString());
                        return (long) Math.ceil(sec * 1000.0) + 150L;
                    }
                }
            } catch (Exception ignored) {}
        }
        return 2000L;
    }

    private String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + ": " + cause.getMessage();
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }

    private static class WebhookTask {
        final String url;
        final String json;
        int attempt;

        WebhookTask(String url, String json, int attempt) {
            this.url = url;
            this.json = json;
            this.attempt = attempt;
        }
    }
}
