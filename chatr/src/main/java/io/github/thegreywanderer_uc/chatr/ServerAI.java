package io.github.thegreywanderer_uc.chatr;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Server-wide AI assistant that:
 * - Appears in the player list
 * - Responds to chat messages mentioning its name
 * - Periodically scans chat logs to help, mediate, or join conversations
 */
public class ServerAI implements Listener {
    
    private final JavaPlugin plugin;
    private final Gson gson = new Gson();
    
    // Configuration
    private boolean enabled;
    private boolean debugMode;
    private String name;
    // DISABLED: Skin support for tab list (Minecraft doesn't support custom skins for fake players)
    // private String skinPlayerName; // Player name whose skin to use
    // private String skinTexture;    // Cached skin texture data
    // private String skinSignature;  // Cached skin signature
    private String serverUrl;
    private String model;
    private String systemPrompt;
    private int chatScanIntervalSeconds;
    private double conversationJoinChance;
    private int maxChatHistorySize;
    private int maxTokens;
    
    // Display configuration
    private boolean showPrefix;
    private String prefixText;
    private String prefixColor;
    private String nameColor;
    
    // RAG configuration
    private boolean ragEnabled;
    private String ragDataPath;
    private String ragEmbeddingModel;
    private String ragLmStudioUrl;
    private int ragMaxContextLength;
    private int ragTopK;
    private int ragMaxHops;
    private float ragSimilarityThreshold;
    private int ragMaxTotalDocs;
    private int ragMaxContextDocs;
    private int ragSnippetWindow;
    private int ragFallbackPrefixLen;
    private RAGSystem ragSystem;
    
    // State
    private final Deque<ChatMessage> recentChat = new ConcurrentLinkedDeque<>();
    private BukkitTask scanTask;
    private File logFolder;
    private UUID serverAiUuid;
    
    // Track which messages we've already processed in periodic scan
    private long lastScanTimestamp = 0;
    
    // Per-player conversation memory for contextual responses
    private final Map<UUID, Deque<ConversationMessage>> playerConversations = new java.util.concurrent.ConcurrentHashMap<>();
    private int maxConversationMemory = 10; // Messages per player
    
    public ServerAI(JavaPlugin plugin) {
        this.plugin = plugin;
        this.serverAiUuid = UUID.nameUUIDFromBytes("ServerAI".getBytes());
        reload();
    }
    
    /**
     * Reload configuration from plugin config
     */
    public void reload() {
        var config = plugin.getConfig();
        
        this.enabled = config.getBoolean("server-ai.enabled", false);
        this.debugMode = config.getBoolean("debug-mode", false);
        this.name = config.getString("server-ai.name", "Heimdall");
        
        // Load skin configuration
        String newSkinPlayerName = config.getString("server-ai.skin", "").trim();
        // DISABLED: Skin loading for tab list (Minecraft doesn't support custom skins for fake players)
        /*
        if (!newSkinPlayerName.equals(this.skinPlayerName)) {
            // Skin changed, reload skin data
            this.skinPlayerName = newSkinPlayerName;
            this.skinTexture = null;
            this.skinSignature = null;
            if (!newSkinPlayerName.isEmpty()) {
                loadSkinData(newSkinPlayerName);
            }
        }
        */
        
        this.chatScanIntervalSeconds = config.getInt("server-ai.chat-scan-interval-seconds", 60);
        this.conversationJoinChance = config.getDouble("server-ai.conversation-join-chance", 0.1);
        this.maxChatHistorySize = config.getInt("server-ai.max-chat-history", 50);
        this.maxTokens = config.getInt("server-ai.max-tokens", config.getInt("ai.max-tokens", 1000));
        this.maxConversationMemory = config.getInt("server-ai.max-conversation-memory", 10);
        
        // Display configuration
        this.showPrefix = config.getBoolean("server-ai.display.show-prefix", true);
        this.prefixText = config.getString("server-ai.display.prefix-text", "AI");
        this.prefixColor = config.getString("server-ai.display.prefix-color", "&6");
        this.nameColor = config.getString("server-ai.display.name-color", "&a");
        
        // RAG configuration
        this.ragEnabled = config.getBoolean("server-ai.rag.enabled", false);
        this.ragDataPath = config.getString("server-ai.rag.dataPath", "ragData");
        this.ragEmbeddingModel = config.getString("server-ai.rag.embeddingModel", "nomic-ai/nomic-embed-text-v1.5");
        this.ragLmStudioUrl = config.getString("server-ai.rag.lmStudioUrl", "http://localhost:1234");
        this.ragMaxContextLength = config.getInt("server-ai.rag.maxContextLength", 2000);
        this.ragTopK = config.getInt("server-ai.rag.topK", 5);
        this.ragMaxHops = config.getInt("server-ai.rag.maxHops", 2);
        this.ragSimilarityThreshold = (float) config.getDouble("server-ai.rag.similarityThreshold", 0.5);
        this.ragMaxTotalDocs = config.getInt("server-ai.rag.maxTotalDocs", 15);
        this.ragMaxContextDocs = config.getInt("server-ai.rag.maxContextDocs", 5);
        this.ragSnippetWindow = config.getInt("server-ai.rag.snippetWindow", 600);
        this.ragFallbackPrefixLen = config.getInt("server-ai.rag.fallbackPrefixLen", 800);
        
        // Server AI has its own server-url and model settings (with defaults in config)
        // This ensures ServerAI works independently of global AI settings
        this.serverUrl = config.getString("server-ai.server-url", "http://localhost:1234");
        this.model = config.getString("server-ai.model", "local-model");
        
        // Build system prompt from config or file
        this.systemPrompt = loadSystemPrompt();
        
        // Setup log folder
        this.logFolder = new File(plugin.getDataFolder(), "serverAI");
        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }
        
