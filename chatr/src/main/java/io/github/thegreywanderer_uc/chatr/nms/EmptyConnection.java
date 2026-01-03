package io.github.thegreywanderer_uc.chatr.nms;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import java.net.SocketAddress;

/**
 * Empty connection for NPC entities that don't need network functionality.
 * Modeled after Citizens2's EmptyConnection.
 */
public class EmptyConnection extends Connection {
    
    public EmptyConnection(PacketFlow packetFlow) {
        super(packetFlow);
        // Set up a dummy channel
        this.channel = new EmptyChannel(null);
        this.address = new SocketAddress() {
            private static final long serialVersionUID = 1L;
        };
    }

    @Override
    public void flushChannel() {
        // No-op
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void send(Packet<?> packet) {
        // No-op - NPCs don't send packets
    }

    // Note: Other send overloads removed as they may not exist in all versions
    // The base Connection class handles fallback behavior
}
