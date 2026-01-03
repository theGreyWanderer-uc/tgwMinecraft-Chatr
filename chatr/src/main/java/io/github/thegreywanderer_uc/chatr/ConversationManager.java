package io.github.thegreywanderer_uc.chatr;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages conversation memory for NPC interactions.
 * - Per-player, per-NPC conversation history
 * - Includes player friendly names for easy identification
 * - Persists to flat files for long-term memory
 */
public class ConversationManager {
    
    private final JavaPlugin plugin;
    private final Gson gson;
    private final File conversationsFolder;
    
    // Key: "playerUUID:npcName" -> conversation history
    private final Map<String, ConversationHistory> conversations = new ConcurrentHashMap<>();
    
    // Configuration
    private int maxMessagesPerConversation;
    private int maxConversationAgeDays;
    private boolean persistenceEnabled;
    
    public ConversationManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        this.conversationsFolder = new File(plugin.getDataFolder(), "conversations");
        
        reload();
        loadAllConversations();
    }
    
    /**
     * Reload configuration
     */
    public void reload() {
        var config = plugin.getConfig();
        this.maxMessagesPerConversation = config.getInt("conversation.max-messages", 20);
        this.maxConversationAgeDays = config.getInt("conversation.max-age-days", 30);
        this.persistenceEnabled = config.getBoolean("conversation.persistence-enabled", true);
    }
    
    /**
     * Get the conversation key for a player and NPC
     */
    private String getKey(UUID playerUuid, String npcName) {
        return playerUuid.toString() + ":" + npcName.toLowerCase();
    }
    
    /**
     * Add a message to a conversation
     * @param player The player (for UUID and friendly name)
     * @param npcName The NPC name
     * @param role "user" or "assistant"
     * @param content The message content
     */
    public void addMessage(Player player, String npcName, String role, String content) {
        String key = getKey(player.getUniqueId(), npcName);
        
        ConversationHistory history = conversations.computeIfAbsent(key, k -> 
            new ConversationHistory(player.getUniqueId(), player.getName(), npcName));
        
        // Update player name in case it changed
        history.playerName = player.getName();
        history.lastUpdated = Instant.now().toEpochMilli();
        
        // Add the message
        history.messages.add(new ChatMessage(role, content, System.currentTimeMillis()));
        
        // Trim if over limit
        while (history.messages.size() > maxMessagesPerConversation) {
            history.messages.removeFirst();
        }
    }
    
    /**
     * Get conversation history for building AI context
     * @return List of messages in order (oldest first)
     */
    public List<ChatMessage> getHistory(UUID playerUuid, String npcName) {
        String key = getKey(playerUuid, npcName);
        ConversationHistory history = conversations.get(key);
        
        if (history == null) {
            return Collections.emptyList();
        }
        
        return new ArrayList<>(history.messages);
    }
    
    /**
     * Get conversation history formatted for AI API
     * @return List of Map with "role" and "content" keys
     */
    public List<Map<String, String>> getHistoryForApi(UUID playerUuid, String npcName) {
        List<ChatMessage> messages = getHistory(playerUuid, npcName);
        List<Map<String, String>> result = new ArrayList<>();
        
        for (ChatMessage msg : messages) {
            result.add(Map.of("role", msg.role, "content", msg.content));
        }
        
        return result;
    }
    
    /**
     * Clear conversation history for a player and NPC
     */
    public void clearHistory(UUID playerUuid, String npcName) {
        String key = getKey(playerUuid, npcName);
        conversations.remove(key);
        
        // Delete file if persistence enabled
        if (persistenceEnabled) {
            File file = getConversationFile(playerUuid, npcName);
            if (file.exists()) {
                file.delete();
            }
        }
    }
    
    /**
     * Clear all conversations for a player
     */
    public void clearAllForPlayer(UUID playerUuid) {
        String prefix = playerUuid.toString() + ":";
        conversations.keySet().removeIf(key -> key.startsWith(prefix));
        
        // Delete player folder if persistence enabled
        if (persistenceEnabled) {
            File playerFolder = new File(conversationsFolder, playerUuid.toString());
            if (playerFolder.exists() && playerFolder.isDirectory()) {
                for (File file : playerFolder.listFiles()) {
                    file.delete();
                }
                playerFolder.delete();
            }
        }
    }
    
    /**
     * Get conversation file path
     */
    private File getConversationFile(UUID playerUuid, String npcName) {
        File playerFolder = new File(conversationsFolder, playerUuid.toString());
        return new File(playerFolder, npcName.toLowerCase() + ".json");
    }
    
    /**
     * Save a specific conversation to file
     */
    public void saveConversation(UUID playerUuid, String npcName) {
        if (!persistenceEnabled) return;
        
        String key = getKey(playerUuid, npcName);
        ConversationHistory history = conversations.get(key);
        
        if (history == null) return;
        
        File file = getConversationFile(playerUuid, npcName);
        file.getParentFile().mkdirs();
        
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            gson.toJson(history, writer);
        } catch (IOException e) {
            plugin.getLogger().warning("[ConversationManager] Failed to save conversation: " + e.getMessage());
        }
    }
    
    /**
     * Save all conversations to files
     */
    public void saveAllConversations() {
        if (!persistenceEnabled) return;
        
        int saved = 0;
        for (Map.Entry<String, ConversationHistory> entry : conversations.entrySet()) {
            ConversationHistory history = entry.getValue();
            saveConversation(history.playerUuid, history.npcName);
            saved++;
        }
        
        plugin.getLogger().info("[ConversationManager] Saved " + saved + " conversations");
    }
    
    /**
     * Load all conversations from files
     */
    public void loadAllConversations() {
        if (!persistenceEnabled) return;
        if (!conversationsFolder.exists()) {
            conversationsFolder.mkdirs();
            return;
        }
        
        int loaded = 0;
        long cutoffTime = System.currentTimeMillis() - (maxConversationAgeDays * 24L * 60L * 60L * 1000L);
        
        // Iterate through player folders
        File[] playerFolders = conversationsFolder.listFiles(File::isDirectory);
        if (playerFolders == null) return;
        
        for (File playerFolder : playerFolders) {
            File[] conversationFiles = playerFolder.listFiles((dir, name) -> name.endsWith(".json"));
            if (conversationFiles == null) continue;
            
            for (File file : conversationFiles) {
                try (Reader reader = new BufferedReader(new InputStreamReader(
                        new FileInputStream(file), StandardCharsets.UTF_8))) {
                    ConversationHistory history = gson.fromJson(reader, ConversationHistory.class);
                    
                    if (history != null) {
                        // Check if conversation is too old
                        if (history.lastUpdated < cutoffTime) {
                            file.delete();
                            continue;
                        }
                        
                        String key = getKey(history.playerUuid, history.npcName);
                        conversations.put(key, history);
                        loaded++;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("[ConversationManager] Failed to load " + file.getName() + ": " + e.getMessage());
                }
            }
        }
        
        plugin.getLogger().info("[ConversationManager] Loaded " + loaded + " conversations");
    }
    
    /**
     * Get statistics about conversations
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalConversations", conversations.size());
        
        // Count unique players
        Set<UUID> uniquePlayers = new HashSet<>();
        for (ConversationHistory history : conversations.values()) {
            uniquePlayers.add(history.playerUuid);
        }
        stats.put("uniquePlayers", uniquePlayers.size());
        
        // Count total messages
        int totalMessages = 0;
        for (ConversationHistory history : conversations.values()) {
            totalMessages += history.messages.size();
        }
        stats.put("totalMessages", totalMessages);
        
        return stats;
    }
    
    /**
     * Get all NPCs a player has talked to
     */
    public Set<String> getNpcsForPlayer(UUID playerUuid) {
        Set<String> npcs = new HashSet<>();
        String prefix = playerUuid.toString() + ":";
        
        for (String key : conversations.keySet()) {
            if (key.startsWith(prefix)) {
                npcs.add(key.substring(prefix.length()));
            }
        }
        
        return npcs;
    }
    
    /**
     * Conversation history container
     */
    public static class ConversationHistory {
        public UUID playerUuid;
        public String playerName; // Friendly name for easy identification
        public String npcName;
        public long createdAt;
        public long lastUpdated;
        public Deque<ChatMessage> messages = new LinkedList<>();
        
        public ConversationHistory() {} // For Gson
        
        public ConversationHistory(UUID playerUuid, String playerName, String npcName) {
            this.playerUuid = playerUuid;
            this.playerName = playerName;
            this.npcName = npcName;
            this.createdAt = System.currentTimeMillis();
            this.lastUpdated = this.createdAt;
        }
    }
    
    /**
     * Individual chat message
     */
    public static class ChatMessage {
        public String role; // "user" or "assistant"
        public String content;
        public long timestamp;
        
        public ChatMessage() {} // For Gson
        
        public ChatMessage(String role, String content, long timestamp) {
            this.role = role;
            this.content = content;
            this.timestamp = timestamp;
        }
    }
}
