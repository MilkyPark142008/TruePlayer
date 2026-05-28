package me.yourname.trueplayer.command;

import me.yourname.trueplayer.fake.FakePlayerManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class PlayerCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final FakePlayerManager manager;

    public PlayerCommand(JavaPlugin plugin, FakePlayerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasCommandAccess(sender)) {
            sender.sendMessage("§c你没有权限使用这个指令。");
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
            case "inventory" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§c只有玩家可以打开假人背包。");
                    return true;
                }
                sender.sendMessage(manager.openInventory(fakeName, player) ? "§a已打开假人 §e" + fakeName + " §a的背包。" : "§c找不到假人：§e" + fakeName);
                return true;
            }
            case "hotbar", "slot" -> {
                handleHotbar(sender, fakeName, args);
                return true;
            }
            case "hand" -> {
                handleHand(sender, fakeName, args);
                return true;
            }
            case "swaphand", "swaphands" -> {
                sender.sendMessage(manager.swapHands(fakeName) ? "§a假人 §e" + fakeName + " §a已交换主手和副手物品。" : "§c交换失败，找不到假人：§e" + fakeName);
                return true;
            }
            case "use" -> {
                handleAction(sender, fakeName, args, true);
                return true;
            }
            case "attack" -> {
                handleAction(sender, fakeName, args, false);
                return true;
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }



    private void handleHotbar(CommandSender sender, String fakeName, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§c用法：/player " + fakeName + " hotbar <0-8>");
            return;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[2]);
        } catch (NumberFormatException exception) {
            sender.sendMessage("§c快捷栏槽位必须是 0-8 的整数。");
            return;
        }
        if (slot < 0 || slot > 8) {
            sender.sendMessage("§c快捷栏槽位必须在 0-8 之间。");
            return;
        }
        sender.sendMessage(manager.selectHotbarSlot(fakeName, slot)
                ? "§a假人 §e" + fakeName + " §a已选择快捷栏槽位 §e" + slot + "§a。"
                : "§c设置失败，找不到假人：§e" + fakeName);
    }

    private void handleHand(CommandSender sender, String fakeName, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("§c用法：/player " + fakeName + " hand <main|off>");
            return;
        }
        String hand = args[2].toLowerCase();
        Boolean mainHand = switch (hand) {
            case "main", "mainhand", "main_hand", "主手" -> true;
            case "off", "offhand", "off_hand", "副手" -> false;
            default -> null;
        };
        if (mainHand == null) {
            sender.sendMessage("§c手部参数只能是 main 或 off。");
            return;
        }
        sender.sendMessage(manager.setInteractionHand(fakeName, mainHand)
                ? "§a假人 §e" + fakeName + " §a已将右键使用手设置为§e" + (mainHand.booleanValue() ? "主手" : "副手") + "§a。"
                : "§c设置失败，找不到假人：§e" + fakeName);
    }

    private void handleAction(CommandSender sender, String fakeName, String[] args, boolean useAction) {
        String actionName = useAction ? "右键" : "左键";
        if (args.length == 2) {
            boolean success = useAction ? manager.use(fakeName) : manager.attack(fakeName);
            sender.sendMessage(success ? "§a假人 §e" + fakeName + " §a已执行一次" + actionName + "。" : "§c执行失败，找不到假人或没有可作用目标：§e" + fakeName);
            return;
        }
        if (args.length != 3) {
            sender.sendMessage("§c用法：/player " + fakeName + " " + (useAction ? "use" : "attack") + " [ticks]");
            return;
        }
        long intervalTicks;
        try {
            intervalTicks = Long.parseLong(args[2]);
        } catch (NumberFormatException exception) {
            sender.sendMessage("§c间隔必须是正整数 tick，例如 10 或 20。");
            return;
        }
        if (intervalTicks <= 0) {
            sender.sendMessage("§c间隔必须大于 0 tick。");
            return;
        }
        boolean success = useAction ? manager.startRepeatingUse(fakeName, intervalTicks) : manager.startRepeatingAttack(fakeName, intervalTicks);
        sender.sendMessage(success ? "§a假人 §e" + fakeName + " §a已开始每 §e" + intervalTicks + " tick §a执行一次" + actionName + "。" : "§c执行失败，找不到假人：§e" + fakeName);
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6TruePlayer 指令帮助：");
        sender.sendMessage("§e/player <name> spawn");
        sender.sendMessage("§e/player <name> kill");
        sender.sendMessage("§e/player <name> use [ticks] §7- 右键一次，或每 ticks 执行一次");
        sender.sendMessage("§e/player <name> attack [ticks] §7- 左键一次，或每 ticks 执行一次");
        sender.sendMessage("§e/player <name> inventory §7- 打开假人背包，也可潜行右键假人打开");
        sender.sendMessage("§e/player <name> hotbar <0-8> §7- 选择假人主手对应的快捷栏槽位");
        sender.sendMessage("§e/player <name> hand <main|off> §7- 设置 use 指令使用主手或副手");
        sender.sendMessage("§e/player <name> swaphands §7- 交换假人主手和副手物品");
        sender.sendMessage("§e/player <name> chunkinfo §7- 查看假人区块加载调试信息");
    }

    private boolean hasCommandAccess(CommandSender sender) {
        if (sender.hasPermission("trueplayer.admin")) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            return false;
        }

        List<String> whitelist = plugin.getConfig().getStringList("player-command-whitelist");
        String playerName = player.getName();
        String playerUuid = player.getUniqueId().toString();
        for (String id : whitelist) {
            if (id.equalsIgnoreCase(playerName) || id.equalsIgnoreCase(playerUuid)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!hasCommandAccess(sender)) return result;
        if (args.length == 1) {
            result.addAll(manager.getFakePlayerNames());
        } else if (args.length == 2) {
            result.addAll(List.of("spawn", "kill", "chunkinfo", "use", "attack", "inventory", "hotbar", "slot", "hand", "swaphands"));
        } else if (args.length == 3 && (args[1].equalsIgnoreCase("use") || args[1].equalsIgnoreCase("attack"))) {
            result.addAll(List.of("10", "20"));
        } else if (args.length == 3 && (args[1].equalsIgnoreCase("hotbar") || args[1].equalsIgnoreCase("slot"))) {
            result.addAll(List.of("0", "1", "2", "3", "4", "5", "6", "7", "8"));
        } else if (args.length == 3 && args[1].equalsIgnoreCase("hand")) {
            result.addAll(List.of("main", "off"));
        }
        return result;
    }
}
