package io.github.thegreywanderer_uc.chatr.nms;

import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Empty packet listener for NPC entities.
 * NPCs don't need to process or send packets since they're not real players.
 * Modeled after Citizens2's EmptyPacketListener.
 */
public class EmptyPacketListener extends ServerGamePacketListenerImpl {
    
    public EmptyPacketListener(MinecraftServer server, Connection connection, 
                                ServerPlayer player, CommonListenerCookie cookie) {
        super(server, connection, player, cookie);
    }

    @Override
    public void resumeFlushing() {
        // No-op for NPCs
    }

    @Override
    public void send(Packet<?> packet) {
        // No-op - NPCs don't send packets to clients
    }
}
