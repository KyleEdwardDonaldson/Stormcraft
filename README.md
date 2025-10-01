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

### Storm Essence Economy
- **Requires Stormcraft-Essence plugin** for economy features
- Earn essence by surviving storm exposure
- Type multipliers for different storm severities
- Zone multipliers for high-risk areas

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
- **Erratic multi-storm spawning** - 1-6 storms can spawn at once!
- Storms spawn at the border between Storm Zone and Safe Zone
- **Variable movement speeds** per storm type (0.03 - 2.0 blocks/second)
  - Fast-moving weak storms (pass in ~2 minutes)
  - Slow-moving medium storms (pass in 2-5 minutes)
  - Very slow dangerous storms (pass in 4-30 minutes!)
- **Damage ramp-up** - Storms build from 0 to full damage over 60 seconds
- Storms move toward Stormlands center
- Visible damage radius on map
- **ActionBar/BossBar storm tracker** shows closest storm distance and direction
- Real-time tracking on Dynmap, squaremap, and BlueMap
- Use `/storm` to check closest storm, `/storms` to list all

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
- **Stormcraft-Essence** - Essence economy and abilities (optional)
- **Dynmap** - Visualize zones and storms on web map
- **squaremap** - Modern map visualization support
- **BlueMap** - 3D web map visualization

---

## 📦 Installation

1. **Requirements:**
   - Paper/Spigot 1.21.3+
   - Java 17+

2. **Optional Dependencies:**
   - **Stormcraft-Essence** (for essence economy and abilities)
   - PlaceholderAPI (for placeholders)
   - WorldGuard (for region protection)
   - Dynmap (for map visualization)
   - squaremap (for modern map visualization)
   - BlueMap (for 3D map visualization)

3. **Install:**
   - Place `stormcraft-0.1.0.jar` in your `plugins` folder
   - Restart server
   - Configure `plugins/Stormcraft/config.yml`
   - Restart again

4. **Optional: Custom World Generation**
   - Install Multiverse-Core (or similar world management plugin)
   - Create a new world with Stormcraft generator:
     ```
     /mv create stormworld normal -g Stormcraft
     ```
   - The generator creates ring-based biome distribution:
     - **Stormlands (0-2500):** Mountain biomes, dramatic peaks
     - **Storm Zone (2500-5000):** Open plains and hills
     - **Safe Zone (5000-10000):** Dense forests
     - **Outer Wilds (10000+):** Normal vanilla biomes
   - Biomes transition smoothly with no harsh boundaries
   - All vanilla structures spawn naturally based on biomes
   - Perfect thematic integration with storm system!

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

  # Erratic burst spawning (1-6 storms at once!)
  erraticSpawning:
    enabled: true
    minBurstSize: 1
    maxBurstSize: 6
    minDelaySeconds: 180   # 3 minutes
    maxDelaySeconds: 900   # 15 minutes
    burstChanceWeights:
      1: 0.40  # 40% chance of 1 storm
      2: 0.25  # 25% chance of 2 storms
      3: 0.15  # etc.
      4: 0.10
      5: 0.07
      6: 0.03

  # Spawn at border between Storm Zone and Safe Zone
  spawnLocation:
    spawnAtBorder: true
    borderBias: 0.7        # 0.7 = 70% toward Storm Zone
    borderSpread: 500      # +/- 500 blocks from border

  # Damage ramp-up (0 to full over time)
  damageRampUp:
    enabled: true
    rampUpSeconds: 60      # 1 minute to reach full damage

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

**Note:** Economy features have been moved to the **Stormcraft-Essence** plugin. Install Stormcraft-Essence to enable essence rewards and abilities. Configure economy settings in `plugins/Stormcraft-Essence/config.yml`.

---

## 🎮 Commands

### Player Commands
- `/storm` - Check closest storm status (distance, direction, time remaining)
- `/storms` - List all active storms
- `/storm ui` - Toggle storm UI display mode (action bar / boss bar)
- `/storm infuse` - Place infusion pedestal (requires Stormcraft-Essence)

### Admin Commands
- `/storm start [type] [duration]` - Force start a storm
- `/storm stop` - End active storm
- `/storm next <seconds>` - Set/force next storm timer
- `/storm reload` - Reload configuration
- `/storm testdamage` - Test exposure damage on self
- `/storm weights` - View storm type weights

**Examples:**
```
/storm start                    # Start random storm
/storm start longDangerous      # Start dangerous storm
/storm start medium 300         # Start 5-minute medium storm
/storm next 60                  # Next storm in 60 seconds
/storm next 0                   # Start countdown now
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

## 🗺️ Map Integration Setup

Stormcraft supports **Dynmap**, **squaremap**, and **BlueMap** for visualizing zones and active storms.

### Setup (All Maps)

1. **Install your preferred map plugin:**
   - [Dynmap](https://www.spigotmc.org/resources/dynmap.274/) (SpigotMC)
   - [squaremap](https://github.com/jpenilla/squaremap) (GitHub)
   - [BlueMap](https://www.spigotmc.org/resources/bluemap.83557/) (SpigotMC)

2. **Enable zones** in Stormcraft config:
   ```yaml
   zones:
     enabled: true
   ```

3. **Restart server**

4. **Access your map** (default ports):
   - Dynmap: `http://your-server:8123`
   - squaremap: `http://your-server:8080`

