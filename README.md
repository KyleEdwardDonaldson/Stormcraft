# ⚡ Stormcraft

**Dangerous, configurable storms with countdowns, exposure damage, and zone-based gameplay.**

Stormcraft adds dynamic weather events that pose a real threat to players. Seek shelter or face the consequences! Features randomized storm types, configurable damage profiles, economy integration, and an optional **Stormlands** zone system with traveling storms.

---

## 🌩️ Features

### Core Storm System
- **Countdown Warnings** - Configurable countdown before storm arrival
- **Storm Types** - Three severity levels: Weak, Medium, Dangerous
- **Weighted Randomization** - Control storm frequency by type
- **Exposure Damage** - Players take damage when exposed to sky during storms
- **Potion Effects** - Blindness, slowness during storms
- **Lightning Strikes** - Chance-based lightning near exposed players
- **Mob Damage** - Storms also damage exposed mobs

### Exposure Detection
- **Sky Access Check** - Requires overhead block coverage for shelter
- **Configurable Depth** - Set minimum blocks needed for protection
- **Material Options** - Choose if leaves/glass count as cover
- **GameMode Exclusions** - Ignore creative/spectator modes
- **WorldGuard Support** - Protected regions exempt from storm damage

### Economy System (Vault Integration)
- **Storm Essence** - Earn currency for braving storms
- **Type Multipliers** - Higher rewards for more dangerous storms
- **Zone Multipliers** - Bonus essence in high-risk areas

### 🗺️ **Stormlands Zone System** (Optional)

Transform your world with three risk/reward zones:

#### **Zone Types**
- **🔴 Stormlands (Center)** - High Risk, High Reward
  - 3x storm frequency
  - 3x essence rewards
  - 2.5x resource spawns
  - Block damage during storms
  - Mostly dangerous storms

- **🟠 Storm Zone (Middle Ring)** - Moderate Risk/Reward
  - 1.5x storm frequency
  - 1.5x essence/resources
  - Mix of storm types

- **🟢 Safe Zone (Outer Region)** - Low Risk, Low Reward
  - 30% normal storm frequency
  - Normal rewards
  - Mostly weak storms

#### **Traveling Storms**
- Storms spawn at random locations and move toward Stormlands center
- **Variable movement speeds** per storm type (0.03 - 2.0 blocks/second)
  - Fast-moving weak storms (pass in ~2 minutes)
  - Slow-moving medium storms (pass in 2-5 minutes)
  - Very slow dangerous storms (pass in 4-30 minutes!)
- Visible damage radius on map
- **ActionBar/BossBar storm tracker** shows distance and direction
- Real-time tracking on Dynmap
- Use `/storm` to check distance anytime

#### **Block Damage System**
- Buildings degrade during storms in Stormlands
- Degradation chains: wood→air, stone→cobble→gravel→air
- Material-based damage rates (wool 3x, glass 2.5x, stone 0.5x)
- Protected blocks immune (obsidian, chests, spawners, etc.)
- WorldGuard region protection

#### **Storm Drops**
- Valuable items spawn during storms
- Rarity tiers: Common, Rare, Epic
- Higher rates in Stormlands
- Glowing items for visibility
- Includes: diamonds, emeralds, netherite scrap, totems, etc.

#### **Custom Ore Generation**
- Passive ore spawning in Stormlands/Storm Zone
- 2x spawn rate in Stormlands
- All vanilla ores + **Ancient Debris (Stormlands exclusive!)**
- Respects Y-levels for deepslate variants

### Integrations
- **PlaceholderAPI** - Display storm status in other plugins
- **WorldGuard** - Respect protected regions
- **Vault** - Economy/permissions support
- **Dynmap** - Visualize zones and storms on web map

---

## 📦 Installation

1. **Requirements:**
   - Paper/Spigot 1.21.3+
   - Java 17+

2. **Optional Dependencies:**
   - Vault (for economy features)
   - PlaceholderAPI (for placeholders)
   - WorldGuard (for region protection)
   - Dynmap (for map visualization)

