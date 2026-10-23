package de.Z7534.ztone.stonelog.discord;

import de.Z7534.ztone.stonelog.Stonelog;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Versendet Embeds asynchron per HTTP an Discord-Webhooks ueber parallele,
 * URL-getrennte Queues mit automatischem Embed-Batching (bis zu 10 Embeds pro POST),
 * Rate-Limit-Tracking und automatischem Backoff.
 * Dadurch laufen Queues auch bei extremem Chat-/Befehlsaufkommen nicht mehr voll.
 */
public class DiscordWebhookSender {

    private static final int MAX_RETRIES = 5;
    private static final int MAX_QUEUE_SIZE_PER_URL = 5000;
    private static final int BATCH_SIZE = 10;

    private final Stonelog plugin;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    private final Map<String, ConcurrentLinkedQueue<WebhookItem>> urlQueues = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> urlWorkers = new ConcurrentHashMap<>();
    private final Map<String, Long> rateLimitedUntil = new ConcurrentHashMap<>();

    public DiscordWebhookSender(Stonelog plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "Stonelog-Webhook-Worker");
            t.setDaemon(true);
            return t;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public void send(List<String> webhookUrls, String username, String avatarUrl, Embed embed) {
        if (webhookUrls == null || webhookUrls.isEmpty() || embed == null) return;
        prepareEmbedAuthor(username, avatarUrl, embed);
        for (String url : webhookUrls) {
            if (url != null && !url.isBlank()) {
                enqueue(url, username, avatarUrl, embed);
            }
        }
    }

    public void send(String webhookUrl, String username, String avatarUrl, Embed embed) {
        if (webhookUrl == null || webhookUrl.isBlank() || embed == null) return;
        prepareEmbedAuthor(username, avatarUrl, embed);
        enqueue(webhookUrl, username, avatarUrl, embed);
    }

    private void prepareEmbedAuthor(String username, String avatarUrl, Embed embed) {
        if (embed.authorName == null && username != null && !username.isBlank()) {
            embed.authorName = username;
        }
        if (embed.authorIconUrl == null && avatarUrl != null && !avatarUrl.isBlank()) {
            embed.authorIconUrl = avatarUrl;
        }
    }

    private void enqueue(String webhookUrl, String username, String avatarUrl, Embed embed) {
        ConcurrentLinkedQueue<WebhookItem> queue = urlQueues.computeIfAbsent(webhookUrl, k -> new ConcurrentLinkedQueue<>());
        if (queue.size() >= MAX_QUEUE_SIZE_PER_URL) {
            plugin.getLogger().warning("Stonelog Webhook Queue voll (" + MAX_QUEUE_SIZE_PER_URL + ") fuer " + webhookUrl + ", aelteste Nachricht verworfen.");
            queue.poll();
        }
        queue.offer(new WebhookItem(username, avatarUrl, embed, 0));
        triggerQueueProcessing(webhookUrl);
    }

    private void triggerQueueProcessing(String webhookUrl) {
        if (scheduler.isShutdown()) return;
        AtomicBoolean worker = urlWorkers.computeIfAbsent(webhookUrl, k -> new AtomicBoolean(false));
        if (worker.compareAndSet(false, true)) {
            scheduler.execute(() -> processUrlQueue(webhookUrl));
        }
    }

    private void processUrlQueue(String webhookUrl) {
        if (scheduler.isShutdown()) return;

        try {
            long now = System.currentTimeMillis();
            Long until = rateLimitedUntil.get(webhookUrl);
            if (until != null && until > now) {
                long delay = Math.max(50, until - now + 50);
                releaseWorker(webhookUrl);
                scheduler.schedule(() -> triggerQueueProcessing(webhookUrl), delay, TimeUnit.MILLISECONDS);
                return;
            }

            ConcurrentLinkedQueue<WebhookItem> queue = urlQueues.get(webhookUrl);
            if (queue == null || queue.isEmpty()) {
                releaseWorker(webhookUrl);
                return;
            }

            List<WebhookItem> batch = new ArrayList<>(BATCH_SIZE);
            for (int i = 0; i < BATCH_SIZE; i++) {
                WebhookItem item = queue.poll();
                if (item == null) break;
                batch.add(item);
            }

            if (batch.isEmpty()) {
                releaseWorker(webhookUrl);
                return;
            }

            dispatchBatch(webhookUrl, batch);
        } catch (Throwable t) {
            releaseWorker(webhookUrl);
            plugin.getLogger().warning("Fehler beim Verarbeiten der Webhook-Queue fuer " + webhookUrl + ": " + t.getMessage());
        }
    }

