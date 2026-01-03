package io.github.thegreywanderer_uc.chatr;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Handles click-to-chat NPC interaction.
 * When a player right-clicks near an NPC, their next chat message is
 * captured and sent to that NPC.
 */
public class ClickToChatHandler implements Listener {

    private final JavaPlugin plugin;
    private final Map<Integer, DirectNpc> npcs;

    // Players awaiting chat input for an NPC
    // Key: playerUUID -> NPC name they're chatting with
    private final Map<UUID, PendingChat> pendingChats = new ConcurrentHashMap<>();

    // Timeout task IDs for each player (to cancel and reschedule)
    private final Map<UUID, Integer> timeoutTasks = new ConcurrentHashMap<>();

    // Configuration
    private int chatTimeoutSeconds;
    private boolean enabled;
    private double interactionDistance;

    // Cooldown to prevent duplicate chat mode starts (in milliseconds)
    private final Map<UUID, Long> lastChatStartTime = new ConcurrentHashMap<>();
    private static final long CHAT_START_COOLDOWN_MS = 1000; // 1 second cooldown

    // Callback for when a chat message should be processed
    private BiConsumer<Player, ChatRequest> chatCallback;

    public ClickToChatHandler(JavaPlugin plugin, Map<Integer, DirectNpc> npcs) {
        this.plugin = plugin;
        this.npcs = npcs;
        reload();
    }
    
    /**
     * Reload configuration
     */
    public void reload() {
        var config = plugin.getConfig();
        this.enabled = config.getBoolean("click-to-chat.enabled", true);
        this.chatTimeoutSeconds = config.getInt("click-to-chat.timeout-seconds", 30);
        this.interactionDistance = config.getDouble("click-to-chat.interaction-distance", 5.0);
    }
    
    /**
     * Set the callback for processing chat messages to NPCs
     */
    public void setChatCallback(BiConsumer<Player, ChatRequest> callback) {
        this.chatCallback = callback;
    }
    
