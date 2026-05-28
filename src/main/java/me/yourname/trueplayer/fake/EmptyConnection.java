package me.yourname.trueplayer.fake;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

import java.util.function.Consumer;

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
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocolInfo, T packetListener) {
        // Fake player has no Netty channel. Paper 1.21.11 calls this from PlayerList#placeNewPlayer.
    }

    @Override
    public void setupOutboundProtocol(ProtocolInfo<?> protocolInfo) {
        // Fake player has no Netty channel.
    }

    @Override
    public void runOnceConnected(Consumer<Connection> consumer) {
        consumer.accept(this);
    }

    @Override
    public void flushChannel() {
        // Fake player has no Netty channel.
    }

    @Override
    public boolean isMemoryConnection() {
        return true;
    }

    @Override
    public boolean isConnected() {
        return true;
    }
}