3. **Install:**
   - Place `stormcraft-0.1.0.jar` in your `plugins` folder
   - Restart server
   - Configure `plugins/Stormcraft/config.yml`
   - Restart again

---

## ⚙️ Configuration

### Basic Setup (config.yml)

```yaml
# Enabled worlds
enabledWorlds:
  - world

# Storm timing
minDelayBetweenStormsSeconds: 900   # 15 minutes
maxDelayBetweenStormsSeconds: 2400  # 40 minutes
countdownDurationSeconds: 900       # 15 minute warning

# Storm type weights (must sum to ~1.0)
stormWeights:
  shortWeak: 0.20        # 20% chance
  medium: 0.65           # 65% chance
  longDangerous: 0.15    # 15% chance

# Damage profiles
damageProfiles:
  shortWeak:
    minDurationSeconds: 600      # 10-30 minutes
    maxDurationSeconds: 1800
    minDamagePerSecond: 0.04     # ~5 minutes to kill
    maxDamagePerSecond: 0.1
    minMovementSpeed: 0.5        # Fast-moving (50 blocks in ~2 min)
    maxMovementSpeed: 2.0        # Very fast (50 blocks in 25 sec)
    extraEffects:
      blindness: false
      slownessAmplifier: -1
      lightningStrikeChance: 0.02
  medium:
    minDurationSeconds: 300
    maxDurationSeconds: 1200
    minDamagePerSecond: 0.2
    maxDamagePerSecond: 0.5
    minMovementSpeed: 0.15       # Slow (50 blocks in ~5.5 min)
    maxMovementSpeed: 0.5        # Moderate (50 blocks in ~2 min)
  longDangerous:
    minDurationSeconds: 180
    maxDurationSeconds: 1200
    minDamagePerSecond: 1.0
    maxDamagePerSecond: 3.0
    minMovementSpeed: 0.03       # Very slow crawl (50 blocks in ~30 min)
    maxMovementSpeed: 0.2        # Slow advance (50 blocks in ~4 min)
```

### Zone System Setup

```yaml
zones:
  enabled: false  # Set to true to enable
  centerX: 0      # Center of your Stormlands
  centerZ: 0

  stormlands:
    radius: 2500  # Blocks from center
    stormFrequencyMultiplier: 3.0
    essenceMultiplier: 3.0
    resourceMultiplier: 2.5
    blockDamageEnabled: true
    stormDropsEnabled: true
    stormTypeWeights:
      shortWeak: 0.20
      medium: 0.30
      longDangerous: 0.50

  stormZone:
    radius: 5000
    # ... (see full config.yml for details)

  safeZone:
    radius: 10000
    # ...

# Traveling storms (requires zones enabled)
travelingStorms:
  enabled: false
  damageRadius: 50.0      # Damage area around storm
  # Movement speed is per storm type (see damageProfiles above)

# Storm Tracker UI (for traveling storms)
stormTracker:
  mode: "actionbar"       # "actionbar" or "bossbar"
  range: 1000             # Distance in blocks to show tracker
  updateInterval: 20      # Ticks between updates (20 = 1 second)

# Block damage (only in Stormlands)
blockDamage:
  enabled: true
  baseChance: 0.01        # 1% base chance
  maxChecksPerTick: 50

# Storm drops
stormDrops:
  enabled: true
  chanceStormlands: 0.1   # 10% per check
  chanceStormZone: 0.05   # 5% per check

# Ore generation
oreGeneration:
  enabled: true
  baseChance: 0.001
  chunksPerTick: 2
  attemptsPerChunk: 3
```

### Exposure Settings

```yaml
exposure:
  checkIntervalTicks: 20  # Check every second
  treatLeavesAsCover: true
  treatGlassAsCover: true
  ignoreGameModes:
    - CREATIVE
    - SPECTATOR
  ignoreIfUnderBlocksMinDepth: 1  # Blocks needed overhead
```

### Economy Settings

