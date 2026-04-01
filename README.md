# EloRanks

A competitive 1v1 Elo-based ranking system for Minecraft Paper servers.

## Features

- **Elo Rating System**: Full Elo implementation with dynamic K-factor
- **1v1 Duels**: Challenge other players to duels
- **UHC-Style Kit**: Diamond armor, bow, 64 arrows, 10 golden apples, cobwebs, oak planks, water/lava buckets
- **Potion Effects**: Speed II and Strength II for the duration of the duel
- **FAWE Integration**: FastAsyncWorldEdit-powered arena loading from schematics
- **Auto-arena Management**: Automatically creates and resets arenas
- **Leaderboard**: Global ranking with pagination
- **Admin Commands**: Comprehensive admin control panel
- **Highly Configurable**: Extensive config options

## Commands

### Player Commands
| Command | Aliases | Description |
|---------|---------|-------------|
| `/er` | `elo`, `elostats`, `erstats` | View your Elo stats |
| `/er <player>` | | View another player's stats |
| `/er top [count]` | | View top players |
| `/duel <player>` | | Challenge a player to duel |
| `/duel accept <player>` | | Accept a duel request |
| `/duel decline <player>` | | Decline a duel request |
| `/leaderboard [page]` | | View the global leaderboard |

### Admin Commands (`/eradmin`)
| Command | Aliases | Description |
|---------|---------|--------------|
| `makeduel <p1> <p2> [force]` | `startduel` | Create duel (force=true for instant) |
| `addarena` | `createarena` | Add new arena instance |
| `reload` | `rl` | Reload configuration |
| `resetall` | `wipedata` | Reset all player data |
| `resetplayer <p>` | `resetp` | Reset specific player's stats |
| `setelo <p> <elo>` | `setrating` | Set player's Elo |
| `adde <p> <amt>` | `addrating` | Add Elo to player |
| `arenainfo` | `arenastatus` | Show arena information |
| `forcereset <id>` | `cleararena` | Force reset specific arena |
| `endduel <p>` | `cancelduel` | End a player's active duel |
| `tparena [p] [id]` | `arenatp` | Teleport to arena |
| `getpos <id>` | `arenapos` | Get arena coordinates |
| `heal <p>` | | Heal a player |
| `feed <p>` | | Feed a player |
| `stats` | `plstats` | Show plugin statistics |
| `debug` | `diag` | Show debug information |

**Permission**: `er.admin` (defaults to OP)

## Configuration

### Elo Settings
```yaml
elo:
  starting: 1000          # Starting Elo for new players
  min: 0                  # Minimum Elo
  max: 10000              # Maximum Elo
  k-factor:
    win: 32               # Base K-factor for wins
    draw: 16              # Base K-factor for draws
    dynamic:
      enabled: true       # Enable dynamic K-factor
      by-games:
        enabled: true
        threshold: 20     # Games before using normal K
        new-player-k: 48 # K-factor for new players
      by-elo:
        enabled: true
        threshold-1: 1500 # At 1500+, use lower K
        threshold-2: 2000 # At 2000+, even lower K
        k-at-1500: 24
        k-at-2000: 16
```

### Arena Settings
```yaml
arena:
  initial-count: 10      # Number of arenas to generate
  spacing: 100            # Blocks between arena instances
  schematic: cloudy.schematic
  auto-expand: true       # Auto-generate more arenas when full
  expand-count: 5         # How many to generate when expanding
```

### Kit Settings
```yaml
kit:
  enabled: true
  sword: DIAMOND_SWORD
  bow: BOW
  arrows: 64
  helmet: DIAMOND_HELMET
  chestplate: DIAMOND_CHESTPLATE
  leggings: DIAMOND_LEGGINGS
  boots: DIAMOND_BOOTS
  offhand: SHIELD
  food: GOLDEN_APPLE:10
  blocks: COBWEB:16,OAK_PLANKS:64
  buckets: WATER_BUCKET,LAVA_BUCKET
```

### Gameplay Settings
```yaml
gameplay:
  fall-damage: false
  fire-damage: false
  hunger-depletion: false
  keep-inventory: true
  natural-regeneration: false
```

## Arena Setup

1. Build your arena in Minecraft
2. Place **RED_WOOL** where you want player 1 to spawn
3. Place **BLUE_WOOL** where you want player 2 to spawn
4. Export the arena as a `.schematic` file (using WorldEdit/FAWE)
5. Name it `cloudy.schematic` (or match your config)
6. Place the schematic in the plugin's `arenas/` folder

## Requirements

- Minecraft Paper 1.21+
- [FastAsyncWorldEdit](https://modrinth.com/plugin/fastasyncworldedit) - Must be installed separately!

## Installation

1. Download and install **FastAsyncWorldEdit** on your server
2. Drop `EloRanks.jar` into your `plugins/` folder
3. (Optional) Add your arena schematic to `plugins/EloRanks/arenas/`
4. Restart the server

## Building

```bash
mvn clean package
```

The built JAR will be in `target/EloRanks-{version}.jar`

## License

MIT License
