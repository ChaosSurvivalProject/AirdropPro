package com.yuanbao.airdrop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 空投调度与执行核心。
 */
public final class AirdropManager {

    private final AirdropPlugin plugin;
    private final Random random = new Random();

    /** 正在下落中的空投：实体 UUID -> 记录 */
    private final Map<UUID, PendingDrop> pending = new HashMap<UUID, PendingDrop>();
    /** 已落地且未被领取的空投箱子坐标 */
    private final Set<Location> activeChests = new HashSet<Location>();

    private int scheduleTaskId = -1;
    private int beaconTaskId = -1;

    private int lastSecond = -1;
    private LocalDate lastDate = null;
    private final Set<String> fired = new HashSet<String>();

    // ---- 配置项 ----
    private String worldName = "world";
    private int minRadius = 200;
    private int maxRadius = 2000;
    private int maxAttempts = 40;
    private int fallHeight = 40;
    private int despawnMinutes = 30;
    private boolean broadcastCoords = true;
    private boolean beaconEnabled = true;
    private List<Integer> notifyBefore = new ArrayList<Integer>();
    private List<DropTime> dropTimes = new ArrayList<DropTime>();

    public AirdropManager(AirdropPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== 生命周期 ====================

    public void start() {
        loadSettings();
        this.lastSecond = LocalTime.now().toSecondOfDay();
        this.lastDate = LocalDate.now();
        this.fired.clear();

        if (scheduleTaskId != -1) {
            Bukkit.getScheduler().cancelTask(scheduleTaskId);
        }
        scheduleTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                plugin, new Runnable() {
                    @Override
                    public void run() {
                        tick();
                    }
                }, 20L, 20L * 30);

        if (beaconTaskId != -1) {
            Bukkit.getScheduler().cancelTask(beaconTaskId);
        }
        if (beaconEnabled) {
            beaconTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                    plugin, new Runnable() {
                        @Override
                        public void run() {
                            refreshBeacons();
                        }
                    }, 20L, 40L);
        }
    }

    public void stop() {
        if (scheduleTaskId != -1) {
            Bukkit.getScheduler().cancelTask(scheduleTaskId);
            scheduleTaskId = -1;
        }
        if (beaconTaskId != -1) {
            Bukkit.getScheduler().cancelTask(beaconTaskId);
            beaconTaskId = -1;
        }
        pending.clear();
    }

    /** 重载配置后重启调度 */
    public void restart() {
        loadSettings();
        AirdropType.loadAll(plugin.getConfig(), plugin.getLogger());
        start();
    }

    public boolean isRunning() {
        return scheduleTaskId != -1;
    }

    // ==================== 配置读取 ====================

    private void loadSettings() {
        FileConfiguration config = plugin.getConfig();

        worldName = config.getString("settings.world", "world");
        minRadius = config.getInt("settings.min-radius", 200);
        maxRadius = config.getInt("settings.max-radius", 2000);
        maxAttempts = config.getInt("settings.max-attempts", 40);
        fallHeight = config.getInt("settings.fall-height", 40);
        despawnMinutes = config.getInt("settings.despawn-minutes", 30);
        broadcastCoords = config.getBoolean("settings.broadcast-coords", true);
        beaconEnabled = config.getBoolean("settings.beacon", true);

        if (maxRadius < minRadius) {
            int tmp = minRadius;
            minRadius = maxRadius;
            maxRadius = tmp;
        }
        if (minRadius < 0) {
            minRadius = 0;
        }
        if (fallHeight < 0) {
            fallHeight = 0;
        }
        if (maxAttempts < 1) {
            maxAttempts = 1;
        }

        notifyBefore = new ArrayList<Integer>();
        List<String> rawNotify = config.getStringList("schedule.notify-before");
        for (String raw : rawNotify) {
            try {
                int minutes = Integer.parseInt(String.valueOf(raw).trim());
                if (minutes > 0) {
                    notifyBefore.add(minutes);
                }
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("[AirdropPro] 无效的提醒时间: " + raw);
            }
        }
        if (notifyBefore.isEmpty()) {
            notifyBefore.add(10);
            notifyBefore.add(5);
            notifyBefore.add(1);
        }

        dropTimes = new ArrayList<DropTime>();
        List<String> rawTimes = config.getStringList("schedule.times");
        for (String raw : rawTimes) {
            DropTime parsed = DropTime.parse(raw);
            if (parsed != null) {
                dropTimes.add(parsed);
            } else {
                plugin.getLogger().warning("[AirdropPro] 无效的投放时间: " + raw);
            }
        }
        if (dropTimes.isEmpty()) {
            dropTimes.add(new DropTime(10, 0));
            dropTimes.add(new DropTime(20, 0));
        }
    }

    // ==================== 时间调度 ====================

    private void tick() {
        LocalTime now = LocalTime.now();
        LocalDate today = LocalDate.now();
        int nowSecond = now.toSecondOfDay();
        int last = lastSecond;
        lastSecond = nowSecond;

        if (lastDate == null || !today.equals(lastDate)) {
            lastDate = today;
            fired.clear();
        }

        for (DropTime time : dropTimes) {
            for (int minutes : notifyBefore) {
                int trigger = time.getSecondOfDay() - minutes * 60;
                if (trigger < 0) {
                    continue; // 不跨天提醒
                }
                String key = time.getKey() + "#notify#" + minutes;
                if (!fired.contains(key) && crossed(last, nowSecond, trigger)) {
                    fired.add(key);
                    announcePending(time, minutes);
                }
            }
            String key = time.getKey() + "#drop";
            if (!fired.contains(key) && crossed(last, nowSecond, time.getSecondOfDay())) {
                fired.add(key);
                dropNow(null);
            }
        }
    }

    /**
     * 判断目标秒数是否落在 (last, now] 这个检查区间内，支持跨午夜。
     */
    private static boolean crossed(int last, int now, int target) {
        if (target < 0) {
            return false;
        }
        if (now >= last) {
            return last < target && target <= now;
        }
        return target > last || target <= now;
    }

    // ==================== 投放 ====================

    /**
     * 立即投放一次空投。type 为 null 时按权重随机。
     */
    public void dropNow(AirdropType forcedType) {
        World world = resolveWorld();
        if (world == null) {
            plugin.getLogger().warning("[AirdropPro] 找不到可用的世界，空投取消。");
            return;
        }
        Location target = findSafeLocation(world);
        if (target == null) {
            plugin.getLogger().warning("[AirdropPro] 未能在 " + maxAttempts + " 次尝试内找到安全落点，空投取消。");
            return;
        }
        AirdropType type = forcedType != null ? forcedType : AirdropType.random(random);
        spawnAirdrop(world, type, target);
    }

    private World resolveWorld() {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return world;
        }
        List<World> worlds = Bukkit.getWorlds();
        if (!worlds.isEmpty()) {
            plugin.getLogger().warning("[AirdropPro] 未找到世界 " + worldName + "，回退到 " + worlds.get(0).getName());
            return worlds.get(0);
        }
        return null;
    }

    private Location findSafeLocation(World world) {
        Location result = findSafeLocation(world, true);
        if (result == null) {
            plugin.getLogger().info("[AirdropPro] 配置半径内没有已生成的安全落点，重试并允许生成新区块。");
            result = findSafeLocation(world, false);
        }
        return result;
    }

    private Location findSafeLocation(World world, boolean generatedOnly) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int x = randomBetween(minRadius, maxRadius) * (random.nextBoolean() ? 1 : -1);
            int z = randomBetween(minRadius, maxRadius) * (random.nextBoolean() ? 1 : -1);

            if (generatedOnly && !world.isChunkGenerated(x >> 4, z >> 4)) {
                continue; // 跳过未生成区块，避免主线程同步生成卡顿
            }
            int highest = world.getHighestBlockYAt(x, z);
            if (highest <= world.getMinHeight()) {
                continue;
            }
            Block ground = world.getBlockAt(x, highest, z);
            if (!ground.getType().isSolid()) {
                continue; // 排除空气、水、岩浆
            }
            Block first = world.getBlockAt(x, highest + 1, z);
            Block second = world.getBlockAt(x, highest + 2, z);
            if (!first.getType().isAir() || !second.getType().isAir()) {
                continue;
            }
            return new Location(world, x + 0.5, highest + 1.0D, z + 0.5);
        }
        return null;
    }

    private void spawnAirdrop(World world, AirdropType type, Location ground) {
        if (fallHeight > 0) {
            Location spawnAt = ground.clone().add(0.0D, fallHeight, 0.0D);
            FallingBlock falling = null;
            try {
                falling = world.spawnFallingBlock(spawnAt, Material.CHEST.createBlockData());
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("[AirdropPro] 空投下落实体生成失败，改为直接放置: " + ex.getMessage());
            }
            if (falling != null) {
                falling.setDropItem(false);
                final FallingBlock entity = falling;
                final PendingDrop drop = new PendingDrop(falling.getUniqueId(), type, ground);
                pending.put(falling.getUniqueId(), drop);

                // 兜底：30 秒后仍未落地则强制放置
                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        PendingDrop stillPending = pending.remove(drop.getEntityId());
                        if (stillPending != null) {
                            if (!entity.isDead()) {
                                entity.remove();
                            }
                            finishDrop(stillPending);
                        }
                    }
                }, 20L * 30);

                announceDropping(type, ground);
                return;
            }
        }
        finishDrop(new PendingDrop(null, type, ground));
    }

    /**
     * 由监听器在 FallingBlock 落地时调用；也用于兜底直接放置。
     */
    public PendingDrop consumePending(UUID entityId) {
        return pending.remove(entityId);
    }

    public void finishDrop(PendingDrop drop) {
        Location loc = drop.getLocation();
        Block block = findPlacementBlock(loc);
        if (block == null) {
            plugin.getLogger().warning("[AirdropPro] 空投落点被占用，无法放置箱子: " + loc);
            return;
        }
        block.setType(Material.CHEST);

        BlockState state = block.getState();
        if (state instanceof Chest) {
            // 重要：1.20.6+ 的 BlockState 是快照语义——getState() 会把方块实体当时的数据
            // （刚放置的空箱子）拷贝一份存进 state，update(true) 会把这份快照 NBT 整体写回
            // 方块实体（CraftBlockEntityState#update -> applyTo -> copyData -> loadWithComponents），
            // 于是先前通过 getBlockInventory() 放进实时箱子的战利品会被空快照清掉——这正是
            // "箱子里没有奖励"的原因。
            // getBlockInventory() 返回的就是方块实体里的实时库存，改它即刻生效且会自动标脏保存，
            // 因此这里直接写入实时库存，并且绝不再调用 state.update()。
            Inventory inventory = ((Chest) state).getBlockInventory();
            for (ItemStack item : drop.getType().buildLoot(random)) {
                if (inventory.firstEmpty() == -1) {
                    break; // 箱子满了就停止放入
                }
                inventory.addItem(item);
            }
        } else {
            plugin.getLogger().warning("[AirdropPro] 空投位置无法转换为箱子: " + block.getLocation());
        }

        Location chestLocation = block.getLocation();
        activeChests.add(chestLocation);
        playDropEffects(chestLocation);
        announceLanded(drop.getType(), chestLocation);
        // 落地信息同时写控制台日志：messages.landed 只会广播给在线玩家，
        // 无玩家在线时 latest.log 里看不到任何落地痕迹（上一轮"没看到 landed 日志"即为此）。
        plugin.getLogger().info("[AirdropPro] 空投已落地: type=" + drop.getType().getNode()
                + " world=" + chestLocation.getWorld().getName()
                + " x=" + chestLocation.getBlockX()
                + " y=" + chestLocation.getBlockY()
                + " z=" + chestLocation.getBlockZ());

        if (despawnMinutes > 0) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                @Override
                public void run() {
                    if (activeChests.remove(chestLocation)) {
                        Block chestBlock = chestLocation.getBlock();
                        if (chestBlock.getType() == Material.CHEST) {
                            chestBlock.setType(Material.AIR);
                        }
                    }
                }
            }, 20L * 60L * despawnMinutes);
        }
    }

    /**
     * 从目标落点向上找第一个可放置的空气/可替换方块，避免覆盖玩家已放下的方块
     * （30 秒兜底延迟内，目标位置可能已经被玩家改动甚至放了别人的箱子）。
     */
    private Block findPlacementBlock(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        for (int dy = 0; dy <= 3; dy++) {
            Block candidate = world.getBlockAt(loc.getBlockX(), loc.getBlockY() + dy, loc.getBlockZ());
            if (candidate.getType().isAir() || candidate.isReplaceable()) {
                return candidate;
            }
        }
        return null;
    }

    // ==================== 空投箱查询 ====================

    public boolean isAirdropChest(Location location) {
        return activeChests.contains(location);
    }

    public void unregister(Location location) {
        activeChests.remove(location);
    }

    public int getActiveCount() {
        return activeChests.size();
    }

    // ==================== 特效与广播 ====================

    private void playDropEffects(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        try {
            world.strikeLightningEffect(loc);
        } catch (Exception ignored) {
            // 部分环境下可能不支持，忽略
        }
        world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0F, 1.0F);
        for (int i = 0; i <= 24; i++) {
            Location particleLoc = loc.clone().add(0.0D, i, 0.0D);
            world.spawnParticle(Particle.END_ROD, particleLoc, 3, 0.15D, 0.15D, 0.15D, 0.01D);
        }
    }

    private void refreshBeacons() {
        if (activeChests.isEmpty()) {
            return;
        }
        for (Location loc : new ArrayList<Location>(activeChests)) {
            World world = loc.getWorld();
            if (world == null) {
                continue;
            }
            for (int i = 0; i <= 30; i += 3) {
                world.spawnParticle(Particle.END_ROD, loc.clone().add(0.0D, i, 0.0D), 1, 0.1D, 0.1D, 0.1D, 0.0D);
            }
        }
    }

    private void announcePending(DropTime time, int minutes) {
        String message = plugin.getConfig().getString("messages.notify",
                "&8[&6空投&8] &e将在 &c{min} &e分钟后的 &b{time} &e于主世界随机地点投放一批补给！");
        message = message.replace("{min}", String.valueOf(minutes)).replace("{time}", time.toString());
        broadcast(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        }
    }

    private void announceDropping(AirdropType type, Location loc) {
        String message = plugin.getConfig().getString("messages.dropping",
                "&8[&6空投&8] &6{type} &e正在降落中，请注意观察天空！");
        message = message.replace("{type}", type.getDisplayName());
        if (broadcastCoords) {
            message += " &7坐标: &f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
        }
        broadcast(message);
    }

    public void announceLanded(AirdropType type, Location loc) {
        String message = plugin.getConfig().getString("messages.landed",
                "&8[&6空投&8] &6{type} &a已落地！先到先得！");
        message = message.replace("{type}", type.getDisplayName());
        if (broadcastCoords) {
            message += " &7坐标: &f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
        }
        broadcast(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        }
    }

    private void broadcast(String message) {
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
    }

    // ==================== 下次投放时间 ====================

    public String getNextDropDescription() {
        if (dropTimes.isEmpty()) {
            return "未配置";
        }
        int nowSecond = LocalTime.now().toSecondOfDay();
        DropTime next = null;
        for (DropTime time : dropTimes) {
            if (time.getSecondOfDay() > nowSecond) {
                if (next == null || time.getSecondOfDay() < next.getSecondOfDay()) {
                    next = time;
                }
            }
        }
        if (next == null) {
            next = dropTimes.get(0);
            return "明天 " + next.toString();
        }
        return "今天 " + next.toString();
    }

    private int randomBetween(int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    // ==================== 内部：投放时间点 ====================

    public static final class DropTime {

        private final int hour;
        private final int minute;

        public DropTime(int hour, int minute) {
            this.hour = hour;
            this.minute = minute;
        }

        public static DropTime parse(String raw) {
            if (raw == null) {
                return null;
            }
            String[] parts = raw.trim().split(":");
            if (parts.length < 2) {
                return null;
            }
            try {
                int hour = Integer.parseInt(parts[0].trim());
                int minute = Integer.parseInt(parts[1].trim());
                if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                    return null;
                }
                return new DropTime(hour, minute);
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        public int getSecondOfDay() {
            return hour * 3600 + minute * 60;
        }

        public String getKey() {
            return toString();
        }

        @Override
        public String toString() {
            return (hour < 10 ? "0" : "") + hour + ":" + (minute < 10 ? "0" : "") + minute;
        }
    }
}
