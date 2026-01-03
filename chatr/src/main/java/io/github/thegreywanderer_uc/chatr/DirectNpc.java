package io.github.thegreywanderer_uc.chatr;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.github.thegreywanderer_uc.chatr.nms.EmptyConnection;
import io.github.thegreywanderer_uc.chatr.nms.EmptyPacketListener;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.List;
import java.util.UUID;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.lang.reflect.Field;

/**
 * NPC implementation using fake player entities (like Citizens2 HumanController).
 * Uses NMS directly via paperweight-userdev for human-like appearance and interaction.
 */
public class DirectNpc {

    private final UUID uuid;
    private final String name;
    private Location location;
    private ServerPlayer nmsPlayer;
    private CraftPlayer bukkitPlayer;
    private boolean spawned = false;
    private final Gson gson = new Gson();

    // Continuous tracking task
    private org.bukkit.scheduler.BukkitTask trackingTask;

    // Skin management (like Citizens2 SkinPacketTracker)
    private String skinPlayerName = "Steve"; // Default skin

    // Color configuration for interaction messages
    private String npcNameColor = null;     // null = use global default
    private String instructionColor = null; // null = use global default
    private String cancelColor = null;      // null = use global default

    public DirectNpc(String name, UUID uuid, Location location) {
        this.uuid = uuid;
        this.name = name;
        this.location = location.clone();
    }

    /**
     * Spawn the NPC by creating/updating the fake player entity.
     * Like Citizens2's HumanController.createEntity()
     */
    public void spawn(Player player) {
        if (nmsPlayer == null) {
            // Create new fake player entity
            createFakePlayer(player);

            if (nmsPlayer == null) {
                Bukkit.getLogger().severe("Failed to spawn NPC '" + name + "' - fake player creation failed");
                return;
            }

            // Add metadata to identify this as a Chatr NPC
            bukkitPlayer.setMetadata("chatr_npc", new FixedMetadataValue(
                Bukkit.getPluginManager().getPlugin("Chatr"), true));
            bukkitPlayer.setMetadata("chatr_uuid", new FixedMetadataValue(
                Bukkit.getPluginManager().getPlugin("Chatr"), uuid.toString()));

            // Configure the fake player as an NPC
            configureAsNpc();
        }

        // Make NPC visible to all online players
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            sendSpawnPackets(onlinePlayer);
        }

        // Start continuous tracking of closest player within 3 blocks
        startTrackingTask();

