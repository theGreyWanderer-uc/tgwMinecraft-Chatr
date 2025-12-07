package io.github.thegreywanderer_uc.chatr;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.EntityType as MinecraftEntityType;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.craftbukkit.v1_21_R6.CraftServer;
import org.bukkit.craftbukkit.v1_21_R6.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R6.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import io.papermc.paper.profile.ProfileResolver;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class Chatr extends JavaPlugin implements CommandExecutor, Listener {
    private FileConfiguration config; // To store config values
    private final Map<String, ServerPlayer> npcs = new HashMap<>(); // Store NPCs by name
    private final Map<String, ArmorStand> npcEntities = new HashMap<>(); // Store real entities for interaction
    private final Map<String, String> npcWorlds = new HashMap<>(); // Store NPC world names
    private final Map<String, String> npcSkins = new HashMap<>(); // Store NPC skin player names
    private FileConfiguration npcConfig;
    private File npcFile;
    private static final EntityDataAccessor<Byte> SKIN_LAYERS = new EntityDataAccessor<>(17, EntityDataSerializers.BYTE);

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        config = getConfig(); // Load the config
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
        getCommand("chatr").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this); // Register listener for joins
        String enableMsg = config.getString("enable-message", "Chatr plugin has been enabled!");
        // Log with ANSI colors on the message only (no extra prefix)
        getLogger().info(toAnsi(enableMsg) + "\u001B[m");
    }

    @Override
    public void onDisable() {
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
        npcWorlds.clear();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
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
            return true;
        }
        if (args[0].equalsIgnoreCase("version")) {
            String version = getDescription().getVersion();
            sender.sendMessage(coloredPrefix + "Chatr version: " + version);
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("chatr.reload")) {
                sender.sendMessage(coloredPrefix + "You don't have permission to reload.");
                return true;
            }
            reloadConfig(); // Reload from file
            config = getConfig();
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
            if (npcs.containsKey(npcName)) {
                sender.sendMessage(coloredPrefix + "An NPC with name '" + npcName + "' already exists.");
                return true;
            }
            Player player = (Player) sender;
            Location loc = player.getLocation().add(player.getLocation().getDirection().normalize());
            UUID uuid = UUID.randomUUID();
            createNpc(npcName, loc, uuid);
            sender.sendMessage(coloredPrefix + "NPC '" + npcName + "' created one block in front.");
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
            ServerPlayer npc = npcs.remove(npcName);
            if (npc == null) {
                sender.sendMessage(coloredPrefix + "No NPC found with name '" + npcName + "'.");
                return true;
            }
            npcWorlds.remove(npcName);
            npcSkins.remove(npcName);
            removeNpc(npc);
            saveNpcs();
            sender.sendMessage(coloredPrefix + "NPC '" + npcName + "' removed.");
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
            ServerPlayer npc = npcs.get(npcName);
            if (npc == null) {
                sender.sendMessage(coloredPrefix + "No NPC found with name '" + npcName + "'.");
                return true;
            }
            setNpcSkin(npcName, playerName);
            sender.sendMessage(coloredPrefix + "Skin for NPC '" + npcName + "' set to '" + playerName + "'.");
            return true;
        }
        sender.sendMessage(coloredPrefix + "Unknown subcommand. Usage: " + command.getUsage());
        return false;
    }

    private void createNpc(String name, Location loc, UUID uuid) {
        // Spawn real ArmorStand for interaction
        ArmorStand armorStand = (ArmorStand) loc.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.ARMOR_STAND);
        armorStand.setVisible(false); // Make it invisible
        armorStand.setGravity(false); // Don't fall
        armorStand.setInvulnerable(true); // Can't be damaged
        armorStand.setCustomName(name); // Set name for identification
        armorStand.setCustomNameVisible(false); // Don't show name tag
        armorStand.setSmall(true); // Make it smaller
        armorStand.setMarker(true); // Remove hitbox for better interaction

        // Create ServerPlayer for visual packets
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel world = ((CraftWorld) loc.getWorld()).getHandle();
        GameProfile profile = new GameProfile(uuid, name);
        ServerPlayer npc = new ServerPlayer(server, world, profile, ClientInformation.createDefault());
        npc.setPos(loc.getX(), loc.getY(), loc.getZ());
        npc.setXRot(loc.getPitch());
        npc.setYRot(loc.getYaw());
        npc.getEntityData().set(SKIN_LAYERS, (byte) 127); // Show all skin layers

        npcs.put(name, npc);
        npcEntities.put(name, armorStand);
        npcWorlds.put(name, loc.getWorld().getName());
        npcSkins.put(name, "");

        // Send visual packets to all online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            sendSpawnPackets(p, npc);
        }

        // Schedule removal from tablist
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ServerGamePacketListenerImpl conn = ((CraftPlayer) p).getHandle().connection;
                conn.send(new ClientboundPlayerInfoRemovePacket(List.of(npc.getUUID())));
            }
        }, 40);

        saveNpcs();
    }

    private void removeNpc(ServerPlayer npc) {
        // Find the NPC name
        String npcName = null;
        for (Map.Entry<String, ServerPlayer> entry : npcs.entrySet()) {
            if (entry.getValue().equals(npc)) {
                npcName = entry.getKey();
                break;
            }
        }

        if (npcName != null) {
            // Remove the ArmorStand
            ArmorStand armorStand = npcEntities.get(npcName);
            if (armorStand != null && !armorStand.isDead()) {
                armorStand.remove();
            }
            npcEntities.remove(npcName);
        }

        // Send visual remove packets to all online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            ServerGamePacketListenerImpl conn = ((CraftPlayer) p).getHandle().connection;
            conn.send(new ClientboundPlayerInfoRemovePacket(List.of(npc.getUUID())));
            conn.send(new ClientboundRemoveEntitiesPacket(npc.getId()));
        }
    }

    private void setNpcSkin(String npcName, String playerName) {
        ServerPlayer npc = npcs.get(npcName);
        if (npc == null) return;
        ProfileResolver.resolveProfile(playerName).thenAccept(profile -> {
            if (profile != null) {
                npc.getGameProfile().getProperties().clear();
                npc.getGameProfile().getProperties().putAll(profile.getProperties());
                // Send update to all players
                for (Player p : Bukkit.getOnlinePlayers()) {
                    ServerGamePacketListenerImpl conn = ((CraftPlayer) p).getHandle().connection;
                    conn.send(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, npc));
                }
                npcSkins.put(npcName, playerName);
                saveNpcs();
            }
        });
    }

    private void loadNpcs() {
        for (String key : npcConfig.getKeys(false)) {
            ConfigurationSection section = npcConfig.getConfigurationSection(key);
            if (section != null) {
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
                    createNpc(key, loc, uuid);
                    if (skin != null && !skin.isEmpty()) {
                        setNpcSkin(key, skin);
                    }
                } else {
                    getLogger().warning("World '" + worldName + "' not found for NPC '" + key + "'");
                }
            }
        }
    }

    private void saveNpcs() {
        // Clear the config first
        for (String key : npcConfig.getKeys(false)) {
            npcConfig.set(key, null);
        }
        for (Map.Entry<String, ServerPlayer> entry : npcs.entrySet()) {
            String name = entry.getKey();
            ServerPlayer npc = entry.getValue();
            ConfigurationSection section = npcConfig.createSection(name);
            section.set("world", npcWorlds.get(name));
            section.set("x", npc.getX());
            section.set("y", npc.getY());
            section.set("z", npc.getZ());
            section.set("yaw", npc.getYRot());
            section.set("pitch", npc.getXRot());
            section.set("uuid", npc.getUUID().toString());
            section.set("skin", npcSkins.get(name));
        }
        try {
            npcConfig.save(npcFile);
        } catch (IOException e) {
            getLogger().severe("Could not save npcs.yml: " + e.getMessage());
        }
    }

    private void sendSpawnPackets(Player player, ServerPlayer npc) {
        ServerGamePacketListenerImpl conn = ((CraftPlayer) player).getHandle().connection;
        // conn.send(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, npc));
        conn.send(new ClientboundAddEntityPacket(npc.getId(), npc.getUUID(), npc.getX(), npc.getY(), npc.getZ(), npc.getYRot(), npc.getXRot(), MinecraftEntityType.PLAYER, 0, npc.getDeltaMovement(), npc.getYRot()));
        conn.send(new ClientboundSetEntityDataPacket(npc.getId(), npc.getEntityData().getNonDefaultValues()));
        conn.send(new ClientboundRotateHeadPacket(npc, (byte) (npc.getYRot() * 256 / 360)));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (ServerPlayer npc : npcs.values()) {
            sendSpawnPackets(player, npc);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ArmorStand) {
            ArmorStand armorStand = (ArmorStand) event.getRightClicked();
            // Check if this ArmorStand is one of our NPCs
            String npcName = null;
            for (Map.Entry<String, ArmorStand> entry : npcEntities.entrySet()) {
                if (entry.getValue().equals(armorStand)) {
                    npcName = entry.getKey();
                    break;
                }
            }
            if (npcName != null) {
                // Handle NPC interaction
                Player player = event.getPlayer();
                player.sendMessage(ChatColor.GREEN + "[NPC] " + ChatColor.YELLOW + npcName + ChatColor.WHITE + ": Hello! I'm " + npcName + ".");
                event.setCancelled(true); // Prevent default interaction
            }
        }
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
}