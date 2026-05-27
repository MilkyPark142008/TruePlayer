package me.yourname.trueplayer.fake;

import net.minecraft.network.Connection;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;


public final class EmptyConnection extends Connection {

    public EmptyConnection() {
        super(PacketFlow.SERVERBOUND);
    }

    @Override
    public void send(Packet<?> packet) {
        // Fake player has no real client.
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener callbacks) {
        // Fake player has no real client.
    }

    @Override
    public void send(Packet<?> packet, ChannelFutureListener callbacks, boolean flush) {
        // Fake player has no real client.
    }

    @Override
    public boolean isConnected() {
        return true;
    }
}