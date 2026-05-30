package me.yourname.trueplayer.fake;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import me.yourname.trueplayer.TruePlayerPlugin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public final class FakePlayerManager implements Listener {

    private final TruePlayerPlugin plugin;
    private static final Pattern VALID_PLAYER_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    private final Map<String, ServerPlayer> fakePlayers = new LinkedHashMap<>();
    private final Map<UUID, String> fakePlayerNamesByUuid = new LinkedHashMap<>();
    private final Map<UUID, String> inventoryViewers = new LinkedHashMap<>();
    private final Map<String, BukkitTask> repeatingActions = new LinkedHashMap<>();

    public FakePlayerManager(TruePlayerPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
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
            GameProfile profile = resolveGameProfile(server, name);

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
            fakePlayerNamesByUuid.put(fakePlayer.getUUID(), key);
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


    private GameProfile resolveGameProfile(MinecraftServer server, String name) {
        return server.services().profileResolver().fetchByName(name)
                .or(() -> server.services().nameToIdCache().get(name)
                        .map(nameAndId -> new GameProfile(nameAndId.id(), nameAndId.name())))
                .orElseGet(() -> {
                    NameAndId offlineProfile = NameAndId.createOffline(name);
                    return new GameProfile(offlineProfile.id(), offlineProfile.name());
                });
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
            String key = name.toLowerCase(Locale.ROOT);
            fakePlayers.remove(key);
            fakePlayerNamesByUuid.remove(fakePlayer.getUUID());
            inventoryViewers.values().removeIf(key::equals);
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
        fakePlayerNamesByUuid.clear();
        inventoryViewers.clear();
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

        HitResult hitResult = pick(fakePlayer, 4.5D, true);
        swingHand(fakePlayer, InteractionHand.MAIN_HAND);
        if (!(hitResult instanceof EntityHitResult entityHitResult)) return false;

        Entity target = entityHitResult.getEntity();
        if (target == fakePlayer || !target.isAttackable()) return false;
        fakePlayer.attack(target);
        return true;
    }

    public boolean use(String name) {
        ServerPlayer fakePlayer = getFakePlayer(name);
        if (fakePlayer == null) return false;

        syncInventoryAndHandItems(fakePlayer);
        HitResult hitResult = pick(fakePlayer, fakePlayer.gameMode.isCreative() ? 5.0D : 4.5D, true);
        for (InteractionHand hand : InteractionHand.values()) {
            if (use(fakePlayer, hand, hitResult)) {
                syncInventoryAndHandItems(fakePlayer);
                return true;
            }
        }
        swingHand(fakePlayer, InteractionHand.MAIN_HAND);
        broadcastHandItems(fakePlayer);
        return false;
    }

    private boolean use(ServerPlayer fakePlayer, InteractionHand hand, HitResult hitResult) {
        if (hitResult instanceof BlockHitResult blockHitResult && hitResult.getType() == HitResult.Type.BLOCK) {
            fakePlayer.resetLastActionTime();
            if (blockHitResult.getBlockPos().getY() < fakePlayer.level().getMaxY()
                    && fakePlayer.level().mayInteract(fakePlayer, blockHitResult.getBlockPos())) {
                InteractionResult result = fakePlayer.gameMode.useItemOn(
                        fakePlayer,
                        fakePlayer.level(),
                        fakePlayer.getItemInHand(hand),
                        hand,
                        blockHitResult
                );
                if (result instanceof InteractionResult.Success success) {
                    if (success.swingSource() == InteractionResult.SwingSource.SERVER) {
                        swingHand(fakePlayer, hand);
                    }
                    return true;
                }
            }
        } else if (hitResult instanceof EntityHitResult entityHitResult) {
            fakePlayer.resetLastActionTime();
            Entity target = entityHitResult.getEntity();
            if (target != fakePlayer) {
                // Paper/Mojang 1.21.11 exposes the entity interaction overloads without a hit-position Vec3.
                // Keep Carpet's ordering: let the target handle direct interaction first,
                // then fall back to the player's interactOn path.
                if (target.interact(fakePlayer, hand).consumesAction()
                        || fakePlayer.interactOn(target, hand).consumesAction()) {
                    return true;
                }
            }
        }

        net.minecraft.world.item.ItemStack itemStack = fakePlayer.getItemInHand(hand);
        if (!itemStack.isEmpty()) {
            InteractionResult result = fakePlayer.gameMode.useItem(fakePlayer, fakePlayer.level(), itemStack, hand);
            if (result.consumesAction()) {
                swingHand(fakePlayer, hand);
                return true;
            }
        }
        return false;
    }

    private void swingHand(ServerPlayer fakePlayer, InteractionHand hand) {
        fakePlayer.swing(hand);
        int animation = hand == InteractionHand.OFF_HAND
                ? ClientboundAnimatePacket.SWING_OFF_HAND
                : ClientboundAnimatePacket.SWING_MAIN_HAND;
        fakePlayer.level().getChunkSource().sendToTrackingPlayers(
                fakePlayer,
                new ClientboundAnimatePacket(fakePlayer, animation)
        );
    }

    private void broadcastHandItems(ServerPlayer fakePlayer) {
        fakePlayer.level().getChunkSource().sendToTrackingPlayers(
                fakePlayer,
                new ClientboundSetEquipmentPacket(fakePlayer.getId(), List.of(
                        Pair.of(EquipmentSlot.MAINHAND, fakePlayer.getItemBySlot(EquipmentSlot.MAINHAND).copy()),
                        Pair.of(EquipmentSlot.OFFHAND, fakePlayer.getItemBySlot(EquipmentSlot.OFFHAND).copy())
                ))
        );
    }

    private void syncInventoryAndHandItems(ServerPlayer fakePlayer) {
        int selectedSlot = fakePlayer.getBukkitEntity().getInventory().getHeldItemSlot();
        if (selectedSlot < 0 || selectedSlot > 8) {
            selectedSlot = fakePlayer.getInventory().getSelectedSlot();
        }
        fakePlayer.getInventory().setSelectedSlot(selectedSlot);
        fakePlayer.getBukkitEntity().getInventory().setHeldItemSlot(selectedSlot);
        fakePlayer.containerMenu.broadcastChanges();
        fakePlayer.inventoryMenu.broadcastChanges();
        broadcastHandItems(fakePlayer);
    }

    private void scheduleInventoryHandRefresh(ServerPlayer fakePlayer) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (fakePlayers.containsValue(fakePlayer)) {
                syncInventoryAndHandItems(fakePlayer);
            }
        });
    }

    private HitResult pick(ServerPlayer fakePlayer, double reach, boolean includeEntities) {
        Vec3 eyePosition = fakePlayer.getEyePosition(1.0F);
        Vec3 viewVector = fakePlayer.getViewVector(1.0F);
        Vec3 endPosition = eyePosition.add(viewVector.x * reach, viewVector.y * reach, viewVector.z * reach);
        HitResult blockHitResult = fakePlayer.level().clip(new ClipContext(
                eyePosition,
                endPosition,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                fakePlayer
        ));
        if (!includeEntities) return blockHitResult;

        AABB searchBox = fakePlayer.getBoundingBox().expandTowards(viewVector.scale(reach)).inflate(1.0D);
        double maxDistanceSqr = reach * reach;
        if (blockHitResult.getType() != HitResult.Type.MISS) {
            maxDistanceSqr = eyePosition.distanceToSqr(blockHitResult.getLocation());
        }

        EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
                fakePlayer.level(),
                fakePlayer,
                eyePosition,
                endPosition,
                searchBox,
                EntitySelector.CAN_BE_PICKED.and(entity -> entity != fakePlayer),
                0.0F
        );
        if (entityHitResult == null) return blockHitResult;

        double entityDistanceSqr = eyePosition.distanceToSqr(entityHitResult.getLocation());
        return entityDistanceSqr <= maxDistanceSqr ? entityHitResult : blockHitResult;
    }

    public boolean selectHotbarSlot(String name, int slot) {
        ServerPlayer fakePlayer = getFakePlayer(name);
        if (fakePlayer == null || slot < 0 || slot > 8) return false;
        fakePlayer.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(slot));
        fakePlayer.getInventory().setSelectedSlot(slot);
        fakePlayer.getBukkitEntity().getInventory().setHeldItemSlot(slot);
        syncInventoryAndHandItems(fakePlayer);
        return true;
    }

    public boolean swapHands(String name) {
        ServerPlayer fakePlayer = getFakePlayer(name);
        if (fakePlayer == null) return false;
        PlayerInventory inventory = fakePlayer.getBukkitEntity().getInventory();
        ItemStack mainHand = inventory.getItemInMainHand();
        ItemStack offHand = inventory.getItemInOffHand();
        inventory.setItemInMainHand(offHand);
        inventory.setItemInOffHand(mainHand);
        syncInventoryAndHandItems(fakePlayer);
        return true;
    }

    public boolean startRepeatingUse(String name, long intervalTicks) {
        return startRepeatingAction(name, "use", intervalTicks, () -> use(name));
    }

    public boolean startRepeatingAttack(String name, long intervalTicks) {
        return startRepeatingAction(name, "attack", intervalTicks, () -> attack(name));
    }

    public boolean stopActions(String name) {
        ServerPlayer fakePlayer = getFakePlayer(name);
        if (fakePlayer == null) return false;

        cancelRepeatingActions(name);
        fakePlayer.setShiftKeyDown(false);
        fakePlayer.setSprinting(false);
        fakePlayer.setJumping(false);
        fakePlayer.setDeltaMovement(Vec3.ZERO);
        fakePlayer.resetLastActionTime();
        return true;
    }

    public boolean hasFakePlayer(String name) {
        return getFakePlayer(name) != null;
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


    public boolean openInventory(String name, org.bukkit.entity.Player viewer) {
        ServerPlayer fakePlayer = getFakePlayer(name);
        if (fakePlayer == null || viewer == null) return false;
        String key = name.toLowerCase(Locale.ROOT);
        syncInventoryAndHandItems(fakePlayer);
        viewer.openInventory(fakePlayer.getBukkitEntity().getInventory());
        inventoryViewers.put(viewer.getUniqueId(), key);
        viewer.sendMessage(getSelectedHotbarMessage(fakePlayer) + " §8| §7最顶上一排为快捷栏；鼠标滚轮/数字键 1-9 选择，按 F 交换主副手。");
        return true;
    }

    @EventHandler
    public void onPlayerInteractFakePlayer(PlayerInteractEntityEvent event) {
        if (!event.getPlayer().isSneaking()) return;
        String fakeName = fakePlayerNamesByUuid.get(event.getRightClicked().getUniqueId());
        if (fakeName == null) return;
        event.setCancelled(true);
        openInventory(fakeName, event.getPlayer());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        inventoryViewers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onViewerHeldItemChange(PlayerItemHeldEvent event) {
        String fakeName = inventoryViewers.get(event.getPlayer().getUniqueId());
        if (fakeName == null) return;
        if (selectHotbarSlot(fakeName, event.getNewSlot())) {
            ServerPlayer fakePlayer = getFakePlayer(fakeName);
            if (fakePlayer != null) event.getPlayer().sendMessage(getSelectedHotbarMessage(fakePlayer));
        }
    }

    @EventHandler
    public void onFakeInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player viewer)) return;
        String fakeName = inventoryViewers.get(viewer.getUniqueId());
        if (fakeName == null || getFakePlayer(fakeName) == null) {
            inventoryViewers.remove(viewer.getUniqueId());
            return;
        }

        ClickType click = event.getClick();
        if (click == ClickType.NUMBER_KEY) {
            int hotbarButton = event.getHotbarButton();
            if (hotbarButton >= 0 && hotbarButton <= 8) {
                event.setCancelled(true);
                selectHotbarSlot(fakeName, hotbarButton);
                ServerPlayer fakePlayer = getFakePlayer(fakeName);
                if (fakePlayer != null) viewer.sendMessage(getSelectedHotbarMessage(fakePlayer));
            }
            return;
        }

        if (click == ClickType.SWAP_OFFHAND || shouldSwapHandsOnDoubleClick(fakeName, event)) {
            event.setCancelled(true);
            if (swapHands(fakeName)) {
                viewer.updateInventory();
                viewer.sendMessage("§a已交换假人主手和副手物品。");
            }
            return;
        }

        ServerPlayer fakePlayer = getFakePlayer(fakeName);
        if (fakePlayer != null) {
            scheduleInventoryHandRefresh(fakePlayer);
        }
    }

    private boolean shouldSwapHandsOnDoubleClick(String fakeName, InventoryClickEvent event) {
        if (event.getClick() != ClickType.DOUBLE_CLICK || !(event.getClickedInventory() instanceof PlayerInventory)) return false;
        ServerPlayer fakePlayer = getFakePlayer(fakeName);
        if (fakePlayer == null || !event.getClickedInventory().equals(fakePlayer.getBukkitEntity().getInventory())) return false;
        int slot = event.getSlot();
        int selectedSlot = fakePlayer.getBukkitEntity().getInventory().getHeldItemSlot();
        return slot == selectedSlot || slot == 40;
    }

    private String formatItem(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType().isAir()) return "空";
        return item.getType().name().toLowerCase(Locale.ROOT) + " x" + item.getAmount();
    }

    private String getSelectedHotbarMessage(ServerPlayer fakePlayer) {
        int slot = fakePlayer.getInventory().getSelectedSlot();
        org.bukkit.inventory.ItemStack item = fakePlayer.getBukkitEntity().getInventory().getItem(slot);
        String itemName = formatItem(item);
        return "§a当前假人快捷栏：§e第 " + (slot + 1) + " 格 §7(" + itemName + ") §8| 背包界面最顶上一排是快捷栏";
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
        lines.add(getSelectedHotbarMessage(fakePlayer));
        lines.add("§7主手：§f" + formatItem(bukkitPlayer.getInventory().getItemInMainHand()));
        lines.add("§7副手：§f" + formatItem(bukkitPlayer.getInventory().getItemInOffHand()));
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
