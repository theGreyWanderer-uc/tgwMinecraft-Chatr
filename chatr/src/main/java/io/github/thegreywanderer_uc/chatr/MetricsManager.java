package io.github.thegreywanderer_uc.chatr;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks metrics and analytics for AI API usage.
 * - API calls per NPC
 * - Response times
 * - Token usage (if available)
 * - Cache hit/miss rates
 * - Player usage statistics
 */
public class MetricsManager {
    
    private final JavaPlugin plugin;
    private final Gson gson;
    private final File metricsFolder;
    
    // Real-time metrics (reset on restart)
    private final Map<String, NpcMetrics> npcMetrics = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerMetrics> playerMetrics = new ConcurrentHashMap<>();
    
    // Global counters
    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger cacheHits = new AtomicInteger(0);
    private final AtomicInteger cacheMisses = new AtomicInteger(0);
    private final AtomicLong totalResponseTimeMs = new AtomicLong(0);
    private final AtomicInteger totalErrors = new AtomicInteger(0);
    
    // Session start time
    private final long sessionStartTime = System.currentTimeMillis();
    
    // Configuration
    private boolean enabled;
    private boolean persistMetrics;
    
    public MetricsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        this.metricsFolder = new File(plugin.getDataFolder(), "metrics");
        
        reload();
        loadTodayMetrics();
    }
    
    /**
     * Reload configuration
     */
    public void reload() {
        var config = plugin.getConfig();
        this.enabled = config.getBoolean("metrics.enabled", true);
        this.persistMetrics = config.getBoolean("metrics.persist", true);
    }
    
    /**
     * Record a successful API request
     * @param npcName The NPC that was queried
     * @param playerUuid The player who made the request
     * @param playerName The player's display name
     * @param responseTimeMs How long the request took
     * @param wasCached Whether the response was from cache
     */
    public void recordRequest(String npcName, UUID playerUuid, String playerName, 
                              long responseTimeMs, boolean wasCached) {
        if (!enabled) return;
        
        totalRequests.incrementAndGet();
        
        if (wasCached) {
            cacheHits.incrementAndGet();
        } else {
            cacheMisses.incrementAndGet();
            totalResponseTimeMs.addAndGet(responseTimeMs);
        }
        
        // NPC metrics
        npcMetrics.computeIfAbsent(npcName, k -> new NpcMetrics(npcName))
                .recordRequest(responseTimeMs, wasCached);
        
        // Player metrics
        playerMetrics.computeIfAbsent(playerUuid, k -> new PlayerMetrics(playerUuid, playerName))
                .recordRequest(npcName);
    }
    
    /**
     * Record an error
     */
    public void recordError(String npcName, UUID playerUuid, String errorType) {
        if (!enabled) return;
        
        totalErrors.incrementAndGet();
        
        npcMetrics.computeIfAbsent(npcName, k -> new NpcMetrics(npcName))
                .recordError(errorType);
    }
    
    /**
     * Get summary statistics
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        
        long uptimeMs = System.currentTimeMillis() - sessionStartTime;
        summary.put("uptimeMinutes", uptimeMs / 60000);
        summary.put("totalRequests", totalRequests.get());
        summary.put("cacheHits", cacheHits.get());
        summary.put("cacheMisses", cacheMisses.get());
        
        double cacheHitRate = totalRequests.get() > 0 
                ? (double) cacheHits.get() / totalRequests.get() * 100 
                : 0;
        summary.put("cacheHitRate", String.format("%.1f%%", cacheHitRate));
        
        long avgResponseTime = cacheMisses.get() > 0 
                ? totalResponseTimeMs.get() / cacheMisses.get() 
                : 0;
        summary.put("avgResponseTimeMs", avgResponseTime);
        
        summary.put("totalErrors", totalErrors.get());
        summary.put("activeNpcs", npcMetrics.size());
        summary.put("uniquePlayers", playerMetrics.size());
        
        return summary;
    }
    
    /**
     * Get metrics for a specific NPC
     */
    public Map<String, Object> getNpcStats(String npcName) {
        NpcMetrics metrics = npcMetrics.get(npcName);
        if (metrics == null) {
            return Map.of("error", "No metrics for NPC: " + npcName);
        }
        return metrics.toMap();
    }
    
    /**
     * Get metrics for all NPCs
     */
    public Map<String, Map<String, Object>> getAllNpcStats() {
        Map<String, Map<String, Object>> all = new LinkedHashMap<>();
        for (Map.Entry<String, NpcMetrics> entry : npcMetrics.entrySet()) {
            all.put(entry.getKey(), entry.getValue().toMap());
        }
        return all;
    }
    
    /**
     * Get top NPCs by request count
     */
    public List<Map.Entry<String, Integer>> getTopNpcs(int limit) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>();
        for (Map.Entry<String, NpcMetrics> entry : npcMetrics.entrySet()) {
            sorted.add(Map.entry(entry.getKey(), entry.getValue().requests.get()));
        }
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }
    
    /**
     * Get top players by request count
     */
    public List<Map.Entry<String, Integer>> getTopPlayers(int limit) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>();
        for (Map.Entry<UUID, PlayerMetrics> entry : playerMetrics.entrySet()) {
            sorted.add(Map.entry(entry.getValue().playerName, entry.getValue().totalRequests.get()));
        }
        sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }
    
    /**
     * Save metrics to file (for daily persistence)
     */
    public void saveMetrics() {
        if (!enabled || !persistMetrics) return;
        
        if (!metricsFolder.exists()) {
            metricsFolder.mkdirs();
        }
        
        String date = LocalDate.now().toString();
        File file = new File(metricsFolder, "metrics-" + date + ".json");
        
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", date);
        data.put("savedAt", Instant.now().toString());
        data.put("summary", getSummary());
        data.put("npcMetrics", getAllNpcStats());
        
        // Player metrics (anonymized - just counts)
        Map<String, Integer> playerCounts = new LinkedHashMap<>();
        for (PlayerMetrics pm : playerMetrics.values()) {
            playerCounts.put(pm.playerName, pm.totalRequests.get());
        }
        data.put("playerCounts", playerCounts);
        
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("[Metrics] Failed to save metrics: " + e.getMessage());
        }
    }
    
    /**
     * Load today's metrics if they exist
     */
    private void loadTodayMetrics() {
        if (!enabled || !persistMetrics) return;
        if (!metricsFolder.exists()) return;
        
        String date = LocalDate.now().toString();
        File file = new File(metricsFolder, "metrics-" + date + ".json");
        
        if (!file.exists()) return;
        
        // We don't restore full state, just log that previous data exists
        plugin.getLogger().info("[Metrics] Found existing metrics for today: " + file.getName());
    }
    
    /**
     * Reset all metrics
     */
    public void reset() {
        totalRequests.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        totalResponseTimeMs.set(0);
        totalErrors.set(0);
        npcMetrics.clear();
        playerMetrics.clear();
    }
    
    /**
     * NPC-specific metrics
     */
    private static class NpcMetrics {
        final String npcName;
        final AtomicInteger requests = new AtomicInteger(0);
        final AtomicInteger cacheHits = new AtomicInteger(0);
        final AtomicLong totalResponseTimeMs = new AtomicLong(0);
        final AtomicInteger errors = new AtomicInteger(0);
        final Map<String, AtomicInteger> errorTypes = new ConcurrentHashMap<>();
        long firstRequestTime = 0;
        long lastRequestTime = 0;
        
        NpcMetrics(String npcName) {
            this.npcName = npcName;
        }
        
        void recordRequest(long responseTimeMs, boolean wasCached) {
            requests.incrementAndGet();
            if (wasCached) {
                cacheHits.incrementAndGet();
            } else {
                totalResponseTimeMs.addAndGet(responseTimeMs);
            }
            
            long now = System.currentTimeMillis();
            if (firstRequestTime == 0) firstRequestTime = now;
            lastRequestTime = now;
        }
        
        void recordError(String errorType) {
            errors.incrementAndGet();
            errorTypes.computeIfAbsent(errorType, k -> new AtomicInteger(0)).incrementAndGet();
        }
        
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("requests", requests.get());
            map.put("cacheHits", cacheHits.get());
            map.put("errors", errors.get());
            
            int nonCached = requests.get() - cacheHits.get();
            long avgTime = nonCached > 0 ? totalResponseTimeMs.get() / nonCached : 0;
            map.put("avgResponseTimeMs", avgTime);
            
            if (firstRequestTime > 0) {
                map.put("firstRequest", Instant.ofEpochMilli(firstRequestTime).toString());
                map.put("lastRequest", Instant.ofEpochMilli(lastRequestTime).toString());
            }
            
            return map;
        }
    }
    
    /**
     * Player-specific metrics
     */
    private static class PlayerMetrics {
        final UUID playerUuid;
        String playerName;
        final AtomicInteger totalRequests = new AtomicInteger(0);
        final Map<String, AtomicInteger> npcInteractions = new ConcurrentHashMap<>();
        
        PlayerMetrics(UUID playerUuid, String playerName) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
        }
        
        void recordRequest(String npcName) {
            totalRequests.incrementAndGet();
            npcInteractions.computeIfAbsent(npcName, k -> new AtomicInteger(0)).incrementAndGet();
        }
    }
}
