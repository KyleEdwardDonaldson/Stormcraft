# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## VPS Environment Context

This repository lives on the **Stormcraft production VPS** that hosts the live Minecraft server. The server runs under **Pterodactyl** panel management.

### Build & Deployment Workflow

When building the plugin JAR, **always build directly into the Pterodactyl plugins directory**:

```
/var/lib/pterodactyl/volumes/31a2482a-dbb7-4d21-8126-bde346cb17db/plugins/
```

**Maven build command:**
```bash
mvn clean package && cp target/Stormcraft-*.jar /var/lib/pterodactyl/volumes/31a2482a-dbb7-4d21-8126-bde346cb17db/plugins/
```

After building, restart the Minecraft server via Pterodactyl panel or console command to load the new version.

### Important Notes
- This is a **production environment** - test thoroughly before building
- The Pterodactyl volume path is the live server's plugin directory
- Configuration files (config.yml, messages.yml) persist in the plugins/Stormcraft/ subdirectory
- Server logs are accessible via Pterodactyl console

## Project Overview

**Stormcraft** is a Paper Minecraft plugin (1.21.x, Java 17+) that adds dangerous, configurable storms with countdown warnings and exposure-based damage. Players must seek shelter or take periodic damage while exposed to open sky during storms.

## Architecture

### Core Components

The plugin follows this high-level architecture (see STORMCRAFT_CONTEXT.md for full details):

- **StormManager**: Orchestrates storm lifecycle (idle → countdown → landfall → clear)
- **CountdownTask**: Handles pre-storm warnings with configurable announcement intervals
- **DamageTask**: Processes exposure checks and applies damage/effects to exposed players
- **ConfigManager**: Loads config.yml and messages.yml with validation
- **PlayerExposureUtil**: Determines if a player is exposed (sky access check, gamemode filtering, WorldGuard region support)

### Storm Model

Three configurable storm types with weighted randomization:
- `shortWeak`: Short duration, low damage
- `medium`: Medium duration/damage (default)
- `longDangerous`: Long duration, high damage

Each type has a **StormProfile** defining:
- Duration (seconds)
- Damage per second
- Extra effects (blindness, slowness, lightning strike chance)

### Package Structure

```
dev.ked.stormcraft
 ├─ StormcraftPlugin.java (main plugin class)
 ├─ config/ConfigManager.java
 ├─ schedule/StormManager.java
 ├─ schedule/CountdownTask.java
 ├─ schedule/DamageTask.java
 ├─ model/StormType.java (enum: SHORT_WEAK, MEDIUM, LONG_DANGEROUS)
 ├─ model/StormProfile.java (duration, DPS, effects)
 ├─ model/ActiveStorm.java (current storm state)
 ├─ exposure/PlayerExposureUtil.java
 ├─ api/events/StormcraftPreStormEvent.java (cancelable)
 ├─ api/events/StormcraftStormStartEvent.java
 ├─ api/events/StormcraftStormTickEvent.java
 ├─ api/events/StormcraftStormEndEvent.java
 └─ api/events/StormcraftExposureCheckEvent.java
```

## Key Implementation Details

### Exposure Detection

Player is "exposed" when ALL conditions are met:
- In an enabled world (default: overworld only)
- Not in Creative/Spectator mode (configurable)
- Has sky access above head position

Sky check implementation options:
- Quick: `world.getHighestBlockYAt(x, z)` vs player Y
- Precise: Raycast upward, with optional leaf/glass as cover

Performance: Batch checks every `exposure.checkIntervalTicks` (default 20 ticks = 1s)

### Persistence

Storm state must persist across server restarts:
- Save `data.json` every 10s and onDisable
- Store: next storm timestamp, active storm state (type, remaining duration)
- On startup: Resume countdown or active storm with remaining time

### Timing System

- Idle window: Random delay between `minDelayBetweenStormsSeconds` and `maxDelayBetweenStormsSeconds`
- Countdown: Fixed `countdownDurationSeconds` with announcements at configured intervals
- Landfall: Duration determined by selected storm profile
- Weather control: Set world weather to THUNDER during storm, CLEAR after

### Soft Dependencies

- **WorldGuard**: Check for custom region flag `stormcraft-protect` to disable damage
- **PlaceholderAPI**: Expose placeholders (`%stormcraft_status%`, `%stormcraft_time_left%`, etc.)

Both are optional; plugin must function without them.

## Commands

All commands use base `/stormcraft` (aliases: `/sc`, `/storm`):

- `/stormcraft` - Show current status
- `/stormcraft start [type] [seconds]` - Force start storm (requires `stormcraft.admin.start`)
- `/stormcraft stop` - End current storm (requires `stormcraft.admin.stop`)
- `/stormcraft next [seconds]` - Set next storm timer (requires `stormcraft.admin.next`)
- `/stormcraft reload` - Reload configs (requires `stormcraft.admin.reload`)
- `/stormcraft testdamage [seconds]` - Test exposure damage on self (requires `stormcraft.admin.test`)
- `/stormcraft weights` - Display current storm type weights (requires `stormcraft.admin`)

## Configuration Files

### config.yml
- Storm weights (probability distribution)
- Damage profiles (per storm type)
- Timing settings (delays, countdown duration, announce intervals)
- Exposure settings (check interval, cover rules, gamemode filtering)
- Enabled worlds list
- Debug flags

### messages.yml
- All player-facing messages with MiniMessage format support
- Placeholders: `{time}`, `{dps}`, `{type}`, `{duration}`, `{next}`

See STORMCRAFT_CONTEXT.md sections 14.1 and 14.2 for full config examples.

## Development Notes

### Technology Stack
- Paper API 1.21 (provides Adventure components)
- Java 17
- Build tool: To be determined (Maven or Gradle)

### Performance Considerations
- Exposure checks are O(n) per tick interval where n = online players
- Cache highest block Y per chunk during storms (invalidate on chunk unload)
- Early-exit for underground players using quick Y-comparison
- Default 20-tick interval keeps checks to ~100/second for 100 players

### Edge Cases
- Pause countdown when no players online (configurable)
- Handle sleep events during storms (configurable: allow/prevent skip)
- Re-assert thunder weather if another plugin interferes
- Validate storm weights sum to reasonable range (warn if not ~1.0)