    /**
     * Get the DirectNpc associated with an entity, if any.
     * Uses entity metadata to identify Chatr NPCs.
     * @param entity The entity that was clicked
     * @return The DirectNpc, or null if not found
     */
    private DirectNpc getNPCFromEntity(org.bukkit.entity.Entity entity) {
        // Check if it's a Chatr NPC using metadata
        if (DirectNpc.isChatrNpc(entity)) {
            String npcUuid = DirectNpc.getNpcUuid(entity);
            if (npcUuid != null) {
                // Find the NPC by UUID
                for (DirectNpc npc : npcs.values()) {
                    if (npc.getUuid().toString().equals(npcUuid)) {
                        return npc;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Get NPC by name from the npcs map.
     * @param npcName The name of the NPC
     * @return The DirectNpc, or null if not found
     */
    private DirectNpc getNPCFromName(String npcName) {
        for (DirectNpc npc : npcs.values()) {
            if (npc.getName().equals(npcName)) {
                return npc;
            }
        }
        return null;
    }
    
    /**
     * Handle right-click on entities - check if it's an NPC ArmorStand
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info("[DEBUG] ClickToChat: onPlayerInteractEntity called for entity: " + event.getRightClicked().getType() + " (ID: " + event.getRightClicked().getEntityId() + ")");
        }

        if (!enabled) {
            if (plugin.getConfig().getBoolean("debug-mode", false)) {
                plugin.getLogger().info("[DEBUG] ClickToChat: Handler disabled, ignoring event");
            }
            return;
        }

        // Get the NPC from the entity using metadata
        DirectNpc clickedNpc = getNPCFromEntity(event.getRightClicked());

        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info("[DEBUG] ClickToChat: Entity clicked (ID: " + event.getRightClicked().getEntityId() + "), found NPC: '" + (clickedNpc != null ? clickedNpc.getName() : "null") + "'");
        }

        if (clickedNpc == null) {
            if (plugin.getConfig().getBoolean("debug-mode", false)) {
                plugin.getLogger().info("[DEBUG] ClickToChat: No NPC found for clicked entity");
            }
            return;
        }

        // Check interaction distance
        Player player = event.getPlayer();
        Location playerLoc = player.getLocation();
        Location npcLoc = clickedNpc.getLocation();
        double distance = playerLoc.distance(npcLoc);

        if (distance > interactionDistance) {
            if (plugin.getConfig().getBoolean("debug-mode", false)) {
                plugin.getLogger().info("[DEBUG] ClickToChat: Player too far from NPC (distance: " + String.format("%.2f", distance) + ", max: " + interactionDistance + ")");
            }
            return;
        }

        String npcName = clickedNpc.getName();
        UUID uuid = player.getUniqueId();

        // Check if this player already has an active chat session
        if (pendingChats.containsKey(uuid)) {
            PendingChat currentChat = pendingChats.get(uuid);
            if (currentChat.npcName.equals(npcName)) {
                // Already chatting with this NPC, ignore
                if (plugin.getConfig().getBoolean("debug-mode", false)) {
                    plugin.getLogger().info("[DEBUG] ClickToChat: Player " + player.getName() + " already chatting with '" + npcName + "', ignoring");
                }
                return;
            } else {
                // Chatting with different NPC - cancel existing chat and start new one
                if (plugin.getConfig().getBoolean("debug-mode", false)) {
                    plugin.getLogger().info("[DEBUG] ClickToChat: Player " + player.getName() + " switching from '" + currentChat.npcName + "' to '" + npcName + "'");
                }

                // Cancel existing timeout task
                Integer existingTaskId = timeoutTasks.remove(uuid);
                if (existingTaskId != null) {
                    Bukkit.getScheduler().cancelTask(existingTaskId);
                }

                // Remove existing pending chat
                pendingChats.remove(uuid);

                // Start new chat mode (bypass cooldown since this is a legitimate switch)
                startChatModeInternal(player, npcName);
                return;
            }
        }

        // Check cooldown to prevent rapid duplicate starts
        long currentTime = System.currentTimeMillis();
        Long lastStartTime = lastChatStartTime.get(uuid);
        if (lastStartTime != null && (currentTime - lastStartTime) < CHAT_START_COOLDOWN_MS) {
            // Too soon since last chat start, ignore
            if (plugin.getConfig().getBoolean("debug-mode", false)) {
                plugin.getLogger().info("[DEBUG] ClickToChat: Cooldown active for " + player.getName() + ", ignoring");
            }
            return;
        }

        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info("[DEBUG] ClickToChat: Starting chat mode for " + player.getName() + " with NPC '" + npcName + "'");
        }

        // Start chat mode with this NPC
        startChatMode(player, npcName);
    }
    
    /**
     * Start chat mode for a player with an NPC
     */
    public void startChatMode(Player player, String npcName) {
        UUID uuid = player.getUniqueId();
        
        // Check cooldown to prevent duplicate chat mode starts
        long currentTime = System.currentTimeMillis();
        Long lastStartTime = lastChatStartTime.get(uuid);
        if (lastStartTime != null && (currentTime - lastStartTime) < CHAT_START_COOLDOWN_MS) {
            // Too soon since last chat start, ignore
            if (plugin.getConfig().getBoolean("debug-mode", false)) {
                plugin.getLogger().info("[DEBUG] ClickToChat: Ignoring duplicate startChatMode call for " + player.getName() + " (cooldown active)");
            }
            return;
        }
        
        // Update cooldown timestamp
        lastChatStartTime.put(uuid, currentTime);
        
        // Debug logging
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info("[DEBUG] ClickToChat: startChatMode called for " + player.getName() + " with NPC '" + npcName + "'");
        }
        
        // Clear any existing pending chat
        pendingChats.remove(uuid);
        
        // Set up new pending chat
        PendingChat pending = new PendingChat(npcName, System.currentTimeMillis());
        pendingChats.put(uuid, pending);
        
        // Notify player with configurable colors (NPC-specific or global defaults)
        DirectNpc npc = getNPCFromName(npcName);
        String npcNameColor = npc != null && npc.getNpcNameColor() != null ? 
            npc.getNpcNameColor() : plugin.getConfig().getString("click-to-chat.colors.npc-name", "&a");
        String instructionColor = npc != null && npc.getInstructionColor() != null ? 
            npc.getInstructionColor() : plugin.getConfig().getString("click-to-chat.colors.instruction", "&e");
        String cancelColor = npc != null && npc.getCancelColor() != null ? 
            npc.getCancelColor() : plugin.getConfig().getString("click-to-chat.colors.cancel", "&7");
        
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', npcNameColor + "[" + npcName + "] " + instructionColor + 
                "Type your message in chat to talk to me!"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', cancelColor + "(type 'cancel' or wait to exit chat mode)"));
        
        // Schedule timeout
        scheduleTimeout(uuid, npcName);
    }

    /**
     * Start chat mode for a player with an NPC (internal method that bypasses cooldown)
     */
    private void startChatModeInternal(Player player, String npcName) {
        UUID uuid = player.getUniqueId();

        // Update cooldown timestamp (still update it for future checks)
        long currentTime = System.currentTimeMillis();
        lastChatStartTime.put(uuid, currentTime);

        // Debug logging
        if (plugin.getConfig().getBoolean("debug-mode", false)) {
            plugin.getLogger().info("[DEBUG] ClickToChat: startChatModeInternal called for " + player.getName() + " with NPC '" + npcName + "'");
        }

        // Clear any existing pending chat
        pendingChats.remove(uuid);

        // Set up new pending chat
        PendingChat pending = new PendingChat(npcName, System.currentTimeMillis());
        pendingChats.put(uuid, pending);

        // Notify player with configurable colors (NPC-specific or global defaults)
        DirectNpc npc = getNPCFromName(npcName);
        String npcNameColor = npc != null && npc.getNpcNameColor() != null ?
            npc.getNpcNameColor() : plugin.getConfig().getString("click-to-chat.colors.npc-name", "&a");
        String instructionColor = npc != null && npc.getInstructionColor() != null ?
            npc.getInstructionColor() : plugin.getConfig().getString("click-to-chat.colors.instruction", "&e");
        String cancelColor = npc != null && npc.getCancelColor() != null ?
            npc.getCancelColor() : plugin.getConfig().getString("click-to-chat.colors.cancel", "&7");

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', npcNameColor + "[" + npcName + "] " + instructionColor +
                "Type your message in chat to talk to me!"));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', cancelColor + "(type 'cancel' or wait to exit chat mode)"));

        // Schedule timeout
        scheduleTimeout(uuid, npcName);
    }
    
    /**
     * Schedule or reschedule a timeout task for a player's chat session
     */
    private void scheduleTimeout(UUID uuid, String npcName) {
        // Cancel any existing timeout task
        Integer existingTaskId = timeoutTasks.get(uuid);
        if (existingTaskId != null) {
            Bukkit.getScheduler().cancelTask(existingTaskId);
        }
        
        // Schedule new timeout task
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingChat current = pendingChats.get(uuid);
            if (current != null && current.npcName.equals(npcName)) {
                pendingChats.remove(uuid);
                timeoutTasks.remove(uuid);
                lastChatStartTime.remove(uuid); // Clean up cooldown
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.sendMessage(ChatColor.GRAY + "[Chat mode with " + npcName + " expired]");
                }
            }
        }, chatTimeoutSeconds * 20L).getTaskId();
        