```yaml
economy:
  enabled: true
  essencePerTick: 0.1  # Base essence per check tick
  essenceMultipliers:
    shortWeak: 1.0
    medium: 2.0
    longDangerous: 4.0
```

---

## 🎮 Commands

### Player Commands
- `/storm` - Check closest storm status (distance, direction, time remaining)
- `/storms` - List all active storms
- `/stormcraft` - Alias for `/storm`

### Admin Commands
- `/stormcraft start [type] [duration]` - Force start a storm
- `/stormcraft stop` - End active storm
- `/stormcraft next <seconds>` - Set/force next storm timer
- `/stormcraft reload` - Reload configuration
- `/stormcraft testdamage` - Test exposure damage on self
- `/stormcraft weights` - View storm type weights

**Examples:**
```
/stormcraft start                    # Start random storm
/stormcraft start longDangerous      # Start dangerous storm
/stormcraft start medium 300         # Start 5-minute medium storm
/stormcraft next 60                  # Next storm in 60 seconds
/stormcraft next 0                   # Start countdown now
```

---

## 🔑 Permissions

- `stormcraft.view` - See storm announcements (default: true)
- `stormcraft.admin` - All admin commands (default: op)
  - `stormcraft.admin.start` - Force start storms
  - `stormcraft.admin.stop` - Stop active storms
  - `stormcraft.admin.next` - Control storm timing
  - `stormcraft.admin.reload` - Reload config
  - `stormcraft.admin.test` - Test damage

---

## 🎯 Storm Tracker Setup (Traveling Storms)

When traveling storms are enabled, the plugin displays real-time storm tracking via **ActionBar** or **BossBar** instead of global chat announcements.

### Configuration

```yaml
stormTracker:
  mode: "actionbar"  # Options: "actionbar" or "bossbar"
  range: 1000        # Distance in blocks to show tracker
  updateInterval: 20 # Update frequency in ticks (20 = 1 second)
```

### Display Modes

**ActionBar Mode** (Recommended)
- Shows compact storm info above hotbar
- Example: `⛈ STORM NW 450m`
- Color-coded by distance:
  - 🔴 Red (< 100 blocks) - DANGER!
  - 🟡 Yellow (100-300 blocks) - Warning
  - 🔵 Blue (> 300 blocks) - Distant
- Changes to `⚡ IN STORM ⚡` when inside damage radius

**BossBar Mode**
- Shows persistent bar at top of screen
- Progress bar fills as storm approaches
- Color changes with distance (blue → yellow → red)
- Title shows direction and distance
- More visible but takes up more screen space

### Setup Steps

1. **Enable zones and traveling storms** in `config.yml`:
   ```yaml
   zones:
     enabled: true
     # ... zone configuration

   travelingStorms:
     enabled: true
     damageRadius: 50.0
   ```

2. **Configure storm tracker** (optional - uses defaults above):
   ```yaml
   stormTracker:
     mode: "actionbar"  # or "bossbar"
     range: 1000
     updateInterval: 20
   ```

3. **Restart server**

### How It Works

- Players see storm info **only when within range** (default 1000 blocks)
- Displays **cardinal direction** (N, NE, E, SE, S, SW, W, NW) and distance
- **No global announcements** when traveling storms enabled (tracker replaces them)
- Works with `/storm` command to check status anytime

### Warning Times by Storm Type

With 1000 block range and 50 block damage radius:

| Storm Type     | Speed Range | Warning Time      |
|----------------|-------------|-------------------|
| Short Weak     | 0.5-2.0 b/s | 8-33 minutes      |
| Medium         | 0.15-0.5 b/s| 33-111 minutes    |
| Long Dangerous | 0.03-0.2 b/s| 83-555+ minutes   |

*Note: Dangerous storms move very slowly and can be visible for hours before arrival!*

---

## 🗺️ Dynmap Setup

1. **Install Dynmap** plugin (download from SpigotMC)
2. **Enable zones** in Stormcraft config:
   ```yaml
   zones:
     enabled: true
   ```
3. **Restart server**
4. **Access Dynmap** at `http://storm.quetzal.games:8123`

