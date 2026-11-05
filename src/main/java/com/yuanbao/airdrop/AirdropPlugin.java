package com.yuanbao.airdrop;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AirdropPro —— Purpur / Paper 1.21.11 定时空投插件。
 *
 * 功能：
 * 1. 三种空投（普通 / 精良 / 传奇），奖励各不相同；
 * 2. 每天固定时间投放两次（默认 10:00 与 20:00）；
 * 3. 投放前按配置的提前量广播倒计时提醒；
 * 4. 每次在配置世界中随机挑选安全的落点。
 */
public final class AirdropPlugin extends JavaPlugin {

    private AirdropManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        AirdropType.loadAll(getConfig(), getLogger());

        manager = new AirdropManager(this);
        manager.start();

        PluginCommand command = getCommand("airdrop");
        if (command != null) {
            AirdropCommand executor = new AirdropCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("[AirdropPro] plugin.yml 中未声明 airdrop 命令，命令功能不可用。");
        }

        getServer().getPluginManager().registerEvents(new AirdropListener(this), this);

        getLogger().info("[AirdropPro] 插件已启用。");
        getLogger().info("[AirdropPro] 下一次空投: " + manager.getNextDropDescription());
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.stop();
            manager = null;
        }
        getLogger().info("[AirdropPro] 插件已停用。");
    }

    public AirdropManager getManager() {
        return manager;
    }

    /**
     * 仅重新读取配置文件（不重启调度器），由 /airdrop reload 通过 manager.restart() 调用。
     */
    public void reloadConfiguration() {
        reloadConfig();
    }
}
