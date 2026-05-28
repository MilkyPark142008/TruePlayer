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
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class FakePlayerManager {

    private final TruePlayerPlugin plugin;
    private static final Pattern VALID_PLAYER_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    private final Map<String, ServerPlayer> fakePlayers = new LinkedHashMap<>();
    private final Map<String, BukkitTask> repeatingActions = new LinkedHashMap<>();

    public FakePlayerManager(TruePlayerPlugin plugin) {
        this.plugin = plugin;
    }

    public SpawnResult spawnFakePlayerResult(String name, Location location) {
        int maxNameLength = plugin.getConfig().getInt("fake-player.max-name-length", 16);
        if (name == null || name.isBlank()) return SpawnResult.INVALID_NAME;
        if (name.length() > maxNameLength || name.length() > 16 || !VALID_PLAYER_NAME.matcher(name).matches()) {
            return SpawnResult.INVALID_NAME;
        }
        if (location.getWorld() == null) return SpawnResult.INVALID_LOCATION;

        String key = name.toLowerCase(Locale.ROOT);
        boolean allowDuplicate = plugin.getConfig().getBoolean("fake-player.allow-duplicate-name", false);
        if (!allowDuplicate && fakePlayers.containsKey(key)) return SpawnResult.ALREADY_EXISTS;

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
            return SpawnResult.SUCCESS;
        } catch (Throwable throwable) {
            plugin.getLogger().severe("Failed to spawn fake player: " + throwable.getClass().getName() + ": " + throwable.getMessage());
            throwable.printStackTrace();
            return SpawnResult.NMS_ERROR;
        }
    }

    public boolean spawnFakePlayer(String name, Location location) {
        return spawnFakePlayerResult(name, location) == SpawnResult.SUCCESS;
    }

    public enum SpawnResult {
        SUCCESS,
        INVALID_NAME,
        INVALID_LOCATION,
        ALREADY_EXISTS,
        NMS_ERROR
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
            cancelRepeatingActions(name);
            fakePlayers.remove(name.toLowerCase(Locale.ROOT));
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
        repeatingActions.values().forEach(BukkitTask::cancel);
        repeatingActions.clear();
        fakePlayers.clear();
    }

    public boolean teleportFakePlayer(String name, Location location) {
        ServerPlayer fakePlayer = getFakePlayer(name);
        if (fakePlayer == null || location.getWorld() == null) return false;
        fakePlayer.teleportTo(
                ((CraftWorld) location.getWorld()).getHandle(),
                location.getX(), location.getY(), location.getZ(),
                Collections.<Relative>emptySet(),
                location.getYaw(), location.getPitch(),
                false,
                TeleportCause.PLUGIN
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

    public boolean startRepeatingUse(String name, long intervalTicks) {
        return startRepeatingAction(name, "use", intervalTicks, () -> use(name));
    }

    public boolean startRepeatingAttack(String name, long intervalTicks) {
        return startRepeatingAction(name, "attack", intervalTicks, () -> attack(name));
    }

    private boolean startRepeatingAction(String name, String action, long intervalTicks, Runnable runnable) {
        if (getFakePlayer(name) == null || intervalTicks <= 0) return false;
        String taskKey = repeatingActionKey(name, action);
        BukkitTask previousTask = repeatingActions.remove(taskKey);
        if (previousTask != null) {
            previousTask.cancel();
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (getFakePlayer(name) == null) {
                BukkitTask currentTask = repeatingActions.remove(taskKey);
                if (currentTask != null) {
                    currentTask.cancel();
                }
                return;
            }
            runnable.run();
        }, 0L, intervalTicks);
        repeatingActions.put(taskKey, task);
        return true;
    }

    private void cancelRepeatingActions(String name) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        List<String> keysToRemove = repeatingActions.keySet().stream()
                .filter(key -> key.startsWith(normalizedName + ":"))
                .toList();
        for (String key : keysToRemove) {
            BukkitTask task = repeatingActions.remove(key);
            if (task != null) {
                task.cancel();
            }
        }
    }

    private String repeatingActionKey(String name, String action) {
        return name.toLowerCase(Locale.ROOT) + ":" + action.toLowerCase(Locale.ROOT);
    }

    public List<String> getChunkInfo(String name) {
        ServerPlayer fakePlayer = getFakePlayer(name);
        if (fakePlayer == null) {
            return List.of("§c找不到假人：§e" + name);
        }

        org.bukkit.entity.Player bukkitPlayer = fakePlayer.getBukkitEntity();
        Location location = bukkitPlayer.getLocation();
        if (location.getWorld() == null) {
            return List.of("§c假人所在世界无效：§e" + name);
        }

        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        boolean inBukkitOnlinePlayers = Bukkit.getOnlinePlayers().stream()
                .anyMatch(player -> player.getUniqueId().equals(bukkitPlayer.getUniqueId()));
        boolean inServerPlayerList = MinecraftServer.getServer().getPlayerList().getPlayers().contains(fakePlayer);
        boolean currentChunkLoaded = location.getWorld().isChunkLoaded(chunkX, chunkZ);

        int radius = 2;
        int loaded = 0;
        int total = 0;
        for (int x = chunkX - radius; x <= chunkX + radius; x++) {
            for (int z = chunkZ - radius; z <= chunkZ + radius; z++) {
                total++;
                if (location.getWorld().isChunkLoaded(x, z)) {
                    loaded++;
                }
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("§6TruePlayer 区块调试：§e" + fakePlayer.getName());
        lines.add("§7世界：§f" + location.getWorld().getName());
        lines.add(String.format(Locale.ROOT, "§7坐标：§f%.2f %.2f %.2f", location.getX(), location.getY(), location.getZ()));
        lines.add("§7区块：§f" + chunkX + " " + chunkZ);
        lines.add("§7Bukkit 在线玩家列表：" + (inBukkitOnlinePlayers ? "§a是" : "§c否"));
        lines.add("§7Server PlayerList：" + (inServerPlayerList ? "§a是" : "§c否"));
        lines.add("§7连接对象：" + (fakePlayer.connection != null ? "§a存在" : "§c不存在"));
        lines.add("§7游戏模式：§f" + bukkitPlayer.getGameMode().name().toLowerCase(Locale.ROOT));
        lines.add("§7当前区块已加载：" + (currentChunkLoaded ? "§a是" : "§c否"));
        lines.add("§7周围 " + radius + " 格区块已加载：§f" + loaded + "/" + total);
        lines.add("§8说明：此命令只读取服务器当前加载状态，不会添加 plugin chunk ticket。");
        return lines;
    }

    public List<String> getFakePlayerNames() {
        return new ArrayList<>(fakePlayers.keySet());
    }

    private ServerPlayer getFakePlayer(String name) {
        if (name == null) return null;
        return fakePlayers.get(name.toLowerCase());
    }
}
