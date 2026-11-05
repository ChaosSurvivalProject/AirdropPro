# AirdropPro —— Minecraft 定时空投插件

> English version: [README_EN.md](README_EN.md)

一个面向 **Paper / Purpur 1.21.x** 服务端的轻量级**定时空投**插件。每天定时（默认 10:00 与 20:00）向主世界随机安全地点投放补给箱，玩家先到先得。内置三档稀有度、可配置奖励、倒计时广播、落地光柱与管理员命令，**零运行时第三方依赖、零外联网络请求**。

## 功能特性

- **三档稀有度，按权重随机** —— 普通（权重 60）、精良（30）、传奇（10）。奖励物品、显示名、数量、自定义名称与附魔全部可在 `config.yml` 配置。
- **定时投放** —— 每天可配置任意多个时间点（24 小时制 `HH:mm`），每个时间点当天只触发一次，并做了跨午夜安全处理。
- **倒计时提醒** —— 投放前按配置提前量（默认 10 / 5 / 1 分钟）全服广播并播放升级音效。
- **随机安全落点** —— 在可配置半径（默认 200–2000 格）内挑选实心地面，排除水/岩浆/空气，优先跳过未生成区块以避免主线程同步生成卡顿，最多尝试 40 次。
- **从天而降 + 特效** —— 空投箱以 `FallingBlock` 从可配置高度（默认 40 格）落下，伴随闪电、雷声与末地烛粒子；30 秒兜底保证必定落地。
- **持续光柱信标** —— 每个未被领取的空投箱都有竖直末地烛光柱标记。
- **自动消失** —— 未领取的箱子在配置时间后（默认 30 分钟，`0` = 永不消失）自动清除，保持世界整洁。
- **坐标广播** —— 可开关，在投放/落地消息后附带 `x, y, z` 坐标。
- **消息文案全可配**，占位符 `{min}`、`{time}`、`{type}`、`{player}`。
- **零依赖 / 零联网** —— `paper-api` 为 `provided`，插件仅使用 Bukkit 调度器与 API，运行时不访问任何网络。

## 运行环境

| 项目 | 要求 |
|---|---|
| 服务端 | Paper / Purpur（或其它 Bukkit 分支）**1.21.x** |
| Java | **Java 21**（服务端） |
| 构建 | Maven 3.9+ 且使用 JDK 21 |

## 安装

1. 从 [Releases](https://github.com/ChaosSurvivalProject/AirdropPro/releases) 下载 `AirdropPro-1.0.0.jar`。
2. 放入服务端 `plugins/` 目录并**重启**服务器。
3. 按需修改 `plugins/AirdropPro/config.yml`，然后执行 `/airdrop reload`。

> 纯服务端插件，无需客户端资源包。

## 命令

所有命令需要权限 `airdrop.admin`（默认 **OP**）。别名：`/ad`、`/kongtou`。

| 命令 | 说明 |
|---|---|
| `/airdrop reload` | 重载配置并重启调度 |
| `/airdrop drop [common\|rare\|legendary]` | 立即投放一次空投（省略类型则按权重随机） |
| `/airdrop next` | 查看下一次投放时间 |
| `/airdrop info` | 查看运行状态、当前未领取空投数与各档权重 |

## 配置（`config.yml`）

| 段 | 键 | 含义 |
|---|---|---|
| `settings` | `world` | 投放世界（找不到时回退到第一个世界） |
| | `min-radius` / `max-radius` | 距原点的随机落点半径 |
| | `max-attempts` | 寻找安全落点的最大尝试次数 |
| | `fall-height` | 空投箱下落高度（`0` = 直接放置） |
| | `despawn-minutes` | 自动消失时间（`0` = 永不消失） |
| | `broadcast-coords` | 广播是否附带坐标 |
| | `beacon` | 是否持续显示光柱 |
| `schedule` | `times` | 每日投放时间点（`"HH:mm"`） |
| | `notify-before` | 提前多少分钟提醒 |
| `messages` | `notify` / `dropping` / `landed` / `claimed` | 广播文案 |
| `types` | `common` / `rare` / `legendary` | 权重、`pick-count` 与奖励物品列表 |

## 从源码构建

```bash
# 需要 JDK 21
mvn clean package
# 产物：target/AirdropPro-1.0.0.jar
```

`paper-api` 依赖在构建时从 [PaperMC 仓库](https://repo.papermc.io/repository/maven-public/) 拉取，**不会**打进成品 jar。

## 许可证

[MIT](LICENSE) © 2026 ChaosSurvivalProject
