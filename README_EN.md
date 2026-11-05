# AirdropPro — Scheduled Airdrop Plugin for Minecraft

> 中文文档：[README.md](README.md)

A lightweight **scheduled airdrop** plugin for **Paper / Purpur 1.21.x** servers. Twice a day (by default 10:00 & 20:00), a supply crate falls from the sky to a random safe location in the world; players race to claim its loot. Three rarity tiers, configurable rewards, countdown broadcasts, a persistent beacon, and simple admin commands — with **zero runtime third-party dependencies** and **zero outbound network requests**.

## Features

- **Three tiers, weighted random** — `common` (weight 60), `rare` (30), `legendary` (10). Rewards, display names, item amounts, custom names and enchantments are all configurable in `config.yml`.
- **Scheduled drops** — any number of times per day (24h `HH:mm`); each drop is fired exactly once per day, with midnight-rollover safety.
- **Countdown reminders** — broadcast and level-up sound at configurable offsets before each drop (default 10 / 5 / 1 minute).
- **Safe random landing** — picks a solid ground block within a configurable radius (default 200–2000), skips water/lava/air and un-generated chunks first (to avoid main-thread chunk generation), with up to 40 attempts.
- **Falling crate & effects** — the chest spawns as a `FallingBlock` from a configurable height (default 40) with lightning, thunder and End-Rod particles; a 30s fallback guarantees it always lands.
- **Persistent beacon** — a vertical End-Rod beam marks every unclaimed crate.
- **Auto despawn** — unclaimed crates vanish after a configurable time (default 30 min, `0` = never) so the world stays clean.
- **Broadcast coordinates** — optionally appends `x, y, z` to dropping/landing messages.
- **Bilingual messages** fully configurable; placeholders `{min}`, `{time}`, `{type}`, `{player}`.
- **Zero dependencies / zero network** — `paper-api` is `provided`; the plugin only uses the Bukkit scheduler and API, never contacts the internet.

## Requirements

| Item | Requirement |
|---|---|
| Server | Paper / Purpur (or any Bukkit fork) **1.21.x** |
| Java | **Java 21** (server-side) |
| Build | Maven 3.9+ with JDK 21 |

## Installation

1. Download `AirdropPro-1.0.0.jar` from the [Releases](https://github.com/ChaosSurvivalProject/AirdropPro/releases) page.
2. Drop it into the server's `plugins/` folder and **restart** the server.
3. Edit `plugins/AirdropPro/config.yml` to your liking, then run `/airdrop reload`.

> The Java resource pack / client is not needed — this is a pure server-side plugin.

## Commands

All commands require the permission `airdrop.admin` (defaults to **OP**). Aliases: `/ad`, `/kongtou`.

| Command | Description |
|---|---|
| `/airdrop reload` | Reload config & restart the scheduler |
| `/airdrop drop [common\|rare\|legendary]` | Drop one airdrop now (random tier if omitted) |
| `/airdrop next` | Show the next scheduled drop time |
| `/airdrop info` | Show runtime status, active crates and tier weights |

## Configuration (`config.yml`)

| Section | Key | Meaning |
|---|---|---|
| `settings` | `world` | Target world (falls back to the first world) |
| | `min-radius` / `max-radius` | Random drop radius from origin |
| | `max-attempts` | Max tries to find a safe spot |
| | `fall-height` | Height the crate falls from (`0` = place directly) |
| | `despawn-minutes` | Auto-despawn time (`0` = never) |
| | `broadcast-coords` | Append coordinates to broadcasts |
| | `beacon` | Keep the light beam visible |
| `schedule` | `times` | Daily drop times (`"HH:mm"`) |
| | `notify-before` | Minutes before each drop to remind |
| `messages` | `notify`/`dropping`/`landed`/`claimed` | Broadcast text |
| `types` | `common`/`rare`/`legendary` | Weight, `pick-count`, and reward item list |

## Build from source

```bash
# JDK 21 required
mvn clean package
# -> target/AirdropPro-1.0.0.jar
```

The `paper-api` dependency is fetched from the [PaperMC repository](https://repo.papermc.io/repository/maven-public/) at build time; it is **not** bundled into the jar.

## License

[MIT](LICENSE) © 2026 ChaosSurvivalProject