        // Initialize RAG system if enabled
        if (ragEnabled) {
            try {
                File ragDataDir = new File(plugin.getDataFolder(), ragDataPath);
                this.ragSystem = new RAGSystem(ragDataDir, ragTopK, ragMaxHops, ragMaxContextLength, ragLmStudioUrl, 
                                           ragSimilarityThreshold, ragMaxTotalDocs, ragMaxContextDocs, ragSnippetWindow, ragFallbackPrefixLen);
                plugin.getLogger().info("[ServerAI] RAG system loaded successfully from: " + ragDataDir.getPath());
            } catch (Exception e) {
                plugin.getLogger().warning("[ServerAI] Failed to load RAG system: " + e.getMessage());
                plugin.getLogger().warning("[ServerAI] RAG will be disabled. Check that ragData files exist.");
                this.ragEnabled = false;
            }
        }
        
        plugin.getLogger().info("[ServerAI] Configuration loaded - Enabled: " + enabled + ", Name: " + name + ", RAG: " + ragEnabled);
        if (debugMode) {
            plugin.getLogger().info("[ServerAI] Debug: Using server-url: " + serverUrl);
            plugin.getLogger().info("[ServerAI] Debug: Using model: " + model);
        }
    }
    
    /**
     * Load system prompt from config or file
     */
    private String loadSystemPrompt() {
        var config = plugin.getConfig();
        String source = config.getString("server-ai.system-prompt-source", "inline");
        
        if ("file".equalsIgnoreCase(source)) {
            // Load from file
            File promptFile = new File(logFolder.getParentFile(), "serverAI/system-prompt.txt");
            if (!promptFile.exists()) {
                // Create default file
                try {
                    promptFile.getParentFile().mkdirs();
                    Files.writeString(promptFile.toPath(), getDefaultSystemPrompt());
                    plugin.getLogger().info("[ServerAI] Created default system-prompt.txt");
                } catch (IOException e) {
                    plugin.getLogger().warning("[ServerAI] Failed to create system-prompt.txt: " + e.getMessage());
                    return getDefaultSystemPrompt();
                }
            }
            
            try {
                String content = Files.readString(promptFile.toPath());
                return content.replace("{name}", name);
            } catch (IOException e) {
                plugin.getLogger().warning("[ServerAI] Failed to read system-prompt.txt: " + e.getMessage());
                return getDefaultSystemPrompt();
            }
        } else {
            // Load from config inline
            String prompt = config.getString("server-ai.system-prompt", null);
            if (prompt != null && !prompt.isEmpty()) {
                return prompt.replace("{name}", name);
            }
            return getDefaultSystemPrompt();
        }
    }
    
    /**
     * Build contextual system prompt with variables substituted
     */
    private String buildContextualSystemPrompt(String rawPrompt, Player player) {
        String prompt = rawPrompt;
        
        // Server AI name
        prompt = prompt.replace("{name}", name);
        
        // Get the main world for server time
        org.bukkit.World mainWorld = Bukkit.getWorlds().get(0);
        if (mainWorld != null) {
            long serverTimeTicks = mainWorld.getTime();
            
            String serverTime = formatTime(serverTimeTicks);
            
            prompt = prompt.replace("{server_time}", serverTime);
            prompt = prompt.replace("{server_time_ticks}", String.valueOf(serverTimeTicks));
            
            // Server weather
            String serverWeather = determineWeather(mainWorld);
            prompt = prompt.replace("{server_weather}", serverWeather);
        }
        
        // Player context
        if (player != null) {
            prompt = prompt.replace("{player_name}", player.getName());
            
            // Player's personal time (ptime)
            // Note: Minecraft doesn't expose player-specific time directly in the API
            // We can only get the world time, but players can have individual time offsets
            // For now, we'll use the world time as an approximation
            long playerTimeTicks = mainWorld != null ? mainWorld.getTime() : 0;
            String playerTime = formatTime(playerTimeTicks);
            
            prompt = prompt.replace("{player_time}", playerTime);
            prompt = prompt.replace("{player_time_ticks}", String.valueOf(playerTimeTicks));
            
            // Player's weather (same as server weather since Minecraft doesn't expose player-specific weather)
            String playerWeather = determineWeather(mainWorld);
            prompt = prompt.replace("{player_weather}", playerWeather);
            
            // Player's biome
            String playerBiome = getBiome(player.getLocation());
            prompt = prompt.replace("{player_biome}", playerBiome);
            
            // Player's position
            Location playerLocation = player.getLocation();
            prompt = prompt.replace("{player_x}", formatCoordinate(playerLocation.getX()));
            prompt = prompt.replace("{player_y}", formatCoordinate(playerLocation.getY()));
            prompt = prompt.replace("{player_z}", formatCoordinate(playerLocation.getZ()));
        }
        
        return prompt;
    }

    // Extracted methods for testing

    /**
     * Format Minecraft time ticks to 24-hour time string
     */
    static String formatTime(long ticks) {
        long hours = ((ticks + 6000) % 24000) / 1000;
        long minutes = ((ticks + 6000) % 1000) * 60 / 1000;
        return String.format("%02d:%02d", hours, minutes);
    }

    /**
     * Determine weather from world state
     */
    static String determineWeather(org.bukkit.World world) {
        if (world.isThundering()) {
            return "thunderstorm";
        } else if (world.hasStorm()) {
            return "rain";
        } else {
            return "clear";
        }
    }

    /**
     * Get biome name from location
     */
    static String getBiome(Location location) {
        return location.getBlock().getBiome().getKey().getKey().toLowerCase().replace("_", " ");
    }

    /**
     * Format coordinate to 1 decimal place
     */
    static String formatCoordinate(double coord) {
        return String.format("%.1f", coord);
    }

    /**
     * Get the default system prompt
     */
    private String getDefaultSystemPrompt() {
        return """
            You are %s, the Server AI assistant for this Minecraft server.
            
            AVAILABLE CONTEXT VARIABLES:
            - {server_time}: Current server time in 24-hour format (e.g., "14:30")
            - {server_weather}: Current server weather (clear/rain/thunderstorm)
            - {player_time}: Player's personal time (ptime) in 24-hour format
            - {player_weather}: Player's weather (clear/rain/thunderstorm)
            - {player_name}: The player's name
            - {player_biome}: The biome the player is currently in
            - {player_x}: The player's X coordinate
            - {player_y}: The player's Y coordinate
            - {player_z}: The player's Z coordinate
            
            RESPONSE RULES:
            - Respond ONLY with your message. No analysis, no thinking, no prefixes.
            - Do NOT explain what you're doing or why.
            - Do NOT start with "Based on..." or "I see that..." or similar.
            - Just respond naturally as if you're chatting.
            - Use plain text only. No markdown formatting (no *, _, `, #).
            - Keep responses concise: 1-3 sentences for simple questions, up to 4-5 for complex help.
            - Provide complete answers - don't ask follow-up questions unless truly necessary.
            
            Your personality:
            - Wise and calm, like an all-seeing guardian named after the Norse god
            - Helpful and knowledgeable about Minecraft
            - Friendly but professional
            
            Your responsibilities:
            - Answer questions directly and helpfully
            - If players need help with Minecraft (crafting, building, etc.), provide the full answer
            - Mediate conflicts calmly if players argue
            - Be a positive presence in the community
            """.formatted(name);
    }
    
    /**
     * Start the Server AI (register listeners, start tasks, add to tab list)
     */
    public void start() {
        if (!enabled) {
            plugin.getLogger().info("[ServerAI] Server AI is disabled in config.");
            return;
        }
        
        // Register event listeners
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        // Add to tab list for all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            addToTabList(player);
        }
        
        // Start periodic chat scan task
        startChatScanTask();
        
        plugin.getLogger().info("[ServerAI] " + name + " is now online and watching over the server!");
    }
    
    /**
     * Stop the Server AI
     */
    public void stop() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
        
        // Remove from tab list for all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeFromTabList(player);
        }
        
        // Unregister listeners
        AsyncPlayerChatEvent.getHandlerList().unregister(this);
        PlayerJoinEvent.getHandlerList().unregister(this);
        PlayerQuitEvent.getHandlerList().unregister(this);
        
        plugin.getLogger().info("[ServerAI] " + name + " has gone offline.");
    }
    
    /**
     * Add Server AI to a player's tab list using the same approach as FancyNpcs
     */
    private void addToTabList(Player player) {
        try {
            Object connection = getPlayerConnection(player);
            if (connection == null) {
                if (debugMode) plugin.getLogger().warning("[ServerAI] Debug: Could not get player connection");
                return;
            }
            
            if (debugMode) plugin.getLogger().info("[ServerAI] Debug: Starting tab list addition for " + player.getName());
            
            // Create GameProfile for the Server AI
            // IMPORTANT: GameProfile name must be ≤16 characters, NO color codes!
            // Color codes go in the displayName Component only
            String profileName = name.length() > 16 ? name.substring(0, 16) : name;
            if (debugMode) plugin.getLogger().info("[ServerAI] Debug: GameProfile name: '" + profileName + "' (length: " + profileName.length() + ")");
            
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Object gameProfile;
            
            // DISABLED: Skin support for tab list (Minecraft doesn't support custom skins for fake players)
            /*
            // Create GameProfile with skin properties if skin data is available
            if (skinTexture != null && !skinTexture.isEmpty()) {
                try {
                    // Warn if signature is null - Minecraft clients may not display unsigned skins
                    // Note: Many default skins (Steve, Alex, etc.) don't have signatures and work fine
                    if (skinSignature == null || skinSignature.isEmpty()) {
                        if (debugMode) {
                            plugin.getLogger().info("[ServerAI] Debug: Skin signature is null/empty for '" + skinPlayerName + "' - using unsigned skin (normal for default skins)");
                        }
                    }
                    
                    // Create PropertyMap with the skin data
                    Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
                    java.lang.reflect.Constructor<?> propConstructor = propertyClass.getConstructor(String.class, String.class, String.class);
                    Object skinProperty = propConstructor.newInstance("textures", skinTexture, skinSignature);
                    
                    // Create mutable Multimap for properties
                    Class<?> multimapClass = Class.forName("com.google.common.collect.Multimap");
                    Class<?> hashMultimapClass = Class.forName("com.google.common.collect.HashMultimap");
                    java.lang.reflect.Method createMethod = hashMultimapClass.getMethod("create");
                    Object propertyMultimap = createMethod.invoke(null);
                    
                    // Add skin property
                    java.lang.reflect.Method putMethod = propertyMultimap.getClass().getMethod("put", Object.class, Object.class);
                    putMethod.invoke(propertyMultimap, "textures", skinProperty);
                    
                    // Create PropertyMap from Multimap
                    Class<?> propertyMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap");
                    java.lang.reflect.Constructor<?> mapConstructor = propertyMapClass.getConstructor(multimapClass);
                    Object propertyMap = mapConstructor.newInstance(propertyMultimap);
                    
                    // Create GameProfile with PropertyMap (3-param constructor)
                    java.lang.reflect.Constructor<?> profileConstructor = gameProfileClass.getConstructor(UUID.class, String.class, propertyMapClass);
                    gameProfile = profileConstructor.newInstance(serverAiUuid, profileName, propertyMap);
                    
                    if (debugMode) plugin.getLogger().info("[ServerAI] Debug: Created GameProfile with skin properties for '" + skinPlayerName + "'");
                    
                } catch (Exception e) {
                    plugin.getLogger().warning("[ServerAI] Failed to create GameProfile with skin properties: " + e.getMessage() + ". Using default GameProfile.");
                    // Fallback to basic GameProfile
                    gameProfile = gameProfileClass.getConstructor(UUID.class, String.class)
                            .newInstance(serverAiUuid, profileName);
                }
            } else {
                // No skin data, use basic GameProfile
                gameProfile = gameProfileClass.getConstructor(UUID.class, String.class)
                        .newInstance(serverAiUuid, profileName);
                
                if (debugMode) plugin.getLogger().info("[ServerAI] Debug: Created GameProfile without skin properties (using default skin)");
            }
            */
            
            // Always use basic GameProfile (no custom skins for fake players in tab list)
            gameProfile = gameProfileClass.getConstructor(UUID.class, String.class)
                    .newInstance(serverAiUuid, profileName);
            
            if (debugMode) plugin.getLogger().info("[ServerAI] Debug: Created GameProfile without skin properties (using default Steve/Alex skin)");
            
            if (debugMode) plugin.getLogger().info("[ServerAI] Debug: Created GameProfile with UUID: " + serverAiUuid);
            
            // Get GameType.SURVIVAL
            Class<?> gameTypeClass = Class.forName("net.minecraft.world.level.GameType");
            Object survivalMode = gameTypeClass.getMethod("byId", int.class).invoke(null, 0);
            if (debugMode) plugin.getLogger().info("[ServerAI] Debug: GameType: " + survivalMode);
            
            // Create Component for display name with colors (like other plugins do)
            String displayNameText = ChatColor.GOLD + "[AI] " + ChatColor.AQUA + name;
            if (debugMode) plugin.getLogger().info("[ServerAI] Debug: Display name text: '" + displayNameText + "' (length: " + displayNameText.length() + ")");
            
            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            java.lang.reflect.Method literalMethod = componentClass.getMethod("literal", String.class);
            Object displayName = literalMethod.invoke(null, displayNameText);
            
            // Create PlayerInfoUpdate packet classes
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            Class<?> actionClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
            Class<?> entryClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
            Class<?> remoteChatSessionDataClass = Class.forName("net.minecraft.network.chat.RemoteChatSession$Data");
            
            Object addAction = actionClass.getEnumConstants()[0]; // ADD_PLAYER
            if (debugMode) plugin.getLogger().info("[ServerAI] Debug: Action: ADD_PLAYER (index 0)");
            
            // Create Entry using explicit constructor with exact parameter types
            // Entry record: (UUID, GameProfile, boolean listed, int latency, GameType, Component displayName, boolean showHat, int listOrder, RemoteChatSession.Data)
            java.lang.reflect.Constructor<?> entryConstructor = entryClass.getConstructor(
                UUID.class,
                gameProfileClass,
                boolean.class,
                int.class,
                gameTypeClass,
                componentClass,
                boolean.class,
                int.class,
                remoteChatSessionDataClass
            );
            
            if (debugMode) plugin.getLogger().info("[ServerAI] Debug: Entry constructor found, creating entry...");
            
            Object entry = entryConstructor.newInstance(
                serverAiUuid,
                gameProfile,
                true,         // listed
                0,            // latency
                survivalMode, // gameMode
                displayName,  // displayName
                true,         // showHat
                -1,           // listOrder (try -1 for fake players)
                null          // chatSession
            );
            
            if (debugMode) {
                plugin.getLogger().info("[ServerAI] Debug: Created Entry successfully");
                // Try to inspect the entry fields
                try {
                    java.lang.reflect.Field uuidField = entryClass.getDeclaredField("profileId");
                    uuidField.setAccessible(true);
                    Object entryUuid = uuidField.get(entry);
                    plugin.getLogger().info("[ServerAI] Debug: Entry UUID: " + entryUuid);
                    
                    java.lang.reflect.Field profileField = entryClass.getDeclaredField("profile");
                    profileField.setAccessible(true);
                    Object entryProfile = profileField.get(entry);
                    plugin.getLogger().info("[ServerAI] Debug: Entry profile: " + entryProfile);
                    
                    java.lang.reflect.Field listedField = entryClass.getDeclaredField("listed");
                    listedField.setAccessible(true);
                    boolean entryListed = (boolean) listedField.get(entry);
                    plugin.getLogger().info("[ServerAI] Debug: Entry listed: " + entryListed);
                    
                } catch (Exception e) {
                    plugin.getLogger().info("[ServerAI] Debug: Could not inspect entry fields: " + e.getMessage());
                }
            }
            
            // Create actions EnumSet with multiple actions like FancyNpcs does
            @SuppressWarnings({"unchecked", "rawtypes"})
            EnumSet actions = EnumSet.noneOf((Class) actionClass);
            actions.add(addAction);  // ADD_PLAYER
            // Try different indices to find UPDATE_DISPLAY_NAME
            for (int i = 0; i < actionClass.getEnumConstants().length; i++) {
                Object enumVal = actionClass.getEnumConstants()[i];
                if (enumVal.toString().contains("DISPLAY_NAME")) {
                    actions.add(enumVal);
                    if (debugMode) plugin.getLogger().info("[ServerAI] Debug: Found UPDATE_DISPLAY_NAME at index " + i);
                    break;
                }
            }
            actions.add(actionClass.getEnumConstants()[3]);  // UPDATE_LISTED (index 3)
            if (debugMode) plugin.getLogger().info("[ServerAI] Debug: Created actions EnumSet: " + actions);
            
            // CRITICAL: Use (EnumSet, List<Entry>) constructor, NOT (EnumSet, Collection<ServerPlayer>)!
            // The single-entry constructor maps ServerPlayer->Entry internally, causing ClassCastException
            java.lang.reflect.Constructor<?> packetConstructor = packetClass.getConstructor(EnumSet.class, List.class);
            List<Object> entries = new ArrayList<>();
            entries.add(entry);
            Object packet = packetConstructor.newInstance(actions, entries);
            
            if (debugMode) {
                plugin.getLogger().info("[ServerAI] Debug: Created packet successfully");
                // Try to inspect packet fields
                try {
                    java.lang.reflect.Field actionsField = packetClass.getDeclaredField("actions");
                    actionsField.setAccessible(true);
                    Object packetActions = actionsField.get(packet);
                    plugin.getLogger().info("[ServerAI] Debug: Packet actions: " + packetActions);
                    
                    java.lang.reflect.Field entriesField = packetClass.getDeclaredField("entries");
                    entriesField.setAccessible(true);
                    Object packetEntries = entriesField.get(packet);
                    plugin.getLogger().info("[ServerAI] Debug: Packet entries: " + packetEntries);
                    
                } catch (Exception e) {
                    plugin.getLogger().info("[ServerAI] Debug: Could not inspect packet fields: " + e.getMessage());
                }
            }
            
            if (debugMode) plugin.getLogger().info("[ServerAI] Debug: Sending packet to player " + player.getName());
            sendPacket(connection, packet);
            if (debugMode) plugin.getLogger().info("[ServerAI] Debug: Successfully added to tab list for " + player.getName());
            
        } catch (Exception e) {
            plugin.getLogger().warning("[ServerAI] Failed to add to tab list: " + e.getMessage());
            if (debugMode) e.printStackTrace();
        }
    }
    
    /**
     * Check if ServerAI is currently visible in player's tab list
     */
    public boolean isInTabList(Player player) {
        try {
            // This is a best-effort check - we can't directly query the client's tab list
            // But we can check if the player has received the packet recently
            if (debugMode) plugin.getLogger().info("[ServerAI] Debug: Checking if " + name + " is in " + player.getName() + "'s tab list");
            
            // For now, just return true if the player is online and ServerAI is enabled
            // In a real implementation, we'd need to track packet send status
            return player.isOnline() && enabled;
            
        } catch (Exception e) {
            if (debugMode) plugin.getLogger().warning("[ServerAI] Debug: Error checking tab list status: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Force refresh ServerAI in player's tab list (for debugging)
     */
    public void refreshTabList(Player player) {
        if (!enabled) return;
        
        if (debugMode) plugin.getLogger().info("[ServerAI] Debug: Force refreshing tab list for " + player.getName());
        
        // Remove first, then add back after a short delay
        removeFromTabList(player);
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            addToTabList(player);
        }, 10L); // 0.5 second delay
    }
    private void removeFromTabList(Player player) {
        try {
            Object connection = getPlayerConnection(player);
            if (connection == null) {
                if (debugMode) plugin.getLogger().warning("[ServerAI] Debug: Could not get player connection for removal");
                return;
            }
            
            if (debugMode) plugin.getLogger().info("[ServerAI] Debug: Removing from tab list for " + player.getName());
            
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
            var constructor = packetClass.getConstructor(List.class);
            Object packet = constructor.newInstance(Collections.singletonList(serverAiUuid));
            
            if (debugMode) plugin.getLogger().info("[ServerAI] Debug: Created remove packet, sending...");
            sendPacket(connection, packet);
            if (debugMode) plugin.getLogger().info("[ServerAI] Debug: Successfully removed from tab list for " + player.getName());
            
        } catch (Exception e) {
            plugin.getLogger().warning("[ServerAI] Failed to remove from tab list: " + e.getMessage());
            if (debugMode) e.printStackTrace();
        }
    }
    
    private Object getPlayerConnection(Player player) throws Exception {
        Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
        java.lang.reflect.Field connectionField = craftPlayer.getClass().getField("connection");
        return connectionField.get(craftPlayer);
    }
    
    private void sendPacket(Object connection, Object packet) throws Exception {
        java.lang.reflect.Method sendMethod = connection.getClass().getMethod("send", 
                Class.forName("net.minecraft.network.protocol.Packet"));
        sendMethod.invoke(connection, packet);
    }
    
    /**
     * Handle player joining - add Server AI to their tab list
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        
        // Add to new player's tab list immediately (no delay)
        addToTabList(event.getPlayer());
    }
    
    /**
     * Handle player leaving
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Nothing special needed - tab list clears automatically
    }
    
    /**
     * Listen to all chat messages
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!enabled) return;
        
        String message = event.getMessage();
        String playerName = event.getPlayer().getName();
        
        // Store message in recent chat history
        ChatMessage chatMsg = new ChatMessage(playerName, message, System.currentTimeMillis());
        recentChat.addLast(chatMsg);
        
        // Trim history if too large
        while (recentChat.size() > maxChatHistorySize) {
            recentChat.pollFirst();
        }
        
        // Check if message mentions the Server AI's name (case-insensitive)
        if (containsName(message)) {
            // Respond immediately on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                respondToMention(event.getPlayer(), message);
            });
        }
    }
    
    /**
     * Check if a message contains the Server AI's name
     */
    private boolean containsName(String message) {
        return message.toLowerCase().contains(name.toLowerCase());
    }
    
    /**
     * Respond to a direct mention
     */
    private void respondToMention(Player player, String message) {
        if (debugMode) plugin.getLogger().info("[ServerAI] " + name + " was mentioned by " + player.getName() + ": " + message);
        
        // Log the mention
        logMessage(player.getName(), message, "mention");
        
        // Add to player's conversation memory
        addToPlayerConversation(player.getUniqueId(), "user", player.getName() + ": " + message);
        
        // Get RAG context if enabled
        String ragContext = "";
        if (ragEnabled && ragSystem != null) {
            try {
                ragContext = ragSystem.retrieveContext(message);
                if (debugMode && !ragContext.isEmpty()) {
                    plugin.getLogger().info("[ServerAI] RAG context retrieved: " + ragContext.substring(0, Math.min(100, ragContext.length())) + "...");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[ServerAI] RAG context retrieval failed: " + e.getMessage());
                // Continue without RAG context
            }
        }
        
        // Build messages list with conversation history
        List<Map<String, Object>> messages = buildMessagesWithHistory(player.getUniqueId(), message, player.getName(), ragContext);
        
        // Make AI call with conversation history
        makeAiCallWithHistory(messages)
                .thenAccept(response -> {
                    if (response != null && !response.isEmpty()) {
                        // Add response to conversation memory
                        addToPlayerConversation(player.getUniqueId(), "assistant", response);
                        
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            broadcastResponse(response);
                            logMessage(name, response, "response");
                        });
                    }
                })
                .exceptionally(e -> {
                    plugin.getLogger().warning("[ServerAI] Failed to generate response: " + e.getMessage());
                    return null;
                });
    }
    
    /**
     * Broadcast a message from the Server AI to all players
     */
    private void broadcastResponse(String message) {
        String formatted = formatResponse(message);
        
        String prefix = "";
        if (showPrefix) {
            prefix = ChatColor.translateAlternateColorCodes('&', prefixColor) + "[" + prefixText + "] " + 
                    ChatColor.translateAlternateColorCodes('&', nameColor) + name + 
                    ChatColor.RESET + " ";
        } else {
            prefix = ChatColor.translateAlternateColorCodes('&', nameColor) + name + 
                    ChatColor.RESET + ": ";
        }
        
        Bukkit.broadcastMessage(prefix + formatted);
    }
    
    /**
     * Format the AI response with colors
     */
    private String formatResponse(String response) {
        // Start with aqua for base text
        String formatted = "&b" + response;
        
        // Highlight numbers
        formatted = formatted.replaceAll("(\\d+(?:\\.\\d+)?)", "&a$1&b");
        
        // Highlight player names (online players)
        for (Player p : Bukkit.getOnlinePlayers()) {
            formatted = formatted.replace(p.getName(), "&a" + p.getName() + "&b");
        }
        
        // Highlight the AI's own name
        formatted = formatted.replace(name, "&a" + name + "&b");
        
        return ChatColor.translateAlternateColorCodes('&', formatted);
    }
    
    /**
     * Start the periodic chat scanning task
     */
    private void startChatScanTask() {
        if (scanTask != null) {
            scanTask.cancel();
        }
        
        long intervalTicks = chatScanIntervalSeconds * 20L;
        
        scanTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (!enabled) return;
            scanChatHistory();
        }, intervalTicks, intervalTicks);
        
        plugin.getLogger().info("[ServerAI] Chat scan task started (every " + chatScanIntervalSeconds + " seconds)");
    }
    
    /**
     * Scan recent chat history and potentially respond
     */
    private void scanChatHistory() {
        // Get messages since last scan
        List<ChatMessage> messagesToAnalyze = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        for (ChatMessage msg : recentChat) {
            if (msg.timestamp > lastScanTimestamp) {
                // Don't analyze messages that already got a direct response (contained our name)
                if (!containsName(msg.message)) {
                    messagesToAnalyze.add(msg);
                }
            }
        }
        
        lastScanTimestamp = currentTime;
        
        if (messagesToAnalyze.isEmpty()) {
            return;
        }
        
        // Build chat log for analysis
        StringBuilder chatLog = new StringBuilder();
        for (ChatMessage msg : messagesToAnalyze) {
            chatLog.append(msg.sender).append(": ").append(msg.message).append("\n");
        }
        
        // Get RAG context if enabled
        String ragContext = "";
        if (ragEnabled && ragSystem != null) {
            try {
                ragContext = ragSystem.retrieveContext(chatLog.toString());
                if (debugMode && !ragContext.isEmpty()) {
                    plugin.getLogger().info("[ServerAI] RAG context retrieved for chat scan: " + ragContext.substring(0, Math.min(100, ragContext.length())) + "...");
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[ServerAI] RAG context retrieval failed for chat scan: " + e.getMessage());
                // Continue without RAG context
            }
        }
        
        // Build a focused scan prompt - no analysis output, just respond or not
        String scanPrompt = String.format("""
            You are %s, the server AI. Review this recent chat and decide if you should respond.
            
            Recent chat:
            ---
            %s
            ---
            
            %s
            Decide:
            - If someone needs help with Minecraft, provide the answer directly.
            - If there's a conflict, calmly mediate.
            - If there's a fun conversation (%.0f%% chance), you may join naturally.
            - Otherwise, do NOT respond.
            
            If you choose to respond, write ONLY your chat message - nothing else.
            If you choose NOT to respond, reply with exactly: NO_RESPONSE
            
            No explanations, no analysis - just your message or NO_RESPONSE.
            """, name, chatLog.toString(), 
               ragContext.isEmpty() ? "" : "Relevant knowledge from server documentation:\n" + ragContext + "\n\n",
               conversationJoinChance * 100);
        
        makeAiCall(scanPrompt, "Analyze and respond if appropriate")
                .thenAccept(response -> {
                    if (response != null && !response.trim().isEmpty()) {
                        String cleaned = cleanResponse(response);
                        
                        if (!cleaned.isEmpty() && !cleaned.equalsIgnoreCase("NO_RESPONSE")) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                broadcastResponse(cleaned);
                                logMessage(name, cleaned, "proactive");
                            });
                        }
                    }
                })
                .exceptionally(e -> {
                    if (debugMode) plugin.getLogger().warning("[ServerAI] Chat scan failed: " + e.getMessage());
                    return null;
                });
    }
    
    /**
     * Clean AI response of any analysis or meta-commentary
     */
    private String cleanResponse(String response) {
        if (response == null) return "";

        String cleaned = response.trim();

        if (debugMode) {
            plugin.getLogger().info("[ServerAI] Raw response: '" + cleaned + "'");
        }

        // Strip thinking tags
        cleaned = cleaned.replaceAll("(?s)<think>.*?</think>", "").trim();
        if (cleaned.startsWith("<think>")) {
            return ""; // Still thinking, no response
        }

        // Remove common AI analysis patterns at the beginning
        String[] patternsToRemove = {
            "(?i)^Based on the (analysis|chat|conversation).*?[,:]\\s*",
            "(?i)^I (see|notice|observe) that.*?[,.]\\s*",
            "(?i)^Looking at the chat.*?[,:]\\s*",
            "(?i)^After (analyzing|reviewing).*?[,:]\\s*",
            "(?i)^The player.*?is (asking|struggling|needs).*?[.]\\s*",
            "(?i)^(Therefore|So|Thus),?\\s*(I will respond:?|my response:?)\\s*",
            "(?i)^Here'?s? my response:?\\s*",
            "(?i)^Response:?\\s*",
            "(?i)^" + name + ":?\\s*",
        };

        for (String pattern : patternsToRemove) {
            cleaned = cleaned.replaceAll(pattern, "");
        }

        // For multi-line responses, if the response seems to contain analysis followed by actual response,
        // try to extract just the final response. But be more conservative - only do this if we have
        // clear indicators of analysis vs response.
        String[] lines = cleaned.split("\n");
        if (lines.length > 1) {
            // Check if the first few lines look like analysis and the last line looks like a response
            boolean hasAnalysisPrefix = false;
            for (int i = 0; i < Math.min(3, lines.length - 1); i++) {
                String line = lines[i].trim().toLowerCase();
                if (line.startsWith("based on") || line.startsWith("i see") ||
                    line.startsWith("the player") || line.startsWith("therefore") ||
                    line.contains("will respond") || line.contains("my response")) {
                    hasAnalysisPrefix = true;
                    break;
                }
            }

            // Only extract the last line if we detected analysis patterns
            if (hasAnalysisPrefix) {
                for (int i = lines.length - 1; i >= 0; i--) {
                    String line = lines[i].trim();
                    if (!line.isEmpty() &&
                        !line.toLowerCase().startsWith("based on") &&
                        !line.toLowerCase().startsWith("i see") &&
                        !line.toLowerCase().startsWith("the player") &&
                        !line.toLowerCase().startsWith("therefore") &&
                        !line.toLowerCase().contains("will respond") &&
                        !line.toLowerCase().contains("my response")) {
                        // This looks like the actual response
                        cleaned = line;
                        break;
                    }
                }
            }
        }

        if (debugMode) {
            plugin.getLogger().info("[ServerAI] Cleaned response: '" + cleaned.trim() + "'");
        }

        return cleaned.trim();
    }
    
    /**
     * Make an AI API call
     */
    private CompletableFuture<String> makeAiCall(String systemPromptText, String userMessage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var config = plugin.getConfig();
                int timeout = config.getInt("ai.timeout-seconds", 30) * 1000;
                
                // Build request - use instance serverUrl and model
                Map<String, Object> message1 = Map.of("role", "system", "content", systemPromptText);
                Map<String, Object> message2 = Map.of("role", "user", "content", userMessage);
                Map<String, Object> requestBody = Map.of(
                        "model", model,
                        "messages", List.of(message1, message2),
                        "temperature", config.getDouble("ai.temperature", 0.7),
                        "top_p", config.getDouble("ai.top-p", 0.9),
                        "top_k", config.getInt("ai.top-k", 50),
                        "max_tokens", maxTokens
                );
                
                String jsonBody = gson.toJson(requestBody);
                String fullUrl = serverUrl + "/v1/chat/completions";
                
                if (debugMode) {
                    plugin.getLogger().info("[ServerAI] Debug: POST " + fullUrl);
                }
                
                java.net.URL url = new java.net.URL(fullUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(timeout);
                conn.setReadTimeout(timeout);
                conn.setDoOutput(true);
                
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                
                int statusCode = conn.getResponseCode();
                
                java.io.InputStream is = (statusCode >= 200 && statusCode < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();
                
                String responseBody;
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    responseBody = sb.toString();
                }
                
                if (statusCode >= 200 && statusCode < 300) {
                    JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
                    if (jsonResponse.has("choices") && jsonResponse.getAsJsonArray("choices").size() > 0) {
                        JsonObject choice = jsonResponse.getAsJsonArray("choices").get(0).getAsJsonObject();
                        if (choice.has("message") && choice.getAsJsonObject("message").has("content")) {
                            String content = choice.getAsJsonObject("message").get("content").getAsString().trim();
                            
                            // Clean the response (strips thinking tags and analysis text)
                            content = cleanResponse(content);
                            
                            if (content.isEmpty()) {
                                return null;
                            }
                            
                            return content;
                        }
                    }
                } else {
                    if (debugMode) plugin.getLogger().warning("[ServerAI] Debug: API returned " + statusCode);
                }
                
                return null;
            } catch (Exception e) {
                if (debugMode) plugin.getLogger().warning("[ServerAI] AI call error: " + e.getMessage());
                return null;
            }
        });
    }
    
    /**
     * Log a message to the Server AI log folder
     */
    private void logMessage(String sender, String message, String type) {
        File logFile = new File(logFolder, "chat.log");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            writer.write(String.format("[%s] [%s] %s: %s%n", timestamp, type.toUpperCase(), sender, message));
        } catch (IOException e) {
            plugin.getLogger().warning("[ServerAI] Failed to log: " + e.getMessage());
        }
    }
    
    /**
     * Check if Server AI is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Get the Server AI's name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Simple chat message record
     */
    private static class ChatMessage {
        final String sender;
        final String message;
        final long timestamp;
        
        ChatMessage(String sender, String message, long timestamp) {
            this.sender = sender;
            this.message = message;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Conversation message for per-player memory
     */
    private static class ConversationMessage {
        final String role; // "user" or "assistant"
        final String content;
        final long timestamp;
        
        ConversationMessage(String role, String content, long timestamp) {
            this.role = role;
            this.content = content;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Add a message to a player's conversation history
     */
    private void addToPlayerConversation(UUID playerUuid, String role, String content) {
        Deque<ConversationMessage> history = playerConversations.computeIfAbsent(
                playerUuid, k -> new ConcurrentLinkedDeque<>());
        
        history.addLast(new ConversationMessage(role, content, System.currentTimeMillis()));
        
        // Trim if over limit
        while (history.size() > maxConversationMemory) {
            history.removeFirst();
        }
    }
    
    /**
     * Build messages list with conversation history for API call
     */
    private List<Map<String, Object>> buildMessagesWithHistory(UUID playerUuid, String currentMessage, String playerName, String ragContext) {
        List<Map<String, Object>> messages = new ArrayList<>();
        
        // Build contextual system prompt with variables substituted
        Player player = Bukkit.getPlayer(playerUuid);
        String contextualPrompt = buildContextualSystemPrompt(systemPrompt, player);
        
        // Add RAG context to system prompt if available
        if (ragContext != null && !ragContext.isEmpty()) {
            contextualPrompt += "\n\nRelevant knowledge from server documentation:\n" + ragContext;
        }
        
        // Always start with system prompt
        messages.add(Map.of("role", "system", "content", contextualPrompt));
        
        // Add conversation history for this player
        Deque<ConversationMessage> history = playerConversations.get(playerUuid);
        if (history != null && !history.isEmpty()) {
            for (ConversationMessage msg : history) {
                // Skip the current message (we'll add it fresh)
                if (msg.role.equals("user") && msg.content.contains(currentMessage)) {
                    continue;
                }
                messages.add(Map.of("role", msg.role, "content", msg.content));
            }
            
            if (debugMode) {
                plugin.getLogger().info("[ServerAI] Debug: Added " + (history.size() - 1) + " messages from conversation history for " + playerName);
            }
        }
        
        // Add current message
        messages.add(Map.of("role", "user", "content", playerName + " said: " + currentMessage));
        
        return messages;
    }
    
    /**
     * Make AI call with pre-built messages list (including history)
     */
    private CompletableFuture<String> makeAiCallWithHistory(List<Map<String, Object>> messages) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var config = plugin.getConfig();
                int timeout = config.getInt("ai.timeout-seconds", 30) * 1000;
                
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", model);
                requestBody.put("messages", messages);
                requestBody.put("temperature", config.getDouble("ai.temperature", 0.7));
                requestBody.put("top_p", config.getDouble("ai.top-p", 0.9));
                requestBody.put("top_k", config.getInt("ai.top-k", 50));
                requestBody.put("max_tokens", maxTokens);
                
                String jsonBody = gson.toJson(requestBody);
                String fullUrl = serverUrl + "/v1/chat/completions";
                
                if (debugMode) {
                    plugin.getLogger().info("[ServerAI] Debug: POST " + fullUrl + " (with " + messages.size() + " messages)");
                }
                
                java.net.URL url = new java.net.URL(fullUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "Chatr-ServerAI/1.0");
                conn.setConnectTimeout(timeout);
                conn.setReadTimeout(timeout);
                conn.setDoOutput(true);
                
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }
                
                int status = conn.getResponseCode();
                InputStream is = (status >= 200 && status < 300) 
                        ? conn.getInputStream() 
                        : conn.getErrorStream();
                
                String responseBody;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line);
                    }
                    responseBody = sb.toString();
                }
                
                if (status >= 200 && status < 300) {
                    JsonObject json = gson.fromJson(responseBody, JsonObject.class);
                    if (json.has("choices") && json.getAsJsonArray("choices").size() > 0) {
                        String content = json.getAsJsonArray("choices")
                                .get(0).getAsJsonObject()
                                .getAsJsonObject("message")
                                .get("content").getAsString().trim();
                        return cleanResponse(content);
                    }
                }
                
                return null;
            } catch (Exception e) {
                if (debugMode) plugin.getLogger().warning("[ServerAI] API call failed: " + e.getMessage());
                return null;
            }
        });
    }
    
    /*
     * DISABLED: Skin loading functionality
     * Minecraft doesn't support custom skins for fake players in tab lists
     * Code kept for future reference when skins work on actual player entities
     */
    
    /**
     * Clear conversation history for a player (e.g., on quit or command)
     */
    public void clearPlayerConversation(UUID playerUuid) {
        playerConversations.remove(playerUuid);
    }
    
    /**
     * Clear all conversation histories
     */
    public void clearAllConversations() {
        playerConversations.clear();
    }
}