        timeoutTasks.put(uuid, taskId);
    }
    
    /**
     * Handle chat message - check if it's for an NPC
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!enabled) return;
        
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        PendingChat pending = pendingChats.get(uuid);
        if (pending == null) {
            return; // Not in chat mode
        }
        
        String message = event.getMessage();
        
        // Check for cancel
        if (message.equalsIgnoreCase("cancel")) {
            event.setCancelled(true);
            pendingChats.remove(uuid);
            // Cancel timeout task
            Integer taskId = timeoutTasks.remove(uuid);
            if (taskId != null) {
                Bukkit.getScheduler().cancelTask(taskId);
            }
            lastChatStartTime.remove(uuid); // Clean up cooldown
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(ChatColor.GRAY + "[Chat mode cancelled]");
            });
            return;
        }
        
        // Cancel the normal chat message
        event.setCancelled(true);
        
        // Process the NPC chat
        if (chatCallback != null) {
            ChatRequest request = new ChatRequest(pending.npcName, message);
            Bukkit.getScheduler().runTask(plugin, () -> {
                chatCallback.accept(player, request);
            });
        }
        
        // Keep chat mode active for next message (don't remove pending chat)
        // Reschedule timeout since activity occurred
        scheduleTimeout(uuid, pending.npcName);
    }
    
    /**
     * Clean up when player leaves
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingChats.remove(uuid);
        // Cancel timeout task
        Integer taskId = timeoutTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }
    
    /**
     * Check if a player is in chat mode
     */
    public boolean isInChatMode(UUID playerUuid) {
        return pendingChats.containsKey(playerUuid);
    }
    
    /**
     * Get the NPC a player is chatting with
     */
    public String getChatNpc(UUID playerUuid) {
        PendingChat pending = pendingChats.get(playerUuid);
        return pending != null ? pending.npcName : null;
    }
    
    /**
     * Cancel chat mode for a player
     */
    public void cancelChatMode(UUID playerUuid) {
        pendingChats.remove(playerUuid);
    }
    
    /**
     * Pending chat state
     */
    private static class PendingChat {
        final String npcName;
        final long timestamp;
        
        PendingChat(String npcName, long timestamp) {
            this.npcName = npcName;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Check if click-to-chat is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Chat request from click-to-chat
     */
    public static class ChatRequest {
        public final String npcName;
        public final String message;
        
        public ChatRequest(String npcName, String message) {
            this.npcName = npcName;
            this.message = message;
        }
    }
}