### What You'll See

**All maps show:**
- 🔴 Red circle = Stormlands (high danger)
- 🟠 Orange circle = Storm Zone
- 🟢 Green circle = Safe Zone
- 🟣 Purple markers = Active storm locations with damage radius

**Multiple Storms:**
When using erratic spawning, you'll see up to 6 simultaneous storms on the map, each with their own damage radius circles.

---

## 🌍 Custom World Generation

Stormcraft includes an optional custom world generator that creates **thematically perfect storm worlds** with ring-based biome distribution.

### How It Works

The generator uses distance from world center (0,0) to determine which biomes spawn:

**Ring 1: Stormlands Core (0-2500 blocks)**
- Biomes: Jagged Peaks, Frozen Peaks, Stony Peaks, Windswept Hills
- Terrain: Dramatic mountains (y=150+), harsh peaks
- Structures: Ancient Cities, Strongholds, Mineshafts
- Theme: Most dangerous, apocalyptic wasteland

**Ring 2: Storm Zone (2500-5000 blocks)**
- Biomes: Plains, Sunflower Plains, Savanna, Meadow
- Terrain: Open, rolling hills
- Structures: Villages, Pillager Outposts
- Theme: Exposed, moderate danger

**Ring 3: Safe Zone (5000-10000 blocks)**
- Biomes: Dark Forest, Jungle, Taiga, Swamp, Mangrove
- Terrain: Dense forests, natural shelter
- Structures: Woodland Mansions, Jungle Temples, Witch Huts
- Theme: Protected, buildable, safe

**Ring 4: Outer Wilds (10000+ blocks)**
- Biomes: All vanilla biomes (normal distribution)
- Terrain: Standard Minecraft
- Structures: Everything vanilla
- Theme: Normal gameplay

### Setup Instructions

1. **Install Multiverse-Core** (recommended world manager):
   ```
   https://dev.bukkit.org/projects/multiverse-core
   ```

2. **Create new world with Stormcraft generator:**
   ```
   /mv create stormworld normal -g Stormcraft
   ```

3. **Configure zones** (use same center as world generation):
   ```yaml
   zones:
     enabled: true
     centerX: 0  # Must match world center
     centerZ: 0  # Must match world center
   ```

4. **Teleport to new world:**
   ```
   /mv tp stormworld
   ```

### Features

✅ **Uses vanilla biomes** - No custom biomes, full compatibility
✅ **Vanilla structures work** - Villages, temples, mansions spawn naturally
✅ **Smooth transitions** - Biomes blend naturally over 300 blocks
✅ **Noise-based variation** - Not perfect circles, natural-looking
✅ **Works with existing worlds** - Only applies to NEW worlds created with generator
✅ **Performance optimized** - Minimal overhead, async generation

### What Players Experience

```
Spawn in Safe Zone → Dark forests, natural shelter
Walk 5000 blocks → Terrain opens up, plains and savannas appear
Walk 2500 blocks → Dramatic mountains loom, storms intensify
Reach center (0,0) → Highest peaks, deadliest storms, best loot
```

### Important Notes

- **Cannot modify existing worlds** - Only works for NEW world creation
- **Reuses existing storm spawn weights** - Biome preferences from config apply
- **Loot scales naturally** - Vanilla structures have better loot in dangerous zones (see below)
- **Map integrations work** - Dynmap/squaremap show zone boundaries perfectly

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
- Earn essence by surviving exposure (requires Stormcraft-Essence)

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

**Zones not appearing on map:**
- Verify your map plugin (Dynmap/squaremap) is installed
- Check `zones.enabled: true`
- Restart server after config changes
- Check console for map integration initialization messages
- Try both map plugins - at least one should work!

**Storms not starting:**
- Check enabled worlds list
- Verify no other weather plugins conflict
- Check console for errors
- Try `/storm next 0` to force countdown

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

### Recently Added (v0.1.0 - Compass & Abilities Update)
- ✅ **Compass navigation** - Compass points away from nearest storm
- ✅ **Zone-based actionbar** - Shows current zone (Stormlands/Storm Zone/Safe Zone)
- ✅ **Variable storm sizes** - 300-3900 block radius based on storm type
- ✅ **Storm tracking improvements** - Distance to edge calculations
- ✅ **Stormclear ability support** - Temp speed boost for storms
- ✅ **Custom world generator** - Ring-based biome distribution
- ✅ **Biome-weighted storm spawning** - Storms prefer thematic biomes per zone
- ✅ Multiple simultaneous storms (erratic spawning)
- ✅ Damage ramp-up system
- ✅ Border-based storm spawning
- ✅ squaremap + BlueMap support
- ✅ Variable storm movement speeds
- ✅ Multi-storm tracking UI

### Future Features
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
