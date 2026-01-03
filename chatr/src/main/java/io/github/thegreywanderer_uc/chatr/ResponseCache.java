package io.github.thegreywanderer_uc.chatr;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple response cache with TTL to reduce duplicate AI API calls.
 * Cache key is hash of (npcName + userMessage).
 */
public class ResponseCache {
    
    private final JavaPlugin plugin;
    
    // Cache: key -> CachedResponse
    private final Map<String, CachedResponse> cache = new ConcurrentHashMap<>();
    
    // Configuration
    private boolean enabled;
    private int ttlSeconds;
    private int maxSize;
    
    public ResponseCache(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }
    
    /**
     * Reload configuration
     */
    public void reload() {
        var config = plugin.getConfig();
        this.enabled = config.getBoolean("cache.enabled", true);
        this.ttlSeconds = config.getInt("cache.ttl-seconds", 300); // 5 minutes default
        this.maxSize = config.getInt("cache.max-size", 100);
        
        // Clear cache on reload
        cache.clear();
    }
    
    /**
     * Generate cache key from NPC name and message
     */
    private String generateKey(String npcName, String userMessage) {
        // Normalize: lowercase NPC name, trim and lowercase message
        String normalizedNpc = npcName.toLowerCase();
        String normalizedMsg = userMessage.trim().toLowerCase();
        return normalizedNpc + ":" + normalizedMsg.hashCode();
    }
    
    /**
     * Get cached response if available and not expired
     * @return The cached response, or null if not found/expired
     */
    public String get(String npcName, String userMessage) {
        if (!enabled) return null;
        
        String key = generateKey(npcName, userMessage);
        CachedResponse cached = cache.get(key);
        
        if (cached == null) {
            return null;
        }
        
        // Check if expired
        if (System.currentTimeMillis() - cached.timestamp > ttlSeconds * 1000L) {
            cache.remove(key);
            return null;
        }
        
        cached.hits++;
        return cached.response;
    }
    
    /**
     * Store a response in the cache
     */
    public void put(String npcName, String userMessage, String response) {
        if (!enabled) return;
        if (response == null || response.isEmpty()) return;
        
        // Evict old entries if over size limit
        if (cache.size() >= maxSize) {
            evictOldest();
        }
        
        String key = generateKey(npcName, userMessage);
        cache.put(key, new CachedResponse(response));
    }
    
    /**
     * Evict the oldest entry
     */
    private void evictOldest() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        
        for (Map.Entry<String, CachedResponse> entry : cache.entrySet()) {
            if (entry.getValue().timestamp < oldestTime) {
                oldestTime = entry.getValue().timestamp;
                oldestKey = entry.getKey();
            }
        }
        
        if (oldestKey != null) {
            cache.remove(oldestKey);
        }
    }
    
    /**
     * Clear all cached responses
     */
    public void clear() {
        cache.clear();
    }
    
    /**
     * Clear cached responses for a specific NPC
     */
    public void clearForNpc(String npcName) {
        String prefix = npcName.toLowerCase() + ":";
        cache.keySet().removeIf(key -> key.startsWith(prefix));
    }
    
    /**
     * Get cache statistics
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("enabled", enabled);
        stats.put("size", cache.size());
        stats.put("maxSize", maxSize);
        stats.put("ttlSeconds", ttlSeconds);
        
        int totalHits = 0;
        for (CachedResponse cached : cache.values()) {
            totalHits += cached.hits;
        }
        stats.put("totalHits", totalHits);
        
        return stats;
    }
    
    /**
     * Check if caching is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Cached response container
     */
    private static class CachedResponse {
        final String response;
        final long timestamp;
        int hits;
        
        CachedResponse(String response) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
            this.hits = 0;
        }
    }
}
