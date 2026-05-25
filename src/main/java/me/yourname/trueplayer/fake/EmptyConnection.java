package me.yourname.trueplayer.fake;

import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import javax.annotation.Nullable;

public final class EmptyConnection extends Connection {

    public EmptyConnection() {
        super(PacketFlow.SERVERBOUND);
    }

    @Override
    public void send(Packet<?> packet) {
        // Fake player has no real client.
    }

    @Override
    public void send(Packet<?> packet, @Nullable PacketSendListener callbacks) {
        // Fake player has no real client.
    }

    @Override
    public void send(Packet<?> packet, @Nullable PacketSendListener callbacks, boolean flush) {
        // Fake player has no real client.
    }

    @Override
    public boolean isConnected() {
        return true;
    }
}