package com.yuanbao.airdrop;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /airdrop 管理命令。
 */
public final class AirdropCommand implements CommandExecutor, TabCompleter {

    private final AirdropPlugin plugin;
    private final List<String> subCommands = Arrays.asList("reload", "drop", "next", "info");

    public AirdropCommand(AirdropPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("airdrop.admin")) {
            sender.sendMessage(color("&c你没有权限使用此命令。"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(java.util.Locale.ROOT);
        AirdropManager manager = plugin.getManager();
        if (manager == null) {
            sender.sendMessage(color("&c[AirdropPro] 管理器尚未初始化。"));
            return true;
        }

        if (sub.equals("reload")) {
            try {
                manager.restart();
                sender.sendMessage(color("&a[AirdropPro] 配置已重载，下次空投：" + manager.getNextDropDescription()));
            } catch (Exception ex) {
                sender.sendMessage(color("&c[AirdropPro] 重载失败: " + ex.getMessage()));
            }
            return true;
        }

        if (sub.equals("drop")) {
            AirdropType type = args.length >= 2 ? AirdropType.fromName(args[1]) : null;
            if (args.length >= 2 && type == null) {
                sender.sendMessage(color("&c未知的空投类型: " + args[1] + "（可选 common / rare / legendary）"));
                return true;
            }
            manager.dropNow(type);
            sender.sendMessage(color("&a[AirdropPro] 已手动投放" + (type == null ? "一次随机" : " " + type.getDisplayName() + "&a") + "空投。"));
            return true;
        }

        if (sub.equals("next")) {
            sender.sendMessage(color("&6[AirdropPro] 下一次空投时间: &f" + manager.getNextDropDescription()));
            return true;
        }

        if (sub.equals("info")) {
            sender.sendMessage(color("&6[AirdropPro] 运行状态: &f" + (manager.isRunning() ? "运行中" : "已停止")));
            sender.sendMessage(color("&6[AirdropPro] 当前未领取空投: &f" + manager.getActiveCount() + " 个"));
            for (AirdropType type : AirdropType.values()) {
                sender.sendMessage(color("&7 - " + type.getDisplayName() + " &7(权重 " + type.getWeight()
                        + ", " + type.getRewards().size() + " 种奖励)"));
            }
            return true;
        }

        sendHelp(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<String>();
        if (!sender.hasPermission("airdrop.admin")) {
            return result;
        }
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
            for (String sub : subCommands) {
                if (sub.startsWith(prefix)) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("drop")) {
            String prefix = args[1].toLowerCase(java.util.Locale.ROOT);
            for (AirdropType type : AirdropType.values()) {
                String name = type.name().toLowerCase(java.util.Locale.ROOT);
                if (name.startsWith(prefix)) {
                    result.add(name);
                }
            }
        }
        return result;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&6===== AirdropPro 空投插件 ====="));
        sender.sendMessage(color("&e/airdrop reload &7- 重载配置并重启调度"));
        sender.sendMessage(color("&e/airdrop drop [common|rare|legendary] &7- 立即投放一次空投"));
        sender.sendMessage(color("&e/airdrop next &7- 查看下一次空投时间"));
        sender.sendMessage(color("&e/airdrop info &7- 查看插件运行状态"));
        if (sender instanceof Player) {
            sender.sendMessage(color("&7当前未领取空投: &f" + plugin.getManager().getActiveCount()));
        }
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
