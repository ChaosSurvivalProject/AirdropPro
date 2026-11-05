package com.yuanbao.airdrop;

import org.bukkit.Location;

import java.util.UUID;

/**
 * 一次正在下落 / 已落地的空投的记录。
 */
public final class PendingDrop {

    private final UUID entityId;      // 下落中的 FallingBlock 实体 ID（可为 null，表示直接放置）
    private final AirdropType type;   // 空投类型
    private final Location location;  // 目标落点（地面箱子所在位置）

    public PendingDrop(UUID entityId, AirdropType type, Location location) {
        this.entityId = entityId;
        this.type = type;
        this.location = location;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public AirdropType getType() {
        return type;
    }

    public Location getLocation() {
        return location;
    }
}
