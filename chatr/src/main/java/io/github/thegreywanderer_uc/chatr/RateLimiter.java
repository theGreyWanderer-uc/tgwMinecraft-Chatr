package io.github.thegreywanderer_uc.chatr;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiter for AI API calls.
 * - Global base rate limit for all players
 * - Permission-based overrides (chatr.ratelimit.tier1, etc.)
 * - Bypass flag for local endpoints (LM Studio, Ollama)
 */
public class RateLimiter {
    
    private final JavaPlugin plugin;
    
    // Key: playerUUID -> last request timestamp
    private final Map<UUID, Long> lastRequestTime = new ConcurrentHashMap<>();
    
    // Key: playerUUID -> request count in current window
    private final Map<UUID, Integer> requestCounts = new ConcurrentHashMap<>();
    
    // Key: playerUUID -> window start timestamp
    private final Map<UUID, Long> windowStart = new ConcurrentHashMap<>();
    
    // Configuration
    private boolean enabled;
    private boolean bypassForLocalEndpoints;
    private int baseRateLimitPerMinute;
    private int baseCooldownSeconds;
    
    // Permission tier limits (requests per minute)
    private Map<String, Integer> tierLimits;
    
    public RateLimiter(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tierLimits = new ConcurrentHashMap<>();
        reload();
    }
    
    /**
     * Reload configuration
     */
    public void reload() {
        var config = plugin.getConfig();
        
        this.enabled = config.getBoolean("rate-limit.enabled", true);
        this.bypassForLocalEndpoints = config.getBoolean("rate-limit.bypass-local", true);
        this.baseRateLimitPerMinute = config.getInt("rate-limit.base-requests-per-minute", 10);
        this.baseCooldownSeconds = config.getInt("rate-limit.base-cooldown-seconds", 3);
        
        // Load tier limits from config
        tierLimits.clear();
        var tiersSection = config.getConfigurationSection("rate-limit.tiers");
        if (tiersSection != null) {
            for (String tier : tiersSection.getKeys(false)) {
                tierLimits.put(tier, tiersSection.getInt(tier));
            }
        }
        
        // Default tiers if not configured
        if (tierLimits.isEmpty()) {
            tierLimits.put("vip", 20);
            tierLimits.put("premium", 30);
            tierLimits.put("unlimited", Integer.MAX_VALUE);
        }
    }
    
    /**
     * Check if rate limiting is bypassed for a given server URL
     */
    public boolean isBypassedForEndpoint(String serverUrl) {
        if (!bypassForLocalEndpoints) return false;
        
        // Check for local endpoints
        return serverUrl.contains("localhost") ||
               serverUrl.contains("127.0.0.1") ||
               serverUrl.contains("0.0.0.0") ||
               serverUrl.startsWith("http://192.168.") ||
               serverUrl.startsWith("http://10.") ||
               serverUrl.startsWith("http://172.16.") ||
               serverUrl.startsWith("http://172.17.") ||
               serverUrl.startsWith("http://172.18.") ||
               serverUrl.startsWith("http://172.19.") ||
               serverUrl.startsWith("http://172.2") ||
               serverUrl.startsWith("http://172.30.") ||
               serverUrl.startsWith("http://172.31.");
    }
    
    /**
     * Check if a player can make a request
     * @param player The player
     * @param serverUrl The AI server URL (to check for local bypass)
     * @return RateLimitResult with allowed status and wait time if denied
     */
    public RateLimitResult canMakeRequest(Player player, String serverUrl) {
        if (!enabled) {
            return RateLimitResult.allowed();
        }
        
        if (isBypassedForEndpoint(serverUrl)) {
            return RateLimitResult.allowed();
        }
        
        // Check for bypass permission
        if (player.hasPermission("chatr.ratelimit.bypass")) {
            return RateLimitResult.allowed();
        }
        
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        
        // Get player's rate limit (based on permissions)
        int rateLimit = getRateLimitForPlayer(player);
        int cooldown = getCooldownForPlayer(player);
        
        // Check cooldown between requests
        Long lastRequest = lastRequestTime.get(uuid);
        if (lastRequest != null) {
            long elapsed = now - lastRequest;
            long cooldownMs = cooldown * 1000L;
            if (elapsed < cooldownMs) {
                int waitSeconds = (int) Math.ceil((cooldownMs - elapsed) / 1000.0);
                return RateLimitResult.denied(waitSeconds, "cooldown");
            }
        }
        
        // Check requests per minute
        Long windowStartTime = windowStart.get(uuid);
        Integer count = requestCounts.get(uuid);
        
        // Reset window if it's been more than a minute
        if (windowStartTime == null || now - windowStartTime > 60000) {
            windowStart.put(uuid, now);
            requestCounts.put(uuid, 0);
            count = 0;
        }
        
        if (count >= rateLimit) {
            long windowEnd = windowStart.get(uuid) + 60000;
            int waitSeconds = (int) Math.ceil((windowEnd - now) / 1000.0);
            return RateLimitResult.denied(waitSeconds, "rate-limit");
        }
        
        return RateLimitResult.allowed();
    }
    
    /**
     * Record that a request was made
     */
    public void recordRequest(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        
        lastRequestTime.put(uuid, now);
        requestCounts.merge(uuid, 1, Integer::sum);
    }
    
    /**
     * Get the rate limit for a player based on permissions
     */
    private int getRateLimitForPlayer(Player player) {
        // Check tiers from highest to lowest
        for (Map.Entry<String, Integer> entry : tierLimits.entrySet()) {
            if (player.hasPermission("chatr.ratelimit." + entry.getKey())) {
                return entry.getValue();
            }
        }
        return baseRateLimitPerMinute;
    }
    
    /**
     * Get cooldown for a player (could be tier-based in future)
     */
    private int getCooldownForPlayer(Player player) {
        // For now, higher tiers have no cooldown
        for (String tier : tierLimits.keySet()) {
            if (player.hasPermission("chatr.ratelimit." + tier)) {
                return 0; // No cooldown for tier players
            }
        }
        return baseCooldownSeconds;
    }
    
    /**
     * Get remaining requests for a player in current window
     */
    public int getRemainingRequests(Player player) {
        UUID uuid = player.getUniqueId();
        
        int rateLimit = getRateLimitForPlayer(player);
        Integer count = requestCounts.getOrDefault(uuid, 0);
        
        // Check if window expired
        Long windowStartTime = windowStart.get(uuid);
        if (windowStartTime == null || System.currentTimeMillis() - windowStartTime > 60000) {
            return rateLimit;
        }
        
        return Math.max(0, rateLimit - count);
    }
    
    /**
     * Clear rate limit data for a player (e.g., when they disconnect)
     */
    public void clearPlayer(UUID uuid) {
        lastRequestTime.remove(uuid);
        requestCounts.remove(uuid);
        windowStart.remove(uuid);
    }
    
    /**
     * Result of a rate limit check
     */
    public static class RateLimitResult {
        public final boolean allowed;
        public final int waitSeconds;
        public final String reason; // "cooldown" or "rate-limit"
        
        private RateLimitResult(boolean allowed, int waitSeconds, String reason) {
            this.allowed = allowed;
            this.waitSeconds = waitSeconds;
            this.reason = reason;
        }
        
        public static RateLimitResult allowed() {
            return new RateLimitResult(true, 0, null);
        }
        
        public static RateLimitResult denied(int waitSeconds, String reason) {
            return new RateLimitResult(false, waitSeconds, reason);
        }
    }
}
