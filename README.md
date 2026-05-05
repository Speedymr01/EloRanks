# EloRanks

A competitive 1v1 Elo-based ranking system for Minecraft 1.21 servers with matchmaking, auto-arena loading, and dynamic K-factor.

## Features

- **Elo Rating System**: Dynamic K-factor based on games played and Elo rating
- **Placement Matches**: 5 placement matches for new players with bonus Elo for beating higher-rated opponents
- **Matchmaking**: Queue-based matchmaking with expanding Elo range over time
- **Auto-Arena Loading**: FAWE-based arena system with automatic loading/unloading
- **1v1 Duels**: UHC-style kits with custom countdown timers
- **Surrender**: Available after 30 seconds - instant loss (full Elo penalty)
- **Scoreboard & Bossbar**: Real-time rank, Elo, and opponent health display
- **Ranked Nametags**: Color-coded rank prefixes on nametags (#1-50)
- **Detailed Config**: Highly customizable with extensive options

## Commands

### Player Commands

| Command | Aliases | Description |
|---------|---------|-------------|
| `/er` | `elo`, `elostats`, `erstats` | View your Elo stats and rank |
| `/er <player>` | | View another player's stats |
| `/er top` | | View top players |
| `/duel <player>` | | Challenge a player to duel |
| `/duel accept <player>` | | Accept a duel request |
| `/duel decline <player>` | | Decline a duel request |
| `/duel match` | `queue`, `mm` | Enter matchmaking queue |
| `/duel cancel` | `stop` | Cancel matchmaking |
| `/surrender` | `forfeit`, `sq` | Surrender from active duel (30s min) |
| `/leaderboard` | `lb` | View global Elo leaderboard |

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

## Permissions

| Permission | Description |
|------------|-------------|
| `er.duel` | Allow dueling other players |
| `er.duel.request` | Allow sending duel requests |
| `er.duel.accept` | Allow accepting duel requests |
| `er.duel.matchmaking` | Allow using matchmaking queue |
| `er.duel.surrender` | Allow surrendering from duels |
| `er.stats.view` | Allow viewing own stats |
| `er.stats.view.others` | Allow viewing other players' stats |
| `er.leaderboard` | Allow viewing the leaderboard |
| `er.admin` | Admin access |

## Configuration

All configuration is stored in `config.yml` (generated on first run).

### Elo Settings
```yaml
elo:
  starting: 1000              # Starting Elo for new players
  min: 0                      # Minimum Elo
  max: 10000                  # Maximum Elo
  k-factor:
    win: 32                   # Base K-factor for wins
    draw: 16                  # Base K-factor for draws
    dynamic:
      enabled: true           # Enable dynamic K-factor
      by-games:
        enabled: true
        threshold: 20         # Games before reducing K
        new-player-k: 48      # K-factor for new players (first games)
      by-elo:
        enabled: true
        threshold-1: 1500     # First Elo threshold
        threshold-2: 2000    # Second Elo threshold
        k-at-1500: 24        # K at first threshold
        k-at-2000: 16        # K at second threshold
  placement:
    enabled: true
    min-players: 5            # Players needed for placement system
    match-count: 5            # Placement matches
    k-factor-games: 5         # K-factor for placement games
    bonus-beating-higher: true # Bonus Elo for beating higher-rated players
```

### Duel Settings
```yaml
duel:
  cooldown: 60                # Seconds between duels
  request-timeout: 30        # Request expiration (seconds)
  arena-world: "duel_arena"  # World for arenas
  allow-spectators: true
  spectator-permission: "er.spectate"
```

### Surrender Settings
```yaml
surrender:
  enabled: true
  min-duel-time-seconds: 30  # Must wait 30 seconds before surrendering
  instant-loss: true        # Full Elo penalty (not halved)
```

### Matchmaking Settings
```yaml
matchmaking:
  enabled: true
  initial-elo-range: 50      # Starting Elo range
  range-increase-per-second: 10  # Range increase per second
  max-elo-range: 500        # Maximum range
  bidirectional-check: true # Both players must be in range
  check-interval-seconds: 1
```

### Countdown Settings
```yaml
countdown:
  teleport-seconds: 5        # Teleport countdown duration
  duel-start-seconds: 20    # Duel start countdown duration
  show-title: true          # Show title
  show-subtitle: true       # Show subtitle
  show-chat-messages: true  # Show chat messages
  colors:
    seconds-20-to-6: "BLUE"
    second-5: "DARK_RED"
    second-4: "RED"
    second-3: "GOLD"
    second-2: "YELLOW"
    second-1: "GREEN"
```

### Arena Settings
```yaml
arena:
  initial-count: 10          # Initial arena count
  spacing: 100               # Distance between arenas
  schematic: "cloudy.schematic"
  auto-expand: true          # Auto-add arenas when needed
  expand-count: 5            # Arenas to add when expanding
  respawn-delay: 3           # Respawn delay (seconds)
  load-timeout: 10           # Schematic load timeout
```

### Kit Settings
```yaml
kit:
  enabled: true
  sword: "DIAMOND_SWORD"
  bow: "BOW"
  arrows: 64
  helmet: "DIAMOND_HELMET"
  chestplate: "DIAMOND_CHESTPLATE"
  leggings: "DIAMOND_LEGGINGS"
  boots: "DIAMOND_BOOTS"
  offhand: "SHIELD"
  food: "GOLDEN_APPLE:10"
  blocks: "COBWEB:16,OAK_PLANKS:64"
  buckets: "WATER_BUCKET,LAVA_BUCKET"
```

### Potion Effects
```yaml
effects:
  speed: true
  speed-level: 2
  speed-duration: 180  # 3 minutes in seconds
  strength: true
  strength-level: 2
  strength-duration: 180
```

### World & Gameplay
```yaml
world:
  void-world: true           # Arena is void world
  spawn-protection: false
  pvp-enabled: true

gameplay:
  fall-damage: false
  fire-damage: false
  hunger-depletion: false
  keep-inventory: true
  natural-regeneration: false
  potion-effects-disabled: true
```

### Scoreboard & Bossbar
```yaml
scoreboard:
  enabled: true
  show-title: true
  title-animation: true
  show-rank: true
  show-elo: true
  show-world: true
  show-opponent-in-duel: true
  update-interval: 2  # seconds

bossbar:
  enabled: true
  show-opponent-health: true
  health-update-interval: 5
```

### Chat Messages
```yaml
chat:
  duel-request: true
  duel-start: true
  duel-end: true
  rank-up: true
  matchmaking-search: true
  pre-duel-instructions: true
```

### Debug Settings
```yaml
debug-mode: false
debug:
  show-elo-calculations: false
  show-matchmaking: false
  show-countdown: false
```

## Requirements

- Minecraft Paper 1.21+
- FAWE (FastAsyncWorldEdit) for arena system
- WorldGuard (recommended for arena protection)

## Installation

1. Install **FastAsyncWorldEdit** on your server
2. Place `EloRanks-2.0.0.jar` in your `plugins` folder
3. Restart or reload the server
4. Configure `config.yml` as needed
5. Add your arena schematic to `plugins/EloRanks/arenas/`

## Arena Setup

1. Build your arena in Minecraft using WorldEdit
2. Place **RED_WOOL** where you want player 1 to spawn
3. Place **BLUE_WOOL** where you want player 2 to spawn
4. Export the arena as a `.schematic` file (using FAWE: `/schem save name`)
5. Name it `cloudy.schematic` (or match your config)
6. Place the schematic in `plugins/EloRanks/arenas/`

## How It Works

### Elo Calculation
- K-factor starts at 48 for new players (first 5 placement games)
- K-factor reduces as players gain games and Elo
- Higher-rated players have less to gain, more to lose
- Bonus Elo for beating higher-rated players during placement

### Matchmaking
- Players join queue with `/duel match`
- Elo range starts at 50 and increases by 10 per second
- Maximum range is 500 Elo
- Both players must be in each other's range to match

### Duel Flow
1. Player A challenges Player B (or both use matchmaking)
2. Player B accepts the challenge (or auto-matched)
3. Both players see 5-second teleport countdown
4. 20-second countdown with color progression before fight:
   - Seconds 20-6: Blue
   - Second 5: Dark Red
   - Second 4: Red
   - Second 3: Orange
   - Second 2: Yellow
   - Second 1: Green
   - Second 0: GO!
5. Fight until one player dies or surrenders (30s minimum)
6. Winner gains Elo, loser loses Elo

### Surrender
- Available after 30 seconds of duel start
- Counts as instant loss - full Elo penalty applied
- Opponent receives full Elo reward (no halving)

## Building

```bash
# Using the build script
cd EloRanks
mvn clean package
```

The built JAR will be in `target/EloRanks-{version}.jar`

## License

MIT License