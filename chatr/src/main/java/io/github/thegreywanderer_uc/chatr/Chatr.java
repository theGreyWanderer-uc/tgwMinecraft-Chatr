package io.github.thegreywanderer_uc.chatr;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.thegreywanderer_uc.chatr.ai.AIProvider;
import io.github.thegreywanderer_uc.chatr.ai.AIProviderException;
import io.github.thegreywanderer_uc.chatr.ai.AIProviderFactory;
import io.github.thegreywanderer_uc.chatr.ai.OpenAIProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// Use ProtocolLib-based NPC instead of NMS ServerPlayer
// import net.minecraft.server.level.ServerPlayer;

public final class Chatr extends JavaPlugin implements CommandExecutor, Listener {
    private FileConfiguration config; // To store config values
    private final Map<Integer, DirectNpc> npcs = new HashMap<>(); // Store NPC entities by ID
    private final Map<Integer, String> npcSkins = new HashMap<>(); // Store skin player names by NPC ID
    private final Map<String, FileConfiguration> npcAiConfigs = new HashMap<>(); // Store NPC AI configs
    private final Map<String, File> npcFolders = new HashMap<>(); // Store NPC folder paths
    private final Map<String, Integer> npcNamesToIds = new HashMap<>(); // Map NPC names to IDs for quick lookup
    private final Map<UUID, String> lastNpcChat = new HashMap<>(); // Track last NPC each player chatted with
    private int nextNpcId = 0; // Next available NPC ID
    private ServerAI serverAI; // Server-wide AI assistant
    private boolean debugMode; // Debug mode flag
    private FileConfiguration npcConfig;
    private File npcFile;
    private final Gson gson = new Gson(); // For JSON parsing
    
    // New feature managers
    private ConversationManager conversationManager;
    private RateLimiter rateLimiter;
    private AIProviderFactory providerFactory;
    private ResponseCache responseCache;
    private MetricsManager metricsManager;
    private ClickToChatHandler clickToChatHandler;

    /**
     * Get the map of NPC entities
     */
    public Map<Integer, DirectNpc> getNpcs() {
        return npcs;
    }

    @Override
    public void onEnable() {
        // Display ASCII art banner
        displayBanner();

        // Save default config if it doesn't exist
        saveDefaultConfig();
        config = getConfig(); // Load the config
        debugMode = config.getBoolean("debug-mode", false); // Load debug mode setting
        // Load NPCs
        npcFile = new File(getDataFolder(), "npcs.yml");
        if (!npcFile.exists()) {
            try {
                npcFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create npcs.yml");
            }
        }
        npcConfig = YamlConfiguration.loadConfiguration(npcFile);
        loadNpcs();
        loadNpcAiConfigs(); // Load AI configs for all NPCs
        getCommand("chatr").setExecutor(this);
        getCommand("chatr").setTabCompleter(new ChatrTabCompleter(this)); // Register tab completer
        getServer().getPluginManager().registerEvents(this, this); // Register listener for joins
        
        // Initialize new feature managers
        initializeManagers();
        
        // Initialize and start Server AI
        serverAI = new ServerAI(this);
        serverAI.start();

        // Log enable message with version and NPC count
        String version = getDescription().getVersion();
        int npcCount = npcs.size();
        getLogger().info("Enabling Chatr v" + version + " - Loaded " + npcCount + " NPC" + (npcCount != 1 ? "s" : ""));
    }