        spawned = true;
    }

    /**
     * Start continuous tracking of the closest player within 3 blocks.
     * Similar to Citizens2's LookClose trait.
     */
    private void startTrackingTask() {
        if (trackingTask != null) {
            trackingTask.cancel();
        }

        trackingTask = Bukkit.getScheduler().runTaskTimer(
            Bukkit.getPluginManager().getPlugin("Chatr"),
            this::trackClosestPlayer,
            1L, // Start after 1 tick
            1L  // Run every tick
        );
    }

    /**
     * Track and face the closest player within 3 blocks.
     * Uses Citizens2-style rotation calculation.
     */
    private void trackClosestPlayer() {
        if (!spawned || nmsPlayer == null) {
            return;
        }

        // Find closest player within 3 blocks
        Player closestPlayer = null;
        double closestDistance = 3.0 * 3.0; // 3 blocks squared

        Location npcEyeLocation = new Location(location.getWorld(), location.getX(), location.getY() + 1.62, location.getZ());

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(location.getWorld())) {
                continue; // Different world
            }

            double distanceSquared = npcEyeLocation.distanceSquared(player.getEyeLocation());
            if (distanceSquared <= closestDistance) {
                closestPlayer = player;
                closestDistance = distanceSquared;
            }
        }

        if (closestPlayer != null) {
            // Use Citizens2's rotation calculation to face the player
            Location target = closestPlayer.getEyeLocation();

            // Citizens2's exact yaw calculation from RotationTrait.rotateToFace()
            float yaw = (float) Math.toDegrees(Math.atan2(target.getZ() - npcEyeLocation.getZ(),
                                                         target.getX() - npcEyeLocation.getX())) - 90.0F;

            // Calculate pitch (but keep head level as requested)
            double dx = target.getX() - npcEyeLocation.getX();
            double dy = target.getY() - npcEyeLocation.getY();
            double dz = target.getZ() - npcEyeLocation.getZ();
            double distanceXZ = Math.sqrt(dx * dx + dz * dz);
            float pitch = distanceXZ == 0 ? 0 : (float) -Math.toDegrees(Math.atan2(dy, distanceXZ));

            // Update rotation
            nmsPlayer.setYRot(yaw);
            nmsPlayer.setYHeadRot(yaw);
            nmsPlayer.setXRot(0.0f); // Keep head level

            // Send rotation update to all nearby players
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.getLocation().distanceSquared(location) <= 64 * 64) { // Within view distance
                    sendRotationUpdate(onlinePlayer);
                }
            }
        }
    }

    /**
     * Create a fake player entity using direct NMS access (paperweight-userdev).
     */
    private void createFakePlayer(Player creator) {
        try {
            // Get NMS world
            ServerLevel nmsWorld = ((CraftWorld) location.getWorld()).getHandle();

            // Create GameProfile with NPC name
            GameProfile profile = new GameProfile(uuid, name);
            
            // Apply skin if one is set
            if (skinPlayerName != null && !skinPlayerName.equals("Steve")) {
                SkinData skinData = fetchSkinData(skinPlayerName);
                if (skinData != null) {
                    // Create a new profile with skin data
                    profile = createProfileWithSkin(profile, skinData);
                    if (profile != null) {
                        Bukkit.getLogger().info("[Chatr] Applied initial skin for NPC '" + name + "' during creation");
                    } else {
                        Bukkit.getLogger().warning("[Chatr] Failed to create profile with initial skin for NPC '" + name + "'");
                    }
                } else {
                    Bukkit.getLogger().warning("[Chatr] Failed to fetch initial skin data for NPC '" + name + "' with player '" + skinPlayerName + "'");
                }
            }

            // Get MinecraftServer instance
            MinecraftServer server = MinecraftServer.getServer();

            // Create ClientInformation (required for ServerPlayer)
            ClientInformation clientInfo = ClientInformation.createDefault();

            // Create the ServerPlayer
            nmsPlayer = new ServerPlayer(server, nmsWorld, profile, clientInfo);

            // Set position
            nmsPlayer.setPos(location.getX(), location.getY(), location.getZ());
            
            // Calculate yaw to face the creator (simple initial facing)
            if (creator != null) {
                Location from = location.clone();
                Location target = creator.getLocation();
                
                // Simple yaw calculation to face the creator initially
                double dx = target.getX() - from.getX();
                double dz = target.getZ() - from.getZ();
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                
                nmsPlayer.setYRot(yaw);
                nmsPlayer.setYHeadRot(yaw);
                nmsPlayer.setXRot(0.0f); // Level head initially
            } else {
                nmsPlayer.setYRot(location.getYaw());
                nmsPlayer.setYHeadRot(location.getYaw());
                nmsPlayer.setXRot(0.0f);
            }
            
            nmsPlayer.setXRot(location.getPitch());

            // Create empty connection to prevent NPEs
            EmptyConnection emptyConnection = new EmptyConnection(PacketFlow.CLIENTBOUND);
            
            // Create empty packet listener (the "connection" field)
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
            nmsPlayer.connection = new EmptyPacketListener(server, emptyConnection, nmsPlayer, cookie);

            // Initialize skin layers (required for NPCs to be visible with skins)
            nmsPlayer.getEntityData().set(net.minecraft.world.entity.Avatar.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0xFF);

            // Create CraftPlayer wrapper
            bukkitPlayer = new CraftPlayer((CraftServer) Bukkit.getServer(), nmsPlayer);

            // Add to world's entity list
            // Note: For player entities, we need to handle this specially
            // We don't add to the player list - that would make them show in tab
            nmsWorld.addNewPlayer(nmsPlayer);

            Bukkit.getLogger().info("NPC '" + name + "' created with entity ID: " + nmsPlayer.getId());

        } catch (Exception e) {
            Bukkit.getLogger().severe("Failed to create NPC: " + e.getMessage());
            e.printStackTrace();
            nmsPlayer = null;
            bukkitPlayer = null;
        }
    }

    /**
     * Configure the fake player with NPC properties.
     */
    private void configureAsNpc() {
        if (bukkitPlayer == null) return;

        // Basic NPC properties
        bukkitPlayer.setInvulnerable(true);
        bukkitPlayer.setCollidable(false);
        bukkitPlayer.setSilent(true);
        bukkitPlayer.setCanPickupItems(false);
        bukkitPlayer.setSleepingIgnored(true);

        // Display name
        bukkitPlayer.setCustomName(name);
        bukkitPlayer.setCustomNameVisible(true);
    }

    /**
     * Send spawn packets to a specific player.
     * Uses the static factory method for player info packets.
     */
    private void sendSpawnPackets(Player targetPlayer) {
        if (nmsPlayer == null) return;

        try {
            ServerPlayer targetNms = ((CraftPlayer) targetPlayer).getHandle();
            
            // 1. Send player info update packet (required before spawn)
            // Use the static factory method for initializing players
            ClientboundPlayerInfoUpdatePacket infoPacket = 
                ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(nmsPlayer));
            targetNms.connection.send(infoPacket);

            // 2. Send entity spawn packet (makes the player visible in the world)
            // For player entities, we need to use the correct constructor parameters
            ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(
                nmsPlayer.getId(),
                nmsPlayer.getUUID(),
                nmsPlayer.getX(),
                nmsPlayer.getY(),
                nmsPlayer.getZ(),
                nmsPlayer.getXRot(),
                nmsPlayer.getYRot(),
                nmsPlayer.getType(),
                0, // data
                nmsPlayer.getDeltaMovement(),
                nmsPlayer.getYHeadRot()
            );
            targetNms.connection.send(spawnPacket);

            // 3. The player should now be visible to the target
            // The server handles entity tracking and spawning automatically
            // when we add the player to the world. However, for manual control,
            // we may need to trigger the tracker.

            // 3. Remove from tab list (NPCs shouldn't appear there)
            // Delay slightly so the player renders properly
            Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugin("Chatr"),
                () -> {
                    ClientboundPlayerInfoRemovePacket removeInfoPacket = 
                        new ClientboundPlayerInfoRemovePacket(List.of(nmsPlayer.getUUID()));
                    targetNms.connection.send(removeInfoPacket);
                },
                20L // 1 second delay
            );

        } catch (Exception e) {
            Bukkit.getLogger().severe("Failed to send spawn packets to " + targetPlayer.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Despawn the NPC by removing the entity.
     */
    public void despawn() {
        if (nmsPlayer != null) {
            // Send remove packets to all players
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                sendDespawnPackets(onlinePlayer);
            }

            // Remove from world
            try {
                ServerLevel level = (ServerLevel) nmsPlayer.level();
                level.removePlayerImmediately(nmsPlayer, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            } catch (Exception e) {
                Bukkit.getLogger().warning("Failed to remove NPC from world: " + e.getMessage());
            }
        }
        nmsPlayer = null;
        bukkitPlayer = null;

        // Stop tracking task
        if (trackingTask != null) {
            trackingTask.cancel();
            trackingTask = null;
        }

        spawned = false;
    }

    /**
     * Send despawn packets to a specific player.
     */
    private void sendDespawnPackets(Player targetPlayer) {
        if (nmsPlayer == null) return;

        try {
            ServerPlayer targetNms = ((CraftPlayer) targetPlayer).getHandle();

            // Send remove entities packet
            ClientboundRemoveEntitiesPacket removePacket = 
                new ClientboundRemoveEntitiesPacket(nmsPlayer.getId());
            targetNms.connection.send(removePacket);

            // Also send player info remove
            ClientboundPlayerInfoRemovePacket removeInfoPacket = 
                new ClientboundPlayerInfoRemovePacket(List.of(nmsPlayer.getUUID()));
            targetNms.connection.send(removeInfoPacket);

        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed to send despawn packets: " + e.getMessage());
        }
    }

    /**
     * Send rotation update packets to a specific player.
     */
    private void sendRotationUpdate(Player targetPlayer) {
        if (nmsPlayer == null) return;

        try {
            ServerPlayer targetNms = ((CraftPlayer) targetPlayer).getHandle();

            // Send entity head look packet
            net.minecraft.network.protocol.game.ClientboundRotateHeadPacket headPacket =
                new net.minecraft.network.protocol.game.ClientboundRotateHeadPacket(nmsPlayer, (byte) ((nmsPlayer.getYHeadRot() * 256.0F) / 360.0F));
            targetNms.connection.send(headPacket);

            // Send entity look packet for body rotation
            net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.Rot lookPacket =
                new net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.Rot(
                    nmsPlayer.getId(),
                    (byte) ((nmsPlayer.getYRot() * 256.0F) / 360.0F),
                    (byte) ((nmsPlayer.getXRot() * 256.0F) / 360.0F),
                    nmsPlayer.onGround()
                );
            targetNms.connection.send(lookPacket);

        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed to send rotation update: " + e.getMessage());
        }
    }

    /**
     * Check if the NPC is spawned.
     */
    public boolean isSpawned() {
        return spawned && nmsPlayer != null;
    }

    /**
     * Get the NPC's location.
     */
    public Location getLocation() {
        return location.clone();
    }

    /**
     * Set the NPC's location.
     */
    public void setLocation(Location location) {
        this.location = location.clone();
        if (nmsPlayer != null) {
            nmsPlayer.setPos(location.getX(), location.getY(), location.getZ());
            nmsPlayer.setYRot(location.getYaw());
            nmsPlayer.setXRot(location.getPitch());
            // TODO: Send position update packets to players
        }
    }

    /**
     * Get the NPC's name.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the NPC's UUID.
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Get the underlying entity (for click detection).
     */
    public Entity getEntity() {
        return bukkitPlayer;
    }

    /**
     * Get the Player entity directly.
     */
    public Player getPlayer() {
        return bukkitPlayer;
    }

    /**
     * Get the NMS ServerPlayer.
     */
    public ServerPlayer getNmsPlayer() {
        return nmsPlayer;
    }

    /**
     * Get the entity ID for packet purposes.
     */
    public int getEntityId() {
        return nmsPlayer != null ? nmsPlayer.getId() : -1;
    }

    /**
     * Check if an entity is a Chatr NPC.
     */
    public static boolean isChatrNpc(Entity entity) {
        return entity.hasMetadata("chatr_npc") &&
               entity.getMetadata("chatr_npc").get(0).asBoolean();
    }

    /**
     * Get the NPC UUID from an entity.
     */
    public static String getNpcUuid(Entity entity) {
        if (entity.hasMetadata("chatr_uuid")) {
            return entity.getMetadata("chatr_uuid").get(0).asString();
        }
        return null;
    }

    /**
     * Set the skin player name for the NPC and apply the skin.
     *
     * @param playerName The name of the player whose skin to use
     */
    public void setSkin(String playerName) {
        this.skinPlayerName = playerName;
        
        // Apply the skin to the GameProfile if the NPC is already created
        if (nmsPlayer != null) {
            applySkinToProfile(playerName);
        }
    }
    
    /**
     * Apply a player's skin to the NPC's GameProfile.
     * 
     * @param playerName The name of the player whose skin to use
     */
    private void applySkinToProfile(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return;
        }
        
        try {
            // Fetch skin data from Mojang API
            SkinData skinData = fetchSkinData(playerName);
            if (skinData != null && nmsPlayer != null) {
                Bukkit.getLogger().info("[Chatr] Applying skin for NPC '" + name + "' - Texture value length: " + skinData.value.length() + ", Signature: " + (skinData.signature != null ? "present" : "null"));
                
                // Apply the skin to the GameProfile
                GameProfile profile = nmsPlayer.getGameProfile();
                
                // Create a new GameProfile with updated properties using reflection
                GameProfile newProfile = createProfileWithSkin(profile, skinData);
                
                if (newProfile != null) {
                    // Note: We don't need to update the existing ServerPlayer's profile
                    // since the respawn process will create a new one with the correct profile
                    Bukkit.getLogger().info("[Chatr] Skin applied to GameProfile for NPC '" + name + "'");
                } else {
                    Bukkit.getLogger().warning("[Chatr] Failed to create new GameProfile with skin for NPC '" + name + "'");
                }
                
                // Mark that the skin has been updated
                if (spawned) {
                    // Respawn for all players to see the new skin (delayed like Citizens2)
                    Bukkit.getScheduler().scheduleSyncDelayedTask(
                        Bukkit.getPluginManager().getPlugin("Chatr"), 
                        () -> {
                            despawn();
                            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                                spawn(onlinePlayer);
                            }
                            Bukkit.getLogger().info("[Chatr] Respawned NPC '" + name + "' with new skin");
                        }
                    );
                }
            } else {
                if (skinData == null) {
                    Bukkit.getLogger().warning("[Chatr] Failed to fetch skin data for player '" + playerName + "' - skinData is null");
                } else {
                    Bukkit.getLogger().warning("[Chatr] Cannot apply skin for NPC '" + name + "' - nmsPlayer is null");
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed to apply skin for NPC '" + name + "' with player '" + playerName + "': " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Fetch skin data from Mojang's API.
     * 
     * @param playerName The player name to fetch skin for
     * @return SkinData containing value and signature, or null if failed
     */
    private SkinData fetchSkinData(String playerName) {
        try {
            Bukkit.getLogger().info("[Chatr] Fetching skin data for player '" + playerName + "'");
            
            // First, get the player's UUID
            URL uuidUrl = new URL("https://api.mojang.com/users/profiles/minecraft/" + playerName);
            HttpURLConnection uuidConnection = (HttpURLConnection) uuidUrl.openConnection();
            uuidConnection.setRequestMethod("GET");
            uuidConnection.setConnectTimeout(5000);
            uuidConnection.setReadTimeout(5000);
            
            if (uuidConnection.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(uuidConnection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // Parse UUID from response
                String uuidResponse = response.toString();
                Bukkit.getLogger().info("[Chatr] UUID API response for '" + playerName + "': " + uuidResponse);
                
                // Find the id field - handle both "id":" and "id" : "
                int idStart = uuidResponse.indexOf("\"id\"");
                if (idStart != -1) {
                    idStart = uuidResponse.indexOf(":", idStart) + 1;
                    // Skip whitespace and quotes
                    while (idStart < uuidResponse.length() && (uuidResponse.charAt(idStart) == ' ' || uuidResponse.charAt(idStart) == ':' || uuidResponse.charAt(idStart) == '"')) {
                        idStart++;
                    }
                    int idEnd = uuidResponse.indexOf("\"", idStart);
                    if (idEnd > idStart) {
                        String uuidStr = uuidResponse.substring(idStart, idEnd);
                        // Remove any dashes that might already be there
                        uuidStr = uuidStr.replace("-", "");
                        
                        // Add dashes to UUID
                        String formattedUuid = uuidStr.substring(0, 8) + "-" + uuidStr.substring(8, 12) + "-" + 
                                             uuidStr.substring(12, 16) + "-" + uuidStr.substring(16, 20) + "-" + 
                                             uuidStr.substring(20);
                        
                        Bukkit.getLogger().info("[Chatr] Formatted UUID for '" + playerName + "': " + formattedUuid);
                        
                        try {
                            UUID uuid = UUID.fromString(formattedUuid);
                            Bukkit.getLogger().info("[Chatr] Successfully parsed UUID: " + uuid);
                            
                            // Now get the profile data - use the non-dashed UUID for the API call
                            URL profileUrl = new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidStr + "?unsigned=false");
                            HttpURLConnection profileConnection = (HttpURLConnection) profileUrl.openConnection();
                            profileConnection.setRequestMethod("GET");
                            profileConnection.setConnectTimeout(5000);
                            profileConnection.setReadTimeout(5000);
                            
                            if (profileConnection.getResponseCode() == 200) {
                                BufferedReader profileReader = new BufferedReader(new InputStreamReader(profileConnection.getInputStream()));
                                StringBuilder profileResponse = new StringBuilder();
                                String profileLine;
                                while ((profileLine = profileReader.readLine()) != null) {
                                    profileResponse.append(profileLine);
                                }
                                profileReader.close();
                                
                                // Parse the skin data from the profile response using Gson
                                String profileJson = profileResponse.toString();
                                Bukkit.getLogger().info("[Chatr] Profile API response for '" + playerName + "': " + profileJson.substring(0, Math.min(200, profileJson.length())) + "...");
                                
                                JsonObject profileObj = gson.fromJson(profileJson, JsonObject.class);
                                if (profileObj.has("properties")) {
                                    JsonArray properties = profileObj.getAsJsonArray("properties");
                                    for (JsonElement prop : properties) {
                                        JsonObject property = prop.getAsJsonObject();
                                        if (property.get("name").getAsString().equals("textures")) {
                                            String textureValue = property.get("value").getAsString();
                                            String textureSignature = property.has("signature") ? property.get("signature").getAsString() : null;
                                            
                                            Bukkit.getLogger().info("[Chatr] Successfully fetched skin data for '" + playerName + "' - value length: " + textureValue.length() + ", signature: " + (textureSignature != null ? "present" : "null"));
                                            return new SkinData(textureValue, textureSignature);
                                        }
                                    }
                                } else {
                                    Bukkit.getLogger().warning("[Chatr] No properties found in profile response for '" + playerName + "'");
                                }
                            } else {
                                Bukkit.getLogger().warning("[Chatr] Profile API returned status " + profileConnection.getResponseCode() + " for '" + playerName + "'");
                            }
                            profileConnection.disconnect();
                        } catch (Exception e) {
                            Bukkit.getLogger().warning("[Chatr] Could not parse UUID or fetch profile for '" + playerName + "': " + e.getMessage());
                        }
                    } else {
                        Bukkit.getLogger().warning("[Chatr] Could not find UUID in response for '" + playerName + "'");
                    }
                } else {
                    Bukkit.getLogger().warning("[Chatr] Could not find 'id' field in UUID response for '" + playerName + "'");
                }
            } else {
                Bukkit.getLogger().warning("[Chatr] UUID API returned status " + uuidConnection.getResponseCode() + " for '" + playerName + "'");
            }
            uuidConnection.disconnect();
            
        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed to fetch skin data for player '" + playerName + "': " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Simple data class for skin information.
     */
    private static class SkinData {
        final String value;
        final String signature;
        
        SkinData(String value, String signature) {
            this.value = value;
            this.signature = signature;
        }
    }
    
    /**
     * Create a new GameProfile with the specified skin data.
     */
    private GameProfile createProfileWithSkin(GameProfile oldProfile, SkinData skinData) {
        try {
            // Get the UUID and name from the old profile using reflection
            Field idField = GameProfile.class.getDeclaredField("id");
            idField.setAccessible(true);
            UUID profileId = (UUID) idField.get(oldProfile);
            
            Field nameField = GameProfile.class.getDeclaredField("name");
            nameField.setAccessible(true);
            String profileName = (String) nameField.get(oldProfile);
            
            // Create Property object
            Class<?> propertyClass = Class.forName("com.mojang.authlib.properties.Property");
            java.lang.reflect.Constructor<?> propConstructor = propertyClass.getConstructor(String.class, String.class, String.class);
            Object skinProperty = propConstructor.newInstance("textures", skinData.value, skinData.signature);
            
            // Create mutable Multimap for properties
            Class<?> hashMultimapClass = Class.forName("com.google.common.collect.HashMultimap");
            java.lang.reflect.Method createMethod = hashMultimapClass.getMethod("create");
            Object propertyMultimap = createMethod.invoke(null);
            
            // Add skin property
            java.lang.reflect.Method putMethod = propertyMultimap.getClass().getMethod("put", Object.class, Object.class);
            putMethod.invoke(propertyMultimap, "textures", skinProperty);
            
            // Create PropertyMap from Multimap
            Class<?> propertyMapClass = Class.forName("com.mojang.authlib.properties.PropertyMap");
            java.lang.reflect.Constructor<?> mapConstructor = propertyMapClass.getConstructor(Class.forName("com.google.common.collect.Multimap"));
            Object propertyMap = mapConstructor.newInstance(propertyMultimap);
            
            // Create GameProfile with PropertyMap (3-param constructor)
            java.lang.reflect.Constructor<?> profileConstructor = GameProfile.class.getConstructor(UUID.class, String.class, propertyMapClass);
            GameProfile newProfile = (GameProfile) profileConstructor.newInstance(profileId, profileName, propertyMap);
            
            return newProfile;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[Chatr] Failed to create GameProfile with skin: " + e.getMessage());
            return null;
        }
    }
    
    /**

     * Get the current skin player name.
     */
    public String getSkin() {
        return skinPlayerName;
    }

    // Color configuration methods

    /**
     * Get the color for NPC name in interaction messages.
     * @return color code or null to use global default
     */
    public String getNpcNameColor() {
        return npcNameColor;
    }

    /**
     * Set the color for NPC name in interaction messages.
     * @param color Minecraft color code (e.g., "&a") or null to use global default
     */
    public void setNpcNameColor(String color) {
        this.npcNameColor = color;
    }

    /**
     * Get the color for instruction text in interaction messages.
     * @return color code or null to use global default
     */
    public String getInstructionColor() {
        return instructionColor;
    }

    /**
     * Set the color for instruction text in interaction messages.
     * @param color Minecraft color code (e.g., "&e") or null to use global default
     */
    public void setInstructionColor(String color) {
        this.instructionColor = color;
    }

    /**
     * Get the color for cancel instruction in interaction messages.
     * @return color code or null to use global default
     */
    public String getCancelColor() {
        return cancelColor;
    }

    /**
     * Set the color for cancel instruction in interaction messages.
     * @param color Minecraft color code (e.g., "&7") or null to use global default
     */
    public void setCancelColor(String color) {
        this.cancelColor = color;
    }
}
