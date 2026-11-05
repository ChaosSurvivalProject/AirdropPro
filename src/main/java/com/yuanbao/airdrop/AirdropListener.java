package com.yuanbao.airdrop;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

import java.util.UUID;

/**
 * 处理空投箱落地与领取。
 */
public final class AirdropListener implements Listener {

    private final AirdropPlugin plugin;

    public AirdropListener(AirdropPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 下落中的空投箱接触地面时，取消原版方块放置，改成真正的补给箱。
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock)) {
            return;
        }
        AirdropManager manager = plugin.getManager();
        if (manager == null) {
            return;
        }
        UUID entityId = event.getEntity().getUniqueId();
        PendingDrop drop = manager.consumePending(entityId);
        if (drop == null) {
            return; // 不是本插件的空投
        }
        event.setCancelled(true);
        event.getEntity().remove();
        manager.finishDrop(drop);
    }

    /**
     * 玩家挖掉空投箱时提示领取成功。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAirdropClaim(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CHEST) {
            return;
        }
        AirdropManager manager = plugin.getManager();
        if (manager == null) {
            return;
        }
        Location location = block.getLocation();
        if (!manager.isAirdropChest(location)) {
            return;
        }
        manager.unregister(location);
        String message = plugin.getConfig().getString("messages.claimed",
                "&8[&6空投&8] &e{player} &a成功领取了一份空投补给！");
        message = message.replace("{player}", event.getPlayer().getName());
        org.bukkit.Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
    }
}