    @Override
    public void onDisable() {
        // Stop Server AI
        if (serverAI != null) {
            serverAI.stop();
        }
        
        // Shutdown new managers
        shutdownManagers();
        
        saveNpcs();
        if (config != null) {
            String disableMsg = config.getString("disable-message", "Chatr plugin has been disabled.");
            // Log with ANSI colors on the message only (no extra prefix)
            getLogger().info(toAnsi(disableMsg) + "\u001B[m");
        } else {
            getLogger().info("Chatr plugin has been disabled (config not loaded).");
        }
        // Clear NPCs on disable (optional, since memory-only)
        npcs.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (debugMode) getLogger().info("[DEBUG] onCommand START - args: " + String.join(", ", args));
        String prefix = config.getString("chat-prefix", "&6[Chatr] ");
        // Translate & codes for in-game colors (unchanged)
        String coloredPrefix = ChatColor.translateAlternateColorCodes('&', prefix);
        if (args.length == 0) {
            sender.sendMessage(coloredPrefix + "Available Chatr commands:");
            sender.sendMessage(coloredPrefix + "/chatr version - Shows the current plugin version.");
            sender.sendMessage(coloredPrefix + "/chatr reload - Reloads the config (requires permission chatr.reload).");
            sender.sendMessage(coloredPrefix + "/chatr create <name> - Creates an NPC one block in front (requires chatr.create).");
            sender.sendMessage(coloredPrefix + "/chatr remove <name> - Removes the NPC with that name (requires chatr.remove).");
            sender.sendMessage(coloredPrefix + "/chatr skin <name> <player> - Sets the skin of the NPC to the skin of the specified player (requires chatr.skin).");
            sender.sendMessage(coloredPrefix + "/chatr color <name> <type> [code] - Sets NPC interaction message colors (requires chatr.color).");
            sender.sendMessage(coloredPrefix + "/chatr info - Shows debug information about NPCs and ArmorStands (requires chatr.admin).");
            sender.sendMessage(coloredPrefix + "/chatr <npc> ai <message> - Chat with an AI-powered NPC (requires chatr.admin).");
            sender.sendMessage(coloredPrefix + "/chatr r <message> - Quick reply to the last NPC you spoke with (requires chatr.ai).");
            sender.sendMessage(coloredPrefix + "/chatr clear <npc> - Clear your conversation history with an NPC (requires chatr.ai).");
            sender.sendMessage(coloredPrefix + "/chatr reload-npc <name> - Reload an NPC's AI configuration from file (requires chatr.reload).");
            sender.sendMessage(coloredPrefix + "/chatr stats - View API usage statistics (requires chatr.admin).");
            sender.sendMessage(coloredPrefix + "/chatr cache [stats|clear] - Manage response cache (requires chatr.admin).");
            return true;
        }
        
        // Handle quick reply command: /chatr r <message>
        if (args[0].equalsIgnoreCase("r") || args[0].equalsIgnoreCase("reply")) {
            if (!sender.hasPermission("chatr.ai")) {
                sender.sendMessage(coloredPrefix + "You don't have permission to chat with NPCs.");
                return true;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(coloredPrefix + "This command can only be run by a player.");
                return true;
            }
            
            Player player = (Player) sender;
            String lastNpcName = lastNpcChat.get(player.getUniqueId());
            
            if (lastNpcName == null) {
                sender.sendMessage(coloredPrefix + "You haven't spoken with any NPC yet. Use /chatr <npc> ai <message> first.");
                return true;
            }
            
            DirectNpc npc = npcs.get(lastNpcName);
            if (npc == null) {
                sender.sendMessage(coloredPrefix + "The NPC '" + lastNpcName + "' no longer exists.");
                lastNpcChat.remove(player.getUniqueId());
                return true;
            }
            
            if (args.length < 2) {
                sender.sendMessage(coloredPrefix + "Usage: /chatr r <your message>");
                return true;
            }
            
            // Combine all args after "r" as the message
            StringBuilder messageBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                if (messageBuilder.length() > 0) messageBuilder.append(" ");
                messageBuilder.append(args[i]);
            }
            String userMessage = messageBuilder.toString().trim();
            String playerName = player.getName();
            
            // Log the user message
            logNpcChat(lastNpcName, playerName, userMessage, null);
            
            // Capture context synchronously before async call
            NpcContext npcCtx = captureNpcContext(lastNpcName);
            PlayerContext playerCtx = capturePlayerContext(player);
            
            // Make AI API call with context
            final String finalNpcName = lastNpcName;
            makeAiApiCall(lastNpcName, userMessage, player, npcCtx, playerCtx)
                .thenAccept(aiResponse -> {
                    Bukkit.getScheduler().runTask(this, () -> {
                        String formattedResponse = formatAiResponse(aiResponse, player, finalNpcName);
                        player.sendMessage(ChatColor.GOLD + "[" + finalNpcName + "] " + formattedResponse);
                        logNpcChat(finalNpcName, playerName, null, aiResponse);
                    });
                })
                .exceptionally(throwable -> {
                    Bukkit.getScheduler().runTask(this, () -> {
                        player.sendMessage(ChatColor.RED + "[" + finalNpcName + "] " + ChatColor.WHITE + "Sorry, I'm having trouble responding right now.");
                        getLogger().warning("AI chat failed for NPC '" + finalNpcName + "': " + throwable.getMessage());
                    });
                    return null;
                });
            
            return true;
        }
        if (args[0].equalsIgnoreCase("version")) {
            String version = getDescription().getVersion();
            sender.sendMessage(coloredPrefix + "Chatr version: " + version);
            return true;
        }
        if (args[0].equalsIgnoreCase("info")) {
            if (!sender.hasPermission("chatr.admin")) {
                sender.sendMessage(coloredPrefix + "You don't have permission to view debug info.");
                return true;
            }

            // Create timestamp for filename
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
            String timestamp = now.format(formatter);
            String filename = "chatrDebugLog_" + timestamp + ".txt";
            File debugFile = new File(getDataFolder(), filename);

            sender.sendMessage(coloredPrefix + "=== NPC Debug Information ===");
            sender.sendMessage(coloredPrefix + "Total NPCs: " + npcs.size());

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(debugFile))) {
                writer.write("=== NPC Debug Information ===");
                writer.newLine();
                writer.write("Generated: " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                writer.newLine();
                writer.write("Total NPCs: " + npcs.size());
                writer.newLine();
                writer.newLine();

                for (Map.Entry<Integer, DirectNpc> entry : npcs.entrySet()) {
                    Integer npcId = entry.getKey();
                    DirectNpc npc = entry.getValue();

                    String npcInfo = "NPC '" + npc.getName() + "' (ID: " + npcId + "):";
                    sender.sendMessage(coloredPrefix + npcInfo);
                    writer.write(npcInfo);
                    writer.newLine();

                    String locationInfo = "  - Location: " + (npc != null ? npc.getLocation() : "null");
                    sender.sendMessage(coloredPrefix + locationInfo);
                    writer.write(locationInfo);
                    writer.newLine();

                    String uuidInfo = "  - UUID: " + (npc != null ? npc.getUuid() : "null");
                    sender.sendMessage(coloredPrefix + uuidInfo);
                    writer.write(uuidInfo);
                    writer.newLine();

                    String worldInfo = "  - World: " + (npc != null ? npc.getLocation().getWorld().getName() : "null");
                    sender.sendMessage(coloredPrefix + worldInfo);
                    writer.write(worldInfo);
                    writer.newLine();

                    writer.newLine();
                }

                String handlerStatus;
                if (clickToChatHandler != null) {
                    handlerStatus = "ClickToChatHandler: Enabled=" + clickToChatHandler.isEnabled();
                } else {
                    handlerStatus = "ClickToChatHandler: null";
                }
                sender.sendMessage(coloredPrefix + handlerStatus);
                writer.write(handlerStatus);
                writer.newLine();

                sender.sendMessage(coloredPrefix + "Debug log saved to: " + debugFile.getAbsolutePath());

            } catch (IOException e) {
                sender.sendMessage(coloredPrefix + "Error writing debug log to file: " + e.getMessage());
                getLogger().warning("Failed to write debug log: " + e.getMessage());
            }

            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("chatr.reload")) {
                sender.sendMessage(coloredPrefix + "You don't have permission to reload.");
                return true;
            }
            reloadConfig(); // Reload from file
            config = getConfig();
            debugMode = config.getBoolean("debug-mode", false); // Reload debug mode setting
            
            // Reload AI provider factory with new configuration
            if (providerFactory != null) {
                providerFactory.reload();
            }
            
            // Reload and restart Server AI
            if (serverAI != null) {
                serverAI.stop();
                serverAI.reload();
                serverAI.start();
            }
            
            // Reload click-to-chat handler configuration
            if (clickToChatHandler != null) {
                clickToChatHandler.reload();
            }
            
            sender.sendMessage(coloredPrefix + "Config reloaded successfully!");
            return true;
        }
        if (args[0].equalsIgnoreCase("create")) {
            if (!sender.hasPermission("chatr.create")) {
                sender.sendMessage(coloredPrefix + "You don't have permission to create NPCs.");
                return true;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(coloredPrefix + "This command can only be run by a player.");
                return true;
            }
            if (args.length != 2) {
                sender.sendMessage(coloredPrefix + "Usage: /chatr create <name>");
                return true;
            }
            String npcName = args[1];
            if (npcNamesToIds.containsKey(npcName)) {
                sender.sendMessage(coloredPrefix + "An NPC with name '" + npcName + "' already exists.");
                return true;
            }
            Player player = (Player) sender;
            // Place NPC exactly where the player is standing
            Location loc = player.getLocation().clone();
            // Ensure NPC is on solid ground (not floating if player is looking up)
            loc.setY(loc.getBlockY());
            UUID uuid = UUID.randomUUID();
            int npcId = nextNpcId++;
            createNpc(npcId, npcName, loc, uuid, player);
            sender.sendMessage(coloredPrefix + "NPC '" + npcName + "' (ID: " + npcId + ") created at your location.");
            return true;
        }
        if (args[0].equalsIgnoreCase("remove")) {
            if (!sender.hasPermission("chatr.remove")) {
                sender.sendMessage(coloredPrefix + "You don't have permission to remove NPCs.");
                return true;
            }
            if (args.length != 2) {
                sender.sendMessage(coloredPrefix + "Usage: /chatr remove <name>");
                return true;
            }
            String npcName = args[1];
            Integer npcId = npcNamesToIds.get(npcName);
            if (npcId == null) {
                sender.sendMessage(coloredPrefix + "No NPC found with name '" + npcName + "'.");
                return true;
            }
            DirectNpc npc = npcs.remove(npcId);
            if (npc == null) {
                sender.sendMessage(coloredPrefix + "No NPC found with name '" + npcName + "'.");
                return true;
            }
            removeNpc(npc);
            npcSkins.remove(npcId); // Also remove skin data
            npcNamesToIds.remove(npcName);
            saveNpcs();
            sender.sendMessage(coloredPrefix + "NPC '" + npcName + "' (ID: " + npcId + ") removed.");
            return true;
        }
        if (args[0].equalsIgnoreCase("skin")) {
            if (!sender.hasPermission("chatr.skin")) {
                sender.sendMessage(coloredPrefix + "You don't have permission to set NPC skins.");
                return true;
            }
            if (args.length != 3) {
                sender.sendMessage(coloredPrefix + "Usage: /chatr skin <npc_name> <player_name>");
                return true;
            }
            String npcName = args[1];
            String playerName = args[2];
            Integer npcId = npcNamesToIds.get(npcName);
            if (npcId == null) {
                sender.sendMessage(coloredPrefix + "No NPC found with name '" + npcName + "'.");
                return true;
            }
            setNpcSkin(npcId, playerName);
            sender.sendMessage(coloredPrefix + "Skin for NPC '" + npcName + "' (ID: " + npcId + ") set to '" + playerName + "'.");
            return true;
        }

        // Handle color commands
        if (args[0].equalsIgnoreCase("color")) {
            if (!sender.hasPermission("chatr.color")) {
                sender.sendMessage(coloredPrefix + "You don't have permission to set NPC colors.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(coloredPrefix + "Usage: /chatr color <npc_name> <type> [color_code]");
                sender.sendMessage(coloredPrefix + "Types: npc-name, instruction, cancel");
                sender.sendMessage(coloredPrefix + "Color codes: &0-&f, &l=bold, &o=italic, etc. Use 'default' to reset to global default.");
                return true;
            }
            String npcName = args[1];
            String colorType = args[2].toLowerCase();
            String colorCode = args.length > 3 ? args[3] : null;
            
            Integer npcId = npcNamesToIds.get(npcName);
            if (npcId == null) {
                sender.sendMessage(coloredPrefix + "No NPC found with name '" + npcName + "'.");
                return true;
            }
            
            DirectNpc npc = npcs.get(npcId);
            if (npc == null) {
                sender.sendMessage(coloredPrefix + "NPC '" + npcName + "' not found in memory.");
                return true;
            }
            
            switch (colorType) {
                case "npc-name":
                    if ("default".equalsIgnoreCase(colorCode)) {
                        npc.setNpcNameColor(null);
                        sender.sendMessage(coloredPrefix + "NPC name color for '" + npcName + "' reset to global default.");
                    } else {
                        npc.setNpcNameColor(colorCode);
                        sender.sendMessage(coloredPrefix + "NPC name color for '" + npcName + "' set to '" + colorCode + "'.");
                    }
                    break;
                case "instruction":
                    if ("default".equalsIgnoreCase(colorCode)) {
                        npc.setInstructionColor(null);
                        sender.sendMessage(coloredPrefix + "Instruction color for '" + npcName + "' reset to global default.");
                    } else {
                        npc.setInstructionColor(colorCode);
                        sender.sendMessage(coloredPrefix + "Instruction color for '" + npcName + "' set to '" + colorCode + "'.");
                    }
                    break;
                case "cancel":
                    if ("default".equalsIgnoreCase(colorCode)) {
                        npc.setCancelColor(null);
                        sender.sendMessage(coloredPrefix + "Cancel color for '" + npcName + "' reset to global default.");
                    } else {
                        npc.setCancelColor(colorCode);
                        sender.sendMessage(coloredPrefix + "Cancel color for '" + npcName + "' set to '" + colorCode + "'.");
                    }
                    break;
                default:
                    sender.sendMessage(coloredPrefix + "Invalid color type. Use: npc-name, instruction, or cancel.");
                    return true;
            }
            
            saveNpcs();
            return true;
        }

        // Handle reload-npc command: /chatr reload-npc <name>
        if (args.length == 2 && args[0].equalsIgnoreCase("reload-npc")) {
            if (!sender.hasPermission("chatr.reload")) {
                sender.sendMessage(coloredPrefix + "You don't have permission to reload NPC configs.");
                return true;
            }

            String npcName = args[1];
            if (!npcNamesToIds.containsKey(npcName)) {
                sender.sendMessage(coloredPrefix + "No NPC found with name '" + npcName + "'.");
                return true;
            }

            // Reload the NPC's AI configuration
            loadNpcAiConfig(npcName);
            sender.sendMessage(coloredPrefix + "Reloaded AI configuration for NPC '" + npcName + "'.");
            return true;
        }

        // Handle AI chat commands: /chatr <npc_name> ai <message>
        if (args.length >= 3 && args[1].equalsIgnoreCase("ai")) {
            if (!sender.hasPermission("chatr.admin")) {
                sender.sendMessage(coloredPrefix + "You don't have permission to chat with NPCs.");
                return true;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(coloredPrefix + "This command can only be run by a player.");
                return true;
            }

            String npcName = args[0];
            Integer npcId = npcNamesToIds.get(npcName);
            if (npcId == null) {
                sender.sendMessage(coloredPrefix + "No NPC found with name '" + npcName + "'.");
                return true;
            }
            DirectNpc npc = npcs.get(npcId);
            if (npc == null) {
                sender.sendMessage(coloredPrefix + "No NPC found with name '" + npcName + "'.");
                return true;
            }

            // Check if NPC has a system prompt
            String systemPrompt = getNpcSystemPrompt(npcName);
            if (systemPrompt == null || systemPrompt.isEmpty()) {
                sender.sendMessage(coloredPrefix + "This NPC is not configured for AI chat.");
                return true;
            }

            // Combine all args after "ai" as the message
            StringBuilder messageBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (messageBuilder.length() > 0) messageBuilder.append(" ");
                messageBuilder.append(args[i]);
            }
            String userMessage = messageBuilder.toString().trim();

            if (userMessage.isEmpty()) {
                sender.sendMessage(coloredPrefix + "Usage: /chatr " + npcName + " ai <your message>");
                return true;
            }

            Player player = (Player) sender;
            String playerName = player.getName();
            
            // Track last NPC this player chatted with
            lastNpcChat.put(player.getUniqueId(), npcName);

            // Log the user message
            logNpcChat(npcName, playerName, userMessage, null);

            // Capture context synchronously before async call
            NpcContext npcCtx = captureNpcContext(npcName);
            PlayerContext playerCtx = capturePlayerContext(player);

            // Make AI API call with player context
            final String finalNpcName = npcName;
            makeAiApiCall(npcName, userMessage, player, npcCtx, playerCtx)
                .thenAccept(aiResponse -> {
                    // Send response to player on main thread
                    Bukkit.getScheduler().runTask(this, () -> {
                        String formattedResponse = formatAiResponse(aiResponse, player, finalNpcName);
                        player.sendMessage(ChatColor.GOLD + "[" + finalNpcName + "] " + formattedResponse);
                        // Log the AI response
                        logNpcChat(finalNpcName, playerName, null, aiResponse);
                    });
                })
                .exceptionally(throwable -> {
                    // Handle error on main thread
                    Bukkit.getScheduler().runTask(this, () -> {
                        player.sendMessage(ChatColor.RED + "[" + finalNpcName + "] " + ChatColor.WHITE + "Sorry, I'm having trouble responding right now.");
                        getLogger().warning("AI chat failed for NPC '" + finalNpcName + "': " + throwable.getMessage());
                    });
                    return null;
                });

            return true;
        }
        
        // Handle stats/metrics command: /chatr stats [summary|npcs|players|npc <name>]
        if (args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("metrics")) {
            if (!sender.hasPermission("chatr.admin")) {
                sender.sendMessage(coloredPrefix + "You don't have permission to view stats.");
                return true;
            }
            
            if (metricsManager == null) {
                sender.sendMessage(coloredPrefix + "Metrics are disabled in config.");
                return true;
            }
            
            String subCmd = args.length > 1 ? args[1].toLowerCase() : "summary";
            
            switch (subCmd) {
                case "summary":
                    Map<String, Object> summary = metricsManager.getSummary();
                    sender.sendMessage(coloredPrefix + "=== API Usage Summary ===");
                    sender.sendMessage(coloredPrefix + "Uptime: " + summary.get("uptimeMinutes") + " minutes");
                    sender.sendMessage(coloredPrefix + "Total Requests: " + summary.get("totalRequests"));
                    sender.sendMessage(coloredPrefix + "Cache Hit Rate: " + summary.get("cacheHitRate"));
                    sender.sendMessage(coloredPrefix + "Avg Response Time: " + summary.get("avgResponseTimeMs") + "ms");
                    sender.sendMessage(coloredPrefix + "Errors: " + summary.get("totalErrors"));
                    sender.sendMessage(coloredPrefix + "Active NPCs: " + summary.get("activeNpcs") + " | Unique Players: " + summary.get("uniquePlayers"));
                    break;
                case "npcs":
                    List<Map.Entry<String, Integer>> topNpcs = metricsManager.getTopNpcs(20);
                    sender.sendMessage(coloredPrefix + "=== Top NPCs by Request Count ===");
                    for (Map.Entry<String, Integer> entry : topNpcs) {
                        sender.sendMessage(coloredPrefix + entry.getKey() + ": " + entry.getValue());
                    }
                    if (topNpcs.isEmpty()) {
                        sender.sendMessage(coloredPrefix + "(No NPC data yet)");
                    }
                    break;
                case "players":
                    List<Map.Entry<String, Integer>> topPlayers = metricsManager.getTopPlayers(20);
                    sender.sendMessage(coloredPrefix + "=== Top Players by Request Count ===");
                    for (Map.Entry<String, Integer> entry : topPlayers) {
                        sender.sendMessage(coloredPrefix + entry.getKey() + ": " + entry.getValue());
                    }
                    if (topPlayers.isEmpty()) {
                        sender.sendMessage(coloredPrefix + "(No player data yet)");
                    }
                    break;
                case "npc":
                    if (args.length < 3) {
                        sender.sendMessage(coloredPrefix + "Usage: /chatr stats npc <name>");
                        return true;
                    }
                    String nName = args[2];
                    Map<String, Object> npcStats = metricsManager.getNpcStats(nName);
                    if (npcStats.containsKey("error")) {
                        sender.sendMessage(coloredPrefix + (String) npcStats.get("error"));
                    } else {
                        sender.sendMessage(coloredPrefix + "=== Stats for NPC '" + nName + "' ===");
                        for (Map.Entry<String, Object> entry : npcStats.entrySet()) {
                            sender.sendMessage(coloredPrefix + entry.getKey() + ": " + entry.getValue());
                        }
                    }
                    break;
                default:
                    sender.sendMessage(coloredPrefix + "Usage: /chatr stats [summary|npcs|players|npc <name>]");
            }
            return true;
        }
        
        // Handle cache command: /chatr cache [stats|clear [npcName|all]]
        if (args[0].equalsIgnoreCase("cache")) {
            if (!sender.hasPermission("chatr.admin")) {
                sender.sendMessage(coloredPrefix + "You don't have permission to manage cache.");
                return true;
            }
            
            if (responseCache == null) {
                sender.sendMessage(coloredPrefix + "Response caching is disabled in config.");
                return true;
            }
            
            String subCmd = args.length > 1 ? args[1].toLowerCase() : "stats";
            
            switch (subCmd) {
                case "stats":
                    Map<String, Object> cacheStats = responseCache.getStats();
                    sender.sendMessage(coloredPrefix + "=== Response Cache Stats ===");
                    sender.sendMessage(coloredPrefix + "Enabled: " + cacheStats.get("enabled"));
                    sender.sendMessage(coloredPrefix + "Size: " + cacheStats.get("size") + "/" + cacheStats.get("maxSize"));
                    sender.sendMessage(coloredPrefix + "TTL: " + cacheStats.get("ttlSeconds") + " seconds");
                    sender.sendMessage(coloredPrefix + "Total Hits: " + cacheStats.get("totalHits"));
                    break;
                case "clear":
                    String target = args.length > 2 ? args[2] : "all";
                    if (target.equalsIgnoreCase("all")) {
                        responseCache.clear();
                        sender.sendMessage(coloredPrefix + "Cache cleared.");
                    } else {
                        responseCache.clearForNpc(target);
                        sender.sendMessage(coloredPrefix + "Cache cleared for NPC '" + target + "'.");
                    }
                    break;
                default:
                    sender.sendMessage(coloredPrefix + "Usage: /chatr cache [stats|clear [npcName|all]]");
            }
            return true;
        }
        
        // Handle clear conversation command: /chatr clear <npcName>
        if (args[0].equalsIgnoreCase("clear")) {
            if (!sender.hasPermission("chatr.ai")) {
                sender.sendMessage(coloredPrefix + "You don't have permission to clear conversations.");
                return true;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(coloredPrefix + "This command can only be run by a player.");
                return true;
            }
            
            if (conversationManager == null) {
                sender.sendMessage(coloredPrefix + "Conversation memory is disabled in config.");
                return true;
            }
            
            if (args.length < 2) {
                sender.sendMessage(coloredPrefix + "Usage: /chatr clear <npcName>");
                return true;
            }
            
            Player player = (Player) sender;
            String npcToClear = args[1];
            
            if (!npcs.containsKey(npcToClear)) {
                sender.sendMessage(coloredPrefix + "No NPC found with name '" + npcToClear + "'.");
                return true;
            }
            
            conversationManager.clearHistory(player.getUniqueId(), npcToClear);
            sender.sendMessage(coloredPrefix + "Cleared your conversation history with " + npcToClear + ".");
            return true;
        }
        
        // Handle ServerAI debugging command: /chatr serverai [refresh|clear]
        if (args[0].equalsIgnoreCase("serverai") || args[0].equalsIgnoreCase("ai")) {
            if (!sender.hasPermission("chatr.admin")) {
                sender.sendMessage(coloredPrefix + "You don't have permission to manage ServerAI.");
                return true;
            }
            if (!(sender instanceof Player)) {
                sender.sendMessage(coloredPrefix + "This command can only be run by a player.");
                return true;
            }
            
            Player player = (Player) sender;
            
            if (serverAI == null) {
                sender.sendMessage(coloredPrefix + "ServerAI is not initialized.");
                return true;
            }
            
            String subCmd = args.length > 1 ? args[1].toLowerCase() : "status";
            
            switch (subCmd) {
                case "status":
                    sender.sendMessage(coloredPrefix + "=== ServerAI Status ===");
                    sender.sendMessage(coloredPrefix + "Enabled: " + serverAI.isEnabled());
                    sender.sendMessage(coloredPrefix + "Name: " + serverAI.getName());
                    sender.sendMessage(coloredPrefix + "In Tab List: " + serverAI.isInTabList(player));
                    break;
                case "refresh":
                    serverAI.refreshTabList(player);
                    sender.sendMessage(coloredPrefix + "Refreshed ServerAI in your tab list.");
                    break;
                case "clear":
                    serverAI.clearAllConversations();
                    sender.sendMessage(coloredPrefix + "Cleared all ServerAI conversation memories.");
                    break;
                default:
                    sender.sendMessage(coloredPrefix + "Usage: /chatr serverai [status|refresh|clear]");
            }
            return true;
        }

        sender.sendMessage(coloredPrefix + "Unknown subcommand. Usage: " + command.getUsage());
        return false;
    }

    private void createNpc(int npcId, String name, Location loc, UUID uuid, Player creator) {
        if (debugMode) getLogger().info("[DEBUG] createNpc called: id=" + npcId + ", name=" + name + ", creator=" + creator.getName());

        // Create NPC using fake player entity (like Citizens2 HumanController)
        if (debugMode) getLogger().info("[DEBUG] About to create DirectNpc instance...");
        DirectNpc npc = new DirectNpc(name, uuid, loc);
        
        if (debugMode) getLogger().info("[DEBUG] DirectNpc created, about to call spawn()...");

        npcs.put(npcId, npc);
        npcNamesToIds.put(name, npcId);

        if (debugMode) getLogger().info("[DEBUG] Stored DirectNpc for NPC '" + name + "' (ID: " + npcId + "). Total NPCs: " + npcs.size());

        // Spawn for creator immediately
        npc.spawn(creator);
        if (debugMode) getLogger().info("[DEBUG] spawn() returned successfully");

        // Spawn for all other online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(creator)) {
                npc.spawn(p);
            }
        }

        saveNpcs();

        // Load AI configuration for this NPC
        loadNpcAiConfig(name);
    }
    
    // Overload for loading NPCs on startup (no specific creator)
    private void createNpc(int npcId, String name, Location loc, UUID uuid) {
        // Create NPC using fake player entity (like Citizens2 HumanController)
        DirectNpc npc = new DirectNpc(name, uuid, loc);

        npcs.put(npcId, npc);
        npcNamesToIds.put(name, npcId);

        // Spawn for all online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            npc.spawn(p);
        }

        // Load AI configuration for this NPC
        loadNpcAiConfig(name);
    }

    private void removeNpc(DirectNpc npc) {
        // Find NPC ID and name
        Integer npcId = null;
        String npcName = null;
        for (Map.Entry<Integer, DirectNpc> entry : npcs.entrySet()) {
            if (entry.getValue().equals(npc)) {
                npcId = entry.getKey();
                npcName = npc.getName();
                break;
            }
        }
        if (npcId != null) {
            npcNamesToIds.remove(npcName);
        }

        // Remove the entity
        npc.despawn();
    }

    private void setNpcSkin(int npcId, String playerName) {
        DirectNpc npc = npcs.get(npcId);
        if (npc == null) return;

        // Set the skin name
        npc.setSkin(playerName);

        // Store the skin name for persistence
        npcSkins.put(npcId, playerName);
        saveNpcs();

        // If the NPC is currently spawned, respawn it to apply the new skin
        if (npc.isSpawned()) {
            getLogger().info("Respawning NPC '" + npc.getName() + "' to apply new skin");
            npc.despawn();
            // Respawn for all online players
            for (Player p : Bukkit.getOnlinePlayers()) {
                npc.spawn(p);
            }
        }

        getLogger().info("Successfully set skin for NPC '" + npc.getName() + "' (ID: " + npcId + ") to player '" + playerName + "'");
    }

    private void loadNpcs() {
        for (String key : npcConfig.getKeys(false)) {
            getLogger().info("[DEBUG] Found config key: '" + key + "'");
            try {
                int npcId = Integer.parseInt(key);
                getLogger().info("[DEBUG] Parsed NPC ID: " + npcId);
                ConfigurationSection section = npcConfig.getConfigurationSection(key);
                if (section != null) {
                    String npcName = section.getString("name");
                    getLogger().info("[DEBUG] Loading NPC '" + npcName + "' with ID " + npcId);
                    if (npcName == null || npcName.isEmpty()) {
                        getLogger().warning("NPC with ID '" + key + "' has no name, skipping");
                        continue;
                    }
                    
                    String worldName = section.getString("world");
                    double x = section.getDouble("x");
                    double y = section.getDouble("y");
                    double z = section.getDouble("z");
                    float yaw = (float) section.getDouble("yaw");
                    float pitch = (float) section.getDouble("pitch");
                    String uuidStr = section.getString("uuid");
                    UUID uuid = UUID.fromString(uuidStr);
                    String skin = section.getString("skin");
                    Location loc = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
                    if (loc.getWorld() != null) {
                        createNpc(npcId, npcName, loc, uuid);
                        DirectNpc npc = npcs.get(npcId);
                        if (npc != null) {
                            if (skin != null && !skin.isEmpty()) {
                                npcSkins.put(npcId, skin);
                                npc.setSkin(skin);
                            }
                            // Load color configuration
                            npc.setNpcNameColor(section.getString("colors.npc-name"));
                            npc.setInstructionColor(section.getString("colors.instruction"));
                            npc.setCancelColor(section.getString("colors.cancel"));
                        }
                        // Update next available ID
                        if (npcId >= nextNpcId) {
                            nextNpcId = npcId + 1;
                        }
                    } else {
                        getLogger().warning("World '" + worldName + "' not found for NPC '" + npcName + "' (ID: " + npcId + ")");
                    }
                }
            } catch (NumberFormatException e) {
                getLogger().warning("Invalid NPC ID format: '" + key + "', skipping - this suggests the data file is in old format");
                getLogger().warning("Config section for key '" + key + "': " + npcConfig.getConfigurationSection(key));
            }
        }
    }

    private void saveNpcs() {
        getLogger().info("[DEBUG] saveNpcs() called - saving " + npcs.size() + " NPCs");
        // Clear the config first
        for (String key : npcConfig.getKeys(false)) {
            npcConfig.set(key, null);
        }
        for (Map.Entry<Integer, DirectNpc> entry : npcs.entrySet()) {
            Integer npcId = entry.getKey();
            DirectNpc npc = entry.getValue();
            Location loc = npc.getLocation();
            getLogger().info("[DEBUG] Saving NPC ID " + npcId + " with name '" + npc.getName() + "'");
            ConfigurationSection section = npcConfig.createSection(String.valueOf(npcId));
            section.set("name", npc.getName());
            section.set("world", loc.getWorld().getName());
            section.set("x", loc.getX());
            section.set("y", loc.getY());
            section.set("z", loc.getZ());
            section.set("yaw", loc.getYaw());
            section.set("pitch", loc.getPitch());
            section.set("uuid", npc.getUuid().toString());
            section.set("skin", npcSkins.getOrDefault(npcId, ""));
            
            // Save color configuration
            if (npc.getNpcNameColor() != null) {
                section.set("colors.npc-name", npc.getNpcNameColor());
            }
            if (npc.getInstructionColor() != null) {
                section.set("colors.instruction", npc.getInstructionColor());
            }
            if (npc.getCancelColor() != null) {
                section.set("colors.cancel", npc.getCancelColor());
            }
        }
        try {
            npcConfig.save(npcFile);
            getLogger().info("[DEBUG] Successfully saved npcs.yml");
        } catch (IOException e) {
            getLogger().severe("Could not save npcs.yml: " + e.getMessage());
        }
    }

    /**
     * Load AI configurations for all NPCs
     */
    private void loadNpcAiConfigs() {
        for (DirectNpc npc : npcs.values()) {
            loadNpcAiConfig(npc.getName());
        }
    }

    /**
     * Load AI configuration for a specific NPC
     */
    private void loadNpcAiConfig(String npcName) {
        File npcFolder = new File(getDataFolder(), "npcs/" + npcName);
        if (!npcFolder.exists()) {
            npcFolder.mkdirs();
        }
        npcFolders.put(npcName, npcFolder);

        File configFile = new File(npcFolder, "config.yml");
        if (!configFile.exists()) {
            // Create default config from template
            try {
                saveResource("npc-config-template.yml", false);
                File templateFile = new File(getDataFolder(), "npc-config-template.yml");
                if (templateFile.exists()) {
                    java.nio.file.Files.copy(templateFile.toPath(), configFile.toPath());
                    // Replace placeholder with actual NPC name
                    FileConfiguration npcAiConfig = YamlConfiguration.loadConfiguration(configFile);
                    npcAiConfig.set("npc-name", npcName);
                    npcAiConfig.save(configFile);
                }
            } catch (IOException e) {
                getLogger().severe("Could not create AI config for NPC '" + npcName + "': " + e.getMessage());
            }
        }

        FileConfiguration npcAiConfig = YamlConfiguration.loadConfiguration(configFile);
        npcAiConfigs.put(npcName, npcAiConfig);
    }

    /**
     * Get AI setting for an NPC (with fallback to global config)
     */
    private String getNpcAiSetting(String npcName, String setting) {
        FileConfiguration npcAiConfig = npcAiConfigs.get(npcName);
        if (npcAiConfig != null) {
            // Check ai.setting first (new format)
            if (npcAiConfig.contains("ai." + setting)) {
                return npcAiConfig.getString("ai." + setting);
            }
            // Check root level for backward compatibility
            if (npcAiConfig.contains(setting)) {
                return npcAiConfig.getString(setting);
            }
        }
        // Fallback to global config
        return config.getString("ai." + setting);
    }

    /**
     * Get AI numeric setting for an NPC (with fallback to global config)
     */
    private double getNpcAiNumericSetting(String npcName, String setting) {
        FileConfiguration npcAiConfig = npcAiConfigs.get(npcName);
        if (npcAiConfig != null) {
            // Check ai.setting first (new format)
            if (npcAiConfig.contains("ai." + setting)) {
                return npcAiConfig.getDouble("ai." + setting);
            }
            // Check root level for backward compatibility
            if (npcAiConfig.contains(setting)) {
                return npcAiConfig.getDouble(setting);
            }
        }
        // Fallback to global config
        return config.getDouble("ai." + setting);
    }

    /**
     * Get system prompt for an NPC
     */
    private String getNpcSystemPrompt(String npcName) {
        FileConfiguration npcAiConfig = npcAiConfigs.get(npcName);
        if (npcAiConfig != null) {
            return npcAiConfig.getString("system-prompt", "").trim();
        }
        return "";
    }

    /**
     * Log chat interaction for an NPC - logs to per-player files
     */
    private void logNpcChat(String npcName, String playerName, String userMessage, String aiResponse) {
        FileConfiguration npcAiConfig = npcAiConfigs.get(npcName);
        if (npcAiConfig == null || !npcAiConfig.getBoolean("enable-chat-logging", true)) {
            return;
        }

        File npcFolder = npcFolders.get(npcName);
        if (npcFolder == null) return;

        // Create logs subfolder and per-player log file
        File logsFolder = new File(npcFolder, "logs");
        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
        }
        
        File logFile = new File(logsFolder, playerName + ".log");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            if (userMessage != null && !userMessage.isEmpty()) {
                writer.write(String.format("[%s] %s: %s%n", timestamp, playerName, userMessage));
            }
            if (aiResponse != null && !aiResponse.isEmpty()) {
                writer.write(String.format("[%s] %s: %s%n", timestamp, npcName, aiResponse));
            }
        } catch (IOException e) {
            getLogger().warning("Could not log chat for NPC '" + npcName + "': " + e.getMessage());
        }
    }
    
    /**
     * Format AI response with colors:
     * - Base text in aqua (&b)
     * - Context variables/keywords in green (&a)
     * 
     * Keywords that get highlighted: player names, NPC names, numbers, 
     * biome names (all Minecraft biomes), weather conditions, time of day,
     * time in HH:MM format, item names, mob names
     */
    private String formatAiResponse(String response, Player player, String npcName) {
        // Start with aqua color for base text
        String formatted = "&b" + response;
        
        // Highlight player name
        if (player != null) {
            formatted = formatted.replaceAll("(?i)(" + Pattern.quote(player.getName()) + ")", "&a$1&b");
        }
        
        // Highlight NPC name
        formatted = formatted.replaceAll("(?i)(" + Pattern.quote(npcName) + ")", "&a$1&b");
        
        // Highlight numbers (health, hunger, levels, coordinates, etc.)
        formatted = formatted.replaceAll("(\\d+(?:\\.\\d+)?)", "&a$1&b");
        
        // Highlight time of day keywords
        String[] timeKeywords = {"morning", "day", "dusk", "night", "dawn", "noon", "midnight", "sunrise", "sunset"};
        for (String keyword : timeKeywords) {
            formatted = formatted.replaceAll("(?i)\\b(" + keyword + ")\\b", "&a$1&b");
        }
        
        // Highlight time format (HH:MM)
        formatted = formatted.replaceAll("(\\d{1,2}:\\d{2})", "&a$1&b");
        
        // Highlight weather keywords
        String[] weatherKeywords = {"clear", "rain", "raining", "storm", "stormy", "thunder", "thunderstorm", "sunny", "cloudy"};
        for (String keyword : weatherKeywords) {
            formatted = formatted.replaceAll("(?i)\\b(" + keyword + ")\\b", "&a$1&b");
        }
        
        // Highlight common biome keywords
        String[] biomeKeywords = {
            "plains", "forest", "desert", "ocean", "jungle", "taiga", "swamp", "mountain", "savanna", "beach", "river", "cave", "nether", "end",
            "stony shore", "stone shore", "snowy tundra", "snowy mountains", "ice spikes", "frozen river", "frozen ocean", "cold ocean", "lukewarm ocean", "warm ocean",
            "badlands", "eroded badlands", "wooded badlands", "desert hills", "desert lakes", "snowy taiga", "snowy taiga hills", "taiga hills", "taiga mountains",
            "giant tree taiga", "giant tree taiga hills", "dark forest", "dark forest hills", "birch forest", "birch forest hills", "tall birch forest", "tall birch hills",
            "giant spruce taiga", "giant spruce taiga hills", "spruce forest", "spruce forest hills", "flower forest", "sunflower plains", "bamboo jungle", "bamboo jungle hills",
            "meadow", "grove", "snowy slopes", "lofty peaks", "stony peaks", "windswept hills", "windswept forest", "windswept gravelly hills", "windswept savanna",
            "mangrove swamp", "deep dark", "dripstone caves", "lush caves", "deep ocean", "deep cold ocean", "deep frozen ocean", "deep lukewarm ocean", "deep warm ocean",
            "the void", "cherry grove", "pale garden"
        };
        for (String keyword : biomeKeywords) {
            formatted = formatted.replaceAll("(?i)\\b(" + keyword + ")\\b", "&a$1&b");
        }
        
        // Translate color codes to Minecraft colors
        return ChatColor.translateAlternateColorCodes('&', formatted);
    }

    /**
     * Captured NPC context data for async use
     */
    private static class NpcContext {
        final String npcName;
        final String timeOfDay;
        final String exactTime;
        final String weather;
        final String biome;
        final String worldName;
        
        NpcContext(String npcName, String timeOfDay, String exactTime, String weather, String biome, String worldName) {
            this.npcName = npcName;
            this.timeOfDay = timeOfDay;
            this.exactTime = exactTime;
            this.weather = weather;
            this.biome = biome;
            this.worldName = worldName;
        }
    }
    
    /**
     * Captured player context data for async use
     */
    private static class PlayerContext {
        final String playerName;
        final String health;
        final String maxHealth;
        final String hunger;
        final String level;
        final String gameMode;
        final String biome;
        
        PlayerContext(String playerName, String health, String maxHealth, String hunger, String level, String gameMode, String biome) {
            this.playerName = playerName;
            this.health = health;
            this.maxHealth = maxHealth;
            this.hunger = hunger;
            this.level = level;
            this.gameMode = gameMode;
            this.biome = biome;
        }
    }

    /**
     * Capture NPC context synchronously for async use
     */
    private NpcContext captureNpcContext(String npcName) {
        // Get NPC ID from name
        Integer npcId = npcNamesToIds.get(npcName);
        if (npcId == null) {
            if (debugMode) getLogger().info("[DEBUG] captureNpcContext: NPC '" + npcName + "' not found in npcNamesToIds map");
            return new NpcContext(npcName, "unknown", "unknown", "unknown", "unknown", "unknown");
        }
        
        DirectNpc npc = npcs.get(npcId);
        if (npc == null) {
            if (debugMode) getLogger().info("[DEBUG] captureNpcContext: NPC '" + npcName + "' (ID: " + npcId + ") not found in npcs map");
            return new NpcContext(npcName, "unknown", "unknown", "unknown", "unknown", "unknown");
        }
        
        Location npcLocation = npc.getLocation();
        if (debugMode) {
            getLogger().info("[DEBUG] captureNpcContext: NPC '" + npcName + "' location: " + npcLocation);
            if (npcLocation != null) {
                getLogger().info("[DEBUG] captureNpcContext: NPC '" + npcName + "' world: " + npcLocation.getWorld());
            }
        }
        
        if (npcLocation == null || npcLocation.getWorld() == null) {
            if (debugMode) getLogger().info("[DEBUG] captureNpcContext: NPC '" + npcName + "' location or world is null, returning unknown context");
            return new NpcContext(npcName, "unknown", "unknown", "unknown", "unknown", "unknown");
        }
        
        org.bukkit.World world = npcLocation.getWorld();
        
        // Time of day (descriptive)
        long time = world.getTime();
        String timeOfDay;
        if (time >= 0 && time < 6000) {
            timeOfDay = "morning";
        } else if (time >= 6000 && time < 12000) {
            timeOfDay = "day";
        } else if (time >= 12000 && time < 13000) {
            timeOfDay = "dusk";
        } else if (time >= 13000 && time < 23000) {
            timeOfDay = "night";
        } else {
            timeOfDay = "dawn";
        }
        
        // Exact time in HH:MM format (like ServerAI)
        String exactTime = formatTime(time);
        
        // Weather
        String weather;
        if (world.isThundering()) {
            weather = "thunderstorm";
        } else if (world.hasStorm()) {
            weather = "rain";
        } else {
            weather = "clear";
        }
        
        // World name
        String worldName = world.getName();
        
        // Biome at NPC location
        String biome = npcLocation.getBlock().getBiome().getKey().getKey().toLowerCase().replace("_", " ");
        
        if (debugMode) {
            getLogger().info("[DEBUG] captureNpcContext: NPC '" + npcName + "' time=" + time + " (" + timeOfDay + "), exactTime=" + exactTime + ", weather=" + weather + ", biome=" + biome);
        }
        
        return new NpcContext(npcName, timeOfDay, exactTime, weather, biome, worldName);
    }
    
    /**
     * Capture player context synchronously for async use
     */
    private PlayerContext capturePlayerContext(Player player) {
        if (player == null) {
            if (debugMode) getLogger().info("[DEBUG] capturePlayerContext: Player is null");
            return null;
        }
        
        String playerName = player.getName();
        String health = String.format("%.1f", player.getHealth());
        String maxHealth = String.format("%.1f", player.getMaxHealth());
        String hunger = String.valueOf(player.getFoodLevel());
        String level = String.valueOf(player.getLevel());
        String gameMode = player.getGameMode().name().toLowerCase();
        
        // Player's biome
        String biome = "unknown";
        try {
            Location playerLocation = player.getLocation();
            if (debugMode) {
                getLogger().info("[DEBUG] capturePlayerContext: Player '" + playerName + "' location: " + playerLocation);
                if (playerLocation != null) {
                    getLogger().info("[DEBUG] capturePlayerContext: Player '" + playerName + "' world: " + playerLocation.getWorld());
                }
            }
            
            if (playerLocation != null && playerLocation.getWorld() != null) {
                biome = playerLocation.getBlock().getBiome().getKey().getKey().toLowerCase().replace("_", " ");
                if (debugMode) getLogger().info("[DEBUG] capturePlayerContext: Player '" + playerName + "' biome: " + biome);
            } else {
                if (debugMode) getLogger().info("[DEBUG] capturePlayerContext: Player '" + playerName + "' location or world is null");
            }
        } catch (Exception e) {
            if (debugMode) getLogger().info("[DEBUG] capturePlayerContext: Exception getting player biome: " + e.getMessage());
            // Biome lookup failed, keep as unknown
        }
        
        PlayerContext ctx = new PlayerContext(playerName, health, maxHealth, hunger, level, gameMode, biome);
        if (debugMode) {
            getLogger().info("[DEBUG] capturePlayerContext: Captured context for '" + playerName + "': health=" + health + ", biome=" + biome);
        }
        return ctx;
    }

    /**
     * Build context-aware system prompt by replacing variables with actual values
     * 
     * Supported variables:
     * NPC Context:
     *   {npc_name} - Name of the NPC
     *   {time} - Current Minecraft world time (day/night/dawn/dusk)
     *   {time_exact} - Exact time in HH:MM format (e.g., 14:22)
     *   {time_ticks} - Raw Minecraft time in ticks
     *   {weather} - Current weather (clear/rain/thunder)
     *   {biome} - Biome the NPC is in
     *   {world} - World name
     * 
     * Player Context:
     *   {player_name} - Player's username
     *   {player_health} - Player's current health (0-20)
     *   {player_max_health} - Player's max health
     *   {player_hunger} - Player's hunger level (0-20)
     *   {player_level} - Player's XP level
     *   {player_gamemode} - Player's gamemode
     *   {player_biome} - Biome where player is located
     */
    private String buildContextualSystemPrompt(String rawPrompt, NpcContext npcCtx, PlayerContext playerCtx) {
        String prompt = rawPrompt;
        
        // NPC context
        if (npcCtx != null) {
            if (debugMode) {
                getLogger().info("[DEBUG] buildContextualSystemPrompt: Replacing NPC variables - time=" + npcCtx.timeOfDay + ", exactTime=" + npcCtx.exactTime + ", weather=" + npcCtx.weather + ", biome=" + npcCtx.biome);
            }
            prompt = prompt.replace("{npc_name}", npcCtx.npcName);
            prompt = prompt.replace("{time}", npcCtx.timeOfDay);
            prompt = prompt.replace("{time_exact}", npcCtx.exactTime);
            prompt = prompt.replace("{weather}", npcCtx.weather);
            prompt = prompt.replace("{biome}", npcCtx.biome);
            prompt = prompt.replace("{world}", npcCtx.worldName);
        }
        
        // Player context
        if (playerCtx != null) {
            if (debugMode) {
                getLogger().info("[DEBUG] buildContextualSystemPrompt: Replacing player variables - name=" + playerCtx.playerName + ", biome=" + playerCtx.biome);
            }
            prompt = prompt.replace("{player_name}", playerCtx.playerName);
            prompt = prompt.replace("{player_health}", playerCtx.health);
            prompt = prompt.replace("{player_max_health}", playerCtx.maxHealth);
            prompt = prompt.replace("{player_hunger}", playerCtx.hunger);
            prompt = prompt.replace("{player_level}", playerCtx.level);
            prompt = prompt.replace("{player_gamemode}", playerCtx.gameMode);
            prompt = prompt.replace("{player_biome}", playerCtx.biome);
        }
        
        if (debugMode) {
            getLogger().info("[DEBUG] buildContextualSystemPrompt: Final prompt contains variables: " + prompt.contains("{"));
        }
        
        return prompt;
    }

    /**
     * Format Minecraft time ticks to HH:MM format (same as ServerAI)
     */
    private static String formatTime(long ticks) {
        long hours = ((ticks + 6000) % 24000) / 1000;
        long minutes = ((ticks + 6000) % 1000) * 60 / 1000;
        return String.format("%02d:%02d", hours, minutes);
    }

    /**
     * Make an AI API call for chat completion with player context
     */
    private CompletableFuture<String> makeAiApiCall(String npcName, String userMessage, Player player, NpcContext npcCtx, PlayerContext playerCtx) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            boolean cacheHit = false;
            
            try {
                // Get AI provider for this NPC
                AIProvider provider = providerFactory.getProviderForNpc(npcName);
                if (provider == null) {
                    throw new RuntimeException("No AI provider configured for NPC '" + npcName + "'");
                }
                
                String model = getNpcAiSetting(npcName, "model");
                String rawSystemPrompt = getNpcSystemPrompt(npcName);
                
                // Build contextual system prompt with variable substitution
                String systemPrompt = buildContextualSystemPrompt(rawSystemPrompt, npcCtx, playerCtx);

                if (debugMode) {
                    getLogger().info("[AI DEBUG] - Raw System Prompt: " + (rawSystemPrompt != null ? rawSystemPrompt.substring(0, Math.min(100, rawSystemPrompt.length())) + "..." : "null"));
                    getLogger().info("[AI DEBUG] - System Prompt (with context): " + (systemPrompt != null ? systemPrompt.substring(0, Math.min(100, systemPrompt.length())) + "..." : "null"));
                    getLogger().info("[AI DEBUG] - Full System Prompt length: " + (systemPrompt != null ? systemPrompt.length() : 0));
                }

                if (model == null || model.isEmpty()) {
                    throw new RuntimeException("AI model not configured");
                }

                if (systemPrompt == null || systemPrompt.isEmpty()) {
                    throw new RuntimeException("NPC does not have a system prompt configured");
                }
                
                // Rate limiting check
                if (rateLimiter != null && player != null) {
                    RateLimiter.RateLimitResult result = rateLimiter.canMakeRequest(player, provider.getName());
                    if (!result.allowed) {
                        throw new RuntimeException("Rate limited. Please wait " + result.waitSeconds + " seconds.");
                    }
                    rateLimiter.recordRequest(player);
                }
                
                // Check response cache first
                if (responseCache != null) {
                    String cachedResponse = responseCache.get(npcName, userMessage);
                    if (cachedResponse != null) {
                        if (debugMode) getLogger().info("[AI DEBUG] Cache hit for message: " + userMessage.substring(0, Math.min(30, userMessage.length())) + "...");
                        cacheHit = true;
                        recordMetrics(npcName, player, System.currentTimeMillis() - startTime, true);
                        return cachedResponse;
                    }
                }
                
                // Build conversation history for provider (excluding system prompt and current message)
                List<Map<String, String>> history = new ArrayList<>();
                if (conversationManager != null && player != null) {
                    history = conversationManager.getHistoryForApi(player.getUniqueId(), npcName);
                    if (debugMode && !history.isEmpty()) {
                        getLogger().info("[AI DEBUG] Added " + history.size() + " conversation history messages");
                    }
                }

                if (debugMode) {
                    String providerDisplayName = provider.getName();
                    // If it's an OpenAI provider with a local URL, show it as local-openai-compatible
                    if (provider instanceof OpenAIProvider && 
                        (((OpenAIProvider) provider).getBaseUrl() != null && 
                         (((OpenAIProvider) provider).getBaseUrl().contains("localhost") || 
                          ((OpenAIProvider) provider).getBaseUrl().contains("127.0.0.1")))) {
                        providerDisplayName = "local-openai-compatible";
                    }
                    getLogger().info("[AI DEBUG] Calling provider: " + providerDisplayName + " with model: " + model);
                }
                
                // Make the AI call using the provider
                double temperature = getNpcAiNumericSetting(npcName, "temperature");
                int maxTokens = (int) config.getDouble("ai.max-tokens", 500);
                
                String content = provider.chatCompletion(
                    model,
                    systemPrompt,
                    userMessage,
                    history,
                    temperature,
                    maxTokens
                );
                
                // Strip thinking tags from "thinking" models (e.g., Qwen3)
                // Case 1: Complete <think>...</think> blocks
                content = content.replaceAll("(?s)<think>.*?</think>", "").trim();
                
                // Case 2: Unclosed <think> tag (model ran out of tokens while thinking)
                // If content still starts with <think>, the model never finished thinking
                if (content.startsWith("<think>")) {
                    if (debugMode) getLogger().warning("[AI DEBUG] Model ran out of tokens while thinking - no actual response generated");
                    content = "I need a moment to gather my thoughts... Could you ask me again?";
                }
                
                // Case 3: Check if response is empty after stripping (model only produced thinking)
                if (content.isEmpty()) {
                    if (debugMode) getLogger().warning("[AI DEBUG] Model response was only thinking content - no actual response");
                    content = "Hmm, let me think about that differently... What would you like to know?";
                }
                
                if (debugMode) getLogger().info("[AI DEBUG] Extracted AI response: " + content);
                
                // Store in conversation history
                if (conversationManager != null && player != null) {
                    conversationManager.addMessage(player, npcName, "user", userMessage);
                    conversationManager.addMessage(player, npcName, "assistant", content);
                }
                
                // Store in cache
                if (responseCache != null) {
                    responseCache.put(npcName, userMessage, content);
                }
                
                // Record metrics
                recordMetrics(npcName, player, System.currentTimeMillis() - startTime, false);
                
                return content;

            } catch (Exception e) {
                if (debugMode) {
                    getLogger().severe("[AI DEBUG] Exception during AI API call for NPC '" + npcName + "':");
                    getLogger().severe("[AI DEBUG] Exception type: " + e.getClass().getName());
                    getLogger().severe("[AI DEBUG] Exception message: " + e.getMessage());
                    e.printStackTrace();
                }
                throw new RuntimeException("AI call failed", e);
            }
        });
    }
    
    /**
     * Record metrics for an API call
     */
    private void recordMetrics(String npcName, Player player, long responseTimeMs, boolean cacheHit) {
        if (metricsManager != null && player != null) {
            metricsManager.recordRequest(npcName, player.getUniqueId(), player.getName(), responseTimeMs, cacheHit);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Delay NPC spawning to avoid packet conflicts during login
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                // Spawn all NPCs for the joining player
                for (DirectNpc npc : npcs.values()) {
                    npc.spawn(player);
                }
            }
        }, 20L); // 1 second delay
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (isChatrNpc(clicked)) {
            Player player = event.getPlayer();
            String npcUuid = getNpcUuid(clicked);
            DirectNpc npc = getNpcByUuid(npcUuid);
            if (npc != null) {
                handleNpcInteraction(player, npc);
                event.setCancelled(true);  // Prevent vanilla interactions
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity damaged = event.getEntity();
        if (isChatrNpc(damaged)) {
            if (debugMode) {
                getLogger().info("[DEBUG] Preventing damage to NPC: " + damaged.getName() +
                    " from " + event.getCause() + " (amount: " + event.getDamage() + ")");
            }
            event.setCancelled(true);  // Make NPCs invulnerable to damage
        }
    }



    /**
     * Get player UUID from Mojang API
     */
    private CompletableFuture<UUID> getPlayerUUID(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = "https://api.mojang.com/users/profiles/minecraft/" + playerName;
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Chatr-Minecraft-Plugin/1.0")
                    .GET()
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                    if (json.has("id")) {
                        String uuidStr = json.get("id").getAsString();
                        // Convert UUID string to UUID object (Mojang returns it without dashes)
                        return UUID.fromString(uuidStr.replaceFirst(
                            "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                            "$1-$2-$3-$4-$5"));
                    }
                } else if (response.statusCode() == 204) {
                    // Player not found
                    return null;
                }
            } catch (Exception e) {
                getLogger().warning("Failed to get UUID for player '" + playerName + "': " + e.getMessage());
            }
            return null;
        });
    }

    /**
     * Get player skin data from Mojang session server
     */
    private CompletableFuture<String> getPlayerSkinData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Add ?unsigned=false to get signed texture data (required for skins to display)
                String url = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", "") + "?unsigned=false";
                getLogger().info("[SKIN DEBUG] Fetching skin from: " + url);
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Chatr-Minecraft-Plugin/1.0")
                    .GET()
                    .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                getLogger().info("[SKIN DEBUG] Mojang API response status: " + response.statusCode());

                if (response.statusCode() == 200) {
                    getLogger().info("[SKIN DEBUG] Mojang API response: " + response.body());
                    return response.body();
                }
            } catch (Exception e) {
                getLogger().warning("Failed to get skin data for UUID '" + uuid + "': " + e.getMessage());
            }
            return null;
        });
    }

    /**
     * Display ASCII art banner for the plugin
     */
    private void displayBanner() {
        getLogger().info("");
        getLogger().info("╔═════════════════════════════════════════════╗");
        getLogger().info("║                   CHATR                     ║");
        getLogger().info("║              AI-Powered NPCs                ║");
        getLogger().info("║                                             ║");
        getLogger().info("║   ██████  ██   ██  █████  ████████  █████   ║");
        getLogger().info("║  ██       ██   ██ ██   ██    ██    ██   ██  ║");
        getLogger().info("║  ██       ███████ ███████    ██    ██████   ║");
        getLogger().info("║  ██       ██   ██ ██   ██    ██    ██   ██  ║");
        getLogger().info("║   ██████  ██   ██ ██   ██    ██    ██   ██  ║");
        getLogger().info("║                                             ║");
        getLogger().info("║         Chat with intelligent NPCs!         ║");
        getLogger().info("╚═════════════════════════════════════════════╝");
        getLogger().info("");
    }

    /**
     * Converts Minecraft & color/formatting codes to ANSI escape sequences for console.
     * Based on standard mappings; resets are handled per-message.
     */
    private String toAnsi(String input) {
        // Colors (foreground)
        input = input.replace("&0", "\u001B[0;30m"); // Black
        input = input.replace("&1", "\u001B[0;34m"); // Dark Blue
        input = input.replace("&2", "\u001B[0;32m"); // Dark Green
        input = input.replace("&3", "\u001B[0;36m"); // Dark Aqua
        input = input.replace("&4", "\u001B[0;31m"); // Dark Red
        input = input.replace("&5", "\u001B[0;35m"); // Dark Purple
        input = input.replace("&6", "\u001B[0;33m"); // Gold
        input = input.replace("&7", "\u001B[0;37m"); // Gray
        input = input.replace("&8", "\u001B[0;30;1m"); // Dark Gray (bright black)
        input = input.replace("&9", "\u001B[0;34;1m"); // Blue
        input = input.replace("&a", "\u001B[0;32;1m"); // Green
        input = input.replace("&b", "\u001B[0;36;1m"); // Aqua
        input = input.replace("&c", "\u001B[0;31;1m"); // Red
        input = input.replace("&d", "\u001B[0;35;1m"); // Light Purple
        input = input.replace("&e", "\u001B[0;33;1m"); // Yellow
        input = input.replace("&f", "\u001B[0;37;1m"); // White (bright white)
        // Formatting/styles (can combine with colors)
        input = input.replace("&k", "\u001B[5m"); // Obfuscated (blink)
        input = input.replace("&l", "\u001B[1m"); // Bold (using standard 1m for reliability)
        input = input.replace("&m", "\u001B[9m"); // Strikethrough
        input = input.replace("&n", "\u001B[4m"); // Underline
        input = input.replace("&o", "\u001B[3m"); // Italic
        input = input.replace("&r", "\u001B[m"); // Reset
        return input;
    }
    
    /**
     * Initialize all new feature managers
     */
    private void initializeManagers() {
        // Conversation manager for per-player-per-NPC memory
        if (config.getBoolean("conversation.enabled", true)) {
            conversationManager = new ConversationManager(this);
            if (debugMode) getLogger().info("[DEBUG] ConversationManager initialized");
        }
        
        // Rate limiter
        if (config.getBoolean("rate-limit.enabled", true)) {
            rateLimiter = new RateLimiter(this);
            if (debugMode) getLogger().info("[DEBUG] RateLimiter initialized");
        }
        
        // AI provider factory (always initialize - handles multiple providers)
        providerFactory = new AIProviderFactory(this);
        if (debugMode) getLogger().info("[DEBUG] AIProviderFactory initialized");
        
        // Response cache
        if (config.getBoolean("cache.enabled", true)) {
            responseCache = new ResponseCache(this);
            if (debugMode) getLogger().info("[DEBUG] ResponseCache initialized");
        }
        
        // Metrics manager
        if (config.getBoolean("metrics.enabled", true)) {
            metricsManager = new MetricsManager(this);
            if (debugMode) getLogger().info("[DEBUG] MetricsManager initialized");
        }
        
        // Click-to-chat handler
        if (config.getBoolean("click-to-chat.enabled", true)) {
            clickToChatHandler = new ClickToChatHandler(this, npcs);
            // Wire up the chat callback
            clickToChatHandler.setChatCallback((player, chatRequest) -> {
                String npcName = chatRequest.npcName;
                String message = chatRequest.message;
                
                // Handle as if player used /chatr <npc> ai <message>
                lastNpcChat.put(player.getUniqueId(), npcName);
                logNpcChat(npcName, player.getName(), message, null);
                
                // Capture context synchronously before async call
                NpcContext npcCtx = captureNpcContext(npcName);
                PlayerContext playerCtx = capturePlayerContext(player);
                
                makeAiApiCall(npcName, message, player, npcCtx, playerCtx)
                    .thenAccept(aiResponse -> {
                        Bukkit.getScheduler().runTask(this, () -> {
                            String formattedResponse = formatAiResponse(aiResponse, player, npcName);
                            player.sendMessage(ChatColor.GOLD + "[" + npcName + "] " + formattedResponse);
                            logNpcChat(npcName, player.getName(), null, aiResponse);
                        });
                    })
                    .exceptionally(throwable -> {
                        Bukkit.getScheduler().runTask(this, () -> {
                            player.sendMessage(ChatColor.RED + "[" + npcName + "] " + ChatColor.WHITE + "Sorry, I'm having trouble responding right now.");
                            getLogger().warning("AI chat (click-to-chat) failed for NPC '" + npcName + "': " + throwable.getMessage());
                        });
                        return null;
                    });
            });
            getServer().getPluginManager().registerEvents(clickToChatHandler, this);
            if (debugMode) getLogger().info("[DEBUG] ClickToChatHandler initialized and registered");
        }
    }
    
    /**
     * Shutdown all managers and save data
     */
    private void shutdownManagers() {
        if (conversationManager != null) {
            conversationManager.saveAllConversations();
            if (debugMode) getLogger().info("[DEBUG] ConversationManager saved and shutdown");
        }
        
        if (metricsManager != null) {
            metricsManager.saveMetrics();
            if (debugMode) getLogger().info("[DEBUG] MetricsManager saved and shutdown");
        }
        
        if (responseCache != null && debugMode) {
            Map<String, Object> cacheStats = responseCache.getStats();
            getLogger().info("[DEBUG] ResponseCache final stats: " + cacheStats);
        }
    }
    
    /**
     * Get all NPC names (for tab completion)
     */
    public Set<String> getNpcNames() {
        return npcNamesToIds.keySet();
    }
    
    /**
     * Get an NPC by name (for click-to-chat handler)
     */
    public DirectNpc getNpc(String name) {
        return npcs.get(name);
    }
    

    
    /**
     * Check if player has permission to chat with NPCs
     */
    public boolean hasAiPermission(Player player) {
        return player.hasPermission("chatr.ai");
    }

    /**
     * Check if an entity is a Chatr NPC
     */
    private boolean isChatrNpc(Entity entity) {
        return entity.hasMetadata("chatr_npc");
    }

    /**
     * Get the NPC UUID from entity metadata
     */
    private String getNpcUuid(Entity entity) {
        if (entity.hasMetadata("chatr_uuid")) {
            return entity.getMetadata("chatr_uuid").get(0).asString();
        }
        return null;
    }

    /**
     * Get NPC by UUID
     */
    private DirectNpc getNpcByUuid(String uuid) {
        try {
            UUID npcUuid = UUID.fromString(uuid);
            for (DirectNpc npc : npcs.values()) {
                if (npc.getUuid().equals(npcUuid)) {
                    return npc;
                }
            }
        } catch (IllegalArgumentException e) {
            // Invalid UUID format
        }
        return null;
    }

    /**
     * Handle player interaction with an NPC
     */
    private void handleNpcInteraction(Player player, DirectNpc npc) {
        // Interaction handling is now done by ClickToChatHandler
        // No message needed here as ClickToChatHandler provides clean instructions
    }
}
