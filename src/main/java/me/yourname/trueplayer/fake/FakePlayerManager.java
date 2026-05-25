package me.yourname.trueplayer.fake;

import com.mojang.authlib.GameProfile;
import me.yourname.trueplayer.TruePlayerPlugin;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.util.RayTraceResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FakePlayerManager {

    private final TruePlayerPlugin plugin;
    private final Map<String, ServerPlayer> fakePlayers = new LinkedHashMap<>();

    public FakePlayerManager(TruePlayerPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean spawnFakePlayer(String name, Location location) {
        int maxNameLength = plugin.getConfig().getInt("fake-player.max-name-length", 16);
        if (name == null || name.isBlank() || name.length() > maxNameLength) return false;
        if (location.getWorld() == null) return false;

        String key = name.toLowerCase();
        boolean allowDuplicate = plugin.getConfig().getBoolean("fake-player.allow-duplicate-name", false);
        if (!allowDuplicate && fakePlayers.containsKey(key)) return false;

        try {
            MinecraftServer server = MinecraftServer.getServer();
            ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();
            GameProfile profile = new GameProfile(
                    UUID.nameUUIDFromBytes(("TruePlayer:" + name).getBytes(StandardCharsets.UTF_8)),
                    name
            );

            ServerPlayer fakePlayer = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
            fakePlayer.setPos(location.getX(), location.getY(), location.getZ());
            fakePlayer.setYRot(location.getYaw());
            fakePlayer.setXRot(location.getPitch());

            EmptyConnection connection = new EmptyConnection();
            CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
            fakePlayer.connection = new ServerGamePacketListenerImpl(server, connection, fakePlayer, cookie);

            server.getPlayerList().placeNewPlayer(connection, fakePlayer, cookie);
            applyDefaultGameMode(fakePlayer);

            fakePlayers.put(key, fakePlayer);

            if (plugin.getConfig().getBoolean("fake-player.spawn-message", true)) {
                Bukkit.broadcastMessage("§7[§bTruePlayer§7] §a假人 §e" + name + " §a已生成。");
            }
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().severe("Failed to spawn fake player: " + throwable.getClass().getName() + ": " + throwable.getMessage());
            throwable.printStackTrace();
            return false;
        }
    }

    private void applyDefaultGameMode(ServerPlayer fakePlayer) {
        String mode = plugin.getConfig().getString("fake-player.default-game-mode", "survival");
        GameType gameType = switch (mode == null ? "survival" : mode.toLowerCase()) {
            case "creative" -> GameType.CREATIVE;
            case "adventure" -> GameType.ADVENTURE;
            case "spectator" -> GameType.SPECTATOR;
            default -> GameType.SURVIVAL;
        };
        fakePlayer.setGameMode(gameType);
    }

    public boolean removeFakePlayer(String name) {
        ServerPlayer fakePlayer = getFakePlayer(name);
        if (fakePlayer == null) return false;

        try {
            fakePlayers.remove(name.toLowerCase());
            fakePlayer.connection.disconnect(Component.literal("Fake player removed"));
            MinecraftServer.getServer().getPlayerList().remove(fakePlayer);
            fakePlayer.remove(Entity.RemovalReason.DISCARDED);
            return true;
        } catch (Throwable throwable) {
            plugin.getLogger().severe("Failed to remove fake player: " + throwable.getMessage());
            throwable.printStackTrace();
            return false;
        }
    }

    public void removeAll() {
        Collection<String> names = new ArrayList<>(fakePlayers.keySet());
        for (String name : names) removeFakePlayer(name);
        fakePlayers.clear();
    }

    public boolean teleportFakePlayer(String name, Location location) {
        ServerPlayer fakePlayer = getFakePlayer(name);
        if (fakePlayer == null || location.getWorld() == null) return false;
        fakePlayer.teleportTo(
                ((CraftWorld) location.getWorld()).getHandle(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()
        );
        return true;
    }

    public boolean lookFakePlayer(String name, float yaw, float pitch) {
        ServerPlayer fakePlayer = getFakePlayer(name);
        if (fakePlayer == null) return false;
        fakePlayer.setYRot(yaw);
        fakePlayer.setXRot(pitch);
        fakePlayer.yHeadRot = yaw;
        fakePlayer.yBodyRot = yaw;
        return true;
    }

    public boolean setSneaking(String name, boolean sneaking) {
        ServerPlayer fakePlayer = getFakePlayer(name);
        if (fakePlayer == null) return false;
        fakePlayer.setShiftKeyDown(sneaking);
        return true;
    }

    public boolean jump(String name) {
        ServerPlayer fakePlayer = getFakePlayer(name);
        if (fakePlayer == null) return false;
        fakePlayer.jumpFromGround();
        return true;
    }

    public boolean attack(String name) {
        ServerPlayer fakePlayer = getFakePlayer(name);
        if (fakePlayer == null) return false;

        org.bukkit.entity.Player bukkitPlayer = fakePlayer.getBukkitEntity();
        RayTraceResult result = bukkitPlayer.getWorld().rayTraceEntities(
                bukkitPlayer.getEyeLocation(),
                bukkitPlayer.getEyeLocation().getDirection(),
                4.5,
                entity -> !entity.getUniqueId().equals(bukkitPlayer.getUniqueId())
        );
        if (result == null || result.getHitEntity() == null) return false;

        Entity target = ((CraftEntity) result.getHitEntity()).getHandle();
        fakePlayer.attack(target);
        fakePlayer.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    public boolean use(String name) {
        ServerPlayer fakePlayer = getFakePlayer(name);
        if (fakePlayer == null) return false;
        // 基础版先只做挥手动作；完整右键方块/实体后续需要模拟 ServerboundUseItem/UseItemOn 逻辑。
        fakePlayer.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    public List<String> getFakePlayerNames() {
        return new ArrayList<>(fakePlayers.keySet());
    }

    private ServerPlayer getFakePlayer(String name) {
        if (name == null) return null;
        return fakePlayers.get(name.toLowerCase());
    }
}
