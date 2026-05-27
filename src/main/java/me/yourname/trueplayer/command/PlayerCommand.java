package me.yourname.trueplayer.command;

import me.yourname.trueplayer.fake.FakePlayerManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class PlayerCommand implements CommandExecutor, TabCompleter {

    private final FakePlayerManager manager;

    public PlayerCommand(FakePlayerManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("trueplayer.admin")) {
            sender.sendMessage("§c你没有权限使用这个指令。");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            List<String> names = manager.getFakePlayerNames();
            sender.sendMessage(names.isEmpty() ? "§e当前没有假人。" : "§a当前假人：§f" + String.join("§7, §f", names));
            return true;
        }

        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        String fakeName = args[0];
        String action = args[1].toLowerCase();

        switch (action) {
            case "spawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§c只有玩家可以在当前位置生成假人。");
                    return true;
                }
                FakePlayerManager.SpawnResult result = manager.spawnFakePlayerResult(fakeName, player.getLocation());
                switch (result) {
                    case SUCCESS -> sender.sendMessage("§a已生成假人：§e" + fakeName);
                    case INVALID_NAME -> sender.sendMessage("§c生成失败：名称不合法。只能使用 1-16 个英文、数字或下划线，例如 Bot1、test_01。");
                    case INVALID_LOCATION -> sender.sendMessage("§c生成失败：当前位置所在世界无效。");
                    case ALREADY_EXISTS -> sender.sendMessage("§c生成失败：假人已存在：§e" + fakeName);
                    case NMS_ERROR -> sender.sendMessage("§c生成失败：NMS 运行时不兼容或构造假人时发生异常，请查看服务器控制台报错。");
                }
                return true;
            }
            case "kill" -> {
                sender.sendMessage(manager.removeFakePlayer(fakeName) ? "§a已移除假人：§e" + fakeName : "§c找不到假人：§e" + fakeName);
                return true;
            }
            case "tp" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§c只有玩家可以使用这个指令。");
                    return true;
                }
                sender.sendMessage(manager.teleportFakePlayer(fakeName, player.getLocation()) ? "§a已将假人传送到你的位置。" : "§c找不到假人：§e" + fakeName);
                return true;
            }
            case "look" -> {
                if (args.length < 4) {
                    sender.sendMessage("§c用法：/player <name> look <yaw> <pitch>");
                    return true;
                }
                try {
                    float yaw = Float.parseFloat(args[2]);
                    float pitch = Float.parseFloat(args[3]);
                    sender.sendMessage(manager.lookFakePlayer(fakeName, yaw, pitch) ? "§a已设置假人视角。" : "§c找不到假人：§e" + fakeName);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c视角参数必须是数字。");
                }
                return true;
            }
            case "sneak" -> {
                sender.sendMessage(manager.setSneaking(fakeName, true) ? "§a假人已潜行。" : "§c找不到假人：§e" + fakeName);
                return true;
            }
            case "unsneak" -> {
                sender.sendMessage(manager.setSneaking(fakeName, false) ? "§a假人已取消潜行。" : "§c找不到假人：§e" + fakeName);
                return true;
            }
            case "jump" -> {
                sender.sendMessage(manager.jump(fakeName) ? "§a假人已跳跃。" : "§c找不到假人：§e" + fakeName);
                return true;
            }
            case "attack" -> {
                sender.sendMessage(manager.attack(fakeName) ? "§a假人已执行攻击动作。" : "§c攻击失败。可能是假人不存在，或视线前方没有实体。");
                return true;
            }
            case "use" -> {
                sender.sendMessage(manager.use(fakeName) ? "§a假人已执行右键动作。" : "§c使用失败。可能是假人不存在。");
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6TruePlayer 指令帮助：");
        sender.sendMessage("§e/player list");
        sender.sendMessage("§e/player <name> spawn");
        sender.sendMessage("§e/player <name> kill");
        sender.sendMessage("§e/player <name> tp");
        sender.sendMessage("§e/player <name> look <yaw> <pitch>");
        sender.sendMessage("§e/player <name> sneak|unsneak|jump|attack|use");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!sender.hasPermission("trueplayer.admin")) return result;
        if (args.length == 1) {
            result.add("list");
            result.addAll(manager.getFakePlayerNames());
        } else if (args.length == 2) {
            result.addAll(List.of("spawn", "kill", "tp", "look", "sneak", "unsneak", "jump", "attack", "use"));
        }
        return result;
    }
}