**What you'll see:**
- 🔴 Red circle = Stormlands (high danger)
- 🟠 Orange circle = Storm Zone
- 🟢 Green circle = Safe Zone
- 🟣 Purple marker = Active storm location (when traveling storms enabled)

---

## 📊 PlaceholderAPI

Available placeholders when PlaceholderAPI is installed:

- `%stormcraft_status%` - Current status (idle/countdown/active)
- `%stormcraft_time%` - Time until next storm / remaining duration
- `%stormcraft_type%` - Upcoming/active storm type
- `%stormcraft_damage%` - Storm damage per second

---

## 🎯 Gameplay Strategy

### Without Zones (Traditional Mode)
- Build secure shelters with solid roofs
- Monitor countdown warnings
- Stay indoors during storms
- Earn essence by surviving exposure

### With Zones (Stormlands Mode)
- **Safe Zone**: Safe building, slow progression
- **Storm Zone**: Moderate risk for faster progression
- **Stormlands**: Highest risk/reward
  - Buildings degrade over time
  - Frequent dangerous storms
  - Best loot and resources
  - Ancient debris spawns naturally
  - Need supply lines to Safe Zone for storage

**Risk vs Reward Balance:**
- Stormlands offers 3x rewards but constant danger
- Block damage prevents permanent settlement
- Players must brave storms for rare resources
- Economy creates trading opportunities

---

## 🛠️ Building for Development

```bash
git clone <repository>
cd stormcraft
mvn clean package
```

Output: `target/stormcraft-0.1.0.jar`

**Requirements:**
- JDK 17+
- Maven 3.6+

---

## 📝 Configuration Tips

### Balanced Server (Recommended)
- Enable zones for endgame content
- Keep traveling storms enabled for dynamic gameplay
- Use **ActionBar mode** for storm tracker (less intrusive)
- Set Stormlands radius to 2500 blocks (small but rewarding)
- Set tracker range to 1000 blocks (3+ minutes warning)
- Enable all resource systems (drops, ore gen, block damage)

### Hardcore Server
- Increase storm frequency (lower delays)
- Increase damage values
- Enable block damage
- Higher lightning chances
- Reduce essence rewards to make risks feel more significant

### Casual Server
- Disable zones (traditional mode)
- Longer delays between storms
- Lower damage values
- More weak storms, fewer dangerous
- Disable block damage

### Performance Optimization
- Disable ore generation if not needed
- Reduce `maxChecksPerTick` for block damage
- Increase check intervals
- Limit storm drop frequency

---

## 🐛 Troubleshooting

**Players taking damage while sheltered:**
- Check `logExposureSamples: true` for debugging
- Verify `treatGlassAsCover` / `treatLeavesAsCover` settings
- Ensure `ignoreIfUnderBlocksMinDepth: 1` is correct
- Check WorldGuard regions aren't interfering

**Zones not appearing on Dynmap:**
- Verify Dynmap is installed
- Check `zones.enabled: true`
- Restart server after config changes
- Check console for Dynmap initialization errors

**Storms not starting:**
- Check enabled worlds list
- Verify no other weather plugins conflict
- Check console for errors
- Try `/stormcraft next 0` to force countdown

**Performance issues:**
- Disable ore generation if not using zones
- Reduce block damage checks
- Lower storm drop frequency
- Increase task intervals

---

## 📄 License

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

**TL;DR:** Free to use, modify, and distribute. Just include the license notice and give credit.

---

## 🤝 Credits

**Author:** Kyle Edward Donaldson

**Website:** https://ked.dev

**Version:** 0.1.0

**Minecraft Version:** 1.21.3+

**API:** Paper/Spigot


---

## 🔮 Roadmap

Future features under consideration:
- Storm severity progression over time
- Seasonal weather patterns
- Custom storm effects (sandstorms, blizzards)
- Player-built storm shelters with durability
- Storm prediction system
- Cross-world zone support
- MySQL persistence for networks

---

**Enjoy the storms! ⚡**
