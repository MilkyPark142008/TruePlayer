package me.yourname.trueplayer.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class TruePlayerCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;

    public TruePlayerCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("trueplayer.admin")) {
            sender.sendMessage("§c你没有权限使用这个指令。");
            return true;
        }

        if (args.length != 3 || !args[0].equalsIgnoreCase("whitelist")) {
            sendHelp(sender, label);
            return true;
        }

        String id = args[1];
        String action = args[2].toLowerCase();

        switch (action) {
            case "add" -> addWhitelist(sender, id);
            case "remove", "move" -> removeWhitelist(sender, id);
            default -> sendHelp(sender, label);
        }
        return true;
    }

    private void addWhitelist(CommandSender sender, String id) {
        List<String> whitelist = new ArrayList<>(plugin.getConfig().getStringList("player-command-whitelist"));
        for (String value : whitelist) {
            if (value.equalsIgnoreCase(id)) {
                sender.sendMessage("§e该 ID 已在 TruePlayer 白名单中：§f" + id);
                return;
            }
        }

        whitelist.add(id);
        plugin.getConfig().set("player-command-whitelist", whitelist);
        plugin.saveConfig();
        sender.sendMessage("§a已加入 TruePlayer 白名单：§f" + id);
    }

    private void removeWhitelist(CommandSender sender, String id) {
        List<String> whitelist = new ArrayList<>(plugin.getConfig().getStringList("player-command-whitelist"));
        boolean removed = whitelist.removeIf(value -> value.equalsIgnoreCase(id));
        if (!removed) {
            sender.sendMessage("§c该 ID 不在 TruePlayer 白名单中：§f" + id);
            return;
        }

        plugin.getConfig().set("player-command-whitelist", whitelist);
        plugin.saveConfig();
        sender.sendMessage("§a已移出 TruePlayer 白名单：§f" + id);
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("§6TruePlayer 管理指令：");
        sender.sendMessage("§e/" + label + " whitelist <id> add §7- 添加可使用 /player 的 ID");
        sender.sendMessage("§e/" + label + " whitelist <id> remove §7- 移除可使用 /player 的 ID");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!sender.hasPermission("trueplayer.admin")) return result;

        if (args.length == 1) {
            result.add("whitelist");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("whitelist")) {
            result.addAll(plugin.getConfig().getStringList("player-command-whitelist"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("whitelist")) {
            result.add("add");
            result.add("remove");
        }
        return result;
    }
}
