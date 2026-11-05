package com.yuanbao.airdrop;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 三种空投类型：普通、精良、传奇。
 * 每种类型的奖励、显示名、权重均从 config.yml 读取。
 */
public enum AirdropType {

    COMMON("common", "&a普通补给", 60),
    RARE("rare", "&9精良补给", 30),
    LEGENDARY("legendary", "&6&l传奇补给", 10);

    private final String node;
    private final String defaultDisplayName;
    private final int defaultWeight;

    private String displayName;
    private int weight;
    private int pickCount;
    private List<ItemStack> rewards;

    AirdropType(String node, String defaultDisplayName, int defaultWeight) {
        this.node = node;
        this.defaultDisplayName = defaultDisplayName;
        this.defaultWeight = defaultWeight;
        this.displayName = defaultDisplayName;
        this.weight = defaultWeight;
        this.pickCount = -1;
        this.rewards = new ArrayList<ItemStack>();
    }

    public String getNode() {
        return node;
    }

    public String getDisplayName() {
        return color(displayName);
    }

    public int getWeight() {
        return weight > 0 ? weight : 0;
    }

    public List<ItemStack> getRewards() {
        return Collections.unmodifiableList(rewards);
    }

    /**
     * 按权重随机抽取一个空投类型；若所有权重都为 0，则退化为等概率随机。
     */
    public static AirdropType random(java.util.Random random) {
        int total = 0;
        for (AirdropType type : values()) {
            total += type.getWeight();
        }
        if (total <= 0) {
            AirdropType[] all = values();
            return all[random.nextInt(all.length)];
        }
        int roll = random.nextInt(total);
        for (AirdropType type : values()) {
            roll -= type.getWeight();
            if (roll < 0) {
                return type;
            }
        }
        return COMMON;
    }

    public static AirdropType fromName(String name) {
        if (name == null) {
            return null;
        }
        String key = name.trim().toUpperCase(Locale.ROOT);
        for (AirdropType type : values()) {
            if (type.name().equals(key) || type.node.equalsIgnoreCase(name.trim())) {
                return type;
            }
        }
        return null;
    }

    /**
     * 从 config.yml 的 types 节点读取全部三种空投配置。
     */
    public static void loadAll(FileConfiguration config, Logger logger) {
        ConfigurationSection root = config == null ? null : config.getConfigurationSection("types");
        for (AirdropType type : values()) {
            ConfigurationSection section = root == null ? null : root.getConfigurationSection(type.node);
            if (section == null) {
                type.applyDefaults();
                continue;
            }
            type.displayName = section.getString("display", type.defaultDisplayName);
            type.weight = section.getInt("weight", type.defaultWeight);
            type.pickCount = section.getInt("pick-count", -1);
            List<ItemStack> parsed = parseRewards(section, logger);
            type.rewards = parsed.isEmpty() ? type.buildDefaultRewards() : parsed;
        }
    }

    /**
     * 生成本次空投的实际战利品（深拷贝，避免多个箱子共享同一个 ItemStack 实例）。
     */
    public List<ItemStack> buildLoot(java.util.Random random) {
        List<ItemStack> loot = new ArrayList<ItemStack>();
        if (rewards.isEmpty()) {
            return loot;
        }
        if (pickCount <= 0 || pickCount >= rewards.size()) {
            for (ItemStack item : rewards) {
                loot.add(item.clone());
            }
            return loot;
        }
        List<ItemStack> pool = new ArrayList<ItemStack>(rewards);
        for (int i = 0; i < pickCount && !pool.isEmpty(); i++) {
            loot.add(pool.remove(random.nextInt(pool.size())).clone());
        }
        return loot;
    }

    private void applyDefaults() {
        this.displayName = defaultDisplayName;
        this.weight = defaultWeight;
        this.pickCount = -1;
        this.rewards = buildDefaultRewards();
    }

    private List<ItemStack> buildDefaultRewards() {
        List<ItemStack> list = new ArrayList<ItemStack>();
        switch (this) {
            case RARE:
                list.add(new ItemStack(Material.DIAMOND, 8));
                list.add(new ItemStack(Material.GOLDEN_APPLE, 4));
                list.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 16));
                list.add(new ItemStack(Material.ENDER_PEARL, 8));
                break;
            case LEGENDARY:
                list.add(new ItemStack(Material.NETHERITE_INGOT, 2));
                list.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
                list.add(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
                list.add(new ItemStack(Material.NETHER_STAR, 2));
                break;
            case COMMON:
            default:
                list.add(new ItemStack(Material.BREAD, 16));
                list.add(new ItemStack(Material.COOKED_BEEF, 8));
                list.add(new ItemStack(Material.ARROW, 32));
                list.add(new ItemStack(Material.TORCH, 16));
                break;
        }
        return list;
    }

    private static List<ItemStack> parseRewards(ConfigurationSection section, Logger logger) {
        List<ItemStack> list = new ArrayList<ItemStack>();
        List<Map<?, ?>> raw = section.getMapList("rewards");
        if (raw == null) {
            return list;
        }
        for (Map<?, ?> entry : raw) {
            if (entry == null) {
                continue;
            }
            Object rawMaterial = entry.get("material");
            if (rawMaterial == null) {
                continue;
            }
            Material material;
            try {
                material = Material.valueOf(String.valueOf(rawMaterial).trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                if (logger != null) {
                    logger.warning("[AirdropPro] 跳过无法识别的物品: " + rawMaterial);
                }
                continue;
            }
            if (!material.isItem() || material.isAir()) {
                if (logger != null) {
                    logger.warning("[AirdropPro] 跳过无法作为物品放进箱子的方块: " + rawMaterial);
                }
                continue;
            }
            int amount = toInt(entry.get("amount"), 1);
            if (amount < 1) {
                amount = 1;
            }
            if (amount > material.getMaxStackSize()) {
                amount = material.getMaxStackSize();
            }
            ItemStack item = new ItemStack(material, amount);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                Object rawName = entry.get("name");
                if (rawName != null) {
                    meta.setDisplayName(color(String.valueOf(rawName)));
                }
                Object rawEnchants = entry.get("enchants");
                if (rawEnchants instanceof Map) {
                    applyEnchantments(meta, (Map<?, ?>) rawEnchants, logger);
                }
                item.setItemMeta(meta);
            }
            list.add(item);
        }
        return list;
    }

    private static void applyEnchantments(ItemMeta meta, Map<?, ?> enchants, Logger logger) {
        for (Map.Entry<?, ?> ench : enchants.entrySet()) {
            if (ench.getKey() == null) {
                continue;
            }
            String key = String.valueOf(ench.getKey()).trim().toLowerCase(Locale.ROOT);
            int level = toInt(ench.getValue(), 1);
            if (level < 1) {
                level = 1;
            }
            NamespacedKey namespacedKey;
            try {
                namespacedKey = NamespacedKey.minecraft(key);
            } catch (IllegalArgumentException ex) {
                if (logger != null) {
                    logger.warning("[AirdropPro] 跳过非法的附魔 ID: " + key);
                }
                continue;
            }
            Enchantment enchantment = Enchantment.getByKey(namespacedKey);
            if (enchantment == null) {
                if (logger != null) {
                    logger.warning("[AirdropPro] 跳过不存在的附魔: " + key);
                }
                continue;
            }
            meta.addEnchant(enchantment, level, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
    }

    private static int toInt(Object value, int def) {
        if (value == null) {
            return def;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }
}