    private void releaseWorker(String webhookUrl) {
        AtomicBoolean worker = urlWorkers.get(webhookUrl);
        if (worker != null) {
            worker.set(false);
        }
    }

    private void dispatchBatch(String webhookUrl, List<WebhookItem> batch) {
        String topUsername = batch.get(0).username;
        String topAvatar = batch.get(0).avatarUrl;
        List<Embed> embeds = new ArrayList<>(batch.size());
        for (WebhookItem item : batch) {
            embeds.add(item.embed);
        }

        String json = JsonUtil.buildPayload(topUsername, topAvatar, embeds);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Stonelog (Minecraft-Plugin)")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
        } catch (Exception e) {
            plugin.getLogger().warning("Ungueltige Webhook-URL: " + webhookUrl);
            releaseWorker(webhookUrl);
            return;
        }

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    long pauseMs = 15;

                    if (error != null) {
                        ConcurrentLinkedQueue<WebhookItem> queue = urlQueues.get(webhookUrl);
                        if (queue != null) {
                            for (WebhookItem item : batch) {
                                if (item.attempt < MAX_RETRIES) {
                                    item.attempt++;
                                    queue.offer(item);
                                }
                            }
                        }
                        pauseMs = 1000;
                    } else {
                        int code = response.statusCode();
                        if (code == 429) {
                            long retryAfterMs = parseRetryAfterMs(response);
                            rateLimitedUntil.put(webhookUrl, System.currentTimeMillis() + retryAfterMs);
                            ConcurrentLinkedQueue<WebhookItem> queue = urlQueues.get(webhookUrl);
                            if (queue != null) {
                                for (WebhookItem item : batch) {
                                    if (item.attempt < MAX_RETRIES) {
                                        item.attempt++;
                                        queue.offer(item);
                                    }
                                }
                            }
                            pauseMs = Math.max(100, retryAfterMs);
                        } else if (code >= 500) {
                            ConcurrentLinkedQueue<WebhookItem> queue = urlQueues.get(webhookUrl);
                            if (queue != null) {
                                for (WebhookItem item : batch) {
                                    if (item.attempt < MAX_RETRIES) {
                                        item.attempt++;
                                        queue.offer(item);
                                    }
                                }
                            }
                            pauseMs = 1500;
                        } else if (code >= 300) {
                            plugin.getLogger().warning("Discord-Webhook Fehler (" + code + "): " + response.body());
                        } else {
                            // Erfolg (200..299)
                            var remHeader = response.headers().firstValue("X-RateLimit-Remaining");
                            if (remHeader.isPresent() && "0".equals(remHeader.get().trim())) {
                                var resetAfterHeader = response.headers().firstValue("X-RateLimit-Reset-After");
                                if (resetAfterHeader.isPresent()) {
                                    try {
                                        double sec = Double.parseDouble(resetAfterHeader.get().trim());
                                        long waitMs = (long) Math.ceil(sec * 1000.0) + 50L;
                                        rateLimitedUntil.put(webhookUrl, System.currentTimeMillis() + waitMs);
                                        pauseMs = Math.max(pauseMs, waitMs);
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    }

                    releaseWorker(webhookUrl);

                    ConcurrentLinkedQueue<WebhookItem> q = urlQueues.get(webhookUrl);
                    if (q != null && !q.isEmpty() && !scheduler.isShutdown()) {
                        scheduler.schedule(() -> triggerQueueProcessing(webhookUrl), pauseMs, TimeUnit.MILLISECONDS);
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

    private static class WebhookItem {
        final String username;
        final String avatarUrl;
        final Embed embed;
        int attempt;

        WebhookItem(String username, String avatarUrl, Embed embed, int attempt) {
            this.username = username;
            this.avatarUrl = avatarUrl;
            this.embed = embed;
            this.attempt = attempt;
        }
    }
}
