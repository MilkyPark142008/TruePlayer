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
        if (!hasCommandAccess(sender)) {
            sender.sendMessage("§c你没有权限使用这个指令。只有 OP/trueplayer.admin 权限或服务器白名单玩家可以使用。");
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
                    case INVALID_NAME -> sender.sendMessage("§c生成失败：名称不合法。只能使用 1-16 个英文、数字或下划线，例如 1、a、qc、Bot1、test_01。");
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
            case "chunkinfo" -> {
                for (String line : manager.getChunkInfo(fakeName)) {
                    sender.sendMessage(line);
                }
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private boolean hasCommandAccess(CommandSender sender) {
        if (sender.hasPermission("trueplayer.admin")) return true;
        if (sender instanceof Player player) return player.isWhitelisted();
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6TruePlayer 指令帮助：");
        sender.sendMessage("§e/player <name> spawn");
        sender.sendMessage("§e/player <name> kill");
        sender.sendMessage("§e/player <name> chunkinfo §7- 查看假人区块加载调试信息");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!hasCommandAccess(sender)) return result;
        if (args.length == 1) {
            result.addAll(manager.getFakePlayerNames());
        } else if (args.length == 2) {
            result.addAll(List.of("spawn", "kill", "chunkinfo"));
        }
        return result;
    }
}
